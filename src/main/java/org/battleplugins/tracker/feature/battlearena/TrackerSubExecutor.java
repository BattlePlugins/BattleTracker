package org.battleplugins.tracker.feature.battlearena;

import net.kyori.adventure.text.Component;
import org.battleplugins.arena.Arena;
import org.battleplugins.arena.command.ArenaCommand;
import org.battleplugins.arena.command.Argument;
import org.battleplugins.arena.command.SubCommandExecutor;
import org.battleplugins.arena.util.PaginationCalculator;
import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.Tracker;
import org.battleplugins.tracker.message.Messages;
import org.battleplugins.tracker.stat.Record;
import org.battleplugins.tracker.stat.StatType;
import org.battleplugins.tracker.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TrackerSubExecutor implements SubCommandExecutor {
    private final Arena arena;
    private final Tracker tracker;

    public TrackerSubExecutor(Arena arena, Tracker tracker) {
        this.arena = arena;
        this.tracker = tracker;
    }

    @ArenaCommand(commands = "top", description = "View the top players in this arena.", permissionNode = "top")
    public void top(Player player) {
        this.top(player, 5);
    }

    @ArenaCommand(commands = "top", description = "View the top players in this arena.", permissionNode = "top")
    public void top(Player player, @Argument(name = "max", description = "The maximum players to show.") int max) {
        int amount = max <= 0 ? 5 : Math.min(100, max);
        Util.getSortedRecords(this.tracker, amount, StatType.RATING).whenComplete((records, e) -> {
            if (records.isEmpty()) {
                Messages.send(player, "leaderboard-no-entries");
                return;
            }

            player.sendMessage(PaginationCalculator.center(Messages.get("header", this.arena.getName()), Component.space()));

            int ranking = 1;
            for (Map.Entry<Record, Float> entry : records.entrySet()) {
                Record record = entry.getKey();

                Util.sendTrackerMessage(player, "leaderboard-arena", ranking++, record);
            }
        });
    }

    @ArenaCommand(commands = "rank", description = "View the rank of a player.", permissionNode = "rank")
    public void rank(Player player) {
        this.rank(player, (String) null);
    }

    @ArenaCommand(commands = "rank", description = "View the rank of a player.", permissionNode = "rank")
    public void rank(Player player, @Argument(name = "name", description = "The name of the player.") String playerName) {
        OfflinePlayer target;
        if ((playerName == null || playerName.isBlank())) {
            Messages.send(player, "command-player-not-found", "<blank>");
            return;
        } else {
            target = Bukkit.getServer().getOfflinePlayerIfCached(playerName);
        }

        if (target == null) {
            CompletableFuture.supplyAsync(() -> Bukkit.getOfflinePlayer(playerName)).thenCompose(this.tracker::getRecord).whenCompleteAsync((record, e) -> {
                if (record == null) {
                    Messages.send(player, "player-has-no-record", playerName);
                    return;
                }

                this.rank(player, record);
            }, Bukkit.getScheduler().getMainThreadExecutor(BattleTracker.getInstance()));
        } else {
            this.tracker.getRecord(target).whenCompleteAsync((record, e) -> {
                if (record == null) {
                    Messages.send(player, "player-has-no-record", playerName);
                    return;
                }

                this.rank(player, record);
            }, Bukkit.getScheduler().getMainThreadExecutor(BattleTracker.getInstance()));
        }
    }

    private void rank(CommandSender sender, Record record) {
        Util.sendTrackerMessage(sender, "rank-arena", -1, record);
    }
}
