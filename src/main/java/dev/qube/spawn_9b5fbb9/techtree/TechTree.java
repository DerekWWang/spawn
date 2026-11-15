package dev.qube.spawn_9b5fbb9.techtree;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;

/**
 * The tech tree from the spreadsheet, loaded once from the generated techtree.json
 * that ships inside the mod jar. Both logical sides read the same file, so the client
 * can show recipes without the server sending them.
 */
public final class TechTree {
    public static final String RESOURCE_PATH = "/data/" + spawn_9b5fbb9.MODID + "/techtree/techtree.json";

    public enum RefKind { ITEM, TAG, ENV, PHEN, STATION }

    /** One input, output or means of production. */
    public record Ref(RefKind kind, String id, String name, int count) {
        static Ref parse(JsonObject o) {
            String ref = o.get("ref").getAsString();
            int colon = ref.indexOf(':');
            if (colon < 0) {
                throw new IllegalStateException("Malformed ref " + ref);
            }
            RefKind kind = switch (ref.substring(0, colon)) {
                case "item" -> RefKind.ITEM;
                case "tag" -> RefKind.TAG;
                case "env" -> RefKind.ENV;
                case "phen" -> RefKind.PHEN;
                case "station" -> RefKind.STATION;
                default -> throw new IllegalStateException("Unknown ref kind " + ref);
            };
            int count = o.has("count") ? o.get("count").getAsInt() : 1;
            if (count < 1) {
                throw new IllegalStateException("Non-positive count in " + ref);
            }
            return new Ref(kind, ref.substring(colon + 1), o.get("name").getAsString(), count);
        }
    }

    public enum Mode { THEORY, HAND, STATION, WORLD, PRODUCER }

    public record FunctionDef(int index, String id, String name, boolean theoretical, List<Ref> inputs, List<Ref> outputs,
                              List<Ref> means, List<String> theory, String mechanism, String notes, int era, String eraName,
                              String route, int depth, int prerequisites, int unlocks, Mode mode, String host, int ticks,
                              String worldHint) {
        public boolean isHand() {
            return mode == Mode.HAND;
        }
    }

    public record Food(int nutrition, float saturation) { }

    public record Medicine(String effect, int seconds, int amplifier, int heal) { }

    public record ItemDef(String id, String name, String category, String sheetCategory, int era, int depth, List<String> madeBy,
                          int usedBy, int stack, int durability, String tool, Food food, Medicine medicine, boolean fertilizer,
                          boolean station, boolean capstone, boolean treatise, String resource) { }

    public record ResourceDef(String id, String name, String kind, String source, String description, String access,
                              String recognisedBy, String tag, String env, String phenomenon, String requirement, String how) { }

    public record Tier(int w, int h, int d, int batch) { }

    public record Area(int radius, int dyMin, int dyMax, String match, int min) { }

    public record Shaft(int depth, String headframe, int headframeMin) { }

    public record Producer(String item, int interval, int max, String function, int perBlock, int radius) { }

    public record Ceiling(String block, int perArea) { }

    public record StationDef(String id, String name, String kind, String shape, List<Tier> tiers, String casing, String family,
                             String interior, boolean openTop, boolean chimney, int fuelTier, List<String> conditions, String power,
                             String provider, boolean room, Ceiling ceiling, Area area, Shaft shaft, Producer producer,
                             boolean coverage, int accessRadius, String note, List<String> hosts) {
        public boolean isRoom() {
            return room;
        }

        public boolean isPrecisionTool() {
            return "precision".equals(family) && !room;
        }
    }

    public record OreDef(String block, String name, String resource, String host, String dropItem, String access,
                         String recognisedBy, boolean reservoir, String item) { }

    public record BlockDef(String id, String name, float hardness, float resistance, String sound, String tool, String level,
                           String drops, boolean ore) { }

    private static TechTree instance;

    public final List<FunctionDef> functions;
    public final Map<String, FunctionDef> functionsById;
    public final List<ItemDef> items;
    public final Map<String, ItemDef> itemsById;
    public final List<ResourceDef> resources;
    public final Map<String, ResourceDef> resourcesById;
    public final List<StationDef> stations;
    public final Map<String, StationDef> stationsById;
    public final List<OreDef> ores;
    public final Map<String, OreDef> oresByBlock;
    public final List<BlockDef> blocks;
    /** item id (our namespace) -> functions that output it */
    public final Map<String, List<FunctionDef>> producersOf;

    private TechTree(JsonObject root) {
        List<ResourceDef> res = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("resources")) {
            JsonObject o = e.getAsJsonObject();
            res.add(new ResourceDef(str(o, "id"), str(o, "name"), str(o, "kind"), str(o, "source"), str(o, "description"),
                    str(o, "access"), str(o, "recognisedBy"), str(o, "tag"), str(o, "env"), str(o, "phenomenon"),
                    str(o, "requirement"), str(o, "how")));
        }
        this.resources = List.copyOf(res);
        this.resourcesById = index(res, ResourceDef::id);

