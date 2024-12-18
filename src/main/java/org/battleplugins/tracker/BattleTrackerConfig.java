package org.battleplugins.tracker;

import org.battleplugins.tracker.sql.SqlSerializer;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

public class BattleTrackerConfig {
    private final DatabaseOptions databaseOptions;
    private final Rating rating;
    private final Advanced advanced;

    BattleTrackerConfig(DatabaseOptions databaseOptions, Rating rating, Advanced advanced) {
        this.databaseOptions = databaseOptions;
        this.rating = rating;
        this.advanced = advanced;
    }

    public DatabaseOptions getDatabaseOptions() {
        return this.databaseOptions;
    }

    public Rating getRating() {
        return this.rating;
    }

    public Advanced getAdvanced() {
        return this.advanced;
    }

    static BattleTrackerConfig load(Configuration configuration) {
        ConfigurationSection dbSection = configuration.getConfigurationSection("database");
        if (dbSection == null) {
            throw new IllegalArgumentException("Database section not found in configuration!");
        }

        ConfigurationSection ratingSection = configuration.getConfigurationSection("rating");
        if (ratingSection == null) {
            throw new IllegalArgumentException("Rating section not found in configuration!");
        }

        ConfigurationSection advancedSection = configuration.getConfigurationSection("advanced");
        if (advancedSection == null) {
            throw new IllegalArgumentException("Advanced section not found in configuration!");
        }

        return new BattleTrackerConfig(DatabaseOptions.load(dbSection), Rating.load(ratingSection), Advanced.load(advancedSection));
    }

    public record DatabaseOptions(
            SqlSerializer.SqlType type,
            String prefix,
            String db,
            String url,
            String port,
            String user,
            String password
    ) {

        public static DatabaseOptions load(ConfigurationSection section) {
            SqlSerializer.SqlType type = SqlSerializer.SqlType.valueOf(section.getString("type").toUpperCase(Locale.ROOT));
            String prefix = section.getString("prefix");
            String db = section.getString("db");
            String url = section.getString("url");
            String port = section.getString("port");
            String user = section.getString("username");
            String password = section.getString("password");
            return new DatabaseOptions(type, prefix, db, url, port, user, password);
        }
    }

    public record Rating(Elo elo) {

        public static Rating load(ConfigurationSection section) {
            ConfigurationSection eloSection = section.getConfigurationSection("elo");
            Elo elo = new Elo(
                    (float) eloSection.getDouble("default"),
                    (float) eloSection.getDouble("spread")
            );

            return new Rating(elo);
        }
    }

    public record Elo(float defaultElo, float spread) {
    }

    public record Advanced(boolean flushOnLeave, int saveInterval, int staleEntryTime) {

        public static Advanced load(ConfigurationSection section) {
            boolean flushOnLeave = section.getBoolean("flush-on-leave");
            int saveInterval = section.getInt("save-interval");
            int staleEntryTime = section.getInt("stale-entry-time");
            return new Advanced(flushOnLeave, saveInterval, staleEntryTime);
        }
    }
}
