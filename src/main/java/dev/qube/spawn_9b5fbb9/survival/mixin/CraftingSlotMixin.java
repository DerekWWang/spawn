package dev.qube.spawn_9b5fbb9.survival.mixin;
import dev.qube.spawn_9b5fbb9.survival.CraftingGuard;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Slot.class)
public abstract class CraftingSlotMixin {
    @Inject(method="mayPickup",at=@At("HEAD"),cancellable=true)
    private void survival$workRequired(Player player,CallbackInfoReturnable<Boolean> callback){if(!CraftingGuard.mayTake(player,(Slot)(Object)this))callback.setReturnValue(false);}
}
