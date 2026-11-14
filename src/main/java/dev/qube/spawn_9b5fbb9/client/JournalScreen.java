package dev.qube.spawn_9b5fbb9.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.qube.spawn_9b5fbb9.network.Payloads;
import dev.qube.spawn_9b5fbb9.station.StationBlock;
import dev.qube.spawn_9b5fbb9.techtree.TechTree;
import dev.qube.spawn_9b5fbb9.techtree.TechTree.FunctionDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The society's journal: what can be discovered next, what is known, and what each function needs.
 * Discovery happens here: hold the inputs and means of production, then pick the right mechanism.
 */
public class JournalScreen extends Screen {
    private static final int BG = 0xF0201A14;
    private static final int PANEL = 0xFF2E261D;
    private static final int BORDER = 0xFF8A6D45;
    private static final int TEXT = 0xFFE8DCC4;
    private static final int DIM = 0xFF9A8D78;
    private static final int GOOD = 0xFF7FD46A;
    private static final int WARN = 0xFFE8C25A;
    private static final int BAD = 0xFFE07060;
    private static final int THEORY = 0xFF7EC8E3;
    private static final int ROW_H = 11;

    private enum Filter { READY, FRONTIER, KNOWN, ALL }

    private Filter filter = Filter.FRONTIER;
    private String search = "";
    private List<FunctionDef> rows = new ArrayList<>();
    private int scroll;
    private int detailScroll;
    private int maxDetailScroll;
    private int quizScroll;
    private int quizMaxScroll;
    private Payloads.Quiz shownQuiz;
    private @Nullable FunctionDef selected;
    private int seenVersion = -1;

    private int left;
    private int top;
    private int w;
    private int h;
    private int listW;

    private EditBox searchBox;
    private Button discoverBtn;
    private Button make1Btn;
    private Button make8Btn;
    private Button cancelBtn;
    private Button treatiseBtn;
    private Button surveyBtn;
    private final List<Button> filterBtns = new ArrayList<>();

    public JournalScreen() {
        this(null);
    }

    public JournalScreen(@Nullable String focus) {
        super(Component.literal("Journal"));
        if (focus != null) {
            this.selected = TechTree.get().functionsById.get(focus);
        }
    }

