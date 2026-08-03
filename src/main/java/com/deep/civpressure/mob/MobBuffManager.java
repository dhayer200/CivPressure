package com.deep.civpressure.mob;

import com.deep.civpressure.CivPressurePlugin;
import com.deep.civpressure.config.ConfigManager;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Giant;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class MobBuffManager {
    private final CivPressurePlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey originalMaxHealthKey;
    private final NamespacedKey originalKnockbackResistanceKey;
    private final NamespacedKey buffedKey;

    private Set<String> coldBiomes = Set.of();
    private Set<CreatureSpawnEvent.SpawnReason> straySpawnReasons = Set.of();

    public MobBuffManager(CivPressurePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        originalMaxHealthKey = new NamespacedKey(plugin, "original_max_health");
        originalKnockbackResistanceKey = new NamespacedKey(plugin, "original_knockback_resistance");
        buffedKey = new NamespacedKey(plugin, "mob_buffed");
    }

    public void start() {
        reloadConfiguredSets();
        refreshLoadedEntities();
    }

    public void reload() {
        reloadConfiguredSets();
        refreshLoadedEntities();
    }

    public void applyBuff(LivingEntity entity, boolean healNewlyBuffed) {
        if (!configManager.isModuleEnabled("mob-buffs")) {
            return;
        }

        double multiplier = healthMultiplier(entity);
        if (multiplier <= 0.0) {
            restoreBuff(entity);
            return;
        }

        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        PersistentDataContainer data = entity.getPersistentDataContainer();
        boolean firstBuff = !data.has(buffedKey, PersistentDataType.BOOLEAN);
        double originalMaxHealth = data.getOrDefault(
                originalMaxHealthKey,
                PersistentDataType.DOUBLE,
                maxHealth.getBaseValue());
        data.set(originalMaxHealthKey, PersistentDataType.DOUBLE, originalMaxHealth);
        data.set(buffedKey, PersistentDataType.BOOLEAN, true);

        double oldMaxHealth = maxHealth.getValue();
        double healthRatio = oldMaxHealth <= 0.0 ? 1.0 : entity.getHealth() / oldMaxHealth;
        maxHealth.setBaseValue(Math.max(1.0, originalMaxHealth * multiplier));
        if (firstBuff && healNewlyBuffed) {
            entity.setHealth(maxHealth.getValue());
        } else {
            entity.setHealth(Math.max(0.01, Math.min(maxHealth.getValue(), maxHealth.getValue() * healthRatio)));
        }

        if (entity instanceof IronGolem) {
            applyGolemKnockbackResistance(entity, data);
        }
    }

    public void refreshEntity(LivingEntity entity, boolean healNewlyBuffed) {
        if (configManager.isModuleEnabled("mob-buffs")) {
            applyBuff(entity, healNewlyBuffed);
        } else if (configManager.getBoolean(
                "mob-buffs.restore-loaded-entities-when-disabled",
                true)) {
            restoreBuff(entity);
        }
    }

    public boolean shouldConvertSkeleton(
            LivingEntity entity,
            CreatureSpawnEvent.SpawnReason spawnReason
    ) {
        return configManager.isModuleEnabled("mob-buffs")
                && configManager.getBoolean("mob-buffs.cold-skeletons-as-strays", true)
                && straySpawnReasons.contains(spawnReason)
                && coldBiomes.contains(entity.getLocation().getBlock().getBiome()
                .getKey().getKey().toLowerCase(Locale.ROOT));
    }

    private void applyGolemKnockbackResistance(
            LivingEntity entity,
            PersistentDataContainer data
    ) {
        AttributeInstance knockbackResistance = entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackResistance == null) {
            return;
        }

        double original = data.getOrDefault(
                originalKnockbackResistanceKey,
                PersistentDataType.DOUBLE,
                knockbackResistance.getBaseValue());
        data.set(originalKnockbackResistanceKey, PersistentDataType.DOUBLE, original);
        double configured = Math.max(
                0.0,
                Math.min(1.0, configManager.getDouble(
                        "mob-buffs.golem-knockback-resistance",
                        1.0)));
        knockbackResistance.setBaseValue(configured);
    }

    private double healthMultiplier(LivingEntity entity) {
        if (entity instanceof Giant) {
            return 0.0;
        }
        if (entity instanceof IronGolem) {
            return Math.max(0.0, configManager.getDouble(
                    "mob-buffs.golem-health-multiplier",
                    2.0));
        }
        if (entity instanceof Enemy) {
            return Math.max(0.0, configManager.getDouble(
                    "mob-buffs.hostile-health-multiplier",
                    1.5));
        }
        if (entity instanceof Mob) {
            return Math.max(0.0, configManager.getDouble(
                    "mob-buffs.passive-health-multiplier",
                    2.25));
        }
        return 0.0;
    }

    private void applyToLoadedEntities() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
                    if (entity instanceof LivingEntity livingEntity) {
                        refreshEntity(livingEntity, false);
                    }
                }
            }
        }
    }

    private void restoreLoadedEntities() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
                    if (entity instanceof LivingEntity livingEntity) {
                        restoreBuff(livingEntity);
                    }
                }
            }
        }
    }

    private void restoreBuff(LivingEntity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        Double originalMaxHealth = data.get(originalMaxHealthKey, PersistentDataType.DOUBLE);
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (originalMaxHealth != null && maxHealth != null) {
            double oldMaxHealth = maxHealth.getValue();
            double healthRatio = oldMaxHealth <= 0.0 ? 1.0 : entity.getHealth() / oldMaxHealth;
            maxHealth.setBaseValue(Math.max(1.0, originalMaxHealth));
            entity.setHealth(Math.max(0.01, Math.min(
                    maxHealth.getValue(),
                    maxHealth.getValue() * healthRatio)));
        }

        Double originalKnockback = data.get(
                originalKnockbackResistanceKey,
                PersistentDataType.DOUBLE);
        AttributeInstance knockbackResistance = entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (originalKnockback != null && knockbackResistance != null) {
            knockbackResistance.setBaseValue(originalKnockback);
        }

        data.remove(originalMaxHealthKey);
        data.remove(originalKnockbackResistanceKey);
        data.remove(buffedKey);
    }

    private void refreshLoadedEntities() {
        if (configManager.isModuleEnabled("mob-buffs")) {
            if (configManager.getBoolean("mob-buffs.buff-existing-loaded-entities", true)) {
                applyToLoadedEntities();
            }
        } else if (configManager.getBoolean(
                "mob-buffs.restore-loaded-entities-when-disabled",
                true)) {
            restoreLoadedEntities();
        }
    }

    private void reloadConfiguredSets() {
        coldBiomes = normalize(configManager.getStringList("mob-buffs.cold-biomes"));
        straySpawnReasons = parseSpawnReasons(
                configManager.getStringList("mob-buffs.stray-spawn-reasons"));
    }

    private Set<String> normalize(Iterable<String> values) {
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            normalized.add(value.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }

    private Set<CreatureSpawnEvent.SpawnReason> parseSpawnReasons(Iterable<String> values) {
        Set<CreatureSpawnEvent.SpawnReason> reasons = new HashSet<>();
        for (String value : values) {
            try {
                reasons.add(CreatureSpawnEvent.SpawnReason.valueOf(
                        value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Unknown stray spawn reason: " + value);
            }
        }
        return Set.copyOf(reasons);
    }
}
