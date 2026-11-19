package dev.qube.spawn_9b5fbb9.physics;

import java.util.ArrayDeque;

import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A snapshot of the blocks in a box, plus one block of padding all round, sorted into what matters
 * structurally: members (built blocks), ground (natural terrain, and anything solid outside the
 * box, which is taken as rigid support), open space and water.
 */
public final class StructureScan {
    public static final byte OPEN = 0;
    public static final byte MEMBER = 1;
    public static final byte GROUND = 2;
    public static final byte WATER = 3;

    public static final byte BED = 1;
    public static final byte DOOR = 2;
    public static final byte BLOCK_ENTITY = 3;

    public static final TagKey<Block> TERRAIN = tag("physics/terrain");
    public static final TagKey<Block> NON_STRUCTURAL = tag("physics/non_structural");

    public final Level level;
    /** World position of cell (0,0,0), which is in the padding. */
    public final BlockPos min;
    public final int sx;
    public final int sy;
    public final int sz;
    public final byte[] kind;
    public final byte[] material;
    /** Fittings noticed while scanning (they need not be members): BED, DOOR or FITTING_ENTITY. */
    public final byte[] fitting;
    /** False if part of the box was in unloaded chunks; nothing may be concluded from such a scan. */
    public final boolean complete;
    public int members;
    public float mass;

    private StructureScan(Level level, BlockPos min, int sx, int sy, int sz, boolean complete) {
        this.level = level;
        this.min = min;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.kind = new byte[sx * sy * sz];
        this.material = new byte[sx * sy * sz];
        this.fitting = new byte[sx * sy * sz];
        this.complete = complete;
    }

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, path));
    }

    /** Scans the inclusive box from boxMin to boxMax. */
    public static StructureScan of(Level level, BlockPos boxMin, BlockPos boxMax) {
        BlockPos min = boxMin.offset(-1, -1, -1);
        int sx = boxMax.getX() - boxMin.getX() + 3;
        int sy = boxMax.getY() - boxMin.getY() + 3;
        int sz = boxMax.getZ() - boxMin.getZ() + 3;
        boolean loaded = true;
        for (int cx = min.getX() >> 4; cx <= (min.getX() + sx - 1) >> 4 && loaded; cx++) {
            for (int cz = min.getZ() >> 4; cz <= (min.getZ() + sz - 1) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    loaded = false;
                    break;
                }
            }
        }
        StructureScan scan = new StructureScan(level, min, sx, sy, sz, loaded);
        if (!loaded) {
            return scan;
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bottom = level.getMinY();
        int top = level.getMaxY();
        for (int y = 0; y < sy; y++) {
            int wy = min.getY() + y;
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int i = scan.index(x, y, z);
                    if (wy < bottom) {
                        scan.kind[i] = GROUND; // below the world: bedrock as far as a structure is concerned
                        continue;
                    }
                    if (wy > top) {
                        continue;
                    }
                    m.set(min.getX() + x, wy, min.getZ() + z);
                    BlockState state = level.getBlockState(m);
                    byte k = classify(state);
                    if (!state.isAir() && scan.inBox(x, y, z)) {
                        scan.fitting[i] = fitting(state);
                    }
                    if (k == MEMBER) {
                        if (!scan.inBox(x, y, z) || state.is(TERRAIN)) {
                            k = GROUND;
                        } else {
                            StructuralMaterial mat = StructuralMaterial.of(state);
                            scan.material[i] = (byte) mat.ordinal();
                            scan.members++;
                            scan.mass += mat.mass;
                        }
                    }
                    scan.kind[i] = k;
                }
            }
        }
        return scan;
    }

    /** One bed counts once (at its head), one door once (at its foot). */
    private static byte fitting(BlockState state) {
        if (state.getBlock() instanceof AbstractBedBlock) {
            return state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD
                    ? BED : 0;
        }
        if (state.is(BlockTags.DOORS)) {
            return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? DOOR : 0;
        }
        return state.hasBlockEntity() ? BLOCK_ENTITY : 0;
    }

    /** MEMBER for anything that stands as a block, WATER, or OPEN. Terrain is sorted out by the caller. */
    public static byte classify(BlockState state) {
        if (state.isAir() || state.is(NON_STRUCTURAL)) {
            return OPEN;
        }
        if (state.isSolid() || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)) {
            return MEMBER;
        }
        return state.getFluidState().is(FluidTags.WATER) ? WATER : OPEN;
    }

    public int index(int x, int y, int z) {
        return (y * sz + z) * sx + x;
    }

    public int x(int index) {
        return index % sx;
    }

    public int z(int index) {
        return (index / sx) % sz;
    }

    public int y(int index) {
        return index / (sx * sz);
    }

    public boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz;
    }

    /** True for cells of the box proper, false for the padding. */
    public boolean inBox(int x, int y, int z) {
        return x >= 1 && y >= 1 && z >= 1 && x < sx - 1 && y < sy - 1 && z < sz - 1;
    }

    public byte kind(int x, int y, int z) {
        return inside(x, y, z) ? kind[index(x, y, z)] : OPEN;
    }

    public BlockPos pos(int index) {
        return new BlockPos(min.getX() + x(index), min.getY() + y(index), min.getZ() + z(index));
    }

    public StructuralMaterial material(int index) {
        return StructuralMaterial.byOrdinal(material[index]);
    }

    public boolean passable(int index) {
        return kind[index] == OPEN || kind[index] == WATER;
    }

    /**
     * Marks every passable cell that connects to the outside of the box. Passable cells left unmarked
     * are enclosed: rooms.
     */
    public boolean[] outside() {
        boolean[] out = new boolean[kind.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    if (!inBox(x, y, z)) {
                        int i = index(x, y, z);
                        if (passable(i)) {
                            out[i] = true;
                            queue.add(i);
                        }
                    }
                }
            }
        }
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = x(i);
            int y = y(i);
            int z = z(i);
            for (int[] d : dirs) {
                int nx = x + d[0];
                int ny = y + d[1];
                int nz = z + d[2];
                if (inside(nx, ny, nz)) {
                    int n = index(nx, ny, nz);
                    if (!out[n] && passable(n)) {
                        out[n] = true;
                        queue.add(n);
                    }
                }
            }
        }
        return out;
    }
}
