package dev.qube.spawn_9b5fbb9.environment;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import static dev.qube.spawn_9b5fbb9.environment.EnvironmentModel.*;

/** Shared observations for bodies, agriculture, animals and client weather. */
public final class EnvironmentApi {
    public record Exposure(boolean roof,double enclosure,boolean fire){}
    private EnvironmentApi(){}
    public static Weather at(ServerLevel level,BlockPos pos){return at(level,pos,SurvivalApi.calendar(level));}
    public static Weather at(ServerLevel level,BlockPos pos,long tick){
        var terrain=TerrainSampling.sample(level,pos.getX(),pos.getZ());int sea=level.getChunkSource().getGenerator().getSeaLevel();
        var local=new Terrain(pos.getY()-sea,terrain.relief(),terrain.temperature(),terrain.humidity(),terrain.continentalness(),terrain.rainShadow());
        return weather(level.getSeed(),pos.getX(),pos.getZ(),tick,local,WorldEnvironment.settings(level));
    }
    public static Exposure exposure(ServerLevel level,BlockPos pos){
        boolean roof=roofed(level,pos),fire=false;int blocked=0;
        for(var direction:Direction.Plane.HORIZONTAL){for(int d=1;d<=4;d++){var p=pos.relative(direction,d);if(!level.hasChunkAt(p))break;if(level.getBlockState(p).isCollisionShapeFullBlock(level,p)||level.getBlockState(p.above()).isCollisionShapeFullBlock(level,p.above())){blocked++;break;}}}
        for(var p:BlockPos.betweenClosed(pos.offset(-3,-1,-3),pos.offset(3,1,3))){if(!level.hasChunkAt(p))continue;var state=level.getBlockState(p);if(state.is(Blocks.FIRE)||state.is(Blocks.LAVA)||state.is(Blocks.CAMPFIRE)&&state.getValue(CampfireBlock.LIT)){fire=true;break;}}
        return new Exposure(roof,blocked/4d,fire);
    }
    public static boolean roofed(ServerLevel level,BlockPos pos){return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,pos.getX(),pos.getZ())>pos.getY();}
}
