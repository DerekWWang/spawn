package dev.qube.spawn_9b5fbb9.logistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import dev.qube.spawn_9b5fbb9.engineering.Assessment;
import dev.qube.spawn_9b5fbb9.engineering.Building;
import dev.qube.spawn_9b5fbb9.engineering.BuildingUse;
import dev.qube.spawn_9b5fbb9.engineering.Engineering;
import dev.qube.spawn_9b5fbb9.knowledge.KnowledgeData;
import dev.qube.spawn_9b5fbb9.knowledge.Requirements;
import dev.qube.spawn_9b5fbb9.knowledge.Society;
import dev.qube.spawn_9b5fbb9.registry.ModContent;
import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import dev.qube.spawn_9b5fbb9.techtree.TechTree.FunctionDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The logistics engine: the society's account of what it has. Every figure exists twice. The
 * ground truth is what is really there, counted by the game. The observed truth is what the
 * society's records say, which is all a manager ever sees.
 *
 * <p>The two drift apart the way real books do. Goods moving in and out of a store are written
 * down with error; the clerks have habits, so the error leans one way; and what rots is never
 * written down at all, so stores hold less than the books claim. A stock-take (someone physically
 * counting) brings the books back to the count, as accurately as the society's record-keeping
 * allows. Headcounts of people and animals are a fresh, imperfect census each day.</p>
 */
public final class Ledger {
    public static final String STOCK = "stock/";
    public static final String FLOW = "flow/";
    private static final int HISTORY_DAYS = 64;

    private Ledger() {
    }

    public static LedgerData.Books books(MinecraftServer server, String society) {
        return LedgerData.get(server).books(society);
    }

    private static void dirty(MinecraftServer server) {
        LedgerData.get(server).setDirty();
    }

    private static double sigma(MinecraftServer server, String society) {
        return RecordKeeping.sigma(KnowledgeData.get(server), society);
    }

    /**
     * The standing lean of one kind of record: the same clerks make the same mistakes, so their
     * error has a direction. It is a habit (kept until the next count) scaled by how well the
     * society keeps records now, so better methods shrink it.
     */
    private static double bias(LedgerData.Books books, String kind, double sigma, RandomSource random) {
        return books.bias.computeIfAbsent(kind, k -> habit(random)) * sigma * 0.6;
    }

    private static double habit(RandomSource random) {
        return Math.max(-2.5, Math.min(2.5, random.nextGaussian()));
    }

    private static double clamp(double relativeError) {
        return Math.max(-0.9, Math.min(3.0, relativeError));
    }

    /** A figure as someone would report it: off by about sigma, never negative. */
    static double noisy(double actual, double sigma, RandomSource random) {
        return Math.max(0, actual * (1 + clamp(random.nextGaussian() * sigma)));
    }

    // ------------------------------------------------------------------ flows

    /**
     * Something was gained or used. The true total always rises by n; the recorded total rises by
     * what was written down.
     *
     * @param kind collected, produced, consumed, spoiled, bred or herded
     */
    public static void flow(MinecraftServer server, String society, String kind, String what, int n, RandomSource random) {
        if (n <= 0 || society == null || society.isEmpty()) {
            return;
        }
        LedgerData.Books books = books(server, society);
        String key = FLOW + kind + "/" + what;
        books.actual.merge(key, (double) n, Double::sum);
        if (!"spoiled".equals(kind)) { // nobody writes down what rots unseen
            double s = sigma(server, society);
            double recorded = n * (1 + clamp(bias(books, "flow", s, random) + random.nextGaussian() * s * 0.5));
            books.observed.merge(key, Math.max(0, recorded), Double::sum);
        }
        dirty(server);
    }

    /** A birth in a pen: entered as bred, and remembered so the census does not take the young for herded animals. */
    public static void born(ServerLevel level, Building pen) {
        MinecraftServer server = level.getServer();
        books(server, pen.society).stamps.merge("births/" + pen.id, 1L, Long::sum);
        flow(server, pen.society, "bred", "animals", 1, level.getRandom());
    }

