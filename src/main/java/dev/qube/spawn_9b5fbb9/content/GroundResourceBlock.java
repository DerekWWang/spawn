package dev.qube.spawn_9b5fbb9.content;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import dev.qube.spawn_9b5fbb9.controllers.WorldActions;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Hand-gatherable resources; patches also supply every new seed without grass-only RNG. */
public class GroundResourceBlock extends Block {
    private final String resource;
    public GroundResourceBlock(Properties properties,String resource){super(properties);this.resource=resource;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Block.box(3,0,3,13,4,13);}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide())player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(gather(player,pos).message()));
        return InteractionResult.SUCCESS;
    }
    public SurvivalApi.ActionResult gather(LivingEntity actor,BlockPos pos){return WorldActions.start(actor,"gather",pos);}
    /** Only the timed server job may commit; no caller-provided inventory or Player proxy. */
    public boolean completeGather(LivingEntity actor,BlockPos pos){
        if(!WorldActions.completing(actor)||!(actor.level() instanceof ServerLevel level)||!actor.isAlive()||actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))>16||!level.getBlockState(pos).is(this))return false;
            if(resource.equals("wild")) {
                for(String id:new String[]{"plant_fiber","rice_seeds","maize_seeds","fiber_seeds","medicinal_seeds"})Block.popResource(level,pos,new ItemStack(SurvivalContent.item(id),2));
                int season=dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.season(SurvivalApi.calendar(level));if(season==1||season==2)Block.popResource(level,pos,new ItemStack(SurvivalContent.item("wild_berries"),2));
                for(Item crop:new Item[]{Items.CARROT,Items.POTATO,Items.BEETROOT_SEEDS,Items.MELON_SEEDS,Items.PUMPKIN_SEEDS})Block.popResource(level,pos,new ItemStack(crop,2));
                Block.popResource(level,pos,new ItemStack(Items.WHEAT_SEEDS,2));
                Block.popResource(level,pos,new ItemStack(Items.STICK,2));
            }else{Block.popResource(level,pos,new ItemStack(SurvivalContent.item(resource),3));Block.popResource(level,pos,new ItemStack(Items.STICK));}
            level.removeBlock(pos,false);
        return true;
    }
}
