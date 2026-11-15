package dev.qube.spawn_9b5fbb9.knowledge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.registry.ModContent;
import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import dev.qube.spawn_9b5fbb9.station.StationBlockEntity;
import dev.qube.spawn_9b5fbb9.station.StationIndex;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import dev.qube.spawn_9b5fbb9.techtree.TechTree.FunctionDef;
import dev.qube.spawn_9b5fbb9.techtree.TechTree.Ref;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Decides whether a function can be discovered or run in a given place, and carries out the
 * consumption. The same rules serve the journal (a player and their inventory) and stations
 * (a structure and its slots), so the two can never disagree.
 */
public final class Requirements {
    public static final List<String> MECHANICAL_PROVIDERS = List.of("water_wheel", "windmill", "steam_engine", "atmospheric_engine");
    /** Filled from both the client (tooltips) and the integrated server thread. */
    private static final Map<String, TagKey<Item>> RESOURCE_TAGS = new java.util.concurrent.ConcurrentHashMap<>();

    private Requirements() {
    }

    /** ok: everything is in place. batches: how many runs the inputs (and output space) allow. */
    public record Check(boolean ok, List<String> missing, int batches) {
        public String first() {
            return missing.isEmpty() ? "" : missing.get(0);
        }
    }

    public enum Phase {
        /** about to start a run: everything, including inputs and output space */
        START,
        /** during a run: surroundings only (structure, stations, tools, power) */
        RUNNING,
        /** finishing a run: everything again */
        COMPLETE
    }

    // ------------------------------------------------------------------ matching

