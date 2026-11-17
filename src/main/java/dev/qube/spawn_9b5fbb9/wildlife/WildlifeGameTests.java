package dev.qube.spawn_9b5fbb9.wildlife;

import dev.qube.spawn_9b5fbb9.content.ItemAging;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.environment.EnvironmentApi;
import dev.qube.spawn_9b5fbb9.environment.EnvironmentModel;
import dev.qube.spawn_9b5fbb9.environment.WorldEnvironment;
import dev.qube.spawn_9b5fbb9.industry.IndustrialGameTests;
import dev.qube.spawn_9b5fbb9.survival.*;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Rain on fire, illness, cloth and predators, on the real blocks, bodies and animals. */
public final class WildlifeGameTests {
    private static final long DAY=24000,LIFE=672000;
    private WildlifeGameTests(){}
    private static LivingEntity person(GameTestHelper h,int x,int z){var actor=h.spawn(EntityTypes.VILLAGER,new BlockPos(x,2,z));actor.setNoAi(true);SurvivalApi.enroll(actor);return actor;}
    private static Player player(GameTestHelper h,double x,double z){var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(h.absoluteVec(new Vec3(x,2,z)));return player;}
    /** A predator that stands still and senses only its own arena. */
    private static <T extends PathfinderMob> T beast(GameTestHelper h,EntityType<T> type,int x,int z){
        T mob=h.spawn(type,new BlockPos(x,2,z));mob.setNoAi(true);mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(7);return mob;
    }
    private static ItemStack garment(String id,long wear){
        ItemStack stack=new ItemStack(SurvivalContent.item(id));var data=ItemAging.data(stack);data.putLong("garmentWear",wear);ItemAging.data(stack,data);return stack;
    }
    private static void wear(LivingEntity actor,int slot,String id,long wear){var body=SurvivalApi.state(actor);body.clothing[slot]="spawn_9b5fbb9:"+id;body.clothingWear[slot]=wear;SurvivalApi.save(actor,body);}

