package top.MiragEdge.emc.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import top.MiragEdge.emc.EMCShop;

import java.util.*;

public class MainCommand implements CommandExecutor, TabExecutor {

    private final Map<String, CommandExecutor> subCommands = new HashMap<>();
    private final EMCShop plugin;

    public MainCommand(EMCShop plugin, top.MiragEdge.emc.Manager.EMCManager emcManager, top.MiragEdge.emc.Manager.ShopManager shopManager) {
        this.plugin = plugin;

        subCommands.put("purchase", new PurchaseCommand());
        subCommands.put("view", new ViewCommand());
        subCommands.put("convert", new ConvertCommand());
        subCommands.put("itememc", new ItememcCommand());
        subCommands.put("pay", new PayCommand());
        subCommands.put("give", new GiveCommand());
        subCommands.put("emc", new EMCCommand());
        subCommands.put("reload", new ReloadCommand(plugin));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        CommandExecutor subCommand = subCommands.get(args[0].toLowerCase());
        if (subCommand == null) {
            sender.sendMessage(Component.text("未知子命令! 使用 /emcshop 查看帮助", NamedTextColor.RED));
            return true;
        }

        String[] newArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
        return subCommand.onCommand(sender, cmd, label, newArgs);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("========== EMC商店帮助 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/emcshop purchase - 打开物品购买菜单", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/emcshop view - 打开物品预览菜单", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/emcshop convert - 打开物品转换菜单", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/emcshop itememc - 查看手持物品EMC值", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/emcshop pay <玩家> <数额> - 向玩家转账", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/emcshop emc [玩家] - 查看EMC余额", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/emcshop give <玩家|all> <数额> - 给予EMC (管理员)", NamedTextColor.RED));
        sender.sendMessage(Component.text("/emcshop reload - 重载插件 (管理员)", NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return new ArrayList<>(subCommands.keySet());
        }
        return Collections.emptyList();
    }
}