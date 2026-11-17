package dev.qube.spawn_9b5fbb9.survival;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.content.Pottery;
import dev.qube.spawn_9b5fbb9.content.Production;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import java.util.Locale;

/** Shared server API: controller type never grants a physiological exemption. */
public final class SurvivalApi {
    public static final String KEY="spawn_survival";
    public static final Identifier CALENDAR=Identifier.fromNamespaceAndPath("spawn_9b5fbb9","calendar");
    public static final net.minecraft.tags.TagKey<EntityType<?>> HUMANOIDS=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath("spawn_9b5fbb9","humanoids"));
    private SurvivalApi(){}
    public static boolean isHumanoid(LivingEntity entity){
        return entity instanceof Player||entity.getPersistentData().getBooleanOr("survivalHumanoid",false)||
            entity.getType().builtInRegistryHolder().is(HUMANOIDS);
    }
    public static boolean exempt(LivingEntity entity){return entity instanceof Player p&&(p.isCreative()||p.isSpectator());}
    public static long calendar(ServerLevel level){
        return level.getServer().overworld().getLevelData().getGameTime()+
            level.getServer().getCommandStorage().get(CALENDAR).getLongOr("sleepOffset",0);
    }
    public static SurvivalState state(LivingEntity entity){
        CompoundTag data=entity.getPersistentData();
        if(data.contains(KEY)){var existing=SurvivalState.decode(data.getByteArray(KEY).orElse(new byte[0]));existing.rules=RealismData.rules();return existing;}
        SurvivalState s=new SurvivalState();
        s.rules=RealismData.rules();
        if(entity.level() instanceof ServerLevel level){
            s.dailyQuotaTicks=Math.round(Config.RECREATION_HOURS.get()*1000);
            s.dayElapsed=Math.floorMod(calendar(level),SurvivalState.DAY);
            s.quotaTicks=Math.max(1,Math.round(Config.RECREATION_HOURS.get()*1000*(SurvivalState.DAY-s.dayElapsed)/SurvivalState.DAY));
        }
        save(entity,s);return s;
    }
    public static void save(LivingEntity entity,SurvivalState state){
        if(!(entity.level() instanceof ServerLevel))throw new IllegalStateException("Server-side survival mutation required");
        entity.getPersistentData().putByteArray(KEY,state.encode());
    }
    public static void enroll(LivingEntity entity){entity.getPersistentData().putBoolean("survivalHumanoid",true);state(entity);}
    public static com.google.gson.JsonObject resources(LivingEntity actor,int radius){return dev.qube.spawn_9b5fbb9.controllers.ResourceActions.discover(actor,radius);}
    public static ActionResult prospect(LivingEntity actor,net.minecraft.core.BlockPos pos){return dev.qube.spawn_9b5fbb9.controllers.ResourceActions.prospect(actor,pos);}
    public static String inspect(LivingEntity entity){
        SurvivalState s=state(entity);
        return String.format(Locale.ROOT,"%s | Recreation %.0f (%.1f/%.1fh) | Hunger %.1f | Thirst %.1f | Temperature %.1f C | Health %.1f/%.1f | Move %.0f%% Manipulation %.0f%% Mental %.0f%% | %s",
            entity.getName().getString(),s.recreation,s.recreationTicks/1000d,s.quotaTicks/1000d,s.hunger,s.thirst,s.temperature,
            entity.getHealth(),entity.getMaxHealth(),s.movement()*100,s.manipulation()*100,s.mental()*100,s.conditions())+" | "+SkillApi.describe(s);
    }
    public record ActionResult(boolean success,String message){}
    private static ActionResult fail(String why){return new ActionResult(false,why);}
    public static ActionResult act(LivingEntity actor,String action,LivingEntity target){
        return act(actor,action,target,net.minecraft.world.InteractionHand.MAIN_HAND);
    }
    public static ActionResult act(LivingEntity actor,String action,LivingEntity target,net.minecraft.world.InteractionHand hand){
        if(!(actor.level() instanceof ServerLevel level)||!actor.isAlive()||!isHumanoid(actor))return fail("Living server humanoid required");
        SurvivalState state=state(actor);ItemStack held=actor.getItemInHand(hand);
        if(!java.util.Set.of("eat","drink","social").contains(action)&&Math.min(state.mental(),state.manipulation())<=.05)return fail("Physiological incapacity prevents this action");
        String id=BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
        if(action.equals("undress")||action.startsWith("undress_"))return undress(actor,state,target,action.length()>8?action.substring(8):"");
        if(action.equals("mend"))return mend(actor,state,target,held,id);
        switch(action){
            case "social" -> {
                if(target==null||target==actor||!target.isAlive()||target.level()!=actor.level()||actor.distanceToSqr(target)>16||
                    !(target instanceof Animal||isHumanoid(target)))return fail("Interact with a living companion within four blocks");
                actor.getPersistentData().putString("survivalCompanion",target.getUUID().toString());
                actor.getPersistentData().putLong("survivalSocialUntil",calendar(level)+200);
                if(isHumanoid(target)){target.getPersistentData().putString("survivalCompanion",actor.getUUID().toString());target.getPersistentData().putLong("survivalSocialUntil",calendar(level)+200);}
                return new ActionResult(true,"Social session started; stay active with your companion");
            }
            case "read","play" -> {
                if(state.mental()<=0)return fail("Mental incapacity prevents this activity");
                if(action.equals("read")&&state.blind())return fail("Blindness prevents reading");
                if(action.equals("read")&&!(held.is(Items.BOOK)||held.is(Items.WRITABLE_BOOK)||held.is(Items.WRITTEN_BOOK)))return fail("Hold a book to read");
                if(action.equals("play")&&!id.equals("wooden_toy"))return fail("Hold a wooden toy");
                actor.getPersistentData().putLong("survivalLeisureUntil",calendar(level)+200);
                actor.getPersistentData().putString("survivalLeisureItem",BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
                return new ActionResult(true,"Recreation active for ten seconds; use again to renew");
            }
            case "drink" -> {
                if(state.thirst>=99)return fail("Already hydrated");
                if(Pottery.waterUnits(held)>0){boolean safe=Pottery.safeWater(held);if(!Pottery.consumeWater(held))return fail("Open the pot before drinking");state.drink(25);contamination(actor,state,safe);}
                else if(held.is(Items.POTION)&&isWater(held)){
                    held.shrink(1);give(actor,new ItemStack(Items.GLASS_BOTTLE));state.drink(25);contamination(actor,state,false);
                }else{
                    var pos=actor.blockPosition();boolean water=false;
                    for(var p:net.minecraft.core.BlockPos.betweenClosed(pos.offset(-2,-1,-2),pos.offset(2,1,2)))
                        if(level.getFluidState(p).is(FluidTags.WATER)&&level.getFluidState(p).isSource()){water=true;break;}
                    if(!water)return fail("Need a filled vessel or source water within reach");
                    state.drink(10);contamination(actor,state,false);
                }
            }
            case "eat" -> {
                var food=held.get(DataComponents.FOOD);if(food==null||state.hunger>=99)return fail("Hold food while hungry");
                if(actor.isUsingItem())return fail("Already using an item");
                actor.startUsingItem(hand);
                return new ActionResult(true,"Eating started; nutrition follows completed consumption");
            }
            case "feed" -> {
                if(target==null||target.level()!=actor.level()||actor.distanceToSqr(target)>9||!isHumanoid(target)||!target.isAlive())return fail("Feed a living humanoid within three blocks");
                if(held.get(DataComponents.FOOD)==null||state(target).hunger>=99)return fail("Hold food for a hungry companion");
                if(target.isUsingItem()||!target.getOffhandItem().isEmpty())return fail("Companion needs a free offhand and must finish the current item");
                target.setItemSlot(EquipmentSlot.OFFHAND,held.split(1));target.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
                return new ActionResult(true,"Feeding companion; nutrition follows completed consumption");
            }
            case "give_water" -> {
                if(target==null||target.level()!=actor.level()||actor.distanceToSqr(target)>9||!isHumanoid(target)||!target.isAlive())return fail("Give water to a living humanoid within three blocks");
                var patient=state(target);if(patient.thirst>=99)return fail("Companion is already hydrated");
                boolean safe=Pottery.safeWater(held);if(!Pottery.consumeWater(held))return fail("Hold an open filled pot");
                hydrate(target,patient,25,safe);save(target,patient);return new ActionResult(true,"Water given to companion");
            }
            case "treat" -> {
                LivingEntity patient=target==null?actor:target;
                if(patient.level()!=actor.level()||actor.distanceToSqr(patient)>9||!isHumanoid(patient))return fail("Patient must be a nearby humanoid");
                SurvivalState illness=state(patient);
                if(state.manipulation()<.15)return fail("Hands cannot apply treatment");
                if(!BuiltInRegistries.ITEM.getKey(held.getItem()).getNamespace().equals("spawn_9b5fbb9")||!illness.treat(id))return fail("Hold a suitable treatment; condition or dose does not permit this item");
                    held.shrink(1);save(patient,illness);SkillApi.award(actor,SkillSet.Skill.MEDICINE,100);return new ActionResult(true,"Treatment applied to "+patient.getName().getString());
            }
            case "clothe" -> {
                LivingEntity wearer=target==null?actor:target;
                if(wearer.level()!=actor.level()||!isHumanoid(wearer)||actor.distanceToSqr(wearer)>9)return fail("Clothe a humanoid within three blocks");
                SurvivalState garments=wearer==actor?state:state(wearer);
                int slot=id.endsWith("_shirt")?0:id.endsWith("_pants")?1:id.endsWith("_jacket")?2:id.endsWith("_shoes")?3:-1;
                if(slot<0||!(id.startsWith("wool_")||id.startsWith("fiber_")))return fail("Hold a shirt, pants, jacket, or shoes");
                long offered=Math.max(0,dev.qube.spawn_9b5fbb9.content.ItemAging.data(held).getLongOr("garmentWear",0));
                if(wearer!=actor&&!consents(wearer,garments)){
                    // Someone awake and able keeps their own coat unless the one held out is the better garment.
                    if(wearer instanceof Player)return fail("The other player must crouch to accept clothing");
                    if(!garments.clothing[slot].isEmpty()&&worth(garments.rules,BuiltInRegistries.ITEM.getKey(held.getItem()).toString(),offered)<=worth(garments.rules,garments.clothing[slot],garments.clothingWear[slot]))
                        return fail("Companion keeps the sounder garment already worn");
                }
                if(state.manipulation()<.15)return fail("Hands cannot manipulate clothing");
                if(!garments.clothing[slot].isEmpty())give(actor,garment(garments,slot));
                garments.clothing[slot]=BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
                var dyed=held.get(DataComponents.DYED_COLOR);garments.clothingColors[slot]=dyed==null?-1:dyed.rgb();
                garments.clothingWear[slot]=offered;held.shrink(1);
                if(wearer!=actor){save(wearer,garments);return new ActionResult(true,"Companion clothed");}
            }
            default -> {return fail("Use social, read, play, drink, eat, feed, treat, clothe, undress, or mend");}
        }
        save(actor,state);syncFood(actor,state);return new ActionResult(true,"Action completed");
    }
    public static final String[] GARMENT_SLOTS={"shirt","pants","jacket","shoes"};
    /** A crouching player agrees; someone unconscious or helpless cannot refuse. */
    private static boolean consents(LivingEntity wearer,SurvivalState body){
        return wearer instanceof Player player&&player.isShiftKeyDown()||Math.min(body.mental(),body.manipulation())<=.05;
    }
    /** Warmth a garment still gives: what a wearer weighs before trading one for another. */
    private static double worth(SimulationRules rules,String garment,long wear){
        double fresh=garment.contains("wool_jacket")?rules.number("woolJacketInsulation"):garment.contains("wool_")?rules.number("woolPieceInsulation"):rules.number("fiberPieceInsulation");
        return fresh*(.5+.5*Math.max(0,1-(double)wear/rules.ticks("garmentLifetimeTicks")));
    }
    private static ActionResult undress(LivingEntity actor,SurvivalState state,LivingEntity target,String part){
        LivingEntity wearer=target==null?actor:target;
        if(wearer.level()!=actor.level()||!isHumanoid(wearer)||actor.distanceToSqr(wearer)>9)return fail("Undress a humanoid within three blocks");
        SurvivalState garments=wearer==actor?state:state(wearer);
        if(wearer!=actor&&!consents(wearer,garments))return fail(wearer instanceof Player?"The other player must crouch to hand over clothing":"Companion will not give up clothing");
        if(state.manipulation()<.15)return fail("Hands cannot manipulate clothing");
        int slot=-1;
        if(part.isEmpty()){for(int outer:new int[]{2,3,0,1})if(!garments.clothing[outer].isEmpty()){slot=outer;break;}}
        else slot=java.util.Arrays.asList(GARMENT_SLOTS).indexOf(part);
        if(slot<0||garments.clothing[slot].isEmpty())return fail(part.isEmpty()?"Nothing is worn":"Name a worn shirt, pants, jacket, or shoes");
        ItemStack taken=garment(garments,slot);garments.clothing[slot]="";garments.clothingWear[slot]=0;garments.clothingColors[slot]=-1;
        // Named before it is handed over: a stack that has gone into an inventory is empty.
        String name=taken.getHoverName().getString();
        give(actor,taken);save(wearer,garments);
        return new ActionResult(true,"Took off "+name);
    }
    private static ActionResult mend(LivingEntity actor,SurvivalState state,LivingEntity target,ItemStack held,String id){
        LivingEntity wearer=target==null?actor:target;
        if(wearer.level()!=actor.level()||!isHumanoid(wearer)||actor.distanceToSqr(wearer)>9)return fail("Mend the clothing of a humanoid within three blocks");
        String cloth=id.equals("yarn")?"wool_":id.equals("cordage")?"fiber_":null;
        if(cloth==null||!BuiltInRegistries.ITEM.getKey(held.getItem()).getNamespace().equals("spawn_9b5fbb9"))return fail("Hold yarn for wool or cordage for fibre clothing");
        if(state.manipulation()<.3||state.blind())return fail("Sewing needs working hands and eyes");
        SurvivalState garments=wearer==actor?state:state(wearer);int worst=-1;
        for(int i=0;i<4;i++)if(garments.clothing[i].contains(cloth)&&(worst<0||garments.clothingWear[i]>garments.clothingWear[worst]))worst=i;
        if(worst<0||!garments.mend(worst))return fail("No garment of that cloth is worn enough to patch");
        held.shrink(1);save(wearer,garments);if(wearer!=actor)save(actor,state);
        SkillApi.award(actor,SkillSet.Skill.WEAVING,60);
        return new ActionResult(true,"Patched the "+GARMENT_SLOTS[worst]+": "+Math.round(garments.condition(worst)*100)+"% sound");
    }
    public static void syncFood(LivingEntity actor,SurvivalState state){
        if(actor instanceof Player p){p.getFoodData().setFoodLevel((int)Math.ceil(state.hunger/5));actor.getPersistentData().putInt("survivalFoodShown",p.getFoodData().getFoodLevel());}
    }
    public static void give(LivingEntity actor,ItemStack stack){
        if(stack.isEmpty())return;
        if(actor instanceof Player p){if(!p.getInventory().add(stack))p.drop(stack,false,net.minecraft.util.Prediction.SERVER_ONLY);}
        else actor.spawnAtLocation((ServerLevel)actor.level(),stack);
    }
    public static ItemStack garment(SurvivalState state,int slot){
        if(state.clothing[slot].isEmpty())return ItemStack.EMPTY;
        ItemStack item=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(state.clothing[slot])));
        var tag=dev.qube.spawn_9b5fbb9.content.ItemAging.data(item);tag.putLong("garmentWear",state.clothingWear[slot]);dev.qube.spawn_9b5fbb9.content.ItemAging.data(item,tag);
        if(state.clothingColors[slot]!=-1)item.set(DataComponents.DYED_COLOR,new net.minecraft.world.item.component.DyedItemColor(state.clothingColors[slot]));
        if(item.isDamageableItem())item.setDamageValue((int)Math.min(item.getMaxDamage()-1,state.clothingWear[slot]*(item.getMaxDamage()-1)/state.rules.ticks("garmentLifetimeTicks")));
        return item;
    }
    public static void hydrate(LivingEntity actor,SurvivalState state,double amount,boolean safe){state.drink(amount);contamination(actor,state,safe);}
    public static ActionResult dye(LivingEntity actor,int slot){
        if(!(actor.level() instanceof ServerLevel)||slot<0||slot>=4)return fail("Invalid garment slot");
        var state=state(actor);var held=actor.getMainHandItem();
        var dye=held.get(DataComponents.DYE);
        if(state.clothing[slot].isEmpty()||dye==null||state.manipulation()<.15)return fail("Wear the garment and hold a natural dye");
        state.clothingColors[slot]=dye.getTextureDiffuseColor();held.shrink(1);save(actor,state);return new ActionResult(true,"Garment dyed");
    }
    public static ActionResult work(LivingEntity actor,String operation,String recipe){
        if(!(actor.level() instanceof ServerLevel level)||!actor.isAlive()||!isHumanoid(actor))return fail("A living server humanoid is required");
        if(operation==null||!java.util.Set.of("produce","fuel","collect","station","cancel").contains(operation))return fail("Unknown operation");
        var process=operation.equals("produce")?Production.RECIPES.get(recipe):null;
        if(operation.equals("produce")&&process==null)return fail("Unknown process");
        dev.qube.spawn_9b5fbb9.content.WorkstationEntity nearest=null;double distance=36;
        for(var pos:net.minecraft.core.BlockPos.betweenClosed(actor.blockPosition().offset(-6,-6,-6),actor.blockPosition().offset(6,6,6))){
            if(!level.hasChunkAt(pos))continue;
            double candidate=actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos));if(candidate>distance)continue;
            if(level.getBlockEntity(pos) instanceof dev.qube.spawn_9b5fbb9.content.WorkstationEntity station&&!station.getBlockState().is(dev.qube.spawn_9b5fbb9.content.SurvivalContent.TROUGH.get())){
                String kind=Production.stationId(station);
                if(process!=null&&!kind.equals(process.station()))continue;
                if(operation.equals("fuel")&&!java.util.Set.of("kiln","forge","bloomery").contains(kind))continue;
                if(operation.equals("collect")&&station.output.isEmpty())continue;
                if(operation.equals("cancel")&&(station.input.isEmpty()||!station.worker.isEmpty()&&!station.worker.equals(actor.getUUID().toString())))continue;
                nearest=station;distance=candidate;
            }
        }
        if(nearest==null)return fail(process==null?"No matching workstation job within six loaded blocks":"Stand within six loaded blocks of a "+process.station());
        String result=switch(operation){
            case "produce"->dev.qube.spawn_9b5fbb9.content.Production.start(actor,nearest,recipe);
            case "fuel"->dev.qube.spawn_9b5fbb9.content.Production.fuel(actor,nearest);
            case "collect"->dev.qube.spawn_9b5fbb9.content.Production.collect(actor,nearest);
            case "cancel"->Production.cancel(actor,nearest);
            case "station"->"Station "+nearest.getBlockPos().toShortString()+" | "+nearest.recipe+" "+nearest.elapsed+"/"+nearest.required+" | Fuel "+nearest.fuel+" | Output "+nearest.output;
            default->"Unknown operation";
        };
        return new ActionResult(result.startsWith("Started")||result.startsWith("Collected")||result.startsWith("Heat reserve")||result.startsWith("Station")||result.startsWith("Removed unfinished"),result);
    }
    public static ActionResult vessel(LivingEntity actor,String operation){
        String result=dev.qube.spawn_9b5fbb9.content.Pottery.action(actor,operation);
        boolean success=result.startsWith("Collected ")||result.startsWith("Hold the open pot")||result.startsWith("Opened pot")||result.startsWith("Pot sealed")||result.startsWith("Food stored")||result.startsWith("Retrieved food");
        return new ActionResult(success,result);
    }
    public static boolean isWater(ItemStack stack){
        var potion=stack.get(DataComponents.POTION_CONTENTS);
        return potion!=null&&potion.potion().map(p->p.is(net.minecraft.world.item.alchemy.Potions.WATER)).orElse(false);
    }
    public static void unsafeFood(LivingEntity actor,SurvivalState state,ItemStack food){
        if(actor.level() instanceof ServerLevel level)dev.qube.spawn_9b5fbb9.content.ItemAging.update(food,level,1);
        if(dev.qube.spawn_9b5fbb9.content.ItemAging.spoiled(food)){state.poisonTicks=Math.max(state.poisonTicks,state.rules.ticks("poisonDurationTicks"));if(actor.getRandom().nextFloat()<state.rules.number("rawFoodInfectionChance"))state.infect(true);return;}
        String id=BuiltInRegistries.ITEM.getKey(food.getItem()).getPath();
        if(java.util.Set.of("beef","porkchop","chicken","mutton","rabbit","cod","salmon","rotten_flesh","poisonous_potato","spoiled_food").contains(id)&&actor.getRandom().nextFloat()<state.rules.number("rawFoodPoisonChance")){
            state.poisonTicks=Math.max(state.poisonTicks,state.rules.ticks("poisonDurationTicks"));if(actor.getRandom().nextFloat()<state.rules.number("rawFoodInfectionChance"))state.infect(true);
        }
    }
    private static void contamination(LivingEntity actor,SurvivalState state,boolean safe){
        if(!safe&&actor.getRandom().nextFloat()<state.rules.number("waterPoisonChance")){state.poisonTicks=Math.max(state.poisonTicks,state.rules.ticks("poisonDurationTicks"));if(actor.getRandom().nextFloat()<state.rules.number("waterInfectionChance"))state.infect(true);}
    }
}
