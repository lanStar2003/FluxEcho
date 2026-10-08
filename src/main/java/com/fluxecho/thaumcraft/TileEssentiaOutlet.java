package com.fluxecho.thaumcraft;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectSource;
import thaumcraft.api.aspects.IEssentiaTransport;

/**
 * The Essentia Outlet: the face of an Essentia Echo towards Thaumcraft. GT's machine tile cannot be an essentia
 * source itself, so this block sits next to the echo and asks it for every essentia it hands out.
 * <ul>
 * <li>An infusion matrix (or anything that drains {@link IAspectSource}s) within 12 blocks takes any learned aspect
 * straight from it.</li>
 * <li>Essentia tubes take the aspect the next tube or jar asks for (a labelled jar, a Thaumatorium), else the
 * outlet's selected aspect (right-click it with a phial).</li>
 * </ul>
 * It holds no essentia: every essentia is made and paid for the moment it leaves.
 */
public class TileEssentiaOutlet extends TileEntity implements IAspectSource, IEssentiaTransport {

    /** Most a tube or a goggles reading sees at once. */
    private static final int SHOWN = 64;

    private Aspect selected;
    private ForgeDirection echoSide = ForgeDirection.UNKNOWN;

    @Override
    public boolean canUpdate() {
        return false;
    }

    public Aspect selected() {
        return selected;
    }

    public void select(Aspect a) {
        selected = a;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    /** The Essentia Echo next to it, or null; server side. */
    public MTEEssentiaEcho echo() {
        if (worldObj == null || worldObj.isRemote) return null;
        MTEEssentiaEcho e = echoAt(echoSide);
        if (e != null) return e;
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            e = echoAt(d);
            if (e != null) {
                echoSide = d;
                return e;
            }
        }
        echoSide = ForgeDirection.UNKNOWN;
        return null;
    }

    private MTEEssentiaEcho echoAt(ForgeDirection d) {
        if (d == ForgeDirection.UNKNOWN) return null;
        TileEntity te = worldObj.getTileEntity(xCoord + d.offsetX, yCoord + d.offsetY, zCoord + d.offsetZ);
        return te instanceof IGregTechTileEntity g && g.getMetaTileEntity() instanceof MTEEssentiaEcho e ? e : null;
    }

    private int affordable(Aspect a) {
        MTEEssentiaEcho e = echo();
        return e == null || a == null ? 0 : e.affordable(a, SHOWN);
    }

    private boolean pay(Aspect a, int amount, boolean pay) {
        MTEEssentiaEcho e = echo();
        return e != null && e.pay(a, amount, pay);
    }

    // ---------------------------------------------------------------- IAspectContainer (infusion, goggles)

    @Override
    public AspectList getAspects() {
        AspectList l = new AspectList();
        if (selected == null) return l;
        if (worldObj != null && worldObj.isRemote) return l.add(selected, 1);
        int n = affordable(selected);
        return n > 0 ? l.add(selected, n) : l;
    }

    @Override
    public void setAspects(AspectList aspects) {}

    @Override
    public boolean doesContainerAccept(Aspect a) {
        return false;
    }

    @Override
    public int addToContainer(Aspect a, int amount) {
        return amount;
    }

    @Override
    public boolean takeFromContainer(Aspect a, int amount) {
        return pay(a, amount, true);
    }

    @Override
    public boolean takeFromContainer(AspectList list) {
        if (!doesContainerContain(list)) return false;
        for (Aspect a : list.getAspects()) if (!pay(a, list.getAmount(a), true)) return false;
        return true;
    }

    @Override
    public boolean doesContainerContainAmount(Aspect a, int amount) {
        return pay(a, amount, false);
    }

    @Override
    public boolean doesContainerContain(AspectList list) {
        // checked one aspect at a time: shared EU and credit can still run out across them, takeFromContainer stops
        // then
        for (Aspect a : list.getAspects()) if (!pay(a, list.getAmount(a), false)) return false;
        return true;
    }

    @Override
    public int containerContains(Aspect a) {
        return affordable(a);
    }

    // ---------------------------------------------------------------- IEssentiaTransport (tubes)

    @Override
    public boolean isConnectable(ForgeDirection face) {
        return face != echoSide;
    }

    @Override
    public boolean canInputFrom(ForgeDirection face) {
        return false;
    }

    @Override
    public boolean canOutputTo(ForgeDirection face) {
        return true;
    }

    @Override
    public void setSuction(Aspect a, int amount) {}

    @Override
    public Aspect getSuctionType(ForgeDirection face) {
        return null;
    }

    @Override
    public int getSuctionAmount(ForgeDirection face) {
        return 0;
    }

    @Override
    public int takeEssentia(Aspect a, int amount, ForgeDirection face) {
        return pay(a, amount, true) ? amount : 0;
    }

    @Override
    public int addEssentia(Aspect a, int amount, ForgeDirection face) {
        return 0;
    }

    /** What the tube on that face is asking for if it can be made, else the selected aspect. */
    @Override
    public Aspect getEssentiaType(ForgeDirection face) {
        if (worldObj == null || worldObj.isRemote) return selected;
        TileEntity te = worldObj.getTileEntity(xCoord + face.offsetX, yCoord + face.offsetY, zCoord + face.offsetZ);
        if (te instanceof IEssentiaTransport t) {
            Aspect wanted = t.getSuctionType(face.getOpposite());
            if (wanted != null && affordable(wanted) > 0) return wanted;
        }
        return selected;
    }

    @Override
    public int getEssentiaAmount(ForgeDirection face) {
        if (worldObj == null || worldObj.isRemote) return selected == null ? 0 : 1;
        return affordable(getEssentiaType(face));
    }

    @Override
    public int getMinimumSuction() {
        return 0;
    }

    @Override
    public boolean renderExtendedTube() {
        return false;
    }

    // ---------------------------------------------------------------- saving and syncing the selection

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (selected != null) tag.setString("Selected", selected.getTag());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        selected = tag.hasKey("Selected") ? Aspect.getAspect(tag.getString("Selected")) : null;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        if (selected != null) tag.setString("Selected", selected.getTag());
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        selected = tag.hasKey("Selected") ? Aspect.getAspect(tag.getString("Selected")) : null;
    }
}
