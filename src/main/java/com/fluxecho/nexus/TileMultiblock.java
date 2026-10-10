package com.fluxecho.nexus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.core.EchoText;
import com.fluxecho.core.Owners;
import com.fluxecho.frame.Formed;
import com.fluxecho.frame.FrameEvents;
import com.fluxecho.logic.Blueprint;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

/**
 * A FluxEcho multiblock's controller: who owns it, which way it faces, whether its structure stands. The structure is
 * a {@link Blueprint} checked with StructureLib every few seconds, and at once when a frame block nearby is placed or
 * broken ({@link FrameEvents}); StructureLib's hologram projector builds or previews it
 * ({@link ISurvivalConstructable}). A formed structure part of which lies in an unloaded chunk is not checked until all
 * of it is loaded again, as StructureLib would take the unloaded blocks for missing ones.
 * <p>
 * While formed, the blocks the subclass names ({@link #flags}) give way to its drawing ({@link Formed}). The client
 * runs the same check on its own copy of the blocks, so both sides agree on where each part is.
 * <p>
 * A multiblock may stand as one of several shapes ({@link #shapes}): the Echo Library forms as the Echo Archive or as
 * the 0.9.2 hall. Formation tries the shape it stood as last, then the others in order, and remembers the one that
 * matched ({@link #shapeName}, saved as {@code Shape} and sent to the client as {@code Sh}); everything that maps
 * blueprint cells to blocks follows that shape ({@link #current}). A multiblock that names no shapes has one,
 * {@link #MAIN}.
 */
public abstract class TileMultiblock extends TileEntity implements ISurvivalConstructable, FrameEvents.Watcher {

    protected static final String MAIN = "main";

    /** One shape a multiblock can stand as: its name in the structure definitions and its blueprint. */
    public static final class Shape {

        public final String name;
        public final Blueprint bp;

        public Shape(String name, Blueprint bp) {
            this.name = name;
            this.bp = bp;
        }
    }

    /** The way the controller's front faces (a horizontal ForgeDirection ordinal). */
    protected int facing = 2;
    protected UUID owner;
    protected String ownerName = "";
    protected boolean formed;
    /** World time it last formed. */
    protected long formedTime;
    /**
     * The name of the shape it stands (or last stood) as; null for the first of {@link #shapes}. A subclass may start
     * it at another shape, and a save from before shapes were remembered gets {@link #legacyShape}.
     */
    protected String shape;

    private int recheck = 1;
    private final List<int[]> seen = new ArrayList<>();
    /** Block range of the last formed structure: x0, y0, z0, x1, y1, z1. */
    private int[] bounds;
    /** The cells last handed to {@link Formed} on this side; null for none. */
    private Map<Long, Integer> formedCells;
    /** The single shape of a multiblock that does not name its shapes, made once. */
    private List<Shape> mainOnly;
    /** How far from the controller any of its shapes reaches, once worked out. */
    private int reach = -1;

    // client
    /** When the client saw it form (ms), for the forming sweep; 0 when not formed. */
    public long formedAt;
    private boolean cellsSet;
    private int clientRetry, sweptTo = Integer.MIN_VALUE;

    protected abstract Blueprint blueprint();

    /**
     * Every shape it may stand as, in the order formation tries them after the remembered one; the first is what a
     * preview (NEI) shows. By default the one shape {@link #MAIN}, {@link #blueprint()}. Both structure definitions
     * must hold a shape of each name.
     */
    protected List<Shape> shapes() {
        if (mainOnly == null) mainOnly = Collections.singletonList(new Shape(MAIN, blueprint()));
        return mainOnly;
    }

    /** Whether the server's formation may try the shape now (a config switch may rule one out). */
    protected boolean tries(Shape s) {
        return true;
    }

    /** The shape a save from before shapes were remembered stood as; null for the first. */
    protected String legacyShape() {
        return null;
    }

    /** The shape it stands (or last stood) as: the remembered one, else the first. */
    protected Shape currentShape() {
        List<Shape> all = shapes();
        if (shape != null) for (Shape s : all) if (s.name.equals(shape)) return s;
        return all.get(0);
    }

    /** The name of the shape it stands (or last stood) as. */
    public String shapeName() {
        return currentShape().name;
    }

