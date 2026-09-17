package com.deep.civpressure.nightfall;

import com.deep.civpressure.CivPressurePlugin;
import com.deep.civpressure.config.ConfigManager;
import com.deep.civpressure.giant.GiantEventManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Runs the Nightfall module: nights get progressively more dangerous (up to a
 * configurable cap measured in the world's age in days) with stronger hostiles,
 * occasional hunting packs, and behind-the-scenes atmospheric sounds. The pure
 * scaling math lives in {@link NightfallEscalation}; this class only wires it to
 * the running server.
 */
public final class NightfallManager {
    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;
    private static final String STATE_FILE_NAME = "nightfall.dat";

    private final CivPressurePlugin plugin;
    private final ConfigManager configManager;
    private final GiantEventManager giantEventManager;
    private final NamespacedKey nightfallMobKey;
    private final NamespacedKey healthModifierKey;
    private final NamespacedKey damageModifierKey;
    private final NamespacedKey speedModifierKey;

    private BukkitTask tickTask;
    private List<WeightedType> siegeComposition = List.of();
    private List<EntityType> jockeyMounts = List.of();
    private List<JockeyRider> jockeyRiders = List.of();
    private List<String> sounds = List.of();
    private SoundCategory soundCategory = SoundCategory.MASTER;
    // Tracks the last calendar day a guaranteed siege ran for each player, so
    // each real night produces exactly one siege even if the Nightfall clock
    // is reset mid-night.
    private final Map<UUID, Long> lastSiegeDay = new HashMap<>();
    private final Map<UUID, Long> clockOffsets = new HashMap<>();
    private final File stateFile;

    public NightfallManager(
            CivPressurePlugin plugin,
            ConfigManager configManager,
            GiantEventManager giantEventManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.giantEventManager = giantEventManager;
        nightfallMobKey = new NamespacedKey(plugin, "nightfall_mob");
        healthModifierKey = new NamespacedKey(plugin, "nightfall_health");
        damageModifierKey = new NamespacedKey(plugin, "nightfall_damage");
        speedModifierKey = new NamespacedKey(plugin, "nightfall_speed");
        stateFile = new File(plugin.getDataFolder(), STATE_FILE_NAME);
    }

    public void start() {
        loadClock();
        reload();
    }

    public void stop() {
        cancelTask();
        saveClock();
    }

    public void reload() {
        cancelTask();
        reloadCachedSets();
        if (!isEnabled()) {
            if (configManager.getBoolean("nightfall.mob-strength.restore-when-disabled", true)) {
                removeBuffsFromLoadedEntities();
            }
            return;
        }
        long interval = Math.max(20L, configManager.getLong("nightfall.tick-interval-ticks", 200L));
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public boolean isEnabled() {
        return configManager.isModuleEnabled("nightfall");
    }

    /** Calendar days the world has existed (ignores the Nightfall offset). */
    public long worldDays(World world) {
        return NightfallEscalation.worldDays(world.getFullTime());
    }

    /**
     * Nightfall escalation day in this world. Admins can reset or jump this
     * without changing the world's actual age.
     */
    public long nightsSurvived(World world) {
        return NightfallEscalation.nightsSurvived(worldDays(world), clockOffset(world));
    }

    public long clockOffset(World world) {
        return clockOffsets.getOrDefault(world.getUID(), 0L);
    }

    /** Sets Nightfall to day 0 in {@code world}. The Minecraft calendar is unchanged. */
    public long resetNight(World world) {
        return setNight(world, 0L);
    }

    /**
     * Sets Nightfall to {@code targetNight} ({@code >= 0}) in {@code world}.
     * Returns the effective night after clamping.
     */
    public long setNight(World world, long targetNight) {
        long night = Math.max(0L, targetNight);
        clockOffsets.put(world.getUID(), NightfallEscalation.clockOffsetFor(worldDays(world), night));
        saveClock();
        return night;
    }

    public int capNights() {
        return configManager.getInt("nightfall.cap-nights", 50);
    }

    public double progress(World world) {
        return NightfallEscalation.progress(nightsSurvived(world), capNights());
    }

    public double currentHealthMultiplier(World world) {
        return NightfallEscalation.multiplier(
                progress(world),
                firstNightBonus(),
                configManager.getDouble("nightfall.mob-strength.max-health-bonus", 1.5));
    }

    public double currentDamageMultiplier(World world) {
        return NightfallEscalation.multiplier(
                progress(world),
                firstNightBonus(),
                configManager.getDouble("nightfall.mob-strength.max-damage-bonus", 0.5));
    }

    private double firstNightBonus() {
        return configManager.getDouble("nightfall.mob-strength.first-night-bonus", 0.15);
    }

    /**
     * Entry point for the spawn listener: buffs naturally spawned night hostiles
     * and (for genuinely natural spawns) can turn eligible mounts into jockeys.
     */
    public void handleCreatureSpawn(LivingEntity entity, CreatureSpawnEvent.SpawnReason reason) {
        buffNaturalSpawn(entity);
        maybeAddNaturalJockey(entity, reason);
        maybeBoostSpawnRate(entity, reason);
    }

    /**
     * Increases the effective night spawn rate: when a hostile spawns naturally
     * at night, there is a chance ({@code nightfall.spawn-rate.bonus}, default
     * 0.25) to spawn an extra one alongside it. Only fires for {@code NATURAL}
     * spawns so the extras it creates do not recurse.
     */
    private void maybeBoostSpawnRate(LivingEntity entity, CreatureSpawnEvent.SpawnReason reason) {
        if (!isEnabled()
                || reason != CreatureSpawnEvent.SpawnReason.NATURAL
                || !configManager.getBoolean("nightfall.spawn-rate.enabled", true)
                || !(entity instanceof Enemy)) {
            return;
        }
        World world = entity.getWorld();
        if (!isEligibleWorld(world) || !isNight(world)) {
            return;
        }
        double bonus = Math.max(0.0, configManager.getDouble("nightfall.spawn-rate.bonus", 0.25));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int extra = (int) Math.floor(bonus);
        if (random.nextDouble() < bonus - extra) {
            extra++;
        }
        double progress = progress(world);
        for (int i = 0; i < extra; i++) {
            Entity duplicate = world.spawnEntity(
                    entity.getLocation(), entity.getType(), CreatureSpawnEvent.SpawnReason.CUSTOM);
            if (duplicate instanceof LivingEntity living) {
                tagAndBuff(living, progress);
            } else {
                duplicate.remove();
            }
        }
    }

    /**
     * Applies escalation buffs to a naturally spawned hostile. Safe to call for
     * any entity; no-ops for non-hostiles, off-hours, or ineligible worlds.
     */
    public void buffNaturalSpawn(LivingEntity entity) {
        if (!isEnabled()
                || !configManager.getBoolean("nightfall.mob-strength.affect-natural-spawns", true)) {
            return;
        }
        World world = entity.getWorld();
        if (!isEligibleWorld(world) || !isNight(world) || !(entity instanceof Enemy)) {
            return;
        }
        applyStrength(entity, progress(world), true);
    }

    /**
     * When a spider or chicken spawns naturally at night, there is a chance to
     * saddle it with a rider (skeleton or baby zombie), boosting jockey numbers
     * server-wide. Only fires for {@code NATURAL} spawns so it never recurses on
     * the riders/mounts this module spawns itself.
     */
    private void maybeAddNaturalJockey(LivingEntity entity, CreatureSpawnEvent.SpawnReason reason) {
        if (!isEnabled()
                || reason != CreatureSpawnEvent.SpawnReason.NATURAL
                || !configManager.getBoolean("nightfall.jockeys.natural-boost-enabled", true)
                || jockeyRiders.isEmpty()) {
            return;
        }
        World world = entity.getWorld();
        if (!isEligibleWorld(world) || !isNight(world) || !entity.getPassengers().isEmpty()) {
            return;
        }
        double chance;
        if (entity instanceof Spider) {
            chance = configManager.getDouble("nightfall.jockeys.spider-rider-chance", 0.3);
        } else if (entity instanceof Chicken) {
            chance = configManager.getDouble("nightfall.jockeys.chicken-rider-chance", 0.5);
        } else {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        mountJockeyRider(entity, world, progress(world));
    }

    private void tick() {
        if (!isEnabled()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            World world = player.getWorld();
            if (!isEligibleWorld(world) || !isEligiblePlayer(player) || !isNight(world)) {
                continue;
            }
            double progress = progress(world);
            maybePlaySound(player, world, progress);
            maybeRunSiege(player, world, progress);
        }
    }

    private void maybePlaySound(Player player, World world, double progress) {
        if (sounds.isEmpty() || !configManager.getBoolean("nightfall.sounds.enabled", true)) {
            return;
        }
        double curve = configManager.getBoolean("nightfall.sounds.night-curve", true)
                ? NightfallEscalation.nightCurve(world.getTime())
                : 1.0;
        double chance = NightfallEscalation.chance(
                progress,
                configManager.getDouble("nightfall.sounds.min-chance-per-cycle", 0.25),
                configManager.getDouble("nightfall.sounds.max-chance-per-cycle", 0.85)) * curve;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() >= chance) {
            return;
        }

        String sound = sounds.get(random.nextInt(sounds.size()));
        double distance = Math.max(0.0, configManager.getDouble("nightfall.sounds.distance", 8.0));
        double angle = random.nextDouble(Math.PI * 2.0);
        Location at = player.getLocation().add(
                Math.cos(angle) * distance,
                random.nextDouble(-2.0, 3.0),
                Math.sin(angle) * distance);
        float volume = (float) Math.max(0.0, configManager.getDouble("nightfall.sounds.volume", 0.9));
        float pitchMin = (float) configManager.getDouble("nightfall.sounds.pitch-min", 0.6);
        float pitchMax = (float) configManager.getDouble("nightfall.sounds.pitch-max", 1.0);
        float pitch = pitchMax <= pitchMin ? pitchMin : (float) random.nextDouble(pitchMin, pitchMax);
        // Played under the configured category (MASTER by default) so it ignores
        // the player's per-category volume sliders.
        player.playSound(at, sound, soundCategory, volume, pitch);
    }

    /**
     * Runs at most one guaranteed siege per player per world-night. Unlike a
     * vanilla zombie siege (a nightly chance), Nightfall's siege is guaranteed
     * once the player is out at night.
     */
    private void maybeRunSiege(Player player, World world, double progress) {
        if (!configManager.getBoolean("nightfall.spawns.enabled", true)) {
            return;
        }
        long day = worldDays(world);
        Long last = lastSiegeDay.get(player.getUniqueId());
        if (last != null && last == day) {
            return;
        }
        lastSiegeDay.put(player.getUniqueId(), day);
        spawnSiege(player, world, progress);
    }

    /**
     * Spawns a diversified siege near the player: weighted "infantry" plus a
     * fraction of jockey "cavalry", scaled by escalation. A giant may also join
     * once past the configured night.
     */
    private void spawnSiege(Player player, World world, double progress) {
        int maxNearby = Math.max(0, configManager.getInt("nightfall.spawns.max-nearby-per-player", 24));
        if (countNearbyNightfallMobs(player) >= maxNearby) {
            return;
        }
        int size = NightfallEscalation.packSize(
                progress,
                configManager.getInt("nightfall.spawns.min-pack-size", 4),
                configManager.getInt("nightfall.spawns.max-pack-size", 12));
        if (size <= 0) {
            return;
        }
        double fraction = Math.max(0.0, Math.min(1.0,
                configManager.getDouble("nightfall.spawns.jockey-fraction", 0.35)));
        // Guarantee at least one jockey per siege (when jockeys are configured).
        boolean jockeysAvailable = !jockeyMounts.isEmpty() && !jockeyRiders.isEmpty();
        int jockeyCount = (int) Math.round(size * fraction);
        if (jockeysAvailable) {
            jockeyCount = Math.max(1, jockeyCount);
        }
        jockeyCount = Math.min(jockeyCount, size);
        int infantryCount = Math.max(0, size - jockeyCount);
        boolean targetPlayer = configManager.getBoolean("nightfall.spawns.target-player", true);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < infantryCount; i++) {
            Location location = findSpawnNear(player, world);
            if (location != null) {
                spawnHostile(world, location, pickInfantry(random), progress, targetPlayer, player);
            }
        }
        for (int i = 0; i < jockeyCount; i++) {
            Location location = findSpawnNear(player, world);
            if (location != null) {
                spawnJockey(world, location, progress, targetPlayer, player);
            }
        }
        maybeSpawnGiantWithPack(player, world);
    }

    private LivingEntity spawnHostile(
            World world,
            Location location,
            EntityType type,
            double progress,
            boolean targetPlayer,
            Player player
    ) {
        Entity spawned = world.spawnEntity(location, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (!(spawned instanceof LivingEntity living)) {
            spawned.remove();
            return null;
        }
        tagAndBuff(living, progress);
        if (targetPlayer && living instanceof Mob mob) {
            mob.setTarget(player);
        }
        return living;
    }

    private void spawnJockey(
            World world,
            Location location,
            double progress,
            boolean targetPlayer,
            Player player
    ) {
        if (jockeyMounts.isEmpty() || jockeyRiders.isEmpty()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        EntityType mountType = jockeyMounts.get(random.nextInt(jockeyMounts.size()));
        Entity mountEntity = world.spawnEntity(location, mountType, CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (!(mountEntity instanceof LivingEntity mount)) {
            mountEntity.remove();
            return;
        }
        LivingEntity rider = mountJockeyRider(mount, world, progress);
        if (rider == null) {
            return;
        }
        if (targetPlayer) {
            if (rider instanceof Mob riderMob) {
                riderMob.setTarget(player);
            }
            if (mount instanceof Mob mountMob && mount instanceof Enemy) {
                mountMob.setTarget(player);
            }
        }
    }

    /**
     * Spawns a rider (skeleton or baby zombie) on an existing mount and tags/buffs
     * both. Returns the rider, or {@code null} if it could not be created.
     */
    private LivingEntity mountJockeyRider(LivingEntity mount, World world, double progress) {
        if (jockeyRiders.isEmpty()) {
            return null;
        }
        JockeyRider spec = jockeyRiders.get(ThreadLocalRandom.current().nextInt(jockeyRiders.size()));
        Entity riderEntity = world.spawnEntity(
                mount.getLocation(), spec.type(), CreatureSpawnEvent.SpawnReason.JOCKEY);
        if (!(riderEntity instanceof LivingEntity rider)) {
            riderEntity.remove();
            return null;
        }
        if (spec.baby() && rider instanceof Zombie zombie) {
            zombie.setBaby();
        }
        mount.addPassenger(rider);
        if (mount instanceof Enemy) {
            tagAndBuff(mount, progress);
        } else {
            tagOnly(mount);
        }
        tagAndBuff(rider, progress);
        return rider;
    }

    private void tagOnly(LivingEntity entity) {
        entity.getPersistentDataContainer().set(nightfallMobKey, PersistentDataType.BYTE, (byte) 1);
        entity.setRemoveWhenFarAway(true);
    }

    private void tagAndBuff(LivingEntity entity, double progress) {
        tagOnly(entity);
        applyStrength(entity, progress, true);
    }

    private EntityType pickInfantry(ThreadLocalRandom random) {
        int total = 0;
        for (WeightedType weighted : siegeComposition) {
            total += weighted.weight();
        }
        if (total <= 0) {
            return EntityType.ZOMBIE;
        }
        int roll = random.nextInt(total);
        for (WeightedType weighted : siegeComposition) {
            roll -= weighted.weight();
            if (roll < 0) {
                return weighted.type();
            }
        }
        return siegeComposition.get(siegeComposition.size() - 1).type();
    }

    private void maybeSpawnGiantWithPack(Player player, World world) {
        if (!configManager.getBoolean("nightfall.giant.enabled", true)) {
            return;
        }
        if (nightsSurvived(world) < configManager.getLong("nightfall.giant.first-night", 15L)) {
            return;
        }
        int maxPerWorld = Math.max(0, configManager.getInt("nightfall.giant.max-per-world", 1));
        if (giantEventManager.count(world) >= maxPerWorld) {
            return;
        }
        double chance = Math.max(0.0, Math.min(1.0,
                configManager.getDouble("nightfall.giant.chance-per-pack", 0.15)));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        double min = Math.max(1.0, configManager.getDouble("nightfall.spawns.min-distance", 16.0));
        double max = Math.max(min, configManager.getDouble("nightfall.spawns.max-distance", 40.0));
        giantEventManager.spawnManagedGiantNear(
                player, min, max, configManager.getBoolean("nightfall.giant.announce", false));
    }

    private Location findSpawnNear(Player player, World world) {
        double min = Math.max(1.0, configManager.getDouble("nightfall.spawns.min-distance", 16.0));
        double max = Math.max(min, configManager.getDouble("nightfall.spawns.max-distance", 40.0));
        int attempts = Math.max(1, configManager.getInt("nightfall.spawns.location-attempts", 12));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double distance = random.nextDouble(min, max + 0.01);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            if (y <= world.getMinHeight() || y + 2 >= world.getMaxHeight()) {
                continue;
            }
            Location location = new Location(world, x + 0.5, y, z + 0.5);
            if (!location.getBlock().getRelative(0, -1, 0).getType().isSolid()) {
                continue;
            }
            if (location.getBlock().isPassable() && location.getBlock().getRelative(0, 1, 0).isPassable()) {
                return location;
            }
        }
        return null;
    }

    private int countNearbyNightfallMobs(Player player) {
        double radius = Math.max(1.0, configManager.getDouble("nightfall.spawns.nearby-radius", 48.0));
        int count = 0;
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius)) {
            if (entity instanceof LivingEntity living
                    && living.getPersistentDataContainer().has(nightfallMobKey, PersistentDataType.BYTE)) {
                count++;
            }
        }
        return count;
    }

    private void applyStrength(LivingEntity entity, double progress, boolean healToFull) {
        double firstNight = firstNightBonus();
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            applyScalar(health, healthModifierKey, NightfallEscalation.multiplier(
                    progress,
                    firstNight,
                    configManager.getDouble("nightfall.mob-strength.max-health-bonus", 1.5)));
            if (healToFull) {
                entity.setHealth(health.getValue());
            }
        }
        AttributeInstance damage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            applyScalar(damage, damageModifierKey, NightfallEscalation.multiplier(
                    progress,
                    firstNight,
                    configManager.getDouble("nightfall.mob-strength.max-damage-bonus", 0.5)));
        }
        // Only zombies get faster; everything else keeps vanilla speed. Speed has
        // no first-night floor (it is excluded from the baseline buff).
        AttributeInstance speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            if (entity instanceof Zombie) {
                applyScalar(speed, speedModifierKey, NightfallEscalation.multiplier(
                        progress,
                        0.0,
                        configManager.getDouble("nightfall.mob-strength.max-speed-bonus", 0.15)));
            } else {
                removeModifier(speed, speedModifierKey);
            }
        }
        // Make every night zombie a door-breaker (vanilla only gives ~10% of
        // zombies this, and only on Hard). Actual breaking still requires Hard
        // difficulty and the mobGriefing gamerule; Drowned ignore this.
        if (entity instanceof Zombie zombie
                && configManager.getBoolean("nightfall.mob-strength.zombies-break-doors", true)) {
            zombie.setCanBreakDoors(true);
        }
    }

    private void applyScalar(AttributeInstance instance, NamespacedKey key, double multiplier) {
        removeModifier(instance, key);
        double amount = multiplier - 1.0;
        if (Math.abs(amount) < 1.0e-6) {
            return;
        }
        instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void removeModifier(AttributeInstance instance, NamespacedKey key) {
        for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
            if (key.equals(modifier.getKey())) {
                instance.removeModifier(modifier);
            }
        }
    }

    private void removeBuffsFromLoadedEntities() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof LivingEntity living) {
                        restore(living);
                    }
                }
            }
        }
    }

    private void restore(LivingEntity entity) {
        for (Attribute attribute : new Attribute[] {
                Attribute.MAX_HEALTH, Attribute.ATTACK_DAMAGE, Attribute.MOVEMENT_SPEED}) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) {
                removeModifier(instance, healthModifierKey);
                removeModifier(instance, damageModifierKey);
                removeModifier(instance, speedModifierKey);
            }
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.remove(nightfallMobKey);
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null && entity.getHealth() > health.getValue()) {
            entity.setHealth(health.getValue());
        }
    }

    private boolean isEligibleWorld(World world) {
        return !configManager.getBoolean("nightfall.overworld-only", true)
                || world.getEnvironment() == World.Environment.NORMAL;
    }

    private boolean isEligiblePlayer(Player player) {
        return player.isOnline()
                && !player.isDead()
                && (player.getGameMode() == GameMode.SURVIVAL
                || player.getGameMode() == GameMode.ADVENTURE);
    }

    private boolean isNight(World world) {
        long time = world.getTime();
        return time >= NIGHT_START && time <= NIGHT_END;
    }

    private void cancelTask() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void reloadCachedSets() {
        siegeComposition = parseComposition();
        jockeyMounts = parseEntityTypes(configManager.getStringList("nightfall.jockeys.mounts"));
        jockeyRiders = parseRiders(configManager.getStringList("nightfall.jockeys.riders"));
        sounds = List.copyOf(configManager.getStringList("nightfall.sounds.list"));
        soundCategory = parseSoundCategory(configManager.getString("nightfall.sounds.category", "master"));
    }

    private List<WeightedType> parseComposition() {
        List<WeightedType> composition = new ArrayList<>();
        for (Map.Entry<String, Integer> entry
                : configManager.getIntMap("nightfall.spawns.composition").entrySet()) {
            EntityType type = parseLivingType(entry.getKey());
            int weight = Math.max(0, entry.getValue());
            if (type != null && weight > 0) {
                composition.add(new WeightedType(type, weight));
            }
        }
        if (composition.isEmpty()) {
            composition.add(new WeightedType(EntityType.ZOMBIE, 1));
        }
        return List.copyOf(composition);
    }

    private List<JockeyRider> parseRiders(Iterable<String> values) {
        List<JockeyRider> riders = new ArrayList<>();
        for (String value : values) {
            String key = value.toLowerCase(Locale.ROOT).trim();
            if (key.equals("baby_zombie") || key.equals("baby-zombie")) {
                riders.add(new JockeyRider(EntityType.ZOMBIE, true));
                continue;
            }
            EntityType type = parseLivingType(key);
            if (type != null) {
                riders.add(new JockeyRider(type, false));
            }
        }
        if (riders.isEmpty()) {
            riders.add(new JockeyRider(EntityType.SKELETON, false));
        }
        return List.copyOf(riders);
    }

    private List<EntityType> parseEntityTypes(Iterable<String> values) {
        List<EntityType> types = new ArrayList<>();
        for (String value : values) {
            EntityType type = parseLivingType(value);
            if (type != null) {
                types.add(type);
            }
        }
        return List.copyOf(types);
    }

    private EntityType parseLivingType(String value) {
        try {
            EntityType type = EntityType.valueOf(value.toUpperCase(Locale.ROOT));
            if (type.getEntityClass() != null && LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
                return type;
            }
            plugin.getLogger().warning("Nightfall entity type is not a living entity: " + value);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Unknown Nightfall entity type: " + value);
        }
        return null;
    }

    private record WeightedType(EntityType type, int weight) {
    }

    private record JockeyRider(EntityType type, boolean baby) {
    }

    private void loadClock() {
        clockOffsets.clear();
        if (!stateFile.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(stateFile);
        ConfigurationSection worlds = data.getConfigurationSection("worlds");
        if (worlds == null) {
            return;
        }
        for (String key : worlds.getKeys(false)) {
            try {
                UUID worldId = UUID.fromString(key);
                long offset = worlds.getLong(key + ".offset", worlds.getLong(key, 0L));
                clockOffsets.put(worldId, offset);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring malformed Nightfall clock entry: " + key);
            }
        }
    }

    private void saveClock() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : clockOffsets.entrySet()) {
            String path = "worlds." + entry.getKey();
            data.set(path + ".offset", entry.getValue());
            World world = plugin.getServer().getWorld(entry.getKey());
            if (world != null) {
                data.set(path + ".name", world.getName());
            }
        }
        try {
            data.save(stateFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + STATE_FILE_NAME, exception);
        }
    }

    private SoundCategory parseSoundCategory(String value) {
        try {
            return SoundCategory.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Unknown Nightfall sound category '" + value + "', using MASTER.");
            return SoundCategory.MASTER;
        }
    }
}
