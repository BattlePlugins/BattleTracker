package org.battleplugins.tracker.feature.recap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BattleRecap {
    private final List<RecapEntry> entries = new ArrayList<>();
    private final double startingHealth;
    private final Instant createTime;
    private final String recapOwner;

    private PlayerInventory inventory;
    private ItemStack[] inventorySnapshot;

    public BattleRecap(Player player) {
        this(player.getInventory(), player.getHealth(), player.getName());
    }

    public BattleRecap(PlayerInventory inventory, double startingHealth, String recapOwner) {
        this.inventory = inventory;
        this.startingHealth = startingHealth;
        this.recapOwner = recapOwner;
        this.createTime = Instant.now();
    }

    public void record(RecapEntry entry) {
        this.entries.add(entry);
    }

    public Optional<RecapEntry> lastEntry() {
        return Optional.ofNullable(this.getLastEntry());
    }

    @Nullable
    public RecapEntry getLastEntry() {
        if (this.entries.isEmpty()) {
            return null;
        }

        return this.entries.get(this.entries.size() - 1);
    }

    public List<RecapEntry> getEntries() {
        return List.copyOf(this.entries);
    }

    public List<RecapEntry> getCombinedEntries() {
        List<RecapEntry> entries = new ArrayList<>();
        RecapEntry lastEntry = null;
        for (RecapEntry entry : this.entries) {
            // We only want to combine the health gain entries, since they can get excessive
            if (entry.kind() == RecapEntry.Kind.LOSS) {
                if (lastEntry != null) {
                    entries.add(lastEntry);
                    lastEntry = null;
                }

                entries.add(entry);
                continue;
            }

            if (lastEntry != null) {
                if (lastEntry.kind() == RecapEntry.Kind.GAIN) {
                    lastEntry = lastEntry.toBuilder()
                            .amount(lastEntry.amount() + entry.amount())
                            .logTime(entry.logTime())
                            .build();
                } else {
                    lastEntry = entry;
                }
            } else {
                lastEntry = entry;
            }
        }

        if (lastEntry != null) {
            entries.add(lastEntry);
        }

        return entries;
    }

    public double getStartingHealth() {
        return this.startingHealth;
    }

    public String getRecapOwner() {
        return this.recapOwner;
    }

    public Instant getCreateTime() {
        return this.createTime;
    }

    public void markDeath() {
        this.inventorySnapshot = this.getInventorySnapshot();
        this.inventory = null;
    }

    public ItemStack[] getInventorySnapshot() {
        if (this.inventorySnapshot != null) {
            return this.inventorySnapshot;
        }

        ItemStack[] contents = this.inventory.getStorageContents();

        // Size is contents + armor + main & offhand
        ItemStack[] snapshot = new ItemStack[contents.length + 6];

        // Main inventory contents
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null) {
                snapshot[i] = item.clone();
            }
        }

        // Armor contents
        snapshot[contents.length] = nullify(this.inventory.getHelmet());
        snapshot[contents.length + 1] = nullify(this.inventory.getChestplate());
        snapshot[contents.length + 2] = nullify(this.inventory.getLeggings());
        snapshot[contents.length + 3] = nullify(this.inventory.getBoots());

        // Hand + offhand contents
        snapshot[contents.length + 4] = nullify(this.inventory.getItemInMainHand());
        snapshot[contents.length + 5] = nullify(this.inventory.getItemInOffHand());
        return snapshot;
    }

    private static ItemStack nullify(ItemStack item) {
        if (item != null && item.getType() == Material.AIR) {
            return null;
        }

        return item;
    }
}
