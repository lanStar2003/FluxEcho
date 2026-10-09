package com.fluxecho.nexus;

import com.fluxecho.Config;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.FluxMachineGui;
import com.fluxecho.nexus.client.NexusScreen;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;

/**
 * The Flux Nexus's GUI, in the flux look: the manifestation table on the left (six inputs, the making drawn in the
 * middle, two outputs, what it can make below), the nexus's state on the right (power, compute, research, ring,
 * bound teams), a button to the research star map and the hologram switch; the player's inventory below.
 */
public final class NexusGui {

    public static final int W = 248, H = 248, INV_Y = 166;
    /** Input slots: 3 x 2 from here. */
    static final int IN_X = 10, IN_Y = 36, OUT_X = 114;

    private NexusGui() {}

    static ModularWindow window(TileNexus n, UIBuildContext ctx) {
        ModularWindow.Builder b = ModularWindow.builder(W, H);
        b.setBackground((x, y, w, h, partial) -> NexusScreen.background(n, x, y, w, h));
        sync(n, b);
        b.bindPlayerInventory(ctx.getPlayer(), new Pos2d((W - 162) / 2, INV_Y), FluxMachineGui.SLOT);

        for (int i = 0; i < TileNexus.INPUTS; i++) b.widget(
            new SlotWidget(n.inv, i).setAccess(true, true)
                .setBackground(FluxMachineGui.SLOT)
                .setPos(IN_X + i % 3 * 18, IN_Y + i / 3 * 18));
        for (int i = 0; i < TileNexus.OUTPUTS; i++) b.widget(
            new SlotWidget(n.inv, TileNexus.INPUTS + i).setAccess(true, false)
                .setBackground(FluxMachineGui.SLOT_OUT)
                .setPos(OUT_X, IN_Y + i * 18));

        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> NexusScreen.header(n, x, y, w, h))
                .setPos(6, 5)
                .setSize(W - 12, 14));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> NexusScreen.making(n, x, y, w, h))
                .setNEITransferRect(NexusNei.ID)
                .setPos(68, 34)
                .setSize(42, 40));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> NexusScreen.table(n, x, y, w, h))
                .setPos(6, 78)
                .setSize(130, 80));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> NexusScreen.state(n, x, y, w, h))
                .setPos(140, 22)
                .setSize(102, 98));

        b.widget(
            new ButtonWidget().setOnClick((click, widget) -> { if (widget.isClient()) NexusScreen.openStarMap(n); })
                .setBackground(
                    (x, y, w, h, partial) -> NexusScreen.button(EchoText.t("nexus.gui.star_map"), x, y, w, h, false))
                .addTooltip(EchoText.t("nexus.gui.star_map.tip"))
                .setPos(140, 124)
                .setSize(58, 18));
        b.widget(
            new ButtonWidget().setOnClick((click, widget) -> { if (!widget.isClient()) n.setHologram(!n.hologram()); })
                .setBackground(
                    (x, y, w, h, partial) -> NexusScreen
                        .button(EchoText.t("nexus.gui.holo"), x, y, w, h, n.clientHologram))
                .addTooltip(EchoText.t("nexus.gui.holo.tip"))
                .setPos(200, 124)
                .setSize(42, 18));
        b.widget(new FakeSyncWidget.BooleanSyncer(n::openBinding, v -> n.guiOpenBinding = v));
        boolean owner = n.team() != null && n.team()
            .equals(
                com.fluxecho.core.Owners.team(
                    ctx.getPlayer()
                        .getUniqueID()));
        b.widget(
            new ButtonWidget()
                .setOnClick((click, widget) -> { if (!widget.isClient() && owner) n.setOpenBinding(!n.openBinding()); })
                .setBackground(
                    (x, y, w, h, partial) -> NexusScreen.button(
                        EchoText.t(n.guiOpenBinding ? "nexus.gui.binding_open" : "nexus.gui.binding_closed"),
                        x,
                        y,
                        w,
                        h,
                        n.guiOpenBinding))
                .addTooltip(EchoText.t(owner ? "nexus.gui.binding.tip" : "nexus.gui.binding.owner_only"))
                .setPos(140, 144)
                .setSize(102, 16));
        return b.build();
    }

    /** What the screen shows that only the server knows. */
    private static void sync(TileNexus n, ModularWindow.Builder b) {
        b.widget(new FakeSyncWidget.StringSyncer(n::status, v -> n.guiStatus = v));
        b.widget(new FakeSyncWidget.StringSyncer(n::research, v -> n.guiResearch = v));
        b.widget(new FakeSyncWidget.StringSyncer(n::manifest, v -> n.guiManifest = v));
        b.widget(new FakeSyncWidget.StringSyncer(n::balance, v -> n.guiBalance = v));
        b.widget(new FakeSyncWidget.StringSyncer(n::unlockedList, v -> n.guiUnlocked = v));
        b.widget(new FakeSyncWidget.LongSyncer(n::upkeep, v -> n.guiUpkeep = v));
        b.widget(
            new FakeSyncWidget.IntegerSyncer(() -> (int) Math.round(n.computeRate() * 10), v -> n.guiCompute10 = v));
        b.widget(
            new FakeSyncWidget.IntegerSyncer(() -> Math.round(n.researchFraction() * 1000), v -> n.guiResearchPm = v));
        b.widget(
            new FakeSyncWidget.IntegerSyncer(() -> Math.round(n.manifestFraction() * 1000), v -> n.guiManifestPm = v));
        b.widget(
            new FakeSyncWidget.IntegerSyncer(
                () -> n.docked()
                    .size(),
                v -> n.guiDocked = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(n::openSlots, v -> n.guiOpen = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(n::boundCount, v -> n.guiBound = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(n::recordCount, v -> n.guiRecords = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(n::restingCount, v -> n.guiResting = v));
    }

    /** The cooldown of a codex entry in minutes, for the GUI. */
    public static int cooldownMinutes() {
        return Math.max(1, Config.recordCooldown / 1200);
    }
}
