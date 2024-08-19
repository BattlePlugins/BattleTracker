package org.battleplugins.tracker.event;

import org.battleplugins.tracker.BattleTracker;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when BattleTracker is starting its initialization.
 */
public class BattleTrackerPreInitializeEvent extends Event {
    private final static HandlerList HANDLERS = new HandlerList();

    private final BattleTracker battleTracker;

    public BattleTrackerPreInitializeEvent(BattleTracker battleTracker) {
        this.battleTracker = battleTracker;
    }

    /**
     * Returns the {@link BattleTracker} instance.
     *
     * @return the BattleTracker instance
     */
    public BattleTracker getBattleTracker() {
        return battleTracker;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
