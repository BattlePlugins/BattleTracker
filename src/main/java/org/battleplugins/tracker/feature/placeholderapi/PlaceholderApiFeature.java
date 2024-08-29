package org.battleplugins.tracker.feature.placeholderapi;

import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.feature.Feature;
import org.bukkit.Bukkit;

public class PlaceholderApiFeature implements Feature {
    private final boolean enabled;

    private BattleTrackerExpansion expansion;

    public PlaceholderApiFeature() {
        this.enabled = Bukkit.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void onEnable(BattleTracker battleTracker) {
        if (!this.enabled) {
            return;
        }

        this.expansion = new BattleTrackerExpansion(battleTracker);
        this.expansion.register();
    }

    @Override
    public void onDisable(BattleTracker battleTracker) {
        if (this.expansion != null) {
            this.expansion.unregister();
        }
    }
}
