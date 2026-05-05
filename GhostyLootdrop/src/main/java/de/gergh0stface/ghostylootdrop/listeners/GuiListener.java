package de.gergh0stface.ghostylootdrop.listeners;

import de.gergh0stface.ghostylootdrop.GhostyLootdrop;
import de.gergh0stface.ghostylootdrop.manager.DropManager;
import de.gergh0stface.ghostylootdrop.manager.LangManager;
import de.gergh0stface.ghostylootdrop.models.SavedDrop;
import de.gergh0stface.ghostylootdrop.utils.GuiUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GuiListener implements Listener {

    private final GhostyLootdrop plugin;
    private final DropManager     dropManager;
    private final LangManager     lang;

    public GuiListener(GhostyLootdrop plugin) {
        this.plugin      = plugin;
        this.dropManager = plugin.getDropManager();
        this.lang        = plugin.getLangManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!dropManager.isInSetup(player)) return;

        Inventory inv = event.getInventory();
        int slot      = event.getRawSlot();
        int size      = inv.getSize();

        // Block clicks on the control row
        if (GuiUtil.isControlSlot(slot, size)) {
            event.setCancelled(true);

            // Confirm button
            if (GuiUtil.isConfirmSlot(slot, size)) {
                handleConfirm(player, inv);
            }
            // Cancel button
            else if (GuiUtil.isCancelSlot(slot, size)) {
                dropManager.cancelSetup(player);
                player.closeInventory();
                player.sendMessage(lang.get("setup-cancelled"));
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!dropManager.isInSetup(player)) return;

        int size = event.getInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (GuiUtil.isControlSlot(slot, size)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!dropManager.isInSetup(player)) return;
        // If they close without confirming, we leave setup open in background
        // unless they explicitly cancelled via button. Do nothing here.
    }

    // ─── Confirm ──────────────────────────────────────────────────────────────

    private void handleConfirm(Player player, Inventory inv) {
        List<ItemStack> items = new ArrayList<>();
        int bottomStart = inv.getSize() - 9;

        for (int i = 0; i < bottomStart; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }

        if (items.isEmpty()) {
            player.sendMessage(lang.get("setup-empty"));
            return;
        }

        // Generate unique ID
        String id = "drop_" + System.currentTimeMillis() + "_" + player.getName().toLowerCase();

        // Save with a default name first
        SavedDrop saved = new SavedDrop(id, "Drop-" + player.getName(),
                player.getUniqueId(), player.getName(), items, System.currentTimeMillis());
        plugin.getStorageManager().saveDrop(saved);

        // Close GUI, then ask for name via chat
        player.closeInventory();
        dropManager.cancelSetup(player);

        player.sendMessage(lang.get("setup-saved", "id", id));
        player.sendMessage(lang.get("drop-name-prompt"));
        dropManager.setPendingName(player.getUniqueId(), id);
    }
}
