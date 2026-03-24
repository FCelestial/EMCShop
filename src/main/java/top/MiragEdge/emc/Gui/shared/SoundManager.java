package top.MiragEdge.emc.Gui.shared;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * 音效管理工具类
 * 提供统一的音效播放方法
 */
public final class SoundManager {

    private SoundManager() {
        // 工具类禁止实例化
    }

    /**
     * 播放UI点击音效
     * @param player 玩家
     */
    public static void playUIClick(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK,
                SharedConstants.UI_SOUND_VOLUME, SharedConstants.BASE_PITCH);
    }

    /**
     * 播放成功音效
     * @param player 玩家
     */
    public static void playSuccess(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP,
                SharedConstants.SUCCESS_SOUND_VOLUME, SharedConstants.HIGH_PITCH);
    }

    /**
     * 播放错误音效
     * @param player 玩家
     */
    public static void playError(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,
                SharedConstants.ERROR_SOUND_VOLUME, SharedConstants.BASE_PITCH);
    }

    /**
     * 播放翻页音效（向前）
     * @param player 玩家
     */
    public static void playPageTurnForward(Player player) {
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN,
                SharedConstants.UI_SOUND_VOLUME, SharedConstants.HIGH_PITCH);
    }

    /**
     * 播放翻页音效（向后）
     * @param player 玩家
     */
    public static void playPageTurnBackward(Player player) {
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN,
                SharedConstants.UI_SOUND_VOLUME, SharedConstants.LOW_PITCH);
    }

    /**
     * 播放关闭菜单音效
     * @param player 玩家
     */
    public static void playClose(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE,
                SharedConstants.UI_SOUND_VOLUME, SharedConstants.LOW_PITCH);
    }

    /**
     * 播放打开菜单音效
     * @param player 玩家
     */
    public static void playOpen(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN,
                SharedConstants.UI_SOUND_VOLUME, SharedConstants.BASE_PITCH);
    }

    /**
     * 播放物品损坏音效
     * @param player 玩家
     */
    public static void playItemBreak(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK,
                SharedConstants.ERROR_SOUND_VOLUME, SharedConstants.LOW_PITCH);
    }

    /**
     * 播放皮革装备音效（通常用于背包空间不足提示）
     * @param player 玩家
     */
    public static void playInventoryFull(Player player) {
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER,
                SharedConstants.ERROR_SOUND_VOLUME, SharedConstants.SOFT_PITCH);
    }

    /**
     * 播放附魔音效（用于解锁物品提示）
     * @param player 玩家
     */
    public static void playEnchant(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE,
                SharedConstants.SUCCESS_SOUND_VOLUME, SharedConstants.BASE_PITCH);
    }

    /**
     * 播放铁门打开音效（用于Ender Chest）
     * @param player 玩家
     */
    public static void playEnderChestOpen(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN,
                SharedConstants.UI_SOUND_VOLUME, SharedConstants.BASE_PITCH);
    }
}
