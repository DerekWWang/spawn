package dev.qube.spawn_9b5fbb9.survival;

/** Dependency-free behavioral tests, executed with the project's Java 25 toolchain. */
public final class SimulationTests {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        SurvivalState starved = new SurvivalState(); starved.hunger = 0;
        close(starved.movement(), .1, "starvation must leave one tenth base speed");
        SurvivalState dry = new SurvivalState(); dry.thirst = 30;
        close(dry.mental(), .3, "30 percent thirst limits mental capacity");
        SurvivalState idle = new SurvivalState(), swimming = new SurvivalState();
        idle.advance(1000, SurvivalState.Activity.IDLE, SurvivalState.Climate.MILD, false, false);
        swimming.advance(1000, SurvivalState.Activity.SWIMMING, SurvivalState.Climate.MILD, false, false);
        check(100-swimming.hunger > 5*(100-idle.hunger), "swimming must cost much more than rest");
        SurvivalState hot = new SurvivalState(), mild = new SurvivalState();
        hot.advance(1000, SurvivalState.Activity.SPRINTING, new SurvivalState.Climate(42, 0, false, false, false), false, false);
        mild.advance(1000, SurvivalState.Activity.SPRINTING, SurvivalState.Climate.MILD, false, false);
        check(hot.thirst < mild.thirst, "hot activity must consume additional water");
        SurvivalState sleeping = new SurvivalState();
        sleeping.advance(4500, SurvivalState.Activity.IDLE, SurvivalState.Climate.MILD, true, true);
        close(sleeping.recreationTicks, 0, "sleep cannot satisfy recreation");
        SurvivalState social = new SurvivalState();
        social.advance(4500, SurvivalState.Activity.IDLE, SurvivalState.Climate.MILD, true, false);
        close(social.recreationTicks, 4500, "active recreation accrues actual elapsed hours");
        SurvivalState neglected = new SurvivalState();
        neglected.advance(24000, SurvivalState.Activity.IDLE, SurvivalState.Climate.MILD, false, false);
        close(neglected.recreation, 70, "a full missed day reduces recreation reserve");
        SurvivalState wounded = new SurvivalState();
        wounded.injure(SurvivalState.Part.LEFT_ARM, 75, true);
        check(wounded.manipulation() < 1, "arm damage must reduce manipulation");
        check(wounded.advance(1000, SurvivalState.Activity.IDLE, SurvivalState.Climate.MILD, false, false).damage() > 0,
            "untreated bleeding must damage health");
        check(wounded.treat("bandage"), "bandage must be an accepted treatment");
        close(wounded.bleeding, 0, "bandage must stop excessive bleeding");
        wounded.injure(SurvivalState.Part.LEFT_EYE, 100, false);
        wounded.injure(SurvivalState.Part.RIGHT_EYE, 100, false);
        check(wounded.blind(), "destroyed eyes must cause blindness");
        wounded.advance(24000, SurvivalState.Activity.IDLE, SurvivalState.Climate.MILD, false, false);
        check(wounded.blind(), "ordinary recovery must not regenerate destroyed eyes");
        SurvivalState infected = new SurvivalState();
        check(infected.infect(true), "susceptible entity can contract deadly infection");
        double damage = 0;
        for (int i=0; i<3600; i++) { infected.hunger=100; infected.thirst=100;
            damage += infected.advance(20, SurvivalState.Activity.IDLE, SurvivalState.Climate.MILD, false, false).damage(); }
        check(damage >= 20, "untreated deadly infection must be capable of killing a fed hydrated entity");
        check(!infected.infect(true), "recovery must grant pathogen immunity");
        SurvivalState cold = new SurvivalState(), clothed = new SurvivalState();
        for (int i=0; i<1200; i++) {
            cold.advance(20, SurvivalState.Activity.IDLE, new SurvivalState.Climate(-15,0,false,false,false),false,false);
            clothed.advance(20, SurvivalState.Activity.IDLE,new SurvivalState.Climate(-15,.9,false,false,false),false,false);
        }
        check(cold.temperature < 35 && clothed.temperature > 35, "warm clothing must prevent ordinary cold illness");
        wounded.clothing[2]="spawn_9b5fbb9:wool_jacket";
        SurvivalState restored=SurvivalState.decode(wounded.encode());
        check(restored.blind() && restored.clothing[2].endsWith("wool_jacket"), "save/load must preserve injuries and clothing");
        close(restored.hunger,wounded.hunger,"save/load must preserve fractional needs");
        SurvivalState empty=SurvivalState.decode(new byte[]{99});
        close(empty.thirst,100,"corrupt or future data must fail safely");
        check(!empty.treat("unrecognized"),"unknown treatment cannot mutate state");
        SurvivalState configured=new SurvivalState();configured.dailyQuotaTicks=4000;
        configured.advance(24000,SurvivalState.Activity.IDLE,SurvivalState.Climate.MILD,false,false);
        close(configured.quotaTicks,4000,"configured recreation must survive day rollover");
        SurvivalState hearing=new SurvivalState();hearing.injure(SurvivalState.Part.HEARING,100,false);
        check(hearing.mental()<1,"sensory injury must affect mental work");
        SurvivalState mildInfection=new SurvivalState();mildInfection.infect(false);
        mildInfection.advance(5999,SurvivalState.Activity.IDLE,SurvivalState.Climate.MILD,false,false);
        close(mildInfection.mental(),1,"mild infection must incubate for six hours");
        mildInfection.advance(1,SurvivalState.Activity.IDLE,SurvivalState.Climate.MILD,false,false);
        close(mildInfection.mental(),.7,"symptoms must begin at the six-hour boundary");
        byte[] legacy=SimulationTests.class.getResourceAsStream("/survival-v1.bin").readAllBytes();var migrated=SurvivalState.decode(legacy);
        check(migrated.blind()&&migrated.hunger==66.25&&migrated.clothingWear[2]==648000,"fixed historical version-one fixture must retain injuries, fractions, and wear");
        SurvivalState recovering=new SurvivalState();recovering.thermalTicks=2000;
        recovering.advance(500,SurvivalState.Activity.IDLE,SurvivalState.Climate.MILD,false,false);
        check(recovering.thermalTicks==2000,"brief normal temperature must not erase accumulated illness");
        recovering.advance(500,SurvivalState.Activity.IDLE,SurvivalState.Climate.MILD,false,false);
        check(recovering.thermalTicks==0,"sustained thermal recovery must clear illness");
        SurvivalState tuned=new SurvivalState();tuned.rules=new SimulationRules(java.util.Map.of("hungerPerDay",80d));
        tuned.advance(1000,SurvivalState.Activity.IDLE,SurvivalState.Climate.MILD,false,false);
        close(tuned.hunger,96.6666666667,"data-pack basal hunger must change actual expenditure");
        close(SurvivalState.decode(tuned.encode()).rules.number("hungerPerDay"),80,"body snapshots must carry server tuning to clients");
        boolean rejected=false;try{new SimulationRules(java.util.Map.of("waterPoisonChance",Double.NaN));}catch(IllegalArgumentException invalid){rejected=true;}
        check(rejected,"invalid nonfinite tuning must be rejected");
        SkillSet learner=new SkillSet();
        learner.practice(SkillSet.Skill.POTTERY,25);
        check(learner.level(SkillSet.Skill.POTTERY)==10,"completed pottery practice must raise pottery skill");
        check(learner.level(SkillSet.Skill.SMITHING)==0,"pottery practice must not train an unrelated profession");
        learner.practice(SkillSet.Skill.POTTERY,75);
        check(learner.level(SkillSet.Skill.POTTERY)==20,"later levels must require more accumulated practice");
        learner.practice(SkillSet.Skill.POTTERY,-100);learner.practice(SkillSet.Skill.POTTERY,Double.NaN);learner.practice(SkillSet.Skill.POTTERY,Double.POSITIVE_INFINITY);
        close(learner.experience(SkillSet.Skill.POTTERY),100,"invalid practice must not remove or create progress");
        learner.practice(SkillSet.Skill.WEAVING,100000);
        check(learner.level(SkillSet.Skill.WEAVING)==100,"practice is bounded to mastery");
        SurvivalState trained=new SurvivalState();trained.skills.copyFrom(learner);trained.injure(SurvivalState.Part.LEFT_ARM,90,false);
        var returned=SurvivalState.decode(trained.encode());
        check(returned.skills.level(SkillSet.Skill.POTTERY)==20&&returned.skills.level(SkillSet.Skill.WEAVING)==100,"saved bodies and client snapshots retain independent skills");
        check(migrated.skills.level(SkillSet.Skill.POTTERY)==0&&migrated.blind(),"old injured bodies migrate with novice skills without losing health");
        byte[] versionFour=SimulationTests.class.getResourceAsStream("/survival-v4.bin").readAllBytes();var previousBody=SurvivalState.decode(versionFour);
        check(previousBody.anatomy[0]==10&&previousBody.hunger==38.125&&previousBody.clothing[2].endsWith("wool_jacket")&&previousBody.skills.level(SkillSet.Skill.POTTERY)==0,"archived version four bodies remain readable after adding practice data");
        System.out.println("PASS: "+assertions+" survival behavior assertions");
    }
    private static void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
    private static void close(double got,double expected,String message){check(Math.abs(got-expected)<.0001,message+": "+got+" != "+expected);}
}
