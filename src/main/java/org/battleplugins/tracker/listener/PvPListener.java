package org.battleplugins.tracker.listener;

import org.battleplugins.tracker.TrackedDataType;
import org.battleplugins.tracker.Tracker;
import org.battleplugins.tracker.event.TrackerDeathEvent;
import org.battleplugins.tracker.feature.recap.Recap;
import org.battleplugins.tracker.feature.recap.RecapEntry;
import org.battleplugins.tracker.stat.Record;
import org.battleplugins.tracker.stat.StatType;
import org.battleplugins.tracker.stat.TallyEntry;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;

public class PvPListener implements Listener {
    private final Tracker tracker;

    public PvPListener(Tracker tracker) {
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!this.tracker.tracksData(TrackedDataType.PVP)) {
            return;
        }

        Player killed = event.getEntity();
        if (this.tracker.getDisabledWorlds().contains(killed.getWorld().getName())) {
            return;
        }

        EntityDamageEvent lastDamageCause = killed.getLastDamageCause();
        Player killer = null;
        if (lastDamageCause instanceof EntityDamageByEntityEvent damageByEntityEvent) {
            Entity damager = damageByEntityEvent.getDamager();
            if (damager instanceof Player) {
                killer = (Player) damager;
            }

            if (damager instanceof Projectile proj) {
                if (proj.getShooter() instanceof Player player) {
                    killer = player;
                }
            }

            if (damager instanceof Tameable tameable && tameable.isTamed()) {
                AnimalTamer owner = tameable.getOwner();
                if (owner instanceof Player player) {
                    killer = player;
                }
            }
        }

        if (killer == null) {
            return;
        }

        Record killerRecord = this.tracker.getOrCreateRecord(killer);
        Record killedRecord = this.tracker.getOrCreateRecord(killed);

        if (killerRecord.isTracking()) {
            this.tracker.incrementValue(StatType.KILLS, killer);
        }

        if (killedRecord.isTracking()) {
            this.tracker.incrementValue(StatType.DEATHS, killed);
        }

        this.tracker.updateRating(killer, killed, false);

        new TrackerDeathEvent(this.tracker, TrackerDeathEvent.DeathType.PLAYER, killer, event).callEvent();

        Player finalKiller = killer;
        this.tracker.getOrCreateVersusTally(killer, killed).whenComplete((versusTally, e) -> {
            // The format is killer : killed : stat1 : stat2 ....
            // If the killer is in place of the killed, we need to swap the values
            boolean addToKills = !versusTally.id2().equals(finalKiller.getUniqueId());
            if (addToKills) {
                this.tracker.modifyTally(versusTally, ctx -> ctx.recordStat(StatType.KILLS, versusTally.getStat(StatType.KILLS) + 1));
            } else {
                this.tracker.modifyTally(versusTally, ctx -> ctx.recordStat(StatType.DEATHS, versusTally.getStat(StatType.DEATHS) + 1));
            }

            // Record a tally entry at the current timestamp
            TallyEntry entry = new TallyEntry(finalKiller, killed, false, Instant.now());
            this.tracker.recordTallyEntry(entry);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!this.tracker.tracksData(TrackedDataType.PVP)) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        if (this.tracker.getDisabledWorlds().contains(damaged.getWorld().getName())) {
            return;
        }

        Recap recap = this.tracker.getFeature(Recap.class);
        if (recap == null) {
            return;
        }

        Entity sourceEntity = event.getDamager();
        if (event.getDamager() instanceof Projectile proj) {
            if (proj.getShooter() instanceof Player shooter) {
                sourceEntity = shooter;
            }
        }

        if (event.getDamager() instanceof Tameable tameable && tameable.isTamed()) {
            AnimalTamer owner = tameable.getOwner();
            if (owner instanceof Player player) {
                sourceEntity = player;
            }
        }

        if (!(sourceEntity instanceof Player player)) {
            return;
        }

        ItemStack itemUsed = player.getInventory().getItemInMainHand();
        recap.getRecap(damaged).record(RecapEntry.builder()
                .damageCause(event.getCause())
                .causingEntity(event.getDamager())
                .sourceEntity(sourceEntity)
                .amount(event.getFinalDamage())
                .itemUsed(itemUsed)
                .logTime(Instant.now())
                .build()
        );
    }
}
