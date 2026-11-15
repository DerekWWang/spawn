package dev.qube.spawn_9b5fbb9.test;

import java.util.List;
import java.util.function.Consumer;

import dev.qube.spawn_9b5fbb9.engineering.Assessment;
import dev.qube.spawn_9b5fbb9.engineering.Building;
import dev.qube.spawn_9b5fbb9.engineering.BuildingUse;
import dev.qube.spawn_9b5fbb9.engineering.Engineering;
import dev.qube.spawn_9b5fbb9.engineering.Schema;
import dev.qube.spawn_9b5fbb9.physics.Collapse;
import dev.qube.spawn_9b5fbb9.physics.Hydrostatics;
import dev.qube.spawn_9b5fbb9.physics.StructuralAnalysis;
import dev.qube.spawn_9b5fbb9.physics.StructureScan;
import dev.qube.spawn_9b5fbb9.physics.Weathering;
import dev.qube.spawn_9b5fbb9.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.registries.DeferredRegister;

/** In-world tests of the physics and engineering engines. */
public final class EngineGameTests {
    private static final String SOCIETY = "test:engineers";

    private EngineGameTests() {
    }

    public static void registerFunctions(DeferredRegister<Consumer<GameTestHelper>> reg) {
        add(reg, "cantilever_reach_depends_on_material", EngineGameTests::cantileverReach);
        add(reg, "load_on_a_beam_counts", EngineGameTests::loadOnBeam);
        add(reg, "arches_and_posts_extend_spans", EngineGameTests::archesAndPosts);
        add(reg, "undermined_blocks_fall", EngineGameTests::underminedBlocksFall);
        add(reg, "overstressed_building_sheds_blocks", EngineGameTests::overstressedBuildingSheds);
        add(reg, "storm_strips_thatch_not_stone", EngineGameTests::stormStripsThatch);
        add(reg, "water_pressure_on_barriers", EngineGameTests::waterPressure);
        add(reg, "timber_decays_faster_than_stone", EngineGameTests::decayByMaterial);
        add(reg, "residence_needs_room_bed_and_door", EngineGameTests::residence);
        add(reg, "storage_and_pen_requirements", EngineGameTests::storageAndPen);
        add(reg, "schema_rebuilds_rotated", EngineGameTests::schemaRebuilds);
    }

    private static void add(DeferredRegister<Consumer<GameTestHelper>> reg, String name, Consumer<GameTestHelper> fn) {
        TechTreeGameTests.FUNCTIONS.add(reg.register(name, () -> fn));
    }

    // ------------------------------------------------------------------ helpers

