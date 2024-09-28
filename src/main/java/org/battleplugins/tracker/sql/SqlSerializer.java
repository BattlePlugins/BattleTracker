package org.battleplugins.tracker.sql;

import org.battleplugins.tracker.BattleTracker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Handles serializing SQL data to a database.
 *
 * @author alkarin_v
 */
public abstract class SqlSerializer {
    protected static final int TIMEOUT = 5;

    public enum SqlType {
        MYSQL("MySQL", "com.mysql.jdbc.Driver"),
        SQLITE("SQLite", "org.sqlite.JDBC");

        private final String name;
        private final String driver;

        SqlType(String name, String driver) {
            this.name = name;
            this.driver = driver;
        }

        public String getName() {
            return this.name;
        }

        public String getDriver() {
            return this.driver;
        }
    }

    public SqlSerializer() {
    }

    public record ResultSetConnection(ResultSet rs, Connection con) {
    }

    public SqlType getType() {
        return SqlInstance.getInstance().getType();
    }

    public Connection getConnection() {
        return this.getConnection(true, true);
    }

    public Connection getConnection(boolean displayErrors, boolean autoCommit) {
        try {
            Connection connection = SqlInstance.getInstance().getDataSource().getConnection();
            connection.setAutoCommit(autoCommit);
            return connection;
        } catch (SQLException e) {
            if (displayErrors) {
                BattleTracker.getInstance().error("Could not get connection to SQL database", e);
            }

            return null;
        }
    }

    public void closeConnection(ResultSetConnection rscon) {
        if (rscon == null || rscon.con == null) {
            return;
        }

        try {
            rscon.con.close();
        } catch (SQLException e) {
            BattleTracker.getInstance().error("Could not close connection to SQL database", e);
        }
    }

    public void closeConnection(Connection con) {
        if (con == null) {
            return;
        }

        try {
            con.close();
        } catch (SQLException e) {
            BattleTracker.getInstance().error("Could not close connection to SQL database", e);
        }
    }

    protected boolean init() {
        return true;
    }

    protected boolean createTable(String tableName, String sqlCreateTable, String... sqlUpdates) {
        // Check to see if our table exists;
        Boolean exists;
        if (SqlInstance.getInstance().getType() == SqlType.SQLITE) {
            exists = this.getBoolean("SELECT count(name) FROM sqlite_master WHERE type='table' AND name='" + tableName + "';");
        } else {
            List<Object> objs = this.getObjects("SHOW TABLES LIKE '" + tableName + "';");
            exists = objs != null && objs.size() == 1;
        }
        
        if (exists != null && exists) {
            return true; // If the table exists nothing left to do
        }

        // Create our table and index
        String strStmt = sqlCreateTable;
        Statement statement;
        int result = 0;
        Connection connection = this.getConnection();

        try {
            statement = connection.createStatement();
            result = statement.executeUpdate(strStmt);
        } catch (SQLException e) {
            BattleTracker.getInstance().error("Could not create table {} (error result: {})", tableName, result, e);
            this.closeConnection(connection);
            return false;
        }
        // Updates and indexes
        if (sqlUpdates != null) {
            for (String sqlUpdate : sqlUpdates) {
                if (sqlUpdate == null) {
                    continue;
                }

                strStmt = sqlUpdate;
                try {
                    statement = connection.createStatement();
                    result = statement.executeUpdate(strStmt);
                } catch (SQLException e) {
                    BattleTracker.getInstance().error("Could not create table {} (error result: {})", tableName, result, e);
                    this.closeConnection(connection);
                    return false;
                }
            }
        }

        this.closeConnection(connection);
        return true;
    }

    /**
     * Check to see whether the database has a particular column
     *
     * @param table the table to check
     * @param column the column to check
     * @return Boolean: whether the column exists
     */
    protected Boolean hasColumn(String table, String column) {
        String statement;
        Boolean columnExists;
        SqlType type = SqlInstance.getInstance().getType();
        switch (type) {
            case MYSQL:
                statement = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? " +
                        "AND TABLE_NAME = ? AND COLUMN_NAME = ?";
                columnExists = this.getBoolean(true, 2, statement, SqlInstance.getInstance().getDatabase(), table, column);
                return columnExists != null && columnExists;
            case SQLITE:
                // After hours, I have discovered that SQL can NOT bind tables...
                // so explicitly put in the table.
                // UPDATE: on Windows machines you need to explicitly put in the column too...
                statement = "SELECT COUNT(" + column + ") FROM '" + table + "'";
                try {
                    columnExists = this.getBoolean(false, 2, statement);
                    // If we got any non error response... we have the table
                    return columnExists != null;
                } catch (Exception e) {
                    return false;
                }
        }
        return false;
    }

