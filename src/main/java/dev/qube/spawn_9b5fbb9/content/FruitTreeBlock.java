package dev.qube.spawn_9b5fbb9.content;
import dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import dev.qube.spawn_9b5fbb9.controllers.WorldActions;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
public class FruitTreeBlock extends Block {
    public FruitTreeBlock(Properties p){super(p);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level world,BlockPos pos,Player actor,BlockHitResult hit){
        if(world instanceof ServerLevel)actor.sendOverlayMessage(Component.literal(harvest(actor,pos).message()));
        return InteractionResult.SUCCESS;
    }
    public SurvivalApi.ActionResult harvest(LivingEntity actor,BlockPos pos){return WorldActions.start(actor,"harvest",pos);}
    public boolean harvestable(LivingEntity actor,BlockPos pos){
        if(!(actor.level() instanceof ServerLevel level)||!level.getBlockState(pos).is(this))return false;
        long now=SurvivalApi.calendar(level),yearTicks=24000L*dev.qube.spawn_9b5fbb9.Config.SEASON_DAYS.get()*4;
        var tree=SeasonalCrops.records(level).getCompoundOrEmpty(Long.toString(pos.asLong()));int season=SeasonalCrops.season(now);
        return now-tree.getLongOr("born",now-yearTicks)>=yearTicks&&(season==1||season==2)&&tree.getLongOr("harvestYear",-1)!=now/yearTicks;
    }
    public boolean completeHarvest(LivingEntity actor,BlockPos pos){
        if(!WorldActions.completing(actor)||!actor.isAlive()||!harvestable(actor,pos)||actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))>16)return false;
        var level=(ServerLevel)actor.level();
        long now=SurvivalApi.calendar(level),yearTicks=24000L*dev.qube.spawn_9b5fbb9.Config.SEASON_DAYS.get()*4;
        var all=SeasonalCrops.records(level);String key=Long.toString(pos.asLong());var tree=all.getCompoundOrEmpty(key);long born=tree.getLongOr("born",now-yearTicks);int season=SeasonalCrops.season(now);long year=now/yearTicks;
        Block.popResource(level,pos,new ItemStack(Items.APPLE,6));tree.putLong("harvestYear",year);
        tree.putLong("born",born);all.put(key,tree);return true;
    }
}
