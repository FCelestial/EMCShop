package top.MiragEdge.emc.Database;

import top.MiragEdge.emc.Data.PlayerData;
import top.MiragEdge.emc.EMCShop;

import java.lang.ref.WeakReference;
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

    // 数据库操作队列系统 - 使用LinkedTransferQueue提高性能
    private final ExecutorService dbExecutor;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final TransferQueue<DatabaseTask> taskQueue = new LinkedTransferQueue<>();
    private volatile boolean isProcessing = false;
    private volatile boolean shutdownRequested = false;

    // 动态批处理配置
    private volatile int currentBatchSize = 100;
    private volatile long lastProcessTime = 0;
    private static final int MIN_BATCH_SIZE = 10;
    private static final int MAX_BATCH_SIZE = 500;
    private static final int BATCH_ADJUST_THRESHOLD = 100;
    private static final int SAVE_COOLDOWN_MS = 5000;

    // 内存泄漏防护优化
    private final Map<UUID, WeakReference<PlayerData>> pendingSaves = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSaveTimes = new ConcurrentHashMap<>();

    // 清理定时器 - 定期清理过期数据
    private final ScheduledExecutorService cleanerScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final long CLEANUP_INTERVAL_MINUTES = 5;
    private static final long MAX_SAVE_TIME_AGE_HOURS = 24;

    // 死锁重试机制
    private final Map<UUID, List<DatabaseTask>> failedTasksByPlayer = new ConcurrentHashMap<>();
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private final Map<UUID, Integer> retryCounts = new ConcurrentHashMap<>();

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

        // 启动队列处理器 - 改为事件驱动 + 定时检查混合模式
        startQueueProcessor();
        startCleanupScheduler();
        createTables();
    }

    private void startQueueProcessor() {
        // 方案1: 使用阻塞式消费，避免空轮询
        Thread queueProcessor = new Thread(() -> {
            while (!shutdownRequested && !Thread.currentThread().isInterrupted()) {
                try {
                    // 阻塞等待任务，最长等待1秒
                    DatabaseTask firstTask = taskQueue.poll(1, TimeUnit.SECONDS);

                    if (firstTask != null) {
                        // 收集更多任务（非阻塞）
                        List<DatabaseTask> batchTasks = new ArrayList<>();
                        batchTasks.add(firstTask);
                        taskQueue.drainTo(batchTasks, currentBatchSize - 1);

                        // 处理批任务
                        processBatch(batchTasks);
                    }

                    // 处理重试任务
                    processRetryTasks();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "队列处理器异常", e);
                }
            }
        }, "Database-Queue-Processor");
        queueProcessor.setDaemon(true);
        queueProcessor.start();

        // 方案2: 保留定时检查作为备用（间隔延长到500ms）
        scheduler.scheduleAtFixedRate(() -> {
            if (!isProcessing && !taskQueue.isEmpty()) {
                isProcessing = true;
                dbExecutor.submit(() -> {
                    try {
                        List<DatabaseTask> batchTasks = new ArrayList<>();
                        taskQueue.drainTo(batchTasks, currentBatchSize);

                        if (!batchTasks.isEmpty()) {
                            processBatch(batchTasks);
                        }
                    } finally {
                        isProcessing = false;
                    }
                });
            }
        }, 0, 500, TimeUnit.MILLISECONDS); // 延长到500ms
    }

    private void startCleanupScheduler() {
        // 定期清理过期数据
        cleanerScheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredData();
            } catch (Exception e) {
                logger.log(Level.WARNING, "清理过期数据时发生异常", e);
            }
        }, CLEANUP_INTERVAL_MINUTES, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    private void cleanupExpiredData() {
        long now = System.currentTimeMillis();
        long maxAge = MAX_SAVE_TIME_AGE_HOURS * 60 * 60 * 1000;

        // 清理lastSaveTimes中的过期记录
        lastSaveTimes.entrySet().removeIf(entry -> {
            long age = now - entry.getValue();
            return age > maxAge;
        });

        // 清理pendingSaves中的空引用
        pendingSaves.entrySet().removeIf(entry -> {
            WeakReference<PlayerData> ref = entry.getValue();
            return ref == null || ref.get() == null;
        });

        // 清理失败任务记录
        failedTasksByPlayer.entrySet().removeIf(entry ->
                entry.getValue() == null || entry.getValue().isEmpty());

        // 清理重试计数
        retryCounts.entrySet().removeIf(entry ->
                entry.getValue() == null || entry.getValue() == 0);

        logger.fine("完成定期数据清理");
    }

    // ====================== 回调接口定义 ======================
    @FunctionalInterface
    public interface DataLoadCallback {
        void onDataLoaded(PlayerData playerData);
    }

    @FunctionalInterface
    public interface UnlockCheckCallback {
        void onCheckComplete(boolean isUnlocked);
    }

    // ====================== 队列处理 ======================
    private void processBatch(List<DatabaseTask> batchTasks) {
        if (batchTasks.isEmpty()) return;

        long startTime = System.currentTimeMillis();

        try (Connection conn = connector.getConnection()) {
            // 按任务类型分组
            Map<TaskType, List<DatabaseTask>> tasksByType = new EnumMap<>(TaskType.class);
            for (DatabaseTask task : batchTasks) {
                tasksByType
                        .computeIfAbsent(task.taskType, k -> new ArrayList<>())
                        .add(task);
            }

            // 处理保存任务 - 批量优化
            if (tasksByType.containsKey(TaskType.SAVE_DATA)) {
                processSaveTasksBatch(conn, tasksByType.get(TaskType.SAVE_DATA));
            }

            // 处理加载任务
            if (tasksByType.containsKey(TaskType.LOAD_DATA)) {
                processLoadTasks(conn, tasksByType.get(TaskType.LOAD_DATA));
            }

            // 处理检查解锁任务
            if (tasksByType.containsKey(TaskType.CHECK_UNLOCK)) {
                processUnlockCheckTasks(conn, tasksByType.get(TaskType.CHECK_UNLOCK));
            }

            long endTime = System.currentTimeMillis();
            long processDuration = endTime - startTime;

            // 根据处理时间动态调整批处理大小
            adjustBatchSize(processDuration);

        } catch (SQLException e) {
            handleDatabaseError(e, batchTasks);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "处理批任务时发生未知异常", e);
        }
    }

    private void processRetryTasks() {
        if (failedTasksByPlayer.isEmpty()) return;

        try (Connection conn = connector.getConnection()) {
            conn.setAutoCommit(false);

            // 处理每个玩家的失败任务
            for (Map.Entry<UUID, List<DatabaseTask>> entry : failedTasksByPlayer.entrySet()) {
                UUID playerId = entry.getKey();
                List<DatabaseTask> failedTasks = entry.getValue();

                if (failedTasks == null || failedTasks.isEmpty()) continue;

                Integer retryCount = retryCounts.get(playerId);
                if (retryCount != null && retryCount >= MAX_RETRY_ATTEMPTS) {
                    logger.warning("玩家 " + playerId + " 的任务重试次数已达上限，跳过");
                    continue;
                }

                try {
                    // 重新执行失败的任务
                    for (DatabaseTask task : failedTasks) {
                        switch (task.taskType) {
                            case SAVE_DATA:
                                if (task.playerData != null) {
                                    savePlayerDataOptimized(conn, task.playerData);
                                }
                                break;
                            case LOAD_DATA:
                                PlayerData data = loadPlayerDataInternal(conn, task.playerId);
                                if (task.callback != null) {
                                    task.callback.accept(data);
                                }
                                break;
                            case CHECK_UNLOCK:
                                boolean unlocked = isItemUnlockedInternal(conn, task.playerId, task.itemId);
                                if (task.unlockCallback != null) {
                                    task.unlockCallback.accept(unlocked);
                                }
                                break;
                        }
                    }

                    conn.commit();

                    // 成功后清理失败记录
                    failedTasksByPlayer.remove(playerId);
                    retryCounts.remove(playerId);

                } catch (SQLException e) {
                    conn.rollback();

                    // 更新重试计数
                    int newRetryCount = retryCounts.merge(playerId, 1, Integer::sum);

                    if (newRetryCount >= MAX_RETRY_ATTEMPTS) {
                        logger.severe("玩家 " + playerId + " 的任务重试 " + MAX_RETRY_ATTEMPTS + " 次后仍然失败");
                        failedTasksByPlayer.remove(playerId);
                        retryCounts.remove(playerId);
                    } else {
                        logger.warning("玩家 " + playerId + " 的任务重试失败 (" + newRetryCount + "/" + MAX_RETRY_ATTEMPTS + ")");
                    }
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "处理重试任务时发生数据库异常", e);
        }
    }

    private void adjustBatchSize(long processDuration) {
        // 如果处理时间过长，减少批处理大小
        if (processDuration > BATCH_ADJUST_THRESHOLD && currentBatchSize > MIN_BATCH_SIZE) {
            int newSize = Math.max(MIN_BATCH_SIZE, currentBatchSize / 2);
            if (newSize != currentBatchSize) {
                logger.fine("批处理大小从 " + currentBatchSize + " 调整为 " + newSize + " (处理时间: " + processDuration + "ms)");
                currentBatchSize = newSize;
            }
        }
        // 如果处理时间很短且队列不空，增加批处理大小
        else if (processDuration < BATCH_ADJUST_THRESHOLD / 2 && taskQueue.size() > currentBatchSize) {
            int newSize = Math.min(MAX_BATCH_SIZE, currentBatchSize * 2);
            if (newSize != currentBatchSize) {
                logger.fine("批处理大小从 " + currentBatchSize + " 调整为 " + newSize + " (处理时间: " + processDuration + "ms)");
                currentBatchSize = newSize;
            }
        }

        lastProcessTime = processDuration;
    }

    // ====================== 数据库任务类型 ======================
    private enum TaskType { SAVE_DATA, LOAD_DATA, CHECK_UNLOCK }

    // 数据库任务对象 - 添加序列化支持
    private static class DatabaseTask {
        final TaskType taskType;
        final UUID playerId;
        final long timestamp;
        PlayerData playerData;
        String itemId;
        final Consumer<PlayerData> callback;
        final Consumer<Boolean> unlockCallback;

        DatabaseTask(PlayerData playerData) {
            this.taskType = TaskType.SAVE_DATA;
            this.playerId = playerData.getPlayerId();
            this.playerData = playerData;
            this.callback = null;
            this.unlockCallback = null;
            this.itemId = null;
            this.timestamp = System.currentTimeMillis();
        }

        DatabaseTask(UUID playerId, Consumer<PlayerData> callback) {
            this.taskType = TaskType.LOAD_DATA;
            this.playerId = playerId;
            this.callback = callback;
            this.unlockCallback = null;
            this.playerData = null;
            this.itemId = null;
            this.timestamp = System.currentTimeMillis();
        }

        DatabaseTask(UUID playerId, String itemId, Consumer<Boolean> unlockCallback) {
            this.taskType = TaskType.CHECK_UNLOCK;
            this.playerId = playerId;
            this.itemId = itemId;
            this.unlockCallback = unlockCallback;
            this.callback = null;
            this.playerData = null;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return "DatabaseTask{" +
                    "taskType=" + taskType +
                    ", playerId=" + playerId +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    // ====================== 公共API方法 ======================
    public void savePlayerDataAsync(PlayerData playerData) {
        if (playerData == null) return;

        UUID playerId = playerData.getPlayerId();
        long now = System.currentTimeMillis();

        // 检查保存冷却时间
        Long lastSave = lastSaveTimes.get(playerId);
        if (lastSave != null && (now - lastSave) < SAVE_COOLDOWN_MS) {
            // 使用WeakReference避免内存泄漏
            pendingSaves.put(playerId, new WeakReference<>(playerData));
            return;
        }

        lastSaveTimes.put(playerId, now);
        pendingSaves.put(playerId, new WeakReference<>(playerData));

        // 立即提交任务，不延迟
        queueTask(new DatabaseTask(playerData));
    }

    public void loadPlayerDataAsync(UUID playerId, DataLoadCallback callback) {
        if (playerId == null || callback == null) return;
        queueTask(new DatabaseTask(playerId, callback::onDataLoaded));
    }

    public void isItemUnlockedAsync(UUID playerId, String itemId, UnlockCheckCallback callback) {
        if (playerId == null || itemId == null || callback == null) return;
        queueTask(new DatabaseTask(playerId, itemId, callback::onCheckComplete));
    }

    private void queueTask(DatabaseTask task) {
        if (task == null) return;

        // 检查任务是否已过期（超过1小时）
        if (System.currentTimeMillis() - task.timestamp > 3600000) {
            logger.warning("任务已过期，丢弃: " + task);
            return;
        }

        if (!taskQueue.offer(task)) {
            logger.warning("数据库队列已满，任务被丢弃: " + task);
        }
    }

    // ====================== 批量任务处理方法 ======================
    private void processSaveTasksBatch(Connection conn, List<DatabaseTask> saveTasks) throws SQLException {
        if (saveTasks.isEmpty()) return;

        try {
            conn.setAutoCommit(false);

            // 按玩家分组，避免重复操作
            Map<UUID, PlayerData> latestDataByPlayer = new HashMap<>();
            for (DatabaseTask task : saveTasks) {
                if (task.taskType == TaskType.SAVE_DATA && task.playerData != null) {
                    // 保留每个玩家的最新数据
                    latestDataByPlayer.put(task.playerId, task.playerData);
                }
            }

            // 批量处理玩家数据
            for (PlayerData playerData : latestDataByPlayer.values()) {
                savePlayerDataOptimized(conn, playerData);
            }

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private void processLoadTasks(Connection conn, List<DatabaseTask> loadTasks) throws SQLException {
        if (loadTasks.isEmpty()) return;

        try {
            conn.setAutoCommit(false);

            for (DatabaseTask task : loadTasks) {
                PlayerData data = loadPlayerDataInternal(conn, task.playerId);
                if (task.callback != null) {
                    task.callback.accept(data);
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

    private void processUnlockCheckTasks(Connection conn, List<DatabaseTask> checkTasks) throws SQLException {
        if (checkTasks.isEmpty()) return;

        try {
            conn.setAutoCommit(false);

            for (DatabaseTask task : checkTasks) {
                boolean unlocked = isItemUnlockedInternal(conn, task.playerId, task.itemId);
                if (task.unlockCallback != null) {
                    task.unlockCallback.accept(unlocked);
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

    // ====================== 数据库操作方法 ======================
    private void savePlayerDataOptimized(Connection conn, PlayerData playerData) throws SQLException {
        UUID playerId = playerData.getPlayerId();
        Set<String> unlockedItems = playerData.getUnlockedItems();
        double emcBalance = playerData.getEmcBalance();

        if (connector.isMySQL()) {
            // MySQL使用临时表批量更新解锁记录
            savePlayerDataMySQL(conn, playerId, unlockedItems, emcBalance);
        } else {
            // SQLite使用优化后的批量操作
            savePlayerDataSQLite(conn, playerId, unlockedItems, emcBalance);
        }
    }

    // MySQL优化版本
    private void savePlayerDataMySQL(Connection conn, UUID playerId, Set<String> unlockedItems, double emcBalance) throws SQLException {
        String playerUuid = playerId.toString();

        // 优化解锁物品保存：使用批量插入和存在性检查
        if (!unlockedItems.isEmpty()) {
            // 1. 删除不再解锁的物品
            if (unlockedItems.size() < 100) { // 小批量使用IN子句
                String deleteSql = "DELETE FROM player_unlocks WHERE player_uuid = ? AND item_id NOT IN (" +
                        String.join(",", Collections.nCopies(unlockedItems.size(), "?")) + ")";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setString(1, playerUuid);
                    int paramIndex = 2;
                    for (String itemId : unlockedItems) {
                        deleteStmt.setString(paramIndex++, itemId);
                    }
                    deleteStmt.executeUpdate();
                }
            } else { // 大批量使用临时表
                createTempTableMySQL(conn);
                batchInsertToTempTable(conn, playerUuid, unlockedItems);

                String deleteSql = "DELETE u FROM player_unlocks u " +
                        "LEFT JOIN temp_player_unlocks t ON u.player_uuid = t.player_uuid AND u.item_id = t.item_id " +
                        "WHERE u.player_uuid = ? AND t.item_id IS NULL";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setString(1, playerUuid);
                    deleteStmt.executeUpdate();
                }

                dropTempTableMySQL(conn);
            }

            // 2. 批量插入新解锁的物品（忽略已存在的）
            String insertSql = "INSERT IGNORE INTO player_unlocks (player_uuid, item_id) VALUES (?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                int batchCount = 0;
                for (String itemId : unlockedItems) {
                    insertStmt.setString(1, playerUuid);
                    insertStmt.setString(2, itemId);
                    insertStmt.addBatch();
                    batchCount++;

                    // 每100条执行一次批量
                    if (batchCount % 100 == 0) {
                        insertStmt.executeBatch();
                    }
                }
                if (batchCount % 100 != 0) {
                    insertStmt.executeBatch();
                }
            }
        } else {
            // 如果没有解锁物品，删除所有相关记录
            String deleteSql = "DELETE FROM player_unlocks WHERE player_uuid = ?";
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setString(1, playerUuid);
                deleteStmt.executeUpdate();
            }
        }

        // 保存EMC余额（使用ON DUPLICATE KEY UPDATE）
        String balanceSql = "INSERT INTO player_emc_balance (player_uuid, balance) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE balance = VALUES(balance), updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement balanceStmt = conn.prepareStatement(balanceSql)) {
            balanceStmt.setString(1, playerUuid);
            balanceStmt.setDouble(2, emcBalance);
            balanceStmt.executeUpdate();
        }
    }

    // SQLite优化版本
    private void savePlayerDataSQLite(Connection conn, UUID playerId, Set<String> unlockedItems, double emcBalance) throws SQLException {
        String playerUuid = playerId.toString();

        // 优化解锁物品保存
        if (!unlockedItems.isEmpty()) {
            // 1. 删除不再解锁的物品
            if (unlockedItems.size() < 100) { // 小批量使用IN子句
                String deleteSql = "DELETE FROM player_unlocks WHERE player_uuid = ? AND item_id NOT IN (" +
                        String.join(",", Collections.nCopies(unlockedItems.size(), "?")) + ")";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setString(1, playerUuid);
                    int paramIndex = 2;
                    for (String itemId : unlockedItems) {
                        deleteStmt.setString(paramIndex++, itemId);
                    }
                    deleteStmt.executeUpdate();
                }
            } else { // 大批量使用临时表（SQLite也支持）
                createTempTableSQLite(conn);
                batchInsertToTempTable(conn, playerUuid, unlockedItems);

                String deleteSql = "DELETE FROM player_unlocks WHERE player_uuid = ? AND item_id NOT IN " +
                        "(SELECT item_id FROM temp_player_unlocks WHERE player_uuid = ?)";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setString(1, playerUuid);
                    deleteStmt.setString(2, playerUuid);
                    deleteStmt.executeUpdate();
                }

                dropTempTableSQLite(conn);
            }

            // 2. 批量插入新解锁的物品（使用INSERT OR IGNORE）
            String insertSql = "INSERT OR IGNORE INTO player_unlocks (player_uuid, item_id, unlocked_at) VALUES (?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                int batchCount = 0;
                for (String itemId : unlockedItems) {
                    insertStmt.setString(1, playerUuid);
                    insertStmt.setString(2, itemId);
                    insertStmt.addBatch();
                    batchCount++;

                    // 每100条执行一次批量
                    if (batchCount % 100 == 0) {
                        insertStmt.executeBatch();
                    }
                }
                if (batchCount % 100 != 0) {
                    insertStmt.executeBatch();
                }
            }
        } else {
            // 如果没有解锁物品，删除所有相关记录
            String deleteSql = "DELETE FROM player_unlocks WHERE player_uuid = ?";
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setString(1, playerUuid);
                deleteStmt.executeUpdate();
            }
        }

        // 保存EMC余额（使用INSERT OR REPLACE）
        String balanceSql = "INSERT OR REPLACE INTO player_emc_balance (player_uuid, balance, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement balanceStmt = conn.prepareStatement(balanceSql)) {
            balanceStmt.setString(1, playerUuid);
            balanceStmt.setDouble(2, emcBalance);
            balanceStmt.executeUpdate();
        }
    }

    // 临时表相关方法
    private void createTempTableMySQL(Connection conn) throws SQLException {
        String createTempTable = "CREATE TEMPORARY TABLE IF NOT EXISTS temp_player_unlocks (" +
                "player_uuid VARCHAR(36) NOT NULL," +
                "item_id VARCHAR(64) NOT NULL," +
                "PRIMARY KEY (player_uuid, item_id)" +
                ") ENGINE=MEMORY";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createTempTable);
        }
    }

    private void createTempTableSQLite(Connection conn) throws SQLException {
        String createTempTable = "CREATE TEMPORARY TABLE IF NOT EXISTS temp_player_unlocks (" +
                "player_uuid TEXT NOT NULL," +
                "item_id TEXT NOT NULL," +
                "PRIMARY KEY (player_uuid, item_id)" +
                ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createTempTable);
        }
    }

    private void batchInsertToTempTable(Connection conn, String playerUuid, Set<String> unlockedItems) throws SQLException {
        if (unlockedItems.isEmpty()) return;

        String insertSql = "INSERT INTO temp_player_unlocks (player_uuid, item_id) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE item_id = VALUES(item_id)";
        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            int batchCount = 0;
            for (String itemId : unlockedItems) {
                insertStmt.setString(1, playerUuid);
                insertStmt.setString(2, itemId);
                insertStmt.addBatch();
                batchCount++;

                if (batchCount % 100 == 0) {
                    insertStmt.executeBatch();
                }
            }
            if (batchCount % 100 != 0) {
                insertStmt.executeBatch();
            }
        }
    }

    private void dropTempTableMySQL(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TEMPORARY TABLE IF EXISTS temp_player_unlocks");
        }
    }

    private void dropTempTableSQLite(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS temp_player_unlocks");
        }
    }

    private PlayerData loadPlayerDataInternal(Connection conn, UUID playerId) throws SQLException {
        PlayerData playerData = new PlayerData(playerId);

        // 加载解锁物品
        String unlocksSql = "SELECT item_id FROM player_unlocks WHERE player_uuid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(unlocksSql)) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    playerData.unlockItem(rs.getString("item_id"));
                }
            }
        }

        // 加载EMC余额
        String balanceSql = "SELECT balance FROM player_emc_balance WHERE player_uuid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(balanceSql)) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    playerData.setEmcBalance(rs.getDouble("balance"));
                } else {
                    // 如果没有记录，设置默认余额为0
                    playerData.setEmcBalance(0.0);
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

    // ====================== 错误处理 ======================
    private void handleDatabaseError(SQLException e, List<DatabaseTask> failedTasks) {
        if (isDeadlockError(e)) {
            logger.warning("数据库死锁检测到，任务将进入重试队列: " + e.getMessage());

            // 按玩家分组失败的任务
            for (DatabaseTask task : failedTasks) {
                failedTasksByPlayer
                        .computeIfAbsent(task.playerId, k -> new ArrayList<>())
                        .add(task);
            }

            // 减少批处理大小以避免死锁
            int oldSize = currentBatchSize;
            currentBatchSize = Math.max(MIN_BATCH_SIZE, currentBatchSize / 2);

            if (oldSize != currentBatchSize) {
                logger.info("死锁检测，批处理大小从 " + oldSize + " 调整为 " + currentBatchSize);
            }

        } else if (isConnectionError(e)) {
            logger.severe("数据库连接错误，任务将延迟重试: " + e.getMessage());

            // 连接错误时，延迟一段时间后重新加入队列
            scheduler.schedule(() -> {
                for (DatabaseTask task : failedTasks) {
                    queueTask(task);
                }
            }, 5, TimeUnit.SECONDS);

        } else {
            logger.log(Level.SEVERE, "数据库操作失败", e);

            // 其他错误，记录但不重试
            for (DatabaseTask task : failedTasks) {
                if (task.taskType == TaskType.SAVE_DATA) {
                    logger.severe("保存玩家 " + task.playerId + " 数据失败");
                }
            }
        }
    }

    private boolean isDeadlockError(SQLException e) {
        return e.getErrorCode() == 1213 ||
                (e.getMessage() != null && e.getMessage().contains("Deadlock")) ||
                e.getErrorCode() == 5 || // SQLITE_BUSY
                (e.getMessage() != null && e.getMessage().contains("database is locked"));
    }

    private boolean isConnectionError(SQLException e) {
        return e.getErrorCode() == 0 || // 常见于连接断开
                e.getMessage() != null && (
                        e.getMessage().contains("connection") ||
                                e.getMessage().contains("socket") ||
                                e.getMessage().contains("Communications link failure")
                );
    }

    // ====================== 同步保存方法 ======================
    public void savePlayerDataSync() {
        shutdownRequested = true;
        scheduler.shutdownNow();
        cleanerScheduler.shutdownNow();
        dbExecutor.shutdown();

        try {
            if (!dbExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("数据库任务未在超时时间内完成，强制关闭");
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try (Connection conn = connector.getConnection()) {
            conn.setAutoCommit(false);

            // 处理所有剩余任务
            List<DatabaseTask> remainingTasks = new ArrayList<>();
            taskQueue.drainTo(remainingTasks);

            // 处理pendingSaves中的任务
            for (WeakReference<PlayerData> ref : pendingSaves.values()) {
                if (ref != null) {
                    PlayerData data = ref.get();
                    if (data != null) {
                        savePlayerDataOptimized(conn, data);
                    }
                }
            }

            // 处理剩余队列任务
            for (DatabaseTask task : remainingTasks) {
                if (task.taskType == TaskType.SAVE_DATA && task.playerData != null) {
                    savePlayerDataOptimized(conn, task.playerData);
                }
            }

            // 处理失败重试任务
            for (List<DatabaseTask> failedTasks : failedTasksByPlayer.values()) {
                for (DatabaseTask task : failedTasks) {
                    if (task.taskType == TaskType.SAVE_DATA && task.playerData != null) {
                        savePlayerDataOptimized(conn, task.playerData);
                    }
                }
            }

            conn.commit();
            logger.info("同步保存完成，共保存 " + (pendingSaves.size() + remainingTasks.size()) + " 个玩家数据");

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "同步保存玩家数据时发生数据库错误", e);
        } finally {
            // 彻底清理所有缓存
            clearAllCaches();
        }
    }

    // ====================== 清理方法 ======================
    // 清理玩家数据（玩家离线时调用）
    public void cleanupPlayerData(UUID playerId) {
        // 立即保存pending数据
        WeakReference<PlayerData> ref = pendingSaves.remove(playerId);
        if (ref != null) {
            PlayerData data = ref.get();
            if (data != null) {
                savePlayerDataAsync(data); // 触发一次异步保存
            }
        }

        // 清理其他缓存
        lastSaveTimes.remove(playerId);
        failedTasksByPlayer.remove(playerId);
        retryCounts.remove(playerId);

        logger.fine("清理玩家 " + playerId + " 的缓存数据");
    }

    private void clearAllCaches() {
        pendingSaves.clear();
        lastSaveTimes.clear();
        taskQueue.clear();
        failedTasksByPlayer.clear();
        retryCounts.clear();
    }

    // ====================== 关闭方法 ======================
    public void shutdown() {
        shutdownRequested = true;

        scheduler.shutdown();
        cleanerScheduler.shutdown();
        dbExecutor.shutdown();

        try {
            if (!dbExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        clearAllCaches();
    }

    // ====================== 创建表方法 ======================
    private void createTables() {
        // 创建解锁物品表
        String unlocksTableSql;
        if (connector.isSQLite()) {
            unlocksTableSql = "CREATE TABLE IF NOT EXISTS player_unlocks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "item_id TEXT NOT NULL," +
                    "unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE(player_uuid, item_id)" +
                    ")";
        } else {
            unlocksTableSql = "CREATE TABLE IF NOT EXISTS player_unlocks (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "item_id VARCHAR(64) NOT NULL," +
                    "unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE KEY unique_unlock (player_uuid, item_id)" +
                    ")";
        }

        // 创建EMC余额表
        String balanceTableSql;
        if (connector.isSQLite()) {
            balanceTableSql = "CREATE TABLE IF NOT EXISTS player_emc_balance (" +
                    "player_uuid TEXT PRIMARY KEY," +
                    "balance REAL NOT NULL DEFAULT 0," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
        } else {
            balanceTableSql = "CREATE TABLE IF NOT EXISTS player_emc_balance (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "balance DOUBLE NOT NULL DEFAULT 0," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ")";
        }

        try (Connection conn = connector.getConnection()) {
            // 创建解锁物品表
            try (PreparedStatement stmt = conn.prepareStatement(unlocksTableSql)) {
                stmt.execute();
            }

            // 创建EMC余额表
            try (PreparedStatement stmt = conn.prepareStatement(balanceTableSql)) {
                stmt.execute();
            }

            logger.info("数据库表创建/检查完成 - 使用" + (connector.isSQLite() ? "SQLite" : "MySQL"));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "创建数据库表失败", e);
        }
    }

    // ====================== 辅助方法 ======================
    /**
     * 获取当前数据库类型
     */
    public String getDatabaseType() {
        return connector.getDatabaseType();
    }

    /**
     * 检查是否是SQLite数据库
     */
    public boolean isSQLite() {
        return connector.isSQLite();
    }

    /**
     * 检查是否是MySQL数据库
     */
    public boolean isMySQL() {
        return connector.isMySQL();
    }

    /**
     * 获取当前队列大小（用于监控）
     */
    public int getQueueSize() {
        return taskQueue.size();
    }

    /**
     * 获取当前批处理大小（用于监控）
     */
    public int getCurrentBatchSize() {
        return currentBatchSize;
    }

    /**
     * 重置批处理大小为默认值
     */
    public void resetBatchSize() {
        currentBatchSize = 100;
    }
}