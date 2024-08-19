package org.battleplugins.tracker;

import org.battleplugins.tracker.feature.TrackerFeature;
import org.battleplugins.tracker.stat.Record;
import org.battleplugins.tracker.stat.StatType;
import org.battleplugins.tracker.stat.TallyContext;
import org.battleplugins.tracker.stat.TallyEntry;
import org.battleplugins.tracker.stat.VersusTally;
import org.battleplugins.tracker.stat.calculator.RatingCalculator;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Main interface used for tracking stats.
 */
public interface Tracker {

    /**
     * Returns the name of the tracker.
     *
     * @return the name of the tracker
     */
    String getName();

    /**
     * Returns the record for the given OfflinePlayer.
     *
     * @param player the OfflinePlayer to get the record from
     * @return the record for the given OfflinePlayer
     */
    CompletableFuture<@Nullable Record> getRecord(OfflinePlayer player);

    /**
     * Returns the top records for the specified stat type, ordered by
     * the specified stat type.
     *
     * @param limit the limit of records to get
     * @param orderBy the stat type to order by
     * @return the top records for the specified stat type
     */
    CompletableFuture<List<Record>> getTopRecords(int limit, StatType orderBy);

    /**
     * Returns the record for the given Player.
     *
     * @param player the Player to get the record from
     * @return the record for the given Player
     */
    Record getRecord(Player player);

    /**
     * Adds a record to the tracker.
     *
     * @param uuid the uuid to add the record for
     * @param record the record to add
     */
    void addRecord(UUID uuid, Record record);

    /**
     * Returns the versus tally for the given OfflinePlayers.
     *
     * @param player1 the first OfflinePlayer to get the versus tally from
     * @param player2 the second OfflinePlayer to get the versus tally from
     * @return the versus tally for the given OfflinePlayers
     */
    CompletableFuture<VersusTally> getVersusTally(OfflinePlayer player1, OfflinePlayer player2);

    /**
     * Creates a new versus tally for the given OfflinePlayers.
     *
     * @param player1 the first OfflinePlayer in the versus tally
     * @param player2 the second OfflinePlayer in the versus tally
     * @return the new versus tally for the given OfflinePlayers
     */
    VersusTally createNewVersusTally(OfflinePlayer player1, OfflinePlayer player2);

    /**
     * Returns or creates a versus tally for the given OfflinePlayers.
     *
     * @param player1 the first OfflinePlayer to get the versus tally from
     * @param player2 the second OfflinePlayer to get the versus tally from
     * @return the versus tally for the given OfflinePlayers or create a new one
     */
    CompletableFuture<VersusTally> getOrCreateVersusTally(OfflinePlayer player1, OfflinePlayer player2);

    /**
     * Modifies the tally with the given context.
     *
     * @param tally the tally to modify
     * @param context the context to modify the tally with
     */
    void modifyTally(VersusTally tally, Consumer<TallyContext> context);

    /**
     * Records a tally entry.
     *
     * @param entry the tally entry to record
     */
    void recordTallyEntry(TallyEntry entry);

    /**
     * Returns a list of tally entries for the given player.
     *
     * @param uuid the UUID of the player to get the tally entries for
     * @param includeLosses if losses should be included in the returned list
     * @return a list of tally entries for the given player
     */
    CompletableFuture<List<TallyEntry>> getTallyEntries(UUID uuid, boolean includeLosses);

    /**
     * Increments a value with the given stat type.
     *
     * @param statType the stat type to increment the value for
     * @param player the player to increment the value for
     */
    default void incrementValue(StatType statType, Player player) {
        Record record = this.getRecord(player);
        if (record == null) {
            return;
        }

        record.setValue(statType, record.getStat(statType) + 1);
    }

    /**
     * Decrements a value with the given stat type.
     *
     * @param statType the stat type to decrement the value for
     * @param player the player to decrement the value for
     */
    default void decrementValue(StatType statType, Player player) {
        Record record = this.getRecord(player);
        if (record == null) {
            return;
        }

        record.setValue(statType, record.getStat(statType) - 1);
    }

    /**
     * Sets a value with the given stat type.
     *
     * @param statType the stat type to set the value for
     * @param value the value to set
     * @param player the player to set the value for
     */
    void setValue(StatType statType, float value, OfflinePlayer player);

