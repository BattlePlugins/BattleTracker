package org.battleplugins.tracker.feature.battlearena;

import org.battleplugins.arena.Arena;
import org.battleplugins.arena.BattleArena;
import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.SqlTracker;
import org.battleplugins.tracker.TrackedDataType;
import org.battleplugins.tracker.sql.TrackerSqlSerializer;
import org.battleplugins.tracker.stat.StatType;
import org.battleplugins.tracker.stat.calculator.RatingCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ArenaTracker extends SqlTracker {
    public static StatType WINS = StatType.create("wins", "Wins", true);
    public static StatType LOSSES = StatType.create("losses", "Losses", true);

    private List<StatType> additionalStats;

    public ArenaTracker(BattleTracker plugin, String name, RatingCalculator calculator, Set<TrackedDataType> trackedData, List<String> disabledWorlds) {
        super(plugin, name, calculator, trackedData, disabledWorlds);
    }

    @Override
    protected TrackerSqlSerializer createSerializer() {
        Arena arena = BattleArena.getInstance().getArena(this.name);
        if (arena == null) {
            throw new IllegalStateException("Arena " + this.name + " does not exist!");
        }

        List<StatType> additionalStats = BattleArenaHandler.getTrackedStats(arena)
                .stream()
                .map(BattleArenaHandler.StatInfo::statType)
                .toList();

        if (!additionalStats.isEmpty()) {
            this.additionalStats = additionalStats;
        }

        List<StatType> generalStats = List.of(
                WINS, LOSSES,
                StatType.KILLS, StatType.DEATHS, StatType.TIES,
                StatType.MAX_STREAK, StatType.MAX_RANKING, StatType.RATING,
                StatType.MAX_RATING, StatType.MAX_KD_RATIO
        );

        List<StatType> allStats = new ArrayList<>(additionalStats);
        allStats.addAll(generalStats);

        return new TrackerSqlSerializer(
                this,
                allStats.stream().distinct().toList(),
                List.of(WINS, LOSSES, StatType.TIES)
        );
    }

    @Override
    public List<StatType> getAdditionalStats() {
        return this.additionalStats == null ? List.of() : this.additionalStats;
    }
}
