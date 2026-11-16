package dev.qube.spawn_9b5fbb9;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class Config {
    private static final ModConfigSpec.Builder BUILDER=new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue SEASON_DAYS=BUILDER.comment("Game days in each season.").defineInRange("seasonDays",28,4,112);
    public static final ModConfigSpec.DoubleValue RECREATION_HOURS=BUILDER.comment("Daily game hours of active recreation; sleep does not count.").defineInRange("recreationHours",4.5,4,5);
    public static final ModConfigSpec.DoubleValue NEEDS_RATE=BUILDER.comment("Physiological time multiplier; identical for human and automated humanoids.").defineInRange("needsRate",1.0,0.1,10);
    public static final ModConfigSpec.DoubleValue INDUSTRY_TIME=BUILDER.comment("Multiplier on industrial work times. 1.0 is real man-hours on the game clock (1000 ticks per hour): a locomotive is months of work for a full yard.").defineInRange("industryTimeScale",1.0,0.001,10);
    public static final ModConfigSpec.BooleanValue HUD=BUILDER.comment("Show survival HUD.").define("showHud",true);
    public static final ModConfigSpec SPEC=BUILDER.build();
    private Config(){}
}
