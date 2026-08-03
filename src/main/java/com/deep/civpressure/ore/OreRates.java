package com.deep.civpressure.ore;

import com.deep.civpressure.biome.BiomeGroup;
import com.deep.civpressure.config.ConfigIntReader;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class OreRates {
    private final ConfigIntReader configManager;
    private final Map<BiomeGroup, Map<OreType, Integer>> defaults = new EnumMap<>(BiomeGroup.class);

    public OreRates(ConfigIntReader configManager) {
        this.configManager = configManager;
        defaults.put(BiomeGroup.PLAINS, rates(85, 85, 50, 40, 20, 20));
        defaults.put(BiomeGroup.IRON, rates(140, 70, 50, 40, 20, 20));
        defaults.put(BiomeGroup.GOLD, rates(80, 140, 50, 40, 20, 20));
        defaults.put(BiomeGroup.LAPIS, rates(80, 70, 120, 40, 20, 20));
        defaults.put(BiomeGroup.REDSTONE, rates(80, 70, 50, 120, 20, 20));
        defaults.put(BiomeGroup.EMERALD, rates(70, 60, 50, 40, 200, 80));
        defaults.put(BiomeGroup.DIAMOND, rates(70, 60, 50, 40, 80, 200));
        defaults.put(BiomeGroup.UNGROUPED, rates(80, 80, 80, 80, 60, 60));
    }

    public int getRate(BiomeGroup group, OreType oreType) {
        if (oreType == OreType.COAL || oreType == OreType.COPPER) {
            return 100;
        }

        String path = "ore-redistribution.rates."
                + group.name().toLowerCase(Locale.ROOT)
                + "."
                + oreType.name().toLowerCase(Locale.ROOT);
        int defaultRate = defaults.get(group).get(oreType);
        return Math.max(0, configManager.getInt(path, defaultRate));
    }

    private Map<OreType, Integer> rates(
            int iron,
            int gold,
            int lapis,
            int redstone,
            int emerald,
            int diamond
    ) {
        Map<OreType, Integer> groupRates = new EnumMap<>(OreType.class);
        groupRates.put(OreType.IRON, iron);
        groupRates.put(OreType.GOLD, gold);
        groupRates.put(OreType.LAPIS, lapis);
        groupRates.put(OreType.REDSTONE, redstone);
        groupRates.put(OreType.EMERALD, emerald);
        groupRates.put(OreType.DIAMOND, diamond);
        return groupRates;
    }
}
