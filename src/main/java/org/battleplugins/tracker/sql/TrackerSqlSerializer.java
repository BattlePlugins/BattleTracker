package org.battleplugins.tracker.sql;

import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.SqlTracker;
import org.battleplugins.tracker.stat.Record;
import org.battleplugins.tracker.stat.StatType;
import org.battleplugins.tracker.stat.TallyEntry;
import org.battleplugins.tracker.stat.VersusTally;
import org.jetbrains.annotations.Blocking;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Main SQL serializer for Trackers.
 *
 * @author alkarin_v
 */
public class TrackerSqlSerializer extends SqlSerializer {

    public static String TABLE_PREFIX;

    public static String DATABASE;
    public static String URL;
    public static String PORT;
    public static String USERNAME;
    public static String PASSWORD;

    public static SqlType TYPE;

    private static final int MAX_LENGTH = 100;

    private final String overallTable;
    private final String tallyTable;
    private final String versusTable;

    private final SqlTracker tracker;

    private final List<String> overallColumns;
    private final List<String> versusColumns;

    public TrackerSqlSerializer(SqlTracker tracker) {
        this(
                tracker,
                List.of(StatType.KILLS, StatType.DEATHS, StatType.TIES, StatType.MAX_STREAK,
                        StatType.MAX_RANKING, StatType.RATING, StatType.MAX_RATING, StatType.MAX_KD_RATIO
                ),
                List.of(StatType.KILLS, StatType.DEATHS, StatType.TIES)
        );
    }

    public TrackerSqlSerializer(SqlTracker tracker, List<StatType> overallColumns, List<StatType> versusColumns) {
        this.overallColumns = overallColumns.stream().map(StatType::getKey).toList();
        this.versusColumns = versusColumns.stream().map(StatType::getKey).toList();

        this.tracker = tracker;

        this.overallTable = TABLE_PREFIX + tracker.getName().toLowerCase() + "_overall";
        this.tallyTable = TABLE_PREFIX + tracker.getName().toLowerCase() + "_tally";
        this.versusTable = TABLE_PREFIX + tracker.getName().toLowerCase() + "_versus";

        this.init();
    }

    @Override
    protected boolean init() {
        this.setDb(DATABASE);
        this.setType(TYPE);
        this.setUrl(URL);
        this.setPort(PORT);
        this.setUsername(USERNAME);
        this.setPassword(PASSWORD);

        super.init();

        this.setupOverallTable();
        this.setupVersusTable();
        this.setupTallyTable();

        return true;
    }

