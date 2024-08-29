package org.battleplugins.tracker.stat;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a statistic type.
 */
public final class StatType {
    private static final Map<String, StatType> STAT_TYPES = new LinkedHashMap<>();

    public static StatType KILLS = new StatType("kills", "Kills", true);
    public static StatType DEATHS = new StatType("deaths", "Deaths", true);
    public static StatType TIES = new StatType("ties", "Ties", true);
    public static StatType STREAK = new StatType("streak", "Streak", false);
    public static StatType MAX_STREAK = new StatType("max_streak", "Max Streak", true);
    public static StatType RANKING = new StatType("ranking", "Ranking", false);
    public static StatType MAX_RANKING = new StatType("max_ranking", "Max Ranking", true);
    public static StatType RATING = new StatType("rating", "Rating", true);
    public static StatType MAX_RATING = new StatType("max_rating", "Max Rating", true);
    public static StatType KD_RATIO = new StatType("kd_ratio", "K/D Ratio", false);
    public static StatType MAX_KD_RATIO = new StatType("max_kd_ratio", "Max K/D Ratio", true);

    private final String key;
    private final String name;
    private final boolean tracked;

    StatType(String key, String name, boolean tracked) {
        this.key = key;
        this.name = name;
        this.tracked = tracked;

        STAT_TYPES.put(key, this);
    }

    public String getKey() {
        return this.key;
    }

    public String getName() {
        return this.name;
    }

    public boolean isTracked() {
        return this.tracked;
    }

    @Nullable
    public static StatType get(String name) {
        return STAT_TYPES.get(name);
    }

    public static StatType create(String key, String name, boolean tracked) {
        if (STAT_TYPES.containsKey(key)) {
            throw new IllegalArgumentException("Stat type with key " + key + " already exists!");
        }

        return new StatType(key, name, tracked);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        StatType that = (StatType) o;
        return this.key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return this.key.hashCode();
    }

    public static List<StatType> values() {
        // Need to ensure the order is retained
        return STAT_TYPES.values().stream().toList();
    }
}
