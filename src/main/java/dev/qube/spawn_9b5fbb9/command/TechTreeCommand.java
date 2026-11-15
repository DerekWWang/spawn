package dev.qube.spawn_9b5fbb9.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import dev.qube.spawn_9b5fbb9.knowledge.Discovery;
import dev.qube.spawn_9b5fbb9.knowledge.Empirical;
import dev.qube.spawn_9b5fbb9.knowledge.HandWork;
import dev.qube.spawn_9b5fbb9.knowledge.Journal;
import dev.qube.spawn_9b5fbb9.knowledge.KnowledgeData;
import dev.qube.spawn_9b5fbb9.knowledge.Requirements;
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
                                .executes(TechTreeCommand::info)))
                // the manager's own actions, usable by a bot that cannot click the journal
                .then(Commands.literal("plan")
                        .then(Commands.argument("target", StringArgumentType.greedyString()).executes(TechTreeCommand::plan)))
                .then(Commands.literal("status")
                        .then(Commands.argument("function", StringArgumentType.word()).suggests(TechTreeCommand::suggest)
                                .executes(TechTreeCommand::status)))
                .then(Commands.literal("discover")
                        .then(Commands.argument("function", StringArgumentType.word()).suggests(TechTreeCommand::suggest)
                                .executes(ctx -> {
                                    Discovery.start(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "function"));
                                    return 1;
                                })))
                .then(Commands.literal("make")
                        .then(Commands.argument("function", StringArgumentType.word()).suggests(TechTreeCommand::suggest)
                                .executes(ctx -> make(ctx, 1))
                                .then(Commands.argument("times", IntegerArgumentType.integer(1, HandWork.MAX_REPEATS))
                                        .executes(ctx -> make(ctx, IntegerArgumentType.getInteger(ctx, "times"))))))
                .then(Commands.literal("stop").executes(ctx -> {
                    HandWork.cancel(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
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

    private static int make(CommandContext<CommandSourceStack> ctx, int times) throws CommandSyntaxException {
        HandWork.start(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "function"), times);
        return 1;
    }

    /** The route from what the society knows to a function or item, one step per line. */
    private static int plan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TechTree tree = TechTree.get();
        TechTree.FunctionDef target = tree.resolveTarget(StringArgumentType.getString(ctx, "target").trim());
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("No function or item by that name or id"));
            return 0;
        }
        List<TechTree.FunctionDef> route = tree.route(target.id(), Society.known(player));
        if (route.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(target.name() + " is already known."), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("Route to " + target.name() + " (" + route.size() + " steps):");
        int i = 1;
        for (TechTree.FunctionDef f : route) {
            sb.append("\n").append(i++).append(". ").append(f.id()).append(" ").append(f.name())
                    .append(f.theoretical() ? " [theory]" : f.host() != null ? " [at " + tree.stationsById.get(f.host()).name() + "]" : "");
            List<String> needs = new ArrayList<>();
            f.inputs().forEach(r -> needs.add(r.count() > 1 ? r.name() + " x" + r.count() : r.name()));
            f.means().forEach(r -> needs.add(r.name()));
            if (!needs.isEmpty()) {
                sb.append(": ").append(String.join(", ", needs));
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return route.size();
    }

    /** Whether the player could discover (or make) a function where they stand, and what is missing. */
    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TechTree.FunctionDef f = TechTree.get().functionsById.get(StringArgumentType.getString(ctx, "function"));
        if (f == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown function id"));
            return 0;
        }
        boolean known = Society.knows(player, f.id());
        Requirements.Check check = Requirements.player(player, f);
        StringBuilder sb = new StringBuilder(f.id() + " " + f.name() + ": known=" + known + ", ready=" + check.ok());
        for (String m : check.missing()) {
            sb.append("\n missing: ").append(m);
        }
        String note = Empirical.describe(KnowledgeData.get(player.level().getServer()), Society.keyOf(player), f);
        if (note != null) {
            sb.append("\n ").append(note);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return check.ok() ? 1 : 0;
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
