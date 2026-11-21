package dev.qube.spawn_9b5fbb9.industry;

import java.util.List;
import java.util.function.Consumer;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.controllers.TransportGameTests;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import dev.qube.spawn_9b5fbb9.transport.VehicleEntity;
import dev.qube.spawn_9b5fbb9.warfare.WarfareGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** GameTests for industry, transport and warfare, on real blocks, entities and inventories. */
public final class IndustrialGameTests {
    private IndustrialGameTests(){}
    private static final DeferredRegister<Consumer<GameTestHelper>> TESTS=DeferredRegister.create(Registries.TEST_FUNCTION,SurvivalContent.MODID);
    static {
        TESTS.register("industry_batch_needs_full_bill",()->IndustrialGameTests::batchNeedsFullBill);
        TESTS.register("industry_skill_gate",()->IndustrialGameTests::skillGate);
        TESTS.register("industry_electronics_bill",()->IndustrialGameTests::electronicsBill);
        TESTS.register("industry_passive_nitre",()->IndustrialGameTests::passiveNitre);
        TESTS.register("industry_powder_mill_fire",()->IndustrialGameTests::powderMillFire);
        TESTS.register("industry_yard_rolls_out_cart",()->IndustrialGameTests::yardRollsOutCart);
        TESTS.register("industry_demolition_spills_stock",()->IndustrialGameTests::demolitionSpillsStock);
        TESTS.register("techtree_no_dead_ends",()->FacilityGameTests::noDeadEnds);
        TESTS.register("techtree_era_gates",()->FacilityGameTests::eraGates);
        TESTS.register("facility_foundry_plant",()->FacilityGameTests::foundryPlant);
        TESTS.register("facility_machine_shop_engine",()->FacilityGameTests::machineShopEngine);
        TESTS.register("facility_cleanroom_fab",()->FacilityGameTests::cleanroomFab);
        TESTS.register("transport_cargo_conserved",()->TransportGameTests::cargoConserved);
        TESTS.register("transport_team_required",()->TransportGameTests::teamRequired);
        TESTS.register("transport_dead_animal_leaves_team",()->TransportGameTests::deadAnimalLeavesTeam);
        TESTS.register("transport_starving_team_dies",()->TransportGameTests::starvingTeamDies);
        TESTS.register("transport_spoiled_food_not_cargo",()->TransportGameTests::spoiledFoodIsNotCargo);
        TESTS.register("transport_food_ages_in_cargo",()->TransportGameTests::foodAgesInCargo);
        TESTS.register("transport_backing_off_the_line",()->TransportGameTests::backingOffTheLine);
        TESTS.register("industry_full_works_not_pulled_down",()->IndustrialGameTests::fullWorksIsNotPulledDown);
        TESTS.register("military_rations_by_nutrition",()->WarfareGameTests::rationsByNutrition);
        TESTS.register("military_absent_soldier_learns_of_death",()->WarfareGameTests::absentSoldierLearnsOfDeath);
        TESTS.register("military_commander_no_threat",()->WarfareGameTests::commanderIsNoThreat);
        TESTS.register("industry_dead_hand_frees_place",()->IndustrialGameTests::deadHandFreesPlace);
        TESTS.register("military_dead_commander_releases_men",()->WarfareGameTests::deadCommanderReleasesMen);
        TESTS.register("transport_track_laying",()->TransportGameTests::trackLaying);
        TESTS.register("transport_train_runs",()->TransportGameTests::trainRuns);
        TESTS.register("transport_wagon_supplies_works",()->TransportGameTests::wagonSuppliesWorks);
        TESTS.register("military_recruit_bounty",()->WarfareGameTests::recruitBounty);
        TESTS.register("military_conscription",()->WarfareGameTests::conscription);
        TESTS.register("military_equipment",()->WarfareGameTests::equipment);
        TESTS.register("military_attack_order",()->WarfareGameTests::attackOrder);
        TESTS.register("military_move_formation",()->WarfareGameTests::moveFormation);
        TESTS.register("military_musket",()->WarfareGameTests::musket);
        TESTS.register("military_morale_rout",()->WarfareGameTests::moraleRout);
        TESTS.register("military_archer_volley",()->WarfareGameTests::archerVolley);
        TESTS.register("military_mounted_soldier",()->WarfareGameTests::mountedSoldier);
        TESTS.register("siege_cannon_masonry_and_earth",()->WarfareGameTests::cannonMasonryAndEarth);
        TESTS.register("siege_crew_loads_from_store",()->WarfareGameTests::crewLoadsFromStore);
        TESTS.register("siege_trebuchet_arc",()->WarfareGameTests::trebuchetArc);
        TESTS.register("weather_rain_douses_fire",()->dev.qube.spawn_9b5fbb9.wildlife.WildlifeGameTests::rainDousesFire);
        TESTS.register("illness_onset_and_spread",()->dev.qube.spawn_9b5fbb9.wildlife.WildlifeGameTests::illnessOnsetAndSpread);
        TESTS.register("clothing_wears_with_use",()->dev.qube.spawn_9b5fbb9.wildlife.WildlifeGameTests::clothWearsWithUse);
        TESTS.register("clothing_mend_swap_undress",()->dev.qube.spawn_9b5fbb9.wildlife.WildlifeGameTests::clothMendSwapUndress);
        TESTS.register("wildlife_ranges_on_the_map",()->dev.qube.spawn_9b5fbb9.wildlife.WildlifeGameTests::rangesOnTheMap);
        TESTS.register("world_spawner_not_offered_forbidden",()->dev.qube.spawn_9b5fbb9.wildlife.WildlifeGameTests::spawnerNotOfferedForbidden);
        TESTS.register("crops_calendar_known_by_block",()->dev.qube.spawn_9b5fbb9.wildlife.WildlifeGameTests::calendarCropsKnownByBlock);
        TESTS.register("items_litter_rots_away",()->dev.qube.spawn_9b5fbb9.wildlife.WildlifeGameTests::litterRotsAway);
        TESTS.register("bots_registry_export",()->dev.qube.spawn_9b5fbb9.wildlife.WildlifeGameTests::registryExportMatchesServer);
        TESTS.register("wildlife_hunger_drives_hunting",()->dev.qube.spawn_9b5fbb9.wildlife.WildlifeGameTests::hungerDrivesHunting);
        TESTS.register("wildlife_people_not_ordinary_prey",()->dev.qube.spawn_9b5fbb9.wildlife.WildlifeGameTests::peopleAreNotOrdinaryPrey);
        TESTS.register("wildlife_wolves_need_the_pack",()->dev.qube.spawn_9b5fbb9.wildlife.WildlifeGameTests::wolvesNeedThePack);
        TESTS.register("wildlife_rout_bite_carcass",()->dev.qube.spawn_9b5fbb9.wildlife.WildlifeGameTests::routBiteAndCarcass);
        TESTS.register("wildlife_bear_takes_bait",()->dev.qube.spawn_9b5fbb9.wildlife.WildlifeGameTests::hungryBearTakesBait);
        TESTS.register("farming_plough_drawn_by_horses",()->dev.qube.spawn_9b5fbb9.agriculture.FarmingGameTests::ploughDrawnByHorses);
        TESTS.register("farming_plough_turns_a_furrow",()->dev.qube.spawn_9b5fbb9.agriculture.FarmingGameTests::ploughTurnsAFurrow);
        TESTS.register("farming_tilled_ground_lasts",()->dev.qube.spawn_9b5fbb9.agriculture.FarmingGameTests::tilledGroundLasts);
        TESTS.register("farming_spring_food",()->dev.qube.spawn_9b5fbb9.agriculture.FarmingGameTests::springFood);
        TESTS.register("farming_coffee_and_tea_bushes",()->dev.qube.spawn_9b5fbb9.agriculture.FarmingGameTests::coffeeAndTeaBushes);
        TESTS.register("farming_brew_and_drink",()->dev.qube.spawn_9b5fbb9.agriculture.FarmingGameTests::brewAndDrink);
        TESTS.register("farming_forage_climates",()->dev.qube.spawn_9b5fbb9.agriculture.FarmingGameTests::forageClimates);
        TESTS.register("wildlife_bear_runs_down_sheep",()->dev.qube.spawn_9b5fbb9.wildlife.WildlifeGameTests::bearRunsDownSheep);
    }
    public static void register(IEventBus bus){TESTS.register(bus);bus.addListener(IndustrialGameTests::tests);}
    private static void tests(RegisterGameTestsEvent event){
        Holder<TestEnvironmentDefinition<?>> env=event.registerEnvironment(Identifier.fromNamespaceAndPath(SurvivalContent.MODID,"industry_and_war"),new TestEnvironmentDefinition.AllOf(List.of()));
        var data=new TestData<Holder<TestEnvironmentDefinition<?>>>(env,net.minecraft.world.level.Level.OVERWORLD,Identifier.fromNamespaceAndPath(SurvivalContent.MODID,"test_arena"),400,0,true,Rotation.NONE,false,1,1,true,8);
        TESTS.getEntries().forEach(test->event.registerTest(test.getId(),new FunctionGameTestInstance(test.unwrapKey().orElseThrow(),data)));
    }

