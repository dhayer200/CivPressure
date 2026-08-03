package com.deep.civpressure.mob;

import com.deep.civpressure.config.ConfigManager;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Stray;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;

public final class MobBuffListener implements Listener {
    private final ConfigManager configManager;
    private final MobBuffManager mobBuffManager;

    public MobBuffListener(ConfigManager configManager, MobBuffManager mobBuffManager) {
        this.configManager = configManager;
        this.mobBuffManager = mobBuffManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!configManager.isModuleEnabled("mob-buffs")) {
            return;
        }

        if (event.getEntity() instanceof Skeleton
                && mobBuffManager.shouldConvertSkeleton(event.getEntity(), event.getSpawnReason())) {
            event.setCancelled(true);
            event.getLocation().getWorld().spawn(
                    event.getLocation(),
                    Stray.class,
                    event.getSpawnReason(),
                    true,
                    stray -> {
                    });
            return;
        }

        mobBuffManager.applyBuff(
                event.getEntity(),
                configManager.getBoolean("mob-buffs.heal-newly-buffed-spawns", true));
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof org.bukkit.entity.LivingEntity livingEntity) {
                mobBuffManager.refreshEntity(livingEntity, false);
            }
        }
    }
}
