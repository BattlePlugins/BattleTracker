package org.battleplugins.tracker;

import org.battleplugins.tracker.event.BattleTrackerPreInitializeEvent;
import org.battleplugins.tracker.feature.Feature;
import org.battleplugins.tracker.feature.battlearena.BattleArenaFeature;
import org.battleplugins.tracker.feature.combatlog.CombatLog;
import org.battleplugins.tracker.feature.damageindicators.DamageIndicators;
import org.battleplugins.tracker.message.Messages;
import org.battleplugins.tracker.sql.SqlSerializer;
import org.battleplugins.tracker.sql.TrackerSqlSerializer;
import org.battleplugins.tracker.stat.calculator.EloCalculator;
import org.battleplugins.tracker.stat.calculator.RatingCalculator;
import org.battleplugins.tracker.util.CommandInjector;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The main class for BattleTracker.
 */
public class BattleTracker extends JavaPlugin {
    private static final int PLUGIN_ID = 4598;

    private static BattleTracker instance;

    final Map<String, Supplier<Tracker>> trackerSuppliers = new HashMap<>();

    private final Map<String, TrackerLoader> trackerLoaders = new HashMap<>();

    private final Map<String, Tracker> trackers = new ConcurrentHashMap<>();
    private final Map<String, RatingCalculator> ratingCalculators = new HashMap<>();
    private final Map<Tracker, List<Listener>> trackerListeners = new HashMap<>();

    private BattleArenaFeature battleArenaFeature;
    private CombatLog combatLog;
    private DamageIndicators damageIndicators;

    private BattleTrackerConfig config;

    private Path trackersPath;
    private Path featuresPath;

    private boolean debugMode;

