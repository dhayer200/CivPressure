package com.deep.civpressure.compass;

import com.deep.civpressure.CivPressurePlugin;
import com.deep.civpressure.biome.BiomeGroup;
import com.deep.civpressure.biome.BiomeGroupRegistry;
import com.deep.civpressure.config.ConfigManager;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BiomeSearchResult;

public final class BiomeCompassManager {
    private static final String UNSELECTED_GROUP = "None";

    private final CivPressurePlugin plugin;
    private final ConfigManager configManager;
    private final BiomeGroupRegistry biomeGroupRegistry;
    private final NamespacedKey compassMarkerKey;
    private final NamespacedKey selectedGroupKey;
    private final NamespacedKey recipeKey;
    private final Set<SearchKey> pendingSearches = ConcurrentHashMap.newKeySet();
    private final Map<SearchKey, SearchCacheEntry> searchCache = new ConcurrentHashMap<>();
    private final AtomicInteger concurrentSearches = new AtomicInteger();

    private BukkitTask updateTask;

    public BiomeCompassManager(
            CivPressurePlugin plugin,
            ConfigManager configManager,
            BiomeGroupRegistry biomeGroupRegistry
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.biomeGroupRegistry = biomeGroupRegistry;
        compassMarkerKey = new NamespacedKey(plugin, "biome_compass");
        selectedGroupKey = new NamespacedKey(plugin, "biome_group");
        recipeKey = new NamespacedKey(plugin, "biome_compass_recipe");
    }

    public void start() {
        reload();
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        plugin.getServer().removeRecipe(recipeKey);
        pendingSearches.clear();
        searchCache.clear();
    }

    public void reload() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        plugin.getServer().removeRecipe(recipeKey);
        if (configManager.isModuleEnabled("biome-compass")
                && configManager.getBoolean("biome-compass.recipe-enabled", true)) {
            registerRecipe();
        }

