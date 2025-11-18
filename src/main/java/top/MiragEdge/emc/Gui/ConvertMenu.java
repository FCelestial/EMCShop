package top.MiragEdge.emc.Gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCloseEvent.Reason;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import top.MiragEdge.emc.Manager.EMCManager;
import top.MiragEdge.emc.EMCShop;
import top.MiragEdge.emc.Utils.MessageUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ConvertMenu implements InventoryHolder, Listener {

    // 定义颜色方案
    private static final TextColor PRIMARY_COLOR = TextColor.color(255, 170, 0);      // 鲜艳的金色
    private static final TextColor SECONDARY_COLOR = TextColor.color(85, 170, 255);   // 饱和的蓝色
    private static final TextColor SUCCESS_COLOR = TextColor.color(85, 255, 85);      // 明亮的绿色
    private static final TextColor WARNING_COLOR = TextColor.color(255, 170, 0);      // 橙色
    private static final TextColor ERROR_COLOR = TextColor.color(255, 85, 85);        // 红色
    private static final TextColor INFO_COLOR = TextColor.color(170, 170, 170);       // 中性灰色
    private static final TextColor VALUE_COLOR = TextColor.color(255, 255, 85);       // 亮黄色
    private static final TextColor BUTTON_COLOR = TextColor.color(85, 255, 255);      // 青蓝色

    private final JavaPlugin plugin;
    private final EMCManager emcManager;
    private final Map<UUID, Inventory> openInventories = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> pendingItems = new ConcurrentHashMap<>();
    private final Set<UUID> convertingPlayers = new HashSet<>();
    private final Map<UUID, Boolean> conversionStatus = new ConcurrentHashMap<>();
    private final File pendingItemsFile;

    // 界面布局常量
    private static final int[] BACKGROUND_SLOTS = {45, 46, 47, 48, 50, 51, 52, 53};
    private static final int CONVERT_SLOT = 49;
    private static final int[] INPUT_SLOTS = new int[45];

    // 性能优化标记
    private volatile boolean saveInProgress = false;
    private volatile long lastSaveTime = 0;
    private static final long SAVE_COOLDOWN = 5000;

    // 修复：使用线程安全的映射来跟踪每个玩家的出售点击状态
    private final Map<UUID, Boolean> sellClickMap = new ConcurrentHashMap<>();

    static {
        for (int i = 0; i < 45; i++) {
            INPUT_SLOTS[i] = i;
        }
    }

    public ConvertMenu(JavaPlugin plugin, EMCManager emcManager) {
        this.plugin = plugin;
        this.emcManager = emcManager;
        this.pendingItemsFile = new File(plugin.getDataFolder(), "pending_items.yml");

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadPendingItems();
        startAutoSaveTask();
    }

    public void saveAllPendingItemsAsync() {
        if (saveInProgress || (System.currentTimeMillis() - lastSaveTime < SAVE_COOLDOWN)) {
            return;
        }

        saveInProgress = true;
        lastSaveTime = System.currentTimeMillis();

        Map<UUID, List<ItemStack>> snapshot = new HashMap<>(pendingItems);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                savePendingItemsToFile(snapshot);
            } finally {
                saveInProgress = false;
            }
        });
    }

    public void saveAllPendingItemsSync() {
        if (saveInProgress) return;

        saveInProgress = true;
        try {
            savePendingItemsToFile(new HashMap<>(pendingItems));
        } finally {
            saveInProgress = false;
        }
    }

    private void savePendingItemsToFile(Map<UUID, List<ItemStack>> itemsMap) {
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, List<ItemStack>> entry : itemsMap.entrySet()) {
            List<Map<String, Object>> serializedItems = new ArrayList<>();
            for (ItemStack item : entry.getValue()) {
                serializedItems.add(item.serialize());
            }
            config.set(entry.getKey().toString(), serializedItems);
        }

        try {
            config.save(pendingItemsFile);
            plugin.getLogger().log(Level.FINE, "成功保存待处理物品数据");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "保存待处理物品数据时出错: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadPendingItems() {
        if (!pendingItemsFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(pendingItemsFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                List<Map<String, Object>> serializedItems = (List<Map<String, Object>>) config.getList(key);

                if (serializedItems != null && !serializedItems.isEmpty()) {
                    List<ItemStack> items = new ArrayList<>();
                    for (Map<String, Object> serialized : serializedItems) {
                        try {
                            ItemStack item = ItemStack.deserialize(serialized);
                            items.add(item);
                        } catch (Exception e) {
                            plugin.getLogger().warning("无法加载物品: " + e.getMessage());
                        }
                    }
                    pendingItems.put(uuid, items);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无效的UUID格式: " + key);
            }
        }
        plugin.getLogger().info("已加载 " + pendingItems.size() + " 个玩家的待处理物品");
    }

    private void startAutoSaveTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, Inventory> entry : openInventories.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    savePendingItems(player, entry.getValue());
                }
            }
            saveAllPendingItemsAsync();
        }, 600, 600);
    }

    private void savePendingItems(Player player, Inventory inv) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : INPUT_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }
        if (!items.isEmpty()) {
            pendingItems.put(player.getUniqueId(), items);
        } else {
            pendingItems.remove(player.getUniqueId());
        }
    }

    public void restorePendingItems(Player player) {
        UUID playerId = player.getUniqueId();

        // 修复：使用更可靠的转换状态检查
        if (conversionStatus.containsKey(playerId) || convertingPlayers.contains(playerId)) {
            return;
        }

        if (pendingItems.containsKey(playerId)) {
            List<ItemStack> items = pendingItems.get(playerId);
            pendingItems.remove(playerId);

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(
                    items.toArray(new ItemStack[0])
            );

            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
                player.sendMessage(MessageUtil.getInstance().getMessage("convert-menu.items-returned"));
            } else {
                player.sendMessage(MessageUtil.getInstance().getMessage("convert-menu.items-restored"));
            }
        }
    }

    public void open(Player player) {
        restorePendingItems(player);

        // 从配置读取标题
        Component title = MessageUtil.getInstance().getMessage("convert-menu.title");

        Inventory inv = Bukkit.createInventory(this, 54, title);

        ItemStack background = createBackgroundItem();
        for (int slot : BACKGROUND_SLOTS) {
            inv.setItem(slot, background);
        }

        updateConvertButton(inv, player, 0);

        player.openInventory(inv);
        openInventories.put(player.getUniqueId(), inv);
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN, 1.0f, 1.0f);
    }

    private ItemStack createBackgroundItem() {
        ItemStack item = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ", Style.style(NamedTextColor.WHITE)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void updateConvertButton(Inventory inv, Player player, double totalValue) {
        ItemStack convertButton = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = convertButton.getItemMeta();
        if (meta == null) return;

        String currencyName = MessageUtil.getInstance().getCurrencyName();

        // 使用配置中的按钮名称
        meta.displayName(MessageUtil.getInstance().getMessage("convert-menu.convert-button-name"));

        double backpackValue = calculateBackpackTotalValue(player);

        List<Component> lore = new ArrayList<>();
        // 菜单价值
        lore.add(Component.text()
                .append(Component.text("菜单物品价值: ", INFO_COLOR))
                .append(Component.text(String.format("%,.1f", totalValue) + " " + currencyName, VALUE_COLOR))
                .build());

        // 背包价值
        lore.add(Component.text()
                .append(Component.text("背包物品价值: ", INFO_COLOR))
                .append(Component.text(String.format("%,.1f", backpackValue) + " " + currencyName, SECONDARY_COLOR))
                .build());

        lore.add(Component.empty());

        // 操作提示
        lore.add(Component.text("关闭界面或点击进行转换", SUCCESS_COLOR));

        // 快捷键提示
        lore.add(Component.text()
                .append(Component.text("按 ", PRIMARY_COLOR))
                .append(Component.text("Q", ERROR_COLOR).decorate(TextDecoration.BOLD))
                .append(Component.text(" 键出售背包物品", PRIMARY_COLOR))
                .build());

        meta.lore(lore);
        convertButton.setItemMeta(meta);

        inv.setItem(CONVERT_SLOT, convertButton);
    }

    private boolean isBasicItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        return !meta.hasDisplayName() &&
                !meta.hasLore() &&
                !meta.hasEnchants() &&
                !meta.hasCustomModelData() &&
                meta.getPersistentDataContainer().isEmpty();
    }

    private double calculateTotalValue(Inventory inv) {
        double total = 0.0;
        for (int slot : INPUT_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            if (!isBasicItem(item)) continue;

            String itemId = item.getType().name();
            double value = emcManager.getItemValue(itemId);
            if (value > 0) {
                total += value * item.getAmount();
            }
        }
        return total;
    }

    private double calculateBackpackTotalValue(Player player) {
        double total = 0.0;
        ItemStack[] contents = player.getInventory().getContents();

        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (!isBasicItem(item)) continue;

            String itemId = item.getType().name();
            double value = emcManager.getItemValue(itemId);

            if (value > 0) {
                total += value * item.getAmount();
            }
        }
        return total;
    }

    /**
     * 带重试机制的存款方法（兼容现有代码）
     * @param player 玩家对象
     * @param amount 存款金额
     */
    private void depositWithRetry(Player player, double amount) {
        if (amount <= 0) return;
        if (player == null) return;

        // 确保在主线程执行经济操作
        Bukkit.getScheduler().runTask(plugin, () -> {
            int retries = 0;
            while (retries < 3) {
                try {
                    // 使用EMCManager的统一存款方法
                    boolean success = emcManager.deposit(player, amount);
                    if (success) {
                        return; // 存款成功
                    } else {
                        plugin.getLogger().warning("存款操作返回失败，重试中... (" + (retries + 1) + "/3)");
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("存款操作异常: " + e.getMessage() + "，重试中... (" + (retries + 1) + "/3)");
                }

                retries++;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            plugin.getLogger().warning("无法完成经济操作: " + player.getName() + " - " + amount);
        });
    }

    private void sellAllItemsInBackpack(Player player) {
        double totalValue = 0.0;
        boolean skippedItems = false;
        List<TranslatableComponent> unlockedComponents = new ArrayList<>();
        ItemStack[] currentContents = player.getInventory().getContents().clone();

        for (int i = 0; i < currentContents.length; i++) {
            ItemStack item = currentContents[i];
            if (item == null || item.getType() == Material.AIR) continue;

            if (!isBasicItem(item)) {
                skippedItems = true;
                continue;
            }

            String itemId = item.getType().name();
            double value = emcManager.getItemValue(itemId);

            if (value > 0) {
                totalValue += value * item.getAmount();
                player.getInventory().setItem(i, null);

                if (!emcManager.isItemUnlocked(player, itemId)) {
                    emcManager.unlockItem(player, itemId);
                    TranslatableComponent translatedName = Component.translatable(item.getType().translationKey());
                    unlockedComponents.add(translatedName);
                }
            }
        }

        if (totalValue > 0) {
            depositWithRetry(player, totalValue);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", String.format("%,.1f", totalValue));
            placeholders.put("currency", MessageUtil.getInstance().getCurrencyName());
            player.sendMessage(MessageUtil.getInstance().getMessage("convert-menu.backpack-sold", placeholders));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } else {
            player.sendMessage(MessageUtil.getInstance().getMessage("convert-menu.no-backpack-items"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
        }

        if (!unlockedComponents.isEmpty()) {
            Component baseMessage = MessageUtil.getInstance().getMessage("convert-menu.unlocked-items");
            String commaSeparator = MessageUtil.getInstance().getRawMessage("general.comma-separator");
            String andSeparator = MessageUtil.getInstance().getRawMessage("general.and-separator");

            for (int i = 0; i < unlockedComponents.size(); i++) {
                if (i > 0) {
                    baseMessage = baseMessage.append(Component.text(
                            i < unlockedComponents.size() - 1 ? commaSeparator : andSeparator,
                            INFO_COLOR));
                }
                baseMessage = baseMessage.append(
                        unlockedComponents.get(i)
                                .color(PRIMARY_COLOR)
                                .decoration(TextDecoration.ITALIC, false)
                );
            }
            player.sendMessage(baseMessage);
        }

        if (skippedItems) {
            player.sendMessage(MessageUtil.getInstance().getMessage("convert-menu.skipped-items"));
        }
    }

    private void performConversion(Player player, Inventory inv, boolean isNormalClose) {
        UUID playerId = player.getUniqueId();

        if (convertingPlayers.contains(playerId)) return;

        try {
            conversionStatus.put(playerId, true);
            convertingPlayers.add(playerId);
            pendingItems.remove(playerId);

            List<ItemStack> itemsSnapshot = new ArrayList<>();
            for (int slot : INPUT_SLOTS) {
                ItemStack item = inv.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    itemsSnapshot.add(item.clone());
                }
            }

            if (itemsSnapshot.isEmpty()) {
                player.sendMessage(MessageUtil.getInstance().getMessage("convert-menu.no-items"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                return;
            }

            double totalValue = 0.0;
            List<ItemStack> itemsToReturn = new ArrayList<>();
            List<TranslatableComponent> unlockedComponents = new ArrayList<>();
            boolean hasNonBasicItems = false;

            for (ItemStack item : itemsSnapshot) {
                if (!isBasicItem(item)) {
                    itemsToReturn.add(item);
                    hasNonBasicItems = true;
                    continue;
                }

                String itemId = item.getType().name();
                double value = emcManager.getItemValue(itemId);

                if (value > 0) {
                    totalValue += value * item.getAmount();
                    if (!emcManager.isItemUnlocked(player, itemId)) {
                        emcManager.unlockItem(player, itemId);
                        TranslatableComponent translatedName = Component.translatable(item.getType().translationKey());
                        unlockedComponents.add(translatedName);
                    }
                } else {
                    itemsToReturn.add(item);
                }
            }

            if (totalValue == 0 && unlockedComponents.isEmpty()) {
                player.sendMessage(MessageUtil.getInstance().getMessage("convert-menu.no-convertible-items"));
            }

            if (totalValue > 0) {
                depositWithRetry(player, totalValue);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("amount", String.format("%,.1f", totalValue));
                placeholders.put("currency", MessageUtil.getInstance().getCurrencyName());
                player.sendMessage(MessageUtil.getInstance().getMessage("convert-menu.conversion-success", placeholders));
            }

            if (!unlockedComponents.isEmpty()) {
                Component baseMessage = MessageUtil.getInstance().getMessage("convert-menu.unlocked-items");
                String commaSeparator = MessageUtil.getInstance().getRawMessage("general.comma-separator");
                String andSeparator = MessageUtil.getInstance().getRawMessage("general.and-separator");

                for (int i = 0; i < unlockedComponents.size(); i++) {
                    if (i > 0) {
                        baseMessage = baseMessage.append(Component.text(
                                i < unlockedComponents.size() - 1 ? commaSeparator : andSeparator,
                                INFO_COLOR));
                    }
                    baseMessage = baseMessage.append(
                            unlockedComponents.get(i)
                                    .color(PRIMARY_COLOR)
                                    .decoration(TextDecoration.ITALIC, false)
                    );
                }
                player.sendMessage(baseMessage);
            }

            if (!itemsToReturn.isEmpty()) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(
                        itemsToReturn.toArray(new ItemStack[0])
                );

                if (!leftover.isEmpty()) {
                    for (ItemStack item : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                    player.sendMessage(MessageUtil.getInstance().getMessage("convert-menu.items-dropped"));
                }

                if (totalValue > 0 || !unlockedComponents.isEmpty()) {
                    player.sendMessage(MessageUtil.getInstance().getMessage("convert-menu.some-items-returned"));
                }
            }

            // 修复：在转换完成后才清空物品槽位
            for (int slot : INPUT_SLOTS) {
                inv.setItem(slot, null);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "转换过程中发生错误: " + e.getMessage(), e);
            // 修复：在异常情况下保存物品，但不清空槽位
            savePendingItems(player, inv);
            player.sendMessage(MessageUtil.getInstance().getMessage("convert-menu.error-occurred"));
        } finally {
            convertingPlayers.remove(playerId);
            conversionStatus.remove(playerId);
            openInventories.remove(playerId);
        }
    }

    // ===== 事件处理部分 =====
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID playerId = player.getUniqueId();
        Inventory inv = openInventories.get(playerId);

        if (inv == null || !event.getInventory().equals(inv)) return;

        int slot = event.getRawSlot();

        if (Arrays.stream(BACKGROUND_SLOTS).anyMatch(s -> s == slot)) {
            event.setCancelled(true);
            return;
        }

        if (slot == CONVERT_SLOT) {
            if (event.getAction() == InventoryAction.DROP_ONE_SLOT ||
                    event.getAction() == InventoryAction.DROP_ALL_SLOT) {
                event.setCancelled(true);
                sellAllItemsInBackpack(player);
                double totalValue = calculateTotalValue(inv);
                updateConvertButton(inv, player, totalValue);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                return;
            }

            event.setCancelled(true);
            // 修复：使用线程安全的映射来跟踪每个玩家的出售点击状态
            sellClickMap.put(playerId, true);
            player.closeInventory();
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            double totalValue = calculateTotalValue(inv);
            updateConvertButton(inv, player, totalValue);
        }, 1L);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = openInventories.get(player.getUniqueId());

        if (inv == null || !event.getInventory().equals(inv)) return;

        for (int slot : event.getRawSlots()) {
            if (Arrays.stream(BACKGROUND_SLOTS).anyMatch(s -> s == slot) || slot == CONVERT_SLOT) {
                event.setCancelled(true);
                return;
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            double totalValue = calculateTotalValue(inv);
            updateConvertButton(inv, player, totalValue);
        }, 1L);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        UUID playerId = player.getUniqueId();
        Inventory inv = openInventories.get(playerId);

        if (inv == null) return;

        try {
            // 修复：使用线程安全的映射来检查出售点击状态
            Boolean sellClick = sellClickMap.remove(playerId);

            if (sellClick != null && sellClick || event.getReason() == Reason.PLAYER) {
                performConversion(player, inv, true);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.9f);
            } else {
                savePendingItems(player, inv);
                saveAllPendingItemsAsync();
                player.sendMessage(MessageUtil.getInstance().getMessage("convert-menu.menu-saved"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.9f);
            }
        } finally {
            openInventories.remove(playerId);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Inventory inv = openInventories.get(playerId);

        // 修复：如果玩家正在转换中，不保存物品
        if (inv != null && !convertingPlayers.contains(playerId)) {
            savePendingItems(player, inv);
            saveAllPendingItemsSync();
            openInventories.remove(playerId);
        }
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public void onPluginDisable() {
        for (Map.Entry<UUID, Inventory> entry : openInventories.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                // 修复：如果玩家正在转换中，不保存物品
                if (!convertingPlayers.contains(player.getUniqueId())) {
                    savePendingItems(player, entry.getValue());
                }
            }
        }
        saveAllPendingItemsSync();
        plugin.getLogger().info("已保存所有待处理物品数据");
    }
}