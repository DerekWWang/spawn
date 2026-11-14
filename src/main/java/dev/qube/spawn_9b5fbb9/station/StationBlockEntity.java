package dev.qube.spawn_9b5fbb9.station;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.knowledge.Environment;
import dev.qube.spawn_9b5fbb9.knowledge.KnowledgeData;
import dev.qube.spawn_9b5fbb9.knowledge.Requirements;
import dev.qube.spawn_9b5fbb9.knowledge.Society;
import dev.qube.spawn_9b5fbb9.registry.ModContent;
import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import dev.qube.spawn_9b5fbb9.techtree.TechTree.FunctionDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * State of one station controller: its slots, the function it is set to run, progress, fuel and
 * the cached result of the last structure check.
 */
public class StationBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int INPUT_START = 0;
    public static final int INPUTS = 15;
    public static final int TOOL_START = 15;
    public static final int TOOLS = 4;
    public static final int FUEL_SLOT = 19;
    public static final int OUTPUT_START = 20;
    public static final int OUTPUTS = 6;
    public static final int SIZE = 26;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX_PROGRESS = 1;
    public static final int DATA_BURN = 2;
    public static final int DATA_BURN_TOTAL = 3;
    public static final int DATA_FORMED = 4;
    public static final int DATA_BATCH = 5;
    public static final int DATA_SELECTED = 6;
    public static final int DATA_RUNNING = 7;
    public static final int DATA_COUNT = 8;

    private static final int[] INPUT_FACE;
    private static final int[] SIDE_FACE;
    private static final int[] OUTPUT_FACE;

    static {
        INPUT_FACE = new int[INPUTS];
        for (int i = 0; i < INPUTS; i++) {
            INPUT_FACE[i] = INPUT_START + i;
        }
        SIDE_FACE = new int[INPUTS + 1];
        System.arraycopy(INPUT_FACE, 0, SIDE_FACE, 0, INPUTS);
        SIDE_FACE[INPUTS] = FUEL_SLOT;
        OUTPUT_FACE = new int[OUTPUTS];
        for (int i = 0; i < OUTPUTS; i++) {
            OUTPUT_FACE[i] = OUTPUT_START + i;
        }
    }

    private static final TagKey<Item> FUEL_WOOD = itemTag("fuel/wood");
    private static final TagKey<Item> FUEL_WOOD_SMALL = itemTag("fuel/wood_small");
    private static final TagKey<Item> FUEL_CHARCOAL = itemTag("fuel/charcoal");
    private static final TagKey<Item> FUEL_COAL = itemTag("fuel/coal");
    private static final TagKey<Item> FUEL_COKE = itemTag("fuel/coke");

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final String stationId;

    private String selected = "";
    private String operator = "";
    private boolean running;
    private boolean paused;
    private int progress;
    private int maxProgress;
    private int batch;
    private int burnTime;
    private int burnTotal;
    private int burnTier;
    private int producerTimer;
    private int demandTicks;
    private long lastBurnTick = -1;
    private boolean rigPowered;
    private boolean dry;
    private long nextDryCheck;

    private StructureValidator.Formation formation;
    private int validateIn;
    private boolean waterSupply;
    private List<String> lastMissing = List.of();
    private String note = "";

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                // synced as shorts, so send fractions out of 1000 rather than raw ticks
                case DATA_PROGRESS -> maxProgress <= 0 ? 0 : (int) Math.min(1000L, progress * 1000L / maxProgress);
                case DATA_MAX_PROGRESS -> maxProgress <= 0 ? 0 : 1000;
                case DATA_BURN -> burnTotal <= 0 ? 0 : (int) Math.min(1000L, burnTime * 1000L / burnTotal);
                case DATA_BURN_TOTAL -> burnTotal <= 0 ? 0 : 1000;
                case DATA_FORMED -> isFormed() ? 1 : 0;
                case DATA_BATCH -> isFormed() ? formation.batch() : 0;
                case DATA_SELECTED -> selectedDef() == null ? -1 : selectedDef().index();
                case DATA_RUNNING -> running ? (paused ? 2 : 1) : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // server authoritative
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public StationBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.STATION_BE.get(), pos, state);
        this.stationId = state.getBlock() instanceof StationBlock sb ? sb.stationId() : "";
        this.formation = StructureValidator.Formation.unchecked(pos);
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, path));
    }

    // ------------------------------------------------------------------ accessors

    public String stationId() {
        return stationId;
    }

    public TechTree.@Nullable StationDef def() {
        return TechTree.get().stationsById.get(stationId);
    }

    public boolean isFormed() {
        return formation.formed();
    }

    public StructureValidator.Formation formation() {
        return formation;
    }

    public AABB bounds() {
        return formation.bounds();
    }

    public @Nullable AABB interior() {
        return formation.interior();
    }

    public boolean hasWaterSupply() {
        return waterSupply;
    }

    public String operator() {
        return operator;
    }

    public void setOperator(String society) {
        this.operator = society == null ? "" : society;
        setChanged();
    }

    public boolean isRunning() {
        return running && !paused;
    }

    public ContainerData data() {
        return data;
    }

    public @Nullable FunctionDef selectedDef() {
        return selected.isEmpty() ? null : TechTree.get().functionsById.get(selected);
    }

    public List<ItemStack> inputSlots() {
        return items.subList(INPUT_START, INPUT_START + INPUTS);
    }

    public List<ItemStack> toolSlots() {
        return items.subList(TOOL_START, TOOL_START + TOOLS);
    }

    public void setInputSlot(int index, ItemStack stack) {
        items.set(INPUT_START + index, stack);
        setChanged();
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            StationIndex.add(level, stationId, worldPosition);
            validateIn = 1 + Math.floorMod(worldPosition.hashCode(), 20);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide()) {
            StationIndex.remove(level, stationId, worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide()) {
            StationIndex.remove(level, stationId, worldPosition);
        }
        super.onChunkUnloaded();
    }

    // ------------------------------------------------------------------ ticking

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        TechTree.StationDef def = def();
        if (def == null) {
            return;
        }
        if (--validateIn <= 0) {
            revalidate(level, pos, state, def);
            validateIn = isFormed() ? 40 : 20;
        }
        if (demandTicks > 0) {
            demandTicks--;
            if (def.fuelTier() > 0) {
                burnOneTick(level);
            }
        }
        if (def.producer() != null) {
            tickProducer(level, def);
        }
        tickProcessing(level, def);
    }

    /** Re-checks the structure now (used by the station screen and tests). */
    public void revalidateNow() {
        if (level instanceof ServerLevel sl && def() != null) {
            revalidate(sl, worldPosition, getBlockState(), def());
        }
    }

    private void revalidate(ServerLevel level, BlockPos pos, BlockState state, TechTree.StationDef def) {
        Direction facing = state.hasProperty(StationBlock.FACING) ? state.getValue(StationBlock.FACING) : Direction.NORTH;
        StationIndex.add(level, stationId, pos); // idempotent; covers the tick before onLoad() runs
        StructureValidator.Formation f = StructureValidator.validate(level, pos, facing, def);
        boolean was = formation.formed();
        formation = f;
        waterSupply = f.formed() && Environment.waterSourceWithin(level, f.bounds().inflate(2));
        if (state.hasProperty(StationBlock.FORMED) && state.getValue(StationBlock.FORMED) != f.formed()) {
            level.setBlock(pos, state.setValue(StationBlock.FORMED, f.formed()), Block.UPDATE_CLIENTS);
        }
        if (was != f.formed()) {
            setChanged();
        }
    }

    private void burnOneTick(ServerLevel level) {
        long now = level.getGameTime();
        if (lastBurnTick != now && burnTime > 0) {
            burnTime--;
            lastBurnTick = now;
        }
    }

    /** Makes sure fuel of at least the given tier is burning. */
    private boolean ensureFuel(int tierNeeded) {
        if (burnTime > 0 && burnTier >= tierNeeded) {
            return true;
        }
        ItemStack fuel = items.get(FUEL_SLOT);
        int tier = fuelTier(fuel);
        if (tier < tierNeeded || tier == 0) {
            return false;
        }
        int ticks = fuelTicks(fuel);
        if (!operator.isEmpty() && level != null && level.getServer() != null
                && KnowledgeData.get(level.getServer()).knows(operator, "T-45")) {
            ticks = ticks * 5 / 4; // Thermodynamics: every engine and furnace runs more efficiently
        }
        fuel.shrink(1);
        burnTime = ticks;
        burnTotal = ticks;
        burnTier = tier;
        setChanged();
        return true;
    }

    public static int fuelTier(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.is(FUEL_COKE)) {
            return 3;
        }
        if (stack.is(FUEL_CHARCOAL) || stack.is(FUEL_COAL)) {
            return 2;
        }
        if (stack.is(FUEL_WOOD) || stack.is(FUEL_WOOD_SMALL)) {
            return 1;
        }
        return 0;
    }

    private static int fuelTicks(ItemStack stack) {
        if (stack.is(FUEL_COKE)) {
            return 3200;
        }
        if (stack.is(net.minecraft.world.item.Items.COAL_BLOCK)) {
            return 14400; // nine coal
        }
        if (stack.is(FUEL_CHARCOAL) || stack.is(FUEL_COAL)) {
            return 1600;
        }
        if (stack.is(FUEL_WOOD)) {
            return 300;
        }
        return 100;
    }

    /** Whether this provider could deliver power right now, without lighting any fuel. */
    public boolean canProvidePower() {
        TechTree.StationDef def = def();
        if (def == null || def.provider() == null || !isFormed()) {
            return false;
        }
        if (def.fuelTier() <= 0) {
            return true;
        }
        return (burnTime > 0 && burnTier >= def.fuelTier()) || fuelTier(items.get(FUEL_SLOT)) >= def.fuelTier();
    }

    /**
     * Called by machines that need this one's mechanical power. Engines burn fuel only while
     * something draws on them.
     */
    public boolean drawPower(int ticks) {
        TechTree.StationDef def = def();
        if (def == null || def.provider() == null || !isFormed()) {
            return false;
        }
        if (def.fuelTier() > 0 && !ensureFuel(def.fuelTier())) {
            return false;
        }
        demandTicks = Math.max(demandTicks, ticks);
        return true;
    }

    private void tickProducer(ServerLevel level, TechTree.StationDef def) {
        TechTree.Producer p = def.producer();
        if (!isFormed()) {
            return;
        }
        if (p.function() != null && (operator.isEmpty() || !KnowledgeData.get(level.getServer()).knows(operator, p.function()))) {
            note = "Whoever built this must know " + nameOf(p.function()) + " for it to yield anything";
            return;
        }
        if (p.item() != null) {
            if (++producerTimer < p.interval()) {
                return;
            }
            producerTimer = 0;
            Item item = ModContent.item(p.item());
            if (item == null) {
                return;
            }
            int have = 0;
            for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUTS; i++) {
                if (items.get(i).is(item)) {
                    have += items.get(i).getCount();
                }
            }
            if (have >= p.max() || !addOutput(new ItemStack(item))) {
                note = "Full: collect from the output slots";
            } else {
                note = "";
            }
            return;
        }
        // drilling rig: draws power only while it has somewhere to put oil and a reservoir to pump
        if (level.getGameTime() % 20 == Math.floorMod(worldPosition.hashCode(), 20)) {
            ItemStack probe = ModContent.stack("crude_oil");
            probe.setCount(Math.max(1, p.perBlock()));
            boolean full = !outputsFit(List.of(probe));
            if (full) {
                note = "Full: collect from the output slots";
                rigPowered = false;
            } else if (dry && (level.getGameTime() < nextDryCheck || findReservoir(level, p) == null)) {
                if (level.getGameTime() >= nextDryCheck) {
                    nextDryCheck = level.getGameTime() + 1200; // look again now and then, without drawing power
                }
                rigPowered = false;
            } else {
                dry = false;
                rigPowered = !"mechanical".equals(def.power()) || Requirements.mechanicalPower(level, bounds(), true);
                if (!rigPowered) {
                    note = "Idle: needs a running engine, water wheel or windmill within " + Config.integer(Config.PRESENCE_RADIUS)
                            + " blocks, or the grid";
                }
            }
        }
        if (!rigPowered) {
            return;
        }
        if (++producerTimer < p.interval()) {
            return;
        }
        producerTimer = 0;
        drill(level, p);
    }

    /** The nearest oil- or gas-bearing block below the well, or null. Only scans loaded chunks. */
    private @Nullable BlockPos findReservoir(ServerLevel level, TechTree.Producer p) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        Block oil = blockOf("oil_reservoir");
        Block gas = blockOf("gas_reservoir");
        for (int y = worldPosition.getY() - 1; y >= level.getMinY(); y--) {
            for (int dx = -p.radius(); dx <= p.radius(); dx++) {
                for (int dz = -p.radius(); dz <= p.radius(); dz++) {
                    m.set(worldPosition.getX() + dx, y, worldPosition.getZ() + dz);
                    if (level.isLoaded(m)) {
                        BlockState s = level.getBlockState(m);
                        if (s.is(oil) || s.is(gas)) {
                            return m.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Drilling rig: turns one block of oil- or gas-bearing rock below into stone and yields its contents. */
    private void drill(ServerLevel level, TechTree.Producer p) {
        BlockPos at = findReservoir(level, p);
        if (at == null) {
            dry = true;
            nextDryCheck = level.getGameTime() + 1200;
            note = "Dry: no oil- or gas-bearing rock within " + p.radius() + " blocks of the well below";
            return;
        }
        String product = level.getBlockState(at).is(blockOf("oil_reservoir")) ? "crude_oil" : "natural_gas";
        ItemStack out = ModContent.stack(product);
        out.setCount(p.perBlock());
        if (!outputsFit(List.of(out))) {
            note = "Full: collect from the output slots";
            return;
        }
        level.setBlock(at, at.getY() < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        addOutput(out);
        dry = false;
        note = "Pumping from y " + at.getY();
    }

    private static Block blockOf(String id) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, id));
    }

    private static String nameOf(String functionId) {
        FunctionDef f = TechTree.get().functionsById.get(functionId);
        return f == null ? functionId : f.name();
    }

    private void tickProcessing(ServerLevel level, TechTree.StationDef def) {
        FunctionDef f = selectedDef();
        if (f == null) {
            if (running) {
                stop();
            }
            return;
        }
        boolean checkTick = Math.floorMod(level.getGameTime() + worldPosition.hashCode(), 20) == 0;
        if (!running) {
            if (!checkTick) {
                return;
            }
            Requirements.Check c = Requirements.station(this, f, isFormed() ? formation.batch() : 1, Requirements.Phase.START);
            lastMissing = c.missing();
            if (!c.ok()) {
                return;
            }
            running = true;
            paused = false;
            progress = 0;
            batch = c.batches();
            maxProgress = Math.max(1, (int) Math.round(f.ticks() * Config.dbl(Config.TIME_MULTIPLIER)));
            setChanged();
        }
        if (checkTick) {
            // engines are only drawn on (and burn fuel) while the run is still advancing
            Requirements.Check c = Requirements.station(this, f, batch, Requirements.Phase.RUNNING, progress < maxProgress);
            lastMissing = c.missing();
            paused = !c.ok();
        }
        if (paused) {
            return;
        }
        if (progress < maxProgress) {
            if (needsFuel(def, f)) {
                if (!ensureFuel(def.fuelTier())) {
                    paused = true;
                    lastMissing = List.of(def.fuelTier() >= 2 ? "Out of fuel: put charcoal, coal or coke in the fuel slot"
                            : "Out of fuel: put wood, charcoal or coal in the fuel slot");
                    return;
                }
                burnOneTick(level);
            }
            progress++;
            setChanged();
            return;
        }
        Requirements.Check c = Requirements.station(this, f, batch, Requirements.Phase.COMPLETE);
        lastMissing = c.missing();
        if (!c.ok()) {
            paused = true;
            return;
        }
        Requirements.consumeFromStation(this, f, c.batches());
        for (TechTree.Ref out : f.outputs()) {
            Item item = ModContent.item(out.id());
            if (item == null) {
                continue;
            }
            int total = out.count() * c.batches();
            while (total > 0) {
                ItemStack stack = new ItemStack(item, Math.min(total, item.getDefaultMaxStackSize()));
                total -= stack.getCount();
                if (!addOutput(stack)) {
                    spawn_9b5fbb9.LOGGER.warn("Station at {} could not store {}; dropping it", worldPosition, stack);
                    net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                            worldPosition.getZ() + 0.5, stack);
                }
            }
        }
        Requirements.wearStationTools(this, f);
        running = false;
        paused = false;
        progress = 0;
        lastMissing = List.of();
        setChanged();
    }

    private static boolean needsFuel(TechTree.StationDef def, FunctionDef f) {
        if (def.fuelTier() <= 0) {
            return false;
        }
        // a charge that already contains its own fuel (charcoal in a bloomery, wood in a pit firing) needs no extra
        for (TechTree.Ref in : f.inputs()) {
            int tier = switch (in.kind() == TechTree.RefKind.TAG ? "tag:" + in.id() : "item:" + in.id()) {
                case "tag:wood" -> 1;
                case "tag:coal", "item:charcoal" -> 2;
                case "item:coke" -> 3;
                default -> 0;
            };
            if (tier >= def.fuelTier()) {
                return false;
            }
        }
        return true;
    }

    private void stop() {
        running = false;
        paused = false;
        progress = 0;
        setChanged();
    }

    // ------------------------------------------------------------------ selection (from the menu)

    /** @return a message for the player, or null on success */
    public @Nullable String select(ServerPlayer player, int functionIndex) {
        if (functionIndex < 0) {
            selected = "";
            stop();
            return null;
        }
        FunctionDef f = TechTree.get().function(functionIndex);
        if (f == null || f.mode() != TechTree.Mode.STATION || !stationId.equals(f.host())) {
            return "That function is not performed at this station.";
        }
        if (!Society.knows(player, f.id())) {
            return "Your society does not know how to perform " + f.name() + " yet.";
        }
        if (!f.id().equals(selected)) {
            selected = f.id();
            stop();
        }
        operator = Society.keyOf(player);
        lastMissing = List.of();
        setChanged();
        return null;
    }

    /** Selects a function without a player (game tests). The operator must be set separately. */
    public void debugSelect(String functionId) {
        FunctionDef f = TechTree.get().functionsById.get(functionId);
        if (f != null && f.mode() == TechTree.Mode.STATION && stationId.equals(f.host())) {
            selected = f.id();
            stop();
        }
    }

    /** Status lines for the station screen. */
    public List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        TechTree.StationDef def = def();
        if (def == null) {
            return lines;
        }
        if (!isFormed()) {
            lines.add("§cIncomplete: " + formation.problem());
        } else {
            TechTree.Tier t = def.tiers().get(Math.max(0, formation.tier()));
            String size = "single".equals(def.shape()) || "area".equals(def.shape()) || "shaft".equals(def.shape()) ? ""
                    : " (" + t.w() + "x" + t.h() + "x" + t.d() + ")";
            lines.add("§aComplete" + size + (formation.batch() > 1 ? ", runs " + formation.batch() + " batches at once" : ""));
        }
        if (!note.isEmpty()) {
            lines.add(note);
        }
        FunctionDef f = selectedDef();
        if (f != null) {
            if (running && !paused) {
                lines.add("Working: " + f.name() + (batch > 1 ? " x" + batch : ""));
            }
            for (String m : lastMissing) {
                lines.add("§e" + m);
            }
        } else if (!def.hosts().isEmpty()) {
            lines.add("Choose a function on the left.");
        }
        return lines;
    }

    // ------------------------------------------------------------------ outputs

    public boolean outputsFit(FunctionDef f, int batches) {
        List<ItemStack> stacks = new ArrayList<>();
        for (TechTree.Ref out : f.outputs()) {
            Item item = ModContent.item(out.id());
            if (item == null) {
                continue;
            }
            int total = out.count() * batches;
            while (total > 0) {
                int n = Math.min(total, item.getDefaultMaxStackSize());
                stacks.add(new ItemStack(item, n));
                total -= n;
            }
        }
        return outputsFit(stacks);
    }

    private boolean outputsFit(List<ItemStack> stacks) {
        List<ItemStack> sim = new ArrayList<>();
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUTS; i++) {
            sim.add(items.get(i).copy());
        }
        for (ItemStack s : stacks) {
            if (!insert(sim, s.copy())) {
                return false;
            }
        }
        return true;
    }

    /** Puts a stack into the output slots. @return false if it did not all fit (nothing is lost: the caller decides) */
    public boolean addOutput(ItemStack stack) {
        List<ItemStack> outs = items.subList(OUTPUT_START, OUTPUT_START + OUTPUTS);
        boolean ok = insert(outs, stack);
        setChanged();
        return ok;
    }

    private static boolean insert(List<ItemStack> slots, ItemStack stack) {
        for (ItemStack s : slots) {
            if (stack.isEmpty()) {
                return true;
            }
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack)) {
                int room = s.getMaxStackSize() - s.getCount();
                int move = Math.min(room, stack.getCount());
                if (move > 0) {
                    s.grow(move);
                    stack.shrink(move);
                }
            }
        }
        for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
            if (slots.get(i).isEmpty()) {
                int move = Math.min(stack.getCount(), stack.getMaxStackSize());
                slots.set(i, stack.split(move));
            }
        }
        return stack.isEmpty();
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(input, items);
        selected = input.getStringOr("selected", "");
        if (!selected.isEmpty() && !TechTree.get().functionsById.containsKey(selected)) {
            selected = ""; // the tree changed since this was saved
        }
        operator = input.getStringOr("operator", "");
        running = input.getBooleanOr("running", false);
        paused = false;
        progress = input.getIntOr("progress", 0);
        maxProgress = input.getIntOr("max_progress", 0);
        batch = Math.max(1, input.getIntOr("batch", 1));
        burnTime = input.getIntOr("burn_time", 0);
        burnTotal = input.getIntOr("burn_total", 0);
        burnTier = input.getIntOr("burn_tier", 0);
        producerTimer = input.getIntOr("producer_timer", 0);
        if (running && maxProgress <= 0) {
            running = false;
            progress = 0;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putString("selected", selected);
        output.putString("operator", operator);
        output.putBoolean("running", running);
        output.putInt("progress", progress);
        output.putInt("max_progress", maxProgress);
        output.putInt("batch", batch);
        output.putInt("burn_time", burnTime);
        output.putInt("burn_total", burnTotal);
        output.putInt("burn_tier", burnTier);
        output.putInt("producer_timer", producerTimer);
    }

    // ------------------------------------------------------------------ container

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SIZE ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack s = ContainerHelper.removeItem(items, slot, count);
        if (!s.isEmpty()) {
            setChanged();
        }
        return s;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        items.set(slot, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    /** Fuel this station can actually burn (a forge will not take logs). */
    public static boolean acceptsFuel(TechTree.@Nullable StationDef def, ItemStack stack) {
        int tier = fuelTier(stack);
        return def != null && def.fuelTier() > 0 && tier >= def.fuelTier();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == FUEL_SLOT) {
            return acceptsFuel(def(), stack);
        }
        return slot < OUTPUT_START;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return switch (side) {
            case DOWN -> OUTPUT_FACE;
            case UP -> INPUT_FACE;
            default -> SIDE_FACE;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        if (slot == FUEL_SLOT) {
            return acceptsFuel(def(), stack);
        }
        return slot >= INPUT_START && slot < INPUT_START + INPUTS;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= OUTPUT_START && slot < OUTPUT_START + OUTPUTS;
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        revalidateNow();
        if (operator.isEmpty() && player instanceof ServerPlayer sp) {
            setOperator(Society.keyOf(sp)); // e.g. placed by a machine: the first user claims it
        }
        return new StationMenu(containerId, inventory, this, data, worldPosition, stationId);
    }
}
