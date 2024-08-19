package org.battleplugins.tracker.feature.battlearena;

import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.feature.Feature;
import org.bukkit.Bukkit;

public class BattleArenaFeature implements Feature {
    private final boolean enabled;

    private BattleArenaHandler handler;

    public BattleArenaFeature() {
        this.enabled = Bukkit.getServer().getPluginManager().getPlugin("BattleArena") != null;
    }

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void onEnable(BattleTracker battleTracker) {
        if (!this.enabled) {
            battleTracker.info("BattleArena not found. Not tracking arena statistics.");
            return;
        }

        this.handler = new BattleArenaHandler(battleTracker);
        battleTracker.info("BattleArena found. Tracking arena statistics.");
    }

    @Override
    public void onDisable(BattleTracker battleTracker) {
        if (this.handler != null) {
            this.handler.onDisable();
        }
    }
}
