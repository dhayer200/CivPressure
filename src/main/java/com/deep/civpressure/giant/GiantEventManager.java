package com.deep.civpressure.giant;

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
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Giant;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

public final class GiantEventManager {
    private static final int REQUIRED_CLEARANCE = 12;

    private final CivPressurePlugin plugin;
    private final ConfigManager configManager;
    private final BiomeGroupRegistry biomeGroupRegistry;
    private final NamespacedKey giantKey;
    private final NamespacedKey spawnedAtKey;
    private final NamespacedKey trophyKey;
    private final Map<UUID, Long> lastAttackTicks = new HashMap<>();

    private BukkitTask spawnTask;
    private BukkitTask maintenanceTask;
    private Set<BiomeGroup> allowedGroups = Set.of();
    private Set<Material> terrainProtectedMaterials = Set.of();

    public GiantEventManager(
            CivPressurePlugin plugin,
            ConfigManager configManager,
            BiomeGroupRegistry biomeGroupRegistry
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.biomeGroupRegistry = biomeGroupRegistry;
        giantKey = new NamespacedKey(plugin, "event_giant");
        spawnedAtKey = new NamespacedKey(plugin, "event_giant_spawned_at");
        trophyKey = new NamespacedKey(plugin, "giant_trophy");
    }

    public void start() {
        reload();
    }

