package dev.qube.spawn_9b5fbb9;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Gameplay rules for the tech tree. Read through the helpers at the bottom so that a value
 * asked for before the config file has loaded falls back to its default instead of throwing.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Discovery: how a society claims a function").push("discovery");
    }

    public static final ModConfigSpec.BooleanValue REQUIRE_EXPLANATION = BUILDER
            .comment("Quiz mode: players must pick the correct mechanism (the spreadsheet's answer key) to discover a function.",
                    "Off by default: discovery is then a period of study by someone who holds the evidence, with no quiz.")
            .define("quizMode", false);

    public static final ModConfigSpec.BooleanValue THEORY_REQUIRED = BUILDER
            .comment("Strict mode: a practical function cannot be discovered until its theories are known.",
                    "Off by default: practice may run ahead of theory. A society can work a craft empirically, wasting",
                    "batches until it gains experience in that craft or learns the theory behind it.")
            .define("theoryRequiredForPractice", false);

    public static final ModConfigSpec.DoubleValue EMPIRICAL_WASTE = BUILDER
            .comment("Chance that a batch is wasted for each theory the operator's society lacks (before experience).")
            .defineInRange("empiricalWastePerMissingTheory", 0.5, 0.0, 0.95);

    public static final ModConfigSpec.DoubleValue EXPERIENCE_RETENTION = BUILDER
            .comment("Each earlier run of the same function multiplies the waste chance by this factor.")
            .defineInRange("wasteFactorPerRunOfExperience", 0.85, 0.5, 1.0);

    public static final ModConfigSpec.IntValue STUDY_SECONDS = BUILDER
            .comment("Base seconds of study to discover a function; deeper functions take longer.")
            .defineInRange("studySeconds", 8, 0, 600);

    public static final ModConfigSpec.IntValue WRONG_ANSWER_COOLDOWN = BUILDER
            .comment("Seconds before a society may try to explain the same function again after a wrong answer.",
                    "Halved once Scientific method is known.")
            .defineInRange("wrongAnswerCooldownSeconds", 60, 0, 3600);

    public static final ModConfigSpec.IntValue SEASONAL_CYCLE_DAYS = BUILDER
            .comment("In-game days a player must have played to have observed the 'Seasonal cycle' phenomenon.")
            .defineInRange("seasonalCycleDays", 2, 0, 100);

    public static final ModConfigSpec.BooleanValue SHARE_WITH_TEAM = BUILDER
            .comment("Players on the same scoreboard team form one society and share all knowledge.")
            .define("shareKnowledgeWithTeam", true);

    static {
        BUILDER.pop().comment("Production").push("production");
    }

    public static final ModConfigSpec.DoubleValue TIME_MULTIPLIER = BUILDER
            .comment("Multiplier on how long every function takes to run.")
            .defineInRange("processingTimeMultiplier", 1.0, 0.05, 20.0);

    public static final ModConfigSpec.IntValue PRESENCE_RADIUS = BUILDER
            .comment("How far (in blocks, edge to edge) another station may be from the one doing the work",
                    "and still count as present. Also how close a player must be to a station to use it from the journal.")
            .defineInRange("presenceRadius", 16, 4, 64);

    public static final ModConfigSpec.IntValue GRID_RADIUS = BUILDER
            .comment("Radius in blocks around an Electric grid substation that counts as connected to the grid.")
            .defineInRange("gridCoverageRadius", 96, 8, 512);

    public static final ModConfigSpec.BooleanValue TOOLS_WEAR = BUILDER
            .comment("Hand tools and instruments used as means of production lose durability each run.")
            .define("toolsWear", true);

    static {
        BUILDER.pop().comment("Natural resources").push("resources");
    }

    public static final ModConfigSpec.BooleanValue GATE_RECOGNITION = BUILDER
            .comment("Deposits whose ore is 'recognised once known' yield only rubble until the miner's society knows the theory.")
            .define("gateOreRecognition", true);

    public static final ModConfigSpec.BooleanValue GATE_ACCESS = BUILDER
            .comment("Deposits that need a Shaft mine, Deep mine or Drilling rig yield only rubble without one nearby.")
            .define("gateOreAccess", true);

    public static final ModConfigSpec.BooleanValue GATE_VANILLA_ORES = BUILDER
            .comment("Also apply the two rules above to vanilla iron and coal ores (listed in the mod's block tags).")
            .define("gateVanillaOres", true);

    public static final ModConfigSpec.DoubleValue FIBER_CHANCE = BUILDER
            .comment("Chance that breaking grass or ferns by hand yields Plant fiber.")
            .defineInRange("plantFiberChance", 0.35, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue LATEX_CHANCE = BUILDER
            .comment("Chance that breaking a jungle log yields Rubber latex.")
            .defineInRange("rubberLatexChance", 0.3, 0.0, 1.0);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    public static boolean bool(ModConfigSpec.BooleanValue v) {
        try {
            return v.getAsBoolean();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }

    public static int integer(ModConfigSpec.IntValue v) {
        try {
            return v.getAsInt();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }

    public static double dbl(ModConfigSpec.DoubleValue v) {
        try {
            return v.getAsDouble();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }
}
