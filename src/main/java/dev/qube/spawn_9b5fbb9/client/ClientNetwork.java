package dev.qube.spawn_9b5fbb9.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import dev.qube.spawn_9b5fbb9.network.Payloads;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client-side handlers for the server's packets. */
public final class ClientNetwork {
    private ClientNetwork() {
    }

    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(Payloads.KnowledgeSync.TYPE, (p, ctx) -> {
            ClientState.society = p.society();
            ClientState.known = new HashSet<>(p.known());
            ClientState.version++;
        });
        event.register(Payloads.JournalStatus.TYPE, (p, ctx) -> {
            Map<String, Payloads.Status> map = new HashMap<>();
            for (Payloads.Status s : p.statuses()) {
                map.put(s.function(), s);
            }
            ClientState.statuses = map;
            ClientState.version++;
        });
        event.register(Payloads.Quiz.TYPE, (p, ctx) -> {
            ClientState.quiz = p;
            ClientState.version++;
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof JournalScreen)) {
                mc.gui.setScreen(new JournalScreen(p.function()));
            }
        });
        event.register(Payloads.CraftProgress.TYPE, (p, ctx) -> {
            ClientState.craft = p;
            ClientState.version++;
        });
        event.register(Payloads.LedgerReport.TYPE, (p, ctx) -> {
            ClientState.ledger = p.lines();
            ClientState.version++;
        });
        event.register(Payloads.StationStatus.TYPE, (p, ctx) -> {
            ClientState.stationContainer = p.containerId();
            ClientState.stationStatus = p.lines();
            ClientState.version++;
        });
    }
}
