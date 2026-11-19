package dev.qube.spawn_9b5fbb9.environment.mixin;

import dev.qube.spawn_9b5fbb9.client.LocalWeather;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientWeatherMixin {
    @Inject(method="getPrecipitationAt",at=@At("HEAD"),cancellable=true)
    private void localPrecipitation(BlockPos pos,CallbackInfoReturnable<Biome.Precipitation> cir){var p=LocalWeather.current();if(p!=null)cir.setReturnValue(p.rain()<=0?Biome.Precipitation.NONE:p.snow()?Biome.Precipitation.SNOW:Biome.Precipitation.RAIN);}
}
