package top.MiragEdge.emc.Database;

import top.MiragEdge.emc.Data.PlayerData;
import top.MiragEdge.emc.EMCShop;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {
    private final DatabaseConnector connector;
    private final EMCShop plugin;
    private final Logger logger;

    // 数据库操作队列系统
    private final ExecutorService dbExecutor;
    private final Map<UUID, PlayerData> pendingSaves = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final BlockingQueue<DatabaseTask> taskQueue = new LinkedBlockingQueue<>();
    private volatile boolean isProcessing = false;

    public DatabaseManager(DatabaseConnector connector, EMCShop plugin) {
        this.connector = connector;
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // 创建单线程数据库执行器
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "Database-Worker");
            thread.setDaemon(true);
            return thread;
        });

        // 启动队列处理器
        startQueueProcessor();
        createTables();
    }

    private void startQueueProcessor() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!isProcessing && !taskQueue.isEmpty()) {
                isProcessing = true;
                dbExecutor.submit(this::processQueue);
            }
        }, 0, 50, TimeUnit.MILLISECONDS); // 每50ms检查一次队列
    }

    private void processQueue() {
        try {
            List<DatabaseTask> batchTasks = new ArrayList<>();
            taskQueue.drainTo(batchTasks, 100); // 每次最多处理100个任务

            if (!batchTasks.isEmpty()) {
                try (Connection conn = connector.getConnection()) {
                    // 按玩家UUID分组排序
                    Map<UUID, List<DatabaseTask>> tasksByPlayer = new TreeMap<>();
                    for (DatabaseTask task : batchTasks) {
                        tasksByPlayer
                                .computeIfAbsent(task.playerId, k -> new ArrayList<>())
                                .add(task);
                    }

                    // 按UUID顺序处理任务
                    for (List<DatabaseTask> playerTasks : tasksByPlayer.values()) {
                        processPlayerTasks(conn, playerTasks);
                    }
                } catch (SQLException e) {
                    handleDatabaseError(e);
                }
            }
        } finally {
            isProcessing = false;
        }
    }

    private void processPlayerTasks(Connection conn, List<DatabaseTask> tasks) throws SQLException {
        try {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            for (DatabaseTask task : tasks) {
                switch (task.taskType) {
                    case SAVE_DATA:
                        savePlayerDataInternal(conn, task.playerData);
                        break;
                    case LOAD_DATA:
                        PlayerData data = loadPlayerDataInternal(conn, task.playerId);
                        task.callback.accept(data);
                        break;
                    case CHECK_UNLOCK:
                        boolean unlocked = isItemUnlockedInternal(conn, task.playerId, task.itemId);
                        task.unlockCallback.accept(unlocked);
                        break;
                }
            }

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    // 数据库任务类型
    private enum TaskType { SAVE_DATA, LOAD_DATA, CHECK_UNLOCK }

    // 数据库任务对象 - 修复字段初始化问题
    private static class DatabaseTask {
        final TaskType taskType;
        final UUID playerId;
        PlayerData playerData;
        String itemId;
        final Consumer<PlayerData> callback;
        final Consumer<Boolean> unlockCallback;

        // 保存任务构造器
        DatabaseTask(PlayerData playerData) {
            this.taskType = TaskType.SAVE_DATA;
            this.playerId = playerData.getPlayerId();
            this.playerData = playerData;
            this.callback = null;
            this.unlockCallback = null;
            this.itemId = null;
        }

        // 加载任务构造器
        DatabaseTask(UUID playerId, Consumer<PlayerData> callback) {
            this.taskType = TaskType.LOAD_DATA;
            this.playerId = playerId;
            this.callback = callback;
            this.unlockCallback = null;
            this.playerData = null;
            this.itemId = null;
        }

        // 检查解锁任务构造器
        DatabaseTask(UUID playerId, String itemId, Consumer<Boolean> unlockCallback) {
            this.taskType = TaskType.CHECK_UNLOCK;
            this.playerId = playerId;
            this.itemId = itemId;
            this.unlockCallback = unlockCallback;
            this.callback = null;
            this.playerData = null;
        }
    }

    // 函数式接口用于回调
    @FunctionalInterface
    public interface DataLoadCallback {
        void onDataLoaded(PlayerData playerData);
    }

    @FunctionalInterface
    public interface UnlockCheckCallback {
        void onCheckComplete(boolean isUnlocked);
    }

    //同步保存玩家数据方法
    public void savePlayerDataSync() {

        scheduler.shutdownNow();
        dbExecutor.shutdown();

        try {
            if (!dbExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("数据库任务未在超时时间内完成，强制关闭");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try (Connection conn = connector.getConnection()) {
            conn.setAutoCommit(false);

            List<DatabaseTask> remainingTasks = new ArrayList<>();
            taskQueue.drainTo(remainingTasks);

            Map<UUID, PlayerData> playerDataMap = new HashMap<>();
            for (DatabaseTask task : remainingTasks) {
                if (task.taskType == TaskType.SAVE_DATA) {

                    playerDataMap.put(task.playerId, task.playerData);
                }
            }

            for (PlayerData data : pendingSaves.values()) {

                playerDataMap.put(data.getPlayerId(), data);
            }
            pendingSaves.clear();

            for (PlayerData playerData : playerDataMap.values()) {
                savePlayerDataInternal(conn, playerData);
            }

            conn.commit();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "同步保存玩家数据时发生数据库错误", e);
            try {
                if (connector.getConnection() != null) {
                    connector.getConnection().rollback();
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "回滚事务失败", ex);
            }
        } finally {

            pendingSaves.clear();
            taskQueue.clear();
        }
    }

    // 队列系统异步保存玩家数据方法
    public void savePlayerDataAsync(PlayerData playerData) {

        pendingSaves.put(playerData.getPlayerId(), playerData);

        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            PlayerData latestData = pendingSaves.remove(playerData.getPlayerId());
            if (latestData != null) {
                queueTask(new DatabaseTask(latestData));
            }
        }, 10);
    }

    public void loadPlayerDataAsync(UUID playerId, DataLoadCallback callback) {
        queueTask(new DatabaseTask(playerId, callback::onDataLoaded));
    }

    public void isItemUnlockedAsync(UUID playerId, String itemId, UnlockCheckCallback callback) {
        queueTask(new DatabaseTask(playerId, itemId, callback::onCheckComplete));
    }

    private void queueTask(DatabaseTask task) {
        if (!taskQueue.offer(task)) {
            logger.warning("数据库队列已满，任务被丢弃！");
        }
    }

    // 以下内部方法在队列线程中执行
    private void savePlayerDataInternal(Connection conn, PlayerData playerData) throws SQLException {
        UUID playerId = playerData.getPlayerId();
        Set<String> unlockedItems = playerData.getUnlockedItems();

        // 先删除旧数据
        String deleteSql = "DELETE FROM player_unlocks WHERE player_uuid = ?";
        try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
            deleteStmt.setString(1, playerId.toString());
            deleteStmt.executeUpdate();
        }

        // 插入新数据
        if (!unlockedItems.isEmpty()) {
            String insertSql = "INSERT INTO player_unlocks (player_uuid, item_id) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE unlocked_at = CURRENT_TIMESTAMP";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                for (String itemId : unlockedItems) {
                    insertStmt.setString(1, playerId.toString());
                    insertStmt.setString(2, itemId);
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }
        }
    }

    private PlayerData loadPlayerDataInternal(Connection conn, UUID playerId) throws SQLException {
        PlayerData playerData = new PlayerData(playerId);
        String sql = "SELECT item_id FROM player_unlocks WHERE player_uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    playerData.unlockItem(rs.getString("item_id"));
                }
            }
        }
        return playerData;
    }

    private boolean isItemUnlockedInternal(Connection conn, UUID playerId, String itemId) throws SQLException {
        String sql = "SELECT 1 FROM player_unlocks WHERE player_uuid = ? AND item_id = ? LIMIT 1";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, itemId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // 错误处理
    private void handleDatabaseError(SQLException e) {
        if (isDeadlockError(e)) {
            logger.warning("数据库死锁检测到，任务将重试: " + e.getMessage());
            // 将失败任务重新加入队列
            if (!taskQueue.isEmpty()) {
                taskQueue.addAll(taskQueue);
            }
        } else {
            logger.log(Level.SEVERE, "数据库操作失败", e);
        }
    }

    private boolean isDeadlockError(SQLException e) {
        return e.getErrorCode() == 1213 ||
                (e.getMessage() != null && e.getMessage().contains("Deadlock"));
    }

    // 关闭资源
    public void shutdown() {
        scheduler.shutdown();
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 创建表的方法保持不变
    private void createTables() {
        String sql = "CREATE TABLE IF NOT EXISTS player_unlocks (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "item_id VARCHAR(64) NOT NULL," +
                "unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "UNIQUE KEY unique_unlock (player_uuid, item_id)" +
                ")";

        try (Connection conn = connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "创建数据库表失败", e);
        }
    }
}