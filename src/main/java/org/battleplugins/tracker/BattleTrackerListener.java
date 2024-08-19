package org.battleplugins.tracker;

import org.battleplugins.tracker.event.BattleTrackerPostInitializeEvent;
import org.battleplugins.tracker.util.TrackerInventoryHolder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;

class BattleTrackerListener implements Listener {
    private final BattleTracker plugin;

    public BattleTrackerListener(BattleTracker plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerLoad(ServerLoadEvent event) {
        // There is logic called later, however by this point all plugins
        // using the BattleTracker API should have been loaded. As modules will
        // listen for this event to register their behavior, we need to ensure
        // they are fully initialized so any references to said modules in
        // tracker config files will be valid.
        new BattleTrackerPostInitializeEvent(this.plugin).callEvent();

        this.plugin.postInitialize();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (Tracker tracker : this.plugin.getTrackers()) {
            tracker.getOrCreateRecord((OfflinePlayer) player).whenComplete((record, e) -> {
                if (tracker instanceof SqlTracker sqlTracker) {
                    sqlTracker.getRecords().lock(player.getUniqueId());
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (Tracker tracker : this.plugin.getTrackers()) {
            CompletableFuture<Void> saveFuture = tracker.save(player);
            if (this.plugin.isDebugMode()) {
                long startTimestamp = System.currentTimeMillis();

                saveFuture.thenRun(() -> this.plugin.info("Saved data for {} in {}ms", player.getName(), System.currentTimeMillis() - startTimestamp));
            }

            if (Bukkit.isStopping()) {
                saveFuture.join();
            }

            if (tracker instanceof SqlTracker sqlTracker) {
                sqlTracker.getRecords().unlock(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof TrackerInventoryHolder keyedHolder)) {
            return;
        }

        if (keyedHolder.getKey().equals(TrackerInventoryHolder.RECAP_KEY)) {
            event.setCancelled(true);
        }

        if (!topInventory.equals(event.getClickedInventory())) {
            return;
        }

        ItemStack itemInSlot = event.getCurrentItem();
        if (itemInSlot != null && itemInSlot.hasItemMeta() && itemInSlot.getItemMeta().getPersistentDataContainer().has(TrackerInventoryHolder.RECAP_KEY)) {
            event.getWhoClicked().closeInventory();

            keyedHolder.handleClick(TrackerInventoryHolder.RECAP_KEY);
        }
    }
}
