package dev.qube.spawn_9b5fbb9.knowledge;

import java.util.ArrayList;
import java.util.List;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import dev.qube.spawn_9b5fbb9.techtree.TechTree.FunctionDef;
import net.minecraft.util.RandomSource;

/**
 * Practice ahead of theory. People smelted copper for millennia before anyone could say why it
 * worked: a craft can be carried on by trial and error, at the price of spoiled batches. Each
 * theory a society lacks adds waste; every run of that craft is experience that reduces it; and
 * learning the theory removes it for every craft that depends on it at once.
 */
public final class Empirical {
    private Empirical() {
    }

    public static List<String> missingTheories(KnowledgeData kd, String society, FunctionDef f) {
        List<String> out = new ArrayList<>();
        for (String t : f.theory()) {
            if (!kd.knows(society, t)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Chance that one batch of f is wasted when run by this society. Zero once the theory is known. */
    public static double wasteChance(KnowledgeData kd, String society, FunctionDef f) {
        if (f.theoretical() || Config.bool(Config.THEORY_REQUIRED)) {
            return 0;
        }
        int k = missingTheories(kd, society, f).size();
        if (k == 0) {
            return 0;
        }
        double base = 1 - Math.pow(1 - Config.dbl(Config.EMPIRICAL_WASTE), k);
        double p = base * Math.pow(Config.dbl(Config.EXPERIENCE_RETENTION), kd.experience(society, f.id()));
        return p < 0.01 ? 0 : Math.min(0.95, p);
    }

    /** How many of the given batches are wasted. */
    public static int botched(RandomSource random, double chance, int batches) {
        int n = 0;
        if (chance > 0) {
            for (int i = 0; i < batches; i++) {
                if (random.nextDouble() < chance) {
                    n++;
                }
            }
        }
        return n;
    }

    public static String describe(KnowledgeData kd, String society, FunctionDef f) {
        List<String> missing = missingTheories(kd, society, f);
        if (missing.isEmpty() || f.theoretical() || Config.bool(Config.THEORY_REQUIRED)) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String t : missing) {
            FunctionDef td = TechTree.get().functionsById.get(t);
            names.add(td == null ? t : td.name());
        }
        int pct = (int) Math.round(wasteChance(kd, society, f) * 100);
        int runs = kd.experience(society, f.id());
        return "Trial and error without " + String.join(", ", names) + ": about " + pct + "% of batches are wasted"
                + (runs > 0 ? " (after " + runs + " runs of experience)" : "") + ". Experience or the theory brings that down.";
    }
}