    @Override
    protected void init() {
        w = Math.min(width - 16, 460);
        h = Math.min(height - 16, 290);
        left = (width - w) / 2;
        top = (height - h) / 2;
        listW = Math.max(130, w * 2 / 5);

        filterBtns.clear();
        int fx = left + 6;
        String[] labels = {"Ready", "Next", "Known", "All"};
        Filter[] values = Filter.values();
        int fw = (listW - 6) / 4;
        for (int i = 0; i < 4; i++) {
            Filter f = values[i];
            Button b = Button.builder(Component.literal(labels[i]), btn -> {
                filter = f;
                scroll = 0;
                rebuildRows();
            }).bounds(fx + i * fw, top + 18, fw - 2, 14).build();
            filterBtns.add(addRenderableWidget(b));
        }
        searchBox = new EditBox(font, left + 6, top + 35, listW - 8, 14, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setMaxLength(40);
        searchBox.setValue(search);
        searchBox.setResponder(s -> {
            search = s.toLowerCase(Locale.ROOT);
            scroll = 0;
            rebuildRows();
        });
        addRenderableWidget(searchBox);

        int bx = left + listW + 6;
        int by = top + h - 20;
        int bw = (w - listW - 12) / 3;
        discoverBtn = addRenderableWidget(Button.builder(Component.literal("Explain & discover"), b -> {
            if (selected != null) {
                ClientPacketDistributor.sendToServer(new Payloads.Discover(selected.id()));
            }
        }).bounds(bx, by, bw * 2 - 2, 16).build());
        make1Btn = addRenderableWidget(Button.builder(Component.literal("Make x1"), b -> craft(1)).bounds(bx, by, bw - 2, 16).build());
        make8Btn = addRenderableWidget(Button.builder(Component.literal("Make x8"), b -> craft(8)).bounds(bx + bw, by, bw - 2, 16).build());
        cancelBtn = addRenderableWidget(Button.builder(Component.literal("Stop work"),
                b -> ClientPacketDistributor.sendToServer(Payloads.CancelCraft.INSTANCE)).bounds(bx + 2 * bw, by, bw - 2, 16).build());
        treatiseBtn = addRenderableWidget(Button.builder(Component.literal("Write treatise"), b -> {
            if (selected != null) {
                ClientPacketDistributor.sendToServer(new Payloads.WriteTreatise(selected.id()));
            }
        }).bounds(bx + bw * 2, by, bw - 2, 16).build());
        surveyBtn = addRenderableWidget(Button.builder(Component.literal("Survey ground"),
                b -> ClientPacketDistributor.sendToServer(Payloads.Prospect.INSTANCE)).bounds(left + w - 86, top + 3, 80, 12).build());

        rebuildRows();
        updateButtons();
        ClientPacketDistributor.sendToServer(Payloads.OpenJournal.INSTANCE);
    }

    private void craft(int times) {
        if (selected != null) {
            ClientPacketDistributor.sendToServer(new Payloads.Craft(selected.id(), times));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (seenVersion != ClientState.version) {
            seenVersion = ClientState.version;
            if (ClientState.quiz != shownQuiz) {
                shownQuiz = ClientState.quiz;
                quizScroll = 0;
            }
            rebuildRows();
        }
        if (minecraft != null && minecraft.player != null && minecraft.player.tickCount % 40 == 0) {
            ClientPacketDistributor.sendToServer(Payloads.OpenJournal.INSTANCE);
        }
        updateButtons();
    }

    private void updateButtons() {
        FunctionDef f = selected;
        boolean known = f != null && ClientState.knows(f.id());
        boolean quiz = ClientState.quiz != null;
        boolean frontier = f != null && !known && ClientState.known.containsAll(f.theory());
        discoverBtn.visible = frontier && !quiz;
        Payloads.Status st = f == null ? null : ClientState.statuses.get(f.id());
        discoverBtn.active = st == null || st.cooldownSeconds() <= 0;
        boolean hand = known && f.mode() == TechTree.Mode.HAND;
        boolean busy = !ClientState.craft.function().isEmpty();
        make1Btn.visible = hand && !quiz && !busy;
        make8Btn.visible = hand && !quiz && !busy;
        cancelBtn.visible = busy && !quiz;
        treatiseBtn.visible = known && f.theoretical() && ClientState.knows("T-11") && !quiz && !busy;
        surveyBtn.visible = ClientState.knows("T-18") && !quiz;
        for (Button b : filterBtns) {
            b.active = !quiz;
        }
    }

    private void rebuildRows() {
        List<FunctionDef> out = new ArrayList<>();
        for (FunctionDef f : TechTree.get().functions) {
            if (!search.isEmpty() && !f.name().toLowerCase(Locale.ROOT).contains(search)
                    && !f.id().toLowerCase(Locale.ROOT).contains(search)) {
                continue;
            }
            boolean known = ClientState.knows(f.id());
            boolean frontier = !known && ClientState.known.containsAll(f.theory());
            Payloads.Status st = ClientState.statuses.get(f.id());
            boolean ready = st != null && (st.state() == Payloads.Status.READY_TO_DISCOVER || st.state() == Payloads.Status.CAN_MAKE);
            boolean include = switch (filter) {
                case READY -> ready;
                case FRONTIER -> frontier;
                case KNOWN -> known;
                case ALL -> true;
            };
            if (include) {
                out.add(f);
            }
        }
        rows = out;
        scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows()));
    }

    private int listTop() {
        return top + 53;
    }

