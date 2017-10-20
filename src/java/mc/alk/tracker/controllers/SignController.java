package mc.alk.tracker.controllers;

import mc.alk.tracker.Tracker;
import mc.alk.tracker.TrackerInterface;
import mc.alk.tracker.objects.Stat;
import mc.alk.tracker.objects.StatSign;
import mc.alk.tracker.objects.StatType;
import mc.alk.util.SignUtil;
import mc.alk.v1r7.util.SerializerUtil;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SignController {
	final Map<String,StatSign> personalSigns = new ConcurrentHashMap<String,StatSign>();
	final Map<String,StatSign> topSigns = new ConcurrentHashMap<String,StatSign>();
	final Map<String, Map<String,StatSign>> allSigns = new ConcurrentHashMap<String,Map<String,StatSign>>();
	final Map<String,Integer> prevSignCount = new ConcurrentHashMap<String,Integer>();

	boolean updating = false;
	public SignController(){
		//		allSigns.put(key, value)
	}
	public void addSign(StatSign sign){
		switch(sign.getSignType()){
		case TOP:
			topSigns.put(sign.getLocationString(), sign);
			break;
		case PERSONAL:
			personalSigns.put(sign.getLocationString(), sign);
		}
	}

	public Map<String,StatSign> getPersonalSigns(){
		return personalSigns;
	}

	public Map<String,StatSign> getTopSigns(){
		return topSigns;
	}

	public void addSigns(Collection<StatSign> signs) {
		for (StatSign sign: signs){
			addSign(sign);}
	}

	public synchronized void updateSigns(){
		if (updating || topSigns.isEmpty())
			return;
		updating = true;
		final Map<String,StatSign> map;
		synchronized(topSigns){
			map = new HashMap<String,StatSign>(topSigns);
		}
		Collection<String> badSigns = new HashSet<String>();
		Collection<TrackerInterface> interfaces = Tracker.getAllInterfaces();
		List<StatSign> tops = new ArrayList<StatSign>();

		for (TrackerInterface ti : interfaces){
			final String tiName = ti.getInterfaceName().toUpperCase();
			for (String loc : map.keySet()){
				StatSign ss = map.get(loc);
				if (!ss.getDBName().toUpperCase().equals(tiName)){
					continue;}
				Sign s = getSign(loc);
				if (s == null){
					badSigns.add(loc);
					continue;
				}

				switch(ss.getSignType()){
				case TOP:
					tops.add(ss);
					break;
				default:
					break;
				}
			}
			doTopSigns(ti,tops);
			tops = new ArrayList<StatSign>();
		}
		synchronized(topSigns){
			for (String s: badSigns){
				topSigns.remove(s);
			}
		}
		updating = false;
	}

	/**
	 * For all of the signs of this type
	 * @param ti TrackerInterface
	 * @param statsigns List<StatSign>
	 */
	private void doTopSigns(TrackerInterface ti, List<StatSign> statsigns){
		if (statsigns == null || statsigns.isEmpty())
			return;
		/// Sort based on stattype
		Collections.sort(statsigns, new Comparator<StatSign>(){
			public int compare(StatSign arg0, StatSign arg1) {
				if (arg0.getStatType() == null && arg1.getStatType() == null) return 0;
				else if (arg1.getStatType() == null ) return -1;
				else if (arg0.getStatType() == null ) return 1;
				return arg0.getStatType().compareTo(arg1.getStatType());
			}
		});
		/// Get the max length we need to query in the database for each stattype
		/// Once we have found the max, do an async update for the statsigns
		StatType st = statsigns.get(0).getStatType();
		List<Stat> stats = new ArrayList<Stat>();
		List<StatSign> update = new ArrayList<StatSign>();
		int max = 0;

		int offset = 0;
		for (StatSign ss: statsigns){
			if (ss.getStatType() != st){
				/// Update signs
				if (st != null)
					updateSigns(ti,update,max, st,offset++);
				/// Reset variables
				st = ss.getStatType();
				stats.clear();
				update = new ArrayList<StatSign>();
				max =0;
			}
			update.add(ss);
			/// If size != prevsize, they have added or removed signs, coutn as a change
			int size = this.getUpDownCount(ss);
			Integer prevSize = prevSignCount.get(ss.getLocationString());
			if (prevSize == null || prevSize != size){
				prevSignCount.put(ss.getLocationString(), size);
			}

			if (max  < size){
				max = size;}
		}
		if (!update.isEmpty() && st != null){
			updateSigns(ti,update,max,st,offset++);}
	}

	class SignResult{
		final List<Sign> signs;
		final int statSignIndex;
		public SignResult(List<Sign> signs, int statSignIndex){
			this.signs = signs;
			this.statSignIndex = statSignIndex;
		}
	}

	private SignResult getUpDown(StatSign ss) {
		Sign s = getSign(ss.getLocation());
		if (s == null)
			return null;
		Sign sign = null;
		World w = s.getLocation().getWorld();
		List<Sign> signList = new ArrayList<Sign>();
		boolean foundUpStatSign = false;
		/// Search up
		int x = s.getLocation().getBlockX();
		int y = s.getLocation().getBlockY();
		int z = s.getLocation().getBlockZ();
		LinkedList<Sign> upsignList = new LinkedList<Sign>();
		while ((sign = getSign(w,x,++y,z)) != null){
			/// another command sign, don't continue
			if (breakLine(sign.getLine(0))){
				foundUpStatSign = true;
				break;
			}
			upsignList.addFirst(sign);
		}
		/// If there isnt a conflicting sign above, then add all those signs as well
		if (!foundUpStatSign){
			signList.addAll(upsignList);}

		/// Add self
		int originalSignIndex = signList.size();
		signList.add(s);

		sign = null;
		/// Search down
		x = s.getLocation().getBlockX();
		y = s.getLocation().getBlockY();
		z = s.getLocation().getBlockZ();
		while ((sign = getSign(w,x,--y,z)) != null){
			String line = sign.getLine(0);
			/// another command sign, don't continue
			if (breakLine(line))
				break;
			signList.add(sign);
		}
		return new SignResult(signList,originalSignIndex);
	}

	private int getUpDownCount(StatSign ss) {
		Sign s = getSign(ss.getLocation());
		if (s == null)
			return 0;
		int count = 1;

		World w = s.getLocation().getWorld();
		/// Search up
		int x = s.getLocation().getBlockX();
		int y = s.getLocation().getBlockY();
		int z = s.getLocation().getBlockZ();
		while (getSign(w,x,++y,z) != null){
			count++;}

		/// Search down
		x = s.getLocation().getBlockX();
		y = s.getLocation().getBlockY();
		z = s.getLocation().getBlockZ();
		while (getSign(w,x,--y,z) != null){
			count++;}
		return count;
	}


	private void updateSigns(final TrackerInterface ti,
			final List<StatSign> update, final int max, final StatType type, final int offset) {
		Bukkit.getScheduler().scheduleSyncDelayedTask(Tracker.getSelf(), new Runnable(){
			public void run() {
				List<Stat> toplist= ti.getTopX(type, max * 4);
				if (toplist != null && !toplist.isEmpty() && Tracker.getSelf().isEnabled()){
					Bukkit.getScheduler().scheduleSyncDelayedTask(Tracker.getSelf(),
							new UpdateSigns(ti.getInterfaceName(), update,toplist));
				}
			}
		},2L*offset);
	}

	class UpdateSigns implements Runnable{
		final String dbName;
		final List<StatSign> statSignList;
		final List<Stat> statList;

		public UpdateSigns(String dbName, List<StatSign> update, List<Stat> toplist) {
			this.dbName = StringUtils.capitalize(dbName);
			this.statSignList = update;
			this.statList = toplist;
		}

		public void run() {
			for (StatSign ss: statSignList){
				Sign s = getSign(ss.getLocation());
				if (s == null)
					continue;
				SignResult sr = getUpDown(ss);
				if (sr == null || sr.signs.isEmpty())
					continue;
				List<Sign> signList = sr.signs;

				boolean quit = false;
				int curTop = 0;
				for (int i =0;i< signList.size() && !quit;i++){
					int startIndex = 0;
					s = signList.get(i);
					if (i == sr.statSignIndex){
						s.setLine(0, MessageController.colorChat("[&e"+dbName+"&0]"));
						s.setLine(1, MessageController.colorChat("["+ss.getStatType().color()+ss.getStatType()+"&0]"));
						s.setLine(2, MessageController.colorChat("&cNo Records"));
						startIndex = 2;
					}
					for (int j=startIndex;j< 4;j++){
						if (curTop >= statList.size()){
							quit = true;
							break;
						}
						int val = (int) statList.get(curTop).getStat(ss.getStatType());
						String statLine = formatStatLine(statList.get(curTop).getName(), val, curTop);
						if (!s.getLine(j).equals(statLine))
							s.setLine(j, statLine+"         ");
						curTop++;
					}
					s.update(true);
				}
			}
		}

        private String formatStatLine(String name, int val, int curTop) {
            StringBuilder sb = new StringBuilder(15);
            sb.append(curTop + 1).append(".");
            int length = sb.length() + intStringLength(val) + 1;
            if (name.length() + length > 15){
                sb.append(name.substring(0, 15-length )).
                        append("~").append(String.valueOf(val));
            } else {
                sb.append(name).append(StringUtils.repeat(" ", 16 - length - name.length())).
                        append(String.valueOf(val));
            }
            return sb.toString();
        }
	}

	public static int intStringLength(int i) {
		return i==0 ? (1) : (i<0) ? (int)Math.log10(Math.abs(i))+2 : (int)Math.log10(i)+1;
	}

	private boolean breakLine(String line) {
		return line != null && (!line.isEmpty() && line.startsWith("["));
	}

	private Sign getSign(World w, int x, int y, int z) {
		Block b = w.getBlockAt(x, y, z);
		Material t = b.getType();
		return t == Material.SIGN || t == Material.SIGN_POST || t==Material.WALL_SIGN ? (Sign)b.getState(): null;
	}

	private Sign getSign(Location l) {
		if (l == null)
			return null;
		Material t = l.getBlock().getType();
		return t == Material.SIGN || t == Material.SIGN_POST || t==Material.WALL_SIGN ? (Sign)l.getBlock().getState(): null;
	}

	private Sign getSign(String loc) {
		Location l = SerializerUtil.getLocation(loc);
		if (l == null)
			return null;
		Material t = l.getBlock().getType();
		return t == Material.SIGN || t == Material.SIGN_POST || t==Material.WALL_SIGN ? (Sign)l.getBlock().getState(): null;
	}

	public StatSign getStatSign(Location location) {
		String key = StatSign.getLocationString(location);
		if (personalSigns.containsKey(key))
			return personalSigns.get(key);
		else
			return topSigns.get(key);
	}

	public void clickedSign(Player player, Sign s, StatSign ss) {
		switch(ss.getSignType()){
		case TOP:
			updateTopSign(player, s, ss);
			break;
		case PERSONAL:
			updatePersonalSign(player, s, ss);
			break;
		}
	}
	private void updatePersonalSign(Player player, Sign s, StatSign ss) {
		updateSign(player,s,ss);
	}

	private void updateTopSign(Player player, Sign s, StatSign ss) {
		updateSign(player,s,ss);
	}
	private void updateSign(Player player, Sign s, StatSign ss){
		String[] lines = s.getLines();
		TrackerInterface ti = Tracker.getInterface(ss.getDBName());
		Stat stat = ti.getRecord(player);
		if (stat == null)
			return;

		lines[0] = "&0Your Stats";
		lines[1] = "&0[&9"+stat.getRating() +"&0]";
		int len = (stat.getWins() +"/" + stat.getLosses()).length();
		lines[2] = (len <= 10) ? "&2"+stat.getWins() +"&0/&4" + stat.getLosses() :
			stat.getWins() +"/" + stat.getLosses();
		if (lines[2].length() <= 12)
			lines[2] = "&0W/L " + lines[2];
		lines[3] = "&0Streak: &9" + stat.getStreak() +"";
		for (int i=0;i<lines.length;i++){
			lines[i] = MessageController.colorChat(lines[i]);
		}
		SignUtil.sendLines(player, s, lines);
	}

	public void clearSigns() {
		topSigns.clear();
		personalSigns.clear();

	}
	public void removeSignAt(Location location) {
		String l = StatSign.getLocationString(location);
		topSigns.remove(l);
		personalSigns.remove(l);
	}
}
