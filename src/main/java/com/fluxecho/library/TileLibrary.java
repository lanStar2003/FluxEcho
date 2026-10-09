package com.fluxecho.library;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;

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
import com.gtnewhorizons.modularui.api.forge.ItemStackHandler;
import com.gtnewhorizons.modularui.api.screen.ITileWithModularUI;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;

/**
 * The Echo Library (blueprint 3.7), the first module of the Flux Nexus: a hall whose shelves each keep one sample as a
 * book, and a reading desk that writes index cards for them. Players walk in and use the shelves themselves
 * ({@link Shelves}); the desk (or the controller) opens the same shelves as a GUI. Docked on its nexus's ring and
 * powered, it lends its samples to every echo machine of its team that holds a card for one ({@link Lending}): in the
 * same dimension, or anywhere once the team researched {@code library_reach}.
 */
public class TileLibrary extends TileModule implements ITileWithModularUI {

    /** One sample a shelf. */
    public static final int MAX = LibraryShape.SHELVES.size(), COLOR = 0x8A5CFF;
    /**
     * How much item NBT the books take to the client at most (characters of its text form, about its size in bytes):
     * a hall full of bees would not fit in one packet. Books past it show their item without the NBT.
     */
    private static final int BOOK_NBT = 48_000;

    private static IStructureDefinition<TileMultiblock> definition, buildDefinition;

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
            booksChanged = true;
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
    private boolean powered, booksChanged;
    private int lends, lendsShown, count = -1;
    /** Shelf block -> sample slot, for the position and facing it was worked out for. */
    private Map<Long, Integer> shelfMap;
    private final int[] shelfMapFor = new int[4];

