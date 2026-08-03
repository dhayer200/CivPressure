package com.deep.civpressure.ore;

import com.deep.civpressure.biome.BiomeGroup;
import com.deep.civpressure.biome.BiomeGroupRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class OreRedistributor {
    private static final long RANDOM_SALT = 0x4349565052455353L;
    private static final int[][] NEIGHBOR_OFFSETS = {
        {1, 0, 0},
        {-1, 0, 0},
        {0, 1, 0},
        {0, -1, 0},
        {0, 0, 1},
        {0, 0, -1},
        {1, 1, 0},
        {-1, 1, 0},
        {1, -1, 0},
        {-1, -1, 0},
        {1, 0, 1},
        {1, 0, -1},
        {-1, 0, 1},
        {-1, 0, -1},
        {0, 1, 1},
        {0, 1, -1},
        {0, -1, 1},
        {0, -1, -1}
    };

    private final BiomeGroupRegistry biomeGroupRegistry;
    private final OreRedistributionPlanner planner;
    private final ProcessedChunkStore processedChunkStore;
    private final Set<ChunkKey> automaticProcessingSuppressions = new HashSet<>();

    public OreRedistributor(
            BiomeGroupRegistry biomeGroupRegistry,
            OreRates oreRates,
            ProcessedChunkStore processedChunkStore
    ) {
        this.biomeGroupRegistry = biomeGroupRegistry;
        this.planner = new OreRedistributionPlanner(oreRates);
        this.processedChunkStore = processedChunkStore;
    }

    public OreRedistributionResult process(Chunk chunk, boolean force) {
        if (chunk.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return new OreRedistributionResult(
                    OreRedistributionResult.Status.UNSUPPORTED_WORLD,
                    0,
                    0);
        }
        if (!force && processedChunkStore.isProcessed(chunk)) {
            return new OreRedistributionResult(
                    OreRedistributionResult.Status.ALREADY_PROCESSED,
                    0,
                    0);
        }

        SplittableRandom random = new SplittableRandom(seedFor(chunk, force));
        List<OreBlock> originalOres = collectOres(chunk);
        int removed = removeReducedOres(originalOres, random);
        int added = addIncreasedOres(chunk, originalOres, random);

        if (!processedChunkStore.markProcessed(chunk)) {
            return new OreRedistributionResult(
                    OreRedistributionResult.Status.PERSISTENCE_FAILED,
                    removed,
                    added);
        }
        return new OreRedistributionResult(
                OreRedistributionResult.Status.PROCESSED,
                removed,
                added);
    }

    public void suppressAutomaticProcessing(World world, int chunkX, int chunkZ) {
        automaticProcessingSuppressions.add(new ChunkKey(world.getUID(), chunkX, chunkZ));
    }

    public void restoreAutomaticProcessing(World world, int chunkX, int chunkZ) {
        automaticProcessingSuppressions.remove(new ChunkKey(world.getUID(), chunkX, chunkZ));
    }

    public boolean isAutomaticProcessingSuppressed(Chunk chunk) {
        return automaticProcessingSuppressions.contains(
                new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()));
    }

    private long seedFor(Chunk chunk, boolean force) {
        long seed = chunk.getWorld().getSeed()
                ^ ((long) chunk.getX() * 341873128712L)
                ^ ((long) chunk.getZ() * 132897987541L)
                ^ RANDOM_SALT;
        return force ? seed ^ System.nanoTime() : seed;
    }

    private List<OreBlock> collectOres(Chunk chunk) {
        List<OreBlock> ores = new ArrayList<>();
        World world = chunk.getWorld();
        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    OreType oreType = findRedistributedOre(block.getType());
                    if (oreType == null) {
                        continue;
                    }

                    BiomeGroup group = biomeGroupRegistry.resolve(block.getBiome(), y);
                    ores.add(new OreBlock(block, oreType, group));
                }
            }
        }
        return ores;
    }

    private int removeReducedOres(List<OreBlock> ores, SplittableRandom random) {
        int removed = 0;
        for (OreBlock ore : ores) {
            if (!planner.shouldRemove(ore.group(), ore.oreType(), random)) {
                continue;
            }

            boolean deepslate = ore.block().getType().name().startsWith("DEEPSLATE_");
            ore.block().setType(deepslate ? Material.DEEPSLATE : Material.STONE, false);
            removed++;
        }
        return removed;
    }

    private int addIncreasedOres(Chunk chunk, List<OreBlock> ores, SplittableRandom random) {
        int added = 0;
        for (OreBlock ore : ores) {
            int attempts = planner.extraAdditions(ore.group(), ore.oreType(), random);
            for (int attempt = 0; attempt < attempts; attempt++) {
                if (addNaturalNeighbor(chunk, ore, random)) {
                    added++;
                }
            }
        }
        return added;
    }

    private boolean addNaturalNeighbor(Chunk chunk, OreBlock source, SplittableRandom random) {
        int startIndex = random.nextInt(NEIGHBOR_OFFSETS.length);
        for (int index = 0; index < NEIGHBOR_OFFSETS.length; index++) {
            int[] offset = NEIGHBOR_OFFSETS[(startIndex + index) % NEIGHBOR_OFFSETS.length];
            int localX = source.block().getX() - (chunk.getX() << 4) + offset[0];
            int y = source.block().getY() + offset[1];
            int localZ = source.block().getZ() - (chunk.getZ() << 4) + offset[2];
            if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
                continue;
            }
            if (y < chunk.getWorld().getMinHeight() || y >= chunk.getWorld().getMaxHeight()) {
                continue;
            }

            Block target = chunk.getBlock(localX, y, localZ);
            Material targetMaterial = target.getType();
            if (targetMaterial != Material.STONE && targetMaterial != Material.DEEPSLATE) {
                continue;
            }

            target.setType(
                    source.oreType().materialFor(targetMaterial == Material.DEEPSLATE),
                    false);
            return true;
        }
        return false;
    }

    private OreType findRedistributedOre(Material material) {
        for (OreType oreType : OreType.values()) {
            if (oreType.isRedistributed() && oreType.matches(material)) {
                return oreType;
            }
        }
        return null;
    }

    private record OreBlock(Block block, OreType oreType, BiomeGroup group) {
    }

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
    }
}
