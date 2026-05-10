package de.gergh0stface.ghostylootdrop.models;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a single active loot drop in the world.
 */
public class LootDrop {

    private final String id;
    private final String name;
    private final String creatorName;
    private final UUID creatorUUID;
    private final List<ItemStack> items;

    // Runtime state
    private Location spawnLocation;
    private Location targetLocation;
    private Location chestLocation;
    private DropState state;
    private ArmorStand hologramStand;
    private long spawnTime;

    /** All blocks placed for the balloon structure – restored to AIR on removal. */
    private final List<Location> structureBlocks = new ArrayList<>();

    public enum DropState {
        FALLING,
        LANDED,
        OPEN,
        REMOVED
    }

    public LootDrop(String id, String name, String creatorName, UUID creatorUUID, List<ItemStack> items) {
        this.id = id;
        this.name = name;
        this.creatorName = creatorName;
        this.creatorUUID = creatorUUID;
        this.items = items;
        this.state = DropState.FALLING;
    }

    // ─── Getters / Setters ─────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCreatorName() { return creatorName; }
    public UUID getCreatorUUID() { return creatorUUID; }
    public List<ItemStack> getItems() { return items; }

    public Location getSpawnLocation() { return spawnLocation; }
    public void setSpawnLocation(Location l) { this.spawnLocation = l; }

    public Location getTargetLocation() { return targetLocation; }
    public void setTargetLocation(Location l) { this.targetLocation = l; }

    public Location getChestLocation() { return chestLocation; }
    public void setChestLocation(Location l) { this.chestLocation = l; }

    public DropState getState() { return state; }
    public void setState(DropState state) { this.state = state; }

    public ArmorStand getHologramStand() { return hologramStand; }
    public void setHologramStand(ArmorStand stand) { this.hologramStand = stand; }

    public long getSpawnTime() { return spawnTime; }
    public void setSpawnTime(long t) { this.spawnTime = t; }

    public List<Location> getStructureBlocks() { return structureBlocks; }
    public void addStructureBlock(Location loc) { structureBlocks.add(loc.clone()); }

    public boolean isLanded() { return state == DropState.LANDED; }
    public boolean isFalling() { return state == DropState.FALLING; }
    public boolean isRemoved() { return state == DropState.REMOVED; }
}