    public CompletableFuture<Record> loadRecord(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            ResultSetConnection connection = this.executeQuery("SELECT * FROM " + this.overallTable + " WHERE id = ?", uuid.toString());
            try {
                ResultSet resultSet = connection.rs();
                if (resultSet.next()) {
                    return this.createRecord(connection);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                this.closeConnection(connection);
            }

            return null;
        });
    }

    public CompletableFuture<List<Record>> getTopRecords(int limit, StatType orderBy) {
        return CompletableFuture.supplyAsync(() -> {
            List<Record> records = new ArrayList<>();
            ResultSetConnection connection = this.executeQuery("SELECT * FROM " + this.overallTable + " ORDER BY CAST(" + orderBy.getKey() + " AS REAL) DESC LIMIT ?", limit);
            try {
                ResultSet resultSet = connection.rs();
                while (resultSet.next()) {
                    records.add(this.createRecord(connection));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                this.closeConnection(connection);
            }

            return records;
        });
    }

    @Blocking
    public Record createRecord(ResultSetConnection connection) throws SQLException {
        ResultSet resultSet = connection.rs();
        Map<StatType, Float> columns = new HashMap<>();
        for (String column : this.overallColumns) {
            columns.put(StatType.get(column), Float.parseFloat(resultSet.getString(column)));
        }

        return new Record(this.tracker, UUID.fromString(resultSet.getString("id")), resultSet.getString("name"), columns);
    }

    public void removeRecord(UUID uuid) {
        this.executeUpdate(true, "DELETE FROM " + this.overallTable + " WHERE id = ?", uuid.toString());
    }

    public CompletableFuture<List<TallyEntry>> loadTallyEntries(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<TallyEntry> entries = new ArrayList<>();
            ResultSetConnection connection = this.executeQuery("SELECT * FROM " + this.tallyTable + " WHERE id1 = ? OR id2 = ?", uuid.toString(), uuid.toString());

            try {
                ResultSet resultSet = connection.rs();
                while (resultSet.next()) {
                    entries.add(this.createTallyEntry(connection));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                this.closeConnection(connection);
            }

            return entries;
        });
    }

    @Blocking
    private TallyEntry createTallyEntry(ResultSetConnection connection) throws SQLException {
        ResultSet resultSet = connection.rs();
        return new TallyEntry(
                UUID.fromString(resultSet.getString("id1")),
                UUID.fromString(resultSet.getString("id2")),
                resultSet.getBoolean("tie"),
                resultSet.getTimestamp("timestamp").toInstant()
        );
    }

    public CompletableFuture<VersusTally> loadVersusTally(UUID uuid1, UUID uuid2) {
        return CompletableFuture.supplyAsync(() -> {
            // Need to check if both id1 AND id2 = uuid1 or uuid2, or vice versa
            ResultSetConnection connection = this.executeQuery("SELECT * FROM " + this.versusTable + " WHERE (id1 = ? AND id2 = ?) OR (id1 = ? AND id2 = ?)", uuid1.toString(), uuid2.toString(), uuid2.toString(), uuid1.toString());

            try {
                ResultSet resultSet = connection.rs();
                if (resultSet.next()) {
                    return this.createVersusTally(connection);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                this.closeConnection(connection);
            }

            return null;
        });
    }

    @Blocking
    private VersusTally createVersusTally(ResultSetConnection connection) throws SQLException {
        ResultSet resultSet = connection.rs();
        Map<StatType, Float> columns = new HashMap<>();
        for (String column : this.versusColumns) {
            if (column.equalsIgnoreCase("infinity")) { // sometimes kdr gets saved as 'infinity'
                columns.put(StatType.get(column), Float.POSITIVE_INFINITY);
                continue;
            }

            columns.put(StatType.get(column), Float.parseFloat(resultSet.getString(column)));
        }

        return new VersusTally(this.tracker,
                UUID.fromString(resultSet.getString("id1")),
                UUID.fromString(resultSet.getString("id2")),
                columns
        );
    }

    public CompletableFuture<Void> save(UUID uuid) {
        return this.saveTotals(uuid);
    }

    public CompletableFuture<Void> saveAll() {
        return this.saveTotals(this.tracker.getRecords().keySet().toArray(UUID[]::new));
    }

    public CompletableFuture<Void> saveTotals(UUID... uuids) {
        if (uuids == null || uuids.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        List<List<Object>> overallBatch = new ArrayList<>();
        List<List<Object>> versusBatch = new ArrayList<>();
        List<List<Object>> tallyBatch = new ArrayList<>();

        List<CompletableFuture<Void>> batches = new ArrayList<>();
        for (UUID uuid : uuids) {
            Record record = this.tracker.getRecords().getCached(uuid);
            if (record == null) {
                BattleTracker.getInstance().warn("Failed to save record for " + uuid + " as they had no record saved.");
                continue;
            }

            // +2 in array for name and id
            String[] overallObjectArray = new String[this.overallColumns.size() + 2];
            overallObjectArray[0] = record.getId().toString();
            overallObjectArray[1] = record.getName();
            for (int i = 0; i < this.overallColumns.size(); i++) {
                String overallColumn = this.overallColumns.get(i);
                overallObjectArray[i + 2] = record.getStatistics().get(StatType.get(overallColumn)).toString();
            }

            overallBatch.add(List.of(overallObjectArray));
            this.executeBatch(true, this.constructInsertOverallStatement(), overallBatch);

            this.tracker.getTallies().save(versusTally -> {
                if (!versusTally.id1().equals(uuid) && !versusTally.id2().equals(uuid)) {
                    return;
                }

                // +4 in array for double name and id
                String[] versusObjectArray = new String[this.versusColumns.size() + 2];
                versusObjectArray[0] = versusTally.id1().toString();
                versusObjectArray[1] = versusTally.id2().toString();

                for (int i = 0; i < this.versusColumns.size(); i++) {
                    String versusColumn = this.versusColumns.get(i);
                    versusObjectArray[i + 2] = Optional.ofNullable(versusTally.statistics().get(StatType.get(versusColumn))).orElse(0f).toString();
                }

                versusBatch.add(List.of(versusObjectArray));
                batches.add(this.executeBatch(true, this.constructInsertVersusStatement(), versusBatch));
            });

            this.tracker.getTallyEntries().save(uuid, entry -> {
                String[] tallyObjectArray = new String[4];
                tallyObjectArray[0] = entry.id1().toString();
                tallyObjectArray[1] = entry.id2().toString();
                tallyObjectArray[2] = Boolean.toString(entry.tie());
                tallyObjectArray[3] = Long.toString(entry.timestamp().toEpochMilli());

                tallyBatch.add(List.of(tallyObjectArray));
                batches.add(this.executeBatch(true, this.constructInsertTallyStatement(), tallyBatch));
            });
        }

        return CompletableFuture.allOf(batches.toArray(CompletableFuture[]::new));
    }

    public List<String> getOverallColumns() {
        return this.overallColumns;
    }

    private String constructInsertOverallStatement() {
        StringBuilder builder = new StringBuilder();
        switch (this.getType()) {
            case MYSQL:
                String insertOverall = "INSERT INTO " + this.overallTable + " VALUES (?, ?, ";
                builder.append(insertOverall);
                for (int i = 0; i < this.overallColumns.size(); i++) {
                    if ((i + 1) < this.overallColumns.size()) {
                        builder.append("?, ");
                    } else {
                        builder.append("?)");
                    }
                }

                builder.append(" ON DUPLICATE KEY UPDATE ");
                builder.append("id = VALUES(id), ");
                builder.append("name = VALUES(name), ");
                for (int i = 0; i < this.overallColumns.size(); i++) {
                    if ((i + 1) < this.overallColumns.size()) {
                        builder.append(this.overallColumns.get(i)).append(" = VALUES(").append(this.overallColumns.get(i)).append("), ");
                    } else {
                        builder.append(this.overallColumns.get(i)).append(" = VALUES(").append(this.overallColumns.get(i)).append(")");
                    }
                }

                break;
            case SQLITE:
                builder.append("INSERT OR REPLACE INTO ").append(this.overallTable).append(" VALUES (");
                builder.append("?, ");
                builder.append("?, ");
                for (int i = 0; i < this.overallColumns.size(); i++) {
                    if ((i + 1) < this.overallColumns.size()) {
                        builder.append("?, ");
                    } else {
                        builder.append("?)");
                    }
                }

                break;
        }

        return builder.toString();
    }

    private String constructInsertVersusStatement() {
        StringBuilder builder = new StringBuilder();
        switch (this.getType()) {
            case MYSQL:
                String insertOverall = "INSERT INTO " + this.versusTable + " VALUES (?, ?, ?, ?, ";
                builder.append(insertOverall);
                for (int i = 0; i < this.versusColumns.size(); i++) {
                    if ((i + 1) < this.versusColumns.size()) {
                        builder.append("?, ");
                    } else {
                        builder.append("?)");
                    }
                }

                builder.append(" ON DUPLICATE KEY UPDATE ");
                builder.append("id1 = VALUES(id1), ");
                builder.append("id2 = VALUES(id2), ");
                for (int i = 0; i < this.versusColumns.size(); i++) {
                    if ((i + 1) < this.versusColumns.size()) {
                        builder.append(this.versusColumns.get(i)).append(" = VALUES(").append(this.versusColumns.get(i)).append("), ");
                    } else {
                        builder.append(this.versusColumns.get(i)).append(" = VALUES(").append(this.versusColumns.get(i)).append(")");
                    }
                }
                break;
            case SQLITE:
                builder.append("INSERT OR REPLACE INTO ").append(this.versusTable).append(" VALUES (");
                builder.append("?, ");
                builder.append("?, ");
                for (int i = 0; i < this.versusColumns.size(); i++) {
                    if ((i + 1) < this.versusColumns.size()) {
                        builder.append("?, ");
                    } else {
                        builder.append("?)");
                    }
                }

                break;
        }

        return builder.toString();
    }

    private String constructInsertTallyStatement() {
        return switch (this.getType()) {
            case MYSQL -> "INSERT INTO " + this.tallyTable + " VALUES (?, ?, ?, ?)";
            case SQLITE -> "INSERT OR REPLACE INTO " + this.tallyTable + " VALUES (?, ?, ?, ?)";
        };
    }

    @Blocking
    private void setupOverallTable() {
        String createOverall = "CREATE TABLE IF NOT EXISTS " + this.overallTable + " ("
                + "id VARCHAR(" + MAX_LENGTH + "), name VARCHAR(" + MAX_LENGTH + "), ";

        StringBuilder createStringBuilder = new StringBuilder();
        createStringBuilder.append(createOverall);
        for (String column : this.overallColumns) {
            createStringBuilder.append(column).append(" VARCHAR(").append(MAX_LENGTH).append("), ");
        }

        createStringBuilder.append(" PRIMARY KEY (id))");

        try {
            this.createTable(this.overallTable, createStringBuilder.toString());
        } catch (Exception e) {
            // Log.err("Failed to create tables!");
            e.printStackTrace();
        }
    }

    @Blocking
    private void setupVersusTable() {
        String createVersus = "CREATE TABLE IF NOT EXISTS " + this.versusTable + "(" +
                "id1 VARCHAR (" + MAX_LENGTH + ") NOT NULL," +
                "id2 VARCHAR (" + MAX_LENGTH + ") NOT NULL, ";

        StringBuilder createStringBuilder = new StringBuilder();
        createStringBuilder.append(createVersus);
        for (String column : this.versusColumns) {
            createStringBuilder.append(column)
                    .append(" VARCHAR(").append(MAX_LENGTH).append("), ");
        }

        createStringBuilder.append(" PRIMARY KEY (id1, id2))");

        try {
            this.createTable(this.versusTable, createStringBuilder.toString());
        } catch (Exception e) {
            // Log.err("Failed to create tables!");
            e.printStackTrace();
        }
    }

    @Blocking
    private void setupTallyTable() {
        String createTally = "CREATE TABLE IF NOT EXISTS " + this.tallyTable + "(" +
                "id1 VARCHAR (" + MAX_LENGTH + ") NOT NULL, " +
                "id2 VARCHAR (" + MAX_LENGTH + ") NOT NULL, " +
                "tie BOOLEAN DEFAULT FALSE, " +
                "timestamp TIMESTAMP NOT NULL, " +
                "PRIMARY KEY (id1, id2, timestamp)";

        if (this.type == SqlType.MYSQL) {
            createTally += ", INDEX (id1), INDEX (id2))";
        } else {
            createTally += ")";
        }

        try {
            this.createTable(this.tallyTable, createTally);
            if (this.type == SqlType.SQLITE) {
                this.executeUpdate("CREATE INDEX IF NOT EXISTS id1_index ON " + this.tallyTable + " (id1)");
                this.executeUpdate("CREATE INDEX IF NOT EXISTS id2_index ON " + this.tallyTable + " (id2)");
            }
        } catch (Exception e) {
            // Log.err("Failed to create tables!");
            e.printStackTrace();
        }
    }

    private static <T> CompletableFuture<T> supplyAsync(SqlSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public interface SqlSupplier<T> {

        T get() throws SQLException;
    }
}
