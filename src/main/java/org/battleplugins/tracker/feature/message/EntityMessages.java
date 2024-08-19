package org.battleplugins.tracker.feature.message;

import net.kyori.adventure.text.Component;
import org.battleplugins.tracker.util.MessageUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record EntityMessages(
        boolean enabled,
        Map<EntityType, List<Component>> messages,
        List<Component> defaultMessages
) {

    public static EntityMessages load(ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled");
        if (!enabled) {
            return new EntityMessages(false, Map.of(), List.of());
        }

        Map<EntityType, List<Component>> messages = new HashMap<>();
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
            messages.put(Registry.ENTITY_TYPE.get(NamespacedKey.fromString(key)), messageList.stream()
                    .map(MessageUtil::deserialize)
                    .collect(Collectors.toList()));
        });

        return new EntityMessages(true, messages, defaultMessages);
    }
}
