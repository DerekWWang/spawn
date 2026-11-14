package dev.qube.spawn_9b5fbb9.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.qube.spawn_9b5fbb9.knowledge.Journal;
import dev.qube.spawn_9b5fbb9.knowledge.KnowledgeData;
import dev.qube.spawn_9b5fbb9.knowledge.Society;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /techtree grant|revoke &lt;players&gt; &lt;function id|all&gt;, /techtree list [&lt;player&gt;], /techtree info &lt;function id&gt;.
 * Granting or revoking changes the whole society the player belongs to.
 */
public final class TechTreeCommand {
    private TechTreeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("techtree")
                .then(Commands.literal("grant").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("function", StringArgumentType.word()).suggests(TechTreeCommand::suggest)
                                        .executes(ctx -> change(ctx, true)))))
                .then(Commands.literal("revoke").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("function", StringArgumentType.word()).suggests(TechTreeCommand::suggest)
                                        .executes(ctx -> change(ctx, false)))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> list(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.literal("info")
                        .then(Commands.argument("function", StringArgumentType.word()).suggests(TechTreeCommand::suggest)
                                .executes(TechTreeCommand::info))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        List<String> ids = new ArrayList<>();
        ids.add("all");
        TechTree.get().functions.forEach(f -> ids.add(f.id()));
        return SharedSuggestionProvider.suggest(ids, builder);
    }

    private static int change(CommandContext<CommandSourceStack> ctx, boolean grant) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String fid = StringArgumentType.getString(ctx, "function");
        TechTree tree = TechTree.get();
        if (!"all".equalsIgnoreCase(fid) && !tree.functionsById.containsKey(fid)) {
            ctx.getSource().sendFailure(Component.literal("Unknown function id " + fid + " (e.g. P-002 or T-16)"));
            return 0;
        }
        var server = ctx.getSource().getServer();
        KnowledgeData data = KnowledgeData.get(server);
        int changed = 0;
        java.util.Set<String> societies = new java.util.LinkedHashSet<>();
        for (ServerPlayer p : targets) {
            societies.add(Society.keyOf(p));
        }
        for (String society : societies) {
            if ("all".equalsIgnoreCase(fid)) {
                if (grant) {
                    for (TechTree.FunctionDef f : tree.functions) {
                        if (data.grant(society, f.id())) {
                            changed++;
                        }
                    }
                } else {
                    changed += data.knownBy(society).size();
                    data.revokeAll(society);
                }
            } else if (grant ? data.grant(society, fid) : data.revoke(society, fid)) {
                changed++;
            }
            Society.syncSociety(server, society);
        }
        for (ServerPlayer p : targets) {
            Journal.send(p);
        }
        int n = changed;
        ctx.getSource().sendSuccess(() -> Component.literal((grant ? "Granted " : "Revoked ") + n + " function(s) for "
                + societies.size() + " society/societies"), true);
        return n;
    }

    private static int list(CommandSourceStack source, ServerPlayer player) {
        var known = Society.known(player);
        source.sendSuccess(() -> Component.literal(Society.displayName(player) + " knows " + known.size() + " of "
                + TechTree.get().functions.size() + " functions"), false);
        return known.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        String fid = StringArgumentType.getString(ctx, "function");
        TechTree.FunctionDef f = TechTree.get().functionsById.get(fid);
        if (f == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown function id " + fid));
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(f.id()).append(" ").append(f.name()).append(" (").append(f.theoretical() ? "theory" : "practical")
                .append(", era ").append(f.era()).append(", ").append(f.route()).append(")");
        if (!f.inputs().isEmpty()) {
            sb.append("\nConsumes: ").append(refs(f.inputs(), true));
        }
        if (!f.means().isEmpty()) {
            sb.append(f.theoretical() ? "\nEvidence: " : "\nMeans: ").append(refs(f.means(), false));
        }
        if (!f.theory().isEmpty()) {
            List<String> names = new ArrayList<>();
            f.theory().forEach(t -> names.add(TechTree.get().functionsById.get(t).name()));
            sb.append("\nTheory: ").append(String.join(", ", names));
        }
        if (!f.outputs().isEmpty()) {
            sb.append("\nProduces: ").append(refs(f.outputs(), true));
        }
        if (f.host() != null) {
            sb.append("\nPerformed at: ").append(TechTree.get().stationsById.get(f.host()).name());
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static String refs(List<TechTree.Ref> refs, boolean counts) {
        List<String> out = new ArrayList<>();
        for (TechTree.Ref r : refs) {
            out.add(counts && r.count() > 1 ? r.name() + " x" + r.count() : r.name());
        }
        return String.join(", ", out);
    }
}
