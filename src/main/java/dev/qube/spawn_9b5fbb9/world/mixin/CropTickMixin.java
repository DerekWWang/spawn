package dev.qube.spawn_9b5fbb9.world.mixin;
import dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class CropTickMixin {
    @Inject(method="isRandomlyTicking",at=@At("HEAD"),cancellable=true)
    private void survival$calendarContinues(CallbackInfoReturnable<Boolean> cir){if(SeasonalCrops.requiresCalendarTick((BlockState)(Object)this))cir.setReturnValue(true);}
    @Inject(method="randomTick", at=@At("HEAD"), cancellable=true)
    private void survival$seasonalTick(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (SeasonalCrops.randomTick((BlockState)(Object)this,level,pos,random)) ci.cancel();
    }
}
