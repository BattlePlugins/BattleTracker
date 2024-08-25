package org.battleplugins.tracker.feature.battlearena;

import org.battleplugins.arena.ArenaPlayer;
import org.battleplugins.arena.messages.Message;
import org.battleplugins.arena.options.ArenaOptionType;
import org.battleplugins.arena.options.types.BooleanArenaOption;
import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.feature.message.MessageAudience;
import org.battleplugins.tracker.message.Messages;
import org.battleplugins.tracker.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

import java.util.List;

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
}
