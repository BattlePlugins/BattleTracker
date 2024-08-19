package org.battleplugins.tracker.feature;

import org.battleplugins.tracker.BattleTracker;

public interface Feature {

    boolean enabled();

    void onEnable(BattleTracker battleTracker);

    void onDisable(BattleTracker battleTracker);
}
