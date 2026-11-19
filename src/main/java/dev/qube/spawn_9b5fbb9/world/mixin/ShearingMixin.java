package dev.qube.spawn_9b5fbb9.world.mixin;
import dev.qube.spawn_9b5fbb9.agriculture.Husbandry;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Sheep.class)
public abstract class ShearingMixin {
    @Inject(method="readyForShearing",at=@At("HEAD"),cancellable=true)
    private void survival$woolCooldown(CallbackInfoReturnable<Boolean> cir){Sheep sheep=(Sheep)(Object)this;if(sheep.level() instanceof ServerLevel level){long now=SurvivalApi.calendar(level);var data=sheep.getPersistentData();if(!Husbandry.mature(sheep,now)||Husbandry.woolRecovery(sheep,now)>0||data.getDoubleOr("nutrition",75)<50||!Husbandry.watered(sheep))cir.setReturnValue(false);}}
    @Inject(method="shear",at=@At("TAIL"))
    private void survival$sheared(CallbackInfo ci){Sheep sheep=(Sheep)(Object)this;if(sheep.level() instanceof ServerLevel level)Husbandry.beginWoolRecovery(sheep,SurvivalApi.calendar(level));}
}
