package top.MiragEdge.emc;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import top.MiragEdge.emc.Commands.MainCommand;
import top.MiragEdge.emc.Database.DatabaseManager;
import top.MiragEdge.emc.Manager.EMCManager;
import top.MiragEdge.emc.Manager.ShopManager;

import java.util.Objects;

public class EMCShop extends JavaPlugin {

    private static EMCShop instance;
    private DatabaseManager databaseManager;
    private EMCManager emcManager;
    private ShopManager shopManager;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        instance = this;

        // 保存默认配置
        saveDefaultConfig();
        config = getConfig();

        // 初始化数据库
        databaseManager = new DatabaseManager(this);
        databaseManager.initDatabase();

        // 初始化管理器
        emcManager = new EMCManager(this, databaseManager);
        shopManager = new ShopManager(this);

        // 注册命令
        MainCommand commandExecutor = new MainCommand(this, emcManager, shopManager);
        Objects.requireNonNull(getCommand("emcshop")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("emcshop")).setTabCompleter(commandExecutor);

        getLogger().info(getDescription().getName() + " v" + getDescription().getVersion() + " 已启用!");
    }

    @Override
    public void onDisable() {
        // 关闭数据库连接
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }

        getLogger().info(getDescription().getName() + " 已禁用!");
    }

    /**
     * 重载插件配置
     */
    public void reloadPlugin() {
        reloadConfig();
        config = getConfig();

        // 重载管理器配置
        if (emcManager != null) {
            emcManager.loadConfig();
        }

        if (shopManager != null) {
            shopManager.loadConfig();
        }

        getLogger().info("配置已重载!");
    }

    public static EMCShop getInstance() {
        return instance;
    }

    public EMCManager getEmcManager() {
        return emcManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}