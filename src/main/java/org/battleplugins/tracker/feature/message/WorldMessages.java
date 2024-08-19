package org.battleplugins.tracker.feature.message;

import net.kyori.adventure.text.Component;
import org.battleplugins.tracker.util.MessageUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public record WorldMessages(
        boolean enabled,
        Map<EntityDamageEvent.DamageCause, List<Component>> messages,
        List<Component> defaultMessages
) {

    public static WorldMessages load(ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled");
        if (!enabled) {
            return new WorldMessages(false, Map.of(), List.of());
        }

        Map<EntityDamageEvent.DamageCause, List<Component>> messages = new HashMap<>();
        List<Component> defaultMessages = section.getStringList("messages.default")
                .stream()
                .map(MessageUtil::deserialize)
                .toList();

        ConfigurationSection messagesSection = section.getConfigurationSection("messages");
        messagesSection.getKeys(false).forEach(key -> {
            if (!messagesSection.isList(key)) {
                throw new IllegalArgumentException("Section " + key + " is not a list of messages!");
            }

            if (key.equalsIgnoreCase("default")) {
                return;
            }

            List<String> messageList = messagesSection.getStringList(key);
            messages.put(EntityDamageEvent.DamageCause.valueOf(key.toUpperCase(Locale.ROOT)), messageList.stream()
                    .map(MessageUtil::deserialize)
                    .collect(Collectors.toList()));
        });

        return new WorldMessages(true, messages, defaultMessages);
    }
}
