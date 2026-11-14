package dev.qube.spawn_9b5fbb9.knowledge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.network.Net;
import dev.qube.spawn_9b5fbb9.network.Payloads;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import dev.qube.spawn_9b5fbb9.techtree.TechTree.FunctionDef;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Functions done by hand (no station listed): knapping, cordage, building a hearth... They take
 * time; inputs are checked again and taken only when each run finishes.
 */
public final class HandWork {
    public static final int MAX_REPEATS = 64;
    private static final Map<UUID, Job> JOBS = new HashMap<>();

    private static final class Job {
        final String function;
        final int total;
        int remaining;
        int timesLeft;

        Job(String function, int total, int times) {
            this.function = function;
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

    public static void start(ServerPlayer player, String functionId, int times) {
        FunctionDef f = TechTree.get().functionsById.get(functionId);
        if (f == null || f.mode() != TechTree.Mode.HAND) {
            return;
        }
        if (!Society.knows(player, f.id())) {
            player.sendOverlayMessage(Component.literal("Your society has not discovered " + f.name() + " yet."));
            return;
        }
        Job current = JOBS.get(player.getUUID());
        if (current != null) {
            FunctionDef cf = TechTree.get().functionsById.get(current.function);
            player.sendOverlayMessage(Component.literal("You are busy with " + (cf == null ? "something" : cf.name()) + "."));
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
        JOBS.put(player.getUUID(), new Job(f.id(), ticks, Math.max(1, Math.min(MAX_REPEATS, times))));
        sendProgress(player);
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
        Requirements.Check check = Requirements.player(player, f);
        if (!check.ok()) {
            JOBS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal(f.name() + " stopped: " + String.join("; ", check.missing()))
                    .withStyle(ChatFormatting.YELLOW));
            sendProgress(player);
            Journal.request(player);
            return;
        }
        Requirements.consumeFromPlayer(player, f);
        Requirements.wearPlayerTools(player, f);
        Requirements.giveOutputs(player, f, 1);
        job.timesLeft--;
        if (job.timesLeft > 0) {
            job.remaining = job.total;
        } else {
            JOBS.remove(player.getUUID());
            player.sendOverlayMessage(Component.literal("Finished: " + f.name()));
            Journal.request(player);
        }
        sendProgress(player);
    }

    public static void sendProgress(ServerPlayer player) {
        Job job = JOBS.get(player.getUUID());
        Payloads.CraftProgress msg = job == null ? new Payloads.CraftProgress("", 0, 0, 0)
                : new Payloads.CraftProgress(job.function, job.remaining, job.total, job.timesLeft);
        Net.send(player, msg);
    }
}
