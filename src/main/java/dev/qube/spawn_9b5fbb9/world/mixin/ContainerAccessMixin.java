package dev.qube.spawn_9b5fbb9.world.mixin;
import dev.qube.spawn_9b5fbb9.world.NaturalWorldRules;
import dev.qube.spawn_9b5fbb9.content.ItemAging;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(BaseContainerBlockEntity.class)
public abstract class ContainerAccessMixin {
    @Inject(method="getItem",at=@At("RETURN"))
    private void survival$access(int slot,CallbackInfoReturnable<ItemStack> cir){clean(cir.getReturnValue());}
    @Inject(method="setItem(ILnet/minecraft/world/item/ItemStack;Z)V",at=@At("HEAD"))
    private void survival$insert(int slot,ItemStack stack,boolean transaction,CallbackInfo ci){clean(stack);}
    private void clean(ItemStack stack){if(((BaseContainerBlockEntity)(Object)this).getLevel() instanceof ServerLevel level){NaturalWorldRules.sanitize(stack);ItemAging.update(stack,level,ItemAging.storageRate());}}
}
