package com.fluxecho.core;

import net.minecraft.item.ItemStack;

import com.fluxecho.FluxEcho;
import com.fluxecho.client.MachineScreen;
import com.fluxecho.logic.SlotLayout;
import com.gtnewhorizons.modularui.api.drawable.AdaptableUITexture;
import com.gtnewhorizons.modularui.api.drawable.UITexture;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.common.widget.CycleButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;

import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.gui.modularui.GUITextureSet;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The echo machines' GUI, in the flux world's look (as FluxDepths' collector and the Mana Echo Spring): the sample
 * in its golden slot and GT's battery slot on the left, the inputs, the machine's work drawn in the middle (its own
 * motif; click it for its NEI page), the outputs, the hologram and auto-output switches and the programmed circuit on
 * the right, and below a readout of power, the sample and the machine's own state. The slots are laid out from how
 * many inputs and outputs the machine has.
 */
public final class FluxMachineGui {

    public static final int WIDTH = 176, HEIGHT = 220, INVENTORY_Y = 138;

    public static final UITexture BACKGROUND = tex("background"), SLOT = tex("slot"), SLOT_OUT = tex("slot_out"),
        SLOT_SAMPLE = tex("slot_sample"), BUTTON = tex("button"), BUTTON_ON = tex("button_on"),
        ICON_HOLO = tex("icon_holo"), ICON_OUTPUT = tex("icon_output"), LOGO = tex("logo");

    /** The dark tab the title sits on, in the machine's colour, in place of GT's light grey one. */
    public static final AdaptableUITexture TITLE_TAB = AdaptableUITexture
        .of(FluxEcho.MODID, "gui/flux/title_tab", 28, 28, 4),
        TITLE_TAB_ANGULAR = AdaptableUITexture.of(FluxEcho.MODID, "gui/flux/title_tab_angular", 28, 28, 4);

    public static final GUITextureSet TEXTURES = new GUITextureSet().setMainBackground(BACKGROUND)
        .setItemSlot(SLOT)
        .setCoverTab(GTUITextures.TAB_COVER_NORMAL, GTUITextures.TAB_COVER_HIGHLIGHT, GTUITextures.TAB_COVER_DISABLED)
        .setTitleTab(TITLE_TAB, TITLE_TAB, TITLE_TAB_ANGULAR)
        .setGregTechLogo(LOGO);

    /** Top and height of the work pane's slot area. */
    private static final int AREA_TOP = 23, AREA_H = 60;

    private FluxMachineGui() {}

    private static UITexture tex(String name) {
        return UITexture.fullImage(FluxEcho.MODID, "gui/flux/" + name);
    }

    /** NEI's id of the machine's page ({@code FluxRecipeHandler}). */
    public static String neiId(MachineId kind) {
        return "fluxecho.machine." + kind.key;
    }

    static void build(MTEEchoMachine m, ModularWindow.Builder b) {
        sync(m, b);
        MachineId k = m.kind();

        if (k.sample) b.widget(
            new SlotWidget(m.inventoryHandler, m.getSpecialSlotIndex()).setAccess(true, true)
                .disableShiftInsert()
                .setBackground(SLOT_SAMPLE)
                .dynamicTooltip(() -> EchoText.lines(k.key + ".gui_sample"))
                .setPos(9, 25));
        b.widget(m.battery(9, 65));

        int[][] in = SlotLayout.grid(m.inputCount(), 31, AREA_TOP + 1, AREA_H - 1, false);
        for (int i = 0; i < in.length; i++) b.widget(
            new SlotWidget(m.inventoryHandler, m.getInputSlot() + i).setAccess(true, true)
                .setBackground(SLOT)
                .setPos(in[i][0], in[i][1]));
        int[][] out = SlotLayout.grid(m.outputSlots(), 145, AREA_TOP + 1, AREA_H - 1, true);
        for (int i = 0; i < out.length; i++) b.widget(
            new SlotWidget(m.inventoryHandler, m.getOutputSlot() + i).setAccess(true, false)
                .setBackground(SLOT_OUT)
                .setPos(out[i][0], out[i][1]));

        int left = SlotLayout.right(in) + 4, right = SlotLayout.left(out) - 4;
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> MachineScreen.panel(m, x, y, w, h))
                .setNEITransferRect(neiId(k))
                .setPos(left, AREA_TOP)
                .setSize(Math.max(8, right - left), AREA_H));

        b.widget(
            new CycleButtonWidget().setToggle(m::hologram, m::setHologram)
                .setStaticTexture(ICON_HOLO)
                .setVariableBackground(BUTTON, BUTTON_ON)
                .addTooltip(state -> EchoText.t("gui.holo." + (state == 1 ? "on" : "off")))
                .setPos(149, 25)
                .setSize(18, 18));
        b.widget(
            new CycleButtonWidget().setToggle(() -> m.mItemTransfer, v -> m.mItemTransfer = v)
                .setStaticTexture(ICON_OUTPUT)
                .setVariableBackground(BUTTON, BUTTON_ON)
                .addTooltip(state -> EchoText.t("gui.output." + (state == 1 ? "on" : "off")))
                .setPos(149, 43)
                .setSize(18, 18));

        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> MachineScreen.header(m, m.guiStatus, x, y, w, h))
                .setPos(6, 5)
                .setSize(164, 14));
        b.widget(
            new DrawableWidget()
                .setDrawable(
                    (x, y, w, h, partial) -> MachineScreen
                        .readout(m, sample(m), m.guiInfo, m.guiEU, m.guiEUCap, m.guiEut, x, y, w, h))
                .setPos(6, 88)
                .setSize(164, 47));
    }

    private static ItemStack sample(MTEEchoMachine m) {
        return m.kind().sample ? m.getStackInSlot(m.getSpecialSlotIndex()) : null;
    }

    /** What the screen shows that only the server knows. */
    private static void sync(MTEEchoMachine m, ModularWindow.Builder b) {
        IGregTechTileEntity base = m.getBaseMetaTileEntity();
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> m.mProgresstime, v -> m.mProgresstime = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> m.mMaxProgresstime, v -> m.mMaxProgresstime = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> m.mMaxProgresstime > 0 ? m.mEUt : 0, v -> m.guiEut = v));
        b.widget(new FakeSyncWidget.StringSyncer(m::shownStatus, v -> m.guiStatus = v));
        b.widget(new FakeSyncWidget.StringSyncer(m::info, v -> m.guiInfo = v));
        b.widget(new FakeSyncWidget.LongSyncer(base::getStoredEU, v -> m.guiEU = v));
        b.widget(new FakeSyncWidget.LongSyncer(base::getEUCapacity, v -> m.guiEUCap = v));
    }
}