    public void reload() {
        stopTasks();
        loadAllowedGroups();
        loadTerrainProtections();
        if (!configManager.isModuleEnabled("giant-events")) {
            if (configManager.getBoolean("giant-events.remove-existing-when-disabled", false)) {
                clearAll();
            }
            return;
        }

        configureLoadedGiants();
        long spawnInterval = Math.max(
                20L,
                configManager.getLong("giant-events.spawn-check-interval-ticks", 24000L));
        long maintenanceInterval = Math.max(
                10L,
                configManager.getLong("giant-events.maintenance-interval-ticks", 20L));
        spawnTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::runSpawnCheck,
                spawnInterval,
                spawnInterval);
        maintenanceTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::maintainGiants,
                maintenanceInterval,
                maintenanceInterval);
    }

    public void stop() {
        stopTasks();
        lastAttackTicks.clear();
    }

    public boolean isEventGiant(Giant giant) {
        return giant.getPersistentDataContainer().has(giantKey, PersistentDataType.BOOLEAN);
    }

    public Giant spawnForCommand(Player player) {
        if (!configManager.isModuleEnabled("giant-events")) {
            return null;
        }
        if (!isAllowedWorld(player.getWorld())) {
            return null;
        }
        if (count(player.getWorld()) >= getMaxGiantsPerWorld()) {
            return null;
        }

        Location location = findManualLocation(player);
        return location == null ? null : spawn(location, false);
    }

    public int clear(World world) {
        int removed = 0;
        for (Giant giant : world.getEntitiesByClass(Giant.class)) {
            if (isEventGiant(giant)) {
                lastAttackTicks.remove(giant.getUniqueId());
                giant.remove();
                removed++;
            }
        }
        return removed;
    }

    public int clearAll() {
        int removed = 0;
        for (World world : plugin.getServer().getWorlds()) {
            removed += clear(world);
        }
        return removed;
    }

    public int count(World world) {
        int count = 0;
        for (Giant giant : world.getEntitiesByClass(Giant.class)) {
            if (isEventGiant(giant)) {
                count++;
            }
        }
        return count;
    }

    public int countAll() {
        int count = 0;
        for (World world : plugin.getServer().getWorlds()) {
            count += count(world);
        }
        return count;
    }

    public int getMaxGiantsPerWorld() {
        return Math.max(0, configManager.getInt("giant-events.max-giants-per-world", 1));
    }

    public void configureIfManaged(Giant giant) {
        if (!isEventGiant(giant)) {
            return;
        }
        PersistentDataContainer data = giant.getPersistentDataContainer();
        if (!data.has(spawnedAtKey, PersistentDataType.LONG)) {
            data.set(spawnedAtKey, PersistentDataType.LONG, System.currentTimeMillis());
        }
        configureAttributes(giant);
        giant.setPersistent(true);
        giant.setRemoveWhenFarAway(false);
        giant.setAI(true);
        giant.setAware(true);
    }

    public void populateDrops(Giant giant, List<ItemStack> drops) {
        if (!isEventGiant(giant)) {
            return;
        }
        if (configManager.getBoolean("giant-events.loot.clear-vanilla-drops", true)) {
            drops.clear();
        }
        addConfiguredDrop(drops, "iron", Material.IRON_INGOT, 4, 10, 1.0);
        addConfiguredDrop(drops, "gold", Material.GOLD_INGOT, 1, 4, 0.65);
        addConfiguredDrop(drops, "emerald", Material.EMERALD, 1, 3, 0.30);
        addConfiguredDrop(drops, "diamond", Material.DIAMOND, 1, 1, 0.05);
        addTrophy(drops);
    }

    public int getDroppedExperience() {
        return Math.max(0, configManager.getInt("giant-events.loot.experience", 40));
    }

    public boolean isBlockDamageEnabled() {
        return configManager.getBoolean("giant-events.block-damage", false);
    }

    public boolean isTerrainDamageEnabled() {
        return isBlockDamageEnabled()
                && configManager.getBoolean("giant-events.terrain-damage-enabled", true);
    }

    private void runSpawnCheck() {
        if (!configManager.isModuleEnabled("giant-events")) {
            return;
        }
        double chance = Math.max(
                0.0,
                Math.min(1.0, configManager.getDouble(
                        "giant-events.spawn-chance-per-check",
                        0.003)));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }

        List<Player> eligiblePlayers = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isEligiblePlayer(player)
                    && isAllowedWorld(player.getWorld())
                    && count(player.getWorld()) < getMaxGiantsPerWorld()
                    && (!requiresNight() || isNight(player.getWorld()))) {
                eligiblePlayers.add(player);
            }
        }
        if (eligiblePlayers.isEmpty()) {
            return;
        }

        Player selected = eligiblePlayers.get(
                ThreadLocalRandom.current().nextInt(eligiblePlayers.size()));
        Location location = findRandomLocation(selected);
        if (location != null) {
            spawn(location, true);
        }
    }

    private Giant spawn(Location location, boolean announce) {
        Giant giant = location.getWorld().spawn(
                location,
                Giant.class,
                CreatureSpawnEvent.SpawnReason.CUSTOM,
                spawned -> {
                    PersistentDataContainer data = spawned.getPersistentDataContainer();
                    data.set(giantKey, PersistentDataType.BOOLEAN, true);
                    data.set(spawnedAtKey, PersistentDataType.LONG, System.currentTimeMillis());
                    configureIfManaged(spawned);
                });
        if (announce && configManager.getBoolean("giant-events.announce-spawn", true)) {
            announceSpawn(giant);
        }
        return giant;
    }

    private void configureAttributes(Giant giant) {
        setAttribute(
                giant,
                Attribute.MAX_HEALTH,
                Math.max(1.0, configManager.getDouble("giant-events.health", 160.0)));
        AttributeInstance maxHealth = giant.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            giant.setHealth(maxHealth.getValue());
        }
        setAttribute(
                giant,
                Attribute.ATTACK_DAMAGE,
                Math.max(0.0, configManager.getDouble("giant-events.damage", 12.0)));
        setAttribute(
                giant,
                Attribute.MOVEMENT_SPEED,
                Math.max(0.0, configManager.getDouble("giant-events.movement-speed", 0.23)));
        setAttribute(
                giant,
                Attribute.KNOCKBACK_RESISTANCE,
                Math.max(0.0, Math.min(1.0, configManager.getDouble(
                        "giant-events.knockback-resistance",
                        0.75))));
        setAttribute(
                giant,
                Attribute.FOLLOW_RANGE,
                Math.max(1.0, configManager.getDouble("giant-events.follow-range", 64.0)));
    }

    private void maintainGiants() {
        if (!configManager.isModuleEnabled("giant-events")) {
            return;
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Giant giant : world.getEntitiesByClass(Giant.class)) {
                if (!isEventGiant(giant)) {
                    continue;
                }
                if (shouldDespawn(giant)) {
                    giant.remove();
                    continue;
                }
                pursueNearestTarget(giant);
                if (isTerrainDamageEnabled()) {
                    damageTerrain(giant);
                }
            }
        }
    }

    private boolean shouldDespawn(Giant giant) {
        long lifetimeTicks = Math.max(
                0L,
                configManager.getLong("giant-events.despawn-after-ticks", 36000L));
        if (lifetimeTicks == 0L) {
            return false;
        }
        long spawnedAt = giant.getPersistentDataContainer().getOrDefault(
                spawnedAtKey,
                PersistentDataType.LONG,
                System.currentTimeMillis());
        long lifetimeMillis = lifetimeTicks * 50L;
        if (System.currentTimeMillis() - spawnedAt < lifetimeMillis) {
            return false;
        }
        if (configManager.getBoolean(
                "giant-events.keep-alive-while-players-nearby",
                true)) {
            double radius = Math.max(
                    1.0,
                    configManager.getDouble("giant-events.engagement-radius", 64.0));
            return giant.getWorld().getNearbyPlayers(
                    giant.getLocation(),
                    radius,
                    this::isEligiblePlayer).isEmpty();
        }
        return true;
    }

    private void pursueNearestTarget(Giant giant) {
        double followRange = Math.max(
                1.0,
                configManager.getDouble("giant-events.follow-range", 64.0));
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity target : giant.getWorld().getNearbyLivingEntities(
                giant.getLocation(),
                followRange)) {
            if (!isEligibleTarget(giant, target)) {
                continue;
            }
            double distance = target.getLocation().distanceSquared(giant.getLocation());
            if (distance < nearestDistance) {
                nearest = target;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            giant.setTarget(null);
            giant.getPathfinder().stopPathfinding();
            return;
        }

        giant.setTarget(nearest);
        giant.setAggressive(true);
        double pathSpeed = Math.max(
                0.1,
                configManager.getDouble("giant-events.pathfinder-speed", 1.0));
        giant.getPathfinder().moveTo(nearest, pathSpeed);
        attackIfInRange(giant, nearest, nearestDistance);
    }

    private boolean isEligibleTarget(Giant giant, LivingEntity target) {
        if (target == giant || !target.isValid() || target.isDead() || target instanceof Giant) {
            return false;
        }
        if (target instanceof Player player) {
            return configManager.getBoolean("giant-events.target-players", true)
                    && isEligiblePlayer(player);
        }
        return configManager.getBoolean("giant-events.target-non-hostile-mobs", true)
                && target instanceof Mob
                && !(target instanceof Enemy);
    }

    private void damageTerrain(Giant giant) {
        if (configManager.getBoolean("giant-events.terrain-damage-require-target", false)
                && giant.getTarget() == null) {
            return;
        }
        double chance = Math.max(
                0.0,
                Math.min(1.0, configManager.getDouble(
                        "giant-events.terrain-damage-chance",
                        0.35)));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }

        int radius = Math.max(1, configManager.getInt("giant-events.terrain-damage-radius", 3));
        int blocksPerTick = Math.max(
                1,
                configManager.getInt("giant-events.terrain-damage-blocks-per-tick", 4));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        World world = giant.getWorld();
        Location center = giant.getLocation();
        for (int i = 0; i < blocksPerTick; i++) {
            int x = center.getBlockX() + random.nextInt(-radius, radius + 1);
            int y = center.getBlockY() + random.nextInt(-radius, radius + 1);
            int z = center.getBlockZ() + random.nextInt(-radius, radius + 1);
            Block block = world.getBlockAt(x, y, z);
            if (shouldProtectTerrainBlock(block)) {
                continue;
            }
            block.setType(Material.AIR, false);
        }
    }

    private boolean shouldProtectTerrainBlock(Block block) {
        Material type = block.getType();
        if (block.isEmpty()
                || block.isLiquid()
                || type.isAir()
                || type == Material.BEDROCK
                || type == Material.BARRIER
                || type == Material.END_PORTAL
                || type == Material.END_PORTAL_FRAME
                || type == Material.COMMAND_BLOCK
                || type == Material.REPEATING_COMMAND_BLOCK
                || type == Material.CHAIN_COMMAND_BLOCK
                || type == Material.JIGSAW
                || type == Material.STRUCTURE_BLOCK
                || type == Material.STRUCTURE_VOID) {
            return true;
        }
        if (terrainProtectedMaterials.contains(type)) {
            return true;
        }
        // Never let giants eat player storage, regardless of the configured list.
        return block.getState() instanceof Container;
    }

    private void attackIfInRange(
            Giant giant,
            LivingEntity target,
            double distanceSquared
    ) {
        double attackRange = Math.max(
                1.0,
                configManager.getDouble("giant-events.attack-range", 7.0));
        if (distanceSquared > attackRange * attackRange || !giant.hasLineOfSight(target)) {
            return;
        }
        long cooldown = Math.max(
                1L,
                configManager.getLong("giant-events.attack-cooldown-ticks", 30L));
        long currentTick = giant.getWorld().getGameTime();
        long lastAttack = lastAttackTicks.getOrDefault(giant.getUniqueId(), Long.MIN_VALUE / 2);
        if (currentTick - lastAttack < cooldown) {
            return;
        }
        giant.attack(target);
        lastAttackTicks.put(giant.getUniqueId(), currentTick);
    }

    private Location findRandomLocation(Player player) {
        double minimumDistance = Math.max(
                1.0,
                configManager.getDouble("giant-events.min-distance-from-players", 80.0));
        double maximumDistance = Math.max(
                minimumDistance,
                configManager.getDouble("giant-events.max-distance-from-players", 180.0));
        int attempts = Math.max(
                1,
                configManager.getInt("giant-events.location-attempts", 32));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double distance = random.nextDouble(minimumDistance, maximumDistance + 0.01);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            Location location = findSurface(player.getWorld(), x, z);
            if (isValidSpawnLocation(location, player, minimumDistance)) {
                return location;
            }
        }
        return null;
    }

    private Location findManualLocation(Player player) {
        int attempts = Math.max(
                8,
                configManager.getInt("giant-events.location-attempts", 32));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = attempt == 0
                    ? Math.toRadians(player.getYaw() + 90.0)
                    : random.nextDouble(Math.PI * 2.0);
            double distance = attempt == 0 ? 16.0 : random.nextDouble(12.0, 32.0);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            Location location = findSurface(player.getWorld(), x, z);
            if (location != null) {
                return location;
            }
        }
        return null;
    }

    private Location findSurface(World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        if (y <= world.getMinHeight() || y + REQUIRED_CLEARANCE >= world.getMaxHeight()) {
            return null;
        }
        Location location = new Location(world, x + 0.5, y, z + 0.5);
        if (!location.getBlock().getRelative(0, -1, 0).getType().isSolid()) {
            return null;
        }
        for (int offset = 0; offset < REQUIRED_CLEARANCE; offset++) {
            if (!location.getBlock().getRelative(0, offset, 0).isPassable()) {
                return null;
            }
        }
        return location;
    }

    private boolean isValidSpawnLocation(
            Location location,
            Player selectedPlayer,
            double minimumPlayerDistance
    ) {
        if (location == null || !isAllowedGroup(location)) {
            return false;
        }
        World world = location.getWorld();
        double minimumSpawnDistance = Math.max(
                0.0,
                configManager.getDouble("giant-events.min-distance-from-spawn", 1000.0));
        if (horizontalDistanceSquared(location, world.getSpawnLocation())
                < minimumSpawnDistance * minimumSpawnDistance) {
            return false;
        }
        if (horizontalDistanceSquared(location, selectedPlayer.getLocation())
                < minimumPlayerDistance * minimumPlayerDistance) {
            return false;
        }
        for (Player player : world.getPlayers()) {
            if (isEligiblePlayer(player)
                    && horizontalDistanceSquared(location, player.getLocation())
                    < minimumPlayerDistance * minimumPlayerDistance) {
                return false;
            }
        }
        return world.getWorldBorder().isInside(location);
    }

    private boolean isAllowedGroup(Location location) {
        BiomeGroup group = biomeGroupRegistry.resolve(
                location.getBlock().getBiome(),
                location.getBlockY());
        return allowedGroups.contains(group);
    }

    private boolean isAllowedWorld(World world) {
        return !configManager.getBoolean("giant-events.overworld-only", true)
                || world.getEnvironment() == World.Environment.NORMAL;
    }

    private boolean requiresNight() {
        return configManager.getBoolean("giant-events.require-night", true);
    }

    private boolean isNight(World world) {
        long time = world.getTime();
        return time >= 13000L && time <= 23000L;
    }

    private boolean isEligiblePlayer(Player player) {
        return player.isOnline()
                && !player.isDead()
                && (player.getGameMode() == GameMode.SURVIVAL
                || player.getGameMode() == GameMode.ADVENTURE);
    }

    private void announceSpawn(Giant giant) {
        Location location = giant.getLocation();
        String direction = directionFromSpawn(location);
        String message = configManager.getString(
                "giant-events.announcement-message",
                "A giant has been sighted in the {direction} wilderness...")
                .replace("{direction}", direction);
        if (configManager.getBoolean("giant-events.announce-exact-coordinates", false)) {
            message += " (" + location.getBlockX() + ", " + location.getBlockZ() + ")";
        }
        String formatted = ChatColor.translateAlternateColorCodes('&', message);
        for (Player player : location.getWorld().getPlayers()) {
            player.sendMessage(ChatColor.GOLD + formatted);
        }
    }

    private String directionFromSpawn(Location location) {
        Location spawn = location.getWorld().getSpawnLocation();
        double x = location.getX() - spawn.getX();
        double z = location.getZ() - spawn.getZ();
        double angle = Math.toDegrees(Math.atan2(-x, z));
        if (angle < 0.0) {
            angle += 360.0;
        }
        String[] directions = {
                "southern", "southwestern", "western", "northwestern",
                "northern", "northeastern", "eastern", "southeastern"
        };
        int index = (int) Math.round(angle / 45.0) % directions.length;
        return directions[index];
    }

    private void addConfiguredDrop(
            List<ItemStack> drops,
            String name,
            Material defaultMaterial,
            int defaultMinimum,
            int defaultMaximum,
            double defaultChance
    ) {
        String path = "giant-events.loot." + name;
        if (!configManager.getBoolean(path + ".enabled", true)) {
            return;
        }
        Material material = Material.matchMaterial(
                configManager.getString(path + ".material", defaultMaterial.name()));
        if (material == null || material.isAir()) {
            plugin.getLogger().warning("Invalid giant loot material at " + path + ".material");
            return;
        }
        double chance = Math.max(
                0.0,
                Math.min(1.0, configManager.getDouble(path + ".chance", defaultChance)));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        int minimum = Math.max(1, configManager.getInt(path + ".minimum", defaultMinimum));
        int maximum = Math.max(minimum, configManager.getInt(path + ".maximum", defaultMaximum));
        int amount = ThreadLocalRandom.current().nextInt(minimum, maximum + 1);
        drops.add(new ItemStack(material, Math.min(amount, material.getMaxStackSize())));
    }

    private void addTrophy(List<ItemStack> drops) {
        String path = "giant-events.loot.trophy";
        if (!configManager.getBoolean(path + ".enabled", true)) {
            return;
        }
        double chance = Math.max(
                0.0,
                Math.min(1.0, configManager.getDouble(path + ".chance", 1.0)));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        Material material = Material.matchMaterial(
                configManager.getString(path + ".material", Material.GHAST_TEAR.name()));
        if (material == null || material.isAir()) {
            plugin.getLogger().warning("Invalid giant trophy material.");
            return;
        }
        ItemStack trophy = new ItemStack(material);
        ItemMeta meta = trophy.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + configManager.getString(
                path + ".name",
                "Giant Trophy"));
        meta.getPersistentDataContainer().set(trophyKey, PersistentDataType.BOOLEAN, true);
        trophy.setItemMeta(meta);
        drops.add(trophy);
    }

    private void configureLoadedGiants() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Giant giant : world.getEntitiesByClass(Giant.class)) {
                configureIfManaged(giant);
            }
        }
    }

    private void loadAllowedGroups() {
        EnumSet<BiomeGroup> groups = EnumSet.noneOf(BiomeGroup.class);
        for (String value : configManager.getStringList("giant-events.allowed-biome-groups")) {
            BiomeGroup.parse(value).ifPresentOrElse(
                    groups::add,
                    () -> plugin.getLogger().warning(
                            "Unknown giant event biome group: " + value.toLowerCase(Locale.ROOT)));
        }
        allowedGroups = Set.copyOf(groups);
    }

    private void loadTerrainProtections() {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String value : configManager.getStringList("giant-events.terrain-damage-protected-materials")) {
            Material material = Material.matchMaterial(value);
            if (material == null) {
                plugin.getLogger().warning(
                        "Unknown giant terrain-damage protected material: "
                                + value.toLowerCase(Locale.ROOT));
            } else {
                materials.add(material);
            }
        }
        terrainProtectedMaterials = materials.isEmpty()
                ? Set.of()
                : Set.copyOf(materials);
    }

    private void stopTasks() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
            maintenanceTask = null;
        }
    }

    private void setAttribute(Giant giant, Attribute attribute, double value) {
        AttributeInstance instance = giant.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private double horizontalDistanceSquared(Location first, Location second) {
        double x = first.getX() - second.getX();
        double z = first.getZ() - second.getZ();
        return x * x + z * z;
    }
}
