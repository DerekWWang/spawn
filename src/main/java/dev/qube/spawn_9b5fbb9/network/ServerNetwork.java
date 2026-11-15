package dev.qube.spawn_9b5fbb9.network;

import dev.qube.spawn_9b5fbb9.knowledge.Discovery;
import dev.qube.spawn_9b5fbb9.knowledge.HandWork;
import dev.qube.spawn_9b5fbb9.knowledge.Journal;
import dev.qube.spawn_9b5fbb9.knowledge.Prospecting;
import dev.qube.spawn_9b5fbb9.knowledge.Society;
import dev.qube.spawn_9b5fbb9.knowledge.Treatises;
import dev.qube.spawn_9b5fbb9.logistics.Ledger;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers payload types. Client-bound handlers are added by the client in ClientNetwork. */
public final class ServerNetwork {
    public static final String VERSION = "1";
    private static final String LEDGER_SENT = "spawn_9b5fbb9_ledger_sent";

    private ServerNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION);
        r.playToServer(Payloads.OpenJournal.TYPE, Payloads.OpenJournal.CODEC, (p, ctx) -> withPlayer(ctx, Journal::request));
        r.playToServer(Payloads.Discover.TYPE, Payloads.Discover.CODEC,
                (p, ctx) -> withPlayer(ctx, player -> Discovery.start(player, p.function())));
        r.playToServer(Payloads.Answer.TYPE, Payloads.Answer.CODEC,
                (p, ctx) -> withPlayer(ctx, player -> Discovery.answer(player, p.function(), p.nonce(), p.choice())));
        r.playToServer(Payloads.Craft.TYPE, Payloads.Craft.CODEC,
                (p, ctx) -> withPlayer(ctx, player -> HandWork.start(player, p.function(), p.times())));
        r.playToServer(Payloads.CancelCraft.TYPE, Payloads.CancelCraft.CODEC, (p, ctx) -> withPlayer(ctx, HandWork::cancel));
        r.playToServer(Payloads.WriteTreatise.TYPE, Payloads.WriteTreatise.CODEC,
                (p, ctx) -> withPlayer(ctx, player -> Treatises.write(player, p.function())));
        r.playToServer(Payloads.Prospect.TYPE, Payloads.Prospect.CODEC, (p, ctx) -> withPlayer(ctx, Prospecting::survey));
        r.playToServer(Payloads.RequestLedger.TYPE, Payloads.RequestLedger.CODEC, (p, ctx) -> withPlayer(ctx, ServerNetwork::sendLedger));

        r.playToClient(Payloads.KnowledgeSync.TYPE, Payloads.KnowledgeSync.CODEC);
        r.playToClient(Payloads.JournalStatus.TYPE, Payloads.JournalStatus.CODEC);
        r.playToClient(Payloads.Quiz.TYPE, Payloads.Quiz.CODEC);
        r.playToClient(Payloads.CraftProgress.TYPE, Payloads.CraftProgress.CODEC);
        r.playToClient(Payloads.StationStatus.TYPE, Payloads.StationStatus.CODEC);
        r.playToClient(Payloads.LedgerReport.TYPE, Payloads.LedgerReport.CODEC);
    }

    /** The books as recorded; the ground truth never leaves the server this way. */
    private static void sendLedger(ServerPlayer player) {
        long now = player.level().getGameTime();
        long last = player.getPersistentData().getLongOr(LEDGER_SENT, now - 100);
        if (now >= last && now - last < 10) {
            return; // at most twice a second
        }
        player.getPersistentData().putLong(LEDGER_SENT, now);
        Net.send(player, new Payloads.LedgerReport(Ledger.report(player.level().getServer(), Society.keyOf(player))));
    }

    private static void withPlayer(IPayloadContext ctx, java.util.function.Consumer<ServerPlayer> action) {
        if (ctx.player() instanceof ServerPlayer player) {
            action.accept(player);
        }
    }
}
