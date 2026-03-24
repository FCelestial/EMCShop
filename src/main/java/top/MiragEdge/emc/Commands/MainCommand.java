package top.MiragEdge.emc.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import top.MiragEdge.emc.EMCShop;

import java.util.*;

public class MainCommand implements CommandExecutor, TabExecutor {

    // 定义渐变色方案
    private static final TextColor PRIMARY_COLOR = TextColor.fromHexString("#39C5BB"); // 主色调 - 蓝绿色
    private static final TextColor SECONDARY_COLOR = TextColor.fromHexString("#FFD166"); // 辅色调 - 琥珀色
    private static final TextColor ACCENT_COLOR = TextColor.fromHexString("#FF6B6B"); // 强调色 - 珊瑚红
    private static final TextColor INFO_COLOR = TextColor.fromHexString("#A9DEF9"); // 信息色 - 淡蓝色
    private static final TextColor HEADER_COLOR = TextColor.fromHexString("#2A96B5"); // 标题色 - 深蓝绿

    private final Map<String, CommandExecutor> subCommands = new HashMap<>();
    private final EMCShop plugin;

    public MainCommand(EMCShop plugin, top.MiragEdge.emc.Manager.EMCManager emcManager) {
        this.plugin = plugin;

        subCommands.put("purchase", new PurchaseCommand(plugin));
        subCommands.put("view", new ViewCommand(plugin));
        subCommands.put("convert", new ConvertCommand(plugin, emcManager));
        subCommands.put("item", new ItemCommand(plugin));
        subCommands.put("reload", new ReloadCommand(plugin));
        subCommands.put("money", new MoneyCommand(plugin, emcManager));
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
            sender.sendMessage(Component.text("未知子命令! 使用 /emcshop 查看帮助", ACCENT_COLOR));
            return true;
        }

        String[] newArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
        return subCommand.onCommand(sender, cmd, label, newArgs);
    }

    private void sendHelp(CommandSender sender) {
        // 创建渐变色标题
        Component title = Component.text()
                .append(Component.text("≡≡≡ ", HEADER_COLOR))
                .append(Component.text("E", TextColor.fromHexString("#36B9C5")))
                .append(Component.text("M", TextColor.fromHexString("#32ADC0")))
                .append(Component.text("C", TextColor.fromHexString("#2EA2BA")))
                .append(Component.text("商店帮助", TextColor.fromHexString("#2A96B5")))
                .append(Component.text(" ≡≡≡", HEADER_COLOR))
                .decoration(TextDecoration.BOLD, true)
                .build();

        // 创建渐变色页脚
        Component footer = Component.text()
                .append(Component.text("≡≡≡ ", HEADER_COLOR))
                .append(Component.text("狐风轩汐", TextColor.fromHexString("#36B9C5")))
                .append(Component.text(" & ", TextColor.fromHexString("#32ADC0")))
                .append(Component.text("FwindEmi", TextColor.fromHexString("#2EA2BA")))
                .append(Component.text(" ≡≡≡", HEADER_COLOR))
                .decoration(TextDecoration.BOLD, true)
                .build();

        sender.sendMessage(Component.empty());
        sender.sendMessage(title);
        sender.sendMessage(createCommandHelp("/emcshop purchase", "打开物品重构菜单", PRIMARY_COLOR));
        sender.sendMessage(createCommandHelp("/emcshop view", "打开物品预览菜单", PRIMARY_COLOR));
        sender.sendMessage(createCommandHelp("/emcshop convert", "打开物品转换菜单", PRIMARY_COLOR));
        sender.sendMessage(createCommandHelp("/buy", "打开物品重构菜单(快捷)", SECONDARY_COLOR));
        sender.sendMessage(createCommandHelp("/view", "打开物品预览菜单(快捷)", SECONDARY_COLOR));
        sender.sendMessage(createCommandHelp("/sell", "打开物品转换菜单(快捷)", SECONDARY_COLOR));
        sender.sendMessage(createCommandHelp("/emcshop item", "查看手持物品的灵叶价值", INFO_COLOR));
        sender.sendMessage(createCommandHelp("/emcshop money", "EMC货币管理（仅EMC模式）", INFO_COLOR));
        sender.sendMessage(createCommandHelp("/emcshop reload", "重载等价交换配置", ACCENT_COLOR));
        sender.sendMessage(footer);
        sender.sendMessage(Component.empty());
    }

    // 创建带颜色的命令帮助条目
    private Component createCommandHelp(String command, String description, TextColor color) {
        return Component.text()
                .append(Component.text(command, color))
                .append(Component.text(" - ", TextColor.fromHexString("#D3D3D3")))
                .append(Component.text(description, color))
                .build();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return new ArrayList<>(subCommands.keySet());
        }

        // 委托给子命令处理 Tab 补全
        CommandExecutor subCommand = subCommands.get(args[0].toLowerCase());
        if (subCommand instanceof TabExecutor tabExecutor) {
            String[] newArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
            return tabExecutor.onTabComplete(sender, cmd, alias, newArgs);
        }

        return Collections.emptyList();
    }

    public EMCShop getPlugin() {
        return plugin;
    }
}