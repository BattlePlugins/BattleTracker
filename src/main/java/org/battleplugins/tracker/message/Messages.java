package org.battleplugins.tracker.message;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Messages {
    private static final Map<String, Component> MESSAGES = new HashMap<>();

    public static void load(Path messagesPath) {
        MESSAGES.clear();

        File messagesFile = messagesPath.toFile();
        FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        for (String key : messagesConfig.getKeys(false)) {
            String messageText = messagesConfig.getString(key);
            if (messageText == null) {
                BattleTracker.getInstance().warn("Message key {} has no value in messages file! Skipping", key);
                continue;
            }

            Component message = MessageUtil.MINI_MESSAGE.deserialize(messageText);
            MESSAGES.put(key, message);
        }
    }

    public static Component get(String key, String... replacements) {
        Component message = MESSAGES.get(key);
        if (message == null) {
            BattleTracker.getInstance().warn("Unknown message key {} in messages file! Skipping", key);
            return Component.empty();
        }

        for (String replacement : replacements) {
            message = message.replaceText(builder -> builder.matchLiteral("{}").once().replacement(Component.text(replacement)));
        }

        return message;
    }

    public static Component get(String key, Map<String, Object> replacements) {
        Component message = MESSAGES.get(key);
        if (message == null) {
            BattleTracker.getInstance().warn("Unknown message key {} in messages file! Skipping", key);
            return Component.empty();
        }

        for (Map.Entry<String, Object> entry : replacements.entrySet()) {
            if (entry.getValue() instanceof ComponentLike componentLike) {
                message = message.replaceText(builder -> builder.matchLiteral("%" + entry.getKey() + "%").once().replacement(componentLike));
            } else {
                message = message.replaceText(builder -> builder.matchLiteral("%" + entry.getKey() + "%").once().replacement(Component.text(entry.getValue().toString())));
            }
        }

        return message;
    }

    public static String getPlain(String key, String... replacements) {
        Component message = MESSAGES.get(key);
        if (message == null) {
            BattleTracker.getInstance().warn("Unknown message key {} in messages file! Skipping", key);
            return "";
        }

        for (String replacement : replacements) {
            message = message.replaceText(builder -> builder.matchLiteral("{}").once().replacement(replacement));
        }

        return PlainTextComponentSerializer.plainText().serialize(MESSAGES.get(key));
    }

    public static void send(Audience audience, String key, Map<String, Object> replacements) {
        Component message = MESSAGES.get(key);
        if (message == null) {
            BattleTracker.getInstance().warn("Unknown message key {} in messages file! Skipping", key);
            return;
        }

        for (Map.Entry<String, Object> entry : replacements.entrySet()) {
            if (entry.getValue() instanceof ComponentLike componentLike) {
                message = message.replaceText(builder -> builder.matchLiteral("%" + entry.getKey() + "%").once().replacement(componentLike));
            } else {
                message = message.replaceText(builder -> builder.matchLiteral("%" + entry.getKey() + "%").once().replacement(Component.text(entry.getValue().toString())));
            }
        }

        message = processClickEvent(message, replacements);
        audience.sendMessage(message);
    }

    public static void send(Audience audience, String key) {
        send(audience, key, Map.of());
    }

    public static void send(Audience audience, String key, String... replacements) {
        send(audience, key, Arrays.stream(replacements).map(Component::text).toArray(Component[]::new));
    }

    public static void send(Audience audience, String key, Component... replacements) {
        Component message = MESSAGES.get(key);
        if (message == null) {
            BattleTracker.getInstance().warn("Unknown message key {} in messages file! Skipping", key);
            return;
        }

        for (Component replacement : replacements) {
            message = message.replaceText(builder -> builder.matchLiteral("{}").once().replacement(replacement));
        }

        audience.sendMessage(message);
    }

    private static Component processClickEvent(Component component, Map<String, Object> replacements) {
        ClickEvent clickEvent = component.clickEvent();
        if (clickEvent != null) {
            for (Map.Entry<String, Object> entry : replacements.entrySet()) {
                clickEvent = ClickEvent.clickEvent(clickEvent.action(), clickEvent.value().replace("%" + entry.getKey() + "%", entry.getValue().toString()));
            }

            component = component.clickEvent(clickEvent);
        }

        List<Component> children = new ArrayList<>();
        for (Component child : component.children()) {
            ClickEvent childClickEvent = child.clickEvent();
            if (childClickEvent != null) {
                for (Map.Entry<String, Object> entry : replacements.entrySet()) {
                    childClickEvent = ClickEvent.clickEvent(childClickEvent.action(), childClickEvent.value().replace("%" + entry.getKey() + "%", entry.getValue().toString()));
                }

                child = child.clickEvent(childClickEvent);
            }

            child = processClickEvent(child, replacements);
            children.add(child);
        }

        component = component.children(children);
        return component;
    }
}
