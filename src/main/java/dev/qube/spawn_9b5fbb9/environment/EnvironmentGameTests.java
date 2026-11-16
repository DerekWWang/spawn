package dev.qube.spawn_9b5fbb9.environment;

import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import static dev.qube.spawn_9b5fbb9.environment.EnvironmentModel.*;

public final class EnvironmentGameTests {
    public static void frostHistory(GameTestHelper h){
        var level=h.getLevel();var old=WorldEnvironment.replaceForTest(level,new Settings("vanilla",0,28,2));var store=level.getServer().getCommandStorage();var key=dev.qube.spawn_9b5fbb9.survival.SurvivalApi.CALENDAR;var calendar=store.get(key).copy();var original=dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.records(level).copy();var pos=h.absolutePos(new net.minecraft.core.BlockPos(2,200,2));
        try{
            level.setBlock(pos.below(),net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE,7),3);level.setBlock(pos.below().east(),net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(),3);
            for(int pass=0;pass<2;pass++){
                level.setBlock(pos,net.minecraft.world.level.block.Blocks.POTATOES.defaultBlockState(),3);var all=original.copy();var data=new net.minecraft.nbt.CompoundTag();data.putLong("last",45*DAY);data.putLong("born",0);data.putLong("growth",10*DAY-600);all.put(Long.toString(pos.asLong()),data);dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.save(level,all);
                for(long time=pass==0?46*DAY:104*DAY;time<=104*DAY;time+=pass==0?DAY:104*DAY){var clock=calendar.copy();clock.putLong("sleepOffset",time-level.getServer().overworld().getLevelData().getGameTime());store.set(key,clock);dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.randomTick(level.getBlockState(pos),level,pos,level.getRandom());}
                h.assertTrue(level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.POTATOES),"A crop that matures before frost survives both frequent and catch-up updates; pass="+pass);
            }
        }finally{store.set(key,calendar);dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.save(level,original);WorldEnvironment.replaceForTest(level,old);}
        h.succeed();
    }
    public static void exposure(GameTestHelper h){
        var level=h.getLevel();var old=WorldEnvironment.replaceForTest(level,new Settings("vanilla",0,28,2));var clockStore=level.getServer().getCommandStorage();var clockKey=dev.qube.spawn_9b5fbb9.survival.SurvivalApi.CALENDAR;var originalClock=clockStore.get(clockKey).copy();var pos=h.absolutePos(new net.minecraft.core.BlockPos(2,2,2));var actor=h.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER,new net.minecraft.core.BlockPos(2,2,2));actor.setNoAi(true);dev.qube.spawn_9b5fbb9.survival.SurvivalApi.enroll(actor);var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(actor.position());
        try{
            var a=dev.qube.spawn_9b5fbb9.survival.SurvivalEvents.climate(actor,level);var b=dev.qube.spawn_9b5fbb9.survival.SurvivalEvents.climate(player,level);
            h.assertTrue(a.equals(b),"Player and enrolled NPC sample exactly the same environmental inputs");
            long wet=-1;for(long t=0;t<112*DAY;t+=6000){var w=EnvironmentApi.at(level,pos,t);if(w.rain()>.5&&!w.snow()){wet=t;break;}}h.assertTrue(wet>=0,"The regional test must find a warm wet spell");var clock=originalClock.copy();clock.putLong("sleepOffset",wet-level.getServer().overworld().getLevelData().getGameTime());clockStore.set(clockKey,clock);
            level.setBlock(pos.above(3),net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(),3);var roof=EnvironmentApi.exposure(level,pos);h.assertTrue(roof.roof(),"An actual roof blocks sky exposure");h.assertFalse(level.isRainingAt(pos),"A transparent physical roof must stop native rainfall as well as body and soil wetting");
            for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)level.setBlock(pos.relative(d,2),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            var room=EnvironmentApi.exposure(level,pos);h.assertTrue(room.enclosure()>=roof.enclosure()&&room.enclosure()==1,"Actual walls independently block cardinal wind paths");
        }finally{clockStore.set(clockKey,originalClock);level.removeBlock(pos.above(3),false);for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)level.removeBlock(pos.relative(d,2),false);WorldEnvironment.replaceForTest(level,old);actor.discard();player.discard();}
        h.succeed();
    }
    public static void cropHistory(GameTestHelper h){
        var level=h.getLevel();var old=WorldEnvironment.replaceForTest(level,new Settings("vanilla",0,28,2));var store=level.getServer().getCommandStorage();var calendar=store.get(dev.qube.spawn_9b5fbb9.survival.SurvivalApi.CALENDAR).copy();
        var records=dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.records(level).copy();var pos=h.absolutePos(new net.minecraft.core.BlockPos(2,2,2));
        try{
            level.setBlock(pos.below(),net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE,7),3);level.setBlock(pos,net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState(),3);
            long start=dev.qube.spawn_9b5fbb9.survival.SurvivalApi.calendar(level);var initial=records.copy();var crop=new net.minecraft.nbt.CompoundTag();crop.putLong("last",start);crop.putLong("born",start);initial.put(Long.toString(pos.asLong()),crop);
            double water=0;long growth=0;
            for(int pass=0;pass<2;pass++){
                dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.save(level,initial.copy());level.setBlock(pos,net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState(),3);level.setBlock(pos.below(),net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE,7),3);
                for(long t=pass==0?600:2*DAY;t<=2*DAY;t+=pass==0?600:2*DAY){var clock=calendar.copy();clock.putLong("sleepOffset",clock.getLongOr("sleepOffset",0)+t);store.set(dev.qube.spawn_9b5fbb9.survival.SurvivalApi.CALENDAR,clock);dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.randomTick(level.getBlockState(pos),level,pos,level.getRandom());}
                var result=dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.records(level);double actualWater=result.getCompoundOrEmpty("soil/"+pos.below().asLong()).getDoubleOr("water",0);long actualGrowth=result.getCompoundOrEmpty(Long.toString(pos.asLong())).getLongOr("growth",0);
                if(pass==0){water=actualWater;growth=actualGrowth;}else h.assertTrue(Math.abs(water-actualWater)<1e-8&&Math.abs(growth-actualGrowth)<=1,"Saved soil and suitable growth agree for frequent ticks versus historical catch-up");
            }
            var bare=initial.copy();var soil=new net.minecraft.nbt.CompoundTag();soil.putLong("last",start-2*DAY);soil.putDouble("water",.8);bare.put("soil/"+pos.below().asLong(),soil);crop.putLong("last",start+2*DAY);crop.putLong("born",start+2*DAY);bare.put(Long.toString(pos.asLong()),crop);dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.save(level,bare);level.setBlock(pos,net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState(),3);
            dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.randomTick(level.getBlockState(pos),level,pos,level.getRandom());h.assertTrue(dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.records(level).getCompoundOrEmpty(Long.toString(pos.asLong())).getLongOr("growth",0)==0,"A newly sown crop cannot receive growth from earlier bare-soil weather");
        }finally{store.set(dev.qube.spawn_9b5fbb9.survival.SurvivalApi.CALENDAR,calendar);dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.save(level,records);WorldEnvironment.replaceForTest(level,old);}
        h.succeed();
    }
    public static void packet(GameTestHelper h){
        var packet=new WeatherNetwork.Local("minecraft:overworld","tectonic",1234,-20,240,15,-12,.7f,14,true);
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{WeatherNetwork.Local.CODEC.encode(buffer,packet);var decoded=WeatherNetwork.Local.CODEC.decode(buffer);h.assertTrue(packet.equals(decoded),"Local weather survives its actual network codec");}finally{buffer.release();}
        h.assertTrue(packet.applicable("minecraft:overworld",-20,240,15,1000),"Fresh local weather applies at the observed location");
        h.assertFalse(packet.applicable("minecraft:the_nether",-20,240,15,1000)||packet.applicable("minecraft:overworld",-20,240,15,5000)||packet.applicable("minecraft:overworld",2000,240,15,1000),"Dimension changes, teleports and stale packets cannot reuse old local weather");h.succeed();
    }
    public static void profile(GameTestHelper h){
        h.assertTrue(WorldEnvironment.selectProfile(true,false,false,false).equals("tectonic"),"Fresh opted-in worlds select Tectonic");
        boolean refused=false;try{WorldEnvironment.selectProfile(true,true,false,false);}catch(IllegalStateException expected){refused=true;}h.assertTrue(refused,"Existing unprofiled worlds require explicit adoption");
        h.assertTrue(WorldEnvironment.selectProfile(false,true,false,false).equals("legacy"),"Ordinary existing worlds retain legacy generation");
        h.assertTrue(WorldEnvironment.runtimeCompatible("3.0.32","2.0.6"),"Pinned versions are supported");
        h.assertFalse(WorldEnvironment.runtimeCompatible("3.1.0+beta2","2.0.6"),"A beta cannot silently replace the pinned terrain generator");
        if(ModList.get().isLoaded("tectonic"))h.assertTrue(ModList.get().isLoaded("lithostitched")&&ModList.get().isLoaded("apollib"),"The actual profile loads both worldgen and bundled config libraries");
        var initial=WorldEnvironment.settings(h.getLevel());var active=new Settings("vanilla",1234,28,2);WorldEnvironment.replaceForTest(h.getLevel(),active);h.assertTrue(WorldEnvironment.active(h.getLevel()),"An explicit simulation profile is active");WorldEnvironment.replaceForTest(h.getLevel(),initial);h.succeed();
    }
    public static void terrain(GameTestHelper h){
        var level=h.getLevel();var a=TerrainSampling.sample(level,-65,-129);var b=TerrainSampling.sample(level,-64,-129);
        h.assertTrue(Double.isFinite(a.elevation())&&Math.abs(a.elevation()-b.elevation())<8,"Negative lattice borders interpolate coherent terrain");
        h.assertTrue(a.equals(TerrainSampling.sample(level,-65,-129)),"Sampling is independent of access order");
        h.assertTrue(TerrainSampling.cachedPoints(level)<=8192,"Terrain cache remains bounded");h.succeed();
    }
}
