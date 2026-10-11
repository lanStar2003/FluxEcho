package com.fluxecho.city;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.IntSupplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import com.fluxecho.city.client.CityScreen;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.FluxMachineGui;
import com.fluxecho.library.client.ArchiveScreen;
import com.fluxecho.logic.ModuleMap;
import com.fluxecho.logic.UnitBrowse;
import com.fluxecho.logic.UnitLayout;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.nexus.client.NexusScreen;
import com.gtnewhorizons.modularui.api.forge.IItemHandlerModifiable;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.math.Size;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;

/**
 * The window of every city module (0.11.0, CITY.md §1.6 item 7), in the flux look of the nexus and the Echo Archive,
 * on three pages chosen by the tabs at the top right. 状态 (Status): whether the module stands formed, whether and where
 * it is docked (its site and the nexus's phase), whether it is powered and what it draws, its owner, its units and
 * samples, beside an emblem in the module's colour that lives with its state. 单元 (Units): the module's units as a
 * list in pages (each with its label, kind and fill), and the unit shown with its slots as a grid the player takes
 * from and puts into, arrows to the neighbouring units. 平面 (Plan): a floor plan of each storey ({@link ModuleMap}), one
 * cell a block, every unit drawn as its cells coloured by how full it is, the one shown outlined; a click picks
 * another. Below every page a line of counts and a hint; then the player's inventory.
 * <p>
 * The unit shown is the window's own selection, kept on the server ({@link Sel}). It starts at
 * {@link TileCityModule#selectedUnit} (which a click on a unit in the world has just set; the window then opens on its
 * Units page) and every change is written back there. The grid is a view of the samples ({@link Grid}) that on the
 * server looks the selection up on every access, so a change of unit never leaves a stale slot mapping behind: the
 * container simply sees slots whose contents changed and sends them. The client asks for a unit through {@link Link}
 * (the list, the plan) or with the arrow buttons; the server checks it and the answer comes back as the synced
 * selection, {@link View#unit}. The list, the plan and the counts read the client's copy of the samples
 * ({@link TileCityModule#clientSamples}, which the module's own description and delta packets keep), so only the page
 * of the list shown is drawn and nothing more is sent for it.
 * <p>
 * Shift-clicking an item in the inventory fills the unit shown, one item a slot, as far as it has empty slots;
 * shift-clicking a sample in the grid takes it back. After a click that touched the grid the server sends the whole
 * window once more ({@link Link}), since vanilla trusts the client to have foreseen a click's effects and the client's
 * grid may still show the previous unit for a tick after the selection changed.
 */
public final class CityGui {

    /** The window, and where the player's inventory starts. */
    public static final int W = 260, H = 256, INV_Y = 174;
    /** The pages. */
    public static final int STATUS = 0, UNITS = 1, PLAN = 2;
    // the top row: the header and the tabs
    public static final int TAB_Y = 4, TAB_W = 40, TAB_H = 15, HEADER_W = 120;
    static final int[] TAB_X = { 130, 172, 214 };
    private static final String[] TAB_KEY = { "city.gui.tab_status", "city.gui.tab_units", "city.gui.tab_plan" };
    // the page area
    public static final int PAGE_TOP = 22, PAGE_BOTTOM = 140, PANEL_RIGHT = 254;
    // 状态: the state's lines on the left, the emblem on the right
    public static final int STATUS_RIGHT = 170, STATUS_X = 11, STATUS_Y = 26, STATUS_W = 154, STATUS_H = 110;
    public static final int EMBLEM_X = 176, EMBLEM_Y = 25, EMBLEM_W = 76, EMBLEM_H = 112;
    // 单元: the list on the left, the unit shown on the right
    public static final int LIST_RIGHT = 118, LIST_X = 9, LIST_HEAD_Y = 25, LIST_HEAD_W = 78, LIST_Y = 37, LIST_W = 106,
        ROW_H = 12, ROWS = 8;
    public static final int PAGER_Y = 24, PAGER_W = 12, PAGER_H = 11, PAGER_PREV_X = 89, PAGER_NEXT_X = 103;
    public static final int DETAIL_LEFT = 122, HEAD_X = 126, HEAD_Y = 25, HEAD_W = 124, HEAD_H = 21, GRID_Y = 52;
    /** The grid: slots a row, and the most slots a unit shows. */
    public static final int GRID_COLS = 6, GRID_MAX = 18;
    public static final int HINT_X = 126, HINT_Y = 107, HINT_W = 124, HINT_H = 12;
    public static final int NAV_Y = 122, NAV_W = 17, NAV_H = 14, NAV_PREV_X = 126, NAV_NEXT_X = 233, NAV_LABEL_X = 145,
        NAV_LABEL_W = 86;
    // 平面: the plan on the left, the storeys and the unit shown on the right
    public static final int PLAN_RIGHT = 134, MAP_MAX_W = 124, MAP_MAX_H = 112;
    public static final int SIDE_X = 140, STOREY_Y = 26, STOREY_W = 26, STOREY_H = 13, STOREY_GAP = 2, STOREY_COLS = 4,
        STOREY_MAX = 8;
    public static final int INFO_X = 140, INFO_Y = 57, INFO_W = 112, INFO_H = 60, OPEN_X = 140, OPEN_Y = 122,
        OPEN_W = 112, OPEN_H = 14;
    // the strip below every page
    public static final int STRIP_Y = 142, FOOT_X = 9, FOOT_Y = 145, FOOT_W = 242, FOOT_H = 26;

