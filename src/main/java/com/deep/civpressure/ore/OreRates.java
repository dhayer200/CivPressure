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
        //                                    coal cop iron gold  lap  red  eme  dia
        defaults.put(BiomeGroup.PLAINS, rates(85, 85, 85, 85, 85, 85, 80, 80));
        defaults.put(BiomeGroup.IRON, rates(85, 85, 140, 90, 75, 75, 75, 75));
        defaults.put(BiomeGroup.GOLD, rates(85, 85, 90, 140, 75, 75, 75, 75));
        defaults.put(BiomeGroup.LAPIS, rates(85, 85, 75, 75, 120, 100, 75, 75));
        defaults.put(BiomeGroup.REDSTONE, rates(85, 85, 75, 75, 100, 120, 75, 75));
        defaults.put(BiomeGroup.EMERALD, rates(85, 85, 65, 65, 65, 65, 200, 150));
        defaults.put(BiomeGroup.DIAMOND, rates(85, 85, 65, 65, 65, 65, 150, 200));
        defaults.put(BiomeGroup.UNGROUPED, rates(90, 90, 90, 90, 90, 90, 90, 90));
    }

    public int getRate(BiomeGroup group, OreType oreType) {
        // "flat" profile: every ore is the same percentage of vanilla everywhere
        // (no biome biasing) - an across-the-board debuff. "biased" uses the
        // per-region specialization table below.
        if (isFlatProfile()) {
            int flatRate = configManager.getInt("ore-redistribution.flat-profile-rate", 90);
            return Math.max(0, flatRate);
        }

        String path = "ore-redistribution.rates."
                + group.name().toLowerCase(Locale.ROOT)
                + "."
                + oreType.name().toLowerCase(Locale.ROOT);
        int defaultRate = defaults.get(group).get(oreType);
        return Math.max(0, configManager.getInt(path, defaultRate));
    }

    private boolean isFlatProfile() {
        return configManager.getString("ore-redistribution.profile", "biased")
                .equalsIgnoreCase("flat");
    }

    private Map<OreType, Integer> rates(
            int coal,
            int copper,
            int iron,
            int gold,
            int lapis,
            int redstone,
            int emerald,
            int diamond
    ) {
        Map<OreType, Integer> groupRates = new EnumMap<>(OreType.class);
        groupRates.put(OreType.COAL, coal);
        groupRates.put(OreType.COPPER, copper);
        groupRates.put(OreType.IRON, iron);
        groupRates.put(OreType.GOLD, gold);
        groupRates.put(OreType.LAPIS, lapis);
        groupRates.put(OreType.REDSTONE, redstone);
        groupRates.put(OreType.EMERALD, emerald);
        groupRates.put(OreType.DIAMOND, diamond);
        return groupRates;
    }
}