    private int visibleRows() {
        return Math.max(1, (top + h - 6 - listTop()) / ROW_H);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mx = event.x();
        double my = event.y();
        if (ClientState.quiz != null) {
            int i = quizOptionAt(mx, my);
            if (i >= 0) {
                Payloads.Quiz q = ClientState.quiz;
                ClientState.quiz = null;
                ClientPacketDistributor.sendToServer(new Payloads.Answer(q.function(), q.nonce(), i));
                return true;
            }
            if (inside(mx, my, quizCancelX(), quizCancelY(), 60, 14)) {
                ClientState.quiz = null;
                return true;
            }
            return true;
        }
        if (mx >= left + 6 && mx < left + listW && my >= listTop()) {
            int idx = scroll + (int) ((my - listTop()) / ROW_H);
            if (idx >= 0 && idx < rows.size() && my < listTop() + visibleRows() * ROW_H) {
                selected = rows.get(idx);
                detailScroll = 0;
                updateButtons();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (ClientState.quiz != null) {
            quizScroll = Math.max(0, Math.min(quizMaxScroll, quizScroll - (int) Math.signum(scrollY) * 12));
            return true;
        }
        if (x < left + listW) {
            scroll = Math.max(0, Math.min(rows.size() - visibleRows(), scroll - (int) Math.signum(scrollY) * 3));
        } else {
            detailScroll = Math.max(0, Math.min(maxDetailScroll, detailScroll - (int) Math.signum(scrollY) * 3));
        }
        return true;
    }

    private static boolean inside(double mx, double my, int x, int y, int ww, int hh) {
        return mx >= x && mx < x + ww && my >= y && my < y + hh;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        g.fill(left, top, left + w, top + h, BG);
        g.outline(left, top, w, h, BORDER);
        g.fill(left + 4, listTop() - 2, left + listW, top + h - 4, PANEL);
        g.fill(left + listW + 4, top + 18, left + w - 4, top + h - 24, PANEL);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        String header = "Journal of " + (ClientState.society.isEmpty() ? "your society" : ClientState.society) + " - "
                + ClientState.known.size() + "/" + TechTree.get().functions.size() + " known";
        g.text(font, header, left + 6, top + 5, TEXT, false);
        drawList(g, mouseX, mouseY);
        if (ClientState.quiz != null) {
            drawQuiz(g, mouseX, mouseY);
        } else {
            drawDetails(g);
        }
        drawProgress(g);
    }

    private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int y = listTop();
        int n = visibleRows();
        if (rows.isEmpty()) {
            g.text(font, filter == Filter.READY ? "Nothing ready yet." : "Nothing here.", left + 8, y + 2, DIM, false);
        }
        for (int i = 0; i < n && scroll + i < rows.size(); i++) {
            FunctionDef f = rows.get(scroll + i);
            int ry = y + i * ROW_H;
            if (f == selected) {
                g.fill(left + 5, ry - 1, left + listW - 1, ry + ROW_H - 1, 0xFF4A3B2A);
            } else if (inside(mouseX, mouseY, left + 5, ry - 1, listW - 6, ROW_H)) {
                g.fill(left + 5, ry - 1, left + listW - 1, ry + ROW_H - 1, 0xFF3A3024);
            }
            int colour = colourFor(f);
            g.fill(left + 7, ry + 2, left + 11, ry + 6, colour);
            String name = font.plainSubstrByWidth(f.name(), listW - 22);
            g.text(font, name, left + 14, ry, f.theoretical() ? THEORY : TEXT, false);
        }
        if (rows.size() > n) {
            int barH = Math.max(8, (n * ROW_H) * n / rows.size());
            int maxScroll = Math.max(1, rows.size() - n);
            int barY = y + (n * ROW_H - barH) * scroll / maxScroll;
            g.fill(left + listW - 3, barY, left + listW - 1, barY + barH, BORDER);
        }
    }

    private int colourFor(FunctionDef f) {
        if (ClientState.knows(f.id())) {
            Payloads.Status st = ClientState.statuses.get(f.id());
            if (st != null && st.state() == Payloads.Status.CAN_MAKE) {
                return GOOD;
            }
            return 0xFFCFC4AE;
        }
        Payloads.Status st = ClientState.statuses.get(f.id());
        if (st == null) {
            return ClientState.known.containsAll(f.theory()) ? WARN : 0xFF5A5046;
        }
        return switch (st.state()) {
            case Payloads.Status.READY_TO_DISCOVER -> GOOD;
            case Payloads.Status.DISCOVER_BLOCKED -> WARN;
            default -> 0xFF5A5046;
        };
    }

    private void drawDetails(GuiGraphicsExtractor g) {
        int x = left + listW + 8;
        int width = w - listW - 16;
        int yTop = top + 22;
        int yBottom = top + h - 26;
        if (selected == null) {
            wrap(g, "Pick a function on the left. Green ones can be discovered (or made) right now; yellow ones are next in line "
                    + "but you are missing something. Theories are blue.\n\nTo discover a function your society must hold every "
                    + "consumed input and every means of production (tools in your inventory, stations built nearby), know every "
                    + "required theory, and explain the mechanism correctly.", x, yTop, width, DIM, yBottom);
            return;
        }
        FunctionDef f = selected;
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(f.name(), f.theoretical() ? THEORY : 0xFFFFE9B8));
        lines.add(new Line(f.id() + " - " + (f.theoretical() ? "Theory" : "Practical") + " - era " + f.era() + " " + f.eraName()
                + " - " + f.route() + " - depth " + f.depth(), DIM));
        boolean known = ClientState.knows(f.id());
        Payloads.Status st = ClientState.statuses.get(f.id());
        if (known) {
            lines.add(new Line("Known to your society.", GOOD));
        } else if (st != null && st.cooldownSeconds() > 0) {
            lines.add(new Line("Wrong explanation: think it over for " + st.cooldownSeconds() + "s.", BAD));
        }
        lines.add(new Line("", TEXT));
        if (!f.theory().isEmpty()) {
            StringBuilder sb = new StringBuilder("Theory: ");
            for (int i = 0; i < f.theory().size(); i++) {
                String t = f.theory().get(i);
                FunctionDef td = TechTree.get().functionsById.get(t);
                sb.append(i > 0 ? ", " : "").append(ClientState.knows(t) ? "" : "[missing] ").append(td == null ? t : td.name());
            }
            lines.add(new Line(sb.toString(), ClientState.known.containsAll(f.theory()) ? TEXT : WARN));
        }
        if (!f.inputs().isEmpty()) {
            lines.add(new Line("Consumes: " + refs(f.inputs(), true), TEXT));
        }
        if (!f.means().isEmpty()) {
            lines.add(new Line((f.theoretical() ? "Evidence on hand: " : "Means of production: ") + refs(f.means(), false), TEXT));
        }
        if (!f.outputs().isEmpty()) {
            lines.add(new Line("Produces: " + refs(f.outputs(), true), TEXT));
        }
        lines.add(new Line(where(f), DIM));
        if (st != null && !st.missing().isEmpty()) {
            lines.add(new Line("", TEXT));
            lines.add(new Line(known ? "To make it here you still need:" : "To discover it you still need:", WARN));
            for (String m : st.missing()) {
                lines.add(new Line(" - " + m, WARN));
            }
        } else if (st != null && st.state() == Payloads.Status.READY_TO_DISCOVER) {
            lines.add(new Line("", TEXT));
            lines.add(new Line("You hold everything. Explain how it works to claim it.", GOOD));
        }
        if (known) {
            lines.add(new Line("", TEXT));
            lines.add(new Line("Mechanism: " + f.mechanism(), 0xFFD8CFB8));
        }
        if (known && f.notes() != null) {
            lines.add(new Line("Effect: " + f.notes(), 0xFFB8C8A0));
        }
        int total = 0;
        for (Line line : lines) {
            total += line.text.isEmpty() ? 4 : font.split(FormattedText.of(line.text), width).size() * font.lineHeight;
        }
        maxDetailScroll = Math.max(0, (total - (yBottom - yTop) + font.lineHeight - 1) / font.lineHeight);
        detailScroll = Math.min(detailScroll, maxDetailScroll);
        int y = yTop - detailScroll * font.lineHeight;
        g.enableScissor(x - 2, yTop, x + width + 2, yBottom);
        for (Line line : lines) {
            if (line.text.isEmpty()) {
                y += 4;
                continue;
            }
            for (FormattedCharSequence seq : font.split(FormattedText.of(line.text), width)) {
                if (y >= yTop - font.lineHeight && y < yBottom) {
                    g.text(font, seq, x, y, line.colour, false);
                }
                y += font.lineHeight;
            }
        }
        g.disableScissor();
    }