    /** Each module kind's plans, worked out once (pure data, the same on both sides). */
    private static final Map<String, ModuleMap> MAPS = new ConcurrentHashMap<>();

    private CityGui() {}

    /** The plans of a module's kind. */
    public static ModuleMap map(TileCityModule c) {
        return MAPS.computeIfAbsent(
            c.moduleKey(),
            k -> new ModuleMap(c.build(), c.layout(), c.dissolves(), MAP_MAX_W, MAP_MAX_H));
    }

    /** The most slots one unit of the module has, as many as the grid shows (at most {@link #GRID_MAX}). */
    public static int gridSlots(UnitLayout layout) {
        int n = 0;
        for (UnitLayout.Unit u : layout.units()) n = Math.max(n, u.slots);
        return Math.max(1, Math.min(GRID_MAX, n));
    }

    /** Where grid slot {@code k} stands: {x, y}, the grid centred in the unit panel. */
    public static int[] gridPos(int slots, int k) {
        int cols = Math.min(GRID_COLS, slots);
        int x0 = DETAIL_LEFT + (PANEL_RIGHT - DETAIL_LEFT - cols * 18) / 2;
        return new int[] { x0 + k % GRID_COLS * 18, GRID_Y + k / GRID_COLS * 18 };
    }

    /** Where the plan stands on its page: {x, y}, centred in the plan panel. */
    public static int[] mapPos(ModuleMap m) {
        return new int[] { 6 + (PLAN_RIGHT - 6 - m.w) / 2, PAGE_TOP + (PAGE_BOTTOM - PAGE_TOP - m.h) / 2 };
    }

    /**
     * One open window's client side: the page shown, the list's page, the storey the plan shows, the unit shown as
     * the server last said (-1 until it has), and the module's state as the window syncs it.
     */
    public static final class View {

        /** {@link #STATUS}, {@link #UNITS} or {@link #PLAN}; the client's own. */
        public int page = STATUS;
        /** The unit shown, as the server says; -1 before it has. */
        public int unit = -1;
        /** The page of the unit list shown, and the storey the plan shows. */
        public int listPage, storey;
        public boolean formed, powered;
        public String dock = "unformed", owner = "";
        public long upkeep;
        /** The site (or ring slot) it is docked on, and the nexus's phase; -1 and 0 while undocked. */
        public int site = -1, phase;
        /** The module kind's plans. */
        public final ModuleMap map;
        /** Whether the player chose a page already (the opening page then no longer applies). */
        boolean chosen;

        View(ModuleMap map) {
            this.map = map;
        }

