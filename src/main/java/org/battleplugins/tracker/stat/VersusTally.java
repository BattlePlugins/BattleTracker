package org.battleplugins.tracker.stat;

import org.battleplugins.tracker.Tracker;
import org.bukkit.OfflinePlayer;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A tally storing versus information about two players.
 *
 * @param tracker the tracker this tally is for
 * @param id1 the first player's UUID
 * @param id2 the second player's UUID
 * @param statistics the statistics of the two players
 */
public record VersusTally(Tracker tracker, UUID id1, UUID id2, Map<StatType, Float> statistics) {

    public VersusTally(Tracker tracker, OfflinePlayer player1, OfflinePlayer player2, Map<StatType, Float> statistics) {
        this(tracker, player1.getUniqueId(), player2.getUniqueId(), statistics);
    }

    public float getStat(StatType statType) {
        return this.statistics.getOrDefault(statType, 0.0F);
    }

    public boolean isTallyFor(UUID uuid1, UUID uuid2) {
        return (this.id1.equals(uuid1) && this.id2.equals(uuid2)) || (this.id1.equals(uuid2) && this.id2.equals(uuid1));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        VersusTally that = (VersusTally) object;
        return Objects.equals(this.id1, that.id1) && Objects.equals(this.id2, that.id2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id1, this.id2);
    }
}