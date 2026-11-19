package dev.qube.spawn_9b5fbb9.world;

import com.mojang.serialization.MapCodec;
import dev.qube.spawn_9b5fbb9.content.SurvivalContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Puts every natural spawn back in its own creature's category, after the forbidden monsters are gone.
 *
 * Vanilla lists ocelots among a jungle's monsters, so they spawn by the monster rules: every tick, until
 * the monster cap is full. An ocelot counts as a creature, never towards that cap, and in vanilla the cap
 * fills with zombies and spiders. With those removed it never fills, and a jungle near a player bred
 * ocelots by the thousand. Listed as a creature, an ocelot spawns as other wild animals do.
 */
public final class SpawnCategories {
    private SpawnCategories() {}

    public enum OwnCategory implements BiomeModifier {
        INSTANCE;

        @Override
        public void modify(RegistryAccess registries, Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
            if (phase != Phase.AFTER_EVERYTHING) return;
            var spawns = builder.getMobSpawnSettings();
            List<Weighted<MobSpawnSettings.SpawnerData>> strays = new ArrayList<>();
            for (MobCategory category : List.copyOf(spawns.getSpawnerTypes())) {
                var list = spawns.getSpawner(category);
                if (list == null) continue;
                list.removeIf(entry -> {
                    if (entry.value().type().getCategory() == category) return false;
                    strays.add(entry);
                    return true;
                });
            }
            for (var entry : strays) spawns.addSpawn(entry.value().type(), entry.value().type().getCategory(), entry.weight(), entry.value().count());
        }

        @Override
        public MapCodec<? extends BiomeModifier> codec() { return CODEC.get(); }
    }

    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, SurvivalContent.MODID);
    public static final Supplier<MapCodec<OwnCategory>> CODEC = TYPES.register("spawns_in_own_category", () -> MapCodec.unit(OwnCategory.INSTANCE));

    public static void register(IEventBus bus) { TYPES.register(bus); }
}
