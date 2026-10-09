package com.fluxecho.nexus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.gtnewhorizon.structurelib.structure.StructureUtility;

/**
 * A FluxEcho multiblock's controller: who owns it, which way it faces, whether its structure stands. The structure is
 * a {@link Blueprint} checked with StructureLib every few seconds, and at once when a frame block nearby is placed or
 * broken ({@link FrameEvents}); StructureLib's hologram projector builds or previews it
 * ({@link ISurvivalConstructable}).
 * <p>
 * While formed, the blocks the subclass names ({@link #flags}) give way to its drawing ({@link Formed}). The client
 * runs the same check on its own copy of the blocks, so both sides agree on where each part is.
 */
public abstract class TileMultiblock extends TileEntity implements ISurvivalConstructable, FrameEvents.Watcher {

    protected static final String MAIN = "main";

    /** The way the controller's front faces (a horizontal ForgeDirection ordinal). */
    protected int facing = 2;
    protected UUID owner;
    protected String ownerName = "";
    protected boolean formed;
    /** World time it last formed. */
    protected long formedTime;

    private int recheck = 1;
    private final List<int[]> seen = new ArrayList<>();
    /** Block range of the last formed structure: x0, y0, z0, x1, y1, z1. */
    private int[] bounds;

    // client
    /** When the client saw it form (ms), for the forming sweep; 0 when not formed. */
    public long formedAt;
    private boolean cellsSet;
    private int clientRetry, sweptTo = Integer.MIN_VALUE;

    protected abstract Blueprint blueprint();

    protected abstract IStructureDefinition<TileMultiblock> definition();

    /** The {@link Formed} flags of a part once formed. */
    protected abstract int flags(char ch);

    /** The lang key (under {@code fluxecho.}) of the structure's description lines for the projector. */
    protected abstract String descriptionKey();

    /** After the structure formed or fell apart, server side. */
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

    void record(char ch, int x, int y, int z) {
        seen.add(new int[] { x, y, z, ch });
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

    /** The block a cell of the blueprint is at: its own position math, for drawing. */
    public int[] cellPos(int a, int b, int c) {
        ForgeDirection f = front();
        return blueprint().world(a, b, c, xCoord, yCoord, zCoord, f.offsetX, f.offsetZ);
    }

    private boolean check() {
        seen.clear();
        Blueprint bp = blueprint();
        return definition()
            .check(this, MAIN, worldObj, extendedFacing(), xCoord, yCoord, zCoord, bp.ctrlA, bp.ctrlB, bp.ctrlC, false);
    }

    private long ownKey() {
        return Formed.key(xCoord, yCoord, zCoord);
    }

    /** Hands the parts StructureLib found to {@link Formed}; on the client, redraws them. */
    private void applyCells() {
        applyCells(Integer.MAX_VALUE);
    }

    /** As {@link #applyCells()}, only the parts up to height {@code level} (the forming sweep). */
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
        Formed.set(worldObj, ownKey(), cells);
        redraw(b);
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
        Formed.set(worldObj, ownKey(), null);
        redraw(bounds);
    }

    private void redraw(int[] b) {
        if (worldObj == null || !worldObj.isRemote || b == null) return;
        worldObj.markBlockRangeForRenderUpdate(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    /** The block range the structure takes up, once checked; null before. */
    public int[] bounds() {
        return bounds;
    }

    @Override
    public void updateEntity() {
        if (!realWorld()) return;
        if (worldObj.isRemote) {
            if (formed && !cellsSet && --clientRetry <= 0) {
                if (check()) {
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
            recheck = formed ? 100 : 40;
            boolean ok = check();
            if (ok) applyCells();
            if (ok != formed) {
                formed = ok;
                if (ok) formedTime = worldObj.getTotalWorldTime();
                else clearCells();
                markDirty();
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                formedChanged(ok);
            }
        }
        serverTick();
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

    @Override
    public boolean covers(World w, int x, int y, int z) {
        if (w != worldObj) return false;
        Blueprint bp = blueprint();
        int r = Math.max(bp.width(), Math.max(bp.depth(), bp.height())) + 1;
        return Math.abs(x - xCoord) <= r && Math.abs(y - yCoord) <= r && Math.abs(z - zCoord) <= r;
    }

    @Override
    public void frameChanged() {
        recheck = 1;
    }

    // ---- StructureLib's hologram projector

    @Override
    public IStructureDefinition<?> getStructureDefinition() {
        return definition();
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        Blueprint bp = blueprint();
        definition().buildOrHints(
            this,
            trigger,
            MAIN,
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

    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        if (formed) return -1;
        Blueprint bp = blueprint();
        return definition().survivalBuild(
            this,
            trigger,
            MAIN,
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
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound t = new NBTTagCompound();
        t.setByte("F", (byte) facing);
        t.setBoolean("On", formed);
        t.setLong("T", formedTime);
        t.setString("O", ownerName);
        writeSync(t);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, t);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound t = pkt.func_148857_g();
        int f = t.getByte("F");
        boolean was = formed;
        facing = f >= 2 && f <= 5 ? f : 2;
        formed = t.getBoolean("On");
        ownerName = t.getString("O");
        readSync(t);
        if (formed != was) {
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
