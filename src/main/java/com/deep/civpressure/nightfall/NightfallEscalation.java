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
