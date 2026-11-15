package dev.qube.spawn_9b5fbb9.knowledge;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import dev.qube.spawn_9b5fbb9.spawn_9b5fbb9;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Prospecting "reveals hidden ore deposits": a survey of the ground beneath the player. */
public final class Prospecting {
    public static final String PROSPECTING = "T-18";
    private static final int RADIUS = 12;
    private static final int DEPTH = 96;
    private static final int COOLDOWN_TICKS = 20 * 30;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private Prospecting() {
    }

    public static void clear() {
        LAST.clear();
    }

    public static void survey(ServerPlayer player) {
        if (!Society.knows(player, PROSPECTING)) {
            player.sendOverlayMessage(Component.literal("Your society does not know Prospecting yet."));
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS && now >= last) {
            player.sendOverlayMessage(Component.literal("You are still writing up the last survey."));
            return;
        }
        LAST.put(player.getUUID(), now);

        Map<Block, TechTree.OreDef> ours = new HashMap<>();
        for (TechTree.OreDef o : TechTree.get().ores) {
            Block b = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(spawn_9b5fbb9.MODID, o.block()));
            ours.put(b, o);
        }
        Map<String, int[]> found = new LinkedHashMap<>(); // name -> {count, minY, maxY}
        BlockPos origin = player.blockPosition();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bottom = Math.max(level.getMinY(), origin.getY() - DEPTH);
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                for (int y = origin.getY(); y >= bottom; y--) {
                    m.set(origin.getX() + x, y, origin.getZ() + z);
                    if (!level.isLoaded(m)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(m);
                    String name = null;
                    TechTree.OreDef o = ours.get(s.getBlock());
                    if (o != null) {
                        boolean recognised = o.recognisedBy() == null || Society.knows(player, o.recognisedBy());
                        name = recognised ? o.name() : "Unfamiliar coloured rock";
                    } else if (s.is(net.minecraft.tags.BlockItemTags.COAL_ORES.block())) {
                        name = "Coal seam";
                    } else if (s.is(BlockTags.IRON_ORES)) {
                        name = Society.knows(player, "T-16") ? "Iron ore" : "Unfamiliar coloured rock";
                    } else if (s.is(BlockTags.COPPER_ORES)) {
                        name = "Copper ore";
                    } else if (s.is(BlockTags.GOLD_ORES)) {
                        name = "Gold ore";
                    }
                    if (name != null) {
                        int[] e = found.computeIfAbsent(name, k -> new int[]{0, Integer.MAX_VALUE, Integer.MIN_VALUE});
                        e[0]++;
                        e[1] = Math.min(e[1], y);
                        e[2] = Math.max(e[2], y);
                    }
                }
            }
        }
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal("Survey: nothing worth digging for within " + RADIUS + " blocks and "
                    + DEPTH + " deep.").withStyle(ChatFormatting.GRAY));
            return;
        }
        player.sendSystemMessage(Component.literal("Survey of the ground within " + RADIUS + " blocks:").withStyle(ChatFormatting.GOLD));
        found.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0])).limit(10).forEach(e ->
                player.sendSystemMessage(Component.literal(" - " + e.getKey() + ": " + e.getValue()[0] + " blocks, y "
                        + e.getValue()[1] + " to " + e.getValue()[2]).withStyle(ChatFormatting.GRAY)));
    }
}
