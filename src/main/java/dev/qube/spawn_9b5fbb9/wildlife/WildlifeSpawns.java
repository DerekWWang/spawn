package dev.qube.spawn_9b5fbb9.wildlife;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/** Bears and lions appear as other wild animals do: on open, lit, natural ground. */
@EventBusSubscriber
public final class WildlifeSpawns {
    private WildlifeSpawns() {
    }

    @SubscribeEvent
    static void placements(RegisterSpawnPlacementsEvent event) {
        event.register(BrownBearEntities.BROWN_BEAR, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, WildlifeSpawns::ground, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(LionEntities.LION, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, WildlifeSpawns::ground, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static boolean ground(EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON) && level.getRawBrightness(pos, 0) > 8;
    }
}
