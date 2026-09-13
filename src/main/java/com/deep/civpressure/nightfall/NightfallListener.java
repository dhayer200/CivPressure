package com.deep.civpressure.nightfall;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Applies Nightfall escalation strength to hostiles that spawn at night. Runs
 * after {@code MobBuffManager} (which is {@code HIGHEST}) so the escalation
 * layers on top of the base mob-buff instead of being overwritten.
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
}
