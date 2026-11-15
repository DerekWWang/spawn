package dev.qube.spawn_9b5fbb9.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** What weather and time do to a structure: wind in storms, and slow decay by material and exposure. */
public final class Weathering {
    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][] ALL = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
    /** Masonry shows its age before it fails: block -> its worn form. */
    private static final Map<Block, Block> WORN = new HashMap<>();

    static {
        worn("stone_bricks", "cracked_stone_bricks");
        worn("deepslate_bricks", "cracked_deepslate_bricks");
        worn("deepslate_tiles", "cracked_deepslate_tiles");
        worn("nether_bricks", "cracked_nether_bricks");
        worn("polished_blackstone_bricks", "cracked_polished_blackstone_bricks");
        worn("cobblestone", "mossy_cobblestone");
    }

    private Weathering() {
    }

    private static void worn(String from, String to) {
        Block a = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(from));
        Block b = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(to));
        if (a != Blocks.AIR && b != Blocks.AIR) {
            WORN.put(a, b);
        }
    }

    /** True if the cell has a face open to the outside air. */
    public static boolean exposed(StructureScan scan, boolean[] outside, int index) {
        int x = scan.x(index);
        int y = scan.y(index);
        int z = scan.z(index);
        for (int[] d : ALL) {
            int nx = x + d[0];
            int ny = y + d[1];
            int nz = z + d[2];
            if (scan.inside(nx, ny, nz) && outside[scan.index(nx, ny, nz)]) {
                return true;
            }
        }
        return false;
    }

    public static StructuralMaterial.Exposure exposure(StructureScan scan, boolean[] outside, int index) {
        int x = scan.x(index);
        int y = scan.y(index);
        int z = scan.z(index);
        boolean open = false;
        for (int[] d : ALL) {
            int nx = x + d[0];
            int ny = y + d[1];
            int nz = z + d[2];
            if (!scan.inside(nx, ny, nz)) {
                continue;
            }
            int n = scan.index(nx, ny, nz);
            if (scan.kind[n] == StructureScan.WATER) {
                return StructuralMaterial.Exposure.SUBMERGED;
            }
            open |= outside[n];
        }
        return open ? StructuralMaterial.Exposure.WET : StructuralMaterial.Exposure.DRY;
    }

    /**
     * Storm load on one member: the stress it already carries grows with the gusts, and an exposed
     * face adds wind in proportion to how readily the material is stripped and how high it stands.
     */
    public static float stormUtilisation(StructureScan scan, StructuralAnalysis analysis, boolean[] outside, int index, float severity,
                                         int baseY) {
        float u = analysis.utilisation(index);
        if (Float.isInfinite(u)) {
            return u;
        }
        float total = u * (1 + 0.5F * severity);
        boolean windward = false;
        int x = scan.x(index);
        int y = scan.y(index);
        int z = scan.z(index);
        for (int[] d : SIDES) {
            if (scan.inside(x + d[0], y, z + d[2]) && outside[scan.index(x + d[0], y, z + d[2])]) {
                windward = true;
                break;
            }
        }
        if (!windward && scan.inside(x, y + 1, z) && outside[scan.index(x, y + 1, z)]) {
            windward = true; // roofs take uplift
        }
        if (windward) {
            total += severity * scan.material(index).wind * (1 + Math.max(0, y - baseY) / 16F);
        }
        return total;
    }

    /** Members a storm of this severity tears loose in one gust. */
    public static List<BlockPos> stormFailures(StructureScan scan, StructuralAnalysis analysis, boolean[] outside, float severity,
                                               RandomSource random) {
        int baseY = Integer.MAX_VALUE;
        for (int i = 0; i < scan.kind.length; i++) {
            if (scan.kind[i] == StructureScan.MEMBER) {
                baseY = Math.min(baseY, scan.y(i));
            }
        }
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < scan.kind.length; i++) {
            if (scan.kind[i] != StructureScan.MEMBER) {
                continue;
            }
            float u = stormUtilisation(scan, analysis, outside, i, severity, baseY);
            if (u > 1.0001F && random.nextFloat() < Math.min(1F, 0.1F + (u - 1F) * 1.5F)) {
                out.add(scan.pos(i));
            }
        }
        return out;
    }

    /**
     * Members that wear out over a span of time. Each member's chance is days/life for its material
     * and exposure; a sample of the structure is examined and the odds scaled to match.
     */
    public static List<BlockPos> decay(StructureScan scan, boolean[] outside, double days, RandomSource random, int sample) {
        List<BlockPos> out = new ArrayList<>();
        if (scan.members == 0 || days <= 0) {
            return out;
        }
        int[] memberCells = new int[scan.members];
        int k = 0;
        for (int i = 0; i < scan.kind.length && k < memberCells.length; i++) {
            if (scan.kind[i] == StructureScan.MEMBER) {
                memberCells[k++] = i;
            }
        }
        int tries = Math.min(sample, k);
        double scale = (double) k / tries;
        for (int t = 0; t < tries; t++) {
            int i = memberCells[tries == k ? t : random.nextInt(k)];
            int life = scan.material(i).life(exposure(scan, outside, i));
            if (life > 0 && random.nextDouble() < Math.min(1.0, days / life * scale)) {
                out.add(scan.pos(i));
            }
        }
        return out;
    }

    /** The worn form a block takes before it fails outright, or null if it simply goes. */
    public static BlockState wornForm(BlockState state) {
        Block next = WORN.get(state.getBlock());
        return next == null ? null : next.defaultBlockState();
    }
}
