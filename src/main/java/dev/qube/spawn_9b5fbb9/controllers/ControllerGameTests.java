package dev.qube.spawn_9b5fbb9.controllers;

import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import dev.qube.spawn_9b5fbb9.survival.SurvivalState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

/** Real NPCs, world blocks and registered recipes; no Player proxy or synthetic supplies. */
public final class ControllerGameTests {
    public static void forgeBlocksWorldWork(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);var forge=h.absolutePos(new BlockPos(2,1,2));h.getLevel().setBlock(forge,SurvivalContent.FORGE.get().defaultBlockState(),3);var station=(dev.qube.spawn_9b5fbb9.content.WorkstationEntity)h.getLevel().getBlockEntity(forge);station.fuel=10000;actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("iron_bloom"),4));h.assertTrue(dev.qube.spawn_9b5fbb9.content.Production.start(actor,station,"work_bloom").startsWith("Started"),"An actual fueled supplied manual forge must claim its worker");var target=h.absolutePos(new BlockPos(2,2,1));h.getLevel().setBlock(target,Blocks.DIRT.defaultBlockState(),3);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.OAK_LOG));double hunger=SurvivalApi.state(actor).hunger;h.assertFalse(WorldActions.start(actor,"mine",target).success(),"A manual forge worker cannot simultaneously begin world labor");h.assertFalse(WorldActions.craft(actor,"minecraft:oak_planks").success(),"A manual forge worker cannot simultaneously reserve handcraft ingredients");h.assertTrue(actor.getMainHandItem().getCount()==1&&h.getLevel().getBlockState(target).is(Blocks.DIRT)&&SurvivalApi.state(actor).hunger==hunger&&!WorldActions.inspectJob(actor).get("active").getAsBoolean(),"Rejected competing labor cannot consume inventory, hunger, or world blocks");
        // Reproduce a legacy save with overlapping claims, then finish its existing world job.
        station.worker="";h.assertTrue(WorldActions.start(actor,"mine",target).success(),"Legacy world work fixture must start before restoring its older forge claim");station.worker=actor.getUUID().toString();for(int i=0;i<100&&WorldActions.inspectJob(actor).get("active").getAsBoolean();i++){actor.tickCount++;WorldActions.tick(actor);}h.assertFalse(WorldActions.inspectJob(actor).get("active").getAsBoolean(),"Existing world work must continue without a forge/world deadlock");h.assertTrue(actor.getPersistentData().getLongOr("survivalManualWorkAt",-1)==h.getLevel().getGameTime()&&!dev.qube.spawn_9b5fbb9.survival.LaborGuard.mayAdvanceForge(actor,station),"A forge cannot retroactively claim elapsed time at the exact world-work completion boundary");actor.discard();h.getLevel().setBlock(target,Blocks.DIRT.defaultBlockState(),3);
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1,2,1))));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.OAK_LOG));var menu=new net.minecraft.world.inventory.CraftingMenu(1,player.getInventory());player.containerMenu=menu;menu.getInputGridSlots().getFirst().set(new ItemStack(Items.OAK_LOG));var grid=net.minecraft.world.item.crafting.CraftingInput.of(1,1,java.util.List.of(menu.getInputGridSlots().getFirst().getItem()));var recipe=h.getLevel().recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,grid,h.getLevel()).orElseThrow();menu.getResultSlot().set(recipe.value().assemble(grid));dev.qube.spawn_9b5fbb9.survival.CraftingGuard.tick(player,SurvivalApi.state(player));h.assertTrue(dev.qube.spawn_9b5fbb9.survival.LaborGuard.gridBusy(player),"A real matching grid with credited work must claim the player's hands");h.assertFalse(WorldActions.start(player,"mine",target).success()||WorldActions.craft(player,"minecraft:oak_planks").success(),"Credited grid work must exclude simultaneous field labor and API handcrafting");h.assertTrue(player.getMainHandItem().getCount()==1&&menu.getInputGridSlots().getFirst().getItem().getCount()==1&&!WorldActions.inspectJob(player).get("active").getAsBoolean(),"Rejecting conflicting grid work must preserve both actual ingredient sources");h.succeed();
    }
    public static void nativePlantingSubstrates(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);var original=h.getLevel().getServer().getCommandStorage().get(SurvivalApi.CALENDAR).copy();try{var spring=original.copy();spring.putLong("sleepOffset",-h.getLevel().getServer().overworld().getLevelData().getGameTime());h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,spring);var cocoa=h.absolutePos(new BlockPos(2,2,1));actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.COCOA_BEANS,4));
            for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL){h.getLevel().setBlock(cocoa,Blocks.AIR.defaultBlockState(),3);for(var side:net.minecraft.core.Direction.Plane.HORIZONTAL)h.getLevel().setBlock(cocoa.relative(side),Blocks.AIR.defaultBlockState(),3);h.getLevel().setBlock(cocoa.relative(direction),Blocks.JUNGLE_LOG.defaultBlockState(),3);h.assertTrue(WorldActions.start(actor,"sow",cocoa).success(),"NPC cocoa must recognize every actual adjacent jungle-log orientation");for(int i=0;i<180;i++){actor.tickCount++;WorldActions.tick(actor);}h.assertTrue(h.getLevel().getBlockState(cocoa).is(Blocks.COCOA)&&h.getLevel().getBlockState(cocoa).getValue(CocoaBlock.FACING)==direction,"Placed cocoa must face its actual support log");}
            h.assertTrue(actor.getMainHandItem().isEmpty(),"Four planted cocoa pods must consume exactly four physical beans");h.getLevel().setBlock(cocoa,Blocks.AIR.defaultBlockState(),3);for(var side:net.minecraft.core.Direction.Plane.HORIZONTAL)h.getLevel().setBlock(cocoa.relative(side),Blocks.AIR.defaultBlockState(),3);
            var cane=h.absolutePos(new BlockPos(3,2,1));h.getLevel().setBlock(cane.below(),Blocks.SAND.defaultBlockState(),3);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SUGAR_CANE,2));h.assertFalse(WorldActions.start(actor,"sow",cane).success(),"Cane without native water-adjacent substrate must be denied");h.assertTrue(actor.getMainHandItem().getCount()==2,"Invalid cane placement cannot reserve a seed");h.getLevel().setBlock(cane.below().east(),Blocks.WATER.defaultBlockState(),3);h.assertTrue(WorldActions.start(actor,"sow",cane).success(),"NPC cane must plant on actual water-adjacent sand without farmland");for(int i=0;i<180;i++){actor.tickCount++;WorldActions.tick(actor);}h.assertTrue(h.getLevel().getBlockState(cane).is(Blocks.SUGAR_CANE)&&actor.getMainHandItem().getCount()==1,"Cane planting must place one real block and consume one real item");
        }finally{h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,original);}actor.discard();h.succeed();
    }
    public static void miningCostOnce(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));var pos=h.absolutePos(new BlockPos(2,2,1));h.getLevel().setBlock(pos,Blocks.IRON_ORE.defaultBlockState(),3);double before=SurvivalApi.state(actor).hunger;h.assertTrue(WorldActions.start(actor,"mine",pos).success(),"Real NPC ore mining must start");int worked=0;while(WorldActions.inspectJob(actor).get("active").getAsBoolean()&&worked<300){actor.tickCount++;WorldActions.tick(actor);worked++;}h.assertTrue(h.getLevel().isEmptyBlock(pos),"Mining must actually finish");double npcBlockCost=before-SurvivalApi.state(actor).hunger-worked*.0005;
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var nativePos=h.absolutePos(new BlockPos(3,2,1));h.getLevel().setBlock(nativePos,Blocks.DIRT.defaultBlockState(),3);double playerBefore=SurvivalApi.state(player).hunger;h.getLevel().destroyBlock(nativePos,true,player);double nativeBlockCost=playerBefore-SurvivalApi.state(player).hunger;
        h.assertTrue(Math.abs(nativeBlockCost-.08)<1e-6&&Math.abs(npcBlockCost-nativeBlockCost)<1e-6,"Controller mining must charge the native .08 block cost exactly once, plus actual manual labor only");actor.discard();h.succeed();
    }
    public static void adventurePermissions(GameTestHelper h){
        var actor=h.makeMockPlayer(net.minecraft.world.level.GameType.ADVENTURE);net.minecraft.world.level.GameType.ADVENTURE.updatePlayerAbilities(actor.getAbilities());var pos=h.absolutePos(new BlockPos(2,2,1));actor.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1,2,1))));h.getLevel().setBlock(pos,Blocks.IRON_ORE.defaultBlockState(),3);var pick=new ItemStack(Items.IRON_PICKAXE);actor.setItemInHand(InteractionHand.MAIN_HAND,pick);double hunger=SurvivalApi.state(actor).hunger;
        h.assertFalse(WorldActions.start(actor,"mine",pos).success(),"Adventure mining without CAN_BREAK must be rejected");h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.IRON_ORE)&&pick.getDamageValue()==0&&pick.getCount()==1&&SurvivalApi.state(actor).hunger==hunger,"Rejected adventure work cannot mutate world, inventory, durability, or hunger");
        var canBreak=new net.minecraft.world.item.AdventureModePredicate(java.util.List.of(net.minecraft.advancements.predicates.BlockPredicate.Builder.block().of(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),Blocks.IRON_ORE).build()));pick.set(net.minecraft.core.component.DataComponents.CAN_BREAK,canBreak);h.assertTrue(WorldActions.start(actor,"mine",pos).success(),"Actual CAN_BREAK components must permit the matching adventure block");WorldActions.cancel(actor,"Permission test");
        h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);h.getLevel().setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE,7),3);var seed=new ItemStack(Items.WHEAT_SEEDS);actor.setItemInHand(InteractionHand.MAIN_HAND,seed);h.assertFalse(WorldActions.start(actor,"sow",pos).success(),"Adventure sowing without CAN_PLACE_ON must be rejected before reserving seeds");h.assertTrue(seed.getCount()==1&&h.getLevel().isEmptyBlock(pos)&&!WorldActions.inspectJob(actor).get("active").getAsBoolean(),"Denied adventure sowing must leave its physical seed and field unchanged");
        var original=h.getLevel().getServer().getCommandStorage().get(SurvivalApi.CALENDAR).copy();try{var spring=original.copy();spring.putLong("sleepOffset",-h.getLevel().getServer().overworld().getLevelData().getGameTime());h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,spring);seed.set(net.minecraft.core.component.DataComponents.CAN_PLACE_ON,new net.minecraft.world.item.AdventureModePredicate(java.util.List.of(net.minecraft.advancements.predicates.BlockPredicate.Builder.block().of(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),Blocks.FARMLAND).build())));h.assertTrue(WorldActions.start(actor,"sow",pos).success(),"A physical CAN_PLACE_ON seed must authorize its matching adventure substrate");WorldActions.cancel(actor,"Permission test");}finally{h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,original);}
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.OAK_LOG));h.assertTrue(WorldActions.craft(actor,"minecraft:oak_planks").success(),"Adventure restrictions must not prohibit ordinary non-world crafting");WorldActions.cancel(actor,"Test cleanup");h.succeed();
    }
    public static void planningBounded(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);var first=WorldActions.planning(actor,0);h.assertTrue(first.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=WorldActions.PLANNING_BYTE_LIMIT,"Controller planning responses must stay within the explicit network byte budget");h.assertTrue(first.has("totalRecipes")&&first.has("nextOffset")&&first.has("crops"),"Planning must report pagination and seasonal constraints");h.assertTrue(first.getAsJsonArray("craftingRecipes").size()+first.getAsJsonArray("processRecipes").size()<=64,"A planner page must contain at most64recipes");int next=first.get("nextOffset").getAsInt();h.assertTrue(next>0,"The first page must make forward progress through registered recipes");var second=WorldActions.planning(actor,next);h.assertTrue(second.get("offset").getAsInt()==next&&second.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=WorldActions.PLANNING_BYTE_LIMIT,"Following nextOffset must produce another bounded page");var recipe=WorldActions.recipeInfo(actor,"spawn_9b5fbb9:knapped_stone_pickaxe",0);h.assertTrue(recipe.has("ingredientSlots")&&recipe.get("craftingTableRequired").getAsBoolean(),"Focused recipe information must include real ingredients and table constraints");var process=WorldActions.recipeInfo(actor,"fire_pot",0);h.assertTrue(process.get("kind").getAsString().equals("process")&&process.has("heatTicks")&&process.has("inputCount"),"Planning must expose actual kiln/forge resource and heat requirements");actor.discard();h.succeed();
    }
    public static void farmAdapters(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);var cow=h.spawn(EntityTypes.COW,new BlockPos(2,2,1));cow.setNoAi(true);cow.setBaby(true);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT,2));h.assertTrue(WorldActions.livestock(actor,cow).success()&&actor.getMainHandItem().getCount()==1,"NPC livestock adapter must consume exactly one real feed portion");
        var hay=h.absolutePos(new BlockPos(1,1,2));h.getLevel().setBlock(hay,Blocks.HAY_BLOCK.defaultBlockState(),3);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("fertilized_egg")));h.assertTrue(WorldActions.nest(actor,hay).success()&&actor.getMainHandItem().isEmpty(),"NPC nest adapter must place the actual fertilized egg");
        var trough=h.absolutePos(new BlockPos(2,1,2));h.getLevel().setBlock(trough,SurvivalContent.TROUGH.get().defaultBlockState(),3);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT));h.assertTrue(WorldActions.trough(actor,trough).success()&&actor.getMainHandItem().isEmpty(),"NPC trough adapter must move real feed into the workstation");h.assertTrue(((dev.qube.spawn_9b5fbb9.content.WorkstationEntity)h.getLevel().getBlockEntity(trough)).getPersistentData().getDoubleOr("feed",0)==8,"Trough adapter must stock exactly the declared feed quantity");cow.discard();actor.discard();h.succeed();
    }
    public static void noOfflineLabor(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.OAK_LOG));h.assertTrue(WorldActions.craft(actor,"minecraft:oak_planks").success(),"Real log must start work");
        var original=h.getLevel().getServer().getCommandStorage().get(SurvivalApi.CALENDAR).copy();try{var jumped=original.copy();jumped.putLong("sleepOffset",original.getLongOr("sleepOffset",0)+80*24000L);h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,jumped);actor.tickCount++;WorldActions.tick(actor);h.assertTrue(WorldActions.inspectJob(actor).get("progress").getAsDouble()<=1,"Eighty calendar days of sleep/unloading cannot credit more than the actual one loaded work tick");WorldActions.cancel(actor,"Test cleanup");}finally{h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,original);}actor.discard();h.succeed();
    }
    public static void sowPhysicalSeed(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT_SEEDS));var pos=h.absolutePos(new BlockPos(2,2,1));h.getLevel().setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE,7),3);
        var original=h.getLevel().getServer().getCommandStorage().get(SurvivalApi.CALENDAR).copy();try{var spring=original.copy();spring.putLong("sleepOffset",-h.getLevel().getServer().overworld().getLevelData().getGameTime());h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,spring);h.assertTrue(WorldActions.start(actor,"sow",pos).success(),"Spring wheat must permit a physical seed");h.assertTrue(actor.getMainHandItem().isEmpty(),"The one seed must be reserved, not copied");for(int i=0;i<180;i++){actor.tickCount++;WorldActions.tick(actor);}h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.WHEAT),"Real loaded planting work must place wheat");var record=dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.records(h.getLevel()).getCompoundOrEmpty(Long.toString(pos.asLong()));h.assertTrue(record.getStringOr("type","").equals("minecraft:wheat")&&record.getLongOr("last",-1)==0,"Controller sowing must register its biological start time");}finally{h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,original);}actor.discard();h.succeed();
    }
    public static void frostHarvest(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);var pos=h.absolutePos(new BlockPos(2,2,1));h.getLevel().setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE,7),3);h.getLevel().setBlock(pos,SurvivalContent.CROPS.get("rice").get().defaultBlockState().setValue(CropBlock.AGE,7),3);
        var original=h.getLevel().getServer().getCommandStorage().get(SurvivalApi.CALENDAR).copy();try{long winter=3L*dev.qube.spawn_9b5fbb9.Config.SEASON_DAYS.get()*24000+100;var shifted=original.copy();shifted.putLong("sleepOffset",winter-h.getLevel().getServer().overworld().getLevelData().getGameTime());h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,shifted);var all=dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.records(h.getLevel());var record=new net.minecraft.nbt.CompoundTag();record.putLong("growth",0);record.putLong("last",winter-1);all.put(Long.toString(pos.asLong()),record);h.assertTrue(WorldActions.start(actor,"harvest",pos).success(),"Visible crop must enter the shared harvest path");for(int i=0;i<180;i++){actor.tickCount++;WorldActions.tick(actor);}h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2),e->e.getItem().is(SurvivalContent.item("rice_grain"))).isEmpty(),"A stale visible AGE7 crop killed by frost must not create fresh produce");}finally{h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,original);}actor.discard();h.succeed();
    }
    public static void bootstrapHoe(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("knapped_stone_hoe")));var pos=h.absolutePos(new BlockPos(2,1,1));h.getLevel().setBlock(pos,Blocks.DIRT.defaultBlockState(),3);h.getLevel().setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(WorldActions.start(actor,"till",pos).success(),"The first-day custom stone hoe must prepare soil for an NPC");for(int i=0;i<250;i++){actor.tickCount++;WorldActions.tick(actor);}h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.FARMLAND),"Timed hoe work must create actual farmland");h.assertTrue(actor.getMainHandItem().getDamageValue()==1,"Tilling must consume one tool durability");actor.discard();h.succeed();
    }
    public static void gatherTimed(GameTestHelper h) {
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);
        var pos=h.absolutePos(new BlockPos(2,2,1));h.getLevel().setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);h.getLevel().setBlock(pos,SurvivalContent.LOOSE_STONES.get().defaultBlockState(),3);
        h.assertTrue(WorldActions.start(actor,"gather",pos).success(),"Enrolled NPC must be able to gather a real patch");
        h.assertTrue(h.getLevel().getBlockState(pos).is(SurvivalContent.LOOSE_STONES.get()),"Starting work must not instantly grant resources");
        var state=SurvivalApi.state(actor);state.anatomy[SurvivalState.Part.BRAIN.ordinal()]=0;SurvivalApi.save(actor,state);
        for(int i=0;i<100;i++){actor.tickCount++;WorldActions.tick(actor);}
        h.assertTrue(h.getLevel().getBlockState(pos).is(SurvivalContent.LOOSE_STONES.get()),"Mental incapacity must pause NPC labor");
        state.anatomy[SurvivalState.Part.BRAIN.ordinal()]=100;SurvivalApi.save(actor,state);
        for(int i=0;i<100;i++){actor.tickCount++;WorldActions.tick(actor);}
        h.assertTrue(h.getLevel().isEmptyBlock(pos),"Actual supplied loaded work ticks must finish gathering");
        h.assertTrue(SurvivalApi.state(actor).hunger<100,"Gathering must expend hunger");actor.discard();h.succeed();
    }
    public static void mineValidated(GameTestHelper h) {
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);
        var pos=h.absolutePos(new BlockPos(2,2,1));h.getLevel().setBlock(pos,Blocks.IRON_ORE.defaultBlockState(),3);
        h.assertFalse(WorldActions.start(actor,"mine",pos).success(),"Bare hands cannot harvest iron ore");
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        h.assertTrue(WorldActions.start(actor,"mine",pos).success(),"Correct physical tool must allow mining");
        actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);actor.tickCount++;WorldActions.tick(actor);
        h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.IRON_ORE),"Changing tools must cancel rather than complete mining");
        h.assertFalse(WorldActions.inspectJob(actor).get("active").getAsBoolean(),"Tool loss must clear job");actor.discard();h.succeed();
    }
    public static void mineDropsAndWear(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        var pos=h.absolutePos(new BlockPos(2,2,1));h.getLevel().setBlock(pos,Blocks.IRON_ORE.defaultBlockState(),3);h.assertTrue(WorldActions.start(actor,"mine",pos).success(),"Physical iron pickaxe must start ore work");
        for(int i=0;i<200;i++){actor.tickCount++;WorldActions.tick(actor);}
        h.assertTrue(h.getLevel().isEmptyBlock(pos),"Timed valid work must remove the actual ore block");
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2),e->e.getItem().is(Items.RAW_IRON)).stream().mapToInt(e->e.getItem().getCount()).sum()==1,"Ore loot must receive the real harvest tool");
        h.assertTrue(actor.getMainHandItem().getDamageValue()==1,"Mining must use real tool durability once");actor.discard();h.succeed();
    }
    public static void craftSharedSupplies(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("stone_edge"),3));actor.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(SurvivalContent.item("cordage")));
        var pos=h.absolutePos(new BlockPos(2,2,1));h.getLevel().setBlock(pos,Blocks.CHEST.defaultBlockState(),3);var chest=(net.minecraft.world.Container)h.getLevel().getBlockEntity(pos);chest.setItem(0,new ItemStack(Items.STICK,2));
        h.assertFalse(WorldActions.craft(actor,"spawn_9b5fbb9:knapped_stone_pickaxe").success(),"NPC three-by-three recipes require the same real crafting table as human work");h.getLevel().setBlock(h.absolutePos(new BlockPos(1,1,2)),Blocks.CRAFTING_TABLE.defaultBlockState(),3);
        h.assertTrue(WorldActions.craft(actor,"spawn_9b5fbb9:knapped_stone_pickaxe").success(),"NPC must use a third actual material kind from a nearby chest");
        h.assertTrue(chest.getItem(0).isEmpty()&&actor.getMainHandItem().isEmpty()&&actor.getOffhandItem().isEmpty(),"Each ingredient count must leave its true server source");
        for(int i=0;i<200;i++){actor.tickCount++;WorldActions.tick(actor);}
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,actor.getBoundingBox().inflate(3),e->e.getItem().is(SurvivalContent.item("knapped_stone_pickaxe"))).size()==1,"The registered shaped recipe must assemble once after work");actor.discard();h.succeed();
    }
    public static void craftReserved(GameTestHelper h) {
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.OAK_LOG));
        h.assertTrue(WorldActions.craft(actor,"minecraft:oak_planks").success(),"Actual registered one-input crafting recipe must start for NPC");
        h.assertTrue(actor.getMainHandItem().isEmpty(),"Job must reserve the real log immediately");
        actor.setPos(actor.position().add(5,0,0));h.getLevel().getChunkAt(actor.blockPosition());actor.tickCount++;WorldActions.tick(actor);
        h.assertFalse(WorldActions.inspectJob(actor).get("active").getAsBoolean(),"Leaving work range must cancel crafting");
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,actor.getBoundingBox().inflate(2),e->e.getItem().is(Items.OAK_LOG)).stream().mapToInt(e->e.getItem().getCount()).sum()==1,"Cancellation returns exactly the reserved material");
        actor.discard();h.succeed();
    }
    public static void craftFinishes(GameTestHelper h) {
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.OAK_LOG));
        h.assertTrue(WorldActions.craft(actor,"minecraft:oak_planks").success(),"Recipe must start");
        var out=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess());actor.saveWithoutId(out);actor.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess(),out.buildResult()));
        h.assertTrue(WorldActions.inspectJob(actor).get("active").getAsBoolean(),"Pending work and reserved materials survive entity serialization");
        for(int i=0;i<400;i++){actor.tickCount++;WorldActions.tick(actor);}
        int count=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,actor.getBoundingBox().inflate(2),e->e.getItem().is(Items.OAK_PLANKS)).stream().mapToInt(e->e.getItem().getCount()).sum();
        h.assertTrue(count==4,"Completion must assemble exactly the registered output once");actor.discard();h.succeed();
    }
    public static void sowRequiresSeed(GameTestHelper h) {
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);var pos=h.absolutePos(new BlockPos(2,2,1));h.getLevel().setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE,7),3);
        h.assertFalse(WorldActions.start(actor,"sow",pos).success(),"A script cannot invent a seed stack");
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("rice_seeds")));
        h.assertFalse(WorldActions.start(actor,"sow",pos).success(),"Rice cannot be sown on an unflooded field");actor.discard();h.succeed();
    }
    private static net.minecraft.world.entity.LivingEntity hand(GameTestHelper h,int x,int y,int z){var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(x,y,z));actor.setNoAi(true);SurvivalApi.enroll(actor);return actor;}
    /** What is walled in by rock on every side is dug to, not mined from where its position happens to be known. */
    public static void buriedBlockNeedsDigging(GameTestHelper h){
        for(int x=0;x<5;x++)for(int y=1;y<=3;y++)for(int z=0;z<5;z++)h.setBlock(x,y,z,Blocks.STONE);
        var actor=hand(h,2,4,2);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));var target=h.absolutePos(new BlockPos(2,2,2));
        var buried=WorldActions.start(actor,"mine",target,InteractionHand.MAIN_HAND);
        h.assertTrue(!buried.success()&&buried.message().contains("buried"),"Stone with rock hard against every face cannot be mined: "+buried.message());
        h.setBlock(2,3,2,Blocks.AIR);var opened=WorldActions.start(actor,"mine",target,InteractionHand.MAIN_HAND);
        h.assertTrue(opened.success(),"With the block above it dug away it can: "+opened.message());
        actor.discard();h.succeed();
    }
    /** A job is held to its tool only where the tool does the work; the same pick a little more worn is the same pick. */
    public static void toolHeldOnlyWhereNeeded(GameTestHelper h){
        h.setBlock(1,1,1,Blocks.STONE);h.setBlock(2,1,1,Blocks.STONE);h.setBlock(2,2,1,Blocks.SAND);var actor=hand(h,1,2,1);
        h.assertTrue(WorldActions.start(actor,"mine",h.absolutePos(new BlockPos(2,2,1)),InteractionHand.MAIN_HAND).success(),"Sand is dug bare-handed");
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BREAD));actor.tickCount=41;WorldActions.tick(actor);
        h.assertTrue(dev.qube.spawn_9b5fbb9.survival.LaborGuard.worldBusy(actor),"Taking up food does not cancel digging that needed no tool");
        WorldActions.cancel(actor,"test");h.setBlock(2,2,1,Blocks.AIR);
        var pick=new ItemStack(Items.IRON_PICKAXE);actor.setItemInHand(InteractionHand.MAIN_HAND,pick);
        h.assertTrue(WorldActions.start(actor,"mine",h.absolutePos(new BlockPos(2,1,1)),InteractionHand.MAIN_HAND).success(),"Stone is mined with a pick");
        pick.setDamageValue(7);actor.tickCount=42;WorldActions.tick(actor);
        h.assertTrue(dev.qube.spawn_9b5fbb9.survival.LaborGuard.worldBusy(actor),"The same pick, a little more worn, is still the pick");
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BREAD));actor.tickCount=43;WorldActions.tick(actor);
        h.assertTrue(!dev.qube.spawn_9b5fbb9.survival.LaborGuard.worldBusy(actor),"Putting the pick away ends work that needed it");
        actor.discard();h.succeed();
    }
    /** A recipe the mod has struck out is not a way of making anything, and a name that is no name at all is answered plainly. */
    public static void struckRecipesAreNotOffered(GameTestHelper h){
        h.setBlock(1,1,1,Blocks.STONE);var actor=hand(h,1,2,1);
        var struck=WorldActions.craft(actor,"minecraft:wooden_pickaxe");
        h.assertTrue(!struck.success()&&struck.message().contains("registered normal crafting recipe"),"The game's wooden pickaxe is not on offer: "+struck.message());
        // NeoForge ships its own versions of the game's tool, armour, bucket and shears recipes. This mod's data has to sit above them for those to stay struck out.
        for(String id:new String[]{"minecraft:iron_pickaxe","minecraft:bucket","minecraft:shears","minecraft:iron_chestplate","minecraft:stone_sword"}){var other=WorldActions.craft(actor,id);h.assertTrue(!other.success()&&other.message().contains("registered normal crafting recipe"),id+" is struck out too: "+other.message());}
        var nonsense=WorldActions.craft(actor,"Oak Planks");
        h.assertTrue(!nonsense.success()&&nonsense.message().contains("registered normal crafting recipe"),"A malformed name is refused in words, not with an error: "+nonsense.message());
        h.assertTrue(!WorldActions.planning(actor).toString().contains("minecraft:bedrock"),"Nothing in the catalogue asks for bedrock");
        actor.discard();h.succeed();
    }
    /** Nobody works in his sleep. */
    public static void noWorkAsleep(GameTestHelper h){
        h.setBlock(1,1,1,Blocks.STONE);h.setBlock(2,1,1,Blocks.STONE);h.setBlock(2,2,1,Blocks.SAND);var actor=hand(h,1,2,1);
        actor.setSleepingPos(actor.blockPosition());
        var asleep=WorldActions.start(actor,"mine",h.absolutePos(new BlockPos(2,2,1)),InteractionHand.MAIN_HAND);
        h.assertTrue(!asleep.success()&&asleep.message().contains("Asleep"),"A sleeper starts no work: "+asleep.message());
        actor.clearSleepingPos();h.assertTrue(WorldActions.start(actor,"mine",h.absolutePos(new BlockPos(2,2,1)),InteractionHand.MAIN_HAND).success(),"Awake, he does");
        actor.setSleepingPos(actor.blockPosition());actor.tickCount=41;WorldActions.tick(actor);
        h.assertTrue(dev.qube.spawn_9b5fbb9.survival.LaborGuard.worldBusy(actor)&&actor.getPersistentData().getCompoundOrEmpty("survivalWorldJob").getStringOr("status","").contains("asleep")&&actor.getPersistentData().getCompoundOrEmpty("survivalWorldJob").getDoubleOr("progress",1)==0,"Work begun waits while he sleeps");
        actor.discard();h.succeed();
    }
}
