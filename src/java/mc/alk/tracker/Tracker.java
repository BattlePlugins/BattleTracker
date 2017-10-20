package mc.alk.tracker;

import mc.alk.plugin.updater.PluginUpdater;
import mc.alk.tracker.controllers.ConfigController;
import mc.alk.tracker.controllers.MessageController;
import mc.alk.tracker.controllers.SignController;
import mc.alk.tracker.controllers.TrackerController;
import mc.alk.tracker.controllers.TrackerImpl;
import mc.alk.tracker.controllers.TrackerImpl.DBConnectionException;
import mc.alk.tracker.executors.BattleTrackerExecutor;
import mc.alk.tracker.executors.TrackerExecutor;
import mc.alk.tracker.listeners.BTEntityListener;
import mc.alk.tracker.listeners.BTPluginListener;
import mc.alk.tracker.listeners.SignListener;
import mc.alk.tracker.objects.StatSign;
import mc.alk.tracker.serializers.SignSerializer;
import mc.alk.tracker.serializers.YamlConfigUpdater;
import mc.alk.tracker.serializers.YamlMessageUpdater;
import mc.alk.v1r7.core.MCPlugin;
import mc.alk.v1r7.core.Version;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The main class file of BattleTracker.
 */

public class Tracker extends MCPlugin {

    static Tracker plugin;
    TrackerController sc;
    final static Map<String, TrackerInterface> interfaces = Collections.synchronizedMap(
            new ConcurrentHashMap<String, TrackerInterface>());
    static SignController signController = new SignController();
    SignSerializer signSerializer;
    // The bukkit id for this project. https://api.curseforge.com/servermods/projects?search=battletracker
    static final int bukkitId = 43688;

    @Override
    public void onEnable() {
        super.onEnable();
        plugin = this;
        plugin.setEnabled(true);
        createPluginFolder();
        loadConfigs();

        Bukkit.getPluginManager().registerEvents(new BTEntityListener(), this);
        Bukkit.getPluginManager().registerEvents(new BTPluginListener(), this);

        getCommand("battleTracker").setExecutor(new BattleTrackerExecutor());
        getCommand("btpvp").setExecutor(new TrackerExecutor(getInterface(Defaults.PVP_INTERFACE)));
        getCommand("btpve").setExecutor(new TrackerExecutor(getInterface(Defaults.PVE_INTERFACE)));

        PluginUpdater.update(this, bukkitId, this.getFile(), Defaults.AUTO_UPDATE, Defaults.ANNOUNCE_UPDATE);
        BTPluginListener.loadPlugins();
    }

    @Override
    public void onDisable() {
        plugin.setEnabled(false);

        saveConfig();
    }

    @Override
    public void onLoad() {
        ConfigurationSerialization.registerClass(StatSign.class);
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        this.loadConfigs();
    }

    public void loadConfigs() {
        if (!plugin.isEnabled()) {
            return;
        }
        /// Load
        ConfigController.setConfig(load("/default_files/config.yml", getDataFolder().getPath() + "/config.yml"));
        MessageController.setConfig(load("/default_files/messages.yml", getDataFolder().getPath() + "/messages.yml"));
		/// on some servers with non bukkit worlds, this is too quick. delay this till after all plugins
        /// are loaded
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            public void run() {
                signSerializer = new SignSerializer(signController);
                signSerializer.setConfig(getDataFolder().getPath() + "/signs.yml");
                signSerializer.loadAll();
            }
        }, 22);

        /// Update
        YamlConfigUpdater cu = new YamlConfigUpdater();
        cu.update(ConfigController.getConfig(), ConfigController.getFile());

        YamlMessageUpdater mu = new YamlMessageUpdater();
        mu.update(MessageController.getConfig(), MessageController.getFile());

        /// Reload
        ConfigController.setConfig(ConfigController.getFile());
        MessageController.setConfig(MessageController.getFile());

        if (Defaults.USE_SIGNS) {
            getServer().getPluginManager().registerEvents(new SignListener(signController), this);

            Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
                public void run() {
                    signController.updateSigns();
                }
            }, 20, 1000);
        }
    }

    public static Tracker getSelf() {
        return plugin;
    }

    @Override
    public void saveConfig() {
        super.saveConfig();
        synchronized (interfaces) {
            for (TrackerInterface ti : interfaces.values()) {
                ti.flush();
            }
        }
        /// can happen if tracker never loads properly (like starting and immediately stopping)
        if (signSerializer != null) {
            signSerializer.saveAll();
        }
    }

    public static TrackerInterface getPVPInterface() {
        return getInterface(Defaults.PVP_INTERFACE, new TrackerOptions());
    }

    public static TrackerInterface getPVEInterface() {
        return getInterface(Defaults.PVE_INTERFACE, new TrackerOptions());
    }

    public static TrackerInterface getInterface(String interfaceName) {
        return getInterface(interfaceName, new TrackerOptions());
    }

    public static TrackerInterface getInterface(String interfaceName, TrackerOptions trackerOptions) {
        try {
            String iname = interfaceName.toLowerCase();
            if (!interfaces.containsKey(iname)) {
                interfaces.put(iname, new TrackerImpl(interfaceName, trackerOptions));
            }
            return interfaces.get(iname);
        } catch (DBConnectionException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Version getVersionObject() {
        String strv = getSelf().getDescription().getVersion();
        return new Version(strv);
    }

    public static boolean hasInterface(String interfaceName) {
        String iname = interfaceName.toLowerCase();
        return interfaces.containsKey(iname);
    }

    public static Collection<TrackerInterface> getAllInterfaces() {
        return new ArrayList<TrackerInterface>(interfaces.values());
    }
}