    public static void rainDousesFire(GameTestHelper h){
        IndustrialGameTests.floor(h);ServerLevel level=h.getLevel();
        var old=WorldEnvironment.replaceForTest(level,new EnvironmentModel.Settings("vanilla",0,28,2));
        var store=level.getServer().getCommandStorage();var clock=store.get(SurvivalApi.CALENDAR).copy();
        int radius=level.getGameRules().get(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
        BlockPos fire=h.absolutePos(new BlockPos(8,2,8)),camp=h.absolutePos(new BlockPos(4,2,4));
        try{
            level.getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER,-1,level.getServer());
            long wet=-1,dry=-1;
            for(long t=0;t<448*DAY&&(wet<0||dry<0);t+=3000){
                var a=EnvironmentApi.at(level,fire,t);var b=EnvironmentApi.at(level,camp,t);
                if(wet<0&&a.rain()>.6&&b.rain()>.6&&!a.snow()&&!b.snow())wet=t;
                if(dry<0&&a.rain()==0&&b.rain()==0)dry=t;
            }
            h.assertTrue(wet>=0&&dry>=0,"The regional weather must offer both a downpour and a dry spell");
            h.assertTrue(level.getRainLevel(1)==0,"Regional weather leaves the world-wide rain flag dry, which is why fire once ignored it");
            calendar(level,clock,dry);
            h.assertTrue(burns(level,fire)==60,"In dry weather a fire on bare ground is never put out by the sky");
            level.setBlock(camp,Blocks.CAMPFIRE.defaultBlockState(),3);level.tickPrecipitation(camp);
            h.assertTrue(level.getBlockState(camp).getValue(CampfireBlock.LIT),"A campfire burns on through dry weather");
            calendar(level,clock,wet);
            // Fire is rained on from the four cells beside it as well, so the roof covers them too.
            for(BlockPos p:BlockPos.betweenClosed(fire.offset(-1,3,-1),fire.offset(1,3,1)))level.setBlock(p,Blocks.GLASS.defaultBlockState(),3);
            level.setBlock(camp.above(3),Blocks.GLASS.defaultBlockState(),3);
            h.assertTrue(burns(level,fire)==60,"Under a roof the downpour does not reach the fire");
            level.tickPrecipitation(camp);h.assertTrue(level.getBlockState(camp).getValue(CampfireBlock.LIT),"A roofed campfire keeps burning in the rain");
            for(BlockPos p:BlockPos.betweenClosed(fire.offset(-1,3,-1),fire.offset(1,3,1)))level.removeBlock(p,false);
            level.removeBlock(camp.above(3),false);
            h.assertTrue(burns(level,fire)<60,"Regional rain puts out fire in the open");
            level.tickPrecipitation(camp);h.assertTrue(!level.getBlockState(camp).getValue(CampfireBlock.LIT),"A downpour drowns a campfire left open to the sky");
        }finally{
            store.set(SurvivalApi.CALENDAR,clock);WorldEnvironment.replaceForTest(level,old);level.getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER,radius,level.getServer());
            for(BlockPos p:BlockPos.betweenClosed(fire.offset(-1,3,-1),fire.offset(1,3,1)))level.removeBlock(p,false);
            for(BlockPos p:new BlockPos[]{fire,camp,camp.above(3)})level.removeBlock(p,false);
        }
        h.succeed();
    }
    private static void calendar(ServerLevel level,net.minecraft.nbt.CompoundTag original,long time){
        var clock=original.copy();clock.putLong("sleepOffset",time-level.getServer().overworld().getLevelData().getGameTime());level.getServer().getCommandStorage().set(SurvivalApi.CALENDAR,clock);
    }
    /** Light a fresh fire sixty times and let each burn one tick: how many are still alight. A young fire on bare ground dies only to rain. */
    private static int burns(ServerLevel level,BlockPos pos){
        int alight=0;
        for(int i=0;i<60;i++){
            level.setBlock(pos,Blocks.FIRE.defaultBlockState(),3);level.getBlockState(pos).tick(level,pos,level.getRandom());
            if(level.getBlockState(pos).is(Blocks.FIRE))alight++;
        }
        level.removeBlock(pos,false);return alight;
    }

    public static void illnessOnsetAndSpread(GameTestHelper h){
        IndustrialGameTests.floor(h);ServerLevel level=h.getLevel();
        var carrier=person(h,8,8);var near=person(h,9,8);var far=person(h,8,12);
        var body=SurvivalApi.state(carrier);
        body.rules=new SimulationRules(Map.of("respiratoryOnsetChancePerDay",0d,"systemicOnsetChancePerDay",0d));
        for(int i=0;i<200;i++)SurvivalEvents.onset(carrier,body,DAY);
        h.assertTrue(body.mildTicks==0&&body.severeTicks==0,"With no chance of onset nobody falls ill");
        body.rules=new SimulationRules(Map.of("respiratoryOnsetChancePerDay",1d,"systemicOnsetChancePerDay",0d));
        SurvivalEvents.onset(carrier,body,DAY);
        long incubation=body.rules.ticks("mildIncubationTicks"),symptoms=body.rules.ticks("mildSymptomsTicks");
        h.assertTrue(body.mildTicks==incubation+symptoms&&body.severeTicks==0,"Illness begins by chance, with nobody to catch it from");
        h.assertTrue(!body.infectious(false)&&body.conditions().equals("Stable"),"Early in the incubation the carrier neither shows it nor passes it on");
        h.assertTrue(SurvivalEvents.transmit(carrier,body,level,1)==0&&SurvivalApi.state(near).mildTicks==0,"Nothing spreads before the carrier is infectious");
        body.mildTicks=symptoms+incubation/4;
        h.assertTrue(body.infectious(false)&&!body.mildSymptoms(),"A carrier is infectious before the first symptom");
        body.mildTicks=symptoms;h.assertTrue(body.mildSymptoms()&&body.conditions().contains("RESPIRATORY"),"Symptoms show once the incubation is over");
        h.assertTrue(SurvivalEvents.transmit(carrier,body,level,1)==1,"In the open only the person within two metres is exposed");
        h.assertTrue(SurvivalApi.state(near).mildTicks>0&&SurvivalApi.state(far).mildTicks==0,"Four metres of open air is out of range of a cough");
        for(int z=7;z<=13;z++)for(int x=7;x<=9;x++)h.setBlock(x,5,z,Blocks.GLASS);
        h.assertTrue(SurvivalEvents.transmit(carrier,body,level,1)==1&&SurvivalApi.state(far).mildTicks>0,"Under one roof the whole room shares the air");
        var recovered=SurvivalApi.state(far);recovered.treat("antibiotic");
        h.assertTrue(!recovered.infect(false),"Someone who has just had it does not catch it again");
        h.succeed();
    }

    public static void clothWearsWithUse(GameTestHelper h){
        var idle=dressed();idle.advance(1000,SurvivalState.Activity.IDLE,SurvivalState.Climate.MILD,false,false);
        var asleep=dressed();asleep.advance(1000,SurvivalState.Activity.IDLE,SurvivalState.Climate.MILD,false,true);
        var walking=dressed();walking.advance(1000,SurvivalState.Activity.WALKING,SurvivalState.Climate.MILD,false,false);
        var running=dressed();running.advance(1000,SurvivalState.Activity.SPRINTING,SurvivalState.Climate.MILD,false,false);
        var soaked=dressed();soaked.advance(1000,SurvivalState.Activity.WALKING,new SurvivalState.Climate(18,0,true,false,false),false,false);
        h.assertTrue(asleep.clothingWear[0]==250&&idle.clothingWear[0]==500&&walking.clothingWear[0]==1000&&running.clothingWear[0]==2000&&soaked.clothingWear[0]==1500,"A shirt wears with what is done in it, and faster wet");
        h.assertTrue(idle.clothingWear[3]==50&&walking.clothingWear[3]==1500&&running.clothingWear[3]==3000,"Shoes wear on the road, hardly at all at rest");
        var coat=new SurvivalState();coat.clothing[2]="spawn_9b5fbb9:wool_jacket";
        double fresh=coat.insulation();coat.clothingWear[2]=LIFE/2;double worn=coat.insulation();
        h.assertTrue(Math.abs(fresh-.35)<1e-9&&Math.abs(worn-.35*.75)<1e-9&&Math.abs(coat.condition(2)-.5)<1e-9,"A coat half worn through keeps three quarters of its warmth");
        coat.clothingWear[2]=LIFE-10;coat.advance(20,SurvivalState.Activity.WALKING,SurvivalState.Climate.MILD,false,false);
        h.assertTrue(coat.clothing[2].isEmpty()&&(coat.wornOut&4)!=0&&coat.insulation()==0,"Worn right through, the coat falls apart and is reported");
        var torn=new SurvivalState();torn.clothing[1]="spawn_9b5fbb9:fiber_pants";torn.tear(1,24000);
        h.assertTrue(torn.clothingWear[1]==24000,"A cut takes life out of the cloth it passes through");
        h.succeed();
    }
    private static SurvivalState dressed(){var body=new SurvivalState();body.clothing[0]="spawn_9b5fbb9:wool_shirt";body.clothing[3]="spawn_9b5fbb9:wool_shoes";return body;}

    public static void clothMendSwapUndress(GameTestHelper h){
        IndustrialGameTests.floor(h);ServerLevel level=h.getLevel();
        var tailor=player(h,8.5,8.5);wear(tailor,2,"wool_jacket",LIFE/2);
        tailor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("cordage"),3));
        h.assertTrue(!SurvivalApi.act(tailor,"mend",null).success()&&tailor.getMainHandItem().getCount()==3,"Cordage does not patch wool");
        tailor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SurvivalContent.item("yarn"),3));
        h.assertTrue(SurvivalApi.act(tailor,"mend",null).success()&&SurvivalApi.state(tailor).clothingWear[2]==LIFE/2-LIFE/5,"A length of yarn gives back a fifth of the jacket's life");
        h.assertTrue(SurvivalApi.act(tailor,"mend",null).success()&&SurvivalApi.state(tailor).clothingWear[2]==LIFE/5,"Patching stops a fifth short of new");
        h.assertTrue(!SurvivalApi.act(tailor,"mend",null).success()&&tailor.getMainHandItem().getCount()==1,"A sound garment takes no patch and no yarn");
        tailor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        var undressed=SurvivalApi.act(tailor,"undress",null);
        h.assertTrue(undressed.success()&&SurvivalApi.state(tailor).clothing[2].isEmpty(),"The outer garment comes off first");
        h.assertTrue(undressed.message().equals("Took off Wool Jacket"),"The answer names the garment taken off: "+undressed.message());
        ItemStack off=ItemStack.EMPTY;for(int i=0;i<tailor.getInventory().getContainerSize();i++)if(tailor.getInventory().getItem(i).is(SurvivalContent.item("wool_jacket")))off=tailor.getInventory().getItem(i);
        h.assertTrue(!off.isEmpty()&&ItemAging.data(off).getLongOr("garmentWear",0)==LIFE/5,"What is taken off keeps its wear");
        h.assertTrue(!SurvivalApi.act(tailor,"undress_pants",null).success(),"Nothing comes off a bare leg");

        var neighbour=person(h,9,8);wear(neighbour,2,"wool_jacket",LIFE*9/10);
        h.assertTrue(!SurvivalApi.act(tailor,"undress",neighbour).success()&&SurvivalApi.state(neighbour).clothing[2].endsWith("wool_jacket"),"Nobody awake is stripped of a coat");
        tailor.setItemInHand(InteractionHand.MAIN_HAND,garment("fiber_jacket",0));
        h.assertTrue(!SurvivalApi.act(tailor,"clothe",neighbour).success()&&tailor.getMainHandItem().getCount()==1,"A thin new jacket is refused for a ragged warm one");
        tailor.setItemInHand(InteractionHand.MAIN_HAND,garment("wool_jacket",0));
        h.assertTrue(SurvivalApi.act(tailor,"clothe",neighbour).success()&&SurvivalApi.state(neighbour).clothingWear[2]==0&&tailor.getMainHandItem().isEmpty(),"A sounder coat of the same kind is accepted in exchange");
        int ragged=0;for(int i=0;i<tailor.getInventory().getContainerSize();i++){var stack=tailor.getInventory().getItem(i);if(stack.is(SurvivalContent.item("wool_jacket"))&&ItemAging.data(stack).getLongOr("garmentWear",0)==LIFE*9/10)ragged++;}
        h.assertTrue(ragged==1,"The ragged coat comes back to the giver, wear and all");
        var helpless=SurvivalApi.state(neighbour);helpless.injure(SurvivalState.Part.BRAIN,100,false);SurvivalApi.save(neighbour,helpless);
        h.assertTrue(SurvivalApi.act(tailor,"undress",neighbour).success()&&SurvivalApi.state(neighbour).clothing[2].isEmpty(),"Someone unconscious can be undressed by another");

        var burnt=person(h,5,5);wear(burnt,0,"wool_shirt",0);wear(burnt,1,"wool_pants",0);
        burnt.hurtServer(level,level.damageSources().onFire(),2);
        var after=SurvivalApi.state(burnt);
        h.assertTrue(after.clothingWear[0]>=20000&&after.clothingWear[1]>=20000,"Flame scorches every garment worn");
        h.succeed();
    }

    public static void rangesOnTheMap(GameTestHelper h){
        h.assertTrue(spawns(h,net.minecraft.world.level.biome.Biomes.TAIGA,BrownBearEntities.BROWN_BEAR)&&spawns(h,net.minecraft.world.level.biome.Biomes.FOREST,BrownBearEntities.BROWN_BEAR)&&spawns(h,net.minecraft.world.level.biome.Biomes.MEADOW,BrownBearEntities.BROWN_BEAR),"Brown bears live in taiga, forest and mountain country");
        h.assertTrue(spawns(h,net.minecraft.world.level.biome.Biomes.SAVANNA,LionEntities.LION)&&!spawns(h,net.minecraft.world.level.biome.Biomes.TAIGA,LionEntities.LION),"Lions live on the savanna and nowhere cold");
        h.assertTrue(!spawns(h,net.minecraft.world.level.biome.Biomes.DESERT,BrownBearEntities.BROWN_BEAR)&&!spawns(h,net.minecraft.world.level.biome.Biomes.SAVANNA,BrownBearEntities.BROWN_BEAR),"No bears in desert or savanna");
        h.succeed();
    }
    private static boolean spawns(GameTestHelper h,net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> biome,EntityType<?> type){
        var info=h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(biome).value().modifiableBiomeInfo().get();
        var settings=info.attributes().applyModifier(net.minecraft.world.attribute.EnvironmentAttributes.NATURAL_MOB_SPAWNS,net.minecraft.world.level.biome.MobSpawnSettings.EMPTY);
        return settings.getMobsToSpawn(MobCategory.CREATURE).unwrap().stream().anyMatch(entry->entry.value().type()==type);
    }

    /** The spawner must never be offered a creature the world forbids: it would build one, see it discarded, and try again every tick. */
    public static void spawnerNotOfferedForbidden(GameTestHelper h){
        var bad=new java.util.TreeSet<String>();
        h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).listElements().forEach(biome->{
            var settings=biome.value().modifiableBiomeInfo().get().attributes().applyModifier(net.minecraft.world.attribute.EnvironmentAttributes.NATURAL_MOB_SPAWNS,net.minecraft.world.level.biome.MobSpawnSettings.EMPTY);
            for(var category:MobCategory.values())for(var entry:settings.getMobsToSpawn(category).unwrap())
                if(entry.value().type().builtInRegistryHolder().is(dev.qube.spawn_9b5fbb9.world.NaturalWorldRules.FORBIDDEN_ENTITIES))
                    bad.add(biome.key().identifier().getPath()+": "+net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entry.value().type()).getPath());
        });
        h.assertTrue(bad.isEmpty(),bad.size()+" forbidden spawn entries remain, e.g. "+bad.stream().limit(4).toList());
        var plains=h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS).value().modifiableBiomeInfo().get().attributes().applyModifier(net.minecraft.world.attribute.EnvironmentAttributes.NATURAL_MOB_SPAWNS,net.minecraft.world.level.biome.MobSpawnSettings.EMPTY);
        h.assertTrue(plains.getMobsToSpawn(MobCategory.CREATURE).unwrap().stream().anyMatch(entry->entry.value().type()==EntityTypes.SHEEP),"Plains still offer sheep");
        h.succeed();
    }

    /** The calendar-crop question is asked for every random tick, so it is answered per block, and answered the same as the rules. */
    public static void calendarCropsKnownByBlock(GameTestHelper h){
        int covered=0;
        for(String id:dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.schedules().keySet()){
            var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.Identifier.parse(id));
            if(block.isEmpty())continue;
            covered++;
            h.assertTrue(dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.requiresCalendarTick(block.get().defaultBlockState()),id+" has a crop rule and must take calendar ticks");
            h.assertTrue(block.get().defaultBlockState().isRandomlyTicking(),id+" must be randomly ticked");
        }
        h.assertTrue(covered>=5,"The crop rules name real blocks");
        for(var plain:java.util.List.of(Blocks.STONE,Blocks.GRASS_BLOCK,Blocks.OAK_LEAVES,Blocks.WATER,Blocks.AIR))
            h.assertTrue(!dev.qube.spawn_9b5fbb9.agriculture.SeasonalCrops.requiresCalendarTick(plain.defaultBlockState()),plain+" has no crop rule");
        h.assertTrue(Blocks.GRASS_BLOCK.defaultBlockState().isRandomlyTicking()&&!Blocks.STONE.defaultBlockState().isRandomlyTicking(),"Vanilla random ticking is untouched for blocks without a rule");
        h.succeed();
    }

    /** Litter does not pile up for ever: organic drops rot to fibre, and fibre and spoiled food rot away. Rock stays. */
    public static void litterRotsAway(GameTestHelper h){
        ServerLevel level=h.getLevel();var store=level.getServer().getCommandStorage();var clock=store.get(SurvivalApi.CALENDAR).copy();
        Vec3 at=h.absoluteVec(new Vec3(1.5,2,1.5));
        var sapling=new ItemEntity(level,at.x,at.y,at.z,new ItemStack(Items.OAK_SAPLING));
        var fish=new ItemEntity(level,at.x,at.y,at.z,new ItemStack(Items.TROPICAL_FISH));
        var coal=new ItemEntity(level,at.x,at.y,at.z,new ItemStack(Items.COAL));
        try{
            long start=SurvivalApi.calendar(level)+DAY;calendar(level,clock,start);
            for(var drop:java.util.List.of(sapling,fish,coal)){drop.setNoGravity(true);level.addFreshEntity(drop);ItemAging.weather(drop);}
            calendar(level,clock,start+40*DAY);
            for(var drop:java.util.List.of(sapling,fish,coal))ItemAging.weather(drop);
            h.assertTrue(sapling.isAlive()&&sapling.getItem().is(SurvivalContent.item("plant_fiber")),"A sapling left out rots to fibre first");
            h.assertTrue(fish.isAlive()&&ItemAging.spoiled(fish.getItem()),"A fish left out spoils first");
            for(var drop:java.util.List.of(sapling,fish,coal))ItemAging.weather(drop);
            calendar(level,clock,start+80*DAY);
            for(var drop:java.util.List.of(sapling,fish,coal))ItemAging.weather(drop);
            h.assertTrue(!sapling.isAlive(),"Fibre left out rots away");
            h.assertTrue(!fish.isAlive(),"Spoiled food left out rots away");
            h.assertTrue(coal.isAlive()&&coal.getItem().is(Items.COAL),"Coal does not rot");
        }finally{store.set(SurvivalApi.CALENDAR,clock);sapling.discard();fish.discard();coal.discard();}
        h.succeed();
    }

    /** What a bot without the mod is told about the mod's content must match what the server really uses. */
    public static void registryExportMatchesServer(GameTestHelper h){
        var root=dev.qube.spawn_9b5fbb9.controllers.RegistryExport.build();
        com.google.gson.JsonObject kiln=null,ore=null;int previous=root.get("vanillaStates").getAsInt()-1;
        for(var element:root.getAsJsonArray("blocks")){
            var block=element.getAsJsonObject();
            h.assertTrue(!block.get("name").getAsString().startsWith("minecraft:"),"Only non-vanilla blocks are exported");
            h.assertTrue(block.get("minStateId").getAsInt()==previous+1,"Mod block states follow the vanilla ones without a gap: "+block.get("name"));
            previous=block.get("maxStateId").getAsInt();
            int count=1;for(var p:block.getAsJsonArray("states"))count*=p.getAsJsonObject().get("num_values").getAsInt();
            h.assertTrue(previous-block.get("minStateId").getAsInt()+1==count&&block.getAsJsonArray("shapes").size()==count,"A block has one state and one shape per combination of its properties: "+block.get("name"));
            if(block.get("name").getAsString().equals("spawn_9b5fbb9:kiln"))kiln=block;
            if(block.get("name").getAsString().equals("spawn_9b5fbb9:hematite_ore"))ore=block;
        }
        h.assertTrue(kiln!=null&&ore!=null,"The kiln and haematite ore are exported");
        var real=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("spawn_9b5fbb9:kiln"));
        h.assertTrue(kiln.get("defaultState").getAsInt()==net.minecraft.world.level.block.Block.getId(real.defaultBlockState())&&kiln.get("boundingBox").getAsString().equals("block"),"The exported kiln has the state id the server sends and is solid");
        h.assertTrue(ore.get("material").getAsString().equals("mineable/pickaxe")&&ore.get("hardness").getAsFloat()>0,"Ore is dug with a pickaxe");
        boolean jacket=false,bear=false;
        for(var element:root.getAsJsonArray("items")){var item=element.getAsJsonObject();if(item.get("name").getAsString().equals("spawn_9b5fbb9:wool_jacket"))jacket=item.get("id").getAsInt()==net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(SurvivalContent.item("wool_jacket"));}
        for(var element:root.getAsJsonArray("entities")){var entity=element.getAsJsonObject();if(entity.get("name").getAsString().equals("spawn_9b5fbb9:brown_bear"))bear=entity.get("id").getAsInt()==net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getId(BrownBearEntities.BROWN_BEAR)&&Math.abs(entity.get("width").getAsFloat()-1.3f)<1e-6;}
        h.assertTrue(jacket&&bear,"Items and entity types carry the ids and sizes the server uses");
        h.succeed();
    }

    public static void hungerDrivesHunting(GameTestHelper h){
        IndustrialGameTests.floor(h);ServerLevel level=h.getLevel();long now=SurvivalApi.calendar(level);
        var bear=beast(h,BrownBearEntities.BROWN_BEAR,8,8);var species=Predators.species(bear);
        h.assertTrue(species!=null&&bear.getMaxHealth()==40&&bear.getAttributeValue(Attributes.ATTACK_DAMAGE)==9&&Predators.predator(bear),"The brown bear is a registered predator with a bear's bulk");
        var sheep=h.spawn(EntityTypes.SHEEP,new BlockPos(11,2,8));sheep.setNoAi(true);
        Predators.starve(bear,species,now,0);
        h.assertTrue(Predators.appetite(bear,species,now)==0&&Predators.choose(bear,species,level)==null,"A fed bear leaves the flock alone");
        Predators.starve(bear,species,now,1.2);
        h.assertTrue(Predators.choose(bear,species,level)==sheep,"Once the meal has worn off it takes the nearest stock");
        h.setBlock(12,2,9,Blocks.CAMPFIRE);
        h.assertTrue(Predators.choose(bear,species,level)==null,"A fire burning by the fold keeps it off");
        h.setBlock(12,2,9,Blocks.AIR);
        Predators.feed(bear,species,now,.5);
        h.assertTrue(Math.abs(Predators.appetite(bear,species,now)-.6)<.01,"Half a meal takes half the hunger away");
        Predators.feed(bear,species,now,1);h.assertTrue(Predators.appetite(bear,species,now)==0,"A full meal sates it");
        sheep.discard();
        var lion=beast(h,LionEntities.LION,8,5);var cat=Predators.species(lion);
        var cow=h.spawn(EntityTypes.COW,new BlockPos(8,2,3));cow.setNoAi(true);
        Predators.starve(lion,cat,now,1.2);
        h.assertTrue((Predators.choose(lion,cat,level)==cow)==level.isDarkOutside(),"A lion that is merely hungry hunts by night");
        Predators.starve(lion,cat,now,2.5);
        h.assertTrue(Predators.choose(lion,cat,level)==cow,"A starving lion hunts by day as well, and takes cattle alone");
        h.succeed();
    }

    public static void peopleAreNotOrdinaryPrey(GameTestHelper h){
        IndustrialGameTests.floor(h);ServerLevel level=h.getLevel();long now=SurvivalApi.calendar(level);
        var bear=beast(h,BrownBearEntities.BROWN_BEAR,8,5);var species=Predators.species(bear);
        var traveller=person(h,8,11);
        Predators.starve(bear,species,now,1.5);
        h.assertTrue(Predators.choose(bear,species,level)==null,"A hungry bear with no stock about still leaves a person at a distance alone");
        Predators.starve(bear,species,now,2.5);
        h.assertTrue(Predators.choose(bear,species,level)==traveller,"A starving bear goes for someone alone");
        traveller.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.TORCH));
        h.assertTrue(Predators.choose(bear,species,level)==null,"A burning torch in the hand keeps it back");
        traveller.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        var second=person(h,9,11);var third=person(h,8,12);
        h.assertTrue(Predators.company(level,traveller)==3&&Predators.choose(bear,species,level)==null,"Three people together are not approached");
        second.discard();third.discard();
        Predators.starve(bear,species,now,0);bear.setPos(h.absoluteVec(new Vec3(8.5,2,8.5)));
        h.assertTrue(Predators.choose(bear,species,level)==traveller,"Walked up on at three metres, even a fed bear attacks");
        h.succeed();
    }

    public static void wolvesNeedThePack(GameTestHelper h){
        IndustrialGameTests.floor(h);ServerLevel level=h.getLevel();long now=SurvivalApi.calendar(level);
        var lead=beast(h,EntityTypes.WOLF,8,3);var species=Predators.species(lead);
        var traveller=person(h,8,8);var cow=h.spawn(EntityTypes.COW,new BlockPos(5,2,5));cow.setNoAi(true);
        Predators.starve(lead,species,now,2.5);
        h.assertTrue(Predators.pack(lead)==1&&Predators.choose(lead,species,level)==null,"One starving wolf takes neither a person nor a cow");
        var second=beast(h,EntityTypes.WOLF,7,3);var third=beast(h,EntityTypes.WOLF,9,3);
        h.assertTrue(Predators.pack(lead)==3&&Predators.choose(lead,species,level)==traveller,"A starving pack of three goes for someone alone");
        Predators.starve(lead,species,now,1.2);
        h.assertTrue(Predators.choose(lead,species,level)==cow,"A pack that is only hungry pulls down the cow and leaves the healthy traveller");
        var hurt=SurvivalApi.state(traveller);hurt.injure(SurvivalState.Part.LEFT_LEG,40,true);SurvivalApi.save(traveller,hurt);
        h.assertTrue(Predators.choose(lead,species,level)==traveller,"Someone bleeding is the easier meal");
        second.setTame(true,false);
        h.assertTrue(Predators.pack(lead)==2&&Predators.choose(second,species,level)==null,"A tame dog is no part of the pack and hunts nobody");
        third.discard();h.succeed();
    }

    public static void routBiteAndCarcass(GameTestHelper h){
        IndustrialGameTests.floor(h);ServerLevel level=h.getLevel();long now=SurvivalApi.calendar(level);
        var bear=beast(h,BrownBearEntities.BROWN_BEAR,8,8);var species=Predators.species(bear);Predators.starve(bear,species,now,2.5);
        var victim=person(h,8,11);wear(victim,2,"wool_jacket",0);wear(victim,1,"wool_pants",0);
        victim.hurtServer(level,level.damageSources().mobAttack(bear),4);
        var wounds=SurvivalApi.state(victim);
        h.assertTrue(wounds.bleeding>0,"Teeth and claws open bleeding wounds");
        h.assertTrue(wounds.clothingWear[2]+wounds.clothingWear[1]>0||wounds.anatomy[SurvivalState.Part.BRAIN.ordinal()]<100,"The cloth over the bitten limb is torn");
        var sheep=h.spawn(EntityTypes.SHEEP,new BlockPos(5,2,5));sheep.setNoAi(true);
        sheep.hurtServer(level,level.damageSources().mobAttack(bear),100);
        h.assertTrue(!sheep.isAlive()&&Predators.appetite(bear,species,SurvivalApi.calendar(level))<.01,"The kill is the meal");
        boolean meat=!level.getEntitiesOfClass(ItemEntity.class,sheep.getBoundingBox().inflate(3),drop->drop.getItem().has(DataComponents.FOOD)).isEmpty();
        h.assertTrue(!meat,"What the bear killed it ate: no mutton is left for anyone else");
        Predators.starve(bear,species,now,2.5);
        bear.hurtServer(level,level.damageSources().generic(),32);
        h.assertTrue(bear.isAlive()&&Predators.routed(bear)&&Predators.choose(bear,species,level)==null,"Badly hurt, the bear breaks off and hunts nobody");
        h.succeed();
    }

    public static void hungryBearTakesBait(GameTestHelper h){
        IndustrialGameTests.floor(h);ServerLevel level=h.getLevel();long now=SurvivalApi.calendar(level);
        var bear=beast(h,BrownBearEntities.BROWN_BEAR,8,8);var species=Predators.species(bear);Predators.starve(bear,species,now,1.5);
        var bait=new ItemEntity(level,bear.getX()+1,bear.getY(),bear.getZ(),new ItemStack(Items.BEEF,40));bait.setDeltaMovement(Vec3.ZERO);level.addFreshEntity(bait);
        bear.tickCount=20;NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(bear));
        h.assertTrue(bait.getItem().getCount()==10&&Predators.appetite(bear,species,SurvivalApi.calendar(level))<.01,"A hungry bear eats a full meal of thirty cuts from meat left lying and is sated");
        bear.tickCount=40;NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(bear));
        h.assertTrue(bait.getItem().getCount()==10,"Fed, it leaves the rest");
        h.succeed();
    }

    /** The whole chain with the animal's own will: it sees the sheep, runs it down, kills it and is fed. */
    public static void bearRunsDownSheep(GameTestHelper h){
        IndustrialGameTests.floor(h);ServerLevel level=h.getLevel();
        for(int i=3;i<=13;i++)for(int y=2;y<=4;y++){h.setBlock(i,y,3,Blocks.STONE);h.setBlock(i,y,13,Blocks.STONE);h.setBlock(3,y,i,Blocks.STONE);h.setBlock(13,y,i,Blocks.STONE);}
        var bear=h.spawn(BrownBearEntities.BROWN_BEAR,new BlockPos(5,2,8));bear.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(9);
        var species=Predators.species(bear);Predators.starve(bear,species,SurvivalApi.calendar(level),1.5);
        var sheep=h.spawn(EntityTypes.SHEEP,new BlockPos(11,2,8));sheep.setNoAi(true);
        h.succeedWhen(()->{
            h.assertTrue(!sheep.isAlive(),"The hungry bear has not yet killed the sheep");
            h.assertTrue(Predators.appetite(bear,species,SurvivalApi.calendar(level))<.01,"The bear that killed is fed");
            bear.discard();
        });
    }
}
