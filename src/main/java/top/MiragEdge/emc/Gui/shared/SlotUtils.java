package top.MiragEdge.emc.Gui.shared;

import java.util.Arrays;

/**
 * 槽位检查工具类
 * 提供统一的槽位验证方法
 */
public final class SlotUtils {

    private SlotUtils() {
        // 工具类禁止实例化
    }

    /**
     * 检查给定槽位是否在顶部边框中
     * @param slot 要检查的槽位
     * @return 是否在顶部边框
     */
    public static boolean isTopBorderSlot(int slot) {
        return Arrays.binarySearch(SharedConstants.TOP_BORDER_SLOTS, slot) >= 0;
    }

    /**
     * 检查给定槽位是否在底部边框中
     * @param slot 要检查的槽位
     * @return 是否在底部边框
     */
    public static boolean isBottomBorderSlot(int slot) {
        return Arrays.binarySearch(SharedConstants.BOTTOM_BORDER_SLOTS, slot) >= 0;
    }

    /**
     * 检查给定槽位是否在任何边框中
     * @param slot 要检查的槽位
     * @return 是否在边框中
     */
    public static boolean isBorderSlot(int slot) {
        return isTopBorderSlot(slot) || isBottomBorderSlot(slot);
    }

    /**
     * 检查给定槽位是否是内容槽位
     * @param slot 要检查的槽位
     * @return 是否在内容区域
     */
    public static boolean isContentSlot(int slot) {
        return Arrays.binarySearch(SharedConstants.CONTENT_SLOTS, slot) >= 0;
    }

    /**
     * 检查给定槽位是否在任何特殊按钮槽位中
     * @param slot 要检查的槽位
     * @return 是否是特殊按钮槽位
     */
    public static boolean isButtonSlot(int slot) {
        return slot == SharedConstants.CLOSE_BUTTON_SLOT
                || slot == SharedConstants.PREV_PAGE_SLOT
                || slot == SharedConstants.NEXT_PAGE_SLOT
                || slot == SharedConstants.INFO_SLOT;
    }

    /**
     * 检查给定槽位是否是ConvertMenu的输入槽位
     * @param slot 要检查的槽位
     * @return 是否在输入区域
     */
    public static boolean isInputSlot(int slot) {
        return Arrays.binarySearch(SharedConstants.INPUT_SLOTS, slot) >= 0;
    }

    /**
     * 检查给定槽位是否是ConvertMenu的背景装饰槽位
     * @param slot 要检查的槽位
     * @return 是否在背景装饰区域
     */
    public static boolean isBackgroundSlot(int slot) {
        return Arrays.binarySearch(SharedConstants.BACKGROUND_SLOTS, slot) >= 0;
    }

    /**
     * 检查给定槽位是否是ConvertMenu的转换按钮槽位
     * @param slot 要检查的槽位
     * @return 是否是转换按钮槽位
     */
    public static boolean isConvertButtonSlot(int slot) {
        return slot == SharedConstants.CONVERT_SLOT;
    }

    /**
     * 验证页码是否在有效范围内
     * @param requestedPage 请求的页码
     * @param totalItems 总物品数量
     * @param pageSize 每页物品数量
     * @return 修正后的有效页码
     */
    public static int validatePage(int requestedPage, int totalItems, int pageSize) {
        if (totalItems <= 0) {
            return 0;
        }
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        int maxPage = Math.max(0, totalPages - 1);
        return Math.max(0, Math.min(requestedPage, maxPage));
    }

    /**
     * 计算内容区域中的相对索引
     * @param slot 槽位号
     * @param page 当前页码
     * @return 在当前页内容中的索引，如果槽位无效则返回-1
     */
    public static int getContentIndex(int slot, int page) {
        if (!isContentSlot(slot)) {
            return -1;
        }
        return slot - SharedConstants.CONTENT_SLOTS[0] + (page * SharedConstants.PAGE_SIZE);
    }

    /**
     * 计算给定页码的起始索引
     * @param page 页码
     * @return 起始索引
     */
    public static int getStartIndex(int page) {
        return page * SharedConstants.PAGE_SIZE;
    }

    /**
     * 计算给定页码的结束索引（不包含）
     * @param page 页码
     * @param totalItems 总物品数量
     * @return 结束索引
     */
    public static int getEndIndex(int page, int totalItems) {
        return Math.min(getStartIndex(page) + SharedConstants.PAGE_SIZE, totalItems);
    }
}
