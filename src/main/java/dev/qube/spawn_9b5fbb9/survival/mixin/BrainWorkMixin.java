package dev.qube.spawn_9b5fbb9.survival.mixin;
import dev.qube.spawn_9b5fbb9.survival.SurvivalEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Brain.class)
public abstract class BrainWorkMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void survival$mentalCadence(ServerLevel level,LivingEntity actor,CallbackInfo callback){if(!SurvivalEvents.brainTick(actor))callback.cancel();}
}
