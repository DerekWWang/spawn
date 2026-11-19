package dev.qube.spawn_9b5fbb9.controllers;

import dev.qube.spawn_9b5fbb9.content.*;
import dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops;
import dev.qube.spawn_9b5fbb9.environment.*;
import dev.qube.spawn_9b5fbb9.survival.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public final class IrrigationGameTests {
    public static void winterHarvest(GameTestHelper h){
        var level=h.getLevel();var old=WorldEnvironment.replaceForTest(level,new EnvironmentModel.Settings("vanilla",0,28,2));var store=level.getServer().getCommandStorage();var calendar=store.get(SurvivalApi.CALENDAR).copy();var actor=h.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);var pos=h.absolutePos(new BlockPos(2,2,1));
        try{
            var clock=calendar.copy();clock.putLong("sleepOffset",84*24000L-level.getServer().overworld().getLevelData().getGameTime());store.set(SurvivalApi.CALENDAR,clock);
            level.setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE,7),3);level.setBlock(pos,SurvivalContent.CROPS.get("maize").get().defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE,7),3);
            var all=SeasonalCrops.records(level);var data=new net.minecraft.nbt.CompoundTag();data.putLong("growth",10*24000L);data.putLong("last",SurvivalApi.calendar(level));all.put(Long.toString(pos.asLong()),data);
            h.assertTrue(WorldActions.start(actor,"harvest",pos).success(),"A mature crop in the regional profile can be harvested in calendar winter");for(int i=0;i<240&&WorldActions.inspectJob(actor).get("active").getAsBoolean();i++){actor.tickCount++;WorldActions.tick(actor);}
            int maize=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,actor.getBoundingBox().inflate(4)).stream().filter(e->e.getItem().is(SurvivalContent.item("maize"))).mapToInt(e->e.getItem().getCount()).sum();h.assertTrue(maize>=3,"Regional winter cannot silently erase a mature maize harvest");
        }finally{WorldActions.cancel(actor,"cleanup");store.set(SurvivalApi.CALENDAR,calendar);WorldEnvironment.replaceForTest(level,old);actor.discard();}
        h.succeed();
    }
    public static void conservation(GameTestHelper h){
        var level=h.getLevel();var old=WorldEnvironment.replaceForTest(level,new EnvironmentModel.Settings("vanilla",0,28,2));
        var actor=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);actor.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1,2,1))));
        var pos=h.absolutePos(new BlockPos(2,1,1));level.setBlock(pos,Blocks.FARMLAND.defaultBlockState(),3);
        try{
            var pot=new ItemStack(SurvivalContent.item("fired_pot"));var tag=ItemAging.data(pot);tag.putInt("water",1);ItemAging.data(pot,tag);actor.setItemInHand(InteractionHand.MAIN_HAND,pot);
            h.assertTrue(WorldActions.start(actor,"irrigate",pos).success(),"An actual open water pot starts irrigation");h.assertTrue(actor.getMainHandItem().isEmpty(),"The actual vessel is reserved during work");
            WorldActions.cancel(actor,"test");var returned=actor.getInventory().getItem(0);h.assertTrue(Pottery.waterUnits(returned)==1&&returned.getDamageValue()==0,"Cancellation returns the unchanged last water portion and vessel");
            h.assertTrue(WorldActions.start(actor,"irrigate",pos).success(),"Returned pot can be used again");for(int i=0;i<240&&WorldActions.inspectJob(actor).get("active").getAsBoolean();i++){actor.tickCount++;WorldActions.tick(actor);}
            returned=actor.getInventory().getItem(0);h.assertTrue(Pottery.isPot(returned)&&Pottery.waterUnits(returned)==0&&returned.getDamageValue()==1,"Useful completion spends exactly one portion and one wear on the same pot");
            h.assertTrue(!WorldActions.inspectJob(actor).get("active").getAsBoolean(),"Completed irrigation releases the worker");
            h.assertFalse(WorldActions.start(actor,"irrigate",pos).success(),"Empty vessels cannot create water");
            SeasonalCrops.irrigate(level,pos,1);tag=ItemAging.data(returned);tag.putInt("water",1);ItemAging.data(returned,tag);
            double xp=SkillApi.inspect(SurvivalApi.state(actor)).toString().hashCode();h.assertTrue(WorldActions.start(actor,"irrigate",pos).success(),"Wet plot can be checked without spending reserved water");
            for(int i=0;i<240&&WorldActions.inspectJob(actor).get("active").getAsBoolean();i++){actor.tickCount++;WorldActions.tick(actor);}
            returned=actor.getInventory().getItem(0);h.assertTrue(Pottery.waterUnits(returned)==1&&returned.getDamageValue()==1,"An already wet plot refunds the unchanged pot");
            h.assertTrue(SkillApi.inspect(SurvivalApi.state(actor)).toString().hashCode()==xp,"Unnecessary watering gives no practice");
        }finally{WorldActions.cancel(actor,"cleanup");WorldEnvironment.replaceForTest(level,old);actor.discard();}
        h.succeed();
    }
}
