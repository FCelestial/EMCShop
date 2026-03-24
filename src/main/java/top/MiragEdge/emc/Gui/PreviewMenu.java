package top.MiragEdge.emc.Gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import top.MiragEdge.emc.EMCShop;
import top.MiragEdge.emc.Gui.shared.SharedConstants;
import top.MiragEdge.emc.Gui.shared.SoundManager;
import top.MiragEdge.emc.Manager.EMCManager;
import top.MiragEdge.emc.Utils.MessageUtil;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预览菜单
 * 玩家可以预览所有物品的EMC值和解锁状态
 */
public class PreviewMenu extends BaseMenu {

    /** 玩家当前页面缓存 */
    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();

    /** 重构损耗率 */
    private final double deconstructionFactor;

    /** 格式化价格的工具 */
    private final DecimalFormat priceFormat = new DecimalFormat("#,##0.00");

    /** 格式化百分比 */
    private final NumberFormat percentageFormat;

    /** 货币名称 */
    private final String currencyName;

    public PreviewMenu(EMCShop plugin) {
        super(plugin, plugin.getEmcManager(), "preview-menu.title");
        this.deconstructionFactor = plugin.getConfig().getDouble("purchase.reconstruction-loss", 0.015);
        this.currencyName = MessageUtil.getInstance().getCurrencyName();

        this.percentageFormat = DecimalFormat.getPercentInstance();
        this.percentageFormat.setMaximumFractionDigits(1);
        this.percentageFormat.setMinimumFractionDigits(1);
    }

    @Override
    protected boolean isPlayerDataLoaded(UUID playerId) {
        return emcManager.getPlayerData(playerId) != null;
    }

    @Override
    protected List<String> getMenuItems(Player player) {
        return new ArrayList<>(emcManager.getEmcValues().keySet());
    }

    @Override
    protected double getItemValue(String itemId) {
        return emcManager.getItemValue(itemId);
    }

    @Override
    protected Material getBorderMaterial() {
        return Material.PURPLE_STAINED_GLASS_PANE;
    }

    @Override
    protected ItemStack createContentItem(Player player, String itemId, double value) {
        boolean unlocked = emcManager.isItemUnlocked(player, itemId);
        return createPreviewItem(itemId, value, unlocked);
    }

    @Override
    protected void handleContentClick(Player player, String itemId, int amount) {
        // PreviewMenu 只是预览，不处理购买点击
        SoundManager.playUIClick(player);
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

    // ==================== PreviewMenu 特有方法 ====================

    @Override
    protected ItemStack createInfoItem(Player player, int totalItems) {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("物品预览信息", SharedConstants.PRIMARY_COLOR_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));

            String lossPercentage = percentageFormat.format(deconstructionFactor);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("已解锁的物品会显示附魔光效", SharedConstants.ACCENT_COLOR));
            lore.add(Component.text("转换价值: 出售时获得的" + currencyName + "值", SharedConstants.SECONDARY_COLOR_CORAL));
            lore.add(Component.text("重构损耗: ", SharedConstants.NEUTRAL_COLOR)
                    .append(Component.text(lossPercentage, SharedConstants.HIGHLIGHT_COLOR)));
            lore.add(Component.text("总计物品: ", SharedConstants.NEUTRAL_COLOR)
                    .append(Component.text(totalItems + "个", SharedConstants.HIGHLIGHT_COLOR)));

            double balance = emcManager.getBalance(player);
            lore.add(Component.text("余额: ", SharedConstants.NEUTRAL_COLOR)
                    .append(Component.text(priceFormat.format(balance) + " " + currencyName, SharedConstants.SUCCESS_COLOR)));

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建预览物品显示
     */
    private ItemStack createPreviewItem(String itemId, double baseValue, boolean unlocked) {
        Material material = Material.matchMaterial(itemId);
        if (material == null || material == Material.AIR) {
            material = Material.BARRIER;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String translationKey = material.translationKey();

            Component translatedName = Component.translatable(translationKey)
                    .color(unlocked ? SharedConstants.SUCCESS_COLOR : SharedConstants.ERROR_COLOR)
                    .decoration(TextDecoration.ITALIC, false);

            meta.displayName(translatedName);

            double deconstructionValue = baseValue * (1 + deconstructionFactor);

            List<Component> lore = new ArrayList<>();

            lore.add(Component.text()
                    .append(Component.text("转换价值: ", SharedConstants.NEUTRAL_COLOR))
                    .append(Component.text(priceFormat.format(baseValue) + " " + currencyName, SharedConstants.HIGHLIGHT_COLOR))
                    .build());

            lore.add(Component.text()
                    .append(Component.text("重构价格: ", SharedConstants.NEUTRAL_COLOR))
                    .append(Component.text(priceFormat.format(deconstructionValue) + " " + currencyName, SharedConstants.HIGHLIGHT_COLOR))
                    .build());

            lore.add(Component.text(""));

            if (unlocked) {
                lore.add(MessageUtil.getInstance().getMessage("preview-menu.unlocked"));
            } else {
                lore.add(MessageUtil.getInstance().getMessage("preview-menu.locked"));
                lore.add(MessageUtil.getInstance().getMessage("preview-menu.unlock-hint"));
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
}
