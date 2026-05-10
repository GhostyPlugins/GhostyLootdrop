package de.gergh0stface.ghostylootdrop.listeners;

import de.gergh0stface.ghostylootdrop.GhostyLootdrop;
import de.gergh0stface.ghostylootdrop.manager.DropManager;
import de.gergh0stface.ghostylootdrop.manager.LangManager;
import de.gergh0stface.ghostylootdrop.models.LootDrop;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ChestListener implements Listener {

    private final GhostyLootdrop plugin;
    private final DropManager     dropManager;
    private final LangManager     lang;

    public ChestListener(GhostyLootdrop plugin) {
        this.plugin      = plugin;
        this.dropManager = plugin.getDropManager();
        this.lang        = plugin.getLangManager();
    }

    // Player opens a drop chest
    @EventHandler(priority = EventPriority.HIGH)
    public void onChestClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;

        Location loc = block.getLocation();
        String dropId = dropManager.getDropIdByChest(loc);
        if (dropId == null) return;

        Player player = event.getPlayer();
        LootDrop drop = dropManager.getDropById(dropId);
        if (drop == null || drop.isRemoved()) return;

        if (!player.hasPermission("ghostylootdrop.use")) {
            event.setCancelled(true);
            player.sendMessage(lang.get("drop-no-permission"));
            return;
        }

        if (dropManager.isChestOpen(loc)) {
            event.setCancelled(true);
            player.sendMessage(lang.get("drop-already-open"));
            return;
        }

        dropManager.setChestOpen(loc, true);
        player.sendMessage(lang.get("drop-opened", "name", drop.getName()));

        String soundName = plugin.getConfig().getString("drop.open-sound", "BLOCK_CHEST_OPEN");
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            float vol   = (float) plugin.getConfig().getDouble("drop.open-sound-volume", 1.0);
            float pitch = (float) plugin.getConfig().getDouble("drop.open-sound-pitch", 1.0);
            loc.getWorld().playSound(loc, sound, vol, pitch);
        } catch (IllegalArgumentException ignored) {}

        drop.setState(LootDrop.DropState.OPEN);
    }

    // Detect item removal – remove drop instantly when chest is emptied
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Inventory topInv = event.getView().getTopInventory();
        Location chestLoc = topInv.getLocation();
        if (chestLoc == null) return;

        String dropId = dropManager.getDropIdByChest(chestLoc);
        if (dropId == null) return;

        LootDrop drop = dropManager.getDropById(dropId);
        if (drop == null || drop.isRemoved()) return;

        // Check 1 tick later (after the click is processed)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (drop.isRemoved()) return;
            Block block = chestLoc.getWorld().getBlockAt(chestLoc);
            if (block.getType() != Material.CHEST) return;

            if (block.getState() instanceof org.bukkit.block.Chest chest) {
                boolean empty = true;
                for (ItemStack item : chest.getInventory().getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        empty = false;
                        break;
                    }
                }
                if (empty) {
                    new java.util.ArrayList<>(chest.getInventory().getViewers())
                            .forEach(human -> human.closeInventory());
                    dropManager.removeDrop(dropId);
                    plugin.getServer().broadcastMessage(
                            lang.get("drop-expired", "name", drop.getName()));
                }
            }
        }, 1L);
    }

    // Player closes the chest
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Location chestLoc = event.getInventory().getLocation();
        if (chestLoc == null) return;

        String dropId = dropManager.getDropIdByChest(chestLoc);
        if (dropId == null) return;

        dropManager.setChestOpen(chestLoc, false);
        LootDrop drop = dropManager.getDropById(dropId);
        if (drop != null && !drop.isRemoved()) {
            drop.setState(LootDrop.DropState.LANDED);
        }
    }

    // Prevent breaking drop chest or structure blocks
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();

        if (event.getBlock().getType() == Material.CHEST
                && dropManager.getDropIdByChest(loc) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(lang.get("drop-no-permission"));
            return;
        }

        if (isStructureBlock(loc)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(lang.get("drop-no-permission"));
        }
    }

    // Protect from explosions
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b ->
                (b.getType() == Material.CHEST && dropManager.getDropIdByChest(b.getLocation()) != null)
                || isStructureBlock(b.getLocation()));
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b ->
                (b.getType() == Material.CHEST && dropManager.getDropIdByChest(b.getLocation()) != null)
                || isStructureBlock(b.getLocation()));
    }

    private boolean isStructureBlock(Location loc) {
        for (LootDrop drop : dropManager.getActiveDrops()) {
            for (Location sLoc : drop.getStructureBlocks()) {
                if (sLoc.getBlockX() == loc.getBlockX()
                        && sLoc.getBlockY() == loc.getBlockY()
                        && sLoc.getBlockZ() == loc.getBlockZ()
                        && sLoc.getWorld().equals(loc.getWorld())) {
                    return true;
                }
            }
        }
        return false;
    }
}
