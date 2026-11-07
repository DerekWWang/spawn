package dev.qube.spawn_9b5fbb9.agriculture;
import java.util.*;
import dev.qube.spawn_9b5fbb9.content.*;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;

import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Calendar-based maturation and one pending pregnancy per dam. Unloaded pregnancies never multiply generations. */
public final class Husbandry {
    public static final long DAY=24000;
    public record AnimalDefinition(int maturityDays,int gestationDays,int productRecoveryDays,int breedingCooldownDays){}
    private static final Map<String,AnimalDefinition> DEFAULT_RULES=Map.of("cow",new AnimalDefinition(28,12,1,4),"sheep",new AnimalDefinition(20,8,4,4),"chicken",new AnimalDefinition(12,4,1,4),"pig",new AnimalDefinition(20,8,1,4));
    private static volatile Map<String,AnimalDefinition> RULES=DEFAULT_RULES;
    public static Map<String,AnimalDefinition> definitions(){return RULES;}
    public static void reloadDefinitions(com.google.gson.JsonObject definitions){
        var replacement=new LinkedHashMap<>(DEFAULT_RULES);
        for(var entry:definitions.entrySet()){
            String species=entry.getKey();if(species.indexOf(':')>=0){var id=ContentDefinitions.id(species);if(!id.getNamespace().equals("minecraft"))throw new IllegalArgumentException("Unsupported husbandry species: "+species);species=id.getPath();}
            AnimalDefinition base=DEFAULT_RULES.get(species);if(base==null)throw new IllegalArgumentException("Unsupported husbandry species: "+species);
            var object=ContentDefinitions.object(entry.getValue(),species);ContentDefinitions.fields(object,"maturityDays","gestationDays","productRecoveryDays","breedingCooldownDays");
            replacement.put(species,new AnimalDefinition((int)ContentDefinitions.integer(object,"maturityDays",base.maturityDays,1,3650),(int)ContentDefinitions.integer(object,"gestationDays",base.gestationDays,1,3650),(int)ContentDefinitions.integer(object,"productRecoveryDays",base.productRecoveryDays,1,3650),(int)ContentDefinitions.integer(object,"breedingCooldownDays",base.breedingCooldownDays,1,3650)));
        }
        RULES=Collections.unmodifiableMap(replacement);
    }
    private static AnimalDefinition definition(Entity animal){return RULES.get(animal instanceof Cow?"cow":animal instanceof Sheep?"sheep":animal instanceof Chicken?"chicken":"pig");}
    public static boolean managed(Entity entity){return entity instanceof Cow||entity instanceof Sheep||entity instanceof Chicken||entity instanceof Pig;}
    public static int maturity(Entity animal){return definition(animal).maturityDays;}
    public static int gestation(Entity animal){return definition(animal).gestationDays;}
    public static void init(){NeoForge.EVENT_BUS.addListener(Husbandry::pre);NeoForge.EVENT_BUS.addListener(Husbandry::tick);NeoForge.EVENT_BUS.addListener(Husbandry::interact);NeoForge.EVENT_BUS.addListener(Husbandry::vanillaBirth);NeoForge.EVENT_BUS.addListener(Husbandry::join);NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> nest(event));}
    private static void join(EntityJoinLevelEvent event){if(event.getEntity() instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg){event.setCanceled(true);event.getEntity().discard();}}
    private static CompoundTag state(Animal animal,long now){
        CompoundTag data=animal.getPersistentData();
        if(!data.contains("husbandryBorn")){data.putLong("husbandryBorn",animal.isBaby()?now:now-maturity(animal)*DAY);data.putLong("husbandryLast",now);data.putDouble("nutrition",75);}
        // Existing adults may have one ready product; every subsequent product has a persisted clock.
        if(animal instanceof Chicken&&!data.contains("eggAt"))data.putLong("eggAt",now);
        if(animal instanceof Sheep&&!data.contains("woolRecovery")){data.putLong("woolRecovery",Math.max(0,data.getLongOr("woolAt",now)-now));data.putLong("woolRecoveryLast",now);}
        if(animal instanceof Cow&&!data.contains("milkRecovery")){data.putLong("milkRecovery",Math.max(0,data.getLongOr("milkAt",now)-now));data.putLong("milkRecoveryLast",now);}
        return data;
    }
    public static void beginWoolRecovery(Sheep sheep,long now){var data=sheep.getPersistentData();long duration=definition(sheep).productRecoveryDays*DAY;data.putLong("woolAt",now+duration);data.putLong("woolRecovery",duration);data.putLong("woolRecoveryLast",now);}
    public static long woolRecovery(Sheep sheep,long now){return state(sheep,now).getLongOr("woolRecovery",0);}
    public static boolean mature(Animal animal,long now){return now-state(animal,now).getLongOr("husbandryBorn",now)>=maturity(animal)*DAY;}
    private static boolean water(ServerLevel level,BlockPos pos){for(BlockPos p:BlockPos.betweenClosed(pos.offset(-4,-1,-4),pos.offset(4,1,4))){if(level.getFluidState(p).is(FluidTags.WATER))return true;if(level.getBlockState(p).is(SurvivalContent.TROUGH.get())&&level.getBlockEntity(p) instanceof WorkstationEntity trough&&trough.getPersistentData().getDoubleOr("water",0)>0)return true;}return false;}
    public static boolean watered(Animal animal){return animal.level() instanceof ServerLevel level&&water(level,animal.blockPosition());}
    private static boolean naturalWater(ServerLevel level,BlockPos pos){for(BlockPos p:BlockPos.betweenClosed(pos.offset(-4,-1,-4),pos.offset(4,1,4)))if(level.getFluidState(p).is(FluidTags.WATER))return true;return false;}
    private static long consumeProvision(ServerLevel level,BlockPos pos,long elapsed,String field,double unitsPerDay){
        long remaining=elapsed;
        for(BlockPos p:BlockPos.betweenClosed(pos.offset(-4,-1,-4),pos.offset(4,1,4))){
            if(remaining<=0)break;
            if(level.getBlockState(p).is(SurvivalContent.TROUGH.get())&&level.getBlockEntity(p) instanceof WorkstationEntity trough){var stock=trough.getPersistentData();double units=Math.max(0,stock.getDoubleOr(field,0));long available=(long)Math.floor(units*DAY/unitsPerDay+.0000001);long used=Math.min(remaining,available);if(used>0){stock.putDouble(field,Math.max(0,units-used*unitsPerDay/DAY));trough.setChanged();remaining-=used;}}
        }
        return elapsed-remaining;
    }
    private static long qualifiedTicks(long elapsed,long watered,long supplied,double initial,double fedNutrition,double threshold){
        long recovering=(long)Math.ceil(Math.max(0,threshold-initial)*DAY/20d);
        long fed=Math.max(0,supplied-recovering);
        long reserve=(long)Math.floor(Math.max(0,fedNutrition-threshold)*DAY/20d);
        return Math.min(elapsed,fed+Math.min(Math.max(0,watered-supplied),reserve));
    }
    private static boolean graze(ServerLevel level,Animal animal){return level.getBlockState(animal.blockPosition().below()).is(Blocks.GRASS_BLOCK)||level.getBlockState(animal.blockPosition()).is(Blocks.SHORT_GRASS)||level.getBlockState(animal.blockPosition().below()).is(Blocks.HAY_BLOCK);}
    private static boolean space(ServerLevel level,Animal animal,int count){return level.getEntitiesOfClass(Animal.class,animal.getBoundingBox().inflate(4)).size()+count<=10&&level.isEmptyBlock(animal.blockPosition().above());}
    private static void pre(EntityTickEvent.Pre event){if(event.getEntity().level().isClientSide())return;if(event.getEntity() instanceof Chicken chicken)chicken.eggTime=6000;}
    private static void tick(EntityTickEvent.Post event){
        if(!(event.getEntity().level() instanceof ServerLevel level)||event.getEntity().tickCount%20!=0)return;
        if(event.getEntity() instanceof ItemEntity item&&item.getItem().is(SurvivalContent.item("fertilized_egg"))){incubate(item,level);return;}
        if(!(event.getEntity() instanceof Animal animal)||!managed(animal))return;
        long now=SurvivalApi.calendar(level);CompoundTag data=state(animal,now);long last=data.getLongOr("husbandryLast",now);long elapsed=Math.max(0,now-last);boolean drinking=water(level,animal.blockPosition());
        long watered=naturalWater(level,animal.blockPosition())?elapsed:consumeProvision(level,animal.blockPosition(),elapsed,"water",1);
        long fed=graze(level,animal)?elapsed:consumeProvision(level,animal.blockPosition(),elapsed,"feed",2);
        long supplied=Math.min(watered,fed);double initial=Math.clamp(data.getDoubleOr("nutrition",75),0,100);
        double fedNutrition=Math.min(100,initial+supplied*20d/DAY);double nutrition=Math.max(0,fedNutrition-(elapsed-supplied)*20d/DAY);
        long growth=qualifiedTicks(elapsed,watered,supplied,initial,fedNutrition,10),products=qualifiedTicks(elapsed,watered,supplied,initial,fedNutrition,50);
        data.putDouble("nutrition",nutrition);data.putLong("husbandryLast",now);
        boolean wasJuvenile=last-data.getLongOr("husbandryBorn",now)<maturity(animal)*DAY;
        if(wasJuvenile)data.putLong("husbandryBorn",data.getLongOr("husbandryBorn",now)+elapsed-growth);
        boolean adult=mature(animal,now);animal.setAge(adult?0:-24000);
        if(animal instanceof Sheep sheep&&data.contains("woolAt")){
            long credited=Math.min(products,Math.max(0,now-data.getLongOr("woolRecoveryLast",last)));
            long remaining=Math.max(0,data.getLongOr("woolRecovery",0)-credited);data.putLong("woolRecovery",remaining);data.putLong("woolRecoveryLast",now);data.putLong("woolAt",now+remaining);sheep.setSheared(remaining>0||nutrition<50||!drinking);
        }
        if(animal instanceof Cow&&data.contains("milkAt")){
            long credited=Math.min(products,Math.max(0,now-data.getLongOr("milkRecoveryLast",last)));
            long remaining=Math.max(0,data.getLongOr("milkRecovery",0)-credited);data.putLong("milkRecovery",remaining);data.putLong("milkRecoveryLast",now);data.putLong("milkAt",now+remaining);
        }
        if(animal instanceof Chicken&&adult&&nutrition>=50&&drinking&&now>=data.getLongOr("eggAt",now)){
            Block.popResource(level,animal.blockPosition(),new ItemStack(Items.EGG));data.putLong("eggAt",now+definition(animal).productRecoveryDays*DAY);data.putDouble("nutrition",nutrition-5);
        }
        long due=data.getLongOr("pregnantUntil",0);int litter=data.getIntOr("litter",1);
        if(due>0&&now>=due&&nutrition>=50&&drinking&&space(level,animal,litter)){
            // Clear before spawning: reload cannot duplicate a delivery.
            data.putLong("pregnantUntil",0);data.putLong("breedAt",now+DAY*definition(animal).breedingCooldownDays);data.putLong("calvedAt",now);data.putDouble("nutrition",nutrition-20);
            for(int i=0;i<litter;i++){AgeableMob baby=animal.getBreedOffspring(level,animal);if(baby!=null){baby.setBaby(true);baby.setPos(animal.position().add((i%2)*.6,0,(i/2)*.6));baby.getPersistentData().putLong("husbandryBorn",due);baby.getPersistentData().putLong("husbandryLast",now);baby.getPersistentData().putDouble("nutrition",75);level.addFreshEntity(baby);}}
        }
    }
    private static void vanillaBirth(BabyEntitySpawnEvent event){if(managed(event.getParentA()))event.setCanceled(true);}
    private static void interact(PlayerInteractEvent.EntityInteract event){
        if(!(event.getLevel() instanceof ServerLevel)||!(event.getTarget() instanceof Animal animal)||!managed(animal)||event.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND)return;
        var held=event.getItemStack();if(!held.is(Items.BUCKET)&&!held.is(Items.SHEARS)&&!animal.isFood(held))return;
        event.setCanceled(true);event.getEntity().sendOverlayMessage(Component.literal(action(event.getEntity(),animal)));
    }
    public static String action(LivingEntity actor,Animal animal){
        if(!(actor.level() instanceof ServerLevel level)||animal.level()!=level||!actor.isAlive()||!animal.isAlive()||!managed(animal)||!SurvivalApi.isHumanoid(actor)||actor.distanceToSqr(animal)>16)return "Stand within four blocks of a living farm animal.";
        if(SurvivalApi.state(actor).manipulation()<=.05)return "Your body cannot handle livestock supplies.";
        ItemStack held=actor.getMainHandItem();long now=SurvivalApi.calendar(level);CompoundTag data=state(animal,now);String message="";
        if(held.is(Items.BUCKET)&&animal instanceof Cow){
            if(data.getLongOr("calvedAt",0)>0&&data.getLongOr("milkRecovery",0)==0&&data.getDoubleOr("nutrition",0)>=50&&water(level,animal.blockPosition())){held.shrink(1);SurvivalApi.give(actor,new ItemStack(Items.MILK_BUCKET));long duration=definition(animal).productRecoveryDays*DAY;data.putLong("milkAt",now+duration);data.putLong("milkRecovery",duration);data.putLong("milkRecoveryLast",now);data.putDouble("nutrition",data.getDoubleOr("nutrition",0)-5);message="Milk collected; recovers in "+definition(animal).productRecoveryDays+" supplied days.";}else message="Milk requires calving, nutrition, water, and product recovery.";
        }else if(held.is(Items.SHEARS)&&animal instanceof Sheep sheep){if(mature(animal,now)&&woolRecovery(sheep,now)==0&&!sheep.isSheared()&&data.getDoubleOr("nutrition",0)>=50&&watered(animal)){Block.popResource(level,animal.blockPosition(),new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("minecraft:white_wool")),2));sheep.setSheared(true);beginWoolRecovery(sheep,now);held.setDamageValue(held.getDamageValue()+1);if(held.getDamageValue()>=held.getMaxDamage()){held.setCount(0);Block.popResource(level,animal.blockPosition(),new ItemStack(SurvivalContent.item("metal_scrap")));}message="Sheared; wool needs "+definition(animal).productRecoveryDays+" well-fed days to regrow.";}else message="No mature, recovered, watered fleece available.";
        }else if(animal.isFood(held)){
            held.shrink(1);data.putDouble("nutrition",Math.min(100,data.getDoubleOr("nutrition",75)+25));data.putLong("fedAt",now);
            if(!mature(animal,now)){message="Fed juvenile; maturation still requires "+maturity(animal)+" days.";}
            else if(data.getLongOr("pregnantUntil",0)>0)message="Pregnancy due in "+Math.max(0,(data.getLongOr("pregnantUntil",0)-now)/DAY)+" days.";
            else if(now<data.getLongOr("breedAt",0)||!water(level,animal.blockPosition()))message="Breeding requires recovery and drinking water.";
            else {
                Animal mate=level.getEntitiesOfClass(Animal.class,animal.getBoundingBox().inflate(4),other->other!=animal&&other.getType()==animal.getType()&&mature(other,now)&&state(other,now).getLongOr("fedAt",-DAY)>=now-1000&&state(other,now).getLongOr("breedAt",0)<=now&&state(other,now).getLongOr("pregnantUntil",0)==0).stream().findFirst().orElse(null);
                if(mate!=null&&space(level,animal,animal instanceof Pig?4:1)){
                    data.putLong("breedAt",now+(gestation(animal)+definition(animal).breedingCooldownDays)*DAY);state(mate,now).putLong("breedAt",now+(gestation(animal)+definition(animal).breedingCooldownDays)*DAY);
                    if(animal instanceof Chicken){ItemStack egg=new ItemStack(SurvivalContent.item("fertilized_egg"));Block.popResource(level,animal.blockPosition(),egg);message="Fertilized egg laid. Place on warm hay to incubate for "+gestation(animal)+" days.";}
                    else{data.putLong("pregnantUntil",now+gestation(animal)*DAY);data.putInt("litter",animal instanceof Pig?2+level.getRandom().nextInt(3):1);message="Pregnancy begun; due in "+gestation(animal)+" days.";}
                }else message="Fed. Feed another mature adult nearby; provide water and space.";
            }
        }
        return message.isEmpty()?"Hold feed, a bucket, or shears.":message;
    }
    private static void nest(PlayerInteractEvent.RightClickBlock event){if(!(event.getLevel() instanceof ServerLevel)||!event.getItemStack().is(SurvivalContent.item("fertilized_egg")))return;event.setCanceled(true);event.getEntity().sendOverlayMessage(Component.literal(nest(event.getEntity(),event.getPos())));}
    public static String nest(LivingEntity actor,BlockPos at){
        if(!(actor.level() instanceof ServerLevel level)||!actor.isAlive()||!SurvivalApi.isHumanoid(actor)||actor.distanceToSqr(Vec3.atCenterOf(at))>16||!actor.getMainHandItem().is(SurvivalContent.item("fertilized_egg")))return "Hold a fertilized egg beside a hay nest.";
        if(!level.getBlockState(at).is(Blocks.HAY_BLOCK))return "An incubation nest needs hay.";
        ItemStack egg=actor.getMainHandItem().split(1);var tag=ItemAging.data(egg);tag.putLong("incubated",0);tag.putLong("nestLast",SurvivalApi.calendar(level));tag.putBoolean("nest",true);ItemAging.data(egg,tag);var item=new ItemEntity(level,at.getX()+.5,at.getY()+1.1,at.getZ()+.5,egg);item.setUnlimitedLifetime();item.setNoGravity(true);level.addFreshEntity(item);return "Fertilized egg placed; "+RULES.get("chicken").gestationDays+" days of warm incubation are required.";
    }
    private static void incubate(ItemEntity item,ServerLevel level){var tag=ItemAging.data(item.getItem());if(!tag.getBooleanOr("nest",false))return;long now=SurvivalApi.calendar(level),last=tag.getLongOr("nestLast",now);BlockPos pos=item.blockPosition();boolean warm=false;
        for(BlockPos p:BlockPos.betweenClosed(pos.offset(-2,-1,-2),pos.offset(2,1,2)))if(Pottery.heat(level,p))warm=true;
        if(warm&&level.getBlockState(pos.below()).is(Blocks.HAY_BLOCK)){long progress=tag.getLongOr("incubated",0)+Math.max(0,now-last);tag.putLong("incubated",progress);if(progress>=RULES.get("chicken").gestationDays*DAY&&level.getEntitiesOfClass(Animal.class,item.getBoundingBox().inflate(3)).size()<10){var chick=EntityTypes.CHICKEN.create(level,EntitySpawnReason.BREEDING);chick.setPos(item.position());chick.setBaby(true);chick.getPersistentData().putLong("husbandryBorn",now);level.addFreshEntity(chick);item.discard();return;}}
        tag.putLong("nestLast",now);ItemAging.data(item.getItem(),tag);
    }
}
