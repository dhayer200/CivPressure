package com.deep.civpressure.survival;

import com.deep.civpressure.CivPressurePlugin;
import com.deep.civpressure.config.ConfigManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class SurvivalHazardManager {
    private final CivPressurePlugin plugin;
    private final ConfigManager configManager;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<UUID, Integer> lastFleePathTick = new HashMap<>();

    private Set<EntityType> fleeingEntityTypes = Set.of();
    private Set<Material> freezeSafeItems = Set.of();
    private Set<Material> freezeArmor = Set.of();
    private Set<String> freezeBiomes = Set.of();
    private Set<String> windBiomes = Set.of();

    public SurvivalHazardManager(CivPressurePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void start() {
        reload();
    }

    public void stop() {
        cancelTasks();
        cleanupFreezeState();
        lastFleePathTick.clear();
    }

    public void reload() {
        cancelTasks();
        cleanupFreezeState();
        lastFleePathTick.clear();
        reloadConfiguredSets();
        scheduleAnimalFlee();
        scheduleFreeze();
        scheduleWind();
        scheduleDrowning();
    }

    private void scheduleAnimalFlee() {
        long interval = Math.max(1L, configManager.getLong("animal-flee.interval-ticks", 10L));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::runAnimalFlee,
                interval,
                interval));
    }

    private void runAnimalFlee() {
        if (!configManager.isModuleEnabled("animal-flee")) {
            return;
        }

        double radius = Math.max(0.0, configManager.getDouble("animal-flee.radius", 8.0));
        double pathfinderSpeed = Math.max(
                0.1,
                configManager.getDouble("animal-flee.pathfinder-speed", 1.65));
        double fleeDistance = Math.max(
                radius,
                configManager.getDouble("animal-flee.flee-distance", 12.0));
        double jitterDegrees = Math.max(
                0.0,
                configManager.getDouble("animal-flee.direction-jitter-degrees", 35.0));
        int destinationAttempts = Math.max(
                1,
                configManager.getInt("animal-flee.destination-attempts", 6));
        int repathInterval = Math.max(
                1,
                configManager.getInt("animal-flee.repath-interval-ticks", 20));
        double fallbackVelocity = Math.max(
                0.0,
                configManager.getDouble("animal-flee.fallback-velocity", 0.45));
        double fallbackVerticalVelocity = configManager.getDouble(
                "animal-flee.fallback-vertical-velocity",
                0.10);
        int maxEntities = Math.max(
                1,
                configManager.getInt("animal-flee.max-entities-per-player", 32));
        Set<UUID> movedEntities = new HashSet<>();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.isSneaking() || isIgnoredGameMode(
                    player,
                    "animal-flee.affect-creative",
                    "animal-flee.affect-spectator",
                    false,
                    false)) {
                continue;
            }

            int considered = 0;
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (++considered > maxEntities) {
                    break;
                }
                if (!(entity instanceof Mob mob)
                        || !fleeingEntityTypes.contains(entity.getType())
                        || mob.isLeashed()
                        || entity.isInsideVehicle()
                        || !entity.getPassengers().isEmpty()
                        || !movedEntities.add(entity.getUniqueId())) {
                    continue;
                }

                int currentTick = mob.getTicksLived();
                Integer lastTick = lastFleePathTick.get(mob.getUniqueId());
                if (lastTick != null
                        && currentTick - lastTick < repathInterval
                        && mob.getPathfinder().hasPath()) {
                    continue;
                }

                boolean pathStarted = startFleePath(
                        mob,
                        player,
                        fleeDistance,
                        jitterDegrees,
                        destinationAttempts,
                        pathfinderSpeed);
                lastFleePathTick.put(mob.getUniqueId(), currentTick);
                if (!pathStarted && fallbackVelocity > 0.0) {
                    Vector away = horizontalAwayVector(mob.getLocation(), player.getLocation());
                    away.multiply(fallbackVelocity).setY(fallbackVerticalVelocity);
                    mob.setVelocity(mob.getVelocity().multiply(0.25).add(away));
                }
            }
        }
    }

    private boolean startFleePath(
            Mob mob,
            Player player,
            double fleeDistance,
            double jitterDegrees,
            int attempts,
            double speed
    ) {
        Vector away = horizontalAwayVector(mob.getLocation(), player.getLocation());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < attempts; attempt++) {
            double jitter = Math.toRadians(random.nextDouble(-jitterDegrees, jitterDegrees));
            Vector direction = rotateHorizontal(away, jitter);
            double distance = fleeDistance * random.nextDouble(0.75, 1.15);
            Location destination = mob.getLocation().clone().add(direction.multiply(distance));
            if (!mob.getWorld().isChunkLoaded(
                    destination.getBlockX() >> 4,
                    destination.getBlockZ() >> 4)) {
                continue;
            }
            if (mob.getPathfinder().moveTo(destination, speed)) {
                return true;
            }
        }
        return false;
    }

    private Vector horizontalAwayVector(Location source, Location threat) {
        Vector away = source.toVector().subtract(threat.toVector()).setY(0);
        if (away.lengthSquared() < 0.0001) {
            away = randomHorizontalDirection();
        }
        return away.normalize();
    }

    private Vector rotateHorizontal(Vector vector, double angle) {
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        return new Vector(
                vector.getX() * cosine - vector.getZ() * sine,
                0,
                vector.getX() * sine + vector.getZ() * cosine).normalize();
    }

    private void scheduleFreeze() {
        long interval = Math.max(1L, configManager.getLong("freeze.interval-ticks", 20L));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> runFreeze(interval),
                interval,
                interval));
    }

    private void runFreeze(long interval) {
        if (!configManager.isModuleEnabled("freeze")) {
            return;
        }

        int freezeIncrement = scaledTicks(
                configManager.getDouble("freeze.freeze-ticks-per-second", 35.0),
                interval);
        int thawAmount = scaledTicks(
                configManager.getDouble("freeze.thaw-ticks-per-second", 20.0),
                interval);
        int lightThreshold = Math.max(0, configManager.getInt("freeze.light-threshold", 8));

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!appliesToWorld(player.getWorld(), "freeze.overworld-only")
                    || isIgnoredGameMode(
                    player,
                    "freeze.affect-creative",
                    "freeze.affect-spectator",
                    false,
                    false)) {
                thaw(player, thawAmount);
                continue;
            }

            Block block = player.getEyeLocation().getBlock();
            boolean coldBiome = freezeBiomes.contains(biomeName(block.getBiome()));
            boolean protectedByLight = Byte.toUnsignedInt(block.getLightFromBlocks()) >= lightThreshold;
            boolean protectedByItem = freezeSafeItems.contains(
                    player.getInventory().getItemInMainHand().getType())
                    || freezeSafeItems.contains(player.getInventory().getItemInOffHand().getType());
            boolean protectedByArmor = configManager.getBoolean("freeze.leather-protects", true)
                    && hasFullProtectiveArmor(player);

            if (!coldBiome || protectedByLight || protectedByItem || protectedByArmor) {
                thaw(player, thawAmount);
                continue;
            }

            if (configManager.getBoolean("freeze.lock-freeze-ticks-while-exposed", true)) {
                player.lockFreezeTicks(true);
            } else if (player.isFreezeTickingLocked()) {
                player.lockFreezeTicks(false);
            }
            player.setFreezeTicks(Math.min(
                    player.getMaxFreezeTicks(),
                    player.getFreezeTicks() + freezeIncrement));
        }
    }

    private void thaw(Player player, int amount) {
        if (player.isFreezeTickingLocked()) {
            player.lockFreezeTicks(false);
        }
        if (amount > 0 && player.getFreezeTicks() > 0) {
            player.setFreezeTicks(Math.max(0, player.getFreezeTicks() - amount));
        }
    }

    private boolean hasFullProtectiveArmor(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor.length < 4) {
            return false;
        }
        for (ItemStack item : armor) {
            if (item == null || !freezeArmor.contains(item.getType())) {
                return false;
            }
        }
        return true;
    }

    private void scheduleWind() {
        long interval = Math.max(1L, configManager.getLong("wind.gust-interval-ticks", 100L));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::telegraphWind,
                interval,
                interval));
    }

    private void telegraphWind() {
        if (!configManager.isModuleEnabled("wind")) {
            return;
        }

        Particle particle = parseParticle(
                configManager.getString("wind.particle", "cloud"),
                Particle.CLOUD);
        Sound sound = parseSound(
                configManager.getString("wind.sound", "entity_breeze_wind_burst"),
                Sound.ENTITY_BREEZE_WIND_BURST);
        int particleCount = Math.max(0, configManager.getInt("wind.particle-count", 18));
        double spread = Math.max(0.0, configManager.getDouble("wind.particle-spread", 0.8));
        float volume = (float) Math.max(0.0, configManager.getDouble("wind.sound-volume", 0.8));
        float pitch = (float) Math.max(0.01, configManager.getDouble("wind.sound-pitch", 0.7));
        long telegraphTicks = Math.max(0L, configManager.getLong("wind.telegraph-ticks", 15L));
        boolean actionbarTelegraph = configManager.getBoolean("wind.actionbar-telegraph", true);
        String actionbarMessage = configManager.getString(
                "wind.actionbar-message",
                "A powerful gust is building...");

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isWindEligible(player)) {
                continue;
            }

            Vector direction = randomHorizontalDirection();
            player.getWorld().spawnParticle(
                    particle,
                    player.getLocation().add(0, 1, 0),
                    particleCount,
                    spread,
                    spread * 0.5,
                    spread,
                    0.02);
            player.playSound(player.getLocation(), sound, volume, pitch);
            if (actionbarTelegraph) {
                player.sendActionBar(Component.text(actionbarMessage, NamedTextColor.GRAY));
            }
            plugin.getServer().getScheduler().runTaskLater(
                    plugin,
                    () -> applyWind(player, direction),
                    telegraphTicks);
        }
    }

    private void applyWind(Player player, Vector direction) {
        if (!player.isOnline() || !isWindEligible(player)) {
            return;
        }

        double minY = configManager.getDouble("wind.min-y", 150.0);
        double peakY = Math.max(minY + 1.0, configManager.getDouble("wind.peak-y", 256.0));
        double minStrength = Math.max(0.0, configManager.getDouble("wind.min-strength", 0.20));
        double maxStrength = Math.max(minStrength, configManager.getDouble("wind.max-strength", 1.20));
        double progress = Math.max(0.0, Math.min(1.0, (player.getY() - minY) / (peakY - minY)));
        double strength = minStrength + (maxStrength - minStrength) * progress;
        if (player.isSneaking()) {
            strength *= 1.0 - Math.max(
                    0.0,
                    Math.min(1.0, configManager.getDouble("wind.sneak-reduce", 0.70)));
        }

        double verticalStrength = configManager.getDouble("wind.vertical-strength", 0.08);
        Vector gust = direction.clone().multiply(strength).setY(verticalStrength);
        player.setVelocity(player.getVelocity().add(gust));
    }

    private boolean isWindEligible(Player player) {
        if (!appliesToWorld(player.getWorld(), "wind.overworld-only")
                || isIgnoredGameMode(
                player,
                "wind.affect-creative",
                "wind.affect-spectator",
                false,
                false)
                || player.getY() < configManager.getDouble("wind.min-y", 150.0)) {
            return false;
        }
        return !configManager.getBoolean("wind.require-configured-biome", true)
                || windBiomes.contains(biomeName(player.getLocation().getBlock().getBiome()));
    }

    private void scheduleDrowning() {
        long interval = Math.max(1L, configManager.getLong("drowning.interval-ticks", 20L));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> runDrowning(interval),
                interval,
                interval));
    }

    private void runDrowning(long interval) {
        if (!configManager.isModuleEnabled("drowning")) {
            return;
        }

        double multiplier = Math.max(
                1.0,
                configManager.getDouble("drowning.air-drain-multiplier", 2.0));
        int baseExtraDrain = (int) Math.round((multiplier - 1.0) * interval);
        int maxExtraDrain = Math.max(
                0,
                configManager.getInt("drowning.max-extra-drain-per-check", 20));
        int minimumAir = configManager.getInt("drowning.minimum-air", -20);
        double respirationReduction = Math.max(
                0.0,
                Math.min(1.0, configManager.getDouble(
                        "drowning.respiration-reduction-per-level",
                        0.25)));

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isUnderWater()
                    || player.hasPotionEffect(org.bukkit.potion.PotionEffectType.WATER_BREATHING)
                    || isIgnoredGameMode(
                    player,
                    "drowning.affect-creative",
                    "drowning.affect-spectator",
                    false,
                    false)) {
                continue;
            }

            ItemStack helmet = player.getInventory().getHelmet();
            int respirationLevel = helmet == null
                    ? 0
                    : helmet.getEnchantmentLevel(Enchantment.RESPIRATION);
            double reduction = Math.min(1.0, respirationLevel * respirationReduction);
            int extraDrain = (int) Math.round(baseExtraDrain * (1.0 - reduction));
            extraDrain = Math.min(maxExtraDrain, Math.max(0, extraDrain));
            if (extraDrain > 0) {
                player.setRemainingAir(Math.max(
                        minimumAir,
                        player.getRemainingAir() - extraDrain));
            }
        }
    }

    private boolean isIgnoredGameMode(
            Player player,
            String creativePath,
            String spectatorPath,
            boolean creativeDefault,
            boolean spectatorDefault
    ) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return !configManager.getBoolean(creativePath, creativeDefault);
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return !configManager.getBoolean(spectatorPath, spectatorDefault);
        }
        return false;
    }

    private boolean appliesToWorld(World world, String path) {
        return !configManager.getBoolean(path, true)
                || world.getEnvironment() == World.Environment.NORMAL;
    }

    private int scaledTicks(double perSecond, long interval) {
        return Math.max(0, (int) Math.round(perSecond * interval / 20.0));
    }

    private String biomeName(Biome biome) {
        return biome.getKey().getKey().toLowerCase(Locale.ROOT);
    }

    private Vector randomHorizontalDirection() {
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
        return new Vector(Math.cos(angle), 0, Math.sin(angle));
    }

    private Particle parseParticle(String configured, Particle fallback) {
        try {
            return Particle.valueOf(configured.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private Sound parseSound(String configured, Sound fallback) {
        String normalized = configured.toLowerCase(Locale.ROOT);
        NamespacedKey key = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);
        if (key == null) {
            return fallback;
        }
        Sound sound = Registry.SOUNDS.get(key);
        return sound == null ? fallback : sound;
    }

    private void cleanupFreezeState() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.isFreezeTickingLocked()) {
                player.lockFreezeTicks(false);
            }
        }
    }

    private void reloadConfiguredSets() {
        fleeingEntityTypes = parseEntityTypes(configManager.getStringList("animal-flee.affected-entities"));
        freezeSafeItems = parseMaterials(configManager.getStringList("freeze.safe-held-items"));
        freezeArmor = parseMaterials(configManager.getStringList("freeze.protective-armor"));
        freezeBiomes = normalize(configManager.getStringList("freeze.biomes"));
        windBiomes = normalize(configManager.getStringList("wind.biomes"));
    }

    private Set<EntityType> parseEntityTypes(Iterable<String> values) {
        Set<EntityType> result = new HashSet<>();
        for (String value : values) {
            try {
                result.add(EntityType.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Unknown animal-flee entity type: " + value);
            }
        }
        return Set.copyOf(result);
    }

    private Set<Material> parseMaterials(Iterable<String> values) {
        Set<Material> result = new HashSet<>();
        for (String value : values) {
            Material material = Material.matchMaterial(value);
            if (material == null) {
                plugin.getLogger().warning("Unknown configured material: " + value);
            } else {
                result.add(material);
            }
        }
        return Set.copyOf(result);
    }

    private Set<String> normalize(Iterable<String> values) {
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            normalized.add(value.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }

    private void cancelTasks() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }
}
