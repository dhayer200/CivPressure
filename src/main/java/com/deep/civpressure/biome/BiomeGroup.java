package com.deep.civpressure.biome;

import java.util.Locale;
import java.util.Optional;

public enum BiomeGroup {
    PLAINS,
    IRON,
    GOLD,
    LAPIS,
    REDSTONE,
    EMERALD,
    DIAMOND,
    UNGROUPED;

    public String displayName() {
        String lowerName = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lowerName.charAt(0)) + lowerName.substring(1);
    }

    public static Optional<BiomeGroup> parse(String value) {
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
