package com.deep.civpressure.season;

public record SeasonState(SeasonType type, int daysActive) {
    public static SeasonState normal() {
        return new SeasonState(SeasonType.NORMAL, 0);
    }
}
