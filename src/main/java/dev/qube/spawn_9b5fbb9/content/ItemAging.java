package dev.qube.spawn_9b5fbb9.content;

import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Timestamp metadata is a component: splits/drops/hoppers preserve it and different ages never merge. */
public final class ItemAging {
    public static final long DAY=24000;
    public record DecayDefinition(double rawMeatDays,double cookedFoodDays,double produceDays,double dryGrainDays,double sealedPotRate,double storageRate,double organicWeatherDays,double durableWeatherDays,double rainMultiplier,double hotMultiplier,java.util.Map<String,Double> shelfDays){}
    private static final DecayDefinition DEFAULT_RULES=new DecayDefinition(.5,1,3,7,1d/6,.9,4,28,2,1.5,java.util.Map.of());
    private static volatile DecayDefinition RULES=DEFAULT_RULES;
    public static DecayDefinition definitions(){return RULES;}
    public static double storageRate(){return RULES.storageRate;}
    public static double sealedRate(){return RULES.sealedPotRate;}
    public static void reloadDefinitions(com.google.gson.JsonObject object){
        ContentDefinitions.fields(object,"rawMeatDays","cookedFoodDays","produceDays","dryGrainDays","sealedPotRate","storageRate","organicWeatherDays","durableWeatherDays","rainMultiplier","hotMultiplier","shelfDays");
        var defaults=DEFAULT_RULES;var shelf=new java.util.LinkedHashMap<String,Double>();
        if(object.has("shelfDays"))for(var entry:ContentDefinitions.object(object.get("shelfDays"),"shelfDays").entrySet()){
            String id=ContentDefinitions.id(entry.getKey()).toString();ContentDefinitions.item(id);var number=new com.google.gson.JsonObject();number.add("days",entry.getValue());shelf.put(id,ContentDefinitions.number(number,"days",1,1d/DAY,3650));
        }
        var replacement=new DecayDefinition(ContentDefinitions.number(object,"rawMeatDays",defaults.rawMeatDays,1d/DAY,3650),ContentDefinitions.number(object,"cookedFoodDays",defaults.cookedFoodDays,1d/DAY,3650),ContentDefinitions.number(object,"produceDays",defaults.produceDays,1d/DAY,3650),ContentDefinitions.number(object,"dryGrainDays",defaults.dryGrainDays,1d/DAY,3650),ContentDefinitions.number(object,"sealedPotRate",defaults.sealedPotRate,.000001,1),ContentDefinitions.number(object,"storageRate",defaults.storageRate,.000001,1),ContentDefinitions.number(object,"organicWeatherDays",defaults.organicWeatherDays,1d/DAY,3650),ContentDefinitions.number(object,"durableWeatherDays",defaults.durableWeatherDays,1d/DAY,3650),ContentDefinitions.number(object,"rainMultiplier",defaults.rainMultiplier,1,100),ContentDefinitions.number(object,"hotMultiplier",defaults.hotMultiplier,1,100),java.util.Collections.unmodifiableMap(shelf));
        RULES=replacement;
    }
    public static CompoundTag data(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag(); }
    /** An item with nothing recorded of it carries no record at all: an empty one would keep it from stacking with its like. */
    public static void data(ItemStack stack,CompoundTag data) { if(data.isEmpty())stack.remove(DataComponents.CUSTOM_DATA);else stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data)); }
    public static boolean isFood(ItemStack stack) { return stack.has(DataComponents.FOOD); }
    public static long shelfLife(ItemStack stack) {
        var key=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());String id=key.getPath();var rules=RULES;
        if(id.equals("spoiled_food")) return Long.MAX_VALUE;
        if(rules.shelfDays.containsKey(key.toString()))return Math.max(1,Math.round(rules.shelfDays.get(key.toString())*DAY));
        if(id.contains("cooked") || id.equals("bread") || id.contains("stew")) return Math.round(DAY*rules.cookedFoodDays);
        if(id.equals("beef")||id.equals("porkchop")||id.equals("chicken")||id.equals("mutton")||id.equals("rabbit")||id.equals("cod")||id.equals("salmon")) return Math.round(DAY*rules.rawMeatDays);
        if(id.contains("grain") || id.equals("wheat") || id.contains("seeds")) return Math.round(DAY*rules.dryGrainDays);
        return Math.round(DAY*rules.produceDays);
    }
    public static void init() { NeoForge.EVENT_BUS.addListener(ItemAging::crafted); NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,ItemAging::dropped); }
    public static void update(ItemStack stack,ServerLevel level,double rate) {
        update(stack,level,rate,true);
    }
    public static void exposed(ItemStack stack,ServerLevel level,double rate){update(stack,level,rate,false);}
    private static void update(ItemStack stack,ServerLevel level,double rate,boolean stored) {
        if(stack.isEmpty())return;
        long now=SurvivalApi.calendar(level);
        if(stored){
            var tag=data(stack);boolean changed=false;
            if(tag.contains("weatherTick")){tag.remove("weatherTick");changed=true;}
            // In the hand, weathering is counted in whole days: the few seconds a stick lay on the ground must not
            // make it a different stick from the next, or nothing gathered would ever stack.
            if(tag.contains("weatherAge")){double age=tag.getDoubleOr("weatherAge",0),whole=Math.floor(age/DAY)*DAY;if(whole!=age){if(whole<=0)tag.remove("weatherAge");else tag.putDouble("weatherAge",whole);changed=true;}}
            // An egg taken off the nest has gone cold: its sitting is over, and time in a pack is not time by the fire.
            if(tag.contains("nest")){tag.remove("nest");tag.remove("nestLast");tag.remove("incubated");changed=true;}
            if(changed)data(stack,tag);
        }
        if(stack.is(SurvivalContent.item("fired_pot"))) {
            CompoundTag pot=data(stack); double preservation=pot.getBooleanOr("sealed",false)?Pottery.preservationRate(stack):1;
            var contents=stack.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY);
            var items=contents.itemCopies().toList(); for(var food:items)update(food,level,preservation);
            if(!items.isEmpty())stack.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(items));
        }
        if(!isFood(stack) && !stack.is(Items.WHEAT))return;
        CompoundTag tag=data(stack); long last=tag.getLongOr("freshTick",now);
        // Looked at again within the second, it is left as it is: an open chest asks after every slot every tick,
        // and each new stamp made the food a changed item to be sent to everyone looking in.
        if(tag.contains("freshTick")&&now>=last&&now-last<20)return;
        double age=tag.getDoubleOr("foodAge",0)+Math.max(0,now-last)*rate;
        tag.putLong("freshTick",now);tag.putDouble("foodAge",age);data(stack,tag);
        if(age>=shelfLife(stack) && !stack.is(SurvivalContent.item("spoiled_food"))) {
            tag.putBoolean("spoiled",true);data(stack,tag);
            stack.set(DataComponents.FOOD,new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(0).build());
        }
    }
    /** Gives food the age it has reached, as of now: for what comes out of bulk stowage, where age is kept by kind and not by stack. */
    public static void stamp(ItemStack stack,ServerLevel level,double age){
        if(!isFood(stack)&&!stack.is(Items.WHEAT))return;
        var tag=data(stack);tag.putDouble("foodAge",age);tag.putLong("freshTick",SurvivalApi.calendar(level)-20);data(stack,tag);update(stack,level,0);
    }
    public static boolean spoiled(ItemStack stack){return data(stack).getBooleanOr("spoiled",false)||stack.is(SurvivalContent.item("spoiled_food"));}
    public static void inherit(ItemStack output,Iterable<ItemStack> inputs,ServerLevel level) {
        // Only what can go off is given an age. Stamped on cloth or iron, the moment of making kept every batch from
        // stacking with the last, and a recipe that wants four cloths in one hand could never be started.
        if(!isFood(output)&&!output.is(Items.WHEAT))return;
        double oldest=0;boolean spoiled=false;
        for(ItemStack input:inputs){ update(input,level,1);oldest=Math.max(oldest,data(input).getDoubleOr("foodAge",0));spoiled|=spoiled(input); }
        var tag=data(output);tag.putDouble("foodAge",oldest);tag.putLong("freshTick",SurvivalApi.calendar(level));tag.putBoolean("spoiled",spoiled);data(output,tag);
        if(spoiled&&isFood(output))output.set(DataComponents.FOOD,new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(0).build());
    }
    private static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if(!(event.getEntity().level() instanceof ServerLevel level))return;
        java.util.List<ItemStack> inputs=new java.util.ArrayList<>();for(int i=0;i<event.getInventory().getContainerSize();i++)inputs.add(event.getInventory().getItem(i));
        inherit(event.getCrafting(),inputs,level);
    }
    // ---- Heaps ----------------------------------------------------------------------------------------
    // A cubic metre of seam breaks down to more than a tonne of ore, twenty-five stacks and more. Each stack lying as
    // its own entity, and none of them ever going away, a few hundred blocks mined were ten thousand entities for
    // the server to tick for good. What does not weather lies in one heap instead: one entity showing a stack, with
    // the count of what is under it. Taking the stack uncovers the next.
    private static final String HEAP="survivalHeap",HEAP_ITEM="survivalHeapItem",HEAP_SHOWN="survivalHeapShown";
    private static boolean inert(String id){return INERT.contains(id)||id.startsWith("crushed_")||id.startsWith("roasted_")||id.endsWith("stone")||id.endsWith("_stones");}
    /** Nothing added to what the item is made with. Every stack that enters the world has its enchantments struck off, so a stack with things taken away is still plain. */
    private static boolean plain(ItemStack stack){var patch=stack.getComponentsPatch();return patch.isEmpty()||patch.split().added().isEmpty();}
    /** Plain goods that never weather: nothing about one differs from the next, so a count stands for them. */
    public static boolean heapable(ItemStack stack){return !stack.isEmpty()&&stack.getMaxStackSize()>1&&plain(stack)&&inert(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());}
    /** What lies under the stack that shows. */
    public static long under(ItemEntity entity){return entity.getPersistentData().getLongOr(HEAP,0);}
    /** Everything in the heap: the stack that shows and what lies under it. */
    public static long heap(ItemEntity entity){return entity.getItem().getCount()+under(entity);}
    /** Makes the entity a heap of its own stack over {@code under} more, and says how much is there to anyone looking. */
    public static void heap(ItemEntity entity,long under){
        var data=entity.getPersistentData();ItemStack stack=entity.getItem();
        if(under<=0||stack.isEmpty()){if(data.contains(HEAP)){data.remove(HEAP);data.remove(HEAP_ITEM);data.remove(HEAP_SHOWN);entity.setCustomName(null);entity.setCustomNameVisible(false);}return;}
        data.putLong(HEAP,under);data.putString(HEAP_ITEM,net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        long total=under+stack.getCount();if(data.getLongOr(HEAP_SHOWN,-1)==total)return;
        data.putLong(HEAP_SHOWN,total);entity.setCustomName(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT,"%,d ",total)).append(stack.getItemName()));entity.setCustomNameVisible(true);
    }
    /** What a block breaks down to falls as one heap of each kind, not as a stack to the entity. */
    private static void dropped(net.neoforged.neoforge.event.level.BlockDropsEvent event){
        var drops=event.getDrops();if(drops.size()<2)return;
        var heaps=new java.util.IdentityHashMap<Item,ItemEntity>();var totals=new java.util.IdentityHashMap<Item,Long>();
        for(var each=drops.iterator();each.hasNext();){
            ItemEntity drop=each.next();ItemStack stack=drop.getItem();if(!heapable(stack))continue;
            totals.merge(stack.getItem(),(long)stack.getCount(),Long::sum);if(heaps.putIfAbsent(stack.getItem(),drop)!=null)each.remove();
        }
        heaps.forEach((item,heap)->{long total=totals.get(item);int shown=(int)Math.min(total,heap.getItem().getMaxStackSize());if(total<=heap.getItem().getCount())return;heap.setItem(heap.getItem().copyWithCount(shown));heap(heap,total-shown);});
    }
    /** Once a second: a heap takes in the smaller heaps and loose stacks of its own kind lying against it, and shows a full stack while it has one. */
    private static void gather(ItemEntity entity,ServerLevel level){
        ItemStack stack=entity.getItem();if(!heapable(stack))return;
        long under=under(entity),mine=under+stack.getCount();
        for(ItemEntity other:level.getEntitiesOfClass(ItemEntity.class,entity.getBoundingBox().inflate(1.25,.5,1.25),near->near!=entity&&near.isAlive()&&ItemStack.isSameItemSameComponents(near.getItem(),stack))){
            long theirs=heap(other);
            // The larger takes the smaller, and of two alike the elder: asked the other way about, the other does the taking.
            if(theirs>mine||theirs==mine&&other.getId()<entity.getId())continue;
            under+=theirs;mine+=theirs;other.getPersistentData().remove(HEAP);other.setItem(ItemStack.EMPTY);other.discard();
        }
        int shown=stack.getCount(),full=stack.getMaxStackSize();
        if(shown<full&&under>0){int more=(int)Math.min(full-shown,under);under-=more;entity.setItem(stack.copyWithCount(shown+more));}
        heap(entity,under);
    }
    /** Called as an item entity is discarded, whatever took it: a hand, a hopper, another stack. What lay under it is still
     * there, and is uncovered as a new entity so that every client sees the old stack go and the next one lie in its place. */
    public static void taken(ItemEntity entity){
        if(!(entity.level() instanceof ServerLevel level))return;
        var data=entity.getPersistentData();long under=data.getLongOr(HEAP,0);if(under<=0)return;
        data.remove(HEAP);var id=net.minecraft.resources.Identifier.tryParse(data.getStringOr(HEAP_ITEM,""));
        Item item=id==null?Items.AIR:net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);if(item==null||item==Items.AIR)return;
        ItemStack next=new ItemStack(item);next.setCount((int)Math.min(under,next.getMaxStackSize()));
        var heap=new ItemEntity(level,entity.getX(),entity.getY(),entity.getZ(),next,0,0,0);heap.setDefaultPickUpDelay();
        heap(heap,under-next.getCount());level.addFreshEntity(heap);
    }
    private static final java.util.Set<String> INERT=java.util.Set.of("hematite","malachite","cassiterite","galena","pyrite","salt","fluorite","bauxite","wolframite","oil_sand","borax","coal","charcoal","coke","clay_ball","brick","flint","quicklime","limestone","sulfur","pig_iron");
    public static void weather(ItemEntity entity) {
        if(!(entity.level() instanceof ServerLevel level))return;
        // Kept from the five-minute sweep by being made young again each second, not by being marked everlasting:
        // an everlasting item never joins another, and every part-stack a miner left lay as its own entity for good.
        entity.setExtendedLifetime();
        var rules=RULES;double exposure=(level.isRainingAt(entity.blockPosition())?rules.rainMultiplier:1)*(level.getBiome(entity.blockPosition()).value().getBaseTemperature()>=1.5f?rules.hotMultiplier:1);
        ItemStack stack=entity.getItem(); exposed(stack,level,exposure);
        // Sound food keeps to its shelf life; once spoiled it lies out like any other organic litter.
        boolean egg=stack.is(SurvivalContent.item("fertilized_egg")),nested=egg&&data(stack).getBooleanOr("nest",false)&&level.getBlockState(entity.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.HAY_BLOCK);
        // An egg is kept only while it sits on its nest. One dropped anywhere else, or whose hay has gone, lies out and rots like any other.
        if(egg&&!nested&&entity.isNoGravity())entity.setNoGravity(false);
        if(nested||isFood(stack)&&!spoiled(stack))return;
        CompoundTag tag=data(stack);long now=SurvivalApi.calendar(level);long last=tag.getLongOr("weatherTick",now);
        String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        // Rock does not rot: broken ore, coal, clay, brick and lime lie where they fell, and what falls together lies in one heap.
        if(inert(id)){gather(entity,level);return;}
        // By what the thing is, not by letters in its name: "pot" is also in potato, and stone is not metal.
        boolean ceramic=id.endsWith("_pot")||id.startsWith("pot_")||id.contains("ceramic");
        boolean metal=id.contains("ingot")||id.contains("scrap")||id.contains("copper")||id.contains("bronze")||id.contains("iron")||id.contains("steel")||id.contains("gold")||id.contains("diamond")||id.contains("obsidian");
        boolean stone=!metal&&!ceramic&&id.contains("stone"),durable=ceramic||metal||stone;
        double age=tag.getDoubleOr("weatherAge",0)+Math.max(0,now-last)*exposure;
        tag.putLong("weatherTick",now);tag.putDouble("weatherAge",age);data(stack,tag);
        boolean weathered=age>DAY*(durable?rules.durableWeatherDays:rules.organicWeatherDays);
        // Fibre is what organic litter rots down to, and spoiled food is already rot: a second spell in the open
        // and nothing is left. Without an end, every egg and sapling a loaded area ever dropped stays in it for good.
        if(weathered&&(id.equals("plant_fiber")||spoiled(stack)||id.endsWith("egg"))){entity.discard();return;}
        // Metal leaves scrap, fired clay its sherds, a stone tool the stone when the haft has gone, and the rest fibre.
        if(weathered && !id.equals("ceramic_shard")&&!id.equals("metal_scrap")) entity.setItem(new ItemStack(SurvivalContent.item(metal?"metal_scrap":ceramic?"ceramic_shard":stone?"loose_stone":"plant_fiber"),Math.min(4,stack.getCount())));
    }
}
