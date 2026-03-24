package top.MiragEdge.emc.Gui.shared;

import net.kyori.adventure.text.format.TextColor;

/**
 * 所有菜单共享的常量定义
 * 统一管理颜色、音效参数、布局配置等
 */
public final class SharedConstants {

    private SharedConstants() {
        // 工具类禁止实例化
    }

    // ==================== 颜色定义 ====================

    // 主色调
    public static final TextColor PRIMARY_COLOR = TextColor.fromHexString("#39C5BB");
    public static final TextColor PRIMARY_COLOR_PURPLE = TextColor.fromHexString("#A974FF");

    // 辅色调
    public static final TextColor SECONDARY_COLOR = TextColor.fromHexString("#FFD166");
    public static final TextColor SECONDARY_COLOR_CORAL = TextColor.fromHexString("#FF6B6B");

    // 强调色
    public static final TextColor ACCENT_COLOR = TextColor.fromHexString("#FFD166");
    public static final TextColor ACCENT_COLOR_MINT = TextColor.fromHexString("#9EE6CF");

    // 信息色
    public static final TextColor INFO_COLOR = TextColor.fromHexString("#A9DEF9");
    public static final TextColor INFO_COLOR_LIGHT = TextColor.fromHexString("#9EE6CF");

    // 成功色
    public static final TextColor SUCCESS_COLOR = TextColor.fromHexString("#9EE6A0");
    public static final TextColor SUCCESS_COLOR_BRIGHT = TextColor.fromHexString("#9EE6A0");

    // 警告色
    public static final TextColor WARNING_COLOR = TextColor.fromHexString("#FFD166");

    // 错误色
    public static final TextColor ERROR_COLOR = TextColor.fromHexString("#FF6B6B");

    // 高亮色
    public static final TextColor HIGHLIGHT_COLOR = TextColor.fromHexString("#FFB347");

    // 中性色
    public static final TextColor NEUTRAL_COLOR = TextColor.fromHexString("#D3D3D3");

    // ==================== 音效常量 ====================

    // 通用UI音效音量
    public static final float UI_SOUND_VOLUME = 0.6f;
    public static final float SUCCESS_SOUND_VOLUME = 0.9f;
    public static final float ERROR_SOUND_VOLUME = 0.7f;

    // 音调常量
    public static final float BASE_PITCH = 1.0f;
    public static final float HIGH_PITCH = 1.15f;
    public static final float LOW_PITCH = 0.85f;
    public static final float SOFT_PITCH = 0.9f;

    // ==================== 布局常量 ====================

    /** 每页内容物品数量 (4行x9列) */
    public static final int PAGE_SIZE = 36;

    /** 顶部边框槽位 (第一行) */
    public static final int[] TOP_BORDER_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    /** 底部边框槽位 (第六行) */
    public static final int[] BOTTOM_BORDER_SLOTS = {45, 46, 47, 48, 49, 50, 51, 52, 53};

    /** 内容槽位数组 (9-44，共36个) */
    public static final int[] CONTENT_SLOTS = new int[PAGE_SIZE];

    /** 玩家信息/信息物品槽位 */
    public static final int INFO_SLOT = 4;

    /** 上一页按钮槽位 */
    public static final int PREV_PAGE_SLOT = 46;

    /** 下一页按钮槽位 */
    public static final int NEXT_PAGE_SLOT = 52;

    /** 关闭按钮槽位 */
    public static final int CLOSE_BUTTON_SLOT = 49;

    // 静态初始化内容槽位
    static {
        for (int i = 0; i < PAGE_SIZE; i++) {
            CONTENT_SLOTS[i] = i + 9;
        }
    }

    // ==================== ConvertMenu 专用布局 ====================

    /** ConvertMenu 输入区域槽位 (0-44，共45个) */
    public static final int[] INPUT_SLOTS = new int[45];
    static {
        for (int i = 0; i < 45; i++) {
            INPUT_SLOTS[i] = i;
        }
    }

    /** ConvertMenu 背景装饰槽位 */
    public static final int[] BACKGROUND_SLOTS = {45, 46, 47, 48, 50, 51, 52, 53};

    /** ConvertMenu 转换按钮槽位 */
    public static final int CONVERT_SLOT = 49;
}