    // ---- Shared fixtures ------------------------------------------------------------------------------
    public static void floor(GameTestHelper h){for(int x=0;x<16;x++)for(int z=0;z<16;z++)h.setBlock(x,1,z,Blocks.STONE);}
    public static LivingEntity worker(GameTestHelper h,int x,int z,Skill skill,int level){
        h.setBlock(x,1,z,Blocks.STONE);var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(x,2,z));actor.setNoAi(true);SurvivalApi.enroll(actor);
        if(skill!=null){var body=SurvivalApi.state(actor);body.skills.setLevel(skill,level);SurvivalApi.save(actor,body);}
        return actor;
    }
    public static WorksEntity works(GameTestHelper h,String station,int x,int z){
        h.setBlock(x,1,z,Blocks.STONE);h.setBlock(x,6,z,Blocks.STONE);
        // Works that need working floor stand in a seven-metre shed: stone floor, roof five metres up.
        if(Industry.SITE.get(station).stream().anyMatch(need->need.startsWith("floor")))for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
            int fx=x+dx,fz=z+dz;if(fx<0||fx>15||fz<0||fz>15)continue;
            if(h.getBlockState(new BlockPos(fx,1,fz)).isAir())h.setBlock(fx,1,fz,Blocks.STONE);h.setBlock(fx,6,fz,Blocks.STONE);
        }
        var pos=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(pos,IndustrialContent.WORKS.get(station).get().defaultBlockState(),3);
        return (WorksEntity)h.getLevel().getBlockEntity(pos);
    }
    public static ItemStack item(String id,int count){return new ItemStack(SurvivalContent.item(id),count);}
    private static void hand(LivingEntity actor,ItemStack stack){actor.setItemInHand(InteractionHand.MAIN_HAND,stack);}

    // ---- Industry -------------------------------------------------------------------------------------
    /** A place at a works is not kept by the dead: once the body is gone the works could not tell, and the place would be lost for good. */
    public static void deadHandFreesPlace(GameTestHelper h){
        var first=worker(h,1,1,Skill.CONSTRUCTION,4);var second=worker(h,1,3,Skill.CONSTRUCTION,4);var works=works(h,"workshop",2,1);
        h.assertTrue(Industry.select(first,works,"wooden_wheel").success()&&Industry.operate(first,works).success(),"The first worker takes the job");
        int places=Industry.RECIPES.get("wooden_wheel").maxWorkers();
        for(int i=1;i<places;i++)works.workers.add(java.util.UUID.randomUUID().toString());
        h.assertTrue(!Industry.operate(second,works).success(),"A full crew admits nobody else");
        first.hurtServer(h.getLevel(),h.getLevel().damageSources().genericKill(),Float.MAX_VALUE);
        h.assertTrue(!first.isAlive()&&!works.workers.contains(first.getUUID().toString()),"The dead worker is struck off at the moment of death");
        h.assertTrue(Industry.operate(second,works).success(),"The place the dead worker held can be taken");
        second.discard();h.succeed();
    }
    public static void batchNeedsFullBill(GameTestHelper h){
        var actor=worker(h,1,1,Skill.CONSTRUCTION,4);var works=works(h,"workshop",2,1);long now=SurvivalApi.calendar(h.getLevel());
        h.assertTrue(Industry.select(actor,works,"wooden_wheel").success(),"A workshop must accept its own recipe");
        h.assertFalse(Industry.select(actor,works,"steel").success(),"A workshop cannot be set to a foundry recipe");
        hand(actor,new ItemStack(Items.COBBLESTONE,8));h.assertTrue(!Industry.deposit(actor,works,InteractionHand.MAIN_HAND,false).success()&&actor.getMainHandItem().getCount()==8,"Materials the recipe does not use are refused and stay in hand");
        hand(actor,new ItemStack(Items.BIRCH_PLANKS,1));h.assertTrue(Industry.deposit(actor,works,InteractionHand.MAIN_HAND,false).success()&&actor.getMainHandItem().isEmpty(),"Any plank species satisfies the plank bill and leaves the hand");
        hand(actor,new ItemStack(Items.IRON_INGOT,10));h.assertTrue(Industry.deposit(actor,works,InteractionHand.MAIN_HAND,false).success(),"Ten kilograms of iron make the tyre and nave bands");
        hand(actor,item("metal_rivet",7));h.assertTrue(Industry.deposit(actor,works,InteractionHand.MAIN_HAND,false).success(),"Rivets are part of a wheel");
        h.assertTrue(Industry.operate(actor,works).success(),"A construction-4 worker may take the job");
        works.advance(h.getLevel(),now);
        h.assertTrue(!works.active&&works.products.isEmpty()&&Ledger.count(works.stock,"#minecraft:planks")==1,"A batch cannot begin one rivet short, and nothing is consumed");
        hand(actor,item("metal_rivet",1));Industry.deposit(actor,works,InteractionHand.MAIN_HAND,false);
        works.advance(h.getLevel(),now+20);
        h.assertTrue(works.active&&works.stock.isEmpty()&&works.products.isEmpty(),"The full bill is consumed when the batch begins, and nothing is finished yet");
        h.assertTrue(Industry.RECIPES.get("wooden_wheel").ticks()==16000,"A wheel is two days of a wheelwright's work on the game clock");
        works.progress=Industry.ticks(Industry.RECIPES.get("wooden_wheel"))-6;
        h.runAfterDelay(60,()->{
            h.assertTrue(works.products.getOrDefault("spawn_9b5fbb9:wooden_wheel",0L)==1&&!works.active,"Exactly one wheel comes from one bill of materials");
            h.assertTrue(SurvivalApi.state(actor).skills.experience(Skill.CONSTRUCTION)>10000,"Completed attended work is practice");
            h.assertTrue(Industry.collect(actor,works).success()&&works.products.isEmpty(),"Finished goods are collected once");
            actor.discard();h.succeed();
        });
    }
    public static void skillGate(GameTestHelper h){
        var novice=worker(h,1,1,null,0);var works=works(h,"workshop",2,1);
        Industry.select(novice,works,"musket");
        h.assertTrue(Industry.operate(novice,works).message().contains("level 12"),"Assembling a musket needs a practised smith");
        works.stock.put("spawn_9b5fbb9:musket_barrel",8L);works.stock.put("spawn_9b5fbb9:musket_lock",8L);works.stock.put("spawn_9b5fbb9:musket_stock",8L);works.stock.put("spawn_9b5fbb9:steel_ingot",1L);works.stock.put("spawn_9b5fbb9:metal_rivet",16L);
        works.workers.add(novice.getUUID().toString());novice.getPersistentData().putLong("survivalWorksPos",works.getBlockPos().asLong());
        long now=SurvivalApi.calendar(h.getLevel());works.advance(h.getLevel(),now);works.advance(h.getLevel(),now+20);
        h.assertTrue(works.active&&works.progress==0&&works.products.isEmpty(),"An unqualified hand contributes no work even if listed");
        novice.discard();h.succeed();
    }
    public static void electronicsBill(GameTestHelper h){
        var server=Industry.RECIPES.get("server");var computer=Industry.RECIPES.get("computer");
        h.assertTrue(server.inputs().get("spawn_9b5fbb9:chip")==20000&&computer.inputs().get("spawn_9b5fbb9:chip")==2800,"A minicomputer of small-scale logic is thousands of packages and a mainframe tens of thousands");
        var bill=Industry.expand("spawn_9b5fbb9:server",1);
        long fine=bill.getOrDefault("@spawn_9b5fbb9:fine_wire",0L),wire=bill.getOrDefault("@spawn_9b5fbb9:insulated_wire",0L);
        h.assertTrue(fine>=320000&&wire>=60000,"Sixteen bonds a package: a server's chips alone take 320,000 bond wires, and its backplanes sixty kilometres of hook-up wire: "+fine+" bonds, "+wire+" m");
        h.assertTrue(bill.getOrDefault("@minecraft:copper_ingot",0L)>=400&&bill.getOrDefault("@spawn_9b5fbb9:aluminium_ingot",0L)>=100&&bill.getOrDefault("@spawn_9b5fbb9:silicon_wafer",0L)>=125
            &&bill.getOrDefault("spawn_9b5fbb9:fluorite",0L)>0&&bill.getOrDefault("spawn_9b5fbb9:salt",0L)>0&&bill.getOrDefault("spawn_9b5fbb9:bauxite",0L)>0&&bill.getOrDefault("spawn_9b5fbb9:malachite",0L)>0&&bill.getOrDefault("minecraft:sand",0L)>0
            &&bill.keySet().stream().noneMatch(id->id.endsWith(":chip")&&!id.startsWith("@")),"The bill bottoms out in mined, grown and gathered raw materials: "+bill.keySet().stream().filter(id->!id.startsWith("@")).toList());
        // Mass is conserved or lost, never created: no recipe turns out more kilograms of metal than went in.
        for(var entry:Industry.RECIPES.entrySet()){
            var r=entry.getValue();if(r.vehicle())continue;String out=r.output();
            if(!out.endsWith("_ingot")&&!out.endsWith(":pig_iron")&&!out.endsWith("_plate"))continue;
            double in=0;for(var input:r.inputs().entrySet())if(!input.getKey().startsWith("#"))in+=Ledger.mass(input.getKey())*input.getValue();
            h.assertTrue(r.amount()*Ledger.mass(out)<=in+1e-6,entry.getKey()+" must not create mass: "+r.amount()+" kg out of "+in+" kg in");
        }
        // Nothing assembled weighs more than its parts: every vehicle and every heavy component is covered by its bill.
        for(var entry:Industry.RECIPES.entrySet()){
            var r=entry.getValue();double in=0;
            for(var input:r.inputs().entrySet())in+=(input.getKey().equals("#minecraft:logs")?600:input.getKey().equals("#minecraft:planks")?150:Ledger.mass(input.getKey()))*input.getValue();
            if(r.vehicle()){var spec=dev.qube.spawn_9b5fbb9.transport.Vehicles.SPECS.get(r.output().substring(8));h.assertTrue(spec.tare()<=in,entry.getKey()+" weighs "+spec.tare()+" kg unladen but its bill is only "+in+" kg");}
            else if(java.util.Set.of("boiler","steam_engine","dynamo","transformer","combustion_engine","electric_motor","gun_carriage","vehicle_frame","wooden_wheel","cannon_barrel","bombard_barrel","rubber_tyre","explosive_shell","powder_charge","paper_cartridge").contains(r.output().substring(r.output().indexOf(':')+1)))
                h.assertTrue(r.amount()*Ledger.mass(r.output())<=in+1e-6,entry.getKey()+" must not weigh more than its parts: "+r.amount()*Ledger.mass(r.output())+" kg from "+in+" kg");
        }
        h.assertTrue(Industry.RECIPES.values().stream().noneMatch(r->r.inputs().isEmpty()),"No industrial recipe creates goods from nothing");
        // Every named material is a real registered item.
        for(var entry:Industry.RECIPES.entrySet()){
            var names=new java.util.ArrayList<>(entry.getValue().inputs().keySet());names.addAll(entry.getValue().byproducts().keySet());if(!entry.getValue().vehicle())names.add(entry.getValue().output());
            for(String id:names)h.assertTrue(id.startsWith("#")||Ledger.item(id)!=Items.AIR,entry.getKey()+" names an item that does not exist: "+id);
        }
        for(var entry:dev.qube.spawn_9b5fbb9.content.Production.RECIPES.entrySet())for(String id:new String[]{entry.getValue().input(),entry.getValue().output(),entry.getValue().second()})
            h.assertTrue(id.isEmpty()||id.startsWith("@")||Ledger.item(id)!=Items.AIR,entry.getKey()+" names an item that does not exist: "+id);
        h.succeed();
    }
    public static void passiveNitre(GameTestHelper h){
        var actor=worker(h,1,1,null,0);var bed=works(h,"niter_bed",2,1);long now=SurvivalApi.calendar(h.getLevel());
        hand(actor,item("spoiled_food",64));h.assertTrue(Industry.deposit(actor,bed,InteractionHand.MAIN_HAND,false).success()&&bed.recipe.equals("niter_earth"),"A nitre bed adopts its recipe from the first delivery");
        hand(actor,item("wood_ash",16));Industry.deposit(actor,bed,InteractionHand.MAIN_HAND,false);hand(actor,new ItemStack(Items.WHEAT,16));Industry.deposit(actor,bed,InteractionHand.MAIN_HAND,false);
        bed.advance(h.getLevel(),now);h.assertTrue(bed.active&&bed.products.isEmpty(),"The bed is laid, not finished");
        bed.advance(h.getLevel(),now+1000000);h.assertTrue(bed.products.isEmpty(),"Six weeks in, the bed has yielded nothing");
        bed.advance(h.getLevel(),now+2000000);h.assertTrue(bed.products.isEmpty(),"Three seasons are not enough");
        bed.advance(h.getLevel(),now+2688001);
        h.assertTrue(bed.products.getOrDefault("spawn_9b5fbb9:niter_earth",0L)==64&&!bed.active,"A full year of 112 days of unattended ripening yields one batch of nitre earth");
        var leach=Industry.RECIPES.get("saltpeter");h.assertTrue(leach.inputs().get("spawn_9b5fbb9:niter_earth")==64&&leach.amount()==3,"A bed gives about a twentieth of its weight in saltpetre");
        actor.discard();h.succeed();
    }
    public static void powderMillFire(GameTestHelper h){
        floor(h);var actor=worker(h,6,8,Skill.ENGINEERING,4);var mill=works(h,"powder_mill",8,8);var pos=mill.getBlockPos();
        for(int kilograms:new int[]{64,61}){hand(actor,item("saltpeter",kilograms));Industry.deposit(actor,mill,InteractionHand.MAIN_HAND,false);}hand(actor,new ItemStack(Items.CHARCOAL,1));Industry.deposit(actor,mill,InteractionHand.MAIN_HAND,false);hand(actor,item("sulfur",17));Industry.deposit(actor,mill,InteractionHand.MAIN_HAND,false);
        h.assertTrue(mill.recipe.equals("gunpowder")&&Ledger.has(mill.stock,Industry.RECIPES.get("gunpowder").inputs()),"The mill holds 75:15:10 by weight: 125 kg of saltpetre and 17 of sulfur to one 25 kg sack of charcoal");
        var powder=Industry.RECIPES.get("gunpowder");h.assertTrue(Math.abs(powder.amount()-(125+25+17))<=1,"What goes into the mill comes out as powder");
        mill.advance(h.getLevel(),SurvivalApi.calendar(h.getLevel()));
        h.assertTrue(h.getLevel().getBlockState(pos).is(IndustrialContent.WORKS.get("powder_mill").get()),"Milling away from fire is safe");
        mill.active=false;mill.stock.put("spawn_9b5fbb9:saltpeter",125L);mill.stock.put("minecraft:charcoal",1L);mill.stock.put("spawn_9b5fbb9:sulfur",17L);
        h.setBlock(10,2,8,Blocks.CAMPFIRE);
        mill.advance(h.getLevel(),SurvivalApi.calendar(h.getLevel())+20);
        h.assertTrue(!h.getLevel().getBlockState(pos).is(IndustrialContent.WORKS.get("powder_mill").get()),"A naked flame beside a working powder mill destroys it");
        h.succeed();
    }
    public static void yardRollsOutCart(GameTestHelper h){
        floor(h);var wright=worker(h,7,8,Skill.CONSTRUCTION,6);var yard=works(h,"assembly_yard",8,8);
        h.assertTrue(Industry.select(wright,yard,"cart").success(),"A yard builds carts");
        hand(wright,item("vehicle_frame",1));Industry.deposit(wright,yard,InteractionHand.MAIN_HAND,false);hand(wright,item("wooden_wheel",2));Industry.deposit(wright,yard,InteractionHand.MAIN_HAND,false);hand(wright,item("harness",1));Industry.deposit(wright,yard,InteractionHand.MAIN_HAND,false);
        h.assertTrue(Industry.operate(wright,yard).success(),"A construction-6 wright may assemble it");
        yard.advance(h.getLevel(),SurvivalApi.calendar(h.getLevel()));h.assertTrue(yard.active,"The frame, wheels and harness are committed");
        yard.progress=Industry.ticks(Industry.RECIPES.get("cart"))-6;
        h.runAfterDelay(60,()->{
            var carts=h.getLevel().getEntitiesOfClass(VehicleEntity.class,new net.minecraft.world.phys.AABB(yard.getBlockPos()).inflate(7),v->v.kind().equals("cart"));
            h.assertTrue(carts.size()==1&&yard.products.isEmpty(),"One bill of materials yields exactly one cart, as an entity on clear ground beside the yard");
            carts.forEach(VehicleEntity::discard);wright.discard();h.succeed();
        });
    }
    public static void demolitionSpillsStock(GameTestHelper h){
        floor(h);var actor=worker(h,7,8,null,0);var works=works(h,"workshop",8,8);var pos=works.getBlockPos();
        Industry.select(actor,works,"sleeper");hand(actor,new ItemStack(Items.OAK_LOG,5));Industry.deposit(actor,works,InteractionHand.MAIN_HAND,false);
        h.getLevel().destroyBlock(pos,false);
        int logs=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(3),e->e.getItem().is(Items.OAK_LOG)).stream().mapToInt(e->e.getItem().getCount()).sum();
        h.assertTrue(logs==5,"Delivered stock is real material and spills when the works is demolished: "+logs);
        actor.discard();h.succeed();
    }
    /** A works holding more than would survive its fall is not pulled down until it is emptied. */
    public static void fullWorksIsNotPulledDown(GameTestHelper h){
        var works=works(h,"workshop",2,1);var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        works.stock.put("minecraft:oak_planks",54L*64+1);
        var refused=new net.neoforged.neoforge.event.level.block.BreakBlockEvent(h.getLevel(),works.getBlockPos(),works.getBlockState(),player);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(refused);
        h.assertTrue(refused.isCanceled(),"More than fifty-four stacks of one thing cannot spill, so the works stands");
        works.stock.put("minecraft:oak_planks",64L);
        var allowed=new net.neoforged.neoforge.event.level.block.BreakBlockEvent(h.getLevel(),works.getBlockPos(),works.getBlockState(),player);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(allowed);
        h.assertTrue(!allowed.isCanceled(),"A works with a little stock comes down and spills it");
        h.succeed();
    }
}
