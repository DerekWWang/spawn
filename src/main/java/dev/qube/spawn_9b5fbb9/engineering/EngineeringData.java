package dev.qube.spawn_9b5fbb9.engineering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Every registered building and every saved schema, stored with the world. */
public final class EngineeringData extends SavedData {
    public static final Codec<EngineeringData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Building.CODEC.listOf().optionalFieldOf("buildings", List.of()).forGetter(d -> List.copyOf(d.buildings.values())),
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Schema.CODEC)).optionalFieldOf("schemas", Map.of())
                    .forGetter(d -> d.schemas),
            Codec.INT.optionalFieldOf("next_id", 1).forGetter(d -> d.nextId)
    ).apply(i, EngineeringData::new));

    public static final SavedDataType<EngineeringData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, "engineering"), EngineeringData::new, CODEC);

    final Map<String, Building> buildings = new LinkedHashMap<>();
    /** society -> schema name -> schema */
    final Map<String, Map<String, Schema>> schemas = new HashMap<>();
    int nextId = 1;

    public EngineeringData() {
    }

    private EngineeringData(List<Building> buildings, Map<String, Map<String, Schema>> schemas, int nextId) {
        for (Building b : buildings) {
            this.buildings.put(b.id, b);
        }
        schemas.forEach((k, v) -> this.schemas.put(k, new LinkedHashMap<>(v)));
        this.nextId = nextId;
    }

    public static EngineeringData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    String newId() {
        return "b" + nextId++;
    }

    public List<Building> all() {
        return new ArrayList<>(buildings.values());
    }

    public Map<String, Schema> schemasOf(String society) {
        return schemas.getOrDefault(society, Map.of());
    }
}
