package dev.qube.spawn_9b5fbb9.survival.mixin;
import dev.qube.spawn_9b5fbb9.survival.CraftingGuard;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin({CraftingMenu.class,InventoryMenu.class,MerchantMenu.class})
public abstract class CraftingQuickMoveMixin {
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true)
    private void survival$noInstantBatch(Player player,int index,CallbackInfoReturnable<ItemStack> callback){
        Object menu=this;
        if(menu instanceof AbstractCraftingMenu crafting&&index==0&&!CraftingGuard.mayTake(player,crafting.getResultSlot()))callback.setReturnValue(ItemStack.EMPTY);
        if(menu instanceof MerchantMenu merchant&&index==2&&!CraftingGuard.mayTake(player,merchant.getSlot(2)))callback.setReturnValue(ItemStack.EMPTY);
    }
}
