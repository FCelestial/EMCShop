package top.MiragEdge.emc.Data;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.util.HashSet;

public class PlayerData {
    private final UUID playerId;
    private final Set<String> unlockedItems;

    public PlayerData(UUID playerId) {
        this.playerId = playerId;
        // 使用线程安全的并发集合
        this.unlockedItems = ConcurrentHashMap.newKeySet();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * 获取解锁物品集合（返回不可修改的副本）
     */
    public Set<String> getUnlockedItems() {
        return Collections.unmodifiableSet(new HashSet<>(unlockedItems));
    }

    public void unlockItem(String itemId) {
        unlockedItems.add(itemId);
    }

    public boolean isItemUnlocked(String itemId) {
        return unlockedItems.contains(itemId);
    }

    /**
     * 批量设置解锁物品（完整替换）
     * @param unlockedItems 新的解锁物品集合
     */
    public void setUnlockedItems(Set<String> unlockedItems) {
        this.unlockedItems.clear();
        this.unlockedItems.addAll(unlockedItems);
    }

}