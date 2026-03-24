package top.MiragEdge.emc.Manager;

import org.bukkit.configuration.ConfigurationSection;
import top.MiragEdge.emc.Data.PlayerData;
import top.MiragEdge.emc.Database.DatabaseManager;
import top.MiragEdge.emc.EMCShop;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class EMCManager {
    private final EMCShop plugin;
    private final DatabaseManager dbManager;
    private final Map<String, Double> emcValues = new LinkedHashMap<>();
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    // 缓存不可修改的EMC值映射，避免每次调用都创建新包装
    private volatile Map<String, Double> cachedEmcValuesView;

    // 经济模式枚举
    public enum EconomyMode {
        VAULT,  // 使用Vault经济系统
        EMC     // 使用插件独立经济系统
    }

    private EconomyMode currentEconomyMode;

    public EMCManager(EMCShop plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        loadEMCValues();
        setupEconomyMode();
    }

    /**
     * 设置经济模式
     */
    public void setupEconomyMode() {
        String mode = plugin.getConfig().getString("economy.mode", "EMC").toUpperCase();
        try {
            currentEconomyMode = EconomyMode.valueOf(mode);
            plugin.getLogger().info("已启用经济模式: " + currentEconomyMode);
        } catch (IllegalArgumentException e) {
            currentEconomyMode = EconomyMode.EMC;
            plugin.getLogger().warning("未知的经济模式: " + mode + ", 默认使用EMC模式");
        }
    }

    /**
     * 获取当前经济模式
     */
    public EconomyMode getCurrentEconomyMode() {
        return currentEconomyMode;
    }

    /**
     * 强制设置经济模式（供重载失败时回退使用）
     */
    public void setCurrentEconomyMode(EconomyMode mode) {
        this.currentEconomyMode = mode;
        plugin.getLogger().info("经济模式已强制设置为: " + mode);
    }

    // 加载物品EMC值（添加异常处理）
    public void loadEMCValues() {
        emcValues.clear();
        // 清除缓存，下次调用getEmcValues时会重新创建
        cachedEmcValuesView = null;

        File file = new File(plugin.getDataFolder(), "items.yml");

        try {
            if (!file.exists()) {
                plugin.saveResource("items.yml", false);
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection baseSection = config.getConfigurationSection("EMC_VALUES.BASE");
            if (baseSection != null) {
                for (String key : baseSection.getKeys(false)) {
                    // 使用大写键名确保一致性
                    String normalizedKey = key.toUpperCase();
                    double value = baseSection.getDouble(key);
                    emcValues.put(normalizedKey, value);
                }
            }
            plugin.getLogger().info("已加载 " + emcValues.size() + " 个物品的EMC值");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "加载EMC值时出错: " + e.getMessage(), e);
        }
    }

    // 获取EMC值映射的方法（使用缓存的不可修改视图）
    public Map<String, Double> getEmcValues() {
        Map<String, Double> cached = cachedEmcValuesView;
        if (cached == null) {
            cached = Collections.unmodifiableMap(emcValues);
            cachedEmcValuesView = cached;
        }
        return cached;
    }

    // 获取物品EMC值（添加空值检查）
    public double getItemValue(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return -1;
        }
        return emcValues.getOrDefault(itemId.toUpperCase(), -1.0);
    }

    /**
     * 获取玩家所有解锁物品（包括配置中已删除的）
     * @param playerId 玩家UUID
     * @return 解锁物品集合
     */
    public Set<String> getPlayerUnlockedItems(UUID playerId) {
        PlayerData playerData = playerDataMap.get(playerId);
        return playerData != null ?
                new HashSet<>(playerData.getUnlockedItems()) : // 返回副本防止外部修改
                Collections.emptySet();
    }

    /**
     * 清理无效解锁记录（配置中不存在的物品）
     * @param playerId 玩家UUID
     */
    public void cleanInvalidUnlocks(UUID playerId) {
        PlayerData playerData = playerDataMap.get(playerId);
        if (playerData != null) {
            // 创建有效物品ID的快照，避免在检查过程中emcValues发生变化
            // 使用HashSet存储以便O(1)查找
            Set<String> validItemsSnapshot = new HashSet<>(emcValues.keySet());

            // 创建副本避免并发修改
            Set<String> unlockedItems = new HashSet<>(playerData.getUnlockedItems());

            // 移除无效物品
            unlockedItems.removeIf(itemId -> !validItemsSnapshot.contains(itemId));

            // 更新解锁列表
            playerData.setUnlockedItems(unlockedItems);

            // 异步保存到数据库
            dbManager.savePlayerDataAsync(playerData);
        }
    }

    // 玩家登录时加载数据
    public void onPlayerLogin(Player player) {
        UUID playerId = player.getUniqueId();

        // 避免重复加载
        if (playerDataMap.containsKey(playerId)) {
            return;
        }

        // 设置临时数据占位符
        playerDataMap.put(playerId, new PlayerData(playerId));

        dbManager.loadPlayerDataAsync(playerId, playerData -> {
            // 确保玩家仍然在线
            if (player.isOnline()) {
                playerDataMap.put(playerId, playerData);
                // 登录时清理无效解锁记录
                cleanInvalidUnlocks(playerId);

                // 初始化EMC余额（如果使用EMC模式且玩家余额为0或负数，说明是新玩家或数据异常）
                if (currentEconomyMode == EconomyMode.EMC && playerData.getEmcBalance() <= 0) {
                    // 只有当余额正好是0时才认为是初始化（数据库中没有余额记录时为0）
                    // 如果之前余额是负数也会被重置为0
                    if (playerData.getEmcBalance() < 0) {
                        playerData.setEmcBalance(0.0);
                        plugin.getLogger().info("玩家 " + player.getName() + " 的EMC余额异常，已重置为: 0.0");
                    }
                }
            }
        });
    }

    // 玩家退出时保存数据（添加空值检查）
    public void onPlayerLogout(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerData playerData = playerDataMap.remove(playerId);

        if (playerData != null) {
            dbManager.savePlayerDataAsync(playerData);
            plugin.getLogger().info("已保存玩家 " + player.getName() + " 的数据");
        }
    }

    // 解锁物品（添加解锁检查）
    public boolean unlockItem(Player player, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        PlayerData playerData = playerDataMap.get(playerId);

        if (playerData != null) {
            // 检查是否已经解锁
            if (!playerData.isItemUnlocked(itemId)) {
                playerData.unlockItem(itemId);
                // 异步保存到数据库
                dbManager.savePlayerDataAsync(playerData);
                plugin.getLogger().info("玩家 " + player.getName() + " 解锁了物品: " + itemId);
                return true;
            }
        }
        return false;
    }

    // 检查物品是否已解锁（添加空值检查）
    public boolean isItemUnlocked(Player player, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }

        PlayerData playerData = playerDataMap.get(player.getUniqueId());
        return playerData != null && playerData.isItemUnlocked(itemId);
    }

    // 获取玩家数据（添加空值检查）
    public PlayerData getPlayerData(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return playerDataMap.get(playerId);
    }

    // ========== 经济系统相关方法 ==========

    /**
     * 获取玩家余额
     * @param player 玩家对象
     * @return 玩家余额
     */
    public double getBalance(Player player) {
        if (player == null) return 0;

        switch (currentEconomyMode) {
            case EMC:
                // 使用插件独立经济系统
                PlayerData playerData = playerDataMap.get(player.getUniqueId());
                if (playerData != null) {
                    double balance = playerData.getEmcBalance();
                    plugin.getLogger().fine("获取玩家 " + player.getName() + " 的EMC余额: " + balance);
                    return balance;
                }
                return 0;

            case VAULT:
            default:
                // 使用Vault经济系统
                try {
                    double balance = EMCShop.getEconomy().getBalance(player);
                    plugin.getLogger().fine("获取玩家 " + player.getName() + " 的Vault余额: " + balance);
                    return balance;
                } catch (Exception e) {
                    plugin.getLogger().warning("获取Vault经济余额失败: " + e.getMessage());
                    return 0;
                }
        }
    }

    /**
     * 从玩家账户扣款（购买物品）
     * @param player 玩家对象
     * @param amount 扣款金额
     * @return 是否扣款成功
     */
    public boolean withdraw(Player player, double amount) {
        if (player == null || amount <= 0) return false;

        switch (currentEconomyMode) {
            case EMC:
                // 使用插件独立经济系统
                PlayerData playerData = playerDataMap.get(player.getUniqueId());
                if (playerData != null) {
                    double currentBalance = playerData.getEmcBalance();
                    if (currentBalance >= amount) {
                        playerData.setEmcBalance(currentBalance - amount);
                        // 异步保存到数据库
                        dbManager.savePlayerDataAsync(playerData);
                        plugin.getLogger().info("从玩家 " + player.getName() + " 扣款: " + amount + " EMC，剩余余额: " + playerData.getEmcBalance());
                        return true;
                    } else {
                        plugin.getLogger().info("玩家 " + player.getName() + " 余额不足，当前余额: " + currentBalance + "，需要: " + amount);
                    }
                }
                return false;

            case VAULT:
            default:
                // 使用Vault经济系统
                try {
                    boolean success = EMCShop.getEconomy().withdrawPlayer(player, amount).transactionSuccess();
                    if (success) {
                        plugin.getLogger().info("从玩家 " + player.getName() + " 扣款: " + amount + " 货币");
                    } else {
                        plugin.getLogger().warning("Vault经济扣款失败: " + player.getName() + " - " + amount);
                    }
                    return success;
                } catch (Exception e) {
                    plugin.getLogger().warning("Vault经济扣款异常: " + e.getMessage());
                    return false;
                }
        }
    }

    /**
     * 向玩家账户存款（出售物品）
     * @param player 玩家对象
     * @param amount 存款金额
     * @return 是否存款成功
     */
    public boolean deposit(Player player, double amount) {
        if (player == null || amount <= 0) return false;

        switch (currentEconomyMode) {
            case EMC:
                // 使用插件独立经济系统
                PlayerData playerData = playerDataMap.get(player.getUniqueId());
                if (playerData != null) {
                    double currentBalance = playerData.getEmcBalance();
                    playerData.setEmcBalance(currentBalance + amount);
                    // 异步保存到数据库
                    dbManager.savePlayerDataAsync(playerData);
                    plugin.getLogger().info("向玩家 " + player.getName() + " 存款: " + amount + " EMC，当前余额: " + playerData.getEmcBalance());
                    return true;
                }
                return false;

            case VAULT:
            default:
                // 使用Vault经济系统
                try {
                    EMCShop.getEconomy().depositPlayer(player, amount);
                    plugin.getLogger().info("向玩家 " + player.getName() + " 存款: " + amount + " 货币");
                    return true;
                } catch (Exception e) {
                    plugin.getLogger().warning("Vault经济存款失败: " + e.getMessage());
                    return false;
                }
        }
    }

    /**
     * 带重试机制的存款方法（兼容现有代码）
     * @param player 玩家对象
     * @param amount 存款金额
     */
    public void depositWithRetry(Player player, double amount) {
        if (amount <= 0) return;

        // 使用异步任务执行存款，避免阻塞主线程
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int retries = 0;
            while (retries < 3) {
                try {
                    // 直接调用异步安全的存款方法
                    // deposit方法内部使用ConcurrentHashMap存储玩家数据，是线程安全的
                    // 数据库保存也是异步的
                    boolean success = deposit(player, amount);
                    if (success) {
                        return; // 存款成功
                    } else {
                        plugin.getLogger().warning("存款操作返回失败，重试中... (" + (retries + 1) + "/3)");
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("存款操作异常: " + e.getMessage() + "，重试中... (" + (retries + 1) + "/3)");
                }

                retries++;
                if (retries < 3) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            plugin.getLogger().warning("无法完成经济操作: " + player.getName() + " - " + amount);
        });
    }

    /**
     * 设置玩家EMC余额（仅EMC模式有效）
     * @param player 玩家对象
     * @param amount 余额数量
     * @return 是否设置成功
     */
    public boolean setBalance(Player player, double amount) {
        if (player == null || amount < 0) return false;

        if (currentEconomyMode == EconomyMode.EMC) {
            PlayerData playerData = playerDataMap.get(player.getUniqueId());
            if (playerData != null) {
                playerData.setEmcBalance(amount);
                // 异步保存到数据库
                dbManager.savePlayerDataAsync(playerData);
                plugin.getLogger().info("设置玩家 " + player.getName() + " 的EMC余额为: " + amount);
                return true;
            }
        } else {
            plugin.getLogger().warning("只能在EMC经济模式下设置玩家余额");
        }
        return false;
    }

    /**
     * 检查玩家是否有足够余额
     * @param player 玩家对象
     * @param amount 检查金额
     * @return 是否有足够余额
     */
    public boolean hasEnoughBalance(Player player, double amount) {
        double balance = getBalance(player);
        boolean hasEnough = balance >= amount;

        if (!hasEnough) {
            plugin.getLogger().fine("玩家 " + player.getName() + " 余额不足，当前余额: " + balance + "，需要: " + amount);
        }

        return hasEnough;
    }

    /**
     * 给玩家发放EMC货币（仅EMC模式有效）
     * @param player 玩家对象
     * @param amount 发放金额
     * @return 是否发放成功
     */
    public boolean giveEMC(Player player, double amount) {
        return deposit(player, amount);
    }

    /**
     * 从玩家扣除EMC货币（仅EMC模式有效）
     * @param player 玩家对象
     * @param amount 扣除金额
     * @return 是否扣除成功
     */
    public boolean takeEMC(Player player, double amount) {
        return withdraw(player, amount);
    }

    /**
     * 重置玩家EMC余额为0（仅EMC模式有效）
     * @param player 玩家对象
     * @return 是否重置成功
     */
    public boolean resetBalance(Player player) {
        return setBalance(player, 0.0);
    }

    /**
     * 获取所有在线玩家的数据快照（用于调试和管理）
     * @return 玩家数据快照映射
     */
    public Map<UUID, PlayerData> getOnlinePlayerDataSnapshot() {
        return new HashMap<>(playerDataMap);
    }

    /**
     * 强制保存所有在线玩家数据（用于紧急保存）
     */
    public void saveAllOnlinePlayerData() {
        int count = 0;
        for (PlayerData playerData : playerDataMap.values()) {
            dbManager.savePlayerDataAsync(playerData);
            count++;
        }
        plugin.getLogger().info("已强制保存 " + count + " 名在线玩家的数据");
    }
}