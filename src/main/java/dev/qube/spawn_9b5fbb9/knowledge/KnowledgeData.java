package dev.qube.spawn_9b5fbb9.knowledge;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * What each society knows, saved with the world. A society is a scoreboard team, or a lone player.
 * Knowledge is never lost except through the /techtree revoke command.
 */
public final class KnowledgeData extends SavedData {
    private static final Codec<Set<String>> SET = Codec.STRING.listOf().xmap(
            list -> (Set<String>) new LinkedHashSet<>(list), set -> List.copyOf(set));

    public static final Codec<KnowledgeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, SET).optionalFieldOf("known", Map.of()).forGetter(d -> d.known),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("player_society", Map.of()).forGetter(d -> d.playerSociety),
            SET.optionalFieldOf("welcomed", Set.of()).forGetter(d -> d.welcomed)
    ).apply(i, KnowledgeData::new));

    public static final SavedDataType<KnowledgeData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, "knowledge"), KnowledgeData::new, CODEC);

    private final Map<String, Set<String>> known = new HashMap<>();
    /** player uuid -> the society key they belonged to when last seen, so knowledge can follow them between teams */
    private final Map<String, String> playerSociety = new HashMap<>();
    private final Set<String> welcomed = new HashSet<>();

    public KnowledgeData() {
    }

    private KnowledgeData(Map<String, Set<String>> known, Map<String, String> playerSociety, Set<String> welcomed) {
        known.forEach((k, v) -> this.known.put(k, new LinkedHashSet<>(v)));
        this.playerSociety.putAll(playerSociety);
        this.welcomed.addAll(welcomed);
    }

    public static KnowledgeData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean knows(String society, String functionId) {
        Set<String> set = known.get(society);
        return set != null && set.contains(functionId);
    }

    public boolean knowsAll(String society, Collection<String> functionIds) {
        for (String id : functionIds) {
            if (!knows(society, id)) {
                return false;
            }
        }
        return true;
    }

    public Set<String> knownBy(String society) {
        Set<String> set = known.get(society);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    /** @return true if this is new knowledge */
    public boolean grant(String society, String functionId) {
        boolean added = known.computeIfAbsent(society, k -> new LinkedHashSet<>()).add(functionId);
        if (added) {
            setDirty();
        }
        return added;
    }

    public boolean revoke(String society, String functionId) {
        Set<String> set = known.get(society);
        boolean removed = set != null && set.remove(functionId);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public void revokeAll(String society) {
        if (known.remove(society) != null) {
            setDirty();
        }
    }

    /** Copies everything one society knows into another (used when a player joins a team). */
    public boolean merge(String from, String into) {
        Set<String> src = known.get(from);
        if (src == null || src.isEmpty() || from.equals(into)) {
            return false;
        }
        boolean changed = known.computeIfAbsent(into, k -> new LinkedHashSet<>()).addAll(src);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public String lastSociety(String player) {
        return playerSociety.get(player);
    }

    public void setLastSociety(String player, String society) {
        if (!society.equals(playerSociety.put(player, society))) {
            setDirty();
        }
    }

    /** @return true the first time it is called for this player */
    public boolean markWelcomed(String player) {
        boolean first = welcomed.add(player);
        if (first) {
            setDirty();
        }
        return first;
    }

    public List<String> societies() {
        return List.copyOf(known.keySet());
    }
}
