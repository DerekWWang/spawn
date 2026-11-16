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
    private static final Identifier MOVE=id("movement"),HANDS=id("hands"),ATTACK=id("attack"),MIND=id("mind");
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
        boolean recreation=meta.getLongOr("survivalLeisureUntil",0)>now&&
            meta.getStringOr("survivalLeisureItem","").equals(BuiltInRegistries.ITEM.getKey(actor.getMainHandItem().getItem()).toString());
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
        var outcome=s.advance(ticks,activity,climate(actor,level),recreation,actor.isSleeping());
        if(s.hunger<=20)actor.setSprinting(false);
        SurvivalApi.save(actor,s);SurvivalApi.syncFood(actor,s);applyCapacities(actor,s,false);
        if(actor instanceof Mob mob){
            if(s.mental()==0&&!mob.isNoAi()){mob.getPersistentData().putBoolean("survivalDisabledAi",true);mob.setNoAi(true);}
            else if(s.mental()>0&&mob.getPersistentData().getBooleanOr("survivalDisabledAi",false)){mob.setNoAi(false);mob.getPersistentData().putBoolean("survivalDisabledAi",false);}
        }
        if(outcome.dehydrated())actor.hurtServer(level,actor.damageSources().genericKill(),Float.MAX_VALUE);
        else if(outcome.damage()>0){
            // A single consolidated physiological damage application avoids resetting another condition's timer.
            actor.setInvulnerableTime(0);actor.hurtServer(level,actor.damageSources().starve(),(float)outcome.damage());
        }else if(actor.tickCount%200==0&&s.hunger>60&&s.thirst>60&&!s.severeSymptoms()&&s.bleeding==0&&s.poisonTicks==0)actor.heal(1);
        if(s.blind())actor.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,60,0,false,false));
        if(s.mildSymptoms()||s.severeSymptoms())transmit(actor,s,level);
        SurvivalNetwork.tracking(actor,s);
    }
    public static SurvivalState.Climate climate(LivingEntity actor,ServerLevel level){
        return climate(actor,level,SurvivalApi.calendar(level));
    }
    public static SurvivalState.Climate climate(LivingEntity actor,ServerLevel level,long tick){
        if(dev.qube.spawn_9b5fbb9.environment.WorldEnvironment.active(level)){
            var weather=dev.qube.spawn_9b5fbb9.environment.EnvironmentApi.at(level,actor.blockPosition(),tick);
            var exposure=dev.qube.spawn_9b5fbb9.environment.EnvironmentApi.exposure(level,actor.blockPosition());var body=SurvivalApi.state(actor);double insulation=0;
            for(String garment:body.clothing)insulation+=garment.contains("wool_jacket")?body.rules.number("woolJacketInsulation"):garment.contains("wool_")?body.rules.number("woolPieceInsulation"):garment.contains("fiber_")?body.rules.number("fiberPieceInsulation"):0;
            return new SurvivalState.Climate(weather.temperature(),insulation,actor.isInWater()||!exposure.roof()&&!weather.snow()&&weather.rain()>.15,exposure.roof(),exposure.fire(),weather.wind(),exposure.enclosure());
        }
        float biome=level.getBiome(actor.blockPosition()).value().getBaseTemperature();
        double ambient=biome<=0?-15:biome<.5?5:biome>1.5?42:biome>1?32:18;
        int season=(int)(SurvivalApi.calendar(level)/24000/Config.SEASON_DAYS.get()%4);
        ambient+=switch(season){case 1->7;case 2->-3;case 3->-12;default->0;};
        ambient-=Math.max(0,actor.getY()-100)*.05;
        boolean sheltered=!level.canSeeSky(actor.blockPosition()),fire=false;
        for(BlockPos p:BlockPos.betweenClosed(actor.blockPosition().offset(-3,-1,-3),actor.blockPosition().offset(3,1,3))){
            var b=level.getBlockState(p);if(b.is(Blocks.FIRE)||b.is(Blocks.LAVA)||b.is(Blocks.CAMPFIRE)&&b.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)){fire=true;break;}
        }
        SurvivalState s=SurvivalApi.state(actor);double insulation=0;
        for(String garment:s.clothing)insulation+=garment.contains("wool_jacket")?s.rules.number("woolJacketInsulation"):garment.contains("wool_")?s.rules.number("woolPieceInsulation"):garment.contains("fiber_")?s.rules.number("fiberPieceInsulation"):0;
        return new SurvivalState.Climate(ambient,insulation,actor.isInWater()||level.isRainingAt(actor.blockPosition()),sheltered,fire);
    }
    public static void applyCapacities(LivingEntity entity,SurvivalState s,boolean exempt){
        modifier(entity,Attributes.MOVEMENT_SPEED,MOVE,exempt?1:s.movement());
        modifier(entity,Attributes.ATTACK_SPEED,HANDS,exempt?1:s.manipulation());
        modifier(entity,Attributes.ATTACK_DAMAGE,ATTACK,exempt?1:s.manipulation());
        modifier(entity,Attributes.BLOCK_BREAK_SPEED,MIND,exempt?1:Math.min(s.manipulation(),Math.max(.05,s.mental())));
    }
    private static void modifier(LivingEntity e,Holder<Attribute> attribute,Identifier id,double capacity){
        var instance=e.getAttribute(attribute);if(instance==null)return;
        instance.removeModifier(id);
        if(capacity<.999)instance.addTransientModifier(new AttributeModifier(id,capacity-1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private static void transmit(LivingEntity actor,SurvivalState s,ServerLevel level){
        double chance=1-Math.pow(1-s.rules.number("contactInfectionChancePerHour"),20d/1000);
        for(LivingEntity other:level.getEntitiesOfClass(LivingEntity.class,actor.getBoundingBox().inflate(6),e->e!=actor&&SurvivalApi.isHumanoid(e)&&!SurvivalApi.exempt(e))){
            if(actor.getRandom().nextDouble()<chance){var target=SurvivalApi.state(other);target.infect(s.severeSymptoms());SurvivalApi.save(other,target);}
        }
    }
    private static SurvivalState autonomy(Mob actor,SurvivalState state,ServerLevel level,long now){
        if(state.mental()==0){actor.getNavigation().stop();return state;}
        for(var hand:net.minecraft.world.InteractionHand.values()){
            var held=actor.getItemInHand(hand);String name=BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
            if(usefulTreatment(name,state)){SurvivalApi.act(actor,"treat",null,hand);state=SurvivalApi.state(actor);}
            int slot=garmentSlot(name);
            if(slot>=0&&state.clothing[slot].isEmpty()&&!(name.startsWith("wool_")&&state.temperature>39)){
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
    private static boolean usefulTreatment(String name,SurvivalState state){return switch(name){case "bandage"->state.bleeding>0;case "antitoxin"->state.poisonTicks>0;case "antiseptic"->state.woundInfection;case "antibiotic"->state.mildTicks>0||state.severeTicks>0;case "painkiller"->state.bleeding>0&&state.painkillerTicks==0;default->false;};}
    public static boolean brainTick(LivingEntity actor){
        if(!SurvivalApi.isHumanoid(actor)||SurvivalApi.exempt(actor))return true;
        if(dev.qube.spawn_9b5fbb9.warfare.Military.underOrders(actor))return false;
        double mental=SurvivalApi.state(actor).mental();if(mental>=.999)return true;
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
            BuiltInRegistries.ITEM.getKey(a.getMainHandItem().getItem()).getPath().matches(".*(sword|spear|axe).*"));
        s.injure(part,e.getHealthDamage()*6,edged);
        if(part.ordinal()<4&&target.getRandom().nextFloat()<.35){int start=part.ordinal()<2?8+part.ordinal()*5:18+(part.ordinal()-2)*5;s.injure(SurvivalState.Part.values()[start+target.getRandom().nextInt(5)],e.getHealthDamage()*8,edged);}
        if(part==SurvivalState.Part.BRAIN&&e.getHealthDamage()>=4&&target.getRandom().nextFloat()<.35)s.injure(target.getRandom().nextBoolean()?SurvivalState.Part.LEFT_EYE:SurvivalState.Part.RIGHT_EYE,e.getHealthDamage()*10,false);
        if(part==SurvivalState.Part.BRAIN&&e.getHealthDamage()>=4&&target.getRandom().nextFloat()<.2)s.injure(SurvivalState.Part.HEARING,e.getHealthDamage()*6,false);
        if(edged&&target.getRandom().nextFloat()<s.rules.number("woundInfectionChance"))s.woundInfection=true;
        SurvivalApi.save(target,s);
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
    @SubscribeEvent public static void item(PlayerInteractEvent.RightClickItem e){
        if(!(e.getLevel() instanceof ServerLevel))return;
        String id=BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()).getPath();String action=
            id.matches("(wool|fiber)_(shirt|pants|jacket|shoes)")?"clothe":
            java.util.Set.of("bandage","painkiller","antitoxin","antiseptic","antibiotic").contains(id)?"treat":
            id.equals("wooden_toy")?"play":e.getItemStack().is(Items.BOOK)?"read":null;
        if(action!=null){var result=SurvivalApi.act(e.getEntity(),action,null,e.getHand());e.getEntity().sendOverlayMessage(Component.literal(result.message()));e.setCancellationResult(InteractionResult.SUCCESS);e.setCanceled(true);}
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract e){
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
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){
        if(e.isWasDeath()){
            var old=SurvivalApi.state(e.getOriginal());var fresh=SurvivalApi.state(e.getEntity());
            fresh.skills.copyFrom(old.skills);SurvivalApi.save(e.getEntity(),fresh);
        }
        if(!e.isWasDeath()&&e.getOriginal().getPersistentData().contains(SurvivalApi.KEY))e.getEntity().getPersistentData().putByteArray(SurvivalApi.KEY,e.getOriginal().getPersistentData().getByteArray(SurvivalApi.KEY).orElse(new byte[0]));
        if(!e.isWasDeath()&&e.getOriginal().getPersistentData().contains("survivalWorldJob"))e.getEntity().getPersistentData().put("survivalWorldJob",e.getOriginal().getPersistentData().getCompoundOrEmpty("survivalWorldJob").copy());
        if(e.isWasDeath()&&e.getEntity().level() instanceof ServerLevel level&&level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY)){
            var old=SurvivalApi.state(e.getOriginal());var fresh=SurvivalApi.state(e.getEntity());
            for(int i=0;i<4;i++){fresh.clothing[i]=old.clothing[i];fresh.clothingWear[i]=old.clothingWear[i];fresh.clothingColors[i]=old.clothingColors[i];}SurvivalApi.save(e.getEntity(),fresh);
        }
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
        for(ServerLevel world:e.getServer().getAllLevels())for(Entity entity:world.getAllEntities())if(entity instanceof LivingEntity actor&&SurvivalApi.isHumanoid(actor)&&!SurvivalApi.exempt(actor)){
            var state=SurvivalApi.state(actor);long elapsed=Math.round(skipped*Config.NEEDS_RATE.get());double damage=0;boolean dry=false;
            if(dev.qube.spawn_9b5fbb9.environment.WorldEnvironment.active(world)){
                long end=SurvivalApi.calendar(world),start=end-skipped;
                for(long cursor=start;cursor<end;){long bucket=Math.floorDiv(cursor,600),next=Math.min(end,(bucket+1)*600);long batch=Math.max(0,Math.round((next-cursor)*Config.NEEDS_RATE.get()));while(batch>0){long part=Math.min(batch,7*SurvivalState.DAY);var partial=state.advance(part,SurvivalState.Activity.IDLE,climate(actor,world,bucket*600+300),false,sleepStates.getOrDefault(actor.getUUID(),false));damage+=partial.damage();dry|=partial.dehydrated();batch-=part;}cursor=next;}
            }else while(elapsed>0){long batch=Math.min(elapsed,7*SurvivalState.DAY);var partial=state.advance(batch,SurvivalState.Activity.IDLE,climate(actor,world),false,sleepStates.getOrDefault(actor.getUUID(),false));damage+=partial.damage();dry|=partial.dehydrated();elapsed-=batch;}
            SurvivalApi.save(actor,state);SurvivalApi.syncFood(actor,state);
            if(dry)actor.hurtServer(world,actor.damageSources().genericKill(),Float.MAX_VALUE);
            else if(damage>0)actor.hurtServer(world,actor.damageSources().starve(),(float)damage);
        }
        sleepStates.clear();
    }
}
