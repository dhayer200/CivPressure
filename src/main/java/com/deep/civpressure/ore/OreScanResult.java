package com.deep.civpressure.ore;

import java.util.EnumMap;
import java.util.Map;

public final class OreScanResult {
    private final Map<OreType, Long> counts = new EnumMap<>(OreType.class);

    public OreScanResult() {
        for (OreType oreType : OreType.values()) {
            counts.put(oreType, 0L);
        }
    }

    public void increment(OreType oreType) {
        counts.compute(oreType, (ignored, count) -> count == null ? 1L : count + 1L);
    }

    public void merge(OreScanResult other) {
        for (OreType oreType : OreType.values()) {
            counts.compute(oreType, (ignored, count) -> count + other.getCount(oreType));
        }
    }

    public long getCount(OreType oreType) {
        return counts.getOrDefault(oreType, 0L);
    }
}
