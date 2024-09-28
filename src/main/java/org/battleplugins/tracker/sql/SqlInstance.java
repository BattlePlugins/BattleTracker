package org.battleplugins.tracker.sql;

import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.BattleTrackerConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Holds an SQL connection to a database.
 * <p>
 * Only one of these should ever exist during a program's runtime, as
 * databases such as SQLite can only have one connection at a time. It is
 * fine if this is shared across multiple "tracker" instances, as long as
 * it is only ever instantiated once.
 */
public final class SqlInstance {
    private static final String CREATE_DATABASE = "CREATE DATABASE IF NOT EXISTS `%s`";

    private static SqlInstance instance;

    private final String db;
    private final SqlSerializer.SqlType type;

    private final String tablePrefix;
    private final String url;
    private final String port;
    private final String username;
    private final String password;

    private PoolingDataSource<PoolableConnection> dataSource;

    private SqlInstance(BattleTrackerConfig.DatabaseOptions options) {
        this.tablePrefix = options.prefix();
        this.db = options.db();
        this.type = options.type();
        if (options.type() == SqlSerializer.SqlType.SQLITE) {
            this.url = BattleTracker.getInstance().getDataFolder().toString();
        } else {
            this.url = options.url();
        }

        this.port = options.port();
        this.username = options.user();
        this.password = options.password();

        this.initDataSource();

        instance = this;
    }

    public void close() {
        try {
            this.dataSource.close();
        } catch (SQLException e) {
            BattleTracker.getInstance().error("Could not close SQL connection!", e);
        }
    }

    private void initDataSource() {
        Connection connection = null;

        // See if the driver exists
        try {
            Class.forName(this.type.getDriver());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Could not find SQL driver " + this.type.getDriver());
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

        // Create the data source
        try {
            this.dataSource = setupDataSource(datasourceString, this.username, this.password, minIdle, maxActive);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create data source for SQL connection", e);
        }

        if (this.type == SqlSerializer.SqlType.MYSQL) {
            String statement = String.format(CREATE_DATABASE, this.db);
            try {
                connection = DriverManager.getConnection(connectionString, this.username, this.password);
                Statement st = connection.createStatement();
                st.executeUpdate(statement);
            } catch (SQLException e) {
                throw new IllegalStateException("Could not create database " + this.db, e);
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public String getTablePrefix() {
        return this.tablePrefix;
    }

    public String getDatabase() {
        return this.db;
    }

    public SqlSerializer.SqlType getType() {
        return this.type;
    }

    public DataSource getDataSource() {
        return this.dataSource;
    }

    public static SqlInstance getInstance() {
        return instance;
    }

    public static void init(BattleTrackerConfig.DatabaseOptions options) {
        if (instance != null) {
            throw new IllegalStateException("Cannot create more than one SQL instance!");
        }

        new SqlInstance(options);
    }

    private static PoolingDataSource<PoolableConnection> setupDataSource(String connectURI, String username, String password,
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
}
