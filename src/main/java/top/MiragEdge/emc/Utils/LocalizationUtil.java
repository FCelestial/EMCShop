package top.MiragEdge.emc.Utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class LocalizationUtil {

    public static Component getLocalizedName(Material material) {
        // 直接使用 Minecraft 的翻译键系统
        return Component.translatable(material.translationKey())
                .decoration(TextDecoration.ITALIC, false);
    }

    public static Component getLocalizedName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return Component.text("空气");
        }

        // 如果有自定义显示名称，优先使用
        if (itemStack.hasItemMeta()) {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta.hasDisplayName()) {
                return meta.displayName();
            }
        }

        // 否则使用材质名称的翻译键
        return getLocalizedName(itemStack.getType());
    }
}