    protected Boolean hasTable(String tableName) {
        Boolean exists;
        if (SqlInstance.getInstance().getType() == SqlType.SQLITE) {
            exists = this.getBoolean("SELECT count(name) FROM sqlite_master WHERE type='table' AND name='" + tableName + "'");
        } else {
            List<Object> objs = this.getObjects("SHOW TABLES LIKE '" + tableName + "';");
            exists = objs != null && objs.size() == 1;
        }
        return exists;
    }

    protected ResultSetConnection executeQuery(String strRawStmt, Object... varArgs) {
        return this.executeQuery(true, TIMEOUT, strRawStmt, varArgs);
    }

    /**
     * Execute the given query
     *
     * @param strRawStmt the raw statement to execute
     * @param varArgs the arguments to pass into the statement
     * @return the ResultSetConnection
     */
    protected ResultSetConnection executeQuery(boolean displayErrors, Integer timeoutSeconds, String strRawStmt, Object... varArgs) {
        return this.executeQuery(this.getConnection(), displayErrors, timeoutSeconds, strRawStmt, varArgs);
    }

    /**
     * Execute the given query
     *
     * @param strRawStmt the raw statement to execute
     * @param varArgs the arguments to pass into the statement
     * @return the ResultSetConnection
     */
    protected ResultSetConnection executeQuery(Connection con, boolean displayErrors, Integer timeoutSeconds,
                                               String strRawStmt, Object... varArgs) {
        PreparedStatement statement;
        ResultSetConnection result = null;

        try {
            statement = getStatement(displayErrors, strRawStmt, con, varArgs);
            statement.setQueryTimeout(timeoutSeconds);
            ResultSet rs = statement.executeQuery();
            result = new ResultSetConnection(rs, con);
        } catch (Exception e) {
            if (displayErrors) {
                BattleTracker.getInstance().error("Could not execute query {}", strRawStmt, e);
            }
        }
        return result;
    }

    protected void executeUpdate(boolean async, String strRawStmt, Object... varArgs) {
        if (async) {
            CompletableFuture.runAsync(() -> {
                try {
                    this.executeUpdate(strRawStmt, varArgs);
                } catch (Exception e) {
                    BattleTracker.getInstance().error("Could not execute update {}", strRawStmt, e);
                }
            });
        } else {
            try {
                this.executeUpdate(strRawStmt, varArgs);
            } catch (Exception e) {
                BattleTracker.getInstance().error("Could not execute update {}", strRawStmt, e);
            }
        }
    }

    protected int executeUpdate(String strRawStmt, Object... varArgs) {
        int result = -1;
        Connection connection = this.getConnection();

        PreparedStatement ps;
        try {
            ps = this.getStatement(strRawStmt, connection, varArgs);
            result = ps.executeUpdate();
        } catch (Exception e) {
            BattleTracker.getInstance().error("Could not execute update {} (result: {})", strRawStmt, result, e);
        } finally {
            this.closeConnection(connection);
        }

        return result;
    }

