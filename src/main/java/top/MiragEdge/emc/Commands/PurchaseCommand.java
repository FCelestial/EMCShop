package top.MiragEdge.emc.Commands;

import top.MiragEdge.emc.EMCShop;
import top.MiragEdge.emc.Gui.PurchaseMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

public class PurchaseCommand implements CommandExecutor {

    private static final String PERMISSION = "emcshop.user.purchase";
    private static final String PERM_MESSAGE = "§6你无法使用!是因为没有权限唉";

    private final PurchaseMenu purchaseMenu;

    public PurchaseCommand(EMCShop plugin) {
        this.purchaseMenu = new PurchaseMenu(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CommandUtils.isPlayer(sender)) return true;
        if (!CommandUtils.checkPermission(sender, PERMISSION, PERM_MESSAGE)) return true;
        Player player = (Player) sender;

        purchaseMenu.openPurchaseMenu(player);
        return true;
    }
}