        long interval = Math.max(
                20L,
                configManager.getLong("biome-compass.update-interval-ticks", 40L));
        updateTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::updatePlayerCompasses,
                interval,
                interval);
    }

    public ItemStack createCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.getPersistentDataContainer().set(
                compassMarkerKey,
                PersistentDataType.BOOLEAN,
                true);
        applyDisplay(meta, null);
        compass.setItemMeta(meta);
        return compass;
    }

    public boolean isBiomeCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(
                compassMarkerKey,
                PersistentDataType.BOOLEAN);
    }

    public void giveCompass(Player player) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(createCompass());
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(
                    player.getLocation(),
                    item));
        }
        player.sendMessage(Component.text("Biome Compass given.", NamedTextColor.GREEN));
    }

    public void openMenu(Player player, EquipmentSlot hand) {
        int size = normalizeInventorySize(configManager.getInt("biome-compass.gui-size", 27));
        Map<Integer, BiomeGroup> groupsBySlot = new HashMap<>();
        for (BiomeGroup group : BiomeGroup.values()) {
            int configuredSlot = configManager.getInt(
                    "biome-compass.group-slots." + group.name().toLowerCase(Locale.ROOT),
                    defaultSlot(group));
            int slot = Math.max(0, Math.min(size - 1, configuredSlot));
            groupsBySlot.put(slot, group);
        }

        BiomeCompassMenu holder = new BiomeCompassMenu(
                player.getUniqueId(),
                hand,
                groupsBySlot);
        Inventory inventory = Bukkit.createInventory(
                holder,
                size,
                Component.text(configManager.getString(
                        "biome-compass.gui-title",
                        "Select Biome Group")));
        holder.setInventory(inventory);

        if (configManager.getBoolean("biome-compass.gui-fill-empty", true)) {
            Material fillerMaterial = material(
                    configManager.getString(
                            "biome-compass.gui-filler-material",
                            "gray_stained_glass_pane"),
                    Material.GRAY_STAINED_GLASS_PANE);
            ItemStack filler = new ItemStack(fillerMaterial);
            ItemMeta fillerMeta = filler.getItemMeta();
            fillerMeta.displayName(Component.text(" "));
            filler.setItemMeta(fillerMeta);
            for (int slot = 0; slot < size; slot++) {
                inventory.setItem(slot, filler);
            }
        }

        for (Map.Entry<Integer, BiomeGroup> entry : groupsBySlot.entrySet()) {
            inventory.setItem(entry.getKey(), createGroupIcon(entry.getValue()));
        }
        player.openInventory(inventory);
    }

    public boolean selectGroup(Player player, EquipmentSlot hand, BiomeGroup group) {
        ItemStack compass = itemInHand(player, hand);
        if (!isBiomeCompass(compass)) {
            player.sendMessage(Component.text(
                    "The Biome Compass is no longer in that hand.",
                    NamedTextColor.RED));
            return false;
        }

        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.getPersistentDataContainer().set(
                selectedGroupKey,
                PersistentDataType.STRING,
                group.name());
        meta.clearLodestone();
        applyDisplay(meta, group);
        compass.setItemMeta(meta);
        requestSearch(player, group, true);
        return true;
    }

    public BiomeGroup selectedGroup(ItemStack compass) {
        if (!isBiomeCompass(compass)) {
            return null;
        }
        String groupName = compass.getItemMeta().getPersistentDataContainer().get(
                selectedGroupKey,
                PersistentDataType.STRING);
        return groupName == null ? null : BiomeGroup.parse(groupName).orElse(null);
    }

    private String regionName(BiomeGroup group) {
        return configManager.getBiomeGroupDisplayName(group);
    }

    private void updatePlayerCompasses() {
        if (!configManager.isModuleEnabled("biome-compass")) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<BiomeGroup> groups = EnumSet.noneOf(BiomeGroup.class);
            for (ItemStack item : player.getInventory().getContents()) {
                BiomeGroup group = selectedGroup(item);
                if (group != null) {
                    groups.add(group);
                }
            }
            for (BiomeGroup group : groups) {
                SearchKey key = new SearchKey(
                        player.getUniqueId(),
                        player.getWorld().getUID(),
                        group);
                SearchCacheEntry cached = searchCache.get(key);
                if (cached != null && cached.isUsable(player.getLocation(), configManager)) {
                    if (cached.target() != null) {
                        applyTargetToInventory(player, group, cached.target());
                    }
                } else {
                    requestSearch(player, group, false);
                }
            }
        }
    }

    private void requestSearch(Player player, BiomeGroup group, boolean notify) {
        SearchKey key = new SearchKey(
                player.getUniqueId(),
                player.getWorld().getUID(),
                group);
        SearchCacheEntry cached = searchCache.get(key);
        if (cached != null && cached.isUsable(player.getLocation(), configManager)) {
            if (cached.target() != null) {
                applyTargetToInventory(player, group, cached.target());
            }
            if (notify && cached.target() != null) {
                sendTargetFound(player, group, cached.target());
            } else if (notify) {
                player.sendMessage(Component.text(
                        "No " + regionName(group) + " region was found within the search radius.",
                        NamedTextColor.RED));
            }
            return;
        }
        if (!pendingSearches.add(key)) {
            return;
        }

        int maxConcurrent = Math.max(
                1,
                configManager.getInt("biome-compass.max-concurrent-searches", 2));
        if (concurrentSearches.incrementAndGet() > maxConcurrent) {
            concurrentSearches.decrementAndGet();
            pendingSearches.remove(key);
            if (notify) {
                player.sendMessage(Component.text(
                        "Biome search is busy. Try again shortly.",
                        NamedTextColor.YELLOW));
            }
            return;
        }

        Location origin = player.getLocation().clone();
        World world = player.getWorld();
        if (notify) {
            player.sendMessage(Component.text(
                    "Searching for the nearest " + regionName(group) + " region...",
                    NamedTextColor.YELLOW));
        }

        Runnable search = () -> {
            Location target = null;
            Throwable failure = null;
            try {
                target = locateBiome(world, origin, group);
            } catch (Throwable throwable) {
                failure = throwable;
            }

            Location result = target;
            Throwable error = failure;
            Bukkit.getScheduler().runTask(plugin, () -> completeSearch(
                    player.getUniqueId(),
                    key,
                    origin,
                    group,
                    result,
                    error,
                    notify));
        };

        if (configManager.getBoolean("biome-compass.async-search", true)) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, search);
        } else {
            search.run();
        }
    }

    private Location locateBiome(World world, Location origin, BiomeGroup group) {
        Set<Biome> groupBiomes = biomeGroupRegistry.getBiomes(group);
        if (groupBiomes.isEmpty()) {
            return null;
        }

        int radius = Math.max(1, configManager.getInt("biome-compass.search-radius", 6000));
        int horizontalInterval = Math.max(
                1,
                configManager.getInt("biome-compass.search-horizontal-interval", 32));
        int verticalInterval = Math.max(
                1,
                configManager.getInt("biome-compass.search-vertical-interval", 64));
        BiomeSearchResult result = world.locateNearestBiome(
                origin,
                radius,
                horizontalInterval,
                verticalInterval,
                groupBiomes.toArray(Biome[]::new));
        return result == null ? null : result.getLocation();
    }

    private void completeSearch(
            UUID playerId,
            SearchKey key,
            Location origin,
            BiomeGroup group,
            Location target,
            Throwable failure,
            boolean notify
    ) {
        pendingSearches.remove(key);
        concurrentSearches.decrementAndGet();
        Player player = Bukkit.getPlayer(playerId);
        if (failure != null) {
            plugin.getLogger().log(Level.WARNING, "Biome search failed for " + group, failure);
            if (player != null && notify) {
                player.sendMessage(Component.text("Biome search failed.", NamedTextColor.RED));
            }
            return;
        }

        searchCache.put(key, new SearchCacheEntry(
                origin.getX(),
                origin.getZ(),
                System.currentTimeMillis(),
                target));
        if (player == null || !player.isOnline() || !player.getWorld().getUID().equals(key.worldId())) {
            return;
        }
        if (target == null) {
            if (notify) {
                player.sendMessage(Component.text(
                        "No " + regionName(group) + " region was found within the search radius.",
                        NamedTextColor.RED));
            }
            return;
        }

        applyTargetToInventory(player, group, target);
        if (notify) {
            sendTargetFound(player, group, target);
        }
    }

    private void applyTargetToInventory(Player player, BiomeGroup group, Location target) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (selectedGroup(item) != group) {
                continue;
            }

            CompassMeta meta = (CompassMeta) item.getItemMeta();
            meta.setLodestone(target);
            meta.setLodestoneTracked(false);
            item.setItemMeta(meta);
            player.getInventory().setItem(slot, item);
        }
    }

    private void sendTargetFound(Player player, BiomeGroup group, Location target) {
        int distance = (int) Math.round(Math.sqrt(
                player.getLocation().distanceSquared(target)));
        player.sendMessage(Component.text(
                regionName(group) + " located about " + distance + " blocks away.",
                NamedTextColor.GREEN));
    }

    private void registerRecipe() {
        List<Material> saplings = new ArrayList<>();
        for (String configured : configManager.getStringList("biome-compass.recipe-saplings")) {
            Material material = Material.matchMaterial(configured);
            if (material == null) {
                plugin.getLogger().warning("Unknown biome compass recipe material: " + configured);
            } else {
                saplings.add(material);
            }
        }
        if (saplings.isEmpty()) {
            plugin.getLogger().warning("Biome Compass recipe disabled because no saplings are configured.");
            return;
        }

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createCompass());
        recipe.shape("SSS", "SCS", "SSS");
        recipe.setIngredient('S', new RecipeChoice.MaterialChoice(saplings));
        recipe.setIngredient('C', Material.COMPASS);
        plugin.getServer().addRecipe(recipe);
    }

    private ItemStack createGroupIcon(BiomeGroup group) {
        String groupName = group.name().toLowerCase(Locale.ROOT);
        Material iconMaterial = material(
                configManager.getString(
                        "biome-compass.group-icons." + groupName,
                        "compass"),
                Material.COMPASS);
        ItemStack icon = new ItemStack(iconMaterial);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(regionName(group), NamedTextColor.GOLD));
        icon.setItemMeta(meta);
        return icon;
    }

    private void applyDisplay(CompassMeta meta, BiomeGroup group) {
        meta.displayName(Component.text(
                configManager.getString("biome-compass.item-name", "Biome Compass"),
                NamedTextColor.AQUA));
        String selected = group == null ? UNSELECTED_GROUP : regionName(group);
        List<Component> lore = new ArrayList<>();
        for (String line : configManager.getStringList("biome-compass.item-lore")) {
            lore.add(Component.text(
                    line.replace("{group}", selected),
                    NamedTextColor.GRAY));
        }
        meta.lore(lore);
    }

    private ItemStack itemInHand(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
    }

    private Material material(String configured, Material fallback) {
        Material material = Material.matchMaterial(configured);
        return material == null ? fallback : material;
    }

    private int normalizeInventorySize(int configured) {
        int clamped = Math.max(9, Math.min(54, configured));
        return ((clamped + 8) / 9) * 9;
    }

    private int defaultSlot(BiomeGroup group) {
        return switch (group) {
            case PLAINS -> 10;
            case IRON -> 11;
            case GOLD -> 12;
            case LAPIS -> 13;
            case REDSTONE -> 14;
            case EMERALD -> 15;
            case DIAMOND -> 16;
            case UNGROUPED -> 22;
        };
    }

    private record SearchKey(UUID playerId, UUID worldId, BiomeGroup group) {
    }

    private record SearchCacheEntry(
            double originX,
            double originZ,
            long createdAt,
            Location target
    ) {
        private boolean isUsable(Location location, ConfigManager configManager) {
            long maxAgeMillis = Math.max(
                    1L,
                    configManager.getLong("biome-compass.cache-duration-seconds", 300L))
                    * 1000L;
            if (System.currentTimeMillis() - createdAt > maxAgeMillis) {
                return false;
            }
            double movementThreshold = Math.max(
                    0.0,
                    configManager.getDouble(
                            "biome-compass.movement-research-distance",
                            256.0));
            double deltaX = location.getX() - originX;
            double deltaZ = location.getZ() - originZ;
            return deltaX * deltaX + deltaZ * deltaZ <= movementThreshold * movementThreshold;
        }
    }
}
