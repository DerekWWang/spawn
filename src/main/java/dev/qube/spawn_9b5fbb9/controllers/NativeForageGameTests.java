package dev.qube.spawn_9b5fbb9.controllers;

import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.survival.*;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.*;

public final class NativeForageGameTests {
    private static PlayerInteractEvent.LeftClickBlock click(Player actor,BlockPos pos){var event=new PlayerInteractEvent.LeftClickBlock(actor,pos,Direction.UP,PlayerInteractEvent.LeftClickBlock.Action.START);NeoForge.EVENT_BUS.post(event);return event;}
    private static void work(Player actor){for(int i=0;i<250&&WorldActions.inspectJob(actor).get("active").getAsBoolean();i++){actor.tickCount++;WorldActions.tick(actor);}}
    private static int drops(GameTestHelper h,BlockPos pos,Item item){return h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(2),e->e.getItem().is(item)).stream().mapToInt(e->e.getItem().getCount()).sum();}
    public static void leftGather(GameTestHelper h){
        var actor=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var pos=h.absolutePos(new BlockPos(2,2,1));actor.setPos(Vec3.atBottomCenterOf(pos.west()));h.getLevel().setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);h.getLevel().setBlock(pos,SurvivalContent.LOOSE_STONES.get().defaultBlockState(),3);
        var event=click(actor,pos);if(!event.isCanceled())h.getLevel().destroyBlock(pos,true,actor);
        h.assertTrue(event.isCanceled()&&WorldActions.inspectJob(actor).get("active").getAsBoolean()&&h.getLevel().getBlockState(pos).is(SurvivalContent.LOOSE_STONES.get())&&drops(h,pos,SurvivalContent.item("loose_stone"))==0,"Native left-click resources must start timed gather without instant block loot");
        var speed=new PlayerEvent.BreakSpeed(actor,h.getLevel().getBlockState(pos),1,pos);NeoForge.EVENT_BUS.post(speed);h.assertTrue(speed.getNewSpeed()==0,"Vanilla break-speed polling cannot bypass resource job timing");work(actor);h.assertTrue(h.getLevel().isEmptyBlock(pos)&&drops(h,pos,SurvivalContent.item("loose_stone"))==3&&SurvivalApi.state(actor).skills.experience(Skill.FORAGING)==4,"Completed native gathering must use real resource yield and one practice award");
        var other=pos.south(2);h.getLevel().setBlock(other.below(),Blocks.DIRT.defaultBlockState(),3);h.getLevel().setBlock(other,SurvivalContent.WILD_PLANTS.get().defaultBlockState(),3);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.OAK_LOG));h.assertTrue(WorldActions.craft(actor,"minecraft:oak_planks").success(),"Competing actual handcraft fixture must reserve a real log");h.assertTrue(click(actor,other).isCanceled()&&WorldActions.inspectJob(actor).get("operation").getAsString().equals("craft")&&h.getLevel().getBlockState(other).is(SurvivalContent.WILD_PLANTS.get()),"Native wild-plant breaking cannot run beside an existing manual job");WorldActions.cancel(actor,"Test cleanup");h.succeed();
    }
    public static void fruitHarvestAndDismantle(GameTestHelper h){
        var actor=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var pos=h.absolutePos(new BlockPos(2,2,1));actor.setPos(Vec3.atBottomCenterOf(pos.west()));h.getLevel().setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);h.getLevel().setBlock(pos,SurvivalContent.FRUIT_TREE.get().defaultBlockState(),3);var original=h.getLevel().getServer().getCommandStorage().get(SurvivalApi.CALENDAR).copy();try{var summer=original.copy();summer.putLong("sleepOffset",dev.qube.spawn_9b5fbb9.Config.SEASON_DAYS.get()*24000L-h.getLevel().getServer().overworld().getLevelData().getGameTime());h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,summer);var event=click(actor,pos);if(!event.isCanceled())h.getLevel().destroyBlock(pos,true,actor);h.assertTrue(event.isCanceled()&&WorldActions.inspectJob(actor).get("active").getAsBoolean()&&WorldActions.inspectJob(actor).get("operation").getAsString().equals("harvest")&&drops(h,pos,Items.APPLE)==0,"Native fruit left-click must start timed food harvest, retaining the tree");work(actor);h.assertTrue(h.getLevel().getBlockState(pos).is(SurvivalContent.FRUIT_TREE.get())&&drops(h,pos,Items.APPLE)==6&&SurvivalApi.state(actor).skills.experience(Skill.FORAGING)==6,"Fruit food and practice must follow one completed annual harvest");h.assertTrue(click(actor,pos).isCanceled()&&!WorldActions.inspectJob(actor).get("active").getAsBoolean(),"Repeated food gathering cannot accidentally dismantle the harvested tree");actor.setShiftKeyDown(true);h.assertTrue(click(actor,pos).isCanceled()&&WorldActions.inspectJob(actor).get("operation").getAsString().equals("mine"),"Sneak-left-click must explicitly request timed tree dismantling");work(actor);h.assertTrue(h.getLevel().isEmptyBlock(pos)&&drops(h,pos,Items.APPLE)==6&&SurvivalApi.state(actor).skills.experience(Skill.FORAGING)==6,"Dismantling cannot create another food harvest or practice award");}finally{h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,original);}h.succeed();
    }
}
