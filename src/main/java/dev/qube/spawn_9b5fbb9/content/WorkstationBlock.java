package dev.qube.spawn_9b5fbb9.content;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
public class WorkstationBlock extends BaseEntityBlock {
    public WorkstationBlock(Properties p){super(p);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new WorkstationEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide()?null:createTickerHelper(type,SurvivalContent.WORKSTATION_ENTITY.get(),WorkstationEntity::tick);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return InteractionResult.SUCCESS;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(level.getBlockEntity(pos) instanceof WorkstationEntity station){station.wear=Math.clamp(ItemAging.data(stack).getIntOr("workstationWear",0),0,256);station.setChanged();}}
}
