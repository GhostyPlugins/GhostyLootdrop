package de.gergh0stface.ghostylootdrop.commands;

import de.gergh0stface.ghostylootdrop.GhostyLootdrop;
import de.gergh0stface.ghostylootdrop.manager.DropManager;
import de.gergh0stface.ghostylootdrop.manager.LangManager;
import de.gergh0stface.ghostylootdrop.models.LootDrop;
import de.gergh0stface.ghostylootdrop.models.SavedDrop;
import de.gergh0stface.ghostylootdrop.utils.ColorUtil;
import de.gergh0stface.ghostylootdrop.utils.GuiUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LootdropCommand implements CommandExecutor, TabCompleter {

    private final GhostyLootdrop plugin;
    private final LangManager lang;
    private final DropManager dropManager;

    public LootdropCommand(GhostyLootdrop plugin) {
        this.plugin      = plugin;
        this.lang        = plugin.getLangManager();
        this.dropManager = plugin.getDropManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // ─── help ──────────────────────────────────────────────────────────
            case "help" -> sendHelp(sender);

            // ─── reload ───────────────────────────────────────────────────────
            case "reload" -> {
                if (!sender.hasPermission("ghostylootdrop.reload")) {
                    sender.sendMessage(lang.get("no-permission"));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(lang.get("reload-success"));
            }

            // ─── create ───────────────────────────────────────────────────────
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(lang.get("player-only"));
                    return true;
                }
                if (!player.hasPermission("ghostylootdrop.create")) {
                    player.sendMessage(lang.get("no-permission"));
                    return true;
                }
                openSetupGui(player);
            }

            // ─── start ────────────────────────────────────────────────────────
            case "start" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(lang.get("player-only"));
                    return true;
                }
                if (!player.hasPermission("ghostylootdrop.start")) {
                    player.sendMessage(lang.get("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(lang.get("invalid-args"));
                    return true;
                }
                String id = args[1];
                SavedDrop saved = plugin.getStorageManager().loadDrop(id);
                if (saved == null) {
                    player.sendMessage(lang.get("drop-not-found", "id", id));
                    return true;
                }
                player.sendMessage(lang.get("drop-launching", "id", id));
                dropManager.launchDrop(player, saved);
            }

            // ─── list ─────────────────────────────────────────────────────────
            case "list" -> {
                if (!sender.hasPermission("ghostylootdrop.list")) {
                    sender.sendMessage(lang.get("no-permission"));
                    return true;
                }
                sender.sendMessage(ColorUtil.color(lang.getRaw("list-header")));
                sender.sendMessage(ColorUtil.color(lang.getRaw("list-title")));
                sender.sendMessage(ColorUtil.color(lang.getRaw("list-header")));

                var drops = dropManager.getActiveDrops();
                if (drops.isEmpty()) {
                    sender.sendMessage(lang.get("list-empty"));
                } else {
                    for (LootDrop d : drops) {
                        String loc = d.getChestLocation() != null
                                ? d.getChestLocation().getBlockX() + ", "
                                + d.getChestLocation().getBlockY() + ", "
                                + d.getChestLocation().getBlockZ()
                                : d.getSpawnLocation().getBlockX() + ", "
                                + d.getSpawnLocation().getBlockY() + ", "
                                + d.getSpawnLocation().getBlockZ() + " (falling)";
                        String[] parts = loc.split(", ");
                        sender.sendMessage(lang.get("list-entry",
                                "id", d.getId(), "name", d.getName(),
                                "x", parts[0], "y", parts[1], "z", parts[2]));
                    }
                }
                sender.sendMessage(ColorUtil.color(lang.getRaw("list-footer")));
            }

            // ─── remove ───────────────────────────────────────────────────────
            case "remove" -> {
                if (!sender.hasPermission("ghostylootdrop.remove")) {
                    sender.sendMessage(lang.get("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(lang.get("invalid-args"));
                    return true;
                }
                String id = args[1];
                boolean ok = dropManager.removeDrop(id);
                if (ok) sender.sendMessage(lang.get("drop-removed", "id", id));
                else    sender.sendMessage(lang.get("drop-remove-fail", "id", id));
            }

            default -> sender.sendMessage(lang.get("unknown-command"));
        }
        return true;
    }

    private void openSetupGui(Player player) {
        int rows    = plugin.getConfig().getInt("gui.setup-rows", 6);
        String title = plugin.getConfig().getString("gui.setup-title", "&8» &6Lootdrop Setup");
        Inventory gui = GuiUtil.createSetupGui(player, rows, title, lang);
        player.openInventory(gui);
        dropManager.startSetup(player);
        player.sendMessage(lang.get("setup-opened"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.color(lang.getRaw("help.header")));
        sender.sendMessage(ColorUtil.color(lang.getRaw("help.title")));
        sender.sendMessage(ColorUtil.color(lang.getRaw("help.separator")));
        sender.sendMessage(ColorUtil.color(lang.getRaw("help.create")));
        sender.sendMessage(ColorUtil.color(lang.getRaw("help.start")));
        sender.sendMessage(ColorUtil.color(lang.getRaw("help.list")));
        sender.sendMessage(ColorUtil.color(lang.getRaw("help.remove")));
        sender.sendMessage(ColorUtil.color(lang.getRaw("help.reload")));
        sender.sendMessage(ColorUtil.color(lang.getRaw("help.footer")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = Arrays.asList("help", "create", "start", "list", "remove", "reload");
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("remove"))) {
            // Suggest saved drop IDs for 'start', active drop IDs for 'remove'
            if (args[0].equalsIgnoreCase("start")) {
                for (SavedDrop d : plugin.getStorageManager().loadAllDrops()) {
                    if (d.getId().startsWith(args[1])) completions.add(d.getId());
                }
            } else {
                for (LootDrop d : dropManager.getActiveDrops()) {
                    if (d.getId().startsWith(args[1])) completions.add(d.getId());
                }
            }
        }
        return completions;
    }
}
