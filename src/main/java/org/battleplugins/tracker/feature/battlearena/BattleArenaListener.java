package org.battleplugins.tracker.feature.battlearena;

import com.google.common.base.Supplier;
import org.battleplugins.arena.Arena;
import org.battleplugins.arena.ArenaPlayer;
import org.battleplugins.arena.competition.JoinResult;
import org.battleplugins.arena.competition.LiveCompetition;
import org.battleplugins.arena.competition.phase.CompetitionPhaseType;
import org.battleplugins.arena.event.BattleArenaPostInitializeEvent;
import org.battleplugins.arena.event.BattleArenaReloadEvent;
import org.battleplugins.arena.event.BattleArenaReloadedEvent;
import org.battleplugins.arena.event.arena.ArenaCreateExecutorEvent;
import org.battleplugins.arena.event.arena.ArenaDrawEvent;
import org.battleplugins.arena.event.arena.ArenaInitializeEvent;
import org.battleplugins.arena.event.arena.ArenaVictoryEvent;
import org.battleplugins.arena.event.player.ArenaKillEvent;
import org.battleplugins.arena.event.player.ArenaLeaveEvent;
import org.battleplugins.arena.event.player.ArenaPreJoinEvent;
import org.battleplugins.arena.event.player.ArenaStatChangeEvent;
import org.battleplugins.arena.options.types.BooleanArenaOption;
import org.battleplugins.arena.stat.ArenaStat;
import org.battleplugins.arena.stat.ArenaStats;
import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.SqlTracker;
import org.battleplugins.tracker.TrackedDataType;
import org.battleplugins.tracker.Tracker;
import org.battleplugins.tracker.stat.Record;
import org.battleplugins.tracker.stat.StatType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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

    private boolean reloaded;

    public BattleArenaListener(BattleTracker battleTracker) {
        this.battleTracker = battleTracker;
    }

    @EventHandler
    public void onBattleArenaPostInitialize(BattleArenaPostInitializeEvent event) {
        for (ArenaStat<?> stat : ArenaStats.values()) {
            String statKey = stat.getKey().replace("-", "_");
            // There may be some crossover with stats (i.e. kills, deaths, etc.), so ensure
            // we don't have any duplicates before proceeding
            if (StatType.get(statKey) != null) {
                continue;
            }

            StatType.create(statKey, stat.getName(), true);
        }
    }

    @EventHandler
    public void onBattleArenaReload(BattleArenaReloadEvent event) {
        this.reloaded = true;

        for (Arena arena : event.getBattleArena().getArenas()) {
            Tracker tracker = this.battleTracker.getTracker(arena.getName().toLowerCase(Locale.ROOT));
            if (tracker == null) {
                continue;
            }

            tracker.saveAll().whenComplete((aVoid, e) -> {
                if (e != null) {
                    this.battleTracker.error("Failed to save tracker data for Arena: {}.", arena.getName());
                    e.printStackTrace();
                }

                tracker.destroy();
            });

            this.battleTracker.unregisterTracker(tracker);
        }
    }

    @EventHandler
    public void onBattleArenaReloaded(BattleArenaReloadedEvent event) {
        for (Arena arena : event.getBattleArena().getArenas()) {
            Tracker tracker = this.battleTracker.getTracker(arena.getName().toLowerCase(Locale.ROOT));
            if (tracker == null) {
                continue;
            }

            // Ensure records are created for online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                tracker.getOrCreateRecord((OfflinePlayer) player).whenComplete((record, e) -> {
                    if (tracker instanceof SqlTracker sqlTracker) {
                        sqlTracker.getRecords().lock(player.getUniqueId());
                    }
                });
            }
        }
    }

    @EventHandler
    public void onArenaInitialize(ArenaInitializeEvent event) {
        Arena arena = event.getArena();

        // Statistic tracking is disabled
        if (!arena.option(BattleArenaHandler.TRACK_STATISTICS_OPTION).map(BooleanArenaOption::isEnabled).orElse(true)) {
            return;
        }

        Consumer<Tracker> pendingExecutor = this.pendingExecutors.remove(arena);
        Supplier<Tracker> supplier = () -> {
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
        };

        this.battleTracker.registerTracker(
                event.getArena().getName().toLowerCase(Locale.ROOT),
                supplier
        );

        // This is kind of silly, but in the event that the plugin is reloaded,
        // we want to ensure our tracker instance gets updated.
        if (this.reloaded) {
            this.battleTracker.registerTracker(supplier.get());
        }

        this.battleTracker.info("Enabled tracking for arena: {}.", arena.getName());
    }

    @EventHandler
    public void onCreateExecutor(ArenaCreateExecutorEvent event) {
        Arena arena = event.getArena();

        // Statistic tracking is disabled
        if (!arena.option(BattleArenaHandler.TRACK_STATISTICS_OPTION).map(BooleanArenaOption::isEnabled).orElse(true)) {
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

    @EventHandler
    public void onStatChange(ArenaStatChangeEvent<?> event) {
        if (!(event.getOldValue() instanceof Number) || !(event.getNewValue() instanceof Number)) {
            return;
        }

        Tracker tracker = this.battleTracker.getTracker(event.getArena().getName());
        if (tracker == null) {
            return;
        }

        if (!(event.getStatHolder() instanceof ArenaPlayer arenaPlayer)) {
            return;
        }

        Record record = tracker.getRecord(arenaPlayer.getPlayer());
        if (!record.isTracking()) {
            return;
        }

        List<BattleArenaHandler.StatInfo> trackedStats = BattleArenaHandler.getTrackedStats(event.getArena());
        for (BattleArenaHandler.StatInfo info : trackedStats) {
            if (!info.stat().equals(event.getStat())) {
                continue;
            }

            Number oldValue = (Number) event.getOldValue();
            Number newValue = (Number) event.getNewValue();

            switch (info.type()) {
                case ADD -> {
                    float delta = newValue.floatValue() - oldValue.floatValue();
                    record.setValue(info.statType(), record.getStat(info.statType()) + delta);
                }
                case REMOVE -> {
                    float delta = oldValue.floatValue() - newValue.floatValue();
                    record.setValue(info.statType(), record.getStat(info.statType()) - delta);
                }
            }
        }
    }
}
