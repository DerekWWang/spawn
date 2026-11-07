package dev.qube.spawn_9b5fbb9.survival;

import java.io.*;
import java.util.*;

/** Immutable, bounded tuning. Each saved/synchronized body carries the rules used to interpret it. */
public final class SimulationRules {
    public record Setting(double initial,double minimum,double maximum){}
    private static final Map<String,Setting> SPECS=new LinkedHashMap<>();
    static {
        setting("hungerPerDay",40,0,1000);setting("thirstPerDay",50,0,1000);
        setting("coldThreshold",35,28,36.9);setting("hotThreshold",40,37.1,44);
        setting("extremeCold",32,25,36);setting("extremeHot",41,38,45);
        setting("thermalDamageTicks",500,1,24000);setting("extremeDamageTicks",200,1,24000);
        setting("thermalGraceTicks",1000,0,168000);setting("thermalRecoveryTicks",1000,1,168000);
        setting("lowThirst",30,1,70);setting("lowThirstMental",.3,.01,1);
        setting("recreationLoss",30,0,100);setting("recreationGain",30,0,100);setting("recreationFloor",.5,.01,1);
        setting("mildIncubationTicks",6000,0,2688000);setting("mildSymptomsTicks",48000,1,2688000);setting("mildMental",.7,.01,1);
        setting("severeIncubationTicks",12000,0,2688000);setting("severeSymptomsTicks",60000,1,2688000);setting("severeMental",.5,.01,1);
        setting("severeDamageTicks",1000,1,2688000);setting("immunityTicks",336000,0,2688000);
        setting("bleedingDamageTicks",1000,1,2688000);setting("poisonDamageTicks",1500,1,2688000);setting("woundDamageTicks",3000,1,2688000);
        setting("poisonDurationTicks",6000,1,2688000);setting("painkillerTicks",6000,1,2688000);
        setting("waterPoisonChance",.18,0,1);setting("waterInfectionChance",.25,0,1);
        setting("rawFoodPoisonChance",.4,0,1);setting("rawFoodInfectionChance",.15,0,1);
        setting("woundInfectionChance",.08,0,1);setting("contactInfectionChancePerHour",.06,0,1);
        setting("anatomyRecoveryPerDay",3,0,100);setting("garmentLifetimeTicks",672000,1000,2688000);
        setting("woolJacketInsulation",.35,0,1);setting("woolPieceInsulation",.18,0,1);setting("fiberPieceInsulation",.06,0,1);
    }
    private static void setting(String name,double initial,double min,double max){SPECS.put(name,new Setting(initial,min,max));}
    public static final SimulationRules DEFAULT=new SimulationRules(Map.of());
    private final Map<String,Double> values;
    public SimulationRules(Map<String,Double> overrides){
        Map<String,Double> next=new LinkedHashMap<>();SPECS.forEach((name,spec)->next.put(name,spec.initial));
        overrides.forEach((name,value)->{
            var spec=SPECS.get(name);
            if(spec==null||value==null||!Double.isFinite(value)||value<spec.minimum||value>spec.maximum)
                throw new IllegalArgumentException("Invalid physiology setting: "+name+"="+value);
            next.put(name,value);
        });
        if(next.get("extremeCold")>=next.get("coldThreshold")||next.get("extremeHot")<=next.get("hotThreshold"))throw new IllegalArgumentException("Extreme temperature must lie beyond illness threshold");
        values=Collections.unmodifiableMap(next);
    }
    public double number(String name){Double value=values.get(name);if(value==null)throw new IllegalArgumentException(name);return value;}
    public long ticks(String name){return Math.round(number(name));}
    public Map<String,Double> values(){return values;}
    public static Map<String,Setting> definitions(){return Collections.unmodifiableMap(SPECS);}
    public void write(DataOutputStream out)throws IOException{out.writeInt(values.size());for(String key:SPECS.keySet())out.writeDouble(number(key));}
    public static SimulationRules read(DataInputStream in)throws IOException{
        int count=in.readInt();if(count<0||count>128)throw new IOException("Invalid physiology rule count");
        var keys=new ArrayList<>(SPECS.keySet());Map<String,Double> values=new LinkedHashMap<>();
        for(int i=0;i<count;i++){double value=in.readDouble();if(i<keys.size())values.put(keys.get(i),value);}
        return new SimulationRules(values);
    }
}
