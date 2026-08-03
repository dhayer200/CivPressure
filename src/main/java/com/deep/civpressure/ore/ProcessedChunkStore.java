package com.deep.civpressure.ore;

import com.deep.civpressure.CivPressurePlugin;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.World;

public final class ProcessedChunkStore {
    private static final String FILE_NAME = "processed_chunks.dat";

    private final CivPressurePlugin plugin;
    private final Path file;
    private final Set<ChunkKey> processedChunks = new HashSet<>();

    public ProcessedChunkStore(CivPressurePlugin plugin) {
        this.plugin = plugin;
        file = plugin.getDataFolder().toPath().resolve(FILE_NAME);
    }

    public void load() {
        processedChunks.clear();
        if (!Files.exists(file)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                ChunkKey key = parse(line);
                if (key != null) {
                    processedChunks.add(key);
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load " + FILE_NAME, exception);
        }
    }

    public boolean isProcessed(Chunk chunk) {
        return isProcessed(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    public boolean isProcessed(World world, int chunkX, int chunkZ) {
        return processedChunks.contains(new ChunkKey(world.getUID(), chunkX, chunkZ));
    }

    public boolean markProcessed(Chunk chunk) {
        ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (!processedChunks.add(key)) {
            return true;
        }

        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(
                    file,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                writer.write(format(key));
                writer.newLine();
            }
            return true;
        } catch (IOException exception) {
            processedChunks.remove(key);
            plugin.getLogger().log(Level.SEVERE, "Could not persist processed chunk " + format(key), exception);
            return false;
        }
    }

    public boolean unmark(World world, int chunkX, int chunkZ) {
        boolean removed = processedChunks.remove(new ChunkKey(world.getUID(), chunkX, chunkZ));
        if (removed) {
            rewrite();
        }
        return removed;
    }

    public int unmarkRadius(World world, int centerChunkX, int centerChunkZ, int radius) {
        int removed = 0;
        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                if (processedChunks.remove(new ChunkKey(world.getUID(), chunkX, chunkZ))) {
                    removed++;
                }
            }
        }
        if (removed > 0) {
            rewrite();
        }
        return removed;
    }

    public int clear(World world) {
        int removed = 0;
        Iterator<ChunkKey> iterator = processedChunks.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().worldId().equals(world.getUID())) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            rewrite();
        }
        return removed;
    }

    public int clearAll() {
        int removed = processedChunks.size();
        processedChunks.clear();
        rewrite();
        return removed;
    }

    private void rewrite() {
        try {
            Files.createDirectories(file.getParent());
            Path temporaryFile = file.resolveSibling(FILE_NAME + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(
                    temporaryFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                for (ChunkKey key : processedChunks) {
                    writer.write(format(key));
                    writer.newLine();
                }
            }
            moveReplacing(temporaryFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not rewrite " + FILE_NAME, exception);
        }
    }

    private void moveReplacing(Path temporaryFile) throws IOException {
        try {
            Files.move(
                    temporaryFile,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ChunkKey parse(String line) {
        String[] parts = line.split(",", -1);
        if (parts.length != 3) {
            plugin.getLogger().warning("Ignoring malformed processed chunk record: " + line);
            return null;
        }

        try {
            return new ChunkKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Ignoring malformed processed chunk record: " + line);
            return null;
        }
    }

    private String format(ChunkKey key) {
        return key.worldId() + "," + key.chunkX() + "," + key.chunkZ();
    }

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
    }
}
