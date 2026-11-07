package dev.qube.spawn_9b5fbb9.world.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class ObsidianMixin {
    @Inject(method="getDestroySpeed",at=@At("HEAD"),cancellable=true)
    private void survival$knappable(BlockGetter level,BlockPos pos,CallbackInfoReturnable<Float> cir){if(((BlockState)(Object)this).is(Blocks.OBSIDIAN))cir.setReturnValue(1.5f);}
    @Inject(method="requiresCorrectToolForDrops",at=@At("HEAD"),cancellable=true)
    private void survival$flakes(CallbackInfoReturnable<Boolean> cir){if(((BlockState)(Object)this).is(Blocks.OBSIDIAN))cir.setReturnValue(false);}
}
