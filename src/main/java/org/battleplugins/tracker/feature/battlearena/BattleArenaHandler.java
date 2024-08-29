package org.battleplugins.tracker.feature.battlearena;

import org.battleplugins.arena.Arena;
import org.battleplugins.arena.ArenaPlayer;
import org.battleplugins.arena.messages.Message;
import org.battleplugins.arena.options.ArenaOptionType;
import org.battleplugins.arena.options.types.BooleanArenaOption;
import org.battleplugins.arena.stat.ArenaStat;
import org.battleplugins.arena.stat.ArenaStats;
import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.feature.message.MessageAudience;
import org.battleplugins.tracker.message.Messages;
import org.battleplugins.tracker.stat.StatType;
import org.battleplugins.tracker.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BattleArenaHandler {
    public static final ArenaOptionType<BooleanArenaOption> TRACK_STATISTICS_OPTION = ArenaOptionType.create("track-statistics", BooleanArenaOption::new);
    public static final MessageAudience ARENA_AUDIENCE = MessageAudience.create("arena", (player) -> {
        ArenaPlayer arenaPlayer = ArenaPlayer.getArenaPlayer(player);
        if (arenaPlayer == null) {
            return List.of();
        }

        return arenaPlayer.getCompetition().getPlayers()
                .stream()
                .map(ArenaPlayer::getPlayer)
                .toList();
    });

    private final BattleTracker battleTracker;

    public BattleArenaHandler(BattleTracker battleTracker) {
        this.battleTracker = battleTracker;

        Bukkit.getPluginManager().registerEvents(new BattleArenaListener(battleTracker), battleTracker);
    }

    public void onDisable() {
        HandlerList.getRegisteredListeners(this.battleTracker).stream()
                .filter(listener -> listener.getListener() instanceof BattleArenaListener)
                .forEach(l -> HandlerList.unregisterAll(l.getListener()));
    }

    public static Message convertMessage(String message) {
        return org.battleplugins.arena.messages.Messages.wrap(MessageUtil.serialize(Messages.get(message)));
    }

    public static List<StatInfo> getTrackedStats(Arena arena) {
        List<StatInfo> stats = new ArrayList<>();
        ConfigurationSection trackingSection = arena.getConfig().get("tracking");
        if (trackingSection != null && trackingSection.isConfigurationSection("stats-tracked")) {
            for (String stat : trackingSection.getConfigurationSection("stats-tracked").getKeys(false)) {
                ArenaStat<?> arenaStat = ArenaStats.get(stat);
                if (arenaStat == null) {
                    BattleTracker.getInstance().warn("Invalid Arena stat {} for arena " + arena.getName());
                    continue;
                }

                StatType statType = StatType.get(trackingSection.getString("stats-tracked." + stat + ".key"));
                if (statType != null) {
                    Type type = Type.valueOf(trackingSection.getString("stats-tracked." + stat + ".type", "add").toUpperCase(Locale.ROOT));
                    stats.add(new StatInfo(arenaStat, statType, type));
                } else {
                    BattleTracker.getInstance().warn("Invalid StatType {} for arena " + arena.getName());
                }
            }
        }

        return stats;
    }

    public record StatInfo(ArenaStat<?> stat, StatType statType, Type type) {
    }

    public enum Type {
        ADD,
        REMOVE
    }
}
