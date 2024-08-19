package org.battleplugins.tracker.feature.message;

import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.Tracker;
import org.battleplugins.tracker.feature.TrackerFeature;
import org.bukkit.configuration.ConfigurationSection;

public record DeathMessages(
        boolean enabled,
        MessageAudience audience,
        PlayerMessages playerMessages,
        EntityMessages entityMessages,
        WorldMessages worldMessages
) implements TrackerFeature {

    @Override
    public void onEnable(BattleTracker battleTracker, Tracker tracker) {
        battleTracker.registerListener(tracker, new DeathMessagesListener(this, tracker));
    }

    public static DeathMessages load(ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled");
        MessageAudience audience = MessageAudience.get(section.getString("audience"));
        PlayerMessages playerMessages = PlayerMessages.load(section.getConfigurationSection("player"));
        EntityMessages entityMessages = EntityMessages.load(section.getConfigurationSection("entity"));
        WorldMessages worldMessages = WorldMessages.load(section.getConfigurationSection("world"));
        return new DeathMessages(enabled, audience, playerMessages, entityMessages, worldMessages);
    }
}