    /** The blueprint of the shape it stands (or last stood) as. */
    protected Blueprint current() {
        return currentShape().bp;
    }

    /**
     * It formed as another shape than the one it remembered (server side, right before {@link #formedChanged}); the
     * new shape is already the current one.
     */
    protected void shapeChanged(String was) {}

    /** The structure as it is checked: its parts note where StructureLib finds them ({@link Recorded}). */
    protected abstract IStructureDefinition<TileMultiblock> definition();

    /**
     * The structure as it is built and previewed: the same parts unwrapped, so the NEI preview (blockrenderer6343) can
     * tell which blocks it is made of, and any further shapes the subclass builds ({@link #buildShape}).
     */
    protected abstract IStructureDefinition<TileMultiblock> buildDefinition();

    /** The controller's own block, which a preview world has to be given before it can show the structure. */
    protected abstract Block coreBlock();

    /** The {@link Formed} flags of a part once formed. */
    protected abstract int flags(char ch);

    /** The lang key (under {@code fluxecho.}) of the structure's description lines for the projector. */
    protected abstract String descriptionKey();

    /** After the structure formed or fell apart, or formed as another shape (then {@code now} is true), server side. */
    protected void formedChanged(boolean now) {}

    protected void serverTick() {}

    protected void clientTick() {}

    /** What the subclass adds to the client's copy. */
    protected void writeSync(NBTTagCompound t) {}

    protected void readSync(NBTTagCompound t) {}

    /** A frame block of the given meta at the blueprint's character, noting where StructureLib finds it. */
    protected static IStructureElement<TileMultiblock> recorded(char ch, Block block, int meta) {
        return new Recorded(ch, StructureUtility.ofBlock(block, meta));
    }

    /**
     * A structure of shapes (named as given) whose parts are {@code char, Block, meta} triples; with {@code record} the
     * parts note where they are found ({@link #definition}), without they are plain ({@link #buildDefinition}).
     */
    protected static IStructureDefinition<TileMultiblock> define(boolean record, String[] names, Blueprint[] shapes,
        Object... parts) {
        StructureDefinition.Builder<TileMultiblock> b = StructureDefinition.builder();
        for (int i = 0; i < names.length; i++) b.addShape(names[i], StructureUtility.transpose(shapes[i].shape()));
        for (int i = 0; i + 2 < parts.length; i += 3) {
            char ch = (Character) parts[i];
            Block block = (Block) parts[i + 1];
            int meta = (Integer) parts[i + 2];
            b.addElement(ch, record ? recorded(ch, block, meta) : StructureUtility.ofBlock(block, meta));
        }
        return b.build();
    }

    void record(char ch, int x, int y, int z) {
        seen.add(new int[] { x, y, z, ch });
    }

    /** Whether the console stand at the position is this structure's own, which opens its GUI. */
    public boolean consoleAt(int x, int y, int z) {
        return false;
    }

    public ForgeDirection front() {
        return ForgeDirection.getOrientation(facing);
    }

    public ExtendedFacing extendedFacing() {
        return ExtendedFacing.of(front());
    }

