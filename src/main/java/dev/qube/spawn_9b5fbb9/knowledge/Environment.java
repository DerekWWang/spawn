package dev.qube.spawn_9b5fbb9.knowledge;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/** Checks against the world: phenomena, water and wild animals. All scans stay inside loaded chunks. */
public final class Environment {
    public static final TagKey<Block> FIRE_SOURCES = TagKey.create(Registries.BLOCK, id("fire_sources"));
    public static final TagKey<EntityType<?>> WILD_GAME = TagKey.create(Registries.ENTITY_TYPE, id("wild_game"));
    public static final TagKey<EntityType<?>> WILD_HERD = TagKey.create(Registries.ENTITY_TYPE, id("wild_herd_animals"));
    public static final TagKey<EntityType<?>> WILD_HORSES = TagKey.create(Registries.ENTITY_TYPE, id("wild_horses"));

    private Environment() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, path);
    }

    /** True if the phenomenon can be observed at pos. The player (if any) is the observer. */
    public static boolean phenomenon(String id, Level level, BlockPos pos, @Nullable ServerPlayer observer) {
        return switch (id) {
            case "wildfire" -> wildfire(level, pos);
            case "night_sky" -> hasSky(level) && level.isDarkOutside() && !level.isRaining() && level.canSeeSky(pos.above());
            case "sunlight" -> hasSky(level) && level.isBrightOutside() && level.canSeeSky(pos.above());
            case "seasonal_cycle" -> seasonalCycle(level, observer);
            case "flowing_river" -> countFluid(level, pos, 6, 3, false, 4) >= 4;
            case "wind" -> hasSky(level) && pos.getY() >= level.getSeaLevel() + 10 && level.canSeeSky(pos.above());
            case "open_water" -> countFluid(level, pos, 8, 2, true, 20) >= 20;
            default -> false;
        };
    }

    public static String phenomenonText(String id) {
        return switch (id) {
            case "wildfire" -> "fire or lava burning within 8 blocks";
            case "night_sky" -> "a clear night sky overhead";
            case "sunlight" -> "open sky in daylight";
            case "seasonal_cycle" -> "having lived through " + Config.integer(Config.SEASONAL_CYCLE_DAYS) + " full days";
            case "flowing_river" -> "flowing water (a stream with current) within 6 blocks";
            case "wind" -> "an exposed site at least 10 blocks above sea level, open to the sky";
            case "open_water" -> "open water (at least 20 water blocks) within 8 blocks";
            default -> id;
        };
    }

    private static boolean hasSky(Level level) {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime();
    }

    private static boolean seasonalCycle(Level level, @Nullable ServerPlayer observer) {
        long needed = (long) Config.integer(Config.SEASONAL_CYCLE_DAYS) * 24000L;
        if (observer != null) {
            int played = observer.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
            return played >= needed;
        }
        return level.getGameTime() >= needed;
    }

    private static boolean wildfire(Level level, BlockPos pos) {
        int r = 8;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -r; dy <= r; dy++) {
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!level.isLoaded(m)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(m);
                    if (s.is(FIRE_SOURCES)) {
                        if (!s.hasProperty(BlockStateProperties.LIT) || s.getValue(BlockStateProperties.LIT)) {
                            return true;
                        }
                    }
                    if (s.getFluidState().is(FluidTags.LAVA)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Counts water blocks around pos.
     *
     * @param sources true to count only still sources, false to count only flowing water
     * @param stopAt  stop scanning once this many are found
     */
    public static int countFluid(Level level, BlockPos pos, int radius, int dy, boolean sources, int stopAt) {
        int found = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -dy; y <= dy; y++) {
                    m.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (!level.isLoaded(m)) {
                        continue;
                    }
                    FluidState f = level.getFluidState(m);
                    if (f.is(FluidTags.WATER) && f.isSource() == sources) {
                        if (++found >= stopAt) {
                            return found;
                        }
                    }
                }
            }
        }
        return found;
    }

    public static boolean waterSourceWithin(Level level, AABB box) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = (int) Math.floor(box.minX); x < (int) Math.ceil(box.maxX); x++) {
            for (int z = (int) Math.floor(box.minZ); z < (int) Math.ceil(box.maxZ); z++) {
                for (int y = (int) Math.floor(box.minY); y < (int) Math.ceil(box.maxY); y++) {
                    m.set(x, y, z);
                    if (level.isLoaded(m)) {
                        FluidState f = level.getFluidState(m);
                        if (f.is(FluidTags.WATER) && f.isSource()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static TagKey<EntityType<?>> animalTag(String env) {
        return switch (env) {
            case "wild_game" -> WILD_GAME;
            case "wild_herd_animals" -> WILD_HERD;
            case "wild_horses" -> WILD_HORSES;
            default -> null;
        };
    }

    /** Wild (untamed, unnamed, unleashed, unridden) adults of the given kind near pos. */
    public static List<Animal> wildAnimals(Level level, BlockPos pos, String env, int radius) {
        TagKey<EntityType<?>> tag = animalTag(env);
        if (tag == null) {
            return List.of();
        }
        AABB box = new AABB(pos).inflate(radius);
        return level.getEntitiesOfClass(Animal.class, box, a -> a.isAlive() && a.is(tag) && !a.isBaby()
                && !a.hasCustomName() && !a.isLeashed() && a.getPassengers().isEmpty() && !a.isVehicle()
                && !(a instanceof AbstractHorse h && h.isTamed()));
    }

    public static String envText(String env) {
        return switch (env) {
            case "water" -> "water (stand by a water source, or carry a water bucket or bottle)";
            case "air" -> "air";
            case "wild_game" -> "wild game within 16 blocks";
            case "wild_herd_animals" -> "wild herd animals (cows, sheep, goats...) within 16 blocks";
            case "wild_horses" -> "an untamed horse, donkey, mule or camel within 16 blocks";
            default -> env;
        };
    }
}
