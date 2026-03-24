package top.MiragEdge.emc.Gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import top.MiragEdge.emc.EMCShop;
import top.MiragEdge.emc.Gui.shared.SharedConstants;
import top.MiragEdge.emc.Gui.shared.SlotUtils;
import top.MiragEdge.emc.Gui.shared.SoundManager;
import top.MiragEdge.emc.Manager.EMCManager;
import top.MiragEdge.emc.Utils.MessageUtil;

import java.text.DecimalFormat;
import java.util.*;

/**
 * 菜单抽象基类
 * 定义所有菜单的公共结构和行为
 */
public abstract class BaseMenu implements Listener, InventoryHolder {

    protected final EMCShop plugin;
    protected final EMCManager emcManager;

    /** 格式化价格的工具 */
    protected final DecimalFormat priceFormat = new DecimalFormat("#,##0.00");

    /** 缓存当前页面物品列表（用于处理点击） */
    protected final Map<UUID, List<String>> playerPageItems = new HashMap<>();

    private final String menuTitleKey;

    protected BaseMenu(EMCShop plugin, EMCManager emcManager, String menuTitleKey) {
        this.plugin = plugin;
        this.emcManager = emcManager;
        this.menuTitleKey = menuTitleKey;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ==================== 公共API ====================

    /**
     * 打开菜单
     * @param player 玩家
     */
    public void open(Player player) {
        UUID playerId = player.getUniqueId();

        if (!isPlayerDataLoaded(playerId)) {
            player.sendMessage(MessageUtil.getInstance().getMessage("general.data-loading"));
            emcManager.onPlayerLogin(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(player), 20L);
            return;
        }

        getOrInitPage(playerId);
        player.openInventory(createPage(player, getCurrentPage(playerId)));
        SoundManager.playOpen(player);
    }

    // ==================== 模板方法（子类实现） ====================

    /**
     * 检查玩家数据是否已加载
     */
    protected abstract boolean isPlayerDataLoaded(UUID playerId);

    /**
     * 获取菜单的物品列表
     */
    protected abstract List<String> getMenuItems(Player player);

    /**
     * 获取物品的EMC值
     */
    protected abstract double getItemValue(String itemId);

    /**
     * 创建内容物品
     */
    protected abstract ItemStack createContentItem(Player player, String itemId, double value);

    /**
     * 处理内容区域点击
     */
    protected abstract void handleContentClick(Player player, String itemId, int amount);

    /**
     * 获取菜单的边框材质
     */
    protected abstract Material getBorderMaterial();

    // ==================== 公共页面创建 ====================

    /**
     * 创建菜单页面
     */
    protected Inventory createPage(Player player, int page) {
        UUID playerId = player.getUniqueId();
        List<String> allItems = getMenuItems(player);

        int validatedPage = SlotUtils.validatePage(page, allItems.size(), SharedConstants.PAGE_SIZE);
        setCurrentPage(playerId, validatedPage);

        Map<String, String> titlePlaceholders = new HashMap<>();
        titlePlaceholders.put("page", String.valueOf(validatedPage + 1));
        titlePlaceholders.put("total", String.valueOf(Math.max(1, (int) Math.ceil((double) allItems.size() / SharedConstants.PAGE_SIZE))));
        Component title = MessageUtil.getInstance().getMessage(menuTitleKey, titlePlaceholders);

        Inventory inv = Bukkit.createInventory(this, 54, title);

        // 设置边框
        fillBorder(inv);

        // 设置信息物品
        inv.setItem(SharedConstants.INFO_SLOT, createInfoItem(player, allItems.size()));

        // 设置翻页按钮
        updateNavigationButtons(inv, validatedPage, allItems.size());

        // 设置关闭按钮
        inv.setItem(SharedConstants.CLOSE_BUTTON_SLOT, createCloseButton());

        // 填充内容
        fillContent(player, inv, validatedPage, allItems);

        return inv;
    }

    /**
     * 填充边框
     */
    protected void fillBorder(Inventory inv) {
        Material borderMat = getBorderMaterial();

        for (int slot : SharedConstants.TOP_BORDER_SLOTS) {
            inv.setItem(slot, createBorderItem(borderMat));
        }

        for (int slot : SharedConstants.BOTTOM_BORDER_SLOTS) {
            inv.setItem(slot, createBorderItem(borderMat));
        }
    }

    /**
     * 更新翻页按钮
     */
    protected void updateNavigationButtons(Inventory inv, int currentPage, int totalItems) {
        int totalPages = (int) Math.ceil((double) totalItems / SharedConstants.PAGE_SIZE);

        if (currentPage > 0) {
            inv.setItem(SharedConstants.PREV_PAGE_SLOT,
                    createNavigationItem(Material.PAPER, Component.text("◄ 上一页", SharedConstants.SECONDARY_COLOR)));
        } else {
            inv.setItem(SharedConstants.PREV_PAGE_SLOT, createBorderItem(getBorderMaterial()));
        }

        if (currentPage < totalPages - 1) {
            inv.setItem(SharedConstants.NEXT_PAGE_SLOT,
                    createNavigationItem(Material.PAPER, Component.text("下一页 ►", SharedConstants.SECONDARY_COLOR)));
        } else {
            inv.setItem(SharedConstants.NEXT_PAGE_SLOT, createBorderItem(getBorderMaterial()));
        }
    }

    /**
     * 填充内容区域
     */
    protected void fillContent(Player player, Inventory inv, int page, List<String> allItems) {
        int startIdx = SlotUtils.getStartIndex(page);
        int endIdx = SlotUtils.getEndIndex(page, allItems.size());

        List<String> currentPageItems = new ArrayList<>();

        for (int i = startIdx; i < endIdx; i++) {
            String itemId = allItems.get(i);
            double value = getItemValue(itemId);
            int slot = SharedConstants.CONTENT_SLOTS[i - startIdx];

            inv.setItem(slot, createContentItem(player, itemId, value));
            currentPageItems.add(itemId);
        }

        playerPageItems.put(player.getUniqueId(), currentPageItems);
    }

    // ==================== 公共物品创建方法 ====================

    /**
     * 创建边框物品
     */
    protected ItemStack createBorderItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建导航按钮
     */
    protected ItemStack createNavigationItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建关闭按钮
     */
    protected ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✖ 关闭菜单", SharedConstants.ERROR_COLOR)
                    .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建信息物品（默认实现，子类可覆盖）
     */
    protected ItemStack createInfoItem(Player player, int totalItems) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("菜单信息", SharedConstants.HIGHLIGHT_COLOR)
                    .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== 事件处理 ====================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BaseMenu menu)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        UUID playerId = player.getUniqueId();

