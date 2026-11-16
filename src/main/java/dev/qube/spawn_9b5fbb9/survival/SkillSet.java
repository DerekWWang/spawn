package dev.qube.spawn_9b5fbb9.survival;

import java.io.*;
import java.util.*;

/** Persistent competence and interests, independent of anatomy and wall time. */
public final class SkillSet {
    public static final int MAX_LEVEL=20;
    public static final double DAILY_SOFT_CAP=4000;
    private static final long DAY=24000;
    private static final int[] THRESHOLDS={0,1000,3000,6000,10000,15000,21000,28000,36000,45000,55000,67000,81000,97000,115000,135000,157000,181000,207000,235000,265000,297000};
    private static final double[] DECAY={0,0,0,0,0,0,0,0,0,0,30,60,120,180,300,540,840,1200,1800,2400,3600};
    public enum Skill {
        POTTERY, SMITHING, WEAVING, FARMING, FORAGING, HUNTING,
        MINING, CONSTRUCTION, COOKING, ANIMALS, MEDICINE,
        // Appended only: saved records are keyed by name, so older saves load with these at level 0.
        MELEE, MARKSMANSHIP, RIDING, GUNNERY, ENGINEERING;
        public String label(){String s=name().toLowerCase(Locale.ROOT);return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    }
    public enum Passion {
        NONE(.35), INTERESTED(1), BURNING(1.5);
        public final double multiplier;
        Passion(double multiplier){this.multiplier=multiplier;}
    }
    private static final class Entry {int level;double xp,daily;Passion passion=Passion.NONE;long lastPractice;}
    private final EnumMap<Skill,Entry> records=new EnumMap<>(Skill.class);
    private double learningFactor=1;
    private long elapsed;
    public SkillSet(){for(var skill:Skill.values())records.put(skill,new Entry());}
    public int level(Skill skill){return records.get(skill).level;}
    public double experience(Skill skill){var e=records.get(skill);return THRESHOLDS[e.level]+e.xp;}
    public double levelExperience(Skill skill){return records.get(skill).xp;}
    public double levelCost(Skill skill){return cost(level(skill));}
    public double untilNext(Skill skill){return level(skill)==MAX_LEVEL?0:cost(level(skill))-records.get(skill).xp;}
    public Passion passion(Skill skill){return records.get(skill).passion;}
    public void setPassion(Skill skill,Passion passion){records.get(Objects.requireNonNull(skill)).passion=Objects.requireNonNull(passion);}
    public double learningFactor(){return learningFactor;}
    public void setLearningFactor(double value){if(!Double.isFinite(value)||value<0||value>10)throw new IllegalArgumentException("Learning factor must be 0 to 10");learningFactor=value;}
    public double learningMultiplier(Skill skill){return learningFactor*passion(skill).multiplier;}
    public double dailyNetExperience(Skill skill){return records.get(skill).daily;}
    public double decayPerDay(Skill skill){return DECAY[level(skill)];}
    public long lastPracticeTick(Skill skill){return records.get(skill).lastPractice;}
    public void alignDay(long dayOffset){if(elapsed==0)elapsed=Math.floorMod(dayOffset,DAY);}
    public void practice(Skill skill,double amount){
        if(skill==null||!Double.isFinite(amount)||amount<=0)return;
        var e=records.get(skill);double raw=Math.min(amount,1e12)*learningMultiplier(skill);
        double allowance=Math.max(0,DAILY_SOFT_CAP-e.daily);
        double gained=Math.min(raw,allowance)+Math.max(0,raw-allowance)*.2;
        double actual=Math.min(gained,THRESHOLDS[21]-experience(skill));
        if(actual<=0)return;
        e.xp+=actual;e.daily+=actual;e.lastPractice=elapsed;
        while(e.level<MAX_LEVEL&&e.xp>=cost(e.level)){e.xp-=cost(e.level);e.level++;}
    }
    public void advance(long ticks){
        if(ticks<0||ticks>7*DAY)throw new IllegalArgumentException("Skill elapsed ticks out of bounds");
        while(ticks>0){
            long step=Math.min(ticks,DAY-Math.floorMod(elapsed,DAY));
            for(var e:records.values())decay(e,step);
            elapsed+=step;ticks-=step;
            if(elapsed%DAY==0)for(var e:records.values())e.daily=0;
        }
    }
    private static void decay(Entry e,double ticks){
        while(ticks>1e-9&&e.level>=10){
            double rate=DECAY[e.level]/DAY,boundary=Math.max(0,(e.xp+1000)/rate);
            double used=Math.min(ticks,boundary),loss=used*rate;
            e.xp-=loss;e.daily-=loss;ticks-=used;
            if(boundary<=used+1e-9){e.level--;e.xp+=cost(e.level);}else break;
        }
    }
    private static double cost(int level){return THRESHOLDS[level+1]-THRESHOLDS[level];}
    public void setLevel(Skill skill,int level){if(level<0||level>MAX_LEVEL)throw new IllegalArgumentException("Skill level must be 0 to 20");var e=records.get(skill);e.level=level;e.xp=0;}
    public void copyFrom(SkillSet other){
        learningFactor=other.learningFactor;elapsed=other.elapsed;
        for(var skill:Skill.values()){var from=other.records.get(skill);var to=records.get(skill);to.level=from.level;to.xp=from.xp;to.daily=from.daily;to.passion=from.passion;to.lastPractice=from.lastPractice;}
    }
    public void write(DataOutput out)throws IOException{
        out.writeDouble(learningFactor);out.writeLong(elapsed);out.writeInt(records.size());
        for(var skill:Skill.values()){var e=records.get(skill);out.writeUTF(skill.name());out.writeInt(e.level);out.writeDouble(e.xp);out.writeByte(e.passion.ordinal());out.writeDouble(e.daily);out.writeLong(e.lastPractice);}
    }
    public void read(DataInput in)throws IOException{
        var parsed=new SkillSet();double factor=in.readDouble();long time=in.readLong();int count=in.readInt();
        if(!Double.isFinite(factor)||factor<0||factor>10||time<0||time>Long.MAX_VALUE-7*DAY||count<0||count>Skill.values().length)throw new IOException("Invalid learning header");
        parsed.learningFactor=factor;parsed.elapsed=time;var seen=EnumSet.noneOf(Skill.class);
        for(int i=0;i<count;i++){
            Skill skill;try{skill=Skill.valueOf(in.readUTF());}catch(IllegalArgumentException invalid){throw new IOException("Unknown skill identifier",invalid);}
            if(!seen.add(skill))throw new IOException("Duplicate skill identifier");
            var e=parsed.records.get(skill);e.level=in.readInt();e.xp=in.readDouble();int passion=in.readUnsignedByte();e.daily=in.readDouble();e.lastPractice=in.readLong();
            if(e.level<0||e.level>MAX_LEVEL||!Double.isFinite(e.xp)||e.xp<0&&e.level<10||e.xp< -1000||e.xp>cost(e.level)||e.level<MAX_LEVEL&&e.xp>=cost(e.level)||passion>=Passion.values().length||!Double.isFinite(e.daily)||Math.abs(e.daily)>1e6||e.lastPractice<0||e.lastPractice>time)throw new IOException("Invalid learning record");
            e.passion=Passion.values()[passion];
        }
        copyFrom(parsed);
    }
    /** Exactly six ordinal doubles were written by the schema-five encoder. */
    public void readLegacy(DataInput in)throws IOException{
        var parsed=new SkillSet();
        for(int i=0;i<6;i++){
            double old=in.readDouble();if(!Double.isFinite(old)||old<0||old>2500)throw new IOException("Invalid legacy skill");
            double score=Math.min(20,2*Math.sqrt(old)/5);int level=(int)Math.floor(score);var e=parsed.records.get(Skill.values()[i]);e.level=level;e.xp=level==20?0:(score-level)*cost(level);
        }
        copyFrom(parsed);
    }
}
