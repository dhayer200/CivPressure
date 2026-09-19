package com.deep.civpressure.nightfall;

/**
 * Pure, server-independent escalation math for the Nightfall module.
 *
 * <p>Nightfall makes each successive night more dangerous until a cap is
 * reached. Everything here is a plain function of the number of nights survived
 * (derived elsewhere from the world's age) so it can be unit tested without a
 * running server.
 */
public final class NightfallEscalation {
    private static final long DUSK = 13000L;
    private static final long MIDNIGHT = 18000L;
    private static final long DAWN = 23000L;
    static final long TICKS_PER_DAY = 24000L;

    private NightfallEscalation() {
    }

    /** Minecraft calendar day from {@code World#getFullTime()}, always {@code >= 0}. */
    public static long worldDays(long fullTime) {
        return Math.floorDiv(Math.max(0L, fullTime), TICKS_PER_DAY);
    }

    /**
     * Nightfall escalation day after applying a persisted clock offset. The
     * offset is {@code worldDays - desiredNight}, so admins can reset or jump
     * the danger clock without rewriting the world's actual age.
     */
    public static long nightsSurvived(long worldDays, long clockOffset) {
        return Math.max(0L, worldDays - clockOffset);
    }

    /**
     * Offset that makes {@link #nightsSurvived(long, long)} report
     * {@code targetNight} (clamped to {@code >= 0}) on the given world day.
     */
    public static long clockOffsetFor(long worldDays, long targetNight) {
        return worldDays - Math.max(0L, targetNight);
    }

    /**
     * Fraction of the way to the danger cap, in {@code [0, 1]}. Zero on the
     * first night, one once {@code nightsSurvived} reaches {@code capNights}.
     */
    public static double progress(long nightsSurvived, int capNights) {
        if (capNights <= 0) {
            return 1.0;
        }
        if (nightsSurvived <= 0) {
            return 0.0;
        }
        if (nightsSurvived >= capNights) {
            return 1.0;
        }
        return (double) nightsSurvived / (double) capNights;
    }

    /**
     * Linear progress from an unlock night to the cap. Zero before and on the
     * unlock night, one at {@code capNights}. Used for late features (giants,
     * phantoms) that stay off until a milestone.
     */
    public static double unlockProgress(long nightsSurvived, long unlockNight, int capNights) {
        if (nightsSurvived < unlockNight) {
            return 0.0;
        }
        if (capNights <= unlockNight) {
            return nightsSurvived >= unlockNight ? 1.0 : 0.0;
        }
        if (nightsSurvived >= capNights) {
            return 1.0;
        }
        return (double) (nightsSurvived - unlockNight) / (double) (capNights - unlockNight);
    }

    /**
     * Chance for a feature that is locked until {@code unlockNight}, then ramps
     * linearly from {@code startChance} (on the unlock night) to {@code endChance}
     * at the cap.
     */
    public static double unlockedChance(
            long nightsSurvived,
            long unlockNight,
            int capNights,
            double startChance,
            double endChance
    ) {
        if (nightsSurvived < unlockNight) {
            return 0.0;
        }
        return chance(unlockProgress(nightsSurvived, unlockNight, capNights), startChance, endChance);
    }

    /** Linear interpolation between {@code start} and {@code end}, clamped to [0,1]. */
    public static double lerp(double start, double end, double t) {
        return start + (end - start) * clamp01(t);
    }

    /**
     * Attribute multiplier ({@code >= 1}) that starts at {@code firstNightBonus}
     * on the first night ({@code progress == 0}) and ramps to {@code maxBonus} at
     * the cap ({@code progress == 1}). Both bonuses are treated as fractions
     * (e.g. {@code 0.15} is +15%) and clamped to be non-negative.
     */
    public static double multiplier(double progress, double firstNightBonus, double maxBonus) {
        return 1.0 + lerp(Math.max(0.0, firstNightBonus), Math.max(0.0, maxBonus), progress);
    }

    /** Interpolated per-cycle probability (for sounds and pack spawns), clamped to [0,1]. */
    public static double chance(double progress, double minChance, double maxChance) {
        return clamp01(lerp(clamp01(minChance), clamp01(maxChance), progress));
    }

    /** Pack size for a night raid, rounded and bounded to {@code [minSize, maxSize]}. */
    public static int packSize(double progress, int minSize, int maxSize) {
        int lo = Math.max(0, minSize);
        int hi = Math.max(lo, maxSize);
        return (int) Math.round(lerp(lo, hi, progress));
    }

    /**
     * Picks an index from a weight table. {@code roll} should be in
     * {@code [0, totalWeight)}. Returns {@code -1} if every weight is zero.
     */
    public static int pickWeightedIndex(int[] weights, int roll) {
        int total = 0;
        for (int weight : weights) {
            total += Math.max(0, weight);
        }
        if (total <= 0) {
            return -1;
        }
        int remaining = Math.floorMod(roll, total);
        for (int i = 0; i < weights.length; i++) {
            remaining -= Math.max(0, weights[i]);
            if (remaining < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    /** Multiplies a weight and rounds to the nearest whole number of at least 1 when the base is positive. */
    public static int scaleWeight(int baseWeight, double factor) {
        if (baseWeight <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(baseWeight * factor));
    }

    /**
     * Relative attraction of a settlement cell. Beds weigh more than villagers;
     * a vanilla village structure is only a fallback landmark.
     */
    public static int settlementScore(int beds, int villagers, boolean villageStructure) {
        return Math.max(0, beds) * 4
                + Math.max(0, villagers) * 3
                + (villageStructure ? 2 : 0);
    }

    /**
     * Within-night intensity in {@code [0, 1]}, peaking at midnight (18000) and
     * falling to zero at dusk (13000) and dawn (23000). Returns zero during the
     * day. Used to make atmosphere build as the night deepens.
     *
     * @param timeOfDay the world time in the day cycle, {@code [0, 24000)}
     */
    public static double nightCurve(long timeOfDay) {
        if (timeOfDay < DUSK || timeOfDay > DAWN) {
            return 0.0;
        }
        long half = MIDNIGHT - DUSK;
        long distance = Math.abs(timeOfDay - MIDNIGHT);
        return clamp01(1.0 - (double) distance / (double) half);
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