        /** Shows a page; opening the plan shows the storey of the unit shown. */
        public void show(int p) {
            chosen = true;
            if (p == PLAN && p != page && unit >= 0) storey = Math.max(0, map.storeyOf(unit));
            page = p;
        }

        /** The server says which unit is shown: the list turns to its page. */
        void shown(int u, int count) {
            unit = u;
            if (UnitBrowse.valid(u, count)) listPage = UnitBrowse.pageOf(u, ROWS);
        }
    }

    /**
     * The window's selection on the server: the unit its grid shows. Every change is written to
     * {@link TileCityModule#selectedUnit} too, the unit the next window opens at.
     */
    static final class Sel {

        private final TileCityModule c;
        int unit;

        Sel(TileCityModule c) {
            this.c = c;
            unit = UnitBrowse.clamp(c.selectedUnit, c.unitCount());
        }

        /** Shows the unit; anything outside the layout is ignored. */
        void set(int u) {
            if (!UnitBrowse.valid(u, c.unitCount())) return;
            unit = u;
            c.selectUnit(u);
        }

        /** Moves along the units, round from the last to the first. */
        void step(int by) {
            set(UnitBrowse.step(unit, by, c.unitCount()));
        }
    }

    /**
     * The grid: the slots of the unit shown as one handler, grid slot {@code k} being sample slot
     * {@code first + k} of that unit, nothing past its slot count. On the server every access looks the window's
     * selection up anew and goes to the module's samples, so the slot mapping is never stale; whether an item may go
     * in is the samples' own rule. On the client it is a plain copy that the container fills, and its checks only
     * predict the server's from the layout.
     */
    static final class Grid implements IItemHandlerModifiable {

        private final TileCityModule c;
        private final IntSupplier unit;
        private final Link link;
        private final boolean client;
        private final ItemStack[] shown;

        Grid(TileCityModule c, IntSupplier unit, Link link, boolean client, int slots) {
            this.c = c;
            this.unit = unit;
            this.link = link;
            this.client = client;
            shown = new ItemStack[slots];
        }

        /** The sample slot grid slot {@code k} stands for now, or -1 when the unit shown has no such slot. */
        int slot(int k) {
            int u = unit.getAsInt();
            if (k < 0 || k >= shown.length || !UnitBrowse.valid(u, c.unitCount())) return -1;
            int[] s = c.unitSlots(u);
            return k < s[1] ? s[0] + k : -1;
        }

        @Override
        public int getSlots() {
            return shown.length;
        }

        @Override
        public ItemStack getStackInSlot(int k) {
            if (k < 0 || k >= shown.length) return null;
            if (client) return shown[k];
            int s = slot(k);
            return s < 0 ? null : c.samples.getStackInSlot(s);
        }

        @Override
        public void setStackInSlot(int k, ItemStack s) {
            if (k < 0 || k >= shown.length) return;
            if (client) {
                shown[k] = s;
                return;
            }
            link.touch();
            int slot = slot(k);
            if (slot >= 0) c.samples.setStackInSlot(slot, s);
        }

        @Override
        public ItemStack insertItem(int k, ItemStack s, boolean simulate) {
            if (s == null || !isItemValid(k, s)) return s;
            if (!client) return c.samples.insertItem(slot(k), s, simulate);
            if (shown[k] != null) return s;
            if (!simulate) shown[k] = one(s);
            return rest(s);
        }

        @Override
        public ItemStack extractItem(int k, int amount, boolean simulate) {
            if (k < 0 || k >= shown.length || amount <= 0) return null;
            if (!client) {
                link.touch();
                int slot = slot(k);
                return slot < 0 ? null : c.samples.extractItem(slot, amount, simulate);
            }
            ItemStack s = shown[k];
            if (s == null) return null;
            ItemStack out = s.copy();
            out.stackSize = Math.min(amount, s.stackSize);
            if (!simulate) {
                if (out.stackSize >= s.stackSize) shown[k] = null;
                else {
                    ItemStack left = s.copy();
                    left.stackSize -= out.stackSize;
                    shown[k] = left;
                }
            }
            return out;
        }

