package de.gergh0stface.ghostylootdrop.listeners;

import de.gergh0stface.ghostylootdrop.GhostyLootdrop;
import de.gergh0stface.ghostylootdrop.manager.DropManager;
import de.gergh0stface.ghostylootdrop.manager.LangManager;
import de.gergh0stface.ghostylootdrop.models.SavedDrop;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

public class ChatListener implements Listener {

    private final GhostyLootdrop plugin;
    private final DropManager     dropManager;
    private final LangManager     lang;

    public ChatListener(GhostyLootdrop plugin) {
        this.plugin      = plugin;
        this.dropManager = plugin.getDropManager();
        this.lang        = plugin.getLangManager();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        if (!dropManager.hasPendingName(uuid)) return;

        event.setCancelled(true); // Hide message from chat

        String input = event.getMessage().trim();
        String dropId = dropManager.getPendingNameDropId(uuid);
        dropManager.clearPendingName(uuid);

        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("abbrechen")) {
            player.sendMessage(lang.get("setup-cancelled"));
            return;
        }

        // Update the saved drop name (run on main thread)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            SavedDrop saved = plugin.getStorageManager().loadDrop(dropId);
            if (saved == null) {
                player.sendMessage(lang.get("drop-not-found", "id", dropId));
                return;
            }
            saved.setName(input);
            plugin.getStorageManager().saveDrop(saved);
            player.sendMessage(lang.get("drop-name-set", "name", input));
        });
    }
}
