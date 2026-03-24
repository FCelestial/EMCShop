package top.MiragEdge.emc.Data;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.util.HashSet;

public class PlayerData {
    private final UUID playerId;
    private final Set<String> unlockedItems;
    private double emcBalance;

    public PlayerData(UUID playerId) {
        this.playerId = playerId;
        // 使用线程安全的并发集合
        this.unlockedItems = ConcurrentHashMap.newKeySet();
        this.emcBalance = 0.0;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * 获取解锁物品集合（返回不可修改的视图）
     */
    public Set<String> getUnlockedItems() {
        return Collections.unmodifiableSet(unlockedItems);
    }

    public void unlockItem(String itemId) {
        unlockedItems.add(itemId);
    }

    public boolean isItemUnlocked(String itemId) {
        return unlockedItems.contains(itemId);
    }

    /**
     * 批量设置解锁物品
     * 注意：调用者应该传入一个防御性副本，因为本方法会清空并重新填充集合
     * @param unlockedItems 新的解锁物品集合（应该是防御性副本）
     */
    public void setUnlockedItems(Set<String> unlockedItems) {
        this.unlockedItems.clear();
        this.unlockedItems.addAll(unlockedItems);
    }


    /**
     * EMC余额相关方法
     */
    public double getEmcBalance() {
        return emcBalance;
    }

    public void setEmcBalance(double emcBalance) {
        this.emcBalance = emcBalance;
    }
}