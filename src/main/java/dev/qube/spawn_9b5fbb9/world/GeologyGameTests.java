package dev.qube.spawn_9b5fbb9.world;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.material.rule.OreVeinRule;

public final class GeologyGameTests {
    public static void oldVeinsDisabled(GameTestHelper h){
        var replaced=new OreVeinRule(Blocks.COPPER_ORE.defaultBlockState(),Blocks.RAW_COPPER_BLOCK.defaultBlockState(),Blocks.GRANITE.defaultBlockState(),.02f,null,null,null);
        try{h.assertTrue(replaced.compile(null).tryApply(0,64,0)==null,"The replaced copper rule cannot create ore outside regional districts");}
        catch(NullPointerException oldPath){h.fail("The old copper vein rule still compiles density samplers instead of yielding to regional geology");}
        h.succeed();
    }
}
