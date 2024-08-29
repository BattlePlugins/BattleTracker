package org.battleplugins.tracker.feature.placeholderapi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.Tracker;
import org.battleplugins.tracker.stat.Record;
import org.battleplugins.tracker.stat.StatType;
import org.battleplugins.tracker.util.Util;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class BattleTrackerExpansion extends PlaceholderExpansion {
    private final BattleTracker battleTracker;

    public BattleTrackerExpansion(BattleTracker battleTracker) {
        this.battleTracker = battleTracker;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "bt";
    }

    @Override
    public @NotNull String getAuthor() {
        return "BattlePlugins";
    }

    @Override
    public @NotNull String getVersion() {
        return this.battleTracker.getPluginMeta().getVersion();
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        String[] split = params.split("_");
        String trackerName = split[0];

        // The tracker is not tracked or does not exist
        Tracker tracker = this.battleTracker.getTracker(trackerName);
        if (tracker == null) {
            return "";
        }

        // Gets leaderboard stats (ex: %bt_pvp_top_kills_1%)
        if (split[1].equalsIgnoreCase("top")) {
            String stat = split[2];
            StatType type = StatType.get(stat);
            int idxOffset = 0;
            if (type == null && split.length >= 4) {
                type = StatType.get(split[2] + "_" + split[3]);
                idxOffset = 1;
            }

            if (type != null) {
                int place;
                try {
                    place = Integer.parseInt(split[3 + idxOffset]);
                } catch (NumberFormatException ex) {
                    return null; // Not a number at the end of the placeholder
                }

                List<Record> topRecords = tracker.getTopRecords(place, type).join();
                if (topRecords.size() < place) {
                    return "";
                }

                Record topRecord = topRecords.get(place - 1);
                if (split.length >= 5) {
                    switch (split[split.length - 1]) {
                        case "name" -> {
                            return topRecord.getName();
                        }
                        case "uuid" -> {
                            return topRecord.getId().toString();
                        }
                    };
                }

                return Util.STAT_FORMAT.format(topRecord.getStat(type));
            }
        }

        // Gets player stats (ex: %bt_pvp_kills%)
        if (player == null || !player.isOnline()) {
            return "";
        }

        StatType type = StatType.get(split[1]);
        if (type == null && split.length >= 3) {
            type = StatType.get(split[1] + "_" + split[2]);
        }

        Record record = tracker.getRecord(player);
        if (type != null) {
            return Util.STAT_FORMAT.format(record.getStat(type));
        }

        return null;
    }
}
