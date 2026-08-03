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
        // Coal is always 100%, so nothing is removed or added.
        SplittableRandom rng = new SplittableRandom(42);
        assertEquals(5000, simulateYield(BiomeGroup.PLAINS, OreType.COAL, 5000, rng));
    }

    @Test
    void doubleRateExactlyDoublesOutput() {
        // Diamond in a diamond biome is 200%: every ore survives and gains exactly one.
        SplittableRandom rng = new SplittableRandom(99);
        assertEquals(20_000, simulateYield(BiomeGroup.DIAMOND, OreType.DIAMOND, 10_000, rng));
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
        // Diamond in plains is 20%. A single chunk (few diamonds) is noisy, but
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
        assertTrue(Math.abs(aggregatePercent - 20.0) <= 1.0,
                () -> "aggregate over " + chunks + " chunks was " + aggregatePercent + "%");
    }
}
