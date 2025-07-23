package top.MiragEdge.emc.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import top.MiragEdge.emc.Gui.TransmutationGUI;
import top.MiragEdge.emc.Manager.EMCManager;
import top.MiragEdge.emc.EMCShop;

public class ConvertCommand implements CommandExecutor {

    private final TransmutationGUI transmutationGui;

    public ConvertCommand(EMCShop plugin, EMCManager emcManager) {
        this.transmutationGui = new TransmutationGUI(plugin, emcManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!CommandUtils.isPlayer(sender)) return true;
        Player player = (Player) sender;

        // 打开转换菜单
        transmutationGui.open(player);
        return true;
    }
}