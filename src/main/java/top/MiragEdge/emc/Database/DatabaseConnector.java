package top.MiragEdge.emc.Database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import top.MiragEdge.emc.EMCShop;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseConnector {
    private final EMCShop plugin;
    private final Logger logger;
    private HikariDataSource dataSource;
    private Connection fallbackConnection;  // 后备连接
    private final String databaseType;

    public DatabaseConnector(EMCShop plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.databaseType = plugin.getConfig().getString("database.type", "mysql").toLowerCase();
        setupDataSource();
    }

    private void setupDataSource() {
        // 根据数据库类型选择配置
        if ("sqlite".equals(databaseType)) {
            setupSQLiteDataSource();
        } else {
            setupMySQLDataSource();
        }
    }

    private void setupMySQLDataSource() {
        // 从配置读取数据库设置
        String host = plugin.getConfig().getString("database.host", "localhost");
        int port = plugin.getConfig().getInt("database.port", 3306);
        String database = plugin.getConfig().getString("database.database", "emcshop");
        String username = plugin.getConfig().getString("database.username", "root");
        String password = plugin.getConfig().getString("database.password", "");

        // 读取连接池配置
        int maxPoolSize = plugin.getConfig().getInt("database.pool.max-size", 10);
        int minIdle = plugin.getConfig().getInt("database.pool.min-idle", 5);
        long connectionTimeout = plugin.getConfig().getLong("database.pool.connection-timeout", 10000);
        long idleTimeout = plugin.getConfig().getLong("database.pool.idle-timeout", 300000);
        long maxLifetime = plugin.getConfig().getLong("database.pool.max-lifetime", 600000);
        long leakDetectionThreshold = plugin.getConfig().getLong("database.pool.leak-detection-threshold", 15000);
        long validationTimeout = plugin.getConfig().getLong("database.pool.validation-timeout", 5000);

        // 配置连接池
        HikariConfig config = new HikariConfig();
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useUnicode=true" +
                "&characterEncoding=UTF-8" +
                "&useSSL=false" +
                "&serverTimezone=Asia/Shanghai" +
                "&autoReconnect=true";

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);

        // 设置连接池参数
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);

        // 确保最大生命周期小于MySQL的wait_timeout（默认28分钟）
        long safeMaxLifetime = Math.min(maxLifetime, TimeUnit.MINUTES.toMillis(28));
        config.setMaxLifetime(safeMaxLifetime);

        config.setPoolName("EMCShop-MySQL-Pool");
        config.setValidationTimeout(validationTimeout);
        config.setConnectionTestQuery("SELECT 1");

        // 启用连接泄漏检测（生产环境推荐设置30秒）
        if (leakDetectionThreshold > 0) {
            config.setLeakDetectionThreshold(leakDetectionThreshold);
            logger.info("MySQL连接泄漏检测已启用（阈值: " + leakDetectionThreshold + "ms）");
        } else {
            logger.warning("MySQL连接泄漏检测未启用，建议在生产环境设置阈值");
        }

        // MySQL性能优化参数
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        // 连接重试配置
        config.addDataSourceProperty("connectRetryAttempts", "3");
        config.addDataSourceProperty("connectRetryDelay", "5000");

        try {
            dataSource = new HikariDataSource(config);
            logger.info("MySQL数据库连接池初始化成功 | " +
                    "最大连接数: " + maxPoolSize + " | " +
                    "最小空闲: " + minIdle + " | " +
                    "最大生命周期: " + safeMaxLifetime + "ms");

            // 测试连接
            try (Connection testConn = dataSource.getConnection()) {
                if (testConn.isValid(2)) {
                    logger.info("MySQL数据库连接测试成功");
                } else {
                    logger.warning("MySQL数据库连接测试失败");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "MySQL数据库连接池初始化失败", e);
            // 尝试使用传统连接作为后备
            setupMySQLFallbackConnection(host, port, database, username, password);
        }
    }

    private void setupSQLiteDataSource() {
        // 从配置读取SQLite设置
        String databaseFile = plugin.getConfig().getString("database.file", "emcshop.db");
        String dataFolder = plugin.getDataFolder().getAbsolutePath();
        String fullPath = dataFolder + "/" + databaseFile;

        // 读取连接池配置（SQLite使用较小的连接池）
        int maxPoolSize = plugin.getConfig().getInt("database.sqlite-pool.max-size", 3);
        int minIdle = plugin.getConfig().getInt("database.sqlite-pool.min-idle", 1);
        long connectionTimeout = plugin.getConfig().getLong("database.sqlite-pool.connection-timeout", 30000);
        long idleTimeout = plugin.getConfig().getLong("database.sqlite-pool.idle-timeout", 300000);
        long maxLifetime = plugin.getConfig().getLong("database.sqlite-pool.max-lifetime", 1800000);
        long leakDetectionThreshold = plugin.getConfig().getLong("database.sqlite-pool.leak-detection-threshold", 15000);

        // 配置SQLite连接池
        HikariConfig config = new HikariConfig();
        String jdbcUrl = "jdbc:sqlite:" + fullPath;

        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");

        // SQLite连接池参数（较小的连接池）
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);

        config.setPoolName("EMCShop-SQLite-Pool");

        // SQLite特定配置
        config.addDataSourceProperty("foreign_keys", "true");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        config.addDataSourceProperty("cache_size", "2000");

        // 启用连接泄漏检测
        if (leakDetectionThreshold > 0) {
            config.setLeakDetectionThreshold(leakDetectionThreshold);
            logger.info("SQLite连接泄漏检测已启用（阈值: " + leakDetectionThreshold + "ms）");
        }

        try {
            dataSource = new HikariDataSource(config);
            logger.info("SQLite数据库连接池初始化成功 | " +
                    "数据库文件: " + fullPath + " | " +
                    "最大连接数: " + maxPoolSize + " | " +
                    "最小空闲: " + minIdle);

            // 测试连接并初始化数据库
            try (Connection testConn = dataSource.getConnection()) {
                // 启用外键支持和WAL模式
                try (var stmt = testConn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON");
                    stmt.execute("PRAGMA journal_mode = WAL");
                    stmt.execute("PRAGMA synchronous = NORMAL");
                    stmt.execute("PRAGMA busy_timeout = 5000");
                    stmt.execute("PRAGMA cache_size = 2000");
                }
                logger.info("SQLite数据库连接测试成功，已启用WAL模式和性能优化");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQLite数据库连接池初始化失败", e);
            // 尝试使用传统连接作为后备
            setupSQLiteFallbackConnection(fullPath);
        }
    }

    /**
     * MySQL后备方案：传统单连接
     */
    private void setupMySQLFallbackConnection(String host, int port, String database, String username, String password) {
        logger.warning("MySQL连接池初始化失败，尝试使用传统连接...");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useUnicode=true" +
                "&characterEncoding=UTF-8" +
                "&useSSL=false" +
                "&serverTimezone=Asia/Shanghai";

        try {
            // 加载驱动
            Class.forName("com.mysql.cj.jdbc.Driver");
            fallbackConnection = DriverManager.getConnection(url, username, password);
            fallbackConnection.setAutoCommit(true);  // 关闭事务自动提交
            logger.warning("已启用MySQL传统单连接后备模式（性能受限）");
        } catch (SQLException | ClassNotFoundException ex) {
            logger.log(Level.SEVERE, "MySQL传统数据库连接失败！", ex);
        }
    }

    /**
     * SQLite后备方案：传统单连接
     */
    private void setupSQLiteFallbackConnection(String fullPath) {
        logger.warning("SQLite连接池初始化失败，尝试使用传统连接...");
        String url = "jdbc:sqlite:" + fullPath;

        try {
            // 加载驱动
            Class.forName("org.sqlite.JDBC");
            fallbackConnection = DriverManager.getConnection(url);
            fallbackConnection.setAutoCommit(true);

            // 启用SQLite性能优化
            try (var stmt = fallbackConnection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL");
                stmt.execute("PRAGMA synchronous = NORMAL");
                stmt.execute("PRAGMA busy_timeout = 5000");
            }

            logger.warning("已启用SQLite传统单连接后备模式（性能受限）");
        } catch (SQLException | ClassNotFoundException ex) {
            logger.log(Level.SEVERE, "SQLite传统数据库连接失败！", ex);
        }
    }

    public Connection getConnection() throws SQLException {
        // 优先使用连接池
        if (dataSource != null && !dataSource.isClosed()) {
            return dataSource.getConnection();
        }

        // 后备连接检查
        if (fallbackConnection != null && !fallbackConnection.isClosed()) {
            logger.warning("使用后备数据库连接（单连接模式）");
            return fallbackConnection;
        }

        throw new SQLException("无可用数据库连接");
    }

    /**
     * 获取当前数据库类型
     */
    public String getDatabaseType() {
        return databaseType;
    }

    /**
     * 检查是否是SQLite数据库
     */
    public boolean isSQLite() {
        return "sqlite".equals(databaseType);
    }

    /**
     * 检查是否是MySQL数据库
     */
    public boolean isMySQL() {
        return "mysql".equals(databaseType);
    }

    public void close() {
        // 关闭连接池
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("数据库连接池已关闭");
        }

        // 关闭后备连接
        if (fallbackConnection != null) {
            try {
                if (!fallbackConnection.isClosed()) {
                    fallbackConnection.close();
                    logger.info("后备数据库连接已关闭");
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "关闭后备连接时出错", e);
            }
        }
    }
}