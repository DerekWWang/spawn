package dev.qube.spawn_9b5fbb9.world.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** The game's furnace, blast furnace and smoker do no work. Opening one was already refused; a hopper needs nobody to
 * open anything, and through one sand still became glass and logs charcoal with no kiln, fuel rule or skill. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceMixin {
    @Inject(method="serverTick",at=@At("HEAD"),cancellable=true)
    private static void survival$cold(ServerLevel level,BlockPos pos,BlockState state,AbstractFurnaceBlockEntity entity,CallbackInfo ci){ci.cancel();}
}
