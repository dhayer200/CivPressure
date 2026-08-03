package com.deep.civpressure.survival;

import com.deep.civpressure.config.ConfigManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ClockTimeSkipEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class SurvivalEventListener implements Listener {
    private final ConfigManager configManager;
    private final Map<UUID, Integer> regenCounters = new HashMap<>();
    private final Set<UUID> successfulSleepers = new HashSet<>();

    public SurvivalEventListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!configManager.isModuleEnabled("fall-damage")
                || event.getCause() != EntityDamageEvent.DamageCause.FALL
                || (configManager.getBoolean("fall-damage.players-only", true)
                && !(event.getEntity() instanceof Player))) {
            return;
        }

        double multiplier = Math.max(0.0, configManager.getDouble("fall-damage.multiplier", 1.5));
        event.setDamage(event.getDamage() * multiplier);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNaturalRegeneration(EntityRegainHealthEvent event) {
        if (!configManager.isModuleEnabled("slow-regen")
                || event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED
                || !(event.getEntity() instanceof Player player)) {
            return;
        }

        int slowFactor = Math.max(1, configManager.getInt("slow-regen.slow-factor", 4));
        int nextCount = regenCounters.getOrDefault(player.getUniqueId(), 0) + 1;
        if (nextCount < slowFactor) {
            regenCounters.put(player.getUniqueId(), nextCount);
            event.setCancelled(true);
        } else {
            regenCounters.put(player.getUniqueId(), 0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onNightSkip(TimeSkipEvent event) {
        if (!configManager.isModuleEnabled("slow-regen")
                || !configManager.getBoolean("slow-regen.sleep-regen-enabled", true)
                || event.getSkipReason() != ClockTimeSkipEvent.SkipReason.NIGHT_SKIP) {
            return;
        }

        event.getWorld().getPlayers().stream()
                .filter(Player::isDeeplySleeping)
                .map(Player::getUniqueId)
                .forEach(successfulSleepers::add);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedLeave(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        if (!successfulSleepers.remove(player.getUniqueId())) {
            return;
        }

        int duration = Math.max(
                1,
                configManager.getInt("slow-regen.sleep-regen-duration-ticks", 2400));
        int amplifier = Math.max(
                0,
                configManager.getInt("slow-regen.sleep-regen-amplifier", 0));
        PotionEffect existing = player.getPotionEffect(PotionEffectType.REGENERATION);
        if (existing != null && existing.getAmplifier() > amplifier) {
            return;
        }
        if (existing != null && existing.getAmplifier() == amplifier) {
            duration = Math.max(duration, existing.getDuration());
        }

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.REGENERATION,
                duration,
                amplifier,
                configManager.getBoolean("slow-regen.sleep-regen-ambient", false),
                configManager.getBoolean("slow-regen.sleep-regen-particles", true),
                configManager.getBoolean("slow-regen.sleep-regen-icon", true)),
                true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        regenCounters.remove(playerId);
        successfulSleepers.remove(playerId);
    }
}
