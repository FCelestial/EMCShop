package top.MiragEdge.emc.Commands;

import top.MiragEdge.emc.EMCShop;
import top.MiragEdge.emc.Gui.PreviewMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

public class ViewCommand implements CommandExecutor {

    private static final String PERMISSION = "emcshop.user.purchase";
    private static final String PERM_MESSAGE = "§c您还没有查看物品预览菜单的权限呢...";

    private final PreviewMenu previewMenu;

    public ViewCommand(EMCShop plugin) {
        this.previewMenu = new PreviewMenu(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CommandUtils.isPlayer(sender)) return true;
        if (!CommandUtils.checkPermission(sender, PERMISSION, PERM_MESSAGE)) return true;
        Player player = (Player) sender;

        previewMenu.openPreviewMenu(player);
        return true;
    }
}