        List<ItemDef> its = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("items")) {
            JsonObject o = e.getAsJsonObject();
            Food food = null;
            if (o.has("food") && o.get("food").isJsonObject()) {
                JsonObject f = o.getAsJsonObject("food");
                food = new Food(f.get("nutrition").getAsInt(), f.get("saturation").getAsFloat());
            }
            Medicine med = null;
            if (o.has("medicine") && o.get("medicine").isJsonObject()) {
                JsonObject m = o.getAsJsonObject("medicine");
                med = new Medicine(m.get("effect").getAsString(), m.get("seconds").getAsInt(), m.get("amplifier").getAsInt(),
                        m.get("heal").getAsInt());
            }
            its.add(new ItemDef(str(o, "id"), str(o, "name"), str(o, "category"), str(o, "sheetCategory"), integer(o, "era", 0),
                    integer(o, "depth", 0), strings(o, "madeBy"), integer(o, "usedBy", 0), integer(o, "stack", 64),
                    integer(o, "durability", 0), str(o, "tool"), food, med, bool(o, "fertilizer"), bool(o, "station"),
                    bool(o, "capstone"), bool(o, "treatise"), str(o, "resource")));
        }
        this.items = List.copyOf(its);
        this.itemsById = index(its, ItemDef::id);

        List<StationDef> sts = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("stations")) {
            JsonObject o = e.getAsJsonObject();
            List<Tier> tiers = new ArrayList<>();
            for (JsonElement t : o.getAsJsonArray("tiers")) {
                JsonObject to = t.getAsJsonObject();
                Tier tier = new Tier(to.get("w").getAsInt(), to.get("h").getAsInt(), to.get("d").getAsInt(), to.get("batch").getAsInt());
                if (tier.w() < 1 || tier.h() < 1 || tier.d() < 1 || tier.batch() < 1) {
                    throw new IllegalStateException("Bad tier for " + str(o, "id"));
                }
                tiers.add(tier);
            }
            if (tiers.isEmpty()) {
                throw new IllegalStateException("Station without tiers: " + str(o, "id"));
            }
            Ceiling ceiling = null;
            if (o.has("ceiling") && o.get("ceiling").isJsonObject()) {
                JsonObject c = o.getAsJsonObject("ceiling");
                ceiling = new Ceiling(c.get("block").getAsString(), c.get("perArea").getAsInt());
            }
            Area area = null;
            if (o.has("area") && o.get("area").isJsonObject()) {
                JsonObject a = o.getAsJsonObject("area");
                JsonArray dy = a.getAsJsonArray("dy");
                area = new Area(a.get("radius").getAsInt(), dy.get(0).getAsInt(), dy.get(1).getAsInt(), a.get("match").getAsString(),
                        a.get("min").getAsInt());
            }
            Shaft shaft = null;
            if (o.has("shaft") && o.get("shaft").isJsonObject()) {
                JsonObject s = o.getAsJsonObject("shaft");
                shaft = new Shaft(s.get("depth").getAsInt(), s.get("headframe").getAsString(), s.get("headframeMin").getAsInt());
            }
            Producer producer = null;
            if (o.has("producer") && o.get("producer").isJsonObject()) {
                JsonObject p = o.getAsJsonObject("producer");
                producer = new Producer(str(p, "item"), integer(p, "interval", 1200), integer(p, "max", 64), str(p, "function"),
                        integer(p, "perBlock", 1), integer(p, "radius", 0));
            }
            int accessRadius = 0;
            if (o.has("access") && o.get("access").isJsonObject()) {
                accessRadius = o.getAsJsonObject("access").get("radius").getAsInt();
            }
            sts.add(new StationDef(str(o, "id"), str(o, "name"), str(o, "kind"), str(o, "shape"), List.copyOf(tiers), str(o, "casing"),
                    str(o, "family"), defaulted(str(o, "interior"), "any"), bool(o, "openTop"), bool(o, "chimney"),
                    integer(o, "fuelTier", 0), strings(o, "conditions"), defaulted(str(o, "power"), "none"), str(o, "provider"),
                    bool(o, "room"), ceiling, area, shaft, producer, bool(o, "coverage"), accessRadius, str(o, "note"),
                    strings(o, "hosts")));
        }
        this.stations = List.copyOf(sts);
        this.stationsById = index(sts, StationDef::id);

        List<FunctionDef> fns = new ArrayList<>();
        JsonArray farr = root.getAsJsonArray("functions");
        for (int i = 0; i < farr.size(); i++) {
            JsonObject o = farr.get(i).getAsJsonObject();
            Mode mode = switch (str(o, "mode")) {
                case "theory" -> Mode.THEORY;
                case "hand" -> Mode.HAND;
                case "station" -> Mode.STATION;
                case "world" -> Mode.WORLD;
                case "producer" -> Mode.PRODUCER;
                default -> throw new IllegalStateException("Unknown mode for " + str(o, "id"));
            };
            fns.add(new FunctionDef(i, str(o, "id"), str(o, "name"), "theoretical".equals(str(o, "type")), refs(o, "inputs"),
                    refs(o, "outputs"), refs(o, "means"), strings(o, "theory"), defaulted(str(o, "mechanism"), ""),
                    str(o, "notes"), integer(o, "era", 0), defaulted(str(o, "eraName"), ""), defaulted(str(o, "route"), ""),
                    integer(o, "depth", 0), integer(o, "prerequisites", 0), integer(o, "unlocks", 0), mode, str(o, "host"),
                    Math.max(1, integer(o, "ticks", 20)), str(o, "worldHint")));
        }
        this.functions = List.copyOf(fns);
        this.functionsById = index(fns, FunctionDef::id);

        List<OreDef> ors = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("ores")) {
            JsonObject o = e.getAsJsonObject();
            ors.add(new OreDef(str(o, "block"), str(o, "name"), str(o, "resource"), str(o, "host"), str(o, "dropItem"),
                    str(o, "access"), str(o, "recognisedBy"), bool(o, "reservoir"), str(o, "item")));
        }
        this.ores = List.copyOf(ors);
        this.oresByBlock = index(ors, OreDef::block);

        List<BlockDef> bls = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("blocks")) {
            JsonObject o = e.getAsJsonObject();
            bls.add(new BlockDef(str(o, "id"), str(o, "name"), o.get("hardness").getAsFloat(), o.get("resistance").getAsFloat(),
                    str(o, "sound"), str(o, "tool"), str(o, "level"), str(o, "drops"), bool(o, "ore")));
        }
        this.blocks = List.copyOf(bls);

        Map<String, List<FunctionDef>> prod = new HashMap<>();
        for (FunctionDef f : functions) {
            for (Ref out : f.outputs()) {
                prod.computeIfAbsent(out.id(), k -> new ArrayList<>()).add(f);
            }
        }
        prod.replaceAll((k, v) -> List.copyOf(v));
        this.producersOf = Collections.unmodifiableMap(prod);

        validate();
    }

    /** Cross-reference checks; a broken generated file should fail loudly at start-up, not mid-game. */
    private void validate() {
        for (FunctionDef f : functions) {
            for (String t : f.theory()) {
                FunctionDef th = functionsById.get(t);
                if (th == null || !th.theoretical()) {
                    throw new IllegalStateException(f.id() + " requires unknown theory " + t);
                }
            }
            List<Ref> all = new ArrayList<>(f.inputs());
            all.addAll(f.outputs());
            all.addAll(f.means());
            for (Ref r : all) {
                switch (r.kind()) {
                    case ITEM -> {
                        if (!itemsById.containsKey(r.id())) {
                            throw new IllegalStateException(f.id() + " references unknown item " + r.id());
                        }
                    }
                    case TAG -> {
                        if (!resourcesById.containsKey(r.id())) {
                            throw new IllegalStateException(f.id() + " references unknown resource " + r.id());
                        }
                    }
                    case STATION -> {
                        if (!stationsById.containsKey(r.id())) {
                            throw new IllegalStateException(f.id() + " references unknown station " + r.id());
                        }
                    }
                    case ENV, PHEN -> { }
                }
            }
            if (f.mode() == Mode.STATION && (f.host() == null || !stationsById.containsKey(f.host()))) {
                throw new IllegalStateException(f.id() + " has no valid host station");
            }
        }
        for (StationDef s : stations) {
            if (!itemsById.containsKey(s.id())) {
                throw new IllegalStateException("Station " + s.id() + " has no item");
            }
            for (Tier t : s.tiers()) {
                if (("box".equals(s.shape()) || "centered".equals(s.shape())) && t.w() % 2 == 0) {
                    throw new IllegalStateException("Station " + s.id() + " has an even width");
                }
                if ("centered".equals(s.shape()) && t.d() % 2 == 0) {
                    throw new IllegalStateException("Centred station " + s.id() + " has an even depth");
                }
            }
        }
    }

    public static synchronized TechTree get() {
        if (instance == null) {
            try (InputStream in = TechTree.class.getResourceAsStream(RESOURCE_PATH)) {
                if (in == null) {
                    throw new IllegalStateException("Missing " + RESOURCE_PATH + " - run tools/techtree/generate_techtree.py");
                }
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                instance = new TechTree(root);
                spawn_9b5fbb9.LOGGER.info("Loaded tech tree: {} functions, {} items, {} structures, {} deposits",
                        instance.functions.size(), instance.items.size(), instance.stations.size(), instance.ores.size());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not read " + RESOURCE_PATH, e);
            }
        }
        return instance;
    }

    public FunctionDef function(int index) {
        return index >= 0 && index < functions.size() ? functions.get(index) : null;
    }

    public List<FunctionDef> hostedBy(String stationId) {
        StationDef s = stationsById.get(stationId);
        if (s == null) {
            return List.of();
        }
        List<FunctionDef> out = new ArrayList<>();
        for (String id : s.hosts()) {
            FunctionDef f = functionsById.get(id);
            if (f != null) {
                out.add(f);
            }
        }
        return out;
    }

    /** The function that builds the infrastructure a resource can only be worked from, or null. */
    public static String accessFunction(String access) {
        return switch (access == null ? "" : access) {
            case "shaft_mine" -> "P-053";
            case "deep_mine" -> "P-127";
            case "drilling_rig" -> "P-141";
            default -> null;
        };
    }

    /** Resolves a function id, item id or name (case-insensitive) to the function to aim for. */
    public FunctionDef resolveTarget(String target) {
        if (target == null) {
            return null;
        }
        FunctionDef f = functionsById.get(target.toUpperCase(java.util.Locale.ROOT));
        if (f != null) {
            return f;
        }
        String key = target.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        List<FunctionDef> makers = producersOf.get(key);
        if (makers != null && !makers.isEmpty()) {
            return makers.stream().min(java.util.Comparator.comparingInt(FunctionDef::depth)).orElse(null);
        }
        for (FunctionDef x : functions) {
            if (x.name().equalsIgnoreCase(target) || x.name().toLowerCase(java.util.Locale.ROOT).replace(' ', '_').equals(key)) {
                return x;
            }
        }
        return null;
    }

    /**
     * Every undiscovered function on the shallowest route to the target, in an order in which they can
     * be pursued (each one after everything it depends on). Where an item has several makers, a known
     * one is used if there is one, otherwise the shallowest.
     */
    public List<FunctionDef> route(String targetId, java.util.Set<String> known) {
        java.util.LinkedHashSet<String> needed = new java.util.LinkedHashSet<>();
        collect(functionsById.get(targetId), known, needed, new java.util.HashSet<>());
        List<FunctionDef> out = new ArrayList<>();
        for (String id : needed) {
            out.add(functionsById.get(id));
        }
        return out;
    }

    private void collect(FunctionDef f, java.util.Set<String> known, java.util.Set<String> needed, java.util.Set<String> seen) {
        if (f == null || known.contains(f.id()) || !seen.add(f.id())) {
            return;
        }
        for (String t : f.theory()) {
            collect(functionsById.get(t), known, needed, seen);
        }
        List<Ref> refs = new ArrayList<>(f.inputs());
        refs.addAll(f.means());
        for (Ref r : refs) {
            switch (r.kind()) {
                case ITEM, STATION -> {
                    List<FunctionDef> makers = producersOf.get(r.id());
                    if (makers == null || makers.isEmpty() || makers.stream().anyMatch(m -> known.contains(m.id()))) {
                        continue;
                    }
                    collect(makers.stream().min(java.util.Comparator.comparingInt(FunctionDef::depth)).orElse(null), known, needed, seen);
                }
                case TAG -> {
                    ResourceDef res = resourcesById.get(r.id());
                    if (res != null) {
                        if (res.recognisedBy() != null) {
                            collect(functionsById.get(res.recognisedBy()), known, needed, seen);
                        }
                        String access = accessFunction(res.access());
                        if (access != null) {
                            collect(functionsById.get(access), known, needed, seen);
                        }
                    }
                }
                case ENV, PHEN -> { }
            }
        }
        needed.add(f.id());
    }

    /** Display name for a ref, falling back to the raw name from the spreadsheet. */
    public String displayName(Ref r) {
        return r.name();
    }

    // ------------------------------------------------------------------ json helpers

    private static <T> Map<String, T> index(List<T> list, java.util.function.Function<T, String> key) {
        Map<String, T> map = new LinkedHashMap<>();
        for (T t : list) {
            if (map.put(key.apply(t), t) != null) {
                throw new IllegalStateException("Duplicate id " + key.apply(t));
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static List<Ref> refs(JsonObject o, String key) {
        List<Ref> out = new ArrayList<>();
        if (o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(key)) {
                out.add(Ref.parse(e.getAsJsonObject()));
            }
        }
        return List.copyOf(out);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static String defaulted(String s, String d) {
        return s == null ? d : s;
    }

    private static int integer(JsonObject o, String key, int d) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? d : e.getAsInt();
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(key);
        if (e != null && e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                out.add(x.getAsString());
            }
        }
        return List.copyOf(out);
    }
}
