package dev.qube.spawn_9b5fbb9.content;
import java.util.*;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import dev.qube.spawn_9b5fbb9.survival.LaborGuard;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class Production {
    public record Recipe(String input,int count,String output,int amount,long ticks,boolean forge,String second,int secondCount){}
    public static volatile Map<String,Recipe> RECIPES=new LinkedHashMap<>();
    private static final Map<String,Recipe> DEFAULT_RECIPES;
    public static void reloadDefinitions(com.google.gson.JsonObject definitions){
        var replacement=new LinkedHashMap<>(DEFAULT_RECIPES);
        for(var entry:definitions.entrySet()){
            String key=entry.getKey();ContentDefinitions.id(key);var object=ContentDefinitions.object(entry.getValue(),key);ContentDefinitions.fields(object,"input","count","output","amount","ticks","forge","second","secondCount");
            Recipe base=DEFAULT_RECIPES.get(key);if(base==null){if(!object.has("input")||!object.has("output"))throw new IllegalArgumentException("New processes require input and output: "+key);base=new Recipe("",1,"",1,1000,false,"",0);}
            String input=ContentDefinitions.string(object,"input",base.input),output=ContentDefinitions.string(object,"output",base.output),second=ContentDefinitions.string(object,"second",base.second);
            Item inputItem=ContentDefinitions.item(input),outputItem=ContentDefinitions.item(output);if(dev.qube.spawn_9b5fbb9.world.NaturalWorldRules.prohibited(new ItemStack(outputItem)))throw new IllegalArgumentException("Process output is prohibited: "+output);
            int secondMax=0;if(!second.isEmpty())secondMax=second.equals("@safe_water")?4:new ItemStack(ContentDefinitions.item(second)).getMaxStackSize();
            int secondDefault=second.isEmpty()?0:second.equals(base.second)?base.secondCount:1;
            replacement.put(key,new Recipe(ContentDefinitions.id(input).toString(),(int)ContentDefinitions.integer(object,"count",base.count,1,new ItemStack(inputItem).getMaxStackSize()),ContentDefinitions.id(output).toString(),(int)ContentDefinitions.integer(object,"amount",base.amount,1,new ItemStack(outputItem).getMaxStackSize()),ContentDefinitions.integer(object,"ticks",base.ticks,1,64000),ContentDefinitions.bool(object,"forge",base.forge),second.startsWith("@")?second:second.isEmpty()?"":ContentDefinitions.id(second).toString(),(int)ContentDefinitions.integer(object,"secondCount",secondDefault,second.isEmpty()?0:1,secondMax)));
        }
        RECIPES=Collections.unmodifiableMap(replacement);
    }
    static {
        recipe("fire_pot","spawn_9b5fbb9:unfired_pot",1,"spawn_9b5fbb9:fired_pot",1,2000,false,"",0);
        recipe("fire_brick","minecraft:clay_ball",1,"minecraft:brick",1,1500,false,"",0);
        for(String metal:new String[]{"copper","tin","iron"}){
            recipe("crush_"+metal,metal.equals("tin")?"spawn_9b5fbb9:tin_ore":"minecraft:raw_"+metal,1,"spawn_9b5fbb9:crushed_"+metal,1,1000,false,"",0);
            recipe("roast_"+metal,"spawn_9b5fbb9:crushed_"+metal,1,"spawn_9b5fbb9:roasted_"+metal,1,2000,false,"",0);
            recipe("smelt_"+metal,"spawn_9b5fbb9:roasted_"+metal,1,metal.equals("iron")?"spawn_9b5fbb9:iron_bloom":metal.equals("tin")?"spawn_9b5fbb9:tin_ingot":"minecraft:copper_ingot",1,4000,false,"",0);
        }
        recipe("bronze","minecraft:copper_ingot",3,"spawn_9b5fbb9:bronze_ingot",4,4000,true,"spawn_9b5fbb9:tin_ingot",1);
        recipe("work_bloom","spawn_9b5fbb9:iron_bloom",1,"minecraft:iron_ingot",1,3000,true,"",0);
        recipe("forge_copper_spear","minecraft:copper_ingot",3,"minecraft:copper_spear",1,3000,true,"spawn_9b5fbb9:cordage",1);
        recipe("forge_iron_spear","minecraft:iron_ingot",3,"minecraft:iron_spear",1,3000,true,"spawn_9b5fbb9:cordage",1);
        for(String food:new String[]{"beef","porkchop","chicken","mutton","rabbit","cod","salmon"})recipe("cook_"+food,"minecraft:"+food,1,"minecraft:cooked_"+food,1,1000,false,"",0);
        recipe("dry_fiber","spawn_9b5fbb9:plant_fiber",3,"spawn_9b5fbb9:dried_fiber",3,1000,false,"",0);
        recipe("charcoal","minecraft:oak_log",1,"minecraft:charcoal",2,2000,false,"",0);
        recipe("sterilize_cloth","spawn_9b5fbb9:fiber_cloth",1,"spawn_9b5fbb9:clean_cloth",2,1000,false,"@safe_water",1);
        for(String metal:new String[]{"copper","bronze","forged_iron"})for(String type:new String[]{"pickaxe","axe","hoe"})recipe(metal+"_"+type,metal.equals("forged_iron")?"minecraft:iron_ingot":metal.equals("bronze")?"spawn_9b5fbb9:bronze_ingot":"minecraft:copper_ingot",type.equals("hoe")?2:3,"spawn_9b5fbb9:"+metal+"_"+type,1,3000,true,"spawn_9b5fbb9:cordage",1);
        DEFAULT_RECIPES=Collections.unmodifiableMap(new LinkedHashMap<>(RECIPES));RECIPES=DEFAULT_RECIPES;
    }
    private static void recipe(String key,String input,int count,String output,int amount,long ticks,boolean forge,String second,int secondCount){RECIPES.put(key,new Recipe(input,count,output,amount,ticks,forge,second,secondCount));}
    private static String id(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    public static void init(){NeoForge.EVENT_BUS.addListener(Production::interact);NeoForge.EVENT_BUS.addListener(Production::stationDrops);}
    private static void stationDrops(net.neoforged.neoforge.event.level.BlockDropsEvent event){if(event.getBlockEntity() instanceof WorkstationEntity station)for(var drop:event.getDrops())if(drop.getItem().is(station.getBlockState().getBlock().asItem())){var data=ItemAging.data(drop.getItem());data.putInt("workstationWear",station.wear);ItemAging.data(drop.getItem(),data);}}
    public static ItemStack finish(String id,ItemStack input,ServerLevel level){Recipe r=RECIPES.get(id);if(r==null)return input;ItemStack out=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(r.output)),r.amount);ItemAging.inherit(out,List.of(input),level);return out;}
    public static String start(Player actor,WorkstationEntity be,String recipe){return start((net.minecraft.world.entity.LivingEntity)actor,be,recipe);}
    public static String start(net.minecraft.world.entity.LivingEntity actor,WorkstationEntity be,String recipe){
        if(!(actor.level() instanceof ServerLevel level)||be.getLevel()!=level||!actor.isAlive()||!SurvivalApi.isHumanoid(actor)||actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()))>36)return "A living humanoid must stand within six blocks in the same world.";
        Recipe r=RECIPES.get(recipe);if(r==null)return "Unknown process.";
        if(be.wear>=256)return "Workstation is worn out; replace its masonry.";
        if(!be.input.isEmpty()||!be.output.isEmpty())return "Workstation occupied.";
        if(r.forge&&!be.getBlockState().is(SurvivalContent.FORGE.get()))return "This process needs a forge.";
        if(!be.getBlockState().is(SurvivalContent.FORGE.get())&&!be.getBlockState().is(SurvivalContent.KILN.get()))return "This process needs a kiln or forge.";
        ItemStack held=actor.getMainHandItem(),other=actor.getOffhandItem();
        if(!id(held).equals(r.input)||held.getCount()<r.count)return "Missing input.";
        if(r.second.equals("@safe_water")){if(Pottery.waterUnits(other)<r.secondCount||!Pottery.safeWater(other)||ItemAging.data(other).getBooleanOr("sealed",false))return "Offhand needs an open pot of boiled water.";}
        else if(!r.second.isEmpty()&&(!id(other).equals(r.second)||other.getCount()<r.secondCount))return "Offhand needs "+r.secondCount+" "+r.second;
        var body=SurvivalApi.state(actor);double capacity=Math.min(body.manipulation(),body.mental());if(capacity<=0.05)return "Your body cannot perform this work.";
        if(be.fuel<r.ticks)return "Add charcoal first (one charcoal supplies 8000 heat ticks).";
        if(r.forge&&!LaborGuard.mayStartForge(actor,be))return "Already performing manual labor; finish or pause the current job first.";
        be.input=held.split(r.count);if(r.second.equals("@safe_water")){for(int i=0;i<r.secondCount;i++)Pottery.consumeWater(other);}else if(r.secondCount>0)other.shrink(r.secondCount);be.recipe=recipe;be.elapsed=0;be.workRemainder=0;be.required=r.forge?r.ticks:(long)(r.ticks/Math.max(.1,capacity));be.last=SurvivalApi.calendar(level);be.worker=r.forge?actor.getUUID().toString():"";
        if(r.forge)LaborGuard.claimForge(actor,be);
        if(!r.forge)body.hunger=Math.max(0,body.hunger-(be.required/1000.0)*0.5);SurvivalApi.save(actor,body);be.setChanged();return "Started "+recipe+"; "+be.required+" work ticks. Heat remaining: "+be.fuel;
    }
    public static String fuel(net.minecraft.world.entity.LivingEntity actor,WorkstationEntity be){
        if(!(actor.level() instanceof ServerLevel level)||be.getLevel()!=level||!actor.isAlive()||!SurvivalApi.isHumanoid(actor)||SurvivalApi.state(actor).manipulation()<=.05||actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()))>36||be.getBlockState().is(SurvivalContent.TROUGH.get()))return "Stand beside a kiln or forge in the same world.";
        if(be.fuel>=64000)return "The heat reserve is full.";
        ItemStack held=actor.getMainHandItem();int heat=held.is(Items.CHARCOAL)?8000:held.is(Items.STICK)?1000:held.is(net.minecraft.tags.ItemTags.LOGS)?4000:0;if(heat==0)return "Hold sticks, a log, or charcoal.";
        held.shrink(1);be.fuel=Math.min(64000,be.fuel+heat);be.setChanged();return "Heat reserve: "+be.fuel;
    }
    public static String collect(net.minecraft.world.entity.LivingEntity actor,WorkstationEntity be){
        if(!(actor.level() instanceof ServerLevel level)||be.getLevel()!=level||!actor.isAlive()||!SurvivalApi.isHumanoid(actor)||SurvivalApi.state(actor).manipulation()<=.05||actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()))>36)return "Stand within six blocks in the same world.";
        if(be.output.isEmpty())return "No completed output.";ItemStack output=be.output;be.output=ItemStack.EMPTY;be.setChanged();SurvivalApi.give(actor,output);return "Collected finished product.";
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
        else if(held.is(Items.CHARCOAL)||held.is(Items.STICK)||(held.is(net.minecraft.tags.ItemTags.LOGS)&&!player.isShiftKeyDown()))message=fuel(player,be);
        else if(held.isEmpty()&&!be.output.isEmpty())message=collect(player,be);
        else if(player.isShiftKeyDown()&&!be.input.isEmpty()){ItemStack unfinished=be.input;be.input=ItemStack.EMPTY;be.required=0;be.elapsed=0;be.worker="";be.setChanged();SurvivalApi.give(player,unfinished);message="Removed unfinished input.";}
        else if(!be.input.isEmpty())message="Processing "+be.recipe+": "+be.elapsed+"/"+be.required+" ticks; heat "+be.fuel;
        else {String match=RECIPES.entrySet().stream().filter(e->e.getValue().input.equals(id(held))&&(!e.getValue().forge||be.getBlockState().is(SurvivalContent.FORGE.get()))&&(e.getValue().second.isEmpty()||e.getValue().second.equals("@safe_water")||e.getValue().second.equals(id(player.getOffhandItem())))).map(Map.Entry::getKey).findFirst().orElse("");message=start(player,be,match);}
        player.sendOverlayMessage(Component.literal(message));
    }
}