    // the client's copy
    public boolean clientPowered;
    public int clientSamples, clientLends;
    public String clientDock = "unformed";
    /** The books on the shelves, by slot. */
    public final ItemStack[] clientBooks = new ItemStack[MAX];

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
        return LibraryShape.cellOf(LibraryShape.SIZE / 2, 0, LibraryShape.SIZE / 2);
    }

    @Override
    protected Blueprint blueprint() {
        return LibraryShape.BLUEPRINT;
    }

    @Override
    protected IStructureDefinition<TileMultiblock> definition() {
        if (definition == null)
            definition = define(true, new String[] { MAIN }, new Blueprint[] { LibraryShape.BLUEPRINT }, parts());
        return definition;
    }

    @Override
    protected IStructureDefinition<TileMultiblock> buildDefinition() {
        if (buildDefinition == null)
            buildDefinition = define(false, new String[] { MAIN }, new Blueprint[] { LibraryShape.BLUEPRINT }, parts());
        return buildDefinition;
    }

    private static Object[] parts() {
        BlockFrame f = FrameModule.frame;
        return new Object[] { LibraryShape.FOUNDATION, f, BlockFrame.FOUNDATION, LibraryShape.PILLAR, f,
            BlockFrame.PILLAR, LibraryShape.SHELF, f, BlockFrame.SHELF, LibraryShape.BASE, f, BlockFrame.BASE,
            LibraryShape.CONSOLE, f, BlockFrame.CONSOLE };
    }

    @Override
    protected Block coreBlock() {
        return LibraryModule.core;
    }

    /** Nothing gives way: the hall is walked into as it stands. */
    @Override
    protected int flags(char ch) {
        return 0;
    }

    @Override
    protected String descriptionKey() {
        return "library.structure";
    }

    public int capacity() {
        return MAX;
    }

    // ---- the hall

    /** Its samples, one a shelf (the GUI's copy on the client). */
    public com.gtnewhorizons.modularui.api.forge.IItemHandlerModifiable samples() {
        return samples;
    }

    /** Where shelf {@code i} (a sample slot) stands. */
    public int[] shelfPos(int i) {
        LibraryShape.Shelf s = LibraryShape.SHELVES.get(i);
        int[] c = LibraryShape.cellOf(s.x, s.y, s.z);
        return cellPos(c[0], c[1], c[2]);
    }

    /** The side (a ForgeDirection ordinal) of shelf {@code i} that faces into the hall, where its book is. */
    public int shelfFace(int i) {
        LibraryShape.Shelf s = LibraryShape.SHELVES.get(i);
        int[] a = shelfPos(i), c = LibraryShape.cellOf(s.x + s.inX, s.y, s.z + s.inZ), b = cellPos(c[0], c[1], c[2]);
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS)
            if (d.offsetX == b[0] - a[0] && d.offsetZ == b[2] - a[2] && d.offsetY == 0) return d.ordinal();
        return ForgeDirection.UNKNOWN.ordinal();
    }

    /** The sample slot of the shelf at the position, or -1 when it is not one of the hall's shelves. */
    public int shelfAt(int x, int y, int z) {
        if (shelfMap == null || shelfMapFor[0] != xCoord
            || shelfMapFor[1] != yCoord
            || shelfMapFor[2] != zCoord
            || shelfMapFor[3] != facing) {
            Map<Long, Integer> m = new HashMap<>();
            for (int i = 0; i < MAX; i++) {
                int[] p = shelfPos(i);
                m.put(Formed.key(p[0], p[1], p[2]), i);
            }
            shelfMap = m;
            shelfMapFor[0] = xCoord;
            shelfMapFor[1] = yCoord;
            shelfMapFor[2] = zCoord;
            shelfMapFor[3] = facing;
        }
        Integer i = shelfMap.get(Formed.key(x, y, z));
        return i == null ? -1 : i;
    }

    /** Where the reading desk stands. */
    public int[] deskPos() {
        int[] c = LibraryShape.cellOf(LibraryShape.SIZE / 2, LibraryShape.HALL_LOW, LibraryShape.SIZE / 2);
        return cellPos(c[0], c[1], c[2]);
    }

    @Override
    public boolean consoleAt(int x, int y, int z) {
        int[] d = deskPos();
        return d[0] == x && d[1] == y && d[2] == z;
    }

    /** The book on shelf {@code i}: the sample on the server, the client's copy on the client. */
    public ItemStack book(int i) {
        if (i < 0 || i >= MAX) return null;
        return worldObj != null && worldObj.isRemote ? clientBooks[i] : samples.getStackInSlot(i);
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

    boolean holdsElsewhere(ItemStack s, int except) {
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
            int i = Math.floorMod(from + k, cap);
            ItemStack s = worldObj != null && worldObj.isRemote ? clientBooks[i] : samples.getStackInSlot(i);
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
        if (was != powered || booksChanged) {
            booksChanged = false;
            sync();
        }
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
        NBTTagList books = new NBTTagList();
        int nbt = 0;
        for (int i = 0; i < MAX; i++) {
            ItemStack s = samples.getStackInSlot(i);
            if (s == null) continue;
            ItemStack shown = s;
            if (s.hasTagCompound()) {
                nbt += s.getTagCompound()
                    .toString()
                    .length();
                if (nbt > BOOK_NBT) {
                    shown = s.copy();
                    shown.setTagCompound(null);
                }
            }
            NBTTagCompound b = shown.writeToNBT(new NBTTagCompound());
            b.setShort("At", (short) i);
            books.appendTag(b);
        }
        t.setTag("B", books);
    }

    @Override
    protected void readSync(NBTTagCompound t) {
        super.readSync(t);
        clientPowered = t.getBoolean("P");
        clientSamples = t.getInteger("S");
        clientLends = t.getInteger("L");
        clientDock = t.getString("Dk");
        java.util.Arrays.fill(clientBooks, null);
        NBTTagList books = t.getTagList("B", 10);
        for (int k = 0; k < books.tagCount(); k++) {
            NBTTagCompound b = books.getCompoundTagAt(k);
            int i = b.getShort("At");
            if (i >= 0 && i < MAX) clientBooks[i] = ItemStack.loadItemStackFromNBT(b);
        }
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
        // read through a handler of the saved size: before 0.9.2 a library kept 54 (setSize would empty it)
        ItemStackHandler saved = new ItemStackHandler(0);
        saved.deserializeNBT(t.getCompoundTag("Samples"));
        for (int i = 0; i < MAX; i++) samples.setStackInSlot(i, i < saved.getSlots() ? saved.getStackInSlot(i) : null);
        desk.deserializeNBT(t.getCompoundTag("Desk"));
        if (desk.getSlots() != 2) desk.setSize(2);
        selected = t.getInteger("Selected");
        count = -1;
        booksChanged = false;
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
