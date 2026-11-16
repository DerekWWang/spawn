package dev.qube.spawn_9b5fbb9.environment.mixin;

import dev.qube.spawn_9b5fbb9.environment.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelWeatherMixin {
    @Inject(method="isRainingAt",at=@At("HEAD"),cancellable=true)
    private void localRain(BlockPos pos,CallbackInfoReturnable<Boolean> cir){if((Object)this instanceof ServerLevel level&&WorldEnvironment.active(level)){var w=EnvironmentApi.at(level,pos);cir.setReturnValue(w.rain()>.15&&!w.snow()&&!EnvironmentApi.roofed(level,pos));}}
}
