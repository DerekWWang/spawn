package dev.qube.spawn_9b5fbb9.engineering;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.knowledge.Society;
import dev.qube.spawn_9b5fbb9.physics.Collapse;
import dev.qube.spawn_9b5fbb9.physics.Hydrostatics;
import dev.qube.spawn_9b5fbb9.physics.StructureScan;
import dev.qube.spawn_9b5fbb9.physics.Weathering;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The engineering engine: buildings (a structure plus what it is for), schemas (reusable designs
 * with their bill of materials) and construction sites, and the application of physics to them.
 *
 * <p>A building is registered by placing a cornerstone in it. Its extent is a box around the
 * cornerstone; everything built inside the box belongs to it, whatever it is made of and however
 * it is shaped. The use decides what the structure must provide.</p>
 */
public final class Engineering {
    /** Members stressed beyond this multiple of their capacity fail at once rather than waiting for a storm. */
    public static final float COLLAPSE_AT = 1.5F;
    private static final int FIT_RADIUS = 24;
    private static final int FIT_LIMIT = 30000;
    private static final Map<String, Long> LAST_NOTICE = new HashMap<>();

    private Engineering() {
    }

    public static void clear() {
        LAST_NOTICE.clear();
    }

    private static EngineeringData data(Level level) {
        return EngineeringData.get(level.getServer());
    }

    // ------------------------------------------------------------------ registry

    public static Building create(ServerLevel level, BlockPos cornerstone, Direction facing, String society) {
        EngineeringData data = data(level);
        Building existing = at(level, cornerstone);
        if (existing != null) {
            return existing;
        }
        String id = data.newId();
        Building b = new Building(id, society, "Building " + id.substring(1), BuildingUse.GENERIC, level.dimension(), cornerstone, facing);
        b.lastDecay = level.getGameTime();
        data.buildings.put(id, b);
        data.setDirty();
        return b;
    }

    public static @Nullable Building at(Level level, BlockPos cornerstone) {
        if (level.getServer() == null) {
            return null;
        }
        for (Building b : data(level).buildings.values()) {
            if (b.dimension == level.dimension() && b.cornerstone.equals(cornerstone)) {
                return b;
            }
        }
        return null;
    }

    public static @Nullable Building byId(MinecraftServer server, String id) {
        return EngineeringData.get(server).buildings.get(id);
    }

    public static List<Building> in(Level level) {
        List<Building> out = new ArrayList<>();
        if (level.getServer() != null) {
            for (Building b : data(level).buildings.values()) {
                if (b.dimension == level.dimension()) {
                    out.add(b);
                }
            }
        }
        return out;
    }

    public static List<Building> of(MinecraftServer server, String society) {
        List<Building> out = new ArrayList<>();
        for (Building b : EngineeringData.get(server).buildings.values()) {
            if (b.society.equals(society)) {
                out.add(b);
            }
        }
        return out;
    }

    public static void remove(Level level, BlockPos cornerstone) {
        Building b = at(level, cornerstone);
        if (b != null) {
            EngineeringData data = data(level);
            data.buildings.remove(b.id);
            data.setDirty();
        }
    }

    public static void changed(Level level) {
        data(level).setDirty();
    }

    /** A block changed here: any building whose box (or its footing) takes it in must be looked at again. */
    public static void markDirty(Level level, BlockPos pos) {
        if (level.getServer() == null) {
            return;
        }
        for (Building b : data(level).buildings.values()) {
            if (b.dimension == level.dimension() && b.contains(pos, 1)) {
                b.dirty = true;
            }
        }
    }

    // ------------------------------------------------------------------ assessment

    public static Assessment assess(ServerLevel level, Building b) {
        Assessment a = Assessment.of(level, b);
        b.assessment = a;
        b.assessedAt = level.getGameTime();
        return a;
    }

    /** The last assessment if it is still good, otherwise a fresh one. */
    public static Assessment current(ServerLevel level, Building b) {
        if (b.assessment != null && b.assessment.complete && !b.dirty && level.getGameTime() - b.assessedAt < 1200) {
            return b.assessment;
        }
        return assess(level, b);
    }

