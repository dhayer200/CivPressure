package com.deep.civpressure.nightfall;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.weather.LightningStrikeEvent;

/**
 * Applies Nightfall escalation to night hostiles and widens the "monsters
 * too close to sleep" check.
 */
public final class NightfallListener implements Listener {
    private final NightfallManager nightfallManager;

    public NightfallListener(NightfallManager nightfallManager) {
        this.nightfallManager = nightfallManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            nightfallManager.handleCreatureSpawn(living, event.getSpawnReason());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        nightfallManager.handleBedEnter(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightning(LightningStrikeEvent event) {
        nightfallManager.handleLightning(event);
    }
}
