package dev.qube.spawn_9b5fbb9.world.mixin;
import dev.qube.spawn_9b5fbb9.world.NaturalWorldRules;
import dev.qube.spawn_9b5fbb9.content.ItemAging;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(BlockEntity.class)
public abstract class ContainerLoadMixin {
    @Inject(method="setLevel",at=@At("TAIL"))
    private void survival$loaded(Level world,CallbackInfo ci){if(world instanceof ServerLevel level&&(Object)this instanceof Container container){for(int i=0;i<container.getContainerSize();i++){var stack=container.getItem(i);NaturalWorldRules.sanitize(stack);ItemAging.update(stack,level,ItemAging.storageRate());}}}
}
