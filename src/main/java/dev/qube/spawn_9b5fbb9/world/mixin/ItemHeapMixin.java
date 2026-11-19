package dev.qube.spawn_9b5fbb9.world.mixin;
import dev.qube.spawn_9b5fbb9.content.ItemAging;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** A heap of ore is one item entity showing a stack over a count of the rest. Whatever takes the stack, a hand, a
 * hopper or a mob, ends by discarding the entity, and there is no event for that: this is where the rest is uncovered. */
@Mixin(Entity.class)
public abstract class ItemHeapMixin {
    @Inject(method="remove",at=@At("HEAD"))
    private void survival$heapStays(Entity.RemovalReason reason,CallbackInfo ci){if(reason==Entity.RemovalReason.DISCARDED&&(Object)this instanceof ItemEntity item)ItemAging.taken(item);}
}
