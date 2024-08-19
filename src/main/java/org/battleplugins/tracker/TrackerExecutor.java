package org.battleplugins.tracker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.battleplugins.tracker.feature.recap.BattleRecap;
import org.battleplugins.tracker.feature.recap.Recap;
import org.battleplugins.tracker.feature.recap.RecapRoundup;
import org.battleplugins.tracker.message.Messages;
import org.battleplugins.tracker.stat.Record;
import org.battleplugins.tracker.stat.StatType;
import org.battleplugins.tracker.stat.TallyEntry;
import org.battleplugins.tracker.stat.VersusTally;
import org.battleplugins.tracker.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class TrackerExecutor implements CommandExecutor {
    private final Tracker tracker;
    private final Map<String, SimpleExecutor> commands;

    public TrackerExecutor(Tracker tracker) {
        this.tracker = tracker;

        this.commands = new HashMap<>(
            Map.of(
                    "top", new SimpleExecutor("View the top players of this tracker.", Arguments.ofOptional("max"), this::top),
                    "rank", new SimpleExecutor("View the rank of a player.", Arguments.ofOptional("player"), this::rank),
                    "versus", new SimpleExecutor("Compare the stats of players in relation to each other.", Arguments.of("player").optional("target"), this::versus)
            )
        );

        if (this.tracker.hasFeature(Recap.class)) {
            this.commands.put("recap", new SimpleExecutor("View the recap of a player.", Arguments.ofOptional("player"), this::recap));
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            this.sendHelp(sender, label);
            return true;
        }

        SimpleExecutor simpleCommand = this.commands.get(args[0]);
        if (simpleCommand != null) {
            if (!hasPermission(sender, args[0])) {
                Messages.send(sender, "command-no-permission");
                return true;
            }

            simpleCommand.consumer().accept(sender, args.length == 1 ? "" : String.join(" ", args).replaceFirst(args[0], "").trim());
            return true;
        }

        this.sendHelp(sender, label);
        return true;
    }

    public void sendHelp(CommandSender sender, String label) {
        if (!hasPermission(sender, "help")) {
            Messages.send(sender, "command-no-permission");
            return;
        }

        Messages.send(sender, "header", this.tracker.getName());
        Map<String, Executor> executors = new HashMap<>(this.commands);

        // Sort alphabetical
        executors.keySet().stream()
                .sorted()
                .forEach(command -> {
                    Executor executor = executors.get(command);
                    String args = executor.describeArgs();
                    sender.sendMessage(Component.text("/" + label + " " + command + (args.isEmpty() ? "" : " " + args), NamedTextColor.YELLOW)
                            .append(Component.text(" " + executor.description(), NamedTextColor.GOLD)));
                });
    }

    public void sendHelp(CommandSender sender, String label, String cmd, @Nullable Executor executor) {
        if (executor == null) {
            this.sendHelp(sender, label);
            return;
        }

        Messages.send(sender, "command-usage", "/" + label + " " + cmd + " " + executor.describeArgs());
    }

    private void top(CommandSender sender, String argument) {
        int amount = Math.max(1, argument == null || argument.isBlank() ? 5 : Math.min(100, Integer.parseInt(argument)));
        Util.getSortedRecords(this.tracker, amount, StatType.RATING).whenComplete((records, e) -> {
            if (records.isEmpty()) {
                Messages.send(sender, "leaderboard-no-entries");
                return;
            }

            Messages.send(sender, "header", this.tracker.getName());
            int ranking = 1;
            for (Map.Entry<Record, Float> entry : records.entrySet()) {
                Record record = entry.getKey();

                Util.sendTrackerMessage(sender, "leaderboard", ranking++, record);
            }
        });
    }

    private void rank(CommandSender sender, String playerName) {
        OfflinePlayer target;
        if (!(sender instanceof Player) && (playerName == null || playerName.isBlank())) {
            Messages.send(sender, "command-player-not-found", "<blank>");
            return;
        } else if (sender instanceof Player player && (playerName == null || playerName.isBlank())) {
            target = player;
        } else {
            target = Bukkit.getServer().getOfflinePlayerIfCached(playerName);
        }

        if (target == null) {
            CompletableFuture.supplyAsync(() -> Bukkit.getOfflinePlayer(playerName)).thenCompose(this.tracker::getRecord).whenCompleteAsync((record, e) -> {
                if (record == null) {
                    Messages.send(sender, "player-has-no-record", playerName);
                    return;
                }

                this.rank(sender, record);
            }, Bukkit.getScheduler().getMainThreadExecutor(BattleTracker.getInstance()));
        } else {
            tracker.getRecord(target).whenCompleteAsync((record, e) -> {
                if (record == null) {
                    Messages.send(sender, "player-has-no-record", playerName);
                    return;
                }

                this.rank(sender, record);
            }, Bukkit.getScheduler().getMainThreadExecutor(BattleTracker.getInstance()));
        }
    }

    private void rank(CommandSender sender, Record record) {
        Util.sendTrackerMessage(sender, "rank", -1, record);
    }

    private void versus(CommandSender sender, String arg) {
        String[] args = arg.split(" ");
        if (args.length == 0) {
            Messages.send(sender, "command-player-not-found", "<blank>");
            return;
        }

        if (args.length == 1) {
            if (sender instanceof Player player) {
                String playerName = args[0];
                CompletableFuture.supplyAsync(() -> Bukkit.getOfflinePlayer(playerName)).whenCompleteAsync((p, e) -> {
                    if (e != null) {
                        BattleTracker.getInstance().error("Failed to get versus tally for {} and {}", playerName, player.getName(), e);
                        return;
                    }

                    if (p == null) {
                        Messages.send(sender, "command-player-not-found", playerName);
                        return;
                    }

                    this.versus(sender, player, p);
                }, Bukkit.getScheduler().getMainThreadExecutor(BattleTracker.getInstance()));
            } else {
                Messages.send(sender, "command-player-not-found", "<blank>");
            }
        } else if (args.length == 2) {
            CompletableFuture<OfflinePlayer> player1Future = CompletableFuture.supplyAsync(() -> Bukkit.getOfflinePlayer(args[0]));
            CompletableFuture<OfflinePlayer> player2Future = CompletableFuture.supplyAsync(() -> Bukkit.getOfflinePlayer(args[1]));

            CompletableFuture.allOf(
                    player1Future,
                    player2Future
            ).whenCompleteAsync((players, e) -> {
                if (e != null) {
                    BattleTracker.getInstance().error("Failed to get versus tally for {} and {}", args[0], args[1], e);
                    return;
                }

                OfflinePlayer player1 = player1Future.join();
                OfflinePlayer player2 = player2Future.join();
                if (player1 == null) {
                    Messages.send(sender, "command-player-not-found", args[0]);
                    return;
                }

                if (player2 == null) {
                    Messages.send(sender, "command-player-not-found", args[1]);
                    return;
                }

                this.versus(sender, player1, player2);
            }, Bukkit.getScheduler().getMainThreadExecutor(BattleTracker.getInstance()));
        }
    }

    private void recap(CommandSender sender, String argument) {
        String[] args = argument.split(" ");
        String arg = args.length >= 1 ? args[0] : null;
        Player player = arg == null || arg.isBlank() ? null : Bukkit.getPlayer(arg);
        if (player == null && sender instanceof Player senderPlayer) {
            player = senderPlayer;
        }

        if (player == null) {
            Messages.send(sender, "command-player-not-found", (arg == null || arg.isBlank()) ? "<blank>" : arg);
            return;
        }

        Recap recap = this.tracker.feature(Recap.class).orElseThrow(() -> new IllegalStateException("Recap feature is not enabled!"));
        if (!recap.enabled()) {
            Messages.send(sender, "recap-not-enabled");
            return;
        }

        BattleRecap battleRecap = recap.getPreviousRecap(player);
        if (battleRecap == null || battleRecap.getLastEntry() == null) {
            Messages.send(sender, "recap-no-recap");
            return;
        }

        if (args.length >= 2) {
            boolean sent = false;
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "item":
                    RecapRoundup.recapItem(sender, battleRecap);
                    sent = true;
                    break;
                case "entity":
                    RecapRoundup.recapEntity(sender, battleRecap);
                    sent = true;
                    break;
                case "cause":
                    RecapRoundup.recapSource(sender, battleRecap);
                    sent = true;
                    break;
                case "player":
                    RecapRoundup.recapPlayer(sender, battleRecap);
                    sent = true;
                    break;
                default:
                    break;
            }

            if (sent) {
                RecapRoundup.sendFooter(sender, this.tracker, battleRecap);
                return;
            }
        }

        recap.showRecap(sender, this.tracker, battleRecap);
    }

    private void versus(CommandSender sender, OfflinePlayer player1, OfflinePlayer player2) {
        CompletableFuture<VersusTally> future = this.tracker.getVersusTally(player1, player2);
        future.whenComplete((tally, e) -> {
            if (e != null) {
                BattleTracker.getInstance().error("Failed to get versus tally for {} and {}", player1.getName(), player2.getName(), e);
                return;
            }

            if (tally == null) {
                Messages.send(sender, "player-has-no-tally", player1.getName(), player2.getName());
                return;
            }

            CompletableFuture<Record> record1Future = this.tracker.getRecord(player1);
            CompletableFuture<Record> record2Future = this.tracker.getRecord(player2);

            CompletableFuture.allOf(
                    record1Future,
                    record2Future
            ).whenCompleteAsync((records, ex) -> {
                if (ex != null) {
                    BattleTracker.getInstance().error("Failed to get records for {} and {}", player1.getName(), player2.getName(), ex);
                    return;
                }

                Record record1 = record1Future.join();
                Record record2 = record2Future.join();
                if (record1 == null) {
                    Messages.send(sender, "player-has-no-record", player1.getName());
                    return;
                }

                if (record2 == null) {
                    Messages.send(sender, "player-has-no-record", player2.getName());
                    return;
                }

                this.versus0(sender, player1, record1, player2, record2, tally);
            }, Bukkit.getScheduler().getMainThreadExecutor(BattleTracker.getInstance()));
        });
    }

    private void versus0(CommandSender sender, OfflinePlayer player1, Record record1, OfflinePlayer player2, Record record2, VersusTally tally) {
        DecimalFormat format = new DecimalFormat("0.##");

        Messages.send(sender, "header", Messages.getPlain("versus-tally"));
        Messages.send(sender, "versus", Map.of(
                "player", record1.getName(),
                "target", record2.getName(),
                "player_rating", format.format(record1.getRating()),
                "target_rating", format.format(record2.getRating())
        ));

        Map<String, Object> replacements = new HashMap<>();
        replacements.put("kills", format.format(tally.getStat(StatType.KILLS)));
        replacements.put("deaths", format.format(tally.getStat(StatType.DEATHS)));

        // Since versus tallies are only stored one way, we need to flip the value
        // in the scenario that the "1st" player instead the 2nd player
        if (tally.id2().equals(player1.getUniqueId())) {
            replacements.put("player", player2.getName());
            replacements.put("target", player1.getName());
        } else {
            replacements.put("player", player1.getName());
            replacements.put("target", player2.getName());
        }

        Messages.send(sender, "versus-compare", replacements);

        CompletableFuture<List<TallyEntry>> future = this.tracker.getTallyEntries(player1.getUniqueId(), true);
        future.whenComplete((entries, e) -> {
            if (e != null) {
                BattleTracker.getInstance().error("Failed to get tally entries for {}", player1.getName(), e);
                return;
            }

            if (entries == null || entries.isEmpty()) {
                return;
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Messages.getPlain("date-format"))
                    .withLocale(sender instanceof Player player ? player.locale() : Locale.ROOT)
                    .withZone(ZoneId.systemDefault());

            Messages.send(sender, "versus-history");

            // Sort entries by most recent
            entries.stream()
                    .filter(entry -> {
                        // Ensure the entries are against the same two players
                        return (entry.id1().equals(player1.getUniqueId()) && entry.id2().equals(player2.getUniqueId())) ||
                                (entry.id1().equals(player2.getUniqueId()) && entry.id2().equals(player1.getUniqueId()));
                    })
                    .sorted((e1, e2) -> e2.timestamp().compareTo(e1.timestamp()))
                    .limit(5) // Limit to top 5
                    .forEach(entry -> {
                        // If the player is the first player, they won
                        if (entry.id1().equals(player1.getUniqueId())) {
                            Messages.send(sender, "versus-history-entry-win", Map.of(
                                    "player", player1.getName(),
                                    "target", player2.getName(),
                                    "date", formatter.format(entry.timestamp())
                            ));
                        } else {
                            Messages.send(sender, "versus-history-entry-loss", Map.of(
                                    "player", player1.getName(),
                                    "target", player2.getName(),
                                    "date", formatter.format(entry.timestamp())
                            ));
                        }
                    });
        });
    }

    record SimpleExecutor(String description, Arguments args, BiConsumer<CommandSender, String> consumer) implements Executor {

        @Override
        public String describeArgs() {
            return this.args.describe();
        }
    }

    private static boolean hasPermission(CommandSender sender, String node) {
        return sender.hasPermission("battletracker.command." + node);
    }

    public interface Executor {
        String description();

        String describeArgs();
    }

    public static class Arguments {
        private final List<Argument> arguments = new ArrayList<>();

        private Arguments() {
        }

        public String describe() {
            if (this.arguments.isEmpty()) {
                return "";
            }

            return this.arguments.stream()
                    .map(argument -> argument.required() ? "<" + argument.name() + ">" : "[" + argument.name() + "]")
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");
        }

        private Arguments(boolean required, String... arguments) {
            for (String argument : arguments) {
                this.arguments.add(new Argument(argument, required));
            }
        }

        public Arguments required(String... arguments) {
            for (String argument : arguments) {
                this.arguments.add(new Argument(argument, true));
            }

            return this;
        }

        public Arguments optional(String... arguments) {
            for (String argument : arguments) {
                this.arguments.add(new Argument(argument, false));
            }

            return this;
        }

        private Arguments(Argument... arguments) {
            this.arguments.addAll(List.of(arguments));
        }

        public static Arguments of(String... arguments) {
            return new Arguments(true, arguments);
        }

        public static Arguments of(Argument... arguments) {
            return new Arguments(arguments);
        }

        public static Arguments ofOptional(String... arguments) {
            return new Arguments(false, arguments);
        }

        public static Arguments empty() {
            return new Arguments();
        }

        public record Argument(String name, boolean required) {
        }
    }
}
