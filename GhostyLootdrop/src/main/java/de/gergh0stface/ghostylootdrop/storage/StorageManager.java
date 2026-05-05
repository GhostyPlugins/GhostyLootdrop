package de.gergh0stface.ghostylootdrop.storage;

import de.gergh0stface.ghostylootdrop.GhostyLootdrop;
import de.gergh0stface.ghostylootdrop.models.SavedDrop;
import de.gergh0stface.ghostylootdrop.utils.ColorUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class StorageManager {

    private final GhostyLootdrop plugin;
    private StorageType type;

    // YAML
    private File dataFile;
    private FileConfiguration dataConfig;

    // MySQL
    private HikariDataSource dataSource;

    public enum StorageType { YAML, MYSQL }

    public StorageManager(GhostyLootdrop plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        String typeStr = plugin.getConfig().getString("storage.type", "YAML").toUpperCase();
        try {
            type = StorageType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown storage type '" + typeStr + "'. Defaulting to YAML.");
            type = StorageType.YAML;
        }

        if (type == StorageType.MYSQL) {
            initMySQL();
        } else {
            initYAML();
        }
    }

    // ─── YAML ──────────────────────────────────────────────────────────────────

    private void initYAML() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create data.yml", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveYAML() {
        try { dataConfig.save(dataFile); } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", e);
        }
    }

    // ─── MySQL ─────────────────────────────────────────────────────────────────

    private void initMySQL() {
        FileConfiguration cfg = plugin.getConfig();
        HikariConfig hc = new HikariConfig();
        String host     = cfg.getString("storage.mysql.host", "localhost");
        int    port     = cfg.getInt   ("storage.mysql.port", 3306);
        String db       = cfg.getString("storage.mysql.database", "ghostylootdrop");
        String user     = cfg.getString("storage.mysql.username", "root");
        String pass     = cfg.getString("storage.mysql.password", "password");
        boolean useSSL  = cfg.getBoolean("storage.mysql.use-ssl", false);
        int poolSize    = cfg.getInt   ("storage.mysql.pool-size", 10);

        hc.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=" + useSSL + "&autoReconnect=true");
        hc.setUsername(user);
        hc.setPassword(pass);
        hc.setMaximumPoolSize(poolSize);
        hc.setConnectionTimeout(10000);
        hc.setPoolName("GhostyLootdrop-Pool");

        try {
            dataSource = new HikariDataSource(hc);
            createMySQLTables();
            Bukkit.getConsoleSender().sendMessage(ColorUtil.color(
                    plugin.getLangManager().getRaw("mysql-connect-success")));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL connection failed! Falling back to YAML.", e);
            Bukkit.getConsoleSender().sendMessage(ColorUtil.color(
                    plugin.getLangManager().getRaw("mysql-connect-fail")));
            type = StorageType.YAML;
            initYAML();
        }
    }

    private void createMySQLTables() throws SQLException {
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ghosty_drops (
                    id           VARCHAR(64)  NOT NULL PRIMARY KEY,
                    name         VARCHAR(128) NOT NULL,
                    creator_uuid VARCHAR(36)  NOT NULL,
                    creator_name VARCHAR(64)  NOT NULL,
                    items        MEDIUMTEXT   NOT NULL,
                    created_at   BIGINT       NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);
        }
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    public void saveDrop(SavedDrop drop) {
        if (type == StorageType.MYSQL) saveDropMySQL(drop);
        else saveDropYAML(drop);
    }

    public void deleteDrop(String id) {
        if (type == StorageType.MYSQL) deleteDropMySQL(id);
        else deleteDropYAML(id);
    }

    public SavedDrop loadDrop(String id) {
        if (type == StorageType.MYSQL) return loadDropMySQL(id);
        return loadDropYAML(id);
    }

    public List<SavedDrop> loadAllDrops() {
        if (type == StorageType.MYSQL) return loadAllMySQL();
        return loadAllYAML();
    }

    public void reload() {
        if (type == StorageType.YAML) {
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        }
        // MySQL needs no reload – connection pool persists
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    // ─── YAML implementations ──────────────────────────────────────────────────

    private void saveDropYAML(SavedDrop drop) {
        String base = "drops." + drop.getId();
        dataConfig.set(base + ".name", drop.getName());
        dataConfig.set(base + ".creator-uuid", drop.getCreatorUUID().toString());
        dataConfig.set(base + ".creator-name", drop.getCreatorName());
        dataConfig.set(base + ".created-at", drop.getCreatedAt());
        dataConfig.set(base + ".items", serializeItems(drop.getItems()));
        saveYAML();
    }

    private void deleteDropYAML(String id) {
        dataConfig.set("drops." + id, null);
        saveYAML();
    }

    private SavedDrop loadDropYAML(String id) {
        String base = "drops." + id;
        if (!dataConfig.contains(base)) return null;
        return yamlToSavedDrop(id, base);
    }

    private List<SavedDrop> loadAllYAML() {
        List<SavedDrop> list = new ArrayList<>();
        if (!dataConfig.contains("drops")) return list;
        for (String id : dataConfig.getConfigurationSection("drops").getKeys(false)) {
            SavedDrop d = yamlToSavedDrop(id, "drops." + id);
            if (d != null) list.add(d);
        }
        return list;
    }

    private SavedDrop yamlToSavedDrop(String id, String base) {
        try {
            String name        = dataConfig.getString(base + ".name", id);
            UUID   uuid        = UUID.fromString(dataConfig.getString(base + ".creator-uuid", UUID.randomUUID().toString()));
            String creatorName = dataConfig.getString(base + ".creator-name", "Unknown");
            long   createdAt   = dataConfig.getLong(base + ".created-at", System.currentTimeMillis());
            String raw         = dataConfig.getString(base + ".items", "");
            List<ItemStack> items = deserializeItems(raw);
            return new SavedDrop(id, name, uuid, creatorName, items, createdAt);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load drop '" + id + "' from YAML: " + e.getMessage());
            return null;
        }
    }

    // ─── MySQL implementations ─────────────────────────────────────────────────

    private void saveDropMySQL(SavedDrop drop) {
        String sql = """
            INSERT INTO ghosty_drops (id, name, creator_uuid, creator_name, items, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE name=VALUES(name), items=VALUES(items);
        """;
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, drop.getId());
            ps.setString(2, drop.getName());
            ps.setString(3, drop.getCreatorUUID().toString());
            ps.setString(4, drop.getCreatorName());
            ps.setString(5, serializeItems(drop.getItems()));
            ps.setLong  (6, drop.getCreatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL save error", e);
        }
    }

    private void deleteDropMySQL(String id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM ghosty_drops WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL delete error", e);
        }
    }

    private SavedDrop loadDropMySQL(String id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM ghosty_drops WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return resultToSavedDrop(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL load error", e);
        }
        return null;
    }

    private List<SavedDrop> loadAllMySQL() {
        List<SavedDrop> list = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM ghosty_drops");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                SavedDrop d = resultToSavedDrop(rs);
                if (d != null) list.add(d);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL loadAll error", e);
        }
        return list;
    }

    private SavedDrop resultToSavedDrop(ResultSet rs) {
        try {
            String id          = rs.getString("id");
            String name        = rs.getString("name");
            UUID   uuid        = UUID.fromString(rs.getString("creator_uuid"));
            String creatorName = rs.getString("creator_name");
            long   createdAt   = rs.getLong("created_at");
            List<ItemStack> items = deserializeItems(rs.getString("items"));
            return new SavedDrop(id, name, uuid, creatorName, items, createdAt);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not parse MySQL row: " + e.getMessage());
            return null;
        }
    }

    // ─── Item Serialization ────────────────────────────────────────────────────

    public static String serializeItems(List<ItemStack> items) {
        try {
            ByteArrayOutputStream out  = new ByteArrayOutputStream();
            BukkitObjectOutputStream oos = new BukkitObjectOutputStream(out);
            oos.writeInt(items.size());
            for (ItemStack item : items) oos.writeObject(item);
            oos.flush();
            return Base64Coder.encodeLines(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Could not serialize items", e);
        }
    }

    public static List<ItemStack> deserializeItems(String data) {
        List<ItemStack> items = new ArrayList<>();
        if (data == null || data.isBlank()) return items;
        try {
            ByteArrayInputStream in   = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream  ois = new BukkitObjectInputStream(in);
            int count = ois.readInt();
            for (int i = 0; i < count; i++) {
                items.add((ItemStack) ois.readObject());
            }
        } catch (Exception e) {
            GhostyLootdrop.getInstance().getLogger().warning("Could not deserialize items: " + e.getMessage());
        }
        return items;
    }
}
