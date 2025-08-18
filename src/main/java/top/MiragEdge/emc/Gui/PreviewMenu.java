package top.MiragEdge.emc.Gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import top.MiragEdge.emc.EMCShop;
import top.MiragEdge.emc.Manager.EMCManager;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PreviewMenu implements Listener, InventoryHolder {

    // 定义紫色调渐变色方案
    private static final TextColor PRIMARY_COLOR = TextColor.fromHexString("#A974FF"); // 主色调 - 紫色
    private static final TextColor SECONDARY_COLOR = TextColor.fromHexString("#FF6B6B"); // 辅色调 - 珊瑚红
    private static final TextColor ACCENT_COLOR = TextColor.fromHexString("#FFD166"); // 强调色 - 琥珀色
    private static final TextColor INFO_COLOR = TextColor.fromHexString("#9EE6CF"); // 信息色 - 薄荷绿
    private static final TextColor SUCCESS_COLOR = TextColor.fromHexString("#9EE6A0"); // 成功色 - 亮绿色
    private static final TextColor WARNING_COLOR = TextColor.fromHexString("#FFD166"); // 警告色 - 琥珀色
    private static final TextColor ERROR_COLOR = TextColor.fromHexString("#FF6B6B"); // 错误色 - 珊瑚红
    private static final TextColor HIGHLIGHT_COLOR = TextColor.fromHexString("#FFB347"); // 高亮色 - 橙黄色
    private static final TextColor NEUTRAL_COLOR = TextColor.fromHexString("#D3D3D3"); // 中性色 - 浅灰色

    private final EMCShop plugin;
    private final EMCManager emcManager;
    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();
    private static final int PAGE_SIZE = 36; // 4行x9列
    private static final int[] TOP_BORDER_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] BOTTOM_BORDER_SLOTS = {45, 46, 47, 48, 49, 50, 51, 52, 53};
    private static final int INFO_SLOT = 4;
    private static final int PREV_PAGE_SLOT = 46;
    private static final int NEXT_PAGE_SLOT = 52;
    private static final int CLOSE_BUTTON_SLOT = 49;
    private static final int[] CONTENT_SLOTS = new int[PAGE_SIZE]; // 9*4=36

    // 用于格式化价格的工具
    private final double deconstructionFactor;
    private final DecimalFormat priceFormat = new DecimalFormat("#,##0.00");
    private final NumberFormat percentageFormat = DecimalFormat.getPercentInstance();

    static {
        // 计算内容槽位 (9-44)
        for (int i = 0; i < PAGE_SIZE; i++) {
            CONTENT_SLOTS[i] = i + 9;
        }
    }

    public PreviewMenu(EMCShop plugin) {
        this.plugin = plugin;
        this.emcManager = plugin.getEmcManager();

        // 从配置读取重构损耗率，默认为1.5%
        this.deconstructionFactor = plugin.getConfig().getDouble("purchase.reconstruction-loss", 0.015);

        // 配置百分比格式
        percentageFormat.setMaximumFractionDigits(1);
        percentageFormat.setMinimumFractionDigits(1);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // 打开预览菜单
    public void openPreviewMenu(Player player) {
        UUID playerId = player.getUniqueId();

        // 检查玩家数据是否已加载
        if (emcManager.getPlayerData(playerId) == null) {
            player.sendMessage(Component.text("正在加载您的数据，请稍候...", WARNING_COLOR));
            // 异步加载玩家数据
            emcManager.onPlayerLogin(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> openPreviewMenu(player), 20L); // 1秒后重试
            return;
        }

        playerPages.putIfAbsent(playerId, 0);
        player.openInventory(createPage(player, 0));
        // 播放音效
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
    }

    // 创建菜单页面
    private Inventory createPage(Player player, int page) {
        UUID playerId = player.getUniqueId();

        // 获取已排序的物品列表（按照配置文件顺序）
        List<String> allItems = new ArrayList<>(emcManager.getEmcValues().keySet());
        int totalPages = (int) Math.ceil((double) allItems.size() / PAGE_SIZE);

        // 确保页码在有效范围内
        page = Math.max(0, Math.min(page, totalPages - 1));
        playerPages.put(playerId, page);

        // 创建渐变色标题
        Component title = Component.text()
                .append(Component.text("物", TextColor.fromHexString("#9B5DE5")))
                .append(Component.text("品", TextColor.fromHexString("#8A5DE5")))
                .append(Component.text("预", TextColor.fromHexString("#7A5DE5")))
                .append(Component.text("览", TextColor.fromHexString("#6A5DE5")))
                .append(Component.text(" | 第 " + (page + 1) + "/" + totalPages + " 页", INFO_COLOR))
                .build();

        // 创建库存
        Inventory inv = Bukkit.createInventory(this, 54, title);

        // 设置顶部边框
        for (int slot : TOP_BORDER_SLOTS) {
            inv.setItem(slot, createBorderItem(Material.PURPLE_STAINED_GLASS_PANE));
        }

        // 设置信息项
        inv.setItem(INFO_SLOT, createInfoItem(player, allItems.size()));

        // 设置底部边框
        for (int slot : BOTTOM_BORDER_SLOTS) {
            inv.setItem(slot, createBorderItem(Material.PURPLE_STAINED_GLASS_PANE));
        }

        // 设置翻页按钮
        if (page > 0) {
            inv.setItem(PREV_PAGE_SLOT, createNavigationItem(Material.PAPER,
                    Component.text("« 上一页", ACCENT_COLOR)));
        } else {
            inv.setItem(PREV_PAGE_SLOT, createBorderItem(Material.PURPLE_STAINED_GLASS_PANE));
        }

        if (page < totalPages - 1) {
            inv.setItem(NEXT_PAGE_SLOT, createNavigationItem(Material.PAPER,
                    Component.text("下一页 »", ACCENT_COLOR)));
        } else {
            inv.setItem(NEXT_PAGE_SLOT, createBorderItem(Material.PURPLE_STAINED_GLASS_PANE));
        }

        // 设置关闭按钮
        inv.setItem(CLOSE_BUTTON_SLOT, createCloseButton());

        // 添加所有物品
        int startIdx = page * PAGE_SIZE;
        int endIdx = Math.min(startIdx + PAGE_SIZE, allItems.size());

        for (int i = startIdx; i < endIdx; i++) {
            String itemId = allItems.get(i);
            double baseValue = emcManager.getItemValue(itemId);
            boolean unlocked = emcManager.isItemUnlocked(player, itemId);
            int slot = CONTENT_SLOTS[i - startIdx];
            inv.setItem(slot, createPreviewItem(itemId, baseValue, unlocked));
        }

        return inv;
    }

    // 创建边框物品
    private ItemStack createBorderItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    // 创建信息物品
    private ItemStack createInfoItem(Player player, int totalItems) {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("物品预览信息", PRIMARY_COLOR)
                .decoration(TextDecoration.ITALIC, false));

        // 格式化转换系数为百分比
        String lossPercentage = percentageFormat.format(deconstructionFactor);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("已解锁的物品会显示附魔光效", ACCENT_COLOR));
        lore.add(Component.text("转换价值: 出售时获得的灵叶值", SECONDARY_COLOR));
        lore.add(Component.text("重构损耗: ", NEUTRAL_COLOR)
                .append(Component.text(lossPercentage, HIGHLIGHT_COLOR)));
        lore.add(Component.text("总计物品: ", NEUTRAL_COLOR)
                .append(Component.text(totalItems + "个", HIGHLIGHT_COLOR)));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // 创建导航按钮（翻页）
    private ItemStack createNavigationItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    // 创建关闭按钮
    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✖ 关闭菜单", ERROR_COLOR)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    // 创建预览物品
    private ItemStack createPreviewItem(String itemId, double baseValue, boolean unlocked) {
        Material material = Material.matchMaterial(itemId);
        if (material == null || material == Material.AIR) {
            material = Material.BARRIER;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // 获取物品的翻译键
            String translationKey = material.translationKey();

            // 创建翻译组件并设置颜色（根据解锁状态）
            Component translatedName = Component.translatable(translationKey)
                    .color(unlocked ? SUCCESS_COLOR : ERROR_COLOR)
                    .decoration(TextDecoration.ITALIC, false);

            // 设置显示名称
            meta.displayName(translatedName);

            // 计算转换价值（确保为浮点数计算）
            double deconstructionValue = baseValue * (1 + deconstructionFactor);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text()
                    .append(Component.text("转换价值: ", NEUTRAL_COLOR))
                    .append(Component.text(priceFormat.format(baseValue) + " 灵叶", HIGHLIGHT_COLOR))
                    .build());

            lore.add(Component.text()
                    .append(Component.text("重构价格: ", NEUTRAL_COLOR))
                    .append(Component.text(priceFormat.format(deconstructionValue) + " 灵叶", HIGHLIGHT_COLOR))
                    .build());

            lore.add(Component.text(""));

            if (unlocked) {
                lore.add(Component.text("已解锁", SUCCESS_COLOR));
            } else {
                lore.add(Component.text("未解锁", ERROR_COLOR));
                lore.add(Component.text("通过转换来解锁物品", INFO_COLOR));
            }

            meta.lore(lore);

            // 为已解锁物品添加发光效果
            if (unlocked) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    // 处理菜单点击事件
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PreviewMenu)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        int slot = event.getRawSlot();

        // 默认取消所有事件（防止玩家移动菜单物品）
        event.setCancelled(true);

        int currentPage = playerPages.getOrDefault(playerId, 0);

        // 点击关闭按钮
        if (slot == CLOSE_BUTTON_SLOT) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.0f);
            return;
        }

        // 点击上一页
        if (slot == PREV_PAGE_SLOT && currentPage > 0) {
            player.openInventory(createPage(player, currentPage - 1));
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 0.85f);
            return;
        }

        // 点击下一页
        if (slot == NEXT_PAGE_SLOT) {
            player.openInventory(createPage(player, currentPage + 1));
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 0.9f);
        }
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}