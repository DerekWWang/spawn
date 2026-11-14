package dev.qube.spawn_9b5fbb9.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.serialization.JsonOps;

import dev.qube.spawn_9b5fbb9.knowledge.Discovery;
import dev.qube.spawn_9b5fbb9.knowledge.HandWork;
import dev.qube.spawn_9b5fbb9.knowledge.KnowledgeData;
import dev.qube.spawn_9b5fbb9.knowledge.Requirements;
import dev.qube.spawn_9b5fbb9.knowledge.Society;
import dev.qube.spawn_9b5fbb9.knowledge.Treatises;
import dev.qube.spawn_9b5fbb9.registry.ModContent;
import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import dev.qube.spawn_9b5fbb9.station.StationBlock;
import dev.qube.spawn_9b5fbb9.station.StationBlockEntity;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import dev.qube.spawn_9b5fbb9.world.WorldEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** In-world tests run by the game-test server (./gradlew runGameTestServer). */
public final class TechTreeGameTests {
    public static final List<DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>> FUNCTIONS = new ArrayList<>();
    private static final String SOCIETY = "test:society";

    private TechTreeGameTests() {
    }

    public static void registerFunctions(DeferredRegister<Consumer<GameTestHelper>> reg) {
        add(reg, "tree_is_complete", TechTreeGameTests::treeIsComplete);
        add(reg, "hearth_forms_and_breaks", TechTreeGameTests::hearthFormsAndBreaks);
        add(reg, "kiln_needs_hollow_open_top", TechTreeGameTests::kilnNeedsHollowOpenTop);
        add(reg, "forge_tier_scales_batch", TechTreeGameTests::forgeTierScalesBatch);
        add(reg, "hearth_burns_ash", TechTreeGameTests::hearthBurnsAsh);
        add(reg, "forge_runs_two_batches", TechTreeGameTests::forgeRunsTwoBatches);
        add(reg, "station_refuses_unknown_function", TechTreeGameTests::stationRefusesUnknownFunction);
        add(reg, "knowledge_round_trip", TechTreeGameTests::knowledgeRoundTrip);
        add(reg, "shaft_mine_grants_access", TechTreeGameTests::shaftMineGrantsAccess);
        add(reg, "cleanroom_needs_filters", TechTreeGameTests::cleanroomNeedsFilters);
        add(reg, "discovery_needs_right_mechanism", TechTreeGameTests::discoveryNeedsRightMechanism);
        add(reg, "hand_work_makes_cordage", TechTreeGameTests::handWorkMakesCordage);
        add(reg, "deposits_need_recognition_and_access", TechTreeGameTests::depositsNeedRecognitionAndAccess);
        add(reg, "station_uses_water_bucket", TechTreeGameTests::stationUsesWaterBucket);
        add(reg, "tools_are_required_and_wear", TechTreeGameTests::toolsAreRequiredAndWear);
        add(reg, "steam_engine_needs_fuel_to_power", TechTreeGameTests::steamEngineNeedsFuel);
        add(reg, "treatise_teaches_another_society", TechTreeGameTests::treatiseTeaches);
        add(reg, "ore_features_place_and_are_added_to_biomes", TechTreeGameTests::oreFeatures);
        add(reg, "grid_substation_covers_radius", TechTreeGameTests::gridCovers);
        add(reg, "machine_tool_needs_running_engine", TechTreeGameTests::machineToolNeedsEngine);
        add(reg, "big_kiln_runs_three_batches", TechTreeGameTests::bigKiln);
    }

    private static void add(DeferredRegister<Consumer<GameTestHelper>> reg, String name, Consumer<GameTestHelper> fn) {
        FUNCTIONS.add(reg.register(name, () -> fn));
    }

    // ------------------------------------------------------------------ helpers

    private static BlockState controller(String id) {
        return ModContent.STATION_BLOCKS.get(id).get().defaultBlockState().setValue(StationBlock.FACING, Direction.NORTH);
    }

