package com.fluxecho.library;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.Config;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.campus.PartBlocks;
import com.fluxecho.core.Directory;
import com.fluxecho.core.Owners;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.Formed;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.gate.Sight;
import com.fluxecho.library.client.LibraryBooks;
import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.LibraryShape;
import com.fluxecho.logic.LibraryUnits;
import com.fluxecho.logic.SpineColour;
import com.fluxecho.nexus.BlockNexus;
import com.fluxecho.nexus.ClientTiles;
import com.fluxecho.nexus.NexusNet;
import com.fluxecho.nexus.NexusRegistry;
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
 * The Echo Library (blueprint 3.7), the first module of the Flux Nexus: a building whose shelves each keep one sample
 * as a book, and a reading desk that writes index cards for them. Docked on its nexus and powered, it lends its
 * samples to every echo machine of its team that holds a card for one ({@link Lending}): in the same dimension, or
 * anywhere once the team researched {@code library_reach}.
 * <p>
 * It stands as one of two shapes ({@link TileMultiblock#shapes}). {@link #MAIN} is the Echo Archive (0.10.0,
 * {@link ArchiveShape}), the two-storey library the campus builds: 78 bookcases of nine books each
 * ({@link LibraryUnits}), used a whole bookcase at a time ({@link Units}). {@link #HALL} is the 0.9.2 hall
 * ({@link LibraryShape}) with its 167 wall shelves, used a shelf at a time ({@link Shelves}); libraries saved before
 * 0.10.0 are halls, a new core is an Archive. Both keep their books in one 702-slot handler, slot {@code i} being
 * place {@code i} of the shape; a hall uses the first 167, and a book a hall has no shelf for goes to the team's
 * {@link LibraryVault} rather than being lost. The desk (or the controller, or a lectern) opens the same books as a
 * GUI, the Archive's at the bookcase last used ({@link #selectedUnit}).
 * <p>
 * The client keeps a copy of every book ({@link #clientBooks}): all of them in the description, then only the slots
 * that changed in {@link NexusNet#LIB_DELTA} packets, so filing one book does not resend the whole room. The Archive's
 * books are drawn in its shelf units' chunk mesh ({@link LibraryBooks}) in their spine colours ({@link SpineColour}).
 */
public class TileLibrary extends TileModule implements ITileWithModularUI {

    /** The 0.9.2 hall's shape name; the Archive is {@link #MAIN}. */
    public static final String HALL = "hall";
    /** Books a hall holds (one a shelf) and books an Archive holds (nine a bookcase). */
    public static final int HALL_BOOKS = LibraryShape.SHELVES.size(), ARCHIVE_BOOKS = LibraryUnits.SLOTS;
    /** The sample handler's size: room for the larger shape, so a library that changes shape keeps every book. */
    public static final int SLOTS = Math.max(HALL_BOOKS, ARCHIVE_BOOKS);
    public static final int COLOR = 0x8A5CFF;

    /** The Archive first (what a new core and NEI show), then the hall. */
    private static final List<Shape> SHAPES = Collections.unmodifiableList(
        Arrays.asList(new Shape(MAIN, ArchiveShape.BLUEPRINT), new Shape(HALL, LibraryShape.BLUEPRINT)));

    private static IStructureDefinition<TileMultiblock> definition, buildDefinition;

    final ItemStackHandler samples = new ItemStackHandler(SLOTS) {

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        /** A place of the shape, not a written card, not a book kept elsewhere; nothing once the tile is gone. */
        @Override
        public boolean isItemValid(int slot, ItemStack s) {
            return !isInvalid() && slot < capacity()
                && s != null
                && !ItemLibraryCard.written(s)
                && !holdsElsewhere(s, slot);
        }

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
            count = -1;
            changed.set(slot);
        }
    };
    /** The card desk: blank cards in, written cards out. */
    final ItemStackHandler desk = new ItemStackHandler(2) {

        @Override
        public boolean isItemValid(int slot, ItemStack s) {
            return !isInvalid() && slot == 0
                && s != null
                && s.getItem() instanceof ItemLibraryCard
                && !ItemLibraryCard.written(s);
        }

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    };

    /** The sample the card desk writes cards for (a slot). */
    int selected;
    /** The Archive's bookcase the GUI shows: set by a click on a bookcase and by the GUI (server side). */
    public int selectedUnit;
    /** Whether the team's vault has filled the Archive once (when it first formed). */
    private boolean refilled;
    private boolean powered;
    private int lends, lendsShown, count = -1;
    /** Slots whose book changed since the last delta packet. */
    private final BitSet changed = new BitSet(SLOTS);
    /** Delta packets sent so far, and a number for this tile's life (both tell the client what is newer). */
    private int rev;
    private final int epoch = ThreadLocalRandom.current()
        .nextInt();
    /** Hall shelf block -> sample slot, for the position and facing it was worked out for. */
    private Map<Long, Integer> shelfMap;
    private final int[] shelfMapFor = new int[4];

    // the client's copy
    public boolean clientPowered;
    public int clientSamples, clientLends;
    public String clientDock = "unformed";
    /** The books on the shelves, by slot (the hall uses the first {@link #HALL_BOOKS}). */
    public final ItemStack[] clientBooks = new ItemStack[SLOTS];
    /** Per slot, the revision its book came with ({@link #applyDelta}); the tile life they belong to. */
    private final int[] clientStamp = new int[SLOTS];
    private int clientEpoch;
    private boolean described;
    /** The spine colour of each slot's book (0 for none), kept so a change recolours only the books it touched. */
    private final int[] clientColour = new int[SLOTS];
    /**
     * The book cells of every slot ({@link Formed#key}), for the position, facing and shape they were worked out for.
     */
    private long[] slotKeys;
    private final int[] slotKeysFor = new int[5];

    // the GUI's
    public String guiDock = "unformed";
    public int guiLends, guiSelected;
    public boolean guiPowered;
    /** The bookcase and the vault's book count as an open GUI last heard them (for the GUI's syncers). */
    public int guiUnit, guiVault;

    public TileLibrary() {
        // a new core is an Archive; a save without a shape is a hall (legacyShape)
        shape = MAIN;
        Arrays.fill(clientStamp, Integer.MIN_VALUE);
    }

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
        if (isArchive()) return ArchiveShape.cellOf(ArchiveShape.MID, 0, (ArchiveShape.DEPTH - 1) / 2);
        return LibraryShape.cellOf(LibraryShape.SIZE / 2, 0, LibraryShape.SIZE / 2);
    }

    /** The blueprint of the shape it stands (or last stood) as. */
    @Override
    protected Blueprint blueprint() {
        return current();
    }

    @Override
    protected List<Shape> shapes() {
        return SHAPES;
    }

    /** With {@code archiveEnabled} off only the hall forms. */
    @Override
    protected boolean tries(Shape s) {
        return Config.archiveEnabled || !MAIN.equals(s.name);
    }

    /** Libraries saved before 0.10.0 were halls. */
    @Override
    protected String legacyShape() {
        return HALL;
    }

    /**
     * Placed by hand. On the controller cell of a hall site's Echo Archive (a site of a nexus loaded nearby) the core
     * faces the way the campus builder commissions it there, towards the nexus, whichever way the player stood: the
     * Archive is checked behind the core's front, so a core turned another way would never form, and the builder takes
     * a core that already stands on its cell as placed without turning it.
     */
    @Override
    public void place(EntityPlayer placer, int front) {
        int site = siteFront();
        super.place(placer, site >= 0 ? site : front);
    }

    /** The front an Archive's core has on the controller cell of a hall site of a loaded nexus; -1 elsewhere. */
    private int siteFront() {
        if (worldObj == null) return -1;
        ModuleSpec spec = ModuleSpecs.get(moduleKey());
        if (spec == null || !spec.enabled()) return -1;
        for (TileMultiblock m : NexusRegistry.loaded(worldObj)) {
            if (!(m instanceof TileNexus n)) continue;
            int[] c = n.centre();
            ForgeDirection f = n.front();
            for (int site : spec.sites) {
                int[] at = spec.controller(site, c[0], c[1], c[2], f.offsetX, f.offsetZ);
                if (at != null && at.length > 3 && at[0] == xCoord && at[1] == yCoord && at[2] == zCoord) return at[3];
            }
        }
        return -1;
    }

    @Override
    protected IStructureDefinition<TileMultiblock> definition() {
        if (definition == null) definition = define(true, shapeNames(), shapeBlueprints(), parts());
        return definition;
    }

    @Override
    protected IStructureDefinition<TileMultiblock> buildDefinition() {
        if (buildDefinition == null) buildDefinition = define(false, shapeNames(), shapeBlueprints(), parts());
        return buildDefinition;
    }

    private static String[] shapeNames() {
        String[] out = new String[SHAPES.size()];
        for (int i = 0; i < out.length; i++) out[i] = SHAPES.get(i).name;
        return out;
    }

    private static Blueprint[] shapeBlueprints() {
        Blueprint[] out = new Blueprint[SHAPES.size()];
        for (int i = 0; i < out.length; i++) out[i] = SHAPES.get(i).bp;
        return out;
    }

    /**
     * Every part of both shapes: the hall's frame parts, and each character of the Archive as the block and meta the
     * campus builder places for it ({@link ArchiveShape#partOf}, {@link PartBlocks}). The door cells ({@code -}) are
     * StructureLib's own "must be air" and the controller ({@code ~}) is skipped, as in the hall.
     */
    private static Object[] parts() {
        BlockFrame f = FrameModule.frame;
        List<Object> out = new ArrayList<>(
            Arrays.asList(
                LibraryShape.FOUNDATION,
                f,
                BlockFrame.FOUNDATION,
                LibraryShape.PILLAR,
                f,
                BlockFrame.PILLAR,
                LibraryShape.SHELF,
                f,
                BlockFrame.SHELF,
                LibraryShape.BASE,
                f,
                BlockFrame.BASE,
                LibraryShape.CONSOLE,
                f,
                BlockFrame.CONSOLE));
        Set<Character> seen = new LinkedHashSet<>();
        for (Blueprint.Cell c : ArchiveShape.BLUEPRINT.cells()) seen.add(c.ch);
        for (char ch : seen) {
            if (ch == ArchiveShape.AIR || ch == ArchiveShape.CONTROLLER || ch == ArchiveShape.ANY) continue;
            int code = ArchiveShape.partOf(ch);
            Block b = PartBlocks.block(code);
            if (b == null) throw new IllegalStateException("no block for the Archive's '" + ch + "'");
            int at = out.indexOf(ch);
            if (at >= 0) {
                // a character both shapes use must mean the same part in both
                if (out.get(at + 1) != b || !out.get(at + 2)
                    .equals(PartBlocks.meta(code)))
                    throw new IllegalStateException("the hall and the Archive disagree on '" + ch + "'");
                continue;
            }
            out.add(ch);
            out.add(b);
            out.add(PartBlocks.meta(code));
        }
        return out.toArray();
    }

    @Override
    protected Block coreBlock() {
        return LibraryModule.core;
    }

    /** Nothing gives way: the library is walked into as it stands. */
    @Override
    protected int flags(char ch) {
        return 0;
    }

    @Override
    protected String descriptionKey() {
        return "library.structure";
    }

    /** Whether it stands (or last stood) as the Echo Archive. */
    public boolean isArchive() {
        return MAIN.equals(shapeName());
    }

    /** Books it holds as the shape it stands as: 702 for the Archive, 167 for the hall. */
    public int capacity() {
        return isArchive() ? ARCHIVE_BOOKS : HALL_BOOKS;
    }

    /** Its samples, one a place (the GUI's copy on the client). */
    public com.gtnewhorizons.modularui.api.forge.IItemHandlerModifiable samples() {
        return samples;
    }

    // ---- the Archive's bookcases

    /** Bookcases of the Archive: 78; none for a hall. */
    public int unitCount() {
        return isArchive() ? LibraryUnits.UNITS.size() : 0;
    }

    /** A block of the Archive as a point of {@link ArchiveShape}: {x, y, z} (it may lie outside the shape). */
    public int[] local(int x, int y, int z) {
        int[] c = cellAt(x, y, z);
        return new int[] { c[0], ArchiveShape.HEIGHT - 1 - c[1], c[2] };
    }

    /** A point in the world as a point of {@link ArchiveShape} (fractional), e.g. for {@link ArchiveShape#inside}. */
    public double[] localPoint(double x, double y, double z) {
        ForgeDirection f = front();
        return ArchiveShape.BLUEPRINT.point(x, y, z, xCoord, yCoord, zCoord, f.offsetX, f.offsetZ);
    }

    /** The block a point of {@link ArchiveShape} is at. */
    public int[] worldOf(int lx, int ly, int lz) {
        int[] c = ArchiveShape.cellOf(lx, ly, lz);
        return cellPos(c[0], c[1], c[2]);
    }

    /**
     * The world x of the block at a point of {@link ArchiveShape}: {@link #worldOf} one coordinate at a time and
     * without an array, for drawing code that asks it for many points a frame. Its y is {@link #worldY}, its z
     * {@link #worldZ}.
     */
    public int worldX(int lx, int lz) {
        ForgeDirection f = front();
        // as Blueprint.world: across runs along (fz, -fx), back from the front along (-fx, -fz)
        return xCoord + (lx - ArchiveShape.BLUEPRINT.ctrlA) * f.offsetZ
            - (lz - ArchiveShape.BLUEPRINT.ctrlC) * f.offsetX;
    }

    /** The world y of the block at height {@code ly} of {@link ArchiveShape} (see {@link #worldX}). */
    public int worldY(int ly) {
        return yCoord + ly - (ArchiveShape.HEIGHT - 1 - ArchiveShape.BLUEPRINT.ctrlB);
    }

    /** The world z of the block at a point of {@link ArchiveShape} (see {@link #worldX}). */
    public int worldZ(int lx, int lz) {
        ForgeDirection f = front();
        return zCoord - (lx - ArchiveShape.BLUEPRINT.ctrlA) * f.offsetX
            - (lz - ArchiveShape.BLUEPRINT.ctrlC) * f.offsetZ;
    }

    /**
     * Where body {@code k} (row * 3 + col: row 0 at the top, col 0 at the left of someone facing the books) of the
     * bookcase stands, {x, y, z}; null when it is no bookcase of an Archive. Its slot is {@code unit * 9 + k}.
     */
    public int[] unitCell(int unit, int k) {
        if (!isArchive() || unit < 0 || unit >= LibraryUnits.UNITS.size() || k < 0 || k >= 9) return null;
        int[] c = LibraryUnits.cellOfSlot(unit * 9 + k);
        return worldOf(c[0], c[1], c[2]);
    }

    /** The side (a ForgeDirection ordinal) the bookcase's books face, out into the room; -1 for no bookcase. */
    public int unitFace(int unit) {
        if (!isArchive() || unit < 0 || unit >= LibraryUnits.UNITS.size()) return -1;
        LibraryUnits.Unit u = LibraryUnits.UNITS.get(unit);
        int[] d = worldDir(u.faceX, u.faceZ);
        for (ForgeDirection fd : ForgeDirection.VALID_DIRECTIONS)
            if (fd.offsetY == 0 && fd.offsetX == d[0] && fd.offsetZ == d[1]) return fd.ordinal();
        return -1;
    }

    /** A horizontal direction of the Archive's frame as a world direction {dx, dz}. */
    private int[] worldDir(int lx, int lz) {
        ForgeDirection f = front();
        // as Blueprint.world maps across onto (fz, -fx) and the depth onto (-fx, -fz)
        return new int[] { lx * f.offsetZ - lz * f.offsetX, -lx * f.offsetX - lz * f.offsetZ };
    }

    /** A world direction {dx, dz} in the Archive's frame. */
    private int[] localDir(int dx, int dz) {
        ForgeDirection f = front();
        return new int[] { dx * f.offsetZ - dz * f.offsetX, -(dx * f.offsetX + dz * f.offsetZ) };
    }

    /**
     * The bookcase a block belongs to (see {@link #unitAt(int, int, int, int, double, double)}), without a hit point.
     */
    public int unitAt(int x, int y, int z, int side) {
        return unitAt(x, y, z, side, Double.NaN, Double.NaN);
    }

    /**
     * The bookcase a block of the Archive belongs to: a body, plinth or crown its own bookcase; a post the bookcase on
     * the side clicked along its run (a side facing along the run), else the half of its face that was hit
     * ({@code hitX, hitZ}: the point in the world; NaN when unknown, then the bookcase at the lower end). -1 for any
     * other block and for a hall.
     */
    public int unitAt(int x, int y, int z, int side, double hitX, double hitZ) {
        if (!isArchive()) return -1;
        int[] l = local(x, y, z);
        int u = LibraryUnits.unitOfCell(l[0], l[1], l[2]);
        if (u >= 0) return u;
        if (ArchiveShape.cell(l[0], l[1], l[2]) != ArchiveShape.POST) return -1;
        int lower = LibraryUnits.unitOfPost(l[0], l[1], l[2], -1), upper = LibraryUnits.unitOfPost(l[0], l[1], l[2], 1);
        if (lower < 0 || upper < 0) return Math.max(lower, upper);
        ForgeDirection d = ForgeDirection.getOrientation(side);
        int[] s = localDir(d.offsetX, d.offsetZ);
        if (towards(lower, s[0], s[1], -1)) return lower;
        if (towards(upper, s[0], s[1], 1)) return upper;
        if (Double.isNaN(hitX) || Double.isNaN(hitZ)) return lower;
        double[] p = localPoint(hitX, y + 0.5, hitZ);
        boolean alongX = LibraryUnits.UNITS.get(lower).alongX;
        double along = alongX ? p[0] - (l[0] + 0.5) : p[2] - (l[2] + 0.5);
        return along < 0 ? lower : upper;
    }

    /** Whether a local direction points along the bookcase's run, the way {@code sign} says. */
    private static boolean towards(int unit, int dx, int dz, int sign) {
        boolean alongX = LibraryUnits.UNITS.get(unit).alongX;
        return alongX ? dx == sign && dz == 0 : dz == sign && dx == 0;
    }

    /** The slot of the book place at a block: an Archive's bookcase body, a hall's shelf; -1 for anything else. */
    public int slotAt(int x, int y, int z) {
        if (!isArchive()) return shelfAt(x, y, z);
        int[] l = local(x, y, z);
        int u = LibraryUnits.unitOfCell(l[0], l[1], l[2]);
        return u < 0 ? -1 : LibraryUnits.slot(u, l[0], l[1], l[2]);
    }

    /** Books in the bookcase (0 to 9): the samples on the server, the client's copy on the client. */
    public int unitFill(int unit) {
        if (unit < 0 || unit >= LibraryUnits.UNITS.size()) return 0;
        int n = 0;
        for (int k = 0; k < 9; k++) if (book(unit * 9 + k) != null) n++;
        return n;
    }

    /** The bookcase's call number, such as {@code G-L3a-2}; empty for none. */
    public String callNumber(int unit) {
        return unit < 0 || unit >= LibraryUnits.UNITS.size() ? "" : LibraryUnits.UNITS.get(unit).call;
    }

    /** The bookcase's first free place (row by row from the top left), a slot; -1 when it is full. */
    public int firstFree(int unit) {
        if (unit < 0 || unit >= LibraryUnits.UNITS.size()) return -1;
        for (int k = 0; k < 9; k++) if (book(unit * 9 + k) == null) return unit * 9 + k;
        return -1;
    }

    /** Selects the bookcase the GUI shows (server side; anything outside 0..77 is ignored). */
    public void selectUnit(int unit) {
        if (unit < 0 || unit >= LibraryUnits.UNITS.size() || unit == selectedUnit) return;
        selectedUnit = unit;
        markDirty();
    }

    // ---- the hall (and the places of either shape, one slot each)

    /** Where book place {@code i} is: a hall's shelf, or an Archive's bookcase body. */
    public int[] shelfPos(int i) {
        if (isArchive()) return unitCell(i / 9, i % 9);
        LibraryShape.Shelf s = LibraryShape.SHELVES.get(i);
        int[] c = LibraryShape.cellOf(s.x, s.y, s.z);
        return cellPos(c[0], c[1], c[2]);
    }

    /**
     * The side (a ForgeDirection ordinal) of place {@code i} that its book shows on: into the hall, out of the case.
     */
    public int shelfFace(int i) {
        if (isArchive()) {
            int face = unitFace(i / 9);
            return face < 0 ? ForgeDirection.UNKNOWN.ordinal() : face;
        }
        LibraryShape.Shelf s = LibraryShape.SHELVES.get(i);
        int[] a = shelfPos(i), c = LibraryShape.cellOf(s.x + s.inX, s.y, s.z + s.inZ), b = cellPos(c[0], c[1], c[2]);
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS)
            if (d.offsetX == b[0] - a[0] && d.offsetZ == b[2] - a[2] && d.offsetY == 0) return d.ordinal();
        return ForgeDirection.UNKNOWN.ordinal();
    }

    /** The slot of the hall's shelf at the position (or the Archive's body), -1 when there is none. */
    public int shelfAt(int x, int y, int z) {
        if (isArchive()) return slotAt(x, y, z);
        if (shelfMap == null || shelfMapFor[0] != xCoord
            || shelfMapFor[1] != yCoord
            || shelfMapFor[2] != zCoord
            || shelfMapFor[3] != facing) {
            Map<Long, Integer> m = new HashMap<>();
            for (int i = 0; i < HALL_BOOKS; i++) {
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
        if (isArchive()) return worldOf(ArchiveShape.DESK[0], ArchiveShape.DESK[1], ArchiveShape.DESK[2]);
        int[] c = LibraryShape.cellOf(LibraryShape.SIZE / 2, LibraryShape.HALL_LOW, LibraryShape.SIZE / 2);
        return cellPos(c[0], c[1], c[2]);
    }

    /** The desk opens the GUI; in the Archive so do the lecterns and the stand under the controller. */
    @Override
    public boolean consoleAt(int x, int y, int z) {
        if (isArchive()) {
            int[] l = local(x, y, z);
            return ArchiveShape.cell(l[0], l[1], l[2]) == ArchiveShape.CONSOLE;
        }
        int[] d = deskPos();
        return d[0] == x && d[1] == y && d[2] == z;
    }

    /** The book in place {@code i}: the sample on the server, the client's copy on the client. */
    public ItemStack book(int i) {
        if (i < 0 || i >= SLOTS) return null;
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
        Item item = s.getItem();
        for (int i = 0; i < capacity(); i++) {
            if (i == except) continue;
            ItemStack x = samples.getStackInSlot(i);
            if (x != null && x.getItem() == item
                && x.getItemDamage() == s.getItemDamage()
                && ItemStack.areItemStackTagsEqual(x, s)) return true;
        }
        return false;
    }

    void lent() {
        lends++;
    }

    /** Samples it keeps (in the places of its shape). */
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
            ItemStack s = book(i);
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

    /** Points the desk at a slot (a book the GUI picked), server side. */
    public void selectSample(int slot) {
        if (slot < 0 || slot >= capacity() || slot == selected) return;
        selected = slot;
        markDirty();
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
            // the client only shows whether it lends (the GUI syncs the count itself), and a description carries every
            // book: it goes out again only when lending starts or stops
            boolean wasLending = lendsShown > 0;
            lendsShown = lends;
            if (wasLending != lendsShown > 0) sync();
            lends = 0;
        }
        if (was != powered) sync();
        sendBooks();
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

    // ---- shapes and the vault

    /**
     * Formed, or formed as another shape: an Archive forming for the first time takes in the team's vault, a hall puts
     * the books it has no shelf for into the vault.
     */
    @Override
    protected void formedChanged(boolean now) {
        super.formedChanged(now);
        if (!now) return;
        if (isArchive()) {
            if (refilled) return;
            refilled = true;
            markDirty();
            int n = LibraryVault.takeInto(worldObj, team(), samples, capacity());
            if (n > 0) announce("refilled", n);
        } else stowOverflow();
    }

    @Override
    protected void shapeChanged(String was) {
        super.shapeChanged(was);
        count = -1;
        shelfMap = null;
    }

    /**
     * A hall's books past its shelves go to the team's vault, one item a book; what a place held beyond one item
     * falls out on the reading desk. Without a team they all stay in the handler, kept.
     */
    private void stowOverflow() {
        List<ItemStack> held = new ArrayList<>(), more = new ArrayList<>();
        for (int i = HALL_BOOKS; i < SLOTS; i++)
            if (samples.getStackInSlot(i) != null) held.add(samples.getStackInSlot(i));
        if (held.isEmpty()) return;
        List<ItemStack> books = oneEach(held, more);
        if (!LibraryVault.put(worldObj, team(), books)) return;
        for (int i = HALL_BOOKS; i < SLOTS; i++) samples.setStackInSlot(i, null);
        int[] d = deskPos();
        for (ItemStack s : more)
            worldObj.spawnEntityInWorld(new EntityItem(worldObj, d[0] + 0.5, d[1] + 1.2, d[2] + 0.5, s));
        announce("overflow", books.size());
    }

    /**
     * The books of some stacks, one item each (copies, in order), as a library place and the vault keep them; the
     * rest of any stack of more than one is added to {@code more} (copies), to be given back rather than lost.
     */
    static List<ItemStack> oneEach(List<ItemStack> stacks, List<ItemStack> more) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s == null || s.stackSize <= 0) continue;
            ItemStack one = s.copy();
            one.stackSize = 1;
            out.add(one);
            if (s.stackSize > 1) {
                ItemStack rest = s.copy();
                rest.stackSize = s.stackSize - 1;
                more.add(rest);
            }
        }
        return out;
    }

    /**
     * Whether the tile still stands in its world: not removed, and still the tile at its block. A GUI open on it closes
     * once it does not ({@link LibraryGui#window}), so nothing is filed into (or pulled from the vault into) a library
     * that was broken while the window stayed open.
     */
    boolean standing() {
        World w = worldObj;
        return !isInvalid() && w != null
            && w.blockExists(xCoord, yCoord, zCoord)
            && w.getTileEntity(xCoord, yCoord, zCoord) == this;
    }

    /** Books in the team's vault (server side). */
    public int vaultCount() {
        return LibraryVault.count(worldObj, team());
    }

    /**
     * The GUI's 取回: fills the free places from the team's vault in order. Server side; the player must be allowed to
     * use the library. Returns how many books came back, -1 when the player may not.
     */
    public int refill(EntityPlayer p) {
        // a window left open on a core that was broken must not pull books into a tile that is no longer saved
        if (worldObj == null || worldObj.isRemote || !standing()) return 0;
        if (!BlockNexus.mayUse(this, p)) {
            p.addChatMessage(new ChatComponentTranslation("fluxecho.library.unit.not_yours", ownerName));
            return -1;
        }
        int left = vaultCount();
        int n = LibraryVault.takeInto(worldObj, team(), samples, capacity());
        String key = n > 0 ? "refilled" : left > 0 ? "no_room" : "empty";
        p.addChatMessage(new ChatComponentTranslation("fluxecho.library.vault." + key, n));
        return n;
    }

    /**
     * The core is being broken: the books go to the team's vault (one item a book; what a place held beyond one item
     * falls out), or fall out where there is no team (or the block only goes back to what it was before a refused
     * placement); the desk's cards fall out. The handlers are emptied either way, so nothing is left to drop twice.
     */
    void takeDown(World w, int x, int y, int z) {
        List<ItemStack> held = new ArrayList<>(), drop = new ArrayList<>();
        for (int i = 0; i < samples.getSlots(); i++)
            if (samples.getStackInSlot(i) != null) held.add(samples.getStackInSlot(i));
        for (int i = 0; i < desk.getSlots(); i++) if (desk.getStackInSlot(i) != null) drop.add(desk.getStackInSlot(i));
        List<ItemStack> more = new ArrayList<>(), books = oneEach(held, more);
        boolean kept = !books.isEmpty() && !w.restoringBlockSnapshots && LibraryVault.put(w, team(), books);
        if (kept) drop.addAll(more);
        else drop.addAll(held);
        for (ItemStack s : drop) w.spawnEntityInWorld(new EntityItem(w, x + 0.5, y + 0.5, z + 0.5, s.copy()));
        for (int i = 0; i < samples.getSlots(); i++) samples.setStackInSlot(i, null);
        for (int i = 0; i < desk.getSlots(); i++) desk.setStackInSlot(i, null);
        changed.clear();
        if (kept) announce("kept", books.size());
    }

    /** Tells the team's players near the library a vault line ({@code fluxecho.library.vault.<key>}, a count). */
    private void announce(String key, int n) {
        UUID team = team();
        if (worldObj == null || team == null) return;
        for (Object o : worldObj.playerEntities) {
            if (!(o instanceof EntityPlayer p) || p.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) > 64 * 64)
                continue;
            if (team.equals(Owners.team(p.getUniqueID())))
                p.addChatMessage(new ChatComponentTranslation("fluxecho.library.vault." + key, n));
        }
    }

    // ---- the books on the client

    /**
     * Sends the books that changed (at most {@link BookPacket#DELTA_SLOTS} a tick, the rest the next) to the players
     * near the library or watching its chunk.
     */
    private void sendBooks() {
        if (changed.isEmpty()) return;
        List<BookPacket.Book> out = new ArrayList<>();
        for (int i = changed.nextSetBit(0); i >= 0
            && out.size() < BookPacket.DELTA_SLOTS; i = changed.nextSetBit(i + 1)) {
            ItemStack s = samples.getStackInSlot(i);
            out.add(new BookPacket.Book(i, s == null ? null : s.writeToNBT(new NBTTagCompound())));
            changed.clear(i);
        }
        rev++;
        Set<EntityPlayerMP> to = audience();
        if (to.isEmpty()) return;
        NBTTagCompound t = NexusNet.at(worldObj.provider.dimensionId, xCoord, yCoord, zCoord);
        t.setInteger("Rv", rev);
        t.setInteger("Ep", epoch);
        BookPacket.write(t, out);
        for (EntityPlayerMP p : to) NexusNet.tell(p, NexusNet.LIB_DELTA, t);
    }

    /**
     * Who hears of book changes: the players within the nexus effect range and 64 blocks of the library (directly or
     * through a light gate), and everyone watching its chunk (who holds its description).
     */
    private Set<EntityPlayerMP> audience() {
        int[] c = centre();
        Set<EntityPlayerMP> to = new LinkedHashSet<>(
            Sight.near(worldObj, c[0] + 0.5, c[1] + 0.5, c[2] + 0.5, Config.nexusEffectRange + 64));
        if (worldObj instanceof WorldServer ws) {
            for (Object o : ws.playerEntities) if (o instanceof EntityPlayerMP p && ws.getPlayerManager()
                .isPlayerWatchingChunk(p, xCoord >> 4, zCoord >> 4)) to.add(p);
        }
        return to;
    }

    @Override
    protected void writeSync(NBTTagCompound t) {
        super.writeSync(t);
        t.setBoolean("P", powered);
        t.setInteger("S", sampleCount());
        t.setInteger("L", lendsShown);
        t.setString("Dk", dockStatus());
        t.setInteger("Rv", rev);
        t.setInteger("Ep", epoch);
        List<BookPacket.Book> all = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = samples.getStackInSlot(i);
            if (s != null) all.add(new BookPacket.Book(i, s.writeToNBT(new NBTTagCompound())));
        }
        BookPacket.write(t, all);
    }

    @Override
    protected void readSync(NBTTagCompound t) {
        super.readSync(t);
        clientPowered = t.getBoolean("P");
        clientLends = t.getInteger("L");
        clientDock = t.getString("Dk");
        int ep = t.getInteger("Ep"), r = t.getInteger("Rv");
        if (!described || ep != clientEpoch) {
            // another life of the tile on the server: nothing heard before counts
            Arrays.fill(clientStamp, Integer.MIN_VALUE);
            clientEpoch = ep;
            described = true;
        }
        ItemStack[] next = new ItemStack[SLOTS];
        BookPacket.read(
            t,
            (slot, stack, id, meta) -> { if (slot >= 0 && slot < SLOTS) next[slot] = stackOf(stack, id, meta); });
        BitSet touched = new BitSet(SLOTS);
        // a delta newer than this description (it may overtake it) keeps its books
        for (int i = 0; i < SLOTS; i++) {
            if (r < clientStamp[i]) continue;
            clientStamp[i] = r;
            if (!ItemStack.areItemStacksEqual(clientBooks[i], next[i])) {
                clientBooks[i] = next[i];
                touched.set(i);
            }
        }
        replayParked(touched);
        booksChanged(touched);
    }

    /** A delta packet for this library (client thread): applied when it is newer than what each slot holds. */
    private void applyDelta(NBTTagCompound t, BitSet touched) {
        int k = t.getInteger("Rv");
        BookPacket.read(t, (slot, stack, id, meta) -> {
            if (slot < 0 || slot >= SLOTS || k <= clientStamp[slot]) return;
            clientStamp[slot] = k;
            ItemStack s = stackOf(stack, id, meta);
            if (!ItemStack.areItemStacksEqual(clientBooks[slot], s)) {
                clientBooks[slot] = s;
                touched.set(slot);
            }
        });
    }

    /** A book as a packet carried it: whole, or its item and metadata only; null for none (or an unknown item). */
    private static ItemStack stackOf(NBTTagCompound stack, int id, int meta) {
        if (stack != null) return ItemStack.loadItemStackFromNBT(stack);
        if (id < 0) return null;
        Item item = Item.getItemById(id);
        return item == null ? null : new ItemStack(item, 1, (short) meta);
    }

    /** The client's books changed in the given slots: recount, recolour, and redraw the Archive's shelves. */
    private void booksChanged(BitSet touched) {
        int n = 0, cap = capacity();
        for (int i = 0; i < cap; i++) if (clientBooks[i] != null) n++;
        clientSamples = n;
        for (int i = touched.nextSetBit(0); i >= 0; i = touched.nextSetBit(i + 1))
            clientColour[i] = colourOf(clientBooks[i]);
        showBooks();
    }

    private static int colourOf(ItemStack s) {
        if (s == null || s.getItem() == null) return 0;
        int name;
        try {
            name = s.getDisplayName()
                .hashCode();
        } catch (RuntimeException e) {
            name = 0;
        }
        return SpineColour.of(Item.getIdFromItem(s.getItem()), s.getItemDamage(), name);
    }

    /** Hands the Archive's books to the shelf mesh ({@link LibraryBooks}); a hall shows none there. */
    private void showBooks() {
        if (!realWorld() || !worldObj.isRemote) return;
        long owner = Formed.key(xCoord, yCoord, zCoord);
        if (!isArchive()) {
            LibraryBooks.forget(owner);
            return;
        }
        long[] keys = slotKeys();
        Map<Long, Integer> cells = new HashMap<>();
        for (int i = 0; i < ARCHIVE_BOOKS; i++) if (clientColour[i] != 0) cells.put(keys[i], clientColour[i]);
        LibraryBooks.put(owner, cells);
    }

    /** The cell of every Archive book place, for its position, facing and shape. */
    private long[] slotKeys() {
        int sh = shapeName().hashCode();
        if (slotKeys == null || slotKeysFor[0] != xCoord
            || slotKeysFor[1] != yCoord
            || slotKeysFor[2] != zCoord
            || slotKeysFor[3] != facing
            || slotKeysFor[4] != sh) {
            long[] k = new long[ARCHIVE_BOOKS];
            for (int i = 0; i < ARCHIVE_BOOKS; i++) {
                int[] p = unitCell(i / 9, i % 9);
                k[i] = p == null ? Long.MIN_VALUE : Formed.key(p[0], p[1], p[2]);
            }
            slotKeys = k;
            slotKeysFor[0] = xCoord;
            slotKeysFor[1] = yCoord;
            slotKeysFor[2] = zCoord;
            slotKeysFor[3] = facing;
            slotKeysFor[4] = sh;
        }
        return slotKeys;
    }

    @Override
    protected void gone() {
        super.gone();
        if (worldObj != null && worldObj.isRemote && realWorld())
            LibraryBooks.forget(Formed.key(xCoord, yCoord, zCoord));
    }

    /** Delta packets for a library the client has not got (yet): kept a while for the description that comes. */
    private static final List<Parked> PARKED = new ArrayList<>();
    private static final int PARKED_MAX = 128;
    private static final long PARKED_MS = 30_000;

    private static final class Parked {

        final int dim, x, y, z;
        final NBTTagCompound t;
        final long at;

        Parked(NBTTagCompound t, long at) {
            this.t = t;
            this.at = at;
            dim = t.getInteger("Dim");
            x = t.getInteger("X");
            y = t.getInteger("Y");
            z = t.getInteger("Z");
        }
    }

    /**
     * A {@link NexusNet#LIB_DELTA} packet arrived (client thread): the library it names takes it, or it waits for that
     * library's description (a packet may overtake the chunk that brings the library).
     */
    public static void receiveDelta(NBTTagCompound t) {
        if (t == null) return;
        int dim = t.getInteger("Dim"), x = t.getInteger("X"), y = t.getInteger("Y"), z = t.getInteger("Z");
        for (TileMultiblock m : ClientTiles.all()) {
            if (!(m instanceof TileLibrary l) || m.isInvalid() || l.xCoord != x || l.yCoord != y || l.zCoord != z)
                continue;
            World w = l.getWorldObj();
            if (w == null || w.provider.dimensionId != dim) continue;
            if (!l.described || t.getInteger("Ep") != l.clientEpoch) break;
            BitSet touched = new BitSet(SLOTS);
            l.applyDelta(t, touched);
            l.booksChanged(touched);
            return;
        }
        park(t);
    }

    private static void park(NBTTagCompound t) {
        long now = System.currentTimeMillis();
        synchronized (PARKED) {
            PARKED.removeIf(p -> now - p.at > PARKED_MS);
            while (PARKED.size() >= PARKED_MAX) PARKED.remove(0);
            PARKED.add(new Parked(t, now));
        }
    }

    /** After a description: the parked deltas of this library's life, oldest first (older slots ignore them). */
    private void replayParked(BitSet touched) {
        if (worldObj == null) return;
        int dim = worldObj.provider.dimensionId;
        List<NBTTagCompound> mine = new ArrayList<>();
        synchronized (PARKED) {
            for (Iterator<Parked> it = PARKED.iterator(); it.hasNext();) {
                Parked p = it.next();
                if (p.dim != dim || p.x != xCoord || p.y != yCoord || p.z != zCoord) continue;
                it.remove();
                if (p.t.getInteger("Ep") == clientEpoch) mine.add(p.t);
            }
        }
        mine.sort((a, b) -> Integer.compare(a.getInteger("Rv"), b.getInteger("Rv")));
        for (NBTTagCompound t : mine) applyDelta(t, touched);
    }

    /** Leaving a world: no parked packet belongs to the next one. */
    public static void clearClient() {
        synchronized (PARKED) {
            PARKED.clear();
        }
    }

    // ---- saving

    @Override
    public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setTag("Samples", samples.serializeNBT());
        t.setTag("Desk", desk.serializeNBT());
        t.setInteger("Selected", selected);
        t.setInteger("Unit", selectedUnit);
        t.setBoolean("Refilled", refilled);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        // read through a handler of the saved size: 0.9.0 kept 54, 0.9.2 kept 167 (setSize would empty it)
        ItemStackHandler saved = new ItemStackHandler(0);
        saved.deserializeNBT(t.getCompoundTag("Samples"));
        for (int i = 0; i < SLOTS; i++)
            samples.setStackInSlot(i, i < saved.getSlots() ? saved.getStackInSlot(i) : null);
        desk.deserializeNBT(t.getCompoundTag("Desk"));
        if (desk.getSlots() != 2) desk.setSize(2);
        selected = t.getInteger("Selected");
        selectedUnit = Math.max(0, Math.min(LibraryUnits.UNITS.size() - 1, t.getInteger("Unit")));
        refilled = t.getBoolean("Refilled");
        count = -1;
        changed.clear();
        shelfMap = null;
    }

    @Override
    public ModularWindow createWindow(UIBuildContext ctx) {
        return LibraryGui.window(this, ctx);
    }

    /** Everything it holds (books and the desk's cards). */
    public List<ItemStack> contents() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < samples.getSlots(); i++)
            if (samples.getStackInSlot(i) != null) out.add(samples.getStackInSlot(i));
        for (int i = 0; i < desk.getSlots(); i++) if (desk.getStackInSlot(i) != null) out.add(desk.getStackInSlot(i));
        return out;
    }
}
