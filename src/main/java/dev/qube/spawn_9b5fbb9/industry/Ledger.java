package dev.qube.spawn_9b5fbb9.industry;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
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
    /** Takes one real stack (at most a full stack) of the first stored item. */
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