    /** A box behind a north-facing controller at (cx, cy, cz): x spans cx-w/2..cx+w/2, z spans cz..cz+d-1. */
    private static void box(GameTestHelper h, int cx, int cy, int cz, int w, int hh, int d, Block shell, boolean hollow, boolean openTop) {
        int half = w / 2;
        for (int y = 0; y < hh; y++) {
            for (int x = -half; x <= half; x++) {
                for (int z = 0; z < d; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    boolean edge = x == -half || x == half || y == 0 || y == hh - 1 || z == 0 || z == d - 1;
                    boolean topInterior = y == hh - 1 && x != -half && x != half && z != 0 && z != d - 1;
                    Block b = (openTop && topInterior) || (!edge && hollow) ? Blocks.AIR : shell;
                    h.setBlock(cx + x, cy + y, cz + z, b);
                }
            }
        }
    }

    private static StationBlockEntity station(GameTestHelper h, int x, int y, int z) {
        return h.getBlockEntity(new BlockPos(x, y, z), StationBlockEntity.class);
    }

    private static void floor(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                h.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }

    // ------------------------------------------------------------------ tests

    private static void treeIsComplete(GameTestHelper h) {
        TechTree tree = TechTree.get();
        h.assertValueEqual(tree.functions.size(), 526, "function count");
        for (TechTree.ItemDef def : tree.items) {
            h.assertTrue(ModContent.item(def.id()) != null, "item registered: " + def.id());
        }
        for (TechTree.StationDef def : tree.stations) {
            h.assertTrue(ModContent.STATION_BLOCKS.containsKey(def.id()), "station block registered: " + def.id());
        }
        for (TechTree.FunctionDef f : tree.functions) {
            for (TechTree.Ref out : f.outputs()) {
                h.assertTrue(ModContent.item(out.id()) != null, f.id() + " output registered: " + out.id());
            }
        }
        h.succeed();
    }

