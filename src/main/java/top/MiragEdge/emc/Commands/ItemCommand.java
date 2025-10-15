package top.MiragEdge.emc.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import top.MiragEdge.emc.EMCShop;
import top.MiragEdge.emc.Manager.EMCManager;
import top.MiragEdge.emc.Utils.LocalizationUtil;

import java.text.DecimalFormat; // 导入 DecimalFormat 类

public class ItemCommand implements CommandExecutor {

    // 定义渐变色方案
    private static final TextColor PRIMARY_COLOR = TextColor.fromHexString("#39C5BB"); // 主色调 - 蓝绿色
    private static final TextColor SECONDARY_COLOR = TextColor.fromHexString("#FFD166"); // 辅色调 - 琥珀色
    private static final TextColor HIGHLIGHT_COLOR = TextColor.fromHexString("#FFB347"); // 高亮色 - 橙黄色
    private static final TextColor SUCCESS_COLOR = TextColor.fromHexString("#9EE6A0"); // 成功色 - 亮绿色
    private static final TextColor ERROR_COLOR = TextColor.fromHexString("#FF6B6B"); // 错误色 - 珊瑚红
    private static final TextColor NEUTRAL_COLOR = TextColor.fromHexString("#D3D3D3"); // 中性色 - 浅灰色
    private static final TextColor HEADER_COLOR = TextColor.fromHexString("#2A96B5"); // 标题色 - 深蓝绿

    private final EMCManager emcManager;
    private final double deconstructionFactor;
    // 将 DecimalFormat 的格式改为 "#,##0.00" 以精确到两位小数
    private final DecimalFormat priceFormat = new DecimalFormat("#,##0.00");

    /**
     * 构造函数现在会从主插件实例中获取 EMC 管理器和重构损耗配置。
     * @param plugin 主插件实例
     */
    public ItemCommand(EMCShop plugin) {
        this.emcManager = plugin.getEmcManager();
        // 从配置中读取重构损耗率，如果未设置则默认为 0.015 (1.5%)
        this.deconstructionFactor = plugin.getConfig().getDouble("purchase.reconstruction-loss", 0.015);
    }

    /**
     * onCommand 方法现在会显示转化价值、重构价格和解锁状态。
     */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令!", ERROR_COLOR));
            return true;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() == Material.AIR) {
            player.sendMessage(Component.text("请手持一个物品!", ERROR_COLOR));
            return true;
        }

        Material material = itemInHand.getType();
        String itemId = material.toString();
        double emcValue = emcManager.getItemValue(itemId);

        if (emcValue <= 0) {
            player.sendMessage(Component.text("该物品无法转换!", ERROR_COLOR));
            return true;
        }

        // 1. 计算重构价格
        double deconstructionValue = (double) emcValue * (1 + deconstructionFactor);

        // 2. 检查解锁状态
        boolean unlocked = emcManager.isItemUnlocked(player, itemId);
        // ---------------

        Component itemName = LocalizationUtil.getLocalizedName(itemInHand);

        // --- 重构消息组件以包含新信息 ---
        Component message = Component.text()
                // 标题使用渐变效果
                .append(createGradientHeader("≡≡≡ 物品信息 ≡≡≡"))
                .append(Component.newline())

                // 物品名称
                .append(Component.text("物品: ", NEUTRAL_COLOR))
                .append(itemName.colorIfAbsent(PRIMARY_COLOR)) // 使用主色调
                .append(Component.newline())
                .append(Component.newline())

                // 转化价值 (原逻辑)
                .append(Component.text("转化价值: ", NEUTRAL_COLOR))
                .append(Component.text(emcValue + " 灵叶", SECONDARY_COLOR))
                .append(Component.newline())

                // 重构价格 (新) - 使用新的 DecimalFormat
                .append(Component.text("重构价格: ", NEUTRAL_COLOR))
                .append(Component.text(priceFormat.format(deconstructionValue) + " 灵叶", HIGHLIGHT_COLOR))
                .append(Component.newline())
                .append(Component.newline())

                // 解锁状态 (新)
                .append(Component.text("解锁状态: ", NEUTRAL_COLOR))
                .append(unlocked ?
                        Component.text("✔ 已解锁", SUCCESS_COLOR) :
                        Component.text("✖ 未解锁", ERROR_COLOR))
                .append(Component.newline())
                .append(Component.newline()) // 添加一个空行以分隔

                // 堆叠数量和ID (原逻辑)
                .append(Component.text("最大堆叠: ", NEUTRAL_COLOR))
                .append(Component.text(itemInHand.getMaxStackSize(), SECONDARY_COLOR))
                .append(Component.text(" | ", NEUTRAL_COLOR))
                .append(Component.text("ID: ", NEUTRAL_COLOR))
                .append(Component.text(itemId.toLowerCase(), SECONDARY_COLOR))
                .build();

        player.sendMessage(message);
        return true;
    }

    // 创建渐变色标题
    private Component createGradientHeader(String text) {
        return Component.text()
                .append(Component.text("≡≡≡ ", HEADER_COLOR))
                .append(Component.text("物", TextColor.fromHexString("#36B9C5")))
                .append(Component.text("品", TextColor.fromHexString("#32ADC0")))
                .append(Component.text("信", TextColor.fromHexString("#2EA2BA")))
                .append(Component.text("息", TextColor.fromHexString("#2A96B5")))
                .append(Component.text(" ≡≡≡", HEADER_COLOR))
                .decoration(TextDecoration.BOLD, true)
                .build();
    }
}