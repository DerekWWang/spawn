package dev.qube.spawn_9b5fbb9.industry;

import java.util.*;
import dev.qube.spawn_9b5fbb9.content.Production;

/** Reachability over every production route. Used to prove the tree has no dead ends and that each
 * era really depends on the one before: ban a station or an item and see what can no longer be made. */
public final class TechTree {
    private TechTree(){}
    private static final String M="spawn_9b5fbb9:";
    /** Stations made by hand or on a crafting grid from gathered materials. */
    public static final Set<String> BASE=Set.of("kiln","bloomery","forge","anvil","loom","workshop","assembly_yard","niter_bed");
    /** The plant blocks a site requirement stands for. */
    public static List<String> plant(String need){
        int colon=need.indexOf(':');String kind=colon<0?need:need.substring(0,colon);
        return switch(kind){
            case "chimney"->List.of(M+"chimney");
            case "firebrick"->List.of(M+"firebrick");
            case "lead_chamber"->List.of(M+"lead_chamber");
            case "engine"->List.of(M+"stationary_engine",M+"steam_boiler",M+"chimney");
            case "electric"->List.of(M+"power_station",M+"generator",M+"stationary_engine",M+"steam_boiler",M+"chimney",M+"power_cable");
            case "equip"->List.of(M+need.substring(colon+1));
            case "cleanroom"->List.of(M+"cleanroom_panel",M+"filter_unit",M+"cleanroom_suit");
            default->List.of();
        };
    }
    /** Every item id and {@code vehicle:} token obtainable when the banned stations and items do not exist. */
    public static Set<String> reachable(Set<String> banned){
        var produced=new HashSet<String>();
        for(var r:Industry.RECIPES.values()){produced.add(r.output());produced.addAll(r.byproducts().keySet());}
        for(var r:Production.RECIPES.values())produced.add(r.output());
        var reached=new LinkedHashSet<String>();
        for(boolean changed=true;changed;){
            changed=false;
            for(var r:Production.RECIPES.values()){
                if(reached.contains(r.output())||banned.contains(r.output())||banned.contains(r.station()))continue;
                if(have(r.input(),produced,reached,banned)&&(r.second().isEmpty()||r.second().startsWith("@")||have(r.second(),produced,reached,banned)))changed|=reached.add(r.output());
            }
            for(var r:Industry.RECIPES.values()){
                if(banned.contains(r.station())||!BASE.contains(r.station())&&!reached.contains(M+r.station()))continue;
                boolean ready=true;
                for(String input:r.inputs().keySet())ready&=have(input,produced,reached,banned);
                for(String need:r.needs())for(String block:plant(need))ready&=have(block,produced,reached,banned);
                if(!ready)continue;
                if(!banned.contains(r.output()))changed|=reached.add(r.output());
                for(String extra:r.byproducts().keySet())if(!banned.contains(extra))changed|=reached.add(extra);
            }
        }
        return reached;
    }
    private static boolean have(String item,Set<String> produced,Set<String> reached,Set<String> banned){
        if(item.startsWith("#"))return true;
        if(banned.contains(item))return false;
        // Anything no recipe makes is mined, grown, gathered or grid-crafted from such things.
        return !produced.contains(item)||reached.contains(item);
    }
    /** Every product of every recipe, for dead-end checks. */
    public static Set<String> products(){
        var all=new LinkedHashSet<String>();for(var r:Industry.RECIPES.values()){all.add(r.output());all.addAll(r.byproducts().keySet());}
        for(var r:Production.RECIPES.values())all.add(r.output());return all;
    }
}
