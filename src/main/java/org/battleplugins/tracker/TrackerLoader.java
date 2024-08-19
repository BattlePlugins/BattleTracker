package org.battleplugins.tracker;

import org.battleplugins.tracker.feature.Killstreaks;
import org.battleplugins.tracker.feature.Rampage;
import org.battleplugins.tracker.feature.message.DeathMessages;
import org.battleplugins.tracker.feature.recap.Recap;
import org.battleplugins.tracker.listener.PvEListener;
import org.battleplugins.tracker.listener.PvPListener;
import org.battleplugins.tracker.stat.calculator.RatingCalculator;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.Configuration;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

record TrackerLoader(BattleTracker battleTracker, Configuration configuration, Path trackerPath) {

    public void load() {
        String name = this.configuration.getString("name");
        String calculatorName = this.configuration.getString("calculator");
        RatingCalculator calculator = this.battleTracker.getCalculator(calculatorName);
        if (calculator == null) {
            this.battleTracker.warn("Rating calculator {} not found!", calculatorName);
            return;
        }

        List<String> trackedData = this.configuration.getStringList("tracked-statistics");
        if (trackedData.isEmpty()) {
            this.battleTracker.warn("No tracked data found for tracker {}!", name);
            return;
        }

        Set<TrackedDataType> dataTypes = EnumSet.noneOf(TrackedDataType.class);
        for (String data : trackedData) {
            try {
                TrackedDataType type = TrackedDataType.valueOf(data.toUpperCase(Locale.ROOT));
                dataTypes.add(type);
            } catch (IllegalArgumentException e) {
                this.battleTracker.warn("Unknown tracked data type {} for tracker {}!", data, name);
            }
        }

        List<String> disabledWorlds = this.configuration.getStringList("disabled-worlds");

        Tracker tracker = new SqlTracker(this.battleTracker, name, calculator, dataTypes, disabledWorlds);
        if (this.configuration.isConfigurationSection("killstreaks")) {
            tracker.registerFeature(Killstreaks.load(this.configuration.getConfigurationSection("killstreaks")));
        }

        if (this.configuration.isConfigurationSection("rampage")) {
            tracker.registerFeature(Rampage.load(this.configuration.getConfigurationSection("rampage")));
        }

        if (this.configuration.isConfigurationSection("death-messages")) {
            tracker.registerFeature(DeathMessages.load(this.configuration.getConfigurationSection("death-messages")));
        }

        if (this.configuration.isConfigurationSection("recap")) {
            tracker.registerFeature(Recap.load(this.configuration.getConfigurationSection("recap")));
        } else {
            // Recaps are always enabled as they are used throughout the tracker for
            // retrieving player damages. However, whether they are displayed is determined
            // by the configuration.
            throw new IllegalArgumentException("Recap configuration not found!");
        }

        // Register command
        PluginCommand command = this.battleTracker.getCommand(tracker.getName().toLowerCase(Locale.ROOT));
        TrackerExecutor executor = new TrackerExecutor(tracker);
        command.setExecutor(executor);

        if (tracker.tracksData(TrackedDataType.PVP)) {
            this.battleTracker.registerListener(tracker, new PvPListener(tracker));
        }

        if (tracker.tracksData(TrackedDataType.PVE) || tracker.tracksData(TrackedDataType.WORLD)) {
            this.battleTracker.registerListener(tracker, new PvEListener(tracker));
        }

        this.battleTracker.registerTracker(tracker);
        this.battleTracker.info("Loaded tracker: {}.", name);
    }
}
