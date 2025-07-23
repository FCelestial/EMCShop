package top.MiragEdge.emc.Commands;

import top.MiragEdge.emc.EMCShop;
import top.MiragEdge.emc.Gui.PurchaseMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

public class PurchaseCommand implements CommandExecutor {

    private final PurchaseMenu purchaseMenu;

    public PurchaseCommand(EMCShop plugin) {
        this.purchaseMenu = new PurchaseMenu(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CommandUtils.isPlayer(sender)) return true;
        Player player = (Player) sender;

        purchaseMenu.openPurchaseMenu(player);
        return true;
    }
}