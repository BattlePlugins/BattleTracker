package mc.alk.tracker.controllers;

import mc.alk.tracker.Tracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;


public class TrackerController {
	static Set<String> dontTrack = new HashSet<String>();
	static Set<String> dontAnnounce = new HashSet<String>();


	public static boolean dontTrack(Player target) {
		return dontTrack.contains(target.getName())	;
	}

	public static boolean dontTrack(String target) {
		return dontTrack.contains(target);
	}

	public static void stopTracking(final String playername,boolean now){
		if (now) {
			dontTrack.add(playername);
		} else {
			Bukkit.getScheduler().scheduleSyncDelayedTask(Tracker.getSelf(), new Runnable(){
				public void run() {
					dontTrack.add(playername);
				}
			});
		}
	}
	public static void resumeTracking(final String playername,boolean now){
		if (now) {
			dontTrack.remove(playername);
		} else {
			Bukkit.getScheduler().scheduleSyncDelayedTask(Tracker.getSelf(), new Runnable(){
				public void run() {
					dontTrack.remove(playername);
				}
			});
		}
	}
	public static void stopAnnouncing(final String playername,boolean now){
		if (now) {
			dontAnnounce.add(playername);
		} else {
			Bukkit.getScheduler().scheduleSyncDelayedTask(Tracker.getSelf(), new Runnable(){
				public void run() {
					dontAnnounce.add(playername);
				}
			});
		}
	}

	public static void resumeAnnouncing(final String playername,boolean now){
		if (now) {
			dontAnnounce.remove(playername);
		} else {
			Bukkit.getScheduler().scheduleSyncDelayedTask(Tracker.getSelf(), new Runnable(){
				public void run() {
					dontAnnounce.remove(playername);
				}
			});
		}
	}

	public static void stopTracking(String playername){stopTracking(playername,false);}
	public static void resumeTracking(String playername){resumeTracking(playername,false);}
	public static void stopAnnouncing(String playername){stopAnnouncing(playername,false);}
	public static void resumeAnnouncing(String playername){resumeAnnouncing(playername,false);}


	public static void stopAnnouncing(Collection<Player> players) {for (Player p:players)dontAnnounce.add(p.getName());}
	public static void resumeAnnouncing(Collection<Player> players) {for (Player p:players)dontAnnounce.remove(p.getName());}
	public static void stopTracking(Collection<Player> players) {for (Player p:players){dontTrack.add(p.getName());}}
	public static void resumeTracking(Collection<Player> players) {for (Player p:players)dontTrack.remove(p.getName());}
}
