package org.battleplugins.tracker.feature;

import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.Tracker;

/**
 * Represents a feature that can be loaded by BattleTracker.
 */
public interface TrackerFeature {

    boolean enabled();

    void onEnable(BattleTracker battleTracker, Tracker tracker);
}