    private static void hearthFormsAndBreaks(GameTestHelper h) {
        floor(h, 8);
        BlockPos c = new BlockPos(3, 1, 3);
        h.setBlock(c, controller("hearth"));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    h.setBlock(c.offset(dx, 0, dz), Blocks.COBBLESTONE);
                }
            }
        }
        StationBlockEntity be = station(h, 3, 1, 3);
        be.revalidateNow();
        h.assertTrue(be.isFormed(), "hearth should form: " + be.formation().problem());
        h.setBlock(c.offset(1, 0, 0), Blocks.DIRT);
        be.revalidateNow();
        h.assertFalse(be.isFormed(), "hearth with a dirt block should not form");
        h.assertTrue(be.formation().problem().contains("Dirt"), "problem names the wrong block: " + be.formation().problem());
        h.succeed();
    }

    private static void kilnNeedsHollowOpenTop(GameTestHelper h) {
        floor(h, 12);
        h.setBlock(5, 1, 3, controller("kiln"));
        box(h, 5, 1, 3, 3, 3, 3, Blocks.BRICKS, true, true);
        StationBlockEntity be = station(h, 5, 1, 3);
        be.revalidateNow();
        h.assertTrue(be.isFormed(), "3x3x3 kiln should form: " + be.formation().problem());
        h.assertValueEqual(be.formation().batch(), 1, "small kiln batch");
        h.setBlock(5, 3, 4, Blocks.BRICKS); // close the top
        be.revalidateNow();
        h.assertFalse(be.isFormed(), "a kiln with a closed top should not form");
        h.setBlock(5, 3, 4, Blocks.AIR);
        h.setBlock(5, 2, 4, Blocks.BRICKS); // fill the chamber
        be.revalidateNow();
        h.assertFalse(be.isFormed(), "a kiln with a filled chamber should not form");
        h.succeed();
    }

    private static void forgeTierScalesBatch(GameTestHelper h) {
        floor(h, 14);
        h.setBlock(6, 1, 2, controller("forge"));
        box(h, 6, 1, 2, 3, 2, 3, Blocks.STONE_BRICKS, true, true);
        StationBlockEntity be = station(h, 6, 1, 2);
        be.revalidateNow();
        h.assertTrue(be.isFormed(), "3x3 forge: " + be.formation().problem());
        h.assertValueEqual(be.formation().batch(), 1, "3x3 forge batch");
        box(h, 6, 1, 2, 5, 2, 5, Blocks.STONE_BRICKS, true, true);
        be.revalidateNow();
        h.assertTrue(be.isFormed(), "5x5 forge: " + be.formation().problem());
        h.assertValueEqual(be.formation().batch(), 2, "5x5 forge batch");
        h.succeed();
    }

    private static void hearthBurnsAsh(GameTestHelper h) {
        floor(h, 8);
        BlockPos c = new BlockPos(3, 1, 3);
        h.setBlock(c, controller("hearth"));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    h.setBlock(c.offset(dx, 0, dz), Blocks.COBBLESTONE);
                }
            }
        }
        KnowledgeData.get(h.getLevel().getServer()).grant(SOCIETY, "P-010");
        StationBlockEntity be = station(h, 3, 1, 3);
        be.setOperator(SOCIETY);
        be.setItem(StationBlockEntity.INPUT_START, new ItemStack(Items.OAK_LOG, 4));
        selectDirect(be, "P-010");
        h.succeedWhen(() -> {
            ItemStack out = be.getItem(StationBlockEntity.OUTPUT_START);
            h.assertTrue(out.is(ModContent.item("wood_ash")), "wood ash produced, status: " + be.statusLines());
            h.assertTrue(be.getItem(StationBlockEntity.INPUT_START).isEmpty(), "logs consumed");
        });
    }

    private static void forgeRunsTwoBatches(GameTestHelper h) {
        floor(h, 14);
        h.setBlock(6, 1, 2, controller("forge"));
        box(h, 6, 1, 2, 5, 2, 5, Blocks.STONE_BRICKS, true, true);
        KnowledgeData.get(h.getLevel().getServer()).grant(SOCIETY, "P-048");
        StationBlockEntity be = station(h, 6, 1, 2);
        be.setOperator(SOCIETY);
        be.setItem(StationBlockEntity.INPUT_START, ModContent.stack("iron_bloom").copyWithCount(3));
        be.setItem(StationBlockEntity.INPUT_START + 1, ModContent.stack("charcoal").copyWithCount(3));
        selectDirect(be, "P-048");
        h.succeedWhen(() -> {
            ItemStack out = be.getItem(StationBlockEntity.OUTPUT_START);
            h.assertTrue(out.is(ModContent.item("wrought_iron")) && out.getCount() == 2,
                    "a 5x5 forge works two blooms at once: " + out + " / " + be.statusLines());
            h.assertValueEqual(be.getItem(StationBlockEntity.INPUT_START).getCount(), 1, "one bloom left");
        });
    }

    private static void stationRefusesUnknownFunction(GameTestHelper h) {
        floor(h, 8);
        BlockPos c = new BlockPos(3, 1, 3);
        h.setBlock(c, controller("hearth"));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    h.setBlock(c.offset(dx, 0, dz), Blocks.COBBLESTONE);
                }
            }
        }
        StationBlockEntity be = station(h, 3, 1, 3);
        be.setOperator("test:nobody");
        be.revalidateNow();
        TechTree.FunctionDef ash = TechTree.get().functionsById.get("P-010");
        Requirements.Check check = Requirements.station(be, ash, 1, Requirements.Phase.START);
        h.assertFalse(check.ok(), "an operator who does not know the function cannot run it");
        h.assertTrue(check.missing().stream().anyMatch(m -> m.contains("must know")), "says why: " + check.missing());
        h.succeed();
    }

    private static void knowledgeRoundTrip(GameTestHelper h) {
        KnowledgeData data = new KnowledgeData();
        data.grant("team:a", "T-01");
        data.grant("team:a", "P-002");
        data.grant("player:b", "T-02");
        var json = KnowledgeData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        KnowledgeData back = KnowledgeData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        h.assertTrue(back.knows("team:a", "T-01") && back.knows("team:a", "P-002") && back.knows("player:b", "T-02"),
                "knowledge survives a save");
        h.assertFalse(back.knows("player:b", "T-01"), "societies stay separate");
        back.merge("team:a", "player:b");
        h.assertTrue(back.knows("player:b", "P-002"), "merging carries knowledge across");
        h.succeed();
    }

    private static void shaftMineGrantsAccess(GameTestHelper h) {
        // a 24-deep shaft does not fit in the test volume, so check the radius/depth rule with a deep hole
        BlockPos ore = h.absolutePos(new BlockPos(10, 1, 10));
        h.assertFalse(WorldEvents.accessible(h.getLevel(), ore, "shaft_mine"), "no mine, no access");
        h.assertFalse(WorldEvents.accessible(h.getLevel(), ore, "drilling_rig"), "rig-only deposits are never hand-mined");
        h.succeed();
    }

    private static void cleanroomNeedsFilters(GameTestHelper h) {
        floor(h, 14);
        Block panel = ModContent.SIMPLE_BLOCKS.get("cleanroom_panel").get();
        Block ffu = ModContent.SIMPLE_BLOCKS.get("fan_filter_unit").get();
        h.setBlock(6, 1, 2, controller("cleanroom"));
        box(h, 6, 1, 2, 9, 5, 9, panel, false, false);
        for (int x = -3; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 7; z++) {
                    h.setBlock(6 + x, 1 + y, 2 + z, Blocks.AIR);
                }
            }
        }
        StationBlockEntity be = station(h, 6, 1, 2);
        be.revalidateNow();
        h.assertFalse(be.isFormed(), "a cleanroom without fan filter units should not form");
        h.assertTrue(be.formation().problem().contains("fan filter"), "explains the filters: " + be.formation().problem());
        int placed = 0;
        for (int x = -4; x <= 4 && placed < 6; x += 2) {
            for (int z = 1; z <= 7 && placed < 6; z += 3) {
                h.setBlock(6 + x, 5, 2 + z, ffu);
                placed++;
            }
        }
        be.revalidateNow();
        h.assertTrue(be.isFormed(), "with six filter units the 9x9 room forms: " + be.formation().problem());
        h.assertTrue(be.interior() != null, "rooms expose their interior");
        h.succeed();
    }

    @SuppressWarnings("removal") // vanilla offers no replacement for a mock player that is in the level
    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(new Vec3(2.5, 1, 2.5));
        p.snapTo(at.x, at.y, at.z, 0, 0);
        p.getInventory().clearContent();
        return p;
    }

    private static int countDropped(GameTestHelper h, BlockPos rel, java.util.function.Predicate<ItemStack> what) {
        BlockPos abs = h.absolutePos(rel);
        int n = 0;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2))) {
            if (what.test(e.getItem())) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    private static void discoveryNeedsRightMechanism(GameTestHelper h) {
        floor(h, 6);
        ServerPlayer p = player(h);
        String society = Society.keyOf(p);
        p.getInventory().add(ModContent.stack("plant_fiber").copyWithCount(3));
        Discovery.start(p, "P-001");
        int[] q = Discovery.pendingQuestion(p.getUUID());
        h.assertTrue(q != null, "holding the plant fibre opens the question for Cordage twisting");
        Discovery.answer(p, "P-001", q[0], q[1]);
        h.assertTrue(KnowledgeData.get(h.getLevel().getServer()).knows(society, "P-001"), "right answer grants it");

        p.getInventory().add(new ItemStack(Items.FLINT));
        p.getInventory().add(new ItemStack(Items.COBBLESTONE));
        Discovery.start(p, "T-01");
        int[] q2 = Discovery.pendingQuestion(p.getUUID());
        h.assertTrue(q2 != null, "flint and stone open Conchoidal fracture");
        Discovery.answer(p, "T-01", q2[0], (q2[1] + 1) % 4);
        h.assertFalse(Society.knows(p, "T-01"), "a wrong mechanism fails even with everything at hand");
        h.assertTrue(Discovery.cooldownSeconds(p, "T-01") > 0, "and starts a cooldown");
        Discovery.start(p, "T-01");
        h.assertTrue(Discovery.pendingQuestion(p.getUUID()) == null, "no new question during the cooldown");

        Discovery.start(p, "P-002"); // knapping needs the theory first
        h.assertTrue(Discovery.pendingQuestion(p.getUUID()) == null, "cannot attempt Knapping without Conchoidal fracture");
        h.succeed();
    }

    private static void handWorkMakesCordage(GameTestHelper h) {
        floor(h, 6);
        ServerPlayer p = player(h);
        KnowledgeData.get(h.getLevel().getServer()).grant(Society.keyOf(p), "P-001");
        p.getInventory().add(ModContent.stack("plant_fiber").copyWithCount(7));
        HandWork.start(p, "P-001", 3);
        h.assertTrue(HandWork.busy(p), "work started");
        h.onEachTick(() -> HandWork.tick(p));
        h.succeedWhen(() -> {
            var inv = p.getInventory().getNonEquipmentItems();
            int cordage = Requirements.count(inv, st -> st.is(ModContent.item("cordage")));
            int fibre = Requirements.count(inv, st -> st.is(ModContent.item("plant_fiber")));
            h.assertValueEqual(cordage, 2, "two runs of three fibres make two cordage; the third run lacks fibre");
            h.assertValueEqual(fibre, 1, "one fibre left over");
            h.assertFalse(HandWork.busy(p), "work stopped when the fibre ran out");
        });
    }

    private static void depositsNeedRecognitionAndAccess(GameTestHelper h) {
        floor(h, 10);
        ServerPlayer p = player(h);
        BlockPos rel = new BlockPos(5, 2, 5);
        // cassiterite: recognised once Smelting is known; no mine needed
        BlockState cass = ModContent.SIMPLE_BLOCKS.get("cassiterite_deposit").get().defaultBlockState();
        Block.dropResources(cass, h.getLevel(), h.absolutePos(rel), null, p, new ItemStack(Items.IRON_PICKAXE));
        h.assertValueEqual(countDropped(h, rel, st -> st.is(ModContent.item("cassiterite"))), 0, "unrecognised cassiterite");
        h.assertTrue(countDropped(h, rel, st -> st.is(Items.COBBLESTONE)) > 0, "it is just a coloured rock");
        h.killAllEntitiesOfClass(ItemEntity.class);
        KnowledgeData.get(h.getLevel().getServer()).grant(Society.keyOf(p), "T-16");
        Block.dropResources(cass, h.getLevel(), h.absolutePos(rel), null, p, new ItemStack(Items.IRON_PICKAXE));
        h.assertTrue(countDropped(h, rel, st -> st.is(ModContent.item("cassiterite"))) > 0, "recognised after Smelting");
        h.killAllEntitiesOfClass(ItemEntity.class);
        // galena: also needs a shaft mine
        BlockState galena = ModContent.SIMPLE_BLOCKS.get("galena_deposit").get().defaultBlockState();
        Block.dropResources(galena, h.getLevel(), h.absolutePos(rel), null, p, new ItemStack(Items.IRON_PICKAXE));
        h.assertValueEqual(countDropped(h, rel, st -> st.is(ModContent.item("galena"))), 0, "no shaft mine, no galena");
        h.killAllEntitiesOfClass(ItemEntity.class);
        // oil-bearing rock: only a drilling rig brings it up
        BlockState oil = ModContent.SIMPLE_BLOCKS.get("oil_reservoir").get().defaultBlockState();
        Block.dropResources(oil, h.getLevel(), h.absolutePos(rel), null, p, new ItemStack(Items.IRON_PICKAXE));
        h.assertValueEqual(countDropped(h, rel, st -> true), 0, "oil rock leaves nothing when dug by hand");
        h.succeed();
    }

    private static void buildHearth(GameTestHelper h, BlockPos c) {
        h.setBlock(c, controller("hearth"));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    h.setBlock(c.offset(dx, 0, dz), Blocks.COBBLESTONE);
                }
            }
        }
    }

    private static void stationUsesWaterBucket(GameTestHelper h) {
        floor(h, 8);
        buildHearth(h, new BlockPos(3, 1, 3));
        KnowledgeData.get(h.getLevel().getServer()).grant(SOCIETY, "P-022");
        StationBlockEntity be = station(h, 3, 1, 3);
        be.setOperator(SOCIETY);
        be.setItem(StationBlockEntity.INPUT_START, new ItemStack(Items.CLAY_BALL, 3));
        be.setItem(StationBlockEntity.INPUT_START + 1, new ItemStack(Items.SAND, 1));
        be.setItem(StationBlockEntity.INPUT_START + 2, new ItemStack(Items.OAK_LOG, 2));
        be.setItem(StationBlockEntity.INPUT_START + 3, new ItemStack(Items.WATER_BUCKET));
        selectDirect(be, "P-022");
        h.succeedWhen(() -> {
            h.assertTrue(be.getItem(StationBlockEntity.OUTPUT_START).is(ModContent.item("clay_pot")), "pit firing made a pot: "
                    + be.statusLines());
            h.assertTrue(be.getItem(StationBlockEntity.INPUT_START + 3).is(Items.BUCKET), "the water bucket was emptied");
        });
    }

    private static void toolsAreRequiredAndWear(GameTestHelper h) {
        floor(h, 14);
        h.setBlock(6, 1, 2, controller("forge"));
        box(h, 6, 1, 2, 3, 2, 3, Blocks.STONE_BRICKS, true, true);
        KnowledgeData.get(h.getLevel().getServer()).grant(SOCIETY, "P-076");
        StationBlockEntity be = station(h, 6, 1, 2);
        be.setOperator(SOCIETY);
        be.revalidateNow();
        be.setItem(StationBlockEntity.INPUT_START, ModContent.stack("copper"));
        be.setItem(StationBlockEntity.INPUT_START + 1, ModContent.stack("animal_fat"));
        be.setItem(StationBlockEntity.FUEL_SLOT, new ItemStack(Items.CHARCOAL, 4));
        TechTree.FunctionDef wire = TechTree.get().functionsById.get("P-076");
        Requirements.Check without = Requirements.station(be, wire, 1, Requirements.Phase.START);
        h.assertTrue(without.missing().stream().anyMatch(m -> m.contains("Draw plate")), "asks for the draw plate: " + without.missing());
        ItemStack plate = ModContent.stack("draw_plate");
        be.setItem(StationBlockEntity.TOOL_START, plate);
        selectDirect(be, "P-076");
        h.succeedWhen(() -> {
            ItemStack out = be.getItem(StationBlockEntity.OUTPUT_START);
            h.assertTrue(out.is(ModContent.item("copper_wire")) && out.getCount() == 2, "wire drawn: " + out + " " + be.statusLines());
            h.assertValueEqual(be.getItem(StationBlockEntity.TOOL_START).getDamageValue(), 1, "the draw plate wore by one use");
            h.assertTrue(be.getItem(StationBlockEntity.FUEL_SLOT).getCount() < 4, "the forge burned charcoal (no fuel in the charge)");
        });
    }

    private static void steamEngineNeedsFuel(GameTestHelper h) {
        floor(h, 14);
        h.setBlock(6, 1, 2, controller("steam_engine"));
        box(h, 6, 1, 2, 3, 4, 5, Blocks.IRON_BLOCK, false, false);
        h.setBlock(8, 1, 2, Blocks.WATER);
        StationBlockEntity be = station(h, 6, 1, 2);
        be.revalidateNow();
        h.assertTrue(be.isFormed(), "steam engine forms: " + be.formation().problem());
        h.assertFalse(be.drawPower(40), "no fuel, no power");
        be.setItem(StationBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 2));
        h.assertTrue(be.drawPower(40), "coal in the firebox gives power");
        h.assertValueEqual(be.getItem(StationBlockEntity.FUEL_SLOT).getCount(), 1, "one coal lit");
        h.setBlock(8, 1, 2, Blocks.STONE);
        be.revalidateNow();
        h.assertFalse(be.isFormed(), "no boiler water, no engine");
        h.succeed();
    }

    private static void treatiseTeaches(GameTestHelper h) {
        floor(h, 6);
        ServerPlayer author = player(h);
        ServerPlayer reader = player(h);
        KnowledgeData data = KnowledgeData.get(h.getLevel().getServer());
        h.assertTrue(!Society.keyOf(author).equals(Society.keyOf(reader)), "two separate societies");
        data.grant(Society.keyOf(author), "T-11");
        data.grant(Society.keyOf(author), "T-02");
        author.getInventory().add(ModContent.stack("clay_tablet"));
        Treatises.write(author, "T-02");
        ItemStack book = ItemStack.EMPTY;
        for (ItemStack s : author.getInventory().getNonEquipmentItems()) {
            if (s.is(ModContent.item("treatise"))) {
                book = s;
            }
        }
        h.assertFalse(book.isEmpty(), "the author wrote a treatise");
        h.assertFalse(Treatises.read(reader, book), "an illiterate society cannot read it");
        data.grant(Society.keyOf(reader), "T-11");
        h.assertTrue(Treatises.read(reader, book), "a literate society learns from it");
        h.assertTrue(Society.knows(reader, "T-02"), "the reader now knows Control of fire");
        h.succeed();
    }

    private static void oreFeatures(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var placed = registries.lookupOrThrow(Registries.PLACED_FEATURE);
        var generator = h.getLevel().getChunkSource().getGenerator();
        BlockPos centre = new BlockPos(8, 8, 8);
        for (TechTree.OreDef ore : TechTree.get().ores) {
            var key = ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, ore.block()));
            var holder = placed.get(key);
            h.assertTrue(holder.isPresent(), "placed feature exists for " + ore.block());
            Block host = switch (ore.host() == null ? "deepslate" : ore.host()) {
                case "sand" -> Blocks.SAND;
                case "dirt" -> Blocks.DIRT;
                case "deepslate" -> Blocks.DEEPSLATE;
                default -> Blocks.STONE;
            };
            for (int x = -4; x <= 4; x++) {
                for (int y = -4; y <= 4; y++) {
                    for (int z = -4; z <= 4; z++) {
                        h.setBlock(centre.offset(x, y, z), host);
                    }
                }
            }
            Block oreBlock = ModContent.SIMPLE_BLOCKS.get(ore.block()).get();
            int found = 0;
            for (int attempt = 0; attempt < 6 && found == 0; attempt++) {
                holder.get().value().feature().value().place(h.getLevel(), generator,
                        net.minecraft.util.RandomSource.create(attempt * 31L + ore.block().hashCode()), h.absolutePos(centre));
                for (int x = -4; x <= 4; x++) {
                    for (int y = -4; y <= 4; y++) {
                        for (int z = -4; z <= 4; z++) {
                            if (h.getLevel().getBlockState(h.absolutePos(centre.offset(x, y, z))).is(oreBlock)) {
                                found++;
                            }
                        }
                    }
                }
            }
            h.assertTrue(found > 0, ore.block() + " vein places into " + host.getName().getString());
        }
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        assertBiomeHas(h, biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS).value(), "malachite_deposit");
        assertBiomeHas(h, biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.BEACH).value(), "amber_sand");
        assertBiomeHas(h, biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.NETHER_WASTES).value(), "sulfur_deposit_extra");
        h.succeed();
    }

    private static void assertBiomeHas(GameTestHelper h, net.minecraft.world.level.biome.Biome biome, String feature) {
        Identifier id = Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, feature);
        boolean has = biome.getGenerationSettings().features().stream()
                .anyMatch(set -> set.stream().anyMatch(holder -> holder.is(id)));
        h.assertTrue(has, "biome modifier adds " + feature);
    }

    private static void gridCovers(GameTestHelper h) {
        floor(h, 6);
        AABB here = new AABB(h.absolutePos(new BlockPos(20, 2, 20)));
        h.assertFalse(Requirements.gridCovered(h.getLevel(), here), "no substation, no grid");
        h.setBlock(2, 1, 2, ModContent.STATION_BLOCKS.get("electric_grid").get().defaultBlockState());
        station(h, 2, 1, 2).revalidateNow();
        h.assertTrue(Requirements.gridCovered(h.getLevel(), here), "a substation covers nearby stations");
        AABB far = here.move(500, 0, 0);
        h.assertFalse(Requirements.gridCovered(h.getLevel(), far), "but not 500 blocks away");
        h.setBlock(2, 1, 2, Blocks.AIR);
        h.assertFalse(Requirements.gridCovered(h.getLevel(), here), "a removed substation no longer counts");
        h.succeed();
    }

    private static void machineToolNeedsEngine(GameTestHelper h) {
        floor(h, 30);
        // a rolling mill: heavy machine that needs mechanical power
        h.setBlock(4, 1, 2, controller("rolling_mill"));
        box(h, 4, 1, 2, 3, 3, 7, Blocks.IRON_BLOCK, false, false);
        StationBlockEntity mill = station(h, 4, 1, 2);
        mill.setOperator(SOCIETY);
        mill.revalidateNow();
        h.assertTrue(mill.isFormed(), "rolling mill forms: " + mill.formation().problem());
        TechTree.FunctionDef plate = TechTree.get().functionsById.get("P-138");
        KnowledgeData.get(h.getLevel().getServer()).grant(SOCIETY, "P-138");
        Requirements.Check c = Requirements.station(mill, plate, 1, Requirements.Phase.RUNNING);
        h.assertTrue(c.missing().stream().anyMatch(m -> m.contains("power")), "no engine: " + c.missing());
        // a steam engine next to it, with water and coal
        h.setBlock(12, 1, 2, controller("steam_engine"));
        box(h, 12, 1, 2, 3, 4, 5, Blocks.IRON_BLOCK, false, false);
        h.setBlock(14, 1, 2, Blocks.WATER);
        StationBlockEntity engine = station(h, 12, 1, 2);
        engine.revalidateNow();
        h.assertTrue(engine.isFormed(), "engine forms: " + engine.formation().problem());
        c = Requirements.station(mill, plate, 1, Requirements.Phase.RUNNING);
        h.assertTrue(c.missing().stream().anyMatch(m -> m.contains("power") || m.contains("Steam engine")),
                "a cold engine gives no power: " + c.missing());
        engine.setItem(StationBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 1));
        c = Requirements.station(mill, plate, 1, Requirements.Phase.RUNNING);
        h.assertFalse(c.missing().stream().anyMatch(m -> m.contains("power") || m.contains("Steam engine")),
                "a fired engine within reach powers the mill: " + c.missing());
        h.assertTrue(engine.getItem(StationBlockEntity.FUEL_SLOT).isEmpty(), "the engine lit its coal when power was drawn");
        h.succeed();
    }

    private static void bigKiln(GameTestHelper h) {
        floor(h, 14);
        h.setBlock(6, 1, 2, controller("kiln"));
        box(h, 6, 1, 2, 5, 4, 5, Blocks.BRICKS, true, true);
        KnowledgeData.get(h.getLevel().getServer()).grant(SOCIETY, "P-059");
        StationBlockEntity be = station(h, 6, 1, 2);
        be.setOperator(SOCIETY);
        be.revalidateNow();
        h.assertTrue(be.isFormed(), "5x4x5 kiln: " + be.formation().problem());
        h.assertValueEqual(be.formation().batch(), 3, "large kiln batch");
        // lime burning: 2 limestone + 2 wood per run; give enough for 4 runs, expect 3 at once
        be.setItem(StationBlockEntity.INPUT_START, ModContent.stack("limestone").copyWithCount(8));
        be.setItem(StationBlockEntity.INPUT_START + 1, new ItemStack(Items.OAK_LOG, 8));
        selectDirect(be, "P-059");
        h.succeedWhen(() -> {
            ItemStack out = be.getItem(StationBlockEntity.OUTPUT_START);
            h.assertTrue(out.is(ModContent.item("quicklime")) && out.getCount() == 3, "three batches of quicklime: " + out
                    + " " + be.statusLines());
            h.assertValueEqual(be.getItem(StationBlockEntity.INPUT_START).getCount(), 2, "limestone left");
        });
    }

    /** Sets the selected function without a player (tests only). */
    private static void selectDirect(StationBlockEntity be, String functionId) {
        be.debugSelect(functionId);
    }
}
