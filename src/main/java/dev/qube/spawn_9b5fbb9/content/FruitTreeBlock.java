package dev.qube.spawn_9b5fbb9.content;
import dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops;
import dev.qube.spawn_9b5fbb9.environment.EnvironmentApi;
import dev.qube.spawn_9b5fbb9.environment.WorldEnvironment;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import dev.qube.spawn_9b5fbb9.controllers.WorldActions;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A planted perennial: a fruit tree, a coffee shrub, a tea bush. It bears nothing for its first year, then gives its
 * crop in its own seasons, once a year or at every plucking, until cold it cannot stand kills it. */
public class FruitTreeBlock extends Block {
    /** @param product what one harvest gives, and how many; @param seasons the seasons it bears in (bit 0 spring .. bit 3 winter);
     *  @param intervalDays days between harvests within those seasons, or 0 for one harvest a year;
     *  @param plantSeasons the seasons it may be set out in; @param killedBelow the air temperature in degrees C that kills it. */
    public record Bearing(String product,int count,int seasons,int intervalDays,int plantSeasons,double killedBelow){}
    /** Apples: a summer-to-autumn crop once a year; set out in spring or autumn; a hardy tree. */
    public static final Bearing APPLE=new Bearing("minecraft:apple",6,0b0110,0,0b0101,-40);
    /** Arabica coffee: one main picking a year as the cherries ripen after the rains; planted with the rains; frost kills it. */
    public static final Bearing COFFEE=new Bearing("spawn_9b5fbb9:coffee_cherries",6,0b1100,0,0b0011,0);
    /** Tea: the young shoots plucked every week through the flushes of spring, summer and autumn; a hard winter kills it. */
    public static final Bearing TEA=new Bearing("spawn_9b5fbb9:tea_leaves",4,0b0111,7,0b0101,-12);
    private final Bearing bearing;
    public FruitTreeBlock(Properties p){this(p,APPLE);}
    public FruitTreeBlock(Properties p,Bearing bearing){super(p);this.bearing=bearing;}
    public Bearing bearing(){return bearing;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level world,BlockPos pos,Player actor,BlockHitResult hit){
        if(world instanceof ServerLevel)actor.sendOverlayMessage(Component.literal(harvest(actor,pos).message()));
        return InteractionResult.SUCCESS;
    }
    public SurvivalApi.ActionResult harvest(LivingEntity actor,BlockPos pos){return WorldActions.start(actor,"harvest",pos);}
    public boolean plantable(long now){return (bearing.plantSeasons()&(1<<SeasonalCrops.season(now)))!=0;}
    private static long year(){return 24000L*dev.qube.spawn_9b5fbb9.Config.SEASON_DAYS.get()*4;}
    public boolean harvestable(LivingEntity actor,BlockPos pos){
        if(!(actor.level() instanceof ServerLevel level)||!level.getBlockState(pos).is(this))return false;
        if(air(level,pos)<bearing.killedBelow())return false; // cold enough to kill it: it dies on its next tick, and bears nothing meanwhile
        long now=SurvivalApi.calendar(level),yearTicks=year();
        var tree=SeasonalCrops.records(level).getCompoundOrEmpty(Long.toString(pos.asLong()));int season=SeasonalCrops.season(now);
        if(now-tree.getLongOr("born",now-yearTicks)<yearTicks||(bearing.seasons()&(1<<season))==0)return false;
        if(bearing.intervalDays()<=0)return tree.getLongOr("harvestYear",-1)!=now/yearTicks;
        return now-tree.getLongOr("harvested",Long.MIN_VALUE/2)>=bearing.intervalDays()*24000L;
    }
    public boolean completeHarvest(LivingEntity actor,BlockPos pos){
        if(!WorldActions.completing(actor)||!actor.isAlive()||!harvestable(actor,pos)||actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))>16)return false;
        var level=(ServerLevel)actor.level();
        long now=SurvivalApi.calendar(level),yearTicks=year();
        var all=SeasonalCrops.records(level);String key=Long.toString(pos.asLong());var tree=all.getCompoundOrEmpty(key);long born=tree.getLongOr("born",now-yearTicks);
        Block.popResource(level,pos,new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(bearing.product())),bearing.count()));
        // A tea bush flowers in autumn, and the autumn plucking brings in its seed.
        if(bearing==TEA&&SeasonalCrops.season(now)==2)Block.popResource(level,pos,new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("spawn_9b5fbb9:tea_seeds")),1));
        tree.putLong("harvestYear",now/yearTicks);tree.putLong("harvested",now);
        tree.putLong("born",born);all.put(key,tree);return true;
    }
    /** The air about the plant: the regional model where it runs, else the biome's warmth and the season. */
    public static double air(ServerLevel level,BlockPos pos){
        long now=SurvivalApi.calendar(level);
        if(WorldEnvironment.active(level))return EnvironmentApi.at(level,pos,now).temperature();
        // Without it: the biome's mean, a seasonal swing that is small in the tropics and large elsewhere, and the cold of the night.
        float t=level.getBiome(pos).value().getBaseTemperature();
        double mean=t<.3?4:t<.65?10:t<.9?15:t<1.5?25:28,swing=mean>=22?2:10;
        return mean+swing*switch(SeasonalCrops.season(now)){case 1->1;case 3->-1;default->0;}-6;
    }
    /** Cold it cannot stand turns the plant to a dead bush, and its record goes with it. */
    public boolean killedByCold(ServerLevel level,BlockPos pos){return killedByCold(level,pos,air(level,pos));}
    public boolean killedByCold(ServerLevel level,BlockPos pos,double air){
        if(bearing.killedBelow()<=-40||air>=bearing.killedBelow()||!level.getBlockState(pos).is(this))return false;
        level.setBlock(pos,Blocks.DEAD_BUSH.defaultBlockState(),3);SeasonalCrops.records(level).remove(Long.toString(pos.asLong()));return true;
    }
    /** Roots go into soil: a tree, shrub or bush is not planted on stone, sand or planks. */
    @Override protected boolean canSurvive(BlockState state,net.minecraft.world.level.LevelReader level,BlockPos pos){var below=level.getBlockState(pos.below());return below.is(net.minecraft.tags.BlockTags.DIRT)||below.is(Blocks.FARMLAND);}
    @Override protected BlockState updateShape(BlockState state,net.minecraft.world.level.LevelReader level,net.minecraft.world.level.ScheduledTickAccess ticks,BlockPos pos,net.minecraft.core.Direction direction,BlockPos neighborPos,BlockState neighborState,RandomSource random){
        return direction==net.minecraft.core.Direction.DOWN&&!canSurvive(state,level,pos)?Blocks.AIR.defaultBlockState():state;
    }
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){killedByCold(level,pos);}
}
