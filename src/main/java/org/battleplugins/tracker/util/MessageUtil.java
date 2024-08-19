package org.battleplugins.tracker.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class MessageUtil {
    public static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static Component deserialize(String message) {
        return MINI_MESSAGE.deserialize(message);
    }

    public static String serialize(Component component) {
        return MINI_MESSAGE.serialize(component);
    }
}
