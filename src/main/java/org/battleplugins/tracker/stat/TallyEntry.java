package org.battleplugins.tracker.stat;

import org.bukkit.OfflinePlayer;

import java.time.Instant;
import java.util.UUID;

/**
 * A tally entry storing information about two players.
 *
 * @param id1 the id of the victor in the tally
 * @param id2 the id of the loser in the tally
 * @param tie whether the tally resulted in a tie
 * @param timestamp the timestamp when this tally was recorded
 */
public record TallyEntry(UUID id1, UUID id2, boolean tie, Instant timestamp) {

    public TallyEntry(OfflinePlayer player1, OfflinePlayer player2, boolean tie, Instant timestamp) {
        this(player1.getUniqueId(), player2.getUniqueId(), tie, timestamp);
    }
}
