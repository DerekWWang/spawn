package dev.qube.spawn_9b5fbb9.physics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

import net.minecraft.core.BlockPos;

/**
 * Follows every member's weight down to the ground and says how hard each block is working.
 *
 * <p>Support spreads outward from the ground, and each block hands its weight (and everything it
 * carries) to its supporter. A block resting on its support adds no bending. A block hung off the
 * side of its support adds carried-load/span (span is that of the weaker of the two); one leaning
 * on the block diagonally below it, as in an arch, a corbel or a rafter, adds 40% of that; one
 * hanging underneath adds the full amount if both are tensile materials and three times as much
 * otherwise. Because every block of a cantilever carries all the blocks beyond it, the stress at
 * its tip grows with the square of its length, as a real bending moment does: timber reaches four
 * blocks, plain stone two, reinforced concrete five, steel eight, and a beam held at both ends
 * twice that. The load each block carries is also compared with its crushing strength.</p>
 *
 * <p>Utilisation above 1 means overstressed. A block with no path to the ground is floating.</p>
 */
public final class StructuralAnalysis {
    public static final float DIAGONAL = 0.4F;
    public static final float HANG_TENSILE = 1.0F;
    public static final float HANG_BRITTLE = 3.0F;

    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][] DIAGONALS_UP = {{1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}};

    public final StructureScan scan;
    /** Bending utilisation per cell (members only); infinity for floating blocks. */
    public final float[] bending;
    /** Tonnes carried by each member, its own weight included. */
    public final float[] load;
    /** Cell index of each member's supporter, -1 for blocks resting on the ground, -2 for floating blocks. */
    public final int[] parent;
    /** How each member bears on its supporter: 0 resting, 1 sideways, DIAGONAL leaning, 1 or 3 hanging. */
    private final float[] edge;
    public int floating;
    public int overstressed;
    public float worst;

    private StructuralAnalysis(StructureScan scan) {
        this.scan = scan;
        this.bending = new float[scan.kind.length];
        this.load = new float[scan.kind.length];
        this.parent = new int[scan.kind.length];
        this.edge = new float[scan.kind.length];
    }

    public static StructuralAnalysis of(StructureScan scan) {
        StructuralAnalysis a = new StructuralAnalysis(scan);
        a.run();
        return a;
    }

    /** Overall utilisation of a member: the worse of bending and crushing. */
    public float utilisation(int index) {
        if (scan.kind[index] != StructureScan.MEMBER) {
            return 0;
        }
        if (parent[index] == -2) {
            return Float.POSITIVE_INFINITY;
        }
        return Math.max(bending[index], load[index] / scan.material(index).comp);
    }

    public boolean sound() {
        return floating == 0 && overstressed == 0;
    }

    private static float step(StructuralMaterial block, StructuralMaterial supporter) {
        return block.mass / Math.min(block.span, supporter.span);
    }

    private void run() {
        int n = scan.kind.length;
        Arrays.fill(bending, Float.POSITIVE_INFINITY);
        Arrays.fill(parent, -2);
        boolean[] done = new boolean[n];
        // queue entries pack (cost bits, index); non-negative floats order the same as their bit patterns
        PriorityQueue<Long> queue = new PriorityQueue<>();
        List<Integer> order = new ArrayList<>(scan.members);

        // seed: members touching the ground (or anything solid outside the box)
        for (int i = 0; i < n; i++) {
            if (scan.kind[i] != StructureScan.MEMBER) {
                continue;
            }
            int x = scan.x(i);
            int y = scan.y(i);
            int z = scan.z(i);
            StructuralMaterial m = scan.material(i);
            float factor = Float.POSITIVE_INFINITY;
            if (scan.kind(x, y - 1, z) == StructureScan.GROUND) {
                factor = 0;
            } else {
                for (int[] d : SIDES) {
                    if (scan.kind(x + d[0], y, z + d[2]) == StructureScan.GROUND) {
                        factor = Math.min(factor, 1);
                    }
                    if (scan.kind(x + d[0], y - 1, z + d[2]) == StructureScan.GROUND) {
                        factor = Math.min(factor, DIAGONAL);
                    }
                }
                if (scan.kind(x, y + 1, z) == StructureScan.GROUND) {
                    factor = Math.min(factor, m.tensile ? HANG_TENSILE : HANG_BRITTLE);
                }
            }
            if (factor < Float.POSITIVE_INFINITY) {
                bending[i] = factor * m.mass / m.span;
                edge[i] = factor;
                parent[i] = -1;
                queue.add(pack(bending[i], i));
            }
        }

        while (!queue.isEmpty()) {
            long entry = queue.poll();
            int s = (int) entry;
            if (done[s]) {
                continue;
            }
            done[s] = true;
            order.add(s);
            int x = scan.x(s);
            int y = scan.y(s);
            int z = scan.z(s);
            StructuralMaterial sm = scan.material(s);
            float base = bending[s];
            relax(queue, done, s, x, y + 1, z, base, 0, sm);
            for (int[] d : SIDES) {
                relax(queue, done, s, x + d[0], y, z + d[2], base, 1, sm);
            }
            for (int[] d : DIAGONALS_UP) {
                relax(queue, done, s, x + d[0], y + 1, z + d[2], base, DIAGONAL, sm);
            }
            relax(queue, done, s, x, y - 1, z, base, -1, sm);
        }

        // weight flows from each block to its supporter; later-settled blocks always hang off earlier ones
        for (int i = 0; i < n; i++) {
            if (scan.kind[i] == StructureScan.MEMBER) {
                load[i] = scan.material(i).mass;
            }
        }
        for (int k = order.size() - 1; k >= 0; k--) {
            int i = order.get(k);
            if (parent[i] >= 0) {
                load[parent[i]] += load[i];
            }
        }
        // bending again, now charging each joint for everything it carries (supporters settle first)
        for (int i : order) {
            StructuralMaterial m = scan.material(i);
            if (parent[i] < 0) {
                bending[i] = edge[i] * load[i] / m.span;
            } else {
                float span = Math.min(m.span, scan.material(parent[i]).span);
                bending[i] = bending[parent[i]] + edge[i] * load[i] / span;
            }
        }
        for (int i = 0; i < n; i++) {
            if (scan.kind[i] != StructureScan.MEMBER) {
                continue;
            }
            float u = utilisation(i);
            if (Float.isInfinite(u)) {
                floating++;
            } else {
                if (u > 1.0001F) {
                    overstressed++;
                }
                worst = Math.max(worst, u);
            }
        }
    }

    /** @param factor 0 = resting on top, 1 = to the side, DIAGONAL = leaning, -1 = hanging below */
    private void relax(PriorityQueue<Long> queue, boolean[] done, int supporter, int x, int y, int z, float base, float factor,
                       StructuralMaterial sm) {
        if (!scan.inside(x, y, z)) {
            return;
        }
        int b = scan.index(x, y, z);
        if (scan.kind[b] != StructureScan.MEMBER || done[b]) {
            return;
        }
        StructuralMaterial bm = scan.material(b);
        float f = factor < 0 ? (bm.tensile && sm.tensile ? HANG_TENSILE : HANG_BRITTLE) : factor;
        float cost = base + step(bm, sm) * f;
        if (cost < bending[b]) {
            bending[b] = cost;
            edge[b] = f;
            parent[b] = supporter;
            queue.add(pack(cost, b));
        }
    }

    private static long pack(float cost, int index) {
        return ((long) Float.floatToIntBits(cost) << 32) | (index & 0xFFFFFFFFL);
    }

    /** World positions of members whose utilisation exceeds the threshold, worst first is not guaranteed. */
    public List<BlockPos> above(float threshold) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < scan.kind.length; i++) {
            if (scan.kind[i] == StructureScan.MEMBER && utilisation(i) > threshold) {
                out.add(scan.pos(i));
            }
        }
        return out;
    }

    public List<BlockPos> floatingBlocks() {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < scan.kind.length; i++) {
            if (scan.kind[i] == StructureScan.MEMBER && parent[i] == -2) {
                out.add(scan.pos(i));
            }
        }
        return out;
    }

    /** The most stressed member, or null if there are none. */
    public BlockPos worstBlock() {
        int best = -1;
        float u = -1;
        for (int i = 0; i < scan.kind.length; i++) {
            if (scan.kind[i] == StructureScan.MEMBER) {
                float v = utilisation(i);
                if (v > u) {
                    u = v;
                    best = i;
                }
            }
        }
        return best < 0 ? null : scan.pos(best);
    }
}
