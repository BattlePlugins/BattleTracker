package mc.alk.tracker.plugins;

import mc.alk.tracker.Tracker;
import mc.alk.tracker.TrackerInterface;
import mc.alk.tracker.objects.Stat;
import mc.alk.tracker.objects.StatType;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class BTPlaceholderExtension extends PlaceholderExpansion {

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
        return Tracker.getVersionObject().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || !player.isOnline())
            return "";

        String[] split = params.split("_");
        String interfaceName = split[0];

        // The interface is not tracked or does not exist
        if (!Tracker.hasInterface(interfaceName))
            return "";

        TrackerInterface tracker = Tracker.getInterface(interfaceName);
        Stat record = tracker.getRecord(player);

        // Gets leaderboard stats (ex: %bt_pvp_top_wins_1%)
        if (split[1].equalsIgnoreCase("top")) {
            try {
                Integer.parseInt(split[3]);
            } catch (NumberFormatException ex) {
                return null; // not a number at the end of the placeholder
            }

            String stat = split[2];
            for (StatType type : StatType.values()) {
                if (!stat.equalsIgnoreCase(type.name()))
                    continue;

                int i = Integer.parseInt(split[3]);

                Stat top = tracker.getTopX(type, i).get(i-1);
                return String.valueOf(top.getStat(type));
            }
        }

        // Gets player stats (ex: %bt_pvp_wins%)
        for (StatType type : StatType.values()) {
            if (split[1].equalsIgnoreCase(type.name())) {
                return String.valueOf(record.getStat(type));
            }

            if (!split[1].equalsIgnoreCase("top"))
                continue;
        }

        return null;
    }
}