    protected CompletableFuture<Void> executeBatch(boolean async, String updateStatement, List<List<Object>> batch) {
        CompletableFuture<Void> future;
        if (async) {
            future = CompletableFuture.runAsync(() -> this.executeBatch(updateStatement, batch));
        } else {
            future = new CompletableFuture<>();
            try {
                this.executeBatch(updateStatement, batch);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }

        return future;
    }

    protected void executeBatch(String updateStatement, List<List<Object>> batch) {
        Connection con = this.getConnection();
        PreparedStatement ps = null;
        try {
            con.setAutoCommit(false);
        } catch (Exception e) {
            BattleTracker.getInstance().error("Could not set auto commit to false", e);
        }
        try {
            ps = con.prepareStatement(updateStatement);
        } catch (Exception e) {
            BattleTracker.getInstance().error("Could not prepare statement {}", updateStatement, e);
        }

        for (List<Object> update : batch) {
            try {
                for (int i = 0; i < update.size(); i++) {
                    ps.setObject(i + 1, update.get(i));
                }
                ps.addBatch();
            } catch (Exception e) {
                BattleTracker.getInstance().error("Could not add batch {} (statement: {})", update, ps, e);
            }
        }
        try {
            ps.executeBatch();
            con.commit();
        } catch (Exception e) {
            BattleTracker.getInstance().error("Could not execute batch {}", updateStatement, e);
        } finally {
            this.closeConnection(con);
        }
    }

    protected PreparedStatement getStatement(String strRawStmt, Connection con, Object... varArgs) {
        return this.getStatement(true, strRawStmt, con, varArgs);
    }

    protected PreparedStatement getStatement(boolean displayErrors, String strRawStmt, Connection con, Object... varArgs) {
        PreparedStatement ps = null;
        try {
            ps = con.prepareStatement(strRawStmt);
            for (int i = 0; i < varArgs.length; i++) {
                ps.setObject(i + 1, varArgs[i]);
            }
        } catch (Exception e) {
            if (displayErrors) {
                BattleTracker.getInstance().error("Could not prepare statement {}", strRawStmt, e);
            }
        }
        return ps;
    }

    public Double getDouble(String query, Object... varArgs) {
        ResultSetConnection rscon = this.executeQuery(query, varArgs);
        if (rscon == null || rscon.con == null) {
            return null;
        }

        try {
            ResultSet rs = rscon.rs;
            while (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            BattleTracker.getInstance().error("Could not get double", e);
        } finally {
            try {
                rscon.con.close();
            } catch (Exception e) {
                BattleTracker.getInstance().error("Could not close connection", e);
            }
        }
        return null;
    }

    public Integer getInteger(String query, Object... varArgs) {
        ResultSetConnection rscon = this.executeQuery(query, varArgs);
        if (rscon == null || rscon.con == null) {
            return null;
        }

        try {
            ResultSet rs = rscon.rs;
            while (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            BattleTracker.getInstance().error("Could not get integer", e);
        } finally {
            try {
                rscon.con.close();
            } catch (Exception e) {
                BattleTracker.getInstance().error("Could not close connection", e);
            }
        }
        return null;
    }

    public Short getShort(String query, Object... varArgs) {
        ResultSetConnection rscon = this.executeQuery(query, varArgs);
        if (rscon == null || rscon.con == null) {
            return null;
        }

        try {
            ResultSet rs = rscon.rs;
            while (rs.next()) {
                return rs.getShort(1);
            }
        } catch (SQLException e) {
            BattleTracker.getInstance().error("Could not get short", e);
        } finally {
            try {
                rscon.con.close();
            } catch (Exception e) {
                BattleTracker.getInstance().error("Could not close connection", e);
            }
        }
        return null;
    }

    public Long getLong(String query, Object... varArgs) {
        ResultSetConnection rscon = this.executeQuery(query, varArgs);
        if (rscon == null || rscon.con == null) {
            return null;
        }

        try {
            ResultSet rs = rscon.rs;
            while (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            BattleTracker.getInstance().error("Could not get long", e);
        } finally {
            try {
                rscon.con.close();
            } catch (Exception e) {
                BattleTracker.getInstance().error("Could not close connection", e);
            }
        }
        return null;
    }

    public Boolean getBoolean(String query, Object... varArgs) {
        return this.getBoolean(true, TIMEOUT, query, varArgs);
    }

    protected Boolean getBoolean(boolean displayErrors, Integer timeoutSeconds,
                                 String query, Object... varArgs) {
        ResultSetConnection rscon = this.executeQuery(displayErrors, timeoutSeconds, query, varArgs);
        if (rscon == null || rscon.con == null) {
            return null;
        }

        try {
            ResultSet rs = rscon.rs;
            while (rs.next()) {
                int i = rs.getInt(1);
                return i > 0;
            }
        } catch (SQLException e) {
            if (displayErrors) {
                BattleTracker.getInstance().error("Could not get boolean", e);
            }
        } finally {
            try {
                rscon.con.close();
            } catch (Exception e) {
                BattleTracker.getInstance().error("Could not close connection", e);
            }
        }
        return null;
    }

    public String getString(String query, Object... varArgs) {
        ResultSetConnection rscon = this.executeQuery(query, varArgs);
        if (rscon == null || rscon.con == null) {
            return null;
        }

        try {
            ResultSet rs = rscon.rs;
            while (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            BattleTracker.getInstance().error("Could not get string", e);
        } finally {
            try {
                rscon.con.close();
            } catch (Exception e) {
                BattleTracker.getInstance().error("Could not close connection", e);
            }
        }
        return null;
    }

    public List<Object> getObjects(String query, Object... varArgs) {
        ResultSetConnection rscon = this.executeQuery(query, varArgs);
        if (rscon == null || rscon.con == null) {
            return null;
        }

        try {
            ResultSet rs = rscon.rs;
            while (rs.next()) {
                java.sql.ResultSetMetaData rsmd = rs.getMetaData();
                int nCol = rsmd.getColumnCount();
                List<Object> objs = new ArrayList<>(nCol);
                for (int i = 0; i < nCol; i++) {
                    objs.add(rs.getObject(i + 1));
                }
                return objs;
            }
        } catch (SQLException e) {
            BattleTracker.getInstance().error("Could not get objects", e);
        } finally {
            try {
                rscon.con.close();
            } catch (Exception e) {
                BattleTracker.getInstance().error("Could not close connection", e);
            }
        }
        return null;
    }
}
