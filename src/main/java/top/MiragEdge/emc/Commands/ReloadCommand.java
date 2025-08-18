package top.MiragEdge.emc.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import top.MiragEdge.emc.EMCShop;

public class ReloadCommand implements CommandExecutor {

    private static final String PERMISSION = "emcshop.admin.reload";
    private static final String PERM_MESSAGE = "§c你需要管理员权限执行此命令!";

    private final EMCShop plugin;

    public ReloadCommand(EMCShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CommandUtils.checkPermission(sender, PERMISSION, PERM_MESSAGE))
            return true;

        plugin.reloadPlugin();
        sender.sendMessage(Component.text("EMCShop 配置已重载!", NamedTextColor.GREEN));
        return true;
    }
}