package com.deep.civpressure.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BiomeGroupResolutionTest {
    @Test
    void surfaceWaterIsPlainsAndSubmergedWaterIsUngrouped() {
        assertEquals(BiomeGroup.PLAINS, BiomeGroupRegistry.waterGroupForY(0));
        assertEquals(BiomeGroup.PLAINS, BiomeGroupRegistry.waterGroupForY(63));
        assertEquals(BiomeGroup.PLAINS, BiomeGroupRegistry.waterGroupForY(320));
        assertEquals(BiomeGroup.UNGROUPED, BiomeGroupRegistry.waterGroupForY(-1));
        assertEquals(BiomeGroup.UNGROUPED, BiomeGroupRegistry.waterGroupForY(-64));
    }
}
