package org.battleplugins.tracker.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.battleplugins.tracker.TrackedDataType;
import org.battleplugins.tracker.Tracker;
import org.battleplugins.tracker.event.TrackerDeathEvent;
import org.battleplugins.tracker.feature.recap.Recap;
import org.battleplugins.tracker.feature.recap.RecapEntry;
import org.battleplugins.tracker.stat.Record;
import org.battleplugins.tracker.stat.StatType;
import org.battleplugins.tracker.util.Util;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

public class PvEListener implements Listener {
    private final Tracker tracker;

    public PvEListener(Tracker tracker) {
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player killed = event.getEntity();
        if (this.tracker.getDisabledWorlds().contains(killed.getWorld().getName())) {
            return;
        }

        TrackedDataType dataType = TrackedDataType.PVE;

        EntityDamageEvent lastDamageCause = killed.getLastDamageCause();
        Entity killer = null;
        String killerName;
        if (lastDamageCause instanceof EntityDamageByEntityEvent damageEvent) {
            EntityType killerType;

            killer = damageEvent.getDamager();
            if (killer instanceof Player) {
                return;
            }

            killerType = killer.getType();
            if (killer instanceof Projectile projectile && projectile.getShooter() instanceof Entity entity) {
                if (projectile.getShooter() instanceof Player) {
                    return;
                }

                killer = entity;
                killerType = projectile.getType();
            }

            if (killer instanceof Tameable tameable && tameable.isTamed()) {
                return; // only players can tame animals
            }

            killerName = PlainTextComponentSerializer.plainText().serialize(Component.translatable(killerType));
        } else {
            dataType = TrackedDataType.WORLD;

            // TODO: Translation support, and use new damage API when it is
            //       ready for widespread adoption
            if (lastDamageCause == null) {
                killerName = "Unknown";
            } else {
                killerName = Util.capitalize(lastDamageCause.getCause().name().toLowerCase(Locale.ROOT).replace("_", " "));
            }
        }

        if (!this.tracker.tracksData(dataType)) {
            return;
        }

        Record killerRecord = this.tracker.getOrCreateRecord(killed);
        if (killerRecord.isTracking()) {
            this.tracker.incrementValue(StatType.DEATHS, killed);
        }

        Record record = new Record(this.tracker, UUID.randomUUID(), killerName, new HashMap<>());
        record.setRating(this.tracker.getRatingCalculator().getDefaultRating());
        this.tracker.getRatingCalculator().updateRating(record, killerRecord, false);

        new TrackerDeathEvent(this.tracker, dataType == TrackedDataType.PVE ? TrackerDeathEvent.DeathType.ENTITY : TrackerDeathEvent.DeathType.WORLD, killer, event).callEvent();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity killed = event.getEntity();
        if (this.tracker.getDisabledWorlds().contains(killed.getWorld().getName())) {
            return;
        }

        if (killed instanceof Player) {
            return;
        }

        if (!this.tracker.tracksData(TrackedDataType.PVE)) {
            return;
        }

        if (!(killed.getLastDamageCause() instanceof EntityDamageByEntityEvent lastDamageCause)) {
            return;
        }

        if (!(lastDamageCause.getDamager() instanceof Player killer)) {
            return;
        }

        Record killerRecord = this.tracker.getOrCreateRecord(killer);
        if (killerRecord.isTracking()) {
            this.tracker.incrementValue(StatType.KILLS, killer);
        }

        String killerName = PlainTextComponentSerializer.plainText().serialize(Component.translatable(killer.getType()));

        Record record = new Record(this.tracker, UUID.randomUUID(), killerName, new HashMap<>());
        record.setRating(this.tracker.getRatingCalculator().getDefaultRating());
        this.tracker.getRatingCalculator().updateRating(killerRecord, record, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (this.tracker.getDisabledWorlds().contains(event.getEntity().getWorld().getName())) {
            return;
        }

        Recap recap = this.tracker.getFeature(Recap.class);
        if (recap == null) {
            return;
        }

        if (!(event instanceof EntityDamageByEntityEvent entityEvent)) {
            if (!this.tracker.tracksData(TrackedDataType.WORLD)) {
                return;
            }

            recap.getRecap(player).record(RecapEntry.builder()
                    .damageCause(event.getCause())
                    .amount(event.getFinalDamage())
                    .logTime(Instant.now())
                    .build()
            );

            return;
        }

        if (!this.tracker.tracksData(TrackedDataType.PVE)) {
            return;
        }

        Entity causingEntity = entityEvent.getDamager();
        Entity sourceEntity = causingEntity;
        if (causingEntity instanceof Projectile proj) {
            if (proj.getShooter() instanceof Entity shooter) {
                sourceEntity = shooter;
            }
        }

        if (causingEntity instanceof Tameable tameable && tameable.isTamed()) {
            AnimalTamer owner = tameable.getOwner();
            if (owner instanceof Entity entity) {
                sourceEntity = entity;
            }
        }

        if (sourceEntity instanceof Player) {
            return;
        }

        ItemStack itemUsed = null;
        if (causingEntity instanceof LivingEntity livingEntity && livingEntity.getEquipment() != null) {
            itemUsed = livingEntity.getEquipment().getItemInMainHand();
        }

        recap.getRecap(player).record(RecapEntry.builder()
                .damageCause(event.getCause())
                .causingEntity(causingEntity)
                .sourceEntity(sourceEntity)
                .amount(event.getFinalDamage())
                .itemUsed(itemUsed)
                .logTime(Instant.now())
                .build()
        );
    }
}
