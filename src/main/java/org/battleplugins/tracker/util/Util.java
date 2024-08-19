package org.battleplugins.tracker.util;

import org.battleplugins.tracker.Tracker;
import org.battleplugins.tracker.message.Messages;
import org.battleplugins.tracker.stat.Record;
import org.battleplugins.tracker.stat.StatType;
import org.bukkit.command.CommandSender;

import java.text.DecimalFormat;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class Util {
    private static final char HEART = '♥';
    public static final DecimalFormat HEALTH_FORMAT = new DecimalFormat("0.00");
    public static final DecimalFormat DAMAGE_FORMAT = new DecimalFormat("#.##");

    public static String formatHealth(double health, boolean loss) {
        return (loss ? "-" : "+") + HEALTH_FORMAT.format(health);
    }

    public static CompletableFuture<Map<Record, Float>> getSortedRecords(Tracker tracker, int limit, StatType type) {
        CompletableFuture<List<Record>> records = tracker.getTopRecords(limit, type);
        return records.thenApply(list -> list.stream().collect(LinkedHashMap::new, (map, record) -> map.put(record, record.getStat(type)), Map::putAll));
    }

    public static void sendTrackerMessage(CommandSender sender, String messageKey, int ranking, Record record) {
        DecimalFormat format = new DecimalFormat("0.##");
        Map<String, Object> replacements = new HashMap<>(StatType.values()
                .stream()
                .map(stat -> new AbstractMap.SimpleEntry<>(stat, record.getStat(stat)))
                .collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey().getKey(), format.format(entry.getValue())),
                        Map::putAll
                )
        );

        replacements.put("ranking", ranking);
        replacements.put("player", record.getName());

        Messages.send(sender, messageKey, replacements);
    }

    public static String toTimeString(Duration duration) {
        long seconds = duration.toSecondsPart();
        long minutes = duration.toMinutesPart();
        long hours = duration.toHoursPart();
        long days = duration.toDaysPart();

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append(" ");
            if (days == 1) {
                builder.append(Messages.getPlain("util-day"));
            } else {
                builder.append(Messages.getPlain("util-days"));
            }
        }

        if (hours > 0) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }

            builder.append(hours).append(" ");
            if (hours == 1) {
                builder.append(Messages.getPlain("util-hour"));
            } else {
                builder.append(Messages.getPlain("util-hours"));
            }
        }

        if (minutes > 0) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }

            builder.append(minutes).append(" ");
            if (minutes == 1) {
                builder.append(Messages.getPlain("util-minute"));
            } else {
                builder.append(Messages.getPlain("util-minutes"));
            }
        }

        if (seconds > 0) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }

            builder.append(seconds).append(" ");
            if (seconds == 1) {
                builder.append(Messages.getPlain("util-second"));
            } else {
                builder.append(Messages.getPlain("util-seconds"));
            }
        }

        return builder.toString();
    }

    public static String toTimeStringShort(Duration duration) {
        long seconds = duration.toSecondsPart();
        long minutes = duration.toMinutesPart();
        long hours = duration.toHoursPart();
        long days = duration.toDaysPart();

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("d");
        }

        if (hours > 0) {
            if (!builder.isEmpty()) {
                builder.append(" ");
            }

            builder.append(hours).append("h");
        }

        if (minutes > 0) {
            if (!builder.isEmpty()) {
                builder.append(" ");
            }

            builder.append(minutes).append("m");
        }

        if (seconds > 0) {
            if (!builder.isEmpty()) {
                builder.append(" ");
            }

            builder.append(seconds).append("s");
        }

        return builder.toString();
    }

    public static String capitalize(String string) {
        if (string.isBlank()) {
            return string;
        }

        char[] buffer = string.toCharArray();
        boolean capitalizeNext = true;
        for (int i = 0; i < buffer.length; i++) {
            char nextChar = buffer[i];
            if (Character.isWhitespace(nextChar)) {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                buffer[i] = Character.toTitleCase(nextChar);
                capitalizeNext = false;
            }
        }

        return new String(buffer);
    }

    public static <T> T getRandom(List<T> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
}
