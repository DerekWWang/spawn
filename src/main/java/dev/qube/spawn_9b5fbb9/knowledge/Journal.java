package dev.qube.spawn_9b5fbb9.knowledge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.qube.spawn_9b5fbb9.network.Net;
import dev.qube.spawn_9b5fbb9.network.Payloads;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import dev.qube.spawn_9b5fbb9.techtree.TechTree.FunctionDef;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds the journal's view of what the player could discover or make right now. The client knows
 * the tree itself; the server adds what only it can judge (inventory, nearby stations, phenomena).
 */
public final class Journal {
    private static final Map<UUID, Long> LAST_SENT = new HashMap<>();

    private Journal() {
    }

    public static void clear() {
        LAST_SENT.clear();
    }

    public static void forget(UUID player) {
        LAST_SENT.remove(player);
    }

    /** Sends the status unless one went out in the last few ticks (requests are cheap to spam). */
    public static void request(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long last = LAST_SENT.get(player.getUUID());
        if (last != null && now - last < 10 && now >= last) {
            return;
        }
        send(player);
    }

    public static void send(ServerPlayer player) {
        LAST_SENT.put(player.getUUID(), player.level().getGameTime());
        Society.sync(player);
        Net.send(player, new Payloads.JournalStatus(statuses(player)));
        HandWork.sendProgress(player);
    }

    /**
     * One entry per function worth showing: everything the society could claim now or is one step from
     * (its theory known), and everything it knows that is made by hand or still carries empirical waste.
     */
    public static List<Payloads.Status> statuses(ServerPlayer player) {
        Requirements.PlayerContext ctx = new Requirements.PlayerContext(player);
        KnowledgeData kd = ctx.knowledge;
        String society = ctx.society;
        Set<String> known = kd.knownBy(society);
        List<Payloads.Status> out = new ArrayList<>();
        for (FunctionDef f : TechTree.get().functions) {
            String note = Empirical.describe(kd, society, f);
            List<String> notes = note == null ? List.of() : List.of(note);
            if (known.contains(f.id())) {
                if (f.mode() == TechTree.Mode.HAND) {
                    Requirements.Check c = Requirements.player(ctx, f);
                    out.add(new Payloads.Status(f.id(), c.ok() ? Payloads.Status.CAN_MAKE : Payloads.Status.MAKE_BLOCKED, 0,
                            trim(c.missing()), notes));
                } else if (note != null) {
                    out.add(new Payloads.Status(f.id(), Payloads.Status.KNOWN_ELSEWHERE, 0, List.of(), notes));
                }
                continue;
            }
            Requirements.Check c = Requirements.player(ctx, f);
            if (c.ok() || known.containsAll(f.theory())) {
                out.add(new Payloads.Status(f.id(), c.ok() ? Payloads.Status.READY_TO_DISCOVER : Payloads.Status.DISCOVER_BLOCKED,
                        Discovery.cooldownSeconds(player, f.id()), trim(c.missing()), notes));
            }
        }
        return out;
    }

    private static List<String> trim(List<String> missing) {
        return missing.size() <= 12 ? missing : missing.subList(0, 12);
    }
}
