package top.MiragEdge.emc.Utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class MessageUtil {

    private static MessageUtil instance;
    private final JavaPlugin plugin;
    private FileConfiguration messageConfig;
    private File messageFile;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // 默认消息配置
    private final Map<String, String> defaultMessages = new HashMap<>();

    private MessageUtil(JavaPlugin plugin) {
        this.plugin = plugin;
        setupDefaultMessages();
        loadMessageConfig();
    }

    public static void initialize(JavaPlugin plugin) {
        if (instance == null) {
            instance = new MessageUtil(plugin);
        }
    }

    public static MessageUtil getInstance() {
        return instance;
    }

    private void setupDefaultMessages() {
        // 转换菜单消息
        defaultMessages.put("convert-menu.title", "<gradient:#FFAA00:#39C5BB>等价交换</gradient> - <color:#55AAFF><bold>物品转换</bold></color>");
        defaultMessages.put("convert-menu.items-restored", "<gradient:#FFAA00:#55AAFF>已恢复您上次在物品转换菜单中的物品</gradient>");
        defaultMessages.put("convert-menu.items-returned", "<gradient:#FF5555:#FFAA00>由于您上次非正常退出，物品转换菜单中的物品已退还至您的背包和脚下</gradient>");
        defaultMessages.put("convert-menu.no-items", "<gradient:#FFAA00:#FF5555>没发现任何物品呢？？</gradient>");
        defaultMessages.put("convert-menu.no-convertible-items", "<gradient:#FFAA00:#FF5555>好像没有可转换的物品哦~</gradient>");
        defaultMessages.put("convert-menu.conversion-success", "<gradient:#FFAA00:#55FF55>转换物品获得了 %s 灵叶</gradient>");
        defaultMessages.put("convert-menu.unlocked-items", "<color:#55FF55>解锁了新物品: </color>");
        defaultMessages.put("convert-menu.skipped-items", "<color:#FFAA00><italic>已自动跳过背包中无法转换的物品。</italic></color>");
        defaultMessages.put("convert-menu.some-items-returned", "<color:#FFAA00><italic>部分无法转换的物品已退还。</italic></color>");
        defaultMessages.put("convert-menu.items-dropped", "<gradient:#FF5555:#FFAA00>部分无法转换的物品因背包已满已掉落在地</gradient>");
        defaultMessages.put("convert-menu.error-occurred", "<gradient:#FF5555:#FFAA00>转换过程出错，物品已保存</gradient>");
        defaultMessages.put("convert-menu.menu-saved", "<gradient:#FFAA00:#FF5555>菜单异常关闭，物品已保存</gradient>");
        defaultMessages.put("convert-menu.backpack-sold", "<color:#55FF55>出售背包物品获得 </color><color:#FFFF55><bold>%s</bold></color><color:#55FF55> 灵叶</color>");
        defaultMessages.put("convert-menu.no-backpack-items", "<color:#FF5555>背包中没有可出售的基础物品！</color>");

        // 预览菜单消息
        defaultMessages.put("preview-menu.title", "<color:#9B5DE5>物</color><color:#8A5DE5>品</color><color:#7A5DE5>预</color><color:#6A5DE5>览</color> | 第 %page%/%total% 页");
        defaultMessages.put("preview-menu.unlocked", "<color:#9EE6A0>已解锁</color>");
        defaultMessages.put("preview-menu.locked", "<color:#FF6B6B>未解锁</color>");
        defaultMessages.put("preview-menu.unlock-hint", "<color:#9EE6CF>通过转换来解锁物品</color>");

        // 购买菜单消息
        defaultMessages.put("purchase-menu.title", "<color:#39C5BB>等价</color><color:#36B9C5>交</color><color:#32ADC0>换</color><color:#2EA2BA> 商</color><color:#2A96B5>店</color> | 第 %page%/%total% 页");
        defaultMessages.put("purchase-menu.insufficient-funds", "<color:#FF6B6B>余额不足! 需要 </color><color:#FFB347>%price% 灵叶</color>");
        defaultMessages.put("purchase-menu.invalid-item", "<color:#FF6B6B>无效的物品ID: %item%</color>");
        defaultMessages.put("purchase-menu.inventory-full", "<color:#FF6B6B>背包空间不足!</color>");
        defaultMessages.put("purchase-menu.purchase-success", "<color:#9EE6A0>成功购买 </color><color:#39C5BB>%item%</color><color:#FFB347> × %amount%</color><color:#9EE6A0> 花费 </color><color:#FFB347>%price% 灵叶</color>\\n<color:#D3D3D3>(基础价值: %base_value% + 重构损耗: %loss%)</color>");

        // 通用消息
        defaultMessages.put("currency.name", "硬币");
        defaultMessages.put("general.data-loading", "<color:#FFD166>正在加载您的数据，请稍候...</color>");
        defaultMessages.put("general.comma-separator", ", ");
        defaultMessages.put("general.and-separator", " 和 ");
    }

    private void loadMessageConfig() {
        messageFile = new File(plugin.getDataFolder(), "message.yml");

        if (!messageFile.exists()) {
            plugin.saveResource("message.yml", false);
            plugin.getLogger().info("创建默认消息配置文件...");
        }

        reloadMessageConfig();
    }

    public void reloadMessageConfig() {
        messageConfig = YamlConfiguration.loadConfiguration(messageFile);

        // 检查并添加缺失的配置项
        boolean needsSave = false;
        for (Map.Entry<String, String> entry : defaultMessages.entrySet()) {
            if (!messageConfig.contains(entry.getKey())) {
                messageConfig.set(entry.getKey(), entry.getValue());
                needsSave = true;
            }
        }

        if (needsSave) {
            try {
                messageConfig.save(messageFile);
                plugin.getLogger().info("消息配置文件已更新，添加了缺失的配置项");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "无法保存消息配置文件: " + e.getMessage());
            }
        }

        plugin.getLogger().info("消息配置文件已重载");
    }

    public Component getMessage(String key) {
        return getMessage(key, new HashMap<>());
    }

    public Component getMessage(String key, Map<String, String> placeholders) {
        String message = messageConfig.getString(key);

        if (message == null) {
            message = defaultMessages.getOrDefault(key, "&cMissing message: " + key);
            plugin.getLogger().warning("消息键 '" + key + "' 未在配置文件中找到，使用默认值");
        }

        // 应用占位符
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        try {
            return miniMessage.deserialize(message);
        } catch (Exception e) {
            plugin.getLogger().warning("解析消息时出错 (key: " + key + "): " + e.getMessage());
            return Component.text(message);
        }
    }

    public String getRawMessage(String key) {
        return messageConfig.getString(key, defaultMessages.get(key));
    }

    public String getCurrencyName() {
        return getRawMessage("currency.name");
    }
}