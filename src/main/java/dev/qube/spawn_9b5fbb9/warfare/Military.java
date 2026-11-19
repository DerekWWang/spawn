package dev.qube.spawn_9b5fbb9.warfare;

import java.util.*;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.qube.spawn_9b5fbb9.content.ItemAging;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.industry.Industry;
import dev.qube.spawn_9b5fbb9.industry.Ledger;
import dev.qube.spawn_9b5fbb9.industry.WorksEntity;
import dev.qube.spawn_9b5fbb9.survival.*;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi.ActionResult;
import dev.qube.spawn_9b5fbb9.transport.VehicleEntity;
import dev.qube.spawn_9b5fbb9.transport.Vehicles;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import static net.minecraft.commands.Commands.*;

/** Military service. A humanoid enlists for rations or is pressed into the ranks, fights with whatever
 * it has been handed, and obeys orders only while its morale holds. Hunger, thirst, wounds, casualties
 * and bombardment wear morale down; men whose nerve breaks run, and unwilling conscripts desert. */
public final class Military {
    private Military(){}
    public static final String KEY="survivalService";
    public static final Set<String> ORDERS=Set.of("hold","follow","retreat","move","attack","charge","fire","guard","mount","dismount","crew","drive","work");
    public enum Weapon {MELEE,BOW,CROSSBOW,MUSKET}
    public static void init(){
        NeoForge.EVENT_BUS.addListener(Military::tick);NeoForge.EVENT_BUS.addListener(Military::damaged);NeoForge.EVENT_BUS.addListener(Military::died);
        NeoForge.EVENT_BUS.addListener(Military::incoming);NeoForge.EVENT_BUS.addListener(EventPriority.HIGH,Military::interact);NeoForge.EVENT_BUS.addListener(Military::commands);
    }

