package dev.qube.spawn_9b5fbb9.engineering;

import java.util.List;
import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import dev.qube.spawn_9b5fbb9.knowledge.Society;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * /building: everything the cornerstone menu does, by command. A building is named by the
 * position of its cornerstone. Players act on their own society's buildings; operators on any.
 */
public final class BuildingCommand {
    private static final List<String> SIDES = List.of("left", "right", "down", "up", "front", "back");
    private static final SuggestionProvider<CommandSourceStack> USES = (ctx, b) -> SharedSuggestionProvider.suggest(
            java.util.Arrays.stream(BuildingUse.values()).map(u -> u.id), b);
    private static final SuggestionProvider<CommandSourceStack> SIDE = (ctx, b) -> SharedSuggestionProvider.suggest(SIDES, b);
    private static final SuggestionProvider<CommandSourceStack> SCHEMAS = (ctx, b) -> {
        ServerPlayer p = ctx.getSource().getPlayer();
        return SharedSuggestionProvider.suggest(p == null ? List.<String>of()
                : EngineeringData.get(ctx.getSource().getServer()).schemasOf(Society.keyOf(p)).keySet(), b);
    };

    private BuildingCommand() {
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> pos() {
        return Commands.argument("pos", BlockPosArgument.blockPos());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("building")
                .then(Commands.literal("list").executes(BuildingCommand::list))
                .then(Commands.literal("info").then(pos().executes(ctx -> show(ctx, false))))
                .then(Commands.literal("survey").then(pos().executes(ctx -> show(ctx, true))))
                .then(Commands.literal("fit").then(pos().executes(BuildingCommand::fit)))
                .then(Commands.literal("use").then(pos().then(Commands.argument("use", StringArgumentType.word()).suggests(USES)
                        .executes(BuildingCommand::use))))
                .then(Commands.literal("name").then(pos().then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(BuildingCommand::name))))
                .then(Commands.literal("grow").then(pos().then(Commands.argument("side", StringArgumentType.word()).suggests(SIDE)
                        .then(Commands.argument("by", IntegerArgumentType.integer(-63, 63)).executes(BuildingCommand::grow)))))
                .then(Commands.literal("extent").then(pos()
                        .then(Commands.argument("left", IntegerArgumentType.integer(0, 63))
                                .then(Commands.argument("right", IntegerArgumentType.integer(0, 63))
                                        .then(Commands.argument("down", IntegerArgumentType.integer(0, 63))
                                                .then(Commands.argument("up", IntegerArgumentType.integer(0, 63))
                                                        .then(Commands.argument("front", IntegerArgumentType.integer(0, 63))
                                                                .then(Commands.argument("back", IntegerArgumentType.integer(0, 63))
                                                                        .executes(BuildingCommand::extent)))))))))
                .then(Commands.literal("plan").then(pos().then(Commands.argument("schema", StringArgumentType.word()).suggests(SCHEMAS)
                        .executes(BuildingCommand::plan))))
                .then(Commands.literal("unplan").then(pos().executes(BuildingCommand::unplan)))
                .then(Commands.literal("missing").then(pos().executes(ctx -> missing(ctx, 16))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 256))
                                .executes(ctx -> missing(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("construct").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(pos().executes(BuildingCommand::construct)))
                .then(Commands.literal("schema")
                        .then(Commands.literal("list").executes(BuildingCommand::schemaList))
                        .then(Commands.literal("save").then(pos().then(Commands.argument("schema", StringArgumentType.word())
                                .executes(BuildingCommand::schemaSave))))
                        .then(Commands.literal("info").then(Commands.argument("schema", StringArgumentType.word()).suggests(SCHEMAS)
                                .executes(BuildingCommand::schemaInfo)))
                        .then(Commands.literal("delete").then(Commands.argument("schema", StringArgumentType.word()).suggests(SCHEMAS)
                                .executes(BuildingCommand::schemaDelete)))));
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }

