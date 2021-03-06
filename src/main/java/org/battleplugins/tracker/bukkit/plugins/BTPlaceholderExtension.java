package org.battleplugins.tracker.bukkit.plugins;

import lombok.AllArgsConstructor;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.tracking.TrackerInterface;
import org.battleplugins.tracker.tracking.stat.record.Record;
import org.battleplugins.tracker.util.TrackerUtil;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class BTPlaceholderExtension extends PlaceholderExpansion {

    private BattleTracker plugin;

    @Override
    public String getIdentifier() {
        return "BT";
    }

    @Override
    public String getAuthor() {
        return "BattlePlugins";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion().toString();
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || !player.isOnline())
            return "";

        String[] split = params.split("_");
        String interfaceName = split[0];

        // The interface is not tracked or does not exist
        if (!plugin.getTrackerManager().hasInterface(interfaceName))
            return "";

        TrackerInterface trackerInterface = plugin.getTrackerManager().getInterface(interfaceName).get();
        Record record = trackerInterface.getOrCreateRecord(plugin.getServer().getPlayer(player.getUniqueId()).get());

        // Gets leaderboard stats (ex: %bt_pvp_top_kills_1%)
        if (split[1].equalsIgnoreCase("top")) {
            try {
                Integer.parseInt(split[3]);
            } catch (NumberFormatException ex) {
                return null; // not a number at the end of the placeholder
            }

            String stat = split[2];
            int ranking = Integer.parseInt(split[3]);
            if (!record.getStats().containsKey(stat))
                return "";

            Map<Record, Float> sortedRecords = TrackerUtil.getSortedRecords(trackerInterface, -1);
            List<Record> records = new ArrayList<>(sortedRecords.keySet());
            return String.valueOf(records.get(ranking).getStat(stat));
        }

        // Gets player stats (ex: %bt_pvp_kills%)
        if (record.getStats().containsKey(split[1]))
            return String.valueOf(record.getStat(split[1]));

        return null;
    }
}
