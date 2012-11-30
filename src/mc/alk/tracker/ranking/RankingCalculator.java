package mc.alk.tracker.ranking;

import java.util.Collection;

import mc.alk.tracker.objects.Stat;

public interface RankingCalculator {

	/**
	 * What is this rating called
	 * @return name of rating (like elo)
	 */
	public String getName();

	/**
	 * Set the default rating
	 * @param initialRating
	 */
	public void setDefaultRating(float initialRanking);
	/**
	 * Get the default rating
	 * @return
	 */
	public float getDefaultRating();

	/**
	 * Change the ratings of stat1 and stat2
	 * @param stat1
	 * @param stat2
	 * @param tie
	 */
	public void changeRatings(Stat stat1, Stat stat2, boolean tie);

	/**
	 * Change the ratings of a group of stats
	 * @param stat1
	 * @param stats
	 * @param tie
	 */
	public void changeRatings(Stat stat1, Collection<Stat> stats, boolean tie);
}
