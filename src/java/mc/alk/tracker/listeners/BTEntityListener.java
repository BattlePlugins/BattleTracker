package mc.alk.tracker.listeners;

import mc.alk.tracker.Defaults;
import mc.alk.tracker.Tracker;
import mc.alk.tracker.TrackerInterface;
import mc.alk.tracker.TrackerOptions;
import mc.alk.tracker.controllers.ConfigController;
import mc.alk.tracker.controllers.MessageController;
import mc.alk.tracker.controllers.TrackerController;
import mc.alk.tracker.objects.SpecialType;
import mc.alk.tracker.objects.Stat;
import mc.alk.tracker.objects.WLT;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class BTEntityListener implements Listener{
	static JavaPlugin plugin;
	static final String UNKNOWN = "unknown";
	ConcurrentHashMap<String,Long> lastDamageTime = new ConcurrentHashMap<String,Long>();
	ConcurrentHashMap<String,RampageStreak> lastKillTime = new ConcurrentHashMap<String,RampageStreak>();
	static HashSet<String> ignoreEntities = new HashSet<String>();
	static HashSet<UUID> ignoreWorlds = new HashSet<UUID>();

	Random r = new Random();
	TrackerInterface playerTi;
	TrackerInterface worldTi;
	int count = 0;

	class RampageStreak{
		Long time; int nkills;
		public RampageStreak(Long t, int nk){this.time = t; this.nkills = nk;}
	}

	public BTEntityListener(){
		TrackerOptions to = new TrackerOptions();
		to.setSaveIndividualRecords(true);
		playerTi = Tracker.getInterface(Defaults.PVP_INTERFACE,to);
		worldTi = Tracker.getInterface(Defaults.PVE_INTERFACE);
		plugin = Tracker.getSelf();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerDeath(PlayerDeathEvent event) {
		ede(event);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEntityDeath(EntityDeathEvent event) {
		if (event instanceof PlayerDeathEvent)
			return;
		/// we have a player killing a mob, if we are not tracking pve
		/// we don't need to enter, no messages are usually sent for this
		if (!ConfigController.getBoolean("trackPvE",false))
			return;
		ede(event);
	}

	private void ede(EntityDeathEvent event) {
		if (ignoreWorlds.contains(event.getEntity().getWorld().getUID()))
			return;
		String target, killer;
		boolean targetPlayer = false, killerPlayer = false;
		boolean isMelee = true;
		ItemStack killingWeapon = null;

		/// Get our hapless target
		Entity targetEntity = event.getEntity();
		if (targetEntity instanceof Player){
			target = ((Player)targetEntity).getName();
			targetPlayer = true;
		} else if (targetEntity instanceof Tameable){
			target = "Tamed" + targetEntity.getType().getName();
		} else {
			target = targetEntity.getType().getName();
		}
		/// Should we be tracking this person
		if (targetPlayer && (TrackerController.dontTrack(target))){
			if (event instanceof PlayerDeathEvent)
				((PlayerDeathEvent) event).setDeathMessage(""); /// Set to none, will cancel all non pvp messages
			return;
		}
		if (!targetPlayer && !ConfigController.getBoolean("trackPvP") && !ConfigController.getBoolean("sendPVPDeathMessages")){
			return;
		}

		/// Determine our killer
		EntityDamageEvent lastDamageCause = targetEntity.getLastDamageCause();
		Player killerEntity = null;
		if (lastDamageCause instanceof EntityDamageByEntityEvent){
			Entity damager = ((EntityDamageByEntityEvent) lastDamageCause).getDamager();
			if (damager instanceof Player) { /// killer is player
				killerEntity = (Player) damager;
				killer = killerEntity.getName();
				killerPlayer = true;
				killingWeapon = killerEntity.getItemInHand();
			} else if (damager instanceof Projectile) { /// we have some sort of projectile
				isMelee = false;
				Projectile proj = (Projectile) damager;
				if (proj.getShooter() instanceof Player){ /// projectile was shot by a player
					killerPlayer = true;
					killerEntity = (Player) proj.getShooter();
					killer = killerEntity.getName();
					killingWeapon = killerEntity.getItemInHand();
				} else if (proj.getShooter() != null){ /// projectile shot by some mob, or other source
					killer = proj.getShooter().getClass().getSimpleName();
				} else {
					killer = UNKNOWN; /// projectile was null?
				}
			} else if (damager instanceof Tameable && ((Tameable) damager).isTamed()) {
				AnimalTamer at = ((Tameable) damager).getOwner();
				if (at != null){
					if (at instanceof Player){
						killerPlayer = true;
						killerEntity = (Player) at;
					}
					killer = at.getName();
				} else {
					killer = damager.getType().getName();
				}
			} else { /// Killer is not a player
				killer = damager.getType().getName();
			}
		} else {
			if (lastDamageCause == null || lastDamageCause.getCause() == null)
				killer  = UNKNOWN;
			else
				killer = lastDamageCause.getCause().name();
		}
        if (killer == null)
            killer = UNKNOWN;
		if (killerPlayer && TrackerController.dontTrack(killer))
			return;
		if (ignoreEntities.contains(killer) || ignoreEntities.contains(targetEntity.toString()) ||
                ignoreEntities.contains(target))
			return;
		/// Decide what to do
		if (targetPlayer && killerPlayer){
			/// Check to see if we add the records
			if (ConfigController.getBoolean("trackPvP",true))
				addRecord(playerTi,killer,target,WLT.WIN);
			PlayerDeathEvent pde = (PlayerDeathEvent) event;

			/// Check to see if an we have disabled death messages
			if (!Defaults.PVP_MESSAGES && !Defaults.BUKKIT_PVP_MESSAGES){
				/// Even with disabled messages, we can still send messages to involved players
				if (Defaults.INVOLVED_PVP_MESSAGES){
					String msg = getPvPDeathMessage(killer,target,isMelee,playerTi,killingWeapon);
					sendMessage(killerEntity, (Player)targetEntity,msg);
				} else {
					sendMessage(pde,null);
				}
				return;
			}

			/// Check sending custom PvP messages
			if (Defaults.PVP_MESSAGES){
				//				final String wpn = killingWeapon != null ? killingWeapon.getType().name().toLowerCase() : null;
				String msg = getPvPDeathMessage(killer,target,isMelee,playerTi,killingWeapon);
				sendMessage(pde,msg);
			}
			/// else we just let bukkit handle it like normal
		} else if (!targetPlayer && !killerPlayer){ /// mobs killing each other, or dying by traps
			/// Do nothing
		} else { /// One player, One other
			/// Get rid of Craft before mobs.. CraftSpider -> Spider
			if (!killerPlayer && killer.contains("Craft")){
				killer = killer.substring(5);}
			if (!targetPlayer && target.contains("Craft")){
				target = target.substring(5);}
			/// Should we track the kills?
			if (ConfigController.getBoolean("trackPvE",true)){
				addRecord(worldTi, killer,target,WLT.WIN);}

			/// Check message sending
			if (targetPlayer && event instanceof PlayerDeathEvent){
				/// Check to see if an admin has disabled death messages
				PlayerDeathEvent pde = (PlayerDeathEvent) event;
				if (!Defaults.PVE_MESSAGES && !Defaults.BUKKIT_PVE_MESSAGES){
					if (Defaults.INVOLVED_PVE_MESSAGES){
						final String wpn = killingWeapon != null ? killingWeapon.getType().name().toLowerCase() : null;
						String msg = getPvEDeathMessage(killer,target,isMelee,wpn);
						sendMessage(null,(Player) targetEntity,msg);
					} else {
						pde.setDeathMessage(null);
					}
					return;
				}

				if (Defaults.PVE_MESSAGES){
					final String wpn = killingWeapon != null ? killingWeapon.getType().name().toLowerCase() : null;
					String msg = getPvEDeathMessage(killer,target,isMelee,wpn);
					sendMessage(pde,msg);
				}
				/// Else let bukkit handle
			}
		}
	}


	private void sendMessage(Player killerEntity, Player targetEntity, String msg) {
		if (killerEntity != null){
			MessageController.sendMessage(killerEntity, msg);}
		if (targetEntity != null){
			MessageController.sendMessage(targetEntity, msg);}
	}

	private void sendMessage(PlayerDeathEvent event, String msg){
		if (msg == null){
			event.setDeathMessage(null);
			return;
		}

		if (Defaults.RADIUS <= 0){
			event.setDeathMessage(msg);
		} else {
			Player player = event.getEntity();
			if (player == null){
				event.setDeathMessage(msg);
				return;
			}
			Location l = player.getLocation();
			if (l==null){
				event.setDeathMessage(msg);
				return;
			}
			event.setDeathMessage(null);
			UUID wid = l.getWorld().getUID();
			Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
			Player players[] = onlinePlayers.toArray(new Player[onlinePlayers.size()]);

			for (Player p: players){
				if (wid != p.getLocation().getWorld().getUID()  || p.getLocation().distanceSquared(l) >= Defaults.RADIUS){
					continue;}
				p.sendMessage(msg);
			}
		}
	}

	public String getPvPDeathMessage(String killer, String target, boolean isMeleeDeath,
			TrackerInterface ti, ItemStack killingWeapon){
		/// Check for a rampage
		try {
			RampageStreak lastKill = lastKillTime.get(killer);
			long now = System.currentTimeMillis() ;
			if (lastKill != null && now - lastKill.time < Defaults.RAMPAGE_TIME){
				lastKill.nkills++;
				lastKill.time = now;
				return MessageController.getSpecialMessage(SpecialType.RAMPAGE, lastKill.nkills, killer,target, killingWeapon);
			} else {
				lastKillTime.put(killer, new RampageStreak(now, 1));
			}
		} catch (Exception e){
			/// in the unlikely case of a Concurrent modification exception, lets not crash
			/// but instead send a normal message
		}

		/// If a normal streak
		/// Player has a multiple of x, send they are on a streak message
		Stat stat = ti.loadPlayerRecord(killer);
		final int streak = stat.getStreak();
		boolean hasStreak = MessageController.contains("special.streak."+streak);
		/// they are on a streak
		if (hasStreak || streak != 0 && Defaults.STREAK_EVERY != 0 && streak % Defaults.STREAK_EVERY== 0){
			return MessageController.getSpecialMessage(SpecialType.STREAK, streak, killer,target, killingWeapon);
		} else { /// Display a normal kill message
			return MessageController.getPvPMessage(isMeleeDeath,killer, target, killingWeapon);
		}
	}

	public String getPvEDeathMessage(String p1, String p2, boolean isMeleeDeath, String killingWeapon){
		return MessageController.getPvEMessage(isMeleeDeath, p1, p2,killingWeapon);
	}

	public static void addRecord(final TrackerInterface ti,final String e1, final String e2, final WLT record){
		ti.addPlayerRecord(e1, e2, record);
	}

	public static void setIgnoreEntities(List<String> list) {
		ignoreEntities.clear();
		if (list != null)
			ignoreEntities.addAll(list);
	}

	public static void setIgnoreWorlds(List<String> list) {
		ignoreWorlds.clear();
		if (list != null){
			for (String s: list){
				World w = Bukkit.getWorld(s);
				if (w != null){
					ignoreWorlds.add(w.getUID());}
			}
		}
	}
}