    /** Sets the box to take in everything built that is joined to the cornerstone. @return blocks found */
    public static int fit(ServerLevel level, Building b) {
        LongOpenHashSet seen = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(b.cornerstone);
        seen.add(b.cornerstone.asLong());
        int minR = 0;
        int maxR = 0;
        int minU = 0;
        int maxU = 0;
        int minB = 0;
        int maxB = 0;
        Direction right = b.right();
        Direction back = b.back();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int found = 0;
        while (!queue.isEmpty() && found < FIT_LIMIT) {
            BlockPos p = queue.poll();
            found++;
            int dx = p.getX() - b.cornerstone.getX();
            int dy = p.getY() - b.cornerstone.getY();
            int dz = p.getZ() - b.cornerstone.getZ();
            int r = dx * right.getStepX() + dz * right.getStepZ();
            int k = dx * back.getStepX() + dz * back.getStepZ();
            minR = Math.min(minR, r);
            maxR = Math.max(maxR, r);
            minU = Math.min(minU, dy);
            maxU = Math.max(maxU, dy);
            minB = Math.min(minB, k);
            maxB = Math.max(maxB, k);
            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        int manhattan = Math.abs(ox) + Math.abs(oy) + Math.abs(oz);
                        if (manhattan == 0 || manhattan == 3) {
                            continue;
                        }
                        m.set(p.getX() + ox, p.getY() + oy, p.getZ() + oz);
                        if (Math.abs(m.getX() - b.cornerstone.getX()) > FIT_RADIUS || Math.abs(m.getY() - b.cornerstone.getY()) > FIT_RADIUS
                                || Math.abs(m.getZ() - b.cornerstone.getZ()) > FIT_RADIUS || !level.isLoaded(m)) {
                            continue;
                        }
                        if (seen.contains(m.asLong())) {
                            continue;
                        }
                        BlockState s = level.getBlockState(m);
                        if (StructureScan.classify(s) == StructureScan.MEMBER && !s.is(StructureScan.TERRAIN)) {
                            seen.add(m.asLong());
                            queue.add(m.immutable());
                        }
                    }
                }
            }
        }
        b.setExtents(-minR, maxR, -minU, maxU, -minB, maxB);
        changed(level);
        return found;
    }

    /** @return null if the box is acceptable, otherwise why not */
    public static @Nullable String checkSize(Building b) {
        if (b.volume() > Config.integer(Config.MAX_BUILDING_VOLUME)) {
            return "That box holds " + b.volume() + " blocks; the limit is " + Config.integer(Config.MAX_BUILDING_VOLUME);
        }
        return null;
    }

    // ------------------------------------------------------------------ schemas and sites

    private static Rotation turn(Direction from, Direction to) {
        int quarter = Math.floorMod(to.get2DDataValue() - from.get2DDataValue(), 4);
        return Rotation.values()[quarter];
    }

    /** The second half of a two-block object (door top, bed head): the first half stands for both. */
    private static boolean secondHalf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return true;
        }
        return state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
    }

    /**
     * Records the building as it stands as a design that can be built again elsewhere.
     *
     * @return an error, or null on success
     */
    public static @Nullable String saveSchema(ServerLevel level, Building b, String name) {
        if (name == null || name.isBlank() || name.length() > 32 || !name.matches("[A-Za-z0-9_\\-]+")) {
            return "Schema names are 1-32 letters, digits, _ or -";
        }
        Assessment a = assess(level, b);
        if (!a.complete) {
            return "The whole building must be loaded";
        }
        if (a.scan.members <= 1) {
            return "Nothing is built here to record";
        }
        Rotation toCanonical = turn(b.facing, Direction.NORTH);
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> index = new HashMap<>();
        List<Integer> cells = new ArrayList<>();
        Map<String, Integer> materials = new TreeMap<>();
        for (int ly = 0; ly < b.sizeY(); ly++) {
            for (int lz = 0; lz < b.sizeZ(); lz++) {
                for (int lx = 0; lx < b.sizeX(); lx++) {
                    BlockPos pos = b.world(lx, ly, lz);
                    if (pos.equals(b.cornerstone)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (StructureScan.classify(state) != StructureScan.MEMBER || state.is(StructureScan.TERRAIN)) {
                        continue;
                    }
                    BlockState canonical = state.rotate(toCanonical);
                    Integer pi = index.get(canonical);
                    if (pi == null) {
                        if (palette.size() >= 16000) {
                            return "Too many different block states for one schema";
                        }
                        pi = palette.size();
                        palette.add(canonical);
                        index.put(canonical, pi);
                    }
                    cells.add(Schema.pack(pi, lx, ly, lz));
                    if (!secondHalf(state)) {
                        Item item = state.getBlock().asItem();
                        if (item != Items.AIR) {
                            materials.merge(BuiltInRegistries.ITEM.getKey(item).toString(), 1, Integer::sum);
                        }
                    }
                }
            }
        }
        Schema schema = new Schema(name, b.use.id, b.sizeX(), b.sizeY(), b.sizeZ(), b.extents[Building.LEFT], b.extents[Building.DOWN],
                b.extents[Building.FRONT], List.copyOf(palette), List.copyOf(cells), Map.copyOf(materials));
        EngineeringData data = data(level);
        data.schemas.computeIfAbsent(b.society, k -> new LinkedHashMap<>()).put(name, schema);
        data.setDirty();
        return null;
    }

    public static @Nullable Schema schema(Level level, String society, String name) {
        return data(level).schemasOf(society).get(name);
    }

    public static boolean deleteSchema(Level level, String society, String name) {
        Map<String, Schema> map = data(level).schemas.get(society);
        if (map != null && map.remove(name) != null) {
            data(level).setDirty();
            return true;
        }
        return false;
    }

    /** Turns the cornerstone into a construction site for the schema. @return an error, or null */
    public static @Nullable String plan(ServerLevel level, Building b, String schemaName) {
        Schema s = schema(level, b.society, schemaName);
        if (s == null) {
            return "Your society has no schema called " + schemaName;
        }
        b.schema = s.name();
        BuildingUse use = BuildingUse.byId(s.use());
        b.use = use == null ? BuildingUse.GENERIC : use;
        b.setExtents(s.anchorX(), s.sx() - 1 - s.anchorX(), s.anchorY(), s.sy() - 1 - s.anchorY(), s.anchorZ(), s.sz() - 1 - s.anchorZ());
        changed(level);
        return null;
    }

    /** A block the site still lacks. */
    public record Missing(BlockPos pos, BlockState state) { }

    /** Blocks of the planned design not yet in place, lowest first (so they can be built in order). */
    public static List<Missing> missing(ServerLevel level, Building b) {
        List<Missing> out = new ArrayList<>();
        Schema s = b.planned() ? schema(level, b.society, b.schema) : null;
        if (s == null) {
            return out;
        }
        Rotation fromCanonical = turn(Direction.NORTH, b.facing);
        for (int cell : s.cells()) {
            BlockPos pos = b.world(Schema.xOf(cell), Schema.yOf(cell), Schema.zOf(cell));
            if (pos.equals(b.cornerstone) || !level.isLoaded(pos)) {
                continue;
            }
            BlockState want = s.palette().get(Schema.paletteOf(cell));
            if (!level.getBlockState(pos).is(want.getBlock())) {
                out.add(new Missing(pos, want.rotate(fromCanonical)));
            }
        }
        out.sort(Comparator.comparingInt((Missing m) -> m.pos().getY()).thenComparingDouble(m -> m.pos().distSqr(b.cornerstone)));
        return out;
    }

    /** What the missing blocks add up to: item id -> count. */
    public static Map<String, Integer> billOfMaterials(List<Missing> missing) {
        Map<String, Integer> out = new TreeMap<>();
        for (Missing m : missing) {
            if (!secondHalf(m.state())) {
                Item item = m.state().getBlock().asItem();
                if (item != Items.AIR) {
                    out.merge(BuiltInRegistries.ITEM.getKey(item).toString(), 1, Integer::sum);
                }
            }
        }
        return out;
    }

    /** Puts every missing block in place at once (operators and tests). @return blocks placed */
    public static int construct(ServerLevel level, Building b) {
        List<Missing> list = missing(level, b);
        for (Missing m : list) {
            level.setBlock(m.pos(), m.state(), Block.UPDATE_CLIENTS);
        }
        b.dirty = true;
        return list.size();
    }

    // ------------------------------------------------------------------ physics on buildings

    /** Storm strength now: 0 in fair weather, about 0.25 in rain, 0.6 to 1.2 in a thunderstorm (fixed for each day). */
    public static float stormSeverity(ServerLevel level) {
        float scale = (float) Config.dbl(Config.STORM_SEVERITY);
        if (level.isThundering()) {
            long day = level.getGameTime() / 24000L;
            long h = (day * 0x9E3779B97F4A7C15L) ^ level.getSeed();
            float unit = ((h >>> 11) & 0xFFFF) / 65535F;
            return (0.6F + 0.6F * unit) * scale;
        }
        return level.isRaining() ? 0.25F * scale : 0F;
    }

    public static void tick(ServerLevel level) {
        Collapse.tick(level);
        long now = level.getGameTime();
        List<Building> here = in(level);
        if (here.isEmpty()) {
            return;
        }
        if (now % 10 == 0) {
            int budget = 2;
            for (Building b : here) {
                if (b.dirty && budget-- > 0) {
                    settle(level, b);
                }
            }
        }
        float severity = stormSeverity(level);
        for (Building b : here) {
            int phase = Math.floorMod(b.id.hashCode(), 1200);
            if ((now + phase) % 1200 == 600 && level.isLoaded(b.cornerstone)
                    && !(level.getBlockState(b.cornerstone).getBlock() instanceof CornerstoneBlock)) {
                remove(level, b.cornerstone); // the stone went without telling us (edited world, moved by a machine)
                continue;
            }
            if (severity >= 0.5F && (now + phase) % 200 == 0) {
                storm(level, b, severity);
            }
            if ((now + phase) % 1200 == 0) {
                weather(level, b);
            }
        }
    }

    /**
     * Looks at a building that has changed. Anything floating, or stressed so far beyond its limit
     * that it cannot stand, comes down now; a barrier that cannot hold its water gives way.
     */
    public static void settle(ServerLevel level, Building b) {
        Assessment a = assess(level, b);
        b.dirty = false;
        if (!a.complete || !Config.bool(Config.STRUCTURAL_STRESS)) {
            return;
        }
        List<BlockPos> failing = a.analysis.above(COLLAPSE_AT);
        if (!failing.isEmpty()) {
            int n = Collapse.drop(level, failing);
            b.dirty = true;
            notice(level, b, "Part of " + b.name + " collapsed: " + n + " block(s) had no adequate support.", true);
            return;
        }
        breach(level, b, a, 0, "gave way under the water it holds back");
    }

    private static void breach(ServerLevel level, Building b, Assessment a, int surge, String why) {
        if (b.use != BuildingUse.BARRIER) {
            return;
        }
        Hydrostatics.Report report = surge == 0 ? a.water : Hydrostatics.analyse(a.scan, surge);
        List<BlockPos> lost = new ArrayList<>();
        for (Hydrostatics.Section s : report.sections()) {
            if (!s.holds()) {
                lost.addAll(Hydrostatics.breach(a.scan, s));
            }
        }
        if (!lost.isEmpty()) {
            int n = Collapse.drop(level, lost);
            b.dirty = true;
            notice(level, b, b.name + " " + why + ": " + n + " block(s) breached.", true);
        }
    }

    /** One gust: exposed, light or already strained members may be torn loose, and barriers face the surge. */
    public static int storm(ServerLevel level, Building b, float severity) {
        if (!Config.bool(Config.STRUCTURAL_STRESS)) {
            return 0;
        }
        Assessment a = current(level, b);
        if (!a.complete) {
            return 0;
        }
        List<BlockPos> torn = Weathering.stormFailures(a.scan, a.analysis, a.outside, severity, level.getRandom());
        int n = 0;
        if (!torn.isEmpty()) {
            n = Collapse.drop(level, torn);
            b.dirty = true;
            notice(level, b, "The storm tore " + n + " block(s) from " + b.name + ".", false);
        }
        breach(level, b, a, Math.round(2 * severity), "was breached by the storm surge");
        return n;
    }

    /** Slow decay since the last pass, and creep in members that are overstressed. */
    public static int weather(ServerLevel level, Building b) {
        long now = level.getGameTime();
        double rate = Config.dbl(Config.DECAY_RATE);
        if (b.lastDecay <= 0 || b.lastDecay > now) {
            b.lastDecay = now;
            changed(level);
            return 0;
        }
        Assessment a = current(level, b);
        if (!a.complete) {
            return 0; // not loaded: the time is made up when it next is
        }
        double days = Math.min(30.0, (now - b.lastDecay) / 24000.0) * rate;
        b.lastDecay = now;
        changed(level);
        int lost = 0;
        for (BlockPos pos : Weathering.decay(a.scan, a.outside, days, level.getRandom(), 96)) {
            BlockState state = level.getBlockState(pos);
            BlockState worn = Weathering.wornForm(state);
            if (worn != null) {
                level.setBlock(pos, worn, Block.UPDATE_ALL);
            } else if (!state.hasBlockEntity() && !pos.equals(b.cornerstone)) {
                level.destroyBlock(pos, false);
                lost++;
            }
            b.dirty = true;
        }
        if (Config.bool(Config.STRUCTURAL_STRESS)) {
            List<BlockPos> creeping = new ArrayList<>();
            for (BlockPos pos : a.analysis.above(1.0001F)) {
                if (level.getRandom().nextFloat() < 0.1F) {
                    creeping.add(pos);
                }
            }
            if (!creeping.isEmpty()) {
                lost += Collapse.drop(level, creeping);
                b.dirty = true;
            }
        }
        if (lost > 0) {
            notice(level, b, b.name + " is deteriorating: " + lost + " block(s) rotted, rusted or gave way and need replacing.", false);
        }
        return lost;
    }

    private static void notice(ServerLevel level, Building b, String text, boolean always) {
        long now = level.getGameTime();
        Long last = LAST_NOTICE.get(b.id);
        if (!always && last != null && now - last < 1200 && now >= last) {
            return;
        }
        LAST_NOTICE.put(b.id, now);
        Component msg = Component.literal(text).withStyle(ChatFormatting.RED);
        for (ServerPlayer p : Society.onlineMembers(level.getServer(), b.society)) {
            p.sendSystemMessage(msg);
        }
    }

    // ------------------------------------------------------------------ presentation

    /** Shows the edges of the building's box to a player for a moment. */
    public static void outline(ServerLevel level, Building b, ServerPlayer player) {
        BlockPos lo = b.min();
        BlockPos hi = b.max().offset(1, 1, 1);
        int[] xs = {lo.getX(), hi.getX()};
        int[] ys = {lo.getY(), hi.getY()};
        int[] zs = {lo.getZ(), hi.getZ()};
        for (int x = lo.getX(); x <= hi.getX(); x++) {
            for (int y : ys) {
                for (int z : zs) {
                    spark(level, player, x, y, z);
                }
            }
        }
        for (int y = lo.getY(); y <= hi.getY(); y++) {
            for (int x : xs) {
                for (int z : zs) {
                    spark(level, player, x, y, z);
                }
            }
        }
        for (int z = lo.getZ(); z <= hi.getZ(); z++) {
            for (int x : xs) {
                for (int y : ys) {
                    spark(level, player, x, y, z);
                }
            }
        }
    }

    private static void spark(ServerLevel level, ServerPlayer player, int x, int y, int z) {
        level.sendParticles(player, ParticleTypes.END_ROD, true, false, x, y, z, 1, 0, 0, 0, 0);
    }

    /** Plain-text report of a building, one fact per line (also what bots read). */
    public static List<String> report(ServerLevel level, Building b) {
        List<String> out = new ArrayList<>();
        Assessment a = current(level, b);
        out.add(b.name + " [" + b.id + "] use=" + b.use.id + " at " + b.cornerstone.getX() + " " + b.cornerstone.getY() + " "
                + b.cornerstone.getZ() + " box=" + b.sizeX() + "x" + b.sizeY() + "x" + b.sizeZ());
        if (!a.complete) {
            out.add("status=unloaded");
            return out;
        }
        out.add("status=" + (a.functional ? "functional" : "not functional") + " sound=" + a.sound + " stress="
                + Math.round(a.stress() * 100) + "% blocks=" + a.scan.members + " mass=" + Math.round(a.scan.mass) + "t");
        StringBuilder mats = new StringBuilder();
        a.materials.forEach((m, n) -> mats.append(mats.length() > 0 ? ", " : "").append(m.id).append(" ").append(n));
        if (mats.length() > 0) {
            out.add("materials: " + mats);
        }
        switch (b.use) {
            case RESIDENCE -> out.add("room=" + a.interiorVolume + "m3 beds=" + a.beds + " doors=" + a.doors + " capacity=" + a.capacity);
            case STORAGE -> out.add("containers=" + a.containers.size() + " slots=" + a.containerSlots + " enclosed=" + a.enclosed);
            case PEN -> out.add("fenced=" + a.penArea + "m2 capacity=" + a.capacity);
            case BARRIER -> out.add("water depth=" + a.water.maxDepth() + "m load=" + Math.round(a.water.worst() * 100) + "% storm load="
                    + Math.round(a.waterInStorm.worst() * 100) + "%");
            default -> { }
        }
        for (String p : a.problems) {
            out.add("problem: " + p);
        }
        if (b.planned()) {
            List<Missing> missing = missing(level, b);
            Schema s = schema(level, b.society, b.schema);
            out.add("site: schema=" + b.schema + " missing=" + missing.size() + (s == null ? "" : " of " + s.blocks()));
        }
        return out;
    }

    public static Identifier id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }
}
