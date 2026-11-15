package dev.qube.spawn_9b5fbb9.client;

import java.util.ArrayList;
import java.util.List;

import dev.qube.spawn_9b5fbb9.station.StationBlock;
import dev.qube.spawn_9b5fbb9.station.StationBlockEntity;
import dev.qube.spawn_9b5fbb9.station.StationMenu;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import dev.qube.spawn_9b5fbb9.techtree.TechTree.FunctionDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** The screen of a station: pick a known function on the left, load inputs, tools and fuel, collect outputs. */
public class StationScreen extends AbstractContainerScreen<StationMenu> {
    private static final int BG = 0xFF2B2520;
    private static final int PANEL = 0xFF3A322A;
    private static final int SLOT = 0xFF1C1814;
    private static final int TEXT = 0xFFE8DCC4;
    private static final int DIM = 0xFF9A8D78;
    private static final int LIST_X = 6;
    private static final int LIST_Y = 16;
    private static final int LIST_W = 124;
    private static final int LIST_H = 106;
    private static final int ROW_H = 11;

    private List<FunctionDef> functions = new ArrayList<>();
    private int scroll;
    private int seenVersion = -1;

    public StationScreen(StationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, StationMenu.WIDTH, StationMenu.HEIGHT);
        this.inventoryLabelX = StationMenu.INV_X;
        this.inventoryLabelY = StationMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        List<FunctionDef> list = new ArrayList<>();
        for (FunctionDef f : TechTree.get().hostedBy(menu.stationId())) {
            if (f.mode() == TechTree.Mode.STATION && ClientState.knows(f.id())) {
                list.add(f);
            }
        }
        functions = list;
        scroll = Math.max(0, Math.min(scroll, functions.size() - LIST_H / ROW_H));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (seenVersion != ClientState.version) {
            seenVersion = ClientState.version;
            rebuild();
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x() - leftPos;
        double my = event.y() - topPos;
        if (mx >= LIST_X && mx < LIST_X + LIST_W && my >= LIST_Y && my < LIST_Y + LIST_H) {
            int idx = scroll + (int) ((my - LIST_Y) / ROW_H);
            if (idx >= 0 && idx < functions.size()) {
                click(functions.get(idx).index());
                return true;
            }
        }
        if (mx >= LIST_X && mx < LIST_X + 40 && my >= LIST_Y + LIST_H + 2 && my < LIST_Y + LIST_H + 14) {
            click(StationMenu.CLEAR_SELECTION);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        double mx = x - leftPos;
        double my = y - topPos;
        if (mx >= LIST_X && mx < LIST_X + LIST_W && my >= LIST_Y && my < LIST_Y + LIST_H) {
            int max = Math.max(0, functions.size() - LIST_H / ROW_H);
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        g.outline(x, y, imageWidth, imageHeight, 0xFF8A6D45);
        g.fill(x + LIST_X - 2, y + LIST_Y - 2, x + LIST_X + LIST_W + 2, y + LIST_Y + LIST_H + 2, PANEL);
        g.fill(x + StationMenu.INPUT_X - 2, y + 76, x + imageWidth - 6, y + 132, PANEL);
        for (Slot slot : menu.slots) {
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, SLOT);
            g.outline(x + slot.x - 1, y + slot.y - 1, 18, 18, 0xFF5A4E40);
        }
        // progress arrow
        int max = menu.data(StationBlockEntity.DATA_MAX_PROGRESS);
        int progress = menu.data(StationBlockEntity.DATA_PROGRESS);
        int ax = x + 274;
        int ay = y + 38;
        g.fill(ax, ay, ax + 14, ay + 6, 0xFF1C1814);
        if (max > 0 && menu.data(StationBlockEntity.DATA_RUNNING) > 0) {
            g.fill(ax, ay, ax + Math.min(14, 14 * progress / max), ay + 6,
                    menu.data(StationBlockEntity.DATA_RUNNING) == 2 ? 0xFFE8C25A : 0xFF7FD46A);
        }
        // fuel flame
        TechTree.StationDef def = menu.def();
        if (def != null && (def.fuelTier() > 0)) {
            int burn = menu.data(StationBlockEntity.DATA_BURN);
            int total = Math.max(1, menu.data(StationBlockEntity.DATA_BURN_TOTAL));
            int fx = x + StationMenu.FUEL_X + 20;
            int fy = y + StationMenu.FUEL_Y;
            g.fill(fx, fy, fx + 4, fy + 16, 0xFF1C1814);
            int hgt = burn > 0 ? Math.max(1, 16 * burn / total) : 0;
            g.fill(fx, fy + 16 - hgt, fx + 4, fy + 16, 0xFFE08A3A);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 5, TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, DIM, false);
        g.text(font, "Inputs", StationMenu.INPUT_X, 8, DIM, false);
        g.text(font, "Tools", StationMenu.TOOL_X, 8, DIM, false);
        g.text(font, "Out", StationMenu.OUTPUT_X, 8, DIM, false);
        TechTree.StationDef def = menu.def();
        if (def != null && def.fuelTier() > 0) {
            g.text(font, "Fuel", StationMenu.FUEL_X + 27, StationMenu.FUEL_Y + 4, DIM, false);
        }
        int selected = menu.data(StationBlockEntity.DATA_SELECTED);
        int rows = LIST_H / ROW_H;
        if (functions.isEmpty()) {
            int y = LIST_Y + 2;
            String msg = def != null && def.hosts().isEmpty()
                    ? "Nothing is made here directly." : "Your society knows nothing that is made here yet.";
            for (FormattedCharSequence seq : font.split(FormattedText.of(msg), LIST_W - 4)) {
                g.text(font, seq, LIST_X + 2, y, DIM, false);
                y += font.lineHeight;
            }
        }
        int mx = mouseX - leftPos;
        int my = mouseY - topPos;
        for (int i = 0; i < rows && scroll + i < functions.size(); i++) {
            FunctionDef f = functions.get(scroll + i);
            int ry = LIST_Y + i * ROW_H;
            if (f.index() == selected) {
                g.fill(LIST_X - 1, ry - 1, LIST_X + LIST_W + 1, ry + ROW_H - 1, 0xFF5A4A36);
            } else if (mx >= LIST_X && mx < LIST_X + LIST_W && my >= ry && my < ry + ROW_H) {
                g.fill(LIST_X - 1, ry - 1, LIST_X + LIST_W + 1, ry + ROW_H - 1, 0xFF463A2E);
            }
            g.text(font, font.plainSubstrByWidth(f.name(), LIST_W - 4), LIST_X + 2, ry + 1, TEXT, false);
        }
        g.text(font, "[clear]", LIST_X, LIST_Y + LIST_H + 4, selected >= 0 ? 0xFFE8C25A : 0xFF6A5E50, false);

        // status
        int sy = 79;
        List<String> lines = ClientState.stationContainer == menu.containerId ? ClientState.stationStatus : List.of();
        FunctionDef sel = TechTree.get().function(selected);
        List<String> all = new ArrayList<>();
        if (sel != null) {
            all.add("§6" + sel.name() + "§r: " + summary(sel));
        }
        all.addAll(lines);
        int width = imageWidth - StationMenu.INPUT_X - 8;
        for (String line : all) {
            for (FormattedCharSequence seq : font.split(FormattedText.of(line), width)) {
                if (sy > 124) {
                    break;
                }
                g.text(font, seq, StationMenu.INPUT_X, sy, TEXT, false);
                sy += font.lineHeight;
            }
        }
    }

    private static String summary(FunctionDef f) {
        List<String> parts = new ArrayList<>();
        for (TechTree.Ref r : f.inputs()) {
            parts.add(r.count() > 1 ? r.name() + " x" + r.count() : r.name());
        }
        String s = String.join(", ", parts);
        List<String> outs = new ArrayList<>();
        for (TechTree.Ref r : f.outputs()) {
            outs.add(r.count() > 1 ? r.name() + " x" + r.count() : r.name());
        }
        return s + " -> " + String.join(", ", outs);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        int mx = mouseX - leftPos;
        int my = mouseY - topPos;
        if (mx >= LIST_X && mx < LIST_X + LIST_W && my >= LIST_Y && my < LIST_Y + LIST_H) {
            int idx = scroll + (my - LIST_Y) / ROW_H;
            if (idx >= 0 && idx < functions.size()) {
                g.setComponentTooltipForNextFrame(font, tooltip(functions.get(idx)), mouseX, mouseY);
            }
        } else if (mx >= 0 && mx < 120 && my >= 2 && my < 14) {
            TechTree.StationDef def = menu.def();
            if (def != null) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(def.name()));
                StationBlock.describe(def, s -> lines.add(Component.literal(s)));
                g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
        }
    }

    private static List<Component> tooltip(FunctionDef f) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(f.name()));
        for (TechTree.Ref r : f.inputs()) {
            lines.add(Component.literal(" in: " + r.name() + (r.count() > 1 ? " x" + r.count() : "")));
        }
        for (TechTree.Ref r : f.means()) {
            String kind = r.kind() == TechTree.RefKind.STATION ? "nearby" : r.kind() == TechTree.RefKind.PHEN ? "site" : "tool";
            lines.add(Component.literal(" " + kind + ": " + r.name()));
        }
        for (TechTree.Ref r : f.outputs()) {
            lines.add(Component.literal(" out: " + r.name() + (r.count() > 1 ? " x" + r.count() : "")));
        }
        lines.add(Component.literal(" " + Math.round(f.ticks() / 20.0) + "s per run"));
        return lines;
    }
}
