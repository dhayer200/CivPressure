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
    void packSizeHandlesInvertedBounds() {
        assertEquals(6, NightfallEscalation.packSize(1.0, 6, 1));
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
