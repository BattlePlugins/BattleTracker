package org.battleplugins.tracker.sponge.listener;

import lombok.AllArgsConstructor;

import org.battleplugins.api.entity.living.player.OfflinePlayer;
import org.battleplugins.api.sponge.entity.living.player.SpongePlayer;
import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.tracking.TrackerInterface;
import org.battleplugins.tracker.tracking.recap.DamageInfo;
import org.battleplugins.tracker.tracking.recap.Recap;
import org.battleplugins.tracker.tracking.recap.RecapManager;
import org.battleplugins.tracker.util.TrackerUtil;
import org.spongepowered.api.data.manipulator.mutable.entity.TameableData;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.projectile.Projectile;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.cause.entity.damage.source.EntityDamageSource;
import org.spongepowered.api.event.entity.DamageEntityEvent;
import org.spongepowered.api.event.entity.DestructEntityEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * Main listener for PvP tracking in Sponge.
 *
 * @author Redned
 */
@AllArgsConstructor
public class PvPListener {

    private BattleTracker plugin;

    /**
     * Event called when one player is killed by another
     *
     * @param event the event being called
     */
    @Listener
    public void onDeath(DestructEntityEvent.Death event) {
        if (!(event.getTargetEntity() instanceof Player))
            return;

        Optional<EntityDamageSource> source = event.getCause().first(EntityDamageSource.class);
        if (!source.isPresent())
            return; // did not die from pvp

        Player killed = (Player) event.getTargetEntity();

        Player killer = null;
        ItemStack weapon = null;
        Entity damager = source.get().getSource();
        if (damager instanceof Player) {
            killer = (Player) damager;
            weapon = killer.getItemInHand(HandTypes.MAIN_HAND).get();
        }

        if (damager instanceof Projectile) {
            Projectile proj = (Projectile) damager;
            if (proj.getShooter() instanceof Player) {
                killer = (Player) proj.getShooter();
                weapon = killer.getItemInHand(HandTypes.MAIN_HAND).get();
            }
        }

        if (damager.get(TameableData.class).isPresent()) {
            Optional<UUID> opOwnerUUID = damager.get(TameableData.class).get().owner().get();
            if (opOwnerUUID.isPresent()) {
                UUID uuid = opOwnerUUID.get();
                if (plugin.getServer().getOfflinePlayer(uuid).map(OfflinePlayer::isOnline).orElse(false)) {
                    killer = ((SpongePlayer) plugin.getServer().getPlayer(uuid).get()).getHandle();
                    // Use a bone to show the case was a wolf
                    weapon = ItemStack.builder().itemType(ItemTypes.BONE).quantity(1).build();
                }
            }
        }

        if (killer == null)
            return;

        // Check the killers world just incase for some reason the
        // killed player was teleported to another world
        if (plugin.getConfigManager().getPvPConfig().getNode("ignoredWorlds").getCollectionValue(String.class).contains(killer.getWorld().getName()))
            return;

        TrackerUtil.updatePvPStats(plugin.getServer().getOfflinePlayer(killed.getUniqueId().toString()).get(),
                plugin.getServer().getOfflinePlayer(killer.getUniqueId().toString()).get());

        TrackerInterface pvpTracker = plugin.getTrackerManager().getPvPInterface();
        if (pvpTracker.getDeathMessageManager().isDefaultMessagesOverriden())
            event.setMessageCancelled(true);

        pvpTracker.getDeathMessageManager().sendItemMessage(killer.getName(), killed.getName(), weapon.getType().getName().toLowerCase());
        pvpTracker.getRecapManager().getDeathRecaps().get(killed.getName()).setVisible(true);
    }

    /**
     * Event called when a player takes damage from another player
     *
     * @param event the event being called
     */
    @Listener
    public void onEntityDamage(DamageEntityEvent event) {
        if (!(event.getTargetEntity() instanceof Player) || !(event.getCause().first(EntityDamageSource.class).isPresent()))
            return;

        EntityDamageSource source = event.getCause().first(EntityDamageSource.class).get();
        if (!(getTrueDamager(source) instanceof Player)) {
            return;
        }

        Player spongePlayer = (Player) event.getTargetEntity();
        org.battleplugins.api.entity.living.player.Player player = plugin.getServer().getPlayer(spongePlayer.getName()).get();
        TrackerInterface pvpTracker = plugin.getTrackerManager().getPvPInterface();

        RecapManager recapManager = pvpTracker.getRecapManager();
        Recap recap = recapManager.getDeathRecaps().computeIfAbsent(player.getName(), (value) -> new Recap(player));
        if (recap.isVisible()) {
            recap = recapManager.getDeathRecaps().compute(player.getName(), (key, value) -> new Recap(player));
        }

        recap.getLastDamages().add(new DamageInfo(spongePlayer.getName(), event.getFinalDamage()));
    }

    private Entity getTrueDamager(EntityDamageSource source) {
        Entity damager = source.getSource();
        if (damager instanceof Projectile) {
            Projectile proj = (Projectile) damager;
            if (proj.getShooter() instanceof Entity) {
                return (Entity) proj.getShooter();
            }
        }

        if (damager.get(TameableData.class).isPresent()) {
            Optional<UUID> opOwnerUUID = damager.get(TameableData.class).get().owner().get();
            if (opOwnerUUID.isPresent()) {
                UUID uuid = opOwnerUUID.get();
                if (plugin.getServer().getOfflinePlayer(uuid).map(OfflinePlayer::isOnline).orElse(false)) {
                    return ((SpongePlayer) plugin.getServer().getPlayer(uuid).get()).getHandle();
                }
            }
        }

        return damager;
    }
}
