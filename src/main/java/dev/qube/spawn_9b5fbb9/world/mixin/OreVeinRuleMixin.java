package dev.qube.spawn_9b5fbb9.world.mixin;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.OreVeinRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The old density-based copper/iron rules must not bypass the regional deposits. */
@Mixin(OreVeinRule.class)
public abstract class OreVeinRuleMixin {
    @Inject(method="compile",at=@At("HEAD"),cancellable=true)
    private void survival$regionalVeins(MaterialRuleContext context,CallbackInfoReturnable<RuleEvaluator> callback){
        var ore=((OreVeinRule)(Object)this).oreBlock();
        if(ore.is(Blocks.COPPER_ORE)||ore.is(Blocks.DEEPSLATE_COPPER_ORE)||ore.is(Blocks.IRON_ORE)||ore.is(Blocks.DEEPSLATE_IRON_ORE))callback.setReturnValue((x,y,z)->null);
    }
}