    // ---- Service record -------------------------------------------------------------------------------
    public static boolean serving(Entity actor){return actor instanceof LivingEntity&&actor.getPersistentData().contains(KEY);}
    static CompoundTag record(LivingEntity actor){return actor.getPersistentData().getCompoundOrEmpty(KEY);}
    static void save(LivingEntity actor,CompoundTag service){actor.getPersistentData().put(KEY,service);}
    public static String commander(Entity actor){return serving(actor)?record((LivingEntity)actor).getStringOr("commander",""):"";}
    public static double morale(LivingEntity actor){return record(actor).getDoubleOr("morale",0);}
    public static String order(LivingEntity actor){return record(actor).getStringOr("order","");}
    /** Soldiers under orders are driven by the order executor, not by village routine. */
    public static boolean underOrders(LivingEntity actor){return actor instanceof Mob&&serving(actor);}
    private static LivingEntity entity(ServerLevel level,String id){
        try{return id.isEmpty()?null:level.getEntity(UUID.fromString(id)) instanceof LivingEntity living?living:null;}catch(IllegalArgumentException malformed){return null;}
    }
    private static ActionResult ok(String text){return new ActionResult(true,text);}
    private static ActionResult fail(String text){return new ActionResult(false,text);}
    private static boolean armed(LivingEntity actor){return weaponDamage(actor.getMainHandItem())>2||kind(actor.getMainHandItem())!=Weapon.MELEE;}
    private static double weaponDamage(ItemStack stack){return stack.isEmpty()?1:stack.getAttributeModifiers().compute(Attributes.ATTACK_DAMAGE,1,EquipmentSlot.MAINHAND);}
    public static Weapon kind(ItemStack stack){
        if(Firearms.isMusket(stack))return Weapon.MUSKET;if(stack.is(Items.CROSSBOW))return Weapon.CROSSBOW;if(stack.is(Items.BOW))return Weapon.BOW;return Weapon.MELEE;
    }
    private static boolean spear(ItemStack stack){return stack.is(ItemTags.SPEARS)||Ledger.id(stack).endsWith("spear");}
    private static boolean shield(ItemStack stack){return stack.is(Items.SHIELD)||stack.has(DataComponents.BLOCKS_ATTACKS);}
    private static String candidate(LivingEntity commander,LivingEntity recruit){
        if(!(commander.level() instanceof ServerLevel)||!commander.isAlive()||!SurvivalApi.isHumanoid(commander))return "A living humanoid commander is required";
        if(recruit==null||recruit==commander||!recruit.isAlive()||recruit.level()!=commander.level()||!SurvivalApi.isHumanoid(recruit))return "Only another living humanoid can serve";
        if(commander.distanceToSqr(recruit)>16)return "Stand within four blocks";
        if(serving(recruit))return commander(recruit).equals(commander.getUUID().toString())?"Already in your service":"Already serving another commander";
        if(SurvivalApi.state(recruit).mental()<.3)return "Too ill or injured to serve";
        return "";
    }
    private static void enlist(LivingEntity commander,LivingEntity recruit,boolean volunteer,int nutrition){
        var service=new CompoundTag();service.putString("commander",commander.getUUID().toString());
        service.putString("kind",volunteer?"volunteer":"conscript");service.putDouble("morale",volunteer?70:35);service.putInt("rations",0);stock(service,nutrition);service.putString("order","follow");
        service.putLong("since",clock((ServerLevel)commander.level()));save(recruit,service);
        // A commander who is not a player leaves no body behind to say he fell, so he is marked as one whose death is to be recorded.
        if(!(commander instanceof Player))commander.getPersistentData().putBoolean(COMMANDS,true);
        if(recruit instanceof Mob mob){mob.setPersistenceRequired();mob.getNavigation().stop();}
    }
    // ---- The fallen ------------------------------------------------------------------------------------
    // A commander's death releases the men who can be found. Those in country nobody has loaded, or in another
    // world, learn of it when they are next seen: the time each commander fell is kept, and a man sworn before it
    // serves nobody. One sworn after it serves whoever bears that name now, so a revived player starts with none.
    private static final net.minecraft.resources.Identifier FALLEN=net.minecraft.resources.Identifier.fromNamespaceAndPath(SurvivalContent.MODID,"fallen_commanders");
    private static final String COMMANDS="survivalCommander";
    private static long clock(ServerLevel level){return level.getServer().overworld().getGameTime();}
    private static void fell(ServerLevel level,LivingEntity commander){
        var storage=level.getServer().getCommandStorage();var fallen=storage.get(FALLEN).copy();fallen.putLong(commander.getUUID().toString(),clock(level));storage.set(FALLEN,fallen);
    }
    private static boolean orphaned(ServerLevel level,CompoundTag service,LivingEntity commander){
        if(commander instanceof Player player&&SurvivalEvents.dead(player))return true;
        return service.getLongOr("since",Long.MAX_VALUE)<=level.getServer().getCommandStorage().get(FALLEN).getLongOr(service.getStringOr("commander",""),Long.MIN_VALUE);
    }
    /** Voluntary enlistment: the recruit takes the commander's rations as bounty and keeps them in the pack. */
    public static ActionResult recruit(LivingEntity commander,LivingEntity recruit){
        String invalid=candidate(commander,recruit);if(!invalid.isEmpty())return fail(invalid);
        ItemStack bounty=commander.getMainHandItem();var food=bounty.get(DataComponents.FOOD);
        if(food==null||ItemAging.spoiled(bounty)||nutrition(bounty)<16)return fail("Offer at least four rations of sound food in your main hand as enlistment bounty");
        if(recruit instanceof Player&&!recruit.isShiftKeyDown())return fail("A player enlists by crouching to accept");
        // A player carries his own food: the bounty goes into his hands, not into a pack only a villager has.
        if(recruit instanceof Player){SurvivalApi.give(recruit,bounty.copy());bounty.setCount(0);enlist(commander,recruit,true,0);return ok(recruit.getName().getString()+" enlisted as a volunteer and took the bounty");}
        int rations=nutrition(bounty)/4;enlist(commander,recruit,true,nutrition(bounty));bounty.setCount(0);
        return ok(recruit.getName().getString()+" enlisted as a volunteer with "+rations+" rations");
    }
    /** A soldier who is his own master gives notice and goes. Only a player can: a villager serves until discharged, deserted or dead. */
    public static ActionResult resign(LivingEntity soldier){
        if(!serving(soldier))return fail("Not in anyone's service");
        if(!(soldier instanceof Player))return fail("Only a player may give notice");
        if(soldier.level() instanceof ServerLevel level)notify(entity(level,commander(soldier)),soldier.getName().getString()+" has left your service");
        release(soldier);return ok("You have left the service");
    }
    /** Impressment: an armed commander with an armed escort, or facing an unarmed subject, compels service. */
    public static ActionResult conscript(LivingEntity commander,LivingEntity target){
        String invalid=candidate(commander,target);if(!invalid.isEmpty())return fail(invalid);
        if(target instanceof Player)return fail("Players cannot be pressed; they may volunteer");
        if(!armed(commander))return fail("Impressment needs a weapon in hand");
        int escort=0;for(LivingEntity soldier:roster(commander,12))if(armed(soldier))escort++;
        if(armed(target)&&escort<2)return fail("An armed subject will not be pressed without at least two armed soldiers at your back");
        enlist(commander,target,false,0);
        return ok(target.getName().getString()+" was pressed into service; conscripts start unwilling and need feeding");
    }
    public static ActionResult discharge(LivingEntity commander,LivingEntity soldier){
        if(!serving(soldier)||!commander(soldier).equals(commander.getUUID().toString()))return fail("Not in your service");
        release(soldier);return ok(soldier.getName().getString()+" discharged");
    }
    private static void release(LivingEntity soldier){
        var service=record(soldier);spill(soldier,service);soldier.getPersistentData().remove(KEY);
        if(soldier.level() instanceof ServerLevel level){
            VehicleEntity gun=vehicle(level,service.getStringOr("target",""));if(gun!=null)gun.crew.remove(soldier.getUUID().toString());
            Industry.leave(soldier);
        }
        if(soldier instanceof Mob mob)mob.getNavigation().stop();
    }
    private static VehicleEntity vehicle(ServerLevel level,String id){try{return id.isEmpty()?null:level.getEntity(UUID.fromString(id)) instanceof VehicleEntity v?v:null;}catch(IllegalArgumentException malformed){return null;}}
    public static List<LivingEntity> roster(LivingEntity commander,double range){
        String id=commander.getUUID().toString();
        return commander.level().getEntitiesOfClass(LivingEntity.class,commander.getBoundingBox().inflate(range),e->e.isAlive()&&serving(e)&&commander(e).equals(id));
    }
    private static int nutrition(ItemStack food){var properties=food.get(DataComponents.FOOD);return properties==null?0:properties.nutrition()*food.getCount();}
    /** Four points of nutrition make a ration, one meal. What is left over is kept towards the next, so small food is worth what it is and no more. */
    private static int stock(CompoundTag service,int nutrition){
        int crumbs=service.getIntOr("crumbs",0)+nutrition,added=crumbs/4;service.putInt("crumbs",crumbs%4);service.putInt("rations",service.getIntOr("rations",0)+added);return added;
    }
    /** What a villager soldier carried in the pack falls where he stands when he leaves the service or dies. */
    private static void spill(LivingEntity soldier,CompoundTag service){
        if(!(soldier instanceof Mob)||!(soldier.level() instanceof ServerLevel level))return;
        drop(level,soldier,new ItemStack(Items.ARROW),service.getIntOr("arrows",0));drop(level,soldier,new ItemStack(Items.FLINT),service.getIntOr("flints",0));
        drop(level,soldier,new ItemStack(SurvivalContent.item("paper_cartridge")),service.getIntOr("cartridges",0));
        service.remove("arrows");service.remove("flints");service.remove("cartridges");
    }
    private static void drop(ServerLevel level,LivingEntity from,ItemStack kind,int count){for(;count>0;count-=kind.getMaxStackSize())from.spawnAtLocation(level,kind.copyWithCount(Math.min(kind.getMaxStackSize(),count)));}
    /** Hands the held item to a soldier: armour is worn, a shield goes to the off hand, anything else
     * becomes the weapon in hand, and food, arrows and cartridges go into the pack. */
    public static ActionResult equip(LivingEntity giver,LivingEntity soldier,InteractionHand hand){
        if(!(giver.level() instanceof ServerLevel)||soldier==null||!soldier.isAlive()||giver.distanceToSqr(soldier)>16)return fail("Stand within four blocks of the soldier");
        if(!serving(soldier)||!commander(soldier).equals(giver.getUUID().toString()))return fail("Only your own soldiers take equipment from you");
        if(SurvivalApi.state(giver).manipulation()<.15)return fail("Your hands cannot hand over equipment");
        ItemStack held=giver.getItemInHand(hand);if(held.isEmpty())return fail("Hold what you are issuing");
        if(soldier instanceof Player){SurvivalApi.give(soldier,held.copy());held.setCount(0);return ok("Issued to "+soldier.getName().getString());}
        var service=record(soldier);
        if(held.has(DataComponents.FOOD)){
            if(ItemAging.spoiled(held))return fail("Rotten food is no ration");
            int added=stock(service,nutrition(held));held.setCount(0);save(soldier,service);return ok("Issued "+added+" rations (pack "+service.getIntOr("rations",0)+")");
        }
        if(held.is(ItemTags.ARROWS)){service.putInt("arrows",service.getIntOr("arrows",0)+held.getCount());held.setCount(0);save(soldier,service);return ok("Quiver: "+service.getIntOr("arrows",0)+" arrows");}
        if(held.is(Items.FLINT)){service.putInt("flints",service.getIntOr("flints",0)+held.getCount());held.setCount(0);save(soldier,service);return ok("Spare flints: "+service.getIntOr("flints",0));}
        if(held.is(SurvivalContent.item("paper_cartridge"))){service.putInt("cartridges",service.getIntOr("cartridges",0)+held.getCount());held.setCount(0);save(soldier,service);return ok("Cartridge box: "+service.getIntOr("cartridges",0));}
        var equippable=held.get(DataComponents.EQUIPPABLE);
        EquipmentSlot slot=shield(held)?EquipmentSlot.OFFHAND:equippable!=null&&equippable.slot().getType()==EquipmentSlot.Type.HUMANOID_ARMOR?equippable.slot():EquipmentSlot.MAINHAND;
        ItemStack previous=soldier.getItemBySlot(slot).copy();soldier.setItemSlot(slot,held.copyWithCount(1));held.shrink(1);
        if(soldier instanceof Mob mob)mob.setGuaranteedDrop(slot);
        SurvivalApi.give(giver,previous);
        return ok(soldier.getName().getString()+" now carries "+soldier.getItemBySlot(slot).getHoverName().getString()+" ("+slot.getName()+")");
    }
    private static Set<String> hostiles(LivingEntity commander){var set=new LinkedHashSet<String>();for(String id:commander.getPersistentData().getStringOr("survivalHostile","").split(","))if(!id.isBlank())set.add(id);return set;}
    /** Declares another commander (or an unattached individual) an enemy: guards engage them on sight. */
    public static ActionResult hostile(LivingEntity commander,Entity other,boolean enemy){
        String id=serving(other)?commander(other):other.getUUID().toString();if(id.equals(commander.getUUID().toString()))return fail("Those are your own");
        var set=hostiles(commander);if(enemy)set.add(id);else set.remove(id);
        // Sixty-four standing quarrels is a great many; the oldest is forgotten for the newest.
        while(set.size()>64)set.remove(set.iterator().next());
        commander.getPersistentData().putString("survivalHostile",String.join(",",set));
        return ok(enemy?"Declared hostile: guards will engage":"No longer hostile");
    }
    /** Issues one order to every listed soldier of this commander. Formation orders give each a place in the ranks. */
    public static ActionResult order(LivingEntity commander,Collection<? extends Entity> soldiers,String type,Vec3 position,Entity target,double radius,String formation){
        if(!ORDERS.contains(type))return fail("Orders: "+String.join(", ",new TreeSet<>(ORDERS)));
        if(!(commander.level() instanceof ServerLevel level))return fail("Server world required");
        if((type.equals("attack")||type.equals("charge")||type.equals("fire")||type.equals("mount")||type.equals("crew")||type.equals("drive"))&&target==null)return fail("That order needs a target");
        if((type.equals("move")||type.equals("guard")||type.equals("drive")||type.equals("work"))&&position==null)return fail("That order needs a position");
        if((type.equals("crew")||type.equals("drive"))&&!(target instanceof VehicleEntity))return fail("That order needs a vehicle or siege piece");
        if(type.equals("mount")&&!(target instanceof AbstractHorse)&&!(target instanceof VehicleEntity))return fail("A soldier mounts a horse or a vehicle, nothing else");
        String id=commander.getUUID().toString();var obeying=new ArrayList<LivingEntity>();
        for(Entity entity:soldiers)if(entity instanceof LivingEntity soldier&&soldier.isAlive()&&serving(soldier)&&commander(soldier).equals(id))obeying.add(soldier);
        if(obeying.isEmpty())return fail("None of those are in your service");
        Vec3 facing=Vec3.directionFromRotation(0,commander.getYRot()),right=new Vec3(-facing.z,0,facing.x);int index=0;
        for(LivingEntity soldier:obeying){
            var service=record(soldier);VehicleEntity old=vehicle(level,service.getStringOr("target",""));if(old!=null&&!type.equals("crew"))old.crew.remove(soldier.getUUID().toString());
            service.putString("order",type);service.remove("target");service.remove("stranded");service.putInt("slot",index);
            if(target!=null)service.putString("target",target.getUUID().toString());
            if(position!=null){
                Vec3 place=position;
                if(type.equals("move")||type.equals("guard")){
                    int count=obeying.size();
                    place=switch(formation==null?"line":formation){
                        case "column"->position.add(right.scale((index%2-.5)*1.4)).add(facing.scale(-(index/2)*1.4));
                        case "square"->{int side=Math.max(2,(int)Math.ceil(count/4d)),edge=index/side%4,along=index%side;double half=side*.7;
                            yield switch(edge){case 0->position.add(-half+along*1.4,0,-half);case 1->position.add(half,0,-half+along*1.4);case 2->position.add(half-along*1.4,0,half);default->position.add(-half,0,half-along*1.4);};}
                        default->{int width=Math.min(count,8),file=index%width,rank=index/width;yield position.add(right.scale((file-(width-1)/2d)*1.3)).add(facing.scale(-rank*1.3));}
                    };
                }
                service.putDouble("x",place.x);service.putDouble("y",place.y);service.putDouble("z",place.z);
            }
            service.putDouble("radius",radius<=0?12:radius);save(soldier,service);index++;
            if(type.equals("dismount"))soldier.stopRiding();
            if(soldier instanceof ServerPlayer player)player.sendSystemMessage(Component.literal("Order from "+commander.getName().getString()+": "+type+(position!=null?" "+BlockPos.containing(position).toShortString():"")+(target!=null?" "+target.getName().getString():"")));
        }
        return ok(obeying.size()+" ordered to "+type);
    }
    public static JsonObject inspect(LivingEntity soldier){
        var json=new JsonObject();json.addProperty("serving",serving(soldier));if(!serving(soldier))return json;var service=record(soldier);
        json.addProperty("commander",service.getStringOr("commander",""));json.addProperty("kind",service.getStringOr("kind",""));json.addProperty("morale",service.getDoubleOr("morale",0));
        json.addProperty("order",service.getStringOr("order",""));json.addProperty("rations",service.getIntOr("rations",0));json.addProperty("arrows",service.getIntOr("arrows",0));json.addProperty("cartridges",service.getIntOr("cartridges",0));
        json.addProperty("routing",service.getLongOr("routUntil",0)>soldier.level().getGameTime());json.addProperty("weapon",Ledger.id(soldier.getMainHandItem()));json.addProperty("offhand",Ledger.id(soldier.getOffhandItem()));
        return json;
    }
    /** Fear spreads from where shot lands. */
    public static void shock(ServerLevel level,Vec3 at,double radius,double amount){
        for(LivingEntity soldier:level.getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(at,at).inflate(radius),Military::serving)){var service=record(soldier);service.putDouble("morale",Math.max(0,service.getDoubleOr("morale",50)-amount));save(soldier,service);}
    }

    // ---- Upkeep and morale ----------------------------------------------------------------------------
    private static void tick(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof LivingEntity soldier)||!(soldier.level() instanceof ServerLevel level)||!soldier.isAlive()||!serving(soldier))return;
        var service=record(soldier);
        if(soldier.tickCount%100==0){upkeep(soldier,level,service);if(!serving(soldier))return;}
        if(soldier instanceof Mob mob&&!mob.isNoAi())execute(mob,level,service);
    }
    private static void upkeep(LivingEntity soldier,ServerLevel level,CompoundTag service){
        LivingEntity commander=entity(level,service.getStringOr("commander",""));
        if(orphaned(level,service,commander)){release(soldier);return;}
        SurvivalState body=SurvivalApi.state(soldier);boolean changed=false;
        boolean conscript=service.getStringOr("kind","").equals("conscript");double morale=service.getDoubleOr("morale",50);
        if(soldier instanceof Mob){
            // Draw rations from a supply vehicle when the pack runs low: an army marches on its wagons.
            if(service.getIntOr("rations",0)<4)for(VehicleEntity wagon:level.getEntitiesOfClass(VehicleEntity.class,soldier.getBoundingBox().inflate(8))){
                boolean drew=false;
                for(String id:new ArrayList<>(wagon.cargo.keySet())){ItemStack sample=new ItemStack(Ledger.item(id));if(!sample.has(DataComponents.FOOD)||id.endsWith("spoiled_food"))continue;Ledger.take(wagon.cargo,id,1);stock(service,nutrition(sample));drew=true;break;}
                if(drew)break;
            }
            if(body.hunger<60&&service.getIntOr("rations",0)>0){service.putInt("rations",service.getIntOr("rations",0)-1);body.feed(20);changed=true;}
            if(service.getIntOr("canteen",0)<4&&waterNear(level,soldier.blockPosition()))service.putInt("canteen",4);
            if(body.thirst<60&&service.getIntOr("canteen",0)>0){service.putInt("canteen",service.getIntOr("canteen",0)-1);SurvivalApi.hydrate(soldier,body,25,false);changed=true;}
        }
        if(changed){SurvivalApi.save(soldier,body);SurvivalApi.syncFood(soldier,body);}
        boolean led=commander!=null&&commander.isAlive()&&commander.distanceToSqr(soldier)<=32*32;
        if(body.hunger<25)morale-=1.5;if(body.thirst<25)morale-=1.5;if(body.bleeding>0)morale-=1;
        if(led)morale+=.25;else morale-=conscript?.5:.1;
        if(body.hunger>60&&body.thirst>60)morale+=((conscript?45:75)-morale)*.02;
        morale=Mth.clamp(morale,0,100);service.putDouble("morale",morale);
        // Unwilling men slip away once nobody is watching; even volunteers leave when all hope is gone.
        if((conscript&&morale<15||morale<5)&&!led){
            if(commander instanceof ServerPlayer player)player.sendSystemMessage(Component.literal(soldier.getName().getString()+" has deserted"));
            release(soldier);
        }
    }
    private static boolean waterNear(ServerLevel level,BlockPos centre){for(BlockPos pos:BlockPos.betweenClosed(centre.offset(-4,-2,-4),centre.offset(4,1,4)))if(level.getFluidState(pos).is(FluidTags.WATER))return true;return false;}
    private static void damaged(LivingDamageEvent.Post event){
        LivingEntity victim=event.getEntity();if(!(victim.level() instanceof ServerLevel level)||event.getHealthDamage()<=0)return;
        Entity attacker=event.getSource().getEntity();float damage=event.getHealthDamage();
        // Fighting teaches fighting, for every humanoid.
        if(attacker instanceof LivingEntity fighter&&SurvivalApi.isHumanoid(fighter)&&fighter!=victim&&(SurvivalApi.isHumanoid(victim)||victim instanceof Mob)&&!event.getSource().is(Firearms.GUNSHOT)){
            if(event.getSource().getDirectEntity() instanceof Projectile)SkillApi.award(fighter,Skill.MARKSMANSHIP,damage*12);
            else if(event.getSource().getDirectEntity()==fighter)SkillApi.award(fighter,Skill.MELEE,damage*12);
        }
        // An unpractised rider is easily unhorsed.
        if(victim.getVehicle() instanceof LivingEntity&&SurvivalApi.isHumanoid(victim)&&!SurvivalApi.exempt(victim)&&damage>=3){
            double chance=Math.max(0,.3-.015*SkillApi.level(victim,Skill.RIDING));
            if(victim.getRandom().nextDouble()<chance){victim.stopRiding();if(victim instanceof ServerPlayer player)player.sendOverlayMessage(Component.literal("Unhorsed!"));}
        }
        if(!serving(victim))return;
        var service=record(victim);boolean conscript=service.getStringOr("kind","").equals("conscript");
        double morale=Math.max(0,service.getDoubleOr("morale",50)-damage*(conscript?1.8:1.2));service.putDouble("morale",morale);
        if(attacker instanceof LivingEntity threat&&threat!=victim&&!side(threat).equals(service.getStringOr("commander","?"))){service.putString("threat",threat.getUUID().toString());service.putLong("threatAt",level.getGameTime());}
        if(morale<20&&service.getLongOr("routUntil",0)<=level.getGameTime()){
            service.putLong("routUntil",level.getGameTime()+400);
            LivingEntity commander=entity(level,service.getStringOr("commander",""));if(commander instanceof ServerPlayer player)player.sendSystemMessage(Component.literal(victim.getName().getString()+" has broken and is running"));
        }
    }
    /** Whose man this is: his commander's, or his own. */
    private static String side(Entity actor){return serving(actor)?commander(actor):actor.getUUID().toString();}
    private static void died(LivingDeathEvent event){
        LivingEntity dead=event.getEntity();if(!(dead.level() instanceof ServerLevel level))return;
        // A dead commander's men are nobody's soldiers: they go home rather than serve a name. Only a person commands,
        // so the death of a hen does not send anyone looking through the world.
        if(dead instanceof Player||dead.getPersistentData().getBooleanOr(COMMANDS,false)){
            fell(level,dead);String fallen=dead.getUUID().toString();var orphans=new ArrayList<LivingEntity>();
            for(ServerLevel world:level.getServer().getAllLevels())for(Entity entity:world.getAllEntities())if(entity instanceof LivingEntity soldier&&soldier!=dead&&soldier.isAlive()&&serving(soldier)&&commander(soldier).equals(fallen))orphans.add(soldier);
            for(LivingEntity soldier:orphans)release(soldier);
        }
        // Nobody stays at war with the dead: the name comes off the list of every commander present.
        if(SurvivalApi.isHumanoid(dead))for(ServerPlayer player:level.getServer().getPlayerList().getPlayers()){var set=hostiles(player);if(set.remove(dead.getUUID().toString()))player.getPersistentData().putString("survivalHostile",String.join(",",set));}
        if(!serving(dead))return;
        spill(dead,record(dead));
        String side=commander(dead);Entity killer=event.getSource().getEntity();String killerSide=killer==null?"":serving(killer)?commander(killer):killer.getUUID().toString();
        for(LivingEntity other:level.getEntitiesOfClass(LivingEntity.class,dead.getBoundingBox().inflate(16),e->e!=dead&&e.isAlive()&&serving(e))){
            var service=record(other);String theirs=service.getStringOr("commander","");double morale=service.getDoubleOr("morale",50);
            if(theirs.equals(side))morale-=8;else if(theirs.equals(killerSide))morale+=4;else continue;
            service.putDouble("morale",Mth.clamp(morale,0,100));
        }
        VehicleEntity gun=vehicle(level,record(dead).getStringOr("target",""));if(gun!=null)gun.crew.remove(dead.getUUID().toString());
    }
    /** A practised hand strikes harder; a novice wastes much of the blow. */
    private static void incoming(LivingIncomingDamageEvent event){
        if(event.getSource().getEntity() instanceof Player player&&event.getSource().getDirectEntity()==player&&!event.getSource().is(Firearms.GUNSHOT)&&player.level() instanceof ServerLevel&&!SurvivalApi.exempt(player))
            event.setAmount((float)(event.getAmount()*(.8+.4*SkillApi.level(player,Skill.MELEE)/20d)));
    }
    private static void interact(PlayerInteractEvent.EntityInteract event){if(event.getEntity().isSpectator())return;
        if(!(event.getLevel() instanceof ServerLevel)||!(event.getTarget() instanceof LivingEntity soldier)||!event.getEntity().isShiftKeyDown())return;
        Player player=event.getEntity();ItemStack held=event.getItemStack();
        if(held.isEmpty()||!serving(soldier)||!commander(soldier).equals(player.getUUID().toString()))return;
        // Feeding, clothing and medicine keep their ordinary meaning; everything else is issued as kit.
        String id=Ledger.id(held);if(held.has(DataComponents.FOOD)&&SurvivalApi.state(soldier).hunger<99||id.matches(".*:(wool|fiber)_(shirt|pants|jacket|shoes)")||Set.of("bandage","painkiller","antitoxin","antiseptic","antibiotic").contains(id.substring(id.indexOf(':')+1)))return;
        var result=equip(player,soldier,event.getHand());player.sendOverlayMessage(Component.literal(result.message()));
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
    }

    // ---- Order execution ------------------------------------------------------------------------------
    private static void go(Mob soldier,Vec3 where,double speed){
        if(soldier.isPassenger()&&!(soldier.getVehicle() instanceof Mob))return;
        // Twice a second is often enough to look for the way again. Asking whenever the last path has run out asks
        // every tick of a man whose goal cannot be reached, and each asking is a search of the ground around him.
        if(beat(soldier))soldier.getNavigation().moveTo(where.x,where.y,where.z,speed);
    }
    private static boolean beat(Mob soldier){return (soldier.tickCount+soldier.getId())%10==0;}
    /** At the place: near it on the ground, whatever the height of the block that was named. */
    private static boolean arrived(Mob soldier,Vec3 place,double within){
        double dx=soldier.getX()-place.x,dz=soldier.getZ()-place.z;return dx*dx+dz*dz<=within&&Math.abs(soldier.getY()-place.y)<=3;
    }
    /** At the end of a road that does not lead where he was sent. */
    private static boolean stranded(Mob soldier){
        if(soldier.isPassenger()&&!(soldier.getVehicle() instanceof Mob))return false;
        var path=soldier.getNavigation().getPath();if(path==null)return soldier.onGround()||soldier.isPassenger();
        if(path.canReach())return false;var end=path.getEndNode();
        return end==null||end.asBlockPos().distSqr(soldier.getRootVehicle().blockPosition())<=4;
    }
    private static void halt(Mob soldier){if(!soldier.getNavigation().isDone())soldier.getNavigation().stop();}
    private static Vec3 post(CompoundTag service,Entity fallback){return service.contains("x")?new Vec3(service.getDoubleOr("x",0),service.getDoubleOr("y",0),service.getDoubleOr("z",0)):fallback.position();}
    private static void execute(Mob soldier,ServerLevel level,CompoundTag service){
        SurvivalState body=SurvivalApi.state(soldier);if(body.mental()<=0)return;
        long now=level.getGameTime();String order=service.getStringOr("order","hold");
        LivingEntity commander=entity(level,service.getStringOr("commander",""));
        // Broken men run from whatever broke them and rally only near their commander.
        if(service.getLongOr("routUntil",0)>now){
            LivingEntity threat=entity(level,service.getStringOr("threat",""));Vec3 from=threat!=null?threat.position():soldier.position().add(soldier.getLookAngle());
            Vec3 away=soldier.position().subtract(from).multiply(1,0,1);if(away.lengthSqr()<1e-4)away=new Vec3(1,0,0);
            go(soldier,soldier.position().add(away.normalize().scale(14)),1.35);return;
        }
        if(service.contains("routUntil")){
            service.remove("routUntil");
            if(commander!=null&&commander.distanceToSqr(soldier)<=24*24)service.putDouble("morale",Math.max(25,service.getDoubleOr("morale",0)));
            else if(service.getDoubleOr("morale",0)<20)service.putLong("routUntil",now+400);
        }
        // Without rations or water a soldier off the firing line breaks ranks to forage for himself.
        boolean foraging=(body.thirst<45&&service.getIntOr("canteen",0)==0||body.hunger<35&&service.getIntOr("rations",0)==0)&&Set.of("hold","follow","guard","move").contains(order);
        if(foraging)return;
        switch(order){
            case "follow","retreat"->{
                if(commander==null){halt(soldier);break;}
                double keep=3+service.getIntOr("slot",0)%6*.8;
                if(soldier.distanceToSqr(commander)>keep*keep*2.2)go(soldier,commander.position(),order.equals("retreat")?1.3:1.1);else{halt(soldier);if(order.equals("retreat"))service.putString("order","follow");}
            }
            case "move"->{
                Vec3 place=post(service,soldier);
                if(arrived(soldier,place,2.6)){halt(soldier);service.putString("order","hold");service.remove("stranded");break;}
                go(soldier,place,1.1);
                // A place he cannot get to is not walked at for ever: after three seconds at the end of the road he holds there.
                if(beat(soldier)){
                    int tries=stranded(soldier)?service.getIntOr("stranded",0)+1:0;service.putInt("stranded",tries);
                    if(tries>=6){halt(soldier);service.putString("order","hold");service.remove("stranded");notify(commander,soldier.getName().getString()+" cannot reach the ordered position and holds where he is");}
                }
            }
            case "hold"->{
                LivingEntity threat=entity(level,service.getStringOr("threat",""));
                if(threat!=null&&threat.isAlive()&&now-service.getLongOr("threatAt",0)<200&&soldier.distanceToSqr(threat)<36)fight(soldier,threat,level,service,body,"hold");else halt(soldier);
            }
            case "guard"->{
                Vec3 place=post(service,soldier);double radius=service.getDoubleOr("radius",12);LivingEntity foe=entity(level,service.getStringOr("foe",""));
                if(foe!=null&&(!foe.isAlive()||foe.position().distanceToSqr(place)>radius*radius*2.25)){foe=null;service.remove("foe");}
                if(foe==null&&now%20==soldier.getId()%20){foe=sight(soldier,level,service,commander,place,radius);if(foe!=null)service.putString("foe",foe.getUUID().toString());}
                if(foe!=null)fight(soldier,foe,level,service,body,"attack");else if(!arrived(soldier,place,3))go(soldier,place,1.0);else halt(soldier);
            }
            case "attack","charge","fire"->{
                LivingEntity target=entity(level,service.getStringOr("target",""));
                if(target==null||!target.isAlive()||target.distanceToSqr(soldier)>96*96){halt(soldier);service.putString("order","hold");service.putDouble("x",soldier.getX());service.putDouble("y",soldier.getY());service.putDouble("z",soldier.getZ());break;}
                fight(soldier,target,level,service,body,order);
            }
            case "mount"->{
                Entity mount=null;try{mount=level.getEntity(UUID.fromString(service.getStringOr("target","")));}catch(IllegalArgumentException ignored){}
                if(mount==null||!mount.isAlive()){service.putString("order","hold");break;}
                if(soldier.getVehicle()==mount){service.putString("order","follow");break;}
                if(soldier.distanceToSqr(mount)>9){go(soldier,mount.position(),1.1);break;}
                if(mount instanceof AbstractHorse horse&&(!horse.isTamed()||!horse.isSaddled())){service.putString("order","hold");notify(commander,soldier.getName().getString()+" cannot ride an untamed or unsaddled animal");break;}
                // A horse carries one rider and a vehicle as many as it seats; nothing else is ridden, and nobody is unseated for him.
                boolean horse=mount instanceof AbstractHorse;
                if(!horse&&!(mount instanceof VehicleEntity)||horse&&!mount.getPassengers().isEmpty()||!soldier.startRiding(mount,horse,true)){service.putString("order","hold");notify(commander,soldier.getName().getString()+" found no seat");}
            }
            case "dismount"->{soldier.stopRiding();service.putString("order","hold");}
            case "crew"->{
                VehicleEntity gun=vehicle(level,service.getStringOr("target",""));if(gun==null){service.putString("order","hold");break;}
                if(soldier.distanceToSqr(gun)>9)go(soldier,gun.position(),1.1);else{halt(soldier);if(!gun.crew.contains(soldier.getUUID().toString()))Siege.man(soldier,gun);}
            }
            case "drive"->{
                VehicleEntity wagon=vehicle(level,service.getStringOr("target",""));if(wagon==null){service.putString("order","hold");break;}
                if(soldier.getVehicle()!=wagon&&soldier.distanceToSqr(wagon)>9){go(soldier,wagon.position(),1.1);break;}
                var result=Vehicles.drive(soldier,wagon,post(service,soldier));if(!result.success())notify(commander,soldier.getName().getString()+": "+result.message());
                service.putString("order","hold");
            }
            case "work"->{
                Vec3 place=post(service,soldier);
                if(soldier.position().distanceToSqr(place)>16){go(soldier,place,1.0);break;}
                halt(soldier);
                if(!soldier.getPersistentData().contains("survivalWorksPos")&&now%40==0){WorksEntity works=Industry.nearest(soldier);if(works!=null){var result=Industry.operate(soldier,works);if(!result.success()){notify(commander,soldier.getName().getString()+": "+result.message());service.putString("order","hold");}}}
            }
            default->halt(soldier);
        }
    }
    private static void notify(LivingEntity commander,String message){if(commander instanceof ServerPlayer player)player.sendSystemMessage(Component.literal(message));}
    /** Guards engage declared enemies, and anyone who has just attacked one of their own. */
    private static LivingEntity sight(Mob soldier,ServerLevel level,CompoundTag service,LivingEntity commander,Vec3 place,double radius){
        // The standing order is carried by the guard: a commander who has gone home, or to bed, has not made peace.
        if(commander!=null)service.putString("enemies",commander.getPersistentData().getStringOr("survivalHostile",""));
        Set<String> enemies=Set.of(service.getStringOr("enemies","").split(","));String side=service.getStringOr("commander","");LivingEntity best=null;double nearest=Double.MAX_VALUE;
        LivingEntity threat=entity(level,service.getStringOr("threat",""));if(threat!=null&&threat.isAlive()&&level.getGameTime()-service.getLongOr("threatAt",0)<600&&threat.position().distanceToSqr(place)<=radius*radius)return threat;
        for(LivingEntity other:level.getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(place,place).inflate(radius),e->e!=soldier&&e.isAlive()&&!SurvivalApi.exempt(e))){
            String theirs=serving(other)?commander(other):other.getUUID().toString();if(theirs.equals(side)||!enemies.contains(theirs))continue;
            double distance=soldier.distanceToSqr(other);if(distance<nearest&&soldier.hasLineOfSight(other)){nearest=distance;best=other;}
        }
        return best;
    }
    private static boolean frontal(LivingEntity defender,Entity attacker){
        Vec3 facing=Vec3.directionFromRotation(0,defender.yBodyRot),toward=attacker.position().subtract(defender.position()).multiply(1,0,1);
        return toward.lengthSqr()<1e-4||facing.dot(toward.normalize())>.35;
    }
    private static void fight(Mob soldier,LivingEntity target,ServerLevel level,CompoundTag service,SurvivalState body,String mode){
        ItemStack weapon=soldier.getMainHandItem();Weapon kind=kind(weapon);long now=level.getGameTime();double distance=soldier.distanceTo(target);
        soldier.getLookControl().setLookAt(target,30,30);
        boolean ammunition=switch(kind){case BOW,CROSSBOW->service.getIntOr("arrows",0)>0;case MUSKET->Firearms.loaded(weapon)&&!Firearms.flintWorn(weapon)||(service.getIntOr("cartridges",0)>0||service.getBooleanOr("ramming",false))&&(!Firearms.flintWorn(weapon)||service.getIntOr("flints",0)>0);default->false;};
        if(kind!=Weapon.MELEE&&ammunition&&!mode.equals("charge")&&body.manipulation()>=.2){
            double range=switch(kind){case BOW->24;case CROSSBOW->40;default->48;};
            if(distance>range||!soldier.hasLineOfSight(target)){if(mode.equals("fire")||mode.equals("hold"))halt(soldier);else go(soldier,target.position(),1.1);return;}
            halt(soldier);float yaw=(float)(Mth.atan2(target.getZ()-soldier.getZ(),target.getX()-soldier.getX())*Mth.RAD_TO_DEG)-90;soldier.setYRot(yaw);soldier.setYBodyRot(yaw);
            if(now<service.getLongOr("nextAttack",0))return;
            double skill=body.skills.level(Skill.MARKSMANSHIP),pace=(.6+.4*skill/20)*Math.max(.2,body.manipulation());
            if(kind==Weapon.MUSKET){
                if(Firearms.flintWorn(weapon)){service.putInt("flints",service.getIntOr("flints",0)-1);Firearms.reflint(weapon);}
                if(!Firearms.loaded(weapon)){
                    // Thirty rounds foul a barrel badly enough that the soldier stops to scour it.
                    if(Firearms.fouling(weapon)>=30){
                        if(!service.getBooleanOr("cleaning",false)){service.putBoolean("cleaning",true);service.putLong("nextAttack",now+(long)(Firearms.CLEAN_WORK/pace));return;}
                        service.putBoolean("cleaning",false);Firearms.clean(weapon);
                    }
                    // The loading drill: the cartridge is spent when ramming begins.
                    if(!service.getBooleanOr("ramming",false)){service.putInt("cartridges",service.getIntOr("cartridges",0)-1);service.putBoolean("ramming",true);service.putLong("nextAttack",now+(long)(Firearms.loadWork(weapon)/pace));return;}
                    service.putBoolean("ramming",false);Firearms.setLoaded(weapon,true);
                }
                Vec3 aim=target.getEyePosition().subtract(0,target.getBbHeight()*.3,0).subtract(soldier.getEyePosition()).normalize();
                Firearms.fire(soldier,weapon,level,aim);soldier.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,true);service.putLong("nextAttack",now+10);return;
            }
            Arrow arrow=new Arrow(level,soldier,new ItemStack(Items.ARROW),weapon);arrow.pickup=AbstractArrow.Pickup.ALLOWED;
            double dx=target.getX()-soldier.getX(),dy=target.getY(.3333)-arrow.getY(),dz=target.getZ()-soldier.getZ(),flat=Math.sqrt(dx*dx+dz*dz);
            float error=(float)Math.max(1,(soldier.isPassenger()?16:11)-.45*skill);
            if(kind==Weapon.CROSSBOW)arrow.shoot(dx,dy+flat*.05,dz,3.15f,error*.6f);else arrow.shoot(dx,dy+flat*.2,dz,1.6f,error);
            level.addFreshEntity(arrow);service.putInt("arrows",service.getIntOr("arrows",0)-1);weapon.hurtAndBreak(1,level,soldier,item->{});
            level.playSound(null,soldier.getX(),soldier.getY(),soldier.getZ(),kind==Weapon.CROSSBOW?SoundEvents.CROSSBOW_SHOOT:SoundEvents.ARROW_SHOOT,SoundSource.NEUTRAL,1,1);
            soldier.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,true);service.putLong("nextAttack",now+(long)((kind==Weapon.CROSSBOW?80:32)/pace));return;
        }
        if(mode.equals("fire")){halt(soldier);return;} // told to shoot, with nothing left to shoot
        boolean reach=soldier.getAttackRangeWith(weapon).isInRange(soldier,target.getBoundingBox(),0)&&soldier.hasLineOfSight(target);
        if(!reach){if(mode.equals("hold"))halt(soldier);else go(soldier,target.position(),soldier.isPassenger()?1.6:1.25);return;}
        if(now<service.getLongOr("nextAttack",0))return;
        double skill=body.skills.level(Skill.MELEE),base=weaponDamage(weapon)*Math.max(.1,body.manipulation())*(.6+.4*skill/20);
        Entity mount=soldier.getVehicle();double speed=mount instanceof LivingEntity?mount.getDeltaMovement().horizontalDistance():0;
        boolean lance=mount instanceof LivingEntity&&spear(weapon)&&speed>.1;
        // Infantry who stand with levelled spears receive a horse on their points before the rider can strike.
        if(mount instanceof LivingEntity horse&&target instanceof Mob defender&&serving(defender)&&spear(defender.getMainHandItem())&&Set.of("hold","guard").contains(order(defender))&&frontal(defender,soldier)){
            horse.hurtServer(level,defender.damageSources().mobAttack(defender),(float)(weaponDamage(defender.getMainHandItem())*1.5));lance=false;base*=.6;
        }
        // A couched spear carries the weight and speed of the horse behind it.
        if(lance)base*=1+Math.min(2.5,speed*7);
        long recover=(long)Math.max(8,20/Math.max(.5,weapon.getAttributeModifiers().compute(Attributes.ATTACK_SPEED,4,EquipmentSlot.MAINHAND))/((.7+.3*skill/20)*Math.max(.2,body.manipulation())));
        service.putLong("nextAttack",now+recover);soldier.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,true);
        if(target instanceof Mob defender&&frontal(defender,soldier)){
            ItemStack guard=shield(defender.getOffhandItem())?defender.getOffhandItem():shield(defender.getMainHandItem())?defender.getMainHandItem():ItemStack.EMPTY;
            if(!guard.isEmpty()&&level.getRandom().nextDouble()<.35+.03*SkillApi.level(defender,Skill.MELEE)){
                guard.hurtAndBreak(Math.max(1,(int)base),level,defender,item->{});level.playSound(null,defender.getX(),defender.getY(),defender.getZ(),SoundEvents.SHIELD_BLOCK,SoundSource.NEUTRAL,1,1);
                SkillApi.award(defender,Skill.MELEE,20);return;
            }
        }
        if(target.hurtServer(level,soldier.damageSources().mobAttack(soldier),(float)base)){weapon.hurtAndBreak(1,level,soldier,item->{});soldier.setLastHurtMob(target);}
        // The blow may have taught him something, so the body is read again before the effort is charged to it.
        var spent=SurvivalApi.state(soldier);spent.hunger=Math.max(0,spent.hunger-.05);SurvivalApi.save(soldier,spent);
    }

    // ---- Commands -------------------------------------------------------------------------------------
    @FunctionalInterface private interface ActorLookup{LivingEntity get(CommandContext<CommandSourceStack> context)throws CommandSyntaxException;}
    private static void commands(RegisterCommandsEvent event){
        var own=literal("military");tree(own,c->c.getSource().getPlayerOrException());event.getDispatcher().register(literal("survival").then(own));
        var controlled=literal("military");tree(controlled,c->living(EntityArgument.getEntity(c,"actor")));
        event.getDispatcher().register(literal("survival").then(literal("control").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(argument("actor",EntityArgument.entity()).then(controlled))));
    }
    private static LivingEntity living(Entity entity)throws CommandSyntaxException{
        if(entity instanceof LivingEntity living&&SurvivalApi.isHumanoid(living))return living;
        throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Living humanoid required")).create();
    }
    private static LivingEntity target(Entity entity)throws CommandSyntaxException{
        if(entity instanceof LivingEntity living)return living;
        throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Living target required")).create();
    }
    private static void tree(ArgumentBuilder<CommandSourceStack,?> node,ActorLookup actor){
        node.then(literal("recruit").then(argument("target",EntityArgument.entity()).executes(c->say(c.getSource(),recruit(actor.get(c),target(EntityArgument.getEntity(c,"target")))))));
        node.then(literal("conscript").then(argument("target",EntityArgument.entity()).executes(c->say(c.getSource(),conscript(actor.get(c),target(EntityArgument.getEntity(c,"target")))))));
        node.then(literal("discharge").then(argument("target",EntityArgument.entity()).executes(c->say(c.getSource(),discharge(actor.get(c),target(EntityArgument.getEntity(c,"target")))))));
        node.then(literal("equip").then(argument("target",EntityArgument.entity()).executes(c->say(c.getSource(),equip(actor.get(c),target(EntityArgument.getEntity(c,"target")),InteractionHand.MAIN_HAND)))));
        node.then(literal("hostile").then(argument("target",EntityArgument.entity()).executes(c->say(c.getSource(),hostile(actor.get(c),EntityArgument.getEntity(c,"target"),true)))));
        node.then(literal("peace").then(argument("target",EntityArgument.entity()).executes(c->say(c.getSource(),hostile(actor.get(c),EntityArgument.getEntity(c,"target"),false)))));
        node.then(literal("roster").executes(c->{
            LivingEntity commander=actor.get(c);var soldiers=roster(commander,96);if(soldiers.isEmpty())return say(c.getSource(),fail("No soldiers within ninety-six blocks"));
            for(LivingEntity soldier:soldiers){var service=record(soldier);var body=SurvivalApi.state(soldier);
                String line=String.format(Locale.ROOT,"%s [%s] %s | morale %.0f | order %s | %s%s | rations %d arrows %d cartridges %d | health %.0f/%.0f hunger %.0f thirst %.0f | melee %d marksmanship %d",
                    soldier.getName().getString(),soldier.getUUID(),service.getStringOr("kind",""),service.getDoubleOr("morale",0),service.getStringOr("order",""),soldier.getMainHandItem().isEmpty()?"unarmed":soldier.getMainHandItem().getHoverName().getString(),
                    soldier.getOffhandItem().isEmpty()?"":" + "+soldier.getOffhandItem().getHoverName().getString(),service.getIntOr("rations",0),service.getIntOr("arrows",0),service.getIntOr("cartridges",0),soldier.getHealth(),soldier.getMaxHealth(),body.hunger,body.thirst,body.skills.level(Skill.MELEE),body.skills.level(Skill.MARKSMANSHIP));
                c.getSource().sendSuccess(()->Component.literal(line),false);}
            return soldiers.size();
        }));
        // A selector needs an operator's rights, so a commander who has none names one soldier or calls on all within earshot.
        var order=literal("order");
        var named=argument("soldiers",EntityArgument.entities());orders(named,actor,c->EntityArgument.getEntities(c,"soldiers"));order.then(named);
        var all=literal("all");orders(all,actor,c->roster(actor.get(c),96));order.then(all);
        node.then(order);
        node.then(literal("resign").executes(c->say(c.getSource(),resign(actor.get(c)))));
    }
    @FunctionalInterface private interface Squad{Collection<? extends Entity> get(CommandContext<CommandSourceStack> context)throws CommandSyntaxException;}
    private static void orders(ArgumentBuilder<CommandSourceStack,?> soldiers,ActorLookup actor,Squad squad){
        for(String simple:new String[]{"hold","follow","retreat","dismount"})soldiers.then(literal(simple).executes(c->say(c.getSource(),order(actor.get(c),squad.get(c),simple,null,null,0,null))));
        soldiers.then(literal("move").then(argument("pos",BlockPosArgument.blockPos()).executes(c->say(c.getSource(),order(actor.get(c),squad.get(c),"move",Vec3.atBottomCenterOf(BlockPosArgument.getBlockPos(c,"pos")),null,0,"line")))
            .then(argument("formation",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"line","column","square"},b)).executes(c->say(c.getSource(),order(actor.get(c),squad.get(c),"move",Vec3.atBottomCenterOf(BlockPosArgument.getBlockPos(c,"pos")),null,0,StringArgumentType.getString(c,"formation")))))));
        soldiers.then(literal("guard").then(argument("pos",BlockPosArgument.blockPos()).executes(c->say(c.getSource(),order(actor.get(c),squad.get(c),"guard",Vec3.atBottomCenterOf(BlockPosArgument.getBlockPos(c,"pos")),null,12,"line")))
            .then(argument("radius",DoubleArgumentType.doubleArg(2,48)).executes(c->say(c.getSource(),order(actor.get(c),squad.get(c),"guard",Vec3.atBottomCenterOf(BlockPosArgument.getBlockPos(c,"pos")),null,DoubleArgumentType.getDouble(c,"radius"),"line"))))));
        for(String aimed:new String[]{"attack","charge","fire","mount","crew"})soldiers.then(literal(aimed).then(argument("target",EntityArgument.entity()).executes(c->say(c.getSource(),order(actor.get(c),squad.get(c),aimed,null,EntityArgument.getEntity(c,"target"),0,null)))));
        soldiers.then(literal("drive").then(argument("vehicle",EntityArgument.entity()).then(argument("pos",BlockPosArgument.blockPos()).executes(c->say(c.getSource(),order(actor.get(c),squad.get(c),"drive",Vec3.atBottomCenterOf(BlockPosArgument.getBlockPos(c,"pos")),EntityArgument.getEntity(c,"vehicle"),0,null))))));
        soldiers.then(literal("work").then(argument("pos",BlockPosArgument.blockPos()).executes(c->say(c.getSource(),order(actor.get(c),squad.get(c),"work",Vec3.atCenterOf(BlockPosArgument.getBlockPos(c,"pos")),null,0,null)))));
        soldiers.then(literal("bombard").then(argument("piece",EntityArgument.entity()).then(argument("pos",BlockPosArgument.blockPos()).executes(c->{
            LivingEntity commander=actor.get(c);Entity piece=EntityArgument.getEntity(c,"piece");if(!(piece instanceof VehicleEntity gun)||gun.spec().artillery()==null)return say(c.getSource(),fail("That is not a siege piece"));
            // The piece is laid by the one who gives the order, standing at it, as with `siege lay`: nobody aims another's gun from afar.
            var laid=Siege.lay(commander,gun,Vec3.atCenterOf(BlockPosArgument.getBlockPos(c,"pos")));if(!laid.success())return say(c.getSource(),laid);
            var manned=order(commander,squad.get(c),"crew",null,gun,0,null);if(!manned.success())return say(c.getSource(),manned);
            return say(c.getSource(),ok(manned.message()+"; the piece will fire on "+BlockPosArgument.getBlockPos(c,"pos").toShortString()+" while ammunition lasts"));
        }))));
    }
    private static int say(CommandSourceStack source,ActionResult result){
        if(result.success())source.sendSuccess(()->Component.literal(result.message()),false);else source.sendFailure(Component.literal(result.message()));return result.success()?1:0;
    }
}
