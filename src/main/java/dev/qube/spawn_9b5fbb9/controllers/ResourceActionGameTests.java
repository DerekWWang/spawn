package dev.qube.spawn_9b5fbb9.controllers;

import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.survival.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public final class ResourceActionGameTests {
    public static void visibleEvidence(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);
        var exposed=h.absolutePos(new BlockPos(2,2,1));var hidden=h.absolutePos(new BlockPos(4,2,1));
        h.getLevel().setBlock(exposed,SurvivalContent.MALACHITE_ORE.get().defaultBlockState(),3);h.getLevel().setBlock(hidden,SurvivalContent.CASSITERITE_ORE.get().defaultBlockState(),3);
        for(int y=1;y<5;y++)for(int z=0;z<4;z++)h.setBlock(3,y,z,Blocks.STONE);
        var report=ResourceActions.discover(actor,16);var entries=report.getAsJsonArray("resources");boolean found=false;
        for(var value:entries){var entry=value.getAsJsonObject();if(entry.get("x").getAsInt()==exposed.getX()&&entry.get("y").getAsInt()==exposed.getY()&&entry.get("z").getAsInt()==exposed.getZ())found=true;
            h.assertFalse(entry.get("x").getAsInt()==hidden.getX()&&entry.get("y").getAsInt()==hidden.getY()&&entry.get("z").getAsInt()==hidden.getZ(),"Discovery cannot disclose hidden ore");}
        h.assertTrue(found&&entries.size()<=64,"Discovery returns actual visible resources within a bounded list");
        h.assertTrue(ResourceActions.prospect(actor,exposed).success(),"A real nearby outcrop can be interpreted");
        h.assertFalse(ResourceActions.prospect(actor,hidden).success(),"Prospecting cannot see through stone");
        h.assertFalse(ResourceActions.prospect(actor,exposed.east(12)).success(),"Prospecting cannot operate remotely");
        h.assertTrue(SurvivalApi.state(actor).skills.experience(SkillSet.Skill.MINING)==0,"Inspecting resources is not an extraction XP farm");
        actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("hematite")));
        h.assertTrue(ResourceActions.prospect(actor,null).success(),"A genuinely held extracted sample can be interpreted");actor.discard();h.succeed();
    }
}
