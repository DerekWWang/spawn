package dev.qube.spawn_9b5fbb9.physics;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * Water pressure on walls. For every stretch of wall with water against one face, per metre run:
 *
 * <pre>
 *   push          F = 1/2 rho g h^2          acting h/3 above the bed
 *   overturning   M = rho g h^3 / 6
 *   resisting     the wall's weight times its lever arm about the dry-side toe, plus, for tensile
 *                 walls driven into the bed (timber piles, steel sheet, reinforced concrete), the
 *                 bending strength of the section
 *   sliding       friction 0.6 x weight against F, unless the wall is driven into the bed
 * </pre>
 *
 * So a plain stone or concrete wall must be thick (about half its retained depth) to stand as a
 * gravity wall, while a single row of timber piles or sheet steel holds if it is embedded at least
 * a third of the depth it retains. Water at the same level on both sides cancels out.
 */
public final class Hydrostatics {
    private static final float G = 9.81F;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private Hydrostatics() {
    }

    /** One loaded stretch of wall: the column at (x, z) with water of the given depth against it. */
    public record Section(BlockPos base, int dx, int dz, int depth, int thickness, int height, int embedment, boolean anchored,
                          float utilisation, boolean overtopped) {
        public boolean holds() {
            return utilisation <= 1.0001F;
        }
    }

    public record Report(List<Section> sections, float worst, int maxDepth) {
        public boolean holds() {
            return worst <= 1.0001F;
        }
    }

    /** Bending strength in kN/m2 that a tensile wall section can mobilise once it is fixed in the bed. */
    private static float bendingStrength(StructuralMaterial m) {
        return switch (m) {
            case TIMBER -> 10_000F;
            case REINFORCED -> 5_000F;
            case IRON, STAINLESS -> 150_000F;
            default -> 0F;
        };
    }

    /**
     * @param surge extra blocks of water depth to design for (storm surge); 0 for the water as it stands
     */
    public static Report analyse(StructureScan scan, int surge) {
        List<Section> out = new ArrayList<>();
        float worst = 0;
        int maxDepth = 0;
        for (int i = 0; i < scan.kind.length; i++) {
            if (scan.kind[i] != StructureScan.MEMBER) {
                continue;
            }
            int x = scan.x(i);
            int y = scan.y(i);
            int z = scan.z(i);
            for (int[] d : SIDES) {
                // (dx, dz) points from the wall towards the water
                if (scan.kind(x + d[0], y, z + d[1]) != StructureScan.WATER) {
                    continue;
                }
                if (scan.kind(x, y - 1, z) == StructureScan.MEMBER && scan.kind(x + d[0], y - 1, z + d[1]) == StructureScan.WATER) {
                    continue; // not the bottom of this wet face
                }
                Section s = section(scan, x, y, z, d[0], d[1], surge);
                if (s != null) {
                    out.add(s);
                    worst = Math.max(worst, s.utilisation());
                    maxDepth = Math.max(maxDepth, s.depth());
                }
            }
        }
        return new Report(out, worst, maxDepth);
    }

    private static Section section(StructureScan scan, int x, int y0, int z, int dx, int dz, int surge) {
        int nx = -dx;
        int nz = -dz;
        // depth of water against the face, and the height of wall available above the bed
        int depth = 0;
        while (scan.kind(x, y0 + depth, z) == StructureScan.MEMBER && scan.kind(x + dx, y0 + depth, z + dz) == StructureScan.WATER) {
            depth++;
        }
        int height = depth;
        while (scan.kind(x, y0 + height, z) == StructureScan.MEMBER) {
            height++;
        }
        int thickness = 0;
        while (scan.kind(x + nx * thickness, y0, z + nz * thickness) == StructureScan.MEMBER) {
            thickness++;
        }
        // water standing against the far face pushes back
        int back = 0;
        while (back < depth && scan.kind(x + nx * thickness, y0 + back, z + nz * thickness) == StructureScan.WATER) {
            back++;
        }
        if (back >= depth) {
            return null;
        }
        int design = depth + surge;
        boolean overtopped = design > height;
        design = Math.min(design, height);
        float push = 0.5F * G * (design * design - back * back);
        float overturning = G * (design * design * design - back * back * back) / 6F;

        float weight = 0;
        float resisting = 0;
        for (int y = y0; scan.kind(x, y, z) == StructureScan.MEMBER; y++) {
            for (int j = 0; scan.kind(x + nx * j, y, z + nz * j) == StructureScan.MEMBER; j++) {
                float w = scan.material(scan.index(x + nx * j, y, z + nz * j)).mass * G;
                weight += w;
                resisting += w * Math.max(0F, thickness - j - 0.5F);
            }
        }
        int embedment = 0;
        while (scan.kind(x, y0 - 1 - embedment, z) == StructureScan.MEMBER) {
            embedment++;
        }
        StructuralMaterial face = scan.material(scan.index(x, y0, z));
        boolean anchored = face.tensile && embedment >= Math.max(1, (design + 2) / 3);
        if (anchored) {
            resisting += bendingStrength(face) * thickness * thickness / 6F;
        }
        float u = resisting <= 0 ? Float.POSITIVE_INFINITY : overturning / resisting;
        if (!anchored) {
            u = Math.max(u, weight <= 0 ? Float.POSITIVE_INFINITY : push / (0.6F * weight));
        }
        return new Section(scan.pos(scan.index(x, y0, z)), dx, dz, depth, thickness, height, embedment, anchored, u, overtopped);
    }

    /** The blocks of a failed section that give way: the wall from the water line down, through its thickness. */
    public static List<BlockPos> breach(StructureScan scan, Section s) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos base = s.base();
        int x = base.getX() - scan.min.getX();
        int y0 = base.getY() - scan.min.getY();
        int z = base.getZ() - scan.min.getZ();
        for (int y = y0; y < y0 + s.height(); y++) {
            for (int j = 0; j < s.thickness(); j++) {
                int cx = x - s.dx() * j;
                int cz = z - s.dz() * j;
                if (scan.kind(cx, y, cz) == StructureScan.MEMBER) {
                    out.add(scan.pos(scan.index(cx, y, cz)));
                }
            }
        }
        return out;
    }
}
