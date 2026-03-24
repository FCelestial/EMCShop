package top.MiragEdge.emc.Utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import top.MiragEdge.emc.EMCShop;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spark 性能分析插件集成类
 * 使用运行时反射检测 Spark，支持 Spark 0.1+ 版本
 * 当 Spark 存在时提供性能监控，当不存在时静默忽略
 */
public class SparkIntegration {

    private final EMCShop plugin;
    private boolean sparkEnabled = false;

    // Spark API 类引用（运行时加载）
    private Class<?> sparkClass = null;
    private Class<?> sparkProviderClass = null;
    private Object sparkInstance = null;

    // 数据库操作统计
    private final AtomicLong databaseSaves = new AtomicLong(0);
    private final AtomicLong databaseLoads = new AtomicLong(0);
    private final AtomicLong databaseErrors = new AtomicLong(0);

    // 经济操作统计
    private final AtomicLong deposits = new AtomicLong(0);
    private final AtomicLong withdrawals = new AtomicLong(0);

    // GUI 操作统计
    private final AtomicLong conversions = new AtomicLong(0);
    private final AtomicLong itemsConverted = new AtomicLong(0);

    public SparkIntegration(EMCShop plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化 Spark 集成（运行时检测）
     */
    public void initialize() {
        try {
            // 尝试加载 Spark 类
            sparkClass = Class.forName("me.lucko.spark.api.Spark");
            sparkProviderClass = Class.forName("me.lucko.spark.api.SparkProvider");

            // 获取 SparkProvider 服务
            Object sparkProvider = Bukkit.getServicesManager().load(sparkProviderClass);
            if (sparkProvider != null) {
                // 调用 instance() 方法获取 Spark 实例
                Method instanceMethod = sparkProviderClass.getMethod("instance");
                sparkInstance = instanceMethod.invoke(sparkProvider);

                if (sparkInstance != null) {
                    sparkEnabled = true;
                    plugin.getLogger().info("已检测到 Spark 性能分析插件，性能监控已启用");
                    return;
                }
            }
        } catch (ClassNotFoundException e) {
            // Spark 未安装
            plugin.getLogger().info("Spark 未安装，跳过性能监控集成");
        } catch (Exception e) {
            plugin.getLogger().warning("Spark 检测失败: " + e.getMessage());
        }

        sparkEnabled = false;
    }

    /**
     * 获取 Spark 实例
     */
    private Object getSpark() {
        return sparkInstance;
    }

    // ==================== 统计更新方法 ====================

    /**
     * 记录数据库保存操作
     */
    public void onDatabaseSave() {
        databaseSaves.incrementAndGet();
    }

    /**
     * 记录数据库加载操作
     */
    public void onDatabaseLoad() {
        databaseLoads.incrementAndGet();
    }

    /**
     * 记录数据库错误
     */
    public void onDatabaseError() {
        databaseErrors.incrementAndGet();
    }

    /**
     * 记录存款操作
     */
    public void onDeposit() {
        deposits.incrementAndGet();
    }

    /**
     * 记录取款操作
     */
    public void onWithdrawal() {
        withdrawals.incrementAndGet();
    }

    /**
     * 记录转换操作
     */
    public void onConversion(int itemCount) {
        conversions.incrementAndGet();
        itemsConverted.addAndGet(itemCount);
    }

    // ==================== Spark API 调用 ====================

    /**
     * 获取服务器 TPS
     * @return TPS 数组 [1分钟, 5分钟, 15分钟]
     */
    public double[] getTps() {
        if (!sparkEnabled || sparkInstance == null) {
            return new double[]{20.0, 20.0, 20.0};
        }

        try {
            Method tpsMethod = sparkClass.getMethod("getTps");
            double[] tps = (double[]) tpsMethod.invoke(sparkInstance);
            return tps != null ? tps : new double[]{20.0, 20.0, 20.0};
        } catch (Exception e) {
            return new double[]{20.0, 20.0, 20.0};
        }
    }

    /**
     * 获取 Tick 统计信息
     */
    public String getTickStatisticInfo() {
        if (!sparkEnabled || sparkInstance == null) {
            return "Spark 不可用";
        }

        try {
            Method tickMethod = sparkClass.getMethod("getTickStatistic");
            Object tickStat = tickMethod.invoke(sparkInstance);
            if (tickStat == null) return "无数据";

            // 获取平均值
            Method avgMethod = tickStat.getClass().getMethod("getAverage");
            double avg = (double) avgMethod.invoke(tickStat);
            return String.format("%.2f ms/tick", avg);
        } catch (Exception e) {
            return "获取失败";
        }
    }

    /**
     * 获取内存使用情况
     */
    public String getMemoryInfo() {
        if (!sparkEnabled || sparkInstance == null) {
            return "Spark 不可用";
        }

        try {
            Method memMethod = sparkClass.getMethod("getMemoryStatistic");
            Object memStat = memMethod.invoke(sparkInstance);
            if (memStat == null) return "无数据";

            // 获取已用内存
            Method usedMethod = memStat.getClass().getMethod("getUsedMemory");
            long used = (long) usedMethod.invoke(memStat);

            // 获取最大内存
            Method maxMethod = memStat.getClass().getMethod("getMaxMemory");
            long max = (long) maxMethod.invoke(memStat);

            double usedMb = used / (1024.0 * 1024.0);
            double maxMb = max / (1024.0 * 1024.0);
            return String.format("%.1f / %.1f MB", usedMb, maxMb);
        } catch (Exception e) {
            return "获取失败";
        }
    }

    /**
     * 获取完整的插件统计信息（可用于 /sparkview 或控制台）
     */
    public String getStatsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== EMCShop 性能统计 ===\n");
        sb.append("Spark 状态: ").append(sparkEnabled ? "已启用" : "未启用").append("\n");

        if (sparkEnabled) {
            double[] tps = getTps();
            sb.append("服务器 TPS: ");
            sb.append(String.format("%.2f / %.2f / %.2f", tps[0], tps[1], tps[2])).append("\n");
            sb.append("Tick 延迟: ").append(getTickStatisticInfo()).append("\n");
            sb.append("内存使用: ").append(getMemoryInfo()).append("\n");
        }

        sb.append("\n--- 操作统计 ---\n");
        sb.append("数据库保存: ").append(databaseSaves).append("\n");
        sb.append("数据库加载: ").append(databaseLoads).append("\n");
        sb.append("数据库错误: ").append(databaseErrors).append("\n");
        sb.append("存款操作: ").append(deposits).append("\n");
        sb.append("取款操作: ").append(withdrawals).append("\n");
        sb.append("转换操作: ").append(conversions).append("\n");
        sb.append("物品转换: ").append(itemsConverted).append("\n");

        return sb.toString();
    }

    /**
     * 检查 Spark 是否启用
     */
    public boolean isSparkEnabled() {
        return sparkEnabled;
    }

    // ==================== Getter 方法 ====================

    public AtomicLong getDatabaseSaves() {
        return databaseSaves;
    }

    public AtomicLong getDatabaseLoads() {
        return databaseLoads;
    }

    public AtomicLong getDatabaseErrors() {
        return databaseErrors;
    }

    public AtomicLong getDeposits() {
        return deposits;
    }

    public AtomicLong getWithdrawals() {
        return withdrawals;
    }

    public AtomicLong getConversions() {
        return conversions;
    }

    public AtomicLong getItemsConverted() {
        return itemsConverted;
    }
}
