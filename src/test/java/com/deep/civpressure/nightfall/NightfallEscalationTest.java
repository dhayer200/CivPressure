package com.deep.civpressure.nightfall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NightfallEscalationTest {

    @Test
    void progressIsZeroOnTheFirstNightAndOneAtTheCap() {
        assertEquals(0.0, NightfallEscalation.progress(0, 50));
        assertEquals(0.5, NightfallEscalation.progress(25, 50));
        assertEquals(1.0, NightfallEscalation.progress(50, 50));
    }

    @Test
    void progressClampsBeyondTheCap() {
        assertEquals(1.0, NightfallEscalation.progress(9999, 50));
    }

    @Test
    void progressWithNonPositiveCapIsFullyEscalated() {
        assertEquals(1.0, NightfallEscalation.progress(0, 0));
        assertEquals(1.0, NightfallEscalation.progress(3, -5));
    }

    @Test
    void progressNeverDecreasesAsNightsPass() {
        double previous = -1.0;
        for (long night = 0; night <= 60; night++) {
            double current = NightfallEscalation.progress(night, 50);
            assertTrue(current >= previous, "progress must be monotonic at night " + night);
            previous = current;
        }
    }

    @Test
    void lerpHitsEndpointsAndClamps() {
        assertEquals(10.0, NightfallEscalation.lerp(10.0, 20.0, 0.0));
        assertEquals(20.0, NightfallEscalation.lerp(10.0, 20.0, 1.0));
        assertEquals(15.0, NightfallEscalation.lerp(10.0, 20.0, 0.5));
        assertEquals(10.0, NightfallEscalation.lerp(10.0, 20.0, -3.0));
        assertEquals(20.0, NightfallEscalation.lerp(10.0, 20.0, 2.0));
    }

    @Test
    void multiplierStartsAtFirstNightBonusAndRampsToTheCap() {
        // Health: +15% first night, +150% (x2.5) at the cap.
        assertEquals(1.15, NightfallEscalation.multiplier(0.0, 0.15, 1.5), 1.0e-9);
        assertEquals(2.5, NightfallEscalation.multiplier(1.0, 0.15, 1.5), 1.0e-9);
        assertEquals(1.825, NightfallEscalation.multiplier(0.5, 0.15, 1.5), 1.0e-9);

        // Damage: +15% first night, +50% at the cap.
        assertEquals(1.15, NightfallEscalation.multiplier(0.0, 0.15, 0.5), 1.0e-9);
        assertEquals(1.5, NightfallEscalation.multiplier(1.0, 0.15, 0.5), 1.0e-9);
    }

    @Test
    void speedHasNoFirstNightFloorAndRampsFromZero() {
        assertEquals(1.0, NightfallEscalation.multiplier(0.0, 0.0, 0.15), 1.0e-9);
        assertEquals(1.15, NightfallEscalation.multiplier(1.0, 0.0, 0.15), 1.0e-9);
    }

    @Test
    void negativeBonusesAreTreatedAsNoBuff() {
        assertEquals(1.0, NightfallEscalation.multiplier(1.0, -2.0, -3.0));
    }

    @Test
    void chanceInterpolatesAndClamps() {
        assertEquals(0.2, NightfallEscalation.chance(0.0, 0.2, 0.8), 1.0e-9);
        assertEquals(0.8, NightfallEscalation.chance(1.0, 0.2, 0.8), 1.0e-9);
        assertEquals(0.5, NightfallEscalation.chance(0.5, 0.2, 0.8), 1.0e-9);
        assertEquals(1.0, NightfallEscalation.chance(1.0, 0.0, 5.0));
        assertEquals(0.0, NightfallEscalation.chance(0.0, -1.0, 0.5));
    }

    @Test
    void packSizeRoundsAndStaysWithinBounds() {
        assertEquals(1, NightfallEscalation.packSize(0.0, 1, 6));
        assertEquals(6, NightfallEscalation.packSize(1.0, 1, 6));
        assertEquals(4, NightfallEscalation.packSize(0.5, 1, 6));
        assertEquals(0, NightfallEscalation.packSize(1.0, -3, 0));
    }

    @Test
    void siegePackDoublesFromTwelveToTwentyFourByTheCap() {
        assertEquals(12, NightfallEscalation.packSize(0.0, 12, 24));
        assertEquals(18, NightfallEscalation.packSize(0.5, 12, 24));
        assertEquals(24, NightfallEscalation.packSize(1.0, 12, 24));
    }

    @Test
    void pickWeightedIndexFollowsTheWeightTable() {
        int[] weights = {16, 4, 2, 5};
        assertEquals(0, NightfallEscalation.pickWeightedIndex(weights, 0));
        assertEquals(0, NightfallEscalation.pickWeightedIndex(weights, 15));
        assertEquals(1, NightfallEscalation.pickWeightedIndex(weights, 16));
        assertEquals(2, NightfallEscalation.pickWeightedIndex(weights, 20));
        assertEquals(3, NightfallEscalation.pickWeightedIndex(weights, 22));
        assertEquals(-1, NightfallEscalation.pickWeightedIndex(new int[] {0, 0}, 0));
    }

    @Test
    void babyZombieJockeyWeightIsOneAndAQuarterTimesABaseSlot() {
        assertEquals(5, NightfallEscalation.scaleWeight(4, 1.25));
        assertEquals(1, NightfallEscalation.scaleWeight(1, 1.25));
    }

    @Test
    void skeletonTrapChanceStaysOffOnNightOneAndHitsTheCap() {
        assertEquals(0.0, NightfallEscalation.chance(0.0, 0.0, 0.25), 1.0e-9);
        assertEquals(0.125, NightfallEscalation.chance(0.5, 0.0, 0.25), 1.0e-9);
        assertEquals(0.25, NightfallEscalation.chance(1.0, 0.0, 0.25), 1.0e-9);
    }

    @Test
    void settlementScorePrefersBedsAndStillCountsVillages() {
        assertTrue(NightfallEscalation.settlementScore(3, 0, false)
                > NightfallEscalation.settlementScore(0, 1, true));
        assertTrue(NightfallEscalation.settlementScore(0, 2, false)
                > NightfallEscalation.settlementScore(0, 0, true));
        assertEquals(0, NightfallEscalation.settlementScore(0, 0, false));
        assertEquals(2, NightfallEscalation.settlementScore(0, 0, true));
    }

    @Test
    void packSizeHandlesInvertedBounds() {
        assertEquals(6, NightfallEscalation.packSize(1.0, 6, 1));
    }

    @Test
    void worldDaysAreFullTimeDividedByAMinecraftDay() {
        assertEquals(0L, NightfallEscalation.worldDays(0L));
        assertEquals(0L, NightfallEscalation.worldDays(23999L));
        assertEquals(1L, NightfallEscalation.worldDays(24000L));
        assertEquals(30L, NightfallEscalation.worldDays(30L * 24000L + 18000L));
    }

    @Test
    void nightsSurvivedSubtractsClockOffsetAndNeverGoesNegative() {
        assertEquals(30L, NightfallEscalation.nightsSurvived(30L, 0L));
        assertEquals(0L, NightfallEscalation.nightsSurvived(30L, 30L));
        assertEquals(15L, NightfallEscalation.nightsSurvived(30L, 15L));
        assertEquals(40L, NightfallEscalation.nightsSurvived(10L, -30L));
        assertEquals(0L, NightfallEscalation.nightsSurvived(5L, 20L));
    }

    @Test
    void clockOffsetMapsAWorldDayOntoAnyRequestedNightfallDay() {
        assertEquals(30L, NightfallEscalation.clockOffsetFor(30L, 0L));
        assertEquals(15L, NightfallEscalation.clockOffsetFor(30L, 15L));
        assertEquals(-30L, NightfallEscalation.clockOffsetFor(10L, 40L));
        assertEquals(10L, NightfallEscalation.clockOffsetFor(10L, -5L));
    }

    @Test
    void settingThenReadingNightfallDayRoundTrips() {
        long worldDays = 22L;
        for (long target : new long[] {0L, 1L, 15L, 50L, 99L}) {
            long offset = NightfallEscalation.clockOffsetFor(worldDays, target);
            assertEquals(Math.max(0L, target), NightfallEscalation.nightsSurvived(worldDays, offset));
        }
    }

    @Test
    void unlockProgressIsZeroBeforeTheUnlockNightThenLinearToTheCap() {
        assertEquals(0.0, NightfallEscalation.unlockProgress(99, 100, 200), 1.0e-9);
        assertEquals(0.0, NightfallEscalation.unlockProgress(100, 100, 200), 1.0e-9);
        assertEquals(0.5, NightfallEscalation.unlockProgress(150, 100, 200), 1.0e-9);
        assertEquals(1.0, NightfallEscalation.unlockProgress(200, 100, 200), 1.0e-9);
        assertEquals(1.0, NightfallEscalation.unlockProgress(250, 100, 200), 1.0e-9);
    }

    @Test
    void unlockedChanceIsZeroBeforeUnlockThenRamps() {
        assertEquals(0.0, NightfallEscalation.unlockedChance(99, 100, 200, 0.05, 0.30), 1.0e-9);
        assertEquals(0.05, NightfallEscalation.unlockedChance(100, 100, 200, 0.05, 0.30), 1.0e-9);
        assertEquals(0.175, NightfallEscalation.unlockedChance(150, 100, 200, 0.05, 0.30), 1.0e-9);
        assertEquals(0.30, NightfallEscalation.unlockedChance(200, 100, 200, 0.05, 0.30), 1.0e-9);
    }

    @Test
    void nightCurvePeaksAtMidnightAndIsZeroOutsideNight() {
        assertEquals(1.0, NightfallEscalation.nightCurve(18000L), 1.0e-9);
        assertEquals(0.0, NightfallEscalation.nightCurve(13000L), 1.0e-9);
        assertEquals(0.0, NightfallEscalation.nightCurve(23000L), 1.0e-9);
        assertEquals(0.0, NightfallEscalation.nightCurve(6000L), 1.0e-9);
        double lateNight = NightfallEscalation.nightCurve(15500L);
        assertTrue(lateNight > 0.0 && lateNight < 1.0, "mid-evening should be partial intensity");
    }
}
