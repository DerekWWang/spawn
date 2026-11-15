package dev.qube.spawn_9b5fbb9.engineering;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.physics.Hydrostatics;
import dev.qube.spawn_9b5fbb9.physics.StructuralAnalysis;
import dev.qube.spawn_9b5fbb9.physics.StructuralMaterial;
import dev.qube.spawn_9b5fbb9.physics.StructureScan;
import dev.qube.spawn_9b5fbb9.station.StationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What a survey of a building finds: its blocks and loads, the space it encloses, and whether it
 * provides what its use requires.
 */
public final class Assessment {
    public final StructureScan scan;
    public final StructuralAnalysis analysis;
    public final boolean[] outside;
    /** False if the building is not fully loaded; nothing else is then filled in. */
    public final boolean complete;
    public int interiorVolume;
    public int beds;
    public int doors;
    public final List<BlockPos> containers = new ArrayList<>();
    public int containerSlots;
    public int penArea;
    public Hydrostatics.Report water;
    public Hydrostatics.Report waterInStorm;
    public final Map<StructuralMaterial, Integer> materials = new EnumMap<>(StructuralMaterial.class);
    /** How many residents, slots or animals the building serves, depending on its use. */
    public int capacity;
    public boolean enclosed;
    public boolean sound;
    public boolean functional;
    public final List<String> problems = new ArrayList<>();

    private Assessment(StructureScan scan) {
        this.scan = scan;
        this.complete = scan.complete;
        this.analysis = scan.complete ? StructuralAnalysis.of(scan) : null;
        this.outside = scan.complete ? scan.outside() : new boolean[0];
    }

    public static Assessment of(ServerLevel level, Building b) {
        Assessment a = new Assessment(StructureScan.of(level, b.min(), b.max()));
        if (!a.complete) {
            a.problems.add("Part of the building is not loaded");
            return a;
        }
        a.measure(level, b);
        a.judge(b);
        return a;
    }

