package dev.marblegate.superpipeslide.client.renderer.projection;

import dev.marblegate.superpipeslide.common.block.station.PlatformProjectorBlockEntity;
import dev.marblegate.superpipeslide.common.block.station.StationNameProjectorBlockEntity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Per-frame projector discovery cache.
 *
 * <p>The renderers used to scan every block entity in a 19x19 chunk square every
 * frame, once for each projector type. This index keeps that scan bounded to
 * chunk refreshes and gives each renderer only the matching projector positions.
 */
public final class ClientProjectionProjectorIndex {
    private static final int CHUNK_REFRESH_TICKS = 20;
    private static final Map<Long, ChunkEntry> CHUNKS = new HashMap<>();
    private static ClientLevel indexedLevel;
    private static FrameProjectors frameProjectors;

    private ClientProjectionProjectorIndex() {
    }

    public static void clear() {
        CHUNKS.clear();
        indexedLevel = null;
        frameProjectors = null;
    }

    public static void forStationNameProjectors(ClientLevel level, int centerChunkX, int centerChunkZ, int radius, Consumer<StationNameProjectorBlockEntity> consumer) {
        nearbyProjectors(level, centerChunkX, centerChunkZ, radius).stationNameProjectors().forEach(consumer);
    }

    public static void forPlatformProjectors(ClientLevel level, int centerChunkX, int centerChunkZ, int radius, Consumer<PlatformProjectorBlockEntity> consumer) {
        if (prepare(level)) {
            return;
        }
        nearbyProjectors(level, centerChunkX, centerChunkZ, radius).platformProjectors().forEach(consumer);
    }

    public static Projectors nearbyProjectors(ClientLevel level, int centerChunkX, int centerChunkZ, int radius) {
        if (prepare(level)) {
            return Projectors.EMPTY;
        }
        long gameTime = level.getGameTime();
        FrameProjectors cached = frameProjectors;
        if (cached != null && cached.matches(level, centerChunkX, centerChunkZ, radius, gameTime)) {
            return cached.projectors();
        }
        List<StationNameProjectorBlockEntity> station = new ArrayList<>();
        List<PlatformProjectorBlockEntity> platform = new ArrayList<>();
        visitChunks(level, centerChunkX, centerChunkZ, radius, gameTime, entry -> {
            for (BlockPos pos : entry.platformProjectors()) {
                if (level.getBlockEntity(pos) instanceof PlatformProjectorBlockEntity projector) {
                    platform.add(projector);
                }
            }
            for (BlockPos pos : entry.stationNameProjectors()) {
                if (level.getBlockEntity(pos) instanceof StationNameProjectorBlockEntity projector) {
                    station.add(projector);
                }
            }
        });
        Projectors projectors = new Projectors(List.copyOf(station), List.copyOf(platform));
        frameProjectors = new FrameProjectors(level, centerChunkX, centerChunkZ, radius, gameTime, projectors);
        return projectors;
    }

    private static boolean prepare(ClientLevel level) {
        if (level == null) {
            clear();
            return true;
        }
        if (indexedLevel != level) {
            CHUNKS.clear();
            frameProjectors = null;
            indexedLevel = level;
        }
        return false;
    }

    private static void visitChunks(ClientLevel level, int centerChunkX, int centerChunkZ, int radius, long gameTime, Consumer<ChunkEntry> consumer) {
        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                ChunkEntry entry = entryFor(level, chunkX, chunkZ, gameTime);
                if (entry != null && !entry.empty()) {
                    consumer.accept(entry);
                }
            }
        }
    }

    private static ChunkEntry entryFor(ClientLevel level, int chunkX, int chunkZ, long gameTime) {
        long key = chunkKey(chunkX, chunkZ);
        ChunkEntry existing = CHUNKS.get(key);
        if (existing != null && !existing.needsRefresh(gameTime)) {
            return existing;
        }
        if (!(level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) {
            CHUNKS.remove(key);
            return null;
        }
        ChunkEntry refreshed = ChunkEntry.scan(chunk, gameTime);
        CHUNKS.put(key, refreshed);
        return refreshed;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    private record ChunkEntry(List<BlockPos> stationNameProjectors, List<BlockPos> platformProjectors, int blockEntityCount, long lastRefreshTick) {
        static ChunkEntry scan(LevelChunk chunk, long gameTime) {
            Collection<BlockEntity> blockEntities = chunk.getBlockEntities().values();
            List<BlockPos> station = new ArrayList<>();
            List<BlockPos> platform = new ArrayList<>();
            for (BlockEntity blockEntity : blockEntities) {
                if (blockEntity instanceof StationNameProjectorBlockEntity) {
                    station.add(blockEntity.getBlockPos().immutable());
                } else if (blockEntity instanceof PlatformProjectorBlockEntity) {
                    platform.add(blockEntity.getBlockPos().immutable());
                }
            }
            return new ChunkEntry(List.copyOf(station), List.copyOf(platform), blockEntities.size(), gameTime);
        }

        boolean needsRefresh(long gameTime) {
            return gameTime - this.lastRefreshTick >= CHUNK_REFRESH_TICKS;
        }

        boolean empty() {
            return this.stationNameProjectors.isEmpty() && this.platformProjectors.isEmpty();
        }
    }

    public record Projectors(List<StationNameProjectorBlockEntity> stationNameProjectors, List<PlatformProjectorBlockEntity> platformProjectors) {
        private static final Projectors EMPTY = new Projectors(List.of(), List.of());
    }

    private record FrameProjectors(ClientLevel level, int centerChunkX, int centerChunkZ, int radius, long gameTime, Projectors projectors) {
        private boolean matches(ClientLevel level, int centerChunkX, int centerChunkZ, int radius, long gameTime) {
            return this.level == level
                    && this.centerChunkX == centerChunkX
                    && this.centerChunkZ == centerChunkZ
                    && this.radius == radius
                    && this.gameTime == gameTime;
        }
    }
}