        @Override
        public int getSlotLimit(int k) {
            return 1;
        }

        @Override
        public boolean isItemValid(int k, ItemStack s) {
            if (s == null || k < 0 || k >= shown.length) return false;
            if (client) {
                // the layout is the same on both sides, so the client knows the unit's slot count
                int u = unit.getAsInt();
                return UnitBrowse.valid(u, c.unitCount()) && k < c.unitSlots(u)[1];
            }
            link.touch();
            int slot = slot(k);
            return slot >= 0 && c.samples.isItemValid(slot, s);
        }
    }

    /**
     * The window's selection, synced: the server sends the unit shown ({@link View#unit} on the client), and the
     * client asks through it for another ({@link #ask}). The server checks it.
     * <p>
     * It also keeps the client's slots honest. Right after a click the server sends no slot changes, taking the client
     * to have foreseen them ({@code EntityPlayerMP.isChangingQuantityOnly}); but the client's grid can still show the
     * previous unit for a tick after the selection changed, and an item may have been refused on the server. So after
     * a click that asked the grid anything, the whole window is sent again on the next tick, as vanilla does after a
     * click it refused.
     */
    static final class Link extends FakeSyncWidget<Integer> {

        static final int ASK_UNIT = 1;
        private final TileCityModule c;
        private final Sel sel;
        /** Whether the grid was asked anything since the last pass; whether a resend is owed. */
        private boolean touched, owed;

        Link(TileCityModule c, Sel sel, View view) {
            super(
                () -> sel.unit,
                v -> view.shown(v, c.unitCount()),
                (buf, v) -> buf.writeVarIntToBuffer(v),
                PacketBuffer::readVarIntFromBuffer);
            this.c = c;
            this.sel = sel;
        }

        /** Client: asks the server to show a unit. */
        void ask(int unit) {
            if (UnitBrowse.valid(unit, c.unitCount())) syncToServer(ASK_UNIT, buf -> buf.writeVarIntToBuffer(unit));
        }

        @Override
        public void readOnServer(int id, PacketBuffer buf) throws IOException {
            int v = buf.readVarIntFromBuffer();
            if (id == ASK_UNIT) sel.set(v);
        }

        /** Server: the grid was asked something (a click is being handled). */
        void touch() {
            touched = true;
        }

        @Override
        public void detectAndSendChanges(boolean init) {
            resend();
            super.detectAndSendChanges(init);
        }

        /** Server: after a click's quiet pass that touched the grid, sends the window again. */
        private void resend() {
            EntityPlayer p = getContext() == null ? null : getContext().getPlayer();
            if (!(p instanceof EntityPlayerMP mp)) return;
            if (mp.isChangingQuantityOnly) {
                if (touched) owed = true;
                touched = false;
                return;
            }
            touched = false;
            if (!owed) return;
            owed = false;
            Container ct = getContext().getContainer();
            if (ct != null && mp.openContainer == ct) mp.sendContainerAndContentsToPlayer(ct, ct.getInventory());
        }
    }

    /** A button of the unit list that turns the list's pages with the mouse wheel (client). */
    private static final class ListButton extends ButtonWidget {

        private final TileCityModule c;
        private final View view;

        ListButton(TileCityModule c, View view) {
            this.c = c;
            this.view = view;
        }

        @Override
        public boolean onMouseScroll(int direction) {
            if (direction == 0) return false;
            view.listPage = UnitBrowse.page(view.listPage + (direction > 0 ? -1 : 1), c.unitCount(), ROWS);
            return true;
        }
    }

