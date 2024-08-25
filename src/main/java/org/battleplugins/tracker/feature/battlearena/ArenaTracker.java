package org.battleplugins.tracker.feature.battlearena;

import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.SqlTracker;
import org.battleplugins.tracker.TrackedDataType;
import org.battleplugins.tracker.sql.TrackerSqlSerializer;
import org.battleplugins.tracker.stat.StatType;
import org.battleplugins.tracker.stat.calculator.RatingCalculator;

import java.util.List;
import java.util.Set;

public class ArenaTracker extends SqlTracker {
    public static StatType WINS = StatType.create("wins", "Wins", true);
    public static StatType LOSSES = StatType.create("losses", "Losses", true);

    public ArenaTracker(BattleTracker plugin, String name, RatingCalculator calculator, Set<TrackedDataType> trackedData, List<String> disabledWorlds) {
        super(plugin, name, calculator, trackedData, disabledWorlds);
    }

    @Override
    protected TrackerSqlSerializer createSerializer() {
        List<StatType> generalStats = List.of(
                WINS, LOSSES,
                StatType.KILLS, StatType.DEATHS, StatType.TIES,
                StatType.MAX_STREAK, StatType.MAX_RANKING, StatType.RATING,
                StatType.MAX_RATING, StatType.MAX_KD_RATIO
        );

        return new TrackerSqlSerializer(
                this,
                generalStats,
                List.of(WINS, LOSSES, StatType.TIES)
        );
    }
}