    public static TagKey<Item> resourceTag(String resourceId) {
        return RESOURCE_TAGS.computeIfAbsent(resourceId, id -> TagKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, "resource/" + id)));
    }

    public static Predicate<ItemStack> matcher(Ref ref) {
        return switch (ref.kind()) {
            case ITEM, STATION -> {
                Item item = ModContent.item(ref.id());
                yield stack -> item != null && !stack.isEmpty() && stack.is(item);
            }
            case TAG -> {
                TagKey<Item> tag = resourceTag(ref.id());
                yield stack -> !stack.isEmpty() && stack.is(tag);
            }
            default -> stack -> false;
        };
    }

    public static boolean isWaterContainer(ItemStack stack) {
        if (stack.is(Items.WATER_BUCKET)) {
            return true;
        }
        if (stack.is(Items.POTION)) {
            PotionContents c = stack.get(DataComponents.POTION_CONTENTS);
            return c != null && c.is(Potions.WATER);
        }
        return false;
    }

    private static ItemStack emptiedContainer(ItemStack stack) {
        return stack.is(Items.WATER_BUCKET) ? new ItemStack(Items.BUCKET) : new ItemStack(Items.GLASS_BOTTLE);
    }

    public static int count(List<ItemStack> stacks, Predicate<ItemStack> match) {
        int n = 0;
        for (ItemStack s : stacks) {
            if (match.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ shared lookups

    public static boolean gridCovered(Level level, AABB area) {
        return !StationIndex.formedNear(level, "electric_grid", area, Config.integer(Config.GRID_RADIUS)).isEmpty();
    }

    /**
     * A running water wheel, windmill or engine within reach, or grid coverage.
     *
     * @param draw true only while work is actually being done: engines then burn fuel. A check made
     *             merely to see whether work could start must not burn anything.
     */
    public static boolean mechanicalPower(Level level, AABB area, boolean draw) {
        double r = Config.integer(Config.PRESENCE_RADIUS);
        for (String id : MECHANICAL_PROVIDERS) {
            for (StationBlockEntity s : StationIndex.formedNear(level, id, area, r)) {
                if (draw ? s.drawPower(40) : s.canProvidePower()) {
                    return true;
                }
            }
        }
        return gridCovered(level, area);
    }

    public static @Nullable StationBlockEntity cleanroomAround(Level level, AABB thing) {
        for (StationBlockEntity cr : StationIndex.formed(level, "cleanroom")) {
            AABB inside = cr.interior();
            if (inside != null && contains(inside, thing)) {
                return cr;
            }
        }
        return null;
    }

    private static boolean contains(AABB outer, AABB inner) {
        double e = 1e-6;
        return inner.minX >= outer.minX - e && inner.minY >= outer.minY - e && inner.minZ >= outer.minZ - e
                && inner.maxX <= outer.maxX + e && inner.maxY <= outer.maxY + e && inner.maxZ <= outer.maxZ + e;
    }

    private static String stationName(String id) {
        TechTree.StationDef def = TechTree.get().stationsById.get(id);
        return def == null ? id : def.name();
    }

    private static String theoryName(String id) {
        FunctionDef t = TechTree.get().functionsById.get(id);
        return t == null ? id : t.name();
    }

    // ------------------------------------------------------------------ player (journal and hand work)

    /**
     * What a player has to hand, looked up once and reused across many function checks (the journal
     * checks hundreds of functions at a time; the world scans behind these answers are not cheap).
     */
    public static final class PlayerContext {
        final ServerPlayer player;
        final Level level;
        final BlockPos pos;
        final AABB here;
        final List<ItemStack> inv;
        final KnowledgeData knowledge;
        final String society;
        private final Map<String, Integer> counts = new HashMap<>();
        private final Map<String, Boolean> phenomena = new HashMap<>();
        private final Map<String, Integer> animals = new HashMap<>();
        private final Map<String, Boolean> stations = new HashMap<>();
        private Boolean nearWater;
        private Boolean grid;
        private Integer waterContainers;

        public PlayerContext(ServerPlayer player) {
            this.player = player;
            this.level = player.level();
            this.pos = player.blockPosition();
            this.here = player.getBoundingBox();
            this.inv = player.getInventory().getNonEquipmentItems();
            this.knowledge = KnowledgeData.get(level.getServer());
            this.society = Society.keyOf(player);
        }

        int count(Ref ref) {
            return counts.computeIfAbsent(ref.kind() + ":" + ref.id(), k -> Requirements.count(inv, matcher(ref)));
        }

        boolean phenomenon(String id) {
            return phenomena.computeIfAbsent(id, k -> Environment.phenomenon(k, level, pos, player));
        }

        int animals(String env) {
            return animals.computeIfAbsent(env, k -> Environment.wildAnimals(level, pos, k, 16).size());
        }

        boolean stationNear(String id) {
            return stations.computeIfAbsent(id, k -> !StationIndex.formedNear(level, k, here, Config.integer(Config.PRESENCE_RADIUS)).isEmpty());
        }

        boolean nearWater() {
            if (nearWater == null) {
                nearWater = Requirements.nearWater(level, pos);
            }
            return nearWater;
        }

        boolean grid() {
            if (grid == null) {
                grid = gridCovered(level, here);
            }
            return grid;
        }

        int waterContainers() {
            if (waterContainers == null) {
                waterContainers = Requirements.count(inv, Requirements::isWaterContainer);
            }
            return waterContainers;
        }
    }

    public static Check player(ServerPlayer player, FunctionDef f) {
        return player(new PlayerContext(player), f);
    }

    public static Check player(PlayerContext ctx, FunctionDef f) {
        List<String> missing = new ArrayList<>();
        // One theory always rests on another. Practice may run ahead of theory unless strict mode is on.
        if (f.theoretical() || Config.bool(Config.THEORY_REQUIRED)) {
            for (String t : f.theory()) {
                if (!ctx.knowledge.knows(ctx.society, t)) {
                    missing.add("Theory: " + theoryName(t));
                }
            }
        }
        int radius = Config.integer(Config.PRESENCE_RADIUS);
        if (!f.theoretical()) {
            for (Ref in : f.inputs()) {
                switch (in.kind()) {
                    case ITEM, TAG, STATION -> {
                        int have = ctx.count(in);
                        if (have < in.count()) {
                            missing.add(in.name() + " x" + in.count() + " (have " + have + ")");
                        }
                    }
                    case ENV -> {
                        switch (in.id()) {
                            case "air" -> { }
                            case "water" -> {
                                if (!ctx.nearWater() && ctx.waterContainers() < in.count()) {
                                    missing.add("Water x" + in.count() + ": stand by a water source or carry water buckets/bottles (have "
                                            + ctx.waterContainers() + ")");
                                }
                            }
                            default -> {
                                int have = ctx.animals(in.id());
                                if (have < in.count()) {
                                    missing.add(in.name() + " x" + in.count() + " within 16 blocks (found " + have + ")");
                                }
                            }
                        }
                    }
                    case PHEN -> {
                        if (!ctx.phenomenon(in.id())) {
                            missing.add(in.name() + ": " + Environment.phenomenonText(in.id()));
                        }
                    }
                }
            }
        }
        for (Ref m : f.means()) {
            switch (m.kind()) {
                case ITEM, TAG -> {
                    if (ctx.count(m) < 1) {
                        missing.add((f.theoretical() ? "Evidence: " : "Tool: ") + m.name());
                    }
                }
                case STATION -> {
                    if ("electric_grid".equals(m.id())) {
                        if (!ctx.grid()) {
                            missing.add("Electric grid coverage (a substation within " + Config.integer(Config.GRID_RADIUS) + " blocks)");
                        }
                    } else if (!ctx.stationNear(m.id())) {
                        missing.add(m.name() + " (a complete one within " + radius + " blocks)");
                    }
                }
                case PHEN -> {
                    if (!ctx.phenomenon(m.id())) {
                        missing.add(m.name() + ": " + Environment.phenomenonText(m.id()));
                    }
                }
                case ENV -> {
                    switch (m.id()) {
                        case "air" -> { }
                        case "water" -> {
                            if (!ctx.nearWater() && ctx.waterContainers() < 1) {
                                missing.add("Water: " + Environment.envText("water"));
                            }
                        }
                        default -> {
                            if (ctx.animals(m.id()) < 1) {
                                missing.add(m.name() + ": " + Environment.envText(m.id()));
                            }
                        }
                    }
                }
            }
        }
        return new Check(missing.isEmpty(), List.copyOf(missing), missing.isEmpty() ? 1 : 0);
    }

    public static boolean nearWater(Level level, BlockPos pos) {
        return Environment.countFluid(level, pos, 4, 3, true, 1) >= 1;
    }

    /** Takes the inputs of one run from the player (after a successful check). */
    public static void consumeFromPlayer(ServerPlayer player, FunctionDef f) {
        NonNullList<ItemStack> inv = player.getInventory().getNonEquipmentItems();
        for (Ref in : f.inputs()) {
            switch (in.kind()) {
                case ITEM, TAG, STATION -> removeMatching(inv, matcher(in), in.count());
                case ENV -> {
                    if ("water".equals(in.id())) {
                        if (!nearWater(player.level(), player.blockPosition())) {
                            for (int i = 0; i < in.count(); i++) {
                                ItemStack empty = useWaterContainer(inv);
                                if (!empty.isEmpty() && !player.getInventory().add(empty)) {
                                    player.drop(empty, false, net.minecraft.util.Prediction.SERVER_ONLY);
                                }
                            }
                        }
                    } else if (!"air".equals(in.id())) {
                        List<Animal> animals = Environment.wildAnimals(player.level(), player.blockPosition(), in.id(), 16);
                        for (int i = 0; i < in.count() && i < animals.size(); i++) {
                            animals.get(i).discard();
                        }
                    }
                }
                case PHEN -> { }
            }
        }
        player.getInventory().setChanged();
    }

    /** Damages each hand tool used once (they are means of production, not inputs). */
    public static void wearPlayerTools(ServerPlayer player, FunctionDef f) {
        if (!Config.bool(Config.TOOLS_WEAR)) {
            return;
        }
        for (Ref m : f.means()) {
            if (m.kind() == TechTree.RefKind.ITEM || m.kind() == TechTree.RefKind.TAG) {
                Predicate<ItemStack> match = matcher(m);
                for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
                    if (match.test(s)) {
                        if (s.isDamageableItem()) {
                            s.hurtAndBreak(1, (ServerLevel) player.level(), player, item -> { });
                        }
                        break;
                    }
                }
            }
        }
    }

    public static void giveOutputs(ServerPlayer player, FunctionDef f, int batches) {
        for (Ref out : f.outputs()) {
            Item item = ModContent.item(out.id());
            if (item == null) {
                continue;
            }
            int total = out.count() * batches;
            while (total > 0) {
                ItemStack stack = new ItemStack(item, Math.min(total, item.getDefaultMaxStackSize()));
                total -= stack.getCount();
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
            }
        }
    }

    // ------------------------------------------------------------------ stations

    /** Checks without drawing power (see {@link #station(StationBlockEntity, FunctionDef, int, Phase, boolean)}). */
    public static Check station(StationBlockEntity be, FunctionDef f, int wanted, Phase phase) {
        return station(be, f, wanted, phase, false);
    }

    /** @param drawPower true only when the run is advancing this tick; engines then burn fuel for it */
    public static Check station(StationBlockEntity be, FunctionDef f, int wanted, Phase phase, boolean drawPower) {
        List<String> missing = new ArrayList<>();
        Level level = be.getLevel();
        TechTree.StationDef def = be.def();
        if (level == null || def == null) {
            return new Check(false, List.of("Station is not in a world"), 0);
        }
        if (!be.isFormed()) {
            missing.add("Structure incomplete: " + be.formation().problem());
            return new Check(false, missing, 0);
        }
        KnowledgeData kd = KnowledgeData.get(level.getServer());
        if (be.operator().isEmpty() || !kd.knows(be.operator(), f.id())) {
            missing.add("Whoever runs this station must know " + f.name());
        }
        AABB host = be.bounds();
        int radius = Config.integer(Config.PRESENCE_RADIUS);
        if ("grid".equals(def.power()) && !gridCovered(level, host)) {
            missing.add("Needs Electric grid coverage");
        } else if ("mechanical".equals(def.power()) && !mechanicalPower(level, host, drawPower)) {
            missing.add("Needs power: a running water wheel, windmill or steam engine within " + radius + " blocks, or the grid");
        }
        boolean needsCleanroom = f.means().stream().anyMatch(m -> m.kind() == TechTree.RefKind.STATION && "cleanroom".equals(m.id()));
        StationBlockEntity cleanroom = null;
        if (needsCleanroom && def.isPrecisionTool()) {
            cleanroom = cleanroomAround(level, host);
            if (cleanroom == null) {
                missing.add("This " + def.name() + " must stand inside a complete Cleanroom");
            }
        }
        List<ItemStack> tools = be.toolSlots();
        for (Ref m : f.means()) {
            switch (m.kind()) {
                case ITEM, TAG -> {
                    if (count(tools, matcher(m)) < 1) {
                        missing.add("Put " + m.name() + " in a tool slot");
                    }
                }
                case STATION -> {
                    if (m.id().equals(be.stationId())) {
                        continue;
                    }
                    if ("electric_grid".equals(m.id())) {
                        if (!gridCovered(level, host)) {
                            missing.add("Needs Electric grid coverage");
                        }
                        continue;
                    }
                    if ("cleanroom".equals(m.id()) && def.isPrecisionTool()) {
                        continue; // checked above: the tool must be inside it
                    }
                    TechTree.StationDef other = TechTree.get().stationsById.get(m.id());
                    List<StationBlockEntity> near = StationIndex.formedNear(level, m.id(), host, radius);
                    boolean ok = false;
                    for (StationBlockEntity s : near) {
                        if (needsCleanroom && other != null && other.isPrecisionTool() && cleanroom != null
                                && (cleanroom.interior() == null || !contains(cleanroom.interior(), s.bounds()))) {
                            continue;
                        }
                        if (other != null && other.provider() != null && !(drawPower ? s.drawPower(40) : s.canProvidePower())) {
                            continue;
                        }
                        ok = true;
                        break;
                    }
                    if (!ok) {
                        String extra = other != null && other.provider() != null ? ", running" : "";
                        if (needsCleanroom && other != null && other.isPrecisionTool()) {
                            extra += ", inside the same cleanroom";
                        }
                        missing.add("Needs a complete " + stationName(m.id()) + " within " + radius + " blocks" + extra);
                    }
                }
                case PHEN -> {
                    BlockPos at = "wind".equals(m.id()) || "sunlight".equals(m.id()) || "night_sky".equals(m.id())
                            ? be.formation().topCenter() : be.getBlockPos();
                    if (!Environment.phenomenon(m.id(), level, at, null)) {
                        missing.add(m.name() + ": " + Environment.phenomenonText(m.id()));
                    }
                }
                case ENV -> {
                    if ("water".equals(m.id()) && !be.hasWaterSupply() && count(be.inputSlots(), Requirements::isWaterContainer) < 1) {
                        missing.add("Water: a water source next to the structure, or water buckets in the inputs");
                    } else if (!"water".equals(m.id()) && !"air".equals(m.id())) {
                        missing.add(m.name() + " cannot be brought into a station");
                    }
                }
            }
        }
        int batches = Math.max(1, wanted);
        if (phase != Phase.RUNNING) {
            List<ItemStack> inputs = be.inputSlots();
            for (Ref in : f.inputs()) {
                switch (in.kind()) {
                    case ITEM, TAG, STATION -> {
                        int have = count(inputs, matcher(in));
                        int possible = have / in.count();
                        if (possible < 1) {
                            missing.add("Needs " + in.name() + " x" + in.count() + " in the inputs (has " + have + ")");
                        }
                        batches = Math.min(batches, possible);
                    }
                    case ENV -> {
                        if ("water".equals(in.id())) {
                            if (!be.hasWaterSupply()) {
                                int have = count(inputs, Requirements::isWaterContainer);
                                int possible = have / in.count();
                                if (possible < 1) {
                                    missing.add("Needs water: a water source within 2 blocks of the structure, or "
                                            + in.count() + " water bucket(s) in the inputs");
                                }
                                batches = Math.min(batches, possible);
                            }
                        } else if (!"air".equals(in.id())) {
                            missing.add(in.name() + " cannot be brought into a station");
                        }
                    }
                    case PHEN -> {
                        if (!Environment.phenomenon(in.id(), level, be.getBlockPos(), null)) {
                            missing.add(in.name() + ": " + Environment.phenomenonText(in.id()));
                        }
                    }
                }
            }
            if (missing.isEmpty()) {
                while (batches > 0 && !be.outputsFit(f, batches)) {
                    batches--;
                }
                if (batches < 1) {
                    missing.add("The output slots are full");
                }
            }
        }
        boolean ok = missing.isEmpty();
        return new Check(ok, List.copyOf(missing), ok ? batches : 0);
    }

    /** Removes the inputs of the given number of runs from a station (after a successful check). */
    public static void consumeFromStation(StationBlockEntity be, FunctionDef f, int batches) {
        List<ItemStack> inputs = be.inputSlots();
        for (Ref in : f.inputs()) {
            switch (in.kind()) {
                case ITEM, TAG, STATION -> removeMatching(inputs, matcher(in), in.count() * batches);
                case ENV -> {
                    if ("water".equals(in.id()) && !be.hasWaterSupply()) {
                        for (int i = 0; i < in.count() * batches; i++) {
                            for (int slot = 0; slot < inputs.size(); slot++) {
                                ItemStack s = inputs.get(slot);
                                if (isWaterContainer(s)) {
                                    ItemStack empty = emptiedContainer(s);
                                    s.shrink(1);
                                    if (s.isEmpty()) {
                                        be.setInputSlot(slot, empty);
                                    } else {
                                        be.addOutput(empty);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
                case PHEN -> { }
            }
        }
    }

    public static void wearStationTools(StationBlockEntity be, FunctionDef f) {
        if (!Config.bool(Config.TOOLS_WEAR) || !(be.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (Ref m : f.means()) {
            if (m.kind() == TechTree.RefKind.ITEM || m.kind() == TechTree.RefKind.TAG) {
                Predicate<ItemStack> match = matcher(m);
                for (ItemStack s : be.toolSlots()) {
                    if (match.test(s)) {
                        if (s.isDamageableItem()) {
                            s.hurtAndBreak(1, level, (ServerPlayer) null, item -> { });
                        }
                        break;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ inventory helpers

    private static void removeMatching(List<ItemStack> stacks, Predicate<ItemStack> match, int amount) {
        int left = amount;
        for (ItemStack s : stacks) {
            if (left <= 0) {
                break;
            }
            if (match.test(s)) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        if (left > 0) {
            spawn_9b5fbb9.LOGGER.warn("Tried to consume {} more items than were present", left);
        }
    }

    /** Empties one water container in the list and returns the empty container (or EMPTY if none was found). */
    private static ItemStack useWaterContainer(NonNullList<ItemStack> inv) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (isWaterContainer(s)) {
                ItemStack empty = emptiedContainer(s);
                if (s.getCount() == 1) {
                    inv.set(i, empty);
                    return ItemStack.EMPTY;
                }
                s.shrink(1);
                return empty;
            }
        }
        return ItemStack.EMPTY;
    }
}
