package dev.qube.spawn_9b5fbb9.network;

import java.util.ArrayList;
import java.util.List;

import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Every packet the mod sends. Client-bound handlers are registered on the client only. */
public final class Payloads {
    private static final int MAX_LIST = 2048;
    private static final StreamCodec<io.netty.buffer.ByteBuf, List<String>> STRINGS =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_LIST));

    private Payloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> createType(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, path));
    }

    // ------------------------------------------------------------------ client -> server

    public record OpenJournal() implements CustomPacketPayload {
        public static final OpenJournal INSTANCE = new OpenJournal();
        public static final Type<OpenJournal> TYPE = createType("open_journal");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenJournal> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<OpenJournal> type() {
            return TYPE;
        }
    }

    public record Discover(String function) implements CustomPacketPayload {
        public static final Type<Discover> TYPE = createType("discover");
        public static final StreamCodec<RegistryFriendlyByteBuf, Discover> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Discover::function, Discover::new);

        @Override
        public Type<Discover> type() {
            return TYPE;
        }
    }

    public record Answer(String function, int nonce, int choice) implements CustomPacketPayload {
        public static final Type<Answer> TYPE = createType("answer");
        public static final StreamCodec<RegistryFriendlyByteBuf, Answer> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Answer::function, ByteBufCodecs.VAR_INT, Answer::nonce, ByteBufCodecs.VAR_INT, Answer::choice,
                Answer::new);

        @Override
        public Type<Answer> type() {
            return TYPE;
        }
    }

    public record Craft(String function, int times) implements CustomPacketPayload {
        public static final Type<Craft> TYPE = createType("craft");
        public static final StreamCodec<RegistryFriendlyByteBuf, Craft> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Craft::function, ByteBufCodecs.VAR_INT, Craft::times, Craft::new);

        @Override
        public Type<Craft> type() {
            return TYPE;
        }
    }

    public record CancelCraft() implements CustomPacketPayload {
        public static final CancelCraft INSTANCE = new CancelCraft();
        public static final Type<CancelCraft> TYPE = createType("cancel_craft");
        public static final StreamCodec<RegistryFriendlyByteBuf, CancelCraft> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<CancelCraft> type() {
            return TYPE;
        }
    }

    public record WriteTreatise(String function) implements CustomPacketPayload {
        public static final Type<WriteTreatise> TYPE = createType("write_treatise");
        public static final StreamCodec<RegistryFriendlyByteBuf, WriteTreatise> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, WriteTreatise::function, WriteTreatise::new);

        @Override
        public Type<WriteTreatise> type() {
            return TYPE;
        }
    }

    public record Prospect() implements CustomPacketPayload {
        public static final Prospect INSTANCE = new Prospect();
        public static final Type<Prospect> TYPE = createType("prospect");
        public static final StreamCodec<RegistryFriendlyByteBuf, Prospect> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<Prospect> type() {
            return TYPE;
        }
    }

    public record RequestLedger() implements CustomPacketPayload {
        public static final RequestLedger INSTANCE = new RequestLedger();
        public static final Type<RequestLedger> TYPE = createType("request_ledger");
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestLedger> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<RequestLedger> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------ server -> client

    /** The society's books as the manager sees them (observed values, never the ground truth). */
    public record LedgerReport(List<String> lines) implements CustomPacketPayload {
        public static final Type<LedgerReport> TYPE = createType("ledger_report");
        public static final StreamCodec<RegistryFriendlyByteBuf, LedgerReport> CODEC =
                StreamCodec.composite(STRINGS, LedgerReport::lines, LedgerReport::new);

        @Override
        public Type<LedgerReport> type() {
            return TYPE;
        }
    }

    public record KnowledgeSync(String society, List<String> known) implements CustomPacketPayload {
        public static final Type<KnowledgeSync> TYPE = createType("knowledge_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, KnowledgeSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, KnowledgeSync::society, STRINGS, KnowledgeSync::known, KnowledgeSync::new);

        @Override
        public Type<KnowledgeSync> type() {
            return TYPE;
        }
    }

    /** Status of one function for the journal, computed by the server against the player's surroundings. */
    public record Status(String function, int state, int cooldownSeconds, List<String> missing, List<String> notes) {
        public static final int THEORY_MISSING = 0;
        public static final int READY_TO_DISCOVER = 1;
        public static final int DISCOVER_BLOCKED = 2;
        public static final int CAN_MAKE = 3;
        public static final int MAKE_BLOCKED = 4;
        public static final int KNOWN_ELSEWHERE = 5;

        static void write(FriendlyByteBuf buf, Status s) {
            buf.writeUtf(s.function);
            buf.writeVarInt(s.state);
            buf.writeVarInt(s.cooldownSeconds);
            buf.writeVarInt(s.missing.size());
            for (String m : s.missing) {
                buf.writeUtf(m);
            }
            buf.writeVarInt(s.notes.size());
            for (String m : s.notes) {
                buf.writeUtf(m);
            }
        }

        static Status read(FriendlyByteBuf buf) {
            String f = buf.readUtf();
            int state = buf.readVarInt();
            int cd = buf.readVarInt();
            int n = Math.min(buf.readVarInt(), 64);
            List<String> missing = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                missing.add(buf.readUtf());
            }
            int k = Math.min(buf.readVarInt(), 16);
            List<String> notes = new ArrayList<>(k);
            for (int i = 0; i < k; i++) {
                notes.add(buf.readUtf());
            }
            return new Status(f, state, cd, List.copyOf(missing), List.copyOf(notes));
        }
    }

    public record JournalStatus(List<Status> statuses) implements CustomPacketPayload {
        public static final Type<JournalStatus> TYPE = createType("journal_status");
        public static final StreamCodec<RegistryFriendlyByteBuf, JournalStatus> CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeVarInt(v.statuses.size());
                    v.statuses.forEach(s -> Status.write(buf, s));
                },
                buf -> {
                    int n = buf.readVarInt();
                    if (n < 0 || n > MAX_LIST) {
                        throw new IllegalArgumentException("Too many journal entries: " + n);
                    }
                    List<Status> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        list.add(Status.read(buf));
                    }
                    return new JournalStatus(List.copyOf(list));
                });

        @Override
        public Type<JournalStatus> type() {
            return TYPE;
        }
    }

    public record Quiz(String function, int nonce, List<String> options) implements CustomPacketPayload {
        public static final Type<Quiz> TYPE = createType("quiz");
        public static final StreamCodec<RegistryFriendlyByteBuf, Quiz> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Quiz::function, ByteBufCodecs.VAR_INT, Quiz::nonce, STRINGS, Quiz::options, Quiz::new);

        @Override
        public Type<Quiz> type() {
            return TYPE;
        }
    }

    /** function == "" means no job is running. */
    public record CraftProgress(String function, int remainingTicks, int totalTicks, int timesLeft, boolean study)
            implements CustomPacketPayload {
        public static final Type<CraftProgress> TYPE = createType("craft_progress");
        public static final StreamCodec<RegistryFriendlyByteBuf, CraftProgress> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, CraftProgress::function, ByteBufCodecs.VAR_INT, CraftProgress::remainingTicks,
                ByteBufCodecs.VAR_INT, CraftProgress::totalTicks, ByteBufCodecs.VAR_INT, CraftProgress::timesLeft,
                ByteBufCodecs.BOOL, CraftProgress::study, CraftProgress::new);

        @Override
        public Type<CraftProgress> type() {
            return TYPE;
        }
    }

    public record StationStatus(int containerId, List<String> lines) implements CustomPacketPayload {
        public static final Type<StationStatus> TYPE = createType("station_status");
        public static final StreamCodec<RegistryFriendlyByteBuf, StationStatus> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StationStatus::containerId, STRINGS, StationStatus::lines, StationStatus::new);

        @Override
        public Type<StationStatus> type() {
            return TYPE;
        }
    }
}
