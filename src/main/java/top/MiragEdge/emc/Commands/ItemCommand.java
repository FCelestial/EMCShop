package top.MiragEdge.emc.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令!", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() == Material.AIR) {
            player.sendMessage(Component.text("请手持一个物品!", NamedTextColor.RED));
            return true;
        }

        Material material = itemInHand.getType();
        String itemId = material.toString();
        int emcValue = emcManager.getItemValue(itemId);

        if (emcValue <= 0) {
            player.sendMessage(Component.text("该物品无法转换!", NamedTextColor.RED));
            return true;
        }

        // --- 新增逻辑 ---
        // 1. 计算重构价格
        // 由于emcValue是int类型，为了确保乘法结果是double类型并保留小数，这里进行强制类型转换
        double deconstructionValue = (double) emcValue * (1 + deconstructionFactor);

        // 2. 检查解锁状态
        boolean unlocked = emcManager.isItemUnlocked(player, itemId);
        // ---------------

        Component itemName = LocalizationUtil.getLocalizedName(itemInHand);

        // --- 重构消息组件以包含新信息 ---
        Component message = Component.text()
                .append(Component.text("≡≡≡ 物品信息 ≡≡≡", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("物品: ", NamedTextColor.GRAY))
                .append(itemName.colorIfAbsent(NamedTextColor.WHITE)) // 使用白色更清晰
                .append(Component.newline())

                // 转化价值 (原逻辑)
                .append(Component.text("转化价值: ", NamedTextColor.GRAY))
                .append(Component.text(emcValue + " 🍃", NamedTextColor.GOLD))
                .append(Component.newline())

                // 重构价格 (新) - 使用新的 DecimalFormat
                .append(Component.text("重构价格: ", NamedTextColor.GRAY))
                .append(Component.text(priceFormat.format(deconstructionValue) + " 🍃", NamedTextColor.YELLOW))
                .append(Component.newline())

                // 解锁状态 (新)
                .append(Component.text("解锁状态: ", NamedTextColor.GRAY))
                .append(unlocked ? Component.text("✔ 已解锁", NamedTextColor.GREEN) : Component.text("✖ 未解锁", NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.newline()) // 添加一个空行以分隔

                // 堆叠数量和ID (原逻辑)
                .append(Component.text("最大堆叠: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(itemInHand.getMaxStackSize(), NamedTextColor.GRAY))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("ID: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(itemId.toLowerCase(), NamedTextColor.GRAY))
                .build();

        player.sendMessage(message);
        return true;
    }
}