package top.MiragEdge.emc.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import top.MiragEdge.emc.Data.PlayerData;
import top.MiragEdge.emc.EMCShop;
import top.MiragEdge.emc.Manager.EMCManager;
import top.MiragEdge.emc.Utils.MessageUtil;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * EMC货币管理子命令
 * /emc money [add/remove/set] [player] [number]
 * 权限: emc.money (OP默认拥有)
 */
public class MoneyCommand implements CommandExecutor, TabExecutor {

    // 定义颜色方案
    private static final TextColor PRIMARY_COLOR = TextColor.fromHexString("#39C5BB");
    private static final TextColor SUCCESS_COLOR = TextColor.fromHexString("#4ADE80");
    private static final TextColor ERROR_COLOR = TextColor.fromHexString("#FF6B6B");
    private static final TextColor INFO_COLOR = TextColor.fromHexString("#A9DEF9");
    private static final TextColor WARNING_COLOR = TextColor.fromHexString("#FFD166");

    // 权限节点
    public static final String PERMISSION_BASE = "emc.money";
    public static final String PERMISSION_ALL = "emc.money.*";
    public static final String PERMISSION_ADD = "emc.money.add";
    public static final String PERMISSION_REMOVE = "emc.money.remove";
    public static final String PERMISSION_SET = "emc.money.set";
    public static final String PERMISSION_VIEW = "emc.money.view";

    private final EMCShop plugin;
    private final EMCManager emcManager;

