package de.gergh0stface.ghostylootdrop.manager;

import de.gergh0stface.ghostylootdrop.GhostyLootdrop;
import de.gergh0stface.ghostylootdrop.utils.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

public class LangManager {

    private final GhostyLootdrop plugin;
    private FileConfiguration lang;
    private String prefix;

    public LangManager(GhostyLootdrop plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        load();
    }

    private void load() {
        String langCode = plugin.getConfig().getString("language", "en");
        File langFile = new File(plugin.getDataFolder(), "lang/" + langCode + ".yml");

        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file '" + langCode + ".yml' not found! Falling back to 'en.yml'.");
            langFile = new File(plugin.getDataFolder(), "lang/en.yml");
        }

        lang = YamlConfiguration.loadConfiguration(langFile);
        prefix = ColorUtil.color(lang.getString("messages.prefix", "&8[&6GhostyLootdrop&8] &r"));
    }

    /**
     * Returns a colorized message with optional placeholder replacements.
     * Placeholders are passed as key-value pairs: key, value, key, value, ...
     */
    public String get(String key, String... placeholders) {
        String raw = lang.getString("messages." + key, "&cMissing message: " + key);
        raw = prefix + raw;

        // Replace placeholders
        if (placeholders.length % 2 == 0) {
            for (int i = 0; i < placeholders.length; i += 2) {
                raw = raw.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
            }
        }
        return ColorUtil.color(raw);
    }

    /**
     * Like get() but without the prefix.
     */
    public String getRaw(String key, String... placeholders) {
        String raw = lang.getString("messages." + key, "&cMissing message: " + key);

        if (placeholders.length % 2 == 0) {
            for (int i = 0; i < placeholders.length; i += 2) {
                raw = raw.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
            }
        }
        return ColorUtil.color(raw);
    }

    /**
     * Sends a message to a player (or console sender).
     */
    public void send(Player player, String key, String... placeholders) {
        player.sendMessage(get(key, placeholders));
    }

    public String getPrefix() {
        return prefix;
    }
}
