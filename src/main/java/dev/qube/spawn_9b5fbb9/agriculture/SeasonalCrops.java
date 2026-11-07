package dev.qube.spawn_9b5fbb9.agriculture;

import java.util.*;
import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.content.ContentDefinitions;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent;

/** All retained harvestable plants use one elapsed-calendar path, independent of random-tick frequency. */
public final class SeasonalCrops {
    public static final long DAY=24000;
    public record Schedule(int days,int mask,boolean warm,boolean frost,boolean flooded){}
    private static volatile Map<String,Schedule> RULES=new LinkedHashMap<>();
    private static final Map<String,Schedule> DEFAULT_RULES;
    private static final Set<String> WARNED=new HashSet<>();
    static {
        rule("wheat",8,5,false,false,false);rule("carrots",8,3,false,false,false);rule("potatoes",10,1,false,true,false);rule("beetroots",6,5,false,false,false);
        rule("rice_crop",12,1,true,true,true);rule("maize_crop",10,3,true,true,false);rule("fiber_crop",6,3,false,false,false);rule("medicinal_crop",8,1,false,false,false);
        rule("sweet_berry_bush",8,5,false,false,false);rule("cave_vines",8,5,false,false,false);rule("cave_vines_plant",8,5,false,false,false);
        rule("melon_stem",10,3,true,true,false);rule("attached_melon_stem",3,3,true,true,false);rule("pumpkin_stem",12,3,false,false,false);rule("attached_pumpkin_stem",4,3,false,false,false);
        rule("cocoa",12,3,true,true,false);rule("sugar_cane",6,3,true,true,false);rule("brown_mushroom",4,15,false,false,false);rule("red_mushroom",4,15,false,false,false);
        DEFAULT_RULES=Collections.unmodifiableMap(new LinkedHashMap<>(RULES));RULES=DEFAULT_RULES;
    }
    private static String qualified(String crop){return crop.indexOf(':')>=0?crop:(crop.endsWith("_crop")?"spawn_9b5fbb9:":"minecraft:")+crop;}
    private static void rule(String id,int days,int mask,boolean warm,boolean frost,boolean flooded){RULES.put(qualified(id),new Schedule(days,mask,warm,frost,flooded));}
    public static Schedule schedule(String crop){return RULES.get(qualified(crop));}
    public static Map<String,Schedule> schedules(){return Map.copyOf(RULES);}
    public static Map<String,Schedule> definitions(){return schedules();}
    public static void reloadDefinitions(com.google.gson.JsonObject definitions){
        var replacement=new LinkedHashMap<>(DEFAULT_RULES);
        for(var entry:definitions.entrySet()){
            String key=qualified(entry.getKey());ContentDefinitions.block(key);var object=ContentDefinitions.object(entry.getValue(),key);ContentDefinitions.fields(object,"days","mask","warm","frost","flooded");
            Schedule base=DEFAULT_RULES.get(key);if(base==null){if(!object.has("days")||!object.has("mask"))throw new IllegalArgumentException("New crop definitions require days and mask: "+key);base=new Schedule(1,15,false,false,false);}
            replacement.put(key,new Schedule((int)ContentDefinitions.integer(object,"days",base.days,1,3650),(int)ContentDefinitions.integer(object,"mask",base.mask,1,15),ContentDefinitions.bool(object,"warm",base.warm),ContentDefinitions.bool(object,"frost",base.frost),ContentDefinitions.bool(object,"flooded",base.flooded)));
        }
        RULES=Collections.unmodifiableMap(replacement);
    }
    public static int season(long time){return (int)(Math.floorDiv(time,DAY*Config.SEASON_DAYS.get())%4);}
    public static int seasonDay(long time){return (int)(Math.floorDiv(time,DAY)%Config.SEASON_DAYS.get())+1;}
    public static Identifier storage(ServerLevel level){return Identifier.fromNamespaceAndPath("spawn_9b5fbb9","crops/"+level.dimension().identifier().getNamespace()+"/"+level.dimension().identifier().getPath());}
    public static CompoundTag records(ServerLevel level){return level.getServer().getCommandStorage().get(storage(level));}
    public static void save(ServerLevel level,CompoundTag records){level.getServer().getCommandStorage().set(storage(level),records);}
    public static boolean sowable(String crop,long now){
        Schedule r=schedule(crop);if(r==null)return true;
        crop=ContentDefinitions.id(qualified(crop)).getPath();
        int season=season(now),day=seasonDay(now);if((r.mask&(1<<season))==0)return false;
        if(crop.equals("maize_crop")||crop.contains("melon"))return season==0?day>=15:season==1&&day<=18;
        return true;
    }
    public static void init(){NeoForge.EVENT_BUS.addListener(SeasonalCrops::bonemeal);NeoForge.EVENT_BUS.addListener(SeasonalCrops::plant);NeoForge.EVENT_BUS.addListener(SeasonalCrops::placed);NeoForge.EVENT_BUS.addListener(SeasonalCrops::broken);NeoForge.EVENT_BUS.addListener(SeasonalCrops::tooltip);}
    private static void bonemeal(BonemealEvent event){if(isPlant(event.getState()))event.setSuccessful(false);}
    private static boolean isPlant(BlockState state){return RULES.containsKey(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())||state.getBlock() instanceof CropBlock||state.getBlock() instanceof BonemealableBlock;}
    public static boolean requiresCalendarTick(BlockState state){return RULES.containsKey(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());}
    private static void initializeWildBerry(BlockState state,CompoundTag data,long now){
        if(!data.isEmpty())return;
        boolean ripe=state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_3)&&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_3)==3;
        ripe|=state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BERRIES)&&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BERRIES);
        if(ripe){data.putBoolean("established",true);data.putLong("growth",8*DAY);data.putLong("born",now-8*DAY);data.putLong("last",now);}
    }
    private static void plant(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getLevel() instanceof ServerLevel level))return;
        String target=BuiltInRegistries.BLOCK.getKey(level.getBlockState(event.getPos()).getBlock()).getPath();
        if(target.equals("sweet_berry_bush")||target.startsWith("cave_vines")){
            long now=SurvivalApi.calendar(level);var all=records(level);String key=Long.toString(event.getPos().asLong());var data=all.getCompoundOrEmpty(key);initializeWildBerry(level.getBlockState(event.getPos()),data,now);all.put(key,data);save(level,all);long duration=data.getBooleanOr("established",false)?2*DAY:8*DAY;
            if((season(now)!=1&&season(now)!=2)||data.getLongOr("growth",0)<duration){event.setCanceled(true);event.getEntity().sendOverlayMessage(Component.literal("Berries require eight days to establish, then two suitable days between summer/autumn harvests."));return;}
            data.putBoolean("established",true);data.putLong("growth",0);data.putLong("last",now);all.put(key,data);save(level,all);
        }
        String id=BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).getPath();String crop=switch(id){case "wheat_seeds"->"wheat";case "carrot"->"carrots";case "potato"->"potatoes";case "beetroot_seeds"->"beetroots";case "melon_seeds"->"melon_stem";case "pumpkin_seeds"->"pumpkin_stem";case "sweet_berries"->"sweet_berry_bush";case "cocoa_beans"->"cocoa";case "rice_seeds"->"rice_crop";case "maize_seeds"->"maize_crop";case "fiber_seeds"->"fiber_crop";case "medicinal_seeds"->"medicinal_crop";default->id;};
        long now=SurvivalApi.calendar(level);
        boolean invalid=!sowable(crop,now)||(crop.equals("fruit_tree")&&season(now)!=0&&season(now)!=2)||(crop.equals("rice_crop")&&!flooded(level,event.getPos().above()));
        if(invalid){event.setCanceled(true);event.getEntity().sendOverlayMessage(Component.literal("Unsuitable sowing season or rice paddy. Spring/autumn wheat; spring rice; late spring/early summer maize."));}
    }
    private static void placed(BlockEvent.EntityPlaceEvent event){if(event.getLevel() instanceof ServerLevel level&&(isPlant(event.getPlacedBlock())||event.getPlacedBlock().is(SurvivalContent.FRUIT_TREE.get()))){
        var all=records(level);var record=new CompoundTag();long now=SurvivalApi.calendar(level);record.putLong("last",now);record.putLong("born",now);record.putString("type",BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).toString());all.put(Long.toString(event.getPos().asLong()),record);save(level,all);
    }}
    private static void broken(net.neoforged.neoforge.event.level.BlockDropsEvent event){ServerLevel level=event.getLevel();String id=BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).getPath();
        var all=records(level);String key=Long.toString(event.getPos().asLong());var old=all.getCompoundOrEmpty(key);long now=SurvivalApi.calendar(level);
        if(id.equals("sweet_berry_bush")||id.startsWith("cave_vines")){
            initializeWildBerry(event.getState(),old,now);
            long required=(old.getBooleanOr("established",false)?2:8)*DAY;
            if((season(now)!=1&&season(now)!=2)||old.getLongOr("growth",0)<required)event.getDrops().removeIf(drop->drop.getItem().is(Items.SWEET_BERRIES)||drop.getItem().is(Items.GLOW_BERRIES));
        }
        if((id.equals("sugar_cane")||id.contains("mushroom"))&&!old.isEmpty()){
            long required=(id.equals("sugar_cane")?6:4)*DAY;
            if(old.getLongOr("growth",0)<required)event.getDrops().clear();
        }
        Schedule definition=schedule(BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString());boolean frost=definition!=null&&definition.frost&&season(now)==3;
        if(frost&&(id.equals("rice_crop")||old.getLongOr("growth",0)<definition.days*DAY))event.getDrops().clear();
        all.remove(key);save(level,all);
        if(!id.endsWith("_crop")||!event.getState().hasProperty(CropBlock.AGE))return;
        if(frost)return;
        String crop=id.substring(0,id.length()-5);int age=event.getState().getValue(CropBlock.AGE);
        if(definition==null||old.getLongOr("growth",0)<definition.days*DAY)age=0;
        Block.popResource(level,event.getPos(),new ItemStack(SurvivalContent.item(crop+"_seeds"),age==7?3:1));
        if(age==7){String out=switch(crop){case "rice"->"rice_grain";case "fiber"->"plant_fiber";case "medicinal"->"medicinal_leaves";default->"maize";};Block.popResource(level,event.getPos(),new ItemStack(SurvivalContent.item(out),3));}
    }
    public static boolean flooded(ServerLevel level,BlockPos pos){for(Direction d:Direction.Plane.HORIZONTAL)if(level.getFluidState(pos.relative(d)).is(FluidTags.WATER)||level.getFluidState(pos.below().relative(d)).is(FluidTags.WATER))return true;return false;}
    public static boolean randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();Schedule rule=schedule(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if(rule==null){if(state.getBlock() instanceof CropBlock){if(WARNED.add(id))System.getLogger("Survival crops").log(System.Logger.Level.WARNING,"Undeclared crop growth disabled: "+id);return true;}return false;}
        long now=SurvivalApi.calendar(level);var all=records(level);String key=Long.toString(pos.asLong());var data=all.getCompoundOrEmpty(key);
        if(id.equals("sweet_berry_bush")||id.startsWith("cave_vines"))initializeWildBerry(state,data,now);
        String type=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if(!data.getStringOr("type",type).equals(type)){data=new CompoundTag();}
        long last=data.getLongOr("last",now);long growth=data.getLongOr("growth",0);long duration=rule.days*DAY;
        if(data.getBooleanOr("fruited",false)&&(id.contains("melon_stem")||id.contains("pumpkin_stem")))duration=(id.contains("melon")?3:4)*DAY;
        boolean berries=id.equals("sweet_berry_bush")||id.startsWith("cave_vines");
        if(berries&&data.getBooleanOr("established",false))duration=2*DAY;
        boolean moist=!state.is(Blocks.FARMLAND) && (level.getBlockState(pos.below()).is(Blocks.FARMLAND)?level.getBlockState(pos.below()).getValue(FarmlandBlock.MOISTURE)>0:flooded(level,pos)||level.isRainingAt(pos));
        float temperature=level.getBiome(pos).value().getBaseTemperature();
        boolean suitable=moist&&(!rule.flooded||flooded(level,pos))&&(!rule.warm||temperature>=0.5f);
        if(id.contains("mushroom"))suitable=temperature>0.1f&&level.getRawBrightness(pos,0)<13&&(!level.canSeeSky(pos)||flooded(level,pos));
        long elapsed=Math.max(0,now-last);long remaining=Math.min(elapsed,DAY*Config.SEASON_DAYS.get()*4L);
        for(long cursor=now-remaining;cursor<now;){long end=Math.min(now,(Math.floorDiv(cursor,DAY)+1)*DAY);int season=season(cursor);boolean warmSeason=season!=3;
            if(rule.frost&&season==3&&(growth<duration||id.equals("rice_crop"))){level.destroyBlock(pos,false);all.remove(key);save(level,all);return true;}
            if(suitable&&(warmSeason||id.contains("mushroom"))){growth+=end-cursor;}cursor=end;
        }
        data.putString("type",type);data.putLong("last",now);data.putLong("growth",growth);data.putLong("born",data.getLongOr("born",now));
        IntegerProperty age=null;for(var property:state.getProperties())if(property instanceof IntegerProperty p&&p.getName().equals("age")){age=p;break;}
        if(age!=null){int max=Collections.max(age.getPossibleValues());int value=(int)Math.min(max,growth*max/duration);if(value!=state.getValue(age))level.setBlock(pos,state.setValue(age,value),2);}
        if(berries&&state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BERRIES))level.setBlock(pos,state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BERRIES,growth>=duration&&(season(now)==1||season(now)==2)),2);
        if(growth>=duration&&id.equals("sugar_cane")&&level.isEmptyBlock(pos.above())&&level.getBlockState(pos.below(2)).getBlock()!=Blocks.SUGAR_CANE){level.setBlock(pos.above(),Blocks.SUGAR_CANE.defaultBlockState(),3);data.putLong("growth",0);}
        if(growth>=duration&&id.contains("mushroom")&&now>=data.getLongOr("sporeAt",0)){
            int nearby=0;for(BlockPos p:BlockPos.betweenClosed(pos.offset(-2,0,-2),pos.offset(2,0,2)))if(level.getBlockState(p).getBlock()==state.getBlock())nearby++;
            if(nearby<8)for(Direction d:Direction.Plane.HORIZONTAL){BlockPos child=pos.relative(d);if(level.isEmptyBlock(child)&&state.canSurvive(level,child)&&level.getRawBrightness(child,0)<13&&!level.canSeeSky(child)){
                level.setBlock(child,state,3);var seedling=new CompoundTag();seedling.putString("type",type);seedling.putLong("born",now);seedling.putLong("last",now);seedling.putLong("growth",0);all.put(Long.toString(child.asLong()),seedling);break;
            }}data.putLong("sporeAt",now+4*DAY);
        }
        if(growth>=duration&&(id.contains("melon_stem")||id.contains("pumpkin_stem"))){
            Block fruitType=id.contains("melon")?Blocks.MELON:Blocks.PUMPKIN;boolean existing=false;for(Direction d:Direction.Plane.HORIZONTAL)if(level.getBlockState(pos.relative(d)).is(fruitType))existing=true;
            if(!existing)for(Direction d:Direction.Plane.HORIZONTAL){BlockPos fruit=pos.relative(d);if(level.isEmptyBlock(fruit)&&level.getBlockState(fruit.below()).isFaceSturdy(level,fruit.below(),Direction.UP)){level.setBlock(fruit,fruitType.defaultBlockState(),3);data.putLong("growth",0);data.putBoolean("fruited",true);break;}}
        }
        all.put(key,data);save(level,all);return true;
    }
    private static void tooltip(ItemTooltipEvent event){String id=BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).getPath();if(id.endsWith("seeds")||id.equals("fruit_tree"))event.getToolTip().add(Component.literal("Seasonal crop: prepared moist farmland; rice needs adjacent paddy water. Bone meal cannot replace growth days."));}
}
