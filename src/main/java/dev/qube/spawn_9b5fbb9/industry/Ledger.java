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

/** Bulk material accounts keyed by item id. Industrial quantities never fit in slot inventories;
 * every unit still enters from a real stack and leaves as a real stack. */
public final class Ledger {
    private Ledger(){}
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
    /** Marching pace under load: a soldier's 35 kg is carried all day; beyond it pace falls away to a tenth at 120 kg. */
    public static double pace(double kilograms){return kilograms<=35?1:Math.max(.1,1-.9*(kilograms-35)/85);}
    public static double mass(Map<String,Long> ledger){double sum=0;for(var entry:ledger.entrySet())sum+=mass(entry.getKey())*entry.getValue();return sum;}
    public static long total(Map<String,Long> ledger){long sum=0;for(long value:ledger.values())sum+=value;return sum;}
    public static long count(Map<String,Long> ledger,String ingredient){
        if(!ingredient.startsWith("#"))return ledger.getOrDefault(ingredient,0L);
        long sum=0;for(var entry:ledger.entrySet())if(matches(ingredient,entry.getKey()))sum+=entry.getValue();return sum;
    }
    public static void add(Map<String,Long> ledger,String itemId,long amount){if(amount>0)ledger.merge(itemId,amount,Long::sum);}
    /** Removes up to {@code amount} units matching the ingredient; returns the amount actually removed. */
    public static long take(Map<String,Long> ledger,String ingredient,long amount){
        long remaining=amount;
        for(var iterator=ledger.entrySet().iterator();iterator.hasNext()&&remaining>0;){
            var entry=iterator.next();if(!matches(ingredient,entry.getKey()))continue;
            long used=Math.min(remaining,entry.getValue());remaining-=used;
            if(used==entry.getValue())iterator.remove();else entry.setValue(entry.getValue()-used);
        }
        return amount-remaining;
    }
    public static boolean has(Map<String,Long> ledger,Map<String,Integer> requirements){
        for(var need:requirements.entrySet())if(count(ledger,need.getKey())<need.getValue())return false;return true;
    }
    /** Why this stack cannot go into a bulk account, or nothing. An account knows a kind and a number: what comes
     * out is new stock of that kind, so food already spoiled would come out sound and a filled vessel empty. */
    public static String unfit(ItemStack stack){
        if(dev.qube.spawn_9b5fbb9.content.ItemAging.spoiled(stack)&&!id(stack).endsWith("spoiled_food"))return "Spoiled food is not cargo: it would have to be sorted from the sound";
        var inside=stack.get(DataComponents.CONTAINER);var data=dev.qube.spawn_9b5fbb9.content.ItemAging.data(stack);
        if(inside!=null&&inside.nonEmptyItems().iterator().hasNext()||data.getIntOr("water",0)>0||data.getBooleanOr("sealed",false))return "Empty the vessel first: bulk stowage keeps the vessel, not what is in it";
        return "";
    }
    /** Takes one real stack (at most a full stack) of the given item. */
    public static ItemStack withdrawStack(Map<String,Long> ledger,String itemId){
        long stored=ledger.getOrDefault(itemId,0L);Item item=item(itemId);if(stored<=0||item==Items.AIR){ledger.remove(itemId);return ItemStack.EMPTY;}
        int count=(int)Math.min(stored,new ItemStack(item).getMaxStackSize());take(ledger,itemId,count);return new ItemStack(item,count);
    }
    public static CompoundTag save(Map<String,Long> ledger){var tag=new CompoundTag();ledger.forEach(tag::putLong);return tag;}
    public static void load(Map<String,Long> ledger,CompoundTag tag){
        ledger.clear();for(String key:tag.keySet()){long value=tag.getLongOr(key,0);if(value>0&&Identifier.tryParse(key)!=null)ledger.put(key,value);}
    }
    public static String describe(Map<String,Long> ledger){
        if(ledger.isEmpty())return "empty";var out=new StringJoiner(", ");
        ledger.forEach((id,count)->out.add(count+" "+Identifier.parse(id).getPath()));return out.toString();
    }
}
