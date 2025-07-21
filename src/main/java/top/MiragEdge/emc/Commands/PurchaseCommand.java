package top.MiragEdge.emc.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PurchaseCommand implements CommandExecutor {

    public PurchaseCommand() {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CommandUtils.isPlayer(sender)) return true;
        Player player = (Player) sender;

        // 打开菜单
        // ShopMenu.openPurchaseMenu(player);
        player.sendMessage(Component.text("已打开物品购买菜单", NamedTextColor.GREEN));
        return true;
    }
}