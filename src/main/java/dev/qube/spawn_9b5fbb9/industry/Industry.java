package dev.qube.spawn_9b5fbb9.industry;

import java.util.*;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.qube.spawn_9b5fbb9.content.ContentDefinitions;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi.ActionResult;
import dev.qube.spawn_9b5fbb9.survival.SkillApi;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import static net.minecraft.commands.Commands.*;

/** Multi-input industrial production. A works holds a bulk ledger of delivered materials and runs its
 * selected recipe batch after batch while inputs, heat or power, and qualified labour remain. Quantities
 * are deliberately industrial: a server needs thousands of chips and each chip dozens of bond wires. */
public final class Industry {
    private Industry(){}
    private static final String M="spawn_9b5fbb9:",V="minecraft:";
    /** @param output an item id, or {@code vehicle:<kind>} for something that leaves the yard on its own wheels. */
    public record Recipe(String station,Map<String,Integer> inputs,String output,int amount,Map<String,Integer> byproducts,long ticks,
                         Skill skill,int minimumSkill,boolean attended,boolean heated,boolean powered,int maxWorkers,double practice){
        public boolean burnsFuel(){return heated||powered;}
        public boolean vehicle(){return output.startsWith("vehicle:");}
    }
    private static final Map<String,Recipe> DEFAULTS=new LinkedHashMap<>();
    public static volatile Map<String,Recipe> RECIPES;
    private static Map<String,Integer> bill(Object... pairs){
        var map=new LinkedHashMap<String,Integer>();
        for(int i=0;i<pairs.length;i+=2){String id=(String)pairs[i];map.put(id.startsWith("#")||id.indexOf(':')>=0?id:M+id,(Integer)pairs[i+1]);}
        return Collections.unmodifiableMap(map);
    }
    private static String out(String id){return id.indexOf(':')>=0?id:M+id;}
    /** Attended manual or machine work. */
    private static void make(String key,String station,String output,int amount,long ticks,Skill skill,int minimum,Map<String,Integer> inputs){
        boolean heated=station.equals("foundry")||station.equals("chemical_works"),powered=station.equals("machine_shop")||station.equals("electronics_works");
        int workers=station.equals("assembly_yard")?8:station.equals("workshop")?2:station.equals("powder_mill")?2:3;
        DEFAULTS.put(key,new Recipe(station,inputs,out(output),amount,Map.of(),ticks,skill,minimum,true,heated,powered,workers,25));
    }
    /** Unattended chemistry or biology: only time (and heat, where stated) does the work. */
    private static void passive(String key,String station,String output,int amount,long ticks,boolean heated,Map<String,Integer> inputs){
        DEFAULTS.put(key,new Recipe(station,inputs,out(output),amount,Map.of(),ticks,null,0,false,heated,false,0,0));
    }
    static {
        // --- Joinery, harness and small arms: a wainwright's and gunstocker's bench.
        make("wooden_wheel","workshop","wooden_wheel",1,1600,Skill.CONSTRUCTION,4,bill("#minecraft:planks",6,"metal_rivet",4));
        make("vehicle_frame","workshop","vehicle_frame",1,2400,Skill.CONSTRUCTION,6,bill("#minecraft:planks",16,"#minecraft:logs",2,"metal_rivet",8));
        make("harness","workshop","harness",1,1600,Skill.WEAVING,4,bill(V+"leather",4,"cordage",4,"metal_rivet",2));
        make("saddle","workshop",V+"saddle",1,2000,Skill.WEAVING,6,bill(V+"leather",5,"cordage",2,"metal_rivet",2));
        make("sleeper","workshop","sleeper",2,400,Skill.CONSTRUCTION,2,bill("#minecraft:logs",1));
        make("gun_carriage","workshop","gun_carriage",1,3000,Skill.CONSTRUCTION,8,bill("#minecraft:planks",12,V+"iron_ingot",4,"wooden_wheel",2));
        make("musket_stock","workshop","musket_stock",1,1400,Skill.CONSTRUCTION,4,bill("#minecraft:planks",4,"cordage",1));
        make("musket","workshop","musket",1,2000,Skill.SMITHING,12,bill("musket_barrel",1,"musket_lock",1,"musket_stock",1,"metal_rivet",2));
        make("paper_cartridge","workshop","paper_cartridge",8,400,Skill.ENGINEERING,0,bill("musket_ball",8,V+"gunpowder",8,V+"paper",2));
        make("powder_charge","workshop","powder_charge",1,300,Skill.ENGINEERING,0,bill(V+"gunpowder",6,"fiber_cloth",1));
        make("powder_mill","workshop","powder_mill",1,4000,Skill.CONSTRUCTION,8,bill(V+"stone",4,"#minecraft:planks",16,"bronze_ingot",4,"cordage",8));
        // --- Nitre beds mature slowly; black powder is the historical 75:15:10 by weight.
        passive("niter_earth","niter_bed","niter_earth",4,120000,false,bill("spoiled_food",8,"wood_ash",2,V+"wheat",2));
        make("gunpowder","powder_mill",V+"gunpowder",20,2400,Skill.ENGINEERING,4,bill("saltpeter",15,V+"charcoal",3,"sulfur",2));
        // --- Chemical works: leaching, roasting, coking, fermentation, distillation and acids.
        make("saltpeter","chemical_works","saltpeter",2,2000,Skill.ENGINEERING,2,bill("niter_earth",4,"wood_ash",1));
        make("sulfur","chemical_works","sulfur",1,1500,Skill.ENGINEERING,2,bill("pyrite",2));
        DEFAULTS.put("coke",new Recipe("chemical_works",bill(V+"coal",4),M+"coke",3,bill("coal_tar",1),3000,Skill.ENGINEERING,4,true,true,false,3,25));
        passive("ferment_maize","chemical_works","fermented_mash",4,48000,false,bill("maize",8));
        passive("ferment_potato","chemical_works","fermented_mash",4,48000,false,bill(V+"potato",8));
        passive("ferment_wheat","chemical_works","fermented_mash",4,48000,false,bill(V+"wheat",8));
        make("distill_alcohol","chemical_works","fuel_alcohol",2,2000,Skill.ENGINEERING,4,bill("fermented_mash",4));
        make("sulfuric_acid","chemical_works","sulfuric_acid",2,2400,Skill.ENGINEERING,8,bill("sulfur",2,"saltpeter",1));
        make("phenolic_resin","chemical_works","phenolic_resin",2,2400,Skill.ENGINEERING,10,bill("coal_tar",2,"fuel_alcohol",1));
        make("photoresist","chemical_works","photoresist",2,2400,Skill.ENGINEERING,12,bill("coal_tar",1,"fuel_alcohol",1));
        make("polysilicon","chemical_works","polysilicon",1,4000,Skill.ENGINEERING,14,bill("metallurgical_silicon",2,"sulfuric_acid",1));
        // --- Foundry: steel, ordnance castings and furnace silicon.
        make("crucible_steel","foundry","steel_ingot",3,4000,Skill.SMITHING,14,bill(V+"iron_ingot",4,V+"charcoal",2));
        make("steel","foundry","steel_ingot",4,3000,Skill.SMITHING,14,bill(V+"iron_ingot",4,"coke",1,"quicklime",1));
        make("cannon_barrel","foundry","cannon_barrel",1,12000,Skill.SMITHING,14,bill("bronze_ingot",48));
        make("bombard_barrel","foundry","bombard_barrel",1,16000,Skill.SMITHING,12,bill(V+"iron_ingot",96));
        make("metallurgical_silicon","foundry","metallurgical_silicon",1,3000,Skill.ENGINEERING,12,bill(V+"sand",4,"coke",2));
        // --- Machine shop: rolling, turning and wire drawing under steam power.
        make("steel_plate","machine_shop","steel_plate",1,400,Skill.ENGINEERING,6,bill("steel_ingot",1));
        make("machined_parts","machine_shop","machine_parts",2,800,Skill.ENGINEERING,8,bill("steel_ingot",2));
        make("steel_rail","machine_shop","steel_rail",1,600,Skill.ENGINEERING,6,bill("steel_ingot",2));
        make("mill_copper_wire","machine_shop","copper_wire",128,800,Skill.ENGINEERING,6,bill(V+"copper_ingot",8));
        make("fine_wire","machine_shop","fine_wire",64,600,Skill.ENGINEERING,10,bill("copper_wire",16));
        make("explosive_shell","machine_shop","explosive_shell",1,1200,Skill.ENGINEERING,12,bill("steel_ingot",2,V+"gunpowder",4));
        // --- Electronics works: crystal growth, lithography, packaging and final assembly.
        make("silicon_boule","electronics_works","silicon_boule",1,12000,Skill.ENGINEERING,14,bill("polysilicon",8));
        make("silicon_wafer","electronics_works","silicon_wafer",24,2400,Skill.ENGINEERING,14,bill("silicon_boule",1));
        make("patterned_wafer","electronics_works","patterned_wafer",1,4800,Skill.ENGINEERING,16,bill("silicon_wafer",1,"photoresist",2,"sulfuric_acid",1));
        make("silicon_die","electronics_works","silicon_die",40,1200,Skill.ENGINEERING,14,bill("patterned_wafer",1));
        make("chip","electronics_works","chip",16,1600,Skill.ENGINEERING,14,bill("silicon_die",16,"fine_wire",640,"chip_package",16));
        make("circuit_board","electronics_works","circuit_board",2,2400,Skill.ENGINEERING,12,bill("fiber_cloth",1,"phenolic_resin",1,V+"copper_ingot",1,"sulfuric_acid",1));
        make("computer","electronics_works","computer",1,48000,Skill.ENGINEERING,18,bill("chip",1200,"circuit_board",40,"copper_wire",6000,"solder",2000,"steel_plate",24,"transformer",2,V+"glass",4));
        make("server","electronics_works","server",1,96000,Skill.ENGINEERING,20,bill("chip",4800,"circuit_board",160,"copper_wire",30000,"solder",8000,"steel_plate",60,"transformer",6,"machine_parts",12));
        // --- Assembly yard: many hands on one large job. Machines, vehicles, ordnance and whole works.
        make("boiler","assembly_yard","boiler",1,6000,Skill.ENGINEERING,8,bill("steel_plate",12,"metal_rivet",48));
        make("steam_engine","assembly_yard","steam_engine",1,12000,Skill.ENGINEERING,10,bill("boiler",1,"machine_parts",16,"steel_plate",6,V+"copper_ingot",4));
        make("dynamo","assembly_yard","dynamo",1,8000,Skill.ENGINEERING,12,bill("copper_wire",400,"steel_plate",4,"machine_parts",4,V+"iron_ingot",8));
        make("transformer","assembly_yard","transformer",1,4000,Skill.ENGINEERING,12,bill("steel_plate",6,"copper_wire",800));
        make("combustion_engine","assembly_yard","combustion_engine",1,16000,Skill.ENGINEERING,14,bill("steel_ingot",12,"machine_parts",24,"copper_wire",60,"lead_ingot",4,"sulfuric_acid",2));
        make("foundry","assembly_yard","foundry",1,12000,Skill.CONSTRUCTION,10,bill(V+"brick",64,V+"clay_ball",16,V+"iron_ingot",16,"quicklime",8));
        make("chemical_works","assembly_yard","chemical_works",1,12000,Skill.CONSTRUCTION,10,bill(V+"brick",32,V+"copper_ingot",24,"lead_ingot",16,V+"glass",8));
        make("machine_shop","assembly_yard","machine_shop",1,24000,Skill.ENGINEERING,10,bill("steam_engine",1,"machine_parts",32,"steel_ingot",32,V+"brick",32,V+"leather",8));
        make("electronics_works","assembly_yard","electronics_works",1,48000,Skill.ENGINEERING,14,bill("steam_engine",1,"dynamo",2,"machine_parts",64,"steel_plate",32,V+"glass",32,"copper_wire",2000,V+"brick",64));
        make("cart","assembly_yard","vehicle:cart",1,3000,Skill.CONSTRUCTION,6,bill("vehicle_frame",1,"wooden_wheel",2,"harness",1));
        make("wagon","assembly_yard","vehicle:wagon",1,6000,Skill.CONSTRUCTION,8,bill("vehicle_frame",2,"wooden_wheel",4,"harness",2,"#minecraft:planks",16));
        make("chariot","assembly_yard","vehicle:chariot",1,4000,Skill.CONSTRUCTION,8,bill("vehicle_frame",1,"wooden_wheel",2,"harness",2,V+"leather",4,"cordage",8));
        make("trebuchet","assembly_yard","vehicle:trebuchet",1,24000,Skill.CONSTRUCTION,10,bill("#minecraft:logs",64,"#minecraft:planks",48,"cordage",64,V+"iron_ingot",8,V+"cobblestone",128));
        make("cannon","assembly_yard","vehicle:cannon",1,4000,Skill.ENGINEERING,6,bill("cannon_barrel",1,"gun_carriage",1,"metal_rivet",16));
        make("bombard","assembly_yard","vehicle:bombard",1,6000,Skill.ENGINEERING,6,bill("bombard_barrel",1,"#minecraft:logs",24,V+"iron_ingot",8,"cordage",16));
        make("field_gun","assembly_yard","vehicle:field_gun",1,16000,Skill.ENGINEERING,14,bill("steel_ingot",60,"machine_parts",24,"wooden_wheel",2,"steel_plate",8));
        make("motor_truck","assembly_yard","vehicle:motor_truck",1,20000,Skill.ENGINEERING,14,bill("combustion_engine",1,"steel_plate",20,"machine_parts",16,"wooden_wheel",4,"vehicle_frame",2,V+"glass",2));
        make("locomotive","assembly_yard","vehicle:locomotive",1,48000,Skill.ENGINEERING,16,bill("boiler",1,"steam_engine",2,"steel_plate",40,"machine_parts",40,"steel_ingot",80,V+"copper_ingot",16));
        make("rail_wagon","assembly_yard","vehicle:rail_wagon",1,12000,Skill.ENGINEERING,10,bill("steel_ingot",24,"machine_parts",6,"#minecraft:planks",32,"steel_plate",6));
        make("rail_carriage","assembly_yard","vehicle:rail_carriage",1,14000,Skill.ENGINEERING,10,bill("steel_ingot",24,"machine_parts",6,"#minecraft:planks",48,V+"glass",8,"wool_cloth",8));
        RECIPES=Collections.unmodifiableMap(new LinkedHashMap<>(DEFAULTS));
    }
    /** Data packs may retune time, yield, skill and bills of material, or add recipes; stations are fixed. */
    public static void reloadDefinitions(JsonObject definitions){
        var replacement=new LinkedHashMap<>(DEFAULTS);
        for(var entry:definitions.entrySet()){
            String key=entry.getKey();var object=ContentDefinitions.object(entry.getValue(),key);
            ContentDefinitions.fields(object,"station","inputs","output","amount","ticks","skill","minimumSkill","attended","heated","powered","maxWorkers","practice");
            Recipe base=DEFAULTS.get(key);
            if(base==null){if(!object.has("station")||!object.has("inputs")||!object.has("output"))throw new IllegalArgumentException("New industrial recipes need station, inputs and output: "+key);base=new Recipe("",Map.of(),"",1,Map.of(),1000,null,0,true,false,false,1,0);}
            String station=ContentDefinitions.string(object,"station",base.station);if(!IndustrialContent.STATIONS.contains(station))throw new IllegalArgumentException("Unknown works: "+station);
            Map<String,Integer> inputs=base.inputs;
            if(object.has("inputs")){
                var parsed=new LinkedHashMap<String,Integer>();
                for(var input:ContentDefinitions.object(object.get("inputs"),"inputs").entrySet()){
                    String id=input.getKey();if(id.startsWith("#"))ContentDefinitions.id(id.substring(1));else ContentDefinitions.item(id);
                    var holder=new JsonObject();holder.add("count",input.getValue());parsed.put(id,(int)ContentDefinitions.integer(holder,"count",1,1,1000000));
                }
                if(parsed.isEmpty())throw new IllegalArgumentException("An industrial recipe consumes real inputs: "+key);inputs=Collections.unmodifiableMap(parsed);
            }
            String output=ContentDefinitions.string(object,"output",base.output);
            if(output.startsWith("vehicle:")){if(!dev.qube.spawn_9b5fbb9.transport.Vehicles.SPECS.containsKey(output.substring(8)))throw new IllegalArgumentException("Unknown vehicle: "+output);}
            else if(dev.qube.spawn_9b5fbb9.world.NaturalWorldRules.prohibited(new ItemStack(ContentDefinitions.item(output))))throw new IllegalArgumentException("Output is prohibited: "+output);
            String skillName=ContentDefinitions.string(object,"skill",base.skill==null?"":base.skill.name());Skill skill=null;
            if(!skillName.isBlank()&&!skillName.equalsIgnoreCase("none")){try{skill=Skill.valueOf(skillName.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException invalid){throw new IllegalArgumentException("Unknown skill: "+skillName);}}
            boolean attended=ContentDefinitions.bool(object,"attended",base.attended);double practice=ContentDefinitions.number(object,"practice",base.practice,0,2500);
            if(practice>0&&(!attended||skill==null))throw new IllegalArgumentException("Practice requires attended skilled work: "+key);
            replacement.put(key,new Recipe(station,inputs,output,(int)ContentDefinitions.integer(object,"amount",base.amount,1,4096),base.byproducts,ContentDefinitions.integer(object,"ticks",base.ticks,1,4000000),skill,
                (int)ContentDefinitions.integer(object,"minimumSkill",base.minimumSkill,0,20),attended,ContentDefinitions.bool(object,"heated",base.heated),ContentDefinitions.bool(object,"powered",base.powered),
                (int)ContentDefinitions.integer(object,"maxWorkers",Math.max(attended?1:0,base.maxWorkers),0,64),practice));
        }
        RECIPES=Collections.unmodifiableMap(replacement);
    }
    public static void init(){NeoForge.EVENT_BUS.addListener(Industry::interact);NeoForge.EVENT_BUS.addListener(Industry::commands);}

    // ---- Controller-neutral operations ---------------------------------------------------------------
    private static ActionResult ok(String text){return new ActionResult(true,text);}
    private static ActionResult fail(String text){return new ActionResult(false,text);}
    private static String reach(LivingEntity actor,WorksEntity works){
        if(!(actor.level() instanceof ServerLevel level)||works.getLevel()!=level||!actor.isAlive()||!SurvivalApi.isHumanoid(actor))return "A living humanoid in the same world is required";
        if(actor.distanceToSqr(Vec3.atCenterOf(works.getBlockPos()))>36)return "Stand within six blocks of the works";
        if(SurvivalApi.state(actor).manipulation()<=.05)return "Your body cannot handle materials";
        return "";
    }
    public static WorksEntity nearest(LivingEntity actor){
        if(!(actor.level() instanceof ServerLevel level))return null;WorksEntity found=null;double best=36.01;
        for(var pos:BlockPos.betweenClosed(actor.blockPosition().offset(-6,-4,-6),actor.blockPosition().offset(6,4,6))){
            if(!level.hasChunkAt(pos))continue;double distance=actor.distanceToSqr(Vec3.atCenterOf(pos));
            if(distance<best&&level.getBlockEntity(pos) instanceof WorksEntity works){found=works;best=distance;}
        }
        return found;
    }
    public static int fuelValue(ItemStack stack){
        return stack.is(Items.CHARCOAL)||stack.is(Items.COAL)?8000:stack.is(SurvivalContent.item("coke"))?12000:stack.is(ItemTags.LOGS)?4000:stack.is(Items.STICK)?1000:0;
    }
    public static ActionResult select(LivingEntity actor,WorksEntity works,String recipeId){
        String invalid=reach(actor,works);if(!invalid.isEmpty())return fail(invalid);
        Recipe recipe=RECIPES.get(recipeId);if(recipe==null)return fail("Unknown industrial recipe");
        if(!recipe.station.equals(works.station()))return fail("That recipe belongs to a "+recipe.station.replace('_',' '));
        if(works.active)return fail("A batch is in progress; let it finish before retooling");
        works.recipe=recipeId;works.progress=0;works.setChanged();
        return ok("Set to "+recipeId+": needs "+describe(recipe.inputs)+" per batch"+(recipe.burnsFuel()?", burns fuel":"")+(recipe.attended?", attended by "+(recipe.skill==null?"any worker":recipe.skill.label().toLowerCase(Locale.ROOT)+" "+recipe.minimumSkill+"+"):", unattended")+".");
    }
    private static String describe(Map<String,Integer> bill){var out=new StringJoiner(", ");bill.forEach((id,count)->out.add(count+" "+(id.startsWith("#")?"any "+id.substring(id.indexOf(':')+1):id.substring(id.indexOf(':')+1))));return out.toString();}
    private static String ingredientFor(Recipe recipe,String itemId){for(String ingredient:recipe.inputs.keySet())if(Ledger.matches(ingredient,itemId))return ingredient;return null;}
    /** Moves the held stack into the works. Only materials its selected recipe actually consumes are accepted. */
    public static ActionResult deposit(LivingEntity actor,WorksEntity works,InteractionHand hand,boolean everything){
        String invalid=reach(actor,works);if(!invalid.isEmpty())return fail(invalid);
        ItemStack held=actor.getItemInHand(hand);if(held.isEmpty())return fail("Hold the material to deliver");
        if(held.isDamaged())return fail("Worn equipment is not raw material");
        String id=Ledger.id(held);Recipe recipe=RECIPES.get(works.recipe);
        if(recipe==null||!recipe.station.equals(works.station())){
            // An unconfigured works adopts the first recipe that uses the delivered material.
            for(var entry:RECIPES.entrySet())if(entry.getValue().station.equals(works.station())&&ingredientFor(entry.getValue(),id)!=null){works.recipe=entry.getKey();recipe=entry.getValue();break;}
            if(recipe==null||!recipe.station.equals(works.station()))return fail("Nothing made here uses that material");
        }
        if(ingredientFor(recipe,id)==null)return fail("The selected recipe ("+works.recipe+") does not use that; it needs "+describe(recipe.inputs));
        long moved=held.getCount();Ledger.add(works.stock,id,moved);held.setCount(0);
        if(everything&&actor instanceof Player player)for(int slot=0;slot<player.getInventory().getContainerSize();slot++){
            ItemStack other=player.getInventory().getItem(slot);if(!other.isEmpty()&&!other.isDamaged()&&Ledger.id(other).equals(id)){moved+=other.getCount();Ledger.add(works.stock,id,other.getCount());other.setCount(0);}
        }
        works.setChanged();return ok("Delivered "+moved+"; "+progressLine(works,recipe));
    }
    /** Moves every unit a works can use out of a cargo ledger (a wagon, truck or rail wagon alongside). */
    public static long depositFrom(WorksEntity works,Map<String,Long> cargo){
        Recipe recipe=RECIPES.get(works.recipe);if(recipe==null)return 0;long moved=0;
        for(var iterator=cargo.entrySet().iterator();iterator.hasNext();){
            var entry=iterator.next();boolean fuel=recipe.burnsFuel()&&fuelValue(new ItemStack(Ledger.item(entry.getKey())))>0&&ingredientFor(recipe,entry.getKey())==null;
            if(fuel){long units=Math.min(entry.getValue(),Math.max(0,(WorksEntity.FUEL_LIMIT-works.fuel)/fuelValue(new ItemStack(Ledger.item(entry.getKey())))));if(units<=0)continue;works.fuel+=units*fuelValue(new ItemStack(Ledger.item(entry.getKey())));moved+=units;if(units==entry.getValue())iterator.remove();else entry.setValue(entry.getValue()-units);continue;}
            if(ingredientFor(recipe,entry.getKey())==null)continue;
            Ledger.add(works.stock,entry.getKey(),entry.getValue());moved+=entry.getValue();iterator.remove();
        }
        if(moved>0)works.setChanged();return moved;
    }
    public static ActionResult fuel(LivingEntity actor,WorksEntity works,InteractionHand hand){
        String invalid=reach(actor,works);if(!invalid.isEmpty())return fail(invalid);
        if(!works.usesFuel())return fail("This works burns no fuel");
        ItemStack held=actor.getItemInHand(hand);int value=fuelValue(held);if(value==0)return fail("Hold coal, coke, charcoal, logs or sticks");
        if(works.fuel+value>WorksEntity.FUEL_LIMIT)return fail("The firebox is full");
        int units=(int)Math.min(held.getCount(),(WorksEntity.FUEL_LIMIT-works.fuel)/value);held.shrink(units);works.fuel+=(long)units*value;works.setChanged();
        return ok("Fuel reserve: "+works.fuel+" ticks");
    }
    public static ActionResult collect(LivingEntity actor,WorksEntity works){
        String invalid=reach(actor,works);if(!invalid.isEmpty())return fail(invalid);
        if(works.pendingVehicle()){String placed=works.releaseVehicles();if(!placed.isEmpty())return ok(placed);}
        if(works.products.isEmpty())return fail("No finished goods to collect");
        int stacks=0;long units=0;
        for(String id:new ArrayList<>(works.products.keySet())){
            if(id.startsWith("vehicle:"))continue;
            while(works.products.containsKey(id)&&stacks<(actor instanceof Player?27:4)){ItemStack stack=Ledger.withdrawStack(works.products,id);if(stack.isEmpty())break;units+=stack.getCount();stacks++;SurvivalApi.give(actor,stack);}
        }
        works.setChanged();return units>0?ok("Collected "+units+" finished units; "+Ledger.describe(works.products)+" remain"):fail("Finished vehicles wait for clear ground or track beside the yard");
    }
    public static ActionResult withdraw(LivingEntity actor,WorksEntity works){
        String invalid=reach(actor,works);if(!invalid.isEmpty())return fail(invalid);
        if(works.stock.isEmpty())return fail("No undelivered stock to recover");
        int stacks=0;long units=0;
        for(String id:new ArrayList<>(works.stock.keySet()))while(works.stock.containsKey(id)&&stacks<(actor instanceof Player?27:4)){ItemStack stack=Ledger.withdrawStack(works.stock,id);if(stack.isEmpty())break;units+=stack.getCount();stacks++;SurvivalApi.give(actor,stack);}
        works.setChanged();return ok("Recovered "+units+" units of unused stock");
    }
    public static ActionResult operate(LivingEntity actor,WorksEntity works){
        String invalid=reach(actor,works);if(!invalid.isEmpty())return fail(invalid);
        Recipe recipe=RECIPES.get(works.recipe);if(recipe==null)return fail("Select a recipe first");
        if(!recipe.attended)return fail("This process runs unattended");
        if(recipe.skill!=null&&SkillApi.level(actor,recipe.skill)<recipe.minimumSkill)return fail("Requires "+recipe.skill.label().toLowerCase(Locale.ROOT)+" level "+recipe.minimumSkill);
        String id=actor.getUUID().toString();
        if(!works.workers.contains(id)&&works.workers.size()>=recipe.maxWorkers)return fail("Only "+recipe.maxWorkers+" can work this job at once");
        leave(actor);works.workers.add(id);actor.getPersistentData().putLong("survivalWorksPos",works.getBlockPos().asLong());works.setChanged();
        return ok("Working "+works.recipe+" ("+works.workers.size()+"/"+recipe.maxWorkers+" hands). Stay within eight blocks; "+progressLine(works,recipe));
    }
    /** An actor works one job at a time: joining a works releases the previous one. */
    public static ActionResult leave(LivingEntity actor){
        var data=actor.getPersistentData();if(!data.contains("survivalWorksPos"))return fail("Not assigned to a works");
        BlockPos pos=BlockPos.of(data.getLongOr("survivalWorksPos",0));data.remove("survivalWorksPos");
        if(actor.level() instanceof ServerLevel level&&level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof WorksEntity works){works.workers.remove(actor.getUUID().toString());works.setChanged();}
        return ok("Left the works");
    }
    private static String progressLine(WorksEntity works,Recipe recipe){
        var missing=new StringJoiner(", ");
        recipe.inputs.forEach((ingredient,count)->{long have=Ledger.count(works.stock,ingredient);if(have<count)missing.add((count-have)+" "+ingredient.substring(ingredient.indexOf(':')+1));});
        String need=missing.length()==0?"inputs ready":"still needs "+missing;
        return works.active?"batch "+(long)works.progress+"/"+recipe.ticks+" work ticks":need;
    }
    public static String status(WorksEntity works){
        Recipe recipe=RECIPES.get(works.recipe);
        return works.station().replace('_',' ')+" | "+(recipe==null?"no recipe":works.recipe+" | "+progressLine(works,recipe))+" | stock: "+Ledger.describe(works.stock)+" | finished: "+Ledger.describe(works.products)
            +(works.usesFuel()?" | fuel "+works.fuel:"")+" | workers "+works.workers.size()+" | batches "+works.batches;
    }
    public static JsonObject info(String recipeId){
        var json=new JsonObject();Recipe recipe=RECIPES.get(recipeId);if(recipe==null){json.addProperty("error","Unknown industrial recipe");return json;}
        json.addProperty("recipe",recipeId);json.addProperty("station",recipe.station);json.addProperty("output",recipe.output);json.addProperty("outputCount",recipe.amount);json.addProperty("workTicks",recipe.ticks);
        var inputs=new JsonObject();recipe.inputs.forEach(inputs::addProperty);json.add("inputs",inputs);
        var extra=new JsonObject();recipe.byproducts.forEach(extra::addProperty);json.add("byproducts",extra);
        json.addProperty("skill",recipe.skill==null?"none":recipe.skill.name().toLowerCase(Locale.ROOT));json.addProperty("minimumSkill",recipe.minimumSkill);
        json.addProperty("attended",recipe.attended);json.addProperty("heated",recipe.heated);json.addProperty("powered",recipe.powered);json.addProperty("maxWorkers",recipe.maxWorkers);
        return json;
    }
    /** Total raw bill for one unit of a product, expanded through every intermediate recipe. */
    public static Map<String,Long> expand(String itemId,long quantity){
        var totals=new TreeMap<String,Long>();expand(itemId,quantity,totals,0);return totals;
    }
    private static void expand(String itemId,long quantity,Map<String,Long> totals,int depth){
        Recipe source=null;
        // Prefer the mechanised route where one exists; hand routes are bootstraps.
        for(var recipe:RECIPES.values())if(recipe.output.equals(itemId)&&(source==null||recipe.powered&&!source.powered))source=recipe;
        if(depth>16){totals.merge(itemId,quantity,Long::sum);return;}
        if(source==null){
            // Hand, kiln, bloomery, forge, anvil and loom processes carry the bill down to what is mined, grown or gathered.
            dev.qube.spawn_9b5fbb9.content.Production.Recipe process=null;
            for(var candidate:dev.qube.spawn_9b5fbb9.content.Production.RECIPES.values())if(candidate.output().equals(itemId)){process=candidate;break;}
            if(process==null){totals.merge(itemId,quantity,Long::sum);return;}
            long runs=(quantity+process.amount()-1)/process.amount();totals.merge("@"+itemId,quantity,Long::sum);
            expand(process.input(),runs*process.count(),totals,depth+1);
            if(!process.second().isEmpty()&&!process.second().startsWith("@"))expand(process.second(),runs*process.secondCount(),totals,depth+1);
            return;
        }
        long batches=(quantity+source.amount-1)/source.amount;totals.merge("@"+itemId,quantity,Long::sum);
        for(var input:source.inputs.entrySet())expand(input.getKey(),batches*input.getValue(),totals,depth+1);
    }

    // ---- Player interaction --------------------------------------------------------------------------
    private static void interact(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(level.getBlockEntity(event.getPos()) instanceof WorksEntity works))return;
        event.setCanceled(true);if(event.getHand()!=InteractionHand.MAIN_HAND)return;
        Player player=event.getEntity();ItemStack held=event.getItemStack();ActionResult result;
        Recipe recipe=RECIPES.get(works.recipe);
        if(held.isEmpty()&&player.isShiftKeyDown())result=works.stock.isEmpty()?leave(player):withdraw(player,works);
        else if(held.isEmpty())result=!works.products.isEmpty()?collect(player,works):recipe!=null&&recipe.attended&&!works.workers.contains(player.getUUID().toString())?operate(player,works):ok(status(works));
        else if(works.usesFuel()&&fuelValue(held)>0&&(recipe==null||ingredientFor(recipe,Ledger.id(held))==null))result=fuel(player,works,InteractionHand.MAIN_HAND);
        else result=deposit(player,works,InteractionHand.MAIN_HAND,player.isShiftKeyDown());
        player.sendOverlayMessage(Component.literal(result.message()));
    }