    /** Natural ground (stone counts as terrain, so it anchors structures and is never part of them). */
    private static void ground(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                h.setBlock(x, 0, z, Blocks.STONE);
            }
        }
        h.setBlock(0, 0, 0, Blocks.BEDROCK); // the test plot hangs in the air; this ties it to the "world"
    }

    private static StructuralAnalysis analyse(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1) {
        StructureScan scan = StructureScan.of(h.getLevel(), h.absolutePos(new BlockPos(x0, y0, z0)), h.absolutePos(new BlockPos(x1, y1, z1)));
        h.assertTrue(scan.complete, "scan covers loaded chunks");
        return StructuralAnalysis.of(scan);
    }

    /** A post at (2, 1..3, 2) of stone bricks with a beam of the given block running +x from its top. */
    private static StructuralAnalysis beam(GameTestHelper h, Block material, int length) {
        return beam(h, Blocks.STONE_BRICKS, material, length);
    }

    private static StructuralAnalysis beam(GameTestHelper h, Block post, Block material, int length) {
        for (int x = 1; x <= 14; x++) {
            for (int y = 1; y <= 6; y++) {
                h.setBlock(x, y, 2, Blocks.AIR);
            }
        }
        for (int y = 1; y <= 3; y++) {
            h.setBlock(2, y, 2, post);
        }
        for (int i = 1; i <= length; i++) {
            h.setBlock(2 + i, 3, 2, material);
        }
        return analyse(h, 1, 1, 1, 14, 6, 3);
    }

    private static Building building(GameTestHelper h, BlockPos rel, Direction facing, BuildingUse use) {
        ServerLevel level = h.getLevel();
        h.setBlock(rel, ModContent.CORNERSTONE.get().defaultBlockState().setValue(dev.qube.spawn_9b5fbb9.engineering.CornerstoneBlock.FACING, facing));
        Building b = Engineering.create(level, h.absolutePos(rel), facing, SOCIETY);
        b.use = use;
        return b;
    }

    private static void done(GameTestHelper h, Building... buildings) {
        for (Building b : buildings) {
            Engineering.remove(h.getLevel(), b.cornerstone);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ physics

    private static void cantileverReach(GameTestHelper h) {
        ground(h, 16);
        h.assertTrue(beam(h, Blocks.OAK_PLANKS, 4).sound(), "a timber beam reaches four blocks");
        h.assertFalse(beam(h, Blocks.OAK_PLANKS, 5).sound(), "but not five");
        h.assertTrue(beam(h, Blocks.STONE_BRICKS, 2).sound(), "stone corbels out two blocks");
        h.assertFalse(beam(h, Blocks.STONE_BRICKS, 3).sound(), "but not three");
        h.assertTrue(beam(h, Blocks.IRON_BLOCK, Blocks.IRON_BLOCK, 8).sound(), "iron on an iron post reaches eight");
        h.assertFalse(beam(h, Blocks.IRON_BLOCK, Blocks.IRON_BLOCK, 9).sound(), "but not nine");
        h.assertFalse(beam(h, Blocks.STONE_BRICKS, Blocks.IRON_BLOCK, 8).sound(),
                "the same beam hung off a stone post fails: a joint is only as strong as the weaker material");
        h.assertFalse(beam(h, Blocks.GLASS, 2).sound(), "glass carries almost nothing sideways");
        StructuralAnalysis a = beam(h, Blocks.OAK_PLANKS, 4);
        h.assertValueEqual(a.floating, 0, "floating blocks");
        h.succeed();
    }

    private static void loadOnBeam(GameTestHelper h) {
        ground(h, 16);
        h.assertTrue(beam(h, Blocks.OAK_PLANKS, 3).sound(), "three blocks of timber stand");
        h.setBlock(5, 4, 2, Blocks.STONE_BRICKS); // a stone block on the tip
        h.setBlock(5, 5, 2, Blocks.STONE_BRICKS);
        h.assertFalse(analyse(h, 1, 1, 1, 14, 6, 3).sound(), "the same beam fails with stone stacked on its tip");
        h.succeed();
    }

    private static void archesAndPosts(GameTestHelper h) {
        ground(h, 20);
        // two posts 10 apart joined by a timber beam: each half is carried from its own side
        for (int y = 1; y <= 3; y++) {
            h.setBlock(2, y, 2, Blocks.STONE_BRICKS);
            h.setBlock(11, y, 2, Blocks.STONE_BRICKS);
        }
        for (int x = 3; x <= 10; x++) {
            h.setBlock(x, 3, 2, Blocks.OAK_PLANKS);
        }
        h.assertTrue(analyse(h, 1, 1, 1, 12, 6, 3).sound(), "a timber beam spans eight between two posts");
        h.setBlock(11, 1, 2, Blocks.AIR);
        h.setBlock(11, 2, 2, Blocks.AIR);
        h.setBlock(11, 3, 2, Blocks.AIR);
        h.assertFalse(analyse(h, 1, 1, 1, 12, 6, 3).sound(), "take one post away and it cannot stand");
        // a stone lintel of four fails, the same opening closed with a stepped arch stands
        for (int x = 2; x <= 12; x++) {
            for (int y = 1; y <= 6; y++) {
                h.setBlock(x, y, 2, Blocks.AIR);
                h.setBlock(x, y, 6, Blocks.AIR);
            }
        }
        for (int y = 1; y <= 3; y++) {
            h.setBlock(2, y, 6, Blocks.STONE_BRICKS);
            h.setBlock(9, y, 6, Blocks.STONE_BRICKS);
        }
        for (int x = 3; x <= 8; x++) {
            h.setBlock(x, 3, 6, Blocks.STONE_BRICKS);
        }
        h.assertFalse(analyse(h, 1, 1, 5, 12, 8, 7).sound(), "a flat stone lintel six wide is overstressed");
        for (int x = 3; x <= 8; x++) {
            h.setBlock(x, 3, 6, Blocks.AIR);
        }
        // arch: each block leans on the one diagonally below it
        h.setBlock(3, 4, 6, Blocks.STONE_BRICKS);
        h.setBlock(4, 5, 6, Blocks.STONE_BRICKS);
        h.setBlock(5, 6, 6, Blocks.STONE_BRICKS);
        h.setBlock(6, 6, 6, Blocks.STONE_BRICKS);
        h.setBlock(7, 5, 6, Blocks.STONE_BRICKS);
        h.setBlock(8, 4, 6, Blocks.STONE_BRICKS);
        StructuralAnalysis arch = analyse(h, 1, 1, 5, 12, 8, 7);
        h.assertTrue(arch.sound(), "the same opening spanned by an arch stands (worst " + arch.worst + ")");
        h.succeed();
    }

    private static void underminedBlocksFall(GameTestHelper h) {
        ground(h, 12);
        for (int y = 1; y <= 3; y++) {
            h.setBlock(5, y, 5, Blocks.STONE_BRICKS);
        }
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                h.setBlock(x, 4, z, Blocks.OAK_PLANKS);
            }
        }
        ServerLevel level = h.getLevel();
        Collapse.disturbed(level, h.absolutePos(new BlockPos(9, 1, 9)));
        Collapse.tick(level);
        h.assertBlockPresent(Blocks.OAK_PLANKS, 5, 4, 5); // disturbing somewhere else changes nothing
        h.setBlock(5, 2, 5, Blocks.AIR);
        Collapse.disturbed(level, h.absolutePos(new BlockPos(5, 2, 5)));
        Collapse.tick(level);
        h.assertBlockPresent(Blocks.AIR, 5, 4, 5);
        h.assertBlockPresent(Blocks.AIR, 4, 4, 6);
        h.assertBlockPresent(Blocks.AIR, 5, 3, 5);
        h.assertBlockPresent(Blocks.STONE_BRICKS, 5, 1, 5); // the stump still stands on the ground
        h.succeed();
    }

    private static void overstressedBuildingSheds(GameTestHelper h) {
        ground(h, 16);
        Building b = building(h, new BlockPos(2, 1, 2), Direction.NORTH, BuildingUse.GENERIC);
        h.setBlock(2, 2, 2, Blocks.STONE_BRICKS);
        h.setBlock(2, 3, 2, Blocks.STONE_BRICKS);
        for (int i = 1; i <= 8; i++) {
            h.setBlock(2 + i, 3, 2, Blocks.OAK_PLANKS); // twice what timber can carry
        }
        Engineering.fit(h.getLevel(), b);
        Assessment before = Engineering.assess(h.getLevel(), b);
        h.assertFalse(before.sound, "an eight-block timber cantilever is unsound");
        h.assertTrue(before.problems.stream().anyMatch(p -> p.contains("overstressed")), "and the survey says so: " + before.problems);
        for (int i = 0; i < 6 && b.dirty; i++) {
            Engineering.settle(h.getLevel(), b);
        }
        h.assertBlockPresent(Blocks.AIR, 10, 3, 2);
        h.assertBlockPresent(Blocks.OAK_PLANKS, 3, 3, 2);
        h.assertTrue(Engineering.assess(h.getLevel(), b).analysis.above(Engineering.COLLAPSE_AT).isEmpty(), "what is left can stand");
        done(h, b);
    }

    private static void stormStripsThatch(GameTestHelper h) {
        ground(h, 16);
        Building hut = building(h, new BlockPos(3, 1, 3), Direction.NORTH, BuildingUse.GENERIC);
        // stone walls 5x5, three high, with a wool (thatch) roof
        for (int x = 3; x <= 7; x++) {
            for (int z = 3; z <= 7; z++) {
                boolean wall = x == 3 || x == 7 || z == 3 || z == 7;
                for (int y = 1; y <= 3; y++) {
                    if (wall && !(x == 3 && y == 1 && z == 3)) {
                        h.setBlock(x, y, z, Blocks.STONE_BRICKS);
                    }
                }
                h.setBlock(x, 4, z, Blocks.HAY_BLOCK);
            }
        }
        Engineering.fit(h.getLevel(), hut);
        Assessment a = Engineering.assess(h.getLevel(), hut);
        h.assertTrue(a.sound, "the hut stands in fair weather: " + a.problems);
        int torn = 0;
        for (int i = 0; i < 4; i++) {
            torn += Engineering.storm(h.getLevel(), hut, 1.2F);
            Engineering.assess(h.getLevel(), hut);
        }
        h.assertTrue(torn > 0, "a severe storm strips the thatch");
        int walls = 0;
        for (int x = 3; x <= 7; x++) {
            for (int z = 3; z <= 7; z++) {
                if ((x == 3 || x == 7 || z == 3 || z == 7) && h.getBlockState(new BlockPos(x, 2, z)).is(Blocks.STONE_BRICKS)) {
                    walls++;
                }
            }
        }
        h.assertValueEqual(walls, 16, "stone wall blocks left standing");
        done(h, hut);
    }

    /** A wall across x at the given z with water on its -z side; returns the worst section. */
    private static Hydrostatics.Report wall(GameTestHelper h, Block block, int thickness, int depth, int embed) {
        for (int x = 2; x <= 8; x++) {
            for (int z = 3; z <= 9; z++) {
                for (int y = 1; y <= 9; y++) {
                    h.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        int bed = 1 + embed; // the lowest level with water against the wall
        for (int x = 3; x <= 7; x++) {
            for (int y = 1; y < bed; y++) {
                h.setBlock(x, y, 4, Blocks.STONE); // the sea bed
            }
            for (int t = 0; t < thickness; t++) {
                for (int y = 1; y < bed + depth; y++) {
                    h.setBlock(x, y, 5 + t, block);
                }
            }
            for (int y = bed; y < bed + depth; y++) {
                h.setBlock(x, y, 4, Blocks.WATER);
            }
        }
        StructureScan scan = StructureScan.of(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 4)), h.absolutePos(new BlockPos(7, 9, 9)));
        return Hydrostatics.analyse(scan, 0);
    }

    private static void waterPressure(GameTestHelper h) {
        ground(h, 14);
        Hydrostatics.Report thin = wall(h, Blocks.STONE_BRICKS, 1, 4, 0);
        h.assertFalse(thin.holds(), "one block of stone cannot hold back four of water (" + thin.worst() + ")");
        h.assertValueEqual(thin.maxDepth(), 4, "water depth");
        Hydrostatics.Report thick = wall(h, Blocks.STONE_BRICKS, 2, 4, 0);
        h.assertTrue(thick.holds(), "two blocks of stone hold four of water as a gravity wall (" + thick.worst() + ")");
        Hydrostatics.Report loose = wall(h, Blocks.OAK_PLANKS, 1, 3, 0);
        h.assertFalse(loose.holds(), "timber merely standing on the bed is pushed over (" + loose.worst() + ")");
        Hydrostatics.Report piled = wall(h, Blocks.OAK_PLANKS, 1, 3, 1);
        h.assertTrue(piled.holds(), "timber driven into the bed holds by bending (" + piled.worst() + ")");
        h.assertTrue(piled.sections().get(0).anchored(), "the piles are anchored");
        // and a registered barrier that cannot hold gives way
        wall(h, Blocks.STONE_BRICKS, 1, 4, 0);
        Building b = building(h, new BlockPos(3, 1, 5), Direction.NORTH, BuildingUse.BARRIER);
        b.setExtents(0, 4, 0, 6, 1, 3);
        Assessment a = Engineering.assess(h.getLevel(), b);
        h.assertFalse(a.functional, "the thin seawall is not a working barrier");
        h.assertTrue(a.problems.stream().anyMatch(p -> p.contains("Fails under")), "the survey explains: " + a.problems);
        Engineering.settle(h.getLevel(), b);
        h.assertBlockPresent(Blocks.AIR, 5, 4, 5);
        done(h, b);
    }

    private static void decayByMaterial(GameTestHelper h) {
        ground(h, 16);
        for (int x = 2; x <= 9; x++) {
            for (int z = 2; z <= 9; z++) {
                h.setBlock(x, 1, z, x <= 5 ? Blocks.OAK_PLANKS : Blocks.STONE_BRICKS);
            }
        }
        ServerLevel level = h.getLevel();
        StructureScan timber = StructureScan.of(level, h.absolutePos(new BlockPos(2, 1, 2)), h.absolutePos(new BlockPos(5, 2, 9)));
        StructureScan stone = StructureScan.of(level, h.absolutePos(new BlockPos(6, 1, 2)), h.absolutePos(new BlockPos(9, 2, 9)));
        int rotted = Weathering.decay(timber, timber.outside(), 30, RandomSource.create(7), 1000).size();
        int crumbled = Weathering.decay(stone, stone.outside(), 30, RandomSource.create(7), 1000).size();
        h.assertValueEqual(timber.members, 32, "timber blocks");
        h.assertTrue(rotted >= 8 && rotted <= 26, "about half of weathered timber goes in 30 days: " + rotted);
        h.assertTrue(crumbled <= 3, "stone barely changes in that time: " + crumbled);
        // under water timber goes faster still
        for (int x = 2; x <= 5; x++) {
            for (int z = 2; z <= 9; z++) {
                h.setBlock(x, 2, z, Blocks.WATER);
            }
        }
        StructureScan wet = StructureScan.of(level, h.absolutePos(new BlockPos(2, 1, 2)), h.absolutePos(new BlockPos(5, 2, 9)));
        int drowned = Weathering.decay(wet, wet.outside(), 30, RandomSource.create(7), 1000).size();
        h.assertTrue(drowned > rotted, "submerged timber rots faster (" + drowned + " against " + rotted + ")");
        h.succeed();
    }

    // ------------------------------------------------------------------ engineering

    /** A 5x5 hut, walls three high with a flat roof, door in the -z wall. */
    private static void hut(GameTestHelper h, int x0, int z0, Block wallBlock) {
        for (int x = x0; x <= x0 + 4; x++) {
            for (int z = z0; z <= z0 + 4; z++) {
                boolean wall = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                for (int y = 1; y <= 3; y++) {
                    h.setBlock(x, y, z, wall ? wallBlock : Blocks.AIR);
                }
                h.setBlock(x, 4, z, wallBlock);
            }
        }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState();
        h.setBlock(new BlockPos(x0 + 2, 1, z0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(new BlockPos(x0 + 2, 2, z0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void residence(GameTestHelper h) {
        ground(h, 14);
        hut(h, 3, 3, Blocks.OAK_PLANKS);
        Building b = building(h, new BlockPos(3, 1, 3), Direction.NORTH, BuildingUse.RESIDENCE);
        int found = Engineering.fit(h.getLevel(), b);
        h.assertTrue(found > 50, "fit finds the hut: " + found);
        Assessment a = Engineering.assess(h.getLevel(), b);
        h.assertValueEqual(a.interiorVolume, 27, "enclosed room");
        h.assertFalse(a.functional, "no bed yet");
        h.assertTrue(a.problems.contains("No bed"), "says so: " + a.problems);
        // a straw bed on the floor is bed enough
        BlockState bed = Blocks.STRAW_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        h.setBlock(new BlockPos(5, 1, 5), bed.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
        h.setBlock(new BlockPos(5, 1, 6), bed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
        a = Engineering.assess(h.getLevel(), b);
        h.assertTrue(a.functional, "a room, a door and a bed make a residence: " + a.problems);
        h.assertValueEqual(a.beds, 1, "beds");
        h.assertValueEqual(a.capacity, 1, "residents");
        h.setBlock(5, 4, 5, Blocks.AIR); // a hole in the roof
        a = Engineering.assess(h.getLevel(), b);
        h.assertFalse(a.functional, "open to the sky it is no longer a room");
        // the same house in brick is just as much a residence: the use does not care about the material
        h.setBlock(5, 4, 5, Blocks.OAK_PLANKS);
        for (int x = 3; x <= 7; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 3; z <= 7; z++) {
                    if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.OAK_PLANKS)) {
                        h.setBlock(x, y, z, Blocks.BRICKS);
                    }
                }
            }
        }
        a = Engineering.assess(h.getLevel(), b);
        h.assertTrue(a.functional, "brick works as well as timber: " + a.problems);
        done(h, b);
    }

    private static void storageAndPen(GameTestHelper h) {
        ground(h, 20);
        hut(h, 2, 2, Blocks.COBBLESTONE);
        Building store = building(h, new BlockPos(2, 1, 2), Direction.NORTH, BuildingUse.STORAGE);
        Engineering.fit(h.getLevel(), store);
        Assessment a = Engineering.assess(h.getLevel(), store);
        h.assertFalse(a.functional, "a store needs containers");
        h.setBlock(4, 1, 4, Blocks.CHEST);
        h.setBlock(4, 1, 5, Blocks.BARREL);
        a = Engineering.assess(h.getLevel(), store);
        h.assertTrue(a.functional && a.enclosed, "chests under a roof: " + a.problems);
        h.assertValueEqual(a.capacity, 54, "slots");

        // a fenced pen: 5x5 ring of fences on the ground
        for (int x = 10; x <= 14; x++) {
            for (int z = 10; z <= 14; z++) {
                if (x == 10 || x == 14 || z == 10 || z == 14) {
                    h.setBlock(x, 1, z, Blocks.OAK_FENCE);
                }
            }
        }
        Building pen = building(h, new BlockPos(10, 1, 10), Direction.NORTH, BuildingUse.PEN);
        pen.setExtents(0, 4, 0, 1, 0, 4);
        a = Engineering.assess(h.getLevel(), pen);
        h.assertValueEqual(a.penArea, 9, "fenced ground");
        h.assertTrue(a.functional, "the pen holds animals: " + a.problems);
        h.setBlock(12, 1, 14, Blocks.AIR); // a gap in the fence
        a = Engineering.assess(h.getLevel(), pen);
        h.assertFalse(a.functional, "a gap lets them out");
        done(h, store, pen);
    }

    private static void schemaRebuilds(GameTestHelper h) {
        ground(h, 24);
        // an L-shaped wall with one glass block, so orientation matters
        Building a = building(h, new BlockPos(3, 1, 3), Direction.NORTH, BuildingUse.GENERIC);
        for (int i = 1; i <= 4; i++) {
            h.setBlock(3 + i, 1, 3, Blocks.BRICKS);
        }
        h.setBlock(3, 1, 4, Blocks.BRICKS);
        h.setBlock(3, 1, 5, Blocks.GLASS);
        h.setBlock(3, 2, 3, Blocks.OAK_PLANKS);
        Engineering.fit(h.getLevel(), a);
        h.assertTrue(Engineering.saveSchema(h.getLevel(), a, "bad name!") != null, "bad names are refused");
        String error = Engineering.saveSchema(h.getLevel(), a, "test_wall");
        h.assertTrue(error == null, "schema saved: " + error);
        Schema s = Engineering.schema(h.getLevel(), SOCIETY, "test_wall");
        h.assertValueEqual(s.blocks(), 7, "blocks in the design (the cornerstone is not one of them)");
        h.assertValueEqual(s.materials().get("minecraft:bricks"), 5, "bricks in the bill of materials");

        Building site = building(h, new BlockPos(14, 1, 14), Direction.EAST, BuildingUse.GENERIC);
        h.assertTrue(Engineering.plan(h.getLevel(), site, "nope") != null, "unknown schemas are refused");
        h.assertTrue(Engineering.plan(h.getLevel(), site, "test_wall") == null, "site planned");
        List<Engineering.Missing> missing = Engineering.missing(h.getLevel(), site);
        h.assertValueEqual(missing.size(), 7, "everything is still to build");
        h.assertValueEqual(Engineering.billOfMaterials(missing).get("minecraft:glass"), 1, "glass needed");
        h.assertValueEqual(Engineering.construct(h.getLevel(), site), 7, "blocks placed");
        h.assertTrue(Engineering.missing(h.getLevel(), site).isEmpty(), "the site is complete");
        // facing north the row ran +x (to the builder's right, since right of north is east);
        // facing east the builder's right is south, so the row runs +z and the glass arm runs -x
        h.assertBlockPresent(Blocks.BRICKS, 14, 1, 18);
        h.assertBlockPresent(Blocks.GLASS, 12, 1, 14);
        h.assertBlockPresent(Blocks.OAK_PLANKS, 14, 2, 14);
        h.setBlock(14, 1, 17, Blocks.AIR);
        h.assertValueEqual(Engineering.missing(h.getLevel(), site).size(), 1, "a removed block is missing again");
        Engineering.deleteSchema(h.getLevel(), SOCIETY, "test_wall");
        done(h, a, site);
    }
}
