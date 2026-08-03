package com.deep.civpressure.wildlife;

import com.deep.civpressure.config.ConfigManager;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.PolarBear;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class MountainPolarBearListener implements Listener {
    private final ConfigManager configManager;

    public MountainPolarBearListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPassiveSpawn(CreatureSpawnEvent event) {
        if (!configManager.isModuleEnabled("mountain-polar-bears")
                || !(event.getEntity() instanceof Animals)
                || event.getEntity() instanceof PolarBear
                || !isAllowedSpawnReason(event.getSpawnReason())) {
            return;
        }

        World world = event.getLocation().getWorld();
        if (configManager.getBoolean("mountain-polar-bears.overworld-only", true)
                && world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }

        int minimumY = configManager.getInt("mountain-polar-bears.min-y", 90);
        if (event.getLocation().getBlockY() < minimumY || !isMountainBiome(event)) {
            return;
        }

        double chance = Math.max(
                0.0,
                Math.min(1.0, configManager.getDouble(
                        "mountain-polar-bears.spawn-chance",
                        0.10)));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }

        double nearbyRadius = Math.max(
                1.0,
                configManager.getDouble("mountain-polar-bears.nearby-radius", 64.0));
        int nearbyCap = Math.max(
                0,
                configManager.getInt("mountain-polar-bears.max-nearby", 2));
        if (world.getNearbyEntitiesByType(
                PolarBear.class,
                event.getLocation(),
                nearbyRadius).size() >= nearbyCap) {
            return;
        }

        world.spawn(
                event.getLocation(),
                PolarBear.class,
                CreatureSpawnEvent.SpawnReason.CUSTOM,
                true,
                polarBear -> {
                });
    }

    private boolean isAllowedSpawnReason(CreatureSpawnEvent.SpawnReason spawnReason) {
        for (String configured : configManager.getStringList(
                "mountain-polar-bears.spawn-reasons")) {
            if (spawnReason.name().equalsIgnoreCase(configured)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMountainBiome(CreatureSpawnEvent event) {
        String biomeName = event.getLocation()
                .getBlock()
                .getBiome()
                .getKey()
                .getKey()
                .toLowerCase(Locale.ROOT);
        Set<String> configuredBiomes = new HashSet<>();
        for (String configured : configManager.getStringList(
                "mountain-polar-bears.biomes")) {
            configuredBiomes.add(configured.toLowerCase(Locale.ROOT));
        }
        return configuredBiomes.contains(biomeName);
    }
}
