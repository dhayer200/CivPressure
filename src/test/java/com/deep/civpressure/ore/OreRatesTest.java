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
    void coalAndCopperAreNowRedistributedFromTheRateTable() {
        // Coal/copper are no longer hardcoded to 100%; they follow the table and
        // can be overridden by config like any other ore.
        OreRates rates = new OreRates(DEFAULTS);
        assertEquals(85, rates.getRate(BiomeGroup.PLAINS, OreType.COAL));
        assertEquals(85, rates.getRate(BiomeGroup.PLAINS, OreType.COPPER));
        assertEquals(85, rates.getRate(BiomeGroup.IRON, OreType.COAL));
        assertEquals(90, rates.getRate(BiomeGroup.UNGROUPED, OreType.COPPER));

        ConfigIntReader override = (path, def) ->
                path.equals("ore-redistribution.rates.plains.coal") ? 50 : def;
        assertEquals(50, new OreRates(override).getRate(BiomeGroup.PLAINS, OreType.COAL));
    }

    @Test
    void defaultRatesMatchDesignTable() {
        OreRates rates = new OreRates(DEFAULTS);
        assertEquals(140, rates.getRate(BiomeGroup.IRON, OreType.IRON));
        assertEquals(200, rates.getRate(BiomeGroup.DIAMOND, OreType.DIAMOND));
        assertEquals(200, rates.getRate(BiomeGroup.EMERALD, OreType.EMERALD));
        assertEquals(150, rates.getRate(BiomeGroup.EMERALD, OreType.DIAMOND));
        assertEquals(150, rates.getRate(BiomeGroup.DIAMOND, OreType.EMERALD));
        assertEquals(80, rates.getRate(BiomeGroup.PLAINS, OreType.DIAMOND));
        assertEquals(85, rates.getRate(BiomeGroup.PLAINS, OreType.IRON));
        assertEquals(90, rates.getRate(BiomeGroup.UNGROUPED, OreType.DIAMOND));
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
    void flatProfileNerfsEveryOreEverywhereWithNoBiasing() {
        ConfigIntReader flat = new ConfigIntReader() {
            @Override
            public int getInt(String path, int def) {
                return def;
            }

            @Override
            public String getString(String path, String def) {
                return path.equals("ore-redistribution.profile") ? "flat" : def;
            }
        };
        OreRates rates = new OreRates(flat);
        for (BiomeGroup group : BiomeGroup.values()) {
            for (OreType ore : OreType.values()) {
                assertEquals(90, rates.getRate(group, ore),
                        () -> group + "/" + ore + " should be a flat 90%");
            }
        }
    }

    @Test
    void flatProfileRespectsConfiguredRate() {
        ConfigIntReader flat = new ConfigIntReader() {
            @Override
            public int getInt(String path, int def) {
                return path.equals("ore-redistribution.flat-profile-rate") ? 75 : def;
            }

            @Override
            public String getString(String path, String def) {
                return path.equals("ore-redistribution.profile") ? "flat" : def;
            }
        };
        OreRates rates = new OreRates(flat);
        assertEquals(75, rates.getRate(BiomeGroup.EMERALD, OreType.DIAMOND));
        assertEquals(75, rates.getRate(BiomeGroup.PLAINS, OreType.COAL));
    }

    @Test
    void negativeConfigIsClampedToZero() {
        OreRates rates = new OreRates((path, def) -> -10);
        assertEquals(0, rates.getRate(BiomeGroup.PLAINS, OreType.DIAMOND));
    }
}
