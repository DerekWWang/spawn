package dev.qube.spawn_9b5fbb9.environment;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import java.util.*;
import static dev.qube.spawn_9b5fbb9.environment.EnvironmentModel.*;

/** Samples the effective generator, never decorated neighbor chunks or player builds. */
public final class TerrainSampling {
    private static final int GRID=64,LIMIT=8192;
    private record Key(String dimension,int x,int z){}
    private static final class Cache {
        final Map<Key,Terrain> terrain=lru(LIMIT);
        final Map<Key,Integer> heights=lru(LIMIT*4);
    }
    private static <V> Map<Key,V> lru(int max){return new LinkedHashMap<>(256,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<Key,V> entry){return size()>max;}};}
    private static final Map<MinecraftServer,Cache> CACHES=Collections.synchronizedMap(new WeakHashMap<>());
    private TerrainSampling(){}
    public static void clear(MinecraftServer server){CACHES.remove(server);}
    public static int cachedPoints(ServerLevel level){synchronized(CACHES){var cache=CACHES.get(level.getServer());return cache==null?0:cache.terrain.size();}}
    public static Terrain sample(WorldGenLevel world,ChunkGenerator generator,int x,int z){return sample(world.getLevel(),generator,x,z);}
    public static Terrain sample(ServerLevel level,int x,int z){return sample(level,level.getChunkSource().getGenerator(),x,z);}
    private static Terrain sample(ServerLevel level,ChunkGenerator generator,int x,int z){
        Cache cache; synchronized(CACHES){cache=CACHES.computeIfAbsent(level.getServer(),s->new Cache());}
        int gx=Math.floorDiv(x,GRID)*GRID,gz=Math.floorDiv(z,GRID)*GRID;double fx=Math.floorMod(x,GRID)/(double)GRID,fz=Math.floorMod(z,GRID)/(double)GRID;
        synchronized(cache){
            var a=vertex(cache,level,generator,gx,gz);var b=vertex(cache,level,generator,gx+GRID,gz);var c=vertex(cache,level,generator,gx,gz+GRID);var d=vertex(cache,level,generator,gx+GRID,gz+GRID);
            return blend(blend(a,b,fx),blend(c,d,fx),fz);
        }
    }
    private static Terrain blend(Terrain a,Terrain b,double t){return new Terrain(a.elevation()+(b.elevation()-a.elevation())*t,a.relief()+(b.relief()-a.relief())*t,a.temperature()+(b.temperature()-a.temperature())*t,a.humidity()+(b.humidity()-a.humidity())*t,a.continentalness()+(b.continentalness()-a.continentalness())*t,a.rainShadow()+(b.rainShadow()-a.rainShadow())*t);}
    private static Terrain vertex(Cache cache,ServerLevel level,ChunkGenerator generator,int x,int z){
        var key=new Key(level.dimension().identifier().toString(),x,z);var prior=cache.terrain.get(key);if(prior!=null)return prior;
        int height=height(cache,level,generator,x,z),sea=generator.getSeaLevel();
        int min=height,max=height;for(int[] offset:new int[][]{{-GRID,0},{GRID,0},{0,-GRID},{0,GRID}}){int h=height(cache,level,generator,x+offset[0],z+offset[1]);min=Math.min(min,h);max=Math.max(max,h);}
        var point=level.getChunkSource().randomState().createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED).sample(Math.floorDiv(x,4),Math.floorDiv(sea,4),Math.floorDiv(z,4));
        double temperature=Math.clamp(.65+point.temperature()/10000d*.8,-.4,1.8),humidity=Math.clamp(.5+point.humidity()/20000d,0,1),continentalness=point.continentalness()/10000d;
        double angle=windAngle(level.getSeed()),barrier=height;
        for(int distance:new int[]{256,512,768,1024}){int upX=(int)Math.round((x-Math.cos(angle)*distance)/GRID)*GRID,upZ=(int)Math.round((z-Math.sin(angle)*distance)/GRID)*GRID;barrier=Math.max(barrier,height(cache,level,generator,upX,upZ));}
        double shadow=Math.clamp((barrier-height-24)/192d,0,1);
        var terrain=new Terrain(height-sea,max-min,temperature,humidity,continentalness,shadow);cache.terrain.put(key,terrain);return terrain;
    }
    private static int height(Cache cache,ServerLevel level,ChunkGenerator generator,int x,int z){
        var key=new Key(level.dimension().identifier().toString(),x,z);return cache.heights.computeIfAbsent(key,k->Math.clamp(generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,level.getChunkSource().randomState())-1,level.getMinY(),level.getMaxY()-1));
    }
}