        // 处理关闭按钮
        if (slot == SharedConstants.CLOSE_BUTTON_SLOT) {
            player.closeInventory();
            SoundManager.playClose(player);
            playerPageItems.remove(playerId);
            return;
        }

        // 处理上一页
        if (slot == SharedConstants.PREV_PAGE_SLOT) {
            int currentPage = getCurrentPage(playerId);
            if (currentPage > 0) {
                player.openInventory(createPage(player, currentPage - 1));
                SoundManager.playPageTurnBackward(player);
            } else {
                SoundManager.playUIClick(player);
            }
            return;
        }

        // 处理下一页
        if (slot == SharedConstants.NEXT_PAGE_SLOT) {
            List<String> items = getMenuItems(player);
            int currentPage = getCurrentPage(playerId);
            int totalPages = (int) Math.ceil((double) items.size() / SharedConstants.PAGE_SIZE);

            if (currentPage < totalPages - 1) {
                player.openInventory(createPage(player, currentPage + 1));
                SoundManager.playPageTurnForward(player);
            } else {
                SoundManager.playUIClick(player);
            }
            return;
        }

        // 处理边框点击
        if (SlotUtils.isBorderSlot(slot)) {
            // 边框点击给予轻微反馈
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.2f, SharedConstants.SOFT_PITCH);
            return;
        }

        // 处理内容区域点击
        if (SlotUtils.isContentSlot(slot)) {
            handleContentAreaClick(player, slot, event.getClick().isLeftClick(),
                    event.getClick().isShiftClick());
        }
    }

    /**
     * 处理内容区域点击
     */
    protected void handleContentAreaClick(Player player, int slot, boolean leftClick, boolean shiftClick) {
        UUID playerId = player.getUniqueId();
        List<String> pageItems = playerPageItems.get(playerId);

        if (pageItems == null) {
            // 缓存不存在，重新打开菜单
            player.openInventory(createPage(player, getCurrentPage(playerId)));
            SoundManager.playUIClick(player);
            return;
        }

        int contentIndex = slot - SharedConstants.CONTENT_SLOTS[0];

        if (contentIndex >= 0 && contentIndex < pageItems.size()) {
            String itemId = pageItems.get(contentIndex);

            if (leftClick) {
                handleContentClick(player, itemId, 1);
            } else if (shiftClick) {
                // Shift点击购买一组
                Material material = Material.matchMaterial(itemId);
                if (material != null) {
                    int amount = new ItemStack(material).getMaxStackSize();
                    handleContentClick(player, itemId, amount);
                }
            }
        } else {
            // 空槽位
            SoundManager.playUIClick(player);
        }
    }

    // ==================== 页面状态管理 ====================

    /**
     * 获取当前页码（被子类管理）
     */
    protected abstract int getCurrentPage(UUID playerId);

    /**
     * 初始化页码（如果不存在）
     */
    protected abstract void getOrInitPage(UUID playerId);

    /**
     * 设置当前页码
     */
    protected abstract void setCurrentPage(UUID playerId, int page);

    @Override
    public Inventory getInventory() {
        return null;
    }
}