    /**
     * Sets the rating for the specified players.
     *
     * @param killer the player to increment the rating for
     * @param loser the player to decrement the rating for
     * @param tie if the end result was a tie
     */
    void updateRating(Player killer, Player loser, boolean tie);

    /**
     * Enables tracking for the specified player.
     *
     * @param player the player to enable tracking for
     */
    default void enableTracking(Player player) {
        Record record = this.getRecord(player);
        if (record == null) {
            return;
        }

        record.setTracking(true);
    }

    /**
     * Disables tracking for the specified player.
     *
     * @param player the player to disable tracking for
     */
    default void disableTracking(Player player) {
        Record record = this.getRecord(player);
        if (record == null) {
            return;
        }

        record.setTracking(false);
    }

    /**
     * Adds a record for the specified player to the tracker from the
     * default SQL columns.
     *
     * @param player the player to create the record for
     * @return the new record created
     */
    Record createNewRecord(OfflinePlayer player);

    /**
     * Adds a record for the specified player to the tracker.
     *
     * @param player the player to create the record for
     * @param record the record to add
     * @return the new record created
     */
    Record createNewRecord(OfflinePlayer player, Record record);

    /**
     * Returns the record for the given player or creates
     * a new one of one was unable to be found.
     *
     * @param player the player to get/create the record for
     * @return the record for the given player or create a new one
     */
    default CompletableFuture<Record> getOrCreateRecord(OfflinePlayer player) {
        return this.getRecord(player).thenApply(record -> {
            if (record == null) {
                return this.createNewRecord(player);
            }

            return record;
        });
    }

    /**
     * Returns the record for the given player or creates
     * a new one of one was unable to be found.
     *
     * @param player the player to get/create the record for
     * @return the record for the given player or create a new one
     */
    default Record getOrCreateRecord(Player player) {
        Record record = this.getRecord(player);
        if (record == null) {
            return this.createNewRecord(player);
        }

        return record;
    }

    /**
     * Removes the record for the specified player.
     *
     * @param player the player to remove the record for
     */
    void removeRecord(OfflinePlayer player);

    /**
     * Returns the {@link TrackerFeature} from the given class.
     *
     * @param feature the class of the feature
     * @return the feature from the given class
     */
    <T extends TrackerFeature> Optional<T> feature(Class<T> feature);

    /**
     * Returns if the tracker has the specified feature.
     *
     * @param feature the class of the feature
     * @return if the tracker has the specified feature
     */
    boolean hasFeature(Class<? extends TrackerFeature> feature);

    /**
     * Returns the {@link TrackerFeature} from the given class.
     *
     * @param feature the class of the feature
     * @return the feature from the given class
     */
    @Nullable
    <T extends TrackerFeature> T getFeature(Class<T> feature);

    /**
     * Registers a feature to the tracker.
     *
     * @param feature the feature to register
     */
    <T extends TrackerFeature> void registerFeature(T feature);

    /**
     * Returns if the tracker tracks the specified data type.
     *
     * @param type the data type to check
     * @return if the tracker tracks the specified data type
     */
    default boolean tracksData(TrackedDataType type) {
        return this.getTrackedData().contains(type);
    }

    /**
     * Returns the {@link TrackedDataType} tracked by this
     * tracker.
     *
     * @return the data tracked by this tracker
     */
    Set<TrackedDataType> getTrackedData();

    /**
     * Returns a list of disabled worlds for this tracker.
     *
     * @return a list of disabled worlds for this tracker
     */
    List<String> getDisabledWorlds();

    /**
     * Returns the rating calculator for this tracker.
     *
     * @return the rating calculator for this tracker
     */
    RatingCalculator getRatingCalculator();

    /**
     * Saves the records to the database for the specified player.
     *
     * @param player the player to save records for
     */
    CompletableFuture<Void> save(OfflinePlayer player);

    /**
     * Saves the records for all the players in the cache.
     */
    CompletableFuture<Void> saveAll();

    /**
     * Flushes cached data in the tracker.
     * <p>
     * This method provides the option to aggressively
     * flush the cache, which will remove all cached objects
     * that do not have a lock set on them.
     * <p>
     * NOTE: Ensure you have run the {@link #saveAll()} method
     * as this method will not clear unsaved entries.
     *
     * @param aggressive if the flush should be aggressive.
     */
    void flush(boolean aggressive);

    /**
     * Destroys the tracker.
     */
    void destroy();
}
