package dev.qube.spawn_9b5fbb9.logistics;

import java.util.Map;

import dev.qube.spawn_9b5fbb9.engineering.Building;
import dev.qube.spawn_9b5fbb9.engineering.BuildingUse;
import dev.qube.spawn_9b5fbb9.engineering.Engineering;
import dev.qube.spawn_9b5fbb9.knowledge.Society;
import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Feeds the logistics engine with what happens in the world. */
@EventBusSubscriber(modid = spawn_9b5fbb9.MODID)
public final class LogisticsEvents {
    private static final int CARRIED_FOOD_INTERVAL = 1200;

    private LogisticsEvents() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        LedgerCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    static void onServerStarting(ServerStartingEvent event) {
        Gestation.clear();
        LedgerCommand.clear();
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        Gestation.clear();
        LedgerCommand.clear();
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Ledger.tick(level);
            Gestation.tick(level);
        }
    }

    /** What is picked up off the ground, other than things a player dropped, was gathered from the world. */
    @SubscribeEvent
    static void onPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        ItemStack original = event.getOriginalStack();
        int taken = original.getCount() - event.getCurrentStack().getCount();
        if (taken <= 0 || event.getItemEntity().getOwner() != null || !Ledger.tracked(original)) {
            return;
        }
        Ledger.flow(player.level().getServer(), Society.keyOf(player), "collected",
                BuiltInRegistries.ITEM.getKey(original.getItem()).toString(), taken, player.getRandom());
    }

    /** Opening a container is when anyone finds out what has gone off in it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || event.getEntity().isSpectator()) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof Container)) {
            return;
        }
        for (Building b : Engineering.in(level)) {
            if (b.use == BuildingUse.STORAGE && b.contains(pos)) {
                Ledger.scanStore(level, b); // ages the whole store, with the losses accounted for
                return;
            }
        }
        Map<String, Integer> spoiled = new java.util.HashMap<>();
        look(level, pos, spoiled);
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = ChestBlock.getConnectedBlockPos(pos, state);
            if (level.isLoaded(other)) {
                look(level, other, spoiled);
            }
        }
        if (!spoiled.isEmpty() && event.getEntity() instanceof ServerPlayer player) {
            int total = 0;
            for (Map.Entry<String, Integer> e : spoiled.entrySet()) {
                total += e.getValue();
                Ledger.flow(level.getServer(), Society.keyOf(player), "spoiled", e.getKey(), e.getValue(), level.getRandom());
            }
            player.sendOverlayMessage(Component.literal(total + " of the food kept here has gone off").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void look(ServerLevel level, BlockPos pos, Map<String, Integer> spoiled) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container c) {
            Spoilage.apply(level, be, c, 1.0, spoiled);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CARRIED_FOOD_INTERVAL != 0) {
            return;
        }
        Map<String, Integer> spoiled = Spoilage.applyToPlayer(player);
        if (spoiled.isEmpty()) {
            return;
        }
        int total = 0;
        for (Map.Entry<String, Integer> e : spoiled.entrySet()) {
            total += e.getValue();
            Ledger.flow(player.level().getServer(), Society.keyOf(player), "spoiled", e.getKey(), e.getValue(), player.getRandom());
        }
        player.sendOverlayMessage(Component.literal(total == 1 ? "Some of the food you are carrying has gone off"
                : total + " of the food you are carrying has gone off").withStyle(ChatFormatting.YELLOW));
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Spoilage.resetClock(player);
        }
    }

    @SubscribeEvent
    static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Spoilage.resetClock(player);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Spoilage.resetClock(player);
        }
    }

    /** Mating starts a pregnancy instead of producing young on the spot. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBreed(BabyEntitySpawnEvent event) {
        if (event.isCanceled() || !(event.getParentA() instanceof Animal a) || !(event.getParentB() instanceof Animal b)
                || !(a.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer cause = event.getCausedByPlayer() instanceof ServerPlayer p ? p : null;
        if (Gestation.conceive(level, a, b, cause)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Animal animal && Gestation.pregnant(animal)) {
            Gestation.track(level, animal);
        }
    }
}
