package org.battleplugins.tracker.stat;

import org.battleplugins.tracker.Tracker;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores and holds tracker data for a player.
 */
public class Record {
    protected Tracker tracker;
    protected UUID id;
    protected String name;
    protected final Map<StatType, Float> statistics;
    private boolean tracking = true;

    public Record(Tracker tracker, UUID id, String name, Map<StatType, Float> statistics) {
        this.tracker = tracker;
        this.id = id;
        this.name = name;
        this.statistics = statistics;

        // Populate untracked records
        this.setValue(StatType.KD_RATIO, this.getStat(StatType.KILLS) / Math.max(1, this.getStat(StatType.DEATHS)));
    }

    /**
     * Returns the ID of the record.
     *
     * @return the ID of the record
     */
    public UUID getId() {
        return this.id;
    }

    /**
     * Returns the name of the record.
     *
     * @return the name of the record
     */
    public String getName() {
        return this.name;
    }

    /**
     * Returns the statistics of the record.
     *
     * @return the statistics of the record
     */
    public Map<StatType, Float> getStatistics() {
        return Map.copyOf(this.statistics);
    }

    /**
     * Returns whether this record should
     * be tracked.
     *
     * @return whether this record should be tracked
     */
    public boolean isTracking() {
        return this.tracking;
    }

    /**
     * Sets whether this record should be tracked.
     *
     * @param tracking whether this record should be tracked
     */
    public void setTracking(boolean tracking) {
        this.tracking = tracking;
    }

    /**
     * Returns if the {@link StatType} is in the record.
     *
     * @param stat the stat to check
     * @return if the StatType is in the record
     */
    public boolean hasStat(StatType stat) {
        return this.statistics.containsKey(stat);
    }

    /**
     * Returns the value for the specified {@link StatType}.
     *
     * @param stat the StatType to get the value of
     * @return the value for the specified StatType
     */
    public float getStat(StatType stat) {
        return this.statistics.getOrDefault(stat, 0.0f);
    }

    /**
     * Sets the value of the given {@link StatType}.
     *
     * @param stat the stat to set the value for
     * @param value the (new) value of the StatType
     */
    public void setValue(StatType stat, float value) {
        this.statistics.put(stat, value);
    }

    /**
     * Increments the value of the given {@link StatType}.
     *
     * @param stat the stat to increment the value for
     */
    public void incrementValue(StatType stat) {
        this.setValue(stat, this.getStat(stat) + 1);
    }

    /**
     * Returns the rating of the record.
     *
     * @return the rating of the record
     */
    public float getRating() {
        return this.statistics.get(StatType.RATING);
    }

    /**
     * Sets the rating of the record.
     *
     * @param rating the rating of the record
     */
    public void setRating(float rating) {
        this.statistics.put(StatType.RATING, rating);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        Record record = (Record) object;
        return Objects.equals(this.id, record.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }
}