    public MoneyCommand(EMCShop plugin, EMCManager emcManager) {
        this.plugin = plugin;
        this.emcManager = emcManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                           @NotNull String label, String[] args) {
        // 检查是否使用EMC独立经济模式
        if (!emcManager.isUsingEMCEconomy()) {
            sender.sendMessage(Component.text("此命令仅在 EMC 独立经济模式下可用", ERROR_COLOR));
            return true;
        }

        // 检查权限
        if (!hasPermission(sender)) {
            sender.sendMessage(MessageUtil.getInstance().getMessage("general.no-permission"));
            return true;
        }

        // 无参数时显示帮助
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "add":
                return handleAdd(sender, args);
            case "remove":
                return handleRemove(sender, args);
            case "set":
                return handleSet(sender, args);
            case "view":
                return handleView(sender, args);
            default:
                sender.sendMessage(Component.text("未知操作: " + action + "，使用 /emc money 查看帮助", ERROR_COLOR));
                return true;
        }
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADD) && !sender.hasPermission(PERMISSION_ALL)) {
            sender.sendMessage(MessageUtil.getInstance().getMessage("general.no-permission"));
            return true;
        }

        // 解析参数: money add [player] [number]
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /emc money add [player] <number>", WARNING_COLOR));
            sender.sendMessage(Component.text("不指定玩家时默认为自己", INFO_COLOR));
            return true;
        }

        String playerName;
        double amount;
        int amountIndex;

        // 判断第二个参数是玩家名还是数字
        if (isNumeric(args[1])) {
            playerName = sender.getName();
            amountIndex = 1;
        } else {
            if (args.length < 3) {
                sender.sendMessage(Component.text("用法: /emc money add [player] <number>", WARNING_COLOR));
                return true;
            }
            playerName = args[1];
            amountIndex = 2;
        }

        try {
            amount = Double.parseDouble(args[amountIndex]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("无效的金额: " + args[amountIndex], ERROR_COLOR));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(Component.text("金额必须大于 0", ERROR_COLOR));
            return true;
        }

        // 异步处理离线玩家
        CompletableFuture.runAsync(() -> {
            UUID playerId = getPlayerUUID(playerName);
            if (playerId == null) {
                sender.sendMessage(Component.text("找不到玩家: " + playerName, ERROR_COLOR));
                return;
            }

            String displayName = getDisplayName(playerId, playerName);
            modifyBalance(sender, playerId, displayName, amount, Operation.ADD);
        });

        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_REMOVE) && !sender.hasPermission(PERMISSION_ALL)) {
            sender.sendMessage(MessageUtil.getInstance().getMessage("general.no-permission"));
            return true;
        }

        // 解析参数: money remove [player] <number>
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /emc money remove [player] <number>", WARNING_COLOR));
            sender.sendMessage(Component.text("不指定玩家时默认为自己", INFO_COLOR));
            return true;
        }

        String playerName;
        double amount;
        int amountIndex;

        if (isNumeric(args[1])) {
            playerName = sender.getName();
            amountIndex = 1;
        } else {
            if (args.length < 3) {
                sender.sendMessage(Component.text("用法: /emc money remove [player] <number>", WARNING_COLOR));
                return true;
            }
            playerName = args[1];
            amountIndex = 2;
        }

        try {
            amount = Double.parseDouble(args[amountIndex]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("无效的金额: " + args[amountIndex], ERROR_COLOR));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(Component.text("金额必须大于 0", ERROR_COLOR));
            return true;
        }

        CompletableFuture.runAsync(() -> {
            UUID playerId = getPlayerUUID(playerName);
            if (playerId == null) {
                sender.sendMessage(Component.text("找不到玩家: " + playerName, ERROR_COLOR));
                return;
            }

            String displayName = getDisplayName(playerId, playerName);
            modifyBalance(sender, playerId, displayName, amount, Operation.REMOVE);
        });

        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_SET) && !sender.hasPermission(PERMISSION_ALL)) {
            sender.sendMessage(MessageUtil.getInstance().getMessage("general.no-permission"));
            return true;
        }

        // 解析参数: money set [player] <number>
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /emc money set [player] <number>", WARNING_COLOR));
            sender.sendMessage(Component.text("不指定玩家时默认为自己", INFO_COLOR));
            return true;
        }

        String playerName;
        double amount;
        int amountIndex;

        if (isNumeric(args[1])) {
            playerName = sender.getName();
            amountIndex = 1;
        } else {
            if (args.length < 3) {
                sender.sendMessage(Component.text("用法: /emc money set [player] <number>", WARNING_COLOR));
                return true;
            }
            playerName = args[1];
            amountIndex = 2;
        }

        try {
            amount = Double.parseDouble(args[amountIndex]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("无效的金额: " + args[amountIndex], ERROR_COLOR));
            return true;
        }

        if (amount < 0) {
            sender.sendMessage(Component.text("金额不能为负数", ERROR_COLOR));
            return true;
        }

        CompletableFuture.runAsync(() -> {
            UUID playerId = getPlayerUUID(playerName);
            if (playerId == null) {
                sender.sendMessage(Component.text("找不到玩家: " + playerName, ERROR_COLOR));
                return;
            }

            String displayName = getDisplayName(playerId, playerName);
            setBalanceDirect(sender, playerId, displayName, amount);
        });

        return true;
    }

    private boolean handleView(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_VIEW) && !sender.hasPermission(PERMISSION_ALL)) {
            sender.sendMessage(MessageUtil.getInstance().getMessage("general.no-permission"));
            return true;
        }

        String playerName;
        if (args.length > 1) {
            playerName = args[1];
        } else {
            if (sender instanceof Player) {
                playerName = sender.getName();
            } else {
                sender.sendMessage(Component.text("控制台必须指定玩家名", ERROR_COLOR));
                return true;
            }
        }

        CompletableFuture.runAsync(() -> {
            UUID playerId = getPlayerUUID(playerName);
            if (playerId == null) {
                sender.sendMessage(Component.text("找不到玩家: " + playerName, ERROR_COLOR));
                return;
            }

            String displayName = getDisplayName(playerId, playerName);
            double balance = getBalanceDirect(playerId);

            Component message = Component.text()
                    .append(Component.text("【EMC货币查询】\n", PRIMARY_COLOR))
                    .append(Component.text("玩家: ", INFO_COLOR))
                    .append(Component.text(displayName, PRIMARY_COLOR))
                    .append(Component.text("\n余额: ", INFO_COLOR))
                    .append(Component.text(String.format("%,.1f", balance), SUCCESS_COLOR))
                    .append(Component.text(" EMC", INFO_COLOR))
                    .build();

            sender.sendMessage(message);
        });

        return true;
    }

    private enum Operation { ADD, REMOVE }

    private void modifyBalance(CommandSender sender, UUID playerId, String displayName, double amount, Operation op) {
        double currentBalance = getBalanceDirect(playerId);
        double newBalance;

        if (op == Operation.ADD) {
            newBalance = currentBalance + amount;
        } else {
            newBalance = currentBalance - amount;
            if (newBalance < 0) {
                sender.sendMessage(Component.text("玩家 " + displayName + " 的余额不足以扣除 " +
                        String.format("%,.1f", amount) + " EMC", ERROR_COLOR));
                // 也通知玩家余额不足
                notifyPlayer(playerId, "money.insufficient-balance", amount);
                return;
            }
        }

        // 直接更新数据库
        if (setBalanceDirect(playerId, newBalance)) {
            String actionText = op == Operation.ADD ? "发放" : "扣除";
            Component message = Component.text()
                    .append(Component.text("【EMC货币管理】\n", PRIMARY_COLOR))
                    .append(Component.text(actionText + "成功\n", SUCCESS_COLOR))
                    .append(Component.text("玩家: ", INFO_COLOR))
                    .append(Component.text(displayName, PRIMARY_COLOR))
                    .append(Component.text("\n" + actionText + "金额: ", INFO_COLOR))
                    .append(Component.text(String.format("+%,.1f", amount), SUCCESS_COLOR))
                    .append(Component.text(" EMC\n", INFO_COLOR))
                    .append(Component.text("当前余额: ", INFO_COLOR))
                    .append(Component.text(String.format("%,.1f", newBalance), SUCCESS_COLOR))
                    .append(Component.text(" EMC", INFO_COLOR))
                    .build();
            sender.sendMessage(message);
            plugin.getLogger().info("管理员 " + sender.getName() + " " + actionText + "玩家 " + displayName + " 的 EMC: " + amount);

            // 通知玩家余额变动
            if (op == Operation.ADD) {
                notifyPlayer(playerId, "money.received", amount);
            } else {
                notifyPlayer(playerId, "money.deducted", amount);
            }
        } else {
            sender.sendMessage(Component.text("操作失败，请查看服务器日志", ERROR_COLOR));
        }
    }

    private void setBalanceDirect(CommandSender sender, UUID playerId, String displayName, double amount) {
        if (setBalanceDirect(playerId, amount)) {
            Component message = Component.text()
                    .append(Component.text("【EMC货币管理】\n", PRIMARY_COLOR))
                    .append(Component.text("设置成功\n", SUCCESS_COLOR))
                    .append(Component.text("玩家: ", INFO_COLOR))
                    .append(Component.text(displayName, PRIMARY_COLOR))
                    .append(Component.text("\n新余额: ", INFO_COLOR))
                    .append(Component.text(String.format("%,.1f", amount), SUCCESS_COLOR))
                    .append(Component.text(" EMC", INFO_COLOR))
                    .build();
            sender.sendMessage(message);
            plugin.getLogger().info("管理员 " + sender.getName() + " 设置玩家 " + displayName + " 的 EMC 余额为: " + amount);

            // 通知玩家余额已设置
            notifyPlayer(playerId, "money.balance-set", amount);
        } else {
            sender.sendMessage(Component.text("设置失败，请查看服务器日志", ERROR_COLOR));
        }
    }

    /**
     * 直接获取玩家余额（支持离线玩家）
     */
    private double getBalanceDirect(UUID playerId) {
        PlayerData data = emcManager.getPlayerData(playerId);
        if (data != null) {
            return data.getEmcBalance();
        }

        // 离线玩家，从数据库加载
        PlayerData offlineData = loadPlayerDataSync(playerId);
        return offlineData != null ? offlineData.getEmcBalance() : 0.0;
    }

    /**
     * 直接设置玩家余额（支持离线玩家）
     */
    private boolean setBalanceDirect(UUID playerId, double amount) {
        // 先尝试从缓存获取
        PlayerData data = emcManager.getPlayerData(playerId);

        if (data != null) {
            data.setEmcBalance(amount);
            plugin.getEmcManager().getDbManager().savePlayerDataAsync(data);
            return true;
        }

        // 离线玩家，需要创建新的PlayerData并保存
        PlayerData offlineData = loadPlayerDataSync(playerId);
        if (offlineData == null) {
            offlineData = new PlayerData(playerId);
        }
        offlineData.setEmcBalance(amount);
        plugin.getEmcManager().getDbManager().savePlayerDataAsync(offlineData);
        return true;
    }

    /**
     * 同步加载玩家数据（用于离线玩家）
     */
    private PlayerData loadPlayerDataSync(UUID playerId) {
        try (java.sql.Connection conn = plugin.getEmcManager().getDbManager()
                .getConnector().getConnection()) {

            PlayerData playerData = new PlayerData(playerId);

            // 查询余额
            String balanceSql = "SELECT balance FROM player_emc_balance WHERE player_uuid = ?";
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(balanceSql)) {
                stmt.setString(1, playerId.toString());
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        playerData.setEmcBalance(rs.getDouble("balance"));
                    }
                }
            }

            // 查询解锁物品
            String unlocksSql = "SELECT item_id FROM player_unlocks WHERE player_uuid = ?";
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(unlocksSql)) {
                stmt.setString(1, playerId.toString());
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        playerData.unlockItem(rs.getString("item_id"));
                    }
                }
            }

            return playerData;
        } catch (java.sql.SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "加载玩家 " + playerId + " 数据失败", e);
            return null;
        }
    }

    /**
     * 通过玩家名获取UUID（支持离线玩家）
     * 验证玩家是否真实存在过
     */
    private UUID getPlayerUUID(String playerName) {
        // 先检查在线玩家
        Player player = Bukkit.getPlayer(playerName);
        if (player != null) {
            return player.getUniqueId();
        }

        // 尝试直接解析玩家名作为UUID
        try {
            return UUID.fromString(playerName);
        } catch (IllegalArgumentException ignored) {
            // 不是有效的 UUID，继续检查
        }

        // 获取离线玩家
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        if (offlinePlayer != null && offlinePlayer.getUniqueId() != null) {
            // 验证这个玩家是否真实存在过（检查数据库中是否有记录）
            if (hasPlayerData(offlinePlayer.getUniqueId())) {
                return offlinePlayer.getUniqueId();
            }
        }

        return null;
    }

    /**
     * 检查玩家是否在数据库中存在
     */
    private boolean hasPlayerData(UUID playerId) {
        try (java.sql.Connection conn = plugin.getEmcManager().getDbManager()
                .getConnector().getConnection()) {

            String sql = "SELECT 1 FROM player_emc_balance WHERE player_uuid = ? LIMIT 1";
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (java.sql.SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "检查玩家数据失败: " + playerId, e);
            // 数据库查询失败时，允许操作继续（可能是新玩家）
            return true;
        }
    }

    /**
     * 获取玩家显示名称
     */
    private String getDisplayName(UUID playerId, String fallbackName) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        if (offlinePlayer != null && offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }

        return fallbackName;
    }

    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 向玩家发送通知消息
     */
    private void notifyPlayer(UUID playerId, String messageKey, double amount) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", String.format("%,.1f", amount));
            placeholders.put("currency", MessageUtil.getInstance().getCurrencyName());
            player.sendMessage(MessageUtil.getInstance().getMessage(messageKey, placeholders));
        }
    }

    private boolean hasPermission(CommandSender sender) {
        // OP默认拥有所有权限
        if (sender.isOp()) {
            return true;
        }
        return sender.hasPermission(PERMISSION_BASE) ||
               sender.hasPermission(PERMISSION_ALL);
    }

    private void sendHelp(CommandSender sender) {
        Component header = Component.text()
                .append(Component.text("═══════════════════════════\n", PRIMARY_COLOR))
                .append(Component.text("   EMC 货币管理帮助\n", PRIMARY_COLOR))
                .append(Component.text("═══════════════════════════\n", PRIMARY_COLOR))
                .build();

        Component addCmd = Component.text()
                .append(Component.text("/emc money add", INFO_COLOR))
                .append(Component.text(" [玩家] <数量>\n", INFO_COLOR))
                .append(Component.text("  - 发放 EMC 货币给玩家\n", NamedTextColor.GRAY))
                .append(Component.text("  - 权限: emc.money.add\n", NamedTextColor.DARK_GRAY))
                .build();

        Component removeCmd = Component.text()
                .append(Component.text("/emc money remove", INFO_COLOR))
                .append(Component.text(" [玩家] <数量>\n", INFO_COLOR))
                .append(Component.text("  - 从玩家扣除 EMC 货币\n", NamedTextColor.GRAY))
                .append(Component.text("  - 权限: emc.money.remove\n", NamedTextColor.DARK_GRAY))
                .build();

        Component setCmd = Component.text()
                .append(Component.text("/emc money set", INFO_COLOR))
                .append(Component.text(" [玩家] <数量>\n", INFO_COLOR))
                .append(Component.text("  - 设置玩家 EMC 余额\n", NamedTextColor.GRAY))
                .append(Component.text("  - 权限: emc.money.set\n", NamedTextColor.DARK_GRAY))
                .build();

        Component viewCmd = Component.text()
                .append(Component.text("/emc money view", INFO_COLOR))
                .append(Component.text(" [玩家]\n", INFO_COLOR))
                .append(Component.text("  - 查看玩家 EMC 余额\n", NamedTextColor.GRAY))
                .append(Component.text("  - 权限: emc.money.view\n", NamedTextColor.DARK_GRAY))
                .build();

        Component tip = Component.text()
                .append(Component.text("\n提示: ", WARNING_COLOR))
                .append(Component.text("不指定玩家时默认为命令发送者\n", NamedTextColor.GRAY))
                .append(Component.text("      OP 拥有所有货币管理权限\n", NamedTextColor.GRAY))
                .build();

        sender.sendMessage(Component.empty());
        sender.sendMessage(header);
        sender.sendMessage(addCmd);
        sender.sendMessage(removeCmd);
        sender.sendMessage(setCmd);
        sender.sendMessage(viewCmd);
        sender.sendMessage(tip);
        sender.sendMessage(Component.empty());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> actions = new ArrayList<>();
            if (sender.hasPermission(PERMISSION_ADD) || sender.hasPermission(PERMISSION_ALL) || sender.isOp()) {
                actions.add("add");
            }
            if (sender.hasPermission(PERMISSION_REMOVE) || sender.hasPermission(PERMISSION_ALL) || sender.isOp()) {
                actions.add("remove");
            }
            if (sender.hasPermission(PERMISSION_SET) || sender.hasPermission(PERMISSION_ALL) || sender.isOp()) {
                actions.add("set");
            }
            if (sender.hasPermission(PERMISSION_VIEW) || sender.hasPermission(PERMISSION_ALL) || sender.isOp()) {
                actions.add("view");
            }
            actions.add("help");

            String input = args[0].toLowerCase();
            completions.addAll(actions.stream()
                    .filter(a -> a.startsWith(input))
                    .toList());
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            if ((action.equals("add") && (sender.hasPermission(PERMISSION_ADD) || sender.hasPermission(PERMISSION_ALL) || sender.isOp())) ||
                (action.equals("remove") && (sender.hasPermission(PERMISSION_REMOVE) || sender.hasPermission(PERMISSION_ALL) || sender.isOp())) ||
                (action.equals("set") && (sender.hasPermission(PERMISSION_SET) || sender.hasPermission(PERMISSION_ALL) || sender.isOp())) ||
                (action.equals("view") && (sender.hasPermission(PERMISSION_VIEW) || sender.hasPermission(PERMISSION_ALL) || sender.isOp()))) {

                // 补全在线玩家名
                String input = args[1].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(input)) {
                        completions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 3) {
            String action = args[0].toLowerCase();
            if ((action.equals("add") && (sender.hasPermission(PERMISSION_ADD) || sender.hasPermission(PERMISSION_ALL) || sender.isOp())) ||
                (action.equals("remove") && (sender.hasPermission(PERMISSION_REMOVE) || sender.hasPermission(PERMISSION_ALL) || sender.isOp())) ||
                (action.equals("set") && (sender.hasPermission(PERMISSION_SET) || sender.hasPermission(PERMISSION_ALL) || sender.isOp()))) {

                // 补全常用金额
                String[] commonAmounts = {"100", "1000", "10000", "100000"};
                String input = args[2].toLowerCase();
                for (String amount : commonAmounts) {
                    if (amount.startsWith(input)) {
                        completions.add(amount);
                    }
                }
            }
        }

        return completions;
    }
}
