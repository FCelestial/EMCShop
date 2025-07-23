package top.MiragEdge.emc.Gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import top.MiragEdge.emc.EMCShop;
import top.MiragEdge.emc.Manager.EMCManager;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TransmutationGUI implements InventoryHolder, Listener {

    private final JavaPlugin plugin;
    private final EMCManager emcManager;
    private final Map<UUID, Inventory> openInventories = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> pendingItems = new ConcurrentHashMap<>();
    private final Set<UUID> convertingPlayers = new HashSet<>();
    private final File pendingItemsFile;

    // 界面布局常量
    private static final int[] BACKGROUND_SLOTS = {45, 46, 47, 48, 50, 51, 52, 53};
    private static final int CONVERT_SLOT = 49;
    private static final int[] INPUT_SLOTS = new int[45];

    // 性能优化标记
    private volatile boolean saveInProgress = false;
    private volatile long lastSaveTime = 0;
    private static final long SAVE_COOLDOWN = 5000;

    private boolean sellClick = false; // 用于区分是点击按钮关闭还是 Q 键出售

    static {
        for (int i = 0; i < 45; i++) {
            INPUT_SLOTS[i] = i;
        }
    }

    public TransmutationGUI(JavaPlugin plugin, EMCManager emcManager) {
        this.plugin = plugin;
        this.emcManager = emcManager;
        this.pendingItemsFile = new File(plugin.getDataFolder(), "pending_items.yml");

        // 确保数据目录存在
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);

        // 加载保存的待处理物品
        loadPendingItems();

        // 启动自动保存任务
        startAutoSaveTask();
    }

    /**
     * 保存所有待处理物品到文件（异步执行）
     */
    public void saveAllPendingItemsAsync() {
        // 避免频繁保存
        if (saveInProgress || (System.currentTimeMillis() - lastSaveTime < SAVE_COOLDOWN)) {
            return;
        }

        saveInProgress = true;
        lastSaveTime = System.currentTimeMillis();

        // 创建数据快照
        Map<UUID, List<ItemStack>> snapshot = new HashMap<>(pendingItems);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                savePendingItemsToFile(snapshot);
            } finally {
                saveInProgress = false;
            }
        });
    }

    /**
     * 同步保存所有待处理物品到文件
     */
    public void saveAllPendingItemsSync() {
        if (saveInProgress) return;

        saveInProgress = true;
        try {
            savePendingItemsToFile(new HashMap<>(pendingItems));
        } finally {
            saveInProgress = false;
        }
    }

    /**
     * 实际保存方法
     */
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

    /**
     * 从文件加载待处理物品
     */
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

    /**
     * 启动自动保存任务
     */
    private void startAutoSaveTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, Inventory> entry : openInventories.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    savePendingItems(player, entry.getValue());
                }
            }
            saveAllPendingItemsAsync(); // 异步保存
        }, 600, 600); // 每30秒保存一次（20 ticks = 1秒）
    }

    /**
     * 保存待处理物品
     */
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

    /**
     * 恢复待处理物品
     */
    public void restorePendingItems(Player player) {
        UUID playerId = player.getUniqueId();
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
                player.sendMessage(ChatColor.RED + "由于您上次非正常退出，物品转换菜单中的物品已退还至您的背包和脚下");
            } else {
                player.sendMessage(ChatColor.YELLOW + "已恢复您上次在物品转换菜单中的物品");
            }
        }
    }

    /**
     * 为玩家打开转换菜单
     */
    public void open(Player player) {
        // 恢复任何待处理物品
        restorePendingItems(player);

        String title = ChatColor.translateAlternateColorCodes('&', "&l&6等价交换 - 物品转换菜单");
        Inventory inv = Bukkit.createInventory(this, 54, title);

        // 填充背景
        ItemStack background = createBackgroundItem();
        for (int slot : BACKGROUND_SLOTS) {
            inv.setItem(slot, background);
        }

        // 添加转换按钮
        updateConvertButton(inv, player, 0);

        player.openInventory(inv);
        openInventories.put(player.getUniqueId(), inv);
        // 不再在open方法中调用 handleSellClick，因为它与Q键出售逻辑关联
        // 播放音效
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN, 1.0f, 1.0f);
    }

    /**
     * 创建背景物品（淡蓝色玻璃板）
     */
    private ItemStack createBackgroundItem() {
        ItemStack item = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + " ");
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建转换按钮（附魔瓶）
     * @param totalValue 当前菜单中物品的总价值
     * @param player 当前玩家
     */
    private void updateConvertButton(Inventory inv, Player player, double totalValue) {
        ItemStack convertButton = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = convertButton.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName(ChatColor.GREEN + "放入物品进行转换");

        // 计算背包中所有物品的总价值
        double backpackValue = calculateBackpackTotalValue(player);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "菜单中物品价值: " + ChatColor.GOLD + String.format("%,.1f", totalValue) + " 灵叶");
        lore.add(ChatColor.GRAY + "背包中物品价值: " + ChatColor.GOLD + String.format("%,.1f", backpackValue) + " 灵叶");
        lore.add("");
        lore.add(ChatColor.YELLOW + "关闭界面或点击进行转换");
        lore.add(ChatColor.YELLOW + "按" + ChatColor.RED + " Q扔出 " + ChatColor.YELLOW + "转换背包中所有物品");


        meta.setLore(lore);
        convertButton.setItemMeta(meta);

        inv.setItem(CONVERT_SLOT, convertButton);
    }

    /**
     * 检查一个物品是否为“基础物品”，即没有附魔、自定义名称、lore等附加数据。
     * @param item 要检查的物品
     * @return 如果是基础物品则返回 true，否则返回 false
     */
    private boolean isBasicItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false; // 空气或null不是有效物品
        }
        // 如果物品没有ItemMeta，那它一定是基础物品
        if (!item.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        // 检查所有常见的修改：名称、Lore、附魔、自定义模型数据、持久化数据容器
        return !meta.hasDisplayName() &&
                !meta.hasLore() &&
                !meta.hasEnchants() &&
                !meta.hasCustomModelData() &&
                meta.getPersistentDataContainer().isEmpty();
    }


    /**
     * 计算菜单中物品的总价值
     * 现在只计算基础物品的价值
     */
    private double calculateTotalValue(Inventory inv) {
        double total = 0.0;
        for (int slot : INPUT_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;

            // 新增检查：只计算基础物品
            if (!isBasicItem(item)) {
                continue;
            }

            String itemId = item.getType().name();
            int value = emcManager.getItemValue(itemId);
            if (value > 0) {
                total += value * item.getAmount();
            }
        }
        return total;
    }

    /**
     * 计算玩家背包中所有物品的总价值
     * 现在只计算基础物品的价值
     * @param player 玩家对象
     * @return 背包物品总价值
     */
    private double calculateBackpackTotalValue(Player player) {
        double total = 0.0;
        ItemStack[] contents = player.getInventory().getContents();

        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) continue;

            if (!isBasicItem(item)) {
                continue;
            }

            String itemId = item.getType().name();
            int value = emcManager.getItemValue(itemId);

            if (value > 0) {
                total += value * item.getAmount();
            }
        }
        return total;
    }

    /**
     * 出售玩家背包中所有有价值的【基础】物品，并解锁新物品。
     * @param player 玩家对象
     */
    private void sellAllItemsInBackpack(Player player) {
        double totalValue = 0.0;
        boolean skippedItems = false;
        List<TranslatableComponent> unlockedComponents = new ArrayList<>(); // 用于收集解锁的物品名称
        ItemStack[] contents = player.getInventory().getContents();

        // 复制一份背包内容进行遍历，因为要修改原背包
        ItemStack[] currentContents = player.getInventory().getContents().clone();

        for (int i = 0; i < currentContents.length; i++) {
            ItemStack item = currentContents[i]; // 使用复制的物品
            if (item == null || item.getType() == Material.AIR) continue;

            // 新增检查：如果不是基础物品，则跳过
            if (!isBasicItem(item)) {
                skippedItems = true;
                continue;
            }

            String itemId = item.getType().name();
            int value = emcManager.getItemValue(itemId);

            if (value > 0) {
                totalValue += value * item.getAmount();
                // 仅当确认物品有价值时才从玩家实际背包中移除
                player.getInventory().setItem(i, null);

                // --- 添加解锁逻辑 ---
                if (!emcManager.isItemUnlocked(player, itemId)) {
                    emcManager.unlockItem(player, itemId);
                    TranslatableComponent translatedName = Component.translatable(item.getType().translationKey());
                    unlockedComponents.add(translatedName);
                }
                // --- 解锁逻辑结束 ---
            } else {
                // 如果物品没有价值，也不应该被移除，所以这里不需要操作
            }
        }

        // 添加经济
        if (totalValue > 0) {
            Economy econ = EMCShop.getEconomy();
            if (econ != null) {
                econ.depositPlayer(player, totalValue);
            }
            player.sendMessage(ChatColor.AQUA + "出售背包物品获得 " +
                    ChatColor.GREEN + String.format("%,.1f", totalValue) + " 🍃");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } else {
            player.sendMessage(ChatColor.RED + "背包中没有可出售的基础物品！");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
        }

        // 解锁提示
        if (!unlockedComponents.isEmpty()) {
            Component baseMessage = Component.text("解锁了新物品: ").color(NamedTextColor.AQUA);
            for (int i = 0; i < unlockedComponents.size(); i++) {
                if (i > 0) {
                    baseMessage = baseMessage.append(Component.text(i < unlockedComponents.size() - 1 ? ", " : " 和 ").color(NamedTextColor.YELLOW));
                }
                baseMessage = baseMessage.append(
                        unlockedComponents.get(i)
                                .color(NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false)
                );
            }
            player.sendMessage(baseMessage);
        }

        if (skippedItems) {
            player.sendMessage(ChatColor.YELLOW + "已自动跳过背包中无法转换的物品。");
        }
    }

    /**
     * 执行转换操作
     */
    private void performConversion(Player player, Inventory inv, boolean isNormalClose) {
        UUID playerId = player.getUniqueId();

        if (convertingPlayers.contains(playerId)) return;

        try {
            convertingPlayers.add(playerId);

            List<ItemStack> itemsSnapshot = new ArrayList<>();
            for (int slot : INPUT_SLOTS) {
                ItemStack item = inv.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    itemsSnapshot.add(item.clone());
                }
            }

            // 如果菜单为空，直接提示并返回
            if (itemsSnapshot.isEmpty()) {
                player.sendMessage(ChatColor.RED + "没发现任何物品呢？？");
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
                int value = emcManager.getItemValue(itemId);

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

            // 如果处理后总价值和解锁物品都为0，说明所有物品都无效
            if (totalValue == 0 && unlockedComponents.isEmpty()) {
                player.sendMessage(ChatColor.RED + "好像没有可转换的物品哦~");
                // 此时，下面的退还逻辑仍然会执行，将所有物品退还给玩家
            }

            // 添加经济
            if (totalValue > 0) {
                Economy econ = EMCShop.getEconomy();
                if (econ != null) {
                    econ.depositPlayer(player, totalValue);
                }
                player.sendMessage(ChatColor.AQUA + "转换物品获得了 " +
                        ChatColor.GREEN + String.format("%,.1f", totalValue) + " 🍃");
            }

            // 解锁提示
            if (!unlockedComponents.isEmpty()) {
                Component baseMessage = Component.text("解锁了新物品: ").color(NamedTextColor.AQUA);
                for (int i = 0; i < unlockedComponents.size(); i++) {
                    if (i > 0) {
                        baseMessage = baseMessage.append(Component.text(i < unlockedComponents.size() - 1 ? ", " : " 和 ").color(NamedTextColor.YELLOW));
                    }
                    baseMessage = baseMessage.append(
                            unlockedComponents.get(i)
                                    .color(NamedTextColor.YELLOW)
                                    .decoration(TextDecoration.ITALIC, false)
                    );
                }
                player.sendMessage(baseMessage);
            }

            // 退还无效/非基础物品
            if (!itemsToReturn.isEmpty()) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(
                        itemsToReturn.toArray(new ItemStack[0])
                );

                if (!leftover.isEmpty()) {
                    for (ItemStack item : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                    player.sendMessage(ChatColor.RED + "部分无法转换的物品因背包已满已掉落在地");
                }

                // 仅在有成功转换的物品时，才发送“部分物品退还”的消息，避免信息重复
                if (totalValue > 0 || !unlockedComponents.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "部分无法转换的物品已退还。");
                }
            }

            // 清空菜单
            for (int slot : INPUT_SLOTS) {
                inv.setItem(slot, null);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "转换过程中发生错误: " + e.getMessage(), e);
            savePendingItems(player, inv);
            player.sendMessage(ChatColor.RED + "转换过程出错，物品已保存");
        } finally {
            convertingPlayers.remove(playerId);
            openInventories.remove(playerId);
            // 这里修改：只有当玩家真正离开或关闭菜单时才移除pendingItems，而不是每次转换都移除
            // 否则，如果Q键出售后又立即关闭菜单，可能导致pendingItems被错误移除
            // if (!convertingPlayers.contains(playerId)) {
            //    pendingItems.remove(playerId);
            // }
        }
    }

    // =================================================================================
    // 事件处理部分
    // =================================================================================

    // handleSellClick 方法不再需要，因为我们直接在 sellAllItemsInBackpack 中处理
    // public void handleSellClick() {
    //     if (sellClick) {
    //         sellClick = false;
    //     }
    // }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
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
                // 直接调用出售背包物品的方法
                sellAllItemsInBackpack(player);
                // Q键出售后，不再需要关闭菜单，更新按钮信息即可
                double totalValue = calculateTotalValue(inv);
                updateConvertButton(inv, player, totalValue);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                return;
            }

            event.setCancelled(true);
            this.sellClick = true; // 标记为点击按钮触发的关闭
            player.closeInventory(); // 关闭菜单将触发 InventoryCloseEvent
            return;
        }

        // 延迟执行，确保物品已放入/移出槽位
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            double totalValue = calculateTotalValue(inv);
            updateConvertButton(inv, player, totalValue);
        }, 1L);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
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
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        Inventory inv = openInventories.get(playerId);

        if (inv == null) return;

        // 如果是点击按钮触发的关闭 (sellClick 为 true) 或玩家主动关闭 (PLAYER)
        // 那么执行转换逻辑
        if (this.sellClick || event.getReason() == InventoryCloseEvent.Reason.PLAYER) {
            this.sellClick = false; // 重置标记
            performConversion(player, inv, true); // isNormalClose 设为 true
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.9f);
        } else {
            // 其他异常关闭情况，保存物品
            savePendingItems(player, inv);
            saveAllPendingItemsAsync();
            player.sendMessage(ChatColor.YELLOW + "菜单异常关闭，物品已保存");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.9f);
        }
        openInventories.remove(playerId);
        // 不再在这里移除 pendingItems，因为 performConversion 已经处理
        // 或者在 performConversion 的 finally 块中处理
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Inventory inv = openInventories.get(playerId);

        if (inv != null) {
            savePendingItems(player, inv);
            saveAllPendingItemsSync();
            openInventories.remove(playerId);
            // 确保玩家退出时，如果还有待处理物品，则保留
            // pendingItems.remove(playerId); // 这里不应该移除，除非是完全处理掉了
        }
    }

    @Override
    public Inventory getInventory() {
        return null; // 此方法通常在自定义InventoryHolder时用于返回实际Inventory实例，此处未使用
    }

    public void onPluginDisable() {
        for (Map.Entry<UUID, Inventory> entry : openInventories.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                savePendingItems(player, entry.getValue());
            }
        }
        saveAllPendingItemsSync();
        plugin.getLogger().info("已保存所有待处理物品数据");
    }
}