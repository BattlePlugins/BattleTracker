package mc.alk.tracker.objects;

import mc.alk.tracker.TrackerInterface;
import mc.alk.battlebukkitlib.objects.Hologram;
import org.bukkit.ChatColor;
import org.bukkit.Location;

import java.util.List;

public class LeaderboardHologram extends Hologram
{
	private String leaderboardName;
	private int topAmount;
	private TrackerInterface trackerInterface;
	private StatType statType;

	public LeaderboardHologram(TrackerInterface trackerInterface,
			StatType statType, String leaderboardName, int topAmount,
			double distanceBetweenLines, Location location)
	{
		super(distanceBetweenLines, location, ChatColor.GREEN+ "Updating...");
		setLeaderboardName(leaderboardName);
		setTrackerInterface(trackerInterface);
		setStatType(statType);
		setTopAmount(topAmount);
		update();
	}

	public LeaderboardHologram(TrackerInterface trackerInterface,
			StatType statType, String leaderboardName, int topAmount,
			VerticalTextSpacing spacingType, Location location)
	{
		super(spacingType.spacing(), location, ChatColor.GREEN+ "Updating...");
		setLeaderboardName(leaderboardName);
		setTrackerInterface(trackerInterface);
		setStatType(statType);
		setTopAmount(topAmount);
		update();
	}

	public String getLeaderboardName()
	{
		return leaderboardName;
	}

	public TrackerInterface getTrackerInterface()
	{
		return trackerInterface;
	}

	public StatType getStatType()
	{
		return statType;
	}

	public int getTopAmount()
	{
		return topAmount;
	}

	public void setLeaderboardName(String name)
	{
		getLines().remove(getLeaderboardName());
		getLines().add(0, leaderboardName);
		this.leaderboardName = name;
	}

	public void setTrackerInterface(TrackerInterface trackerInterface)
	{
		this.trackerInterface = trackerInterface;
	}

	public void setTopAmount(int amount)
	{
		this.topAmount = amount;
	}

	public void setStatType(StatType type)
	{
		this.statType = type;
	}

	public void update()
	{
		getLines().clear();
		setLeaderboardName(getLeaderboardName());
		List<Stat> stats = getTrackerInterface().getTopX(getStatType(),
				getTopAmount());
		for (Stat stat : stats)
		{
			getLines().add(stat.getName() + " - " + stat.getWins());
		}
	}
}
