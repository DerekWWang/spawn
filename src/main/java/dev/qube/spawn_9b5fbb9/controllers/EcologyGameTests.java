package dev.qube.spawn_9b5fbb9.controllers;

import dev.qube.spawn_9b5fbb9.content.*;
import dev.qube.spawn_9b5fbb9.survival.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class EcologyGameTests {
    public static void singleSource(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);var pos=h.absolutePos(new BlockPos(2,2,1));
        h.getLevel().setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);h.getLevel().setBlock(pos,SurvivalContent.WILD_PLANTS.get().defaultBlockState(),3);
        h.assertTrue(WorldActions.forage(actor,pos).success(),"Actual legacy forage source is usable");
        for(int i=0;i<200&&WorldActions.inspectJob(actor).get("active").getAsBoolean();i++){actor.tickCount++;WorldActions.tick(actor);}
        var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,actor.getBoundingBox().inflate(4));
        h.assertTrue(drops.stream().noneMatch(e->e.getItem().is(SurvivalContent.item("rice_seeds"))||e.getItem().is(SurvivalContent.item("maize_seeds"))||e.getItem().is(Items.PUMPKIN_SEEDS)),"One unidentified wild plant must not manufacture seeds of unrelated species");
        h.assertTrue(h.getLevel().isEmptyBlock(pos)&&!WorldActions.forage(actor,pos).success(),"The consumed source remains finite");actor.discard();h.succeed();
    }
    public static void rocksAreRocks(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);var pos=h.absolutePos(new BlockPos(2,2,1));
        h.getLevel().setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);h.getLevel().setBlock(pos,SurvivalContent.LOOSE_STONES.get().defaultBlockState(),3);WorldActions.forage(actor,pos);
        for(int i=0;i<200&&WorldActions.inspectJob(actor).get("active").getAsBoolean();i++){actor.tickCount++;WorldActions.tick(actor);}
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,actor.getBoundingBox().inflate(4)).stream().noneMatch(e->e.getItem().is(Items.STICK)),"Rock gathering never creates woody debris");actor.discard();h.succeed();
    }
}
