package org.battleplugins.tracker.feature.message;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Represents a message audience.
 */
public final class MessageAudience {
    private static final Map<String, MessageAudience> AUDIENCES = new HashMap<>();

    public static final MessageAudience GLOBAL = new MessageAudience("global", player -> List.copyOf(Bukkit.getOnlinePlayers()));
    public static final MessageAudience WORLD = new MessageAudience("world", player -> List.copyOf(player.getWorld().getPlayers()));
    public static final MessageAudience LOCAL = new MessageAudience("local", List::of);

    private final String name;
    private final Function<Player, List<Player>> audienceProvider;

    public MessageAudience(String name, Function<Player, List<Player>> audienceProvider) {
        this.name = name;
        this.audienceProvider = audienceProvider;

        AUDIENCES.put(name, this);
    }

    public String getName() {
        return this.name;
    }

    public List<Player> getAudience(Player player) {
        return this.audienceProvider.apply(player);
    }

    public void broadcastMessage(Component message, Player player, @Nullable Player target) {
        List<Player> viewers;

        // Special logic for target, since we need the context from
        // the death event logic earlier
        if (this == MessageAudience.LOCAL) {
            if (target == null) {
                viewers = List.of(player);
            } else {
                viewers = List.of(player, target);
            }
        } else {
            viewers = this.getAudience(player);
        }

        for (Player viewer : viewers) {
            viewer.sendMessage(message);
        }
    }

    public static MessageAudience create(String name, Function<Player, List<Player>> audienceProvider) {
        return new MessageAudience(name, audienceProvider);
    }

    @Nullable
    public static MessageAudience get(String name) {
        return AUDIENCES.get(name);
    }
}
