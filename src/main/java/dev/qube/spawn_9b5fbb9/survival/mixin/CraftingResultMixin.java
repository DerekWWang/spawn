package dev.qube.spawn_9b5fbb9.survival.mixin;
import dev.qube.spawn_9b5fbb9.survival.CraftingGuard;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ResultSlot.class)
public abstract class CraftingResultMixin {
    @Inject(method="onTake",at=@At("TAIL"))
    private void survival$nextJob(Player player,ItemStack output,CallbackInfo callback){CraftingGuard.completed(player,output);}
}
