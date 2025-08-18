package top.MiragEdge.emc.Gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import top.MiragEdge.emc.EMCShop;
import top.MiragEdge.emc.Manager.EMCManager;
import top.MiragEdge.emc.Utils.LocalizationUtil;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PurchaseMenu implements Listener, InventoryHolder {

    // 定义渐变色方案
    private static final TextColor PRIMARY_COLOR = TextColor.fromHexString("#39C5BB"); // 主色调 - 蓝绿色
    private static final TextColor SECONDARY_COLOR = TextColor.fromHexString("#FFD166"); // 辅色调 - 琥珀色
    private static final TextColor ACCENT_COLOR = TextColor.fromHexString("#FF6B6B"); // 强调色 - 珊瑚红
    private static final TextColor INFO_COLOR = TextColor.fromHexString("#A9DEF9"); // 信息色 - 淡蓝色
    private static final TextColor SUCCESS_COLOR = TextColor.fromHexString("#9EE6A0"); // 成功色 - 薄荷绿
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
    private static final int PLAYER_HEAD_SLOT = 4;
    private static final int PREV_PAGE_SLOT = 46;
    private static final int NEXT_PAGE_SLOT = 52;
    private static final int CLOSE_BUTTON_SLOT = 49;
    private static final int[] CONTENT_SLOTS = new int[PAGE_SIZE]; // 9*4=36

    // 重构损耗率（从配置读取）
    private final double reconstructionLoss;

    // 用于格式化价格的工具
    private final DecimalFormat priceFormat = new DecimalFormat("#,##0.00");

    // 音效配置常量（1.21.4专属优化）
    private static final float UI_SOUND_VOLUME = 0.6f;       // UI操作音稍低避免刺耳
    private static final float SUCCESS_SOUND_VOLUME = 0.9f;  // 成功音清晰但不突兀
    private static final float ERROR_SOUND_VOLUME = 0.7f;    // 错误音柔和提示
    private static final float BASE_PITCH = 1.0f;
    private static final float HIGH_PITCH = 1.15f;           // 稍高音调区分正向操作
    private static final float LOW_PITCH = 0.85f;            // 稍低音调区分反向操作
    private static final float SOFT_PITCH = 0.9f;            // 柔和音调用于次要提示

    static {
        // 计算内容槽位 (9-44)
        for (int i = 0; i < PAGE_SIZE; i++) {
            CONTENT_SLOTS[i] = i + 9;
        }
    }

    public PurchaseMenu(EMCShop plugin) {
        this.plugin = plugin;
        this.emcManager = plugin.getEmcManager();
        this.reconstructionLoss = plugin.getConfig().getDouble("purchase.reconstruction-loss", 0.015);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // 打开购买菜单
    public void openPurchaseMenu(Player player) {
        UUID playerId = player.getUniqueId();

        if (emcManager.getPlayerData(playerId) == null) {
            player.sendMessage(Component.text("正在加载您的数据，请稍候...", WARNING_COLOR));
            emcManager.onPlayerLogin(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> openPurchaseMenu(player), 20L);
            return;
        }

        playerPages.putIfAbsent(playerId, 0);
        player.openInventory(createPage(player, 0));
        // 播放音效
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN, 1.0f, 1.0f);
    }

    // 创建菜单页面
    private Inventory createPage(Player player, int page) {
        UUID playerId = player.getUniqueId();
        List<String> allItems = new ArrayList<>(emcManager.getEmcValues().keySet());
        List<String> unlockedItems = new ArrayList<>();

        for (String itemId : allItems) {
            if (emcManager.isItemUnlocked(player, itemId)) {
                unlockedItems.add(itemId);
            }
        }

        int totalPages = (int) Math.ceil((double) unlockedItems.size() / PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));
        playerPages.put(playerId, page);

        // 使用渐变色标题
        Component title = Component.text()
                .append(Component.text("等", PRIMARY_COLOR))
                .append(Component.text("价", TextColor.fromHexString("#36B9C5")))
                .append(Component.text("交", TextColor.fromHexString("#32ADC0")))
                .append(Component.text("换", TextColor.fromHexString("#2EA2BA")))
                .append(Component.text(" 商店", TextColor.fromHexString("#2A96B5")))
                .append(Component.text(" | 第 " + (page + 1) + "/" + totalPages + " 页", INFO_COLOR))
                .build();

        Inventory inv = Bukkit.createInventory(this, 54, title);

        // 设置边框和按钮
        for (int slot : TOP_BORDER_SLOTS) {
            inv.setItem(slot, createBorderItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        }
        inv.setItem(PLAYER_HEAD_SLOT, createPlayerInfoItem(player, unlockedItems.size(), allItems.size()));
        for (int slot : BOTTOM_BORDER_SLOTS) {
            inv.setItem(slot, createBorderItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        }

        if (page > 0) {
            inv.setItem(PREV_PAGE_SLOT, createNavigationItem(Material.PAPER,
                    Component.text("« 上一页", SECONDARY_COLOR)));
        } else {
            inv.setItem(PREV_PAGE_SLOT, createBorderItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        }

        if (page < totalPages - 1) {
            inv.setItem(NEXT_PAGE_SLOT, createNavigationItem(Material.PAPER,
                    Component.text("下一页 »", SECONDARY_COLOR)));
        } else {
            inv.setItem(NEXT_PAGE_SLOT, createBorderItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        }

        inv.setItem(CLOSE_BUTTON_SLOT, createCloseButton());

        // 添加物品
        int startIdx = page * PAGE_SIZE;
        int endIdx = Math.min(startIdx + PAGE_SIZE, unlockedItems.size());
        for (int i = startIdx; i < endIdx; i++) {
            String itemId = unlockedItems.get(i);
            double baseValue = emcManager.getItemValue(itemId);
            int slot = CONTENT_SLOTS[i - startIdx];
            inv.setItem(slot, createShopItem(itemId, baseValue));
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

    // 创建玩家信息物品
    private ItemStack createPlayerInfoItem(Player player, int unlockedCount, int totalItems) {
        ItemStack item;
        try {
            item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            meta.setOwningPlayer(player);
            item.setItemMeta(meta);
        } catch (Exception e) {
            item = new ItemStack(Material.SUNFLOWER);
        }

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(player.getName(), HIGHLIGHT_COLOR)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("已解锁: ", NEUTRAL_COLOR)
                .append(Component.text(unlockedCount + "/" + totalItems, SECONDARY_COLOR)));

        double balance = plugin.getEconomy().getBalance(player);
        lore.add(Component.text("余额: ", NEUTRAL_COLOR)
                .append(Component.text(priceFormat.format(balance) + " 灵叶", SUCCESS_COLOR)));

        String lossPercentage = String.format("%.2f", reconstructionLoss * 100);
        lore.add(Component.text("重构损耗: ", NEUTRAL_COLOR)
                .append(Component.text(lossPercentage + "%", ERROR_COLOR)));

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

    // 创建商店物品（已解锁）
    private ItemStack createShopItem(String itemId, double baseValue) {
        Material material = Material.matchMaterial(itemId);
        if (material == null || material == Material.AIR) {
            material = Material.BARRIER;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String translationKey = material.translationKey();
            Component translatedName = Component.translatable(translationKey)
                    .color(PRIMARY_COLOR)
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(translatedName);

            double actualPrice = baseValue * (1 + reconstructionLoss);
            String formattedActualPrice = priceFormat.format(actualPrice);
            List<Component> lore = new ArrayList<>();

            // 使用渐变色显示价格信息
            lore.add(Component.text()
                    .append(Component.text("重构价格: ", NEUTRAL_COLOR))
                    .append(Component.text(formattedActualPrice + " 灵叶", HIGHLIGHT_COLOR))
                    .build());

            lore.add(Component.text()
                    .append(Component.text("左键", SECONDARY_COLOR))
                    .append(Component.text("点击购买一个", INFO_COLOR))
                    .build());

            lore.add(Component.text()
                    .append(Component.text("按Q", SECONDARY_COLOR))
                    .append(Component.text("扔出购买一组", INFO_COLOR))
                    .build());

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // 处理菜单点击事件
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PurchaseMenu) || !(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        int slot = event.getRawSlot();
        event.setCancelled(true);

        int currentPage = playerPages.getOrDefault(playerId, 0);

        // 点击关闭按钮
        if (slot == CLOSE_BUTTON_SLOT) {
            player.closeInventory();
            // 关闭音效
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, UI_SOUND_VOLUME, LOW_PITCH);
            return;
        }

        // 点击上一页
        if (slot == PREV_PAGE_SLOT && currentPage > 0) {
            player.openInventory(createPage(player, currentPage - 1));
            // 翻页音效：轻脆的纸张翻动声
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, UI_SOUND_VOLUME, LOW_PITCH);
            return;
        }

        // 点击下一页
        if (slot == NEXT_PAGE_SLOT) {
            List<String> unlockedItems = getUnlockedItems(player);
            int totalPages = (int) Math.ceil((double) unlockedItems.size() / PAGE_SIZE);
            if (currentPage < totalPages - 1) {
                player.openInventory(createPage(player, currentPage + 1));
                // 翻页音效：区分上下页的音调
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, UI_SOUND_VOLUME, HIGH_PITCH);
            } else {
                // 无效操作提示：柔和的反馈音
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, ERROR_SOUND_VOLUME, SOFT_PITCH);
            }
            return;
        }

        // 点击内容槽位（已解锁物品）
        if (isContentSlot(slot)) {
            int contentIndex = getContentIndex(slot, currentPage);
            List<String> unlockedItems = getUnlockedItems(player);

            if (contentIndex >= 0 && contentIndex < unlockedItems.size()) {
                String itemId = unlockedItems.get(contentIndex);

                if (event.getClick().isLeftClick()) {
                    purchaseItem(player, itemId, 1);
                } else if (event.getClick() == ClickType.DROP) {
                    Material material = Material.matchMaterial(itemId);
                    if (material != null) {
                        int amount = new ItemStack(material).getMaxStackSize();
                        purchaseItem(player, itemId, amount);
                    }
                }
            } else {
                // 空槽位点击：轻微无效提示
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, ERROR_SOUND_VOLUME, SOFT_PITCH);
            }
        } else if (isBorderSlot(slot)) {
            // 边框点击：极低音量的反馈（几乎不打扰）
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.2f, SOFT_PITCH);
        }
    }

    // 检查是否是边框槽位
    private boolean isBorderSlot(int slot) {
        for (int borderSlot : TOP_BORDER_SLOTS) if (slot == borderSlot) return true;
        for (int borderSlot : BOTTOM_BORDER_SLOTS) if (slot == borderSlot) return true;
        return false;
    }

    // 检查是否是内容槽位
    private boolean isContentSlot(int slot) {
        for (int contentSlot : CONTENT_SLOTS) if (slot == contentSlot) return true;
        return false;
    }

    // 获取内容索引
    private int getContentIndex(int slot, int currentPage) {
        int startContentSlot = CONTENT_SLOTS[0];
        int indexInPage = slot - startContentSlot;
        return currentPage * PAGE_SIZE + indexInPage;
    }

    // 获取玩家已解锁物品列表
    private List<String> getUnlockedItems(Player player) {
        // 获取玩家所有解锁物品（包括配置中已删除的）
        Set<String> playerUnlocks = emcManager.getPlayerUnlockedItems(player.getUniqueId());

        // 获取当前配置中所有有效物品ID
        Set<String> validItems = emcManager.getEmcValues().keySet();

        // 双重验证：只保留同时存在于解锁列表和当前配置的物品
        List<String> unlockedItems = new ArrayList<>();
        for (String itemId : playerUnlocks) {
            if (validItems.contains(itemId)) {
                unlockedItems.add(itemId);
            }
        }
        return unlockedItems;
    }

    // 购买物品（应用重构损耗）
    private void purchaseItem(Player player, String itemId, int amount) {
        double baseValue = emcManager.getItemValue(itemId);
        double actualPricePerItem = baseValue * (1 + reconstructionLoss);
        double totalPrice = actualPricePerItem * amount;
        double balance = plugin.getEconomy().getBalance(player);

        // 余额不足
        if (balance < totalPrice) {
            String formattedTotalPrice = priceFormat.format(totalPrice);
            player.sendMessage(Component.text()
                    .append(Component.text("余额不足! 需要 ", ERROR_COLOR))
                    .append(Component.text(formattedTotalPrice + " 灵叶", HIGHLIGHT_COLOR))
                    .build());
            // 错误提示：柔和的否定音（不刺耳）
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, ERROR_SOUND_VOLUME, BASE_PITCH);
            return;
        }

        // 物品无效检查
        Material material = Material.matchMaterial(itemId);
        if (material == null || material == Material.AIR) {
            player.sendMessage(Component.text("无效的物品ID: " + itemId, ERROR_COLOR));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, ERROR_SOUND_VOLUME, LOW_PITCH);
            return;
        }

        // 背包空间检查
        ItemStack itemStack = new ItemStack(material, amount);
        Map<Integer, ItemStack> leftOver = player.getInventory().addItem(itemStack);
        if (!leftOver.isEmpty()) {
            player.sendMessage(Component.text("背包空间不足!", ERROR_COLOR));
            // 空间不足：轻量的提示音
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, ERROR_SOUND_VOLUME, SOFT_PITCH);
            return;
        }

        // 购买成功流程
        plugin.getEconomy().withdrawPlayer(player, totalPrice);
        Component itemName = LocalizationUtil.getLocalizedName(material);
        String formattedTotalPrice = priceFormat.format(totalPrice);
        String formattedBaseTotal = priceFormat.format(baseValue * amount);
        String formattedLoss = priceFormat.format(totalPrice - (baseValue * amount));

        Component message = Component.text()
                .append(Component.text("成功购买 ", SUCCESS_COLOR))
                .append(itemName.colorIfAbsent(PRIMARY_COLOR))
                .append(Component.text(" × " + amount, HIGHLIGHT_COLOR))
                .append(Component.text(" 花费 ", SUCCESS_COLOR))
                .append(Component.text(formattedTotalPrice + " 灵叶", HIGHLIGHT_COLOR))
                .append(Component.newline())
                .append(Component.text("(基础价值: " + formattedBaseTotal + " + 重构损耗: " +
                        formattedLoss + ")", NEUTRAL_COLOR))
                .build();

        player.sendMessage(message);
        // 购买成功：1.21新增的收集音效（清脆悦耳）
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SUCCESS_SOUND_VOLUME, HIGH_PITCH);

        // 刷新菜单
        player.openInventory(createPage(player, playerPages.get(player.getUniqueId())));
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}