    /** The building at the given cornerstone, if the caller may manage it; otherwise reports why not and returns null. */
    public static Building owned(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        Building b = Engineering.at(level, pos);
        if (b == null) {
            ctx.getSource().sendFailure(Component.literal("There is no cornerstone at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
            return null;
        }
        ServerPlayer player = ctx.getSource().getPlayer();
        boolean operator = Commands.LEVEL_GAMEMASTERS.check(ctx.getSource().permissions());
        if (!operator && (player == null || !Society.keyOf(player).equals(b.society))) {
            ctx.getSource().sendFailure(Component.literal(b.name + " belongs to another society"));
            return null;
        }
        return b;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<Building> mine = Engineering.of(ctx.getSource().getServer(), Society.keyOf(player));
        if (mine.isEmpty()) {
            say(ctx, "Your society has no registered buildings. Place a cornerstone in one.");
            return 0;
        }
        StringBuilder sb = new StringBuilder("Buildings of " + Society.displayName(player) + ":");
        for (Building b : mine) {
            sb.append("\n ").append(b.id).append(" ").append(b.name).append(" use=").append(b.use.id).append(" at ")
                    .append(b.cornerstone.getX()).append(" ").append(b.cornerstone.getY()).append(" ").append(b.cornerstone.getZ())
                    .append(" in ").append(b.dimension.identifier());
        }
        say(ctx, sb.toString());
        return mine.size();
    }

    private static int show(CommandContext<CommandSourceStack> ctx, boolean survey) throws CommandSyntaxException {
        Building b = owned(ctx);
        if (b == null) {
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        ServerPlayer player = ctx.getSource().getPlayer();
        if (survey) {
            Engineering.assess(level, b);
            if (player != null) {
                Engineering.outline(level, b, player);
            }
        }
        if (player != null) {
            BuildingMenu.send(player, level, b);
        } else {
            say(ctx, String.join("\n", Engineering.report(level, b)));
        }
        Assessment a = b.assessment;
        return a != null && a.functional ? 1 : 0;
    }

    private static int refresh(CommandContext<CommandSourceStack> ctx, Building b) {
        ServerLevel level = ctx.getSource().getLevel();
        String problem = Engineering.checkSize(b);
        if (problem != null) {
            ctx.getSource().sendFailure(Component.literal(problem));
        }
        Engineering.changed(level);
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null) {
            Engineering.outline(level, b, player);
            BuildingMenu.send(player, level, b);
        }
        return 1;
    }

    private static int fit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Building b = owned(ctx);
        if (b == null) {
            return 0;
        }
        int found = Engineering.fit(ctx.getSource().getLevel(), b);
        refresh(ctx, b);
        return found;
    }

    private static int use(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Building b = owned(ctx);
        if (b == null) {
            return 0;
        }
        BuildingUse u = BuildingUse.byId(StringArgumentType.getString(ctx, "use"));
        if (u == null) {
            ctx.getSource().sendFailure(Component.literal("Uses: structure, residence, storage, pen, barrier"));
            return 0;
        }
        b.use = u;
        b.dirty = true;
        return refresh(ctx, b);
    }

    private static int name(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Building b = owned(ctx);
        if (b == null) {
            return 0;
        }
        String n = StringArgumentType.getString(ctx, "name").trim();
        if (n.isEmpty() || n.length() > 40) {
            ctx.getSource().sendFailure(Component.literal("Names are 1 to 40 characters"));
            return 0;
        }
        b.name = n;
        return refresh(ctx, b);
    }

    private static int grow(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Building b = owned(ctx);
        if (b == null) {
            return 0;
        }
        int side = SIDES.indexOf(StringArgumentType.getString(ctx, "side"));
        if (side < 0) {
            ctx.getSource().sendFailure(Component.literal("Sides: " + String.join(", ", SIDES)));
            return 0;
        }
        int[] e = b.extents.clone();
        e[side] = Math.max(0, e[side] + IntegerArgumentType.getInteger(ctx, "by"));
        return resize(ctx, b, e);
    }

    private static int extent(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Building b = owned(ctx);
        if (b == null) {
            return 0;
        }
        int[] e = new int[6];
        for (int i = 0; i < 6; i++) {
            e[i] = IntegerArgumentType.getInteger(ctx, SIDES.get(i));
        }
        return resize(ctx, b, e);
    }

    private static int resize(CommandContext<CommandSourceStack> ctx, Building b, int[] e) {
        int[] before = b.extents.clone();
        b.setExtents(e[0], e[1], e[2], e[3], e[4], e[5]);
        if (Engineering.checkSize(b) != null) {
            String why = Engineering.checkSize(b);
            b.setExtents(before[0], before[1], before[2], before[3], before[4], before[5]);
            ctx.getSource().sendFailure(Component.literal(why));
            return 0;
        }
        if (b.planned()) {
            b.schema = ""; // a resized box no longer matches the design
        }
        return refresh(ctx, b);
    }

    private static int plan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Building b = owned(ctx);
        if (b == null) {
            return 0;
        }
        String error = Engineering.plan(ctx.getSource().getLevel(), b, StringArgumentType.getString(ctx, "schema"));
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        return refresh(ctx, b);
    }

