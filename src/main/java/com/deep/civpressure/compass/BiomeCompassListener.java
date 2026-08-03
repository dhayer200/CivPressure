package com.deep.civpressure.compass;

import com.deep.civpressure.biome.BiomeGroup;
import com.deep.civpressure.config.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class BiomeCompassListener implements Listener {
    private final ConfigManager configManager;
    private final BiomeCompassManager compassManager;

    public BiomeCompassListener(
            ConfigManager configManager,
            BiomeCompassManager compassManager
    ) {
        this.configManager = configManager;
        this.compassManager = compassManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCompassUse(PlayerInteractEvent event) {
        if (!configManager.isModuleEnabled("biome-compass")
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        ItemStack item = event.getItem();
        if (!compassManager.isBiomeCompass(item)) {
            return;
        }

        event.setCancelled(true);
        EquipmentSlot hand = event.getHand() == EquipmentSlot.OFF_HAND
                ? EquipmentSlot.OFF_HAND
                : EquipmentSlot.HAND;
        compassManager.openMenu(event.getPlayer(), hand);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BiomeCompassMenu menu)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !player.getUniqueId().equals(menu.playerId())
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        BiomeGroup group = menu.groupAt(event.getRawSlot());
        if (group == null) {
            return;
        }

        if (compassManager.selectGroup(player, menu.hand(), group)) {
            player.closeInventory();
        }
    }
}
