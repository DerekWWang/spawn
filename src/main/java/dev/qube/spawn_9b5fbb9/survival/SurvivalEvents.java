package dev.qube.spawn_9b5fbb9.survival;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.network.SurvivalNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.*;
import java.util.UUID;

public final class SurvivalEvents {
    private static final Identifier MOVE=id("movement"),HANDS=id("hands"),ATTACK=id("attack"),MIND=id("mind"),LEGS=id("legs");
    private static Identifier id(String name){return Identifier.fromNamespaceAndPath("spawn_9b5fbb9",name);}
    private static long sleepBefore=-1;
    private static final java.util.Map<UUID,Boolean> sleepStates=new java.util.HashMap<>();
    private SurvivalEvents(){}
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){SurvivalCommands.register(e.getDispatcher());}
    @SubscribeEvent public static void tick(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof LivingEntity actor)||!(actor.level() instanceof ServerLevel level)||!actor.isAlive()||
            actor.tickCount%20!=0||!SurvivalApi.isHumanoid(actor))return;
        SurvivalState s=SurvivalApi.state(actor);
        s.dailyQuotaTicks=Math.round(Config.RECREATION_HOURS.get()*1000);
        if(SurvivalApi.exempt(actor)){applyCapacities(actor,s,true);if(actor instanceof ServerPlayer player)SurvivalNetwork.send(player,s,false);return;}
        long now=SurvivalApi.calendar(level);
        CompoundTag meta=actor.getPersistentData();
        double dx=actor.getX()-meta.getDoubleOr("survivalX",actor.getX());
        double dz=actor.getZ()-meta.getDoubleOr("survivalZ",actor.getZ());
        double dy=actor.getY()-meta.getDoubleOr("survivalY",actor.getY());
        boolean moving=dx*dx+dz*dz>.005;
        meta.putDouble("survivalX",actor.getX());meta.putDouble("survivalZ",actor.getZ());meta.putDouble("survivalY",actor.getY());
        SurvivalState.Activity activity=actor.isInWater()?SurvivalState.Activity.SWIMMING:
            actor.onClimbable()&&(moving||Math.abs(dy)>.01)?SurvivalState.Activity.CLIMBING:actor.isSprinting()?SurvivalState.Activity.SPRINTING:
            moving?SurvivalState.Activity.WALKING:SurvivalState.Activity.IDLE;
        if(actor instanceof Mob mob&&actor.tickCount%100==0)s=autonomy(mob,s,level,now);
        if(actor instanceof Player player){int shown=meta.getIntOr("survivalFoodShown",player.getFoodData().getFoodLevel());int increase=player.getFoodData().getFoodLevel()-shown;if(increase>0)s.feed(increase*5);}
        String pastime=meta.getStringOr("survivalLeisureItem","");
        boolean recreation=meta.getLongOr("survivalLeisureUntil",0)>now&&
            (pastime.equals(BuiltInRegistries.ITEM.getKey(actor.getMainHandItem().getItem()).toString())||pastime.equals(BuiltInRegistries.ITEM.getKey(actor.getOffhandItem().getItem()).toString()));
        if(meta.getLongOr("survivalSocialUntil",0)>now){
            try{
                Entity other=level.getEntity(UUID.fromString(meta.getStringOr("survivalCompanion","")));
                if(other!=null&&other.isAlive()&&actor.distanceToSqr(other)<=16){
                    recreation=true;
                    if(moving)meta.putLong("survivalSocialUntil",now+200);
                }
            }catch(IllegalArgumentException ignored){}
        }
        long ticks=Math.max(1,Math.round(20*Config.NEEDS_RATE.get()));
        if(actor instanceof Player player)CraftingGuard.tick(player,s);
        var outcome=s.advance(ticks,activity,climate(actor,level,now,s),recreation,actor.isSleeping());
        onset(actor,s,ticks);wornOut(actor,s);
        if(s.hunger<=20)actor.setSprinting(false);
        SurvivalApi.save(actor,s);SurvivalApi.syncFood(actor,s);applyCapacities(actor,s,false);
        if(actor instanceof Mob mob){
            if(s.mental()==0&&!mob.isNoAi()){mob.getPersistentData().putBoolean("survivalDisabledAi",true);mob.setNoAi(true);}
            else if(s.mental()>0&&mob.getPersistentData().getBooleanOr("survivalDisabledAi",false)){mob.setNoAi(false);mob.getPersistentData().putBoolean("survivalDisabledAi",false);}
        }
        if(outcome.dehydrated())actor.hurtServer(level,actor.damageSources().genericKill(),Float.MAX_VALUE);
        else if(outcome.damage()>0){
            // A single consolidated physiological damage application avoids resetting another condition's timer.
            // A sleeper is not shaken awake once a second by his own wound or fever: any blow ends sleep, and one
            // sick player would keep a whole server from its night. What would kill him still does.
            if(actor.isSleeping()&&actor.getHealth()>outcome.damage())actor.setHealth(actor.getHealth()-(float)outcome.damage());
            else{actor.setInvulnerableTime(0);actor.hurtServer(level,actor.damageSources().starve(),(float)outcome.damage());}
        }else if(actor.tickCount%200==0&&s.hunger>60&&s.thirst>60&&!s.severeSymptoms()&&s.bleeding==0&&s.poisonTicks==0)actor.heal(1);
        if(s.blind())actor.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,60,0,false,false));
        if(s.infectious(false)||s.infectious(true))transmit(actor,s,level);
        SurvivalNetwork.tracking(actor,s);
    }
    public static SurvivalState.Climate climate(LivingEntity actor,ServerLevel level){
        return climate(actor,level,SurvivalApi.calendar(level));
    }
    public static SurvivalState.Climate climate(LivingEntity actor,ServerLevel level,long tick){return climate(actor,level,tick,SurvivalApi.state(actor));}
    /** With the body already in hand, so that reading the weather does not mean reading the actor a second time. */
    public static SurvivalState.Climate climate(LivingEntity actor,ServerLevel level,long tick,SurvivalState body){
        if(dev.qube.spawn_9b5fbb9.environment.WorldEnvironment.active(level))
            return exposed(actor,level,tick,dev.qube.spawn_9b5fbb9.environment.EnvironmentApi.exposure(level,actor.blockPosition()),body.insulation());
        return sheltered(actor,level,body);
    }
    /** The weather of one moment over shelter that was looked at once: a night slept through is forty such moments under one roof. */
    private static SurvivalState.Climate exposed(LivingEntity actor,ServerLevel level,long tick,dev.qube.spawn_9b5fbb9.environment.EnvironmentApi.Exposure exposure,double insulation){
        var weather=dev.qube.spawn_9b5fbb9.environment.EnvironmentApi.at(level,actor.blockPosition(),tick);
        return new SurvivalState.Climate(weather.temperature(),insulation,actor.isInWater()||!exposure.roof()&&!weather.snow()&&weather.rain()>.15,exposure.roof(),exposure.fire(),weather.wind(),exposure.enclosure());
    }
    private static SurvivalState.Climate sheltered(LivingEntity actor,ServerLevel level,SurvivalState s){
        float biome=level.getBiome(actor.blockPosition()).value().getBaseTemperature();
        double ambient=biome<=0?-15:biome<.5?5:biome>1.5?42:biome>1?32:18;
        int season=(int)(SurvivalApi.calendar(level)/24000/Config.SEASON_DAYS.get()%4);
        ambient+=switch(season){case 1->7;case 2->-3;case 3->-12;default->0;};
        ambient-=Math.max(0,actor.getY()-100)*.05;
        boolean sheltered=!level.canSeeSky(actor.blockPosition()),fire=false;
        for(BlockPos p:BlockPos.betweenClosed(actor.blockPosition().offset(-3,-1,-3),actor.blockPosition().offset(3,1,3))){
            var b=level.getBlockState(p);if(b.is(Blocks.FIRE)||b.is(Blocks.LAVA)||b.is(Blocks.CAMPFIRE)&&b.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)){fire=true;break;}
        }
        return new SurvivalState.Climate(ambient,s.insulation(),actor.isInWater()||level.isRainingAt(actor.blockPosition()),sheltered,fire);
    }
    public static void applyCapacities(LivingEntity entity,SurvivalState s,boolean exempt){
        // The load on the back is weighed once a second.
        var data=entity.getPersistentData();
        if(entity.tickCount%20==0||!data.contains("survivalBurden"))data.putDouble("survivalBurden",dev.qube.spawn_9b5fbb9.industry.Ledger.carried(entity));
        double burden=data.getDoubleOr("survivalBurden",0),pace=dev.qube.spawn_9b5fbb9.industry.Ledger.pace(burden);
        if(!exempt&&pace<.5&&entity.tickCount%100==0&&entity instanceof net.minecraft.server.level.ServerPlayer player)player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Overloaded: carrying "+Math.round(burden)+" kg. Load a cart."));
        // Out cold, nothing answers: the body lies where it fell.
        boolean out=!exempt&&s.mental()==0;
        modifier(entity,Attributes.MOVEMENT_SPEED,MOVE,exempt?1:out?0:s.movement()*pace);
        modifier(entity,Attributes.JUMP_STRENGTH,LEGS,out?0:1);
        modifier(entity,Attributes.ATTACK_SPEED,HANDS,exempt?1:s.manipulation());
        modifier(entity,Attributes.ATTACK_DAMAGE,ATTACK,exempt?1:s.manipulation());
        modifier(entity,Attributes.BLOCK_BREAK_SPEED,MIND,exempt?1:Math.min(s.manipulation(),Math.max(.05,s.mental())));
    }
    private static void modifier(LivingEntity e,Holder<Attribute> attribute,Identifier id,double capacity){
        var instance=e.getAttribute(attribute);if(instance==null)return;
        // To the nearest hundredth, and left alone when that has not moved: taking a modifier off and putting it
        // back marks the attribute changed, and every change is sent to everyone watching the actor.
        double amount=capacity<.999?Math.round((capacity-1)*100)/100d:0;var current=instance.getModifier(id);
        if(current==null?amount==0:current.amount()==amount)return;
        instance.removeModifier(id);
        if(amount!=0)instance.addTransientModifier(new AttributeModifier(id,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    /** Nobody has to hand a first case to anyone: illness turns up by chance, more often in the starved and parched. */
    public static void onset(LivingEntity actor,SurvivalState s,long ticks){
        double share=(double)ticks/SurvivalState.DAY,rundown=s.hunger<30||s.thirst<30?2:1;
        if(actor.getRandom().nextDouble()<1-Math.pow(1-Math.min(1,s.rules.number("respiratoryOnsetChancePerDay")*rundown),share))s.infect(false);
        if(actor.getRandom().nextDouble()<1-Math.pow(1-Math.min(1,s.rules.number("systemicOnsetChancePerDay")*rundown),share))s.infect(true);
    }
    private static void wornOut(LivingEntity actor,SurvivalState s){
        if(s.wornOut==0)return;
        if(actor instanceof ServerPlayer player)for(int i=0;i<4;i++)if((s.wornOut&1<<i)!=0)player.sendSystemMessage(Component.literal("Your "+SurvivalApi.GARMENT_SLOTS[i]+(i==0||i==2?" has":" have")+" worn through and fallen apart."));
        s.wornOut=0;
    }
    /** Coughs carry about two metres in the open and across a shared room under one roof. */
    public static int transmit(LivingEntity actor,SurvivalState s,ServerLevel level){
        double chance=1-Math.pow(1-s.rules.number("contactInfectionChancePerHour"),20d/1000);
        return transmit(actor,s,level,chance);
    }
    public static int transmit(LivingEntity actor,SurvivalState s,ServerLevel level,double chance){
        double near=s.rules.number("contactRange"),room=s.rules.number("sharedRoomRange");int cases=0;
        boolean indoors=dev.qube.spawn_9b5fbb9.environment.EnvironmentApi.roofed(level,actor.blockPosition());
        for(LivingEntity other:level.getEntitiesOfClass(LivingEntity.class,actor.getBoundingBox().inflate(Math.max(near,room)),e->e!=actor&&e.isAlive()&&SurvivalApi.isHumanoid(e)&&!SurvivalApi.exempt(e))){
            // The dice first: nearly every throw fails, and a failed throw has no need to know who could see whom.
            double distance=actor.distanceTo(other);if(distance>near&&!(indoors&&distance<=room)||actor.getRandom().nextDouble()>=chance)continue;
            if(distance>near&&!(dev.qube.spawn_9b5fbb9.environment.EnvironmentApi.roofed(level,other.blockPosition())&&actor.hasLineOfSight(other)))continue;
            var target=SurvivalApi.state(other);boolean caught=false;
            if(s.infectious(false))caught|=target.infect(false);
            if(s.infectious(true))caught|=target.infect(true);
            if(caught){SurvivalApi.save(other,target);cases++;}
        }
        return cases;
    }
    private static SurvivalState autonomy(Mob actor,SurvivalState state,ServerLevel level,long now){
        if(state.mental()==0){actor.getNavigation().stop();return state;}
        for(var hand:net.minecraft.world.InteractionHand.values()){
            var held=actor.getItemInHand(hand);String name=BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
            if(usefulTreatment(name,state)){SurvivalApi.act(actor,"treat",null,hand);state=SurvivalApi.state(actor);}
            int slot=garmentSlot(name);
            // A garment in rags is changed for a sounder one in hand.
            boolean ragged=slot>=0&&!state.clothing[slot].isEmpty()&&state.condition(slot)<.2&&dev.qube.spawn_9b5fbb9.content.ItemAging.data(held).getLongOr("garmentWear",0)<state.clothingWear[slot];
            if(slot>=0&&(state.clothing[slot].isEmpty()||ragged)&&!(name.startsWith("wool_")&&state.temperature>39)){
                SurvivalApi.act(actor,"clothe",null,hand);state=SurvivalApi.state(actor);
            }
        }
        final SurvivalState needs=state;
        var supplies=level.getEntitiesOfClass(ItemEntity.class,actor.getBoundingBox().inflate(8),drop->{
            String name=BuiltInRegistries.ITEM.getKey(drop.getItem().getItem()).getPath();int slot=garmentSlot(name);
            return usefulTreatment(name,needs)||slot>=0&&needs.clothing[slot].isEmpty()&&!(name.startsWith("wool_")&&needs.temperature>39);
        });
        supplies.sort(java.util.Comparator.comparingDouble(actor::distanceToSqr));
        if(!supplies.isEmpty()){
            var supply=supplies.getFirst();if(actor.distanceToSqr(supply)>4)actor.getNavigation().moveTo(supply,1);
            else{
                var hand=actor.getOffhandItem().isEmpty()?net.minecraft.world.InteractionHand.OFF_HAND:net.minecraft.world.InteractionHand.MAIN_HAND;
                if(!actor.getItemInHand(hand).isEmpty())SurvivalApi.give(actor,actor.getItemInHand(hand).copy());
                actor.setItemSlot(hand==net.minecraft.world.InteractionHand.OFF_HAND?EquipmentSlot.OFFHAND:EquipmentSlot.MAINHAND,supply.getItem().split(1));
                String name=BuiltInRegistries.ITEM.getKey(actor.getItemInHand(hand).getItem()).getPath();
                SurvivalApi.act(actor,garmentSlot(name)>=0?"clothe":"treat",null,hand);state=SurvivalApi.state(actor);
            }
        }
        if(state.hunger<60){
            if(actor.getMainHandItem().get(DataComponents.FOOD)!=null){SurvivalApi.act(actor,"eat",null);copyStateNeeds(actor,state);}
            else if(actor.getOffhandItem().get(DataComponents.FOOD)!=null){SurvivalApi.act(actor,"eat",null,net.minecraft.world.InteractionHand.OFF_HAND);copyStateNeeds(actor,state);}
            else for(ItemEntity drop:level.getEntitiesOfClass(ItemEntity.class,actor.getBoundingBox().inflate(8),e->e.getItem().get(DataComponents.FOOD)!=null)){
                actor.getNavigation().moveTo(drop,1);
                if(actor.distanceToSqr(drop)<4&&(actor.getMainHandItem().isEmpty()||actor.getOffhandItem().isEmpty())){
                    var hand=actor.getMainHandItem().isEmpty()?net.minecraft.world.InteractionHand.MAIN_HAND:net.minecraft.world.InteractionHand.OFF_HAND;
                    actor.setItemSlot(hand==net.minecraft.world.InteractionHand.MAIN_HAND?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND,drop.getItem().split(1));SurvivalApi.act(actor,"eat",null,hand);
                }break;
            }
        }
        if(state.thirst<65){
            ItemStack vessel=dev.qube.spawn_9b5fbb9.content.Pottery.waterUnits(actor.getMainHandItem())>0?actor.getMainHandItem():actor.getOffhandItem();
            if(dev.qube.spawn_9b5fbb9.content.Pottery.waterUnits(vessel)>0){
                boolean safe=dev.qube.spawn_9b5fbb9.content.Pottery.safeWater(vessel);
                if(dev.qube.spawn_9b5fbb9.content.Pottery.consumeWater(vessel))SurvivalApi.hydrate(actor,state,25,safe);
            }else for(BlockPos p:BlockPos.betweenClosed(actor.blockPosition().offset(-8,-2,-8),actor.blockPosition().offset(8,2,8))){
                if(level.getFluidState(p).is(FluidTags.WATER)&&level.getFluidState(p).isSource()){
                    if(actor.distanceToSqr(p.getX()+.5,p.getY()+.5,p.getZ()+.5)<9)SurvivalApi.hydrate(actor,state,10,false);
                    else actor.getNavigation().moveTo(p.getX()+.5,p.getY()+1,p.getZ()+.5,1);
                    break;
                }
            }
        }
        if(state.recreationTicks<state.quotaTicks&&state.hunger>30&&state.thirst>30){
            for(LivingEntity companion:level.getEntitiesOfClass(LivingEntity.class,actor.getBoundingBox().inflate(8),e->e!=actor&&e.isAlive()&&(SurvivalApi.isHumanoid(e)||e instanceof net.minecraft.world.entity.animal.Animal))){
                actor.getLookControl().setLookAt(companion,30,30);
                if(actor.distanceToSqr(companion)>9)actor.getNavigation().moveTo(companion,.7);
                else{actor.getPersistentData().putString("survivalCompanion",companion.getUUID().toString());actor.getPersistentData().putLong("survivalSocialUntil",now+200);}break;
            }
        }
        return state;
    }
    private static int garmentSlot(String name){if(!name.startsWith("wool_")&&!name.startsWith("fiber_"))return -1;return name.endsWith("_shirt")?0:name.endsWith("_pants")?1:name.endsWith("_jacket")?2:name.endsWith("_shoes")?3:-1;}
    private static boolean usefulTreatment(String name,SurvivalState state){return switch(name){case "bandage"->state.bleeding>0;case "antitoxin"->state.poisonTicks>0;case "antiseptic"->state.woundInfection;case "antibiotic"->state.mildSymptoms()||state.severeSymptoms();case "painkiller"->state.bleeding>0&&state.painkillerTicks==0;default->false;};}
    public static boolean brainTick(LivingEntity actor){
        if(!SurvivalApi.isHumanoid(actor)||SurvivalApi.exempt(actor))return true;
        if(dev.qube.spawn_9b5fbb9.warfare.Military.underOrders(actor))return false;
        double mental=actor.getPersistentData().getDoubleOr(SurvivalApi.MENTAL,1);if(mental>=.999)return true;
        double budget=actor.getPersistentData().getDoubleOr("survivalBrainBudget",0)+mental;
        boolean allowed=budget>=1-1e-9;if(allowed)budget=Math.max(0,budget-1);actor.getPersistentData().putDouble("survivalBrainBudget",budget);return allowed;
    }
    private static void copyStateNeeds(LivingEntity actor,SurvivalState state){var saved=SurvivalApi.state(actor);state.hunger=saved.hunger;state.thirst=saved.thirst;state.poisonTicks=saved.poisonTicks;state.severeTicks=saved.severeTicks;}
    @SubscribeEvent public static void damage(LivingDamageEvent.Post e){
        if(!(e.getEntity().level() instanceof ServerLevel)||e.getHealthDamage()<=0)return;
        if(e.getSource().getEntity() instanceof LivingEntity attacker&&SurvivalApi.isHumanoid(attacker)){
            var work=SurvivalApi.state(attacker);work.hunger=Math.max(0,work.hunger-.25);SurvivalApi.save(attacker,work);
        }
        LivingEntity target=e.getEntity();if(!SurvivalApi.isHumanoid(target)||SurvivalApi.exempt(target))return;
        String source=e.getSource().getMsgId();if(source.equals("starve")||source.equals("genericKill"))return;
        var s=SurvivalApi.state(target);int region=target.getRandom().nextInt(100);
        var part=region<12?SurvivalState.Part.BRAIN:region<30?SurvivalState.Part.LEFT_ARM:region<48?SurvivalState.Part.RIGHT_ARM:region<70?SurvivalState.Part.LEFT_LEG:SurvivalState.Part.RIGHT_LEG;
        var projectile=e.getSource().getDirectEntity();
        if(projectile instanceof net.minecraft.world.entity.projectile.Projectile&&projectile.getY()>target.getY()+target.getBbHeight()*.75)part=SurvivalState.Part.BRAIN;
        if(source.equals("fall"))part=target.getRandom().nextBoolean()?SurvivalState.Part.LEFT_LEG:SurvivalState.Part.RIGHT_LEG;
        else if(e.getSource().getSourcePosition()!=null){
            var offset=e.getSource().getSourcePosition().subtract(target.position());var right=target.getDirection().getClockWise();
            double side=offset.x*right.getStepX()+offset.z*right.getStepZ();
            if(Math.abs(side)>.25){
                if(part==SurvivalState.Part.LEFT_ARM||part==SurvivalState.Part.RIGHT_ARM)part=side>0?SurvivalState.Part.RIGHT_ARM:SurvivalState.Part.LEFT_ARM;
                else if(part==SurvivalState.Part.LEFT_LEG||part==SurvivalState.Part.RIGHT_LEG)part=side>0?SurvivalState.Part.RIGHT_LEG:SurvivalState.Part.LEFT_LEG;
            }
        }
        boolean edged=e.getSource().getEntity() instanceof LivingEntity a&&(
            BuiltInRegistries.ITEM.getKey(a.getMainHandItem().getItem()).getPath().matches(".*(sword|spear|axe).*"))
            // Balls and shot tear and carry cloth into the wound: they bleed and fester like a cut.
            ||source.equals("gunshot")||source.equals("artillery");
        // Teeth and claws open wounds as a blade does, and a bite is far dirtier.
        boolean bitten=e.getSource().getDirectEntity() instanceof LivingEntity beast&&dev.qube.spawn_9b5fbb9.wildlife.Predators.predator(beast);
        edged|=bitten;
        boolean burnt=e.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE);
        long rip=Math.round(e.getHealthDamage()*s.rules.number("garmentTearTicksPerDamage"));
        if(burnt)for(int i=0;i<4;i++)s.tear(i,rip);
        else if(edged&&part!=SurvivalState.Part.BRAIN){
            // The outer layer over the struck limb takes the cut.
            if(part.ordinal()<2)s.tear(s.clothing[2].isEmpty()?0:2,rip);else s.tear(1,rip);
        }
        s.injure(part,e.getHealthDamage()*6,edged);
        if(part.ordinal()<4&&target.getRandom().nextFloat()<.35){int start=part.ordinal()<2?8+part.ordinal()*5:18+(part.ordinal()-2)*5;s.injure(SurvivalState.Part.values()[start+target.getRandom().nextInt(5)],e.getHealthDamage()*8,edged);}
        if(part==SurvivalState.Part.BRAIN&&e.getHealthDamage()>=4&&target.getRandom().nextFloat()<.35)s.injure(target.getRandom().nextBoolean()?SurvivalState.Part.LEFT_EYE:SurvivalState.Part.RIGHT_EYE,e.getHealthDamage()*10,false);
        if(part==SurvivalState.Part.BRAIN&&e.getHealthDamage()>=4&&target.getRandom().nextFloat()<.2)s.injure(SurvivalState.Part.HEARING,e.getHealthDamage()*6,false);
        if(edged&&target.getRandom().nextFloat()<s.rules.number("woundInfectionChance")*(bitten?3:1))s.woundInfection=true;
        wornOut(target,s);SurvivalApi.save(target,s);
    }
    @SubscribeEvent public static void eaten(LivingEntityUseItemEvent.Finish e){
        var actor=e.getEntity();if(!(actor.level() instanceof ServerLevel)||!SurvivalApi.isHumanoid(actor))return;
        dev.qube.spawn_9b5fbb9.content.ItemAging.update(e.getItem(),(ServerLevel)actor.level(),1);
        var state=SurvivalApi.state(actor);var food=e.getItem().get(DataComponents.FOOD);
        if(food!=null){state.feed(food.nutrition()*5);SurvivalApi.unsafeFood(actor,state,e.getItem());}
        if(e.getItem().is(Items.POTION)&&SurvivalApi.isWater(e.getItem()))SurvivalApi.hydrate(actor,state,25,false);
        SurvivalApi.save(actor,state);SurvivalApi.syncFood(actor,state);
    }
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent e){cost(e.getEntity(),.1);}
    @SubscribeEvent public static void attack(AttackEntityEvent e){if(e.getEntity().level() instanceof ServerLevel&&!SurvivalApi.exempt(e.getEntity())){var s=SurvivalApi.state(e.getEntity());if(s.mental()==0||s.manipulation()<.1)e.setCanceled(true);}}
    @SubscribeEvent public static void mining(PlayerEvent.BreakSpeed e){if(e.getEntity().level() instanceof ServerLevel&&!SurvivalApi.exempt(e.getEntity())){var s=SurvivalApi.state(e.getEntity());if(s.mental()==0||s.manipulation()<.1)e.setNewSpeed(0);}}
    @SubscribeEvent public static void broken(net.neoforged.neoforge.event.level.BlockDropsEvent e){if(e.getBreaker() instanceof LivingEntity living)cost(living,.08);}
    private static void cost(LivingEntity e,double cost){if(e.level() instanceof ServerLevel&&SurvivalApi.isHumanoid(e)&&!SurvivalApi.exempt(e)){var s=SurvivalApi.state(e);s.hunger=Math.max(0,s.hunger-cost);SurvivalApi.save(e,s);}}
    @SubscribeEvent public static void item(PlayerInteractEvent.RightClickItem e){if(e.getEntity().isSpectator())return;
        if(!(e.getLevel() instanceof ServerLevel))return;
        String id=BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()).getPath();String action=
            id.matches("(wool|fiber)_(shirt|pants|jacket|shoes)")?"clothe":
            e.getEntity().isShiftKeyDown()&&(id.equals("yarn")||id.equals("cordage"))&&BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()).getNamespace().equals("spawn_9b5fbb9")?"mend":
            java.util.Set.of("bandage","painkiller","antitoxin","antiseptic","antibiotic").contains(id)?"treat":
            id.equals("wooden_toy")?"play":e.getItemStack().is(Items.BOOK)?"read":null;
        if(action!=null){var result=SurvivalApi.act(e.getEntity(),action,null,e.getHand());e.getEntity().sendOverlayMessage(Component.literal(result.message()));e.setCancellationResult(InteractionResult.SUCCESS);e.setCanceled(true);}
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract e){if(e.getEntity().isSpectator())return;
        if(!(e.getLevel() instanceof ServerLevel)||!(e.getTarget() instanceof LivingEntity other))return;
        String id=BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()).getPath();
        if(e.getEntity().isShiftKeyDown()&&SurvivalApi.isHumanoid(other)&&e.getItemStack().get(DataComponents.FOOD)!=null){
            var result=SurvivalApi.act(e.getEntity(),"feed",other,e.getHand());e.getEntity().sendOverlayMessage(Component.literal(result.message()));e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);
        }else if(e.getEntity().isShiftKeyDown()&&id.matches("(wool|fiber)_(shirt|pants|jacket|shoes)")&&SurvivalApi.isHumanoid(other)){
            var result=SurvivalApi.act(e.getEntity(),"clothe",other,e.getHand());e.getEntity().sendOverlayMessage(Component.literal(result.message()));e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);
        }else if(java.util.Set.of("bandage","painkiller","antitoxin","antiseptic","antibiotic").contains(id)){
            var result=SurvivalApi.act(e.getEntity(),"treat",other,e.getHand());e.getEntity().sendOverlayMessage(Component.literal(result.message()));e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);
        }else if(e.getEntity().isShiftKeyDown()){
            var result=SurvivalApi.act(e.getEntity(),"social",other);e.getEntity().sendSystemMessage(Component.literal(SurvivalApi.isHumanoid(other)?SurvivalApi.inspect(other):result.message()));
            e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
    /** Marks a player whose life has ended. It follows them through every later respawn and login. */
    public static final String DEAD="survivalDead";
    public static boolean dead(Player player){return player.getPersistentData().getBooleanOr(DEAD,false);}
    /** Dead, or lying dead and not yet returned: a client that never asks to respawn is not thereby left a voice and a hand. */
    private static boolean departed(Player player){return dead(player)||player.isDeadOrDying();}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){
        boolean ended=e.isWasDeath()&&(dead(e.getOriginal())||!SurvivalApi.exempt(e.getOriginal()));
        // Death is the end of that life: nothing learned or worn is carried on, and the one who comes back only watches.
        if(ended)e.getEntity().getPersistentData().putBoolean(DEAD,true);
        if(e.isWasDeath()&&!ended){
            var old=SurvivalApi.state(e.getOriginal());var fresh=SurvivalApi.state(e.getEntity());
            fresh.skills.copyFrom(old.skills);SurvivalApi.save(e.getEntity(),fresh);
        }
        if(!e.isWasDeath()&&e.getOriginal().getPersistentData().contains(SurvivalApi.KEY))e.getEntity().getPersistentData().putByteArray(SurvivalApi.KEY,e.getOriginal().getPersistentData().getByteArray(SurvivalApi.KEY).orElse(new byte[0]));
        if(!e.isWasDeath()&&e.getOriginal().getPersistentData().contains("survivalWorldJob"))e.getEntity().getPersistentData().put("survivalWorldJob",e.getOriginal().getPersistentData().getCompoundOrEmpty("survivalWorldJob").copy());
        if(e.isWasDeath()&&!ended&&e.getEntity().level() instanceof ServerLevel level&&level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY)){
            var old=SurvivalApi.state(e.getOriginal());var fresh=SurvivalApi.state(e.getEntity());
            for(int i=0;i<4;i++){fresh.clothing[i]=old.clothing[i];fresh.clothingWear[i]=old.clothingWear[i];fresh.clothingColors[i]=old.clothingColors[i];}SurvivalApi.save(e.getEntity(),fresh);
        }
    }
    @SubscribeEvent public static void respawned(PlayerEvent.PlayerRespawnEvent e){layToRest(e.getEntity());}
    @SubscribeEvent public static void loggedIn(PlayerEvent.PlayerLoggedInEvent e){layToRest(e.getEntity());}
    /** The dead take no further part: they are held as spectators, whatever game mode the server would give them. */
    public static void layToRest(Player player){
        if(!(player instanceof ServerPlayer gone)||!dead(gone)||gone.isSpectator())return;
        gone.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        gone.sendSystemMessage(Component.literal("You died. That life is over: you may watch, but you take no further part."));
    }
    // The dead touch nothing, order nothing and say nothing. Vanilla lets a spectator's clicks reach block handlers
    // and its commands and chat reach everyone, so each of those doors is shut here for a player who has died.
    /** Out cold: nothing in the head is answering. Read from the mark the body's own tick leaves, so that asking is a lookup. */
    public static boolean insensible(Player player){return !SurvivalApi.exempt(player)&&player.isAlive()&&player.getPersistentData().getDoubleOr(SurvivalApi.MENTAL,1)<=0;}
    /** What the dead may not do, the unconscious cannot. */
    private static boolean absent(Player player){return departed(player)||insensible(player);}
    /** An unconscious player lies where they fell. The body may fall, or be carried in a vehicle; it does not walk, jump or swim along,
     * whatever the client says of its own legs. A player's movement is the client's to report, so it is undone here, each tick. */
    @SubscribeEvent public static void laidOut(PlayerTickEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer player))return;var data=player.getPersistentData();
        if(!insensible(player)){if(data.contains("survivalDownX")){data.remove("survivalDownX");data.remove("survivalDownZ");player.setForcedPose(null);}return;}
        boolean adrift=player.isPassenger();
        double x=data.getDoubleOr("survivalDownX",player.getX()),z=data.getDoubleOr("survivalDownZ",player.getZ()),dx=player.getX()-x,dz=player.getZ()-z;
        if(adrift||!data.contains("survivalDownX")){data.putDouble("survivalDownX",player.getX());data.putDouble("survivalDownZ",player.getZ());}
        else if(dx*dx+dz*dz>.0004){if(player.connection!=null)player.connection.teleport(x,player.getY(),z,player.getYRot(),player.getXRot());else player.setPos(x,player.getY(),z);}
        if(player.getForcedPose()!=Pose.SWIMMING)player.setForcedPose(Pose.SWIMMING);
        player.setSprinting(false);if(player.isUsingItem())player.stopUsingItem();
        if(player.connection!=null&&player.tickCount%20==0){
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,60,0,false,false));
            if(player.containerMenu!=player.inventoryMenu)player.closeContainer();
            if(player.tickCount%100==0)player.sendOverlayMessage(Component.literal("You are unconscious."));
        }
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void deadTouchBlock(PlayerInteractEvent.RightClickBlock e){if(absent(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void deadUseItem(PlayerInteractEvent.RightClickItem e){if(absent(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void deadStrikeBlock(PlayerInteractEvent.LeftClickBlock e){if(absent(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void deadTouchEntity(PlayerInteractEvent.EntityInteract e){if(absent(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void deadSpeak(net.neoforged.neoforge.event.ServerChatEvent e){if(absent(e.getPlayer()))e.setCanceled(true);}
    @SubscribeEvent public static void sighted(PlayerEvent.StartTracking e){if(e.getTarget() instanceof LivingEntity actor&&e.getEntity() instanceof ServerPlayer viewer)SurvivalNetwork.appear(actor,viewer);}
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void deadCommand(net.neoforged.neoforge.event.CommandEvent e){
        var source=e.getParseResults().getContext().getSource();
        if(source.getEntity() instanceof Player player&&departed(player)&&!net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS).test(source)){
            e.setCanceled(true);source.sendFailure(Component.literal("The dead take no part in the world."));return;
        }
        // The unconscious give no orders and learn nothing of the world. Their own body is still theirs to be told of.
        if(source.getEntity() instanceof Player player&&insensible(player)&&!net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS).test(source)){
            String line=e.getParseResults().getReader().getString().trim();if(line.startsWith("/"))line=line.substring(1);
            if(line.matches("survival (json|health|skills|calendar)"))return;
            e.setCanceled(true);source.sendFailure(Component.literal("You are unconscious."));
        }
    }
    /** An operator's way to put a player back into the world as a new person: fresh body, nothing learned. */
    public static boolean revive(ServerPlayer player){
        if(!newLife(player))return false;
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);SurvivalApi.syncFood(player,SurvivalApi.state(player));
        return true;
    }
    /** Clears the mark of death and everything the old life held, so the next state read is a new person's. */
    public static boolean newLife(Player player){
        if(!dead(player))return false;
        var data=player.getPersistentData();data.remove(DEAD);data.remove(SurvivalApi.KEY);return true;
    }
    @SubscribeEvent public static void death(LivingDeathEvent e){
        var actor=e.getEntity();if(!(actor.level() instanceof ServerLevel level)||!SurvivalApi.isHumanoid(actor))return;
        if(actor instanceof Player&&level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY))return;
        var state=SurvivalApi.state(actor);for(int i=0;i<4;i++){SurvivalApi.give(actor,SurvivalApi.garment(state,i));state.clothing[i]="";state.clothingWear[i]=0;}SurvivalApi.save(actor,state);
    }
    @SubscribeEvent public static void sleep(SleepFinishedTimeEvent e){
        if(e.getLevel() instanceof ServerLevel level&&level==level.getServer().overworld()){
            var clock=level.dimensionType().defaultClock();if(clock.isPresent()){
                sleepBefore=level.getServer().clockManager().getInstance(clock.get()).totalTicks();sleepStates.clear();
                for(ServerLevel world:level.getServer().getAllLevels())for(Entity entity:world.getAllEntities())if(entity instanceof LivingEntity actor&&SurvivalApi.isHumanoid(actor))sleepStates.put(actor.getUUID(),actor.isSleeping());
            }
        }
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e){
        if(sleepBefore<0)return;var level=e.getServer().overworld();var clock=level.dimensionType().defaultClock();
        long skipped=clock.map(c->e.getServer().clockManager().getInstance(c).totalTicks()-sleepBefore).orElse(0L);sleepBefore=-1;
        if(skipped<=0)return;skipped=Math.min(skipped,SurvivalState.DAY);
        CompoundTag calendar=e.getServer().getCommandStorage().get(SurvivalApi.CALENDAR).copy();
        calendar.putLong("sleepOffset",calendar.getLongOr("sleepOffset",0)+skipped);e.getServer().getCommandStorage().set(SurvivalApi.CALENDAR,calendar);
        // Listed first: one who dies of the night drops what he wore, and a drop is a new entity in the very
        // collection that would otherwise be under the loop's feet.
        var sleepers=new java.util.ArrayList<LivingEntity>();
        for(ServerLevel world:e.getServer().getAllLevels())for(Entity entity:world.getAllEntities())if(entity instanceof LivingEntity actor&&actor.isAlive()&&SurvivalApi.isHumanoid(actor)&&!SurvivalApi.exempt(actor))sleepers.add(actor);
        for(LivingEntity actor:sleepers){
            if(!actor.isAlive()||!(actor.level() instanceof ServerLevel world))continue;
            var state=SurvivalApi.state(actor);long elapsed=Math.round(skipped*Config.NEEDS_RATE.get());double damage=0;boolean dry=false;
            if(dev.qube.spawn_9b5fbb9.environment.WorldEnvironment.active(world)){
                long end=SurvivalApi.calendar(world),start=end-skipped;
                var exposure=dev.qube.spawn_9b5fbb9.environment.EnvironmentApi.exposure(world,actor.blockPosition());double insulation=state.insulation();
                for(long cursor=start;cursor<end;){long bucket=Math.floorDiv(cursor,600),next=Math.min(end,(bucket+1)*600);long batch=Math.max(0,Math.round((next-cursor)*Config.NEEDS_RATE.get()));while(batch>0){long part=Math.min(batch,7*SurvivalState.DAY);var partial=state.advance(part,SurvivalState.Activity.IDLE,exposed(actor,world,bucket*600+300,exposure,insulation),false,sleepStates.getOrDefault(actor.getUUID(),false));damage+=partial.damage();dry|=partial.dehydrated();batch-=part;}cursor=next;}
            }else{var climate=sheltered(actor,world,state);while(elapsed>0){long batch=Math.min(elapsed,7*SurvivalState.DAY);var partial=state.advance(batch,SurvivalState.Activity.IDLE,climate,false,sleepStates.getOrDefault(actor.getUUID(),false));damage+=partial.damage();dry|=partial.dehydrated();elapsed-=batch;}}
            onset(actor,state,Math.round(skipped*Config.NEEDS_RATE.get()));wornOut(actor,state);
            SurvivalApi.save(actor,state);SurvivalApi.syncFood(actor,state);
            if(dry)actor.hurtServer(world,actor.damageSources().genericKill(),Float.MAX_VALUE);
            else if(damage>0)actor.hurtServer(world,actor.damageSources().starve(),(float)damage);
        }
        sleepStates.clear();
    }
}
