package com.deep.civpressure.season;

import com.deep.civpressure.biome.BiomeGroup;
import com.deep.civpressure.biome.BiomeGroupRegistry;
import com.deep.civpressure.config.ConfigManager;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

public final class SeasonGrowthListener implements Listener {
    private final ConfigManager configManager;
    private final BiomeGroupRegistry biomeGroupRegistry;
    private final SeasonManager seasonManager;

    public SeasonGrowthListener(
            ConfigManager configManager,
            BiomeGroupRegistry biomeGroupRegistry,
            SeasonManager seasonManager
    ) {
        this.configManager = configManager;
        this.biomeGroupRegistry = biomeGroupRegistry;
        this.seasonManager = seasonManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (!configManager.isModuleEnabled("seasons")
                || !seasonManager.cropEffectsEnabled()
                || !seasonManager.appliesTo(event.getBlock().getWorld())
                || !seasonManager.isCrop(event.getBlock().getType())) {
            return;
        }

        BiomeGroup group = biomeGroupRegistry.resolve(
                event.getBlock().getBiome(),
                event.getBlock().getY());
        SeasonType season = seasonManager.getSeason(group);
        boolean coldBiome = seasonManager.isColdBiome(event.getBlock().getBiome());
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (season == SeasonType.DROUGHT
                && seasonManager.droughtCropEffectEnabled()
                && random.nextDouble() < seasonManager.droughtBlockChance(coldBiome)) {
            event.setCancelled(true);
            return;
        }
        if (season != SeasonType.WET
                || !seasonManager.wetCropEffectEnabled()
                || random.nextDouble() >= seasonManager.wetBonusChance(coldBiome)) {
            return;
        }

        BlockState newState = event.getNewState();
        BlockData blockData = newState.getBlockData();
        if (blockData instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
            ageable.setAge(ageable.getAge() + 1);
            newState.setBlockData(ageable);
        }
    }
}
