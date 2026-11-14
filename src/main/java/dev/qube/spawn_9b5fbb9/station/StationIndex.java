package dev.qube.spawn_9b5fbb9.station;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Where every loaded station controller is, per dimension and station type, so presence checks
 * never scan the world. Server thread only. Entries are added when a controller's block entity
 * loads and removed when it unloads or is broken; a stale entry is harmless because every
 * lookup re-reads the block entity and skips unloaded chunks.
 */
public final class StationIndex {
    private static final Map<ResourceKey<Level>, Map<String, Set<BlockPos>>> INDEX = new HashMap<>();

    private StationIndex() {
    }

    static void add(Level level, String stationId, BlockPos pos) {
        if (level instanceof ServerLevel) {
            INDEX.computeIfAbsent(level.dimension(), k -> new HashMap<>()).computeIfAbsent(stationId, k -> new HashSet<>())
                    .add(pos.immutable());
        }
    }

    static void remove(Level level, String stationId, BlockPos pos) {
        if (level instanceof ServerLevel) {
            Map<String, Set<BlockPos>> byType = INDEX.get(level.dimension());
            if (byType != null) {
                Set<BlockPos> set = byType.get(stationId);
                if (set != null) {
                    set.remove(pos);
                }
            }
        }
    }

    public static void clear() {
        INDEX.clear();
    }

    /** Every loaded, formed station of this type in the level. */
    public static List<StationBlockEntity> formed(Level level, String stationId) {
        List<StationBlockEntity> out = new ArrayList<>();
        Map<String, Set<BlockPos>> byType = INDEX.get(level.dimension());
        if (byType == null) {
            return out;
        }
        Set<BlockPos> set = byType.get(stationId);
        if (set == null) {
            return out;
        }
        for (BlockPos pos : List.copyOf(set)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof StationBlockEntity station && !station.isRemoved() && stationId.equals(station.stationId())
                    && station.isFormed()) {
                out.add(station);
            } else if (!(be instanceof StationBlockEntity)) {
                set.remove(pos);
            }
        }
        return out;
    }

    /** Formed stations of this type whose footprint lies within radius blocks of area (edge to edge). */
    public static List<StationBlockEntity> formedNear(Level level, String stationId, AABB area, double radius) {
        List<StationBlockEntity> out = new ArrayList<>();
        for (StationBlockEntity s : formed(level, stationId)) {
            if (distance(s.bounds(), area) <= radius) {
                out.add(s);
            }
        }
        return out;
    }

    public static double distance(AABB a, AABB b) {
        double dx = Math.max(0, Math.max(a.minX - b.maxX, b.minX - a.maxX));
        double dy = Math.max(0, Math.max(a.minY - b.maxY, b.minY - a.maxY));
        double dz = Math.max(0, Math.max(a.minZ - b.maxZ, b.minZ - a.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
