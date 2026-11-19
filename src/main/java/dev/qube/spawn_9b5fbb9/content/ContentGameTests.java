package dev.qube.spawn_9b5fbb9.content;

import java.util.List;
import dev.qube.spawn_9b5fbb9.agriculture.Husbandry;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class ContentGameTests {
    private static WorkstationEntity testForge(GameTestHelper h,int x){var pos=h.absolutePos(new BlockPos(x,1,1));h.getLevel().setBlock(pos,SurvivalContent.FORGE.get().defaultBlockState(),3);var forge=(WorkstationEntity)h.getLevel().getBlockEntity(pos);forge.fuel=16000;return forge;}
    private static net.minecraft.world.entity.LivingEntity forgeWorker(GameTestHelper h){var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(2,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("iron_bloom"),8));return actor;}
    public static void forgeSingleActor(GameTestHelper h){var actor=forgeWorker(h);var first=testForge(h,1);var second=testForge(h,3);h.assertTrue(Production.start(actor,first,"work_bloom").startsWith("Started"),"A supplied worker must be able to start one actual manual forge");String denied=Production.start(actor,second,"work_bloom");h.assertTrue(!denied.startsWith("Started")&&second.input.isEmpty()&&actor.getMainHandItem().getCount()==4,"One actor cannot reserve a second manual forge or lose the second bloom on rejection");h.assertTrue(first.input.is(SurvivalContent.item("iron_bloom"))&&second.fuel==16000,"Rejecting concurrent work must preserve the original job and unused second forge fuel");actor.discard();h.succeed();}
    public static void forgeWorldJob(GameTestHelper h){var actor=forgeWorker(h);var forge=testForge(h,1);var patch=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlock(patch.below(),Blocks.DIRT.defaultBlockState(),3);h.getLevel().setBlock(patch,SurvivalContent.LOOSE_STONES.get().defaultBlockState(),3);h.assertTrue(dev.qube.spawn_9b5fbb9.controllers.WorldActions.start(actor,"gather",patch).success(),"The shared-budget test must start actual loaded world work");String denied=Production.start(actor,forge,"work_bloom");h.assertTrue(!denied.startsWith("Started")&&forge.input.isEmpty()&&actor.getMainHandItem().getCount()==8,"Existing gathering/mining/crafting labor must prevent a manual forge from consuming supplies");h.assertTrue(dev.qube.spawn_9b5fbb9.controllers.WorldActions.inspectJob(actor).get("active").getAsBoolean(),"Rejected forge work cannot replace the existing world job");dev.qube.spawn_9b5fbb9.controllers.WorldActions.cancel(actor,"Labor budget test cleanup");actor.discard();h.succeed();}
    public static void forgeLegacyBudget(GameTestHelper h){var actor=forgeWorker(h);var first=testForge(h,1);var second=testForge(h,3);long now=SurvivalApi.calendar(h.getLevel());for(var forge:new WorkstationEntity[]{first,second}){forge.input=new ItemStack(SurvivalContent.item("iron_bloom"));forge.recipe="work_bloom";forge.required=3000;forge.last=now-20;forge.worker=actor.getUUID().toString();}double hunger=SurvivalApi.state(actor).hunger;first.advance(h.getLevel(),now);second.advance(h.getLevel(),now);double progress=first.elapsed+first.workRemainder+second.elapsed+second.workRemainder;h.assertTrue(progress>0&&progress<=20.000001,"Two legacy manual forges assigned to one UUID must share at most twenty work ticks per twenty-tick interval");h.assertTrue(32000-first.fuel-second.fuel<=20&&hunger-SurvivalApi.state(actor).hunger<=.010001,"Paused parallel forge work cannot burn extra fuel or charge duplicate manual hunger");actor.discard();h.succeed();}
    public static void finiteTroughCatchup(GameTestHelper h){try(var clock=new CalendarScope(h)){var calf=h.spawn(EntityTypes.COW,new BlockPos(1,2,1));calf.setNoAi(true);calf.setBaby(true);calf.getPersistentData().putLong("husbandryBorn",clock.start);calf.getPersistentData().putLong("husbandryLast",clock.start);livestockSupplies(h,calf,false);h.getLevel().setBlock(calf.blockPosition().below(),Blocks.STONE.defaultBlockState(),3);var pos=calf.blockPosition().offset(1,0,0);h.getLevel().setBlock(pos,SurvivalContent.TROUGH.get().defaultBlockState(),3);var trough=(WorkstationEntity)h.getLevel().getBlockEntity(pos);trough.getPersistentData().putDouble("water",1);trough.getPersistentData().putDouble("feed",128);clock.at(clock.start+28*24000L);husbandryTick(calf);h.assertTrue(calf.isBaby(),"One finite water portion cannot finance twenty-eight growth days");h.assertTrue(SurvivalApi.calendar(h.getLevel())-calf.getPersistentData().getLongOr("husbandryBorn",0)==24000,"Catch-up must credit exactly the one supplied water day, retaining genuine juvenile progress");h.assertTrue(trough.getPersistentData().getDoubleOr("water",-1)==0,"Catch-up must actually consume finite trough water");calf.discard();}h.succeed();}
    public static void cropDefinitions(GameTestHelper h){try{dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.reloadDefinitions(com.google.gson.JsonParser.parseString("{\"minecraft:wheat\":{\"days\":10}}").getAsJsonObject());h.assertTrue(dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.schedule("wheat").days()==10&&dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.schedule("rice_crop").days()==12,"Crop reload must apply declared overrides while preserving omitted defaults");boolean rejected=false;try{dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.reloadDefinitions(com.google.gson.JsonParser.parseString("{\"minecraft:wheat\":{\"days\":0}}").getAsJsonObject());}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected&&dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.schedule("wheat").days()==10,"Invalid crop definitions cannot partially replace active rules");}finally{dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.reloadDefinitions(new com.google.gson.JsonObject());}h.succeed();}
    public static void husbandryDefinitions(GameTestHelper h){var cow=h.spawn(EntityTypes.COW,new BlockPos(1,2,1));try{Husbandry.reloadDefinitions(com.google.gson.JsonParser.parseString("{\"cow\":{\"maturityDays\":30}}").getAsJsonObject());h.assertTrue(Husbandry.maturity(cow)==30&&Husbandry.gestation(cow)==12,"Husbandry reload must tune species timings while retaining omitted gestation defaults");boolean rejected=false;try{Husbandry.reloadDefinitions(com.google.gson.JsonParser.parseString("{\"imaginary_animal\":{\"maturityDays\":10}}").getAsJsonObject());}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected&&Husbandry.maturity(cow)==30,"Unknown husbandry species must be rejected without replacing active rules");}finally{Husbandry.reloadDefinitions(new com.google.gson.JsonObject());cow.discard();}h.succeed();}
    public static void decayDefinitions(GameTestHelper h){try{ItemAging.reloadDefinitions(com.google.gson.JsonParser.parseString("{\"rawMeatDays\":2}").getAsJsonObject());h.assertTrue(ItemAging.shelfLife(new ItemStack(Items.BEEF))==48000&&ItemAging.shelfLife(new ItemStack(Items.COOKED_BEEF))==24000,"Decay reload must tune raw shelf life while preserving omitted category defaults");boolean rejected=false;var invalid=new com.google.gson.JsonObject();invalid.addProperty("sealedPotRate",Double.NaN);try{ItemAging.reloadDefinitions(invalid);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected&&ItemAging.shelfLife(new ItemStack(Items.BEEF))==48000,"Nonfinite preservation must be rejected without corrupting prior rules");}finally{ItemAging.reloadDefinitions(new com.google.gson.JsonObject());}h.succeed();}
    public static void productionDefinitions(GameTestHelper h){try{Production.reloadDefinitions(com.google.gson.JsonParser.parseString("{\"fire_pot\":{\"ticks\":2500}}").getAsJsonObject());h.assertTrue(Production.RECIPES.get("fire_pot").ticks()==2500&&Production.RECIPES.get("smelt_tin").ticks()==6000,"Production reload must tune declared processes and preserve omitted recipes");boolean rejected=false;try{Production.reloadDefinitions(com.google.gson.JsonParser.parseString("{\"fire_pot\":{\"output\":\"spawn_9b5fbb9:imaginary_output\"}}").getAsJsonObject());}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected&&Production.RECIPES.get("fire_pot").ticks()==2500,"An unregistered production output cannot replace active recipes");}finally{Production.reloadDefinitions(new com.google.gson.JsonObject());}h.succeed();}
    private static PlayerInteractEvent.RightClickBlock berryClick(GameTestHelper h,net.minecraft.world.entity.player.Player actor,BlockPos pos){var event=new PlayerInteractEvent.RightClickBlock(actor,InteractionHand.MAIN_HAND,pos,new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false));NeoForge.EVENT_BUS.post(event);return event;}
    public static void wildBerryRecovery(GameTestHelper h){try(var clock=new CalendarScope(h)){
        long season=dev.qube.spawn_9b5fbb9.Config.SEASON_DAYS.get()*24000L,year=season*4,summer=Math.floorDiv(clock.start,year)*year+season+24000;if(summer<clock.start)summer+=year;clock.at(summer);
        BlockPos pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);h.getLevel().setBlock(pos.offset(1,0,0),Blocks.WATER.defaultBlockState(),3);h.getLevel().setBlock(pos,Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_3,3),3);var actor=h.makeMockPlayer(GameType.SURVIVAL);actor.setPos(Vec3.atCenterOf(pos));
        h.assertTrue(h.getLevel().getBlockState(pos).isRandomlyTicking(),"A ripe bush must keep its seasonal calendar active");h.assertTrue(berryClick(h,actor,pos).isCanceled()&&dev.qube.spawn_9b5fbb9.controllers.WorldActions.inspectJob(actor).get("active").getAsBoolean(),"A naturally ripe wild bush must start shared timed work without requiring a planting record");h.assertTrue(countItem(h,actor,Items.SWEET_BERRIES)==0,"Starting berry work cannot produce instant food");
        for(int i=0;i<250&&dev.qube.spawn_9b5fbb9.controllers.WorldActions.inspectJob(actor).get("active").getAsBoolean();i++){actor.tickCount++;NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(actor));}h.assertFalse(dev.qube.spawn_9b5fbb9.controllers.WorldActions.inspectJob(actor).get("active").getAsBoolean(),"Loaded manual work must complete the berry harvest");h.assertTrue(countItem(h,actor,Items.SWEET_BERRIES)>0,"Completed wild harvest must produce actual berries");
        h.assertTrue(berryClick(h,actor,pos).isCanceled()&&!dev.qube.spawn_9b5fbb9.controllers.WorldActions.inspectJob(actor).get("active").getAsBoolean(),"Wild initial harvest must start a real two-day recovery");clock.at(summer+2*24000L-1);h.getLevel().getBlockState(pos).randomTick(h.getLevel(),pos,h.getLevel().getRandom());h.assertTrue(berryClick(h,actor,pos).isCanceled()&&!dev.qube.spawn_9b5fbb9.controllers.WorldActions.inspectJob(actor).get("active").getAsBoolean(),"Berry recovery cannot finish one tick early");clock.at(summer+2*24000L);h.getLevel().getBlockState(pos).randomTick(h.getLevel(),pos,h.getLevel().getRandom());h.assertTrue(berryClick(h,actor,pos).isCanceled()&&dev.qube.spawn_9b5fbb9.controllers.WorldActions.inspectJob(actor).get("active").getAsBoolean(),"Established wild berries must become eligible for timed harvest after exactly two suitable days");dev.qube.spawn_9b5fbb9.controllers.WorldActions.cancel(actor,"Test cleanup");
    }h.succeed();}
    public static void matureRiceFrost(GameTestHelper h){try(var clock=new CalendarScope(h)){
        long season=dev.qube.spawn_9b5fbb9.Config.SEASON_DAYS.get()*24000L,year=season*4,winter=Math.floorDiv(clock.start,year)*year+season*3+1;if(winter<clock.start)winter+=year;clock.at(winter);BlockPos pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE,7),3);h.getLevel().setBlock(pos.offset(1,0,0),Blocks.WATER.defaultBlockState(),3);var rice=SurvivalContent.CROPS.get("rice").get().getStateForAge(7);h.getLevel().setBlock(pos,rice,3);var all=dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.records(h.getLevel());var data=new net.minecraft.nbt.CompoundTag();data.putLong("last",winter-1);data.putLong("growth",12*24000L);all.put(Long.toString(pos.asLong()),data);
        h.assertTrue(rice.isRandomlyTicking(),"Mature rice cannot stop its calendar and become immune to autumn frost");rice.randomTick(h.getLevel(),pos,h.getLevel().getRandom());h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Unharvested mature rice must be lost at winter frost");
    }h.succeed();}
    public static void stationFuelCatchup(GameTestHelper h){var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,SurvivalContent.KILN.get().defaultBlockState(),3);var kiln=(WorkstationEntity)h.getLevel().getBlockEntity(pos);kiln.input=new ItemStack(SurvivalContent.item("unfired_pot"));kiln.recipe="fire_pot";kiln.required=2000;kiln.fuel=8000;kiln.last=100;kiln.advance(h.getLevel(),10100);h.assertTrue(kiln.output.is(SurvivalContent.item("fired_pot"))&&kiln.fuel==6000,"Unloaded catch-up must consume only2000job heat ticks, preserving6000unused fuel");h.succeed();}
    public static void storedWeatherPause(GameTestHelper h){try(var clock=new CalendarScope(h)){var tool=new ItemStack(SurvivalContent.item("knapped_stone_pickaxe"));var tag=ItemAging.data(tool);tag.putLong("weatherTick",clock.start);tag.putDouble("weatherAge",20);ItemAging.data(tool,tag);var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);actor.setItemInHand(InteractionHand.MAIN_HAND,tool);actor.tickCount=20;NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(actor));clock.at(clock.start+40*24000L);NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(actor));actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);var dropped=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),actor.getX(),actor.getY(),actor.getZ(),tool);h.getLevel().addFreshEntity(dropped);ItemAging.weather(dropped);h.assertTrue(dropped.getItem().is(SurvivalContent.item("knapped_stone_pickaxe")),"Forty stored days cannot become environmental weathering when a tool is dropped again");h.assertTrue(ItemAging.data(dropped.getItem()).getDoubleOr("weatherAge",0)<=20,"Storage must pause exposure rather than add unseen weather age");dropped.discard();actor.discard();}h.succeed();}
    public static void npcFoodFreshness(GameTestHelper h){try(var clock=new CalendarScope(h)){var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);var meat=new ItemStack(Items.BEEF);var age=ItemAging.data(meat);age.putLong("freshTick",clock.start-24000);ItemAging.data(meat,age);actor.setItemInHand(InteractionHand.MAIN_HAND,meat);actor.tickCount=20;NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(actor));h.assertTrue(ItemAging.spoiled(actor.getMainHandItem()),"A scripted NPC hand must age stored food just like a player inventory");actor.discard();}h.succeed();}
    public static void stationWearCarry(GameTestHelper h){var pos=h.absolutePos(new BlockPos(1,1,1));var actor=h.makeMockPlayer(GameType.SURVIVAL);actor.setPos(Vec3.atCenterOf(pos));h.getLevel().setBlock(pos,SurvivalContent.KILN.get().defaultBlockState(),3);var kiln=(WorkstationEntity)h.getLevel().getBlockEntity(pos);kiln.wear=255;h.getLevel().destroyBlock(pos,true,actor);var items=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2),drop->drop.getItem().is(SurvivalContent.item("kiln")));h.assertTrue(items.size()==1&&ItemAging.data(items.getFirst().getItem()).getIntOr("workstationWear",0)==255,"Breaking a workstation must preserve accumulated wear in its dropped item");var carried=items.getFirst().getItem().copy();h.getLevel().setBlock(pos,SurvivalContent.KILN.get().defaultBlockState(),3);SurvivalContent.KILN.get().setPlacedBy(h.getLevel(),pos,SurvivalContent.KILN.get().defaultBlockState(),actor,carried);h.assertTrue(((WorkstationEntity)h.getLevel().getBlockEntity(pos)).wear==255,"Replacing a carried workstation cannot refresh its finite lifetime");h.succeed();}
    public static void dryWoolRecovery(GameTestHelper h){try(var clock=new CalendarScope(h)){var sheep=h.spawn(EntityTypes.SHEEP,new BlockPos(1,2,1));sheep.setNoAi(true);livestockSupplies(h,sheep,true);husbandryTick(sheep);var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,2));actor.setNoAi(true);SurvivalApi.enroll(actor);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));Husbandry.action(actor,sheep);livestockSupplies(h,sheep,false);clock.at(clock.start+4*24000L);husbandryTick(sheep);livestockSupplies(h,sheep,true);husbandryTick(sheep);h.assertFalse(sheep.readyForShearing(),"Four dry days followed by immediate watering cannot regenerate a fleece");serializeAnimal(h,sheep);clock.at(clock.start+8*24000L-1);husbandryTick(sheep);h.assertFalse(sheep.readyForShearing(),"Supplied wool recovery must retain its final required tick across reload");clock.at(clock.start+8*24000L);husbandryTick(sheep);h.assertTrue(sheep.readyForShearing(),"Exactly four well-supplied days must regenerate wool after a dry delay");sheep.discard();actor.discard();}h.succeed();}
    /** Calendar jumps are scoped so neighboring/async tests keep their original calendar. */
    private static final class CalendarScope implements AutoCloseable {
        private final GameTestHelper h;
        private final net.minecraft.nbt.CompoundTag original;
        final long start;
        CalendarScope(GameTestHelper h){this.h=h;original=h.getLevel().getServer().getCommandStorage().get(SurvivalApi.CALENDAR).copy();start=SurvivalApi.calendar(h.getLevel())+24000;at(start);}
        void at(long value){var data=original.copy();data.putLong("sleepOffset",value-h.getLevel().getServer().overworld().getLevelData().getGameTime());h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,data);}
        @Override public void close(){h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,original);}
    }
    private static void husbandryTick(net.minecraft.world.entity.animal.Animal animal){animal.tickCount=20;NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(animal));}
    private static void serializeAnimal(GameTestHelper h,net.minecraft.world.entity.animal.Animal animal){var output=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess());animal.saveWithoutId(output);animal.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess(),output.buildResult()));}
    private static void livestockSupplies(GameTestHelper h,net.minecraft.world.entity.animal.Animal animal,boolean watered){if(!watered)for(var pos:BlockPos.betweenClosed(animal.blockPosition().offset(-4,-1,-4),animal.blockPosition().offset(4,1,4)))if(h.getLevel().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER))h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);h.getLevel().setBlock(animal.blockPosition().below(),Blocks.HAY_BLOCK.defaultBlockState(),3);h.getLevel().setBlock(animal.blockPosition().offset(2,0,0),(watered?Blocks.WATER:Blocks.AIR).defaultBlockState(),3);animal.getPersistentData().putDouble("nutrition",100);}
    private static int countItem(GameTestHelper h,net.minecraft.world.entity.Entity entity,net.minecraft.world.item.Item item){return h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,entity.getBoundingBox().inflate(2),drop->drop.getItem().is(item)).stream().mapToInt(drop->drop.getItem().getCount()).sum();}
    public static void dailyEggs(GameTestHelper h){
        try(var clock=new CalendarScope(h)){
            var hen=h.spawn(EntityTypes.CHICKEN,new BlockPos(1,2,1));hen.setNoAi(true);livestockSupplies(h,hen,true);husbandryTick(hen);int first=countItem(h,hen,Items.EGG);
            h.assertTrue(first<=1,"An eligible adult hen can lay at most one initial egg");serializeAnimal(h,hen);for(int i=0;i<3;i++)husbandryTick(hen);h.assertTrue(countItem(h,hen,Items.EGG)==first,"Repeated updates/reload at one calendar instant cannot duplicate eggs");
            clock.at(clock.start+24000-1);husbandryTick(hen);h.assertTrue(countItem(h,hen,Items.EGG)==first,"Egg recovery requires a complete day");clock.at(clock.start+24000);husbandryTick(hen);h.assertTrue(countItem(h,hen,Items.EGG)==first+1,"A fed watered adult must actually lay at its daily boundary");
            serializeAnimal(h,hen);clock.at(clock.start+8*24000L);husbandryTick(hen);h.assertTrue(countItem(h,hen,Items.EGG)==first+2,"Unloaded catch-up may lay one due egg, not a burst of missed days");husbandryTick(hen);h.assertTrue(countItem(h,hen,Items.EGG)==first+2,"Catch-up/reload cannot repeatedly claim the same due egg");hen.discard();
        }h.succeed();
    }
    private static void itemTick(net.minecraft.world.entity.item.ItemEntity item){item.tickCount=20;NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(item));}
    /** Two hens paired give one fertilized egg; on hay by a fire it hatches at four days and not before, and never in the cold. */
    public static void henEggHatches(GameTestHelper h){
        try(var clock=new CalendarScope(h)){
            var level=h.getLevel();var eggItem=dev.qube.spawn_9b5fbb9.content.SurvivalContent.item("fertilized_egg");
            var hen=h.spawn(EntityTypes.CHICKEN,new BlockPos(1,2,1));var mate=h.spawn(EntityTypes.CHICKEN,new BlockPos(2,2,1));hen.setNoAi(true);mate.setNoAi(true);livestockSupplies(h,hen,true);livestockSupplies(h,mate,true);husbandryTick(hen);husbandryTick(mate);
            var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,2));actor.setNoAi(true);SurvivalApi.enroll(actor);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT_SEEDS,2));
            Husbandry.action(actor,mate);String said=Husbandry.action(actor,hen);
            var laid=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,hen.getBoundingBox().inflate(3),drop->drop.getItem().is(eggItem));
            h.assertTrue(laid.size()==1&&said.contains("Fertilized egg"),"Feeding two supplied hens must give exactly one fertilized egg: "+said);
            h.assertTrue(actor.getMainHandItem().isEmpty(),"Pairing consumes both portions of seed");
            BlockPos warm=h.absolutePos(new BlockPos(1,2,4)),cold=h.absolutePos(new BlockPos(1,2,9));
            level.setBlock(warm,Blocks.HAY_BLOCK.defaultBlockState(),3);level.setBlock(warm.east(),Blocks.CAMPFIRE.defaultBlockState(),3);level.setBlock(cold,Blocks.HAY_BLOCK.defaultBlockState(),3);
            actor.setItemInHand(InteractionHand.MAIN_HAND,laid.getFirst().getItem().copy());laid.getFirst().discard();
            actor.setPos(Vec3.atBottomCenterOf(warm.north()));h.assertTrue(Husbandry.nest(actor,warm).contains("placed"),"A fertilized egg is set on a hay nest");
            actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(eggItem));actor.setPos(Vec3.atBottomCenterOf(cold.north()));h.assertTrue(Husbandry.nest(actor,cold).contains("placed"),"A second egg is set on hay with no fire");
            java.util.function.Function<BlockPos,List<net.minecraft.world.entity.item.ItemEntity>> eggs=at->level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(at).inflate(1.5),drop->drop.getItem().is(eggItem));
            java.util.function.Function<BlockPos,Integer> chicks=at->level.getEntitiesOfClass(net.minecraft.world.entity.animal.chicken.Chicken.class,new net.minecraft.world.phys.AABB(at).inflate(2),bird->bird.isBaby()).size();
            h.assertTrue(eggs.apply(warm).size()==1&&eggs.apply(cold).size()==1,"Each nest holds its egg");
            var warmEgg=eggs.apply(warm).getFirst();var coldEgg=eggs.apply(cold).getFirst();itemTick(warmEgg);itemTick(coldEgg);
            clock.at(clock.start+4*24000L-1);itemTick(warmEgg);itemTick(coldEgg);
            h.assertTrue(chicks.apply(warm)==0&&warmEgg.isAlive(),"An egg cannot hatch a tick short of four warm days");
            clock.at(clock.start+4*24000L);itemTick(warmEgg);itemTick(coldEgg);
            h.assertTrue(chicks.apply(warm)==1&&!warmEgg.isAlive(),"Four warm days on hay hatch one chick and use up the egg");
            clock.at(clock.start+12*24000L);itemTick(coldEgg);
            h.assertTrue(chicks.apply(cold)==0&&coldEgg.isAlive(),"An egg with no heat never hatches");
            for(var bird:level.getEntitiesOfClass(net.minecraft.world.entity.animal.chicken.Chicken.class,new net.minecraft.world.phys.AABB(warm).inflate(12)))bird.discard();
            for(var drop:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(warm).inflate(12)))drop.discard();
            actor.discard();
        }h.succeed();
    }
    /** A nested egg must still be on its hay after real time has passed, or it can never incubate. */
    public static void nestedEggStays(GameTestHelper h){
        var level=h.getLevel();var eggItem=dev.qube.spawn_9b5fbb9.content.SurvivalContent.item("fertilized_egg");BlockPos nest=h.absolutePos(new BlockPos(1,2,1));
        level.setBlock(nest,Blocks.HAY_BLOCK.defaultBlockState(),3);
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,3));actor.setNoAi(true);SurvivalApi.enroll(actor);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(eggItem));
        h.assertTrue(Husbandry.nest(actor,nest).contains("placed"),"A fertilized egg is set on a hay nest");
        h.runAfterDelay(60,()->{
            var eggs=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(nest).inflate(24),drop->drop.getItem().is(eggItem));
            h.assertTrue(eggs.size()==1,"The nest holds one egg, found "+eggs.size());
            var egg=eggs.getFirst();
            h.assertTrue(egg.blockPosition().below().equals(nest),"Three seconds on, the egg is still on its hay; it is at "+egg.position()+" and the nest is "+nest);
            egg.discard();actor.discard();h.succeed();
        });
    }
    /** A sow farrows a litter of two to four and a ewe drops one lamb, each on its day; a dam that is dying delivers nothing. */
    public static void litterLambAndDyingDam(GameTestHelper h){
        try(var clock=new CalendarScope(h)){
            var level=h.getLevel();
            var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,2));actor.setNoAi(true);SurvivalApi.enroll(actor);
            var sow=h.spawn(EntityTypes.PIG,new BlockPos(1,2,1));var boar=h.spawn(EntityTypes.PIG,new BlockPos(2,2,1));sow.setNoAi(true);boar.setNoAi(true);livestockSupplies(h,sow,true);livestockSupplies(h,boar,true);husbandryTick(sow);husbandryTick(boar);
            actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.CARROT,2));Husbandry.action(actor,boar);String said=Husbandry.action(actor,sow);
            long due=sow.getPersistentData().getLongOr("pregnantUntil",0);int litter=sow.getPersistentData().getIntOr("litter",0);
            h.assertTrue(due==clock.start+8*24000L&&litter>=2&&litter<=4,"A sow carries two to four piglets for eight days: "+said);
            h.assertTrue(boar.getPersistentData().getLongOr("pregnantUntil",0)==0,"Only the animal fed second conceives");
            clock.at(due);livestockSupplies(h,sow,true);husbandryTick(sow);
            var piglets=level.getEntitiesOfClass(net.minecraft.world.entity.animal.pig.Pig.class,sow.getBoundingBox().inflate(3),pig->pig.isBaby());
            h.assertTrue(piglets.size()==litter,"The whole litter is born on the day: "+piglets.size()+" of "+litter);
            for(var pig:level.getEntitiesOfClass(net.minecraft.world.entity.animal.pig.Pig.class,sow.getBoundingBox().inflate(6)))pig.discard();
            long start=due;
            var ewe=h.spawn(EntityTypes.SHEEP,new BlockPos(1,2,1));var ram=h.spawn(EntityTypes.SHEEP,new BlockPos(2,2,1));ewe.setNoAi(true);ram.setNoAi(true);livestockSupplies(h,ewe,true);livestockSupplies(h,ram,true);husbandryTick(ewe);husbandryTick(ram);
            actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT,2));Husbandry.action(actor,ram);said=Husbandry.action(actor,ewe);
            long lambing=ewe.getPersistentData().getLongOr("pregnantUntil",0);
            h.assertTrue(lambing==start+8*24000L&&ewe.getPersistentData().getIntOr("litter",0)==1,"A ewe carries one lamb for eight days: "+said);
            clock.at(lambing);livestockSupplies(h,ewe,true);float health=ewe.getHealth();ewe.setHealth(0);husbandryTick(ewe);
            java.util.function.Supplier<Integer> lambs=()->level.getEntitiesOfClass(net.minecraft.world.entity.animal.sheep.Sheep.class,ewe.getBoundingBox().inflate(3),sheep->sheep.isBaby()).size();
            h.assertTrue(lambs.get()==0&&ewe.getPersistentData().getLongOr("pregnantUntil",0)==lambing,"A dying ewe does not lamb");
            ewe.setHealth(health);husbandryTick(ewe);
            h.assertTrue(lambs.get()==1&&ewe.getPersistentData().getLongOr("pregnantUntil",-1)==0,"The same ewe, alive, lambs once");
            for(var sheep:level.getEntitiesOfClass(net.minecraft.world.entity.animal.sheep.Sheep.class,ewe.getBoundingBox().inflate(6)))sheep.discard();
            actor.discard();
        }h.succeed();
    }
    public static void gestationDelivery(GameTestHelper h){
        try(var clock=new CalendarScope(h)){
            var mother=h.spawn(EntityTypes.COW,new BlockPos(1,2,1));var mate=h.spawn(EntityTypes.COW,new BlockPos(2,2,1));mother.setNoAi(true);mate.setNoAi(true);livestockSupplies(h,mother,true);livestockSupplies(h,mate,true);husbandryTick(mother);husbandryTick(mate);
            var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,2));actor.setNoAi(true);SurvivalApi.enroll(actor);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT,2));Husbandry.action(actor,mate);Husbandry.action(actor,mother);
            var pregnant=mother.getPersistentData().getLongOr("pregnantUntil",0)>0?mother:mate;long due=pregnant.getPersistentData().getLongOr("pregnantUntil",0);h.assertTrue(due==clock.start+12*24000L,"Feeding two mature supplied cows must start a real twelve-day pregnancy");h.assertTrue(actor.getMainHandItem().isEmpty(),"Pairing must consume both real feed portions");
            serializeAnimal(h,pregnant);clock.at(due-1);livestockSupplies(h,pregnant,true);husbandryTick(pregnant);h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class,pregnant.getBoundingBox().inflate(2),cow->cow.isBaby()).isEmpty(),"A cow cannot deliver before its due tick");
            // Exhaust all nearby water at the due instant: a pending delivery is deferred.
            clock.at(due);for(var p:BlockPos.betweenClosed(pregnant.blockPosition().offset(-4,-1,-4),pregnant.blockPosition().offset(4,1,4)))if(h.getLevel().getFluidState(p).is(net.minecraft.tags.FluidTags.WATER))h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),3);husbandryTick(pregnant);h.assertTrue(pregnant.getPersistentData().getLongOr("pregnantUntil",0)==due,"An unsupplied dam must retain one pending pregnancy");
            livestockSupplies(h,pregnant,true);husbandryTick(pregnant);var babies=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class,pregnant.getBoundingBox().inflate(2),cow->cow.isBaby());h.assertTrue(babies.size()==1,"A supplied due pregnancy must produce one actual calf");h.assertTrue(babies.getFirst().getPersistentData().getLongOr("husbandryBorn",0)==due,"Newborn biological age starts at the scheduled birth time");
            serializeAnimal(h,pregnant);husbandryTick(pregnant);h.assertTrue(pregnant.getPersistentData().getLongOr("pregnantUntil",-1)==0&&h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class,pregnant.getBoundingBox().inflate(2),cow->cow.isBaby()).size()==1,"Delivery state must survive serialization and never duplicate a litter");for(var baby:babies)baby.discard();mother.discard();mate.discard();actor.discard();
        }h.succeed();
    }
    public static void productRecovery(GameTestHelper h){
        try(var clock=new CalendarScope(h)){
            var cow=h.spawn(EntityTypes.COW,new BlockPos(1,2,1));cow.setNoAi(true);livestockSupplies(h,cow,true);husbandryTick(cow);var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,2));SurvivalApi.enroll(actor);actor.setNoAi(true);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BUCKET,8));
            Husbandry.action(actor,cow);h.assertTrue(actor.getMainHandItem().getCount()==8,"An uncalved cow cannot supply milk");cow.getPersistentData().putLong("calvedAt",clock.start);Husbandry.action(actor,cow);Husbandry.action(actor,cow);h.assertTrue(actor.getMainHandItem().getCount()==7&&countItem(h,actor,Items.MILK_BUCKET)==1,"Milk collection consumes one bucket and immediately enters daily recovery");serializeAnimal(h,cow);
            clock.at(clock.start+24000-1);husbandryTick(cow);Husbandry.action(actor,cow);h.assertTrue(actor.getMainHandItem().getCount()==7,"Milk cannot recover before one complete supplied day");clock.at(clock.start+24000);husbandryTick(cow);Husbandry.action(actor,cow);h.assertTrue(actor.getMainHandItem().getCount()==6,"Milk must recover at the one-day boundary after reload");
            var sheep=h.spawn(EntityTypes.SHEEP,new BlockPos(2,2,1));sheep.setNoAi(true);livestockSupplies(h,sheep,true);husbandryTick(sheep);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));Husbandry.action(actor,sheep);h.assertTrue(sheep.isSheared(),"Shearing must remove an actual mature fleece");long shorn=SurvivalApi.calendar(h.getLevel());serializeAnimal(h,sheep);clock.at(shorn+4*24000L-1);husbandryTick(sheep);sheep.setSheared(false);h.assertFalse(sheep.readyForShearing(),"Vanilla grass regrowth cannot bypass four full wool days");clock.at(shorn+4*24000L);husbandryTick(sheep);h.assertTrue(sheep.readyForShearing(),"Fed watered wool must actually recover after four days");
            cow.discard();sheep.discard();actor.discard();
        }h.succeed();
    }
    public static void suppliedMaturation(GameTestHelper h){
        try(var clock=new CalendarScope(h)){
            var calf=h.spawn(EntityTypes.COW,new BlockPos(1,2,1));calf.setNoAi(true);calf.setBaby(true);calf.getPersistentData().putLong("husbandryBorn",clock.start);calf.getPersistentData().putLong("husbandryLast",clock.start);livestockSupplies(h,calf,false);clock.at(clock.start+28*24000L);husbandryTick(calf);h.assertTrue(calf.isBaby(),"Waterless juvenile time cannot become adult growth");
            livestockSupplies(h,calf,true);serializeAnimal(h,calf);clock.at(clock.start+55*24000L);husbandryTick(calf);h.assertTrue(calf.isBaby(),"Cow must receive all twenty-eight supplied growth days");clock.at(clock.start+56*24000L);husbandryTick(calf);h.assertFalse(calf.isBaby(),"A continually fed watered calf matures at twenty-eight qualifying days");calf.discard();
        }h.succeed();
    }
    private static void cropRecord(GameTestHelper h,BlockPos pos,long growth,boolean fruited){var all=dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.records(h.getLevel());var data=new net.minecraft.nbt.CompoundTag();data.putLong("growth",growth);data.putLong("last",SurvivalApi.calendar(h.getLevel()));data.putBoolean("fruited",fruited);all.put(Long.toString(pos.asLong()),data);}
    public static void slowHarvest(GameTestHelper h){
        var actor=h.makeMockPlayer(GameType.SURVIVAL);BlockPos mushroom=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(mushroom.below(),Blocks.DIRT.defaultBlockState(),3);h.getLevel().setBlock(mushroom,Blocks.BROWN_MUSHROOM.defaultBlockState(),3);cropRecord(h,mushroom,4*24000L-1,false);
        h.getLevel().destroyBlock(mushroom,true,actor);h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(mushroom).inflate(1),e->e.getItem().is(Blocks.BROWN_MUSHROOM.asItem())).isEmpty(),"A planted mushroom cannot yield before four suitable days");
        h.getLevel().setBlock(mushroom,Blocks.BROWN_MUSHROOM.defaultBlockState(),3);cropRecord(h,mushroom,4*24000L,false);h.getLevel().destroyBlock(mushroom,true,actor);h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(mushroom).inflate(1),e->e.getItem().is(Blocks.BROWN_MUSHROOM.asItem())).size()==1,"Mature mushroom must remain harvestable");
        BlockPos cane=h.absolutePos(new BlockPos(3,1,1));h.getLevel().setBlock(cane,Blocks.SUGAR_CANE.defaultBlockState(),3);cropRecord(h,cane,6*24000L-1,false);h.getLevel().destroyBlock(cane,true,actor);h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(cane).inflate(1),e->e.getItem().is(Blocks.SUGAR_CANE.asItem())).isEmpty(),"Breaking an immature planted cane cannot skip six warm days");h.succeed();
    }
    public static void repeatFruit(GameTestHelper h){
        BlockPos pos=h.absolutePos(new BlockPos(2,1,2));for(var ground:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,-1,1)))h.getLevel().setBlock(ground,Blocks.DIRT.defaultBlockState(),3);h.getLevel().setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE,7),3);h.getLevel().setBlock(pos,Blocks.MELON_STEM.defaultBlockState(),3);cropRecord(h,pos,10*24000L,false);
        dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.randomTick(h.getLevel().getBlockState(pos),h.getLevel(),pos,h.getLevel().getRandom());BlockPos fruit=null;for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)if(h.getLevel().getBlockState(pos.relative(d)).is(Blocks.MELON))fruit=pos.relative(d);h.assertTrue(fruit!=null,"Ten-day stem must bear its first melon");h.getLevel().removeBlock(fruit,false);
        cropRecord(h,pos,2*24000L,true);dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.randomTick(h.getLevel().getBlockState(pos),h.getLevel(),pos,h.getLevel().getRandom());boolean early=false;for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)early|=h.getLevel().getBlockState(pos.relative(d)).is(Blocks.MELON);h.assertFalse(early,"A later melon must wait all three warm days");
        cropRecord(h,pos,3*24000L,true);dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.randomTick(h.getLevel().getBlockState(pos),h.getLevel(),pos,h.getLevel().getRandom());boolean grown=false;for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)grown|=h.getLevel().getBlockState(pos.relative(d)).is(Blocks.MELON);h.assertTrue(grown,"Later melons should mature in three days rather than repeating ten-day establishment");h.succeed();
    }
    public static void controllerVessels(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);ItemStack pot=new ItemStack(SurvivalContent.item("fired_pot"));actor.setItemInHand(InteractionHand.MAIN_HAND,pot);actor.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.BEEF,3));
        Pottery.action(actor,"store");h.assertTrue(pot.get(DataComponents.CONTAINER).copyOne().getCount()==3&&actor.getOffhandItem().isEmpty(),"Scripted NPC storage must move real supplies");
        actor.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(SurvivalContent.item("pot_lid"),2));Pottery.action(actor,"seal");h.assertTrue(ItemAging.data(pot).getBooleanOr("sealed",false)&&actor.getOffhandItem().getCount()==1,"Sealing must consume a physical lid");
        var existingLids=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,actor.getBoundingBox().inflate(2),item->item.getItem().is(SurvivalContent.item("pot_lid"))).stream().map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
        Pottery.action(actor,"seal");h.assertFalse(ItemAging.data(pot).getBooleanOr("sealed",true),"Opening must unseal the NPC pot");
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,actor.getBoundingBox().inflate(2),item->!existingLids.contains(item.getUUID())&&item.getItem().is(SurvivalContent.item("pot_lid"))).stream().mapToInt(item->item.getItem().getCount()).sum()==1,"Opening must return exactly one new lid independent of nearby fixture drops");
        Pottery.action(actor,"retrieve");h.assertTrue(pot.get(DataComponents.CONTAINER).copyOne().isEmpty(),"Retrieval must empty the original slot");
        actor.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.CARROT,4));pot.setDamageValue(pot.getMaxDamage()-1);Pottery.action(actor,"store");h.assertTrue(pot.isEmpty(),"Exhausted pottery must break");
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,actor.getBoundingBox().inflate(2),item->item.getItem().is(Items.CARROT)&&item.getItem().getCount()==4).size()==1,"Broken pottery must spill its stored food exactly once");actor.discard();h.succeed();
    }
    public static void npcBoiling(GameTestHelper h){
        var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));SurvivalApi.enroll(actor);ItemStack pot=new ItemStack(SurvivalContent.item("fired_pot"));var data=ItemAging.data(pot);data.putInt("water",4);ItemAging.data(pot,data);actor.setItemInHand(InteractionHand.MAIN_HAND,pot);
        BlockPos fire=h.absolutePos(new BlockPos(1,1,2));h.getLevel().setBlock(fire,Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT,false),3);
        h.assertTrue(Pottery.action(actor,"boil").contains("lit fire"),"Unlit campfires cannot boil water");h.getLevel().setBlock(fire,Blocks.CAMPFIRE.defaultBlockState(),3);Pottery.action(actor,"boil");
        for(int i=0;i<49;i++)NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(actor));h.assertFalse(Pottery.safeWater(pot),"NPC water cannot become safe before complete boiling");NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(actor));h.assertTrue(Pottery.safeWater(pot),"NPC holding water at lit heat must boil it after1000ticks");
        var tag=ItemAging.data(pot);tag.putBoolean("sealed",true);ItemAging.data(pot,tag);h.assertFalse(Pottery.consumeWater(pot),"Sealed vessels cannot be drunk or drained");h.assertTrue(Pottery.waterUnits(pot)==4,"Rejected sealed drainage must preserve quantity");h.succeed();
    }
    public static void preservation(GameTestHelper h){
        long now=SurvivalApi.calendar(h.getLevel());ItemStack exposed=new ItemStack(Items.BEEF);var age=ItemAging.data(exposed);age.putLong("freshTick",now-24000);ItemAging.data(exposed,age);
        ItemStack sealed=new ItemStack(SurvivalContent.item("fired_pot"));sealed.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(exposed.copy())));var tag=ItemAging.data(sealed);tag.putBoolean("sealed",true);ItemAging.data(sealed,tag);
        ItemAging.update(exposed,h.getLevel(),1);ItemAging.update(sealed,h.getLevel(),1);
        ItemStack preserved=sealed.get(DataComponents.CONTAINER).copyOne();
        h.assertTrue(ItemAging.spoiled(exposed),"Unpreserved day-old meat must spoil");h.assertFalse(ItemAging.spoiled(preserved),"Sealed pot must keep the same meat fresh");
        h.assertTrue(Math.abs(ItemAging.data(preserved).getDoubleOr("foodAge",0)-4000)<.1,"Sealed preservation must be exactly one sixth");
        ItemStack split=preserved.split(1);h.assertTrue(ItemAging.data(split).getDoubleOr("foodAge",0)==4000,"Splitting must retain ingredient age");h.succeed();
    }
    public static void completedFiring(GameTestHelper h){
        BlockPos pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,SurvivalContent.KILN.get().defaultBlockState(),3);var kiln=(WorkstationEntity)h.getLevel().getBlockEntity(pos);
        kiln.input=new ItemStack(SurvivalContent.item("unfired_pot"));kiln.recipe="fire_pot";kiln.required=2000;kiln.fuel=2000;kiln.last=100;kiln.advance(h.getLevel(),1100);
        h.assertTrue(kiln.output.isEmpty(),"Half-fired pottery must not become a finished vessel");h.assertTrue(kiln.input.is(SurvivalContent.item("unfired_pot")),"Unfinished input must remain recoverable");
        kiln.advance(h.getLevel(),2100);h.assertTrue(kiln.output.is(SurvivalContent.item("fired_pot")),"Completed firing must produce real fired pottery");h.assertTrue(kiln.input.isEmpty()&&kiln.fuel==0,"Firing must consume input and fuel once");
        kiln.advance(h.getLevel(),4100);h.assertTrue(kiln.output.getCount()==1,"Repeat tick must not duplicate output");h.succeed();
    }
    public static void seasonalBonemeal(GameTestHelper h){
        BlockPos pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState(),3);var block=SurvivalContent.CROPS.get("rice").get();h.getLevel().setBlock(pos,block.defaultBlockState(),3);
        var event=new BonemealEvent(null,h.getLevel(),pos,block.defaultBlockState(),BonemealSource.INTERACTION,new ItemStack(Items.BONE_MEAL));NeoForge.EVENT_BUS.post(event);
        h.assertTrue(event.isCanceled()&&!event.isSuccessful(),"Bone meal cannot replace twelve warm flooded days");h.assertTrue(h.getLevel().getBlockState(pos).getValue(CropBlock.AGE)==0,"Seedling age must stay unchanged");h.succeed();
    }
    public static void juvenileFeeding(GameTestHelper h){
        var animal=h.spawn(EntityTypes.COW,new BlockPos(1,2,1));animal.setBaby(true);long now=SurvivalApi.calendar(h.getLevel());animal.getPersistentData().putLong("husbandryBorn",now);animal.getPersistentData().putDouble("nutrition",40);
        var actor=h.makeMockPlayer(GameType.SURVIVAL);actor.setPos(animal.position().add(0,0,1));actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT,2));int age=animal.getAge();var event=new PlayerInteractEvent.EntityInteract(actor,InteractionHand.MAIN_HAND,animal,Vec3.ZERO);NeoForge.EVENT_BUS.post(event);
        h.assertTrue(event.isCanceled(),"Husbandry must replace vanilla feeding behavior");h.assertTrue(animal.getAge()==age&&animal.isBaby(),"Feeding cannot instantly mature a juvenile");h.assertTrue(actor.getMainHandItem().getCount()==1,"Feeding must consume one real supply");h.assertFalse(Husbandry.mature(animal,now+27*24000L),"Cow maturity must require all 28 days");h.succeed();
    }
    private static net.minecraft.world.entity.LivingEntity potter(GameTestHelper h,int x,int z){h.setBlock(x,1,z,Blocks.STONE);var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(x,2,z));actor.setNoAi(true);SurvivalApi.enroll(actor);return actor;}
    private static WorkstationEntity kiln(GameTestHelper h,int x,int z){var pos=h.absolutePos(new BlockPos(x,2,z));h.setBlock(x,1,z,Blocks.STONE);h.getLevel().setBlock(pos,SurvivalContent.KILN.get().defaultBlockState(),3);return (WorkstationEntity)h.getLevel().getBlockEntity(pos);}
    /** Only what can go off carries an age: iron, cloth and sticks are plain goods that stack with their like. */
    public static void plainGoodsStack(GameTestHelper h){
        var ingot=new ItemStack(Items.IRON_INGOT);ItemAging.inherit(ingot,List.of(new ItemStack(Items.RAW_IRON,3)),h.getLevel());
        h.assertTrue(!ingot.has(DataComponents.CUSTOM_DATA)&&ItemStack.isSameItemSameComponents(ingot,new ItemStack(Items.IRON_INGOT)),"What is made that is not food is not stamped with the moment of making");
        var bread=new ItemStack(Items.BREAD);ItemAging.inherit(bread,List.of(new ItemStack(Items.WHEAT,3)),h.getLevel());
        h.assertTrue(ItemAging.data(bread).contains("freshTick"),"Food is");
        var stick=new ItemStack(Items.STICK,2);var tag=ItemAging.data(stick);tag.putLong("weatherTick",SurvivalApi.calendar(h.getLevel()));tag.putDouble("weatherAge",37);ItemAging.data(stick,tag);
        ItemAging.update(stick,h.getLevel(),1);
        h.assertTrue(!stick.has(DataComponents.CUSTOM_DATA),"A stick picked up after seconds on the ground is a stick like any other");
        var old=new ItemStack(Items.STICK);tag=ItemAging.data(old);tag.putDouble("weatherAge",2.5*24000);ItemAging.data(old,tag);ItemAging.update(old,h.getLevel(),1);
        h.assertTrue(ItemAging.data(old).getDoubleOr("weatherAge",0)==2*24000d,"Days in the open are still counted, in whole days");
        h.succeed();
    }
    /** An egg off its nest rots like anything else, and loose rock is kept without being barred from joining a like heap. */
    public static void looseEggRots(GameTestHelper h){try(var clock=new CalendarScope(h)){
        var level=h.getLevel();h.setBlock(1,1,1,Blocks.STONE);var at=h.absoluteVec(new Vec3(1.5,2,1.5));
        var egg=new ItemStack(SurvivalContent.item("fertilized_egg"));var tag=ItemAging.data(egg);tag.putLong("weatherTick",clock.start-5*24000L);ItemAging.data(egg,tag);
        var loose=new net.minecraft.world.entity.item.ItemEntity(level,at.x,at.y,at.z,egg,0,0,0);level.addFreshEntity(loose);ItemAging.weather(loose);
        h.assertTrue(loose.isRemoved(),"A fertilized egg left lying off a nest is gone after its days in the open");
        var carried=new ItemStack(SurvivalContent.item("fertilized_egg"));tag=ItemAging.data(carried);tag.putBoolean("nest",true);tag.putLong("incubated",3*24000L);tag.putLong("nestLast",clock.start);ItemAging.data(carried,tag);
        ItemAging.update(carried,level,1);
        h.assertTrue(!ItemAging.data(carried).contains("nest")&&!ItemAging.data(carried).contains("incubated"),"An egg taken off the nest has lost its sitting");
        var ore=new net.minecraft.world.entity.item.ItemEntity(level,at.x,at.y,at.z,new ItemStack(SurvivalContent.item("hematite"),10),0,0,0);level.addFreshEntity(ore);ItemAging.weather(ore);
        h.assertTrue(ore.isAlive()&&ore.getAge()<0&&ore.getAge()>-32768&&!ore.getItem().has(DataComponents.CUSTOM_DATA),"Loose ore is kept from the sweep and can still join another heap: age "+ore.getAge());
        ore.discard();h.succeed();
    }}
    /** A dead worker's job is taken up where it stands by the next hand that can do it. */
    public static void deadWorkersJobIsTakenUp(GameTestHelper h){
        var kiln=kiln(h,2,2);kiln.fuel=16000;var first=potter(h,1,2);var second=potter(h,3,2);
        first.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.CLAY_BALL,8));
        String begun=Production.start(first,kiln,"fire_pot");
        h.assertTrue(begun.startsWith("Started")&&kiln.worker.equals(first.getUUID().toString()),"The first potter begins the firing: "+begun);
        h.assertTrue(Production.start(second,kiln,"fire_pot").equals("Workstation occupied."),"While he lives the kiln is his");
        kiln.elapsed=700;first.hurtServer(h.getLevel(),h.getLevel().damageSources().genericKill(),Float.MAX_VALUE);
        h.assertTrue(kiln.worker.isEmpty()&&!kiln.input.isEmpty()&&kiln.elapsed==700,"His death frees the kiln and leaves the work as it stood");
        String taken=Production.start(second,kiln,"fire_pot");
        h.assertTrue(taken.startsWith("Took up")&&kiln.worker.equals(second.getUUID().toString())&&kiln.elapsed==700,"The next potter takes it up where it was: "+taken);
        second.discard();h.succeed();
    }
    /** The fire burns by the clock and the work goes at the worker's pace: a slow hand is told the fire he really needs. */
    public static void slowHandNeedsMoreFire(GameTestHelper h){
        var kiln=kiln(h,2,2);var novice=potter(h,1,2);novice.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.CLAY_BALL,8));
        kiln.fuel=2000;String cold=Production.start(novice,kiln,"fire_pot");
        h.assertTrue(cold.contains("at your pace")&&kiln.input.isEmpty()&&novice.getMainHandItem().getCount()==8,"The bare two thousand is not enough for a beginner, and nothing is taken: "+cold);
        kiln.fuel=8000;String lit=Production.start(novice,kiln,"fire_pot");
        h.assertTrue(lit.startsWith("Started"),"With a full charge of charcoal he begins: "+lit);
        novice.discard();h.succeed();
    }
    /** The game's furnace does no work, fed by hand or by hopper. */
    public static void furnaceStaysCold(GameTestHelper h){
        h.setBlock(1,1,1,Blocks.STONE);h.setBlock(1,2,1,Blocks.FURNACE);var pos=h.absolutePos(new BlockPos(1,2,1));
        var furnace=(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)h.getLevel().getBlockEntity(pos);
        furnace.setItem(0,new ItemStack(Items.OAK_LOG,4));furnace.setItem(1,new ItemStack(Items.COAL,4));
        for(int tick=0;tick<260;tick++)net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(h.getLevel(),pos,h.getLevel().getBlockState(pos),furnace);
        h.assertTrue(furnace.getItem(2).isEmpty()&&furnace.getItem(1).getCount()==4&&furnace.getItem(0).getCount()==4,"Thirteen seconds on, nothing has burned and nothing has cooked");
        h.succeed();
    }
    /** A painkiller is for pain, and a wound bleeds on through sleep without shaking the sleeper awake. */
    public static void sleepAndPain(GameTestHelper h){
        var actor=potter(h,1,1);var body=SurvivalApi.state(actor);
        h.assertTrue(!body.treat("painkiller"),"With no pain there is nothing for a painkiller to do");
        body.bleeding=5;h.assertTrue(body.treat("painkiller"),"With a wound there is");
        body=SurvivalApi.state(actor);body.bleeding=5;SurvivalApi.save(actor,body);actor.setSleepingPos(actor.blockPosition());float before=actor.getHealth();
        actor.tickCount=20;NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(actor));
        h.assertTrue(actor.isSleeping()&&actor.getHealth()<before,"He bleeds and sleeps on: health "+before+" to "+actor.getHealth()+", sleeping "+actor.isSleeping());
        actor.discard();h.succeed();
    }
    public static void oreLiesInOneHeap(GameTestHelper h){
        var level=h.getLevel();for(int x=0;x<5;x++)for(int z=0;z<5;z++)h.setBlock(x,1,z,Blocks.STONE);
        BlockPos pos=h.absolutePos(new BlockPos(2,2,2));var seam=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("spawn_9b5fbb9:hematite_ore")).defaultBlockState();
        var ore=SurvivalContent.item("hematite");
        java.util.function.Supplier<List<net.minecraft.world.entity.item.ItemEntity>> lying=()->level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2),e->e.isAlive()&&e.getItem().is(ore));
        for(var stale:lying.get())stale.kill(level);
        Block.dropResources(seam,level,pos,null,null,ItemStack.EMPTY);
        var heaps=lying.get();
        h.assertTrue(heaps.size()==1&&ItemAging.heap(heaps.getFirst())==1600&&heaps.getFirst().getItem().getCount()==64&&heaps.getFirst().hasCustomName(),"A metre of seam breaks down to one heap of 1,600, not twenty-five stacks: "+heaps.size()+" entities");
        var heap=heaps.getFirst();var miner=h.makeMockServerPlayer(GameType.SURVIVAL);miner.setPos(heap.position());heap.setNoPickUpDelay();heap.playerTouch(miner);
        heaps=lying.get();
        h.assertTrue(heap.isRemoved()&&miner.getInventory().countItem(ore)==64&&heaps.size()==1&&ItemAging.heap(heaps.getFirst())==1536,"Taking the stack that shows uncovers the next: carried "+miner.getInventory().countItem(ore)+", lying "+heaps.size());
        var rest=heaps.getFirst();var loose=new net.minecraft.world.entity.item.ItemEntity(level,rest.getX()+.5,rest.getY(),rest.getZ(),new ItemStack(ore,40),0,0,0);level.addFreshEntity(loose);ItemAging.weather(rest);
        h.assertTrue(loose.isRemoved()&&lying.get().size()==1&&ItemAging.heap(rest)==1576,"A loose stack lying against a heap joins it: "+ItemAging.heap(rest));
        rest.kill(level);h.assertTrue(lying.get().isEmpty(),"A heap killed outright is gone, not uncovered again");
        h.succeed();
    }
    public static void onlyStockBreeds(GameTestHelper h){try(var clock=new CalendarScope(h)){
        var mare=h.spawn(EntityTypes.HORSE,new BlockPos(1,2,1));var stallion=h.spawn(EntityTypes.HORSE,new BlockPos(2,2,1));mare.setNoAi(true);stallion.setNoAi(true);livestockSupplies(h,mare,true);livestockSupplies(h,stallion,true);husbandryTick(mare);husbandryTick(stallion);
        h.assertTrue(Husbandry.managed(mare)&&Husbandry.gestation(mare)==14&&Husbandry.managed(h.spawn(EntityTypes.RABBIT,new BlockPos(3,2,3))),"Horses and rabbits are stock, bred by the calendar like cattle");
        var groom=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,2));groom.setNoAi(true);SurvivalApi.enroll(groom);groom.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT,2));Husbandry.action(groom,stallion);Husbandry.action(groom,mare);
        long due=Math.max(mare.getPersistentData().getLongOr("pregnantUntil",0),stallion.getPersistentData().getLongOr("pregnantUntil",0));
        h.assertTrue(due==clock.start+14*24000L,"Two fed, watered horses start a fourteen-day pregnancy, not a foal on the spot: due "+(due-clock.start));
        var wolf=h.spawn(EntityTypes.WOLF,new BlockPos(3,2,1));wolf.setNoAi(true);wolf.setInLoveTime(600);wolf.tickCount=20;NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(wolf));
        h.assertTrue(!Husbandry.managed(wolf)&&!wolf.isInLove(),"A beast that is not stock is not bred at all");
        var birth=new net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent(wolf,wolf,null);NeoForge.EVENT_BUS.post(birth);
        h.assertTrue(birth.isCanceled(),"No young come of the game's own pairing, for any animal");
        mare.discard();stallion.discard();groom.discard();wolf.discard();h.succeed();
    }}
    public static void unconsciousLiesStill(GameTestHelper h){
        h.setBlock(1,1,1,Blocks.STONE);var player=h.makeMockServerPlayer(GameType.SURVIVAL);var at=h.absoluteVec(new Vec3(1.5,2,1.5));player.setPos(at);player.setOnGround(true);player.tickCount=1;
        int brain=dev.qube.spawn_9b5fbb9.survival.SurvivalState.Part.BRAIN.ordinal();var body=SurvivalApi.state(player);body.anatomy[brain]=0;SurvivalApi.save(player,body);
        h.assertTrue(dev.qube.spawn_9b5fbb9.survival.SurvivalEvents.insensible(player),"A player whose brain has stopped answering is out cold");
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        player.setPos(at.add(1,0,0));player.setOnGround(true);NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        h.assertTrue(player.position().distanceTo(at)<.01&&player.getForcedPose()==net.minecraft.world.entity.Pose.SWIMMING,"Out cold, the body lies where it fell: it was walked a block and is back");
        dev.qube.spawn_9b5fbb9.survival.SurvivalEvents.applyCapacities(player,SurvivalApi.state(player),false);
        h.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)==0&&player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH)==0,"The legs answer nothing");
        var use=new PlayerInteractEvent.RightClickItem(player,InteractionHand.MAIN_HAND);NeoForge.EVENT_BUS.post(use);
        var touch=new PlayerInteractEvent.LeftClickBlock(player,h.absolutePos(new BlockPos(1,1,1)),net.minecraft.core.Direction.UP,PlayerInteractEvent.LeftClickBlock.Action.START);NeoForge.EVENT_BUS.post(touch);
        h.assertTrue(use.isCanceled()&&touch.isCanceled(),"Nor do the hands");
        body=SurvivalApi.state(player);body.anatomy[brain]=100;SurvivalApi.save(player,body);NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        dev.qube.spawn_9b5fbb9.survival.SurvivalEvents.applyCapacities(player,SurvivalApi.state(player),false);
        h.assertTrue(!dev.qube.spawn_9b5fbb9.survival.SurvivalEvents.insensible(player)&&player.getForcedPose()==null&&player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH)>0,"Come round, the player gets up");
        h.succeed();
    }
}