    private record Line(String text, int colour) { }

    private static String where(FunctionDef f) {
        return switch (f.mode()) {
            case THEORY -> "Discovered by study; nothing is consumed.";
            case HAND -> "Done by hand: use Make below once discovered.";
            case WORLD -> f.worldHint() == null ? "Done in the world." : f.worldHint();
            case PRODUCER -> "Produced over time by the " + stationName(f.host()) + ".";
            case STATION -> {
                TechTree.StationDef s = TechTree.get().stationsById.get(f.host());
                List<String> parts = new ArrayList<>();
                if (s != null) {
                    StationBlock.describe(s, parts::add);
                }
                yield "Performed at a " + stationName(f.host()) + ". " + String.join(" ", parts);
            }
        };
    }

    private static String stationName(String id) {
        TechTree.StationDef s = id == null ? null : TechTree.get().stationsById.get(id);
        return s == null ? String.valueOf(id) : s.name();
    }

    private static String refs(List<TechTree.Ref> refs, boolean counts) {
        List<String> out = new ArrayList<>();
        for (TechTree.Ref r : refs) {
            out.add(counts && r.count() > 1 ? r.name() + " x" + r.count() : r.name());
        }
        return String.join(", ", out);
    }

    private void wrap(GuiGraphicsExtractor g, String text, int x, int y, int width, int colour, int maxY) {
        for (String para : text.split("\n")) {
            if (para.isEmpty()) {
                y += 4;
                continue;
            }
            for (FormattedCharSequence seq : font.split(FormattedText.of(para), width)) {
                if (y + font.lineHeight > maxY) {
                    return;
                }
                g.text(font, seq, x, y, colour, false);
                y += font.lineHeight;
            }
        }
    }

