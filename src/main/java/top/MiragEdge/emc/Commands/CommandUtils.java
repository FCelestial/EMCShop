package top.MiragEdge.emc.Commands;

import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public class CommandUtils {

    public static boolean checkPermission(CommandSender sender, String permission, String message) {
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