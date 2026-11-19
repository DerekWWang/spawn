package dev.qube.spawn_9b5fbb9.world.mixin;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Cancels the common generator entry point, including future structure sets and dimensions. */
@Mixin(ChunkGenerator.class)
public abstract class StructureMixin {
    @Inject(method="createStructures", at=@At("HEAD"), cancellable=true)
    private void survival$noStructures(CallbackInfo ci) { ci.cancel(); }
}
