package dev.qube.spawn_9b5fbb9.content;
import java.util.*;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import dev.qube.spawn_9b5fbb9.survival.LaborGuard;
import dev.qube.spawn_9b5fbb9.survival.SkillApi;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class Production {
    public record Recipe(String input,int count,String output,int amount,long ticks,boolean forge,String second,int secondCount,String station,Skill skill,int minimumSkill,boolean manual,boolean heated,double practice,double minimumManipulation){
        /** Existing integrations keep their old heated kiln/forge constructor. */
        public Recipe(String input,int count,String output,int amount,long ticks,boolean forge,String second,int secondCount){this(input,count,output,amount,ticks,forge,second,secondCount,forge?"forge":"kiln",null,0,forge,true,0,.05);}
    }
    public static volatile Map<String,Recipe> RECIPES=new LinkedHashMap<>();
    private static final Map<String,Recipe> DEFAULT_RECIPES;
    public static void reloadDefinitions(com.google.gson.JsonObject definitions){
        var replacement=new LinkedHashMap<>(DEFAULT_RECIPES);
        for(var entry:definitions.entrySet()){
            String key=entry.getKey();ContentDefinitions.id(key);var object=ContentDefinitions.object(entry.getValue(),key);ContentDefinitions.fields(object,"input","count","output","amount","ticks","forge","second","secondCount","station","skill","minimumSkill","manual","heated","practice","minimumManipulation");
            Recipe base=DEFAULT_RECIPES.get(key);if(base==null){if(!object.has("input")||!object.has("output"))throw new IllegalArgumentException("New processes require input and output: "+key);base=new Recipe("",1,"",1,1000,false,"",0);}
            String input=ContentDefinitions.string(object,"input",base.input),output=ContentDefinitions.string(object,"output",base.output),second=ContentDefinitions.string(object,"second",base.second);
            Item inputItem=ContentDefinitions.item(input),outputItem=ContentDefinitions.item(output);if(dev.qube.spawn_9b5fbb9.world.NaturalWorldRules.prohibited(new ItemStack(outputItem)))throw new IllegalArgumentException("Process output is prohibited: "+output);
            int secondMax=0;if(!second.isEmpty())secondMax=second.equals("@safe_water")?4:new ItemStack(ContentDefinitions.item(second)).getMaxStackSize();
            int secondDefault=second.isEmpty()?0:second.equals(base.second)?base.secondCount:1;
            String station=ContentDefinitions.string(object,"station",base.station);if(station.indexOf(':')>=0){var id=ContentDefinitions.id(station);if(!id.getNamespace().equals(SurvivalContent.MODID))throw new IllegalArgumentException("Unknown production station: "+station);station=id.getPath();}
            if(!Set.of("kiln","bloomery","forge","anvil","loom").contains(station))throw new IllegalArgumentException("Unknown production station: "+station);
            if(ContentDefinitions.bool(object,"forge",station.equals("forge"))!=station.equals("forge"))throw new IllegalArgumentException("forge flag must agree with the explicit station");
            String skillName=ContentDefinitions.string(object,"skill",base.skill==null?"":base.skill.name());Skill skill=null;if(!skillName.isBlank()&&!skillName.equalsIgnoreCase("none")){try{skill=Skill.valueOf(skillName.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException invalid){throw new IllegalArgumentException("Unknown production skill: "+skillName);}}
            Recipe configured=new Recipe(ContentDefinitions.id(input).toString(),(int)ContentDefinitions.integer(object,"count",base.count,1,new ItemStack(inputItem).getMaxStackSize()),ContentDefinitions.id(output).toString(),(int)ContentDefinitions.integer(object,"amount",base.amount,1,new ItemStack(outputItem).getMaxStackSize()),ContentDefinitions.integer(object,"ticks",base.ticks,1,64000),station.equals("forge"),second.startsWith("@")?second:second.isEmpty()?"":ContentDefinitions.id(second).toString(),(int)ContentDefinitions.integer(object,"secondCount",secondDefault,second.isEmpty()?0:1,secondMax),station,skill,(int)ContentDefinitions.integer(object,"minimumSkill",base.minimumSkill,0,20),ContentDefinitions.bool(object,"manual",base.manual),ContentDefinitions.bool(object,"heated",base.heated),ContentDefinitions.number(object,"practice",base.practice,0,2500),ContentDefinitions.number(object,"minimumManipulation",base.minimumManipulation,.05,1));
            validateRequirements(configured);replacement.put(key,configured);
        }
        RECIPES=Collections.unmodifiableMap(replacement);
    }
    static {
        // A storage jar is thrown from eight lumps, thirty kilograms of clay.
        skilled("fire_pot","minecraft:clay_ball",8,"spawn_9b5fbb9:fired_pot",1,2000,"kiln",Skill.POTTERY,0,true,true,25,.25,"",0);
        // A small kiln fires a barrow-load of moulded bricks over a day and a night.
        recipe("fire_brick","minecraft:clay_ball",64,"minecraft:brick",60,24000,false,"",0);
        for(var mineral:Map.of("malachite","copper","cassiterite","tin","hematite","iron").entrySet())skilled("crush_"+mineral.getKey(),"spawn_9b5fbb9:"+mineral.getKey(),48,"spawn_9b5fbb9:crushed_"+mineral.getValue(),48,1000,"anvil",Skill.MINING,0,true,false,10,.1,"",0);
        skilled("prepare_flax_fiber","spawn_9b5fbb9:flax_fiber",1,"spawn_9b5fbb9:plant_fiber",1,400,"loom",Skill.WEAVING,0,true,false,10,.1,"",0);
        skilled("prepare_yarrow_leaves","spawn_9b5fbb9:yarrow_leaves",1,"spawn_9b5fbb9:medicinal_leaves",1,400,"loom",Skill.MEDICINE,0,true,false,10,.1,"",0);
        for(String metal:new String[]{"copper","tin","iron"}){
            recipe("roast_"+metal,"spawn_9b5fbb9:crushed_"+metal,32,"spawn_9b5fbb9:roasted_"+metal,32,2000,false,"",0);
            // Charcoal is the reductant as well as the heat. Sorted copper carbonate gives half its weight in metal, tin stone three
            // quarters, and a bloomery leaves most of its iron in the slag: 48 kg of ore and 50 kg of charcoal for a 12 kg bloom.
            int ore=metal.equals("iron")?48:32,yield=metal.equals("iron")?12:metal.equals("tin")?24:16,sacks=metal.equals("iron")?2:1;
            skilled("smelt_"+metal,"spawn_9b5fbb9:roasted_"+metal,ore,metal.equals("iron")?"spawn_9b5fbb9:iron_bloom":metal.equals("tin")?"spawn_9b5fbb9:tin_ingot":"minecraft:copper_ingot",yield,metal.equals("iron")?8000:6000,"bloomery",null,0,false,true,0,.05,"minecraft:charcoal",sacks);
        }
        skilled("bronze","minecraft:copper_ingot",9,"spawn_9b5fbb9:bronze_ingot",10,4000,"forge",Skill.SMITHING,0,true,true,20,.15,"spawn_9b5fbb9:tin_ingot",1);
        skilled("work_bloom","spawn_9b5fbb9:iron_bloom",4,"minecraft:iron_ingot",3,3000,"forge",Skill.SMITHING,0,true,true,15,.15,"",0);
        skilled("copper_rivets","minecraft:copper_ingot",1,"spawn_9b5fbb9:metal_rivet",4,800,"forge",Skill.SMITHING,0,true,true,25,.15,"",0);
        // Boiler rivets and track spikes are wrought iron.
        skilled("iron_rivets","minecraft:iron_ingot",1,"spawn_9b5fbb9:metal_rivet",4,800,"forge",Skill.SMITHING,0,true,true,25,.15,"",0);
        for(String food:new String[]{"beef","porkchop","chicken","mutton","rabbit","cod","salmon"})recipe("cook_"+food,"minecraft:"+food,1,"minecraft:cooked_"+food,1,1000,false,"",0);
        recipe("dry_fiber","spawn_9b5fbb9:plant_fiber",3,"spawn_9b5fbb9:dried_fiber",3,1000,false,"",0);
        // A clamp of four cubic metres of wood chars for two days on its own charge and gives a tenth of its weight in charcoal.
        skilled("charcoal","minecraft:oak_log",4,"minecraft:charcoal",8,48000,"kiln",null,0,false,false,0,.05,"",0);
        // Any timber chars, and any timber burns to ash.
        for(String wood:new String[]{"spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","pale_oak"})skilled("charcoal_"+wood,"minecraft:"+wood+"_log",4,"minecraft:charcoal",8,48000,"kiln",null,0,false,false,0,.05,"",0);
        for(String wood:new String[]{"oak","spruce","jungle","acacia","dark_oak","mangrove","cherry","pale_oak"})recipe("burn_ash_"+wood,"minecraft:"+wood+"_log",1,"spawn_9b5fbb9:wood_ash",3,1000,false,"",0);
        recipe("sterilize_cloth","spawn_9b5fbb9:fiber_cloth",1,"spawn_9b5fbb9:clean_cloth",2,1000,false,"@safe_water",1);
        for(String metal:new String[]{"copper","bronze","forged_iron"}){
            String ingot=metal.equals("forged_iron")?"minecraft:iron_ingot":metal.equals("bronze")?"spawn_9b5fbb9:bronze_ingot":"minecraft:copper_ingot";int minimum=metal.equals("copper")?4:metal.equals("bronze")?8:12;
            for(String type:new String[]{"pickaxe","axe","hoe","sword","shovel"}){
                String blank="spawn_9b5fbb9:"+metal+"_"+type+"_blank";
                int ingots=type.equals("shovel")?1:type.equals("hoe")||type.equals("sword")?2:3;String tool=(type.equals("sword")||type.equals("shovel"))&&!metal.equals("bronze")?"minecraft:"+(metal.equals("forged_iron")?"iron":metal)+"_"+type:"spawn_9b5fbb9:"+metal+"_"+type;
                skilled("forge_"+metal+"_"+type+"_blank",ingot,ingots,blank,1,1000,"forge",Skill.SMITHING,0,true,true,15,.15,"",0);
                skilled(metal+"_"+type,blank,1,tool,1,1500,"anvil",Skill.SMITHING,minimum,true,false,25,.15,"spawn_9b5fbb9:cordage",1);
            }
            for(String piece:new String[]{"helmet","chestplate","leggings","boots"}){
                int ingots=switch(piece){case "helmet"->5;case "chestplate"->8;case "leggings"->7;default->4;};String blank="spawn_9b5fbb9:"+metal+"_"+piece+"_blank";String out=metal.equals("bronze")?"spawn_9b5fbb9:bronze_"+piece:"minecraft:"+(metal.equals("forged_iron")?"iron":metal)+"_"+piece;
                skilled("forge_"+metal+"_"+piece+"_blank",ingot,ingots,blank,1,1600,"forge",Skill.SMITHING,0,true,true,20,.15,"",0);
                skilled(metal+"_"+piece,blank,1,out,1,2000,"anvil",Skill.SMITHING,metal.equals("forged_iron")?14:12,true,false,35,.15,"spawn_9b5fbb9:metal_rivet",2);
            }
        }
        for(String metal:new String[]{"copper","iron"}){String blank="spawn_9b5fbb9:"+metal+"_spear_blank";skilled("forge_"+metal+"_spear_blank","minecraft:"+metal+"_ingot",3,blank,1,1200,"forge",Skill.SMITHING,0,true,true,15,.15,"",0);skilled("forge_"+metal+"_spear",blank,1,"minecraft:"+metal+"_spear",1,1600,"anvil",Skill.SMITHING,metal.equals("copper")?4:12,true,false,25,.15,"spawn_9b5fbb9:cordage",1);}
        skilled("forge_shield_blank","minecraft:iron_ingot",1,"spawn_9b5fbb9:shield_blank",1,1000,"forge",Skill.SMITHING,0,true,true,15,.15,"",0);
        skilled("forge_crossbow_blank","minecraft:iron_ingot",1,"spawn_9b5fbb9:crossbow_blank",1,1200,"forge",Skill.SMITHING,0,true,true,15,.15,"",0);
        for(String plank:new String[]{"oak_planks","acacia_planks","bamboo_planks","birch_planks","cherry_planks","crimson_planks","dark_oak_planks","jungle_planks","mangrove_planks","pale_oak_planks","poplar_planks","spruce_planks","warped_planks"}){
            skilled(plank.equals("oak_planks")?"shield":"shield_with_"+plank,"spawn_9b5fbb9:shield_blank",1,"minecraft:shield",1,1600,"anvil",Skill.SMITHING,12,true,false,25,.15,"minecraft:"+plank,6);
            skilled(plank.equals("oak_planks")?"crossbow_stock":"crossbow_stock_from_"+plank,"minecraft:"+plank,3,"spawn_9b5fbb9:crossbow_stock",1,1200,"loom",Skill.CONSTRUCTION,2,true,false,15,.1,"spawn_9b5fbb9:cordage",2);
        }
        skilled("crossbow","spawn_9b5fbb9:crossbow_blank",1,"minecraft:crossbow",1,2000,"anvil",Skill.SMITHING,12,true,false,30,.15,"spawn_9b5fbb9:crossbow_stock",1);
        for(String utility:new String[]{"shears","bucket","flint_and_steel","spyglass","brush"}){
            boolean copper=utility.equals("spyglass")||utility.equals("brush");int ingots=utility.equals("bucket")?3:utility.equals("shears")||utility.equals("spyglass")?2:1;String blank="spawn_9b5fbb9:"+utility+"_blank";
            skilled("forge_"+utility+"_blank","minecraft:"+(copper?"copper":"iron")+"_ingot",ingots,blank,1,1000,"forge",Skill.SMITHING,0,true,true,15,.15,"",0);
            String second=utility.equals("flint_and_steel")?"minecraft:flint":utility.equals("spyglass")?"minecraft:amethyst_shard":utility.equals("brush")?"spawn_9b5fbb9:brush_bundle":"spawn_9b5fbb9:metal_rivet";
            skilled(utility,blank,1,"minecraft:"+utility,1,1400,"anvil",Skill.SMITHING,copper?4:12,true,false,25,.15,second,utility.equals("bucket")?2:1);
        }
        for(String color:new String[]{"white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"})skilled(color.equals("white")?"spin_wool":"spin_"+color+"_wool","minecraft:"+color+"_wool",1,"spawn_9b5fbb9:yarn",4,800,"loom",Skill.WEAVING,0,true,false,10,.1,"",0);
        skilled("weave_wool_cloth","spawn_9b5fbb9:yarn",4,"spawn_9b5fbb9:wool_cloth",1,1000,"loom",Skill.WEAVING,0,true,false,12,.1,"",0);
        skilled("weave_fiber_cloth","spawn_9b5fbb9:cordage",3,"spawn_9b5fbb9:fiber_cloth",1,1000,"loom",Skill.WEAVING,0,true,false,12,.1,"",0);
        for(String fabric:new String[]{"fiber","wool"})for(String piece:new String[]{"shirt","pants","jacket","shoes"}){int cloth=piece.equals("jacket")?6:piece.equals("shoes")?2:4;int minimum=piece.equals("jacket")?4:piece.equals("pants")?2:0;skilled("weave_"+fabric+"_"+piece,"spawn_9b5fbb9:"+fabric+"_cloth",cloth,"spawn_9b5fbb9:"+fabric+"_"+piece,1,piece.equals("jacket")?1600:1000,"loom",Skill.WEAVING,minimum,true,false,20,.1,"spawn_9b5fbb9:cordage",1);}
        // Pre-industrial routes into gunpowder weapons, wire and the first machine parts. Mechanised
        // equivalents live in Industry; these hand processes are what makes the first works possible.
        skilled("smelt_galena","spawn_9b5fbb9:galena",32,"spawn_9b5fbb9:lead_ingot",24,3000,"bloomery",null,0,false,true,0,.05,"minecraft:charcoal",1);
        // Tallow is the lubricant of the wooden and early iron ages.
        for(String meat:new String[]{"porkchop","mutton","beef"})recipe("render_tallow_"+meat,"minecraft:"+meat,4,"spawn_9b5fbb9:lubricant",1,1000,false,"",0);
        // Forest glass: sand fluxed with wood ash, which carries the potash and lime that plain sand lacks.
        // Hammered wrought-iron plate: the only boiler plate there is until a rolling mill exists.
        skilled("forge_iron_plate","minecraft:iron_ingot",20,"spawn_9b5fbb9:iron_plate",19,1600,"forge",Skill.SMITHING,8,true,true,20,.2,"",0);
        recipe("burn_ash","minecraft:birch_log",1,"spawn_9b5fbb9:wood_ash",3,1000,false,"",0);
        recipe("ash_from_charcoal","minecraft:charcoal",2,"spawn_9b5fbb9:wood_ash",1,600,false,"",0);
        skilled("forge_musket_barrel_blank","minecraft:iron_ingot",3,"spawn_9b5fbb9:musket_barrel_blank",1,1600,"forge",Skill.SMITHING,0,true,true,20,.15,"",0);
        skilled("musket_barrel","spawn_9b5fbb9:musket_barrel_blank",1,"spawn_9b5fbb9:musket_barrel",1,2400,"anvil",Skill.SMITHING,14,true,false,35,.2,"",0);
        skilled("forge_musket_lock_blank","minecraft:iron_ingot",1,"spawn_9b5fbb9:musket_lock_blank",1,1000,"forge",Skill.SMITHING,0,true,true,15,.15,"",0);
        skilled("musket_lock","spawn_9b5fbb9:musket_lock_blank",1,"spawn_9b5fbb9:musket_lock",1,2000,"anvil",Skill.SMITHING,14,true,false,35,.2,"minecraft:flint",1);
        skilled("forge_machine_parts_blank","minecraft:iron_ingot",2,"spawn_9b5fbb9:machine_parts_blank",1,1600,"forge",Skill.SMITHING,0,true,true,20,.15,"",0);
        skilled("machine_parts","spawn_9b5fbb9:machine_parts_blank",1,"spawn_9b5fbb9:machine_parts",1,3000,"anvil",Skill.SMITHING,14,true,false,40,.2,"spawn_9b5fbb9:metal_rivet",2);
        skilled("cast_musket_balls","spawn_9b5fbb9:lead_ingot",1,"spawn_9b5fbb9:musket_ball",32,600,"forge",Skill.SMITHING,0,true,true,10,.1,"",0);
        skilled("forge_round_shot","minecraft:iron_ingot",3,"spawn_9b5fbb9:round_shot",1,1200,"forge",Skill.SMITHING,4,true,true,15,.15,"",0);
        // A cubic metre of rubble dresses down to eight 40 kg balls, half a day of a mason's work each.
        skilled("dress_stone_shot","minecraft:cobblestone",1,"spawn_9b5fbb9:stone_shot",8,32000,"anvil",Skill.MINING,2,true,false,15,.15,"",0);
        skilled("solder","spawn_9b5fbb9:tin_ingot",3,"spawn_9b5fbb9:solder",5,800,"forge",Skill.SMITHING,2,true,true,10,.1,"spawn_9b5fbb9:lead_ingot",2);
        DEFAULT_RECIPES=Collections.unmodifiableMap(new LinkedHashMap<>(RECIPES));RECIPES=DEFAULT_RECIPES;
    }
    private static void recipe(String key,String input,int count,String output,int amount,long ticks,boolean forge,String second,int secondCount){RECIPES.put(key,new Recipe(input,count,output,amount,ticks,forge,second,secondCount));}
    private static void skilled(String key,String input,int count,String output,int amount,long ticks,String station,Skill skill,int minimum,boolean manual,boolean heated,double xp,double manipulation,String second,int secondCount){RECIPES.put(key,new Recipe(input,count,output,amount,ticks,station.equals("forge"),second,secondCount,station,skill,minimum,manual,heated,xp,manipulation));}
    public static boolean requiresStation(ItemStack stack){String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();return id.equals("spawn_9b5fbb9:fired_pot")||id.equals("spawn_9b5fbb9:unfired_pot")||id.equals("spawn_9b5fbb9:metal_rivet")||id.equals("spawn_9b5fbb9:crossbow_stock")||id.endsWith("_blank")||textile(id)||smithMinimum(id)>0;}
    private static boolean textile(String id){return id.equals("spawn_9b5fbb9:yarn")||id.equals("spawn_9b5fbb9:wool_cloth")||id.equals("spawn_9b5fbb9:fiber_cloth")||id.startsWith("spawn_9b5fbb9:wool_")||id.startsWith("spawn_9b5fbb9:fiber_")&&Set.of("shirt","pants","jacket","shoes").stream().anyMatch(id::endsWith);}
    private static int smithMinimum(String id){String path=Identifier.parse(id).getPath();if(Set.of("shield","crossbow","shears","bucket","flint_and_steel").contains(path))return 12;if(path.equals("spyglass")||path.equals("brush"))return 4;boolean armor=Set.of("helmet","chestplate","leggings","boots","horse_armor","nautilus_armor").stream().anyMatch(piece->path.endsWith("_"+piece));boolean tool=Set.of("pickaxe","axe","hoe","shovel","sword","spear").stream().anyMatch(piece->path.endsWith("_"+piece));if(!armor&&!tool)return 0;int level=path.startsWith("copper_")?4:path.startsWith("bronze_")?8:path.startsWith("iron_")||path.startsWith("forged_iron_")?12:path.startsWith("golden_")||path.startsWith("diamond_")||path.startsWith("chainmail_")?12:0;return armor&&level>0?path.startsWith("iron_")||path.startsWith("forged_iron_")?14:12:level;}
    private static void validateRequirements(Recipe r){
        if((r.station.equals("loom")||r.station.equals("anvil"))&&(!r.manual||r.heated))throw new IllegalArgumentException("Loom/anvil require unheated manual work");
        if(r.practice>0&&(!r.manual||r.skill==null))throw new IllegalArgumentException("Practice requires actual manual skilled work");
        // Protect the actual material/station contract of shipped workshop stages.
        // Duration and practice awards remain independently tunable.
        var known=DEFAULT_RECIPES.values().stream().filter(base->base.output.equals(r.output)).toList();
        if(known.stream().anyMatch(base->base.output.endsWith("_blank")&&base.station.equals("forge"))&&(!r.station.equals("forge")||!r.manual||!r.heated||r.skill!=Skill.SMITHING))throw new IllegalArgumentException("Equipment blank formation requires heated manual smithing at a forge");
        var bindings=known.stream().filter(base->!base.second.isEmpty()&&(smithMinimum(base.output)>0||textile(base.output)||base.output.equals("spawn_9b5fbb9:crossbow_stock"))).toList();
        if(!bindings.isEmpty()&&bindings.stream().noneMatch(base->base.second.equals(r.second)&&r.secondCount>=base.secondCount))throw new IllegalArgumentException("Workshop finishing requires its matching binding or riveting supplies");
        if(r.output.equals("spawn_9b5fbb9:fired_pot")&&(!r.input.equals("minecraft:clay_ball")||r.count<1||!r.station.equals("kiln")||!r.manual||!r.heated||r.skill!=Skill.POTTERY||r.minimumManipulation<.25))throw new IllegalArgumentException("Pottery needs clay, a tended fueled kiln and 25% manipulation");
        int minimum=smithMinimum(r.output);String outPath=Identifier.parse(r.output).getPath();String expectedBlank="spawn_9b5fbb9:"+(outPath.startsWith("iron_")&&!outPath.equals("iron_spear")?"forged_"+outPath:outPath)+"_blank";
        if(minimum>0&&(!r.station.equals("anvil")||!r.manual||r.skill!=Skill.SMITHING||r.minimumSkill<minimum||!r.input.equals(expectedBlank)))throw new IllegalArgumentException("Finished metal equipment needs a practiced smith, matching blank and anvil");
        int weavingMinimum=r.output.endsWith("_jacket")?4:r.output.endsWith("_pants")?2:0;
        if(textile(r.output)&&(!r.station.equals("loom")||!r.manual||r.heated||r.skill!=Skill.WEAVING||r.minimumSkill<weavingMinimum))throw new IllegalArgumentException("Textiles require practiced manual loom work");
        if(r.output.equals("spawn_9b5fbb9:crossbow_stock")&&(!r.station.equals("loom")||!r.manual||r.heated||r.skill!=Skill.CONSTRUCTION||r.minimumSkill<2))throw new IllegalArgumentException("Crossbow stock assembly needs construction2 at a loom");
    }
    private static String id(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    public static void init(){NeoForge.EVENT_BUS.addListener(Production::interact);NeoForge.EVENT_BUS.addListener(Production::stationDrops);}
    private static void stationDrops(net.neoforged.neoforge.event.level.BlockDropsEvent event){if(event.getBlockEntity() instanceof WorkstationEntity station)for(var drop:event.getDrops())if(drop.getItem().is(station.getBlockState().getBlock().asItem())){var data=ItemAging.data(drop.getItem());data.putInt("workstationWear",station.wear);ItemAging.data(drop.getItem(),data);}}
    public static ItemStack finish(String id,ItemStack input,ServerLevel level){Recipe r=RECIPES.get(id);if(r==null)return input;ItemStack out=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(r.output)),r.amount);ItemAging.inherit(out,List.of(input),level);return out;}
    public static String stationId(WorkstationEntity station){var id=BuiltInRegistries.BLOCK.getKey(station.getBlockState().getBlock());return id.getNamespace().equals(SurvivalContent.MODID)?id.getPath():"";}
    public static boolean canWork(net.minecraft.world.entity.LivingEntity actor,Recipe recipe){var body=SurvivalApi.state(actor);return body.manipulation()>=recipe.minimumManipulation&&body.mental()>.05&&(recipe.skill==null||SkillApi.level(actor,recipe.skill)>=recipe.minimumSkill);}
    public static String start(Player actor,WorkstationEntity be,String recipe){return start((net.minecraft.world.entity.LivingEntity)actor,be,recipe);}
    public static String start(net.minecraft.world.entity.LivingEntity actor,WorkstationEntity be,String recipe){
        if(!(actor.level() instanceof ServerLevel level)||be.getLevel()!=level||!actor.isAlive()||!SurvivalApi.isHumanoid(actor)||actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()))>36)return "A living humanoid must stand within six blocks in the same world.";
        Recipe r=RECIPES.get(recipe);if(r==null)return "Unknown process.";
        if(be.wear>=256)return "Workstation is worn out; replace its masonry.";
        if(!be.input.isEmpty()||!be.output.isEmpty())return "Workstation occupied.";
        try{validateRequirements(r);}catch(IllegalArgumentException invalid){return invalid.getMessage();}
        if(!stationId(be).equals(r.station))return "This process needs a "+r.station+".";
        ItemStack held=actor.getMainHandItem(),other=actor.getOffhandItem();
        if(!id(held).equals(r.input)||held.getCount()<r.count)return "Missing input.";
        if(r.second.equals("@safe_water")){if(Pottery.waterUnits(other)<r.secondCount||!Pottery.safeWater(other)||ItemAging.data(other).getBooleanOr("sealed",false))return "Offhand needs an open pot of boiled water.";}
        else if(!r.second.isEmpty()&&(!id(other).equals(r.second)||other.getCount()<r.secondCount))return "Offhand needs "+r.secondCount+" "+r.second;
        if(r.skill!=null&&SkillApi.level(actor,r.skill)<r.minimumSkill)return "Requires "+r.skill.name().toLowerCase(Locale.ROOT)+" level "+r.minimumSkill+".";
        if(!canWork(actor,r))return "Your body cannot perform this work (manipulation needs "+Math.round(r.minimumManipulation*100)+"%).";
        if(r.heated&&be.fuel<r.ticks)return "Add fuel first (one charcoal supplies 8000 heat ticks).";
        if(r.manual&&!LaborGuard.mayStartForge(actor,be))return "Already performing manual labor; finish or pause the current job first.";
        be.input=held.split(r.count);be.secondary=ItemStack.EMPTY;if(r.second.equals("@safe_water")){for(int i=0;i<r.secondCount;i++)Pottery.consumeWater(other);}else if(r.secondCount>0)be.secondary=other.split(r.secondCount);
        be.recipe=recipe;be.elapsed=0;be.workRemainder=0;be.required=r.ticks;be.last=SurvivalApi.calendar(level);be.configure(r,actor);
        if(r.manual)LaborGuard.claimForge(actor,be);
        be.setChanged();return "Started "+recipe+"; "+be.required+" work ticks at "+r.station+". "+(r.heated?"Heat remaining: "+be.fuel:"No fuel required.");
    }
    public static String fuel(net.minecraft.world.entity.LivingEntity actor,WorkstationEntity be){
        if(!(actor.level() instanceof ServerLevel level)||be.getLevel()!=level||!actor.isAlive()||!SurvivalApi.isHumanoid(actor)||SurvivalApi.state(actor).manipulation()<=.05||actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()))>36||!Set.of("kiln","bloomery","forge").contains(stationId(be)))return "Stand beside a heated kiln, bloomery or forge.";
        if(be.fuel>=128000)return "The heat reserve is full.";
        ItemStack held=actor.getMainHandItem();int heat=held.is(Items.CHARCOAL)||held.is(Items.COAL)||held.is(SurvivalContent.item("coke"))?8000:held.is(Items.STICK)?80:held.is(net.minecraft.tags.ItemTags.LOGS)?96000:0;if(heat==0)return "Hold sticks, a log, charcoal, coal or coke.";
        if(heat>8000&&!stationId(be).equals("kiln"))return "Raw wood cannot reach working heat here; burn charcoal.";
        held.shrink(1);be.fuel=Math.min(128000,be.fuel+heat);be.setChanged();return "Heat reserve: "+be.fuel;
    }
    public static String collect(net.minecraft.world.entity.LivingEntity actor,WorkstationEntity be){
        if(!(actor.level() instanceof ServerLevel level)||be.getLevel()!=level||!actor.isAlive()||!SurvivalApi.isHumanoid(actor)||SurvivalApi.state(actor).manipulation()<=.05||actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()))>36)return "Stand within six blocks in the same world.";
        if(be.output.isEmpty())return "No completed output.";ItemStack output=be.output;be.output=ItemStack.EMPTY;be.setChanged();SurvivalApi.give(actor,output);return "Collected finished product.";
    }
    public static String cancel(net.minecraft.world.entity.LivingEntity actor,WorkstationEntity be){
        if(!(actor.level() instanceof ServerLevel level)||be.getLevel()!=level||!actor.isAlive()||!SurvivalApi.isHumanoid(actor)||actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()))>36)return "Stand within six blocks of the workstation.";
        if(be.input.isEmpty())return "No unfinished input.";
        ItemStack unfinished=be.input,secondary=be.secondary;be.input=ItemStack.EMPTY;be.secondary=ItemStack.EMPTY;be.pendingOutput=ItemStack.EMPTY;be.required=0;be.elapsed=0;be.worker="";be.setChanged();SurvivalApi.give(actor,unfinished);SurvivalApi.give(actor,secondary);return "Removed unfinished input and reserved supplies.";
    }
    public static String stockTrough(net.minecraft.world.entity.LivingEntity actor,WorkstationEntity be){
        if(!(actor.level() instanceof ServerLevel level)||be.getLevel()!=level||!actor.isAlive()||!SurvivalApi.isHumanoid(actor)||!be.getBlockState().is(SurvivalContent.TROUGH.get())||actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()))>16)return "Stand within four blocks of a trough.";
        if(SurvivalApi.state(actor).manipulation()<=.05)return "Your body cannot stock the trough.";
        ItemStack held=actor.getMainHandItem();
            var stock=be.getPersistentData();double feed=stock.getDoubleOr("feed",0),water=stock.getDoubleOr("water",0);
            if(Pottery.waterUnits(held)>0&&water<32&&Pottery.consumeWater(held)){water=Math.min(32,water+1);stock.putDouble("water",water);}
            else if((held.is(Items.WHEAT)||held.is(Items.WHEAT_SEEDS)||held.is(Items.CARROT)||held.is(Items.POTATO)||ItemAging.isFood(held))&&!ItemAging.spoiled(held)&&feed<128){held.shrink(1);feed=Math.min(128,feed+8);stock.putDouble("feed",feed);}
            be.setChanged();return "Trough: feed "+(int)feed+"/128, water "+(int)water+"/32. Supports livestock within four blocks.";
    }
    private static void interact(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(level.getBlockEntity(event.getPos()) instanceof WorkstationEntity be))return;
        event.setCanceled(true);Player player=event.getEntity();if(event.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND)return;
        ItemStack held=event.getItemStack();String message;
        if(be.getBlockState().is(SurvivalContent.TROUGH.get()))message=stockTrough(player,be);
        else if(Set.of("kiln","bloomery","forge").contains(stationId(be))&&(held.is(Items.CHARCOAL)||held.is(Items.COAL)||held.is(SurvivalContent.item("coke"))||held.is(Items.STICK)||(held.is(net.minecraft.tags.ItemTags.LOGS)&&!player.isShiftKeyDown())))message=fuel(player,be);
        else if(held.isEmpty()&&!be.output.isEmpty())message=collect(player,be);
        else if(player.isShiftKeyDown()&&!be.input.isEmpty())message=cancel(player,be);
        else if(!be.input.isEmpty())message="Processing "+be.recipe+": "+be.elapsed+"/"+be.required+" ticks; heat "+be.fuel;
        else {String match=RECIPES.entrySet().stream().filter(e->e.getValue().input.equals(id(held))&&e.getValue().station.equals(stationId(be))&&(e.getValue().second.isEmpty()||e.getValue().second.equals("@safe_water")||e.getValue().second.equals(id(player.getOffhandItem())))).map(Map.Entry::getKey).findFirst().orElse("");message=start(player,be,match);}
        player.sendOverlayMessage(Component.literal(message));
    }
}
