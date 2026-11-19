package dev.qube.spawn_9b5fbb9.logistics;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.knowledge.KnowledgeData;

/**
 * How well a society can count. What the manager sees is never the truth, only the records, and
 * records are as good as the tools for keeping them: memory, tallies, writing, arithmetic,
 * standard measures, paper, statistics, calculating machines, computers.
 */
public final class RecordKeeping {
    private RecordKeeping() {
    }

    /** One level of record-keeping: the function that brings it, its name, and its typical relative error. */
    public record Level(String function, String name, double error) { }

    private static final Level[] LEVELS = {
            new Level(null, "memory and guesswork", 0.40),
            new Level("T-09", "tally marks", 0.25),
            new Level("T-11", "written records", 0.15),
            new Level("T-13", "written accounts with arithmetic", 0.10),
            new Level("T-20", "accounts in standard weights and measures", 0.07),
            new Level("P-063", "paper ledgers", 0.05),
            new Level("T-50", "ledgers audited by sampling", 0.03),
            new Level("P-150", "machine-added ledgers", 0.02),
            new Level("P-221", "computer records", 0.005),
            new Level("P-266", "a networked database", 0.001),
    };

    /** The best level the society has reached (levels need not be reached in order). */
    public static Level level(KnowledgeData kd, String society) {
        Level best = LEVELS[0];
        for (Level l : LEVELS) {
            if (l.function() != null && kd.knows(society, l.function()) && l.error() < best.error()) {
                best = l;
            }
        }
        return best;
    }

    /** Typical relative error (one standard deviation) in anything the society records. */
    public static double sigma(KnowledgeData kd, String society) {
        return level(kd, society).error() * Config.dbl(Config.RECORD_NOISE);
    }
}
