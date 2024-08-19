package org.battleplugins.tracker.feature;

import net.kyori.adventure.text.Component;
import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.Tracker;
import org.battleplugins.tracker.event.TrackerDeathEvent;
import org.battleplugins.tracker.feature.message.MessageAudience;
import org.battleplugins.tracker.util.MessageUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record Rampage(
        boolean enabled,
        int rampageTime,
        MessageAudience audience,
        Map<String, Component> messages
) implements TrackerFeature {

    @Override
    public void onEnable(BattleTracker battleTracker, Tracker tracker) {
        battleTracker.registerListener(tracker, new RampageListener(tracker, this));
    }

    public static Rampage load(ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled");
        if (!enabled) {
            return new Rampage(false, 0, MessageAudience.GLOBAL, Map.of());
        }

        int rampageTime = section.getInt("rampage-time");
        MessageAudience audience = MessageAudience.get(section.getString("audience"));

        Map<String, Component> messages = new HashMap<>();
        ConfigurationSection messagesSection = section.getConfigurationSection("messages");
        messagesSection.getKeys(false).forEach(key -> {
            if (!messagesSection.isString(key)) {
                throw new IllegalArgumentException("Message " + key + " is not a string!");
            }

            messages.put(key, MessageUtil.deserialize(messagesSection.getString(key)));
        });

        return new Rampage(true, rampageTime, audience, messages);
    }

    private static class RampageListener implements Listener {
        private final Tracker tracker;
        private final Rampage rampage;

        private final Map<UUID, Long> lastKill = new HashMap<>();
        private final Map<UUID, Integer> killCount = new HashMap<>();

        private RampageListener(Tracker tracker, Rampage rampage) {
            this.tracker = tracker;
            this.rampage = rampage;
        }

        @EventHandler
        public void onTrackerDeath(TrackerDeathEvent event) {
            if (!event.getTracker().equals(this.tracker)) {
                return;
            }

            if (!(event.getKiller() instanceof Player killer)) {
                return;
            }

            UUID killerUUID = killer.getUniqueId();
            long lastKillTime = this.lastKill.getOrDefault(killerUUID, 0L);
            if (System.currentTimeMillis() - lastKillTime > this.rampage.rampageTime() * 1000L) {
                this.killCount.put(killerUUID, 0);
            }

            this.lastKill.put(killerUUID, System.currentTimeMillis());
            int killCount = this.killCount.getOrDefault(killerUUID, 0) + 1;
            this.killCount.put(killerUUID, killCount);

            if (killCount > 1) {
                Component message = this.rampage.messages().get(Integer.toString(killCount));
                if (message == null) {
                    message = this.rampage.messages().get("default");
                }

                message = message.replaceText(builder -> builder.matchLiteral("%player%").once().replacement(killer.name()));

                this.rampage.audience().broadcastMessage(message, killer, event.getPlayer());
            }
        }
    }
}
