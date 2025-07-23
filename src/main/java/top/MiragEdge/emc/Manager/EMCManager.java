package top.MiragEdge.emc.Manager;

import org.bukkit.configuration.ConfigurationSection;
import top.MiragEdge.emc.Data.PlayerData;
import top.MiragEdge.emc.Database.DatabaseManager;
import top.MiragEdge.emc.EMCShop;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class EMCManager {
    private final EMCShop plugin;
    private final DatabaseManager dbManager;
    private final Map<String, Integer> emcValues = new LinkedHashMap<>();
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    public EMCManager(EMCShop plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        loadEMCValues();
    }

    // 加载物品EMC值（添加异常处理）
    public void loadEMCValues() {
        emcValues.clear();
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
                    int value = baseSection.getInt(key);
                    emcValues.put(normalizedKey, value);
                }
            }
            plugin.getLogger().info("已加载 " + emcValues.size() + " 个物品的EMC值");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "加载EMC值时出错: " + e.getMessage(), e);
        }
    }

    // 添加获取EMC值映射的方法
    public Map<String, Integer> getEmcValues() {
        return Collections.unmodifiableMap(emcValues);
    }

    // 获取物品EMC值（添加空值检查）
    public int getItemValue(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return -1;
        }
        return emcValues.getOrDefault(itemId.toUpperCase(), -1);
    }

    // 玩家登录时加载数据（添加状态检查）
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
            }
        });
    }

    // 玩家退出时保存数据（添加空值检查）
    public void onPlayerLogout(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerData playerData = playerDataMap.remove(playerId);

        if (playerData != null) {
            dbManager.savePlayerDataAsync(playerData);
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
}