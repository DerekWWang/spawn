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
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){int height=resource.equals("fallen_sticks")?1:resource.equals("loose_stone")||shore()?3:resource.equals("wild_sphagnum")?4:resource.equals("wild_maize")?16:resource.equals("wild_wheat")||resource.equals("wild_rice")?14:12;return Block.box(2,0,2,14,height,14);}
    public String resource(){return resource;}
    /** Lies on the strand, on sand or shingle, not rooted in soil. */
    private boolean shore(){return resource.equals("seashells")||resource.equals("murex_shells")||resource.equals("pearl_oysters");}
    public boolean harvestable(LivingEntity actor){int season=dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.season(SurvivalApi.calendar((ServerLevel)actor.level()));return switch(resource){case "wild_bramble","wild_rice","wild_maize","wild_pumpkin","wild_melon"->season==1||season==2;case "wild_flax","wild_yarrow","wild_woad","wild_weld","wild_madder","wild_meadowsweet"->season!=3;case "wild_wheat"->season==0||season==2;
        // Ramsons carpet the woods in spring and are gone by summer; wild peas pod from spring into summer; wild coffee ripens
        // after the rains, in autumn and winter; wild tea flushes from spring to autumn.
        case "wild_garlic"->season==0;case "wild_peas"->season==0||season==1;case "wild_coffee"->season==2||season==3;case "wild_tea"->season!=3;default->true;};}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){var support=level.getBlockState(pos.below());return resource.equals("loose_stone")||resource.equals("fallen_sticks")||shore()?support.isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP):support.is(Blocks.GRASS_BLOCK)||support.is(net.minecraft.tags.BlockTags.DIRT)||support.is(Blocks.FARMLAND)||support.is(Blocks.MUD);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,net.minecraft.core.Direction direction,BlockPos neighborPos,BlockState neighborState,net.minecraft.util.RandomSource random){return canSurvive(state,level,pos)?state:Blocks.AIR.defaultBlockState();}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide())player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(gather(player,pos).message()));
        return InteractionResult.SUCCESS;
    }
    public SurvivalApi.ActionResult gather(LivingEntity actor,BlockPos pos){return WorldActions.start(actor,"gather",pos);}
    /** Only the timed server job may commit; no caller-provided inventory or Player proxy. */
    public boolean completeGather(LivingEntity actor,BlockPos pos){
        if(!WorldActions.completing(actor)||!(actor.level() instanceof ServerLevel level)||!actor.isAlive()||actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))>16||!level.getBlockState(pos).is(this)||!harvestable(actor))return false;
            switch(resource){
                case "wild"->drop(level,pos,"plant_fiber",2);
                case "loose_stone"->drop(level,pos,"loose_stone",3);
                case "fallen_sticks"->Block.popResource(level,pos,new ItemStack(Items.STICK,2));
                case "wild_flax"->{drop(level,pos,"flax_fiber",2);drop(level,pos,"flax_seeds",2);}
                case "wild_yarrow"->{drop(level,pos,"yarrow_leaves",2);drop(level,pos,"yarrow_seeds",2);}
                case "wild_bramble"->{drop(level,pos,"blackberries",3);drop(level,pos,"bramble_seeds",1);}
                case "wild_rice"->{drop(level,pos,"rice_grain",2);drop(level,pos,"rice_seeds",2);}
                case "wild_maize"->{drop(level,pos,"maize",1);drop(level,pos,"maize_seeds",2);}
                case "wild_wheat"->{Block.popResource(level,pos,new ItemStack(Items.WHEAT));Block.popResource(level,pos,new ItemStack(Items.WHEAT_SEEDS,2));}
                // A strandline gives a handful of shells; a bank of dye whelks rather more; a pearl is one find.
                case "seashells"->drop(level,pos,"seashell",3);
                case "murex_shells"->drop(level,pos,"murex",4);
                case "pearl_oysters"->{drop(level,pos,"pearl",1);drop(level,pos,"seashell",2);}
                case "wild_woad"->drop(level,pos,"woad_leaves",4);
                case "wild_indigo"->drop(level,pos,"indigo_leaves",4);
                case "wild_madder"->drop(level,pos,"madder_root",2);
                case "wild_weld"->drop(level,pos,"weld",3);
                case "wild_meadowsweet"->drop(level,pos,"meadowsweet",2);
                case "wild_sphagnum"->drop(level,pos,"sphagnum",3);
                case "wild_cinchona"->drop(level,pos,"cinchona_bark",2);
                case "wild_garlic"->drop(level,pos,"ramsons",3);
                case "wild_peas"->{drop(level,pos,"peas",2);drop(level,pos,"pea_seeds",1);}
                case "wild_coffee"->drop(level,pos,"coffee_cherries",3);
                case "wild_tea"->{drop(level,pos,"tea_leaves",3);drop(level,pos,"tea_seeds",1);}
                case "wild_carrot"->Block.popResource(level,pos,new ItemStack(Items.CARROT,2));
                case "wild_potato"->Block.popResource(level,pos,new ItemStack(Items.POTATO,2));
                case "wild_beetroot"->{Block.popResource(level,pos,new ItemStack(Items.BEETROOT));Block.popResource(level,pos,new ItemStack(Items.BEETROOT_SEEDS,2));}
                case "wild_pumpkin"->Block.popResource(level,pos,new ItemStack(Items.PUMPKIN_SEEDS,2));
                case "wild_melon"->{Block.popResource(level,pos,new ItemStack(Items.MELON_SLICE,2));Block.popResource(level,pos,new ItemStack(Items.MELON_SEEDS));}
                default->{return false;}
            }
            level.removeBlock(pos,false);
        return true;
    }
    private static void drop(ServerLevel level,BlockPos pos,String id,int count){Block.popResource(level,pos,new ItemStack(SurvivalContent.item(id),count));}
}
