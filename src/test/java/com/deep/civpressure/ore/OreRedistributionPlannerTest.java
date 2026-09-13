package com.deep.civpressure.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deep.civpressure.biome.BiomeGroup;
import com.deep.civpressure.config.ConfigIntReader;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Statistical validation of "our method" against the vanilla baseline. Vanilla
 * generates some number of ores; the planner decides how many survive and how
 * many are added, and the resulting count should track the configured rate.
 */
class OreRedistributionPlannerTest {
    private static final ConfigIntReader DEFAULTS = (path, def) -> def;
    private final OreRates rates = new OreRates(DEFAULTS);
    private final OreRedistributionPlanner planner = new OreRedistributionPlanner(rates);

    /**
     * Simulate redistributing {@code count} vanilla ores of one type in one
     * biome group, returning the resulting ore count (survivors + additions).
     * Mirrors {@link OreRedistributor}'s remove-pass-then-add-pass ordering.
     */
    private long simulateYield(BiomeGroup group, OreType ore, int count, SplittableRandom rng) {
        long result = 0;
        for (int i = 0; i < count; i++) {
            if (!planner.shouldRemove(group, ore, rng)) {
                result++;
            }
        }
        for (int i = 0; i < count; i++) {
            result += planner.extraAdditions(group, ore, rng);
        }
        return result;
    }

    @Test
    void yieldMatchesConfiguredRateAcrossEveryGroupAndOre() {
        int vanillaPerType = 200_000;
        SplittableRandom rng = new SplittableRandom(0xCAFEBABEL);
        for (BiomeGroup group : BiomeGroup.values()) {
            for (OreType ore : OreType.values()) {
                long yield = simulateYield(group, ore, vanillaPerType, rng);
                double actualPercent = 100.0 * yield / vanillaPerType;
                double expectedPercent = rates.getRate(group, ore);
                assertTrue(Math.abs(actualPercent - expectedPercent) <= 1.5,
                        () -> group + "/" + ore + " expected ~" + expectedPercent
                                + "% but produced " + actualPercent + "%");
            }
        }
    }

    @Test
    void rateOf100LeavesCountExactlyUnchanged() {
        // A 100% rate removes and adds nothing, regardless of the default table.
        OreRates hundred = new OreRates((path, def) -> 100);
        OreRedistributionPlanner flat = new OreRedistributionPlanner(hundred);
        SplittableRandom rng = new SplittableRandom(42);
        long yield = 0;
        for (int i = 0; i < 5000; i++) {
            if (!flat.shouldRemove(BiomeGroup.PLAINS, OreType.DIAMOND, rng)) {
                yield++;
            }
        }
        for (int i = 0; i < 5000; i++) {
            yield += flat.extraAdditions(BiomeGroup.PLAINS, OreType.DIAMOND, rng);
        }
        assertEquals(5000, yield);
    }

    @Test
    void doubleRateExactlyDoublesOutput() {
        // A 200% rate keeps every ore and adds exactly one more, regardless of table.
        OreRates twoHundred = new OreRates((path, def) -> 200);
        OreRedistributionPlanner doubler = new OreRedistributionPlanner(twoHundred);
        SplittableRandom rng = new SplittableRandom(99);
        long yield = 0;
        for (int i = 0; i < 10_000; i++) {
            if (!doubler.shouldRemove(BiomeGroup.DIAMOND, OreType.DIAMOND, rng)) {
                yield++;
            }
        }
        for (int i = 0; i < 10_000; i++) {
            yield += doubler.extraAdditions(BiomeGroup.DIAMOND, OreType.DIAMOND, rng);
        }
        assertEquals(20_000, yield);
    }

    @Test
    void deterministicForAGivenSeed() {
        SplittableRandom a = new SplittableRandom(123);
        SplittableRandom b = new SplittableRandom(123);
        assertEquals(
                simulateYield(BiomeGroup.IRON, OreType.IRON, 10_000, a),
                simulateYield(BiomeGroup.IRON, OreType.IRON, 10_000, b));
    }

    @Test
    void perChunkVariesButRangeAggregateConvergesToRate() {
        // Diamond in plains is 80%. A single chunk (few diamonds) is noisy, but
        // aggregated over a large range it converges to the configured rate.
        int diamondsPerChunk = 8;
        int chunks = 4000;
        SplittableRandom rng = new SplittableRandom(7);
        long totalVanilla = 0;
        long totalYield = 0;
        for (int c = 0; c < chunks; c++) {
            totalVanilla += diamondsPerChunk;
            totalYield += simulateYield(BiomeGroup.PLAINS, OreType.DIAMOND, diamondsPerChunk, rng);
        }
        double aggregatePercent = 100.0 * totalYield / totalVanilla;
        assertTrue(Math.abs(aggregatePercent - 80.0) <= 1.0,
                () -> "aggregate over " + chunks + " chunks was " + aggregatePercent + "%");
    }
}
