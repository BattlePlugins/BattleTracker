package org.battleplugins.tracker.feature.battlearena;

import org.battleplugins.arena.Arena;
import org.battleplugins.arena.ArenaPlayer;
import org.battleplugins.arena.competition.JoinResult;
import org.battleplugins.arena.competition.LiveCompetition;
import org.battleplugins.arena.competition.phase.CompetitionPhaseType;
import org.battleplugins.arena.event.arena.ArenaCreateExecutorEvent;
import org.battleplugins.arena.event.arena.ArenaDrawEvent;
import org.battleplugins.arena.event.arena.ArenaInitializeEvent;
import org.battleplugins.arena.event.arena.ArenaVictoryEvent;
import org.battleplugins.arena.event.player.ArenaKillEvent;
import org.battleplugins.arena.event.player.ArenaLeaveEvent;
import org.battleplugins.arena.event.player.ArenaPreJoinEvent;
import org.battleplugins.arena.options.types.BooleanArenaOption;
import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.TrackedDataType;
import org.battleplugins.tracker.Tracker;
import org.battleplugins.tracker.stat.Record;
import org.battleplugins.tracker.stat.StatType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class BattleArenaListener implements Listener {
    private final BattleTracker battleTracker;
    private final Map<Arena, Consumer<Tracker>> pendingExecutors = new HashMap<>();

    public BattleArenaListener(BattleTracker battleTracker) {
        this.battleTracker = battleTracker;
    }

    @EventHandler
    public void onArenaInitialize(ArenaInitializeEvent event) {
        Arena arena = event.getArena();

        // Statistic tracking is disabled
        if (!arena.option(BattleArenaHandler.TRACK_STATISTICS).map(BooleanArenaOption::isEnabled).orElse(true)) {
            return;
        }

        Consumer<Tracker> pendingExecutor = this.pendingExecutors.remove(arena);
        this.battleTracker.registerTracker(
                event.getArena().getName().toLowerCase(Locale.ROOT),
                () -> {
                    Tracker tracker = new ArenaTracker(
                            this.battleTracker,
                            arena.getName(),
                            this.battleTracker.getCalculator("elo"),
                            Set.of(TrackedDataType.PVP),
                            List.of()
                    );

                    if (pendingExecutor != null) {
                        pendingExecutor.accept(tracker);
                    }

                    return tracker;
                }
        );

        this.battleTracker.info("Enabled tracking for arena: {}.", arena.getName());
    }

    @EventHandler
    public void onCreateExecutor(ArenaCreateExecutorEvent event) {
        Arena arena = event.getArena();

        // Statistic tracking is disabled
        if (!arena.option(BattleArenaHandler.TRACK_STATISTICS).map(BooleanArenaOption::isEnabled).orElse(true)) {
            return;
        }

        this.pendingExecutors.put(arena, tracker -> event.registerSubExecutor(new TrackerSubExecutor(arena, tracker)));
    }

    @EventHandler
    public void onArenaJoin(ArenaPreJoinEvent event) {
        Tracker tracker = this.battleTracker.getTracker(event.getArena().getName());
        if (tracker == null) {
            return;
        }

        if (this.battleTracker.getCombatLog().isInCombat(event.getPlayer())) {
            event.setResult(new JoinResult(false, BattleArenaHandler.convertMessage("combat-log-cannot-join-arena")));
        }
    }

    @EventHandler
    public void onArenaLeave(ArenaLeaveEvent event) {
        // If player leaves or quits, we want to decrement their elo
        if (event.getCause() != ArenaLeaveEvent.Cause.COMMAND && event.getCause() != ArenaLeaveEvent.Cause.DISCONNECT) {
            return;
        }

        // Player is not in an in-game phase, so we don't want to decrement their elo
        if (!CompetitionPhaseType.INGAME.equals(event.getCompetition().getPhaseManager().getCurrentPhase().getType())) {
            return;
        }

        Tracker tracker = this.battleTracker.getTracker(event.getArena().getName());
        if (tracker == null) {
            return;
        }

        Player player = event.getPlayer();
        Record record = tracker.getRecord(player);
        if (!record.isTracking()) {
            return;
        }

        // Update rating
        Record[] records = event.getCompetition().getPlayers()
                .stream()
                .filter(p -> !p.equals(event.getArenaPlayer()))
                .map(p -> tracker.getRecord(p.getPlayer()))
                .toArray(Record[]::new);

        tracker.getRatingCalculator().updateRating(records, new Record[] { record }, false);
        tracker.setValue(StatType.RATING, record.getRating(), player);
    }

    @EventHandler
    public void onArenaKill(ArenaKillEvent event) {
        Tracker tracker = this.battleTracker.getTracker(event.getArena().getName());
        if (tracker == null) {
            return;
        }

        Player killer = event.getKiller().getPlayer();
        Player killed = event.getKilled().getPlayer();

        Record killerRecord = tracker.getRecord(killer);
        Record killedRecord = tracker.getRecord(killed);

        if (killerRecord.isTracking()) {
            killerRecord.incrementValue(StatType.KILLS);
        }

        if (killedRecord.isTracking()) {
            killedRecord.incrementValue(StatType.DEATHS);
        }

        // Update ratios
        tracker.setValue(StatType.KD_RATIO, killerRecord.getStat(StatType.KILLS) / Math.max(1, killerRecord.getStat(StatType.DEATHS)), killer);
        tracker.setValue(StatType.KD_RATIO, killedRecord.getStat(StatType.KILLS) / Math.max(1, killedRecord.getStat(StatType.DEATHS)), killed);

        float killerKdr = killerRecord.getStat(StatType.KD_RATIO);
        float killerMaxKdr = killerRecord.getStat(StatType.MAX_KD_RATIO);

        if (killerKdr > killerMaxKdr) {
            tracker.setValue(StatType.MAX_KD_RATIO, killerKdr, killer);
        }

        tracker.setValue(StatType.STREAK, 0, killed);
        tracker.incrementValue(StatType.STREAK, killer);

        float killerStreak = killerRecord.getStat(StatType.STREAK);
        float killerMaxStreak = killerRecord.getStat(StatType.MAX_STREAK);

        if (killerStreak > killerMaxStreak) {
            tracker.setValue(StatType.MAX_STREAK, killerStreak, killer);
        }
    }

    @EventHandler
    public void onArenaVictory(ArenaVictoryEvent event) {
        // Development note: ArenaVictoryEvent will always be called in conjunction
        // with the ArenaLoseEvent, so we can process all our logic here

        if (!(event.getCompetition() instanceof LiveCompetition<?> liveCompetition)) {
            return;
        }

        Tracker tracker = this.battleTracker.getTracker(event.getArena().getName());
        if (tracker == null) {
            return;
        }

        Record[] victorRecords = event.getVictors()
                .stream()
                .map(player -> tracker.getRecord(player.getPlayer()))
                .toArray(Record[]::new);

        Set<ArenaPlayer> losers = liveCompetition.getPlayers()
                .stream()
                .filter(p -> !event.getVictors().contains(p))
                .collect(Collectors.toSet());

        Record[] loserRecords = losers.stream()
                .map(player -> tracker.getRecord(player.getPlayer()))
                .toArray(Record[]::new);

        // Update ratings
        tracker.getRatingCalculator().updateRating(victorRecords, loserRecords, false);

        for (ArenaPlayer victor : event.getVictors()) {
            Player victorPlayer = victor.getPlayer();
            Record victorRecord = tracker.getRecord(victorPlayer);

            if (!victorRecord.isTracking()) {
                continue;
            }

            victorRecord.incrementValue(ArenaTracker.WINS);

            float victorRating = victorRecord.getRating();
            float victorMaxRating = victorRecord.getStat(StatType.MAX_RATING);

            tracker.setValue(StatType.RATING, victorRecord.getRating(), victorPlayer);

            if (victorRating > victorMaxRating) {
                tracker.setValue(StatType.MAX_RATING, victorRating, victorPlayer);
            }
        }

        for (ArenaPlayer loser : losers) {
            Player loserPlayer = loser.getPlayer();
            Record loserRecord = tracker.getRecord(loserPlayer);

            if (!loserRecord.isTracking()) {
                continue;
            }

            loserRecord.incrementValue(ArenaTracker.LOSSES);

            float loserRating = loserRecord.getRating();
            float loserMaxRating = loserRecord.getStat(StatType.MAX_RATING);

            tracker.setValue(StatType.RATING, loserRecord.getRating(), loserPlayer);

            if (loserRating > loserMaxRating) {
                tracker.setValue(StatType.MAX_RATING, loserRating, loserPlayer);
            }
        }
    }

    @EventHandler
    public void onDraw(ArenaDrawEvent event) {
        if (!(event.getCompetition() instanceof LiveCompetition<?> liveCompetition)) {
            return;
        }

        Tracker tracker = this.battleTracker.getTracker(event.getArena().getName());
        if (tracker == null) {
            return;
        }

        Record[] records = liveCompetition.getPlayers()
                .stream()
                .map(player -> tracker.getRecord(player.getPlayer()))
                .toArray(Record[]::new);

        // Update ratings
        tracker.getRatingCalculator().updateRating(records, true);

        for (ArenaPlayer player : liveCompetition.getPlayers()) {
            Player bukkitPlayer = player.getPlayer();
            Record record = tracker.getRecord(bukkitPlayer);

            if (!record.isTracking()) {
                continue;
            }

            record.incrementValue(StatType.TIES);

            float victorRating = record.getRating();
            float victorMaxRating = record.getStat(StatType.MAX_RATING);

            tracker.setValue(StatType.RATING, record.getRating(), bukkitPlayer);

            if (victorRating > victorMaxRating) {
                tracker.setValue(StatType.MAX_RATING, victorRating, bukkitPlayer);
            }
        }
    }
}