    static ModularWindow window(TileCityModule c, UIBuildContext ctx) {
        // the window closes once the module is broken (or its chunk goes): it would keep working on a removed tile
        ctx.setValidator(c::standing);
        EntityPlayer player = ctx.getPlayer();
        boolean client = player.worldObj.isRemote;
        View view = new View(map(c));
        Sel sel = new Sel(c);
        // a click on a unit in the world opens the window on its Units page (server side; the client is told)
        int opening = !client && c.openOnUnit ? UNITS : STATUS;
        if (!client) c.openOnUnit = false;
        IntSupplier unit = client ? () -> view.unit : () -> sel.unit;
        Link link = new Link(c, sel, view);
        int slots = gridSlots(c.layout());
        Grid grid = new Grid(c, unit, link, client, slots);

        ModularWindow.Builder b = ModularWindow.builder(W, H);
        b.setBackground((x, y, w, h, partial) -> CityScreen.background(c, view, slots, x, y, w, h));
        b.bindPlayerInventory(player, new Pos2d((W - 162) / 2, INV_Y), FluxMachineGui.SLOT);
        b.widget(link);
        sync(c, b, view);
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> opening, v -> { if (!view.chosen) view.page = v; }));

        List<SlotWidget> gridSlots = new ArrayList<>();
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> CityScreen.header(c, view, x, y, w, h))
                // every tick: the grid's slots follow the page and the unit shown (each tells the server itself)
                .setTicker(widget -> enableGrid(c, view, gridSlots))
                .setPos(6, 5)
                .setSize(HEADER_W, 14));
        for (int i = 0; i < TAB_X.length; i++) {
            int page = i;
            String key = TAB_KEY[i];
            b.widget(
                new ButtonWidget().setOnClick((click, widget) -> { if (widget.isClient()) view.show(page); })
                    .setBackground(
                        (x, y, w, h, partial) -> NexusScreen.tab(EchoText.t(key), x, y, w, h, view.page == page))
                    .setPos(TAB_X[i], TAB_Y)
                    .setSize(TAB_W, TAB_H));
        }

        statusPage(c, b, view);
        unitsPage(c, b, view, sel, grid, link, player, slots, gridSlots);
        planPage(c, b, view, link);
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> CityScreen.foot(c, view, x, y, w, h))
                .setPos(FOOT_X, FOOT_Y)
                .setSize(FOOT_W, FOOT_H));
        return b.build();
    }

    /** What the window shows that only the server knows. */
    private static void sync(TileCityModule c, ModularWindow.Builder b, View view) {
        b.widget(new FakeSyncWidget.BooleanSyncer(c::formed, v -> view.formed = v));
        b.widget(new FakeSyncWidget.StringSyncer(c::dockStatus, v -> view.dock = v));
        b.widget(new FakeSyncWidget.BooleanSyncer(c::powered, v -> view.powered = v));
        b.widget(new FakeSyncWidget.LongSyncer(c::upkeep, v -> view.upkeep = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> c.nexus() != null ? c.slot() : -1, v -> view.site = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> c.nexus() != null ? TileNexus.PHASE : 0, v -> view.phase = v));
        b.widget(new FakeSyncWidget.StringSyncer(c::ownerName, v -> view.owner = v));
    }

    /**
     * The grid's slots on the client: a slot is on while the Units page is shown and the unit shown has that slot.
     * {@code SlotWidget.setEnabled} tells the server only when the state changes.
     */
    private static void enableGrid(TileCityModule c, View view, List<SlotWidget> slots) {
        int n = view.page == UNITS && UnitBrowse.valid(view.unit, c.unitCount()) ? c.unitSlots(view.unit)[1] : 0;
        for (int k = 0; k < slots.size(); k++) slots.get(k)
            .setEnabled(k < n);
    }

    /** 状态: the state's lines and the emblem. */
    private static void statusPage(TileCityModule c, ModularWindow.Builder b, View view) {
        Function<Widget, Boolean> on = widget -> view.page == STATUS;
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> CityScreen.status(c, view, x, y, w, h))
                .dynamicTooltip(() -> CityScreen.statusTip(c, view))
                .setUpdateTooltipEveryTick(true)
                .setPos(STATUS_X, STATUS_Y)
                .setSize(STATUS_W, STATUS_H)
                .setEnabled(on));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> CityScreen.emblem(c, view, x, y, w, h))
                .setPos(EMBLEM_X, EMBLEM_Y)
                .setSize(EMBLEM_W, EMBLEM_H)
                .setEnabled(on));
    }

    /** 单元: the list with its pager, the unit shown with its grid, the hint and the arrows. */
    private static void unitsPage(TileCityModule c, ModularWindow.Builder b, View view, Sel sel, Grid grid, Link link,
        EntityPlayer player, int slots, List<SlotWidget> gridSlots) {
        Function<Widget, Boolean> on = widget -> view.page == UNITS;
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> CityScreen.listHead(c, view, x, y, w, h))
                .setPos(LIST_X, LIST_HEAD_Y)
                .setSize(LIST_HEAD_W, 10)
                .setEnabled(on));
        int[] pagerX = { PAGER_PREV_X, PAGER_NEXT_X };
        for (int i = 0; i < 2; i++) {
            int by = i == 0 ? -1 : 1;
            b.widget(
                new ButtonWidget()
                    .setOnClick(
                        (click, widget) -> {
                            if (widget.isClient())
                                view.listPage = UnitBrowse.page(view.listPage + by, c.unitCount(), ROWS);
                        })
                    .setBackground((x, y, w, h, partial) -> ArchiveScreen.arrows(x, y, w, h, by > 0, false))
                    .dynamicTooltip(() -> EchoText.lines(by > 0 ? "city.gui.page_next" : "city.gui.page_prev"))
                    .setPos(pagerX[i], PAGER_Y)
                    .setSize(PAGER_W, PAGER_H)
                    .setEnabled(widget -> view.page == UNITS && c.unitCount() > ROWS));
        }
        for (int i = 0; i < ROWS; i++) {
            int line = i;
            ButtonWidget row = new ListButton(c, view);
            row.setOnClick((click, widget) -> {
                if (!widget.isClient()) return;
                int u = UnitBrowse.unitAt(view.listPage, line, c.unitCount(), ROWS);
                if (u >= 0) link.ask(u);
            });
            row.setBackground((x, y, w, h, partial) -> CityScreen.row(c, view, line, x, y, w, h));
            row.dynamicTooltip(() -> CityScreen.rowTip(c, view, line));
            row.setUpdateTooltipEveryTick(true);
            row.setPos(LIST_X, LIST_Y + i * ROW_H);
            row.setSize(LIST_W, ROW_H);
            row.setEnabled(on);
            b.widget(row);
        }
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> CityScreen.unitHead(c, view, x, y, w, h))
                .dynamicTooltip(() -> CityScreen.unitTip(c, view.unit))
                .setUpdateTooltipEveryTick(true)
                .setPos(HEAD_X, HEAD_Y)
                .setSize(HEAD_W, HEAD_H)
                .setEnabled(on));
        for (int k = 0; k < slots; k++) {
            int[] at = gridPos(slots, k);
            // one item a slot, even from a hotbar key over an empty slot
            SlotWidget s = new SlotWidget(new UnitSlot(grid, k, player)).setAccess(true, true);
            s.setBackground(FluxMachineGui.SLOT_SAMPLE);
            s.setPos(at[0], at[1]);
            gridSlots.add(s);
            b.widget(s);
        }
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> CityScreen.hint(c, view, x, y, w, h))
                .setPos(HINT_X, HINT_Y)
                .setSize(HINT_W, HINT_H)
                .setEnabled(on));
        int[] navX = { NAV_PREV_X, NAV_NEXT_X };
        for (int i = 0; i < 2; i++) {
            int by = i == 0 ? -1 : 1;
            b.widget(
                new ButtonWidget().setOnClick((click, widget) -> { if (!widget.isClient()) sel.step(by); })
                    .setBackground((x, y, w, h, partial) -> ArchiveScreen.arrows(x, y, w, h, by > 0, false))
                    .dynamicTooltip(() -> EchoText.lines(by > 0 ? "city.gui.next" : "city.gui.prev"))
                    .setPos(navX[i], NAV_Y)
                    .setSize(NAV_W, NAV_H)
                    .setEnabled(on));
        }
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> CityScreen.navLabel(c, view, x, y, w, h))
                .setPos(NAV_LABEL_X, NAV_Y)
                .setSize(NAV_LABEL_W, NAV_H)
                .setEnabled(on));
    }

    /** 平面: the plan, the storeys, the unit shown and the way to its Units page. */
    private static void planPage(TileCityModule c, ModularWindow.Builder b, View view, Link link) {
        Function<Widget, Boolean> on = widget -> view.page == PLAN;
        ModuleMap m = view.map;
        int[] at = mapPos(m);
        ButtonWidget plan = new ButtonWidget();
        plan.setOnClick((click, widget) -> {
            if (!widget.isClient()) return;
            int u = mapUnit(widget, m, view.storey);
            if (u >= 0) link.ask(u);
        });
        plan.setBackground((x, y, w, h, partial) -> CityScreen.map(c, view, mapUnit(plan, m, view.storey), x, y, w, h));
        plan.dynamicTooltip(() -> CityScreen.mapTip(c, mapUnit(plan, m, view.storey)));
        plan.setUpdateTooltipEveryTick(true);
        plan.setPos(at[0], at[1]);
        plan.setSize(m.w, m.h);
        plan.setEnabled(on);
        b.widget(plan);
        int storeys = Math.min(STOREY_MAX, m.storeys());
        for (int s = 0; s < storeys; s++) {
            int storey = s;
            b.widget(
                new ButtonWidget().setOnClick((click, widget) -> { if (widget.isClient()) view.storey = storey; })
                    .setBackground((x, y, w, h, partial) -> CityScreen.storeyTab(c, view, storey, x, y, w, h))
                    .dynamicTooltip(() -> CityScreen.storeyTip(c, view, storey))
                    .setPos(
                        SIDE_X + s % STOREY_COLS * (STOREY_W + STOREY_GAP),
                        STOREY_Y + s / STOREY_COLS * (STOREY_H + STOREY_GAP))
                    .setSize(STOREY_W, STOREY_H)
                    .setEnabled(on));
        }
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> CityScreen.planInfo(c, view, x, y, w, h))
                .dynamicTooltip(() -> CityScreen.unitTip(c, view.unit))
                .setUpdateTooltipEveryTick(true)
                .setPos(INFO_X, INFO_Y)
                .setSize(INFO_W, INFO_H)
                .setEnabled(on));
        b.widget(
            new ButtonWidget().setOnClick((click, widget) -> { if (widget.isClient()) view.show(UNITS); })
                .setBackground(
                    (x, y, w, h, partial) -> NexusScreen
                        .button(EchoText.t("city.gui.open_unit"), x, y, w, h, false, view.unit < 0, c.moduleColor()))
                .dynamicTooltip(() -> EchoText.lines("city.gui.open_unit.tip"))
                .setPos(OPEN_X, OPEN_Y)
                .setSize(OPEN_W, OPEN_H)
                .setEnabled(on));
    }

    // ---- client helpers

    /** Where the mouse is over the widget, {x, y} in the widget's own pixels; null when it is elsewhere (client). */
    static double[] mouse(Widget widget) {
        if (widget == null || widget.getContext() == null || !widget.isClient()) return null;
        Pos2d m = widget.getContext()
            .getMousePos(), at = widget.getAbsolutePos();
        Size size = widget.getSize();
        if (m == null || at == null || size == null) return null;
        double mx = m.x - at.x + 0.5, my = m.y - at.y + 0.5;
        return mx < 0 || my < 0 || mx > size.width || my > size.height ? null : new double[] { mx, my };
    }

    /** The unit under the mouse on the plan of a storey, or -1 (client). */
    static int mapUnit(Widget widget, ModuleMap m, int storey) {
        double[] p = mouse(widget);
        return p == null ? -1 : m.nearest(storey, p[0], p[1]);
    }

    /** One of the stack. */
    private static ItemStack one(ItemStack s) {
        ItemStack one = s.copy();
        one.stackSize = 1;
        return one;
    }

    /** The stack less one, or null when that leaves nothing. */
    private static ItemStack rest(ItemStack s) {
        if (s.stackSize <= 1) return null;
        ItemStack rest = s.copy();
        rest.stackSize--;
        return rest;
    }
}
