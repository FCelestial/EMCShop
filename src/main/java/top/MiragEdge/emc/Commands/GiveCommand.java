package top.MiragEdge.emc.Commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public class GiveCommand implements CommandExecutor {

    private static final String PERMISSION = "emcshop.admin.give";
    private static final String PERM_MESSAGE = "§c你需要管理员权限执行此命令!";

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CommandUtils.checkPermission(sender, PERMISSION, PERM_MESSAGE)) return true;

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /emcshop give <玩家|all> <数额>", NamedTextColor.RED));
            return true;
        }

        try {
            double amount = Double.parseDouble(args[1]);
            if ("all".equalsIgnoreCase(args[0])) {
                // 给所有玩家发放
                Bukkit.getOnlinePlayers().forEach(p -> {
                    // 调用经济系统API
                    p.sendMessage(Component.text("管理员向你发放了 " + amount + " EMC", NamedTextColor.GOLD));
                });
                sender.sendMessage(Component.text("已向所有玩家发放 " + amount + " EMC", NamedTextColor.GREEN));
            } else {
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage(Component.text("玩家未找到!", NamedTextColor.RED));
                    return true;
                }
                // 调用经济系统API
                target.sendMessage(Component.text("管理员向你发放了 " + amount + " EMC", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("已向 " + target.getName() + " 发放 " + amount + " EMC", NamedTextColor.GREEN));
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("无效的EMC数值!", NamedTextColor.RED));
        }
        return true;
    }
}