package dev.qube.spawn_9b5fbb9.agriculture;

import java.util.*;
import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import dev.qube.spawn_9b5fbb9.content.ContentDefinitions;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill;
import dev.qube.spawn_9b5fbb9.survival.LaborGuard;
import dev.qube.spawn_9b5fbb9.controllers.WorldActions;
import dev.qube.spawn_9b5fbb9.environment.*;
import net.minecraft.world.entity.LivingEntity;
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
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** All retained harvestable plants use one elapsed-calendar path, independent of random-tick frequency. */
public final class SeasonalCrops {
    public static final long DAY=24000;
    public record Schedule(int days,int mask,boolean warm,boolean frost,boolean flooded){}
    private static volatile Map<String,Schedule> RULES=new LinkedHashMap<>();
    private static final Map<String,Schedule> DEFAULT_RULES;
    private static final Set<String> WARNED=new HashSet<>();
    private record NativePractice(ServerLevel level,BlockPos pos,String block,Skill skill,double xp,long time,String main,int mainCount,int mainDamage,String off,int offCount,int offDamage,boolean tool){}
    private static final Map<LivingEntity,List<NativePractice>> PENDING=new WeakHashMap<>();
    static {
        rule("wheat",8,5,false,false,false);rule("carrots",8,3,false,false,false);rule("potatoes",10,1,false,true,false);rule("beetroots",6,5,false,false,false);
        rule("rice_crop",12,1,true,true,true);rule("maize_crop",10,3,true,true,false);rule("fiber_crop",6,3,false,false,false);rule("medicinal_crop",8,1,false,false,false);
        rule("flax_crop",8,3,false,false,false);
        // Field peas: a cool-season pulse, sown in early spring (or autumn where winters are mild), hardy to frost, ripe in six days.
        rule("pea_crop",6,5,false,false,false);rule("yarrow_crop",8,1,false,false,false);rule("bramble_crop",8,3,false,false,false);
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
    private static final Map<ServerLevel,Identifier> STORAGE=new WeakHashMap<>();
    /** Where a world made before records were kept by chunk holds them. Asked for with every crop tick, so the name is made once. */
    public static Identifier storage(ServerLevel level){return STORAGE.computeIfAbsent(level,world->Identifier.fromNamespaceAndPath("spawn_9b5fbb9","crops/"+world.dimension().identifier().getNamespace()+"/"+world.dimension().identifier().getPath()));}
    /** Crop and soil records, each kept in the chunk it stands in. A key is a block position, or {@code soil/} and one.
     * What an older world still holds in its one world-wide store is moved across the first time it is asked for. */
    public static final class Records {
        private final ServerLevel level;
        private Records(ServerLevel level){this.level=level;}
        private static boolean soil(String key){return key.startsWith("soil/");}
        private static String place(String key){return soil(key)?key.substring(5):key;}
        private static BlockPos at(String key){return BlockPos.of(Long.parseLong(place(key)));}
        private CompoundTag old(){return level.getServer().getCommandStorage().get(storage(level));}
        private CompoundTag forget(String key){
            var old=old();if(old.isEmpty()||!old.contains(key))return null;
            var found=old.getCompoundOrEmpty(key);old.remove(key);level.getServer().getCommandStorage().set(storage(level),old);return found;
        }
        /** The record itself where there is one, to be changed and then {@link #put}; otherwise an empty one that is not kept. */
        public CompoundTag getCompoundOrEmpty(String key){
            var held=dev.qube.spawn_9b5fbb9.world.ChunkRecords.peek(level,at(key),soil(key)?"soil":"crop");
            var found=held==null?null:held.getCompound(place(key)).orElse(null);if(found!=null)return found;
            found=forget(key);if(found==null)return new CompoundTag();
            dev.qube.spawn_9b5fbb9.world.ChunkRecords.put(level,at(key),soil(key)?"soil":"crop",place(key),found);return found;
        }
        public void put(String key,CompoundTag record){forget(key);dev.qube.spawn_9b5fbb9.world.ChunkRecords.put(level,at(key),soil(key)?"soil":"crop",place(key),record);}
        public void remove(String key){forget(key);dev.qube.spawn_9b5fbb9.world.ChunkRecords.remove(level,at(key),soil(key)?"soil":"crop",place(key));}
    }
    public static Records records(ServerLevel level){return new Records(level);}
    public static boolean sowable(String crop,long now){
        Schedule r=schedule(crop);if(r==null)return true;
        crop=ContentDefinitions.id(qualified(crop)).getPath();
        int season=season(now),day=seasonDay(now);if((r.mask&(1<<season))==0)return false;
        if(crop.equals("maize_crop")||crop.contains("melon"))return season==0?day>=15:season==1&&day<=18;
        return true;
    }
    public static void init(){NeoForge.EVENT_BUS.addListener(SeasonalCrops::bonemeal);NeoForge.EVENT_BUS.addListener(SeasonalCrops::plant);NeoForge.EVENT_BUS.addListener(SeasonalCrops::placed);NeoForge.EVENT_BUS.addListener(SeasonalCrops::broken);NeoForge.EVENT_BUS.addListener(SeasonalCrops::tooltip);NeoForge.EVENT_BUS.addListener(SeasonalCrops::nativeHarvest);NeoForge.EVENT_BUS.addListener(SeasonalCrops::nativeTill);NeoForge.EVENT_BUS.addListener(SeasonalCrops::practiceTick);}
    private static void bonemeal(BonemealEvent event){if(isPlant(event.getState()))event.setSuccessful(false);}
    private static boolean isPlant(BlockState state){return requiresCalendarTick(state)||state.getBlock() instanceof CropBlock||state.getBlock() instanceof BonemealableBlock;}
    /** Which blocks the current rules cover, remembered per block: the answer is asked for every random tick the world takes. */
    private record CalendarBlocks(Map<String,Schedule> rules,Map<Block,Boolean> known){}
    private static volatile CalendarBlocks CALENDAR_BLOCKS=new CalendarBlocks(null,Map.of());
    public static boolean requiresCalendarTick(BlockState state){
        var rules=RULES;var cache=CALENDAR_BLOCKS;
        if(cache.rules()!=rules){cache=new CalendarBlocks(rules,new java.util.concurrent.ConcurrentHashMap<>());CALENDAR_BLOCKS=cache;}
        Block block=state.getBlock();Boolean known=cache.known().get(block);if(known!=null)return known;
        var key=BuiltInRegistries.BLOCK.getResourceKey(block);if(key.isEmpty())return false; // not registered yet: ask again later
        boolean covered=rules.containsKey(key.get().identifier().toString());cache.known().put(block,covered);return covered;
    }
    private static void initializeWildBerry(BlockState state,CompoundTag data,long now){
        if(!data.isEmpty())return;
        boolean ripe=state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_3)&&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_3)==3;
        ripe|=state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BERRIES)&&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BERRIES);
        if(ripe){data.putBoolean("established",true);data.putLong("growth",8*DAY);data.putLong("born",now-8*DAY);data.putLong("last",now);}
    }
    private static void plant(PlayerInteractEvent.RightClickBlock event){if(event.getEntity().isSpectator())return;
        if(!(event.getLevel() instanceof ServerLevel level))return;
        var actor=event.getEntity();var targetState=level.getBlockState(event.getPos());String target=BuiltInRegistries.BLOCK.getKey(targetState.getBlock()).getPath();
        if(!SurvivalApi.exempt(actor)&&event.getItemStack().is(net.minecraft.tags.ItemTags.HOES)&&(targetState.is(Blocks.DIRT)||targetState.is(Blocks.GRASS_BLOCK)||targetState.is(Blocks.DIRT_PATH))){event.setCanceled(true);actor.sendOverlayMessage(Component.literal(WorldActions.start(actor,"till",event.getPos(),event.getHand()).message()));return;}
        if(target.equals("sweet_berry_bush")||target.startsWith("cave_vines")){
            if(!SurvivalApi.exempt(actor)){var body=SurvivalApi.state(actor);if(Math.min(body.manipulation(),body.mental())<=.05||LaborGuard.worldBusy(actor)||LaborGuard.forgeBusy(actor)||LaborGuard.gridBusy(actor)){event.setCanceled(true);actor.sendOverlayMessage(Component.literal("Finish the current manual work and use able hands to harvest."));return;}}
            long now=SurvivalApi.calendar(level);var all=records(level);String key=Long.toString(event.getPos().asLong());var data=all.getCompoundOrEmpty(key);initializeWildBerry(level.getBlockState(event.getPos()),data,now);all.put(key,data);long duration=data.getBooleanOr("established",false)?2*DAY:8*DAY;
            if((season(now)!=1&&season(now)!=2)||data.getLongOr("growth",0)<duration){event.setCanceled(true);event.getEntity().sendOverlayMessage(Component.literal("Berries require eight days to establish, then two suitable days between summer/autumn harvests."));return;}
            event.setCanceled(true);actor.sendOverlayMessage(Component.literal(WorldActions.start(actor,"harvest",event.getPos(),event.getHand()).message()));return;
        }
        if(!SurvivalApi.exempt(actor)&&event.getItemStack().getItem() instanceof BlockItem blockItem&&(schedule(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString())!=null||blockItem.getBlock() instanceof dev.qube.spawn_9b5fbb9.content.FruitTreeBlock)&&(!targetState.hasBlockEntity()||actor.isShiftKeyDown())){
            var context=new net.minecraft.world.item.context.BlockPlaceContext(new net.minecraft.world.item.context.UseOnContext(actor,event.getHand(),event.getHitVec()));event.setCanceled(true);actor.sendOverlayMessage(Component.literal(WorldActions.start(actor,"sow",context.getClickedPos(),event.getHand()).message()));return;
        }
        String id=BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).getPath();String crop=switch(id){case "wheat_seeds"->"wheat";case "carrot"->"carrots";case "potato"->"potatoes";case "beetroot_seeds"->"beetroots";case "melon_seeds"->"melon_stem";case "pumpkin_seeds"->"pumpkin_stem";case "sweet_berries"->"sweet_berry_bush";case "cocoa_beans"->"cocoa";case "rice_seeds"->"rice_crop";case "maize_seeds"->"maize_crop";case "fiber_seeds"->"fiber_crop";case "medicinal_seeds"->"medicinal_crop";default->id;};
        long now=SurvivalApi.calendar(level);
        boolean invalid=!sowable(crop,now)||(crop.equals("fruit_tree")&&season(now)!=0&&season(now)!=2)||(crop.equals("rice_crop")&&!flooded(level,event.getPos().above()));
        if(invalid){event.setCanceled(true);event.getEntity().sendOverlayMessage(Component.literal("Unsuitable sowing season or rice paddy. Spring/autumn wheat; spring rice; late spring/early summer maize."));}
    }
    private static void placed(BlockEvent.EntityPlaceEvent event){if(event.getLevel() instanceof ServerLevel level&&(isPlant(event.getPlacedBlock())||event.getPlacedBlock().getBlock() instanceof dev.qube.spawn_9b5fbb9.content.FruitTreeBlock)){
        var all=records(level);var record=new CompoundTag();long now=SurvivalApi.calendar(level);record.putLong("last",now);record.putLong("born",now);record.putBoolean("sown",event.getEntity() instanceof LivingEntity);record.putString("type",BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).toString());all.put(Long.toString(event.getPos().asLong()),record);
        if(event.getEntity() instanceof LivingEntity actor&&!WorldActions.completing(actor)&&(requiresCalendarTick(event.getPlacedBlock())||event.getPlacedBlock().getBlock() instanceof dev.qube.spawn_9b5fbb9.content.FruitTreeBlock))queuePractice(actor,event.getPos(),event.getPlacedBlock(),Skill.FARMING,3,false);
    }}
    private static void broken(net.neoforged.neoforge.event.level.BlockDropsEvent event){ServerLevel level=event.getLevel();String id=BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).getPath();
        var all=records(level);String key=Long.toString(event.getPos().asLong());var old=all.getCompoundOrEmpty(key);long now=SurvivalApi.calendar(level);
        if(id.equals("sweet_berry_bush")||id.startsWith("cave_vines")){
            initializeWildBerry(event.getState(),old,now);
            long required=(old.getBooleanOr("established",false)?2:8)*DAY;
            if((season(now)!=1&&season(now)!=2)||old.getLongOr("growth",0)<required)event.getDrops().removeIf(drop->drop.getItem().is(Items.SWEET_BERRIES)||drop.getItem().is(Items.GLOW_BERRIES));
        }
        // What was planted, or grew from what was planted, has to be waited for. What was found growing wild is there
        // to be cut, and a joint of cane that grew out of a ripe one is itself the harvest.
        if((id.equals("sugar_cane")||id.contains("mushroom"))&&!old.isEmpty()&&!old.getBooleanOr("wild",false)&&!old.getBooleanOr("harvestableStage",false)){
            long required=(id.equals("sugar_cane")?6:4)*DAY;
            if(old.getLongOr("growth",0)<required)event.getDrops().clear();
        }
        Schedule definition=schedule(BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString());boolean frost=!WorldEnvironment.active(level)&&definition!=null&&definition.frost&&season(now)==3;
        if(frost&&(id.equals("rice_crop")||old.getLongOr("growth",0)<definition.days*DAY))event.getDrops().clear();
        if(!old.isEmpty()){all.remove(key);}
        // Practice belongs to the timed work transaction; a raw block-destruction event carries no labor credit.
        if(!id.endsWith("_crop")||!event.getState().hasProperty(CropBlock.AGE))return;
        if(frost)return;
        String crop=id.substring(0,id.length()-5);int age=event.getState().getValue(CropBlock.AGE);
        if(definition==null||old.getLongOr("growth",0)<definition.days*DAY)age=0;
        Block.popResource(level,event.getPos(),new ItemStack(SurvivalContent.item(crop+"_seeds"),age==7?3:1));
        if(age==7){String out=switch(crop){case "rice"->"rice_grain";case "fiber"->"plant_fiber";case "medicinal"->"medicinal_leaves";case "flax"->"flax_fiber";case "pea"->"peas";case "yarrow"->"yarrow_leaves";case "bramble"->"blackberries";default->"maize";};Block.popResource(level,event.getPos(),new ItemStack(SurvivalContent.item(out),3));}
    }
    public static boolean berries(BlockState state){String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();return id.equals("sweet_berry_bush")||id.equals("cave_vines")||id.equals("cave_vines_plant");}
    public static Skill harvestSkill(ServerLevel level,BlockPos pos,BlockState state){if(state.getBlock() instanceof dev.qube.spawn_9b5fbb9.content.FruitTreeBlock)return Skill.FORAGING;return state.getBlock() instanceof CropBlock||state.getBlock() instanceof StemBlock||state.is(Blocks.MELON)||state.is(Blocks.PUMPKIN)||records(level).getCompoundOrEmpty(Long.toString(pos.asLong())).getBooleanOr("sown",false)?Skill.FARMING:Skill.FORAGING;}
    public static boolean harvestReady(ServerLevel level,BlockPos pos,BlockState state){
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();var record=records(level).getCompoundOrEmpty(Long.toString(pos.asLong()));long now=SurvivalApi.calendar(level);
        if(state.is(Blocks.MELON)||state.is(Blocks.PUMPKIN))return record.getBooleanOr("grownFruit",false);
        Schedule rule=schedule(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());if(rule==null)return false;
        if(berries(state)){initializeWildBerry(state,record,now);long days=(record.getBooleanOr("established",false)?2:8)*DAY;return (season(now)==1||season(now)==2)&&record.getLongOr("growth",0)>=days;}
        long growth=record.getLongOr("growth",0),required=rule.days*DAY;if(!WorldEnvironment.active(level)&&rule.frost&&season(now)==3&&(id.equals("rice_crop")||growth<required))return false;
        if(id.equals("sugar_cane"))return growth>=required||record.getBooleanOr("harvestableStage",false)||level.getBlockState(pos.above()).is(Blocks.SUGAR_CANE)&&records(level).getCompoundOrEmpty(Long.toString(pos.above().asLong())).getBooleanOr("harvestableStage",false);
        if(growth<required)return false;
        for(var property:state.getProperties())if(property instanceof IntegerProperty age&&age.getName().equals("age"))return state.getValue(age).equals(Collections.max(age.getPossibleValues()));
        return true;
    }
    /** Native berry yield/reset, callable by the same timed LivingEntity action without a Player proxy. */
    public static boolean harvestBerries(LivingEntity actor,BlockPos pos){
        if(!(actor.level() instanceof ServerLevel level)||!WorldActions.completing(actor))return false;var state=level.getBlockState(pos);if(!berries(state)||!harvestReady(level,pos,state))return false;
        var age=net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_3;var hasBerries=net.minecraft.world.level.block.state.properties.BlockStateProperties.BERRIES;var after=state.hasProperty(age)?state.setValue(age,1):state.hasProperty(hasBerries)?state.setValue(hasBerries,false):null;if(after==null||!level.setBlock(pos,after,3))return false;
        var all=records(level);var record=all.getCompoundOrEmpty(Long.toString(pos.asLong()));record.putBoolean("established",true);record.putLong("growth",0);record.putLong("last",SurvivalApi.calendar(level));all.put(Long.toString(pos.asLong()),record);boolean sweet=state.is(Blocks.SWEET_BERRY_BUSH);Block.popResource(level,pos,new ItemStack(sweet?Items.SWEET_BERRIES:Items.GLOW_BERRIES,sweet?2+level.getRandom().nextInt(2):1));return true;
    }
    private static void nativeHarvest(PlayerInteractEvent.LeftClickBlock event){
        if(!(event.getLevel() instanceof ServerLevel level)||SurvivalApi.exempt(event.getEntity())||event.getAction()!=PlayerInteractEvent.LeftClickBlock.Action.START)return;var state=level.getBlockState(event.getPos());
        if(state.getBlock() instanceof dev.qube.spawn_9b5fbb9.content.GroundResourceBlock||state.getBlock() instanceof dev.qube.spawn_9b5fbb9.content.FruitTreeBlock)return;
        if(!requiresCalendarTick(state)&&!state.is(Blocks.MELON)&&!state.is(Blocks.PUMPKIN))return;if(berries(state)&&!harvestReady(level,event.getPos(),state))return;event.setCanceled(true);event.getEntity().sendOverlayMessage(Component.literal(WorldActions.start(event.getEntity(),"harvest",event.getPos()).message()));
    }
    private static void nativeTill(BlockEvent.BlockToolModificationEvent event){if(!event.isSimulated()&&event.getLevel() instanceof ServerLevel&&event.getPlayer()!=null&&event.getFinalState()!=null&&event.getFinalState().is(Blocks.FARMLAND)&&!event.getState().is(Blocks.FARMLAND)&&!WorldActions.completing(event.getPlayer()))queuePractice(event.getPlayer(),event.getPos(),event.getFinalState(),Skill.FARMING,2,true);}
    private static String itemId(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    private static void queuePractice(LivingEntity actor,BlockPos pos,BlockState result,Skill skill,double xp,boolean tool){
        if(!(actor.level() instanceof ServerLevel level)||!SurvivalApi.isHumanoid(actor)||SurvivalApi.exempt(actor))return;var main=actor.getMainHandItem();var off=actor.getOffhandItem();var list=PENDING.computeIfAbsent(actor,a->new ArrayList<>());long time=level.getGameTime();list.removeIf(p->p.time==time&&p.pos.equals(pos));if(list.size()>=16)return;list.add(new NativePractice(level,pos.immutable(),BuiltInRegistries.BLOCK.getKey(result.getBlock()).toString(),skill,xp,time,itemId(main),main.getCount(),main.getDamageValue(),itemId(off),off.getCount(),off.getDamageValue(),tool));
    }
    private static boolean spent(ItemStack after,String id,int count,int damage,boolean tool){return count>0&&(after.isEmpty()||itemId(after).equals(id)&&(after.getCount()<count||tool&&after.getDamageValue()>damage));}
    private static void practiceTick(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof LivingEntity actor)||!(actor.level() instanceof ServerLevel level))return;var list=PENDING.remove(actor);if(list==null)return;
        for(var pending:list){if(level!=pending.level||!actor.isAlive()||level.getGameTime()-pending.time>2||!level.hasChunkAt(pending.pos)||!BuiltInRegistries.BLOCK.getKey(level.getBlockState(pending.pos).getBlock()).toString().equals(pending.block))continue;
            boolean complete=spent(actor.getMainHandItem(),pending.main,pending.mainCount,pending.mainDamage,pending.tool)||spent(actor.getOffhandItem(),pending.off,pending.offCount,pending.offDamage,pending.tool);
            if(complete)LaborGuard.worked(actor); // Learning is committed by the actual timed job, never raw placement.
        }
    }
    public static boolean flooded(ServerLevel level,BlockPos pos){for(Direction d:Direction.Plane.HORIZONTAL)if(level.getFluidState(pos.relative(d)).is(FluidTags.WATER)||level.getFluidState(pos.below().relative(d)).is(FluidTags.WATER))return true;return false;}
    private static BlockPos plot(ServerLevel level,BlockPos pos){return level.getBlockState(pos).is(Blocks.FARMLAND)?pos:pos.below();}
    public static boolean irrigable(ServerLevel level,BlockPos pos){return WorldEnvironment.active(level)&&level.hasChunkAt(pos)&&level.getBlockState(plot(level,pos)).is(Blocks.FARMLAND);}
    private static String soilKey(BlockPos pos){return "soil/"+pos.asLong();}
    /** Called only at successful work completion. Water changes moisture, never growth directly. */
    public static boolean irrigate(ServerLevel level,BlockPos pos,double amount){
        if(!irrigable(level,pos)||!Double.isFinite(amount)||amount<=0)return false;
        var ground=plot(level,pos);var crop=ground.above();var state=level.getBlockState(crop);
        if(requiresCalendarTick(state))randomTick(state,level,crop,level.getRandom());
        var all=records(level);var soil=all.getCompoundOrEmpty(soilKey(ground));advanceSoil(level,ground,soil,SurvivalApi.calendar(level),null,false);
        double before=soil.getDoubleOr("water",.25);if(before>=.95){all.put(soilKey(ground),soil);return false;}
        soil.putDouble("water",Math.min(1,before+amount));soil.remove("bareSince");all.put(soilKey(ground),soil);
        level.setBlock(ground,level.getBlockState(ground).setValue(FarmlandBlock.MOISTURE,7),2);return true;
    }
    /** Fixed weather buckets make historical weather independent of random-tick frequency. */
    private static double[] advanceSoil(ServerLevel level,BlockPos ground,CompoundTag soil,long now,Schedule rule,boolean mushroom){
        return advanceSoil(level,ground,soil,now,rule,mushroom,0,Long.MAX_VALUE,false);
    }
    private static double[] advanceSoil(ServerLevel level,BlockPos ground,CompoundTag soil,long now,Schedule rule,boolean mushroom,double initialGrowth,long duration,boolean alwaysFrost){
        long last=soil.getLongOr("last",now);double water=soil.getDoubleOr("water",level.getBlockState(ground).is(Blocks.FARMLAND)&&level.getBlockState(ground).getValue(FarmlandBlock.MOISTURE)>0?.45:.25),growth=0;boolean frost=false;
        long start=Math.max(last,now-4L*WorldEnvironment.settings(level).seasonDays()*DAY);boolean adjacent=flooded(level,ground.above()),roof=EnvironmentApi.roofed(level,ground.above());
        for(long cursor=start;cursor<now;){long bucket=Math.floorDiv(cursor,600),end=Math.min(now,(bucket+1)*600);var weather=EnvironmentApi.at(level,ground.above(),bucket*600+300);
            double rain=roof||weather.snow()?0:weather.rain();double after=EnvironmentModel.soilWater(water,rain,weather.temperature(),weather.wind(),adjacent,end-cursor);
            if(rule!=null){frost|=rule.frost&&weather.temperature()<-2&&(initialGrowth+growth<duration||alwaysFrost);
                boolean temperature=weather.temperature()>=(rule.warm?12:2)&&weather.temperature()<36;
                if(!frost&&temperature&&(!rule.flooded||adjacent)&&(!mushroom||level.getRawBrightness(ground.above(),0)<13))growth+=EnvironmentModel.moistTicks(water,rain,weather.temperature(),weather.wind(),adjacent,end-cursor,.25);
            }
            water=after;cursor=end;
        }
        soil.putDouble("water",water);soil.putLong("last",now);return new double[]{growth,frost?1:0};
    }
    /** Tilled ground left bare. Vanilla lets dry farmland slump back to dirt within a minute or two; turned soil really lies
     * open for weeks before weeds and rain close it. It takes water as vanilla farmland does, and goes back to dirt only
     * after lying bare and dry for a whole season. */
    /** Ground frozen hard: the air well below freezing where the regional model runs, else the depth of winter where the biome's
     * mean less its seasonal swing is below freezing. Neither a hoe nor a plough breaks it. */
    public static boolean frozen(ServerLevel level,BlockPos pos){
        long now=SurvivalApi.calendar(level);
        if(WorldEnvironment.active(level))return frozenAir(EnvironmentApi.at(level,pos.above(),now).temperature());
        return frozenBiome(level.getBiome(pos).value().getBaseTemperature(),season(now));
    }
    public static boolean frozenAir(double celsius){return celsius<-2;}
    public static boolean frozenBiome(float biomeTemperature,int season){
        if(season!=3)return false;
        float t=biomeTemperature;double mean=t<.3?4:t<.65?10:t<.9?15:t<1.5?25:28,swing=mean>=22?2:10;
        return mean-swing<0;
    }
    /** Ground has just been turned: whatever clock it kept while it lay bare before starts again. */
    public static void tilled(ServerLevel level,BlockPos pos){var all=records(level);String key=soilKey(pos);var soil=all.getCompoundOrEmpty(key);if(soil.contains("bareSince")){soil.remove("bareSince");all.put(key,soil);}}
    public static boolean farmland(BlockState state,ServerLevel level,BlockPos pos){
        var above=level.getBlockState(pos.above());if(!above.isAir()&&!above.canBeReplaced())return false;
        boolean water=level.isRainingAt(pos.above())||net.neoforged.neoforge.common.FarmlandWaterManager.hasBlockWaterTicket(level,pos)
            ||level.findBlocksIn(pos.offset(-4,0,-4),pos.offset(4,1,4)).filterState(near->state.canBeHydrated(level,pos,near.getFluidState())).anyMatched();
        int moisture=state.getValue(FarmlandBlock.MOISTURE);var all=records(level);String key=soilKey(pos);var soil=all.getCompoundOrEmpty(key);long now=SurvivalApi.calendar(level);
        if(water){if(moisture<7)level.setBlock(pos,state.setValue(FarmlandBlock.MOISTURE,7),2);if(soil.contains("bareSince")){soil.remove("bareSince");all.put(key,soil);}return true;}
        if(moisture>0){level.setBlock(pos,state.setValue(FarmlandBlock.MOISTURE,moisture-1),2);if(soil.contains("bareSince")){soil.remove("bareSince");all.put(key,soil);}return true;}
        if(!soil.contains("bareSince")){soil.putLong("bareSince",now);all.put(key,soil);return true;}
        if(now-soil.getLongOr("bareSince",now)>=DAY*Config.SEASON_DAYS.get()){soil.remove("bareSince");all.put(key,soil);((FarmlandBlock)state.getBlock()).turnToBaseBlock(null,state,level,pos);}
        return true;
    }
    public static boolean randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        if(state.is(Blocks.FARMLAND))return farmland(state,level,pos);
        // Nearly every random tick is grass, leaves or water: leave those to vanilla before doing any lookups by name.
        if(!requiresCalendarTick(state)&&!(state.getBlock() instanceof CropBlock))return false;
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();Schedule rule=schedule(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if(rule==null){if(state.getBlock() instanceof CropBlock){if(WARNED.add(id))System.getLogger("Survival crops").log(System.Logger.Level.WARNING,"Undeclared crop growth disabled: "+id);return true;}return false;}
        long now=SurvivalApi.calendar(level);var all=records(level);String key=Long.toString(pos.asLong());var data=all.getCompoundOrEmpty(key);
        if(id.equals("sweet_berry_bush")||id.startsWith("cave_vines"))initializeWildBerry(state,data,now);
        String type=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if(!data.getStringOr("type",type).equals(type)){data=new CompoundTag();}
        // Nothing on record when it is first looked at: it was not sown or grown from anything sown, so it is wild and already established.
        if(data.isEmpty())data.putBoolean("wild",true);
        long last=data.getLongOr("last",now);long growth=data.getLongOr("growth",0);long duration=rule.days*DAY;
        if(data.getBooleanOr("fruited",false)&&(id.contains("melon_stem")||id.contains("pumpkin_stem")))duration=(id.contains("melon")?3:4)*DAY;
        boolean berries=id.equals("sweet_berry_bush")||id.startsWith("cave_vines");
        if(berries&&data.getBooleanOr("established",false))duration=2*DAY;
        boolean moist=!state.is(Blocks.FARMLAND) && (level.getBlockState(pos.below()).is(Blocks.FARMLAND)?level.getBlockState(pos.below()).getValue(FarmlandBlock.MOISTURE)>0:flooded(level,pos)||level.isRainingAt(pos));
        float temperature=level.getBiome(pos).value().getBaseTemperature();
        boolean suitable=moist&&(!rule.flooded||flooded(level,pos))&&(!rule.warm||temperature>=0.5f);
        if(id.contains("mushroom"))suitable=temperature>0.1f&&level.getRawBrightness(pos,0)<13&&(!level.canSeeSky(pos)||flooded(level,pos));
        long elapsed=Math.max(0,now-last);long remaining=Math.min(elapsed,DAY*Config.SEASON_DAYS.get()*4L);
        if(WorldEnvironment.active(level)){
            var ground=plot(level,pos);String soilId=soilKey(ground);var soil=all.getCompoundOrEmpty(soilId);if(!soil.contains("last"))soil.putLong("last",last);
            if(soil.getLongOr("last",now)<last)advanceSoil(level,ground,soil,Math.min(now,last),null,false);
            var result=advanceSoil(level,ground,soil,now,rule,id.contains("mushroom"),growth+data.getDoubleOr("growthFraction",0),duration,id.equals("rice_crop"));all.put(soilId,soil);
            if(result[1]>0){level.destroyBlock(pos,false);all.remove(key);return true;}
            double earned=result[0]+data.getDoubleOr("growthFraction",0);growth+=(long)earned;data.putDouble("growthFraction",earned-(long)earned);
            if(level.getBlockState(ground).is(Blocks.FARMLAND))level.setBlock(ground,level.getBlockState(ground).setValue(FarmlandBlock.MOISTURE,soil.getDoubleOr("water",0)>.25?7:0),2);
        }else for(long cursor=now-remaining;cursor<now;){long end=Math.min(now,(Math.floorDiv(cursor,DAY)+1)*DAY);int season=season(cursor);boolean warmSeason=season!=3;
            if(rule.frost&&season==3&&(growth<duration||id.equals("rice_crop"))){level.destroyBlock(pos,false);all.remove(key);return true;}
            if(suitable&&(warmSeason||id.contains("mushroom"))){growth+=end-cursor;}cursor=end;
        }
        data.putString("type",type);data.putLong("last",now);data.putLong("growth",growth);data.putLong("born",data.getLongOr("born",now));
        IntegerProperty age=null;for(var property:state.getProperties())if(property instanceof IntegerProperty p&&p.getName().equals("age")){age=p;break;}
        if(age!=null){int max=Collections.max(age.getPossibleValues());int value=(int)Math.min(max,growth*max/duration);if(value!=state.getValue(age))level.setBlock(pos,state.setValue(age,value),2);}
        if(berries&&state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BERRIES))level.setBlock(pos,state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BERRIES,growth>=duration&&(season(now)==1||season(now)==2)),2);
        if(growth>=duration&&id.equals("sugar_cane")&&level.isEmptyBlock(pos.above())&&level.getBlockState(pos.below(2)).getBlock()!=Blocks.SUGAR_CANE){level.setBlock(pos.above(),Blocks.SUGAR_CANE.defaultBlockState(),3);var stage=new CompoundTag();stage.putString("type",type);stage.putLong("born",now);stage.putLong("last",now);stage.putBoolean("harvestableStage",true);stage.putBoolean("sown",data.getBooleanOr("sown",false));all.put(Long.toString(pos.above().asLong()),stage);data.putLong("growth",0);}
        if(growth>=duration&&id.contains("mushroom")&&now>=data.getLongOr("sporeAt",0)){
            int nearby=0;for(BlockPos p:BlockPos.betweenClosed(pos.offset(-2,0,-2),pos.offset(2,0,2)))if(level.getBlockState(p).getBlock()==state.getBlock())nearby++;
            if(nearby<8)for(Direction d:Direction.Plane.HORIZONTAL){BlockPos child=pos.relative(d);if(level.isEmptyBlock(child)&&state.canSurvive(level,child)&&level.getRawBrightness(child,0)<13&&!level.canSeeSky(child)){
                level.setBlock(child,state,3);var seedling=new CompoundTag();seedling.putString("type",type);seedling.putLong("born",now);seedling.putLong("last",now);seedling.putLong("growth",0);all.put(Long.toString(child.asLong()),seedling);break;
            }}data.putLong("sporeAt",now+4*DAY);
        }
        if(growth>=duration&&(id.contains("melon_stem")||id.contains("pumpkin_stem"))){
            Block fruitType=id.contains("melon")?Blocks.MELON:Blocks.PUMPKIN;boolean existing=false;for(Direction d:Direction.Plane.HORIZONTAL)if(level.getBlockState(pos.relative(d)).is(fruitType))existing=true;
            if(!existing)for(Direction d:Direction.Plane.HORIZONTAL){BlockPos fruit=pos.relative(d);if(level.isEmptyBlock(fruit)&&level.getBlockState(fruit.below()).isFaceSturdy(level,fruit.below(),Direction.UP)){level.setBlock(fruit,fruitType.defaultBlockState(),3);var grown=new CompoundTag();grown.putBoolean("grownFruit",true);grown.putBoolean("sown",true);grown.putLong("last",now);all.put(Long.toString(fruit.asLong()),grown);data.putLong("growth",0);data.putBoolean("fruited",true);break;}}
        }
        all.put(key,data);return true;
    }
    private static void tooltip(ItemTooltipEvent event){String id=BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).getPath();if(id.endsWith("seeds")||id.equals("fruit_tree"))event.getToolTip().add(Component.literal("Seasonal crop: prepared moist farmland; rice needs adjacent paddy water. Bone meal cannot replace growth days."));}
}
