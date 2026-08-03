package com.deep.civpressure.durability;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class DurabilityClassifier {
    private static final Set<Material> TOOLS = EnumSet.of(
            Material.WOODEN_PICKAXE,
            Material.STONE_PICKAXE,
            Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE,
            Material.NETHERITE_PICKAXE,
            Material.WOODEN_AXE,
            Material.STONE_AXE,
            Material.IRON_AXE,
            Material.GOLDEN_AXE,
            Material.DIAMOND_AXE,
            Material.NETHERITE_AXE,
            Material.WOODEN_SHOVEL,
            Material.STONE_SHOVEL,
            Material.IRON_SHOVEL,
            Material.GOLDEN_SHOVEL,
            Material.DIAMOND_SHOVEL,
            Material.NETHERITE_SHOVEL,
            Material.WOODEN_HOE,
            Material.STONE_HOE,
            Material.IRON_HOE,
            Material.GOLDEN_HOE,
            Material.DIAMOND_HOE,
            Material.NETHERITE_HOE);

    private static final Set<Material> WEAPONS = EnumSet.of(
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD,
            Material.NETHERITE_SWORD,
            Material.MACE,
            Material.TRIDENT);

    private static final Set<Material> ARMOR = EnumSet.of(
            Material.LEATHER_HELMET,
            Material.CHAINMAIL_HELMET,
            Material.IRON_HELMET,
            Material.GOLDEN_HELMET,
            Material.DIAMOND_HELMET,
            Material.NETHERITE_HELMET,
            Material.TURTLE_HELMET,
            Material.LEATHER_CHESTPLATE,
            Material.CHAINMAIL_CHESTPLATE,
            Material.IRON_CHESTPLATE,
            Material.GOLDEN_CHESTPLATE,
            Material.DIAMOND_CHESTPLATE,
            Material.NETHERITE_CHESTPLATE,
            Material.LEATHER_LEGGINGS,
            Material.CHAINMAIL_LEGGINGS,
            Material.IRON_LEGGINGS,
            Material.GOLDEN_LEGGINGS,
            Material.DIAMOND_LEGGINGS,
            Material.NETHERITE_LEGGINGS,
            Material.LEATHER_BOOTS,
            Material.CHAINMAIL_BOOTS,
            Material.IRON_BOOTS,
            Material.GOLDEN_BOOTS,
            Material.DIAMOND_BOOTS,
            Material.NETHERITE_BOOTS);

    private static final Set<Material> SHIELDS = Set.of(Material.SHIELD);

    private static final Set<Material> BOWS_CROSSBOWS = Set.of(
            Material.BOW,
            Material.CROSSBOW);

    private static final Set<Material> UTILITY = Set.of(
            Material.FISHING_ROD,
            Material.SHEARS,
            Material.FLINT_AND_STEEL,
            Material.BRUSH,
            Material.CARROT_ON_A_STICK,
            Material.WARPED_FUNGUS_ON_A_STICK);

    public DurabilityCategory classify(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return DurabilityCategory.OTHER;
        }

        Material material = itemStack.getType();
        if (TOOLS.contains(material)) {
            return DurabilityCategory.TOOLS;
        }
        if (WEAPONS.contains(material)) {
            return DurabilityCategory.WEAPONS;
        }
        if (ARMOR.contains(material)) {
            return DurabilityCategory.ARMOR;
        }
        if (SHIELDS.contains(material)) {
            return DurabilityCategory.SHIELDS;
        }
        if (BOWS_CROSSBOWS.contains(material)) {
            return DurabilityCategory.BOWS_CROSSBOWS;
        }
        if (UTILITY.contains(material)) {
            return DurabilityCategory.UTILITY;
        }
        return DurabilityCategory.OTHER;
    }
}
