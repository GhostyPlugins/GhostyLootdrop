package de.gergh0stface.ghostylootdrop.manager;

import de.gergh0stface.ghostylootdrop.GhostyLootdrop;
import de.gergh0stface.ghostylootdrop.models.LootDrop;
import de.gergh0stface.ghostylootdrop.models.SavedDrop;
import de.gergh0stface.ghostylootdrop.utils.ColorUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DropManager {

    private final GhostyLootdrop plugin;

    private final Map<String, LootDrop>   activeDrops  = new ConcurrentHashMap<>();
    private final Map<String, BossBar>    bossBars     = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> fallingTasks = new ConcurrentHashMap<>();
    private final Set<String>             openChests   = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, String>     chestDropMap = new ConcurrentHashMap<>();

    private final Map<UUID, List<ItemStack>> setupSessions  = new ConcurrentHashMap<>();
    private final Map<UUID, String>          pendingNames   = new ConcurrentHashMap<>();

    public DropManager(GhostyLootdrop plugin) {
        this.plugin = plugin;
        startAutoRemoveTask();
    }

    // ─── Setup sessions ────────────────────────────────────────────────────────

    public void startSetup(Player p)          { setupSessions.put(p.getUniqueId(), new ArrayList<>()); }
    public boolean isInSetup(Player p)        { return setupSessions.containsKey(p.getUniqueId()); }
    public void cancelSetup(Player p)         { setupSessions.remove(p.getUniqueId()); pendingNames.remove(p.getUniqueId()); }
    public void setPendingName(UUID u, String id) { pendingNames.put(u, id); }
    public boolean hasPendingName(UUID u)     { return pendingNames.containsKey(u); }
    public String getPendingNameDropId(UUID u){ return pendingNames.get(u); }
    public void clearPendingName(UUID u)      { pendingNames.remove(u); }
    public Map<UUID, List<ItemStack>> getSetupSessions() { return setupSessions; }

    // ─── Launch ────────────────────────────────────────────────────────────────

    public void launchDrop(Player launcher, SavedDrop saved) {
        Location base   = launcher.getLocation().clone();
        int spawnHeight = plugin.getConfig().getInt("drop.spawn-height", 100);

        // Target = ground below player
        Location targetLoc = findGround(base);
        // Balloon origin = directly above target, at spawn height
        Location originLoc = targetLoc.clone().add(0, spawnHeight, 0);

        LootDrop drop = new LootDrop(
                saved.getId() + "-" + System.currentTimeMillis(),
                saved.getName(),
                launcher.getName(),
                launcher.getUniqueId(),
                new ArrayList<>(saved.getItems())
        );
        drop.setSpawnLocation(originLoc.clone());
        drop.setTargetLocation(targetLoc.clone());
        drop.setState(LootDrop.DropState.FALLING);
        drop.setSpawnTime(System.currentTimeMillis());

        activeDrops.put(drop.getId(), drop);

        // Play spawn sound
        playSound(originLoc, "drop.spawn-sound", "drop.spawn-sound-volume", "drop.spawn-sound-pitch");

        // Particles at launch
        if (plugin.getConfig().getBoolean("drop.spawn-particles", true)) {
            originLoc.getWorld().spawnParticle(Particle.FIREWORK, originLoc, 60, 2, 2, 2, 0.2);
        }

        // Title broadcast (center screen) with world name + coords
        String worldName = getWorldName(targetLoc.getWorld());
        broadcastTitle(
                plugin.getLangManager().getRaw("drop-spawned-title", "name", drop.getName()),
                plugin.getLangManager().getRaw("drop-spawned-sub",
                        "world", worldName,
                        "x", String.valueOf(targetLoc.getBlockX()),
                        "y", String.valueOf(targetLoc.getBlockY()),
                        "z", String.valueOf(targetLoc.getBlockZ()))
        );

        // BossBar
        setupBossBar(drop);

        // Build balloon at spawn height immediately
        buildBalloonAt(drop, originLoc.clone());

        // Spawn hologram above balloon tip
        spawnHologram(drop, originLoc.clone().add(0.5, 19, 0.5));

        // Start falling
        startFallTask(drop, originLoc.clone(), targetLoc.clone(), spawnHeight);
    }

    // ─── Falling task ─────────────────────────────────────────────────────────

    /**
     * Moves the entire balloon structure down 1 block every STEP_TICKS.
     * 100 blocks / (STEP_TICKS / 20 ticks/s) should equal 30 s → STEP_TICKS = 6.
     */
    private static final int STEP_TICKS = 6;

    private void startFallTask(LootDrop drop, Location currentOrigin, Location target, int totalBlocks) {
        final Location[] current = { currentOrigin.clone() };
        final int[] stepsDone   = { 0 };

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (drop.isRemoved()) { cancel(); return; }

                // Check if we've reached the target
                if (current[0].getBlockY() <= target.getBlockY()) {
                    land(drop, current[0].clone());
                    cancel();
                    return;
                }

                // Shift the balloon 1 block down
                shiftBalloonDown(drop, current[0]);
                current[0].subtract(0, 1, 0);
                stepsDone[0]++;

                // Trail particles alongside balloon
                current[0].getWorld().spawnParticle(
                        Particle.CLOUD,
                        current[0].clone().add(0.5, 10, 0.5),
                        6, 2, 0.5, 2, 0.02);

                // Move hologram with balloon
                if (drop.getHologramStand() != null && drop.getHologramStand().isValid()) {
                    drop.getHologramStand().teleport(
                            current[0].clone().add(0.5, 20, 0.5));
                }

                // BossBar progress
                BossBar bar = bossBars.get(drop.getId());
                if (bar != null) {
                    double progress = Math.max(0, 1.0 - (double) stepsDone[0] / totalBlocks);
                    bar.setProgress(progress);
                }
            }
        }.runTaskTimer(plugin, 0L, STEP_TICKS);

        fallingTasks.put(drop.getId(), task);
    }

    // ─── Balloon build / shift ─────────────────────────────────────────────────

    /**
     * Builds the full balloon structure at the given basket-origin.
     * bx, by, bz = centre of the basket floor.
     */
    private void buildBalloonAt(LootDrop drop, Location origin) {
        World world = origin.getWorld();
        int bx = origin.getBlockX();
        int by = origin.getBlockY();
        int bz = origin.getBlockZ();

        // Clear tracked blocks first (safety on rebuild)
        drop.getStructureBlocks().clear();

        // ── Basket ring (3×3 floor + 4 corner fence posts + side rails) ─────
        // Floor 3×3
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setBlock(drop, world, bx + dx, by,     bz + dz, Material.SPRUCE_PLANKS);
                setBlock(drop, world, bx + dx, by + 3, bz + dz, Material.SPRUCE_PLANKS);
            }
        }
        // Fence walls Y+1, Y+2
        int[][] corners = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
        for (int[] c : corners) {
            setBlock(drop, world, bx + c[0], by + 1, bz + c[1], Material.SPRUCE_FENCE);
            setBlock(drop, world, bx + c[0], by + 2, bz + c[1], Material.SPRUCE_FENCE);
        }

        // ── Balloon body (circular layers with rainbow colours) ─────────────
        // Layer definitions: {dy_above_basket_floor, radius, material}
        Object[][] layers = {
            // rope neck (narrow)
            { 4, 1, Material.DARK_OAK_FENCE   },
            // balloon proper – rainbow from bottom up
            { 5, 1, Material.RED_WOOL          },
            { 6, 2, Material.RED_WOOL          },
            { 7, 3, Material.ORANGE_WOOL       },
            { 8, 3, Material.ORANGE_WOOL       },
            { 9, 3, Material.YELLOW_WOOL       },
            {10, 4, Material.YELLOW_WOOL       },   // widest
            {11, 4, Material.LIME_WOOL         },
            {12, 3, Material.GREEN_WOOL        },
            {13, 3, Material.CYAN_WOOL         },
            {14, 3, Material.LIGHT_BLUE_WOOL   },
            {15, 2, Material.BLUE_WOOL         },
            {16, 2, Material.PURPLE_WOOL       },
            {17, 1, Material.MAGENTA_WOOL      },
            {18, 1, Material.PINK_WOOL         },
            {19, 0, Material.WHITE_WOOL        },   // tip (1×1)
        };

        for (Object[] layer : layers) {
            int dy  = (int) layer[0];
            int rad = (int) layer[1];
            Material mat = (Material) layer[2];
            fillCircle(drop, world, bx, by + dy, bz, rad, mat);
        }
    }

    /**
     * Shifts the entire balloon structure 1 block downward:
     * 1. Remove all current structure blocks (set to AIR)
     * 2. Rebuild at current origin - 1
     */
    private void shiftBalloonDown(LootDrop drop, Location currentOrigin) {
        // Remove old blocks
        for (Location loc : drop.getStructureBlocks()) {
            Block b = loc.getWorld().getBlockAt(loc);
            if (!b.getType().isAir()) b.setType(Material.AIR);
        }
        drop.getStructureBlocks().clear();

        // Rebuild 1 block lower
        Location newOrigin = currentOrigin.clone().subtract(0, 1, 0);
        buildBalloonAt(drop, newOrigin);
    }

    private void fillCircle(LootDrop drop, World world, int cx, int cy, int cz, int r, Material mat) {
        if (r == 0) {
            setBlock(drop, world, cx, cy, cz, mat);
            return;
        }
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= r * r + r) {
                    setBlock(drop, world, cx + dx, cy, cz + dz, mat);
                }
            }
        }
    }

    private void setBlock(LootDrop drop, World world, int x, int y, int z, Material mat) {
        Location loc = new Location(world, x, y, z);
        if (!mat.isAir()) {
            world.getBlockAt(loc).setType(mat, false); // false = no physics update (performance)
            drop.addStructureBlock(loc);
        }
    }

    // ─── Landing ──────────────────────────────────────────────────────────────

    private void land(LootDrop drop, Location finalOrigin) {
        drop.setState(LootDrop.DropState.LANDED);

        // Chest goes 1 block above basket floor (inside the basket)
        Location chestLoc = finalOrigin.clone().add(0, 1, 0);
        chestLoc.getWorld().getBlockAt(chestLoc).setType(Material.CHEST);
        drop.setChestLocation(chestLoc.clone());
        chestDropMap.put(blockKey(chestLoc), drop.getId());

        // Fill chest
        Block b = chestLoc.getWorld().getBlockAt(chestLoc);
        if (b.getState() instanceof Chest chest) {
            chest.getInventory().clear();
            for (ItemStack item : drop.getItems()) chest.getInventory().addItem(item);
        }

        // Move hologram just above balloon tip
        if (drop.getHologramStand() != null && drop.getHologramStand().isValid()) {
            drop.getHologramStand().teleport(finalOrigin.clone().add(0.5, 21, 0.5));
        }

        // Particles + sound
        if (plugin.getConfig().getBoolean("drop.land-particles", true)) {
            finalOrigin.getWorld().spawnParticle(Particle.EXPLOSION,
                    finalOrigin.clone().add(0.5, 2, 0.5), 5, 1, 1, 1, 0.1);
            finalOrigin.getWorld().spawnParticle(Particle.FIREWORK,
                    finalOrigin.clone().add(0.5, 2, 0.5), 80, 2, 2, 2, 0.3);
        }
        playSound(finalOrigin, "drop.land-sound", "drop.land-sound-volume", "drop.land-sound-pitch");

        // Flares
        if (plugin.getConfig().getBoolean("drop.flare.enabled", true)) {
            int count = plugin.getConfig().getInt("drop.flare.count", 3);
            for (int i = 0; i < count; i++) {
                final int delay = i * 15;
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> launchFlare(finalOrigin.clone().add(0.5, 1, 0.5)), delay);
            }
        }

        // Update BossBar
        BossBar bar = bossBars.get(drop.getId());
        if (bar != null) {
            bar.setTitle(ColorUtil.color(
                    plugin.getLangManager().getRaw("bossbar-landed", "name", drop.getName())));
            bar.setProgress(1.0);
        }

        String worldName = getWorldName(finalOrigin.getWorld());
        broadcastTitle(
                plugin.getLangManager().getRaw("drop-landed-title", "name", drop.getName()),
                plugin.getLangManager().getRaw("drop-landed-sub",
                        "world", worldName,
                        "x", String.valueOf(finalOrigin.getBlockX()),
                        "y", String.valueOf(chestLoc.getBlockY()),
                        "z", String.valueOf(finalOrigin.getBlockZ()))
        );
    }

    // ─── Remove ───────────────────────────────────────────────────────────────

    public boolean removeDrop(String id) {
        LootDrop drop = activeDrops.remove(id);
        if (drop == null) return false;

        drop.setState(LootDrop.DropState.REMOVED);

        BukkitTask task = fallingTasks.remove(id);
        if (task != null) task.cancel();

        BossBar bar = bossBars.remove(id);
        if (bar != null) { bar.removeAll(); bar.setVisible(false); }

        if (drop.getHologramStand() != null && drop.getHologramStand().isValid()) {
            drop.getHologramStand().remove();
        }

        // Remove chest
        if (drop.getChestLocation() != null) {
            Block b = drop.getChestLocation().getWorld().getBlockAt(drop.getChestLocation());
            if (b.getType() == Material.CHEST) b.setType(Material.AIR);
            chestDropMap.remove(blockKey(drop.getChestLocation()));
        }

        // Remove all balloon structure blocks
        List<Location> blocks = new ArrayList<>(drop.getStructureBlocks());
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Block blk = blocks.get(i).getWorld().getBlockAt(blocks.get(i));
            if (!blk.getType().isAir() && blk.getType() != Material.CHEST) {
                blk.setType(Material.AIR, false);
            }
        }
        drop.getStructureBlocks().clear();

        return true;
    }

    public void removeAllDrops() {
        for (String id : new HashSet<>(activeDrops.keySet())) removeDrop(id);
    }

    // ─── Chest access ─────────────────────────────────────────────────────────

    private String blockKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public String getDropIdByChest(Location loc) { return chestDropMap.get(blockKey(loc)); }
    public LootDrop getDropById(String id)        { return activeDrops.get(id); }
    public boolean isChestOpen(Location loc)      { return openChests.contains(blockKey(loc)); }

    public void setChestOpen(Location loc, boolean open) {
        String k = blockKey(loc);
        if (open) openChests.add(k); else openChests.remove(k);
    }

    public Collection<LootDrop> getActiveDrops() { return activeDrops.values(); }

    // ─── Hologram ─────────────────────────────────────────────────────────────

    private void spawnHologram(LootDrop drop, Location loc) {
        if (!plugin.getConfig().getBoolean("drop.hologram.enabled", true)) return;

        List<String> lines = plugin.getConfig().getStringList("drop.hologram.lines");
        String text = (lines.isEmpty() ? "&6&l✦ LOOT DROP ✦" : lines.get(0))
                .replace("{name}", drop.getName());

        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCustomNameVisible(true);
        stand.setCustomName(ColorUtil.color(text));
        stand.setSmall(true);
        stand.setCollidable(false);
        stand.setInvulnerable(true);
        drop.setHologramStand(stand);
    }

    // ─── BossBar ──────────────────────────────────────────────────────────────

    private void setupBossBar(LootDrop drop) {
        if (!plugin.getConfig().getBoolean("drop.bossbar.enabled", true)) return;

        BarColor color;
        BarStyle style;
        try { color = BarColor.valueOf(plugin.getConfig().getString("drop.bossbar.color", "RED").toUpperCase()); }
        catch (IllegalArgumentException e) { color = BarColor.RED; }
        try { style = BarStyle.valueOf(plugin.getConfig().getString("drop.bossbar.style", "SOLID").toUpperCase()); }
        catch (IllegalArgumentException e) { style = BarStyle.SOLID; }

        String title = ColorUtil.color(plugin.getLangManager().getRaw("bossbar-falling", "name", drop.getName()));
        BossBar bar  = Bukkit.createBossBar(title, color, style);
        bar.setProgress(1.0);
        Bukkit.getOnlinePlayers().forEach(bar::addPlayer);
        bossBars.put(drop.getId(), bar);
    }

    // ─── Auto-remove ──────────────────────────────────────────────────────────

    private void startAutoRemoveTask() {
        int seconds = plugin.getConfig().getInt("drop.auto-remove-seconds", 300);
        if (seconds <= 0) return;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (LootDrop drop : new ArrayList<>(activeDrops.values())) {
                if (drop.isLanded() && (now - drop.getSpawnTime()) > (long) seconds * 1000) {
                    broadcast(plugin.getLangManager().get("drop-expired", "name", drop.getName()));
                    removeDrop(drop.getId());
                }
            }
        }, 200L, 200L);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns a human-friendly world name.
     * Uses reflection to hook into Multiverse-Core if present,
     * so there is zero compile-time dependency on Multiverse.
     */
    private String getWorldName(World world) {
        try {
            org.bukkit.plugin.Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
            if (mv != null && mv.isEnabled()) {
                // getMVWorldManager() via reflection
                Object wm = mv.getClass().getMethod("getMVWorldManager").invoke(mv);
                // isMVWorld(World)
                boolean isMV = (boolean) wm.getClass().getMethod("isMVWorld", World.class).invoke(wm, world);
                if (isMV) {
                    // getMVWorld(World) → MVWorld
                    Object mvWorld = wm.getClass().getMethod("getMVWorld", World.class).invoke(wm, world);
                    // getAlias()
                    String alias = (String) mvWorld.getClass().getMethod("getAlias").invoke(mvWorld);
                    if (alias != null && !alias.isBlank()) return alias;
                }
            }
        } catch (Exception ignored) {
            // Multiverse not installed or API changed – fall through
        }
        return world.getName();
    }

    /**
     * Sends the drop-spawned / drop-landed message as a centred TITLE
     * to all online players instead of a chat message.
     *
     * @param title    big centre line  (e.g. "✦ LOOT DROP ✦")
     * @param subtitle smaller sub-line (e.g. world + coords)
     */
    private void broadcastTitle(String title, String subtitle) {
        String t  = ColorUtil.color(title);
        String st = ColorUtil.color(subtitle);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(t, st, 10, 70, 20);
        }
    }

    /** Still used for chat-only messages (expired, removed, etc.). */
    private void broadcast(String message) {
        Bukkit.broadcastMessage(message);
    }

    private Location findGround(Location base) {
        Location loc = new Location(base.getWorld(), base.getBlockX(),
                base.getWorld().getMaxHeight(), base.getBlockZ());
        while (loc.getBlockY() > 1) {
            if (loc.getWorld().getBlockAt(loc).getType().isSolid()) {
                return loc.add(0, 1, 0);
            }
            loc.subtract(0, 1, 0);
        }
        return base.clone();
    }

    private void playSound(Location loc, String soundKey, String volKey, String pitchKey) {
        String name = plugin.getConfig().getString(soundKey, "ENTITY_WITHER_SPAWN");
        float  vol  = (float) plugin.getConfig().getDouble(volKey,   1.0);
        float  pit  = (float) plugin.getConfig().getDouble(pitchKey, 1.0);
        try { loc.getWorld().playSound(loc, Sound.valueOf(name.toUpperCase()), vol, pit); }
        catch (IllegalArgumentException e) { plugin.getLogger().warning("Unknown sound: " + name); }
    }

    private void launchFlare(Location loc) {
        Firework fw = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.ORANGE, Color.YELLOW, Color.RED)
                .withFade(Color.WHITE)
                .with(FireworkEffect.Type.BURST)
                .trail(true).build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }
}
