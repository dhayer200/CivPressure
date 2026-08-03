package com.deep.civpressure.ore;

import com.deep.civpressure.CivPressurePlugin;
import com.deep.civpressure.config.ConfigManager;
import java.util.logging.Level;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;

public final class OrePopulateListener implements Listener {
    private final CivPressurePlugin plugin;
    private final ConfigManager configManager;
    private final OreRedistributor oreRedistributor;

    public OrePopulateListener(
            CivPressurePlugin plugin,
            ConfigManager configManager,
            OreRedistributor oreRedistributor
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.oreRedistributor = oreRedistributor;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkPopulate(ChunkPopulateEvent event) {
        if (!configManager.isModuleEnabled("ore-redistribution")) {
            return;
        }
        if (oreRedistributor.isAutomaticProcessingSuppressed(event.getChunk())) {
            return;
        }

        OreRedistributionResult result = oreRedistributor.process(event.getChunk(), false);
        if (result.status() == OreRedistributionResult.Status.PERSISTENCE_FAILED) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Ore redistribution changed chunk {0},{1} but could not persist its processed state.",
                    new Object[] {event.getChunk().getX(), event.getChunk().getZ()});
        }
    }
}
