package dev.qube.spawn_9b5fbb9.industry;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import dev.qube.spawn_9b5fbb9.content.ItemAging;
import dev.qube.spawn_9b5fbb9.survival.SurvivalApi;

/** Bulk material accounts keyed by item id. Industrial quantities never fit in slot inventories;
 * every unit still enters from a real stack and leaves as a real stack. */
public final class Ledger {
    private Ledger(){}
    /** An account that also knows how old its food is. A bulk account keeps a kind and a number, and for food that was a
     * larder outside time: bread came off a cart a season later as it went on. Each kind of food has one age here, the
     * mean of what was put in, and goes on ageing at the rate of a store room. Past its time it is spoiled food. */
    public static final class Account extends TreeMap<String,Long>{
        /** By item id: age in ticks, and the calendar tick it was reckoned at. */
        final Map<String,double[]> fresh=new HashMap<>();
    }
    public static final String SPOILED="spawn_9b5fbb9:spoiled_food";
    private static final Map<String,Boolean> PERISHABLE=new java.util.concurrent.ConcurrentHashMap<>();
    /** Food, and grain: what a store room does not keep for ever. */
    public static boolean perishable(String itemId){
        return PERISHABLE.computeIfAbsent(itemId,id->{if(id.equals(SPOILED)||Identifier.tryParse(id)==null)return false;Item item=item(id);if(item==Items.AIR)return false;ItemStack stack=new ItemStack(item);return stack.has(DataComponents.FOOD)||stack.is(Items.WHEAT);});
    }
    private static double age(Map<String,Long> ledger,String itemId,long now){
        double[] age=ledger instanceof Account account?account.fresh.get(itemId):null;
        return age==null||ledger.getOrDefault(itemId,0L)<=0?0:age[0]+Math.max(0,now-age[1])*ItemAging.storageRate();
    }
    private static void mix(Map<String,Long> ledger,String itemId,long amount,double theirs,long now){
        if(amount<=0)return;
        if(ledger instanceof Account account&&perishable(itemId)){long held=ledger.getOrDefault(itemId,0L);account.fresh.put(itemId,new double[]{(age(ledger,itemId,now)*held+theirs*amount)/(held+amount),now});}
        ledger.merge(itemId,amount,Long::sum);
    }
    /** Puts {@code amount} of a real stack into the account, food with the age it has. The caller takes it from the stack. */
    public static void stow(Map<String,Long> ledger,ItemStack stack,long amount,Level world){
        String id=id(stack);
        if(world instanceof ServerLevel level&&perishable(id)){ItemAging.update(stack,level,1);mix(ledger,id,amount,ItemAging.data(stack).getDoubleOr("foodAge",0),SurvivalApi.calendar(level));}
        else add(ledger,id,amount);
    }
    /** Adds units that are coming out of another account, food with the age it had there. The caller takes them out of that one. */
    public static void receive(Map<String,Long> to,Map<String,Long> from,String itemId,long amount,Level world){
        if(world instanceof ServerLevel level&&perishable(itemId)){long now=SurvivalApi.calendar(level);mix(to,itemId,amount,age(from,itemId,now),now);}
        else add(to,itemId,amount);
    }
    /** Brings the food in an account up to the present. What has passed its shelf life is spoiled food from then on. */
    public static void settle(Map<String,Long> ledger,Level world){
        if(!(ledger instanceof Account account)||!(world instanceof ServerLevel level))return;
        if(!account.fresh.isEmpty())account.fresh.keySet().retainAll(ledger.keySet());
        if(ledger.isEmpty())return;
        long now=SurvivalApi.calendar(level);List<String> gone=null;
        for(String id:ledger.keySet()){
            if(!perishable(id))continue;
            double[] age=account.fresh.computeIfAbsent(id,key->new double[]{0,now});
            age[0]+=Math.max(0,now-age[1])*ItemAging.storageRate();age[1]=now;
            if(age[0]>=ItemAging.shelfLife(new ItemStack(item(id)))){if(gone==null)gone=new ArrayList<>();gone.add(id);}
        }
        if(gone!=null)for(String id:gone){long count=ledger.remove(id);account.fresh.remove(id);ledger.merge(SPOILED,count,Long::sum);}
    }
    public static String id(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    public static Item item(String id){Item item=BuiltInRegistries.ITEM.getValue(Identifier.parse(id));return item==null?Items.AIR:item;}
    /** An ingredient is an exact item id, or {@code #namespace:tag} for interchangeable materials. */
    public static boolean matches(String ingredient,String itemId){
        if(!ingredient.startsWith("#"))return ingredient.equals(itemId);
        Item item=item(itemId);if(item==Items.AIR)return false;
        return new ItemStack(item).is(TagKey.create(Registries.ITEM,Identifier.parse(ingredient.substring(1))));
    }
    private static final Map<String,Double> MASS=new HashMap<>();
    private static void mass(double kilograms,String... ids){for(String id:ids)MASS.put(id.indexOf(':')<0?"spawn_9b5fbb9:"+id:id,kilograms);}
    static {
        mass(25,"minecraft:coal","minecraft:charcoal","coke");
        mass(3.5,"minecraft:brick");mass(3.75,"minecraft:clay_ball");mass(45,"minecraft:glass");mass(7.5,"minecraft:glass_pane");mass(.5,"minecraft:glass_bottle");
        mass(1600,"minecraft:sand","minecraft:cobblestone");mass(1700,"minecraft:gravel");mass(2600,"minecraft:stone","limestone");mass(1300,"minecraft:dirt");
        mass(.007,"copper_wire");mass(.01,"insulated_wire");mass(.00002,"fine_wire");mass(.002,"chip","chip_package");mass(.0001,"silicon_die");mass(.001,"passive_components");
        mass(.3,"circuit_board");mass(.0013,"silicon_wafer","wafer_oxidised","wafer_diffused","wafer_gated","wafer_contacted","patterned_wafer");mass(.2,"photomask");mass(.005,"minecraft:paper");
        mass(.031,"musket_ball");mass(.042,"paper_cartridge");mass(.52,"powder_charge");mass(3,"round_shot");mass(40,"stone_shot");mass(8,"explosive_shell");
        mass(31,"steel_rail");mass(85,"sleeper");mass(.25,"metal_rivet");mass(4.5,"musket");mass(2,"musket_barrel");mass(.4,"musket_lock");mass(1.2,"musket_stock");
        mass(45,"wooden_wheel");mass(300,"vehicle_frame");mass(12,"harness","minecraft:saddle");mass(5,"minecraft:leather");mass(320,"gun_carriage");mass(400,"cannon_barrel");mass(1500,"bombard_barrel");
        mass(1300,"boiler");mass(3100,"steam_engine");mass(1440,"dynamo");mass(155,"transformer");mass(340,"combustion_engine");mass(22,"electric_motor");mass(25,"rubber_tyre");
        mass(1,"minecraft:iron_ingot","minecraft:copper_ingot","minecraft:gold_ingot","minecraft:gunpowder","minecraft:bone_meal","minecraft:wheat","maize");mass(.1,"minecraft:flint");mass(.3,"minecraft:stick");
        mass(.5,"lens");mass(.1,"vacuum_tube");mass(6,"display_tube");mass(9,"minecraft:hay_block");
    }
    /** Mass of one item in kilograms. Metals, minerals, chemicals and liquids are a kilogram or a litre to the item; solid fuel
     * comes in 25 kg sacks; logs are a cubic metre of air-dried timber, planks a quarter of that sawn; other blocks a cubic metre of stone. */
    public static double mass(String itemId){
        Double known=MASS.get(itemId);if(known!=null)return known;
        Item item=item(itemId);if(item==Items.AIR)return 1;ItemStack stack=new ItemStack(item);
        if(stack.is(ItemTags.LOGS))return 600;if(stack.is(ItemTags.PLANKS))return 150;
        if(stack.is(ItemTags.ARROWS))return .05;if(stack.has(DataComponents.FOOD))return .3;
        if(building(item))return 1500;
        // The mod's own materials are kilograms; other loose vanilla goods average half a kilogram.
        return itemId.startsWith("spawn_9b5fbb9:")?1:.5;
    }
    /** A block in item form: a cubic metre of something. Seeds, roots and the like place a different block and are loose goods. */
    public static boolean building(Item item){return item instanceof BlockItem placed&&BuiltInRegistries.ITEM.getKey(item).equals(BuiltInRegistries.BLOCK.getKey(placed.getBlock()));}
    private static double borne(ItemStack stack){return stack.isEmpty()||building(stack.getItem())?0:mass(id(stack))*stack.getCount();}
    /** Kilograms an actor carries on the body: a player's whole inventory, or another actor's hands, armour and pack. Building
     * blocks are excepted: carrying and placing whole cubic metres by hand is Minecraft's own convention and stays one. */
    public static double carried(LivingEntity actor){
        double kilograms=0;
        if(actor instanceof Player player){var pack=player.getInventory();for(int slot=0;slot<pack.getContainerSize();slot++)kilograms+=borne(pack.getItem(slot));}
        else{
            for(EquipmentSlot slot:EquipmentSlot.values())kilograms+=borne(actor.getItemBySlot(slot));
            if(actor instanceof InventoryCarrier carrier)for(int slot=0;slot<carrier.getInventory().getContainerSize();slot++)kilograms+=borne(carrier.getInventory().getItem(slot));
        }
        return kilograms;
    }
    /** The most a person can bear at all: at 120 kg they are down to a tenth of their pace, and they lift no more. */
    public static final double MOST_BORNE=120;
    /** How many of a loose stack an actor can take up without going over what they can bear. Building blocks and weightless
     * things are Minecraft's own convention and are always taken whole. */
    public static int liftable(LivingEntity actor,ItemStack stack){
        if(stack.isEmpty()||building(stack.getItem()))return stack.getCount();
        double each=mass(id(stack));if(each<=0)return stack.getCount();
        double room=MOST_BORNE-carried(actor);return (int)Math.max(0,Math.min(stack.getCount(),Math.floor(room/each+1e-9)));
    }
    /** Marching pace under load: a soldier's 35 kg is carried all day; beyond it pace falls away to a tenth at 120 kg. */
    public static double pace(double kilograms){return kilograms<=35?1:Math.max(.1,1-.9*(kilograms-35)/85);}
    public static double mass(Map<String,Long> ledger){double sum=0;for(var entry:ledger.entrySet())sum+=mass(entry.getKey())*entry.getValue();return sum;}
    public static long total(Map<String,Long> ledger){long sum=0;for(long value:ledger.values())sum+=value;return sum;}
    public static long count(Map<String,Long> ledger,String ingredient){
        if(!ingredient.startsWith("#"))return ledger.getOrDefault(ingredient,0L);
        long sum=0;for(var entry:ledger.entrySet())if(matches(ingredient,entry.getKey()))sum+=entry.getValue();return sum;
    }
    /** Adds new stock. Food made or brought in with no age of its own is fresh, and freshens the mean of what is there. */
    public static void add(Map<String,Long> ledger,String itemId,long amount){
        if(amount<=0)return;
        if(ledger instanceof Account account&&!account.fresh.isEmpty()){
            double[] age=account.fresh.get(itemId);long held=ledger.getOrDefault(itemId,0L);
            if(age!=null){if(held<=0)account.fresh.remove(itemId);else age[0]*=held/(double)(held+amount);}
        }
        ledger.merge(itemId,amount,Long::sum);
    }
    /** Removes up to {@code amount} units matching the ingredient; returns the amount actually removed. */
    public static long take(Map<String,Long> ledger,String ingredient,long amount){
        long remaining=amount;
        for(var iterator=ledger.entrySet().iterator();iterator.hasNext()&&remaining>0;){
            var entry=iterator.next();if(!matches(ingredient,entry.getKey()))continue;
            long used=Math.min(remaining,entry.getValue());remaining-=used;
            if(used==entry.getValue()){iterator.remove();if(ledger instanceof Account account)account.fresh.remove(entry.getKey());}else entry.setValue(entry.getValue()-used);
        }
        return amount-remaining;
    }
    public static boolean has(Map<String,Long> ledger,Map<String,Integer> requirements){
        for(var need:requirements.entrySet())if(count(ledger,need.getKey())<need.getValue())return false;return true;
    }
    /** Why this stack cannot go into a bulk account, or nothing. An account knows a kind, a number and for food an age:
     * a stack already spoiled would be counted in with the sound, and a filled vessel would come out empty. */
    public static String unfit(ItemStack stack){
        if(dev.qube.spawn_9b5fbb9.content.ItemAging.spoiled(stack)&&!id(stack).endsWith("spoiled_food"))return "Spoiled food is not cargo: it would have to be sorted from the sound";
        var inside=stack.get(DataComponents.CONTAINER);var data=dev.qube.spawn_9b5fbb9.content.ItemAging.data(stack);
        if(inside!=null&&inside.nonEmptyItems().iterator().hasNext()||data.getIntOr("water",0)>0||data.getBooleanOr("sealed",false))return "Empty the vessel first: bulk stowage keeps the vessel, not what is in it";
        return "";
    }
    /** Takes one real stack (at most a full stack) of the given item. Food comes out with the age it has reached. */
    public static ItemStack withdrawStack(Map<String,Long> ledger,String itemId,Level world){return withdrawStack(ledger,itemId,Long.MAX_VALUE,world);}
    public static ItemStack withdrawStack(Map<String,Long> ledger,String itemId,long limit,Level world){
        long stored=ledger.getOrDefault(itemId,0L);Item item=item(itemId);if(stored<=0||item==Items.AIR){ledger.remove(itemId);return ItemStack.EMPTY;}
        double age=world instanceof ServerLevel level&&perishable(itemId)?age(ledger,itemId,SurvivalApi.calendar(level)):0;
        int count=(int)Math.min(Math.min(stored,Math.max(1,limit)),new ItemStack(item).getMaxStackSize());take(ledger,itemId,count);ItemStack stack=new ItemStack(item,count);
        if(world instanceof ServerLevel level&&perishable(itemId))ItemAging.stamp(stack,level,age);
        return stack;
    }
    public static CompoundTag save(Map<String,Long> ledger){
        var tag=new CompoundTag();ledger.forEach(tag::putLong);
        if(ledger instanceof Account account&&!account.fresh.isEmpty()){
            var fresh=new CompoundTag();account.fresh.forEach((id,age)->{if(ledger.containsKey(id)){var each=new CompoundTag();each.putDouble("age",age[0]);each.putLong("at",(long)age[1]);fresh.put(id,each);}});
            if(!fresh.isEmpty())tag.put("fresh",fresh);
        }
        return tag;
    }
    public static void load(Map<String,Long> ledger,CompoundTag tag){
        ledger.clear();for(String key:tag.keySet()){long value=tag.getLongOr(key,0);if(value>0&&Identifier.tryParse(key)!=null)ledger.put(key,value);}
        if(ledger instanceof Account account){
            account.fresh.clear();var fresh=tag.getCompoundOrEmpty("fresh");
            for(String key:fresh.keySet())if(ledger.containsKey(key)){var each=fresh.getCompoundOrEmpty(key);account.fresh.put(key,new double[]{each.getDoubleOr("age",0),each.getLongOr("at",0)});}
        }
    }
    public static String describe(Map<String,Long> ledger){
        if(ledger.isEmpty())return "empty";var out=new StringJoiner(", ");
        ledger.forEach((id,count)->out.add(count+" "+Identifier.parse(id).getPath()));return out.toString();
    }
}
