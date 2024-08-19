package org.battleplugins.tracker.util;

import com.google.common.base.Suppliers;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.battleplugins.tracker.Tracker;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TrackerInventoryHolder implements InventoryHolder {
    public static final NamespacedKey RECAP_KEY = new NamespacedKey("battletracker", "recap");

    private final Key key;
    private final Tracker tracker;
    private final Supplier<Inventory> supplier;

    private final Map<Key, Runnable> onClick = new HashMap<>();

    public TrackerInventoryHolder(Key key, Tracker tracker, Supplier<Inventory> supplier) {
        this.key = key;
        this.tracker = tracker;
        this.supplier = Suppliers.memoize(supplier::get);
    }

    public Key getKey() {
        return this.key;
    }

    public Tracker getTracker() {
        return this.tracker;
    }

    public void onClick(Key key, Runnable runnable) {
        this.onClick.put(key, runnable);
    }

    public void handleClick(Key key) {
        Runnable runnable = this.onClick.get(key);
        if (runnable == null) {
            return;
        }

        runnable.run();
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return this.supplier.get();
    }

    public static Inventory create(Key key, Tracker tracker, int size, Component title, Consumer<TrackerInventoryHolder> holderConsumer) {
        AtomicReference<Inventory> ref = new AtomicReference<>();
        return ref.updateAndGet(nil -> {
            TrackerInventoryHolder holder = new TrackerInventoryHolder(key, tracker, ref::get);
            holderConsumer.accept(holder);

            return Bukkit.createInventory(holder, size, title);
        });
    }
}