    public boolean formed() {
        return formed;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    /** The owner's team: the team GT's wireless network and FluxEcho's ledger use. */
    public UUID team() {
        return Owners.team(owner);
    }

    public void place(EntityPlayer placer, int front) {
        if (placer != null) {
            owner = placer.getUniqueID();
            ownerName = placer.getCommandSenderName();
        }
        facing = front >= 2 && front <= 5 ? front : 2;
        recheck = 1;
        markDirty();
    }

    /**
     * Sets the owner and the front without a player at hand: the campus builder commissions a module it raised for
     * the nexus's owner this way. A null owner leaves the tile without one.
     */
    public void place(UUID owner, String ownerName, int front) {
        this.owner = owner;
        this.ownerName = ownerName == null ? "" : ownerName;
        facing = front >= 2 && front <= 5 ? front : 2;
        recheck = 1;
        markDirty();
    }

    /** The block a cell of the current shape's blueprint is at: its own position math, for drawing. */
    public int[] cellPos(int a, int b, int c) {
        ForgeDirection f = front();
        return current().world(a, b, c, xCoord, yCoord, zCoord, f.offsetX, f.offsetZ);
    }

    /**
     * The cell of the current shape's blueprint a block is in, {across, layer from the top, row from the front}: the
     * inverse of {@link #cellPos}. It may lie outside the blueprint; callers test the range.
     */
    public int[] cellAt(int x, int y, int z) {
        ForgeDirection f = front();
        return current().cell(x, y, z, xCoord, yCoord, zCoord, f.offsetX, f.offsetZ);
    }

    /** Checks the structure as one shape, noting the parts StructureLib finds there. */
    private boolean check(Shape s) {
        seen.clear();
        Blueprint bp = s.bp;
        return definition().check(
            this,
            s.name,
            worldObj,
            extendedFacing(),
            xCoord,
            yCoord,
            zCoord,
            bp.ctrlA,
            bp.ctrlB,
            bp.ctrlC,
            false);
    }

    /**
     * The shape the structure stands as: the remembered one first, then the others in order (those {@link #tries}
     * allows); null when none does. After a match {@link #seen} holds that shape's parts.
     */
    private Shape match() {
        Shape cur = currentShape();
        if (tries(cur) && check(cur)) return cur;
        for (Shape s : shapes()) if (s != cur && tries(s) && check(s)) return s;
        return null;
    }

    private long ownKey() {
        return Formed.key(xCoord, yCoord, zCoord);
    }

    /**
     * Hands the parts StructureLib found to {@link Formed}; on the client, redraws the structure when that changes
     * which of its blocks give way.
     */
    private void applyCells() {
        applyCells(Integer.MAX_VALUE);
    }

    /**
     * As {@link #applyCells()}, only the parts up to height {@code level} (the forming sweep). The chunk mesh is built
     * again only when the cells changed: a multiblock none of whose blocks give way (the library) has none, and
     * re-meshing all of the Echo Archive's sections on every layer of the sweep would only cost frames.
     */
    private void applyCells(int level) {
        Map<Long, Integer> cells = new HashMap<>();
        int[] b = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
            Integer.MIN_VALUE };
        for (int[] s : seen) {
            int f = flags((char) s[3]);
            if (f != 0 && s[1] <= level) cells.put(Formed.key(s[0], s[1], s[2]), f);
            for (int i = 0; i < 3; i++) {
                b[i] = Math.min(b[i], s[i]);
                b[i + 3] = Math.max(b[i + 3], s[i]);
            }
        }
        if (seen.isEmpty()) b = null;
        Map<Long, Integer> now = cells.isEmpty() ? null : cells;
        boolean changed = !Objects.equals(now, formedCells);
        Formed.set(worldObj, ownKey(), now);
        formedCells = now;
        if (changed) redraw(b);
        bounds = b;
    }

    /** How long the forming sweep takes, from the bottom of the structure to its top. */
    public static final long SWEEP_MS = 2000;

    /** Height the forming sweep has reached on the client; above the top once done. */
    public double sweepLevel() {
        if (bounds == null || formedAt == 0) return Double.MAX_VALUE;
        double f = (System.currentTimeMillis() - formedAt) / (double) SWEEP_MS;
        if (f >= 1) return Double.MAX_VALUE;
        return bounds[1] + Math.max(0, f) * (bounds[4] - bounds[1] + 1);
    }

    private void clearCells() {
        boolean had = formedCells != null;
        Formed.set(worldObj, ownKey(), null);
        formedCells = null;
        if (had) redraw(bounds);
    }

