package dev.qube.spawn_9b5fbb9.survival;

import java.io.*;
import static dev.qube.spawn_9b5fbb9.survival.SkillSet.Skill.*;
import static dev.qube.spawn_9b5fbb9.survival.SkillSet.Passion.*;

/** Learning boundaries and a fixture written by the pre-change schema-five encoder. */
public final class LearningTests {
    private static int assertions;
    public static int run() throws Exception {
        var none=new SkillSet();none.practice(POTTERY,1000);close(none.experience(POTTERY),350,"uninterested learning");
        var interested=new SkillSet();interested.setPassion(POTTERY,INTERESTED);interested.practice(POTTERY,1000);
        check(interested.level(POTTERY)==1,"1000 XP reaches level one");check(interested.level(SMITHING)==0,"trades remain independent");
        var burning=new SkillSet();burning.setPassion(POTTERY,BURNING);burning.practice(POTTERY,1000);close(burning.experience(POTTERY),1500,"burning learning");
        interested.practice(POTTERY,2900);interested.practice(POTTERY,200);close(interested.experience(POTTERY),4020,"split award at daily cap");
        interested.practice(POTTERY,100);close(interested.experience(POTTERY),4040,"learning beyond cap is one fifth");
        interested.advance(24000);close(interested.dailyNetExperience(POTTERY),0,"new simulated day resets cap");
        interested.practice(POTTERY,100);close(interested.experience(POTTERY),4140,"new day restores full learning");
        var threshold=new SkillSet();threshold.setLevel(MINING,10);close(threshold.experience(MINING),55000,"level ten threshold");
        threshold.setLevel(MINING,20);close(threshold.experience(MINING),265000,"level twenty threshold");
        threshold.setLevel(MINING,9);threshold.advance(7*24000);close(threshold.experience(MINING),45000,"ordinary competence does not decay");
        threshold.setLevel(MINING,10);threshold.advance(24000);close(threshold.experience(MINING),54970,"expert decay uses simulation days");
        check(threshold.level(MINING)==10,"small decay does not oscillate the displayed level");
        var top=new SkillSet();top.setLevel(MINING,20);top.advance(24000);check(top.level(MINING)==19,"one thousand XP buffer eventually downgrades mastery");
        check(top.experience(MINING)>261000&&top.experience(MINING)<264000,"downgraded decay continues at the lower rate");
        var split=new SkillSet();split.setLevel(MINING,20);for(int i=0;i<1200;i++)split.advance(20);
        close(split.experience(MINING),top.experience(MINING),"split and combined decay agree at level boundary");
        top.setPassion(MINING,BURNING);top.practice(MINING,Double.MAX_VALUE);check(top.level(MINING)==20&&top.experience(MINING)<=297000,"mastery cannot accumulate unbounded XP");
        double before=top.experience(MINING);top.practice(MINING,Double.NaN);top.practice(MINING,-1);close(top.experience(MINING),before,"invalid awards do not alter progress");
        var body=new SurvivalState();body.skills.copyFrom(interested);body.skills.setPassion(MEDICINE,BURNING);
        var loaded=SurvivalState.decode(body.encode());check(loaded.skills.passion(MEDICINE)==BURNING,"saved passions");
        close(loaded.skills.dailyNetExperience(POTTERY),100,"saved daily cap");close(loaded.skills.experience(POTTERY),4140,"saved XP");
        check(body.encode().length<8192,"state fits the negotiated packet bound");
        var old=SurvivalState.decode(LearningTests.class.getResourceAsStream("/survival-v5.bin").readAllBytes());
        check(old.skills.level(POTTERY)==5&&old.skills.level(SMITHING)==20,"historical trade competence migration");
        close(old.skills.experience(WEAVING),4500,"historical fractional competence migration");
        close(old.skills.experience(FARMING),51000,"historical farming progress migration");
        close(old.skills.experience(FORAGING),200,"historical early progress migration");
        check(old.skills.level(MINING)==0&&old.anatomy[0]==25&&old.hunger==37.625&&old.clothing[2].endsWith("wool_jacket"),"migration preserves the body and starts new trades as novices");
        var invalid=new ByteArrayOutputStream();var out=new DataOutputStream(invalid);
        out.writeDouble(1);out.writeLong(0);out.writeInt(2);
        for(int i=0;i<2;i++){out.writeUTF("POTTERY");out.writeInt(1);out.writeDouble(0);out.writeByte(0);out.writeDouble(0);out.writeLong(0);}
        boolean duplicate=false;try{new SkillSet().read(new DataInputStream(new ByteArrayInputStream(invalid.toByteArray())));}catch(IOException expected){duplicate=true;}
        check(duplicate,"duplicate stable skill records rejected");
        return assertions;
    }
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    private static void close(double actual,double expected,String message){check(Math.abs(actual-expected)<.0001,message+": "+actual+" != "+expected);}
}