    @Override
    public void onLoad() {
        instance = this;

        this.loadConfig(false);

        Path dataFolder = this.getDataFolder().toPath();
        this.trackersPath = dataFolder.resolve("trackers");
        this.featuresPath = dataFolder.resolve("features");

        new BattleTrackerPreInitializeEvent(this).callEvent();
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(new BattleTrackerListener(this), this);

        // Register default calculators
        this.registerCalculator(new EloCalculator(this.config.getRating().elo()));

        this.enable();

        // Loads all tracker loaders
        this.loadTrackerLoaders(this.trackersPath);

        if (this.config.getAdvanced().saveInterval() != -1) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                this.debug("Auto save: Saving all trackers.");
                for (Tracker tracker : this.trackers.values()) {
                    long startTIme = System.currentTimeMillis();
                    tracker.saveAll().whenComplete((aVoid, e) -> {
                        if (e != null) {
                            this.error("Error saving tracker {}!", tracker.getName(), e);
                        } else {
                            this.debug("Auto save: Saved tracker {} in {}ms.", tracker.getName(), System.currentTimeMillis() - startTIme);
                        }

                        tracker.flush(false);

                        this.debug("Auto save: Flushed tracker {}.", tracker.getName());
                    });
                }

                this.debug("Auto save: Finished saving all trackers.");
            }, this.config.getAdvanced().saveInterval() * 20L, this.config.getAdvanced().saveInterval() * 20L);
        }

        new Metrics(this, PLUGIN_ID);
    }

    private void enable() {
        if (Files.notExists(this.trackersPath)) {
            try {
                Files.createDirectories(this.trackersPath);
            } catch (IOException e) {
                throw new RuntimeException("Error creating trackers directory!", e);
            }

            this.saveResource("trackers/pve.yml", false);
            this.saveResource("trackers/pvp.yml", false);
        }

        if (Files.notExists(this.featuresPath)) {
            try {
                Files.createDirectories(this.featuresPath);
            } catch (IOException e) {
                throw new RuntimeException("Error creating features directory!", e);
            }

            this.saveResource("features/combat-log.yml", false);
            this.saveResource("features/damage-indicators.yml", false);
        }

        Path dataFolder = this.getDataFolder().toPath();
        if (Files.notExists(dataFolder.resolve("messages.yml"))) {
            this.saveResource("messages.yml", false);
        }

        this.battleArenaFeature = new BattleArenaFeature();
        this.battleArenaFeature.onEnable(this);
    }

    @Override
    public void onDisable() {
        this.disable(true).whenComplete((aVoid, e) -> {
            if (e != null) {
                this.error("Error disabling BattleTracker!", e);
            }
        });
    }

    private CompletableFuture<Void> disable(boolean block) {
        if (this.battleArenaFeature != null) {
            this.unloadFeature(this.battleArenaFeature);
        }

        if (this.combatLog != null) {
            this.unloadFeature(this.combatLog);
        }

        if (this.damageIndicators != null) {
            this.unloadFeature(this.damageIndicators);
        }

        // Save all trackers
        List<CompletableFuture<Void>> saveFutures = new ArrayList<>();
        for (Tracker tracker : this.trackers.values()) {
            List<Listener> listeners = this.trackerListeners.remove(tracker);
            if (listeners != null && !listeners.isEmpty()) {
                listeners.forEach(HandlerList::unregisterAll);
            }

            saveFutures.add(tracker.saveAll().thenRun(tracker::destroy));
        }

        CompletableFuture<Void> future = CompletableFuture.allOf(saveFutures.toArray(CompletableFuture[]::new))
                .thenRun(this.trackers::clear);

        if (block) {
            future.join();
        }

        return future;
    }

    void postInitialize() {
        Messages.load(this.getDataFolder().toPath().resolve("messages.yml"));

        this.loadTrackers();

        this.combatLog = this.loadFeature(this.featuresPath.resolve("combat-log.yml"), CombatLog::load);
        this.damageIndicators = this.loadFeature(this.featuresPath.resolve("damage-indicators.yml"), DamageIndicators::load);
    }

    /**
     * Reloads the plugin.
     */
    public void reload() {
        this.disable(false).whenCompleteAsync((aVoid, e) -> {
            if (e != null) {
                this.error("Error disabling plugin!", e);
            }

            // Reload the config
            this.loadConfig(true);

            this.enable();
            this.postInitialize();
        }, Bukkit.getScheduler().getMainThreadExecutor(this));
    }

    <T extends Feature> T loadFeature(Path path, Function<ConfigurationSection, T> featureLoader) {
        Configuration configuration = YamlConfiguration.loadConfiguration(path.toFile());
        T feature = featureLoader.apply(configuration);
        if (feature.enabled()) {
            feature.onEnable(this);
        }

        return feature;
    }

    void unloadFeature(Feature feature) {
        if (feature.enabled()) {
            feature.onDisable(this);
        }
    }

    /**
     * Returns an in-memory representation of the configuration.
     *
     * @return the BattleTracker configuration
     */
    public BattleTrackerConfig getMainConfig() {
        return config;
    }

    /**
     * Returns the rating calculator with the given name.
     *
     * @param name the name of the rating calculator
     * @return the rating calculator with the given name
     */
    @Nullable
    public RatingCalculator getCalculator(String name) {
        return this.ratingCalculators.get(name);
    }

    /**
     * Registers a {@link RatingCalculator}.
     *
     * @param calculator the rating calculator to register
     */
    public void registerCalculator(RatingCalculator calculator) {
        this.ratingCalculators.put(calculator.getName(), calculator);
    }

    void registerTracker(Tracker tracker) {
        this.trackers.put(tracker.getName(), tracker);
    }

    /**
     * Registers a {@link Listener} to a {@link Tracker}.
     *
     * @param tracker the tracker to register the listener to
     * @param listener the listener to register
     */
    public void registerListener(Tracker tracker, Listener listener) {
        this.trackerListeners.computeIfAbsent(tracker, key -> new ArrayList<>()).add(listener);

        Bukkit.getPluginManager().registerEvents(listener, this);
    }

    /**
     * Registers a {@link Tracker} supplier.
     *
     * @param name the name of the tracker supplier to register
     * @param trackerSupplier the tracker supplier to register
     */
    public void registerTracker(String name, Supplier<Tracker> trackerSupplier) {
        this.trackerSuppliers.put(name, trackerSupplier);
    }

    /**
     * Returns the {@link Tracker} with the given name.
     *
     * @param name the name of the tracker
     * @return the tracker with the given name
     */
    public Optional<Tracker> tracker(String name) {
        return Optional.ofNullable(this.getTracker(name));
    }

    /**
     * Returns the {@link Tracker} with the given name,
     * or null if the tracker does not exist.
     *
     * @param name the name of the tracker
     * @return the tracker with the given name
     */
    @Nullable
    public Tracker getTracker(String name) {
        return this.trackers.get(name);
    }

    /**
     * Returns all the {@link Tracker}s associated with the plugin.
     *
     * @return all the trackers associated with the plugin
     */
    public List<Tracker> getTrackers() {
        return List.copyOf(this.trackers.values());
    }

    /**
     * Returns the {@link CombatLog} feature.
     *
     * @return the CombatLog feature
     */
    public CombatLog getCombatLog() {
        return this.combatLog;
    }

    /**
     * Returns whether the plugin is in debug mode.
     *
     * @return whether the plugin is in debug mode
     */
    public boolean isDebugMode() {
        return this.debugMode;
    }

    /**
     * Sets whether the plugin is in debug mode.
     *
     * @param debugMode whether the plugin is in debug mode
     */
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public void info(String message) {
        this.getSLF4JLogger().info(message);
    }

    public void info(String message, Object... args) {
        this.getSLF4JLogger().info(message, args);
    }

    public void error(String message) {
        this.getSLF4JLogger().error(message);
    }

    public void error(String message, Object... args) {
        this.getSLF4JLogger().error(message, args);
    }

    public void warn(String message) {
        this.getSLF4JLogger().warn(message);
    }

    public void warn(String message, Object... args) {
        this.getSLF4JLogger().warn(message, args);
    }

    public void debug(String message, Object... args) {
        if (this.isDebugMode()) {
            this.getSLF4JLogger().info("[DEBUG] " + message, args);
        }
    }

    private void loadTrackerLoaders(Path path) {
        if (Files.notExists(path)) {
            return;
        }

        // Create tracker loaders
        try (Stream<Path> trackerPaths = Files.walk(path)) {
            trackerPaths.forEach(trackerPath -> {
                try {
                    if (Files.isDirectory(trackerPath)) {
                        return;
                    }

                    Configuration configuration = YamlConfiguration.loadConfiguration(Files.newBufferedReader(trackerPath));
                    String name = configuration.getString("name");
                    if (name == null) {
                        this.warn("Tracker {} does not have a name!", trackerPath.getFileName());
                        return;
                    }

                    TrackerLoader loader = new TrackerLoader(this, configuration, trackerPath);
                    this.trackerLoaders.put(name, loader);

                    CommandInjector.inject(name, name.toLowerCase(Locale.ROOT));
                } catch (IOException e) {
                    throw new RuntimeException("Error reading tracker config", e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Error walking trackers path!", e);
        }
    }

    private void loadTrackers() {
        // Register our trackers once ALL the plugins have loaded. This ensures that
        // all custom plugins adding their own trackers have been loaded.
        for (TrackerLoader value : this.trackerLoaders.values()) {
            try {
                value.load();
            } catch (Exception e) {
                this.error("An error occurred when loading tracker {}: {}", value.trackerPath().getFileName(), e.getMessage(), e);
            }
        }

        // Load custom trackers
        for (Map.Entry<String, Supplier<Tracker>> entry : this.trackerSuppliers.entrySet()) {
            Tracker tracker = entry.getValue().get();
            this.registerTracker(tracker);
        }
    }

    private void loadConfig(boolean reload) {
        this.saveDefaultConfig();

        File configFile = new File(this.getDataFolder(), "config.yml");
        Configuration config = YamlConfiguration.loadConfiguration(configFile);
        try {
            this.config = BattleTrackerConfig.load(config);
        } catch (Exception e) {
            this.error("Failed to load BattleTracker configuration!", e);
            if (!reload) {
                this.getServer().getPluginManager().disablePlugin(this);
            }
        }

        BattleTrackerConfig.DatabaseOptions databaseOptions = this.config.getDatabaseOptions();

        TrackerSqlSerializer.TYPE = databaseOptions.type();
        TrackerSqlSerializer.TABLE_PREFIX = databaseOptions.prefix();
        TrackerSqlSerializer.DATABASE = databaseOptions.db();
        TrackerSqlSerializer.PORT = databaseOptions.port();
        TrackerSqlSerializer.USERNAME = databaseOptions.user();
        TrackerSqlSerializer.PASSWORD = databaseOptions.password();

        if (databaseOptions.type() == SqlSerializer.SqlType.SQLITE) {
            TrackerSqlSerializer.URL = this.getDataFolder().toString();
        } else {
            TrackerSqlSerializer.URL = databaseOptions.url();
        }
    }

    /**
     * Returns the instance of the plugin.
     *
     * @return the instance of the plugin
     */
    public static BattleTracker getInstance() {
        return instance;
    }
}