    // ---- Commands ------------------------------------------------------------------------------------
    @FunctionalInterface private interface ActorLookup{LivingEntity get(CommandContext<CommandSourceStack> context)throws CommandSyntaxException;}
    private static void commands(RegisterCommandsEvent event){
        var own=literal("works");tree(own,c->c.getSource().getPlayerOrException());
        event.getDispatcher().register(literal("survival").then(own));
        var controlled=literal("works");tree(controlled,c->living(EntityArgument.getEntity(c,"actor")));
        event.getDispatcher().register(literal("survival").then(literal("control").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(argument("actor",EntityArgument.entity()).then(controlled))));
    }
    private static LivingEntity living(Entity entity)throws CommandSyntaxException{
        if(entity instanceof LivingEntity living&&SurvivalApi.isHumanoid(living))return living;
        throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Living humanoid required")).create();
    }
    private static void tree(ArgumentBuilder<CommandSourceStack,?> node,ActorLookup actor){
        node.then(literal("recipes").executes(c->{WorksEntity works=nearest(actor.get(c));var names=new ArrayList<String>();RECIPES.forEach((id,r)->{if(works==null||r.station.equals(works.station()))names.add(id);});return say(c.getSource(),ok(String.join(", ",names)));}));
        node.then(literal("info").then(argument("recipe",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(RECIPES.keySet(),b)).executes(c->say(c.getSource(),ok(info(StringArgumentType.getString(c,"recipe")).toString())))));
        node.then(literal("bill").then(argument("recipe",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(RECIPES.keySet(),b)).executes(c->{
            Recipe recipe=RECIPES.get(StringArgumentType.getString(c,"recipe"));if(recipe==null)return say(c.getSource(),fail("Unknown industrial recipe"));
            var raw=new StringJoiner(", ");var made=new StringJoiner(", ");var totals=new TreeMap<String,Long>();
            recipe.inputs.forEach((id,count)->expand(id,count,totals,0));
            totals.forEach((id,count)->{if(id.startsWith("@"))made.add(count+" "+id.substring(id.indexOf(':')+1));else raw.add(count+" "+id.substring(id.indexOf(':')+1));});
            return say(c.getSource(),ok("One "+recipe.output+" — raw materials: "+raw+" | intermediates made on the way: "+made));
        })));
        node.then(literal("set").then(argument("recipe",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(RECIPES.keySet(),b)).executes(c->at(c,actor,(a,w)->select(a,w,StringArgumentType.getString(c,"recipe"))))));
        node.then(literal("deposit").executes(c->at(c,actor,(a,w)->deposit(a,w,InteractionHand.MAIN_HAND,true))));
        node.then(literal("fuel").executes(c->at(c,actor,(a,w)->fuel(a,w,InteractionHand.MAIN_HAND))));
        node.then(literal("collect").executes(c->at(c,actor,Industry::collect)));
        node.then(literal("withdraw").executes(c->at(c,actor,Industry::withdraw)));
        node.then(literal("operate").executes(c->at(c,actor,Industry::operate)));
        node.then(literal("leave").executes(c->say(c.getSource(),leave(actor.get(c)))));
        node.then(literal("status").executes(c->at(c,actor,(a,w)->ok(status(w)))));
    }
    private static int at(CommandContext<CommandSourceStack> context,ActorLookup lookup,java.util.function.BiFunction<LivingEntity,WorksEntity,ActionResult> action)throws CommandSyntaxException{
        LivingEntity actor=lookup.get(context);WorksEntity works=nearest(actor);
        return say(context.getSource(),works==null?fail("No works within six blocks"):action.apply(actor,works));
    }
    private static int say(CommandSourceStack source,ActionResult result){
        if(result.success())source.sendSuccess(()->Component.literal(result.message()),false);else source.sendFailure(Component.literal(result.message()));return result.success()?1:0;
    }
}
