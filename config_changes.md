# EMCShop 代码优化建议

## 发现的问题

### 1. 硬编码魔法数字 (P2)

以下常量硬编码在代码中，建议移到配置文件：

```java
// 原始代码
private static final int MIN_BATCH_SIZE = 10;
private static final int MAX_BATCH_SIZE = 500;
private static final int SAVE_COOLDOWN_MS = 5000;
private static final long CLEANUP_INTERVAL_MINUTES = 5;
private static final long MAX_SAVE_TIME_AGE_HOURS = 24;
private static final int MAX_RETRY_ATTEMPTS = 3;
```

### 2. 建议的配置项 (config.yml)

```yaml
database:
  # 批处理配置
  batch-size: 100              # 默认批处理大小
  min-batch-size: 10          # 最小批处理大小
  max-batch-size: 500         # 最大批处理大小
  
  # 性能配置
  save-cooldown-ms: 5000       # 保存冷却时间(毫秒)
  max-retry-attempts: 3        # 最大重试次数
  
  # 清理配置
  cleanup-interval-minutes: 5  # 清理间隔(分钟)
  max-save-time-age-hours: 24  # 数据最大保存时间(小时)
```

### 3. 需要修改的代码位置

需要在 `DatabaseManager` 构造函数中添加配置加载：

```java
public DatabaseManager(DatabaseConnector connector, EMCShop plugin) {
    this.connector = connector;
    this.plugin = plugin;
    this.logger = plugin.getLogger();
    
    // 从配置加载
    loadConfiguration();
    // ...
}

private void loadConfiguration() {
    currentBatchSize = plugin.getConfig().getInt("database.batch-size", 100);
    minBatchSize = plugin.getConfig().getInt("database.min-batch-size", 10);
    maxBatchSize = plugin.getConfig().getInt("database.max-batch-size", 500);
    saveCooldownMs = plugin.getConfig().getInt("database.save-cooldown-ms", 5000);
    maxRetryAttempts = plugin.getConfig().getInt("database.max-retry-attempts", 3);
    cleanupIntervalMinutes = plugin.getConfig().getLong("database.cleanup-interval-minutes", 5);
    maxSaveTimeAgeHours = plugin.getConfig().getLong("database.max-save-time-age-hours", 24);
}
```

## 后续优化建议

1. **添加监控指标暴露** - 通过 JMX 或命令暴露队列大小、批处理命中率等
2. **添加配置热重载** - 支持 `/emcshop reload` 时重新加载数据库配置
3. **添加 Metrics 支持** - 对接 Prometheus 等监控系统

## PR 内容

本 PR 创建 Issue 供开发者参考，是否需要我继续完成代码修改并提交 PR？
