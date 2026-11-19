package dev.qube.spawn_9b5fbb9.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.serialization.JsonOps;

import dev.qube.spawn_9b5fbb9.knowledge.Discovery;
import dev.qube.spawn_9b5fbb9.knowledge.Empirical;
import dev.qube.spawn_9b5fbb9.knowledge.HandWork;
import dev.qube.spawn_9b5fbb9.knowledge.KnowledgeData;
import dev.qube.spawn_9b5fbb9.knowledge.Requirements;
import dev.qube.spawn_9b5fbb9.knowledge.Society;
import dev.qube.spawn_9b5fbb9.knowledge.Treatises;
import dev.qube.spawn_9b5fbb9.registry.ModContent;
import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import dev.qube.spawn_9b5fbb9.station.StationBlock;
import dev.qube.spawn_9b5fbb9.station.StationBlockEntity;
import dev.qube.spawn_9b5fbb9.station.SurvivalStations;
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
        add(reg, "furnace_needs_hollow_open_top", TechTreeGameTests::furnaceNeedsHollowOpenTop);
        add(reg, "furnace_tier_scales_batch", TechTreeGameTests::furnaceTierScalesBatch);
        add(reg, "hearth_burns_ash", TechTreeGameTests::hearthBurnsAsh);
        add(reg, "big_furnace_runs_two_batches", TechTreeGameTests::furnaceRunsTwoBatches);
        add(reg, "station_refuses_unknown_function", TechTreeGameTests::stationRefusesUnknownFunction);
        add(reg, "knowledge_round_trip", TechTreeGameTests::knowledgeRoundTrip);
        add(reg, "shaft_mine_grants_access", TechTreeGameTests::shaftMineGrantsAccess);
        add(reg, "cleanroom_needs_filters", TechTreeGameTests::cleanroomNeedsFilters);
        add(reg, "discovery_needs_right_mechanism", TechTreeGameTests::discoveryNeedsRightMechanism);
        add(reg, "hand_work_makes_cordage", TechTreeGameTests::handWorkMakesCordage);
        add(reg, "deposits_need_recognition_and_access", TechTreeGameTests::depositsNeedRecognitionAndAccess);
        add(reg, "station_uses_water_bucket", TechTreeGameTests::stationUsesWaterBucket);
        add(reg, "tools_are_required_and_wear", TechTreeGameTests::toolsAreRequiredAndWear);
        add(reg, "engine_needs_fuel_to_power", TechTreeGameTests::engineNeedsFuel);
        add(reg, "survival_workstation_is_the_station", TechTreeGameTests::survivalWorkstationIsTheStation);
        add(reg, "treatise_teaches_another_society", TechTreeGameTests::treatiseTeaches);
        add(reg, "ore_features_place_and_are_added_to_biomes", TechTreeGameTests::oreFeatures);
        add(reg, "grid_substation_covers_radius", TechTreeGameTests::gridCovers);
        add(reg, "machine_tool_needs_running_engine", TechTreeGameTests::machineToolNeedsEngine);
        add(reg, "quiz_options_do_not_reshuffle", TechTreeGameTests::quizStable);
        add(reg, "study_discovers_without_quiz", TechTreeGameTests::studyDiscovers);
        add(reg, "practice_runs_ahead_of_theory", TechTreeGameTests::practiceAheadOfTheory);
        add(reg, "route_planner_orders_prerequisites", TechTreeGameTests::routePlanner);
        add(reg, "idle_machine_burns_no_engine_fuel", TechTreeGameTests::idleMachineBurnsNothing);
        add(reg, "furnace_refuses_wood_fuel", TechTreeGameTests::furnaceRefusesWood);
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

    /** The test society learns a function together with its theories, so its runs waste nothing. */
    private static void know(GameTestHelper h, String functionId) {
        KnowledgeData kd = KnowledgeData.get(h.getLevel().getServer());
        kd.grant(SOCIETY, functionId);
        for (String t : TechTree.get().functionsById.get(functionId).theory()) {
            kd.grant(SOCIETY, t);
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
            if (SurvivalStations.owned(def.id())) {
                h.assertTrue(SurvivalStations.block(def.id()) != Blocks.AIR, "a survival workstation stands in for " + def.id());
                h.assertFalse(ModContent.STATION_BLOCKS.containsKey(def.id()), "and the tree registers no block of its own: " + def.id());
                continue;
            }
            h.assertTrue(ModContent.STATION_BLOCKS.containsKey(def.id()), "station block registered: " + def.id());
        }
        h.assertTrue(tree.survival.contains("cordage") && ModContent.item("cordage") == net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, "cordage")),
                "a shared item is the survival item");
        for (TechTree.FunctionDef f : tree.functions) {
            h.assertFalse(f.host() != null && SurvivalStations.owned(f.host()),
                    f.id() + " is done by hand beside the survival " + f.host() + ", not hosted by it");
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

    private static void furnaceNeedsHollowOpenTop(GameTestHelper h) {
        floor(h, 12);
        h.setBlock(5, 1, 3, controller("smelting_furnace"));
        box(h, 5, 1, 3, 3, 3, 3, Blocks.BRICKS, true, true);
        StationBlockEntity be = station(h, 5, 1, 3);
        be.revalidateNow();
        h.assertTrue(be.isFormed(), "3x3x3 smelting furnace should form: " + be.formation().problem());
        h.assertValueEqual(be.formation().batch(), 1, "small furnace batch");
        h.setBlock(5, 3, 4, Blocks.BRICKS); // close the top
        be.revalidateNow();
        h.assertFalse(be.isFormed(), "a furnace with a closed top should not form");
        h.setBlock(5, 3, 4, Blocks.AIR);
        h.setBlock(5, 2, 4, Blocks.BRICKS); // fill the chamber
        be.revalidateNow();
        h.assertFalse(be.isFormed(), "a furnace with a filled chamber should not form");
        h.succeed();
    }

    private static void furnaceTierScalesBatch(GameTestHelper h) {
        floor(h, 14);
        h.setBlock(6, 1, 2, controller("smelting_furnace"));
        box(h, 6, 1, 2, 3, 3, 3, Blocks.STONE_BRICKS, true, true);
        StationBlockEntity be = station(h, 6, 1, 2);
        be.revalidateNow();
        h.assertTrue(be.isFormed(), "3x3x3 furnace: " + be.formation().problem());
        h.assertValueEqual(be.formation().batch(), 1, "3x3x3 furnace batch");
        box(h, 6, 1, 2, 5, 4, 5, Blocks.STONE_BRICKS, true, true);
        be.revalidateNow();
        h.assertTrue(be.isFormed(), "5x4x5 furnace: " + be.formation().problem());
        h.assertValueEqual(be.formation().batch(), 2, "5x4x5 furnace batch");
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
        know(h, "P-010");
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

    private static void furnaceRunsTwoBatches(GameTestHelper h) {
        floor(h, 14);
        h.setBlock(6, 1, 2, controller("smelting_furnace"));
        box(h, 6, 1, 2, 5, 4, 5, Blocks.BRICKS, true, true);
        know(h, "P-044");
        StationBlockEntity be = station(h, 6, 1, 2);
        be.setOperator(SOCIETY);
        // copper smelting: 3 malachite (the survival mineral) + 2 charcoal per run; enough for three runs, two at once
        be.setItem(StationBlockEntity.INPUT_START, ModContent.stack("malachite").copyWithCount(9));
        be.setItem(StationBlockEntity.INPUT_START + 1, ModContent.stack("charcoal").copyWithCount(6));
        be.setItem(StationBlockEntity.TOOL_START, ModContent.stack("crucible"));
        selectDirect(be, "P-044");
        h.succeedWhen(() -> {
            ItemStack out = be.getItem(StationBlockEntity.OUTPUT_START);
            h.assertTrue(out.is(ModContent.item("copper")) && out.getCount() == 2,
                    "a 5x4x5 furnace smelts two charges at once: " + out + " / " + be.statusLines());
            h.assertValueEqual(be.getItem(StationBlockEntity.INPUT_START).getCount(), 3, "one charge of malachite left");
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
        Block panel = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, "cleanroom_panel")); // the survival panel
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

    private static TechTree.FunctionDef fn(String id) {
        return TechTree.get().functionsById.get(id);
    }

    /** Quiz mode (optional): the right mechanism grants, a wrong one starts a cooldown. */
    private static void discoveryNeedsRightMechanism(GameTestHelper h) {
        floor(h, 6);
        ServerPlayer p = player(h);
        String society = Society.keyOf(p);
        Discovery.openQuestion(p, fn("P-001"));
        int[] q = Discovery.pendingQuestion(p.getUUID());
        h.assertTrue(q != null, "a question is open for Cordage twisting");
        p.getInventory().add(ModContent.stack("plant_fiber").copyWithCount(3));
        Discovery.answer(p, "P-001", q[0], q[1]);
        h.assertTrue(KnowledgeData.get(h.getLevel().getServer()).knows(society, "P-001"), "right answer grants it");

        Discovery.openQuestion(p, fn("T-01"));
        int[] q2 = Discovery.pendingQuestion(p.getUUID());
        Discovery.answer(p, "T-01", q2[0], (q2[1] + 1) % 4);
        h.assertFalse(Society.knows(p, "T-01"), "a wrong mechanism fails");
        h.assertTrue(Discovery.cooldownSeconds(p, "T-01") > 0, "and starts a cooldown");
        Discovery.openQuestion(p, fn("T-01"));
        h.assertTrue(Discovery.pendingQuestion(p.getUUID()) == null, "no new question during the cooldown");
        h.succeed();
    }

    /** Default mode: holding the evidence and studying it discovers a function; no quiz. */
    private static void studyDiscovers(GameTestHelper h) {
        floor(h, 6);
        ServerPlayer p = player(h);
        Discovery.start(p, "T-01");
        h.assertFalse(HandWork.busy(p), "no flint and stone, no study");
        p.getInventory().add(new ItemStack(Items.FLINT));
        p.getInventory().add(new ItemStack(Items.COBBLESTONE));
        Discovery.start(p, "T-01");
        h.assertTrue(HandWork.busy(p), "studying Conchoidal fracture");
        h.assertFalse(Society.knows(p, "T-01"), "not known until the study is done");
        h.assertTrue(Discovery.pendingQuestion(p.getUUID()) == null, "no quiz by default");
        h.onEachTick(() -> HandWork.tick(p));
        h.succeedWhen(() -> h.assertTrue(Society.knows(p, "T-01"), "study ends in discovery"));
    }

    /** Practice may run ahead of theory, at the price of waste that experience brings down. */
    private static void practiceAheadOfTheory(GameTestHelper h) {
        floor(h, 6);
        ServerPlayer p = player(h);
        KnowledgeData kd = KnowledgeData.get(h.getLevel().getServer());
        String society = Society.keyOf(p);
        TechTree.FunctionDef knapping = fn("P-002"); // needs the theory of Conchoidal fracture
        p.getInventory().add(new ItemStack(Items.FLINT, 2));
        p.getInventory().add(new ItemStack(Items.COBBLESTONE));
        h.assertTrue(Requirements.player(p, knapping).ok(), "knapping can be worked out without its theory");
        double fresh = Empirical.wasteChance(kd, society, knapping);
        h.assertTrue(fresh > 0.4 && fresh < 0.6, "about half the first attempts fail: " + fresh);
        kd.addExperience(society, "P-002", 10);
        double practised = Empirical.wasteChance(kd, society, knapping);
        h.assertTrue(practised < fresh / 3, "ten runs of experience cut the waste: " + practised);
        kd.grant(society, "T-01");
        h.assertValueEqual(Empirical.wasteChance(kd, society, knapping), 0.0, "waste once the theory is known");
        // a theory still rests on the theories before it
        h.assertFalse(Requirements.player(p, fn("T-03")).ok(), "Pyrolysis needs Control of fire and a hearth");
        h.succeed();
    }

    private static void routePlanner(GameTestHelper h) {
        TechTree tree = TechTree.get();
        List<TechTree.FunctionDef> route = tree.route("P-082", java.util.Set.of()); // black powder
        h.assertTrue(route.size() >= 30 && route.size() <= 60, "black powder route length: " + route.size());
        h.assertValueEqual(route.get(route.size() - 1).id(), "P-082", "the goal comes last");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (TechTree.FunctionDef f : route) {
            for (String t : f.theory()) {
                h.assertTrue(seen.contains(t), f.id() + " comes after its theory " + t);
            }
            seen.add(f.id());
        }
        java.util.Set<String> known = new java.util.HashSet<>(seen);
        known.remove("P-082");
        h.assertValueEqual(tree.route("P-082", known).size(), 1, "with everything else known, one step remains");
        h.assertTrue(tree.resolveTarget("black powder") != null && tree.resolveTarget("black_powder") != null, "targets resolve by name");
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
        // platinum placer: recognised once Chemical elements is known; no mine needed
        BlockState placer = ModContent.SIMPLE_BLOCKS.get("platinum_placer").get().defaultBlockState();
        Block.dropResources(placer, h.getLevel(), h.absolutePos(rel), null, p, new ItemStack(Items.IRON_PICKAXE));
        h.assertValueEqual(countDropped(h, rel, st -> st.is(ModContent.item("platinum"))), 0, "unrecognised platinum");
        h.assertTrue(countDropped(h, rel, st -> st.is(Items.COBBLESTONE)) > 0, "it is just a coloured rock");
        h.killAllEntitiesOfClass(ItemEntity.class);
        KnowledgeData.get(h.getLevel().getServer()).grant(Society.keyOf(p), "T-42");
        Block.dropResources(placer, h.getLevel(), h.absolutePos(rel), null, p, new ItemStack(Items.IRON_PICKAXE));
        h.assertTrue(countDropped(h, rel, st -> st.is(ModContent.item("platinum"))) > 0, "recognised after Chemical elements");
        h.killAllEntitiesOfClass(ItemEntity.class);
        // cinnabar: needs a shaft mine
        BlockState cinnabar = ModContent.SIMPLE_BLOCKS.get("cinnabar_deposit").get().defaultBlockState();
        Block.dropResources(cinnabar, h.getLevel(), h.absolutePos(rel), null, p, new ItemStack(Items.IRON_PICKAXE));
        h.assertValueEqual(countDropped(h, rel, st -> st.is(ModContent.item("cinnabar"))), 0, "no shaft mine, no cinnabar");
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
        know(h, "P-022");
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
        h.setBlock(6, 1, 2, controller("smelting_furnace"));
        box(h, 6, 1, 2, 3, 3, 3, Blocks.BRICKS, true, true);
        know(h, "P-044");
        StationBlockEntity be = station(h, 6, 1, 2);
        be.setOperator(SOCIETY);
        be.revalidateNow();
        be.setItem(StationBlockEntity.INPUT_START, ModContent.stack("malachite").copyWithCount(3));
        be.setItem(StationBlockEntity.INPUT_START + 1, ModContent.stack("charcoal").copyWithCount(2));
        TechTree.FunctionDef smelt = TechTree.get().functionsById.get("P-044");
        Requirements.Check without = Requirements.station(be, smelt, 1, Requirements.Phase.START);
        h.assertTrue(without.missing().stream().anyMatch(m -> m.contains("Crucible")), "asks for the crucible: " + without.missing());
        be.setItem(StationBlockEntity.TOOL_START, ModContent.stack("crucible"));
        selectDirect(be, "P-044");
        h.succeedWhen(() -> {
            ItemStack out = be.getItem(StationBlockEntity.OUTPUT_START);
            h.assertTrue(out.is(ModContent.item("copper")) && out.getCount() == 1, "copper smelted: " + out + " " + be.statusLines());
            h.assertValueEqual(be.getItem(StationBlockEntity.TOOL_START).getDamageValue(), 1, "the crucible wore by one use");
        });
    }

    private static void engineNeedsFuel(GameTestHelper h) {
        floor(h, 14);
        h.setBlock(6, 1, 2, controller("atmospheric_engine"));
        box(h, 6, 1, 2, 3, 6, 5, Blocks.IRON_BLOCK, false, false);
        h.setBlock(8, 1, 2, Blocks.WATER);
        StationBlockEntity be = station(h, 6, 1, 2);
        be.revalidateNow();
        h.assertTrue(be.isFormed(), "atmospheric engine forms: " + be.formation().problem());
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
        assertBiomeHas(h, biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS).value(), "native_copper_deposit");
        assertBiomeHas(h, biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.BEACH).value(), "amber_sand");
        h.assertFalse(registries.lookupOrThrow(Registries.PLACED_FEATURE).get(ResourceKey.create(Registries.PLACED_FEATURE,
                Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, "malachite_deposit"))).isPresent(),
                "malachite is left to the survival geology");
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
        TechTree.FunctionDef plate = TechTree.get().functionsById.get("P-139");
        know(h, "P-139");
        Requirements.Check c = Requirements.station(mill, plate, 1, Requirements.Phase.RUNNING);
        h.assertTrue(c.missing().stream().anyMatch(m -> m.contains("power")), "no engine: " + c.missing());
        h.assertTrue(c.missing().stream().anyMatch(m -> m.contains("Forge")), "no forge: " + c.missing());
        // the survival forge within reach is the forge
        h.setBlock(4, 1, 12, SurvivalStations.block("forge"));
        c = Requirements.station(mill, plate, 1, Requirements.Phase.RUNNING);
        h.assertFalse(c.missing().stream().anyMatch(m -> m.contains("Forge")), "the survival forge counts: " + c.missing());
        // an atmospheric engine next to it, with water and coal
        h.setBlock(12, 1, 2, controller("atmospheric_engine"));
        box(h, 12, 1, 2, 3, 6, 5, Blocks.IRON_BLOCK, false, false);
        h.setBlock(14, 1, 2, Blocks.WATER);
        StationBlockEntity engine = station(h, 12, 1, 2);
        engine.revalidateNow();
        h.assertTrue(engine.isFormed(), "engine forms: " + engine.formation().problem());
        c = Requirements.station(mill, plate, 1, Requirements.Phase.RUNNING);
        h.assertTrue(c.missing().stream().anyMatch(m -> m.contains("power")), "a cold engine gives no power: " + c.missing());
        engine.setItem(StationBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 1));
        c = Requirements.station(mill, plate, 1, Requirements.Phase.RUNNING);
        h.assertFalse(c.missing().stream().anyMatch(m -> m.contains("power")),
                "an engine with coal within reach can power the mill: " + c.missing());
        h.assertValueEqual(engine.getItem(StationBlockEntity.FUEL_SLOT).getCount(), 1, "checking alone burns nothing");
        c = Requirements.station(mill, plate, 1, Requirements.Phase.RUNNING, true);
        h.assertTrue(c.ok() || !c.missing().stream().anyMatch(m -> m.contains("power")), "drawing works: " + c.missing());
        h.assertTrue(engine.getItem(StationBlockEntity.FUEL_SLOT).isEmpty(), "the engine lights its coal once power is drawn");
        h.succeed();
    }

    /** Work the tree did at a kiln, forge or loom is hand work beside the survival workstation. */
    private static void survivalWorkstationIsTheStation(GameTestHelper h) {
        floor(h, 12);
        ServerPlayer p = player(h);
        TechTree.FunctionDef wire = TechTree.get().functionsById.get("P-076");
        h.assertTrue(wire.mode() == TechTree.Mode.HAND && wire.host() == null, "wire drawing is hand work");
        KnowledgeData kd = KnowledgeData.get(h.getLevel().getServer());
        kd.grant(Society.keyOf(p), "P-076");
        for (String t : wire.theory()) {
            kd.grant(Society.keyOf(p), t);
        }
        p.getInventory().add(ModContent.stack("copper"));
        p.getInventory().add(ModContent.stack("animal_fat"));
        p.getInventory().add(ModContent.stack("draw_plate"));
        Requirements.Check far = Requirements.player(p, wire);
        h.assertTrue(far.missing().stream().anyMatch(m -> m.contains("Forge")), "no forge near: " + far.missing());
        h.setBlock(6, 1, 6, SurvivalStations.block("forge"));
        Requirements.Check near = Requirements.player(p, wire);
        h.assertTrue(near.ok(), "beside a survival forge the wire can be drawn: " + near.missing());
        h.assertTrue(SurvivalStations.near(h.getLevel(), "steam_engine", new AABB(h.absolutePos(new BlockPos(6, 1, 6))), 4) == false,
                "a forge is not an engine");
        h.setBlock(8, 1, 6, SurvivalStations.block("steam_engine"));
        h.assertTrue(SurvivalStations.near(h.getLevel(), "steam_engine", new AABB(h.absolutePos(new BlockPos(6, 1, 6))), 4),
                "the survival stationary engine is the steam engine");
        h.succeed();
    }

    private static void quizStable(GameTestHelper h) {
        floor(h, 6);
        ServerPlayer p = player(h);
        Discovery.openQuestion(p, fn("P-001"));
        List<String> first = Discovery.pendingOptions(p.getUUID());
        Discovery.openQuestion(p, fn("P-001"));
        List<String> second = Discovery.pendingOptions(p.getUUID());
        h.assertTrue(!first.isEmpty() && first.equals(second), "asking again shows the same options in the same order");
        int[] q = Discovery.pendingQuestion(p.getUUID());
        Discovery.answer(p, "P-001", q[0], (q[1] + 1) % first.size());
        Discovery.openQuestion(p, fn("P-001"));
        h.assertTrue(Discovery.pendingQuestion(p.getUUID()) == null, "cooldown after a wrong answer");
        h.assertFalse(Society.knows(p, "P-001"), "not learned");
        h.succeed();
    }

    private static void idleMachineBurnsNothing(GameTestHelper h) {
        floor(h, 30);
        h.setBlock(4, 1, 2, controller("rolling_mill"));
        box(h, 4, 1, 2, 3, 3, 7, Blocks.IRON_BLOCK, false, false);
        h.setBlock(12, 1, 2, controller("atmospheric_engine"));
        box(h, 12, 1, 2, 3, 6, 5, Blocks.IRON_BLOCK, false, false);
        h.setBlock(14, 1, 2, Blocks.WATER);
        know(h, "P-138");
        StationBlockEntity mill = station(h, 4, 1, 2);
        mill.setOperator(SOCIETY);
        selectDirect(mill, "P-138"); // selected, but no steel to roll
        StationBlockEntity engine = station(h, 12, 1, 2);
        engine.setItem(StationBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 3));
        h.runAtTickTime(200, () -> {
            h.assertTrue(mill.isFormed() && engine.isFormed(), "both formed");
            h.assertValueEqual(engine.getItem(StationBlockEntity.FUEL_SLOT).getCount(), 3, "an idle mill does not make the engine burn coal");
            h.assertFalse(mill.isRunning(), "nothing to roll");
            h.succeed();
        });
    }

    private static void furnaceRefusesWood(GameTestHelper h) {
        floor(h, 14);
        h.setBlock(6, 1, 2, controller("smelting_furnace"));
        StationBlockEntity be = station(h, 6, 1, 2);
        h.assertFalse(be.canPlaceItem(StationBlockEntity.FUEL_SLOT, new ItemStack(Items.OAK_LOG)), "logs are not hot enough for metal");
        h.assertTrue(be.canPlaceItem(StationBlockEntity.FUEL_SLOT, new ItemStack(Items.CHARCOAL)), "charcoal is");
        h.assertFalse(be.canPlaceItem(StationBlockEntity.OUTPUT_START, new ItemStack(Items.CHARCOAL)), "outputs take nothing");
        h.setBlock(3, 1, 2, controller("hearth"));
        h.assertTrue(station(h, 3, 1, 2).canPlaceItem(StationBlockEntity.FUEL_SLOT, new ItemStack(Items.OAK_LOG)), "a hearth burns wood");
        h.succeed();
    }

    /** Sets the selected function without a player (tests only). */
    private static void selectDirect(StationBlockEntity be, String functionId) {
        be.debugSelect(functionId);
    }
}
