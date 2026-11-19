package dev.qube.spawn_9b5fbb9.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Strict shared validation. Reloaders construct replacements before publishing them. */
public final class ContentDefinitions {
    private ContentDefinitions(){}
    public static JsonObject object(JsonElement value,String name){if(value==null||!value.isJsonObject())throw new IllegalArgumentException(name+" must be an object");return value.getAsJsonObject();}
    public static void fields(JsonObject object,String... names){Set<String> valid=Set.of(names);for(String key:object.keySet())if(!valid.contains(key))throw new IllegalArgumentException("Unknown content field: "+key);}
    public static double number(JsonObject object,String name,double fallback,double min,double max){
        if(!object.has(name)){if(!Double.isFinite(fallback)||fallback<min||fallback>max)throw new IllegalArgumentException(name+" needs an explicit valid value");return fallback;}JsonElement value=object.get(name);
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(name+" must be a number");
        double number=value.getAsDouble();if(!Double.isFinite(number)||number<min||number>max)throw new IllegalArgumentException(name+" must be finite within "+min+".."+max);return number;
    }
    public static long integer(JsonObject object,String name,long fallback,long min,long max){double value=number(object,name,fallback,min,max);if(value!=Math.rint(value))throw new IllegalArgumentException(name+" must be an integer");return (long)value;}
    public static boolean bool(JsonObject object,String name,boolean fallback){if(!object.has(name))return fallback;JsonElement value=object.get(name);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException(name+" must be a boolean");return value.getAsBoolean();}
    public static String string(JsonObject object,String name,String fallback){if(!object.has(name))return fallback;JsonElement value=object.get(name);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException(name+" must be a string");return value.getAsString();}
    public static Identifier id(String value){try{return Identifier.parse(value);}catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid registry identifier: "+value,invalid);}}
    public static Item item(String value){Identifier id=id(value);Item item=BuiltInRegistries.ITEM.getValue(id);if(item==null||item==Items.AIR||!id.equals(BuiltInRegistries.ITEM.getKey(item)))throw new IllegalArgumentException("Unknown/non-item registry identifier: "+value);return item;}
    public static Block block(String value){Identifier id=id(value);Block block=BuiltInRegistries.BLOCK.getValue(id);if(block==null||block==Blocks.AIR||!id.equals(BuiltInRegistries.BLOCK.getKey(block)))throw new IllegalArgumentException("Unknown crop block: "+value);return block;}
}
