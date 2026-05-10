package de.gergh0stface.ghostylootdrop.models;

import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.UUID;

/**
 * A saved (template) loot drop that can be launched by an admin.
 * Stored in data.yml or MySQL.
 */
public class SavedDrop {

    private final String id;
    private String name;
    private final UUID creatorUUID;
    private final String creatorName;
    private List<ItemStack> items;
    private final long createdAt;

    public SavedDrop(String id, String name, UUID creatorUUID, String creatorName,
                     List<ItemStack> items, long createdAt) {
        this.id = id;
        this.name = name;
        this.creatorUUID = creatorUUID;
        this.creatorName = creatorName;
        this.items = items;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getCreatorUUID() { return creatorUUID; }
    public String getCreatorName() { return creatorName; }
    public List<ItemStack> getItems() { return items; }
    public void setItems(List<ItemStack> items) { this.items = items; }
    public long getCreatedAt() { return createdAt; }
}