    // ------------------------------------------------------------------ quiz

    private int quizX() {
        return left + listW + 6;
    }

    private int quizWidth() {
        return w - listW - 12;
    }

    private int quizCancelX() {
        return left + w - 70;
    }

    private int quizCancelY() {
        return top + h - 22;
    }

    /** Bottom of the area the options may use (the "Not yet" button sits below it). */
    private int quizOptionsBottom() {
        return quizCancelY() - 4;
    }

    private int optionHeight(String text) {
        return font.split(FormattedText.of(text), quizWidth() - 16).size() * font.lineHeight + 6;
    }

    private int quizOptionAt(double mx, double my) {
        Payloads.Quiz q = ClientState.quiz;
        if (q == null) {
            return -1;
        }
        int first = quizFirstOptionY(q);
        if (my < first || my >= quizOptionsBottom()) {
            return -1;
        }
        int y = first - quizScroll;
        for (int i = 0; i < q.options().size(); i++) {
            int hh = optionHeight(q.options().get(i));
            if (inside(mx, my, quizX(), y, quizWidth(), hh)) {
                return i;
            }
            y += hh + 3;
        }
        return -1;
    }

    private int quizFirstOptionY(Payloads.Quiz q) {
        return top + 22 + font.split(FormattedText.of(quizPrompt(q)), quizWidth()).size() * font.lineHeight + 4;
    }

