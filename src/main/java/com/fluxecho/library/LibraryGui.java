package com.fluxecho.library;

import com.fluxecho.core.EchoText;
import com.fluxecho.core.FluxMachineGui;
import com.fluxecho.library.client.LibraryScreen;
import com.fluxecho.logic.LibraryShape;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;

/**
 * The Echo Library's GUI. A library standing as the Echo Archive gets the Archive's window ({@link ArchiveGui}: a
 * bookcase at a time, the floor plans, the catalogue). A 0.9.2 hall keeps its own window, unchanged: one level of the
 * hall's shelves at a time (the arrows walk up and down the levels, each shelf where it stands in the GUI's order), the
 * card desk below (blank cards in, the chosen sample between the arrows, written cards out), its state, and the
 * player's inventory.
 */
public final class LibraryGui {

    public static final int W = 184, ROWS = 4, SLOTS_Y = 24, PAGE_Y = SLOTS_Y + ROWS * 18 + 2, DESK_Y = PAGE_Y + 20;
    /** Shelf levels: one page each. */
    public static final int PAGES = LibraryShape.HALL_HIGH - LibraryShape.HALL_LOW + 1;

    private LibraryGui() {}

    /** The page (shelf level) of each of the hall's sample slots and its place on that page. */
    private static final int[] PAGE = new int[TileLibrary.HALL_BOOKS], PLACE = new int[TileLibrary.HALL_BOOKS];
    private static final int[] ON_PAGE = new int[PAGES];

    static {
        for (int i = 0; i < TileLibrary.HALL_BOOKS; i++) {
            int p = LibraryShape.SHELVES.get(i).y - LibraryShape.HALL_LOW;
            PAGE[i] = p;
            PLACE[i] = ON_PAGE[p]++;
        }
    }

    /** The page (shelf level, from the bottom) sample slot {@code i} is on. */
    public static int pageOf(int i) {
        return PAGE[i];
    }

    /** Shelves on the page. */
    public static int onPage(int page) {
        return ON_PAGE[Math.floorMod(page, PAGES)];
    }

    static ModularWindow window(TileLibrary l, UIBuildContext ctx) {
        // the window closes once the library is broken (or its chunk goes): it would keep working on a removed tile,
        // whose books and vault pulls are never saved
        ctx.setValidator(l::standing);
        if (l.isArchive()) return ArchiveGui.window(l, ctx);
        int stateY = DESK_Y + 24, invY = stateY + 30;
        ModularWindow.Builder b = ModularWindow.builder(W, invY + 82);
        b.setBackground((x, y, w, h, partial) -> LibraryScreen.background(l, x, y, w, h, ROWS));
        b.bindPlayerInventory(ctx.getPlayer(), new Pos2d((W - 162) / 2, invY), FluxMachineGui.SLOT);
        b.widget(new FakeSyncWidget.StringSyncer(l::dockStatus, v -> l.guiDock = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(l::lendsShown, v -> l.guiLends = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> l.selected, v -> l.guiSelected = v));
        b.widget(new FakeSyncWidget.BooleanSyncer(l::powered, v -> l.guiPowered = v));

        int left = (W - 162) / 2;
        SlotWidget[] slots = new SlotWidget[TileLibrary.HALL_BOOKS];
        for (int i = 0; i < slots.length; i++) {
            // one item a shelf, even from a hotbar key over an empty shelf
            slots[i] = new SlotWidget(new BookSlot(l.samples, i, ctx.getPlayer())).setAccess(true, true);
            slots[i].setBackground(FluxMachineGui.SLOT_SAMPLE)
                .setPos(left + PLACE[i] % 9 * 18, SLOTS_Y + PLACE[i] / 9 * 18);
            // the client turns the pages; the slot tells the server itself
            slots[i].setEnabled(PAGE[i] == 0);
            b.widget(slots[i]);
        }
        // the page shown: the client's only
        int[] page = { 0 };
        for (int step = -1; step <= 1; step += 2) {
            int s = step;
            b.widget(new ButtonWidget().setOnClick((click, widget) -> {
                if (!widget.isClient()) return;
                page[0] = Math.floorMod(page[0] + s, PAGES);
                for (int i = 0; i < slots.length; i++) slots[i].setEnabled(PAGE[i] == page[0]);
            })
                .setBackground((x, y, w, h, partial) -> LibraryScreen.arrow(x, y, w, h, s > 0))
                .dynamicTooltip(() -> EchoText.lines(s > 0 ? "library.gui.page_up" : "library.gui.page_down"))
                .setPos(s < 0 ? left : left + 150, PAGE_Y)
                .setSize(12, 16));
        }
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> LibraryScreen.page(l, page[0], x, y, w, h))
                .setPos(left + 14, PAGE_Y)
                .setSize(134, 16));

        b.widget(
            new SlotWidget(l.desk, 0).setAccess(true, true)
                .setBackground(FluxMachineGui.SLOT)
                .dynamicTooltip(() -> EchoText.lines("library.gui.blank"))
                .setPos(left, DESK_Y));
        b.widget(
            new ButtonWidget().setOnClick((click, widget) -> { if (!widget.isClient()) l.select(-1); })
                .setBackground((x, y, w, h, partial) -> LibraryScreen.arrow(x, y, w, h, false))
                .setPos(left + 22, DESK_Y)
                .setSize(12, 18));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> LibraryScreen.chosen(l, x, y, w, h))
                .setPos(left + 36, DESK_Y)
                .setSize(18, 18));
        b.widget(
            new ButtonWidget().setOnClick((click, widget) -> { if (!widget.isClient()) l.select(1); })
                .setBackground((x, y, w, h, partial) -> LibraryScreen.arrow(x, y, w, h, true))
                .setPos(left + 56, DESK_Y)
                .setSize(12, 18));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> LibraryScreen.press(l, x, y, w, h))
                .setPos(left + 72, DESK_Y)
                .setSize(30, 18));
        b.widget(
            new SlotWidget(l.desk, 1).setAccess(true, false)
                .setBackground(FluxMachineGui.SLOT_OUT)
                .setPos(left + 106, DESK_Y));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> LibraryScreen.header(l, x, y, w, h))
                .setPos(6, 5)
                .setSize(W - 12, 14));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> LibraryScreen.state(l, x, y, w, h))
                .setPos(6, stateY)
                .setSize(W - 12, 26));
        return b.build();
    }
}
