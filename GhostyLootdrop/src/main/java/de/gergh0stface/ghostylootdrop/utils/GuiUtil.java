package de.gergh0stface.ghostylootdrop.utils;

import de.gergh0stface.ghostylootdrop.manager.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class GuiUtil {

    private GuiUtil() {}

    /**
     * Creates the setup GUI for a loot drop.
     * The bottom row contains control buttons; the rest is for items.
     * Uses LangManager so button labels respect the configured language.
     */
    public static Inventory createSetupGui(Player player, int rows, String title, LangManager lang) {
        int size = Math.min(6, Math.max(1, rows)) * 9;
        Inventory inv = Bukkit.createInventory(null, size, ColorUtil.color(title));

        int bottomStart = size - 9;

        // Confirm button (slot bottomStart + 2)
        ItemStack confirm = createItem(
                Material.LIME_STAINED_GLASS_PANE,
                lang.getRaw("gui.confirm-button"),
                List.of(lang.getRaw("gui.confirm-button-lore1"), lang.getRaw("gui.confirm-button-lore2"))
        );
        inv.setItem(bottomStart + 2, confirm);

        // Cancel button (slot bottomStart + 6)
        ItemStack cancel = createItem(
                Material.RED_STAINED_GLASS_PANE,
                lang.getRaw("gui.cancel-button"),
                List.of(lang.getRaw("gui.cancel-button-lore"))
        );
        inv.setItem(bottomStart + 6, cancel);

        // Info item (slot bottomStart + 4)
        ItemStack info = createItem(
                Material.CHEST,
                lang.getRaw("gui.info-title"),
                List.of(lang.getRaw("gui.info-lore1"), lang.getRaw("gui.info-lore2"))
        );
        inv.setItem(bottomStart + 4, info);

        // Decorative glass panes for empty bottom-row slots
        ItemStack deco = createItem(Material.GRAY_STAINED_GLASS_PANE, "&8 ", List.of());
        for (int i = bottomStart; i < size; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, deco);
        }

        return inv;
    }

    public static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(ColorUtil.color(name));
        meta.setLore(lore.stream().map(ColorUtil::color).toList());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Returns true only if the slot is inside the TOP inventory's control row.
     *
     * Bug fix: previously "slot >= bottomStart" also matched the player's
     * own inventory slots (rawSlot >= inventorySize), causing all player-inventory
     * clicks to be cancelled and items to bounce back.
     * Correct check: slot must be within [bottomStart, inventorySize).
     */
    public static boolean isControlSlot(int slot, int inventorySize) {
        int bottomStart = inventorySize - 9;
        return slot >= bottomStart && slot < inventorySize;
    }

    public static boolean isConfirmSlot(int slot, int inventorySize) {
        return slot == (inventorySize - 9) + 2;
    }

    public static boolean isCancelSlot(int slot, int inventorySize) {
        return slot == (inventorySize - 9) + 6;
    }
}

