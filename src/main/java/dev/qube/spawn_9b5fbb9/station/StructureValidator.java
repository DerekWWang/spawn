package dev.qube.spawn_9b5fbb9.station;

import org.jspecify.annotations.Nullable;

import dev.qube.spawn_9b5fbb9.knowledge.Environment;
import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * Checks whether the blocks around a station controller form the station's structure.
 *
 * <p>Box stations extend behind the controller, which sits in the middle of the bottom row of the
 * front face; centred stations are built around it. Shell blocks must be the station's casing, the
 * inside must be empty for furnaces, and the open top of a furnace must be empty too. A station
 * may list several tiers (sizes): the largest one that is complete wins, and larger tiers run
 * more batches at once.</p>
 */
public final class StructureValidator {
    public static final TagKey<Block> SHAFT_PASSABLE = TagKey.create(Registries.BLOCK, id("shaft_passable"));

    private StructureValidator() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, path);
    }

    /** Result of a check. bounds always covers the blocks that were (or would be) part of the structure. */
    public record Formation(boolean formed, int tier, int batch, AABB bounds, @Nullable AABB interior, String problem,
                            BlockPos topCenter) {
        public static Formation unchecked(BlockPos pos) {
            return new Formation(false, -1, 0, new AABB(pos), null, "Not checked yet", pos);
        }
    }

    public static TagKey<Block> casingTag(String casing) {
        return TagKey.create(Registries.BLOCK, id("casing/" + casing));
    }

    public static Formation validate(Level level, BlockPos origin, Direction facing, TechTree.StationDef def) {
        return switch (def.shape()) {
            case "single" -> new Formation(true, 0, 1, new AABB(origin), null, "", origin);
            case "area" -> area(level, origin, def);
            case "shaft" -> shaft(level, origin, def);
            default -> boxes(level, origin, facing, def);
        };
    }

    // ------------------------------------------------------------------ boxes

    private static Formation boxes(Level level, BlockPos origin, Direction facing, TechTree.StationDef def) {
        Formation best = null;
        double bestScore = -1;
        for (int t = def.tiers().size() - 1; t >= 0; t--) {
            Attempt a = checkTier(level, origin, facing, def, t);
            if (a.formation.formed()) {
                return a.formation;
            }
            if (a.score > bestScore) {
                bestScore = a.score;
                best = a.formation;
            }
        }
        return best;
    }

    private record Attempt(Formation formation, double score) { }

    private static Attempt checkTier(Level level, BlockPos origin, Direction facing, TechTree.StationDef def, int tierIndex) {
        TechTree.Tier tier = def.tiers().get(tierIndex);
        boolean centered = "centered".equals(def.shape());
        int half = tier.w() / 2;
        int xMin = -half;
        int xMax = half;
        int zMin = centered ? -(tier.d() / 2) : 0;
        int zMax = centered ? tier.d() / 2 : tier.d() - 1;
        int h = tier.h();
        Direction back = facing.getOpposite();
        Direction right = facing.getClockWise();
        TagKey<Block> casing = casingTag(def.casing());
        Block ceilingBlock = def.ceiling() == null ? null
                : BuiltInRegistries.BLOCK.getOptional(Identifier.parse(def.ceiling().block())).orElse(null);

        String size = tier.w() + "x" + tier.h() + "x" + tier.d();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int total = 0;
        int good = 0;
        int ceilingCount = 0;
        String problem = null;
        BlockPos first = origin;
        BlockPos last = origin;
        for (int y = 0; y < h; y++) {
            for (int x = xMin; x <= xMax; x++) {
                for (int z = zMin; z <= zMax; z++) {
                    place(m, origin, right, back, x, y, z);
                    if (x == xMin && y == 0 && z == zMin) {
                        first = m.immutable();
                    }
                    if (x == xMax && y == h - 1 && z == zMax) {
                        last = m.immutable();
                    }
                    if (x == 0 && y == 0 && z == 0) {
                        continue; // the controller itself
                    }
                    total++;
                    if (!level.isLoaded(m)) {
                        return new Attempt(fail(def, tierIndex, origin, first, last, "Part of the " + size
                                + " structure is in an unloaded chunk", right, back, xMin, xMax, zMin, zMax, h), 0);
                    }
                    BlockState state = level.getBlockState(m);
                    boolean shell = x == xMin || x == xMax || y == 0 || y == h - 1 || z == zMin || z == zMax;
                    boolean topInterior = y == h - 1 && h > 1 && x != xMin && x != xMax && z != zMin && z != zMax;
                    String expected;
                    boolean ok;
                    if (def.openTop() && topInterior) {
                        ok = isOpen(state);
                        expected = "open air (the top is open)";
                    } else if (shell) {
                        ok = state.is(casing);
                        expected = casingName(def.casing());
                        if (ok && y == h - 1 && ceilingBlock != null && state.is(ceilingBlock)) {
                            ceilingCount++;
                        }
                    } else if ("air".equals(def.interior())) {
                        ok = isOpen(state);
                        expected = "an empty interior";
                    } else {
                        ok = true;
                        expected = "";
                    }
                    if (ok) {
                        good++;
                    } else if (problem == null) {
                        problem = "Expected " + expected + " at " + m.getX() + " " + m.getY() + " " + m.getZ()
                                + " but found " + state.getBlock().getName().getString();
                    }
                }
            }
        }
        double score = total == 0 ? 1 : (double) good / total;
        if (problem != null) {
            return new Attempt(fail(def, tierIndex, origin, first, last, size + ": " + good + "/" + total + " blocks right. "
                    + problem, right, back, xMin, xMax, zMin, zMax, h), score);
        }
        if (def.ceiling() != null) {
            int needed = Math.max(1, (int) Math.ceil(tier.w() * tier.d() / (double) def.ceiling().perArea()));
            if (ceilingCount < needed) {
                return new Attempt(fail(def, tierIndex, origin, first, last, size + ": the ceiling needs at least " + needed
                        + " fan filter units (has " + ceilingCount + ")", right, back, xMin, xMax, zMin, zMax, h), score * 0.99);
            }
        }
        BlockPos topCenter = topCenter(origin, right, back, zMin, zMax, h);
        if (def.chimney()) {
            BlockPos probe = def.openTop() ? topCenter : topCenter.above();
            if (!level.canSeeSky(probe)) {
                return new Attempt(fail(def, tierIndex, origin, first, last, size + ": the chimney at " + probe.getX() + " "
                        + probe.getY() + " " + probe.getZ() + " needs open sky above it", right, back, xMin, xMax, zMin, zMax, h),
                        score * 0.99);
            }
        }
        for (String cond : def.conditions()) {
            String bad = condition(level, cond, origin, right, back, xMin, xMax, zMin, zMax, h, topCenter, bounds(first, last));
            if (bad != null) {
                return new Attempt(fail(def, tierIndex, origin, first, last, size + ": " + bad, right, back, xMin, xMax, zMin,
                        zMax, h), score * 0.99);
            }
        }
        AABB interior = null;
        if (def.room() && tier.w() > 2 && tier.d() > 2 && h > 2) {
            interior = bounds(at(origin, right, back, xMin + 1, 1, zMin + 1), at(origin, right, back, xMax - 1, h - 2, zMax - 1));
        }
        return new Attempt(new Formation(true, tierIndex, tier.batch(), bounds(first, last), interior, "", topCenter), 1);
    }

    private static Formation fail(TechTree.StationDef def, int tier, BlockPos origin, BlockPos first, BlockPos last, String problem,
                                  Direction right, Direction back, int xMin, int xMax, int zMin, int zMax, int h) {
        return new Formation(false, tier, 0, bounds(first, last), null, problem, topCenter(origin, right, back, zMin, zMax, h));
    }

    private static BlockPos topCenter(BlockPos origin, Direction right, Direction back, int zMin, int zMax, int h) {
        return at(origin, right, back, 0, h - 1, (zMin + zMax) / 2);
    }

    private static boolean isOpen(BlockState state) {
        return state.isAir() || state.is(BlockTags.FIRE);
    }

    private static void place(BlockPos.MutableBlockPos m, BlockPos origin, Direction right, Direction back, int x, int y, int z) {
        m.set(origin.getX() + right.getStepX() * x + back.getStepX() * z, origin.getY() + y,
                origin.getZ() + right.getStepZ() * x + back.getStepZ() * z);
    }

    private static BlockPos at(BlockPos origin, Direction right, Direction back, int x, int y, int z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        place(m, origin, right, back, x, y, z);
        return m.immutable();
    }

    static AABB bounds(BlockPos a, BlockPos b) {
        return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    /** @return null if the site condition holds, otherwise what is wrong */
    private static @Nullable String condition(Level level, String cond, BlockPos origin, Direction right, Direction back, int xMin,
                                              int xMax, int zMin, int zMax, int h, BlockPos topCenter, AABB bounds) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        switch (cond) {
            case "flowing_water" -> {
                int found = 0;
                for (int x = xMin; x <= xMax; x++) {
                    for (int z = zMin; z <= zMax; z++) {
                        for (int[] o : new int[][]{{0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {0, -1, -1}, {0, -1, 1}}) {
                            place(m, origin, right, back, x + o[0], o[1], z + o[2]);
                            if (level.isLoaded(m)) {
                                FluidState f = level.getFluidState(m);
                                if (f.is(FluidTags.WATER) && !f.isSource()) {
                                    found++;
                                }
                            }
                        }
                    }
                }
                return found >= 2 ? null : "the bottom of the wheel must dip into flowing water (a stream with current)";
            }
            case "water_nearby" -> {
                return Environment.waterSourceWithin(level, bounds.inflate(2)) ? null
                        : "needs a water source within 2 blocks to feed the boiler";
            }
            case "wind" -> {
                return Environment.phenomenon("wind", level, topCenter, null) ? null
                        : "the top must stand in open wind: open sky and at least 10 blocks above sea level";
            }
            case "water_at_back" -> {
                int found = 0;
                for (int x = xMin; x <= xMax; x++) {
                    for (int y = -1; y <= 0; y++) {
                        place(m, origin, right, back, x, y, zMax + 1);
                        if (level.isLoaded(m) && level.getFluidState(m).is(FluidTags.WATER)) {
                            found++;
                        }
                    }
                }
                return found >= 3 ? null : "the far end of the slipway must run into open water";
            }
            case "reservoir_behind" -> {
                int found = 0;
                for (int x = xMin - 2; x <= xMax + 2 && found < 20; x++) {
                    for (int z = zMax + 1; z <= zMax + 6 && found < 20; z++) {
                        for (int y = 0; y < h; y++) {
                            place(m, origin, right, back, x, y, z);
                            if (level.isLoaded(m)) {
                                FluidState f = level.getFluidState(m);
                                if (f.is(FluidTags.WATER) && f.isSource()) {
                                    found++;
                                }
                            }
                        }
                    }
                }
                return found >= 20 ? null : "must hold back a reservoir: at least 20 water blocks behind the wall";
            }
            default -> {
                return null;
            }
        }
    }

    public static String casingName(String casing) {
        if (casing == null) {
            return "casing";
        }
        return switch (casing) {
            case "timber" -> "timber (logs, planks or wooden fences)";
            case "stone" -> "stone (cobble, stone, stone bricks, deepslate, limestone...)";
            case "clay" -> "clay, bricks, mud bricks or terracotta";
            case "masonry" -> "masonry (stone, bricks, clay or terracotta)";
            case "refractory" -> "refractory brick (bricks or nether bricks)";
            case "furnace" -> "furnace lining (bricks or dressed stone)";
            case "still" -> "copper blocks or bricks";
            case "timber_metal" -> "timber or iron/steel frame blocks";
            case "timber_stone" -> "timber or stone";
            case "masonry_timber" -> "masonry or timber";
            case "engine_house" -> "masonry or iron/steel blocks";
            case "heavy", "metal" -> "iron or steel machine blocks";
            case "refractory_metal" -> "bricks or iron/steel blocks";
            case "industrial" -> "steel casing, reinforced concrete or bricks";
            case "refractory_industrial" -> "bricks, steel casing or reinforced concrete";
            case "building" -> "walls (stone, brick, timber, concrete, steel, glass, doors)";
            case "precision" -> "stainless steel casing or glass";
            case "precision_industrial" -> "stainless steel, steel casing or concrete";
            case "glass_precision" -> "glass or stainless steel casing";
            case "heavy_precision" -> "steel or stainless steel casing";
            case "cleanroom_room", "cleanroom" -> "cleanroom panels, fan filter units, glass or iron doors";
            case "concrete" -> "reinforced concrete";
            case "lead" -> "lead sheet lining";
            default -> casing.replace('_', ' ');
        };
    }

    // ------------------------------------------------------------------ areas and shafts

    private static Formation area(Level level, BlockPos origin, TechTree.StationDef def) {
        TechTree.Area a = def.area();
        if (a == null) {
            return new Formation(false, -1, 0, new AABB(origin), null, "No area definition", origin);
        }
        TagKey<Block> tag = a.match().startsWith("#") ? TagKey.create(Registries.BLOCK, Identifier.parse(a.match().substring(1))) : null;
        boolean water = "water".equals(a.match());
        int found = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -a.radius(); x <= a.radius(); x++) {
            for (int z = -a.radius(); z <= a.radius(); z++) {
                for (int dy = a.dyMin(); dy <= a.dyMax(); dy++) {
                    m.set(origin.getX() + x, origin.getY() + dy, origin.getZ() + z);
                    if (!level.isLoaded(m)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(m);
                    if ((tag != null && s.is(tag)) || (water && s.getFluidState().is(FluidTags.WATER))) {
                        found++;
                    }
                }
            }
        }
        AABB bounds = new AABB(origin.getX() - a.radius(), origin.getY() + a.dyMin(), origin.getZ() - a.radius(),
                origin.getX() + a.radius() + 1, origin.getY() + a.dyMax() + 1, origin.getZ() + a.radius() + 1);
        if (found >= a.min()) {
            return new Formation(true, 0, 1, bounds, null, "", origin);
        }
        String what = water ? "water blocks" : a.match().endsWith("field_soil") ? "tilled farmland blocks"
                : a.match().endsWith("pen_fencing") ? "fences, gates or walls" : "matching blocks";
        return new Formation(false, 0, 0, bounds, null, "Needs " + a.min() + " " + what + " within " + a.radius()
                + " blocks (found " + found + ")", origin);
    }

    private static Formation shaft(Level level, BlockPos origin, TechTree.StationDef def) {
        TechTree.Shaft s = def.shaft();
        if (s == null) {
            return new Formation(false, -1, 0, new AABB(origin), null, "No shaft definition", origin);
        }
        AABB bounds = new AABB(origin.getX() - 1, origin.getY() - s.depth(), origin.getZ() - 1, origin.getX() + 2,
                origin.getY() + 4, origin.getZ() + 2);
        for (int i = 1; i <= s.depth(); i++) {
            BlockPos p = origin.below(i);
            if (p.getY() < level.getMinY()) {
                return new Formation(false, 0, 0, bounds, null, "The shaft cannot be " + s.depth()
                        + " blocks deep here: it would pass the bottom of the world", origin);
            }
            if (!level.isLoaded(p)) {
                return new Formation(false, 0, 0, bounds, null, "Part of the shaft is in an unloaded chunk", origin);
            }
            BlockState st = level.getBlockState(p);
            if (!(st.isAir() || st.is(SHAFT_PASSABLE))) {
                return new Formation(false, 0, 0, bounds, null, "The shaft must be open (air or ladders) for " + s.depth()
                        + " blocks below the headframe; blocked at depth " + i + " (y " + p.getY() + ") by "
                        + st.getBlock().getName().getString(), origin);
            }
        }
        TagKey<Block> frame = TagKey.create(Registries.BLOCK, Identifier.parse(s.headframe().substring(1)));
        int count = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                for (int y = 0; y <= 3; y++) {
                    m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (level.isLoaded(m) && level.getBlockState(m).is(frame)) {
                        count++;
                    }
                }
            }
        }
        if (count < s.headframeMin()) {
            return new Formation(false, 0, 0, bounds, null, "The headframe needs at least " + s.headframeMin()
                    + " frame blocks around the shaft mouth (found " + count + ")", origin);
        }
        return new Formation(true, 0, 1, bounds, null, "", origin);
    }
}
