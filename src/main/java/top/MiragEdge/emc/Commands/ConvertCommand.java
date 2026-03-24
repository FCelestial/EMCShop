package top.MiragEdge.emc.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import top.MiragEdge.emc.Gui.ConvertMenu;
import top.MiragEdge.emc.Manager.EMCManager;
import top.MiragEdge.emc.EMCShop;

public class ConvertCommand implements CommandExecutor {

    private static final String PERMISSION = "emcshop.user.convert";
    private static final String PERM_MESSAGE = "§6你无法使用!是因为没有权限唉";

    private final ConvertMenu convertMenu;

    public ConvertCommand(EMCShop plugin, EMCManager emcManager) {
        this.convertMenu = new ConvertMenu(plugin, emcManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CommandUtils.isPlayer(sender)) return true;
        if (!CommandUtils.checkPermission(sender, PERMISSION, PERM_MESSAGE)) return true;
        Player player = (Player) sender;

        // 打开转换菜单
        convertMenu.open(player);
        return true;
    }
}