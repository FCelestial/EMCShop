package top.MiragEdge.emc.Gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import top.MiragEdge.emc.EMCShop;
import top.MiragEdge.emc.Gui.shared.SharedConstants;
import top.MiragEdge.emc.Gui.shared.SoundManager;
import top.MiragEdge.emc.Manager.EMCManager;
import top.MiragEdge.emc.Utils.LocalizationUtil;
import top.MiragEdge.emc.Utils.MessageUtil;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 购买菜单
 * 玩家可以在这里购买已解锁的物品
 */
public class PurchaseMenu extends BaseMenu {

    /** 玩家当前页面缓存 */
    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();

    /** 重构损耗率 */
    private final double reconstructionLoss;

    /** 货币名称 */
    private final String currencyName;

    /** 格式化价格的工具 */
    private final DecimalFormat priceFormat = new DecimalFormat("#,##0.00");

    public PurchaseMenu(EMCShop plugin) {
        super(plugin, plugin.getEmcManager(), "purchase-menu.title");
        this.reconstructionLoss = plugin.getConfig().getDouble("purchase.reconstruction-loss", 0.015);
        this.currencyName = MessageUtil.getInstance().getCurrencyName();
    }

    @Override
    protected boolean isPlayerDataLoaded(UUID playerId) {
        return emcManager.getPlayerData(playerId) != null;
    }

    @Override
    protected List<String> getMenuItems(Player player) {
        Set<String> playerUnlocks = emcManager.getPlayerUnlockedItems(player.getUniqueId());
        Set<String> validItems = emcManager.getEmcValues().keySet();

        List<String> unlockedItems = new ArrayList<>();
        for (String itemId : playerUnlocks) {
            if (validItems.contains(itemId)) {
                unlockedItems.add(itemId);
            }
        }
        return unlockedItems;
    }

    @Override
    protected double getItemValue(String itemId) {
        return emcManager.getItemValue(itemId);
    }

    @Override
    protected Material getBorderMaterial() {
        return Material.LIGHT_BLUE_STAINED_GLASS_PANE;
    }

    @Override
    protected ItemStack createContentItem(Player player, String itemId, double value) {
        return createShopItem(itemId, value);
    }

    @Override
    protected void handleContentClick(Player player, String itemId, int amount) {
        purchaseItem(player, itemId, amount);
    }

    @Override
    protected int getCurrentPage(UUID playerId) {
        return playerPages.getOrDefault(playerId, 0);
    }

    @Override
    protected void getOrInitPage(UUID playerId) {
        playerPages.putIfAbsent(playerId, 0);
    }

    @Override
    protected void setCurrentPage(UUID playerId, int page) {
        playerPages.put(playerId, page);
    }

    // ==================== PurchaseMenu 特有方法 ====================

    @Override
    protected ItemStack createInfoItem(Player player, int totalItems) {
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
        meta.displayName(Component.text(player.getName(), SharedConstants.HIGHLIGHT_COLOR)
                .decoration(TextDecoration.ITALIC, false));

        int unlockedCount = getMenuItems(player).size();
        // 获取EMC物品总数（不是玩家已解锁的数量）
        int emcItemsCount = emcManager.getEmcValues().size();

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("已解锁: ", SharedConstants.NEUTRAL_COLOR)
                .append(Component.text(unlockedCount + "/" + emcItemsCount, SharedConstants.SECONDARY_COLOR)));

        double balance = emcManager.getBalance(player);
        lore.add(Component.text("余额: ", SharedConstants.NEUTRAL_COLOR)
                .append(Component.text(priceFormat.format(balance) + " " + currencyName, SharedConstants.SUCCESS_COLOR)));

        String lossPercentage = String.format("%.2f", reconstructionLoss * 100);
        lore.add(Component.text("重构损耗: ", SharedConstants.NEUTRAL_COLOR)
                .append(Component.text(lossPercentage + "%", SharedConstants.ERROR_COLOR)));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建商店物品（已解锁物品）
     */
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
                    .color(SharedConstants.PRIMARY_COLOR)
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(translatedName);

            double actualPrice = baseValue * (1 + reconstructionLoss);
            String formattedActualPrice = priceFormat.format(actualPrice);

            List<Component> lore = new ArrayList<>();

            lore.add(Component.text()
                    .append(Component.text("重构价格: ", SharedConstants.NEUTRAL_COLOR))
                    .append(Component.text(formattedActualPrice + " " + currencyName, SharedConstants.HIGHLIGHT_COLOR))
                    .build());

            lore.add(Component.text()
                    .append(Component.text("左键", SharedConstants.SECONDARY_COLOR))
                    .append(Component.text("点击购买一个", SharedConstants.INFO_COLOR))
                    .build());

            lore.add(Component.text()
                    .append(Component.text("Q键", SharedConstants.SECONDARY_COLOR))
                    .append(Component.text("购买一组", SharedConstants.INFO_COLOR))
                    .build());

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 购买物品
     */
    private void purchaseItem(Player player, String itemId, int amount) {
        double baseValue = emcManager.getItemValue(itemId);
        double actualPricePerItem = baseValue * (1 + reconstructionLoss);
        double totalPrice = actualPricePerItem * amount;
        double balance = emcManager.getBalance(player);

        if (balance < totalPrice) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("price", priceFormat.format(totalPrice));
            placeholders.put("currency", currencyName);
            player.sendMessage(MessageUtil.getInstance().getMessage("purchase-menu.insufficient-funds", placeholders));
            SoundManager.playError(player);
            return;
        }

        Material material = Material.matchMaterial(itemId);
        if (material == null || material == Material.AIR) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("item", itemId);
            player.sendMessage(MessageUtil.getInstance().getMessage("purchase-menu.invalid-item", placeholders));
            SoundManager.playItemBreak(player);
            return;
        }

        ItemStack itemStack = new ItemStack(material, amount);
        Map<Integer, ItemStack> leftOver = player.getInventory().addItem(itemStack);
        if (!leftOver.isEmpty()) {
            player.sendMessage(MessageUtil.getInstance().getMessage("purchase-menu.inventory-full"));
            SoundManager.playInventoryFull(player);
            return;
        }

        emcManager.withdraw(player, totalPrice);
        Component itemName = LocalizationUtil.getLocalizedName(material);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("item", "");
        placeholders.put("amount", String.valueOf(amount));
        placeholders.put("price", priceFormat.format(totalPrice));
        placeholders.put("base_value", priceFormat.format(baseValue * amount));
        placeholders.put("loss", priceFormat.format(totalPrice - (baseValue * amount)));
        placeholders.put("currency", currencyName);

        Component message = MessageUtil.getInstance().getMessage("purchase-menu.purchase-success", placeholders);
        message = replaceItemNamePlaceholder(message, itemName);

        player.sendMessage(message);
        SoundManager.playSuccess(player);

        // 刷新菜单
        player.openInventory(createPage(player, getCurrentPage(player.getUniqueId())));
    }

    /**
     * 替换消息中的物品名称占位符
     */
    private Component replaceItemNamePlaceholder(Component message, Component itemName) {
        return message;
    }
}