    private void measure(ServerLevel level, Building b) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < scan.kind.length; i++) {
            int x = scan.x(i);
            int y = scan.y(i);
            int z = scan.z(i);
            if (!scan.inBox(x, y, z)) {
                continue;
            }
            switch (scan.fitting[i]) {
                case StructureScan.BED -> beds++;
                case StructureScan.DOOR -> doors++;
                case StructureScan.BLOCK_ENTITY -> {
                    m.set(scan.min.getX() + x, scan.min.getY() + y, scan.min.getZ() + z);
                    BlockEntity be = level.getBlockEntity(m);
                    if (be instanceof Container c && !(be instanceof StationBlockEntity)) {
                        containers.add(m.immutable());
                        containerSlots += c.getContainerSize();
                    }
                }
                default -> { }
            }
            if (scan.passable(i)) {
                if (!outside[i]) {
                    interiorVolume++;
                }
            } else if (scan.kind[i] == StructureScan.MEMBER) {
                materials.merge(scan.material(i), 1, Integer::sum);
            }
        }
        enclosed = interiorVolume >= 4;
        penArea = fencedArea(b);
        water = Hydrostatics.analyse(scan, 0);
        waterInStorm = Hydrostatics.analyse(scan, 2);
        sound = !Config.bool(Config.STRUCTURAL_STRESS) || analysis.sound();
    }

    /** Standing room at the cornerstone's level that cannot be walked out of: ground closed in by fences or walls. */
    private int fencedArea(Building b) {
        int y = b.extents[Building.DOWN] + 1;
        if (y < 1 || y >= scan.sy - 1) {
            return 0;
        }
        boolean[] reached = new boolean[scan.sx * scan.sz];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int z = 0; z < scan.sz; z++) {
            for (int x = 0; x < scan.sx; x++) {
                boolean rim = x == 0 || z == 0 || x == scan.sx - 1 || z == scan.sz - 1;
                if (rim && scan.passable(scan.index(x, y, z))) {
                    reached[z * scan.sx + x] = true;
                    queue.add(new int[]{x, z});
                }
            }
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0];
                int nz = c[1] + d[1];
                if (nx < 0 || nz < 0 || nx >= scan.sx || nz >= scan.sz || reached[nz * scan.sx + nx]) {
                    continue;
                }
                if (scan.passable(scan.index(nx, y, nz))) {
                    reached[nz * scan.sx + nx] = true;
                    queue.add(new int[]{nx, nz});
                }
            }
        }
        int area = 0;
        for (int z = 1; z < scan.sz - 1; z++) {
            for (int x = 1; x < scan.sx - 1; x++) {
                if (!reached[z * scan.sx + x] && scan.passable(scan.index(x, y, z)) && !scan.passable(scan.index(x, y - 1, z))) {
                    area++;
                }
            }
        }
        return area;
    }

    private void judge(Building b) {
        if (scan.members == 0) {
            problems.add("Nothing is built inside the box yet");
        }
        if (!sound) {
            if (analysis.floating > 0) {
                problems.add(analysis.floating + " block(s) have no support");
            }
            if (analysis.overstressed > 0) {
                BlockPos w = analysis.worstBlock();
                problems.add(analysis.overstressed + " block(s) are overstressed (worst " + Math.round(analysis.worst * 100)
                        + "% at " + w.getX() + " " + w.getY() + " " + w.getZ() + "): shorten the span, add a post or use a stronger material");
            }
        }
        switch (b.use) {
            case RESIDENCE -> {
                capacity = Math.min(beds, interiorVolume / 8);
                if (interiorVolume < 8) {
                    problems.add("No enclosed room: walls, a roof and a closed doorway are needed (the box must take in the roof)");
                }
                if (beds < 1) {
                    problems.add("No bed");
                }
                if (doors < 1) {
                    problems.add("No door");
                }
                if (beds > 0 && capacity < beds) {
                    problems.add("Only room for " + capacity + " of " + beds + " beds (8 blocks of air each)");
                }
                functional = sound && interiorVolume >= 8 && beds >= 1 && doors >= 1;
            }
            case STORAGE -> {
                capacity = containerSlots;
                if (containers.isEmpty()) {
                    problems.add("No chests, barrels or other containers inside");
                }
                if (!enclosed) {
                    problems.add("Open to the weather: goods spoil at the full rate (enclose it under a roof to halve that)");
                }
                functional = sound && !containers.isEmpty();
            }
            case PEN -> {
                capacity = penArea / 3;
                if (penArea < 6) {
                    problems.add("Not closed in: fence or wall off at least 6 blocks of ground at the cornerstone's level");
                }
                functional = sound && penArea >= 6;
            }
            case BARRIER -> {
                capacity = water.maxDepth();
                if (water.sections().isEmpty()) {
                    problems.add("No water stands against it");
                }
                for (Hydrostatics.Section s : water.sections()) {
                    if (!s.holds()) {
                        problems.add("Fails under " + s.depth() + " m of water at " + s.base().getX() + " " + s.base().getY() + " "
                                + s.base().getZ() + " (" + Math.round(s.utilisation() * 100) + "%): " + advice(s));
                        break;
                    }
                }
                if (water.holds() && !waterInStorm.holds()) {
                    problems.add("Holds today's water but not a storm surge of 2 m (" + Math.round(waterInStorm.worst() * 100) + "%)");
                }
                functional = sound && !water.sections().isEmpty() && water.holds();
            }
            default -> functional = sound && scan.members > 0;
        }
    }

    private static String advice(Hydrostatics.Section s) {
        if (s.anchored()) {
            return "thicken the wall";
        }
        return "make it thicker (about half the depth it retains), or use timber, steel or reinforced concrete driven at least "
                + Math.max(1, (s.depth() + 2) / 3) + " block(s) into the bed";
    }

    /** Worst utilisation of any standing member, as a fraction (1 = at its limit). */
    public float stress() {
        return analysis == null ? 0 : analysis.worst;
    }
}
