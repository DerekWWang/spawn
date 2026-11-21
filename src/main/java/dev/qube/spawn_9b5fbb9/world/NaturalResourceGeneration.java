package dev.qube.spawn_9b5fbb9.world;

import dev.qube.spawn_9b5fbb9.content.*;
import dev.qube.spawn_9b5fbb9.environment.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Thin chunk-local adapters over the deterministic resource fields. */
public final class NaturalResourceGeneration {
    private static final DeferredRegister<MapCodec<? extends Feature>> FEATURES=DeferredRegister.create(Registries.FEATURE_TYPE,SurvivalContent.MODID);
    static {FEATURES.register("natural_surface",()->SurfaceFeature.CODEC);FEATURES.register("regional_geology",()->GeologyFeature.CODEC);}
    public static void register(IEventBus bus){FEATURES.register(bus);}
    private static boolean soil(BlockState state){return state.is(Blocks.GRASS_BLOCK)||state.is(BlockTags.DIRT)||state.is(Blocks.MUD);}
    private static boolean stone(BlockState state){return state.is(BlockTags.BASE_STONE_OVERWORLD)||state.is(SurvivalContent.LIMESTONE.get())||state.is(SurvivalContent.MARBLE.get());}
    private static BlockState marble(long seed,int x,int y,int z){return ResourceFields.lapis(seed,x,y,z)?Blocks.LAPIS_ORE.defaultBlockState():SurvivalContent.MARBLE.get().defaultBlockState();}
    private static BlockState host(ResourceFields.Host host){return switch(host){case GRANITE->Blocks.GRANITE.defaultBlockState();case LIMESTONE->SurvivalContent.LIMESTONE.get().defaultBlockState();case SANDSTONE->Blocks.SANDSTONE.defaultBlockState();default->Blocks.STONE.defaultBlockState();};}
    private static BlockState ore(ResourceFields.Mineral mineral){return switch(mineral){case COPPER->SurvivalContent.MALACHITE_ORE.get().defaultBlockState();case TIN->SurvivalContent.CASSITERITE_ORE.get().defaultBlockState();case IRON->SurvivalContent.HEMATITE_ORE.get().defaultBlockState();case COAL->Blocks.COAL_ORE.defaultBlockState();default->Blocks.STONE.defaultBlockState();};}
    /** Accessory minerals ride inside the primary deposits without moving them: galena with
     * limestone-hosted copper, and pyrite ("coal brasses", the old source of sulfur) in coal seams. */
    private static BlockState accessory(long seed,ResourceFields.Mineral mineral,int x,int y,int z){
        double roll=ResourceFields.unit(ResourceFields.hash(seed^0x5EEDL,x,z,y*31L+401));
        if(mineral==ResourceFields.Mineral.COPPER&&roll<.14)return dev.qube.spawn_9b5fbb9.industry.IndustrialContent.GALENA_ORE.get().defaultBlockState();
        // Fluorspar is the classic gangue of limestone-hosted lead veins; rock salt beds lie with coal-measure sandstones.
        if(mineral==ResourceFields.Mineral.COPPER&&roll<.20)return dev.qube.spawn_9b5fbb9.industry.IndustrialContent.FLUORITE_ORE.get().defaultBlockState();
        if(mineral==ResourceFields.Mineral.COAL&&roll>.90)return dev.qube.spawn_9b5fbb9.industry.IndustrialContent.ROCK_SALT.get().defaultBlockState();
        // The same sedimentary basins carry bituminous sands and evaporite borates; tin lodes carry wolframite;
        // weathered ironstone grades into lateritic bauxite.
        if(mineral==ResourceFields.Mineral.COAL&&roll>.80)return dev.qube.spawn_9b5fbb9.industry.IndustrialContent.TAR_SAND.get().defaultBlockState();
        if(mineral==ResourceFields.Mineral.COAL&&roll>.76)return dev.qube.spawn_9b5fbb9.industry.IndustrialContent.BORAX_ORE.get().defaultBlockState();
        // Topaz is a mineral of tin-bearing granite (greisen), found with the cassiterite it formed beside.
        if(mineral==ResourceFields.Mineral.TIN&&roll>.97)return SurvivalContent.TOPAZ_ORE.get().defaultBlockState();
        if(mineral==ResourceFields.Mineral.TIN&&roll<.15)return dev.qube.spawn_9b5fbb9.industry.IndustrialContent.WOLFRAMITE_ORE.get().defaultBlockState();
        if(mineral==ResourceFields.Mineral.IRON&&roll<.10)return dev.qube.spawn_9b5fbb9.industry.IndustrialContent.BAUXITE_ORE.get().defaultBlockState();
        if(mineral==ResourceFields.Mineral.COAL&&roll<.12)return dev.qube.spawn_9b5fbb9.industry.IndustrialContent.PYRITE_ORE.get().defaultBlockState();
        return ore(mineral);
    }
    private static boolean accessible(WorldGenLevel world,BlockPos pos){return world.hasChunk(Math.floorDiv(pos.getX(),16),Math.floorDiv(pos.getZ(),16));}
    private static boolean nearWater(WorldGenLevel world,BlockPos pos){
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){var p=pos.offset(dx,-1,dz);if(accessible(world,p)&&world.getFluidState(p).is(FluidTags.WATER))return true;}
        return false;
    }
    private static boolean nearWood(WorldGenLevel world,BlockPos pos){
        for(int dx=-4;dx<=4;dx+=2)for(int dz=-4;dz<=4;dz+=2)for(int dy=0;dy<=3;dy+=3){var p=pos.offset(dx,dy,dz);if(own(p.getX(),p.getZ(),pos.getX()>>4,pos.getZ()>>4)){var state=world.getBlockState(p);if(state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES)||state.is(SurvivalContent.FRUIT_TREE.get()))return true;}}
        return false;
    }
    /** Wooded ground in a temperate-to-tropical biome: a forest, taiga or jungle, by its stable biome tags. */
    public static boolean woodland(WorldGenLevel world,BlockPos pos){
        var biome=world.getBiome(pos);float temperature=biome.value().getBaseTemperature();
        return temperature>=.2&&temperature<=1.2&&(biome.is(BiomeTags.IS_FOREST)||biome.is(BiomeTags.IS_TAIGA)||biome.is(BiomeTags.IS_JUNGLE));
    }
    /** Open water at or just below the foot of this column, within four blocks: a shore. */
    private static boolean shoreline(WorldGenLevel world,BlockPos pos){
        for(int dx=-4;dx<=4;dx+=2)for(int dz=-4;dz<=4;dz+=2)for(int dy=-1;dy>=-3;dy--){var p=pos.offset(dx,dy,dz);if(accessible(world,p)&&world.getFluidState(p).is(FluidTags.WATER))return true;}
        return false;
    }
    public static String source(ResourceFields.Herb herb){return "wild_"+herb.name().toLowerCase(java.util.Locale.ROOT);}
    public static String source(ResourceFields.Forage forage){return "wild_"+forage.name().toLowerCase(java.util.Locale.ROOT);}
    /** Where a forage plant grows wild: in the open, on soil, in the climate {@link ResourceFields#forageClimate} gives it. */
    public static boolean habitat(WorldGenLevel world,BlockPos pos,ResourceFields.Forage forage){
        if(!world.isEmptyBlock(pos)||!soil(world.getBlockState(pos.below())))return false;
        if(world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ())>pos.getY())return false;
        var biome=world.getBiome(pos);float temperature=biome.value().getBaseTemperature();
        boolean jungle=biome.is(BiomeTags.IS_JUNGLE),taiga=biome.is(BiomeTags.IS_TAIGA),wood=biome.is(BiomeTags.IS_FOREST)||taiga||jungle,wet=nearWater(world,pos);
        double c=temperature<.3?4:temperature<.65?10:temperature<.9?15:temperature<1.5?25:28;
        if(WorldEnvironment.active(world.getLevel())){
            var terrain=TerrainSampling.sample(world,world.getLevel().getChunkSource().getGenerator(),pos.getX(),pos.getZ());c=EnvironmentModel.baseline(terrain);
            if(terrain.relief()>45||terrain.humidity()<(forage==ResourceFields.Forage.PEAS?.15:.3))return false;
        }
        return ResourceFields.forageClimate(forage,c,wood,wet,jungle,taiga);
    }
    public static String source(ResourceFields.Shore shore){return switch(shore){case SHELLS->"seashells";case MUREX->"murex_shells";case PEARL->"pearl_oysters";default->"";};}
    /** Where a dye or medicine plant grows wild. Each belongs to a climate, so no one place has them all. */
    public static boolean habitat(WorldGenLevel world,BlockPos pos,ResourceFields.Herb herb){
        if(!world.isEmptyBlock(pos)||!soil(world.getBlockState(pos.below())))return false;
        if(world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ())>pos.getY())return false;
        var biome=world.getBiome(pos);float temperature=biome.value().getBaseTemperature();
        boolean jungle=biome.is(BiomeTags.IS_JUNGLE),taiga=biome.is(BiomeTags.IS_TAIGA),wood=biome.is(BiomeTags.IS_FOREST)||taiga||jungle,wet=nearWater(world,pos);
        // Biome temperature stands for the climate where no regional model runs: taiga 0.25, forest 0.7, plains 0.8, jungle 0.95, savanna and desert 2.
        double c=temperature<.3?4:temperature<.65?10:temperature<.9?15:temperature<1.5?25:28;
        if(WorldEnvironment.active(world.getLevel())){
            var terrain=TerrainSampling.sample(world,world.getLevel().getChunkSource().getGenerator(),pos.getX(),pos.getZ());c=EnvironmentModel.baseline(terrain);
            if(terrain.relief()>45||terrain.humidity()<.15)return false;
        }
        return switch(herb){
            case WOAD->!wood&&!wet&&c>=5&&c<=18;
            case WELD->!wood&&!wet&&c>=8&&c<=22;
            case MADDER->!wood&&!wet&&c>=14&&c<=28;
            case INDIGO->c>=22&&(jungle||biome.is(BiomeTags.IS_SAVANNA));
            case MEADOWSWEET->wet&&!jungle&&c>=4&&c<=18;
            case SPHAGNUM->wet&&c<=12&&(taiga||c<=6||wood);
            case CINCHONA->jungle;
        };
    }
    private static BlockPos ground(WorldGenLevel world,int x,int z){return new BlockPos(x,world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z);}
    public static String source(ResourceFields.Patch patch){
        return switch(patch.plant()){
            case FIBER->"wild_flax";case YARROW->"wild_yarrow";case BRAMBLE->"wild_bramble";case RICE->"wild_rice";case MAIZE->"wild_maize";case WHEAT->"wild_wheat";
            case ROOTS->new String[]{"wild_carrot","wild_potato","wild_beetroot"}[(int)Math.floorMod(patch.salt(),3)];
            case GOURD->Math.floorMod(patch.salt(),2)==0?"wild_pumpkin":"wild_melon";
        };
    }
    public static boolean habitat(WorldGenLevel world,BlockPos pos,ResourceFields.Plant plant){
        if(!world.isEmptyBlock(pos)||!soil(world.getBlockState(pos.below())))return false;
        // Terrain exposure works during decoration, before propagated lighting exists.
        if(world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ())>pos.getY())return false;
        var biome=world.getBiome(pos);float temperature=biome.value().getBaseTemperature();
        if(temperature<.2||temperature>1.2)return false;
        if(WorldEnvironment.active(world.getLevel())){
            var terrain=TerrainSampling.sample(world,world.getLevel().getChunkSource().getGenerator(),pos.getX(),pos.getZ());double c=EnvironmentModel.baseline(terrain);
            if(terrain.relief()>45||c<3||c>34||terrain.humidity()<.20)return false;
            if((plant==ResourceFields.Plant.RICE||plant==ResourceFields.Plant.MAIZE||plant==ResourceFields.Plant.GOURD)&&c<14)return false;
        }
        // Biomes are stable before decoration; neighboring tree features are not.
        boolean wet=nearWater(world,pos),wood=biome.is(BiomeTags.IS_FOREST)||biome.is(BiomeTags.IS_TAIGA)||biome.is(BiomeTags.IS_JUNGLE);
        return switch(plant){case RICE->wet&&temperature>=.7;case MAIZE,GOURD->temperature>=.65&&!wet;case BRAMBLE->wood&&(biome.is(BiomeTags.IS_FOREST)||temperature<.8);case FIBER,YARROW,WHEAT,ROOTS->!wet&&!wood&&temperature<1;};
    }
    private static boolean own(int x,int z,int chunkX,int chunkZ){return Math.floorDiv(x,16)==chunkX&&Math.floorDiv(z,16)==chunkZ;}
    private static final class SurfaceFeature implements Feature{
        private static final MapCodec<SurfaceFeature> CODEC=MapCodec.unit(SurfaceFeature::new);
        @Override public MapCodec<SurfaceFeature> codec(){return CODEC;}
        @Override public boolean place(WorldGenLevel world,ChunkGenerator generator,RandomSource random,BlockPos origin){
            long seed=world.getSeed();int cx=Math.floorDiv(origin.getX(),16),cz=Math.floorDiv(origin.getZ(),16);boolean placed=false;
            for(var point:ResourceFields.grove(seed,cx,cz)){
                var pos=ground(world,point.x(),point.z());var biome=world.getBiome(pos);
                if(biome.value().getBaseTemperature()<.3||biome.value().getBaseTemperature()>.95||!soil(world.getBlockState(pos.below())))continue;
                if(WorldEnvironment.active(world.getLevel())){var terrain=TerrainSampling.sample(world,generator,point.x(),point.z());double c=EnvironmentModel.baseline(terrain);if(c<5||c>27||terrain.relief()>35||terrain.humidity()<.3)continue;}
                if(Math.floorMod(point.x(),16)<2||Math.floorMod(point.x(),16)>13||Math.floorMod(point.z(),16)<2||Math.floorMod(point.z(),16)>13)continue;
                if(tree(world,pos))placed=true;
            }
            for(var patch:ResourceFields.surfacePatches(seed,cx,cz))for(var point:ResourceFields.patchPositions(patch)){
                if(!own(point.x(),point.z(),cx,cz))continue;var pos=ground(world,point.x(),point.z());
                if(habitat(world,pos,patch.plant()))placed|=world.setBlock(pos,SurvivalContent.FORAGE.get(source(patch)).get().defaultBlockState(),2);
            }
            for(var patch:ResourceFields.foragePatches(seed,cx,cz))for(var point:ResourceFields.patchPositions(patch)){
                if(!own(point.x(),point.z(),cx,cz))continue;var pos=ground(world,point.x(),point.z());
                if(habitat(world,pos,patch.forage()))placed|=world.setBlock(pos,SurvivalContent.FORAGE.get(source(patch.forage())).get().defaultBlockState(),2);
            }
            for(var patch:ResourceFields.herbPatches(seed,cx,cz))for(var point:ResourceFields.patchPositions(patch)){
                if(!own(point.x(),point.z(),cx,cz))continue;var pos=ground(world,point.x(),point.z());
                if(habitat(world,pos,patch.herb()))placed|=world.setBlock(pos,SurvivalContent.FORAGE.get(source(patch.herb())).get().defaultBlockState(),2);
            }
            for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++){
                var pos=ground(world,x,z);if(!world.isEmptyBlock(pos))continue;var below=world.getBlockState(pos.below());
                if(!below.isFaceSturdy(world,pos.below(),Direction.UP))continue;
                // The strandline: only columns the field picks are looked at, so the biome and the water are read for a few in a hundred.
                boolean beach=below.is(Blocks.SAND)||below.is(Blocks.GRAVEL)||below.is(Blocks.CLAY)||below.is(Blocks.RED_SAND);
                boolean strand=(beach||soil(below))&&ResourceFields.strandStone(seed,x,z,beach);
                if((beach||soil(below))&&(strand||ResourceFields.shoreCandidate(seed,x,z,beach))&&shoreline(world,pos)){
                    var biome=world.getBiome(pos);boolean sea=biome.is(BiomeTags.IS_BEACH)||biome.is(BiomeTags.IS_OCEAN)||biome.is(BiomeTags.IS_DEEP_OCEAN);float temperature=biome.value().getBaseTemperature();
                    boolean warm=temperature>=.6;
                    if(WorldEnvironment.active(world.getLevel()))warm=EnvironmentModel.baseline(TerrainSampling.sample(world,generator,x,z))>=18;
                    var find=ResourceFields.shore(seed,x,z,beach,sea,warm);
                    if(find!=ResourceFields.Shore.NONE){placed|=world.setBlock(pos,SurvivalContent.FORAGE.get(source(find)).get().defaultBlockState(),2);continue;}
                    if(strand){placed|=world.setBlock(pos,SurvivalContent.LOOSE_STONES.get().defaultBlockState(),2);continue;}
                }
                boolean rocky=stone(below)||below.is(Blocks.GRAVEL)||below.is(Blocks.SANDSTONE);
                if(ResourceFields.looseStone(seed,x,z,rocky))placed|=world.setBlock(pos,SurvivalContent.LOOSE_STONES.get().defaultBlockState(),2);
                else if(soil(below)&&ResourceFields.fallenStick(seed,x,z)&&nearWood(world,pos))placed|=world.setBlock(pos,SurvivalContent.FORAGE.get("fallen_sticks").get().defaultBlockState(),2);
                // Woods have their own fibre plants (nettle, bast, vine) in the undergrowth, where flax will not grow.
                else if(soil(below)&&ResourceFields.undergrowth(seed,x,z)&&woodland(world,pos))placed|=world.setBlock(pos,SurvivalContent.WILD_PLANTS.get().defaultBlockState(),2);
            }
            return placed;
        }
    }
    /** A genuine small apple tree; the harvestable core is in the trunk within reach. */
    private static boolean tree(WorldGenLevel world,BlockPos pos){
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=0;dy<=5;dy++)if(!world.isEmptyBlock(pos.offset(dx,dy,dz)))return false;
        for(int dy=0;dy<4;dy++)world.setBlock(pos.above(dy),dy==0?SurvivalContent.FRUIT_TREE.get().defaultBlockState():Blocks.OAK_LOG.defaultBlockState(),2);
        for(int dy=2;dy<=5;dy++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if((dx!=0||dz!=0||dy>=4)&&Math.abs(dx)+Math.abs(dz)<=(dy==5?2:3))world.setBlock(pos.offset(dx,dy,dz),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,Math.clamp(Math.abs(dx)+Math.abs(dz)+Math.max(0,dy-3),1,6)),2);
        return true;
    }
    private static final class GeologyFeature implements Feature{
        private static final MapCodec<GeologyFeature> CODEC=MapCodec.unit(GeologyFeature::new);
        @Override public MapCodec<GeologyFeature> codec(){return CODEC;}
        @Override public boolean place(WorldGenLevel world,ChunkGenerator generator,RandomSource random,BlockPos origin){
            long seed=world.getSeed();int cx=Math.floorDiv(origin.getX(),16),cz=Math.floorDiv(origin.getZ(),16);boolean placed=false;
            var district=ResourceFields.district(seed,Math.floorDiv(cx,16),Math.floorDiv(cz,16));
            boolean environment=WorldEnvironment.active(world.getLevel());
            if(environment){var reference=TerrainSampling.sample(world,generator,district.x(),district.z());district=ResourceFields.district(seed,Math.floorDiv(cx,16),Math.floorDiv(cz,16),(int)Math.round(reference.elevation()+generator.getSeaLevel()),reference.relief(),world.getMinY(),world.getMaxY());}
            for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++){
                var top=ground(world,x,z).below();var quarry=ResourceFields.quarry(seed,x,z);
                if(environment&&quarry!=ResourceFields.Host.NONE)quarry=ResourceFields.provinceHost(seed,x,z);
                if(quarry!=ResourceFields.Host.NONE){
                    int qx=Math.floorDiv(x,128),qz=Math.floorDiv(z,128);long salt=ResourceFields.hash(seed,qx,qz,71);
                    int centerX=qx*128+40+(int)Math.floorMod(ResourceFields.hash(salt,0,0,1),48),centerZ=qz*128+40+(int)Math.floorMod(ResourceFields.hash(salt,0,0,2),48);
                    boolean exposure=(x-centerX)*(x-centerX)+(z-centerZ)*(z-centerZ)<100;
                    boolean baked=quarry==ResourceFields.Host.LIMESTONE&&ResourceFields.marble(seed,x,z);
                    for(int y=Math.max(world.getMinY()+5,top.getY()-12);y<=top.getY();y++){var p=new BlockPos(x,y,z);var state=world.getBlockState(p);if(stone(state)||exposure&&soil(state))placed|=world.setBlock(p,baked?marble(seed,x,y,z):host(quarry),2);}
                }
                if(soil(world.getBlockState(top))&&ResourceFields.unit(ResourceFields.hash(seed,Math.floorDiv(x,12),Math.floorDiv(z,12),111))<.35&&nearWater(world,top.above())){
                    for(int depth=0;depth<3;depth++){var p=top.below(depth);if(soil(world.getBlockState(p))||world.getBlockState(p).is(Blocks.GRAVEL))placed|=world.setBlock(p,Blocks.CLAY.defaultBlockState(),2);}
                }
                int low=Math.max(world.getMinY()+5,district.y()-district.height()-3),high=Math.min(top.getY(),district.y()+18);
                for(int y=low;y<=high;y++){
                    var sample=ResourceFields.sampleDeposit(district,x,y,z);if(sample==null)continue;var pos=new BlockPos(x,y,z);if(!stone(world.getBlockState(pos)))continue;
                    placed|=world.setBlock(pos,sample.ore()?accessory(seed,sample.mineral(),x,y,z):host(sample.host()),2);
                }
            }
            return placed;
        }
    }
}
