package dev.qube.spawn_9b5fbb9.survival;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import dev.qube.spawn_9b5fbb9.agriculture.*;
import dev.qube.spawn_9b5fbb9.content.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/** Data-pack reload on the server, with rollback if any content group fails validation. */
public final class RealismData {
    private static volatile SimulationRules active=SimulationRules.DEFAULT;
    private static JsonObject previousContent=new JsonObject();
    private record Pending(SimulationRules rules,JsonObject content){}
    private static volatile Pending pending;
    private RealismData(){}
    public static SimulationRules rules(){return active;}
    public static void init(){NeoForge.EVENT_BUS.addListener(RealismData::listeners);NeoForge.EVENT_BUS.addListener(RealismData::bound);}
    private static void listeners(AddServerReloadListenersEvent event){
        event.addListener(Identifier.fromNamespaceAndPath("spawn_9b5fbb9","survival_rules"),(ResourceManagerReloadListener)RealismData::reload);
    }
    private static JsonObject read(ResourceManager manager,String path){
        var resource=manager.getResource(Identifier.fromNamespaceAndPath("spawn_9b5fbb9","survival/"+path));
        if(resource.isEmpty())return new JsonObject();
        try(var reader=resource.get().openAsReader()){return JsonParser.parseReader(reader).getAsJsonObject();}
        catch(IOException|RuntimeException invalid){throw new IllegalArgumentException("Invalid survival/"+path,invalid);}
    }
    public static SimulationRules physiology(JsonObject json){
        for(String key:json.keySet())if(!key.equals("values")&&!key.equals("pathogens"))throw new IllegalArgumentException("Unknown physiology group: "+key);
        Map<String,Double> values=new LinkedHashMap<>();JsonObject settings=json.has("values")?json.getAsJsonObject("values"):new JsonObject();
        for(var entry:settings.entrySet())values.put(entry.getKey(),number(entry.getValue(),entry.getKey()));
        if(json.has("pathogens"))for(var entry:json.getAsJsonObject("pathogens").entrySet()){
            String prefix=switch(entry.getKey()){case "respiratory"->"mild";case "systemic"->"severe";default->throw new IllegalArgumentException("Unknown pathogen: "+entry.getKey());};
            JsonObject pathogen=entry.getValue().getAsJsonObject();
            for(var property:pathogen.entrySet()){
                String target=switch(property.getKey()){
                    case "incubationTicks"->prefix+"IncubationTicks";case "symptomsTicks"->prefix+"SymptomsTicks";case "mentalCapacity"->prefix+"Mental";
                    case "damageIntervalTicks"->{if(!prefix.equals("severe"))throw new IllegalArgumentException("Respiratory strain is nonlethal");yield "severeDamageTicks";}
                    default->throw new IllegalArgumentException("Unknown pathogen property: "+property.getKey());
                };values.put(target,number(property.getValue(),property.getKey()));
            }
        }
        return new SimulationRules(values);
    }
    private static double number(JsonElement value,String key){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(key+" must be numeric");return value.getAsDouble();}
    private static JsonObject group(JsonObject data,String name){return data.has(name)?data.getAsJsonObject(name):new JsonObject();}
    private static void content(JsonObject data){
        for(String key:data.keySet())if(!java.util.Set.of("crops","husbandry","decay","production").contains(key))throw new IllegalArgumentException("Unknown content group: "+key);
        SeasonalCrops.reloadDefinitions(group(data,"crops"));Husbandry.reloadDefinitions(group(data,"husbandry"));
        ItemAging.reloadDefinitions(group(data,"decay"));Production.reloadDefinitions(group(data,"production"));
    }
    public static void reload(ResourceManager resources){
        var next=physiology(read(resources,"physiology.json"));var nextContent=read(resources,"content_rules.json");
        // 26.3 binds dynamic item components after reload listeners; ItemStacks are unsafe until then.
        pending=new Pending(next,nextContent);
    }
    private static void bound(net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent event){
        if(event.getUpdateCause()!=net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent.UpdateCause.SERVER_DATA_LOAD)return;
        Pending next=pending;if(next==null)return;pending=null;
        try{content(next.content);}catch(RuntimeException invalid){content(previousContent);throw invalid;}
        previousContent=next.content.deepCopy();active=next.rules;
    }
}
