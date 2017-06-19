package mc.alk.tracker.events;

import mc.alk.tracker.Tracker;
import mc.alk.tracker.TrackerInterface;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class TrackerEvent extends Event{
	private static final HandlerList handlers = new HandlerList();
	final TrackerInterface trackerInterface;

	public TrackerEvent(TrackerInterface trackerInterface){
		this.trackerInterface = trackerInterface;
	}

	/**
	 * Alias for getInterfaceName
	 * @return
	 */
	public String getDBName(){
		return trackerInterface.getInterfaceName();
	}

	/**
	 * Returns the name of the interface this event was called from
	 * @return
	 */
	public String getInterfaceName(){
		return trackerInterface.getInterfaceName();
	}

	public void callEvent(){
		Bukkit.getServer().getPluginManager().callEvent(this);
	}

	public void callSyncEvent(){
		final Event event = this;
		Bukkit.getScheduler().scheduleSyncDelayedTask(Tracker.getSelf(), new Runnable(){
			public void run() {
				Bukkit.getServer().getPluginManager().callEvent(event);
			}
		});
	}

	@Override
	public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
