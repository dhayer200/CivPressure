package com.deep.civpressure.durability;

import com.deep.civpressure.config.ConfigManager;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class DurabilityPressureListener implements Listener {
    private final ConfigManager configManager;
    private final DurabilityClassifier classifier;

    public DurabilityPressureListener(ConfigManager configManager) {
        this.configManager = configManager;
        classifier = new DurabilityClassifier();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (!configManager.isModuleEnabled("durability-pressure")) {
            return;
        }

        if (event.getPlayer().getGameMode() == GameMode.CREATIVE
                && !configManager.getBoolean("durability-pressure.affect-creative", false)) {
            return;
        }
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR
                && !configManager.getBoolean("durability-pressure.affect-spectator", false)) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !item.getType().isItem() || item.getType().getMaxDurability() <= 0) {
            return;
        }

        if (!configManager.getBoolean("durability-pressure.include-mending-items", true)
                && hasMending(item)) {
            return;
        }

        DurabilityCategory category = classifier.classify(item);
        if (category == DurabilityCategory.OTHER) {
            return;
        }

        double multiplier = multiplierFor(category);
        if (multiplier <= 1.0) {
            return;
        }

        int vanillaDamage = Math.max(0, event.getDamage());
        int bonusDamage = (int) Math.ceil(vanillaDamage * (multiplier - 1.0));
        int maxExtra = Math.max(0, configManager.getInt("durability-pressure.max-extra-damage-per-event", 4));
        bonusDamage = Math.min(maxExtra, bonusDamage);
        if (bonusDamage <= 0) {
            return;
        }

        event.setDamage(vanillaDamage + bonusDamage);
    }

    private double multiplierFor(DurabilityCategory category) {
        return switch (category) {
            case TOOLS -> configManager.getDouble("durability-pressure.tools-multiplier", 1.5);
            case WEAPONS -> configManager.getDouble("durability-pressure.weapons-multiplier", 1.35);
            case ARMOR -> configManager.getDouble("durability-pressure.armor-multiplier", 1.35);
            case SHIELDS -> configManager.getDouble("durability-pressure.shields-multiplier", 1.5);
            case BOWS_CROSSBOWS -> configManager.getDouble(
                    "durability-pressure.bows-crossbows-multiplier",
                    1.25);
            case UTILITY -> configManager.getDouble("durability-pressure.utility-multiplier", 1.25);
            case OTHER -> 1.0;
        };
    }

    private boolean hasMending(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        return meta != null && meta.hasEnchant(org.bukkit.enchantments.Enchantment.MENDING);
    }
}
