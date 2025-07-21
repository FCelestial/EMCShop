package top.MiragEdge.emc.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PayCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CommandUtils.isPlayer(sender)) return true;
        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /emcshop pay <玩家> <数额>", NamedTextColor.RED));
            return true;
        }

        try {
            Player target = sender.getServer().getPlayer(args[0]);
            double amount = Double.parseDouble(args[1]);

            if (target == null) {
                player.sendMessage(Component.text("玩家未在线!", NamedTextColor.RED));
                return true;
            }

            // 这里调用经济系统API实现转账
            player.sendMessage(Component.text()
                    .append(Component.text("成功向 ", NamedTextColor.GREEN))
                    .append(Component.text(target.getName(), NamedTextColor.AQUA))
                    .append(Component.text(" 转账 " + amount + " EMC", NamedTextColor.GREEN)));

        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的EMC数值!", NamedTextColor.RED));
        }
        return true;
    }
}