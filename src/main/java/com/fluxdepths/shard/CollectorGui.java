package com.fluxdepths.shard;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.fluxdepths.FluxDepths;
import com.fluxdepths.client.CollectorScreen;
import com.fluxdepths.item.ItemImprint;
import com.fluxdepths.nei.VeinHandler;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.drawable.UITexture;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.common.widget.CycleButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.FluidSlotWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;

import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.gui.modularui.GUITextureSet;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The Flux Shard Collector's own GUI, in the flux world's look rather than GT's: a core slot for the circuit, a drill
 * head slot and a drilling fluid tank on the left, the four imprint slots (the ones the tier does not use locked), the
 * depth shaft in the middle (its progress; click it for the veins in NEI), the outputs, the hologram and auto-output
 * switches, and below a readout of power, speed, drill wear and the vein it echoes.
 */
public final class CollectorGui {

    public static final int WIDTH = 176, HEIGHT = 220, INVENTORY_Y = 138, TITLE = 0x55E0FF;

    public static final UITexture BACKGROUND = tex("collector"), SLOT = tex("slot"), SLOT_CORE = tex("slot_core"),
        SLOT_HEAD = tex("slot_head"), SLOT_IMPRINT = tex("slot_imprint"), SLOT_FLUID = tex("slot_fluid"),
        LOCK = tex("lock"), BUTTON = tex("button"), BUTTON_ON = tex("button_on"), ICON_HOLO = tex("icon_holo"),
        ICON_OUTPUT = tex("icon_output"), LOGO = tex("logo");

    public static final GUITextureSet TEXTURES = new GUITextureSet().setMainBackground(BACKGROUND)
        .setItemSlot(SLOT)
        .setFluidSlot(SLOT_FLUID)
        .setCoverTab(GTUITextures.TAB_COVER_NORMAL, GTUITextures.TAB_COVER_HIGHLIGHT, GTUITextures.TAB_COVER_DISABLED)
        .setTitleTab(GTUITextures.TAB_TITLE, GTUITextures.TAB_TITLE_DARK, GTUITextures.TAB_TITLE_ANGULAR)
        .setGregTechLogo(LOGO);

    private CollectorGui() {}

    private static UITexture tex(String name) {
        return UITexture.fullImage(FluxDepths.MODID, "gui/" + name);
    }

    static void build(MTEFluxCollector m, ModularWindow.Builder b) {
        sync(m, b);

        b.widget(slot(m, MTEFluxCollector.CORE, 9, 25, SLOT_CORE, s -> CoreCircuits.tier(s) > 0, "core"));
        b.widget(slot(m, m.headSlot(), 9, 45, SLOT_HEAD, s -> DrillHeads.uses(s) > 0, "head"));
        b.widget(
            new FluidSlotWidget(m.drillingTank()).setBackground(SLOT_FLUID)
                .dynamicTooltip(() -> tip("fluid"))
                .setPos(9, 65));
        for (int i = 0; i < ShardTier.MAX_IMPRINTS; i++) {
            int index = i, x = 31 + i % 2 * 18, y = 25 + i / 2 * 18;
            b.widget(slot(m, m.imprintSlot(i), x, y, SLOT_IMPRINT, s -> ItemImprint.vein(s) != null, "imprint"));
            b.widget(
                new DrawableWidget().setDrawable(() -> index >= m.tier().imprints ? LOCK : IDrawable.EMPTY)
                    .setPos(x, y)
                    .setSize(18, 18));
        }
        for (int i = 0; i < 4; i++) b.widget(
            new SlotWidget(m.inventoryHandler, m.getOutputSlot() + i).setAccess(true, false)
                .setBackground(SLOT)
                .setPos(107 + i % 2 * 18, 25 + i / 2 * 18));

        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> CollectorScreen.shaft(m, x, y, w, h))
                .setNEITransferRect(VeinHandler.ID)
                .setPos(71, 23)
                .setSize(32, 58));

        b.widget(
            new CycleButtonWidget().setToggle(m::hologram, m::setHologram)
                .setStaticTexture(ICON_HOLO)
                .setVariableBackground(BUTTON, BUTTON_ON)
                .addTooltip(
                    state -> StatCollector.translateToLocal("fluxdepths.gui.holo." + (state == 1 ? "on" : "off")))
                .setPos(149, 25)
                .setSize(18, 18));
        b.widget(
            new CycleButtonWidget().setToggle(() -> m.mItemTransfer, v -> m.mItemTransfer = v)
                .setStaticTexture(ICON_OUTPUT)
                .setVariableBackground(BUTTON, BUTTON_ON)
                .addTooltip(
                    state -> StatCollector.translateToLocal("fluxdepths.gui.output." + (state == 1 ? "on" : "off")))
                .setPos(149, 43)
                .setSize(18, 18));

        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> CollectorScreen.header(m, x, y, w, h))
                .setPos(6, 5)
                .setSize(164, 14));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> CollectorScreen.imprintCaption(m, x, y, w, h))
                .setPos(31, 62)
                .setSize(36, 9));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> CollectorScreen.speedCaption(m, x, y, w, h))
                .setPos(107, 62)
                .setSize(36, 9));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> CollectorScreen.readout(m, x, y, w, h))
                .setPos(6, 88)
                .setSize(164, 47));
    }

    /** What the screen shows that only the server knows. */
    private static void sync(MTEFluxCollector m, ModularWindow.Builder b) {
        ShardState s = m.state();
        IGregTechTileEntity base = m.getBaseMetaTileEntity();
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> m.mProgresstime, v -> m.mProgresstime = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> m.mMaxProgresstime, v -> m.mMaxProgresstime = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> s.status.ordinal(), v -> s.status = ShardState.status(v)));
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> s.headUses, v -> s.headUses = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> s.imprints, v -> s.imprints = v));
        b.widget(new FakeSyncWidget.LongSyncer(() -> s.produced, v -> s.produced = v));
        b.widget(new FakeSyncWidget.StringSyncer(m::veinName, v -> m.guiVein = v));
        b.widget(new FakeSyncWidget.LongSyncer(base::getStoredEU, v -> m.guiEU = v));
        b.widget(new FakeSyncWidget.LongSyncer(base::getEUCapacity, v -> m.guiEUCap = v));
        b.widget(new FakeSyncWidget.LongSyncer(base::getStoredSteam, v -> m.guiSteam = v));
    }

    private static SlotWidget slot(MTEFluxCollector m, int index, int x, int y, UITexture background,
        Predicate<ItemStack> filter, String tip) {
        return (SlotWidget) new SlotWidget(m.inventoryHandler, index).setFilter(filter)
            .setBackground(background)
            .dynamicTooltip(() -> tip(tip))
            .setPos(x, y);
    }

    private static List<String> tip(String key) {
        return Arrays.asList(
            StatCollector.translateToLocal("fluxdepths.gui." + key)
                .split("\\\\n"));
    }
}