    private static String quizPrompt(Payloads.Quiz q) {
        FunctionDef f = TechTree.get().functionsById.get(q.function());
        return "Explain " + (f == null ? q.function() : f.name()) + ": which mechanism is right?";
    }

    private void drawQuiz(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Payloads.Quiz q = ClientState.quiz;
        int x = quizX();
        int width = quizWidth();
        g.fill(x - 2, top + 18, x + width + 2, top + h - 4, 0xFF1A140F);
        int y = top + 22;
        for (FormattedCharSequence seq : font.split(FormattedText.of(quizPrompt(q)), width)) {
            g.text(font, seq, x, y, 0xFFFFE9B8, false);
            y += font.lineHeight;
        }
        int first = quizFirstOptionY(q);
        int bottom = quizOptionsBottom();
        int total = 0;
        for (String option : q.options()) {
            total += optionHeight(option) + 3;
        }
        quizMaxScroll = Math.max(0, total - (bottom - first));
        quizScroll = Math.max(0, Math.min(quizScroll, quizMaxScroll));
        g.enableScissor(x - 2, first, x + width + 2, bottom);
        y = first - quizScroll;
        boolean inArea = mouseY >= first && mouseY < bottom;
        for (int i = 0; i < q.options().size(); i++) {
            List<FormattedCharSequence> lines = font.split(FormattedText.of(q.options().get(i)), width - 16);
            int hh = lines.size() * font.lineHeight + 6;
            boolean hover = inArea && inside(mouseX, mouseY, x, y, width, hh);
            g.fill(x, y, x + width, y + hh, hover ? 0xFF4A3B2A : PANEL);
            g.outline(x, y, width, hh, hover ? 0xFFE8C25A : BORDER);
            g.text(font, (char) ('A' + i) + ")", x + 3, y + 3, WARN, false);
            int ly = y + 3;
            for (FormattedCharSequence seq : lines) {
                g.text(font, seq, x + 14, ly, TEXT, false);
                ly += font.lineHeight;
            }
            y += hh + 3;
        }
        g.disableScissor();
        if (quizMaxScroll > 0) {
            int area = bottom - first;
            int barH = Math.max(8, area * area / (area + quizMaxScroll));
            int barY = first + (area - barH) * quizScroll / quizMaxScroll;
            g.fill(x + width, barY, x + width + 2, barY + barH, BORDER);
        }
        boolean hoverCancel = inside(mouseX, mouseY, quizCancelX(), quizCancelY(), 60, 14);
        g.fill(quizCancelX(), quizCancelY(), quizCancelX() + 60, quizCancelY() + 14, hoverCancel ? 0xFF5A4030 : 0xFF3A3024);
        g.centeredText(font, "Not yet", quizCancelX() + 30, quizCancelY() + 3, TEXT);
    }

    private void drawProgress(GuiGraphicsExtractor g) {
        Payloads.CraftProgress c = ClientState.craft;
        if (c.function().isEmpty() || c.totalTicks() <= 0) {
            return;
        }
        FunctionDef f = TechTree.get().functionsById.get(c.function());
        int x = left + listW + 6;
        int y = top + h - 36;
        int width = w - listW - 12;
        float done = 1f - Math.max(0, c.remainingTicks()) / (float) c.totalTicks();
        g.fill(x, y, x + width, y + 8, 0xFF1A140F);
        g.fill(x, y, x + (int) (width * done), y + 8, 0xFF6FAE52);
        String label = "Working: " + (f == null ? c.function() : f.name()) + (c.timesLeft() > 1 ? " (" + c.timesLeft() + " to go)" : "");
        g.text(font, font.plainSubstrByWidth(label, width), x, y - 10, TEXT, false);
    }
}
