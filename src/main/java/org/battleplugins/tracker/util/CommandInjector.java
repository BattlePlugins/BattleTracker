package org.battleplugins.tracker.util;

import org.battleplugins.tracker.BattleTracker;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class CommandInjector {

    public static PluginCommand inject(String trackerName, String commandName, String... aliases) {
        return inject(trackerName, commandName, "The main command for the " + trackerName + " tracker!", aliases);
    }

    public static PluginCommand inject(String headerName, String commandName, String description, String... aliases) {
        try {
            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);

            PluginCommand pluginCommand = constructor.newInstance(commandName, BattleTracker.getInstance());
            pluginCommand.setAliases(List.of(aliases));
            pluginCommand.setDescription(description);
            pluginCommand.setPermission("battletracker.command." + commandName);

            Bukkit.getCommandMap().register(commandName, "battletracker", pluginCommand);
            return pluginCommand;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException("Failed to construct PluginCommand " + headerName, e);
        }
    }
}