package de.gergh0stface.ghostylootdrop;

import de.gergh0stface.ghostylootdrop.commands.LootdropCommand;
import de.gergh0stface.ghostylootdrop.listeners.ChestListener;
import de.gergh0stface.ghostylootdrop.listeners.ChatListener;
import de.gergh0stface.ghostylootdrop.listeners.GuiListener;
import de.gergh0stface.ghostylootdrop.manager.DropManager;
import de.gergh0stface.ghostylootdrop.manager.LangManager;
import de.gergh0stface.ghostylootdrop.storage.StorageManager;
import de.gergh0stface.ghostylootdrop.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class GhostyLootdrop extends JavaPlugin {

    private static GhostyLootdrop instance;
    private LangManager langManager;
    private DropManager dropManager;
    private StorageManager storageManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default configs
        saveDefaultConfig();
        saveResource("lang/en.yml", false);
        saveResource("lang/de.yml", false);

        // Init managers
        this.langManager = new LangManager(this);
        this.storageManager = new StorageManager(this);
        this.dropManager = new DropManager(this);

        // Register commands
        LootdropCommand cmdExecutor = new LootdropCommand(this);
        getCommand("ghostylootdrop").setExecutor(cmdExecutor);
        getCommand("ghostylootdrop").setTabCompleter(cmdExecutor);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChestListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);

        // Console banner
        printBanner();
    }

    @Override
    public void onDisable() {
        if (dropManager != null) dropManager.removeAllDrops();
        if (storageManager != null) storageManager.close();
        getLogger().info("GhostyLootdrop disabled. Bye!");
    }

    public void reload() {
        reloadConfig();
        langManager.reload();
        storageManager.reload();
        dropManager.removeAllDrops();
    }

    private void printBanner() {
        String version = getDescription().getVersion();
        String line = "==============================================";
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color("&6" + line));
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color("&r"));
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color("&6                   GhostyLootdrop &ev" + version + " &6enabled"));
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color("&6                   Author &8>> &eGer_Gh0stface"));
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color("&r"));
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color("&6" + line));
    }

    public static GhostyLootdrop getInstance() { return instance; }
    public LangManager getLangManager() { return langManager; }
    public DropManager getDropManager() { return dropManager; }
    public StorageManager getStorageManager() { return storageManager; }
}
