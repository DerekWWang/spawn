package dev.qube.spawn_9b5fbb9.survival;

import java.io.*;
import java.util.Arrays;

/** Controller-independent rules. All durations are simulation ticks, never wall time. */
public final class SurvivalState {
    public static final long DAY=24000, HOUR=1000;
    public enum Activity {
        IDLE(1,1), WALKING(2,2), SPRINTING(4,3), CLIMBING(5,3), SWIMMING(6,3);
        final double food,water;
        Activity(double food,double water){this.food=food;this.water=water;}
    }
    public enum Part {
        LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, BRAIN, LEFT_EYE, RIGHT_EYE, HEARING,
        LEFT_THUMB,LEFT_INDEX,LEFT_MIDDLE,LEFT_RING,LEFT_LITTLE,
        RIGHT_THUMB,RIGHT_INDEX,RIGHT_MIDDLE,RIGHT_RING,RIGHT_LITTLE,
        LEFT_TOE_1,LEFT_TOE_2,LEFT_TOE_3,LEFT_TOE_4,LEFT_TOE_5,
        RIGHT_TOE_1,RIGHT_TOE_2,RIGHT_TOE_3,RIGHT_TOE_4,RIGHT_TOE_5;
    }
    public record Climate(double ambient,double insulation,boolean wet,boolean sheltered,boolean fire,double wind,double enclosure,boolean regional) {
        public Climate(double ambient,double insulation,boolean wet,boolean sheltered,boolean fire){this(ambient,insulation,wet,sheltered,fire,0,0,false);}
        public Climate(double ambient,double insulation,boolean wet,boolean sheltered,boolean fire,double wind,double enclosure){this(ambient,insulation,wet,sheltered,fire,wind,enclosure,true);}
        public static final Climate MILD=new Climate(18,0,false,false,false);
    }
    public record Outcome(double damage,boolean dehydrated) {}
    public double hunger=100,thirst=100,recreation=100,temperature=37,bleeding;
    public final double[] anatomy=new double[Part.values().length];
    public long livedTicks,dayElapsed,recreationTicks,quotaTicks=4500;
    public long dailyQuotaTicks=4500;
    public long thermalRecoveryTicks;
    public long poisonTicks,painkillerTicks,mildTicks,severeTicks,mildImmunity,severeImmunity,thermalTicks;
    public boolean woundInfection;
    public final String[] clothing={"","","",""};
    public final long[] clothingWear=new long[4];
    public final int[] clothingColors={-1,-1,-1,-1};
    public SimulationRules rules=SimulationRules.DEFAULT;
    public final SkillSet skills=new SkillSet();
    public SurvivalState(){Arrays.fill(anatomy,100);}
    public double movement(){
        double food=hunger>=50?1:hunger>=30?.8+(hunger-30)*.01:
            hunger>=10?.5+(hunger-10)*.015:.1+hunger*.04;
        return Math.max(.01,food*(.15+.85*average(2,4)/100)*(.7+.3*average(18,28)/100)*thermalCapacity()*painCapacity());
    }
    public double manipulation(){
        double food=.2+.8*Math.min(1,hunger/30);
        return Math.max(0,food*(.1+.9*average(0,2)/100)*(.4+.6*average(8,18)/100)*thermalCapacity()*painCapacity());
    }
    public double mental(){
        if(anatomy[Part.BRAIN.ordinal()]<=10)return 0;
        double cutoff=rules.number("lowThirst"),limit=rules.number("lowThirstMental");
        double hydration=thirst<=cutoff?limit:Math.min(1,limit+(thirst-cutoff)*(1-limit)/20);
        double floor=rules.number("recreationFloor");
        return Math.min(hydration, (floor+(1-floor)*recreation/100)*anatomy[4]/100*
            (blind()?.4:1)*(.8+.2*anatomy[7]/100)*(mildSymptoms()?rules.number("mildMental"):1)*(severeSymptoms()?rules.number("severeMental"):1)*thermalCapacity());
    }
    public boolean blind(){return anatomy[5]<=0&&anatomy[6]<=0;}
    public boolean severeSymptoms(){return severeTicks>0&&severeTicks<=rules.ticks("severeSymptomsTicks");}
    public boolean mildSymptoms(){return mildTicks>0&&mildTicks<=rules.ticks("mildSymptomsTicks");}
    private double average(int from,int to){double sum=0;for(int i=from;i<to;i++)sum+=anatomy[i];return sum/(to-from);}
    private double thermalCapacity(){return temperature<rules.number("coldThreshold")||temperature>rules.number("hotThreshold")?.55:1;}
    private double painCapacity(){return bleeding>0&&painkillerTicks==0?.75:1;}
    public void injure(Part part,double amount,boolean edged){
        if(!Double.isFinite(amount)||amount<=0)return;
        int i=part.ordinal(); anatomy[i]=clamp(anatomy[i]-amount,0,100);
        if(edged)bleeding=clamp(bleeding+amount/25,0,10);
    }
    public boolean infect(boolean severe){
        if(severe){if(severeTicks>0||severeImmunity>livedTicks)return false;severeTicks=rules.ticks("severeIncubationTicks")+rules.ticks("severeSymptomsTicks");}
        else{if(mildTicks>0||mildImmunity>livedTicks)return false;mildTicks=rules.ticks("mildIncubationTicks")+rules.ticks("mildSymptomsTicks");}
        return true;
    }
    public boolean treat(String item){
        switch(item){
            case "bandage" -> {if(bleeding<=0)return false;bleeding=0;}
            case "painkiller" -> {if(painkillerTicks>0)return false;painkillerTicks=rules.ticks("painkillerTicks");}
            case "antitoxin" -> {if(poisonTicks<=0)return false;poisonTicks=0;}
            case "antiseptic" -> {if(!woundInfection)return false;woundInfection=false;}
            case "antibiotic" -> {
                if(severeTicks<=0&&mildTicks<=0)return false;
                if(severeTicks>0)severeImmunity=livedTicks+rules.ticks("immunityTicks");
                if(mildTicks>0)mildImmunity=livedTicks+rules.ticks("immunityTicks");
                severeTicks=mildTicks=0;
            }
            default -> {return false;}
        }
        return true;
    }
    public Outcome advance(long ticks,Activity activity,Climate climate,boolean recreating,boolean sleeping){
        if(ticks<0||ticks>7*DAY)throw new IllegalArgumentException("elapsed ticks out of bounds");
        double damage=0;
        // Bounded substeps keep large sleep/test intervals equivalent to real entity updates.
        while(ticks>0){
            long step=Math.min(20,Math.min(ticks,DAY-dayElapsed));
            skills.alignDay(dayElapsed);skills.advance(step);
            livedTicks+=step;dayElapsed+=step;ticks-=step;
            hunger=clamp(hunger-rules.number("hungerPerDay")*step/DAY*(sleeping?.5:activity.food),0,100);
            double insulation=clamp(climate.insulation*(climate.wet?.65:1),0,.95);
            double ambient=climate.ambient+(climate.wet?-4:0);
            if(climate.sheltered&&!climate.regional)ambient=18+(ambient-18)*.5;
            if(climate.regional)ambient-=Math.max(0,climate.wind)*(1-clamp(climate.enclosure,0,1))*.35*Math.min(1,Math.max(0,20-ambient)/20);
            double heat=1+Math.min(1,Math.max(0,(ambient-26)/16))*1.5;
            thirst=clamp(thirst-rules.number("thirstPerDay")*step/DAY*(sleeping?.5:activity.water)*heat,0,100);
            if(recreating&&!sleeping)recreationTicks=Math.min(quotaTicks,recreationTicks+step);
            double target=37-Math.max(0,18-ambient)*.13*(1-insulation)
                +Math.max(0,ambient-26)*(.25*(1-.65*thirst/100)+insulation*.08);
            if(activity.food>=4)target+=.4;
            if(climate.fire)target=Math.max(37.2,target);
            temperature=clamp(temperature+(target-temperature)*step/12000d,25,45);
            if(temperature<rules.number("coldThreshold")||temperature>rules.number("hotThreshold")){
                thermalRecoveryTicks=0;
                thermalTicks+=step;
                if(thermalTicks>rules.ticks("thermalGraceTicks"))damage+=step/(temperature<rules.number("extremeCold")||temperature>rules.number("extremeHot")?rules.number("extremeDamageTicks"):rules.number("thermalDamageTicks"));
            }else{thermalRecoveryTicks+=step;if(thermalRecoveryTicks>=rules.ticks("thermalRecoveryTicks"))thermalTicks=0;}
            damage+=bleeding*step/rules.number("bleedingDamageTicks");
            if(hunger<=0)damage+=step/1000d;
            if(poisonTicks>0){damage+=step/rules.number("poisonDamageTicks");poisonTicks=Math.max(0,poisonTicks-step);}
            if(severeSymptoms())damage+=step/rules.number("severeDamageTicks");
            if(woundInfection){damage+=step/rules.number("woundDamageTicks");}
            painkillerTicks=Math.max(0,painkillerTicks-step);
            if(mildTicks>0){mildTicks=Math.max(0,mildTicks-step);if(mildTicks==0)mildImmunity=livedTicks+rules.ticks("immunityTicks");}
            if(severeTicks>0){severeTicks=Math.max(0,severeTicks-step);if(severeTicks==0)severeImmunity=livedTicks+rules.ticks("immunityTicks");}
            if(hunger>50&&thirst>50&&bleeding==0&&!severeSymptoms()&&!woundInfection){
                for(int i=0;i<anatomy.length;i++)if(!(i==5||i==6)||anatomy[i]>0)
                    anatomy[i]=Math.min(100,anatomy[i]+step*rules.number("anatomyRecoveryPerDay")/DAY);
            }
            for(int i=0;i<4;i++)if(!clothing[i].isEmpty()){
                clothingWear[i]+=step;
                if(clothingWear[i]>=rules.ticks("garmentLifetimeTicks")){clothing[i]="";clothingWear[i]=0;}
            }
            if(dayElapsed==DAY){
                double missing=1-Math.min(1,(double)recreationTicks/Math.max(1,quotaTicks));
                recreation=clamp(recreation+(missing==0?rules.number("recreationGain"):-rules.number("recreationLoss")*missing),0,100);
                dayElapsed=recreationTicks=0;quotaTicks=dailyQuotaTicks;
            }
        }
        return new Outcome(damage,thirst<=0);
    }
    public void drink(double amount){thirst=clamp(thirst+amount,0,100);if(temperature>37)temperature=Math.max(37,temperature-.3);}
    public void feed(double amount){hunger=clamp(hunger+amount,0,100);}
    public String conditions(){
        StringBuilder s=new StringBuilder();
        if(bleeding>0)s.append("BLEEDING ");if(poisonTicks>0)s.append("POISONED ");
        if(mildTicks>0)s.append("RESPIRATORY INFECTION ");if(severeTicks>0)s.append("SYSTEMIC INFECTION ");
        if(woundInfection)s.append("WOUND INFECTION ");if(blind())s.append("PERMANENT BLINDNESS ");
        if(temperature<rules.number("coldThreshold"))s.append("HYPOTHERMIA ");if(temperature>rules.number("hotThreshold"))s.append("HEAT STROKE ");
        if(painkillerTicks>0)s.append("PAIN RELIEF ");return s.isEmpty()?"Stable":s.toString().trim();
    }
    public byte[] encode(){
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
            out.writeInt(6);
            for(double value:new double[]{hunger,thirst,recreation,temperature,bleeding})out.writeDouble(value);
            for(long value:new long[]{livedTicks,dayElapsed,recreationTicks,quotaTicks,poisonTicks,painkillerTicks,
                mildTicks,severeTicks,mildImmunity,severeImmunity,thermalTicks})out.writeLong(value);
            for(double part:anatomy)out.writeDouble(part);
            out.writeBoolean(woundInfection);
            for(int i=0;i<4;i++){out.writeUTF(clothing[i]);out.writeLong(clothingWear[i]);}
            out.writeLong(dailyQuotaTicks);
            out.writeLong(thermalRecoveryTicks);for(int color:clothingColors)out.writeInt(color);
            rules.write(out);
            skills.write(out);
            return bytes.toByteArray();
        }catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
    public static SurvivalState decode(byte[] data){
        SurvivalState s=new SurvivalState();if(data.length==0||data.length>8192)return s;
        try{
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(data));int version=in.readInt();if(version<1||version>6)return s;
            s.hunger=bounded(in.readDouble(),0,100);s.thirst=bounded(in.readDouble(),0,100);
            s.recreation=bounded(in.readDouble(),0,100);s.temperature=bounded(in.readDouble(),25,45);s.bleeding=bounded(in.readDouble(),0,10);
            s.livedTicks=Math.max(0,in.readLong());s.dayElapsed=Math.floorMod(in.readLong(),DAY);
            s.recreationTicks=Math.max(0,in.readLong());s.quotaTicks=Math.max(1,Math.min(5000,in.readLong()));
            s.poisonTicks=Math.max(0,in.readLong());s.painkillerTicks=Math.max(0,in.readLong());
            s.mildTicks=Math.max(0,in.readLong());s.severeTicks=Math.max(0,in.readLong());
            s.mildImmunity=Math.max(0,in.readLong());s.severeImmunity=Math.max(0,in.readLong());s.thermalTicks=Math.max(0,in.readLong());
            for(int i=0;i<s.anatomy.length;i++)s.anatomy[i]=bounded(in.readDouble(),0,100);
            s.woundInfection=in.readBoolean();for(int i=0;i<4;i++){s.clothing[i]=in.readUTF();s.clothingWear[i]=Math.max(0,in.readLong());}
            if(version>=2)s.dailyQuotaTicks=Math.max(4000,Math.min(5000,in.readLong()));
            if(version>=3){s.thermalRecoveryTicks=Math.max(0,in.readLong());for(int i=0;i<4;i++)s.clothingColors[i]=in.readInt();}
            if(version>=4)s.rules=SimulationRules.read(in);
            if(version>=6)s.skills.read(in);else if(version==5)s.skills.readLegacy(in);
            if(version<6)s.skills.alignDay(s.dayElapsed);
            return s;
        }catch(IOException|IllegalArgumentException invalid){return new SurvivalState();}
    }
    private static double bounded(double value,double min,double max){if(!Double.isFinite(value))throw new IllegalArgumentException();return clamp(value,min,max);}
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
}
