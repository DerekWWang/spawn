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
        TESTS.register("transport_cargo_conserved",()->TransportGameTests::cargoConserved);
        TESTS.register("transport_team_required",()->TransportGameTests::teamRequired);
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
    }
    public static void register(IEventBus bus){TESTS.register(bus);bus.addListener(IndustrialGameTests::tests);}
    private static void tests(RegisterGameTestsEvent event){
        Holder<TestEnvironmentDefinition<?>> env=event.registerEnvironment(Identifier.fromNamespaceAndPath(SurvivalContent.MODID,"industry_and_war"),new TestEnvironmentDefinition.AllOf(List.of()));
        var data=new TestData<Holder<TestEnvironmentDefinition<?>>>(env,net.minecraft.world.level.Level.OVERWORLD,Identifier.fromNamespaceAndPath(SurvivalContent.MODID,"test_arena"),400,0,true,Rotation.NONE,false,1,1,true,8);
        TESTS.getEntries().forEach(test->event.registerTest(test.getId(),new FunctionGameTestInstance(test.getKey(),data)));
    }

    // ---- Shared fixtures ------------------------------------------------------------------------------
    public static void floor(GameTestHelper h){for(int x=0;x<16;x++)for(int z=0;z<16;z++)h.setBlock(x,1,z,Blocks.STONE);}
    public static LivingEntity worker(GameTestHelper h,int x,int z,Skill skill,int level){
        h.setBlock(x,1,z,Blocks.STONE);var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(x,2,z));actor.setNoAi(true);SurvivalApi.enroll(actor);
        if(skill!=null){var body=SurvivalApi.state(actor);body.skills.setLevel(skill,level);SurvivalApi.save(actor,body);}
        return actor;
    }
    public static WorksEntity works(GameTestHelper h,String station,int x,int z){
        h.setBlock(x,1,z,Blocks.STONE);var pos=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(pos,IndustrialContent.WORKS.get(station).get().defaultBlockState(),3);
        return (WorksEntity)h.getLevel().getBlockEntity(pos);
    }
    public static ItemStack item(String id,int count){return new ItemStack(SurvivalContent.item(id),count);}
    private static void hand(LivingEntity actor,ItemStack stack){actor.setItemInHand(InteractionHand.MAIN_HAND,stack);}

    // ---- Industry -------------------------------------------------------------------------------------
    public static void batchNeedsFullBill(GameTestHelper h){
        var actor=worker(h,1,1,Skill.CONSTRUCTION,4);var works=works(h,"workshop",2,1);long now=SurvivalApi.calendar(h.getLevel());
        h.assertTrue(Industry.select(actor,works,"wooden_wheel").success(),"A workshop must accept its own recipe");
        h.assertFalse(Industry.select(actor,works,"steel").success(),"A workshop cannot be set to a foundry recipe");
        hand(actor,new ItemStack(Items.COBBLESTONE,8));h.assertTrue(!Industry.deposit(actor,works,InteractionHand.MAIN_HAND,false).success()&&actor.getMainHandItem().getCount()==8,"Materials the recipe does not use are refused and stay in hand");
        hand(actor,new ItemStack(Items.BIRCH_PLANKS,6));h.assertTrue(Industry.deposit(actor,works,InteractionHand.MAIN_HAND,false).success()&&actor.getMainHandItem().isEmpty(),"Any plank species satisfies the plank bill and leaves the hand");
        hand(actor,item("metal_rivet",3));h.assertTrue(Industry.deposit(actor,works,InteractionHand.MAIN_HAND,false).success(),"Rivets are part of a wheel");
        h.assertTrue(Industry.operate(actor,works).success(),"A construction-4 worker may take the job");
        works.advance(h.getLevel(),now);
        h.assertTrue(!works.active&&works.products.isEmpty()&&Ledger.count(works.stock,"#minecraft:planks")==6,"A batch cannot begin one rivet short, and nothing is consumed");
        hand(actor,item("metal_rivet",1));Industry.deposit(actor,works,InteractionHand.MAIN_HAND,false);
        works.advance(h.getLevel(),now+20);
        h.assertTrue(works.active&&works.stock.isEmpty()&&works.products.isEmpty(),"The full bill is consumed when the batch begins, and nothing is finished yet");
        works.progress=Industry.RECIPES.get("wooden_wheel").ticks()-6;
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
        works.stock.put("spawn_9b5fbb9:musket_barrel",1L);works.stock.put("spawn_9b5fbb9:musket_lock",1L);works.stock.put("spawn_9b5fbb9:musket_stock",1L);works.stock.put("spawn_9b5fbb9:metal_rivet",2L);
        works.workers.add(novice.getUUID().toString());novice.getPersistentData().putLong("survivalWorksPos",works.getBlockPos().asLong());
        long now=SurvivalApi.calendar(h.getLevel());works.advance(h.getLevel(),now);works.advance(h.getLevel(),now+20);
        h.assertTrue(works.active&&works.progress==0&&works.products.isEmpty(),"An unqualified hand contributes no work even if listed");
        novice.discard();h.succeed();
    }
    public static void electronicsBill(GameTestHelper h){
        var server=Industry.RECIPES.get("server");var computer=Industry.RECIPES.get("computer");
        h.assertTrue(server.inputs().get("spawn_9b5fbb9:chip")==4800&&computer.inputs().get("spawn_9b5fbb9:chip")==1200,"Computers and servers need thousands of chips");
        var bill=Industry.expand("spawn_9b5fbb9:server",1);
        long fine=bill.getOrDefault("@spawn_9b5fbb9:fine_wire",0L),wire=bill.getOrDefault("@spawn_9b5fbb9:copper_wire",0L);
        h.assertTrue(fine>=192000&&fine+wire>=250000,"A server's chips alone need hundreds of thousands of bond wires: "+fine+" fine, "+wire+" copper");
        h.assertTrue(bill.getOrDefault("@minecraft:copper_ingot",0L)>=5000&&bill.getOrDefault("minecraft:sand",0L)>0&&bill.getOrDefault("minecraft:clay_ball",0L)>0&&bill.keySet().stream().noneMatch(id->id.endsWith(":chip")&&!id.startsWith("@")),"The bill bottoms out in mined, grown and gathered raw materials: "+bill.keySet().stream().filter(id->!id.startsWith("@")).toList());
        h.assertTrue(Industry.RECIPES.values().stream().noneMatch(r->r.inputs().isEmpty()),"No industrial recipe creates goods from nothing");
        h.succeed();
    }
    public static void passiveNitre(GameTestHelper h){
        var actor=worker(h,1,1,null,0);var bed=works(h,"niter_bed",2,1);long now=SurvivalApi.calendar(h.getLevel());
        hand(actor,item("spoiled_food",8));h.assertTrue(Industry.deposit(actor,bed,InteractionHand.MAIN_HAND,false).success()&&bed.recipe.equals("niter_earth"),"A nitre bed adopts its recipe from the first delivery");
        hand(actor,item("wood_ash",2));Industry.deposit(actor,bed,InteractionHand.MAIN_HAND,false);hand(actor,new ItemStack(Items.WHEAT,2));Industry.deposit(actor,bed,InteractionHand.MAIN_HAND,false);
        bed.advance(h.getLevel(),now);h.assertTrue(bed.active&&bed.products.isEmpty(),"The bed is laid, not finished");
        bed.advance(h.getLevel(),now+60000);h.assertTrue(bed.products.isEmpty(),"Half the maturing time yields nothing");
        bed.advance(h.getLevel(),now+120001);
        h.assertTrue(bed.products.getOrDefault("spawn_9b5fbb9:niter_earth",0L)==4&&!bed.active,"Five days of unattended maturing yield one batch of nitre earth");
        actor.discard();h.succeed();
    }
    public static void powderMillFire(GameTestHelper h){
        floor(h);var actor=worker(h,6,8,Skill.ENGINEERING,4);var mill=works(h,"powder_mill",8,8);var pos=mill.getBlockPos();
        hand(actor,item("saltpeter",15));Industry.deposit(actor,mill,InteractionHand.MAIN_HAND,false);hand(actor,new ItemStack(Items.CHARCOAL,3));Industry.deposit(actor,mill,InteractionHand.MAIN_HAND,false);hand(actor,item("sulfur",2));Industry.deposit(actor,mill,InteractionHand.MAIN_HAND,false);
        h.assertTrue(mill.recipe.equals("gunpowder")&&Ledger.has(mill.stock,Industry.RECIPES.get("gunpowder").inputs()),"The mill holds the historical 15:3:2 charge");
        mill.advance(h.getLevel(),SurvivalApi.calendar(h.getLevel()));
        h.assertTrue(h.getLevel().getBlockState(pos).is(IndustrialContent.WORKS.get("powder_mill").get()),"Milling away from fire is safe");
        mill.active=false;mill.stock.put("spawn_9b5fbb9:saltpeter",15L);mill.stock.put("minecraft:charcoal",3L);mill.stock.put("spawn_9b5fbb9:sulfur",2L);
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
        yard.progress=Industry.RECIPES.get("cart").ticks()-6;
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
}
