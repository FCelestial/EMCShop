package top.MiragEdge.emc.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class ItememcCommand implements CommandExecutor {

    public ItememcCommand() {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CommandUtils.isPlayer(sender)) return true;
        Player player = (Player) sender;

        // 操作
        // ShopMenu.openPurchaseMenu(player);
        player.sendMessage(Component.text("手上物品价值", NamedTextColor.GREEN));
        return true;
    }
}