    private void redraw(int[] b) {
        if (worldObj == null || !worldObj.isRemote || b == null) return;
        worldObj.markBlockRangeForRenderUpdate(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    /**
     * The block range the structure takes up: x0, y0, z0, x1, y1, z1. Once checked, the range of the parts it was
     * found with; before, the whole box of the current shape round the controller.
     */
    public int[] bounds() {
        if (bounds != null) return bounds;
        Blueprint bp = current();
        int[] b = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
            Integer.MIN_VALUE };
        for (int k = 0; k < 8; k++) {
            int[] p = cellPos(
                (k & 1) == 0 ? 0 : bp.width() - 1,
                (k & 2) == 0 ? 0 : bp.height() - 1,
                (k & 4) == 0 ? 0 : bp.depth() - 1);
            for (int i = 0; i < 3; i++) {
                b[i] = Math.min(b[i], p[i]);
                b[i + 3] = Math.max(b[i + 3], p[i]);
            }
        }
        return b;
    }

    @Override
    public void updateEntity() {
        if (!realWorld()) return;
        if (worldObj.isRemote) {
            if (formed && !cellsSet && --clientRetry <= 0) {
                // the client checks the shape the server found; its own config does not decide
                if (check(currentShape())) {
                    cellsSet = true;
                    sweptTo = Integer.MIN_VALUE;
                    applyCells(Integer.MIN_VALUE);
                } else clientRetry = 20;
            }
            if (formed && cellsSet && sweptTo != Integer.MAX_VALUE) {
                // the blocks give way layer by layer as the sweep passes
                double level = sweepLevel();
                int to = level == Double.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.floor(level) - 1;
                if (to != sweptTo) {
                    sweptTo = to;
                    applyCells(to);
                }
            }
            clientTick();
            return;
        }
        if (--recheck <= 0) {
            if (formed && !loaded(bounds())) {
                // StructureLib takes a block in an unloaded chunk for a wrong one: a formed structure stays as it is
                // until all of it is loaded again, rather than coming apart (and forming anew) with the chunks
                recheck = 20;
            } else recheckNow();
        }
        serverTick();
    }

    /** Checks the structure (server side): formed or not, and as which shape. */
    private void recheckNow() {
        recheck = formed ? 100 : 40;
        Shape s = match();
        boolean ok = s != null;
        String was = shapeName();
        boolean switched = ok && !s.name.equals(was);
        if (switched) shape = s.name;
        if (ok) applyCells();
        if (ok != formed || switched) {
            formed = ok;
            if (ok) formedTime = worldObj.getTotalWorldTime();
            else clearCells();
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            if (switched) shapeChanged(was);
            formedChanged(ok);
        }
    }

    /** Whether every chunk of a block range (x0, y0, z0, x1, y1, z1) is loaded. */
    private boolean loaded(int[] b) {
        if (b == null) return true;
        int y0 = Math.max(0, b[1]), y1 = Math.min(255, b[4]);
        return y0 > y1 || worldObj.checkChunksExist(b[0], y0, b[2], b[3], y1, b[5]);
    }

    /** Sends the client copy again (formed state, the subclass's sync data). */
    protected void sync() {
        if (worldObj != null && !worldObj.isRemote) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    /** The client's world class, set by the client proxy: other worlds on the client are previews (NEI). */
    public static Class<?> clientWorld;

    /** Whether the tile is in a world players are in, not a preview's or a fake one. */
    protected boolean realWorld() {
        if (worldObj == null) return false;
        if (!worldObj.isRemote) return worldObj instanceof net.minecraft.world.WorldServer;
        return clientWorld != null && clientWorld.isInstance(worldObj);
    }

    @Override
    public void validate() {
        super.validate();
        recheck = 1;
        if (!realWorld()) return;
        FrameEvents.watch(this);
        if (worldObj.isRemote) ClientTiles.add(this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        gone();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        gone();
    }

    /** Unloaded or broken: its parts show again, the watchers forget it. */
    protected void gone() {
        FrameEvents.unwatch(this);
        ClientTiles.remove(this);
        if (worldObj != null) clearCells();
        cellsSet = false;
    }

    /** Whether the block could belong to any of its shapes: a cube round the controller as large as the largest. */
    @Override
    public boolean covers(World w, int x, int y, int z) {
        if (w != worldObj) return false;
        if (reach < 0) {
            int r = 0;
            for (Shape s : shapes()) r = Math.max(r, Math.max(s.bp.width(), Math.max(s.bp.depth(), s.bp.height())));
            reach = r + 1;
        }
        return Math.abs(x - xCoord) <= reach && Math.abs(y - yCoord) <= reach && Math.abs(z - zCoord) <= reach;
    }

    @Override
    public void frameChanged() {
        recheck = 1;
    }

    // ---- StructureLib's hologram projector

    @Override
    public IStructureDefinition<?> getStructureDefinition() {
        return buildDefinition();
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        if (!realWorld()) placeOwnBlock();
        buildShape(trigger, hintsOnly);
    }

    /**
     * Builds (or shows) the structure for the trigger: in a real world the shape it stands (or last stood) as, in a
     * preview the first of its shapes.
     */
    protected void buildShape(ItemStack trigger, boolean hintsOnly) {
        Shape s = realWorld() ? currentShape() : shapes().get(0);
        build(s.name, s.bp, trigger, hintsOnly);
    }

    /** Builds (or shows) one shape of {@link #buildDefinition} round the controller. */
    protected void build(String shape, Blueprint bp, ItemStack trigger, boolean hintsOnly) {
        buildDefinition().buildOrHints(
            this,
            trigger,
            shape,
            worldObj,
            extendedFacing(),
            xCoord,
            yCoord,
            zCoord,
            bp.ctrlA,
            bp.ctrlB,
            bp.ctrlC,
            hintsOnly);
    }

    /**
     * In a preview world the tile stands alone: the NEI preview takes the multiblock's item from the block at the
     * controller, so it gets its own block (and stays the tile there).
     */
    private void placeOwnBlock() {
        Block core = coreBlock();
        if (worldObj == null || core == null || worldObj.getBlock(xCoord, yCoord, zCoord) == core) return;
        worldObj.setBlock(xCoord, yCoord, zCoord, core, 0, 2);
        if (worldObj.getTileEntity(xCoord, yCoord, zCoord) != this)
            worldObj.setTileEntity(xCoord, yCoord, zCoord, this);
    }

    /** The projector builds the shape it stands (or last stood) as; a preview builds the whole of the first at once. */
    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        if (!realWorld()) {
            // previews build the whole of it at once
            construct(trigger, false);
            return -1;
        }
        if (formed) return -1;
        Shape s = currentShape();
        Blueprint bp = s.bp;
        return buildDefinition().survivalBuild(
            this,
            trigger,
            s.name,
            worldObj,
            extendedFacing(),
            xCoord,
            yCoord,
            zCoord,
            bp.ctrlA,
            bp.ctrlB,
            bp.ctrlC,
            elementBudget,
            env,
            false);
    }

    @Override
    public String[] getStructureDescription(ItemStack trigger) {
        return EchoText.lines(descriptionKey())
            .toArray(new String[0]);
    }

    // ---- saving and the client copy

    @Override
    public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setByte("Facing", (byte) facing);
        if (owner != null) {
            t.setLong("OwnerMost", owner.getMostSignificantBits());
            t.setLong("OwnerLeast", owner.getLeastSignificantBits());
        }
        t.setString("OwnerName", ownerName);
        t.setBoolean("Formed", formed);
        t.setLong("FormedTime", formedTime);
        if (shape != null) t.setString("Shape", shape);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        facing = t.getByte("Facing");
        if (facing < 2 || facing > 5) facing = 2;
        owner = t.hasKey("OwnerMost") ? new UUID(t.getLong("OwnerMost"), t.getLong("OwnerLeast")) : null;
        ownerName = t.getString("OwnerName");
        formed = t.getBoolean("Formed");
        formedTime = t.getLong("FormedTime");
        shape = t.hasKey("Shape") ? t.getString("Shape") : legacyShape();
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound t = new NBTTagCompound();
        t.setByte("F", (byte) facing);
        t.setBoolean("On", formed);
        t.setLong("T", formedTime);
        t.setString("O", ownerName);
        t.setString("Sh", shapeName());
        writeSync(t);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, t);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound t = pkt.func_148857_g();
        int f = t.getByte("F");
        boolean was = formed;
        String wasShape = shapeName();
        facing = f >= 2 && f <= 5 ? f : 2;
        formed = t.getBoolean("On");
        ownerName = t.getString("O");
        if (t.hasKey("Sh")) shape = t.getString("Sh");
        boolean switched = !wasShape.equals(shapeName());
        readSync(t);
        if (formed && was && switched) {
            // it stands as another shape now: its parts are another set, found and swept in anew
            clearCells();
            formedAt = System.currentTimeMillis();
            cellsSet = false;
            clientRetry = 0;
        } else if (formed != was) {
            if (formed) {
                // a structure seen for the first time long after it formed does not play the forming sweep again
                long age = worldObj == null ? 1000 : worldObj.getTotalWorldTime() - t.getLong("T");
                formedAt = System.currentTimeMillis() - Math.max(0, age) * 50;
                cellsSet = false;
                clientRetry = 0;
            } else {
                formedAt = 0;
                cellsSet = false;
                clearCells();
            }
        }
    }
}
