package com.deep.civpressure.season;

import com.deep.civpressure.biome.BiomeGroup;
import com.deep.civpressure.biome.BiomeGroupRegistry;
import com.deep.civpressure.config.ConfigManager;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.MoistureChangeEvent;

public final class SeasonFarmlandListener implements Listener {
    private final ConfigManager configManager;
    private final BiomeGroupRegistry biomeGroupRegistry;
    private final SeasonManager seasonManager;

    public SeasonFarmlandListener(
            ConfigManager configManager,
            BiomeGroupRegistry biomeGroupRegistry,
            SeasonManager seasonManager
    ) {
        this.configManager = configManager;
        this.biomeGroupRegistry = biomeGroupRegistry;
        this.seasonManager = seasonManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMoistureChange(MoistureChangeEvent event) {
        if (!configManager.isModuleEnabled("seasons")
                || !seasonManager.appliesTo(event.getBlock().getWorld())) {
            return;
        }

        BiomeGroup group = biomeGroupRegistry.resolve(
                event.getBlock().getBiome(),
                event.getBlock().getY());
        SeasonType season = seasonManager.getSeason(group);
        if (!seasonManager.farmlandEffectEnabled(season)) {
            return;
        }

        BlockState newState = event.getNewState();
        BlockData blockData = newState.getBlockData();
        if (blockData instanceof Farmland farmland) {
            farmland.setMoisture(seasonManager.targetFarmlandMoisture(farmland, season));
            newState.setBlockData(farmland);
        }
    }
}
