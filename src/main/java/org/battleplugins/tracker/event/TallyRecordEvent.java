package org.battleplugins.tracker.event;

import org.battleplugins.tracker.Tracker;
import org.battleplugins.tracker.stat.Record;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a tally is recorded for two different
 * players.
 */
public class TallyRecordEvent extends Event {
    private final static HandlerList HANDLERS = new HandlerList();

    private final Tracker tracker;
    private final Record victor;
    private final Record loser;
    private final boolean tie;

    public TallyRecordEvent(Tracker tracker, Record victor, Record loser, boolean tie) {
        this.tracker = tracker;
        this.victor = victor;
        this.loser = loser;
        this.tie = tie;
    }

    /**
     * Returns the {@link Tracker} instance this
     * tally was recorded in.
     *
     * @return the Tracker instance
     */
    public Tracker getTracker() {
        return this.tracker;
    }

    /**
     * Returns the victor of the tally.
     *
     * @return the victor of the tally
     */
    public Record getVictor() {
        return this.victor;
    }

    /**
     * Returns the loser of the tally.
     *
     * @return the loser of the tally
     */
    public Record getLoser() {
        return this.loser;
    }

    /**
     * Returns whether the tally was a tie.
     *
     * @return whether the tally was a tie
     */
    public boolean isTie() {
        return this.tie;
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
