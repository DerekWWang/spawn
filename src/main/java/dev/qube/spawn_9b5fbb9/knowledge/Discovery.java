package dev.qube.spawn_9b5fbb9.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.network.Net;
import dev.qube.spawn_9b5fbb9.network.Payloads;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import dev.qube.spawn_9b5fbb9.techtree.TechTree.FunctionDef;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Claiming a function. The player must hold every input and means of production, know every
 * required theory, and explain the mechanism: they are shown the true mechanism from the
 * spreadsheet among others drawn from related functions, and must pick the right one.
 */
public final class Discovery {
    private static final long QUIZ_LIFETIME_TICKS = 20L * 60 * 10;
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** society|function -> game time when another attempt is allowed */
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    /** society|function -> wrong answers so far; picks which set of wrong options is shown */
    private static final Map<String, Integer> ATTEMPTS = new HashMap<>();

    private record Pending(String function, int nonce, int correct, long expires, List<String> options) { }

    private Discovery() {
    }

    public static void clear() {
        PENDING.clear();
        COOLDOWNS.clear();
        ATTEMPTS.clear();
    }

    public static void forget(UUID player) {
        PENDING.remove(player);
    }

    /** For game tests: {nonce, correct option} of the player's open question, or null. */
    public static List<String> pendingOptions(UUID player) {
        Pending p = PENDING.get(player);
        return p == null ? List.of() : p.options();
    }

    public static int[] pendingQuestion(UUID player) {
        Pending p = PENDING.get(player);
        return p == null ? null : new int[]{p.nonce(), p.correct()};
    }

    public static int cooldownSeconds(ServerPlayer player, String functionId) {
        Long until = COOLDOWNS.get(Society.keyOf(player) + "|" + functionId);
        if (until == null) {
            return 0;
        }
        long left = until - player.level().getGameTime();
        return left <= 0 ? 0 : (int) ((left + 19) / 20);
    }

    public static void start(ServerPlayer player, String functionId) {
        FunctionDef f = TechTree.get().functionsById.get(functionId);
        if (f == null) {
            return;
        }
        if (Society.knows(player, f.id())) {
            player.sendOverlayMessage(Component.literal("Your society already knows " + f.name() + "."));
            return;
        }
        int cd = cooldownSeconds(player, f.id());
        if (cd > 0) {
            player.sendOverlayMessage(Component.literal("Think it over for " + cd + " more seconds before trying again."));
            return;
        }
        Requirements.Check check = Requirements.player(player, f);
        if (!check.ok()) {
            player.sendSystemMessage(Component.literal("To claim " + f.name() + " you still need: " + String.join("; ",
                    check.missing().subList(0, Math.min(4, check.missing().size())))).withStyle(ChatFormatting.YELLOW));
            Journal.request(player);
            return;
        }
        if (!Config.bool(Config.REQUIRE_EXPLANATION) || f.mechanism().isBlank()) {
            grant(player, f);
            return;
        }
        boolean cheaper = f.theoretical() && Society.knows(player, "T-27"); // Recorded scholarship
        int optionCount = cheaper ? 3 : 4;
        // The same society sees the same options until it answers wrongly: asking again (or "Not yet")
        // must not reshuffle the wrong answers, or the right one would be the only option that never changes.
        String key = Society.keyOf(player) + "|" + f.id();
        long seed = ((long) key.hashCode() << 32) ^ ATTEMPTS.getOrDefault(key, 0) * 0x9E3779B97F4A7C15L ^ optionCount;
        Random random = new Random(seed);
        List<String> options = new ArrayList<>(distractors(f, optionCount - 1, random));
        options.add(f.mechanism());
        Collections.shuffle(options, random);
        int correct = options.indexOf(f.mechanism());
        int nonce = random.nextInt(Integer.MAX_VALUE);
        PENDING.put(player.getUUID(), new Pending(f.id(), nonce, correct, player.level().getGameTime() + QUIZ_LIFETIME_TICKS,
                List.copyOf(options)));
        Net.send(player, new Payloads.Quiz(f.id(), nonce, List.copyOf(options)));
    }

    public static void answer(ServerPlayer player, String functionId, int nonce, int choice) {
        Pending p = PENDING.remove(player.getUUID());
        FunctionDef f = TechTree.get().functionsById.get(functionId);
        if (p == null || f == null || !p.function().equals(functionId) || p.nonce() != nonce
                || player.level().getGameTime() > p.expires()) {
            player.sendOverlayMessage(Component.literal("That question has expired; try again."));
            return;
        }
        int cd = cooldownSeconds(player, f.id());
        if (cd > 0) {
            // e.g. a team-mate answered wrongly while this question was open
            player.sendOverlayMessage(Component.literal("Your society must think it over for " + cd + " more seconds."));
            return;
        }
        if (choice != p.correct()) {
            int seconds = Config.integer(Config.WRONG_ANSWER_COOLDOWN);
            if (Society.knows(player, "T-36")) {
                seconds /= 2; // Scientific method speeds every theoretical function
            }
            String key = Society.keyOf(player) + "|" + f.id();
            COOLDOWNS.put(key, player.level().getGameTime() + seconds * 20L);
            ATTEMPTS.merge(key, 1, Integer::sum);
            player.sendSystemMessage(Component.literal("That mechanism does not explain " + f.name()
                    + ". A wrong mechanism fails even when the recipe is right.").withStyle(ChatFormatting.RED));
            Journal.request(player);
            return;
        }
        Requirements.Check check = Requirements.player(player, f);
        if (!check.ok()) {
            player.sendSystemMessage(Component.literal("Right explanation, but you no longer have everything: "
                    + String.join("; ", check.missing())).withStyle(ChatFormatting.YELLOW));
            Journal.request(player);
            return;
        }
        grant(player, f);
    }

    private static void grant(ServerPlayer player, FunctionDef f) {
        MinecraftServer server = player.level().getServer();
        Society.grant(server, Society.keyOf(player), f.id(), player);
        Journal.send(player);
    }

    /** Wrong answers from related functions: same route first, then the same era, then anything. */
    static List<String> distractors(FunctionDef f, int n, Random random) {
        List<FunctionDef> sameRoute = new ArrayList<>();
        List<FunctionDef> sameEra = new ArrayList<>();
        List<FunctionDef> rest = new ArrayList<>();
        for (FunctionDef o : TechTree.get().functions) {
            if (o == f || o.mechanism().isBlank() || o.mechanism().equals(f.mechanism())) {
                continue;
            }
            if (o.route().equals(f.route())) {
                sameRoute.add(o);
            } else if (o.era() == f.era()) {
                sameEra.add(o);
            } else {
                rest.add(o);
            }
        }
        Set<String> picked = new LinkedHashSet<>();
        for (List<FunctionDef> pool : List.of(sameRoute, sameEra, rest)) {
            List<FunctionDef> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled, random);
            for (FunctionDef o : shuffled) {
                if (picked.size() >= n) {
                    break;
                }
                picked.add(o.mechanism());
            }
        }
        return List.copyOf(picked);
    }
}