    private static String refKey(TechTree.Ref ref) {
        return switch (ref.kind()) {
            case ITEM, STATION -> spawn_9b5fbb9.MODID + ":" + ref.id();
            case TAG -> "#" + ref.id();
            default -> null;
        };
    }

    public static void recordConsumed(MinecraftServer server, String society, FunctionDef f, int batches) {
        if (server == null || batches <= 0) {
            return;
        }
        RandomSource random = server.overworld().getRandom();
        for (TechTree.Ref in : f.inputs()) {
            String key = refKey(in);
            if (key != null) {
                flow(server, society, "consumed", key, in.count() * batches, random);
            }
        }
    }

    public static void recordProduced(MinecraftServer server, String society, FunctionDef f, int batches) {
        if (server == null || batches <= 0) {
            return;
        }
        RandomSource random = server.overworld().getRandom();
        for (TechTree.Ref out : f.outputs()) {
            String key = refKey(out);
            if (key != null) {
                flow(server, society, "produced", key, out.count() * batches, random);
            }
        }
    }

    /** True for things worth a line in the books: tech-tree items, natural resources and food. */
    public static boolean tracked(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (spawn_9b5fbb9.MODID.equals(id.getNamespace()) || Spoilage.halfLife(stack) > 0) {
            return true;
        }
        for (TechTree.ResourceDef r : TechTree.get().resources) {
            if ("tag".equals(r.source()) && stack.is(Requirements.resourceTag(r.id()))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ stores

    /**
     * Looks into a storage building: ages its contents, counts what is really there, and enters the
     * change since the last look in the books (with error). Spoilage is not entered.
     */
    public static void scanStore(ServerLevel level, Building b) {
        Assessment a = Engineering.current(level, b);
        if (!a.complete) {
            return;
        }
        MinecraftServer server = level.getServer();
        LedgerData.Books books = books(server, b.society);
        RandomSource random = level.getRandom();
        double quality = Spoilage.quality(level, b, a);
        Map<String, Integer> before = new HashMap<>();
        Map<String, Integer> spoiled = new HashMap<>();
        for (BlockPos pos : a.containers) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container c) {
                for (int slot = 0; slot < c.getContainerSize(); slot++) {
                    ItemStack stack = c.getItem(slot);
                    if (!stack.isEmpty()) {
                        before.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
                    }
                }
                Spoilage.apply(level, be, c, quality, spoiled);
            }
        }
        String prefix = STOCK + b.id + "/";
        Set<String> keys = new HashSet<>();
        before.keySet().forEach(item -> keys.add(prefix + item));
        for (String key : books.actual.keySet()) {
            if (key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        double s = sigma(server, b.society);
        double lean = bias(books, "stock", s, random);
        for (String key : keys) {
            String item = key.substring(prefix.length());
            int counted = before.getOrDefault(item, 0);
            double delta = counted - books.actual(key);
            if (delta != 0) {
                double entered = delta * (1 + clamp(lean + random.nextGaussian() * s * 0.5));
                books.observed.put(key, Math.max(0, books.observed(key) + entered));
            }
            int lost = spoiled.getOrDefault(item, 0);
            int now = counted - lost;
            if (now <= 0 && books.observed(key) < 0.5) {
                books.actual.remove(key);
                books.observed.remove(key);
            } else {
                books.actual.put(key, (double) Math.max(0, now));
            }
            if (lost > 0) {
                flow(server, b.society, "spoiled", item, lost, random);
            }
        }
        dirty(server);
    }

    /**
     * A stock-take: someone counts what is in the store and the books are corrected to the count.
     * A count from memory is twice as good as the society's running records. With Writing, a
     * count kept on a clay tablet is twice as good again and one on paper and ink three times,
     * if the counter carries them; they are used up.
     *
     * @return what to tell the counter
     */
    public static String stocktake(ServerLevel level, Building b, ServerPlayer counter) {
        if (b.use != BuildingUse.STORAGE) {
            return b.name + " is not a store.";
        }
        if (counter != null && !b.bounds().inflate(8).contains(counter.position())) {
            return "You have to be at " + b.name + " to count what is in it.";
        }
        scanStore(level, b);
        MinecraftServer server = level.getServer();
        KnowledgeData kd = KnowledgeData.get(server);
        double s = RecordKeeping.sigma(kd, b.society);
        // someone looking at the goods does better than the running record; writing the count down does better still
        String how = "from memory";
        double precision = s / 2;
        if (counter != null && kd.knows(b.society, "T-11")) {
            List<ItemStack> inv = counter.getInventory().getNonEquipmentItems();
            Item paper = ModContent.item("paper");
            Item ink = ModContent.item("ink");
            Item tablet = ModContent.item("clay_tablet");
            if (take(inv, paper, ink)) {
                precision = s / 6;
                how = "written up on paper";
            } else if (take(inv, tablet, null)) {
                precision = s / 4;
                how = "pressed into a clay tablet";
            }
        }
        LedgerData.Books books = books(server, b.society);
        RandomSource random = level.getRandom();
        String prefix = STOCK + b.id + "/";
        int kinds = 0;
        for (String key : new ArrayList<>(books.actual.keySet())) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            if (books.actual(key) <= 0) {
                books.actual.remove(key); // nothing left of it: off the books altogether
                books.observed.remove(key);
            } else {
                books.observed.put(key, (double) Math.round(noisy(books.actual(key), precision, random)));
                kinds++;
            }
        }
        for (String key : new ArrayList<>(books.observed.keySet())) {
            if (key.startsWith(prefix) && !books.actual.containsKey(key)) {
                books.observed.remove(key); // on the books but not on the shelves
            }
        }
        books.bias.put("stock", habit(random)); // a fresh count, a fresh set of mistakes
        books.stamps.put("stocktake/" + b.id, level.getGameTime());
        dirty(server);
        return "Counted " + kinds + " kind(s) of goods in " + b.name + ", " + how + ". The books now match the shelves to within about "
                + percent(precision) + ".";
    }

    private static boolean take(List<ItemStack> inv, Item first, Item second) {
        if (first == null) {
            return false;
        }
        ItemStack a = null;
        ItemStack c = null;
        for (ItemStack s : inv) {
            if (a == null && s.is(first)) {
                a = s;
            } else if (second != null && c == null && s.is(second)) {
                c = s;
            }
        }
        if (a == null || (second != null && c == null)) {
            return false;
        }
        a.shrink(1);
        if (c != null) {
            c.shrink(1);
        }
        return true;
    }

    static String percent(double fraction) {
        double pct = fraction * 100;
        boolean whole = pct >= 10 || Math.abs(pct - Math.round(pct)) < 0.05;
        return (whole ? String.valueOf(Math.round(pct)) : String.format(java.util.Locale.ROOT, "%.1f", pct)) + "%";
    }

    // ------------------------------------------------------------------ census

    /**
     * Counts the society's people and animals in this dimension and enters the (imperfect) result.
     * People are its members online plus villagers living in its residences; animals are those in
     * its pens.
     */
    public static void census(ServerLevel level, String society) {
        MinecraftServer server = level.getServer();
        LedgerData.Books books = books(server, society);
        RandomSource random = level.getRandom();
        double s = sigma(server, society);
        Set<String> touched = new HashSet<>();

        int alive = 0;
        int wounded = 0;
        for (ServerPlayer p : Society.onlineMembers(server, society)) {
            if (p.isAlive() && !p.isSpectator()) {
                alive++;
                if (p.getHealth() < p.getMaxHealth() * 0.5F) {
                    wounded++;
                }
            }
        }
        set(books, touched, "people/players/alive", alive);
        set(books, touched, "people/players/wounded", wounded);

        for (Building b : Engineering.of(server, society)) {
            if (b.dimension != level.dimension()) {
                continue;
            }
            Assessment a = Engineering.current(level, b);
            if (!a.complete) {
                continue; // out of sight: the last figures stand
            }
            if (b.use == BuildingUse.RESIDENCE) {
                clear(books, "resident/" + b.id + "/");
                int living = 0;
                int hurt = 0;
                int expecting = 0;
                for (Villager v : level.getEntitiesOfClass(Villager.class, b.bounds())) {
                    if (!v.isAlive()) {
                        continue;
                    }
                    living++;
                    if (v.getHealth() < v.getMaxHealth() * 0.5F) {
                        hurt++;
                    }
                    if (Gestation.pregnant(v)) {
                        expecting++;
                    }
                }
                set(books, touched, "resident/" + b.id + "/alive", living);
                set(books, touched, "resident/" + b.id + "/wounded", hurt);
                set(books, touched, "resident/" + b.id + "/pregnant", expecting);
                set(books, touched, "housing/" + b.id, a.functional ? a.capacity : 0);
            } else if (b.use == BuildingUse.PEN) {
                String prefix = "herd/" + b.id + "/";
                double was = books.sum(books.actual, prefix, "/count");
                clear(books, prefix);
                Map<String, int[]> herd = new TreeMap<>();
                for (Animal animal : level.getEntitiesOfClass(Animal.class, b.bounds())) {
                    if (!animal.isAlive()) {
                        continue;
                    }
                    String type = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).getPath();
                    int[] n = herd.computeIfAbsent(type, k -> new int[3]);
                    n[0]++;
                    if (Gestation.pregnant(animal)) {
                        n[1]++;
                    }
                    if (animal.isBaby()) {
                        n[2]++;
                    }
                }
                int total = 0;
                for (Map.Entry<String, int[]> e : herd.entrySet()) {
                    set(books, touched, prefix + e.getKey() + "/count", e.getValue()[0]);
                    set(books, touched, prefix + e.getKey() + "/pregnant", e.getValue()[1]);
                    set(books, touched, prefix + e.getKey() + "/young", e.getValue()[2]);
                    total += e.getValue()[0];
                }
                // animals that turned up since the last count and were not born here were herded in
                long births = books.stamps.getOrDefault("births/" + b.id, 0L);
                books.stamps.remove("births/" + b.id);
                int herded = (int) Math.max(0, total - was - births);
                flow(server, society, "herded", "animals", herded, random);
            }
        }
        for (String key : touched) {
            double actual = books.actual(key);
            if (actual == 0) {
                books.observed.remove(key);
            } else {
                books.observed.put(key, noisy(actual, s, random));
            }
        }
        long day = level.getGameTime() / 24000L;
        books.stamps.put("census", level.getGameTime());
        snapshot(books, day);
        dirty(server);
    }

    private static void set(LedgerData.Books books, Set<String> touched, String key, int value) {
        touched.add(key);
        if (value == 0) {
            books.actual.remove(key);
        } else {
            books.actual.put(key, (double) value);
        }
    }

    private static void clear(LedgerData.Books books, String prefix) {
        books.actual.keySet().removeIf(k -> k.startsWith(prefix));
        books.observed.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** One line per quantity, summed over buildings, for both truths. */
    public static Map<String, Double> totals(LedgerData.Books books, boolean observed) {
        Map<String, Double> src = observed ? books.observed : books.actual;
        Map<String, Double> out = new TreeMap<>();
        for (Map.Entry<String, Double> e : src.entrySet()) {
            String[] part = e.getKey().split("/");
            String key;
            if (part[0].equals("stock") && part.length >= 3) {
                key = "stock/" + e.getKey().substring(part[0].length() + part[1].length() + 2);
            } else if (part[0].equals("resident") && part.length == 3) {
                key = "people/" + part[2];
            } else if (part[0].equals("people") && part.length == 3) {
                key = "people/" + part[2];
            } else if (part[0].equals("housing")) {
                key = "people/housing";
            } else if (part[0].equals("herd") && part.length == 4) {
                key = "herd/" + part[2] + ("count".equals(part[3]) ? "" : "/" + part[3]);
            } else {
                key = e.getKey();
            }
            out.merge(key, e.getValue(), Double::sum);
        }
        return out;
    }

    private static void snapshot(LedgerData.Books books, long day) {
        books.history.removeIf(sn -> sn.day() == day);
        books.history.add(new LedgerData.Snapshot(day, Map.copyOf(totals(books, false)), Map.copyOf(totals(books, true))));
        while (books.history.size() > HISTORY_DAYS) {
            books.history.remove(0);
        }
    }

    // ------------------------------------------------------------------ reports

    private static String name(String key) {
        if (key.startsWith("#")) {
            TechTree.ResourceDef r = TechTree.get().resourcesById.get(key.substring(1));
            return r == null ? key.substring(1) : r.name().toLowerCase(java.util.Locale.ROOT);
        }
        Identifier id = Identifier.tryParse(key);
        if (id != null) {
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item != null) {
                return new ItemStack(item).getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
            }
        }
        return key.replace('_', ' ');
    }

    private static String list(Map<String, Double> values, int limit) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(values.entrySet());
        entries.removeIf(e -> Math.round(e.getValue()) <= 0);
        entries.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            sb.append(i > 0 ? ", " : "").append(name(entries.get(i).getKey())).append(" ").append(Math.round(entries.get(i).getValue()));
        }
        if (entries.size() > limit) {
            sb.append(", and ").append(entries.size() - limit).append(" more");
        }
        return sb.length() == 0 ? "nothing" : sb.toString();
    }

    private static Map<String, Double> under(Map<String, Double> map, String prefix) {
        Map<String, Double> out = new TreeMap<>();
        for (Map.Entry<String, Double> e : map.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                out.put(e.getKey().substring(prefix.length()), e.getValue());
            }
        }
        return out;
    }

    /** The books as the manager sees them. Nothing here comes from the ground truth. */
    public static List<String> report(MinecraftServer server, String society) {
        LedgerData.Books books = books(server, society);
        KnowledgeData kd = KnowledgeData.get(server);
        RecordKeeping.Level level = RecordKeeping.level(kd, society);
        List<String> out = new ArrayList<>();
        out.add("Records kept by " + level.name() + " (figures are typically off by " + percent(RecordKeeping.sigma(kd, society)) + ")");
        Map<String, Double> t = totals(books, true);
        long now = server.overworld().getGameTime();
        Long census = books.stamps.get("census");
        out.add("People: " + Math.round(t.getOrDefault("people/alive", 0.0)) + " alive, " + Math.round(t.getOrDefault("people/pregnant", 0.0))
                + " pregnant, " + Math.round(t.getOrDefault("people/wounded", 0.0)) + " wounded; beds for "
                + Math.round(t.getOrDefault("people/housing", 0.0)) + (census == null ? " (no census yet)" : " (census " + ago(now - census) + ")"));
        Map<String, Double> herd = under(t, "herd/");
        if (herd.isEmpty()) {
            out.add("Livestock: none penned");
        } else {
            StringBuilder sb = new StringBuilder("Livestock: ");
            boolean first = true;
            for (Map.Entry<String, Double> e : herd.entrySet()) {
                if (e.getKey().contains("/")) {
                    continue;
                }
                long pregnant = Math.round(herd.getOrDefault(e.getKey() + "/pregnant", 0.0));
                long young = Math.round(herd.getOrDefault(e.getKey() + "/young", 0.0));
                sb.append(first ? "" : "; ").append(e.getKey().replace('_', ' ')).append(" ").append(Math.round(e.getValue()));
                if (pregnant > 0 || young > 0) {
                    sb.append(" (").append(pregnant).append(" pregnant, ").append(young).append(" young)");
                }
                first = false;
            }
            out.add(sb.toString());
        }
        out.add("Herded in " + Math.round(books.observed(FLOW + "herded/animals")) + ", born " + Math.round(books.observed(FLOW + "bred/animals"))
                + " (all time)");
        boolean anyStore = false;
        for (Building b : Engineering.of(server, society)) {
            if (b.use != BuildingUse.STORAGE) {
                continue;
            }
            anyStore = true;
            Long counted = books.stamps.get("stocktake/" + b.id);
            out.add(b.name + ": " + list(under(books.observed, STOCK + b.id + "/"), 8)
                    + (counted == null ? " (never counted)" : " (counted " + ago(now - counted) + ")"));
        }
        if (!anyStore) {
            out.add("Stores: none. Register a building as storage to keep books on its contents.");
        } else {
            out.add("In store, all told: " + list(under(t, "stock/"), 12));
        }
        out.add("Collected: " + list(under(books.observed, FLOW + "collected/"), 8));
        out.add("Produced: " + list(under(books.observed, FLOW + "produced/"), 8));
        out.add("Used up: " + list(under(books.observed, FLOW + "consumed/"), 8));
        return out;
    }

    private static String ago(long ticks) {
        if (ticks < 0) {
            return "just now";
        }
        long days = ticks / 24000L;
        return days == 0 ? "today" : days == 1 ? "a day ago" : days + " days ago";
    }

    /** For operators and analysis: every total as it is and as it is recorded, with the error. */
    public static List<String> truth(MinecraftServer server, String society) {
        LedgerData.Books books = books(server, society);
        Map<String, Double> actual = totals(books, false);
        Map<String, Double> observed = totals(books, true);
        Set<String> keys = new java.util.TreeSet<>(actual.keySet());
        keys.addAll(observed.keySet());
        List<String> out = new ArrayList<>();
        out.add("metric,actual,observed,error");
        for (String key : keys) {
            double a = actual.getOrDefault(key, 0.0);
            double o = observed.getOrDefault(key, 0.0);
            String error = a == 0 ? (o == 0 ? "0%" : "phantom") : Math.round((o - a) / a * 100) + "%";
            out.add(key + "," + Math.round(a) + "," + Math.round(o) + "," + error);
        }
        return out;
    }

    /** Daily totals for the last days, both truths: day,metric,actual,observed. */
    public static List<String> history(MinecraftServer server, String society, int days) {
        LedgerData.Books books = books(server, society);
        List<String> out = new ArrayList<>();
        out.add("day,metric,actual,observed");
        int from = Math.max(0, books.history.size() - days);
        for (LedgerData.Snapshot sn : books.history.subList(from, books.history.size())) {
            Set<String> keys = new java.util.TreeSet<>(sn.actual().keySet());
            keys.addAll(sn.observed().keySet());
            for (String key : keys) {
                out.add(sn.day() + "," + key + "," + Math.round(sn.actual().getOrDefault(key, 0.0)) + ","
                        + Math.round(sn.observed().getOrDefault(key, 0.0)));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ ticking

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        MinecraftServer server = level.getServer();
        if (now % 20 == 0) {
            for (Building b : Engineering.in(level)) {
                if (b.use == BuildingUse.STORAGE && Math.floorMod(now / 20 + b.id.hashCode(), 10) == 0) {
                    scanStore(level, b);
                }
            }
        }
        if (now % 200 == 7) {
            // a census at the start of each day for every society with someone to take it
            long day = now / 24000L;
            String stamp = "census_day/" + level.dimension().identifier();
            Set<String> societies = new HashSet<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                societies.add(Society.keyOf(p));
            }
            for (String society : societies) {
                LedgerData.Books books = books(server, society);
                if (books.stamps.getOrDefault(stamp, -1L) < day) {
                    books.stamps.put(stamp, day);
                    census(level, society);
                }
            }
            // forget the books of buildings that no longer exist
            for (Map.Entry<String, LedgerData.Books> e : LedgerData.get(server).all().entrySet()) {
                purge(server, e.getValue());
            }
        }
    }

    private static final Set<String> PER_BUILDING = Set.of("stock", "herd", "resident", "housing", "stocktake", "births");

    private static void purge(MinecraftServer server, LedgerData.Books books) {
        Set<String> gone = new HashSet<>();
        for (Map<String, ?> map : List.of(books.actual, books.observed, books.stamps)) {
            for (String key : map.keySet()) {
                String[] part = key.split("/");
                if (part.length >= 2 && PER_BUILDING.contains(part[0]) && Engineering.byId(server, part[1]) == null) {
                    gone.add(part[0] + "/" + part[1]);
                }
            }
        }
        if (gone.isEmpty()) {
            return;
        }
        for (Map<String, ?> map : List.of(books.actual, books.observed, books.stamps)) {
            map.keySet().removeIf(k -> {
                for (String prefix : gone) {
                    if (k.equals(prefix) || k.startsWith(prefix + "/")) {
                        return true;
                    }
                }
                return false;
            });
        }
        dirty(server);
    }
}
