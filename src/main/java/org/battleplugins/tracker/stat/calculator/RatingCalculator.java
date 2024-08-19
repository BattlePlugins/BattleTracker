package org.battleplugins.tracker.stat.calculator;

import org.battleplugins.tracker.stat.Record;

import java.util.function.Predicate;

/**
 * Interface for rating calculators
 */
public interface RatingCalculator {

    /**
     * Returns the name of the calculator.
     *
     * @return the name of the calculator
     */
    String getName();

    /**
     * Returns the default rating.
     *
     * @return the default rating
     */
    float getDefaultRating();

    /**
     * Updates the rating of the killer and the killed players.
     *
     * @param killer the killer's Record
     * @param killed the player killed's Record
     * @param tie if the final result is a tie
     */
    void updateRating(Record killer, Record killed, boolean tie);

    /**
     * Updates the rating of the killer and the killed players.
     *
     * @param killer the killer's Record
     * @param killed an array of the players killed's Record
     * @param tie if the final result is a tie
     */
    void updateRating(Record killer, Record[] killed, boolean tie);

    /**
     * Updates the rating of the killer and the killed players.
     *
     * @param killer the killer's Record
     * @param killed an array of the players killed's Record
     * @param tie if the final result is a tie
     */
    void updateRating(Record[] killer, Record[] killed, boolean tie);

    /**
     * Updates the rating of the players in the array.
     *
     * @param records the array of players to update
     * @param tie if the final result is a tie
     */
    void updateRating(Record[] records, boolean tie);
}
