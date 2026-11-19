package dev.qube.spawn_9b5fbb9.physics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.qube.spawn_9b5fbb9.Config;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Things fall. A block or group of blocks left with no connection to the ground drops, and falling
 * blocks hurt whoever is underneath. Only removals caused by players and explosions are examined,
 * on the tick after they happen; untouched terrain is never searched.
 */
public final class Collapse {
    /** At most this many blocks of one collapse become falling entities; the rest break where they are. */
    public static final int MAX_FALLING = 256;
    private static final int PER_TICK = 48;
    private static final Map<ResourceKey<Level>, ArrayDeque<BlockPos>> DISTURBED = new HashMap<>();

    private Collapse() {
    }

    public static void clear() {
        DISTURBED.clear();
    }

    /** Notes that the block at pos is being removed; what rested on it is checked next tick. */
    public static void disturbed(Level level, BlockPos pos) {
        if (level instanceof ServerLevel && Config.integer(Config.FLOATING_LIMIT) > 0) {
            ArrayDeque<BlockPos> queue = DISTURBED.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>());
            if (queue.size() < 4096) {
                queue.add(pos.immutable());
            }
        }
    }

    public static void tick(ServerLevel level) {
        ArrayDeque<BlockPos> queue = DISTURBED.get(level.dimension());
        if (queue == null || queue.isEmpty()) {
            return;
        }
        int cap = Config.integer(Config.FLOATING_LIMIT);
        LongOpenHashSet anchored = new LongOpenHashSet();
        for (int i = 0; i < PER_TICK && !queue.isEmpty(); i++) {
            BlockPos pos = queue.poll();
            if (cap <= 0 || !level.isLoaded(pos) || holds(level.getBlockState(pos))) {
                continue; // something is still (or again) there
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 0 || Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 3) {
                            continue;
                        }
                        BlockPos n = pos.offset(dx, dy, dz);
                        List<BlockPos> loose = floatingComponent(level, n, cap, anchored);
                        if (!loose.isEmpty()) {
                            drop(level, loose);
                        }
                    }
                }
            }
        }
    }

    /** A block that can carry or be carried: solid, and not foliage. */
    public static boolean holds(BlockState state) {
        return !state.isAir() && state.isSolid() && !state.is(StructureScan.NON_STRUCTURAL);
    }

    /**
     * The connected group of blocks containing start, if that group is small and touches nothing that
     * anchors it; empty if it is anchored. Blocks touching by a face or an edge count as connected.
     * A group larger than the cap is taken to be part of the terrain.
     */
    public static List<BlockPos> floatingComponent(ServerLevel level, BlockPos start, int cap, LongOpenHashSet anchored) {
        if (!level.isLoaded(start) || !holds(level.getBlockState(start)) || anchored.contains(start.asLong())) {
            return List.of();
        }
        List<BlockPos> found = new ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.asLong());
        boolean fixed = false;
        int floor = level.getMinY();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && !fixed) {
            BlockPos p = queue.poll();
            found.add(p);
            if (found.size() > cap || p.getY() <= floor) {
                fixed = true;
                break;
            }
            for (int dx = -1; dx <= 1 && !fixed; dx++) {
                for (int dy = -1; dy <= 1 && !fixed; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int manhattan = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        if (manhattan == 0 || manhattan == 3) {
                            continue;
                        }
                        m.set(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                        long key = m.asLong();
                        if (seen.contains(key)) {
                            continue;
                        }
                        if (!level.isLoaded(m) || anchored.contains(key)) {
                            fixed = true; // runs off the loaded world, or joins something known to be anchored
                            break;
                        }
                        BlockState s = level.getBlockState(m);
                        if (s.is(Blocks.BEDROCK)) {
                            fixed = true;
                            break;
                        }
                        if (holds(s)) {
                            seen.add(key);
                            queue.add(m.immutable());
                        }
                    }
                }
            }
        }
        if (fixed) {
            anchored.addAll(seen);
            return List.of();
        }
        return found;
    }

    /**
     * Brings blocks down, lowest first. Plain blocks become falling blocks that hurt what they land
     * on; blocks with contents (chests, stations) break and spill instead.
     *
     * @return how many blocks came down
     */
    public static int drop(ServerLevel level, Collection<BlockPos> blocks) {
        List<BlockPos> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingInt(BlockPos::getY));
        int falling = 0;
        int n = 0;
        for (BlockPos pos : sorted) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(Blocks.BEDROCK)) {
                continue;
            }
            if (state.hasBlockEntity() || !state.isSolid()) {
                level.destroyBlock(pos, true);
            } else if (falling < MAX_FALLING) {
                FallingBlockEntity entity = FallingBlockEntity.fall(level, pos, state);
                entity.setHurtsEntities(2.0F, 40);
                falling++;
            } else {
                level.destroyBlock(pos, false);
            }
            n++;
        }
        return n;
    }
}
