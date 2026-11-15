package dev.qube.spawn_9b5fbb9.test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.mojang.serialization.JsonOps;

import dev.qube.spawn_9b5fbb9.engineering.Assessment;
import dev.qube.spawn_9b5fbb9.engineering.Building;
import dev.qube.spawn_9b5fbb9.engineering.BuildingUse;
import dev.qube.spawn_9b5fbb9.engineering.Engineering;
import dev.qube.spawn_9b5fbb9.knowledge.KnowledgeData;
import dev.qube.spawn_9b5fbb9.logistics.Gestation;
import dev.qube.spawn_9b5fbb9.logistics.Ledger;
import dev.qube.spawn_9b5fbb9.logistics.LedgerData;
import dev.qube.spawn_9b5fbb9.logistics.RecordKeeping;
import dev.qube.spawn_9b5fbb9.logistics.Spoilage;
import dev.qube.spawn_9b5fbb9.registry.ModContent;
import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredRegister;

/** In-world tests of the logistics engine. Each test keeps its own society's books. */
public final class LogisticsGameTests {
    private static final Item[] GOODS = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.FLINT, Items.BRICK, Items.COAL, Items.STICK, Items.BONE,
            Items.STRING, Items.FEATHER, Items.LEATHER, Items.PAPER, Items.CLAY_BALL, Items.DIAMOND, Items.EMERALD, Items.REDSTONE,
            Items.COPPER_INGOT, Items.QUARTZ, Items.GUNPOWDER, Items.SUGAR, Items.BOOK};

    private LogisticsGameTests() {
    }

    static void registerFunctions(DeferredRegister<Consumer<GameTestHelper>> reg) {
        EngineGameTests.add(reg, "books_drift_until_a_stocktake", LogisticsGameTests::booksDrift);
        EngineGameTests.add(reg, "spoilage_is_real_but_unrecorded", LogisticsGameTests::spoilageUnrecorded);
        EngineGameTests.add(reg, "good_storage_slows_spoilage", LogisticsGameTests::storageQuality);
        EngineGameTests.add(reg, "stocktake_on_paper_is_more_exact", LogisticsGameTests::stocktakeOnPaper);
        EngineGameTests.add(reg, "census_counts_people_and_herds", LogisticsGameTests::census);
        EngineGameTests.add(reg, "animals_carry_young_before_birth", LogisticsGameTests::gestation);
        EngineGameTests.add(reg, "record_keeping_improves_with_knowledge", LogisticsGameTests::recordKeeping);
        EngineGameTests.add(reg, "flows_are_recorded_with_error", LogisticsGameTests::flows);
        EngineGameTests.add(reg, "ledger_survives_saving", LogisticsGameTests::persistence);
    }

    // ------------------------------------------------------------------ helpers

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** A society with nothing on its books and nothing learned (tests may be run again in the same world). */
    private static String fresh(GameTestHelper h, String society) {
        MinecraftServer server = h.getLevel().getServer();
        LedgerData.get(server).all().remove(society);
        KnowledgeData.get(server).revokeAll(society);
        return society;
    }

    /** A society whose records are as good as they get, so that what the books say can be asserted. */
    private static String exact(GameTestHelper h, String society) {
        KnowledgeData.get(h.getLevel().getServer()).grant(fresh(h, society), "P-266");
        return society;
    }

    /** A stone storehouse on the plot with one chest in it. */
    private static Building store(GameTestHelper h, String society) {
        EngineGameTests.ground(h, 12);
        EngineGameTests.hut(h, 2, 2, Blocks.COBBLESTONE);
        Building store = EngineGameTests.building(h, new BlockPos(2, 1, 2), Direction.NORTH, BuildingUse.STORAGE, society);
        Engineering.fit(h.getLevel(), store);
        h.setBlock(4, 1, 4, Blocks.CHEST);
        Assessment a = Engineering.assess(h.getLevel(), store);
        h.assertTrue(a.functional && a.enclosed && a.sound, "a sound, enclosed store: " + a.problems);
        return store;
    }

    private static ChestBlockEntity chest(GameTestHelper h) {
        return h.getBlockEntity(new BlockPos(4, 1, 4), ChestBlockEntity.class);
    }

    private static void near(GameTestHelper h, double value, double expected, double tolerance, String what) {
        h.assertTrue(Math.abs(value - expected) <= tolerance, what + ": expected about " + expected + ", got " + value);
    }

    // ------------------------------------------------------------------ tests

    private static void booksDrift(GameTestHelper h) {
        String society = fresh(h, "test:ledger_drift");
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        Building store = store(h, society);
        ChestBlockEntity chest = chest(h);
        for (int i = 0; i < GOODS.length; i++) {
            chest.setItem(i, new ItemStack(GOODS[i], 50));
        }
        Ledger.scanStore(level, store);
        LedgerData.Books books = Ledger.books(server, society);
        String prefix = Ledger.STOCK + store.id + "/";
        int wrong = 0;
        for (Item good : GOODS) {
            h.assertValueEqual(books.actual(prefix + id(good)), 50.0, "ground truth of " + id(good));
            h.assertTrue(books.observed(prefix + id(good)) > 0, "something is on the books for " + id(good));
            if (Math.abs(books.observed(prefix + id(good)) - 50) > 1) {
                wrong++;
            }
        }
        h.assertTrue(wrong > 0, "books kept from memory are wrong somewhere");
        h.assertTrue(books.bias.containsKey("stock"), "the clerks lean one way");

        // goods leave behind the clerk's back: the truth follows at once
        for (int i = 0; i < GOODS.length; i++) {
            chest.removeItem(i, i == 0 ? 50 : 25);
        }
        Ledger.scanStore(level, store);
        h.assertValueEqual(books.actual(prefix + id(GOODS[0])), 0.0, "all of the first good is gone");
        h.assertValueEqual(books.actual(prefix + id(GOODS[1])), 25.0, "half of the rest is left");

        // the manager only ever sees the books
        List<String> report = Ledger.report(server, society);
        h.assertTrue(report.get(0).contains("memory"), "the report says how the records are kept: " + report.get(0));
        h.assertTrue(report.stream().anyMatch(l -> l.startsWith(store.name) && l.contains("never counted")), "store line: " + report);

        // with good records, a count puts the books right
        KnowledgeData.get(server).grant(society, "P-266");
        String said = Ledger.stocktake(level, store, null);
        h.assertTrue(said.startsWith("Counted 19 kind(s)"), said);
        h.assertFalse(books.observed.containsKey(prefix + id(GOODS[0])), "what is gone is struck from the books");
        for (int i = 1; i < GOODS.length; i++) {
            h.assertValueEqual(books.observed(prefix + id(GOODS[i])), 25.0, "counted " + id(GOODS[i]));
        }
        h.assertTrue(Ledger.report(server, society).stream().anyMatch(l -> l.startsWith(store.name) && l.contains("counted today")),
                "the report says when the store was counted");
        near(h, Ledger.totals(books, true).get("stock/" + id(GOODS[1])), 25, 0.01, "total in store");
        List<String> truth = Ledger.truth(server, society);
        h.assertTrue(truth.contains("stock/" + id(GOODS[1]) + ",25,25,0%"), "operators can compare both truths: " + truth);

        // a building that is no longer a store keeps no books; one that is gone is forgotten
        h.assertTrue(Ledger.stocktake(level, EngineGameTests.building(h, new BlockPos(9, 1, 9), Direction.NORTH, BuildingUse.GENERIC, society), null)
                .endsWith("is not a store."), "only stores are counted");
        EngineGameTests.done(h, store, Engineering.at(level, h.absolutePos(new BlockPos(9, 1, 9))));
    }

    private static void spoilageUnrecorded(GameTestHelper h) {
        String society = exact(h, "test:ledger_spoilage");
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        Building store = store(h, society);
        ChestBlockEntity chest = chest(h);
        chest.setItem(0, new ItemStack(Items.BEEF, 64));
        chest.setItem(1, new ItemStack(Items.IRON_INGOT, 64));
        Ledger.scanStore(level, store);
        LedgerData.Books books = Ledger.books(server, society);
        String beef = Ledger.STOCK + store.id + "/minecraft:beef";
        h.assertValueEqual(books.actual(beef), 64.0, "meat in store");
        near(h, books.observed(beef), 64, 1, "meat on the books");

        // four days in a sound, enclosed store age meat by two: one half-life
        Spoilage.backdate(level, chest, 96000);
        Ledger.scanStore(level, store);
        int left = chest.getItem(0).getCount();
        h.assertTrue(left >= 12 && left <= 52, "about half the meat has gone off: " + left + " left");
        h.assertValueEqual(chest.getItem(1).getCount(), 64, "iron keeps");
        h.assertValueEqual(books.actual(beef), (double) left, "the truth knows");
        near(h, books.observed(beef), 64, 1, "the books do not");
        h.assertValueEqual(books.actual(Ledger.FLOW + "spoiled/minecraft:beef"), (double) (64 - left), "true losses");
        h.assertValueEqual(books.observed(Ledger.FLOW + "spoiled/minecraft:beef"), 0.0, "nobody recorded them");

        // only a count finds the shortfall
        Ledger.stocktake(level, store, null);
        h.assertValueEqual(books.observed(beef), (double) left, "the count finds what is left");
        EngineGameTests.done(h, store);
    }

    private static void storageQuality(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.assertValueEqual(Spoilage.halfLife(new ItemStack(Items.BEEF)), 2.0, "fresh meat");
        h.assertValueEqual(Spoilage.halfLife(new ItemStack(Items.BREAD)), 6.0, "bread");
        h.assertValueEqual(Spoilage.halfLife(new ItemStack(Items.WHEAT)), 30.0, "grain");
        h.assertValueEqual(Spoilage.halfLife(ModContent.stack("carcass")), 2.0, "a carcass");
        h.assertValueEqual(Spoilage.halfLife(ModContent.stack("insulin")), 6.0, "insulin needs a cold chain");
        h.assertValueEqual(Spoilage.halfLife(ModContent.stack("canned_food")), 0.0, "canned food keeps");
        h.assertValueEqual(Spoilage.halfLife(ModContent.stack("preserved_food")), 0.0, "salted food keeps");
        h.assertValueEqual(Spoilage.halfLife(new ItemStack(Items.IRON_INGOT)), 0.0, "iron keeps");

        RandomSource random = RandomSource.create(42);
        int open = Spoilage.lost(random, 10000, 2, 2);
        h.assertTrue(open > 4700 && open < 5300, "half is lost in one half-life: " + open);
        int cold = Spoilage.lost(random, 10000, 2 * 0.1, 2);
        h.assertTrue(cold > 500 && cold < 850, "a cold store loses a tenth as fast: " + cold);
        h.assertValueEqual(Spoilage.lost(random, 100, 5, 0), 0, "things that keep");
        h.assertValueEqual(Spoilage.lost(random, 100, 0, 2), 0, "no time, no loss");

        // a sound roofed store halves the rate; a chest left in the open gets none of that
        Building store = store(h, fresh(h, "test:ledger_quality"));
        h.assertValueEqual(Spoilage.quality(level, store, Engineering.assess(level, store)), 0.5, "enclosed store");
        h.setBlock(9, 1, 9, Blocks.CHEST);
        Building heap = EngineGameTests.building(h, new BlockPos(8, 1, 9), Direction.NORTH, BuildingUse.STORAGE, "test:ledger_quality");
        heap.setExtents(1, 1, 0, 1, 1, 1);
        Assessment a = Engineering.assess(level, heap);
        h.assertFalse(a.enclosed, "a chest under the sky");
        h.assertValueEqual(Spoilage.quality(level, heap, a), 1.0, "open storage");

        // food a player carries spoils too, and they hear of it only by looking
        ChestBlockEntity chest = chest(h);
        chest.setItem(0, new ItemStack(Items.BEEF, 64));
        chest.setItem(1, ModContent.stack("canned_food").copyWithCount(16));
        Spoilage.age(random, chest, 20, null);
        h.assertTrue(chest.getItem(0).isEmpty(), "meat left for twenty days is gone");
        h.assertValueEqual(chest.getItem(1).getCount(), 16, "the cans are fine");
        EngineGameTests.done(h, store, heap);
    }

    @SuppressWarnings("removal") // vanilla offers no replacement for a mock player that is in the level
    private static void stocktakeOnPaper(GameTestHelper h) {
        String society = fresh(h, "test:ledger_paper");
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        KnowledgeData.get(server).grant(society, "T-11"); // writing
        Building store = store(h, society);
        ChestBlockEntity chest = chest(h);
        for (int i = 0; i < GOODS.length; i++) {
            chest.setItem(i, new ItemStack(GOODS[i], 64));
        }
        ServerPlayer clerk = h.makeMockServerPlayerInLevel();
        clerk.getInventory().clearContent();
        Vec3 far = h.absoluteVec(new Vec3(4.5, 1, 4.5)).add(0, 0, 40);
        clerk.snapTo(far.x, far.y, far.z, 0, 0);
        h.assertTrue(Ledger.stocktake(level, store, clerk).startsWith("You have to be at"), "nobody counts a store from afar");

        Vec3 inside = h.absoluteVec(new Vec3(4.5, 1, 5.5));
        clerk.snapTo(inside.x, inside.y, inside.z, 0, 0);
        h.assertTrue(Ledger.stocktake(level, store, clerk).contains("from memory"), "with nothing to write on, the count is kept in the head");
        clerk.getInventory().add(ModContent.stack("clay_tablet").copyWithCount(2));
        h.assertTrue(Ledger.stocktake(level, store, clerk).contains("clay tablet"), "a tablet is used if there is one");
        h.assertValueEqual(clerk.getInventory().countItem(ModContent.item("clay_tablet")), 1, "tablets left");
        clerk.getInventory().add(ModContent.stack("paper").copyWithCount(3));
        clerk.getInventory().add(ModContent.stack("ink").copyWithCount(3));
        String said = Ledger.stocktake(level, store, clerk);
        h.assertTrue(said.contains("on paper") && said.contains("2.5%"), "paper and ink beat the tablet: " + said);
        h.assertValueEqual(clerk.getInventory().countItem(ModContent.item("paper")), 2, "paper left");
        h.assertValueEqual(clerk.getInventory().countItem(ModContent.item("ink")), 2, "ink left");
        h.assertValueEqual(clerk.getInventory().countItem(ModContent.item("clay_tablet")), 1, "the tablet is kept for next time");

        // written on paper by a society with writing: within 2.5% or so, so never far off
        LedgerData.Books books = Ledger.books(server, society);
        for (Item good : GOODS) {
            near(h, books.observed(Ledger.STOCK + store.id + "/" + id(good)), 64, 64 * 0.15, "paper count of " + id(good));
        }
        clerk.discard();
        EngineGameTests.done(h, store);
    }

    private static void census(GameTestHelper h) {
        String society = exact(h, "test:ledger_census");
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        EngineGameTests.ground(h, 24);
        EngineGameTests.hut(h, 2, 2, Blocks.OAK_PLANKS);
        BlockState bed = Blocks.STRAW_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        h.setBlock(new BlockPos(4, 1, 4), bed.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
        h.setBlock(new BlockPos(4, 1, 5), bed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
        Building home = EngineGameTests.building(h, new BlockPos(2, 1, 2), Direction.NORTH, BuildingUse.RESIDENCE, society);
        Engineering.fit(level, home);
        h.assertTrue(Engineering.assess(level, home).functional, "a home");
        h.spawnWithNoFreeWill(EntityTypes.VILLAGER, 3, 1, 3);
        Villager hurt = h.spawnWithNoFreeWill(EntityTypes.VILLAGER, 5, 1, 3);
        hurt.setHealth(6);

        for (int x = 12; x <= 16; x++) {
            for (int z = 12; z <= 16; z++) {
                if (x == 12 || x == 16 || z == 12 || z == 16) {
                    h.setBlock(x, 1, z, Blocks.OAK_FENCE);
                }
            }
        }
        Building pen = EngineGameTests.building(h, new BlockPos(12, 1, 12), Direction.NORTH, BuildingUse.PEN, society);
        pen.setExtents(0, 4, 0, 1, 0, 4);
        h.assertTrue(Engineering.assess(level, pen).functional, "a pen");
        Cow mother = h.spawnWithNoFreeWill(EntityTypes.COW, 13, 1, 13);
        h.spawnWithNoFreeWill(EntityTypes.COW, 15, 1, 13);
        h.spawnWithNoFreeWill(EntityTypes.COW, 13, 1, 15);
        h.spawnWithNoFreeWill(EntityTypes.COW, 15, 1, 15).setBaby(true);
        Gestation.begin(level, mother, null, null, level.getGameTime() + 240000);

        Ledger.census(level, society);
        LedgerData.Books books = Ledger.books(server, society);
        h.assertValueEqual(books.actual("herd/" + pen.id + "/cow/count"), 4.0, "cows");
        h.assertValueEqual(books.actual("herd/" + pen.id + "/cow/pregnant"), 1.0, "in calf");
        h.assertValueEqual(books.actual("herd/" + pen.id + "/cow/young"), 1.0, "calves");
        h.assertValueEqual(books.actual("resident/" + home.id + "/alive"), 2.0, "residents");
        h.assertValueEqual(books.actual("resident/" + home.id + "/wounded"), 1.0, "wounded");
        h.assertValueEqual(books.actual("housing/" + home.id), 1.0, "beds");
        h.assertValueEqual(books.actual(Ledger.FLOW + "herded/animals"), 4.0, "animals brought in");
        Map<String, Double> real = Ledger.totals(books, false);
        h.assertValueEqual(real.get("people/alive"), 2.0, "people in all");
        h.assertValueEqual(real.get("people/wounded"), 1.0, "wounded in all");
        h.assertValueEqual(real.get("people/housing"), 1.0, "beds in all");
        h.assertValueEqual(real.get("herd/cow"), 4.0, "cattle in all");
        h.assertValueEqual(real.get("herd/cow/pregnant"), 1.0, "in calf in all");
        Map<String, Double> seen = Ledger.totals(books, true);
        near(h, seen.get("herd/cow"), 4, 0.1, "cattle as recorded by a society with good records");
        near(h, seen.get("people/alive"), 2, 0.1, "people as recorded");
        h.assertValueEqual(books.history.size(), 1, "a day's snapshot is kept");
        List<String> report = Ledger.report(server, society);
        h.assertTrue(report.stream().anyMatch(l -> l.startsWith("People: 2 alive, 0 pregnant, 1 wounded; beds for 1")), "people line: " + report);
        h.assertTrue(report.stream().anyMatch(l -> l.contains("cow 4 (1 pregnant, 1 young)")), "livestock line: " + report);

        // counting the same herd again does not herd it in again
        Ledger.census(level, society);
        h.assertValueEqual(books.actual(Ledger.FLOW + "herded/animals"), 4.0, "no new arrivals");
        h.assertValueEqual(books.history.size(), 1, "one snapshot a day");
        // an animal that dies is gone from the next count
        mother.discard();
        Ledger.census(level, society);
        h.assertValueEqual(books.actual("herd/" + pen.id + "/cow/count"), 3.0, "one fewer");
        h.assertFalse(books.actual.containsKey("herd/" + pen.id + "/cow/pregnant"), "and none in calf");
        EngineGameTests.done(h, home, pen);
    }

    private static void gestation(GameTestHelper h) {
        String society = fresh(h, "test:ledger_gestation");
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        EngineGameTests.ground(h, 12);
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                if (x == 2 || x == 6 || z == 2 || z == 6) {
                    h.setBlock(x, 1, z, Blocks.OAK_FENCE);
                }
            }
        }
        Building pen = EngineGameTests.building(h, new BlockPos(2, 1, 2), Direction.NORTH, BuildingUse.PEN, society);
        pen.setExtents(0, 4, 0, 1, 0, 4);
        Cow a = h.spawnWithNoFreeWill(EntityTypes.COW, 3, 1, 3);
        Cow b = h.spawnWithNoFreeWill(EntityTypes.COW, 5, 1, 5);
        h.assertTrue(Gestation.enabled(), "gestation is on by default");

        // the vanilla path: two animals in love mate
        a.setInLove(null);
        b.setInLove(null);
        a.spawnChildFromBreeding(level, b);
        h.assertTrue(Gestation.pregnant(a), "mating starts a pregnancy");
        h.assertFalse(Gestation.pregnant(b), "in one of them");
        h.assertFalse(a.isInLove() || b.isInLove(), "and ends the courtship");
        h.assertValueEqual(level.getEntitiesOfClass(Cow.class, pen.bounds()).size(), 2, "no calf yet");
        // she cannot be bred again while carrying: a second mating makes the other one pregnant, a third nothing
        h.assertTrue(Gestation.conceive(level, a, b, null), "a second mating");
        h.assertTrue(Gestation.pregnant(b), "the other cow is now carrying too");
        long dueBefore = Gestation.due(a);
        h.assertTrue(Gestation.conceive(level, a, b, null), "a third mating is absorbed");
        h.assertValueEqual(Gestation.due(a), dueBefore, "and changes nothing");

        Ledger.census(level, society);
        LedgerData.Books books = Ledger.books(server, society);
        h.assertValueEqual(books.actual("herd/" + pen.id + "/cow/pregnant"), 2.0, "the census sees them");
        h.assertValueEqual(books.actual(Ledger.FLOW + "herded/animals"), 2.0, "two cows were brought in");

        // bring one birth forward; the other cow stays in calf
        Gestation.begin(level, a, b.getUUID(), null, level.getGameTime() + 30);
        h.succeedWhen(() -> {
            List<Cow> cows = level.getEntitiesOfClass(Cow.class, pen.bounds());
            h.assertValueEqual(cows.size(), 3, "a calf is born");
            h.assertTrue(cows.stream().filter(Cow::isBaby).count() == 1, "it is young");
            h.assertFalse(Gestation.pregnant(a), "the mother is no longer carrying");
            h.assertTrue(Gestation.pregnant(b), "the other still is");
            h.assertTrue(a.getAge() > 0, "and she rests before breeding again");
            h.assertValueEqual(books.actual(Ledger.FLOW + "bred/animals"), 1.0, "the birth is on the ground truth");
            Ledger.census(level, society);
            h.assertValueEqual(books.actual(Ledger.FLOW + "herded/animals"), 2.0, "a calf born here was not herded in");
            h.assertValueEqual(books.actual("herd/" + pen.id + "/cow/young"), 1.0, "one calf");
            Engineering.remove(level, pen.cornerstone);
        });
    }

    private static void recordKeeping(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        KnowledgeData kd = KnowledgeData.get(server);
        String society = fresh(h, "test:ledger_levels");
        h.assertValueEqual(RecordKeeping.level(kd, society).error(), 0.40, "memory");
        kd.grant(society, "T-09");
        h.assertValueEqual(RecordKeeping.level(kd, society).error(), 0.25, "tallies");
        kd.grant(society, "P-063");
        h.assertValueEqual(RecordKeeping.level(kd, society).error(), 0.05, "paper, even without the steps between");
        kd.grant(society, "T-11");
        h.assertValueEqual(RecordKeeping.level(kd, society).error(), 0.05, "a lesser method does not make it worse");
        kd.grant(society, "P-266");
        h.assertValueEqual(RecordKeeping.level(kd, society).error(), 0.001, "a database");
        h.assertTrue(Ledger.report(server, society).get(0).contains("networked database"), "the report names the method");
        for (String id : List.of("T-09", "T-11", "T-13", "T-20", "P-063", "T-50", "P-150", "P-221", "P-266")) {
            h.assertTrue(TechTree.get().functionsById.containsKey(id), "record-keeping level " + id + " is a function in the tree");
        }
        h.succeed();
    }

    private static void flows(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        RandomSource random = RandomSource.create(7);
        String good = exact(h, "test:ledger_flows_exact");
        String poor = fresh(h, "test:ledger_flows_memory");
        for (int i = 0; i < 10; i++) {
            Ledger.flow(server, good, "collected", "minecraft:wheat", 100, random);
            Ledger.flow(server, poor, "collected", "minecraft:wheat", 100, random);
        }
        Ledger.flow(server, good, "collected", "minecraft:wheat", 0, random);
        Ledger.flow(server, good, "collected", "minecraft:wheat", -5, random);
        Ledger.flow(server, "", "collected", "minecraft:wheat", 5, random);
        LedgerData.Books exact = Ledger.books(server, good);
        LedgerData.Books rough = Ledger.books(server, poor);
        String wheat = Ledger.FLOW + "collected/minecraft:wheat";
        h.assertValueEqual(exact.actual(wheat), 1000.0, "ground truth");
        near(h, exact.observed(wheat), 1000, 10, "a database records it almost exactly");
        h.assertValueEqual(rough.actual(wheat), 1000.0, "the truth does not depend on the records");
        h.assertTrue(Math.abs(rough.observed(wheat) - 1000) > 1, "memory over- or under-reports: " + rough.observed(wheat));
        h.assertTrue(rough.observed(wheat) >= 100 && rough.observed(wheat) <= 4000, "but within reason: " + rough.observed(wheat));
        h.assertTrue(rough.bias.containsKey("flow"), "with a standing lean");

        Ledger.flow(server, good, "spoiled", "minecraft:beef", 50, random);
        h.assertValueEqual(exact.actual(Ledger.FLOW + "spoiled/minecraft:beef"), 50.0, "spoilage is real");
        h.assertValueEqual(exact.observed(Ledger.FLOW + "spoiled/minecraft:beef"), 0.0, "and unrecorded");

        TechTree.FunctionDef f = TechTree.get().functionsById.get("P-001");
        Ledger.recordProduced(server, good, f, 3);
        Ledger.recordConsumed(server, good, f, 3);
        int outputs = 0;
        for (TechTree.Ref out : f.outputs()) {
            if (out.kind() == TechTree.RefKind.ITEM) {
                h.assertValueEqual(exact.actual(Ledger.FLOW + "produced/" + spawn_9b5fbb9.MODID + ":" + out.id()), out.count() * 3.0,
                        "produced " + out.id());
                outputs++;
            }
        }
        h.assertTrue(outputs > 0, "P-001 makes something");
        h.assertTrue(exact.actual.keySet().stream().anyMatch(k -> k.startsWith(Ledger.FLOW + "consumed/")), "and uses something");

        h.assertTrue(Ledger.tracked(new ItemStack(Items.WHEAT)), "food is tracked");
        h.assertTrue(Ledger.tracked(ModContent.stack("hand_axe")), "tech-tree items are tracked");
        h.assertTrue(Ledger.tracked(new ItemStack(Items.FLINT)), "natural resources are tracked");
        h.assertFalse(Ledger.tracked(new ItemStack(Items.SADDLE)), "other things are not");
        h.succeed();
    }

    private static void persistence(GameTestHelper h) {
        LedgerData data = new LedgerData();
        LedgerData.Books books = data.books("team:x");
        books.actual.put("stock/b1/minecraft:wheat", 12.0);
        books.observed.put("stock/b1/minecraft:wheat", 15.5);
        books.bias.put("stock", 0.1);
        books.stamps.put("census", 5L);
        books.history.add(new LedgerData.Snapshot(3, Map.of("stock/minecraft:wheat", 12.0), Map.of("stock/minecraft:wheat", 15.5)));
        var json = LedgerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        LedgerData.Books back = LedgerData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().books("team:x");
        h.assertValueEqual(back.actual("stock/b1/minecraft:wheat"), 12.0, "actual");
        h.assertValueEqual(back.observed("stock/b1/minecraft:wheat"), 15.5, "observed");
        h.assertValueEqual(back.bias.get("stock"), 0.1, "bias");
        h.assertValueEqual(back.stamps.get("census"), 5L, "stamps");
        h.assertValueEqual(back.history.size(), 1, "history");
        h.assertValueEqual(back.history.get(0).observed().get("stock/minecraft:wheat"), 15.5, "snapshot");
        back.actual.put("x", 1.0); // decoded books are still writable
        h.succeed();
    }
}
