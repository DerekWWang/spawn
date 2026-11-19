package dev.qube.spawn_9b5fbb9.world.mixin;
import net.minecraft.world.level.levelgen.feature.MonsterRoomFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(MonsterRoomFeature.class)
public abstract class DungeonMixin {
    @Inject(method="place",at=@At("HEAD"),cancellable=true)
    private void survival$noDungeons(CallbackInfoReturnable<Boolean> cir){cir.setReturnValue(false);}
}
