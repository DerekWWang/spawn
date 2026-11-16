package dev.qube.spawn_9b5fbb9.environment.mixin;

import dev.qube.spawn_9b5fbb9.client.LocalWeather;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client-only mixin covers renderer, sky tint, particles and rain sound intensity. */
@Mixin(Level.class)
public abstract class ClientRainMixin {
    @Inject(method="getRainLevel",at=@At("HEAD"),cancellable=true)
    private void localRainLevel(float partial,CallbackInfoReturnable<Float> cir){if(((Level)(Object)this).isClientSide()&&LocalWeather.current()!=null)cir.setReturnValue(LocalWeather.rain());}
    @Inject(method="getThunderLevel",at=@At("HEAD"),cancellable=true)
    private void localThunder(float partial,CallbackInfoReturnable<Float> cir){if(((Level)(Object)this).isClientSide()&&LocalWeather.current()!=null)cir.setReturnValue(0f);}
}
