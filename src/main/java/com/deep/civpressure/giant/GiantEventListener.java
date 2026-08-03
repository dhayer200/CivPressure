package com.deep.civpressure.giant;

import org.bukkit.entity.Giant;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;

public final class GiantEventListener implements Listener {
    private final GiantEventManager giantEventManager;

    public GiantEventListener(GiantEventManager giantEventManager) {
        this.giantEventManager = giantEventManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Giant giant)
                || !giantEventManager.isEventGiant(giant)) {
            return;
        }
        giantEventManager.populateDrops(giant, event.getDrops());
        event.setDroppedExp(giantEventManager.getDroppedExperience());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (!giantEventManager.isBlockDamageEnabled()
                && event.getEntity() instanceof Giant giant
                && giantEventManager.isEventGiant(giant)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!giantEventManager.isBlockDamageEnabled()
                && event.getEntity() instanceof Giant giant
                && giantEventManager.isEventGiant(giant)) {
            event.blockList().clear();
            event.setYield(0.0F);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Giant giant) {
                giantEventManager.configureIfManaged(giant);
            }
        }
    }
}
