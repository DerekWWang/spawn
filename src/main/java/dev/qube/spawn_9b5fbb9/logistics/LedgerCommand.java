package dev.qube.spawn_9b5fbb9.logistics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import dev.qube.spawn_9b5fbb9.engineering.Building;
import dev.qube.spawn_9b5fbb9.engineering.BuildingCommand;
import dev.qube.spawn_9b5fbb9.knowledge.Society;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * /ledger: the society's books, for players and for bots.
 *
 * <ul>
 * <li>report: the books as recorded (people, livestock, each store, flows)</li>
 * <li>census: count people and animals now</li>
 * <li>stocktake &lt;cornerstone&gt;: count a store and correct its books</li>
 * <li>get &lt;metric&gt;: one recorded figure, as the command's result</li>
 * <li>truth, history: the real figures beside the recorded ones (operators only)</li>
 * </ul>
 */
public final class LedgerCommand {
    /** Counting takes time: one census, and one count of each store, per game hour. */
    private static final long COUNT_INTERVAL = 1000;
    private static final int MAX_LINES = 120;
    private static final Map<String, Long> LAST_COUNT = new HashMap<>();

    private static final SuggestionProvider<CommandSourceStack> METRICS = (ctx, b) -> {
        ServerPlayer p = ctx.getSource().getPlayer();
        return SharedSuggestionProvider.suggest(p == null ? List.<String>of()
                : Ledger.totals(Ledger.books(ctx.getSource().getServer(), Society.keyOf(p)), true).keySet(), b);
    };

    private LedgerCommand() {
    }

    public static void clear() {
        LAST_COUNT.clear();
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ledger")
                .executes(LedgerCommand::report)
                .then(Commands.literal("report").executes(LedgerCommand::report))
                .then(Commands.literal("census").executes(LedgerCommand::census))
                .then(Commands.literal("stocktake").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(LedgerCommand::stocktake)))
                .then(Commands.literal("get").then(Commands.argument("metric", StringArgumentType.greedyString()).suggests(METRICS)
                        .executes(LedgerCommand::get)))
                .then(Commands.literal("truth").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> truth(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> truth(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("history").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> history(ctx, 3))
                        .then(Commands.argument("days", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> history(ctx, IntegerArgumentType.getInteger(ctx, "days"))))));
    }

    private static void say(CommandContext<CommandSourceStack> ctx, List<String> lines) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < shown; i++) {
            sb.append(i > 0 ? "\n" : "").append(lines.get(i));
        }
        if (lines.size() > shown) {
            sb.append("\n... and ").append(lines.size() - shown).append(" more lines");
        }
        String text = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }

    private static boolean tooSoon(CommandContext<CommandSourceStack> ctx, String what, String message) {
        long now = ctx.getSource().getServer().overworld().getGameTime();
        Long last = LAST_COUNT.get(what);
        if (last != null && now >= last && now - last < COUNT_INTERVAL) {
            ctx.getSource().sendFailure(Component.literal(message).withStyle(ChatFormatting.YELLOW));
            return true;
        }
        LAST_COUNT.put(what, now);
        return false;
    }

    private static int report(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<String> lines = Ledger.report(ctx.getSource().getServer(), Society.keyOf(player));
        say(ctx, lines);
        return lines.size();
    }

    private static int census(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String society = Society.keyOf(player);
        if (tooSoon(ctx, "census/" + society, "A census was taken less than an hour ago.")) {
            return 0;
        }
        Ledger.census(ctx.getSource().getLevel(), society);
        List<String> lines = Ledger.report(ctx.getSource().getServer(), society);
        say(ctx, lines.subList(0, Math.min(4, lines.size())));
        return 1;
    }

    private static int stocktake(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Building b = BuildingCommand.owned(ctx);
        if (b == null) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null && !b.bounds().inflate(8).contains(player.position())) {
            ctx.getSource().sendFailure(Component.literal("You have to be at " + b.name + " to count what is in it."));
            return 0;
        }
        if (tooSoon(ctx, "stocktake/" + b.id, b.name + " was counted less than an hour ago.")) {
            return 0;
        }
        String result = Ledger.stocktake(ctx.getSource().getLevel(), b, player);
        ctx.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String metric = StringArgumentType.getString(ctx, "metric").trim();
        MinecraftServer server = ctx.getSource().getServer();
        Map<String, Double> totals = Ledger.totals(Ledger.books(server, Society.keyOf(player)), true);
        long value = Math.round(totals.getOrDefault(metric, 0.0));
        ctx.getSource().sendSuccess(() -> Component.literal(metric + " = " + value + " (recorded)"), false);
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    private static int truth(CommandContext<CommandSourceStack> ctx, ServerPlayer of) {
        List<String> lines = Ledger.truth(ctx.getSource().getServer(), Society.keyOf(of));
        say(ctx, lines);
        return lines.size() - 1;
    }

    private static int history(CommandContext<CommandSourceStack> ctx, int days) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<String> lines = Ledger.history(ctx.getSource().getServer(), Society.keyOf(player), days);
        say(ctx, lines);
        return lines.size() - 1;
    }
}
