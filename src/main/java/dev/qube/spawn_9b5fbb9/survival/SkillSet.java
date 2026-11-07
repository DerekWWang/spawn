package dev.qube.spawn_9b5fbb9.survival;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Locale;

/** Learned competence, independent of bodily capacity. Practice has increasing level costs. */
public final class SkillSet {
    public enum Skill {
        POTTERY, SMITHING, WEAVING, FARMING, FORAGING, HUNTING;
        public String label(){String s=name().toLowerCase(Locale.ROOT);return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    }
    private final double[] experience=new double[Skill.values().length];
    public double experience(Skill skill){return experience[skill.ordinal()];}
    public int level(Skill skill){return Math.min(100,(int)Math.floor(2*Math.sqrt(experience(skill))));}
    public double untilNext(Skill skill){int level=level(skill);return level==100?0:(level+1d)*(level+1d)/4-experience(skill);}
    public void practice(Skill skill,double amount){if(skill!=null&&Double.isFinite(amount)&&amount>0)experience[skill.ordinal()]=Math.min(2500,experience(skill)+amount);}
    public void copyFrom(SkillSet other){System.arraycopy(other.experience,0,experience,0,experience.length);}
    public void setLevel(Skill skill,int level){if(level<0||level>100)throw new IllegalArgumentException("Skill level must be 0 to 100");experience[skill.ordinal()]=level*level/4d;}
    public void write(DataOutput out)throws IOException{for(double value:experience)out.writeDouble(value);}
    public void read(DataInput in)throws IOException{
        for(int i=0;i<experience.length;i++){double value=in.readDouble();if(!Double.isFinite(value)||value<0||value>2500)throw new IOException("Invalid skill experience");experience[i]=value;}
    }
}
