package dev.qube.spawn_9b5fbb9.engineering;

import dev.qube.spawn_9b5fbb9.physics.Collapse;
import dev.qube.spawn_9b5fbb9.physics.StructuralMaterial;
import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Feeds the physics and engineering engines with what happens in the world. */
@EventBusSubscriber(modid = spawn_9b5fbb9.MODID)
public final class EngineeringEvents {
    private EngineeringEvents() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Engineering.tick(level);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBreak(BreakBlockEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof Level level && !level.isClientSide()) {
            Collapse.disturbed(level, event.getPos());
            Engineering.markDirty(level, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof Level level && !level.isClientSide()) {
            Engineering.markDirty(level, event.getPos());
        }
    }

    @SubscribeEvent
    static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        int n = 0;
        for (BlockPos pos : event.getAffectedBlocks()) {
            Engineering.markDirty(level, pos);
            if (n++ < 1024) {
                Collapse.disturbed(level, pos);
            }
        }
    }

    @SubscribeEvent
    static void onTags(TagsUpdatedEvent event) {
        StructuralMaterial.clearCache();
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        BuildingCommand.register(event.getDispatcher());
    }
}