    private static int unplan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Building b = owned(ctx);
        if (b == null) {
            return 0;
        }
        b.schema = "";
        return refresh(ctx, b);
    }

    private static int missing(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        Building b = owned(ctx);
        if (b == null) {
            return 0;
        }
        if (!b.planned()) {
            ctx.getSource().sendFailure(Component.literal(b.name + " is not being built from a schema"));
            return 0;
        }
        List<Engineering.Missing> list = Engineering.missing(ctx.getSource().getLevel(), b);
        if (list.isEmpty()) {
            say(ctx, b.name + " is complete: every block of " + b.schema + " is in place.");
            return 0;
        }
        StringBuilder sb = new StringBuilder(b.name + " still needs " + list.size() + " block(s):");
        Engineering.billOfMaterials(list).forEach((item, n) -> sb.append("\n need ").append(item).append(" x").append(n));
        for (int i = 0; i < Math.min(count, list.size()); i++) {
            Engineering.Missing m = list.get(i);
            sb.append("\n place ").append(BuiltInRegistries.BLOCK.getKey(m.state().getBlock())).append(" at ").append(m.pos().getX())
                    .append(" ").append(m.pos().getY()).append(" ").append(m.pos().getZ());
        }
        say(ctx, sb.toString());
        return list.size();
    }

    private static int construct(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Building b = owned(ctx);
        if (b == null) {
            return 0;
        }
        int placed = Engineering.construct(ctx.getSource().getLevel(), b);
        say(ctx, "Placed " + placed + " block(s) of " + b.name);
        return placed;
    }

    private static int schemaList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Map<String, Schema> map = EngineeringData.get(ctx.getSource().getServer()).schemasOf(Society.keyOf(player));
        if (map.isEmpty()) {
            say(ctx, "No schemas saved. Survey a building and save it as a schema to reuse the design.");
            return 0;
        }
        StringBuilder sb = new StringBuilder("Schemas:");
        map.values().forEach(s -> sb.append("\n ").append(s.name()).append(" (").append(s.use()).append(", ").append(s.sx()).append("x")
                .append(s.sy()).append("x").append(s.sz()).append(", ").append(s.blocks()).append(" blocks)"));
        say(ctx, sb.toString());
        return map.size();
    }

    private static int schemaSave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Building b = owned(ctx);
        if (b == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "schema");
        String error = Engineering.saveSchema(ctx.getSource().getLevel(), b, name);
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        Schema s = Engineering.schema(ctx.getSource().getLevel(), b.society, name);
        say(ctx, "Saved schema " + name + ": " + s.blocks() + " blocks, " + s.materials().size() + " kinds of material.");
        return s.blocks();
    }

    private static int schemaInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Schema s = Engineering.schema(ctx.getSource().getLevel(), Society.keyOf(player), StringArgumentType.getString(ctx, "schema"));
        if (s == null) {
            ctx.getSource().sendFailure(Component.literal("No such schema"));
            return 0;
        }
        StringBuilder sb = new StringBuilder("Schema " + s.name() + ": use=" + s.use() + " size=" + s.sx() + "x" + s.sy() + "x" + s.sz()
                + " blocks=" + s.blocks());
        s.materials().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append("\n need ").append(e.getKey()).append(" x").append(e.getValue()));
        say(ctx, sb.toString());
        return s.blocks();
    }

    private static int schemaDelete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "schema");
        if (!Engineering.deleteSchema(ctx.getSource().getLevel(), Society.keyOf(player), name)) {
            ctx.getSource().sendFailure(Component.literal("No such schema"));
            return 0;
        }
        say(ctx, "Deleted schema " + name);
        return 1;
    }
}
