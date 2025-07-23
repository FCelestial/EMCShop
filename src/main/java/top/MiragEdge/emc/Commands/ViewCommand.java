package top.MiragEdge.emc.Commands;

import top.MiragEdge.emc.EMCShop;
import top.MiragEdge.emc.Gui.PreviewMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class ViewCommand implements CommandExecutor {

    private final PreviewMenu previewMenu;

    public ViewCommand(EMCShop plugin) {
        this.previewMenu = new PreviewMenu(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CommandUtils.isPlayer(sender)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令!", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;
        previewMenu.openPreviewMenu(player);
        return true;
    }
}