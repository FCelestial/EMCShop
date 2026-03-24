package top.MiragEdge.emc.Commands;

import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public class CommandUtils {

    /**
     * 检查命令发送者是否有指定权限
     * 对于OP玩家，自动授予所有emcshop.*权限（兼容传统OP权限模式）
     */
    public static boolean checkPermission(CommandSender sender, String permission, String message) {
        // OP玩家自动拥有所有emcshop权限
        if (sender.isOp()) {
            return true;
        }

        if (!sender.hasPermission(permission)) {
            sender.sendMessage(Component.text(message, NamedTextColor.RED));
            return false;
        }
        return true;
    }

    public static boolean isPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行!", NamedTextColor.RED));
            return false;
        }
        return true;
    }
}