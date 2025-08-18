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
import top.MiragEdge.emc.Gui.ConvertMenu;
import top.MiragEdge.emc.Manager.EMCManager;
import top.MiragEdge.emc.Gui.PurchaseMenu;

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
    private ConvertMenu convertMenu;

    @Override
    public void onEnable() {
        instance = this;

        // 保存默认配置
        saveDefaultConfig();

        // 初始化数据库连接
        dbConnector = new DatabaseConnector(this);
        dbManager = new DatabaseManager(dbConnector, this);

        // 初始化Vault经济
        if (!setupEconomy()) {
            getLogger().severe("没有发现 Vault 经济插件前置! 关闭插件...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 初始化管理器
        emcManager = new EMCManager(this, dbManager);

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);

        // 注册命令
        MainCommand commandExecutor = new MainCommand(this, emcManager);
        Objects.requireNonNull(getCommand("emcshop")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("emcshop")).setTabCompleter(commandExecutor);

        getLogger().info("等价交换插件已启用");
    }

    @Override
    public void onDisable() {
        getLogger().info("开始关闭插件...");

        // 同步保存在线玩家数据
        if (dbManager != null) {
            getLogger().info("正在保存所有玩家数据...");
            dbManager.savePlayerDataSync();
            getLogger().info("玩家数据已同步保存");
        } else {
            getLogger().warning("dbManager 为 null，无法保存玩家数据");
        }

        // 安全调用转换菜单的禁用方法
        if (convertMenu != null) {
            getLogger().info("正在保存待处理物品数据...");
            convertMenu.onPluginDisable();
            getLogger().info("待处理物品数据已保存");
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
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return true;
    }

    // 玩家登录事件 - 加载玩家解锁数据
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        emcManager.onPlayerLogin(event.getPlayer());
        Player player = event.getPlayer();

        // 加载玩家解锁数据
        if (emcManager != null) {
            emcManager.onPlayerLogin(player);
        }

        // 恢复任何待处理的转换物品
        if (convertMenu != null) {
            convertMenu.restorePendingItems(player);
        }
    }

    // 玩家退出事件 - 保存玩家解锁数据
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        emcManager.onPlayerLogout(event.getPlayer());
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

        // 重载EMC物品配置
        if (emcManager != null) {
            emcManager.loadEMCValues(); // 重新加载物品配置

            // 清理所有在线玩家的无效解锁记录
            for (UUID playerId : onlinePlayers) {
                emcManager.cleanInvalidUnlocks(playerId);
            }
        }

        getLogger().info("配置已重载! 已清理 " + onlinePlayers.size() + " 名玩家的无效解锁记录");
    }

    public EMCManager getEmcManager() {
        return emcManager;
    }

    public static Economy getEconomy() {
        return economy;
    }
}