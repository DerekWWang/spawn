package dev.qube.spawn_9b5fbb9.knowledge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.logistics.Ledger;
import dev.qube.spawn_9b5fbb9.network.Net;
import dev.qube.spawn_9b5fbb9.network.Payloads;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import dev.qube.spawn_9b5fbb9.techtree.TechTree.FunctionDef;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a person does with their own hands and head: functions that need no station (knapping,
 * cordage, building a hearth...) and the study that discovers a function. Both take time, and the
 * materials are checked again, and taken, only when each run finishes.
 */
public final class HandWork {
    public static final int MAX_REPEATS = 64;
    private static final Map<UUID, Job> JOBS = new HashMap<>();

    private static final class Job {
        final String function;
        final boolean study;
        final int total;
        int remaining;
        int timesLeft;

        Job(String function, boolean study, int total, int times) {
            this.function = function;
            this.study = study;
            this.total = total;
            this.remaining = total;
            this.timesLeft = times;
        }
    }

    private HandWork() {
    }

    public static void clear() {
        JOBS.clear();
    }

    public static void forget(UUID player) {
        JOBS.remove(player);
    }

    public static boolean busy(ServerPlayer player) {
        return JOBS.containsKey(player.getUUID());
    }

    private static boolean refuseIfBusy(ServerPlayer player) {
        Job current = JOBS.get(player.getUUID());
        if (current == null) {
            return false;
        }
        FunctionDef cf = TechTree.get().functionsById.get(current.function);
        player.sendOverlayMessage(Component.literal("You are busy " + (current.study ? "studying " : "with ")
                + (cf == null ? "something" : cf.name()) + "."));
        return true;
    }

    public static void start(ServerPlayer player, String functionId, int times) {
        FunctionDef f = TechTree.get().functionsById.get(functionId);
        if (f == null || f.mode() != TechTree.Mode.HAND) {
            return;
        }
        if (!Society.knows(player, f.id())) {
            player.sendOverlayMessage(Component.literal("Your society has not discovered " + f.name() + " yet."));
            return;
        }
        if (refuseIfBusy(player)) {
            return;
        }
        Requirements.Check check = Requirements.player(player, f);
        if (!check.ok()) {
            player.sendSystemMessage(Component.literal("Cannot do " + f.name() + ": " + String.join("; ",
                    check.missing().subList(0, Math.min(4, check.missing().size())))).withStyle(ChatFormatting.YELLOW));
            Journal.request(player);
            return;
        }
        int ticks = Math.max(1, (int) Math.round(f.ticks() * Config.dbl(Config.TIME_MULTIPLIER)));
        JOBS.put(player.getUUID(), new Job(f.id(), false, ticks, Math.max(1, Math.min(MAX_REPEATS, times))));
        sendProgress(player);
    }

    /** Study that ends in discovering f. Requirements must already have been checked. */
    static void startStudy(ServerPlayer player, FunctionDef f) {
        if (refuseIfBusy(player)) {
            return;
        }
        JOBS.put(player.getUUID(), new Job(f.id(), true, studyTicks(player, f), 1));
        sendProgress(player);
    }

    /** How long it takes to work a function out: deeper ideas take longer, and method helps. */
    public static int studyTicks(ServerPlayer player, FunctionDef f) {
        double seconds = Config.integer(Config.STUDY_SECONDS) + (f.theoretical() ? f.depth() : f.depth() * 0.5);
        KnowledgeData kd = KnowledgeData.get(player.level().getServer());
        String society = Society.keyOf(player);
        if (f.theoretical()) {
            if (kd.knows(society, "T-36")) {
                seconds *= 0.5; // Scientific method speeds every theoretical function
            }
            if (kd.knows(society, "T-27")) {
                seconds *= 0.75; // Recorded scholarship makes theory cheaper
            }
        } else {
            seconds *= 1 + 0.5 * Empirical.missingTheories(kd, society, f).size(); // working it out blind is slower
        }
        return Math.max(1, (int) Math.round(seconds * 20 * Config.dbl(Config.TIME_MULTIPLIER)));
    }

    public static void cancel(ServerPlayer player) {
        if (JOBS.remove(player.getUUID()) != null) {
            sendProgress(player);
        }
    }

    public static void tick(ServerPlayer player) {
        Job job = JOBS.get(player.getUUID());
        if (job == null) {
            return;
        }
        FunctionDef f = TechTree.get().functionsById.get(job.function);
        if (f == null || !player.isAlive()) {
            cancel(player);
            return;
        }
        job.remaining--;
        if (job.remaining > 0) {
            if (job.remaining % 10 == 0) {
                sendProgress(player);
            }
            return;
        }
        if (job.study) {
            JOBS.remove(player.getUUID());
            Discovery.completeStudy(player, f);
            sendProgress(player);
            return;
        }
        Requirements.Check check = Requirements.player(player, f);
        if (!check.ok()) {
            JOBS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal(f.name() + " stopped: " + String.join("; ", check.missing()))
                    .withStyle(ChatFormatting.YELLOW));
            sendProgress(player);
            Journal.request(player);
            return;
        }
        KnowledgeData kd = KnowledgeData.get(player.level().getServer());
        String society = Society.keyOf(player);
        boolean wasted = Empirical.botched(player.getRandom(), Empirical.wasteChance(kd, society, f), 1) > 0;
        Requirements.consumeFromPlayer(player, f);
        Requirements.wearPlayerTools(player, f);
        Ledger.recordConsumed(player.level().getServer(), society, f, 1);
        if (wasted) {
            player.sendOverlayMessage(Component.literal(f.name() + ": the attempt failed and the materials are wasted."));
        } else {
            Requirements.giveOutputs(player, f, 1);
            Ledger.recordProduced(player.level().getServer(), society, f, 1);
        }
        kd.addExperience(society, f.id(), 1);
        job.timesLeft--;
        if (job.timesLeft > 0) {
            job.remaining = job.total;
        } else {
            JOBS.remove(player.getUUID());
            if (!wasted) {
                player.sendOverlayMessage(Component.literal("Finished: " + f.name()));
            }
            Journal.request(player);
        }
        sendProgress(player);
    }

    public static void sendProgress(ServerPlayer player) {
        Job job = JOBS.get(player.getUUID());
        Payloads.CraftProgress msg = job == null ? new Payloads.CraftProgress("", 0, 0, 0, false)
                : new Payloads.CraftProgress(job.function, job.remaining, job.total, job.timesLeft, job.study);
        Net.send(player, msg);
    }
}
