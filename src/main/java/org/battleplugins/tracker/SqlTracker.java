package org.battleplugins.tracker;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;
import org.battleplugins.tracker.event.TallyRecordEvent;
import org.battleplugins.tracker.feature.TrackerFeature;
import org.battleplugins.tracker.sql.DbCache;
import org.battleplugins.tracker.sql.TrackerSqlSerializer;
import org.battleplugins.tracker.stat.Record;
import org.battleplugins.tracker.stat.StatType;
import org.battleplugins.tracker.stat.TallyContext;
import org.battleplugins.tracker.stat.TallyEntry;
import org.battleplugins.tracker.stat.VersusTally;
import org.battleplugins.tracker.stat.calculator.RatingCalculator;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SqlTracker implements Tracker {
    private final BattleTracker plugin;
    private final String name;
    private final RatingCalculator calculator;
    private final Set<TrackedDataType> trackedData;
    private final List<String> disabledWorlds;
    private final TrackerSqlSerializer sqlSerializer;

    private final DbCache.MapCache<UUID, Record> records = DbCache.createMap();
    private final DbCache.SetCache<VersusTally> tallies = DbCache.createSet();
    private final DbCache.MultimapCache<UUID, TallyEntry> tallyEntries = DbCache.createMultimap();

    private final ClassToInstanceMap<TrackerFeature> features = MutableClassToInstanceMap.create();

    private long lastTopLoad = 0;

    public SqlTracker(BattleTracker plugin, String name, RatingCalculator calculator, Set<TrackedDataType> trackedData, List<String> disabledWorlds) {
        this(plugin, name, calculator, trackedData, disabledWorlds, null);
    }

    public SqlTracker(BattleTracker plugin, String name, RatingCalculator calculator, Set<TrackedDataType> trackedData, List<String> disabledWorlds, TrackerSqlSerializer sqlSerializer) {
        this.plugin = plugin;
        this.name = name;
        this.calculator = calculator;
        this.trackedData = trackedData;
        this.disabledWorlds = disabledWorlds;

        if (sqlSerializer == null) {
            sqlSerializer = this.createSerializer();
        }

        this.sqlSerializer = sqlSerializer;
    }

    protected TrackerSqlSerializer createSerializer() {
        return new TrackerSqlSerializer(this);
    }

    @Override
    public String getName() {
        return this.name;
    }

    public DbCache.MapCache<UUID, Record> getRecords() {
        return this.records;
    }

    @Override
    public CompletableFuture<@Nullable Record> getRecord(OfflinePlayer player) {
        return this.records.getOrLoad(player.getUniqueId(), this.sqlSerializer.loadRecord(player.getUniqueId())).exceptionally(e -> {
            this.plugin.error("Failed to load record for {}", player.getUniqueId(), e);
            return null;
        });
    }

    @Override
    public CompletableFuture<List<Record>> getTopRecords(int limit, StatType orderBy) {
        // Recompute this every minute
        if (System.currentTimeMillis() - this.lastTopLoad > 60000) {
            this.lastTopLoad = System.currentTimeMillis();
            return this.records.loadBulk(this.sqlSerializer.getTopRecords(limit, orderBy), Record::getId)
                    .thenApply(records -> records.stream().toList());
        }

        // Load from cache
        return CompletableFuture.completedFuture(this.records.keySet()
                .stream()
                .map(this.records::getCached)
                .sorted((r1, r2) -> Float.compare(r2.getStat(orderBy), r1.getStat(orderBy)))
                .limit(limit)
                .toList()
        );
    }

    @Override
    public Record getRecord(Player player) {
        // Online players will always have a record
        Record record = this.records.getCached(player.getUniqueId());
        if (record == null) {
            // Uh oh, there's a bug in BattleTracker!
            throw new RuntimeException("Failed to load record for " + player.getUniqueId() + ". This is a bug in the plugin and should be reported!");
        }

        return record;
    }

    @Override
    public void addRecord(UUID uuid, Record record) {
        this.records.put(uuid, record);
    }

    public DbCache.SetCache<VersusTally> getTallies() {
        return this.tallies;
    }

    @Override
    public CompletableFuture<VersusTally> getVersusTally(OfflinePlayer player1, OfflinePlayer player2) {
        return this.tallies.getOrLoad(tally -> tally.isTallyFor(player1.getUniqueId(), player2.getUniqueId()), this.sqlSerializer.loadVersusTally(player1.getUniqueId(), player2.getUniqueId())
                .exceptionally(e -> {
                    this.plugin.error("Failed to load tally entries for {} and {}", player1.getUniqueId(), player2.getUniqueId(), e);
                    return null;
        }));
    }

    @Override
    public VersusTally createNewVersusTally(OfflinePlayer player1, OfflinePlayer player2) {
        VersusTally versusTally = new VersusTally(this, player1, player2, new HashMap<>());
        this.tallies.add(versusTally);
        return versusTally;
    }

    @Override
    public CompletableFuture<VersusTally> getOrCreateVersusTally(OfflinePlayer player1, OfflinePlayer player2) {
        return this.getVersusTally(player1, player2).thenApply(tally -> {
            if (tally == null) {
                return this.createNewVersusTally(player1, player2);
            }

            return tally;
        });
    }

    @Override
    public void modifyTally(VersusTally tally, Consumer<TallyContext> context) {
        context.accept((statType, value) -> {
            tally.statistics().put(statType, value);
            SqlTracker.this.tallies.modify(tally);
        });
    }

    public DbCache.MultimapCache<UUID, TallyEntry> getTallyEntries() {
        return this.tallyEntries;
    }

    @Override
    public void recordTallyEntry(TallyEntry entry) {
        this.tallyEntries.put(entry.id1(), entry);
        this.tallyEntries.put(entry.id2(), entry);

        Record record1 = this.records.getCached(entry.id1());
        Record record2 = this.records.getCached(entry.id2());
        if (record1 == null || record2 == null) {
            this.plugin.warn("Failed to call tally entry event for {} and {} as one of the records was not found!", entry.id1(), entry.id2());
            return;
        }

        new TallyRecordEvent(this, record1, record2, entry.tie()).callEvent();
    }

    @Override
    public CompletableFuture<List<TallyEntry>> getTallyEntries(UUID uuid, boolean includeLosses) {
        return this.tallyEntries.getOrLoad(uuid, this.sqlSerializer.loadTallyEntries(uuid)).thenApply(entries -> {
            if (includeLosses) {
                return entries;
            }

            return entries.stream().filter(entry -> entry.id1().equals(uuid)).toList();
        }).exceptionally(e -> {
            this.plugin.error("Failed to load tally entries for {}", uuid, e);
            return List.of();
        });
    }

    @Override
    public void setValue(StatType statType, float value, OfflinePlayer player) {
        this.getOrCreateRecord(player).whenComplete((record, e) -> {
            if (record == null) {
                return;
            }

            record.setValue(statType, value);
        });
    }

    @Override
    public void updateRating(Player killer, Player loser, boolean tie) {
        Record killerRecord = this.getOrCreateRecord(killer);
        Record killedRecord = this.getOrCreateRecord(loser);
        this.calculator.updateRating(killerRecord, killedRecord, tie);

        float killerRating = killerRecord.getRating();
        float killerMaxRating = killerRecord.getStat(StatType.MAX_RATING);

        this.setValue(StatType.RATING, killerRecord.getRating(), killer);
        this.setValue(StatType.RATING, killedRecord.getRating(), loser);

        if (killerRating > killerMaxRating) {
            this.setValue(StatType.MAX_RATING, killerRating, killer);
        }

        if (tie) {
            this.incrementValue(StatType.TIES, killer);
            this.incrementValue(StatType.TIES, loser);
        }

        this.setValue(StatType.KD_RATIO, killerRecord.getStat(StatType.KILLS) / Math.max(1, killerRecord.getStat(StatType.DEATHS)), killer);
        this.setValue(StatType.KD_RATIO, killedRecord.getStat(StatType.KILLS) / Math.max(1, killedRecord.getStat(StatType.DEATHS)), loser);

        float killerKdr = killerRecord.getStat(StatType.KD_RATIO);
        float killerMaxKdr = killerRecord.getStat(StatType.MAX_KD_RATIO);

        if (killerKdr > killerMaxKdr) {
            this.setValue(StatType.MAX_KD_RATIO, killerKdr, killer);
        }

        this.setValue(StatType.STREAK, 0, loser);
        this.incrementValue(StatType.STREAK, killer);

        float killerStreak = killerRecord.getStat(StatType.STREAK);
        float killerMaxStreak = killerRecord.getStat(StatType.MAX_STREAK);

        if (killerStreak > killerMaxStreak) {
            this.setValue(StatType.MAX_STREAK, killerStreak, killer);
        }
    }

    @Override
    public Record createNewRecord(OfflinePlayer player) {
        Map<StatType, Float> columns = new HashMap<>();
        for (String column : this.sqlSerializer.getOverallColumns()) {
            columns.put(StatType.get(column), 0f);
        }

        Record record = new Record(this, player.getUniqueId(), player.getName(), columns);
        return this.createNewRecord(player, record);
    }

    @Override
    public Record createNewRecord(OfflinePlayer player, Record record) {
        record.setRating(this.calculator.getDefaultRating());

        this.records.put(player.getUniqueId(), record);
        return record;
    }

    @Override
    public void removeRecord(OfflinePlayer player) {
        this.records.remove(player.getUniqueId());
        this.sqlSerializer.removeRecord(player.getUniqueId());
    }

    @Override
    public <T extends TrackerFeature> Optional<T> feature(Class<T> feature) {
        return Optional.ofNullable(this.getFeature(feature));
    }

    @Override
    public boolean hasFeature(Class<? extends TrackerFeature> feature) {
        return this.features.containsKey(feature);
    }

    @Override
    public <T extends TrackerFeature> @Nullable T getFeature(Class<T> feature) {
        return this.features.getInstance(feature);
    }

    @Override
    public void registerFeature(TrackerFeature feature) {
        this.features.put(feature.getClass(), feature);

        if (feature.enabled()) {
            feature.onEnable(this.plugin, this);
        }
    }

    @Override
    public Set<TrackedDataType> getTrackedData() {
        return Set.copyOf(this.trackedData);
    }

    @Override
    public List<String> getDisabledWorlds() {
        return List.copyOf(this.disabledWorlds);
    }

    @Override
    public RatingCalculator getRatingCalculator() {
        return this.calculator;
    }

    @Override
    public CompletableFuture<Void> save(OfflinePlayer player) {
        return this.sqlSerializer.save(player.getUniqueId());
    }

    @Override
    public CompletableFuture<Void> saveAll() {
        return this.sqlSerializer.saveAll();
    }

    @Override
    public void flush(boolean aggressive) {
        for (UUID uuid : this.records.keySet()) {
            this.records.flush(uuid, aggressive);
        }

        this.tallies.flush(aggressive);
        for (UUID uuid : this.tallyEntries.keySet()) {
            this.tallyEntries.flush(uuid, aggressive);
        }
    }

    @Override
    public void destroy() {
        try {
            this.sqlSerializer.closeConnection(this.sqlSerializer.getConnection());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close connection!", e);
        }
    }
}
