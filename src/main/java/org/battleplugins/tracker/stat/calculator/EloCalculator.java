package org.battleplugins.tracker.stat.calculator;

import org.battleplugins.tracker.BattleTrackerConfig;
import org.battleplugins.tracker.stat.Record;

/**
 * Class for calculating for elo.
 */
public class EloCalculator implements RatingCalculator {
    private final BattleTrackerConfig.Elo elo;

    public EloCalculator(BattleTrackerConfig.Elo elo) {
        this.elo = elo;
    }

    @Override
    public String getName() {
        return "elo";
    }

    @Override
    public float getDefaultRating() {
        return this.elo.defaultElo();
    }

    @Override
    public void updateRating(Record killer, Record killed, boolean tie) {
        float result = tie ? 0.5f : 1.0f;
        float eloChange = this.getEloChange(killer, killed, result);
        if (killer.isTracking()) {
            killer.setRating(killer.getRating() + eloChange);
        }
        if (killed.isTracking()) {
            killed.setRating(killed.getRating() - eloChange);
        }
    }

    @Override
    public void updateRating(Record killer, Record[] killed, boolean tie) {
        float result = tie ? 0.5f : 1.0f;
        double eloWinner = 0;
        double dampening = killed.length == 1 ? 1 : killed.length / 2.0D;
        for (Record record : killed) {
            double eloChange = this.getEloChange(killer, record, result) / dampening;
            eloWinner += eloChange;
            if (record.isTracking()) {
                record.setRating(record.getRating() - (float) eloChange);
            }
        }

        if (killer.isTracking()) {
            killer.setRating(killer.getRating() + (float) eloWinner);
        }
    }

    @Override
    public void updateRating(Record[] killer, Record[] killed, boolean tie) {
        float resultKiller = tie ? 0.5f : 1.0f;
        float resultKilled = tie ? 0.5f : 0.0f;

        double dampening = killed.length == 1 ? 1 : killed.length / 2.0D;

        // Update ratings for the killers
        for (Record killerRecord : killer) {
            double totalEloChange = 0;
            for (Record killedRecord : killed) {
                double eloChange = this.getEloChange(killerRecord, killedRecord, resultKiller) / dampening;
                totalEloChange += eloChange;
            }

            if (killerRecord.isTracking()) {
                killerRecord.setRating(killerRecord.getRating() + (float) totalEloChange);
            }
        }

        // Update ratings for the killed
        for (Record killedRecord : killed) {
            double totalEloChange = 0;
            for (Record killerRecord : killer) {
                double eloChange = this.getEloChange(killedRecord, killerRecord, resultKilled) / dampening;
                totalEloChange += eloChange;
            }

            if (killedRecord.isTracking()) {
                killedRecord.setRating(killedRecord.getRating() + (float) totalEloChange);
            }
        }
    }

    @Override
    public void updateRating(Record[] records, boolean tie) {
        float result = tie ? 0.5f : 1.0f;

        // Dampening factor for multiple players
        double dampening = records.length == 1 ? 1 : records.length / 2.0D;

        for (int i = 0; i < records.length; i++) {
            Record player = records[i];
            double totalEloChange = 0;

            for (int j = 0; j < records.length; j++) {
                if (i != j) { // Ensure we don't compare the player to themselves
                    Record opponent = records[j];

                    // Only calculate Elo change if they are not friendly to each other
                    double eloChange = this.getEloChange(player, opponent, result) / dampening;
                    totalEloChange += eloChange;
                }
            }

            // Update the player's rating if they are being tracked
            if (player.isTracking()) {
                player.setRating(player.getRating() + (float) totalEloChange);
            }
        }
    }

    private float getEloChange(Record killer, Record killed, float result) {
        float di = killed.getRating() - killer.getRating();

        float expected = (float) (1f / (1 + Math.pow(10, di / this.elo.spread())));
        return getK(killer.getRating()) * (result - expected);
    }

    /**
     * Returns the 'k' value for elo calculations
     * <p>
     * <a href="https://en.wikipedia.org/wiki/Elo_rating_system#Most_accurate_K-factor">...</a>
     *
     * @param elo the elo to take into consideration
     * @return the 'k' value for elo calculations
     */
    public static int getK(float elo) {
        if (elo < 1600) {
            return 50;
        } else if (elo < 1800) {
            return 35;
        } else if (elo < 2000) {
            return 20;
        } else if (elo < 2500) {
            return 10;
        }
        return 6;
    }
}
