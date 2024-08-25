package org.battleplugins.tracker.sql;

import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
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

    private DataSource dataSource;

    protected String db = "minecraft";
    protected SqlType type = SqlType.MYSQL;

    protected String url = "localhost";
    protected String port = "3306";
    protected String username = "root";
    protected String password = "";

    private String createDatabase = "CREATE DATABASE IF NOT EXISTS `" + db + "`";

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPort() {
        return this.port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setType(SqlType type) {
        this.type = type;
    }

    public SqlType getType() {
        return this.type;
    }

    public String getDb() {
        return this.db;
    }

    public void setDb(String db) {
        this.db = db;
        this.createDatabase = "CREATE DATABASE IF NOT EXISTS `" + db + "`";
    }

    public record ResultSetConnection(ResultSet rs, Connection con) {
    }

    protected void close(ResultSetConnection rscon) {
        try {
            rscon.rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection(boolean displayErrors) throws SQLException {
        return this.getConnection(displayErrors, true);
    }

    public Connection getConnection() throws SQLException {
        return this.getConnection(true, true);
    }

    public Connection getConnection(boolean displayErrors, boolean autoCommit) throws SQLException {
        if (this.dataSource == null) {
            throw new java.sql.SQLException("Connection is null.  Did you intiliaze your SQL connection?");
        }

        try {
            Connection con = this.dataSource.getConnection();
            con.setAutoCommit(autoCommit);
            return con;
        } catch (SQLException e1) {
            if (displayErrors)
                e1.printStackTrace();
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
            e.printStackTrace();
        }
    }

    public void closeConnection(Connection con) {
        if (con == null) {
            return;
        }

        try {
            con.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    protected boolean init() {
        Connection con = null;  // Our database connection
        try {
            Class.forName(this.type.getDriver());
        } catch (ClassNotFoundException e1) {
            e1.printStackTrace();
            return false;
        }

        String connectionString;
        String datasourceString;
        int minIdle;
        int maxActive;
        switch (this.type) {
            case SQLITE:
                datasourceString = connectionString = "jdbc:sqlite:" + this.url + "/" + this.db + ".sqlite";
                maxActive = 1;
                minIdle = -1;
                break;
            case MYSQL:
            default:
                minIdle = 10;
                maxActive = 20;
                datasourceString = "jdbc:mysql://" + this.url + ":" + this.port + "/" + this.db + "?autoReconnect=true";
                connectionString = "jdbc:mysql://" + this.url + ":" + this.port + "?autoReconnect=true";
                break;
        }

        try {
            this.dataSource = setupDataSource(datasourceString, this.username, this.password, minIdle, maxActive);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        if (this.type == SqlType.MYSQL) {
            String strStmt = this.createDatabase;
            try {
                con = DriverManager.getConnection(connectionString, this.username, this.password);
                Statement st = con.createStatement();
                st.executeUpdate(strStmt);
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            } finally {
                this.closeConnection(con);
            }
        }

        return true;
    }
    
    public static PoolingDataSource<PoolableConnection> setupDataSource(String connectURI, String username, String password,
                                                                        int minIdle, int maxTotal) {
        ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(connectURI, username, password);
        PoolableConnectionFactory factory = new PoolableConnectionFactory(connectionFactory, null);
        factory.setValidationQuery("SELECT 1");

        GenericObjectPoolConfig<PoolableConnection> poolConfig = new GenericObjectPoolConfig<>();
        if (minIdle != -1) {
            poolConfig.setMinIdle(minIdle);
        }

        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setTestOnBorrow(true); // Test before the connection is made

        // Object pool
        GenericObjectPool<PoolableConnection> connectionPool = new GenericObjectPool<>(factory, poolConfig);
        factory.setPool(connectionPool);

        // Pooling data source
        return new PoolingDataSource<>(connectionPool);
    }

    protected boolean createTable(String tableName, String sqlCreateTable, String... sqlUpdates) {
        // Check to see if our table exists;
        Boolean exists;
        if (this.type == SqlType.SQLITE) {
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
        Connection connection;
        try {
            connection = this.getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

        try {
            statement = connection.createStatement();
            result = statement.executeUpdate(strStmt);
        } catch (SQLException e) {
            e.printStackTrace();
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
                    e.printStackTrace();
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
        switch (type) {
            case MYSQL:
                statement = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? " +
                        "AND TABLE_NAME = ? AND COLUMN_NAME = ?";
                columnExists = this.getBoolean(true, 2, statement, db, table, column);
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
        if (type == SqlType.SQLITE) {
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
    protected ResultSetConnection executeQuery(boolean displayErrors, Integer timeoutSeconds,
                                               String strRawStmt, Object... varArgs) {
        Connection con;
        try {
            con = this.getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }

        return this.executeQuery(con, displayErrors, timeoutSeconds, strRawStmt, varArgs);
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
                e.printStackTrace();
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
                    e.printStackTrace();
                }
            });
        } else {
            try {
                this.executeUpdate(strRawStmt, varArgs);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected int executeUpdate(String strRawStmt, Object... varArgs) {
        int result = -1;
        Connection con;
        try {
            con = getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }

        PreparedStatement ps;
        try {
            ps = this.getStatement(strRawStmt, con, varArgs);
            result = ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            this.closeConnection(con);
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
        Connection con;
        try {
            con = this.getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        PreparedStatement ps = null;
        try {
            con.setAutoCommit(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            ps = con.prepareStatement(updateStatement);
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (List<Object> update : batch) {
            try {
                for (int i = 0; i < update.size(); i++) {
                    ps.setObject(i + 1, update.get(i));
                }
                ps.addBatch();
            } catch (Exception e) {
                System.err.println("statement = " + ps);
                e.printStackTrace();
            }
        }
        try {
            ps.executeBatch();
            con.commit();
        } catch (Exception e) {
            e.printStackTrace();
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
                e.printStackTrace();
            }
        }
        return ps;
    }

    public Double getDouble(String query, Object... varArgs) {
        ResultSetConnection rscon = this.executeQuery(query, varArgs);
        if (rscon == null || rscon.con == null)
            return null;
        try {
            ResultSet rs = rscon.rs;
            while (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                rscon.con.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public Integer getInteger(String query, Object... varArgs) {
        ResultSetConnection rscon = this.executeQuery(query, varArgs);
        if (rscon == null || rscon.con == null)
            return null;
        try {
            ResultSet rs = rscon.rs;
            while (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                rscon.con.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public Short getShort(String query, Object... varArgs) {
        ResultSetConnection rscon = this.executeQuery(query, varArgs);
        if (rscon == null || rscon.con == null)
            return null;
        try {
            ResultSet rs = rscon.rs;
            while (rs.next()) {
                return rs.getShort(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                rscon.con.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public Long getLong(String query, Object... varArgs) {
        ResultSetConnection rscon = this.executeQuery(query, varArgs);
        if (rscon == null || rscon.con == null)
            return null;
        try {
            ResultSet rs = rscon.rs;
            while (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                rscon.con.close();
            } catch (Exception e) {
                e.printStackTrace();
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
            if (displayErrors)
                e.printStackTrace();
        } finally {
            try {
                rscon.con.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public String getString(String query, Object... varArgs) {
        ResultSetConnection rscon = this.executeQuery(query, varArgs);
        if (rscon == null || rscon.con == null)
            return null;
        try {
            ResultSet rs = rscon.rs;
            while (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                rscon.con.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public List<Object> getObjects(String query, Object... varArgs) {
        ResultSetConnection rscon = this.executeQuery(query, varArgs);
        if (rscon == null || rscon.con == null)
            return null;
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
            e.printStackTrace();
        } finally {
            try {
                rscon.con.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
