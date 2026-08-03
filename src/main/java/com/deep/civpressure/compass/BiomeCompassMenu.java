package com.deep.civpressure.compass;

import com.deep.civpressure.biome.BiomeGroup;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class BiomeCompassMenu implements InventoryHolder {
    private final UUID playerId;
    private final EquipmentSlot hand;
    private final Map<Integer, BiomeGroup> groupsBySlot;
    private Inventory inventory;

    public BiomeCompassMenu(
            UUID playerId,
            EquipmentSlot hand,
            Map<Integer, BiomeGroup> groupsBySlot
    ) {
        this.playerId = playerId;
        this.hand = hand;
        this.groupsBySlot = Map.copyOf(groupsBySlot);
    }

    public UUID playerId() {
        return playerId;
    }

    public EquipmentSlot hand() {
        return hand;
    }

    public BiomeGroup groupAt(int slot) {
        return groupsBySlot.get(slot);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
