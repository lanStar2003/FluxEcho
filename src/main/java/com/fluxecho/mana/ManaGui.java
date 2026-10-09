package com.fluxecho.mana;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import com.fluxecho.FluxEcho;
import com.fluxecho.core.CoreCircuits;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.FluxMachineGui;
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
 * The Mana Echo Spring's own GUI, in the flux world's look (as FluxDepths' collector): the core circuit, the petals
 * and the charge slot on the left; the spring's basin in the middle, the echo rising out of the flux layer and the
 * mana it holds (click it for every tier in NEI); the pools in reach beside it; the hologram switch; and below a
 * readout of its rate, petals, pools and power.
 */
public final class ManaGui {

    public static final int WIDTH = 176, HEIGHT = 220, INVENTORY_Y = 138, TITLE = 0x46C8FF;

    public static final UITexture BACKGROUND = tex("background"), SLOT = tex("slot"), SLOT_CORE = tex("slot_core"),
        SLOT_PETAL = tex("slot_petal"), SLOT_CHARGE = tex("slot_charge"), BUTTON = tex("button"),
        BUTTON_ON = tex("button_on"), ICON_HOLO = tex("icon_holo"), LOGO = tex("logo");

    public static final GUITextureSet TEXTURES = new GUITextureSet().setMainBackground(BACKGROUND)
        .setItemSlot(SLOT)
        .setCoverTab(GTUITextures.TAB_COVER_NORMAL, GTUITextures.TAB_COVER_HIGHLIGHT, GTUITextures.TAB_COVER_DISABLED)
        .setTitleTab(FluxMachineGui.TITLE_TAB, FluxMachineGui.TITLE_TAB, FluxMachineGui.TITLE_TAB_ANGULAR)
        .setGregTechLogo(LOGO);

    private ManaGui() {}

    private static UITexture tex(String name) {
        return UITexture.fullImage(FluxEcho.MODID, "gui/spring/" + name);
    }

    static void build(MTEManaSpring m, ModularWindow.Builder b) {
        sync(m, b);

        b.widget(slot(m, MTEManaSpring.CORE, 9, 25, SLOT_CORE, s -> CoreCircuits.tier(s) > 0, "core"));
        b.widget(slot(m, m.petalSlot(), 9, 45, SLOT_PETAL, MTEManaSpring::isPetal, "petal"));
        b.widget(slot(m, m.chargeSlot(), 9, 65, SLOT_CHARGE, MTEManaSpring::chargeable, "charge"));

        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> ManaScreen.basin(m, x, y, w, h))
                .setNEITransferRect(SpringNei.ID)
                .setPos(31, 23)
                .setSize(72, 60));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> ManaScreen.pools(m, x, y, w, h))
                .dynamicTooltip(() -> EchoText.lines("spring.gui.pools_tip"))
                .setPos(107, 23)
                .setSize(38, 60));

        b.widget(
            new CycleButtonWidget().setToggle(m::hologram, m::setHologram)
                .setStaticTexture(ICON_HOLO)
                .setVariableBackground(BUTTON, BUTTON_ON)
                .addTooltip(state -> ManaText.t("gui.holo." + (state == 1 ? "on" : "off")))
                .setPos(149, 25)
                .setSize(18, 18));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> ManaScreen.reach(m, x, y, w, h))
                .dynamicTooltip(() -> EchoText.lines("spring.gui.reach_tip", m.guiRange, m.guiHeight))
                .setPos(147, 45)
                .setSize(22, 16));

        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> ManaScreen.header(m, x, y, w, h))
                .setPos(6, 5)
                .setSize(164, 14));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> ManaScreen.readout(m, x, y, w, h))
                .setPos(6, 88)
                .setSize(164, 47));
    }

    /** What the screen shows that only the server knows, its config's numbers among them. */
    private static void sync(MTEManaSpring m, ModularWindow.Builder b) {
        IGregTechTileEntity base = m.getBaseMetaTileEntity();
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> m.mProgresstime, v -> m.mProgresstime = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> m.mMaxProgresstime, v -> m.mMaxProgresstime = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(m::tier, v -> m.clientTier = v));
        b.widget(
            new FakeSyncWidget.IntegerSyncer(
                () -> m.state()
                    .ordinal(),
                v -> m.state = MTEManaSpring.State.of(v)));
        b.widget(new FakeSyncWidget.IntegerSyncer(m::buffer, v -> m.buffer = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(m::bufferCap, v -> m.guiBufferCap = v));
        b.widget(new FakeSyncWidget.LongSyncer(m::credit, v -> m.credit = v));
        b.widget(new FakeSyncWidget.LongSyncer(m::delivered, v -> m.delivered = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(m::manaPerTick, v -> m.guiMana = v));
        b.widget(new FakeSyncWidget.LongSyncer(m::euPerTick, v -> m.guiEUt = v));
        b.widget(new FakeSyncWidget.LongSyncer(m::petalWorth, v -> m.guiPetal = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(m::range, v -> m.guiRange = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(m::height, v -> m.guiHeight = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> m.poolCount, v -> m.poolCount = v));
        b.widget(new FakeSyncWidget.LongSyncer(() -> m.poolMana, v -> m.poolMana = v));
        b.widget(new FakeSyncWidget.LongSyncer(() -> m.poolCap, v -> m.poolCap = v));
        for (int i = 0; i < MTEManaSpring.SHOWN_POOLS; i++) {
            int index = i;
            b.widget(new FakeSyncWidget.IntegerSyncer(() -> m.poolFill[index], v -> m.poolFill[index] = v));
        }
        b.widget(new FakeSyncWidget.LongSyncer(base::getStoredEU, v -> m.guiEU = v));
        b.widget(new FakeSyncWidget.LongSyncer(base::getEUCapacity, v -> m.guiEUCap = v));
    }

    private static SlotWidget slot(MTEManaSpring m, int index, int x, int y, UITexture background,
        Predicate<ItemStack> filter, String tip) {
        return (SlotWidget) new SlotWidget(m.inventoryHandler, index).setFilter(filter)
            .setBackground(background)
            .dynamicTooltip(() -> tip(tip))
            .setPos(x, y);
    }

    private static List<String> tip(String key) {
        return EchoText.lines("spring.gui.slot." + key);
    }
}
