package dev.qube.spawn_9b5fbb9.content;

import java.util.List;
import java.util.function.Consumer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import dev.qube.spawn_9b5fbb9.world.NaturalWorldRules;
import dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops;
import dev.qube.spawn_9b5fbb9.agriculture.Husbandry;
import net.minecraft.world.item.*;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import dev.qube.spawn_9b5fbb9.world.WorldGameTests;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SurvivalContent {
    public static final String MODID = "spawn_9b5fbb9";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final Map<String,DeferredItem<? extends Item>> MATERIALS = new LinkedHashMap<>();
    public static final DeferredBlock<WorkstationBlock> KILN = BLOCKS.registerBlock("kiln",WorkstationBlock::new,p->p.strength(2.5f));
    public static final DeferredBlock<WorkstationBlock> FORGE = BLOCKS.registerBlock("forge",WorkstationBlock::new,p->p.strength(3.5f));
    public static final DeferredBlock<WorkstationBlock> BLOOMERY = BLOCKS.registerBlock("bloomery",WorkstationBlock::new,p->p.strength(3));
    public static final DeferredBlock<WorkstationBlock> ANVIL = BLOCKS.registerBlock("anvil",WorkstationBlock::new,p->p.strength(3));
    public static final DeferredBlock<WorkstationBlock> LOOM = BLOCKS.registerBlock("loom",WorkstationBlock::new,p->p.strength(2));
    public static final DeferredBlock<WorkstationBlock> TROUGH = BLOCKS.registerBlock("trough",WorkstationBlock::new,p->p.strength(2));
    public static final DeferredBlock<Block> TIN_ORE = BLOCKS.registerSimpleBlock("tin_ore",p->p.strength(3).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> LOOSE_STONES = BLOCKS.registerBlock("loose_stones",p->new GroundResourceBlock(p,"loose_stone"),p->p.noCollision().instabreak());
    public static final DeferredBlock<Block> WILD_PLANTS = BLOCKS.registerBlock("wild_plants",p->new GroundResourceBlock(p,"wild"),p->p.noCollision().instabreak());
    public static final DeferredBlock<Block> FRUIT_TREE = BLOCKS.registerBlock("fruit_tree",FruitTreeBlock::new,p->p.strength(0.4f).randomTicks().noOcclusion());
    public static final Map<String,DeferredBlock<CropBlock>> CROPS = new LinkedHashMap<>();
    public static final Map<String,DeferredBlock<GroundResourceBlock>> FORAGE = new LinkedHashMap<>();
    public static final DeferredBlock<Block> LIMESTONE=BLOCKS.registerSimpleBlock("limestone",p->p.strength(2.5f).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> MALACHITE_ORE=BLOCKS.registerSimpleBlock("malachite_ore",p->p.strength(3).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> CASSITERITE_ORE=BLOCKS.registerSimpleBlock("cassiterite_ore",p->p.strength(3).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> HEMATITE_ORE=BLOCKS.registerSimpleBlock("hematite_ore",p->p.strength(3).requiresCorrectToolForDrops());
    public static final net.neoforged.neoforge.registries.DeferredHolder<BlockEntityType<?>,BlockEntityType<WorkstationEntity>> WORKSTATION_ENTITY = BLOCK_ENTITIES.register("workstation",()->new BlockEntityType<>(WorkstationEntity::new,Set.of(KILN.get(),FORGE.get(),TROUGH.get(),BLOOMERY.get(),ANVIL.get(),LOOM.get())));
    private static final DeferredRegister<Consumer<GameTestHelper>> TESTS = DeferredRegister.create(Registries.TEST_FUNCTION, MODID);
    static {
        for(String source:new String[]{"fallen_sticks","wild_flax","wild_yarrow","wild_bramble","wild_rice","wild_maize","wild_wheat","wild_carrot","wild_potato","wild_beetroot","wild_pumpkin","wild_melon"}){
            var block=BLOCKS.registerBlock(source,p->new GroundResourceBlock(p,source),p->p.noCollision().instabreak());FORAGE.put(source,block);MATERIALS.put(source,ITEMS.registerSimpleBlockItem(block));
        }
        for(String material:new String[]{"flax_fiber","yarrow_leaves","malachite","cassiterite","hematite"})MATERIALS.put(material,ITEMS.registerSimpleItem(material));
        for(String id:new String[]{"wooden_toy","loose_stone","stone_edge","obsidian_flake","plant_fiber","dried_fiber","cordage","yarn","wool_cloth","fiber_cloth","unfired_pot","pot_lid","crushed_copper","roasted_copper","crushed_tin","roasted_tin","crushed_iron","roasted_iron","tin_ingot","bronze_ingot","iron_bloom","medicinal_leaves","clean_cloth","bandage","painkiller","antitoxin","antiseptic","antibiotic","ceramic_shard","metal_scrap","fertilized_egg"}) MATERIALS.put(id,ITEMS.registerSimpleItem(id));
        MATERIALS.put("fired_pot",ITEMS.registerSimpleItem("fired_pot",p->p.stacksTo(1).durability(96)));
        for(String cloth:new String[]{"wool","fiber"}) for(String piece:new String[]{"shirt","pants","jacket","shoes"}) {
            String id=cloth+"_"+piece; MATERIALS.put(id,ITEMS.registerSimpleItem(id,p->p.durability(cloth.equals("wool")?384:192)));
        }
        for(String crop:new String[]{"rice","maize","fiber","medicinal","flax","yarrow","bramble"}) {
            var block=BLOCKS.registerBlock(crop+"_crop",CropBlock::new,p->p.noCollision().randomTicks().instabreak()); CROPS.put(crop,block);
            MATERIALS.put(crop+"_seeds",ITEMS.registerSimpleBlockItem(crop+"_seeds",block));
        }
        for(String food:new String[]{"rice_grain","maize","wild_berries","blackberries"}) MATERIALS.put(food,ITEMS.registerSimpleItem(food,p->p.food(new FoodProperties.Builder().nutrition(food.endsWith("berries")?2:4).saturationModifier(0.2f).build())));
        MATERIALS.put("spoiled_food",ITEMS.registerSimpleItem("spoiled_food",p->p.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0).build())));
        ToolMaterial bronze=new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL,420,6.5f,2,1,ItemTags.COPPER_TOOL_MATERIALS);
        ToolMaterial obsidian=new ToolMaterial(BlockTags.INCORRECT_FOR_STONE_TOOL,54,7,3,1,ItemTags.STONE_TOOL_MATERIALS);
        for(String material:new String[]{"knapped_stone","obsidian","copper","bronze","forged_iron"}) {
            ToolMaterial tier=switch(material){case "obsidian"->obsidian;case "copper"->ToolMaterial.COPPER;case "bronze"->bronze;case "forged_iron"->ToolMaterial.IRON;default->ToolMaterial.STONE;};
            MATERIALS.put(material+"_pickaxe",ITEMS.registerSimpleItem(material+"_pickaxe",p->p.pickaxe(tier,1,-2.8f)));
            MATERIALS.put(material+"_axe",ITEMS.registerSimpleItem(material+"_axe",p->p.axe(tier,5,-3)));
            MATERIALS.put(material+"_hoe",ITEMS.registerSimpleItem(material+"_hoe",p->p.hoe(tier,-1,-2)));
        }
        MATERIALS.put("spear",ITEMS.registerSimpleItem("spear",p->p.sword(ToolMaterial.STONE,3,-2.4f)));
        MATERIALS.put("metal_rivet",ITEMS.registerSimpleItem("metal_rivet"));
        for(String metal:new String[]{"copper","bronze","forged_iron"})for(String type:new String[]{"pickaxe","axe","hoe","sword","shovel","helmet","chestplate","leggings","boots"}){String id=metal+"_"+type+"_blank";MATERIALS.put(id,ITEMS.registerSimpleItem(id));}
        for(String metal:new String[]{"copper","iron"}){String id=metal+"_spear_blank";MATERIALS.put(id,ITEMS.registerSimpleItem(id));}
        for(String id:new String[]{"shield_blank","crossbow_blank","crossbow_stock","shears_blank","bucket_blank","flint_and_steel_blank","spyglass_blank","brush_blank","brush_bundle"})MATERIALS.put(id,ITEMS.registerSimpleItem(id));
        MATERIALS.put("bronze_sword",ITEMS.registerSimpleItem("bronze_sword",p->p.sword(bronze,3,-2.4f)));
        MATERIALS.put("bronze_shovel",ITEMS.registerSimpleItem("bronze_shovel",p->p.shovel(bronze,1.5f,-3)));
        var bronzeArmor=new net.minecraft.world.item.equipment.ArmorMaterial(18,java.util.Map.of(net.minecraft.world.item.equipment.ArmorType.HELMET,2,net.minecraft.world.item.equipment.ArmorType.CHESTPLATE,6,net.minecraft.world.item.equipment.ArmorType.LEGGINGS,5,net.minecraft.world.item.equipment.ArmorType.BOOTS,2),1,net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_COPPER,0,0,ItemTags.COPPER_TOOL_MATERIALS,net.minecraft.world.item.equipment.EquipmentAssets.COPPER);
        for(var type:new net.minecraft.world.item.equipment.ArmorType[]{net.minecraft.world.item.equipment.ArmorType.HELMET,net.minecraft.world.item.equipment.ArmorType.CHESTPLATE,net.minecraft.world.item.equipment.ArmorType.LEGGINGS,net.minecraft.world.item.equipment.ArmorType.BOOTS}){String id="bronze_"+type.getName();MATERIALS.put(id,ITEMS.registerSimpleItem(id,p->p.humanoidArmor(bronzeArmor,type)));}
        for(var block:List.of(KILN,FORGE,BLOOMERY,ANVIL,LOOM,TROUGH,TIN_ORE,FRUIT_TREE,LIMESTONE,MALACHITE_ORE,CASSITERITE_ORE,HEMATITE_ORE)) MATERIALS.put(block.getId().getPath(),ITEMS.registerSimpleBlockItem(block));
        TESTS.register("world_prohibited_additions", () -> WorldGameTests::prohibitedAdditions);
        TESTS.register("world_container_prohibition",()->WorldGameTests::containerProhibition);
        TESTS.register("content_preservation",()->ContentGameTests::preservation);
        TESTS.register("content_completed_firing",()->ContentGameTests::completedFiring);
        TESTS.register("content_seasonal_bonemeal",()->ContentGameTests::seasonalBonemeal);
        TESTS.register("content_juvenile_feeding",()->ContentGameTests::juvenileFeeding);
        TESTS.register("content_controller_vessels",()->ContentGameTests::controllerVessels);
        TESTS.register("content_npc_boiling",()->ContentGameTests::npcBoiling);
        TESTS.register("content_slow_harvest",()->ContentGameTests::slowHarvest);
        TESTS.register("content_repeat_fruit",()->ContentGameTests::repeatFruit);
        TESTS.register("content_daily_eggs",()->ContentGameTests::dailyEggs);
        TESTS.register("content_gestation_delivery",()->ContentGameTests::gestationDelivery);
        TESTS.register("content_hen_egg_hatches",()->ContentGameTests::henEggHatches);
        TESTS.register("content_nested_egg_stays",()->ContentGameTests::nestedEggStays);
        TESTS.register("content_plain_goods_stack",()->ContentGameTests::plainGoodsStack);
        TESTS.register("content_loose_egg_rots",()->ContentGameTests::looseEggRots);
        TESTS.register("content_dead_workers_job_taken_up",()->ContentGameTests::deadWorkersJobIsTakenUp);
        TESTS.register("content_slow_hand_needs_more_fire",()->ContentGameTests::slowHandNeedsMoreFire);
        TESTS.register("content_furnace_stays_cold",()->ContentGameTests::furnaceStaysCold);
        TESTS.register("content_ore_lies_in_one_heap",()->ContentGameTests::oreLiesInOneHeap);
        TESTS.register("content_only_stock_breeds",()->ContentGameTests::onlyStockBreeds);
        TESTS.register("survival_unconscious_lies_still",()->ContentGameTests::unconsciousLiesStill);
        TESTS.register("reviewfix_records_kept_by_chunk",()->dev.qube.spawn_9b5fbb9.controllers.ReviewFixGameTests::recordsKeptByChunk);
        TESTS.register("survival_sleep_and_pain",()->ContentGameTests::sleepAndPain);
        TESTS.register("content_litter_lamb_dying_dam",()->ContentGameTests::litterLambAndDyingDam);
        TESTS.register("content_product_recovery",()->ContentGameTests::productRecovery);
        TESTS.register("content_supplied_maturation",()->ContentGameTests::suppliedMaturation);
        TESTS.register("content_station_fuel_catchup",()->ContentGameTests::stationFuelCatchup);
        TESTS.register("content_stored_weather_pause",()->ContentGameTests::storedWeatherPause);
        TESTS.register("content_npc_food_freshness",()->ContentGameTests::npcFoodFreshness);
        TESTS.register("content_station_wear_carry",()->ContentGameTests::stationWearCarry);
        TESTS.register("content_dry_wool_recovery",()->ContentGameTests::dryWoolRecovery);
        TESTS.register("content_wild_berry_recovery",()->ContentGameTests::wildBerryRecovery);
        TESTS.register("content_mature_rice_frost",()->ContentGameTests::matureRiceFrost);
        TESTS.register("content_crop_definitions",()->ContentGameTests::cropDefinitions);
        TESTS.register("content_husbandry_definitions",()->ContentGameTests::husbandryDefinitions);
        TESTS.register("content_decay_definitions",()->ContentGameTests::decayDefinitions);
        TESTS.register("content_production_definitions",()->ContentGameTests::productionDefinitions);
        TESTS.register("content_finite_trough_catchup",()->ContentGameTests::finiteTroughCatchup);
        TESTS.register("content_forge_single_actor",()->ContentGameTests::forgeSingleActor);
        TESTS.register("content_forge_world_job",()->ContentGameTests::forgeWorldJob);
        TESTS.register("content_forge_legacy_budget",()->ContentGameTests::forgeLegacyBudget);
        TESTS.register("practiced_clay_pot_quality",()->PracticedContentGameTests::clayPotQuality);
        TESTS.register("learning_mining_exposure",()->dev.qube.spawn_9b5fbb9.controllers.LearningWorkGameTests::miningExposure);
        TESTS.register("ecology_single_source",()->dev.qube.spawn_9b5fbb9.controllers.EcologyGameTests::singleSource);
        TESTS.register("ecology_rocks",()->dev.qube.spawn_9b5fbb9.controllers.EcologyGameTests::rocksAreRocks);
        TESTS.register("geology_old_veins",()->dev.qube.spawn_9b5fbb9.world.GeologyGameTests::oldVeinsDisabled);
        TESTS.register("resources_nearest_first_bounded",()->dev.qube.spawn_9b5fbb9.controllers.ResourceActionGameTests::nearestFirstAndBounded);
        TESTS.register("resources_visible_evidence",()->dev.qube.spawn_9b5fbb9.controllers.ResourceActionGameTests::visibleEvidence);
        TESTS.register("resources_two_actor_chain",()->dev.qube.spawn_9b5fbb9.controllers.ReviewFixGameTests::twoActorMineralChain);
        TESTS.register("reviewfix_flax_bootstrap",()->dev.qube.spawn_9b5fbb9.controllers.ReviewFixGameTests::flaxBootstrap);
        TESTS.register("reviewfix_native_kiln",()->dev.qube.spawn_9b5fbb9.controllers.ReviewFixGameTests::nativeKiln);
        TESTS.register("reviewfix_grid_parity",()->dev.qube.spawn_9b5fbb9.controllers.ReviewFixGameTests::gridParity);
        TESTS.register("reviewfix_repeated_extraction",()->dev.qube.spawn_9b5fbb9.controllers.ReviewFixGameTests::repeatedExtraction);
        TESTS.register("reviewfix_decoration_order",()->dev.qube.spawn_9b5fbb9.controllers.ReviewFixGameTests::decorationOrder);
        TESTS.register("reviewfix_covered_habitat",()->dev.qube.spawn_9b5fbb9.controllers.ReviewFixGameTests::coveredHabitat);
        TESTS.register("reviewfix_full_animal",()->dev.qube.spawn_9b5fbb9.controllers.ReviewFixGameTests::fullAnimal);
        TESTS.register("environment_profile",()->dev.qube.spawn_9b5fbb9.environment.EnvironmentGameTests::profile);
        TESTS.register("environment_terrain",()->dev.qube.spawn_9b5fbb9.environment.EnvironmentGameTests::terrain);
        TESTS.register("environment_packet",()->dev.qube.spawn_9b5fbb9.environment.EnvironmentGameTests::packet);
        TESTS.register("environment_crop_history",()->dev.qube.spawn_9b5fbb9.environment.EnvironmentGameTests::cropHistory);
        TESTS.register("environment_exposure",()->dev.qube.spawn_9b5fbb9.environment.EnvironmentGameTests::exposure);
        TESTS.register("environment_frost_history",()->dev.qube.spawn_9b5fbb9.environment.EnvironmentGameTests::frostHistory);
        TESTS.register("learning_passion_snapshot",()->dev.qube.spawn_9b5fbb9.controllers.LearningWorkGameTests::passionSnapshot);
        TESTS.register("practiced_pottery_physical_capacity",()->PracticedContentGameTests::potteryPhysicalCapacity);
        TESTS.register("practiced_smith_gate_conservation",()->PracticedContentGameTests::smithGateConservation);
        TESTS.register("practiced_loom_real_inputs",()->PracticedContentGameTests::loomUsesRealInputs);
        TESTS.register("practiced_cancelled_no_practice",()->PracticedContentGameTests::cancelledWorkNoPractice);
        TESTS.register("practiced_legacy_pot_fine",()->PracticedContentGameTests::legacyPotFine);
        TESTS.register("practiced_armor_skill_gate",()->PracticedContentGameTests::armourSkillGate);
        TESTS.register("practiced_cancelled_bindings",()->PracticedContentGameTests::cancelledBindingsConserved);
        TESTS.register("practiced_jar_capacities",()->PracticedContentGameTests::jarCapacities);
        TESTS.register("practiced_novice_rivets",()->PracticedContentGameTests::noviceRivetsPractice);
        TESTS.register("practiced_full_metal_chain",()->PracticedContentGameTests::fullMetalChain);
        TESTS.register("practiced_reload_binding_invariant",()->PracticedContentGameTests::reloadBindingInvariant);
        TESTS.register("practiced_reload_blank_stage",()->PracticedContentGameTests::reloadBlankStageInvariant);
    }
    public static void register(IEventBus bus) {
        dev.qube.spawn_9b5fbb9.world.NaturalResourceGeneration.register(bus);
        // Industry, transport and warfare add to the same registers before they are bound to the bus.
        dev.qube.spawn_9b5fbb9.industry.IndustrialContent.register(bus);
        ITEMS.register(bus); BLOCKS.register(bus); BLOCK_ENTITIES.register(bus);
        TESTS.register(bus);
        bus.addListener(SurvivalContent::tests);
        bus.addListener(SurvivalContent::creative);
    }
    public static void init() { NaturalWorldRules.init(); ItemAging.init(); Pottery.init(); SeasonalCrops.init(); Husbandry.init(); Production.init(); dev.qube.spawn_9b5fbb9.controllers.ResourceActions.init(); dev.qube.spawn_9b5fbb9.world.ExtractionHistory.init(); dev.qube.spawn_9b5fbb9.industry.IndustrialContent.init(); }
    public static Item item(String id) { return MATERIALS.get(id).get(); }
    private static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event){event.removeIf(NaturalWorldRules::prohibited,CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);if(event.getTabKey()==CreativeModeTabs.INGREDIENTS)MATERIALS.values().forEach(event::accept);}
    private static void tests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(Identifier.fromNamespaceAndPath(MODID,"living_world"), new TestEnvironmentDefinition.AllOf(List.of()));
        var data = new TestData<Holder<TestEnvironmentDefinition<?>>>(env,net.minecraft.world.level.Level.OVERWORLD,Identifier.fromNamespaceAndPath("spawn_9b5fbb9","test_arena"), 100,0,true,Rotation.NONE,false,1,1,true,8);
        TESTS.getEntries().forEach(test -> event.registerTest(test.getId(), new FunctionGameTestInstance(test.unwrapKey().orElseThrow(), data)));
    }
}
