package com.deep.civpressure.season;

import java.util.Locale;

public enum SeasonType {
    NORMAL,
    DROUGHT,
    WET;

    public String displayName() {
        String lowerName = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lowerName.charAt(0)) + lowerName.substring(1);
    }
}
