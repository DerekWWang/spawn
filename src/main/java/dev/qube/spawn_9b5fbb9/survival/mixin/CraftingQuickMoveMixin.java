package dev.qube.spawn_9b5fbb9.survival.mixin;
import dev.qube.spawn_9b5fbb9.survival.CraftingGuard;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin({CraftingMenu.class,InventoryMenu.class})
public abstract class CraftingQuickMoveMixin {
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true)
    private void survival$noInstantBatch(Player player,int index,CallbackInfoReturnable<ItemStack> callback){
        if(index==0&&!CraftingGuard.mayTake(player,((AbstractCraftingMenu)(Object)this).getResultSlot()))callback.setReturnValue(ItemStack.EMPTY);
    }
}
