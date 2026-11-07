package dev.qube.spawn_9b5fbb9.survival.mixin;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void survivalOwnsExpenditure(ServerPlayer player,CallbackInfo callback){
        if(!SurvivalApi.exempt(player))callback.cancel();
    }
}
