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
    /** @param output an item id, or {@code vehicle:<kind>} for something that leaves the yard on its own wheels.
     *  @param ticks work in man-hours on the game clock (1000 ticks to the hour) for attended work, elapsed time otherwise.
     *  @param needs site requirements checked by {@link Facility}: roof, water, blast, engine, electric, cleanroom,
     *               floor:N, chimney:N, firebrick:N, lead_chamber:N, equip:&lt;block&gt;.
     *  @param load electrical demand in kilowatts while the work is in hand. */
    public record Recipe(String station,Map<String,Integer> inputs,String output,int amount,Map<String,Integer> byproducts,long ticks,
                         Skill skill,int minimumSkill,boolean attended,boolean heated,boolean powered,int maxWorkers,double practice,List<String> needs,int load){
        public boolean electric(){return needs.contains("electric");}
        /** Electric processes draw from the power station; everything else burns its own fuel on site. */
        public boolean burnsFuel(){return (heated||powered)&&!electric();}
        public boolean vehicle(){return output.startsWith("vehicle:");}
    }
    private static final Map<String,Recipe> DEFAULTS=new LinkedHashMap<>();
    public static volatile Map<String,Recipe> RECIPES;
    /** What every job at a kind of works needs from its site before any recipe-specific plant. */
    public static final Map<String,List<String>> SITE=Map.of(
        "workshop",List.of("roof","floor:12"),"assembly_yard",List.of(),"niter_bed",List.of("roof"),"powder_mill",List.of(),
        "foundry",List.of("firebrick:12","chimney:4","floor:16"),"chemical_works",List.of("roof","chimney:4","water","floor:16"),
        "machine_shop",List.of("roof","engine","floor:40"),"electronics_works",List.of("roof","electric","floor:16"),"power_station",List.of());
    private static Map<String,Integer> bill(Object... pairs){
        var map=new LinkedHashMap<String,Integer>();
        for(int i=0;i<pairs.length;i+=2){String id=(String)pairs[i];map.put(id.startsWith("#")||id.indexOf(':')>=0?id:M+id,(Integer)pairs[i+1]);}
        return Collections.unmodifiableMap(map);
    }
    private static String out(String id){return id.indexOf(':')>=0?id:M+id;}
    private static Recipe build(String station,String output,int amount,Map<String,Integer> byproducts,long ticks,Skill skill,int minimum,boolean attended,Map<String,Integer> inputs,String... extra){
        var needs=new ArrayList<>(SITE.get(station));int load=0,crew=-1;
        for(String need:extra){
            if(need.startsWith("crew:")){crew=Integer.parseInt(need.substring(5));continue;}
            if(need.startsWith("electric")){load=Math.max(load,need.indexOf(':')>0?Integer.parseInt(need.substring(need.indexOf(':')+1)):10);need="electric";}
            // A stricter count replaces the default for the same plant.
            final String wanted=need,prefix=need.indexOf(':')>0&&!need.startsWith("equip:")?need.substring(0,need.indexOf(':')+1):"";
            needs.removeIf(existing->existing.equals(wanted)||!prefix.isEmpty()&&existing.startsWith(prefix));
            needs.add(wanted);
        }
        if(needs.contains("electric")&&load==0)load=10;
        boolean heated=station.equals("foundry")||station.equals("chemical_works"),powered=station.equals("machine_shop");
        int workers=!attended?0:crew>0?crew:station.equals("assembly_yard")?8:station.equals("workshop")||station.equals("powder_mill")?2:station.equals("foundry")?8:3;
        return new Recipe(station,inputs,out(output),amount,byproducts,ticks,skill,minimum,attended,heated,powered,workers,attended&&skill!=null?25:0,List.copyOf(needs),load);
    }
    /** Attended manual or machine work; {@code hours} are man-hours. */
    private static void make(String key,String station,String output,int amount,double hours,Skill skill,int minimum,Map<String,Integer> inputs,String... needs){
        DEFAULTS.put(key,build(station,output,amount,Map.of(),Math.round(hours*1000),skill,minimum,true,inputs,needs));
    }
    private static void make(String key,String station,String output,int amount,Map<String,Integer> byproducts,double hours,Skill skill,int minimum,Map<String,Integer> inputs,String... needs){
        DEFAULTS.put(key,build(station,output,amount,byproducts,Math.round(hours*1000),skill,minimum,true,inputs,needs));
    }
    /** Unattended chemistry or biology: only elapsed days do the work. */
    private static void passive(String key,String station,String output,int amount,double days,Map<String,Integer> inputs){
        Recipe r=build(station,output,amount,Map.of(),Math.round(days*24000),null,0,false,inputs);
        DEFAULTS.put(key,new Recipe(r.station,r.inputs,r.output,r.amount,r.byproducts,r.ticks,null,0,false,false,false,0,0,List.of("roof"),0));
    }
    // Units throughout: metals, minerals and chemicals 1 item = 1 kg; solid fuels (coal, coke, charcoal) one 25 kg sack; wire one
    // metre; liquids one litre; bricks one brick; block items (logs, planks, stone, sand, glass, firebrick) their real volume.
    static {
        final Skill C=Skill.CONSTRUCTION,E=Skill.ENGINEERING,S=Skill.SMITHING,W=Skill.WEAVING;
        // ================= Pre-industrial: hand, horse, water and charcoal =================
        // --- Workshop: joinery, harness, masonry fittings and small arms, under a roof with room to work.
        make("wooden_wheel","workshop","wooden_wheel",1,16,C,4,bill("#minecraft:planks",1,V+"iron_ingot",10,"metal_rivet",8));
        make("vehicle_frame","workshop","vehicle_frame",1,24,C,6,bill("#minecraft:planks",2,"#minecraft:logs",1,V+"iron_ingot",10,"metal_rivet",40));
        make("harness","workshop","harness",1,12,W,4,bill(V+"leather",6,"cordage",4,"metal_rivet",8));
        make("saddle","workshop",V+"saddle",1,30,W,6,bill(V+"leather",5,"cordage",2,"metal_rivet",4,"#minecraft:planks",1));
        make("sleeper","workshop","sleeper",7,3.5,C,2,bill("#minecraft:logs",1));
        make("gun_carriage","workshop","gun_carriage",1,80,C,8,bill("#minecraft:planks",2,V+"iron_ingot",40,"wooden_wheel",2));
        make("musket_stock","workshop","musket_stock",8,64,C,4,bill("#minecraft:planks",1));
        // Springs and frizzen are steel: cementation or crucible steel, long before Bessemer.
        make("musket","workshop","musket",8,48,S,12,bill("musket_barrel",8,"musket_lock",8,"musket_stock",8,"steel_ingot",1,"metal_rivet",16));
        // The service charge with its priming is six drams, 10.6 g, behind a 31 g ball: ninety-six rounds to the kilogram.
        make("paper_cartridge","workshop","paper_cartridge",96,4.8,E,0,bill("musket_ball",96,V+"gunpowder",1,V+"paper",24));
        // Half-kilogram serge bags: one is the service charge of a six-pounder.
        make("powder_charge","workshop","powder_charge",16,.5,E,0,bill(V+"gunpowder",8,"fiber_cloth",1));
        make("powder_mill","workshop","powder_mill",1,200,C,8,bill(V+"stone",2,"#minecraft:planks",8,"bronze_ingot",40,"cordage",8));
        make("chimney","workshop","chimney",1,8,C,4,bill(V+"brick",500,"quicklime",50,V+"sand",1));
        make("lead_chamber","workshop","lead_chamber",1,40,C,6,bill("lead_ingot",400,"#minecraft:planks",4));
        // A lead-sheathed main: six kilograms of copper to the metre, wrapped and tarred.
                make("power_cable","workshop","power_cable",1,1,E,4,bill(V+"copper_ingot",6,"insulated_wire",2,"lead_ingot",8,"coal_tar",1));
        make("cleanroom_suit","workshop","cleanroom_suit",1,6,W,8,bill("clean_cloth",6,"cordage",1));
        // Hand drawing through a plate: about ten metres an hour of millimetre wire, 140 m to the kilogram.
        make("draw_copper_wire","workshop","copper_wire",140,14,S,6,bill(V+"copper_ingot",1));
        // --- Clamps: field kilns built of their own charge on open ground, burned with cordwood.
        make("lime_clamp","assembly_yard","quicklime",1400,60,C,2,bill("limestone",1,"#minecraft:logs",3));
        make("brick_clamp","assembly_yard",V+"brick",1900,200,C,2,bill(V+"clay_ball",2000,"#minecraft:logs",2));
        make("firebrick","assembly_yard","firebrick",1,60,C,6,bill(V+"clay_ball",480,V+"sand",1,"#minecraft:logs",2));
        // --- Nitre: a bed ripens for a full year of four seasons under cover and gives a few percent of its weight in saltpetre.
        passive("niter_earth","niter_bed","niter_earth",64,112,bill("spoiled_food",64,"wood_ash",16,V+"wheat",16));
        // 75:15:10 by weight. Charcoal comes by the 25 kg sack, so a mill charge is 125 kg of saltpetre and 17 of sulfur to one sack.
                make("gunpowder","powder_mill",V+"gunpowder",166,50,E,4,bill("saltpeter",125,V+"charcoal",1,"sulfur",17));
        // --- Foundry (firebrick hearth, flue, floor): steel in crucibles, the blast furnace, gun founding, cupellation.
        make("crucible_steel","foundry","steel_ingot",30,12,S,14,bill(V+"iron_ingot",30,V+"charcoal",1),"crew:3");
        make("pig_iron_charcoal","foundry","pig_iron",1000,192,S,10,bill("roasted_iron",1800,V+"charcoal",60,"quicklime",120),"firebrick:24","chimney:8","blast");
        make("cannon_barrel","foundry","cannon_barrel",1,300,S,14,bill("bronze_ingot",400,V+"clay_ball",60,V+"sand",1));
        make("iron_cannon_barrel","foundry","cannon_barrel",1,250,S,12,bill("pig_iron",450,V+"clay_ball",60,V+"sand",1),"firebrick:24","chimney:8");
        make("bombard_barrel","foundry","bombard_barrel",1,1500,S,12,bill(V+"iron_ingot",1500,V+"charcoal",40));
        // Ore by the tonne: calcining kilns and reverberatory hearths instead of the hand bloomery.
        make("calcine_iron_ore","foundry","roasted_iron",3000,60,S,2,bill("hematite",3200,"#minecraft:logs",2));
        make("smelt_copper_ore","foundry",V+"copper_ingot",500,80,S,8,bill("malachite",1000,V+"charcoal",40));
        make("smelt_tin_ore","foundry","tin_ingot",150,30,S,8,bill("cassiterite",200,V+"charcoal",12));
        make("smelt_lead_ore","foundry","lead_ingot",240,30,S,6,bill("galena",320,V+"charcoal",8));
        make("cast_bronze","foundry","bronze_ingot",400,16,S,8,bill(V+"copper_ingot",360,"tin_ingot",40));
        make("cast_round_shot","foundry","round_shot",100,30,S,8,bill("pig_iron",300,V+"clay_ball",20),"firebrick:24","chimney:8");
        // Forest glass: as much beech ash as sand, and the ash is half a percent of the wood burned for it.
                make("glass","foundry",V+"glass",44,40,S,6,bill(V+"sand",1,"wood_ash",1200));
        make("glass_panes","foundry",V+"glass_pane",6,3,S,8,bill(V+"glass",1));
        make("blow_bottles","foundry",V+"glass_bottle",90,30,S,8,bill(V+"glass",1));
        // Argentiferous lead holds a few hundred grams of silver to the tonne; the litharge is reduced back to lead.
        make("silver","foundry","silver",1,bill("lead_ingot",1900),60,S,10,bill("lead_ingot",2000,V+"charcoal",16),"crew:2");
        // --- Chemical works (roof, flue, water, floor): leaching, roasting, distilling, and acids in lead chambers.
        make("saltpeter","chemical_works","saltpeter",3,6,E,0,bill("niter_earth",64,"wood_ash",8));
        make("sulfur","chemical_works","sulfur",1,2,E,0,bill("pyrite",4));
        make("wood_distillation","chemical_works",V+"charcoal",8,bill("methanol",30),12,E,2,bill("#minecraft:logs",4));
        // A kilogram of grain ferments to about 0.37 L of spirit; roots a quarter of that. Mash is counted by the spirit in it.
                passive("ferment_maize","chemical_works","fermented_mash",12,4,bill("maize",64));
        passive("ferment_potato","chemical_works","fermented_mash",3,4,bill(V+"potato",64));
        passive("ferment_wheat","chemical_works","fermented_mash",40,4,bill(V+"wheat",64));
        make("distill_alcohol","chemical_works","fuel_alcohol",24,6,E,4,bill("fermented_mash",40));
        make("sulfuric_acid","chemical_works","sulfuric_acid",60,10,E,8,bill("sulfur",20,"saltpeter",1),"lead_chamber:2");
        make("nitric_acid","chemical_works","nitric_acid",6,4,E,8,bill("saltpeter",10,"sulfuric_acid",10));
        make("hydrofluoric_acid","chemical_works","hydrofluoric_acid",5,4,E,10,bill("fluorite",10,"sulfuric_acid",12),"lead_chamber:2");
        // Leblanc soda: salt and acid to salt cake, then chalk and coal; the hydrochloric acid is its by-product.
        make("soda_ash","chemical_works","soda_ash",10,bill("hydrochloric_acid",8),8,E,8,bill("salt",12,"sulfuric_acid",10,"quicklime",6,V+"coal",1));
        make("hydrochloric_acid","chemical_works","hydrochloric_acid",20,4,E,8,bill("salt",12,"sulfuric_acid",10));
        make("caustic_soda","chemical_works","caustic_soda",7,3,E,8,bill("soda_ash",10,"quicklime",6));
        make("raw_rubber","chemical_works","raw_rubber",3,6,E,2,bill(V+"jungle_log",1));
        make("rubber","chemical_works","rubber",20,4,E,6,bill("raw_rubber",20,"sulfur",1));
        make("boron_oxide","chemical_works","boron_oxide",3,3,E,10,bill("borax",10,"sulfuric_acid",5));
        // ================= Industrial: coke, steam, machine tools and oil =================
        // One small oven charge: a tonne of coal, three quarters of it back as coke and a twenty-fifth as tar.
        make("coke","chemical_works","coke",30,bill("coal_tar",40),16,E,4,bill(V+"coal",40));
        make("pig_iron","foundry","pig_iron",2000,192,S,10,bill("roasted_iron",3600,"coke",80,"quicklime",240),"firebrick:24","chimney:8","blast");
        // Bessemer: five tonnes blown in twenty minutes, by a blowing engine and a converter crew.
        make("steel","foundry","steel_ingot",4500,12,S,14,bill("pig_iron",5000),"firebrick:24","chimney:8","engine");
        // The puddler burns two thirds of a tonne of coal to the tonne of bar and fettles the hearth with oxide.
                make("puddle_iron","foundry",V+"iron_ingot",900,60,S,10,bill("pig_iron",1000,V+"coal",16,"roasted_iron",50),"firebrick:24","chimney:8");
        make("soda_glass","foundry",V+"glass",48,40,S,6,bill(V+"sand",1,"soda_ash",500,"quicklime",150));
        // Coke-oven tar is about three percent phenols.
                make("phenolic_resin","chemical_works","phenolic_resin",10,6,E,10,bill("coal_tar",250,"methanol",6));
        make("alumina","chemical_works","alumina",9,6,E,12,bill("bauxite",20,"caustic_soda",1));
        make("cryolite","chemical_works","cryolite",20,6,E,12,bill("hydrofluoric_acid",12,"alumina",5,"soda_ash",16),"lead_chamber:2");
        make("extract_crude","chemical_works","crude_oil",10,2,E,4,bill("oil_sand",100));
        make("refine_oil","chemical_works","petrol",20,bill("lubricant",10),4,E,8,bill("crude_oil",100),"equip:distillation_column");
        // The first boiler is hammered plate and the first cylinder cast iron bored with hand-built tools.
        make("boiler","assembly_yard","boiler",1,800,E,8,bill("iron_plate",1200,"metal_rivet",400));
        make("steam_engine","assembly_yard","steam_engine",1,1500,E,10,bill("boiler",1,"pig_iron",1500,"machine_parts",150,"iron_plate",100,V+"copper_ingot",40,"lubricant",10));
        make("steam_boiler","assembly_yard","steam_boiler",1,200,E,8,bill("boiler",1,"firebrick",2,V+"brick",1000,"quicklime",100));
        make("stationary_engine","assembly_yard","stationary_engine",1,300,E,10,bill("steam_engine",1,"pig_iron",200,V+"brick",1000,"quicklime",100));
        make("foundry","assembly_yard","foundry",1,600,C,10,bill(V+"brick",3000,"firebrick",6,V+"clay_ball",100,V+"iron_ingot",200,"quicklime",300));
        make("chemical_works","assembly_yard","chemical_works",1,600,C,10,bill(V+"brick",2000,V+"copper_ingot",300,"lead_ingot",400,V+"glass_bottle",40,"quicklime",200));
        make("distillation_column","assembly_yard","distillation_column",1,400,E,8,bill("iron_plate",1500,V+"copper_ingot",200,"firebrick",2,V+"brick",500));
        make("machine_shop","assembly_yard","machine_shop",1,2000,E,10,bill("pig_iron",4000,"machine_parts",400,"iron_plate",300,V+"brick",2000,V+"leather",16,"lubricant",20));
        // --- Machine shop (roof, engine with boiler, flue and water, forty square metres of floor): rolling, turning, drawing.
        make("steel_plate","machine_shop","steel_plate",480,4,E,6,bill("steel_ingot",500));
        make("roll_iron_plate","machine_shop","iron_plate",480,4,E,6,bill(V+"iron_ingot",500));
        make("machined_parts","machine_shop","machine_parts",20,10,E,8,bill("steel_ingot",30));
        make("precision_parts","machine_shop","precision_parts",15,15,E,10,bill("steel_ingot",30,"lubricant",1));
        make("steel_rail","machine_shop","steel_rail",10,3,E,6,bill("steel_ingot",310));
        make("mill_copper_wire","machine_shop","copper_wire",1400,2,E,6,bill(V+"copper_ingot",10));
        make("insulated_wire","machine_shop","insulated_wire",1000,5,E,8,bill("copper_wire",1000,"yarn",10,"coal_tar",5));
        make("lens","machine_shop","lens",8,160,E,14,bill(V+"glass_pane",1,"coal_tar",2));
        make("explosive_shell","machine_shop","explosive_shell",1,2,E,12,bill("steel_ingot",7,V+"gunpowder",1));
        make("rubber_tyre","machine_shop","rubber_tyre",1,3,E,8,bill("rubber",20,"steel_ingot",5));
        make("electric_motor","machine_shop","electric_motor",1,20,E,12,bill("steel_plate",15,"insulated_wire",300,"precision_parts",5,V+"copper_ingot",2));
        // ================= Electrical: dynamos, arc furnaces and electrolysis =================
        // A 35 kW machine is a tonne and a half of iron and eighty kilograms of copper.
                make("dynamo","assembly_yard","dynamo",1,400,E,12,bill("pig_iron",1000,"steel_plate",300,"insulated_wire",8000,"precision_parts",40,"coke",1));
        make("generator","assembly_yard","generator",1,200,E,12,bill("dynamo",1,"pig_iron",300,"precision_parts",20,"insulated_wire",200,"lubricant",5));
        make("power_station","assembly_yard","power_station",1,200,E,12,bill(V+"copper_ingot",60,"insulated_wire",500,"steel_plate",100,V+"stone",1));
        make("transformer","assembly_yard","transformer",1,80,E,12,bill("steel_plate",120,"insulated_wire",1500,"lubricant",20));
        make("combustion_engine","assembly_yard","combustion_engine",1,600,E,14,bill("pig_iron",200,"steel_ingot",60,"precision_parts",60,"insulated_wire",30,"lead_ingot",8,"sulfuric_acid",2,"rubber",5,"lubricant",5));
        make("arc_furnace","assembly_yard","arc_furnace",1,800,E,14,bill("firebrick",8,"steel_plate",2000,"coke",20,"insulated_wire",500,"transformer",1));
        make("electrolytic_cell","assembly_yard","electrolytic_cell",1,500,E,14,bill("steel_plate",1500,"coke",40,V+"copper_ingot",200,"insulated_wire",300,"firebrick",4));
        // Twelve megawatt-hours to the tonne: four hundred kilowatts on the electrodes for over half a day, from a dozen generating sets.
        make("metallurgical_silicon","foundry","metallurgical_silicon",450,40,E,12,bill(V+"sand",1,"coke",20),"electric:400","equip:arc_furnace","crew:3");
        // Twelve and a half kilowatt-hours to the kilogram; bone ash is a sixth phosphorus.
                make("phosphorus","foundry","phosphorus",16,6,E,12,bill(V+"bone_meal",100,V+"sand",1,"coke",2),"electric:100","equip:arc_furnace","crew:3");
        // A first-generation potline: 25 kWh to the kilogram.
                make("aluminium","chemical_works","aluminium_ingot",10,7.5,E,14,bill("alumina",20,"coke",1,"cryolite",1),"electric:100","equip:electrolytic_cell");
        // Tungstate leached with soda, precipitated with acid and reduced in electrolytic hydrogen.
        make("tungsten","chemical_works","tungsten",5,24,E,14,bill("wolframite",10,"soda_ash",6,"hydrochloric_acid",8),"electric:50","equip:electrolytic_cell");
        // Trichlorosilane is fractionated in a column and reduced on hot rods in hydrogen from the cells.
                make("polysilicon","chemical_works","polysilicon",10,30,E,14,bill("metallurgical_silicon",12,"hydrochloric_acid",40),"electric:150","equip:distillation_column","equip:electrolytic_cell");
        make("photoresist","chemical_works","photoresist",5,8,E,12,bill("coal_tar",100,"methanol",3,"nitric_acid",1));
        make("fine_wire","machine_shop","fine_wire",4000,2,E,12,bill("aluminium_ingot",1));
        make("cleanroom_panel","machine_shop","cleanroom_panel",1,4,E,12,bill("steel_plate",40,"aluminium_ingot",10,"phenolic_resin",10));
        make("filter_unit","machine_shop","filter_unit",1,10,E,14,bill("electric_motor",1,"steel_plate",30,V+"paper",200,"aluminium_ingot",10,"rubber",2));
        make("vacuum_pump","machine_shop","vacuum_pump",1,60,E,14,bill("precision_parts",40,"steel_plate",30,"electric_motor",1,"lubricant",5,"rubber",2));
        make("water_purifier","assembly_yard","water_purifier",1,200,E,14,bill("steel_plate",200,V+"glass_bottle",20,"phenolic_resin",60,V+"copper_ingot",30,"electric_motor",1,"rubber",5));
        make("electronics_works","assembly_yard","electronics_works",1,1500,E,14,bill("precision_parts",300,"steel_plate",800,V+"glass_pane",16,"insulated_wire",2000,V+"brick",2000,"electric_motor",8));
        // ================= Electronic: vacuum, optics, crystal growth and the planar process =================
        make("vacuum_tube","electronics_works","vacuum_tube",20,20,E,14,bill(V+"glass_bottle",20,"tungsten",1,"copper_wire",20,"steel_plate",1),"equip:vacuum_pump");
        make("passive_components","electronics_works","passive_components",500,10,E,12,bill("aluminium_ingot",1,V+"paper",4,"coke",1,V+"clay_ball",2,"copper_wire",100));
        // The phosphor is calcium tungstate.
        make("display_tube","electronics_works","display_tube",1,30,E,16,bill(V+"glass_bottle",4,V+"glass_pane",1,"tungsten",1,"quicklime",1,"copper_wire",30,"vacuum_tube",1),"equip:vacuum_pump");
        // Ceramic dual-in-line bodies with their lead frames and sealing glass.
        make("chip_package","electronics_works","chip_package",250,25,E,12,bill("alumina",1,"copper_wire",25,V+"glass_bottle",1));
        make("circuit_board","electronics_works","circuit_board",4,4,E,12,bill("fiber_cloth",1,"phenolic_resin",1,V+"copper_ingot",1,"nitric_acid",1));
        make("crystal_puller","assembly_yard","crystal_puller",1,800,E,16,bill("steel_plate",600,"precision_parts",150,"vacuum_pump",1,"vacuum_tube",12,"insulated_wire",500,V+"glass_bottle",4,"rubber",2));
        make("diffusion_furnace","assembly_yard","diffusion_furnace",1,500,E,16,bill("firebrick",2,V+"glass_bottle",10,"insulated_wire",400,"steel_plate",300,"vacuum_tube",4));
        make("lithography_stepper","assembly_yard","lithography_stepper",1,1500,E,18,bill("lens",12,"precision_parts",200,"steel_plate",300,"electric_motor",2,"vacuum_tube",6,"tungsten",1,V+"glass_pane",2));
        make("wire_bonder","assembly_yard","wire_bonder",1,600,E,16,bill("precision_parts",60,"electric_motor",2,"vacuum_tube",2,"steel_plate",40,"lens",2,"tungsten",1));
        make("silicon_boule","electronics_works","silicon_boule",10,100,E,14,bill("polysilicon",20,"phosphorus",1),"electric:30","equip:crystal_puller");
        make("silicon_wafer","electronics_works","silicon_wafer",400,40,E,14,bill("silicon_boule",1,"hydrofluoric_acid",1,"nitric_acid",1));
        // Emulsion masks: silver halide in gelatin on glass, shot down through a reduction camera. Contact printing wears one out per lot.
        make("photomask","electronics_works","photomask",8,320,E,16,bill(V+"glass_pane",1,"silver",1,"nitric_acid",1,"salt",1,V+"bone_meal",2),"equip:lithography_stepper");
        // A four-mask p-channel MOS flow on lots of twenty-five wafers: field oxide, source and drain diffusion, gate oxide, contacts, metal.
        make("oxidise_wafers","electronics_works","wafer_oxidised",25,6,E,16,bill("silicon_wafer",25),"cleanroom","electric:20","equip:diffusion_furnace","equip:water_purifier");
        make("diffuse_wafers","electronics_works","wafer_diffused",25,12,E,16,bill("wafer_oxidised",25,"photoresist",1,"photomask",1,"hydrofluoric_acid",2,"boron_oxide",1),"cleanroom","electric:25","equip:lithography_stepper","equip:diffusion_furnace","equip:water_purifier");
        make("gate_wafers","electronics_works","wafer_gated",25,12,E,16,bill("wafer_diffused",25,"photoresist",1,"photomask",1,"hydrofluoric_acid",2),"cleanroom","electric:25","equip:lithography_stepper","equip:diffusion_furnace","equip:water_purifier");
        make("contact_wafers","electronics_works","wafer_contacted",25,8,E,16,bill("wafer_gated",25,"photoresist",1,"photomask",1,"hydrofluoric_acid",2),"cleanroom","electric:15","equip:lithography_stepper","equip:water_purifier");
        make("metallise_wafers","electronics_works","patterned_wafer",25,10,E,16,bill("wafer_contacted",25,"aluminium_ingot",1,"photoresist",1,"photomask",1,"nitric_acid",1),"cleanroom","electric:20","equip:lithography_stepper","equip:vacuum_pump","equip:water_purifier");
        // Some 800 gross dice a millimetre and a half square on a two-inch wafer; one in five works in a perfect room.
        make("silicon_die","electronics_works","silicon_die",160,6,E,14,bill("patterned_wafer",1),"cleanroom");
        make("chip","electronics_works","chip",16,1.6,E,14,bill("silicon_die",16,"fine_wire",256,"chip_package",16),"cleanroom","equip:wire_bonder");
        // A minicomputer and a mainframe of small-scale logic: thousands of identical gate packages, wired by hand.
        make("computer","electronics_works","computer",1,4000,E,18,bill("chip",2800,"circuit_board",120,"insulated_wire",8000,"solder",20,"passive_components",6000,"steel_plate",150,"transformer",2,"display_tube",1,"electric_motor",4,"precision_parts",20),"crew:16");
        make("server","electronics_works","server",1,30000,E,20,bill("chip",20000,"circuit_board",900,"insulated_wire",60000,"solder",150,"passive_components",45000,"steel_plate",1200,"transformer",12,"display_tube",2,"electric_motor",30,"precision_parts",200),"crew:16");
        // ================= Assembly yard: vehicles and ordnance at their real weights =================
        make("cart","assembly_yard","vehicle:cart",1,40,C,6,bill("vehicle_frame",1,"wooden_wheel",2,"harness",1));
        make("wagon","assembly_yard","vehicle:wagon",1,120,C,8,bill("vehicle_frame",2,"wooden_wheel",4,"harness",2,"#minecraft:planks",4));
        make("chariot","assembly_yard","vehicle:chariot",1,80,C,8,bill("vehicle_frame",1,"wooden_wheel",2,"harness",2,V+"leather",4,"cordage",8));
        make("trebuchet","assembly_yard","vehicle:trebuchet",1,2000,C,10,bill("#minecraft:logs",16,"#minecraft:planks",16,"cordage",64,V+"iron_ingot",100,V+"cobblestone",4));
        make("cannon","assembly_yard","vehicle:cannon",1,60,E,6,bill("cannon_barrel",1,"gun_carriage",1,"metal_rivet",40));
        make("bombard","assembly_yard","vehicle:bombard",1,200,E,6,bill("bombard_barrel",1,"#minecraft:logs",6,V+"iron_ingot",100,"cordage",16));
        make("field_gun","assembly_yard","vehicle:field_gun",1,3000,E,14,bill("steel_ingot",1000,"precision_parts",60,"wooden_wheel",2,"steel_plate",60));
        make("motor_truck","assembly_yard","vehicle:motor_truck",1,1500,E,14,bill("combustion_engine",1,"steel_ingot",1200,"steel_plate",800,"precision_parts",150,"rubber_tyre",4,"#minecraft:planks",2,V+"glass_pane",2,"lubricant",10));
        make("locomotive","assembly_yard","vehicle:locomotive",1,12000,E,16,bill("steel_ingot",14000,"steel_plate",5000,"pig_iron",3000,"precision_parts",1200,V+"copper_ingot",700,"lubricant",40));
        make("rail_wagon","assembly_yard","vehicle:rail_wagon",1,600,E,10,bill("steel_ingot",2500,"pig_iron",1000,"machine_parts",60,"#minecraft:planks",8));
        make("rail_carriage","assembly_yard","vehicle:rail_carriage",1,1500,E,10,bill("steel_ingot",3500,"pig_iron",1200,"machine_parts",80,"#minecraft:planks",16,V+"glass_pane",8,"wool_cloth",8));
        RECIPES=Collections.unmodifiableMap(new LinkedHashMap<>(DEFAULTS));
    }
    /** Data packs may retune time, yield, skill and bills of material, or add recipes; stations are fixed. */
    public static void reloadDefinitions(JsonObject definitions){
        var replacement=new LinkedHashMap<>(DEFAULTS);
        for(var entry:definitions.entrySet()){
            String key=entry.getKey();var object=ContentDefinitions.object(entry.getValue(),key);
            ContentDefinitions.fields(object,"station","inputs","output","amount","ticks","skill","minimumSkill","attended","heated","powered","maxWorkers","practice");
            Recipe base=DEFAULTS.get(key);
            if(base==null){if(!object.has("station")||!object.has("inputs")||!object.has("output"))throw new IllegalArgumentException("New industrial recipes need station, inputs and output: "+key);base=new Recipe("",Map.of(),"",1,Map.of(),1000,null,0,true,false,false,1,0,null,0);}
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
            replacement.put(key,new Recipe(station,inputs,output,(int)ContentDefinitions.integer(object,"amount",base.amount,1,1000000),base.byproducts,ContentDefinitions.integer(object,"ticks",base.ticks,1,2000000000L),skill,
                (int)ContentDefinitions.integer(object,"minimumSkill",base.minimumSkill,0,20),attended,ContentDefinitions.bool(object,"heated",base.heated),ContentDefinitions.bool(object,"powered",base.powered),
                (int)ContentDefinitions.integer(object,"maxWorkers",Math.max(attended?1:0,base.maxWorkers),0,64),practice,base.needs==null?SITE.get(station):base.needs,base.needs==null?(SITE.get(station).contains("electric")?10:0):base.load));
        }
        RECIPES=Collections.unmodifiableMap(replacement);
    }
    public static void init(){NeoForge.EVENT_BUS.addListener(Industry::interact);NeoForge.EVENT_BUS.addListener(Industry::commands);NeoForge.EVENT_BUS.addListener(Industry::died);NeoForge.EVENT_BUS.addListener(Industry::loggedOut);NeoForge.EVENT_BUS.addListener(Industry::demolish);}
    /** The dead hold no place at a works. Once the body is gone the works cannot tell a dead hand from one out of sight, and the place would stay taken for good. */
    private static void died(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event){if(!event.getEntity().level().isClientSide())leave(event.getEntity());}
    private static void loggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){leave(event.getEntity());}

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
    /** Heat in fuel ticks. A 25 kg sack of coal, coke or charcoal is 8000; a cubic metre of air-dried cordwood twelve times that. */
    public static int fuelValue(ItemStack stack){
        return stack.is(Items.CHARCOAL)||stack.is(Items.COAL)||stack.is(SurvivalContent.item("coke"))?8000:stack.is(ItemTags.LOGS)?96000:stack.is(Items.STICK)?80:0;
    }
    /** Fuel ticks burned per tick of work: a forge fire is 1 (3 kg an hour). A furnace hearth burns 25 kg an hour, retorts and
     * stills 12 kg, and the boiler of a fifty-horsepower shop engine 100 kg. */
    public static int fuelRate(String station){return switch(station){case "foundry"->8;case "chemical_works"->4;case "machine_shop"->32;default->1;};}
    /** Work ticks for one batch: man-hours at 1000 ticks to the hour, scaled by the server's industryTimeScale. */
    public static long ticks(Recipe recipe){
        double scale=1;try{scale=dev.qube.spawn_9b5fbb9.Config.INDUSTRY_TIME.get();}catch(RuntimeException unloaded){}
        return Math.max(1,Math.round(recipe.ticks*scale));
    }
    public static ActionResult select(LivingEntity actor,WorksEntity works,String recipeId){
        String invalid=reach(actor,works);if(!invalid.isEmpty())return fail(invalid);
        Recipe recipe=RECIPES.get(recipeId);if(recipe==null)return fail("Unknown industrial recipe");
        if(!recipe.station.equals(works.station()))return fail("That recipe belongs to a "+recipe.station.replace('_',' '));
        // A batch of something that is no longer made here (a recipe a data pack took away) can never finish, and must not hold the works for good.
        if(works.active&&RECIPES.get(works.recipe)!=null)return fail("A batch is in progress; let it finish before retooling");
        boolean retooled=!recipeId.equals(works.recipe);works.active=false;works.recipe=recipeId;works.progress=0;works.setChanged();
        if(retooled&&actor.level() instanceof ServerLevel level)works.inspect(level,recipe);
        return ok("Set to "+recipeId+": needs "+describe(recipe.inputs)+" per batch"+(recipe.burnsFuel()?", burns fuel":"")+(recipe.electric()?", draws "+recipe.load+" kW":"")+(recipe.attended?", attended by "+(recipe.skill==null?"any worker":recipe.skill.label().toLowerCase(Locale.ROOT)+" "+recipe.minimumSkill+"+"):", unattended")+". "+siteLine(works));
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
        String unfit=Ledger.unfit(held);if(!unfit.isEmpty())return fail(unfit);
        long moved=held.getCount();Ledger.add(works.stock,id,moved);held.setCount(0);
        if(everything&&actor instanceof Player player)for(int slot=0;slot<player.getInventory().getContainerSize();slot++){
            ItemStack other=player.getInventory().getItem(slot);if(!other.isEmpty()&&!other.isDamaged()&&Ledger.id(other).equals(id)&&Ledger.unfit(other).isEmpty()){moved+=other.getCount();Ledger.add(works.stock,id,other.getCount());other.setCount(0);}
        }
        works.setChanged();return ok("Delivered "+moved+"; "+progressLine(works,recipe));
    }
    /** Moves every unit a works can use out of a cargo ledger (a wagon, truck or rail wagon alongside). */
    public static long depositFrom(WorksEntity works,Map<String,Long> cargo){
        // A power station makes nothing and so has no recipe; it burns, and a wagon of coal drawn up to it is its supply.
        Recipe recipe=RECIPES.get(works.recipe);boolean station=works.station().equals("power_station");if(recipe==null&&!station)return 0;long moved=0;
        for(var iterator=cargo.entrySet().iterator();iterator.hasNext();){
            var entry=iterator.next();int heat=fuelValue(new ItemStack(Ledger.item(entry.getKey())));
            boolean fuel=(recipe==null||recipe.burnsFuel())&&heat>0&&(recipe==null||ingredientFor(recipe,entry.getKey())==null)&&(heat==8000||!works.station().equals("foundry"));
            if(fuel){long units=Math.min(entry.getValue(),Math.max(0,(works.fuelLimit()-works.fuel)/heat));if(units<=0)continue;works.fuel+=units*heat;moved+=units;if(units==entry.getValue())iterator.remove();else entry.setValue(entry.getValue()-units);continue;}
            if(recipe==null||ingredientFor(recipe,entry.getKey())==null)continue;
            Ledger.add(works.stock,entry.getKey(),entry.getValue());moved+=entry.getValue();iterator.remove();
        }
        if(moved>0)works.setChanged();return moved;
    }
    public static ActionResult fuel(LivingEntity actor,WorksEntity works,InteractionHand hand){
        String invalid=reach(actor,works);if(!invalid.isEmpty())return fail(invalid);
        if(!works.usesFuel())return fail("This works burns no fuel");
        ItemStack held=actor.getItemInHand(hand);int value=fuelValue(held);if(value==0)return fail("Hold coal, coke, charcoal, logs or sticks");
        // Boilers and stills burn cordwood; a furnace hearth cannot reach heat on it.
        if(value!=8000&&works.station().equals("foundry"))return fail("A furnace hearth wants coal, coke or charcoal, not raw wood");
        if(works.fuel+value>works.fuelLimit())return fail("The bunker is full");
        int units=(int)Math.min(held.getCount(),(works.fuelLimit()-works.fuel)/value);held.shrink(units);works.fuel+=(long)units*value;works.setChanged();
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
        // A place is held by standing at the work. One who has wandered off, or died, gives it up to whoever comes
        // for it; one who is merely out of sight of the server (asleep in an unloaded house) keeps it.
        if(!works.workers.contains(id)&&works.workers.size()>=recipe.maxWorkers&&actor.level() instanceof ServerLevel level)for(String holder:new ArrayList<>(works.workers)){
            net.minecraft.world.entity.Entity found;try{found=level.getEntity(java.util.UUID.fromString(holder));}catch(IllegalArgumentException malformed){works.workers.remove(holder);continue;}
            if(found instanceof LivingEntity absent&&(!absent.isAlive()||absent.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(works.getBlockPos()))>64)){works.workers.remove(holder);absent.getPersistentData().remove("survivalWorksPos");}
        }
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
        return works.active?"batch "+(long)works.progress+"/"+ticks(recipe)+" work ticks":need;
    }
    public static String siteLine(WorksEntity works){return works.site.isEmpty()?"Site in order.":"Site lacks: "+String.join("; ",works.site)+".";}
    public static String status(WorksEntity works){
        Recipe recipe=RECIPES.get(works.recipe);
        if(works.station().equals("power_station")&&works.getLevel() instanceof ServerLevel level){
            String fault=Facility.stationFault(level,works);int generators=Facility.generators(level,works);
            return "power station | "+(fault.isEmpty()?"running":fault)+" | generators "+generators+" (capacity "+Facility.GENERATOR_KW*generators+" kW) | load now "+(level.getGameTime()/20==works.loadStamp?works.loadUsed:0)+" kW | fuel "+works.fuel+" ("+works.fuel/8000+" sacks)";
        }
        // Asking after a works does not run it. The works reads its own site every five seconds, and that reading is what is reported.
        return works.station().replace('_',' ')+" | "+(recipe==null?"no recipe":works.recipe+" | "+progressLine(works,recipe))+" | stock: "+Ledger.describe(works.stock)+" | finished: "+Ledger.describe(works.products)
            +(works.usesFuel()?" | fuel "+works.fuel:"")+" | workers "+works.workers.size()+" | batches "+works.batches+(recipe==null?"":" | "+siteLine(works))+(recipe!=null&&recipe.needs.contains("cleanroom")?" | cleanliness "+Math.round(works.cleanliness)+"%":"");
    }
    public static JsonObject info(String recipeId){
        var json=new JsonObject();Recipe recipe=RECIPES.get(recipeId);if(recipe==null){json.addProperty("error","Unknown industrial recipe");return json;}
        json.addProperty("recipe",recipeId);json.addProperty("station",recipe.station);json.addProperty("output",recipe.output);json.addProperty("outputCount",recipe.amount);json.addProperty("workTicks",ticks(recipe));json.addProperty("manHours",recipe.ticks/1000d);
        var inputs=new JsonObject();recipe.inputs.forEach(inputs::addProperty);json.add("inputs",inputs);
        var extra=new JsonObject();recipe.byproducts.forEach(extra::addProperty);json.add("byproducts",extra);
        json.addProperty("skill",recipe.skill==null?"none":recipe.skill.name().toLowerCase(Locale.ROOT));json.addProperty("minimumSkill",recipe.minimumSkill);
        var needs=new com.google.gson.JsonArray();recipe.needs.forEach(needs::add);json.add("siteRequirements",needs);json.addProperty("electricLoadKilowatts",recipe.load);
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

    /** A works knocked down spills at most fifty-four stacks of each thing it holds, and no vehicle at all: more than
     * that would bury the server in loose items. So it is not knocked down while it holds more; it is emptied first. */
    private static void demolish(net.neoforged.neoforge.event.level.block.BreakBlockEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(level.getBlockEntity(event.getPos()) instanceof WorksEntity works))return;
        boolean lost=works.pendingVehicle();
        for(var ledger:List.of(works.stock,works.products))for(var entry:ledger.entrySet())if(!entry.getKey().startsWith("vehicle:")&&entry.getValue()>54L*new ItemStack(Ledger.item(entry.getKey())).getMaxStackSize())lost=true;
        if(!lost)return;
        event.setCanceled(true);event.setNotifyClient(true);
        if(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)player.sendOverlayMessage(Component.literal("This works holds more than would survive pulling it down: withdraw its stock and collect what it has made first"));
    }
    // ---- Player interaction --------------------------------------------------------------------------
    private static void interact(PlayerInteractEvent.RightClickBlock event){if(event.getEntity().isSpectator())return;
        if(!(event.getLevel() instanceof ServerLevel level)||!(level.getBlockEntity(event.getPos()) instanceof WorksEntity works))return;
        event.setCanceled(true);if(event.getHand()!=InteractionHand.MAIN_HAND)return;
        Player player=event.getEntity();ItemStack held=event.getItemStack();ActionResult result;
        Recipe recipe=RECIPES.get(works.recipe);
        if(held.isEmpty()&&player.isShiftKeyDown())result=works.stock.isEmpty()?leave(player):withdraw(player,works);
        else if(held.isEmpty())result=!works.products.keySet().stream().allMatch(product->product.startsWith("vehicle:"))?collect(player,works):recipe!=null&&recipe.attended&&!works.workers.contains(player.getUUID().toString())?operate(player,works):ok(status(works));
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
