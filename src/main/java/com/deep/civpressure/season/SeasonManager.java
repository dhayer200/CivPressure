package com.deep.civpressure.season;

import com.deep.civpressure.CivPressurePlugin;
import com.deep.civpressure.biome.BiomeGroup;
import com.deep.civpressure.biome.BiomeGroupRegistry;
import com.deep.civpressure.config.ConfigManager;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class SeasonManager {
    private static final String STATE_FILE_NAME = "seasons.dat";

    private final CivPressurePlugin plugin;
    private final ConfigManager configManager;
    private final BiomeGroupRegistry biomeGroupRegistry;
    private final Map<BiomeGroup, SeasonState> states = new EnumMap<>(BiomeGroup.class);
    private final File stateFile;
    private Set<String> cropMaterials = Set.of();
    private Set<String> coldBiomes = Set.of();

    private BukkitTask dayTask;
    private BukkitTask farmlandTask;
    private long lastProcessedDay = Long.MIN_VALUE;

    public SeasonManager(
            CivPressurePlugin plugin,
            ConfigManager configManager,
            BiomeGroupRegistry biomeGroupRegistry
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.biomeGroupRegistry = biomeGroupRegistry;
        stateFile = new File(plugin.getDataFolder(), STATE_FILE_NAME);
        for (BiomeGroup group : BiomeGroup.values()) {
            states.put(group, SeasonState.normal());
        }
    }

    public void start() {
        load();
        reloadConfiguredSets();
        restartTasks();
    }

    public void stop() {
        cancelTasks();
        save();
    }

    public void reload() {
        configManager.load();
        refreshConfiguration();
    }

    public void refreshConfiguration() {
        reloadConfiguredSets();
        restartTasks();
    }

    public SeasonState getState(BiomeGroup group) {
        return states.getOrDefault(group, SeasonState.normal());
    }

    public SeasonType getSeason(BiomeGroup group) {
        return getState(group).type();
    }

    public String getRegionDisplayName(BiomeGroup group) {
        return configManager.getBiomeGroupDisplayName(group);
    }

    public void setSeason(BiomeGroup group, SeasonType type, boolean announce) {
        SeasonState previous = getState(group);
        SeasonState next = type == SeasonType.NORMAL
                ? SeasonState.normal()
                : new SeasonState(type, 1);
        states.put(group, next);
        save();

        if (announce && previous.type() != type) {
            announceTransition(group, previous.type(), type);
        }
    }

    public boolean isColdBiome(Biome biome) {
        String biomeKey = biome.getKey().getKey().toLowerCase(Locale.ROOT);
        return coldBiomes.contains(biomeKey);
    }

    public boolean isCrop(Material material) {
        String materialName = material.name().toLowerCase(Locale.ROOT);
        return cropMaterials.contains(materialName);
    }

    public double droughtBlockChance(boolean coldBiome) {
        String path = coldBiome
                ? "seasons.drought-block-chance-cold"
                : "seasons.drought-block-chance";
        double defaultValue = coldBiome ? 0.90 : 0.75;
        return chance(path, defaultValue);
    }

    public double wetBonusChance(boolean coldBiome) {
        String path = coldBiome
                ? "seasons.wet-bonus-chance-cold"
                : "seasons.wet-bonus-chance";
        double defaultValue = coldBiome ? 0.60 : 0.40;
        return chance(path, defaultValue);
    }

    public boolean appliesTo(World world) {
        return !configManager.getBoolean("seasons.overworld-only", true)
                || world.getEnvironment() == World.Environment.NORMAL;
    }

    public boolean cropEffectsEnabled() {
        return configManager.getBoolean("seasons.crop-effects-enabled", true);
    }

    public boolean droughtCropEffectEnabled() {
        return configManager.getBoolean("seasons.drought-crop-effect-enabled", true);
    }

    public boolean wetCropEffectEnabled() {
        return configManager.getBoolean("seasons.wet-crop-effect-enabled", true);
    }

    public boolean farmlandEffectEnabled(SeasonType season) {
        if (!configManager.getBoolean("seasons.farmland-enabled", true)) {
            return false;
        }
        return switch (season) {
            case DROUGHT -> configManager.getBoolean("seasons.drought-farmland-enabled", true);
            case WET -> configManager.getBoolean("seasons.wet-farmland-enabled", true);
            case NORMAL -> false;
        };
    }

    public int targetFarmlandMoisture(Farmland farmland, SeasonType season) {
        int configuredMoisture = switch (season) {
            case DROUGHT -> configManager.getInt("seasons.farmland-drought-moisture", 0);
            case WET -> configManager.getInt(
                    "seasons.farmland-wet-moisture",
                    farmland.getMaximumMoisture());
            case NORMAL -> farmland.getMoisture();
        };
        return clampMoisture(configuredMoisture, farmland);
    }

    private void restartTasks() {
        cancelTasks();
        long rollInterval = Math.max(
                20L,
                configManager.getLong("seasons.roll-check-interval-ticks", 100L));
        dayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkDay, rollInterval, rollInterval);

        if (configManager.getBoolean("seasons.farmland-enabled", true)) {
            long farmlandInterval = Math.max(
                    20L,
                    configManager.getLong("seasons.farmland-check-interval-ticks", 100L));
            farmlandTask = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    this::updateNearbyFarmland,
                    farmlandInterval,
                    farmlandInterval);
        }
    }

    private void cancelTasks() {
        if (dayTask != null) {
            dayTask.cancel();
            dayTask = null;
        }
        if (farmlandTask != null) {
            farmlandTask.cancel();
            farmlandTask = null;
        }
    }

    private void checkDay() {
        if (!configManager.isModuleEnabled("seasons")) {
            return;
        }
        if (!configManager.getBoolean("seasons.automatic-rolls-enabled", true)) {
            return;
        }

        World clockWorld = findClockWorld();
        if (clockWorld == null) {
            return;
        }

        long currentDay = Math.floorDiv(clockWorld.getFullTime(), 24000L);
        if (lastProcessedDay == Long.MIN_VALUE) {
            lastProcessedDay = currentDay;
            save();
            return;
        }
        if (currentDay < lastProcessedDay) {
            lastProcessedDay = currentDay;
            save();
            return;
        }

        long maxCatchUpDays = Math.max(
                1L,
                configManager.getLong("seasons.max-catch-up-days", 32L));
        if (currentDay - lastProcessedDay > maxCatchUpDays) {
            lastProcessedDay = currentDay - maxCatchUpDays;
        }
        while (lastProcessedDay < currentDay) {
            lastProcessedDay++;
            rollDay();
        }
    }

    private void rollDay() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (BiomeGroup group : BiomeGroup.values()) {
            SeasonState state = getState(group);
            if (state.type() == SeasonType.NORMAL) {
                double roll = random.nextDouble();
                double droughtChance = chance("seasons.drought-chance", 0.12);
                double wetChance = chance("seasons.wet-chance", 0.12);
                if (roll < droughtChance) {
                    setSeason(group, SeasonType.DROUGHT, true);
                } else if (roll < droughtChance + wetChance) {
                    setSeason(group, SeasonType.WET, true);
                }
                continue;
            }

            int nextDaysActive = state.daysActive() + 1;
            double endChance = Math.min(
                    chance("seasons.max-end-chance", 0.99),
                    chance("seasons.end-chance-start", 0.30)
                            + Math.max(0, nextDaysActive - 1)
                            * Math.max(0.0, configManager.getDouble(
                                    "seasons.end-chance-increase-per-day",
                                    0.045)));
            if (random.nextDouble() < endChance) {
                setSeason(group, SeasonType.NORMAL, true);
            } else {
                states.put(group, new SeasonState(state.type(), nextDaysActive));
            }
        }
        save();
    }

    private World findClockWorld() {
        String configuredWorld = configManager.getString("seasons.clock-world", "").trim();
        if (!configuredWorld.isEmpty()) {
            World world = Bukkit.getWorld(configuredWorld);
            if (world != null) {
                return world;
            }
        }
        return Bukkit.getWorlds().stream()
                .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                .findFirst()
                .orElse(null);
    }

    private void updateNearbyFarmland() {
        if (!configManager.isModuleEnabled("seasons")) {
            return;
        }

        int radius = Math.max(0, configManager.getInt("seasons.farmland-check-radius", 8));
        int verticalRadius = Math.max(0, configManager.getInt("seasons.farmland-vertical-radius", 4));
        int maxChecks = Math.max(
                1,
                configManager.getInt("seasons.farmland-max-block-checks-per-player", 5000));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!appliesTo(player.getWorld())) {
                continue;
            }
            updateFarmlandAround(player, radius, verticalRadius, maxChecks);
        }
    }

    private void updateFarmlandAround(Player player, int radius, int verticalRadius, int maxChecks) {
        Block center = player.getLocation().getBlock();
        int checked = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -verticalRadius; y <= verticalRadius; y++) {
                    if (++checked > maxChecks) {
                        return;
                    }

                    int blockX = center.getX() + x;
                    int blockY = center.getY() + y;
                    int blockZ = center.getZ() + z;
                    if (blockY < player.getWorld().getMinHeight()
                            || blockY >= player.getWorld().getMaxHeight()
                            || !player.getWorld().isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                        continue;
                    }

                    Block block = player.getWorld().getBlockAt(blockX, blockY, blockZ);
                    if (block.getType() != Material.FARMLAND
                            || !(block.getBlockData() instanceof Farmland farmland)) {
                        continue;
                    }

                    BiomeGroup group = biomeGroupRegistry.resolve(block.getBiome(), block.getY());
                    SeasonType season = getSeason(group);
                    if (farmlandEffectEnabled(season)) {
                        setMoisture(block, farmland, targetFarmlandMoisture(farmland, season));
                    }
                }
            }
        }
    }

    private int clampMoisture(int moisture, Farmland farmland) {
        return Math.max(0, Math.min(farmland.getMaximumMoisture(), moisture));
    }

    private void setMoisture(Block block, Farmland farmland, int moisture) {
        if (farmland.getMoisture() == moisture) {
            return;
        }
        BlockData updated = farmland.clone();
        ((Farmland) updated).setMoisture(moisture);
        block.setBlockData(updated, false);
    }

    private double chance(String path, double defaultValue) {
        return Math.max(0.0, Math.min(1.0, configManager.getDouble(path, defaultValue)));
    }

    private void reloadConfiguredSets() {
        cropMaterials = normalize(configManager.getStringList("seasons.crop-materials"));
        coldBiomes = normalize(configManager.getStringList("seasons.cold-biomes"));
    }

    private Set<String> normalize(Iterable<String> values) {
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            normalized.add(value.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }

    private void announceTransition(BiomeGroup group, SeasonType previous, SeasonType next) {
        if (!configManager.getBoolean("seasons.broadcast-start-end", true)) {
            return;
        }

        Component message;
        String regionName = getRegionDisplayName(group);
        if (next == SeasonType.NORMAL) {
            message = Component.text(
                    previous.displayName() + " has ended across " + regionName + ".",
                    NamedTextColor.GREEN);
        } else {
            message = Component.text(
                    next.displayName() + " has begun across " + regionName + ".",
                    next == SeasonType.DROUGHT ? NamedTextColor.GOLD : NamedTextColor.AQUA);
        }
        Bukkit.broadcast(message);
    }

    private void load() {
        if (!stateFile.exists()) {
            World clockWorld = findClockWorld();
            if (clockWorld != null) {
                lastProcessedDay = Math.floorDiv(clockWorld.getFullTime(), 24000L);
            }
            save();
            return;
        }

        YamlConfiguration data = YamlConfiguration.loadConfiguration(stateFile);
        lastProcessedDay = data.getLong("last-processed-day", Long.MIN_VALUE);
        for (BiomeGroup group : BiomeGroup.values()) {
            String path = "groups." + group.name().toLowerCase(Locale.ROOT);
            String typeName = data.getString(path + ".type", SeasonType.NORMAL.name());
            SeasonType type;
            try {
                type = SeasonType.valueOf(typeName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                type = SeasonType.NORMAL;
            }
            int daysActive = type == SeasonType.NORMAL
                    ? 0
                    : Math.max(1, data.getInt(path + ".days-active", 1));
            states.put(group, new SeasonState(type, daysActive));
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("last-processed-day", lastProcessedDay);
        for (Map.Entry<BiomeGroup, SeasonState> entry : states.entrySet()) {
            String path = "groups." + entry.getKey().name().toLowerCase(Locale.ROOT);
            data.set(path + ".type", entry.getValue().type().name());
            data.set(path + ".days-active", entry.getValue().daysActive());
        }

        try {
            data.save(stateFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + STATE_FILE_NAME, exception);
        }
    }
}
