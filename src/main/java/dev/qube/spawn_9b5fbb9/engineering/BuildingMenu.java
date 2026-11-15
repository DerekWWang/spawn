package dev.qube.spawn_9b5fbb9.engineering;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The cornerstone's menu: a report in chat whose bracketed words are clickable and run the
 * matching /building command. Everything here can equally be done by typing the command, which is
 * how a bot uses it.
 */
public final class BuildingMenu {
    private static final String[] SIDES = {"left", "right", "down", "up", "front", "back"};

    private BuildingMenu() {
    }

    private static MutableComponent button(String label, String command, String hint) {
        return Component.literal("[" + label + "]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(hint))));
    }

    private static MutableComponent prompt(String label, String command, String hint) {
        return Component.literal("[" + label + "]").withStyle(s -> s.withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent.SuggestCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(hint))));
    }

    public static void send(ServerPlayer player, ServerLevel level, Building b) {
        String at = b.cornerstone.getX() + " " + b.cornerstone.getY() + " " + b.cornerstone.getZ();
        List<String> report = Engineering.report(level, b);
        player.sendSystemMessage(Component.literal("--- " + report.get(0) + " ---").withStyle(ChatFormatting.GOLD));
        for (int i = 1; i < report.size(); i++) {
            String line = report.get(i);
            player.sendSystemMessage(Component.literal(line).withStyle(line.startsWith("problem:") ? ChatFormatting.YELLOW
                    : ChatFormatting.GRAY));
        }
        MutableComponent row = Component.empty()
                .append(button("Survey", "/building survey " + at, "Measure the building again and show its box")).append(" ")
                .append(button("Fit box", "/building fit " + at, "Set the box to everything built that joins the cornerstone")).append(" ");
        BuildingUse next = BuildingUse.values()[(b.use.ordinal() + 1) % BuildingUse.values().length];
        row.append(button("Use: " + b.use.id, "/building use " + at + " " + next.id, b.use.description + " Click for: " + next.id)).append(" ")
                .append(prompt("Name", "/building name " + at + " ", "Rename this building"));
        player.sendSystemMessage(row);
        MutableComponent box = Component.literal("Box ").withStyle(ChatFormatting.GRAY);
        for (int i = 0; i < 6; i++) {
            box.append(Component.literal(SIDES[i] + " " + b.extents[i] + " ").withStyle(ChatFormatting.GRAY))
                    .append(button("-", "/building grow " + at + " " + SIDES[i] + " -1", "Shrink")).append(button("+",
                            "/building grow " + at + " " + SIDES[i] + " 1", "Grow")).append(" ");
        }
        player.sendSystemMessage(box);
        MutableComponent row2 = Component.empty()
                .append(prompt("Save as schema", "/building schema save " + at + " ", "Record this design so it can be built again")).append(" ")
                .append(prompt("Build from schema", "/building plan " + at + " ", "Turn this cornerstone into a site for a saved design"))
                .append(" ");
        if (b.planned()) {
            row2.append(button("Missing blocks", "/building missing " + at, "List what the site still needs")).append(" ");
        }
        if (b.use == BuildingUse.STORAGE) {
            row2.append(button("Take stock", "/ledger stocktake " + at, "Count what is really here and correct the books"));
        }
        player.sendSystemMessage(row2);
    }
}
