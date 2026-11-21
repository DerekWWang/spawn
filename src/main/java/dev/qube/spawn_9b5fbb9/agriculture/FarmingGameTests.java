package dev.qube.spawn_9b5fbb9.agriculture;

import dev.qube.spawn_9b5fbb9.content.FruitTreeBlock;
import dev.qube.spawn_9b5fbb9.content.GroundResourceBlock;
import dev.qube.spawn_9b5fbb9.content.ItemAging;
import dev.qube.spawn_9b5fbb9.content.Production;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.content.WorkstationEntity;
import dev.qube.spawn_9b5fbb9.controllers.WorldActions;
import dev.qube.spawn_9b5fbb9.industry.IndustrialGameTests;
import dev.qube.spawn_9b5fbb9.industry.Industry;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import dev.qube.spawn_9b5fbb9.transport.VehicleEntity;
import dev.qube.spawn_9b5fbb9.transport.Vehicles;
import dev.qube.spawn_9b5fbb9.world.ResourceFields;
import java.util.EnumMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The horse plough, spring crops, and coffee and tea from the wild to the field and the cup. */
public final class FarmingGameTests {
    private FarmingGameTests(){}
    private static final long DAY=24000;
    private static long season(){return DAY*dev.qube.spawn_9b5fbb9.Config.SEASON_DAYS.get();}
    /** Sets the calendar to a moment, keeping the stored calendar to put back. */
    private static final class Calendar implements AutoCloseable {
        private final GameTestHelper h;private final net.minecraft.nbt.CompoundTag original;
        Calendar(GameTestHelper h){this.h=h;original=h.getLevel().getServer().getCommandStorage().get(SurvivalApi.CALENDAR).copy();}
        /** Year {@code year} (from 1), season 0..3, day 1.. of that season, at noon. */
        long at(int year,int season,int day){long value=(year*4L+season)*season()+(day-1)*DAY+6000;var data=original.copy();data.putLong("sleepOffset",value-h.getLevel().getServer().overworld().getLevelData().getGameTime());h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,data);return value;}
        @Override public void close(){h.getLevel().getServer().getCommandStorage().set(SurvivalApi.CALENDAR,original);}
    }
    private static void work(LivingEntity actor){for(int i=0;i<2000&&WorldActions.inspectJob(actor).get("active").getAsBoolean();i++){actor.tickCount++;WorldActions.tick(actor);}}
    private static int dropped(GameTestHelper h,BlockPos at,String item){return h.getLevel().getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(at).inflate(4),e->e.getItem().is(SurvivalContent.item(item))).stream().mapToInt(e->e.getItem().getCount()).sum();}
    private static void clearDrops(GameTestHelper h,BlockPos at){h.getLevel().getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(at).inflate(6)).forEach(net.minecraft.world.entity.Entity::discard);}
    private static net.minecraft.world.entity.animal.equine.AbstractHorse tame(net.minecraft.world.entity.animal.equine.AbstractHorse horse){horse.setAge(0);horse.setTamed(true);return horse;}

    /** Only a tamed horse goes in the plough's traces; oxen, donkeys and wild horses are refused. */
    public static void ploughDrawnByHorses(GameTestHelper h){
        IndustrialGameTests.floor(h);var plough=Vehicles.spawn(h.getLevel(),"plough",h.absoluteVec(new Vec3(3.5,2,8.5)),-90);
        var driver=IndustrialGameTests.worker(h,3,6,Skill.FARMING,0);
        var wild=h.spawn(EntityTypes.HORSE,new BlockPos(5,2,6));wild.setAge(0);
        h.assertTrue(!Vehicles.hitch(driver,plough,wild).success(),"A wild horse will not take the traces");
        var cow=h.spawn(EntityTypes.COW,new BlockPos(5,2,4));
        var ox=Vehicles.hitch(driver,plough,cow);h.assertTrue(!ox.success()&&ox.message().contains("tamed horses"),"An ox is refused: this plough is drawn by horses: "+ox.message());
        var donkey=tame(h.spawn(EntityTypes.DONKEY,new BlockPos(6,2,6)));
        h.assertTrue(!Vehicles.hitch(driver,plough,donkey).success(),"A donkey is refused");
        var horse=tame(h.spawn(EntityTypes.HORSE,new BlockPos(7,2,6)));
        h.assertTrue(Vehicles.hitch(driver,plough,horse).success(),"A tamed horse is put to the plough");
        h.assertTrue(Vehicles.status(plough).contains("share lifted"),"A new plough travels with its share lifted: "+Vehicles.status(plough));
        h.assertTrue(Vehicles.share(driver,plough,true).success()&&plough.share,"The share is set in the ground");
        var spec=Vehicles.SPECS.get("plough");h.assertTrue(spec.teamMin()==1&&spec.teamMax()==2,"One horse in broken ground, two at most");
        var made=Industry.RECIPES.get("plough");h.assertTrue(made!=null&&made.station().equals("workshop")&&made.output().equals("vehicle:plough"),"Ploughs are made at a workshop");
        plough.releaseTeam();plough.discard();for(var e:new net.minecraft.world.entity.Entity[]{wild,cow,donkey,horse,driver})e.discard();h.succeed();
    }
    private static VehicleEntity plough(GameTestHelper h,int z,boolean grass,boolean lowered,int horses){
        for(int x=0;x<16;x++){h.setBlock(x,1,z,grass?Blocks.GRASS_BLOCK:Blocks.DIRT);if(grass&&x%3==0)h.setBlock(x,2,z,Blocks.SHORT_GRASS);}
        var plough=Vehicles.spawn(h.getLevel(),"plough",h.absoluteVec(new Vec3(2.5,2,z+.5)),-90);
        var driver=IndustrialGameTests.worker(h,1,z,Skill.FARMING,0);h.setBlock(1,1,z,grass?Blocks.GRASS_BLOCK:Blocks.DIRT);
        for(int i=0;i<horses;i++)h.assertTrue(Vehicles.hitch(driver,plough,tame(h.spawn(EntityTypes.HORSE,new BlockPos(5+i,2,z)))).success(),"Horse "+i+" harnessed");
        Vehicles.share(driver,plough,lowered);
        h.assertTrue(Vehicles.drive(driver,plough,h.absoluteVec(new Vec3(15.5,2,z+.5))).success(),"The ploughman takes the handles");
        return plough;
    }
    private static int tilled(GameTestHelper h,int z){int n=0;for(int x=0;x<16;x++)if(h.getBlockState(new BlockPos(x,1,z)).is(Blocks.FARMLAND))n++;return n;}
    /** Driven with the share down, a horse plough turns a furrow of farmland behind it; with the share up it does not; old turf needs two horses. */
    public static void ploughTurnsAFurrow(GameTestHelper h){
        IndustrialGameTests.floor(h);
        var lifted=plough(h,1,false,false,1);var cutting=plough(h,5,false,true,1);var oneOnTurf=plough(h,9,true,true,1);var pairOnTurf=plough(h,13,true,true,2);double startX=lifted.getX();
        h.runAfterDelay(380,()->{
            int open=tilled(h,5),skimmed=tilled(h,9),broken=tilled(h,13);
            h.assertTrue(open>=3,"A horse plough in broken ground turns a furrow: "+open+" blocks of farmland");
            h.assertTrue(tilled(h,1)==0&&lifted.getX()-startX>3,"With the share lifted the plough travels and cuts nothing: moved "+(lifted.getX()-startX));
            h.assertTrue(skimmed==0&&oneOnTurf.getX()-startX>2,"One horse draws the plough over old turf but cannot break it: "+skimmed+", moved "+(oneOnTurf.getX()-startX));
            h.assertTrue(broken>=2,"Two horses break the turf: "+broken);
            h.assertTrue(cutting.ploughed>=open&&cutting.ploughed>0&&Vehicles.status(cutting).contains("furrow"),"The plough keeps count of its furrow (dry bare farmland may already have slumped back): "+cutting.ploughed+" turned, "+open+" still tilled");
            boolean buried=true;for(int x=0;x<16;x++)if(h.getBlockState(new BlockPos(x,1,13)).is(Blocks.FARMLAND)&&!h.getBlockState(new BlockPos(x,2,13)).isAir())buried=false;
            h.assertTrue(buried,"Grass on the turf is turned under");
            for(var plough:new VehicleEntity[]{lifted,cutting,oneOnTurf,pairOnTurf}){plough.ejectPassengers();plough.releaseTeam();plough.discard();}
            h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(20),e->!(e instanceof net.minecraft.world.entity.player.Player)).forEach(net.minecraft.world.entity.Entity::discard);
            h.succeed();
        });
    }
    /** Ploughed ground lies open for a season before it closes up again, and takes up water as farmland should. */
    public static void tilledGroundLasts(GameTestHelper h){
        try(var calendar=new Calendar(h)){
            var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,1,3));for(int x=0;x<8;x++)for(int z=0;z<8;z++)h.setBlock(x,1,z,Blocks.DIRT);
            level.setBlock(pos,Blocks.FARMLAND.defaultBlockState(),3);level.setBlock(pos.above(3),Blocks.STONE.defaultBlockState(),3);long start=calendar.at(1,0,1); // a roof, so no rain falls on the test
            for(int i=0;i<3;i++)SeasonalCrops.randomTick(level.getBlockState(pos),level,pos,level.getRandom());
            h.assertTrue(level.getBlockState(pos).is(Blocks.FARMLAND),"Bare dry tilth does not slump back to dirt in a minute");
            calendar.at(1,0,20);SeasonalCrops.randomTick(level.getBlockState(pos),level,pos,level.getRandom());
            h.assertTrue(level.getBlockState(pos).is(Blocks.FARMLAND),"Nor in three weeks");
            calendar.at(1,1,3);SeasonalCrops.randomTick(level.getBlockState(pos),level,pos,level.getRandom());
            h.assertTrue(level.getBlockState(pos).is(Blocks.DIRT),"After a season bare it has closed up into dirt");
            level.setBlock(pos,Blocks.FARMLAND.defaultBlockState(),3);level.setBlock(pos.east(2),Blocks.WATER.defaultBlockState(),3);
            SeasonalCrops.randomTick(level.getBlockState(pos),level,pos,level.getRandom());
            h.assertTrue(level.getBlockState(pos).getValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE)==7,"Tilth beside water is moist and ready to sow");
            level.setBlock(pos.east(2),Blocks.DIRT.defaultBlockState(),3);
            // A stale clock from ground that was dug and tilled again does not count against the new tilth.
            var soil=SeasonalCrops.records(level).getCompoundOrEmpty("soil/"+pos.asLong());soil.putLong("bareSince",start-DAY*200);SeasonalCrops.records(level).put("soil/"+pos.asLong(),soil);
            level.setBlock(pos,Blocks.FARMLAND.defaultBlockState(),3);SeasonalCrops.tilled(level,pos);SeasonalCrops.randomTick(level.getBlockState(pos),level,pos,level.getRandom());
            h.assertTrue(level.getBlockState(pos).is(Blocks.FARMLAND),"Freshly tilled ground keeps no old clock");
            level.setBlock(pos.above(3),Blocks.AIR.defaultBlockState(),3);
        }
        h.succeed();
    }
    /** Nobody takes up more than they can bear: from a heavy heap a person takes what brings them to 120 kg and leaves the rest lying. */
    public static void carryingLimit(GameTestHelper h){
        var player=(net.minecraft.server.level.ServerPlayer)h.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(h.absoluteVec(new Vec3(2.5,2,2.5)));player.getInventory().clearContent();
        var clay=new ItemStack(net.minecraft.world.item.Items.CLAY_BALL,64);
        h.assertTrue(!SurvivalApi.exempt(player)&&!player.isSpectator(),"The test player is an ordinary survival player");
        h.assertTrue(dev.qube.spawn_9b5fbb9.industry.Ledger.liftable(player,clay)==32,"Thirty-two 3.75 kg lumps of clay are 120 kg: "+dev.qube.spawn_9b5fbb9.industry.Ledger.liftable(player,clay));
        h.assertTrue(dev.qube.spawn_9b5fbb9.industry.Ledger.liftable(player,new ItemStack(net.minecraft.world.item.Items.DIRT,64))==64,"Building blocks stay Minecraft's own convention");
        var drop=new ItemEntity(h.getLevel(),player.getX(),player.getY(),player.getZ(),clay.copy());drop.setNoPickUpDelay();h.getLevel().addFreshEntity(drop);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre(player,drop));
        int held=0;for(int i=0;i<player.getInventory().getContainerSize();i++)if(player.getInventory().getItem(i).is(net.minecraft.world.item.Items.CLAY_BALL))held+=player.getInventory().getItem(i).getCount();
        h.assertTrue(held==32&&drop.getItem().getCount()==32,"From a stack of 64 the player takes 32 and leaves 32: took "+held+", left "+drop.getItem().getCount());
        h.assertTrue(dev.qube.spawn_9b5fbb9.industry.Ledger.liftable(player,new ItemStack(net.minecraft.world.item.Items.CLAY_BALL,10))==0,"At 120 kg no more is taken");
        drop.discard();player.getInventory().clearContent();
        var mine=new ItemEntity(h.getLevel(),player.getX(),player.getY(),player.getZ(),new ItemStack(net.minecraft.world.item.Items.CLAY_BALL,4));mine.setNoPickUpDelay();mine.setThrower(player);h.getLevel().addFreshEntity(mine);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre(player,mine));
        h.assertTrue(mine.getItem().getCount()==4&&player.getInventory().countItem(net.minecraft.world.item.Items.CLAY_BALL)==0,"What a person has just put down is not scooped up again in passing");
        mine.discard();
        // Frozen ground: neither hoe nor plough breaks it.
        h.assertTrue(SeasonalCrops.frozenAir(-5)&&!SeasonalCrops.frozenAir(1),"Ground freezes hard below about -2 C");
        h.assertTrue(SeasonalCrops.frozenBiome(.25f,3)&&!SeasonalCrops.frozenBiome(.25f,0)&&!SeasonalCrops.frozenBiome(.95f,3),"A taiga winter freezes the ground; its spring and a jungle winter do not");
        h.succeed();
    }
    /** Spring food: ramsons in the woods and wild peas in the grass, picked only in their seasons; field peas sown in spring ripen within it. */
    public static void springFood(GameTestHelper h){
        try(var calendar=new Calendar(h)){
            calendar.at(1,0,1);
            var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);h.setBlock(1,1,1,Blocks.STONE);
            String[][] finds={{"wild_garlic","ramsons","3"},{"wild_peas","peas","2"},{"wild_peas","pea_seeds","1"}};
            for(var find:finds){
                var pos=h.absolutePos(new BlockPos(2,2,1));h.getLevel().setBlock(pos.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);h.getLevel().setBlock(pos,SurvivalContent.FORAGE.get(find[0]).get().defaultBlockState(),3);clearDrops(h,pos);
                h.assertTrue(WorldActions.forage(actor,pos).success(),find[0]+" is picked in spring");work(actor);
                h.assertTrue(dropped(h,pos,find[1])==Integer.parseInt(find[2]),find[0]+" gives "+find[2]+" "+find[1]+": "+dropped(h,pos,find[1]));
            }
            var ramsons=(GroundResourceBlock)SurvivalContent.FORAGE.get("wild_garlic").get();var peas=(GroundResourceBlock)SurvivalContent.FORAGE.get("wild_peas").get();
            var coffee=(GroundResourceBlock)SurvivalContent.FORAGE.get("wild_coffee").get();var tea=(GroundResourceBlock)SurvivalContent.FORAGE.get("wild_tea").get();
            h.assertTrue(ramsons.harvestable(actor)&&peas.harvestable(actor)&&tea.harvestable(actor)&&!coffee.harvestable(actor),"Spring: ramsons, wild peas and tea; no ripe coffee");
            calendar.at(1,1,5);h.assertTrue(!ramsons.harvestable(actor)&&peas.harvestable(actor),"By summer the ramsons are gone; the peas still pod");
            calendar.at(1,2,5);h.assertTrue(!peas.harvestable(actor)&&coffee.harvestable(actor)&&tea.harvestable(actor),"Autumn: coffee ripe, tea still flushing, peas over");
            calendar.at(1,3,5);h.assertTrue(coffee.harvestable(actor)&&!tea.harvestable(actor),"Winter: the coffee picking runs on; tea rests");
            long spring=calendar.at(1,0,1);
            h.assertTrue(SeasonalCrops.sowable("spawn_9b5fbb9:pea_crop",spring)&&!SeasonalCrops.sowable("spawn_9b5fbb9:pea_crop",calendar.at(1,1,1))&&SeasonalCrops.sowable("spawn_9b5fbb9:pea_crop",calendar.at(1,2,1)),"Field peas are sown in spring or autumn, not summer");
            var rule=SeasonalCrops.schedule("spawn_9b5fbb9:pea_crop");h.assertTrue(rule.days()<dev.qube.spawn_9b5fbb9.Config.SEASON_DAYS.get()&&!rule.frost()&&!rule.warm(),"Peas are hardy and ripen within the spring they are sown in");
            h.assertTrue(SurvivalContent.item("peas").components().has(net.minecraft.core.component.DataComponents.FOOD)&&SurvivalContent.item("ramsons").components().has(net.minecraft.core.component.DataComponents.FOOD),"Peas and ramsons are food");
            actor.discard();
        }
        h.succeed();
    }
    /** Planted coffee and tea bear from their second year, in their own seasons; frost kills coffee and a hard winter tea. */
    public static void coffeeAndTeaBushes(GameTestHelper h){
        try(var calendar=new Calendar(h)){
            var level=h.getLevel();var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);h.setBlock(1,1,1,Blocks.STONE);
            var coffeePos=h.absolutePos(new BlockPos(2,2,1));var teaPos=h.absolutePos(new BlockPos(1,2,3));
            for(var pos:new BlockPos[]{coffeePos,teaPos})level.setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);
            var coffee=(FruitTreeBlock)SurvivalContent.COFFEE_BUSH.get();var tea=(FruitTreeBlock)SurvivalContent.TEA_BUSH.get();
            long planted=calendar.at(1,0,3);
            h.assertTrue(coffee.plantable(planted)&&tea.plantable(planted)&&coffee.plantable(calendar.at(1,1,3))&&!tea.plantable(calendar.at(1,1,3))&&!coffee.plantable(calendar.at(1,2,3)),"Coffee is planted with the rains of spring and summer, tea in spring or autumn");
            h.assertTrue(!coffee.defaultBlockState().canSurvive(level,h.absolutePos(new BlockPos(1,2,1)))&&coffee.defaultBlockState().canSurvive(level,coffeePos),"A shrub is planted in soil, not on stone");
            for(var pos:new BlockPos[]{coffeePos,teaPos}){level.setBlock(pos,(pos==coffeePos?coffee:tea).defaultBlockState(),3);var record=new net.minecraft.nbt.CompoundTag();record.putLong("born",planted);SeasonalCrops.records(level).put(Long.toString(pos.asLong()),record);}
            calendar.at(1,2,10);h.assertTrue(!coffee.harvestable(actor,coffeePos)&&!tea.harvestable(actor,teaPos),"Nothing is picked in the first year");
            calendar.at(2,2,10);clearDrops(h,coffeePos);
            h.assertTrue(coffee.harvestable(actor,coffeePos)&&WorldActions.start(actor,"harvest",coffeePos).success(),"In its second autumn the coffee is picked");work(actor);
            h.assertTrue(dropped(h,coffeePos,"coffee_cherries")==6,"A coffee shrub gives six handfuls of cherries: "+dropped(h,coffeePos,"coffee_cherries"));
            calendar.at(2,3,10);h.assertTrue(!coffee.harvestable(actor,coffeePos),"Coffee gives one picking a year");
            calendar.at(3,0,2);h.assertTrue(tea.harvestable(actor,teaPos)&&WorldActions.start(actor,"harvest",teaPos).success(),"Tea is plucked in the spring flush");work(actor);
            h.assertTrue(dropped(h,teaPos,"tea_leaves")==4,"A plucking gives four handfuls of leaf");
            calendar.at(3,0,5);h.assertTrue(!tea.harvestable(actor,teaPos),"The bush needs a week to put out new shoots");
            calendar.at(3,0,10);h.assertTrue(tea.harvestable(actor,teaPos),"A week on it is plucked again");
            h.assertTrue(!tea.killedByCold(level,teaPos,-5)&&level.getBlockState(teaPos).is(tea),"Tea stands a light frost");
            h.assertTrue(coffee.killedByCold(level,coffeePos,-1)&&level.getBlockState(coffeePos).is(Blocks.DEAD_BUSH),"A frost kills coffee");
            h.assertTrue(tea.killedByCold(level,teaPos,-15)&&level.getBlockState(teaPos).is(Blocks.DEAD_BUSH),"A hard winter kills tea");
            var applePos=h.absolutePos(new BlockPos(3,2,3));level.setBlock(applePos.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(applePos,SurvivalContent.FRUIT_TREE.get().defaultBlockState(),3);
            h.assertTrue(!((FruitTreeBlock)SurvivalContent.FRUIT_TREE.get()).killedByCold(level,applePos,-30)&&level.getBlockState(applePos).is(SurvivalContent.FRUIT_TREE.get()),"An apple tree is hardy");
            actor.discard();
        }
        h.succeed();
    }
    private static ItemStack pot(boolean boiled){var pot=new ItemStack(SurvivalContent.item("fired_pot"));var data=ItemAging.data(pot);data.putInt("water",4);data.putBoolean("waterSafe",boiled);ItemAging.data(pot,data);return pot;}
    /** Dried tea is steeped and roast coffee brewed only in boiled water, and a cup slakes thirst safely and counts as a break. */
    public static void brewAndDrink(GameTestHelper h){
        h.setBlock(1,1,1,Blocks.STONE);var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(1,2,1));actor.setNoAi(true);SurvivalApi.enroll(actor);
        var kilnPos=h.absolutePos(new BlockPos(2,1,2));h.getLevel().setBlock(kilnPos,SurvivalContent.KILN.get().defaultBlockState(),3);var kiln=(WorkstationEntity)h.getLevel().getBlockEntity(kilnPos);
        kiln.fuel=20000; // a kiln already fired, so a brew that is accepted starts
        for(String[] brew:new String[][]{{"dried_tea","brew_tea"},{"roasted_coffee","brew_coffee"}}){
            actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item(brew[0]),1));actor.setItemInHand(InteractionHand.OFF_HAND,pot(false));
            String raw=Production.start(actor,kiln,brew[1]);h.assertTrue(raw.contains("boiled water"),brew[1]+" refuses stream water: "+raw);
            actor.setItemInHand(InteractionHand.OFF_HAND,pot(true));
            String boiled=Production.start(actor,kiln,brew[1]);h.assertTrue(boiled.startsWith("Started"),brew[1]+" starts with boiled water at a fired kiln: "+boiled);
            Production.cancel(actor,kiln);
        }
        for(String[] chain:new String[][]{{"coffee_cherries","dry_coffee","green_coffee"},{"green_coffee","roast_coffee","roasted_coffee"},{"tea_leaves","dry_tea","dried_tea"}}){
            var r=Production.RECIPES.get(chain[1]);h.assertTrue(r!=null&&r.input().equals("spawn_9b5fbb9:"+chain[0])&&r.output().equals("spawn_9b5fbb9:"+chain[2])&&r.station().equals("kiln"),chain[1]+" turns "+chain[0]+" to "+chain[2]+" at a kiln");
        }
        var body=SurvivalApi.state(actor);body.thirst=50;body.poisonTicks=0;long rested=body.recreationTicks;SurvivalApi.save(actor,body);
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("tea"),2));
        var drank=SurvivalApi.act(actor,"drink",null);var after=SurvivalApi.state(actor);
        h.assertTrue(drank.success()&&after.thirst>=69.9&&after.poisonTicks==0&&actor.getMainHandItem().getCount()==1,"A cup of tea slakes thirst and is safe: "+drank.message()+" thirst "+after.thirst);
        h.assertTrue(after.recreationTicks>rested,"A cup of tea is a break from work");
        actor.discard();h.succeed();
    }
    /** Each wild plant keeps to its climate, and the field gives each its share of stands. */
    public static void forageClimates(GameTestHelper h){
        var F=ResourceFields.Forage.class;
        h.assertTrue(ResourceFields.forageClimate(ResourceFields.Forage.GARLIC,10,true,false,false,false)&&!ResourceFields.forageClimate(ResourceFields.Forage.GARLIC,10,false,false,false,false)&&!ResourceFields.forageClimate(ResourceFields.Forage.GARLIC,25,true,true,true,false),"Ramsons: temperate woods, not open grass or jungle");
        h.assertTrue(ResourceFields.forageClimate(ResourceFields.Forage.PEAS,15,false,false,false,false)&&!ResourceFields.forageClimate(ResourceFields.Forage.PEAS,15,true,false,false,false)&&!ResourceFields.forageClimate(ResourceFields.Forage.PEAS,4,false,false,false,false),"Wild peas: open warm-temperate grassland");
        h.assertTrue(ResourceFields.forageClimate(ResourceFields.Forage.COFFEE,25,true,false,true,false)&&!ResourceFields.forageClimate(ResourceFields.Forage.COFFEE,15,true,true,false,false),"Wild coffee: tropical forest only");
        h.assertTrue(ResourceFields.forageClimate(ResourceFields.Forage.TEA,18,true,true,false,false)&&ResourceFields.forageClimate(ResourceFields.Forage.TEA,25,true,false,true,false)&&!ResourceFields.forageClimate(ResourceFields.Forage.TEA,8,true,true,false,true)&&!ResourceFields.forageClimate(ResourceFields.Forage.TEA,18,false,true,false,false),"Tea: warm wet hill forest, not taiga or open ground");
        var counts=new EnumMap<ResourceFields.Forage,Integer>(F);
        var seen=new java.util.HashSet<Long>();
        for(int cx=0;cx<40;cx++)for(int cz=0;cz<40;cz++)for(int i=0;i<2;i++)for(int j=0;j<2;j++)for(var patch:ResourceFields.foragePatches(11,cx*2+i,cz*2+j))
            if(Math.floorDiv(patch.x(),32)==cx&&Math.floorDiv(patch.z(),32)==cz&&seen.add(patch.salt()))counts.merge(patch.forage(),1,Integer::sum);
        int total=counts.values().stream().mapToInt(Integer::intValue).sum();
        h.assertTrue(total==1600&&counts.values().stream().allMatch(n->n>300&&n<500),"One stand to a 32-block cell, the kinds in even shares: "+counts);
        h.assertTrue(ResourceFields.foragePatches(11,5,7).equals(ResourceFields.foragePatches(11,5,7)),"The field is the same every time for a seed");
        h.succeed();
    }
}
