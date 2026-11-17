package dev.qube.spawn_9b5fbb9.environment.mixin;

import dev.qube.spawn_9b5fbb9.environment.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerWeatherMixin {
    @Inject(method="advanceWeatherCycle",at=@At("HEAD"),cancellable=true)
    private void regionalCycle(CallbackInfo ci){var level=(ServerLevel)(Object)this;if(WorldEnvironment.active(level)){level.setRainLevel(0);level.setThunderLevel(0);ci.cancel();}}
    @Inject(method="tickPrecipitation",at=@At("HEAD"),cancellable=true)
    private void regionalSurface(BlockPos pos,CallbackInfo ci){
        var level=(ServerLevel)(Object)this;
        if(!WorldEnvironment.active(level)){
            // The ordinary weather cycle: steady rain still drowns a cooking fire left in the open.
            var open=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,pos).below();
            if(level.isLoaded(open)&&level.isRainingAt(open.above()))douse(level,open,level.getBlockState(open));
            return;
        }
        ci.cancel();
        var top=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,pos);var below=top.below();if(!level.isAreaLoaded(below,1))return;
        var w=EnvironmentApi.at(level,top);var ground=level.getBlockState(below);var surface=level.getBlockState(top);
        if(w.temperature()>2){if(surface.is(Blocks.SNOW))level.removeBlock(top,false);if(ground.is(Blocks.SNOW))level.removeBlock(below,false);if(ground.is(Blocks.ICE))level.setBlockAndUpdate(below,Blocks.WATER.defaultBlockState());}
        if(w.temperature()<-2&&ground.is(Blocks.WATER)&&level.getFluidState(below).isSource()&&level.getMaxLocalRawBrightness(below)<10)level.setBlockAndUpdate(below,Blocks.ICE.defaultBlockState());
        if(w.rain()<=.15)return;
        if(!w.snow()&&w.rain()>EnvironmentApi.DOUSING_RAIN)douse(level,below,ground);
        if(w.snow()){
            int limit=Math.min(8,level.getGameRules().get(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT));
            if(limit>0&&Blocks.SNOW.defaultBlockState().canSurvive(level,top)){
                if(surface.is(Blocks.SNOW)){int layers=surface.getValue(SnowLayerBlock.LAYERS);if(layers<limit)level.setBlockAndUpdate(top,surface.setValue(SnowLayerBlock.LAYERS,layers+1));}
                else if(surface.isAir())level.setBlockAndUpdate(top,Blocks.SNOW.defaultBlockState());
            }
        }
        ground.getBlock().handlePrecipitation(ground,level,below,w.snow()?Biome.Precipitation.SNOW:Biome.Precipitation.RAIN);
    }
    private static void douse(ServerLevel level,BlockPos pos,net.minecraft.world.level.block.state.BlockState state){
        if(!state.is(net.minecraft.tags.BlockTags.CAMPFIRES)||!state.getValue(CampfireBlock.LIT))return;
        level.levelEvent(null,1009,pos,0);CampfireBlock.douse(null,level,pos,state);
        level.setBlockAndUpdate(pos,state.setValue(CampfireBlock.LIT,false));
    }
}
