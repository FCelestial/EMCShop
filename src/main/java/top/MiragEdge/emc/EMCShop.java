package top.MiragEdge.emc;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import top.MiragEdge.emc.Commands.MainCommand;
import top.MiragEdge.emc.Database.DatabaseConnector;
import top.MiragEdge.emc.Database.DatabaseManager;
import top.MiragEdge.emc.Manager.EMCManager;
import top.MiragEdge.emc.Utils.MessageUtil;
import top.MiragEdge.emc.Utils.SparkIntegration;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class EMCShop extends JavaPlugin implements Listener {

    private static EMCShop instance;
    private DatabaseConnector dbConnector;
    private DatabaseManager dbManager;
    private EMCManager emcManager;
    private static Economy economy;
    private SparkIntegration sparkIntegration;

    @Override
    public void onEnable() {
        getLogger().info(" ");
        getLogger().info("=======================");
        getLogger().info(" ");

        instance = this;

        // 保存默认配置
        saveDefaultConfig();

        // 初始化消息工具
        MessageUtil.initialize(this);

        // 初始化数据库连接
        dbConnector = new DatabaseConnector(this);
        dbManager = new DatabaseManager(dbConnector, this);

        // 初始化管理器
        emcManager = new EMCManager(this, dbManager);

        // 根据经济模式决定是否初始化Vault
        EMCManager.EconomyMode economyMode = emcManager.getCurrentEconomyMode();
        if (economyMode == EMCManager.EconomyMode.VAULT) {
            // 只在VAU
            // LT模式下初始化Vault经济
            if (!setupEconomy()) {
                getLogger().severe("没有发现 Vault 经济插件前置! 关闭插件...");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            getLogger().info("已启用Vault经济模式");
        } else {
            getLogger().info("已启用EMC独立经济模式");
        }

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);

        // 注册命令
        MainCommand commandExecutor = new MainCommand(this, emcManager);
        Objects.requireNonNull(getCommand("emcshop")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("emcshop")).setTabCompleter(commandExecutor);

        // 初始化 Spark 性能监控集成
        sparkIntegration = new SparkIntegration(this);
        sparkIntegration.initialize();

        getLogger().info("等价交换插件已启用");
        getLogger().info("=======================");
        getLogger().info(" ");
    }

    @Override
    public void onDisable() {
        getLogger().info(" ");
        getLogger().info("=======================");
        getLogger().info(" ");
        getLogger().info("开始关闭插件...");

        // 同步保存在线玩家数据
        if (dbManager != null) {
            getLogger().info("正在保存所有玩家数据...");
            dbManager.savePlayerDataSync();
            getLogger().info("玩家数据已同步保存");
        } else {
            getLogger().warning("dbManager 为 null，无法保存玩家数据");
        }

        // 关闭数据库资源
        if (dbManager != null) {
            dbManager.shutdown();
        } else {
            getLogger().warning("dbManager 为 null，无法关闭数据库管理器");
        }

        if (dbConnector != null) {
            dbConnector.close();
        } else {
            getLogger().warning("dbConnector 为 null，无法关闭数据库连接");
        }

        getLogger().info("插件已安全关闭");
        getLogger().info(" ");
        getLogger().info("=======================");
        getLogger().info(" ");
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    // 玩家登录事件 - 加载玩家数据
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 加载玩家数据
        if (emcManager != null) {
            emcManager.onPlayerLogin(player);
        }
    }

    // 玩家退出事件 - 保存玩家数据
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (emcManager != null) {
            emcManager.onPlayerLogout(event.getPlayer());
        }
    }

    /**
     * 重载插件配置
     */
    public void reloadPlugin() {
        // 保存当前在线玩家列表（防止重载时玩家退出导致NPE）
        Set<UUID> onlinePlayers = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlinePlayers.add(player.getUniqueId());
        }

        // 重载主配置
        reloadConfig();

        // 重载消息配置
        MessageUtil.getInstance().reloadMessageConfig();

        // 重载EMC物品配置
        if (emcManager != null) {
            emcManager.loadEMCValues(); // 重新加载物品配置

            // 重新设置经济模式（可能在配置中更改了）
            String oldMode = emcManager.getCurrentEconomyMode().name();
            emcManager.setupEconomyMode();
            String newMode = emcManager.getCurrentEconomyMode().name();

            if (!oldMode.equals(newMode)) {
                getLogger().info("经济模式已从 " + oldMode + " 切换到 " + newMode);

                // 如果切换到VAULT模式，需要检查Vault是否可用
                if (emcManager.getCurrentEconomyMode() == EMCManager.EconomyMode.VAULT && !setupEconomy()) {
                    getLogger().warning("切换到VAULT模式失败，未找到Vault经济插件！将保持使用EMC模式");
                    emcManager.setCurrentEconomyMode(EMCManager.EconomyMode.EMC);
                }
            }

            // 清理所有在线玩家的无效解锁记录
            for (UUID playerId : onlinePlayers) {
                emcManager.cleanInvalidUnlocks(playerId);
            }
        }

        getLogger().info("配置和消息文件已重载，自动清理了 " + onlinePlayers.size() + " 名玩家的无效解锁记录。");
    }

    /**
     * 获取EMC管理器
     */
    public EMCManager getEmcManager() {
        return emcManager;
    }

    /**
     * 获取Spark性能监控集成
     */
    public SparkIntegration getSparkIntegration() {
        return sparkIntegration;
    }

    /**
     * 获取Vault经济实例
     * 注意：在EMC模式下可能返回null
     */
    public static Economy getEconomy() {
        return economy;
    }

    /**
     * 获取插件实例
     */
    public static EMCShop getInstance() {
        return instance;
    }

    /**
     * 检查是否使用Vault经济
     */
    public boolean isUsingVaultEconomy() {
        return emcManager != null &&
                emcManager.getCurrentEconomyMode() == EMCManager.EconomyMode.VAULT &&
                economy != null;
    }

    /**
     * 检查是否使用EMC独立经济
     */
    public boolean isUsingEMCEconomy() {
        return emcManager != null &&
                emcManager.getCurrentEconomyMode() == EMCManager.EconomyMode.EMC;
    }
}