package org.battleplugins.tracker.feature.damageindicators;

import net.kyori.adventure.text.Component;
import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.feature.Feature;
import org.battleplugins.tracker.util.MessageUtil;
import org.battleplugins.tracker.util.Util;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public record DamageIndicators(
        boolean enabled,
        List<String> disabledWorlds,
        Component format
) implements Feature {

    @Override
    public void onEnable(BattleTracker battleTracker) {
        battleTracker.getServer().getPluginManager().registerEvents(new DamageIndicatorListener(battleTracker, this), battleTracker);
    }

    @Override
    public void onDisable(BattleTracker battleTracker) {
        HandlerList.getRegisteredListeners(battleTracker).stream()
                .filter(listener -> listener.getListener() instanceof DamageIndicatorListener)
                .forEach(l -> HandlerList.unregisterAll(l.getListener()));
    }

    public static DamageIndicators load(ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled");
        if (!enabled) {
            return new DamageIndicators(false, List.of(), Component.empty());
        }

        List<String> disabledWorlds = section.getStringList("disabled-worlds");
        String format = section.getString("format", "");
        return new DamageIndicators(true, disabledWorlds, MessageUtil.MINI_MESSAGE.deserialize(format));
    }

    private record DamageIndicatorListener(BattleTracker battleTracker,
                                           DamageIndicators damageIndicators) implements Listener {

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onEntityDamage(EntityDamageByEntityEvent event) {
            if (!(event.getDamager() instanceof Player damager)) {
                return;
            }

            Entity damaged = event.getEntity();

            double xRand = ThreadLocalRandom.current().nextDouble(0.4, 0.7) / (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
            double yRand = ThreadLocalRandom.current().nextDouble(0.5, 1.5);
            double zRand = ThreadLocalRandom.current().nextDouble(0.4, 0.7) / (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);

            ArmorStand indicator = damaged.getWorld().spawn(damaged.getLocation().clone().add(xRand, yRand, zRand), ArmorStand.class, entity -> {
                entity.customName(this.damageIndicators.format.replaceText(builder ->
                        builder.matchLiteral("{damage}").once().replacement(Component.text(Util.DAMAGE_FORMAT.format(event.getFinalDamage())))
                ));

                entity.setCustomNameVisible(true);
                entity.setMarker(true);
                entity.setPersistent(false);
                entity.setInvisible(true);
                entity.setGravity(false);
                entity.setSmall(true);
                entity.setVisibleByDefault(false);
            });

            damager.showEntity(this.battleTracker, indicator);

            new BukkitRunnable() {
                int counter = 0;

                @Override
                public void run() {
                    if (counter++ > 40) {
                        indicator.remove();
                        this.cancel();
                        return;
                    }

                    indicator.teleport(indicator.getLocation().clone().subtract(0, 0.06, 0));
                    if (indicator.isOnGround()) {
                        indicator.remove();
                        this.cancel();
                    }
                }
            }.runTaskTimer(this.battleTracker, 1, 1);
        }
    }
}
