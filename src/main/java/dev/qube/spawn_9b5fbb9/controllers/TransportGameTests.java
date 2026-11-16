package dev.qube.spawn_9b5fbb9.controllers;

import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.industry.*;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import dev.qube.spawn_9b5fbb9.transport.*;
import dev.qube.spawn_9b5fbb9.transport.RailTrackBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Lives beside WorldActions so track laying can be driven through the real timed job. */
public final class TransportGameTests {
    private TransportGameTests(){}
    private static Vec3 at(GameTestHelper h,double x,double y,double z){return h.absoluteVec(new Vec3(x,y,z));}
    public static void cargoConserved(GameTestHelper h){
        IndustrialGameTests.floor(h);var cart=Vehicles.spawn(h.getLevel(),"cart",at(h,8.5,2,8.5),0);
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(at(h,6.5,2,8.5));
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT,64));
        h.assertTrue(Vehicles.load(player,cart,InteractionHand.MAIN_HAND,false).success()&&player.getMainHandItem().isEmpty()&&cart.cargo.get("minecraft:wheat")==64,"Loading moves the real stack onto the cart");
        for(int i=0;i<8;i++){player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT,64));Vehicles.load(player,cart,InteractionHand.MAIN_HAND,false);}
        h.assertTrue(Ledger.total(cart.cargo)==576,"A cart carries nine stacks");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT,64));
        h.assertTrue(!Vehicles.load(player,cart,InteractionHand.MAIN_HAND,false).success()&&player.getMainHandItem().getCount()==64&&Ledger.total(cart.cargo)==576,"A full cart refuses more and the goods stay in hand");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));player.getMainHandItem().setDamageValue(5);
        h.assertTrue(!Vehicles.load(player,cart,InteractionHand.MAIN_HAND,false).success(),"Worn equipment is not bulk cargo");
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        h.assertTrue(Vehicles.unload(player,cart,"minecraft:wheat",100).success(),"Cargo unloads by name");
        int carried=0;for(int slot=0;slot<player.getInventory().getContainerSize();slot++)if(player.getInventory().getItem(slot).is(Items.WHEAT))carried+=player.getInventory().getItem(slot).getCount();
        h.assertTrue(carried==100&&cart.cargo.get("minecraft:wheat")==476,"Unloading conserves every unit: "+carried);
        cart.discard();h.succeed();
    }
    public static void teamRequired(GameTestHelper h){
        IndustrialGameTests.floor(h);var cart=Vehicles.spawn(h.getLevel(),"cart",at(h,3.5,2,8.5),-90);
        var driver=IndustrialGameTests.worker(h,3,6,Skill.RIDING,0);Vec3 destination=at(h,11.5,2,8.5);
        h.assertTrue(Vehicles.drive(driver,cart,destination).message().contains("team"),"A cart without a draught animal goes nowhere");
        var horse=h.spawn(EntityTypes.HORSE,new BlockPos(5,2,6));
        h.assertTrue(!Vehicles.hitch(driver,cart,horse).success(),"A wild horse will not take harness");
        horse.setAge(0);horse.setTamed(true);
        h.assertTrue(Vehicles.hitch(driver,cart,horse).success()&&cart.team.size()==1&&horse.isNoAi(),"A tamed adult horse is harnessed and stands in the shafts");
        h.assertTrue(!Vehicles.hitch(driver,cart,horse).success(),"The same animal cannot be harnessed twice");
        h.assertTrue(Vehicles.drive(driver,cart,destination).success()&&driver.getVehicle()==cart,"With a team the driver takes the reins");
        double start=cart.getX();
        h.runAfterDelay(100,()->{
            h.assertTrue(cart.getX()-start>4,"The team draws the cart towards its destination: moved "+(cart.getX()-start));
            h.assertTrue(horse.getX()>cart.getX()+1.5&&Math.abs(horse.getZ()-cart.getZ())<.5,"The horse stays in harness ahead of the cart");
            h.assertTrue(Vehicles.unhitch(driver,cart).success()&&cart.team.isEmpty()&&!horse.isNoAi()&&!horse.getPersistentData().contains("survivalHitched"),"Unhitching frees the animal");
            cart.discard();horse.discard();driver.discard();h.succeed();
        });
    }
    private static int count(net.minecraft.world.entity.player.Player player,String id){
        int sum=0;for(int slot=0;slot<player.getInventory().getContainerSize();slot++){ItemStack stack=player.getInventory().getItem(slot);if(!stack.isEmpty()&&Ledger.id(stack).equals(id))sum+=stack.getCount();}return sum;
    }
    public static void trackLaying(GameTestHelper h){
        IndustrialGameTests.floor(h);var level=h.getLevel();var player=h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos centre=h.absolutePos(new BlockPos(8,2,8));player.setPos(Vec3.atBottomCenterOf(centre.north(2)));player.setYRot(0);
        h.assertTrue(WorldActions.start(player,"lay_track",centre).message().contains("short by"),"Track cannot be laid without rails, sleepers, ballast and spikes");
        player.getInventory().add(IndustrialGameTests.item("steel_rail",4));player.getInventory().add(IndustrialGameTests.item("sleeper",4));player.getInventory().add(new ItemStack(Items.GRAVEL,4));player.getInventory().add(IndustrialGameTests.item("metal_rivet",8));
        level.setBlock(centre.east(),Blocks.COBBLESTONE.defaultBlockState(),3);
        h.assertTrue(WorldActions.start(player,"lay_track",centre).message().contains("three blocks wide"),"A line needs its full three-block bed");
        level.setBlock(centre.east(),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(WorldActions.start(player,"lay_track",centre).success(),"With materials and a clear firm bed the job starts");
        for(int i=0;i<300;i++){player.tickCount++;WorldActions.tick(player);}
        h.assertTrue(level.getBlockState(centre).isAir()&&count(player,"spawn_9b5fbb9:steel_rail")==4,"Track is labour: nothing is placed or consumed part-way through");
        for(int i=0;i<600&&WorldActions.inspectJob(player).get("active").getAsBoolean();i++){player.tickCount++;WorldActions.tick(player);}
        var track=IndustrialContent.RAIL_TRACK.get();var laid=level.getBlockState(centre);
        h.assertTrue(laid.is(track)&&laid.getValue(RailTrackBlock.PART)==Part.CENTER&&laid.getValue(RailTrackBlock.AXIS)==Direction.Axis.Z,"The centre block follows the layer's facing");
        h.assertTrue(level.getBlockState(centre.west()).is(track)&&level.getBlockState(centre.west()).getValue(RailTrackBlock.PART)==Part.NEG&&level.getBlockState(centre.east()).is(track)&&level.getBlockState(centre.east()).getValue(RailTrackBlock.PART)==Part.POS,"One metre of line occupies three blocks across");
        h.assertTrue(count(player,"spawn_9b5fbb9:steel_rail")==2&&count(player,"spawn_9b5fbb9:sleeper")==2&&count(player,"minecraft:gravel")==2&&count(player,"spawn_9b5fbb9:metal_rivet")==4,"Exactly one metre of materials is consumed");
        h.assertTrue(SurvivalApi.state(player).skills.experience(Skill.CONSTRUCTION)>0,"Laying track is construction practice");
        level.removeBlock(centre,false);
        h.assertTrue(level.getBlockState(centre.west()).isAir()&&level.getBlockState(centre.east()).isAir(),"Shoulders do not stand without their centre");
        h.succeed();
    }
    private static void line(GameTestHelper h,int fromX,int toX,int z){
        var track=IndustrialContent.RAIL_TRACK.get().defaultBlockState().setValue(RailTrackBlock.AXIS,Direction.Axis.X);
        for(int x=fromX;x<=toX;x++){h.setBlock(x,2,z,track.setValue(RailTrackBlock.PART,Part.CENTER));h.setBlock(x,2,z-1,track.setValue(RailTrackBlock.PART,Part.NEG));h.setBlock(x,2,z+1,track.setValue(RailTrackBlock.PART,Part.POS));}
    }
    public static void trainRuns(GameTestHelper h){
        IndustrialGameTests.floor(h);line(h,1,14,8);var level=h.getLevel();
        h.assertTrue(Vehicles.spawn(level,"locomotive",at(h,6.5,2,3.5),-90)==null,"Rolling stock cannot be set down off the rails");
        var engine=Vehicles.spawn(level,"locomotive",at(h,6.5,2,8.5),-90);var wagon=Vehicles.spawn(level,"rail_wagon",at(h,2.5,2,8.5),-90);
        h.assertTrue(engine!=null&&wagon!=null&&engine.facing==Direction.EAST,"An engine set on track faces along it");
        var driver=IndustrialGameTests.worker(h,6,6,Skill.RIDING,0);Vec3 destination=at(h,13.5,2,8.5);
        h.assertTrue(Vehicles.drive(driver,engine,destination).message().contains("coal"),"A cold engine does not move");
        Ledger.add(engine.cargo,"minecraft:coal",4);
        h.assertTrue(Vehicles.drive(driver,engine,destination).message().contains("boiler"),"Nor one with a dry boiler");
        engine.water=12000;Ledger.add(wagon.cargo,"minecraft:iron_ingot",640);
        h.assertTrue(Railway.couple(driver,engine).success()&&engine.cars.size()==1&&wagon.coupledTo.equals(engine.getUUID().toString()),"The wagon couples behind the engine");
        h.assertTrue(Vehicles.drive(driver,engine,destination).success(),"Coaled, watered and crewed, the train starts");
        double start=engine.getX();
        h.runAfterDelay(160,()->{
            h.assertTrue(engine.getX()-start>3,"The engine runs along the line: moved "+(engine.getX()-start));
            double gap=engine.getX()-wagon.getX();
            h.assertTrue(Math.abs(gap-Railway.SPACING)<.3&&Math.abs(wagon.getZ()-engine.getZ())<.01,"The coupled wagon follows at coupling distance on the same rails: gap "+gap);
            h.assertTrue(Ledger.count(engine.cargo,"minecraft:coal")<4&&engine.water<12000,"Running burns coal and boils water");
            h.assertTrue(Ledger.count(wagon.cargo,"minecraft:iron_ingot")==640,"Freight arrives intact");
            h.assertTrue(Railway.uncouple(driver,engine).success()&&engine.cars.isEmpty()&&wagon.coupledTo.isEmpty(),"The wagon can be dropped");
            engine.discard();wagon.discard();driver.discard();h.succeed();
        });
    }
    public static void wagonSuppliesWorks(GameTestHelper h){
        IndustrialGameTests.floor(h);var wagon=Vehicles.spawn(h.getLevel(),"wagon",at(h,5.5,2,8.5),0);
        var carter=IndustrialGameTests.worker(h,6,6,null,0);var shop=IndustrialGameTests.works(h,"workshop",8,8);
        Industry.select(carter,shop,"sleeper");Ledger.add(wagon.cargo,"minecraft:oak_log",300);Ledger.add(wagon.cargo,"minecraft:wheat",50);
        h.assertTrue(Vehicles.deliver(carter,wagon).success(),"A wagon drawn up alongside delivers straight to the works");
        h.assertTrue(shop.stock.getOrDefault("minecraft:oak_log",0L)==300&&!wagon.cargo.containsKey("minecraft:oak_log")&&wagon.cargo.get("minecraft:wheat")==50,"Only what the works' recipe uses crosses over; the rest stays aboard");
        Ledger.add(shop.products,"spawn_9b5fbb9:sleeper",600);
        h.assertTrue(Vehicles.pickup(carter,wagon).success()&&wagon.cargo.get("spawn_9b5fbb9:sleeper")==600&&shop.products.isEmpty(),"Finished goods load in bulk for onward transport");
        wagon.discard();carter.discard();h.succeed();
    }
}
