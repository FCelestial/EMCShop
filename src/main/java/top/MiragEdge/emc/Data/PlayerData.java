package top.MiragEdge.emc.Data;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerData {
    private final UUID playerId;
    private final Set<String> unlockedItems = new HashSet<>();

    public PlayerData(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Set<String> getUnlockedItems() {
        return new HashSet<>(unlockedItems);
    }

    public void unlockItem(String itemId) {
        unlockedItems.add(itemId);
    }

    public boolean isItemUnlocked(String itemId) {
        return unlockedItems.contains(itemId);
    }

    public void clearUnlocks() {
        unlockedItems.clear();
    }
}