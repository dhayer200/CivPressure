package com.deep.civpressure.biome;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.block.Biome;

public final class BiomeGroupRegistry {
    private final Map<Biome, BiomeGroup> groups = new HashMap<>();
    private final Map<BiomeGroup, Set<Biome>> biomesByGroup = new EnumMap<>(BiomeGroup.class);
    private final Set<Biome> waterBiomes = Set.of(
            Biome.OCEAN,
            Biome.DEEP_OCEAN,
            Biome.WARM_OCEAN,
            Biome.LUKEWARM_OCEAN,
            Biome.COLD_OCEAN,
            Biome.FROZEN_OCEAN,
            Biome.DEEP_LUKEWARM_OCEAN,
            Biome.DEEP_COLD_OCEAN,
            Biome.DEEP_FROZEN_OCEAN,
            Biome.RIVER,
            Biome.FROZEN_RIVER,
            Biome.BEACH,
            Biome.SNOWY_BEACH,
            Biome.STONY_SHORE
    );

    public BiomeGroupRegistry() {
        register(BiomeGroup.PLAINS,
                Biome.PLAINS,
                Biome.SUNFLOWER_PLAINS,
                Biome.MEADOW,
                Biome.CHERRY_GROVE,
                Biome.FLOWER_FOREST,
                Biome.FOREST,
                Biome.BIRCH_FOREST,
                Biome.OLD_GROWTH_BIRCH_FOREST,
                Biome.DAPPLED_FOREST);

        register(BiomeGroup.IRON,
                Biome.TAIGA,
                Biome.SNOWY_TAIGA,
                Biome.OLD_GROWTH_PINE_TAIGA,
                Biome.OLD_GROWTH_SPRUCE_TAIGA,
                Biome.SAVANNA,
                Biome.SAVANNA_PLATEAU,
                Biome.WINDSWEPT_SAVANNA);

        register(BiomeGroup.GOLD,
                Biome.DESERT,
                Biome.BADLANDS,
                Biome.ERODED_BADLANDS,
                Biome.WOODED_BADLANDS);

        register(BiomeGroup.LAPIS,
                Biome.DARK_FOREST,
                Biome.SWAMP,
                Biome.MANGROVE_SWAMP);

        register(BiomeGroup.REDSTONE,
                Biome.JUNGLE,
                Biome.BAMBOO_JUNGLE,
                Biome.SPARSE_JUNGLE);

        // Emerald = all mountainous (non-snowy) highlands.
        register(BiomeGroup.EMERALD,
                Biome.STONY_PEAKS,
                Biome.WINDSWEPT_HILLS,
                Biome.WINDSWEPT_GRAVELLY_HILLS,
                Biome.WINDSWEPT_FOREST);

        // Diamond = all snowy land. Frozen oceans/rivers and snowy beaches are
        // water biomes, so they resolve by depth (see waterBiomes) rather than
        // being registered here.
        register(BiomeGroup.DIAMOND,
                Biome.FROZEN_PEAKS,
                Biome.JAGGED_PEAKS,
                Biome.SNOWY_SLOPES,
                Biome.GROVE,
                Biome.SNOWY_PLAINS,
                Biome.ICE_SPIKES);

        register(BiomeGroup.UNGROUPED,
                Biome.MUSHROOM_FIELDS,
                Biome.DRIPSTONE_CAVES,
                Biome.LUSH_CAVES,
                Biome.DEEP_DARK,
                Biome.SULFUR_CAVES);
    }

    public BiomeGroup resolve(Biome biome, int y) {
        if (waterBiomes.contains(biome)) {
            return waterGroupForY(y);
        }
        return groups.getOrDefault(biome, BiomeGroup.UNGROUPED);
    }

    /**
     * Y-based resolution for water and coastal biomes: surface water counts as
     * Plains, submerged/underground water counts as Ungrouped. Pure so it can be
     * unit tested without the Bukkit biome registry.
     */
    public static BiomeGroup waterGroupForY(int y) {
        return y >= 0 ? BiomeGroup.PLAINS : BiomeGroup.UNGROUPED;
    }

    public boolean isWaterBiome(Biome biome) {
        return waterBiomes.contains(biome);
    }

    public Set<Biome> getBiomes(BiomeGroup group) {
        return Set.copyOf(biomesByGroup.getOrDefault(group, Set.of()));
    }

    private void register(BiomeGroup group, Biome... biomes) {
        for (Biome biome : biomes) {
            groups.put(biome, group);
            biomesByGroup.computeIfAbsent(group, ignored -> new HashSet<>()).add(biome);
        }
    }
}
