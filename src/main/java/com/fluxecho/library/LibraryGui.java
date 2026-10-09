package com.fluxecho.library;

import com.fluxecho.core.EchoText;
import com.fluxecho.core.FluxMachineGui;
import com.fluxecho.library.client.LibraryScreen;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;

/**
 * The Echo Library's GUI: its shelves of samples (one of each), the card desk below (blank cards in, the chosen
 * sample between the arrows, written cards out), its state, and the player's inventory.
 */
public final class LibraryGui {

    public static final int W = 184;

    private LibraryGui() {}

    /** Rows of sample slots for the library's capacity. */
    static int rows(TileLibrary l) {
        return (l.capacity() + 8) / 9;
    }

    public static int deskY(TileLibrary l) {
        return 24 + rows(l) * 18 + 6;
    }

    static ModularWindow window(TileLibrary l, UIBuildContext ctx) {
        int rows = rows(l), deskY = deskY(l), stateY = deskY + 24, invY = stateY + 30;
        ModularWindow.Builder b = ModularWindow.builder(W, invY + 82);
        b.setBackground((x, y, w, h, partial) -> LibraryScreen.background(l, x, y, w, h, rows));
        b.bindPlayerInventory(ctx.getPlayer(), new Pos2d((W - 162) / 2, invY), FluxMachineGui.SLOT);
        b.widget(new FakeSyncWidget.StringSyncer(l::dockStatus, v -> l.guiDock = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(l::lendsShown, v -> l.guiLends = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> l.selected, v -> l.guiSelected = v));
        b.widget(new FakeSyncWidget.BooleanSyncer(l::powered, v -> l.guiPowered = v));

        int left = (W - 162) / 2;
        for (int i = 0; i < l.capacity(); i++) b.widget(
            new SlotWidget(l.samples, i).setAccess(true, true)
                .setBackground(FluxMachineGui.SLOT_SAMPLE)
                .setPos(left + i % 9 * 18, 24 + i / 9 * 18));

        b.widget(
            new SlotWidget(l.desk, 0).setAccess(true, true)
                .setBackground(FluxMachineGui.SLOT)
                .dynamicTooltip(() -> EchoText.lines("library.gui.blank"))
                .setPos(left, deskY));
        b.widget(
            new ButtonWidget().setOnClick((click, widget) -> { if (!widget.isClient()) l.select(-1); })
                .setBackground((x, y, w, h, partial) -> LibraryScreen.arrow(x, y, w, h, false))
                .setPos(left + 22, deskY)
                .setSize(12, 18));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> LibraryScreen.chosen(l, x, y, w, h))
                .setPos(left + 36, deskY)
                .setSize(18, 18));
        b.widget(
            new ButtonWidget().setOnClick((click, widget) -> { if (!widget.isClient()) l.select(1); })
                .setBackground((x, y, w, h, partial) -> LibraryScreen.arrow(x, y, w, h, true))
                .setPos(left + 56, deskY)
                .setSize(12, 18));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> LibraryScreen.press(l, x, y, w, h))
                .setPos(left + 72, deskY)
                .setSize(30, 18));
        b.widget(
            new SlotWidget(l.desk, 1).setAccess(true, false)
                .setBackground(FluxMachineGui.SLOT_OUT)
                .setPos(left + 106, deskY));
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
