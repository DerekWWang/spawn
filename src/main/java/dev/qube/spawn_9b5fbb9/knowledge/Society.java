package dev.qube.spawn_9b5fbb9.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.qube.spawn_9b5fbb9.Config;
import dev.qube.spawn_9b5fbb9.network.Net;
import dev.qube.spawn_9b5fbb9.network.Payloads;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

/**
 * A society is the unit that owns knowledge: a scoreboard team (when team sharing is on)
 * or a single player. Keys are "team:&lt;name&gt;" or "player:&lt;uuid&gt;".
 */
public final class Society {
    private Society() {
    }

    public static String keyOf(ServerPlayer player) {
        PlayerTeam team = player.getTeam();
        if (team != null && Config.bool(Config.SHARE_WITH_TEAM)) {
            return "team:" + team.getName();
        }
        return "player:" + player.getUUID();
    }

    public static String displayName(ServerPlayer player) {
        PlayerTeam team = player.getTeam();
        if (team != null && Config.bool(Config.SHARE_WITH_TEAM)) {
            return team.getDisplayName().getString();
        }
        return player.getGameProfile().name();
    }

    public static boolean knows(ServerPlayer player, String functionId) {
        return KnowledgeData.get(player.level().getServer()).knows(keyOf(player), functionId);
    }

    public static Set<String> known(ServerPlayer player) {
        return KnowledgeData.get(player.level().getServer()).knownBy(keyOf(player));
    }

    public static List<ServerPlayer> onlineMembers(MinecraftServer server, String society) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (keyOf(p).equals(society)) {
                out.add(p);
            }
        }
        return out;
    }

    public static void sync(ServerPlayer player) {
        List<String> ids = List.copyOf(known(player));
        Net.send(player, new Payloads.KnowledgeSync(displayName(player), ids));
    }

    public static void syncSociety(MinecraftServer server, String society) {
        for (ServerPlayer p : onlineMembers(server, society)) {
            sync(p);
        }
    }

    /**
     * Records a discovery and tells every online member.
     *
     * @return true if it was new
     */
    public static boolean grant(MinecraftServer server, String society, String functionId, @Nullable ServerPlayer by) {
        TechTree.FunctionDef f = TechTree.get().functionsById.get(functionId);
        if (f == null) {
            return false;
        }
        boolean added = KnowledgeData.get(server).grant(society, functionId);
        if (added) {
            Component msg = Component.literal((by != null ? by.getGameProfile().name() + " discovered " : "Your society now knows ")
                            + (f.theoretical() ? "the theory of " : "how to perform ") + f.name() + ".")
                    .withStyle(f.theoretical() ? ChatFormatting.AQUA : ChatFormatting.GREEN);
            for (ServerPlayer p : onlineMembers(server, society)) {
                p.sendSystemMessage(msg);
            }
            syncSociety(server, society);
        }
        return added;
    }

    /**
     * Notices team changes. Knowledge travels with a player: whatever their old society knew
     * is merged into the new one.
     */
    public static void checkMembership(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        KnowledgeData data = KnowledgeData.get(server);
        String uuid = player.getUUID().toString();
        String now = keyOf(player);
        String before = data.lastSociety(uuid);
        if (now.equals(before)) {
            return;
        }
        if (before != null) {
            data.merge(before, now);
        }
        data.setLastSociety(uuid, now);
        syncSociety(server, now);
    }
}
