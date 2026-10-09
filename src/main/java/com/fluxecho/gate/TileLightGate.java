package com.fluxecho.gate;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import com.fluxecho.logic.GateGeometry;

/**
 * A light gate: which way it faces, whether it is the one inside a room, and its partner, which is always in the same
 * world: where it stands and the blocks it shows through this gate ({@link #far}). The pairing itself lives in
 * {@link GateRegistry}; the tile carries what clients need to draw the gate, to see through it from their own world
 * and to notice someone walking into it.
 */
public class TileLightGate extends TileEntity {

    /** The gates loaded on this client, for drawing and for noticing a step into one. */
    public static final Set<TileLightGate> CLIENT = Collections.newSetFromMap(new WeakHashMap<>());

    public int facing;
    public boolean inside, linked;
    /** The partner, when linked. */
    public int partnerX, partnerY, partnerZ, partnerFacing;
    /** What the partner's side shows through this gate, null when nothing. */
    public GateGeometry.Box far;

    public GateGeometry.Gate gate() {
        return new GateGeometry.Gate(xCoord, yCoord, zCoord, facing);
    }

    public GateGeometry.Gate partner() {
        return new GateGeometry.Gate(partnerX, partnerY, partnerZ, partnerFacing);
    }

    /** Takes the registry's word for what this gate is linked to; true when that changed anything. */
    boolean link(GateRegistry.Entry partner, GateGeometry.Box far) {
        boolean l = partner != null;
        int px = l ? partner.x : 0, py = l ? partner.y : 0, pz = l ? partner.z : 0, pf = l ? partner.facing : 0;
        if (l == linked && px == partnerX
            && py == partnerY
            && pz == partnerZ
            && pf == partnerFacing
            && same(far, this.far)) return false;
        linked = l;
        partnerX = px;
        partnerY = py;
        partnerZ = pz;
        partnerFacing = pf;
        this.far = l ? far : null;
        return true;
    }

    private static boolean same(GateGeometry.Box a, GateGeometry.Box b) {
        if (a == null || b == null) return a == b;
        return a.minX == b.minX && a.minY == b.minY
            && a.minZ == b.minZ
            && a.maxX == b.maxX
            && a.maxY == b.maxY
            && a.maxZ == b.maxZ;
    }

    @Override
    public boolean canUpdate() {
        return false;
    }

    @Override
    public void validate() {
        super.validate();
        if (worldObj != null && worldObj.isRemote) synchronized (CLIENT) {
            CLIENT.add(this);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        forget();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        forget();
    }

    private void forget() {
        if (worldObj != null && worldObj.isRemote) synchronized (CLIENT) {
            CLIENT.remove(this);
        }
    }

    /** Tells the clients that look at it what changed. */
    public void changed() {
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setByte("facing", (byte) facing);
        t.setBoolean("inside", inside);
        t.setBoolean("linked", linked);
        if (linked) {
            t.setIntArray("partner", new int[] { partnerX, partnerY, partnerZ, partnerFacing });
            if (far != null)
                t.setIntArray("far", new int[] { far.minX, far.minY, far.minZ, far.maxX, far.maxY, far.maxZ });
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        facing = t.getByte("facing") & 3;
        inside = t.getBoolean("inside");
        linked = t.getBoolean("linked");
        int[] p = t.getIntArray("partner"), f = t.getIntArray("far");
        if (p.length == 4) {
            partnerX = p[0];
            partnerY = p[1];
            partnerZ = p[2];
            partnerFacing = p[3] & 3;
        }
        far = linked && f.length == 6 ? new GateGeometry.Box(f[0], f[1], f[2], f[3], f[4], f[5]) : null;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound t = new NBTTagCompound();
        writeToNBT(t);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, t);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
    }

    /** The frame and membrane are drawn around the block, three wide and three high. */
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return AxisAlignedBB.getBoundingBox(xCoord - 2, yCoord, zCoord - 2, xCoord + 3, yCoord + 4, zCoord + 3);
    }
}
