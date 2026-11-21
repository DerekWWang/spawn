package dev.qube.spawn_9b5fbb9.controllers;

import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.survival.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public final class ResourceActionGameTests {
    /** A survey of open ground lists the nearest things in sight, nearest first, no more than eight of a kind, and says when there were more. */
    public static void nearestFirstAndBounded(GameTestHelper h){
        for(int x=0;x<13;x++)for(int z=0;z<13;z++)h.setBlock(x,1,z,Blocks.SAND);
        h.setBlock(12,2,12,SurvivalContent.LOOSE_STONES.get());
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(6,2,6));actor.setNoAi(true);SurvivalApi.enroll(actor);
        var center=actor.blockPosition();var report=ResourceActions.discover(actor,16);var entries=report.getAsJsonArray("resources");
        int sand=0;boolean stones=false;for(var value:entries){var id=value.getAsJsonObject().get("identity").getAsString();if(id.equals("sand"))sand++;if(id.equals("loose_stone"))stones=true;}
        h.assertTrue(sand==ResourceActions.PER_KIND&&report.get("truncated").getAsBoolean()&&report.get("moreOfKind").toString().contains("sand"),"A field of sand is listed only up to the limit of a kind and marked as more: "+sand);
        h.assertTrue(stones,"The sand underfoot must not hide the loose stones across the field: "+report);
        double last=-1;
        for(var value:entries){var entry=value.getAsJsonObject();double distance=new BlockPos(entry.get("x").getAsInt(),entry.get("y").getAsInt(),entry.get("z").getAsInt()).distSqr(center);
            h.assertTrue(distance>=last,"The list runs from nearest to farthest");last=distance;
            h.assertTrue(ResourceActions.visible(actor,new BlockPos(entry.get("x").getAsInt(),entry.get("y").getAsInt(),entry.get("z").getAsInt())),"Everything listed is in sight");}
        var first=entries.get(0).getAsJsonObject();
        h.assertTrue(new BlockPos(first.get("x").getAsInt(),first.get("y").getAsInt(),first.get("z").getAsInt()).equals(center.below()),"The sand underfoot comes first");
        h.assertTrue(last<=16*16,"Nothing beyond the radius is listed");
        actor.discard();h.succeed();
    }
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
