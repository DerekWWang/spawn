package dev.qube.spawn_9b5fbb9.environment.mixin;

import dev.qube.spawn_9b5fbb9.environment.WorldEnvironment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/**
 * Fire asks the world whether it is raining anywhere before it asks whether rain falls on it.
 * Regional weather keeps the world-wide flag dry, so the first question must pass for the local one to be asked.
 */
@Mixin(FireBlock.class)
public abstract class FireRainMixin {
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
    private boolean regionalRain(ServerLevel level){return WorldEnvironment.active(level)||level.isRaining();}
}
