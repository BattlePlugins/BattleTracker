package org.battleplugins.tracker.feature;

import net.kyori.adventure.text.Component;
import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.Tracker;
import org.battleplugins.tracker.event.TrackerDeathEvent;
import org.battleplugins.tracker.feature.message.MessageAudience;
import org.battleplugins.tracker.stat.Record;
import org.battleplugins.tracker.stat.StatType;
import org.battleplugins.tracker.util.MessageUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;

public record Killstreaks(
        boolean enabled,
        int minimumKills,
        int killstreakMessageInterval,
        MessageAudience audience,
        Map<String, Component> messages
) implements TrackerFeature {

    @Override
    public void onEnable(BattleTracker battleTracker, Tracker tracker) {
        battleTracker.registerListener(tracker, new KillstreakListener(tracker, this));
    }

    public static Killstreaks load(ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled");
        if (!enabled) {
            return new Killstreaks(false, 0, 0, MessageAudience.GLOBAL, Map.of());
        }

        int minimumKills = section.getInt("minimum-kills");
        int killstreakMessageInterval = section.getInt("killstreak-message-interval");
        MessageAudience audience = MessageAudience.get(section.getString("audience"));

        Map<String, Component> messages = new HashMap<>();
        ConfigurationSection messagesSection = section.getConfigurationSection("messages");
        messagesSection.getKeys(false).forEach(key -> {
            if (!messagesSection.isString(key)) {
                throw new IllegalArgumentException("Message " + key + " is not a string!");
            }

            messages.put(key, MessageUtil.deserialize(messagesSection.getString(key)));
        });

        return new Killstreaks(true, minimumKills, killstreakMessageInterval, audience, messages);
    }

    private record KillstreakListener(Tracker tracker, Killstreaks killstreaks) implements Listener {

        @EventHandler
        public void onTrackerDeath(TrackerDeathEvent event) {
            if (!event.getTracker().equals(this.tracker)) {
                return;
            }

            if (!(event.getKiller() instanceof Player killer)) {
                return;
            }

            Record record = this.tracker.getRecord(killer);
            float streak = record.getStat(StatType.STREAK);
            if (streak >= this.killstreaks.minimumKills()) {
                if ((int) streak % this.killstreaks.killstreakMessageInterval() != 0) {
                    return;
                }

                String killsStr = Integer.toString(Float.valueOf(streak).intValue());
                Component message = this.killstreaks.messages().get(killsStr);
                if (message == null) {
                    message = this.killstreaks.messages().get("default");
                }

                message = message.replaceText(builder -> builder.matchLiteral("%player%").once().replacement(killer.name()));
                message = message.replaceText(builder -> builder.matchLiteral("%kills%").once().replacement(killsStr));

                this.killstreaks.audience().broadcastMessage(message, killer, event.getPlayer());
            }
        }
    }
}
