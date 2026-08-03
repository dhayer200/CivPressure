package com.deep.civpressure.ore;

import com.deep.civpressure.biome.BiomeGroup;
import java.util.random.RandomGenerator;

/**
 * Pure decision logic for ore redistribution, split out from the Bukkit-facing
 * {@link OreRedistributor} so it can be unit tested without a running server.
 *
 * <p>The rate is a percentage of vanilla generation for a given biome group and
 * ore. Below 100 some vanilla ores are removed; above 100 extra ores are added
 * next to existing ones. Coal and copper are never redistributed (always 100).
 */
public final class OreRedistributionPlanner {
    private static final int PERCENT = 100;

    private final OreRates oreRates;

    public OreRedistributionPlanner(OreRates oreRates) {
        this.oreRates = oreRates;
    }

    /**
     * Whether a single vanilla ore should be removed. Consumes exactly one
     * random draw when the rate is below 100 and none otherwise, matching the
     * historical generation sequence so existing seeds are unaffected.
     */
    public boolean shouldRemove(BiomeGroup group, OreType oreType, RandomGenerator random) {
        int rate = oreRates.getRate(group, oreType);
        if (rate >= PERCENT) {
            return false;
        }
        return random.nextInt(PERCENT) >= rate;
    }

    /**
     * How many extra ores to attempt to add next to a single vanilla ore.
     * Consumes one random draw only when the rate exceeds 100.
     */
    public int extraAdditions(BiomeGroup group, OreType oreType, RandomGenerator random) {
        int extraPercent = oreRates.getRate(group, oreType) - PERCENT;
        if (extraPercent <= 0) {
            return 0;
        }
        int additions = extraPercent / PERCENT;
        if (random.nextInt(PERCENT) < extraPercent % PERCENT) {
            additions++;
        }
        return additions;
    }
}
