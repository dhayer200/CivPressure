package com.deep.civpressure.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deep.civpressure.biome.BiomeGroup;
import com.deep.civpressure.config.ConfigIntReader;
import org.junit.jupiter.api.Test;

class OreRatesTest {
    /** A reader that reports no config overrides (always returns the code default). */
    private static final ConfigIntReader DEFAULTS = (path, def) -> def;

    @Test
    void coalAndCopperAreNeverRedistributed() {
        // Even a hostile config cannot change coal/copper: they stay at 100%.
        OreRates rates = new OreRates((path, def) -> 5);
        for (BiomeGroup group : BiomeGroup.values()) {
            assertEquals(100, rates.getRate(group, OreType.COAL), group + " coal");
            assertEquals(100, rates.getRate(group, OreType.COPPER), group + " copper");
        }
    }

    @Test
    void defaultRatesMatchDesignTable() {
        OreRates rates = new OreRates(DEFAULTS);
        assertEquals(140, rates.getRate(BiomeGroup.IRON, OreType.IRON));
        assertEquals(200, rates.getRate(BiomeGroup.DIAMOND, OreType.DIAMOND));
        assertEquals(200, rates.getRate(BiomeGroup.EMERALD, OreType.EMERALD));
        assertEquals(20, rates.getRate(BiomeGroup.PLAINS, OreType.DIAMOND));
        assertEquals(85, rates.getRate(BiomeGroup.PLAINS, OreType.IRON));
        assertEquals(60, rates.getRate(BiomeGroup.UNGROUPED, OreType.DIAMOND));
    }

    @Test
    void everyDefaultRateHasNonZeroFloorSoNoSeedIsResourceLocked() {
        OreRates rates = new OreRates(DEFAULTS);
        for (BiomeGroup group : BiomeGroup.values()) {
            for (OreType ore : OreType.values()) {
                assertTrue(rates.getRate(group, ore) > 0, group + "/" + ore + " must be > 0");
            }
        }
    }

    @Test
    void configOverrideReplacesDefaultForThatEntryOnly() {
        ConfigIntReader override = (path, def) ->
                path.equals("ore-redistribution.rates.plains.diamond") ? 5 : def;
        OreRates rates = new OreRates(override);
        assertEquals(5, rates.getRate(BiomeGroup.PLAINS, OreType.DIAMOND));
        assertEquals(85, rates.getRate(BiomeGroup.PLAINS, OreType.IRON));
    }

    @Test
    void negativeConfigIsClampedToZero() {
        OreRates rates = new OreRates((path, def) -> -10);
        assertEquals(0, rates.getRate(BiomeGroup.PLAINS, OreType.DIAMOND));
    }
}
