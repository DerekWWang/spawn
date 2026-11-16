package dev.qube.spawn_9b5fbb9.controllers;

import dev.qube.spawn_9b5fbb9.content.*;
import dev.qube.spawn_9b5fbb9.survival.*;
import dev.qube.spawn_9b5fbb9.world.NaturalResourceGeneration;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.*;
import java.lang.reflect.Proxy;
import java.util.*;

public final class ReviewFixGameTests {
    private static void work(net.minecraft.world.entity.LivingEntity actor){for(int i=0;i<1000&&WorldActions.inspectJob(actor).get("active").getAsBoolean();i++){actor.tickCount++;WorldActions.tick(actor);}}
    public static void flaxBootstrap(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);var pos=h.absolutePos(new BlockPos(2,2,1));
        h.getLevel().setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);h.getLevel().setBlock(pos,SurvivalContent.FORAGE.get("wild_flax").get().defaultBlockState(),3);
        h.assertTrue(WorldActions.forage(actor,pos).success(),"A real flax stand can be gathered without tools");work(actor);
        for(int i=0;i<2;i++){h.assertTrue(WorldActions.craft(actor,"spawn_9b5fbb9:prepare_flax_fiber").success(),"Fresh flax needs real hand processing before any loom or cordage exists");work(actor);}
        h.assertTrue(WorldActions.craft(actor,"spawn_9b5fbb9:twist_cordage").success(),"Hand-processed flax must enter the existing cordage recipe");work(actor);
        int cordage=h.getLevel().getEntitiesOfClass(ItemEntity.class,actor.getBoundingBox().inflate(4),e->e.getItem().is(SurvivalContent.item("cordage"))).stream().mapToInt(e->e.getItem().getCount()).sum();
        h.assertTrue(cordage==2&&SurvivalApi.state(actor).skills.experience(SkillSet.Skill.WEAVING)>0,"One natural flax source supplies two real cordage after actual work");actor.discard();h.succeed();
    }
    public static void nativeKiln(GameTestHelper h){
        var actor=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var pos=h.absolutePos(new BlockPos(2,2,1));actor.setPos(Vec3.atBottomCenterOf(pos.west()));h.getLevel().setBlock(pos,SurvivalContent.KILN.get().defaultBlockState(),3);
        var click=new PlayerInteractEvent.LeftClickBlock(actor,pos,Direction.UP,PlayerInteractEvent.LeftClickBlock.Action.START);NeoForge.EVENT_BUS.post(click);
        var speed=new PlayerEvent.BreakSpeed(actor,h.getLevel().getBlockState(pos),1,pos);NeoForge.EVENT_BUS.post(speed);
        h.assertTrue(!click.isCanceled()&&speed.getNewSpeed()>0&&!WorldActions.inspectJob(actor).get("active").getAsBoolean(),"An empty kiln must remain removable through ordinary native mining");h.succeed();
    }
    public static void gridParity(GameTestHelper h){
        for(int skill:new int[]{0,20}){
            var controller=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);controller.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1,2,1))));controller.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.OAK_LOG));var a=SurvivalApi.state(controller);a.skills.setLevel(SkillSet.Skill.CONSTRUCTION,skill);SurvivalApi.save(controller,a);
            h.assertTrue(WorldActions.craft(controller,"minecraft:oak_planks").success(),"Controller reserves a real log");for(int i=0;i<20;i++){controller.tickCount++;WorldActions.tick(controller);}
            var nativeActor=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);nativeActor.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1,2,4))));var b=SurvivalApi.state(nativeActor);b.skills.setLevel(SkillSet.Skill.CONSTRUCTION,skill);SurvivalApi.save(nativeActor,b);
            var menu=new net.minecraft.world.inventory.CraftingMenu(1,nativeActor.getInventory());nativeActor.containerMenu=menu;menu.getInputGridSlots().getFirst().set(new ItemStack(Items.OAK_LOG));menu.getResultSlot().set(new ItemStack(Items.OAK_PLANKS,4));CraftingGuard.tick(nativeActor,b);
            double nativeWork=nativeActor.getPersistentData().getDoubleOr("survivalGridWork",0),controllerWork=controller.getPersistentData().getCompoundOrEmpty("survivalWorldJob").getDoubleOr("progress",0),expected=skill==0?10:20;
            h.assertTrue(Math.abs(nativeWork-expected)<1e-6&&Math.abs(controllerWork-expected)<1e-6,"Equal native/controller work exposure must use the same competence-dependent speed");
            h.assertTrue(nativeActor.getPersistentData().getLongOr("survivalGridExposure",0)==20,"Native learning exposure counts active ticks");WorldActions.cancel(controller,"Test cleanup");
        }
        h.assertTrue(SkillApi.craftingSkill(new ItemStack(Items.COAL_BLOCK))==null,"Reversible block compression must not train construction");h.succeed();
    }
    public static void repeatedExtraction(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("knapped_stone_pickaxe")));var pos=h.absolutePos(new BlockPos(2,2,1));
        h.getLevel().setBlock(pos,Blocks.GRANITE.defaultBlockState(),3);h.assertTrue(WorldActions.start(actor,"mine",pos).success(),"Natural quarry stone can be extracted");work(actor);double earned=SurvivalApi.state(actor).skills.experience(SkillSet.Skill.MINING);h.assertTrue(earned>0,"First actual natural extraction teaches mining");
        for(int i=0;i<2;i++){
            var prior=h.getLevel().getEntitiesOfClass(ItemEntity.class,actor.getBoundingBox().inflate(4),e->e.getItem().is(Items.GRANITE)).getFirst();prior.getItem().shrink(1);if(prior.getItem().isEmpty())prior.discard();
            h.getLevel().setBlock(pos,Blocks.GRANITE.defaultBlockState(),3);var body=SurvivalState.decode(SurvivalApi.state(actor).encode());SurvivalApi.save(actor,body);
            h.assertTrue(WorldActions.start(actor,"mine",pos).success(),"Placed stone remains physically mineable");work(actor);
            h.assertTrue(SurvivalApi.state(actor).skills.experience(SkillSet.Skill.MINING)==earned,"Replacing and re-mining recovered stone cannot renew extraction practice, including after actor reload");
        }
        var fresh=pos.south();var snapshot=net.neoforged.neoforge.common.util.BlockSnapshot.create(h.getLevel().dimension(),h.getLevel(),fresh,3);h.getLevel().setBlock(fresh,Blocks.GRANITE.defaultBlockState(),3);
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent(snapshot,Blocks.DIRT.defaultBlockState(),actor));
        var storage=h.getLevel().getServer().getCommandStorage();var saved=storage.get(dev.qube.spawn_9b5fbb9.world.ExtractionHistory.KEY).copy();storage.set(dev.qube.spawn_9b5fbb9.world.ExtractionHistory.KEY,new net.minecraft.nbt.CompoundTag());storage.set(dev.qube.spawn_9b5fbb9.world.ExtractionHistory.KEY,saved);
        h.assertFalse(dev.qube.spawn_9b5fbb9.world.ExtractionHistory.claim(h.getLevel(),fresh,Blocks.GRANITE.defaultBlockState()),"A real placement event excludes a fresh position, and serialized world exclusions survive reload");
        actor.discard();h.succeed();
    }
    public static void twoActorMineralChain(GameTestHelper h){
        var miner=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));var processor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,4));miner.setNoAi(true);processor.setNoAi(true);SurvivalApi.enroll(miner);SurvivalApi.enroll(processor);miner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("knapped_stone_pickaxe")));var pos=h.absolutePos(new BlockPos(2,2,1));
        h.getLevel().setBlock(pos,SurvivalContent.MALACHITE_ORE.get().defaultBlockState(),3);h.assertTrue(WorldActions.start(miner,"mine",pos).success(),"A supplied miner extracts actual malachite ore");work(miner);
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(3),e->e.getItem().is(SurvivalContent.item("malachite")));h.assertTrue(drops.stream().mapToInt(e->e.getItem().getCount()).sum()==420,"A cubic metre of copper lode breaks down to 420 kg of sorted malachite: "+drops.stream().mapToInt(e->e.getItem().getCount()).sum()+" in "+drops.size()+" heaps");processor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("malachite"),48));drops.forEach(ItemEntity::discard);
        var at=h.absolutePos(new BlockPos(2,2,4));h.getLevel().setBlock(at,SurvivalContent.ANVIL.get().defaultBlockState(),3);var station=(WorkstationEntity)h.getLevel().getBlockEntity(at);Production.reloadDefinitions(com.google.gson.JsonParser.parseString("{\"crush_malachite\":{\"ticks\":20}}").getAsJsonObject());h.assertTrue(Production.start(processor,station,"crush_malachite").startsWith("Started"),"Another actor reserves the transferred mineral in a real processing station");
        h.runAfterDelay(80,()->{try{h.assertTrue(station.output.is(SurvivalContent.item("crushed_copper"))&&station.output.getCount()==48&&processor.getMainHandItem().isEmpty(),"The processor consumes exactly the transferred 48 kg for 48 kg of crushed ore");
        Production.collect(processor,station);h.assertTrue(station.output.isEmpty()&&SurvivalApi.state(miner).skills.experience(SkillSet.Skill.MINING)>0&&SurvivalApi.state(processor).skills.experience(SkillSet.Skill.MINING)>0,"Collection and learning belong to the two actual workers");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,processor.getBoundingBox().inflate(4),e->e.getItem().is(SurvivalContent.item("crushed_copper"))).stream().mapToInt(e->e.getItem().getCount()).sum()==48,"Collaboration conserves physical material through extraction, transfer, processing and collection");miner.discard();processor.discard();h.succeed();}finally{Production.reloadDefinitions(new com.google.gson.JsonObject());}});
    }
    private static WorldGenLevel testWorld(GameTestHelper h,net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> biome){
        var level=h.getLevel();var holder=level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biome);
        return (WorldGenLevel)Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),new Class[]{WorldGenLevel.class},(proxy,method,args)->method.getName().equals("getSeed")?1L:method.getName().equals("getBiome")?holder:method.invoke(level,args));
    }
    private static void pad(GameTestHelper h){for(int x=-448;x<=-417;x++)for(int z=320;z<336;z++){h.getLevel().setBlock(new BlockPos(x,63,z),Blocks.DIRT.defaultBlockState(),2);for(int y=64;y<=72;y++)h.getLevel().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}}
    private static List<String> snapshot(GameTestHelper h){var values=new ArrayList<String>();for(int x=-448;x<=-417;x++)for(int z=320;z<336;z++)for(int y=64;y<=70;y++)values.add(h.getLevel().getBlockState(new BlockPos(x,y,z)).toString());return values;}
    public static void decorationOrder(GameTestHelper h){
        var world=testWorld(h,Biomes.FOREST);var feature=h.getLevel().registryAccess().lookupOrThrow(Registries.FEATURE).getOrThrow(ResourceKey.create(Registries.FEATURE,Identifier.fromNamespaceAndPath(SurvivalContent.MODID,"natural_surface"))).value();var generator=h.getLevel().getChunkSource().getGenerator();
        pad(h);for(int x:new int[]{-448,-432})feature.place(world,generator,net.minecraft.util.RandomSource.create(0),new BlockPos(x,64,320));var forward=snapshot(h);
        pad(h);for(int x:new int[]{-432,-448})feature.place(world,generator,net.minecraft.util.RandomSource.create(0),new BlockPos(x,64,320));h.assertTrue(forward.equals(snapshot(h)),"Actual neighboring surface adapters must produce the same blocks in both decoration orders");h.succeed();
    }
    public static void coveredHabitat(GameTestHelper h){
        var open=h.absolutePos(new BlockPos(5,2,1));h.getLevel().setBlock(open.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);
        h.assertTrue(NaturalResourceGeneration.habitat(testWorld(h,Biomes.PLAINS),open,dev.qube.spawn_9b5fbb9.world.ResourceFields.Plant.FIBER),"Natural grassland must support an exposed flax stand");
        var pos=h.absolutePos(new BlockPos(2,2,1));h.getLevel().setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);h.getLevel().setBlock(pos.above(2),Blocks.STONE.defaultBlockState(),3);
        h.assertFalse(NaturalResourceGeneration.habitat(testWorld(h,Biomes.PLAINS),pos,dev.qube.spawn_9b5fbb9.world.ResourceFields.Plant.FIBER),"An open-habitat fiber plant cannot establish under a solid roof");h.succeed();
    }
    public static void fullAnimal(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);var cow=h.spawn(EntityTypes.COW,new BlockPos(2,2,1));cow.setNoAi(true);cow.getPersistentData().putLong("husbandryBorn",-10000000);cow.getPersistentData().putDouble("nutrition",100);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT));
        dev.qube.spawn_9b5fbb9.agriculture.Husbandry.action(actor,cow);h.assertTrue(SurvivalApi.state(actor).skills.experience(SkillSet.Skill.ANIMALS)==0,"Consuming the last food on a fully nourished animal is not useful animal-handling practice");actor.discard();cow.discard();h.succeed();
    }
}
