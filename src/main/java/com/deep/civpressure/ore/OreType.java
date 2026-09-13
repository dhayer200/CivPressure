package com.deep.civpressure.ore;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;

public enum OreType {
    COAL(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE),
    COPPER(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE),
    IRON(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE),
    GOLD(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE),
    LAPIS(Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE),
    REDSTONE(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE),
    EMERALD(Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE),
    DIAMOND(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE);

    private final Material normalMaterial;
    private final Material deepslateMaterial;
    private final Set<Material> materials;

    OreType(Material normalMaterial, Material deepslateMaterial) {
        this.normalMaterial = normalMaterial;
        this.deepslateMaterial = deepslateMaterial;
        this.materials = EnumSet.noneOf(Material.class);
        this.materials.addAll(Arrays.asList(normalMaterial, deepslateMaterial));
    }

    public boolean matches(Material material) {
        return materials.contains(material);
    }

    public boolean isRedistributed() {
        // Every ore (coal and copper included) is now controlled by the
        // configurable per-biome rate table in OreRates.
        return true;
    }

    public Material materialFor(boolean deepslate) {
        return deepslate ? deepslateMaterial : normalMaterial;
    }

    public String displayName() {
        String lowerName = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lowerName.charAt(0)) + lowerName.substring(1);
    }
}
