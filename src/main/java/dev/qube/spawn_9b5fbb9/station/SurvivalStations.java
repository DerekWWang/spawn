package dev.qube.spawn_9b5fbb9.station;

import java.util.Map;

import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

/**
 * The tech tree's stations that the survival rules build as workstations of their own: the kiln, bloomery,
 * forge and loom, the machine shop and the other works. Wherever the tree asks for one of these, a survival
 * workstation of that kind within reach is the station. The functions the tree performed at them are done by
 * hand beside the workstation (the generator turns them into hand work that needs the station near).
 */
public final class SurvivalStations {
    /** Tree stations whose survival counterpart has another name. */
    private static final Map<String, String> BLOCK_FOR = Map.of("steam_engine", "stationary_engine");

    private SurvivalStations() {
    }

    /** True if the survival rules own this station id. */
    public static boolean owned(String stationId) {
        return TechTree.get().survival.contains(stationId) && TechTree.get().stationsById.containsKey(stationId);
    }

    /** The survival block that stands in for the station, or air if there is none. */
    public static Block block(String stationId) {
        Identifier id = Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, BLOCK_FOR.getOrDefault(stationId, stationId));
        return BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.AIR);
    }

    /** True if a survival workstation of this kind stands within radius blocks of area (edge to edge), in loaded land. */
    public static boolean near(Level level, String stationId, AABB area, double radius) {
        Block block = block(stationId);
        if (block == Blocks.AIR) {
            return false;
        }
        AABB box = area.inflate(radius);
        int minX = Mth.floor(box.minX), maxX = Mth.floor(box.maxX);
        int minY = Math.max(level.getMinY(), Mth.floor(box.minY)), maxY = Math.min(level.getMaxY(), Mth.floor(box.maxY));
        int minZ = Mth.floor(box.minZ), maxZ = Mth.floor(box.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    int index = chunk.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= chunk.getSectionsCount()) {
                        continue;
                    }
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(block))) {
                        continue;
                    }
                    for (int x = Math.max(minX, cx << 4); x <= Math.min(maxX, (cx << 4) + 15); x++) {
                        for (int z = Math.max(minZ, cz << 4); z <= Math.min(maxZ, (cz << 4) + 15); z++) {
                            for (int y = Math.max(minY, sy << 4); y <= Math.min(maxY, (sy << 4) + 15); y++) {
                                if (section.getBlockState(x & 15, y & 15, z & 15).is(block)
                                        && StationIndex.distance(new AABB(pos.set(x, y, z)), area) <= radius) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}
