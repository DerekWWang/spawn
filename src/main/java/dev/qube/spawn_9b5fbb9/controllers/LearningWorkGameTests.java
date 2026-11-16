package dev.qube.spawn_9b5fbb9.controllers;

import dev.qube.spawn_9b5fbb9.survival.*;
import dev.qube.spawn_9b5fbb9.content.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import static dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill.*;

public final class LearningWorkGameTests {
    public static void miningExposure(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);
        actor.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(SurvivalContent.item("knapped_stone_pickaxe")));
        var pos=h.absolutePos(new BlockPos(2,2,1));h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(WorldActions.start(actor,"mine",pos).success(),"A supplied miner starts actual extraction");
        int ticks=0;while(WorldActions.inspectJob(actor).get("active").getAsBoolean()&&ticks<2000){actor.tickCount++;WorldActions.tick(actor);ticks++;}
        h.assertTrue(h.getLevel().isEmptyBlock(pos),"Real stone was extracted");
        double xp=SurvivalApi.state(actor).skills.experience(MINING);
        h.assertTrue(Math.abs(xp-ticks*.25*.35)<1e-6,"Mining learning follows actual active work exposure");
        h.assertTrue(SurvivalApi.state(actor).skills.experience(FORAGING)==0,"Mining is independent of foraging");
        for(int i=0;i<20;i++){actor.tickCount++;WorldActions.tick(actor);}
        h.assertTrue(SurvivalApi.state(actor).skills.experience(MINING)==xp,"Completed work is never awarded twice");actor.discard();h.succeed();
    }
    public static void passionSnapshot(GameTestHelper h){
        var actor=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var body=SurvivalApi.state(actor);
        body.skills.setPassion(POTTERY,SkillSet.Passion.BURNING);body.skills.setLevel(POTTERY,12);body.injure(SurvivalState.Part.LEFT_ARM,75,false);SurvivalApi.save(actor,body);
        var info=SkillApi.inspect(body).getAsJsonObject("pottery");
        h.assertTrue(info.get("maxLevel").getAsInt()==20&&info.get("passion").getAsString().equals("burning"),"Queries expose real scale and interests");
        h.assertTrue(info.get("learningMultiplier").getAsDouble()==1.5&&info.get("effectiveLevel").getAsDouble()<12,"Queries distinguish learning and bodily capacity");h.succeed();
    }
}
