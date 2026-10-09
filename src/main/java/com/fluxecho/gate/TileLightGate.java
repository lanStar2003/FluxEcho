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
 * A light gate: which way it faces, whether it is the one inside a plot, and whether it has a partner. The pairing
 * itself lives in {@link GateRegistry}; clients only learn what they need to draw the gate and to notice someone
 * walking into it.
 */
public class TileLightGate extends TileEntity {

    /** The gates loaded on this client, for drawing and for noticing a step into one. */
    public static final Set<TileLightGate> CLIENT = Collections.newSetFromMap(new WeakHashMap<>());

    public int facing;
    public boolean inside, linked;

    public GateGeometry.Gate gate() {
        return new GateGeometry.Gate(xCoord, yCoord, zCoord, facing);
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
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        facing = t.getByte("facing") & 3;
        inside = t.getBoolean("inside");
        linked = t.getBoolean("linked");
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
