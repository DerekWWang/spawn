package dev.qube.spawn_9b5fbb9.logistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Each society's books, stored with the world. Every figure is kept twice: what is actually
 * there (the ground truth) and what the society's records say (the observed truth).
 *
 * <p>Keys: stock/&lt;building&gt;/&lt;item&gt;, flow/&lt;kind&gt;/&lt;item&gt;, people/players/..., resident/&lt;building&gt;/...,
 * housing/&lt;building&gt;, herd/&lt;building&gt;/&lt;animal&gt;[/pregnant|/young].</p>
 */
public final class LedgerData extends SavedData {
    private static final Codec<Map<String, Double>> NUMBERS = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE);

    /** One day's totals, both as they were and as they were recorded. */
    public record Snapshot(long day, Map<String, Double> actual, Map<String, Double> observed) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(Snapshot::day),
                NUMBERS.fieldOf("actual").forGetter(Snapshot::actual),
                NUMBERS.fieldOf("observed").forGetter(Snapshot::observed)
        ).apply(i, Snapshot::new));
    }

    public static final class Books {
        static final Codec<Books> CODEC = RecordCodecBuilder.create(i -> i.group(
                NUMBERS.optionalFieldOf("actual", Map.of()).forGetter(b -> b.actual),
                NUMBERS.optionalFieldOf("observed", Map.of()).forGetter(b -> b.observed),
                NUMBERS.optionalFieldOf("bias", Map.of()).forGetter(b -> b.bias),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("stamps", Map.of()).forGetter(b -> b.stamps),
                Snapshot.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(b -> b.history)
        ).apply(i, Books::new));

        public final Map<String, Double> actual = new HashMap<>();
        public final Map<String, Double> observed = new HashMap<>();
        /** The habit of over- or under-counting in each kind of record, in standard deviations (see Ledger.bias). */
        public final Map<String, Double> bias = new HashMap<>();
        public final Map<String, Long> stamps = new HashMap<>();
        public final List<Snapshot> history = new ArrayList<>();

        public Books() {
        }

        private Books(Map<String, Double> actual, Map<String, Double> observed, Map<String, Double> bias, Map<String, Long> stamps,
                      List<Snapshot> history) {
            this.actual.putAll(actual);
            this.observed.putAll(observed);
            this.bias.putAll(bias);
            this.stamps.putAll(stamps);
            this.history.addAll(history);
        }

        public double actual(String key) {
            return actual.getOrDefault(key, 0.0);
        }

        public double observed(String key) {
            return observed.getOrDefault(key, 0.0);
        }

        /** Sum of every figure whose key starts with the prefix and ends with the suffix. */
        public double sum(Map<String, Double> map, String prefix, String suffix) {
            double total = 0;
            for (Map.Entry<String, Double> e : map.entrySet()) {
                if (e.getKey().startsWith(prefix) && e.getKey().endsWith(suffix)) {
                    total += e.getValue();
                }
            }
            return total;
        }
    }

    public static final Codec<LedgerData> CODEC = Codec.unboundedMap(Codec.STRING, Books.CODEC)
            .xmap(LedgerData::new, d -> d.books);

    public static final SavedDataType<LedgerData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, "ledger"), LedgerData::new, CODEC);

    private final Map<String, Books> books = new HashMap<>();

    public LedgerData() {
    }

    private LedgerData(Map<String, Books> books) {
        this.books.putAll(books);
    }

    public static LedgerData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Books books(String society) {
        return books.computeIfAbsent(society, k -> new Books());
    }

    public Map<String, Books> all() {
        return books;
    }
}
