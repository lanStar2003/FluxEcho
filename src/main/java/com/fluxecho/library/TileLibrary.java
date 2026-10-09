package com.fluxecho.library;

import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.fluxecho.Config;
import com.fluxecho.core.Directory;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.Formed;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.LibraryShape;
import com.fluxecho.nexus.TileModule;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.research.Research;
import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.core.ServerEvents;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.modularui.api.forge.ItemStackHandler;
import com.gtnewhorizons.modularui.api.screen.ITileWithModularUI;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;

/**
 * The Echo Library (blueprint 3.7), the first module of the Flux Nexus: it keeps one of each sample, and writes index
 * cards for them. Docked on its nexus's ring and powered, it lends its samples to every echo machine of its team that
 * holds a card for one ({@link Lending}): in the same dimension, or anywhere once the team researched
 * {@code library_reach}.
 */
public class TileLibrary extends TileModule implements ITileWithModularUI {

    public static final int MAX = 54, COLOR = 0x8A5CFF;

    private static IStructureDefinition<TileMultiblock> definition;

    final ItemStackHandler samples = new ItemStackHandler(MAX) {

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack s) {
            return slot < capacity() && s != null && !ItemLibraryCard.written(s) && !holdsElsewhere(s, slot);
        }

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
            count = -1;
        }
    };
    /** The card desk: blank cards in, written cards out. */
    final ItemStackHandler desk = new ItemStackHandler(2) {

        @Override
        public boolean isItemValid(int slot, ItemStack s) {
            return slot == 0 && s != null && s.getItem() instanceof ItemLibraryCard && !ItemLibraryCard.written(s);
        }

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    };

    int selected;
    private boolean powered;
    private int lends, lendsShown, count = -1;

    // the client's copy
    public boolean clientPowered;
    public int clientSamples, clientLends;
    public String clientDock = "unformed";

    // the GUI's
    public String guiDock = "unformed";
    public int guiLends, guiSelected;
    public boolean guiPowered;

    @Override
    public String moduleKey() {
        return "library";
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    protected int[] centreCell() {
        return new int[] { 4, 7, 4 };
    }

    @Override
    protected Blueprint blueprint() {
        return LibraryShape.BLUEPRINT;
    }

    @Override
    protected IStructureDefinition<TileMultiblock> definition() {
        if (definition == null) {
            BlockFrame f = FrameModule.frame;
            definition = StructureDefinition.<TileMultiblock>builder()
                .addShape(MAIN, StructureUtility.transpose(LibraryShape.BLUEPRINT.shape()))
                .addElement(LibraryShape.FOUNDATION, recorded(LibraryShape.FOUNDATION, f, BlockFrame.FOUNDATION))
                .addElement(LibraryShape.PILLAR, recorded(LibraryShape.PILLAR, f, BlockFrame.PILLAR))
                .addElement(LibraryShape.SHELF, recorded(LibraryShape.SHELF, f, BlockFrame.SHELF))
                .build();
        }
        return definition;
    }

    @Override
    protected int flags(char ch) {
        return LibraryShape.opens(ch) ? Formed.HIDE : 0;
    }

    @Override
    protected String descriptionKey() {
        return "library.structure";
    }

    public int capacity() {
        return Math.max(1, Math.min(MAX, Config.libraryCapacity));
    }

    // ---- lending

    /** Whether it lends to a machine of the team in the dimension now. */
    boolean lends(UUID team, int dim) {
        if (!formed || !powered || team == null || !team.equals(team())) return false;
        TileNexus n = nexus();
        if (n == null) return false;
        return dim == worldObj.provider.dimensionId || n.has(Research.LIBRARY_REACH);
    }

    /** Whether it keeps the sample (item, damage and NBT alike). */
    boolean holds(ItemStack s) {
        return holdsElsewhere(s, -1);
    }

    private boolean holdsElsewhere(ItemStack s, int except) {
        if (s == null) return false;
        for (int i = 0; i < capacity(); i++) {
            if (i == except) continue;
            ItemStack x = samples.getStackInSlot(i);
            if (x != null && x.getItem() == s.getItem()
                && x.getItemDamage() == s.getItemDamage()
                && ItemStack.areItemStackTagsEqual(x, s)) return true;
        }
        return false;
    }

    void lent() {
        lends++;
    }

    /** Samples it keeps. */
    public int sampleCount() {
        if (count < 0) {
            int n = 0;
            for (int i = 0; i < capacity(); i++) if (samples.getStackInSlot(i) != null) n++;
            count = n;
        }
        return count;
    }

    /** The sample the card desk writes cards for: the selected slot, or the next kept one after it. */
    public ItemStack selectedSample() {
        return selectedSample(selected);
    }

    /** As {@link #selectedSample()} from slot {@code from} (the client's copy of the choice). */
    public ItemStack selectedSample(int from) {
        int cap = capacity();
        for (int k = 0; k < cap; k++) {
            ItemStack s = samples.getStackInSlot(Math.floorMod(from + k, cap));
            if (s != null) return s;
        }
        return null;
    }

    /** Moves the desk's choice to the next (or previous) kept sample. */
    void select(int step) {
        int cap = capacity();
        for (int k = 1; k <= cap; k++) {
            int i = Math.floorMod(selected + step * k, cap);
            if (samples.getStackInSlot(i) != null) {
                selected = i;
                markDirty();
                return;
            }
        }
    }

    public boolean powered() {
        return powered;
    }

    public int lendsShown() {
        return lendsShown;
    }

    @Override
    protected void serverTick() {
        super.serverTick();
        long t = worldObj.getTotalWorldTime();
        boolean was = powered;
        TileNexus n = nexus();
        UUID team = team();
        if (formed && n != null && Config.nexusEnabled && team != null) {
            long eu = Config.libraryUpkeep;
            powered = eu <= 0 || GTWirelessBackend.INSTANCE.add(team, BigInteger.valueOf(-eu));
            if (powered && eu > 0) ServerEvents.addTeamTick(team, 0, eu, eu, true);
        } else powered = false;
        if (t % 10 == 0) writeCard();
        if (t % 100 == 0) {
            String dock = dockStatus();
            Directory.report(
                worldObj,
                xCoord,
                yCoord,
                zCoord,
                team,
                "tile.fluxecho.library.name",
                "fluxecho.dock." + (powered || !"docked".equals(dock) ? dock : "no_power"),
                !"docked".equals(dock) || !powered ? Directory.PROBLEM
                    : lendsShown > 0 ? Directory.WORKING : Directory.IDLE);
            if (lends != lendsShown) {
                lendsShown = lends;
                sync();
            }
            lends = 0;
        }
        if (was != powered) sync();
    }

    /** The desk writes one card per blank one for the chosen sample. */
    private void writeCard() {
        ItemStack blank = desk.getStackInSlot(0), out = desk.getStackInSlot(1);
        if (blank == null || out != null) return;
        ItemStack s = selectedSample();
        if (s == null) return;
        desk.extractItem(0, 1, false);
        desk.setStackInSlot(1, ItemLibraryCard.write(s, team()));
    }

    // ---- client copy

    @Override
    protected void writeSync(NBTTagCompound t) {
        super.writeSync(t);
        t.setBoolean("P", powered);
        t.setInteger("S", sampleCount());
        t.setInteger("L", lendsShown);
        t.setString("Dk", dockStatus());
    }

    @Override
    protected void readSync(NBTTagCompound t) {
        super.readSync(t);
        clientPowered = t.getBoolean("P");
        clientSamples = t.getInteger("S");
        clientLends = t.getInteger("L");
        clientDock = t.getString("Dk");
    }

    @Override
    public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setTag("Samples", samples.serializeNBT());
        t.setTag("Desk", desk.serializeNBT());
        t.setInteger("Selected", selected);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        samples.deserializeNBT(t.getCompoundTag("Samples"));
        if (samples.getSlots() != MAX) samples.setSize(MAX);
        desk.deserializeNBT(t.getCompoundTag("Desk"));
        if (desk.getSlots() != 2) desk.setSize(2);
        selected = t.getInteger("Selected");
        count = -1;
    }

    @Override
    public ModularWindow createWindow(UIBuildContext ctx) {
        return LibraryGui.window(this, ctx);
    }

    /** Everything it holds, for when its core is broken. */
    public java.util.List<ItemStack> contents() {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (int i = 0; i < samples.getSlots(); i++)
            if (samples.getStackInSlot(i) != null) out.add(samples.getStackInSlot(i));
        for (int i = 0; i < desk.getSlots(); i++) if (desk.getStackInSlot(i) != null) out.add(desk.getStackInSlot(i));
        return out;
    }
}
