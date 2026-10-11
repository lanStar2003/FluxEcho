package com.fluxecho.campus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import com.fluxecho.core.Owners;
import com.fluxecho.logic.RecycleSlots;
import com.fluxecho.nexus.TileNexus;
import com.gtnewhorizons.modularui.api.screen.ITileWithModularUI;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;

/**
 * The recycle bin's tile (0.10.1): where the recycle store of a nexus's campus ({@link Campus#recycle}) is taken out.
 * It holds nothing itself; it is a window onto the store of the nexus it is linked to.
 * <p>
 * For automation it is an {@link ISidedInventory} of {@link RecycleSlots#SLOTS} virtual slots, take only, on every
 * side: the store's largest kinds, each as a stack ({@link RecycleSlots} keeps the view honest when several hoppers,
 * pipes, AE buses and players act in the same tick). The stacks it hands out are live: a caller that shrinks one it
 * read (some mods empty an inventory that way) takes those items out of the store at its next access, as one that
 * calls {@code decrStackSize} or sets a smaller stack does. Nothing can be put in.
 * <p>
 * Right-click opens its screen ({@link RecycleGui}). The link works as the supply port's: the establish and forum
 * jobs link the bin they stand ({@link NexusLink#linkTo}), and a bin placed by hand links to the nearest loaded nexus
 * of its placer's team within {@link TileSupplyPort#RANGE} blocks ({@link TileSupplyPort#nearestNexus}), looking again
 * by itself while its nexus is gone. It belongs to that team from then on; only its members (or the linked nexus's)
 * may link it or take from its screen ({@link #mayUse}). NBT {@code Nexus/Team/Facing}, description {@code F}.
 */
public class TileRecycleBin extends TileEntity implements ISidedInventory, NexusLink, ITileWithModularUI {

    private static final int[] ALL = new int[RecycleSlots.SLOTS];
    static {
        for (int i = 0; i < ALL.length; i++) ALL[i] = i;
    }

    /** The linked nexus controller {x, y, z}, or null. */
    private int[] link;
    /** The team whose nexus a hand-placed bin looks for. */
    private UUID team;
    /** The way its front faces (a horizontal ForgeDirection ordinal). */
    private int facing = 3;

    // live, server
    /** The view of the linked nexus's store (made anew when the bin is linked to another store). */
    private RecycleSlots<NBTTagCompound> view = new RecycleSlots<>();
    /** The stacks handed out for the view's current revision (live: a caller may shrink them). */
    private final ItemStack[] shown = new ItemStack[RecycleSlots.SLOTS];
    private int shownRev = -1;

    // ---- the link

    /** Links the bin to the nexus whose campus placed it, and faces the walkway (the builder calls it). */
    @Override
    public void linkTo(TileNexus nexus) {
        if (nexus == null) return;
        link(nexus);
        setFacing(TileSupplyPort.walkwayFacing(nexus, xCoord, zCoord));
    }

    /** Links the bin to the nexus and takes over its owner's team. */
    public void link(TileNexus nexus) {
        link = new int[] { nexus.xCoord, nexus.yCoord, nexus.zCoord };
        team = nexus.team();
        markDirty();
    }

    /** Forgets the link (the team stays, so a new nexus of the team is found by itself). */
    public void unlink() {
        link = null;
        markDirty();
    }

    public boolean linked() {
        return link != null;
    }

    /** The linked nexus controller {x, y, z}, or null. */
    public int[] linkPos() {
        return link == null ? null : link.clone();
    }

    /**
     * The linked nexus while it is loaded, else null. Server side. Looked up in the world every time
     * ({@link TileSupplyPort#loadedNexus}): a nexus whose chunk unloads is never invalidated, so a kept reference
     * would let automation drain a copy of the store whose saved self comes back whole.
     */
    public TileNexus nexus() {
        if (worldObj == null || worldObj.isRemote) return null;
        return TileSupplyPort.loadedNexus(worldObj, link);
    }

    /** The recycle store of the linked nexus while it is loaded, else null. Server side. */
    public RecycleStore store() {
        TileNexus n = nexus();
        return n == null ? null
            : n.campus()
                .recycle();
    }

    /**
     * Whether the player may link the bin or take from its screen: a member of the team it belongs to (or of its
     * linked nexus), or anyone while it belongs to no team yet.
     */
    public boolean mayUse(EntityPlayer p) {
        if (p == null) return false;
        if (team == null || team.equals(Owners.team(p.getUniqueID()))) return true;
        TileNexus n = nexus();
        return n != null && n.member(p);
    }

    /**
     * Links to the nearest loaded nexus of the player's team within range and remembers the team; returns the nexus, or
     * null when there is none (the team is remembered either way). Nothing changes for a player who may not use it.
     */
    public TileNexus linkFor(EntityPlayer p) {
        if (p == null || worldObj == null || worldObj.isRemote || !mayUse(p)) return null;
        if (team == null) team = Owners.team(p.getUniqueID());
        markDirty();
        TileNexus n = TileSupplyPort.nearestNexus(worldObj, xCoord, yCoord, zCoord, team);
        if (n != null) link(n);
        return n;
    }

    /** The team the bin belongs to, or null before anyone linked it. */
    public UUID team() {
        return team;
    }

    public int facing() {
        return facing;
    }

    public void setFacing(int f) {
        facing = f >= 2 && f <= 5 ? f : 3;
        markDirty();
        if (worldObj != null && !worldObj.isRemote) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    /** Whether the tile still stands where it was opened (the screen closes when it does not). */
    boolean standing() {
        return !isInvalid() && worldObj != null
            && worldObj.blockExists(xCoord, yCoord, zCoord)
            && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        if (worldObj.getTotalWorldTime() % 100 == 0 && team != null) relink();
    }

    /** A lost nexus (its core broken, its position empty) is let go and the nearest one of the team taken instead. */
    private void relink() {
        if (link != null) {
            if (!worldObj.blockExists(link[0], link[1], link[2]) || nexus() != null) return;
            unlink();
        }
        TileNexus n = TileSupplyPort.nearestNexus(worldObj, xCoord, yCoord, zCoord, team);
        if (n != null) link(n);
    }

    // ---- the virtual slots

    /** The view's way into a store: its largest kinds and the stack size of each (looked up once per kind). */
    private static final class Source implements RecycleSlots.Store<NBTTagCompound> {

        final RecycleStore store;
        final Map<NBTTagCompound, ItemStack> protos = new HashMap<>();

        Source(RecycleStore store) {
            this.store = store;
        }

        @Override
        public List<NBTTagCompound> largest() {
            List<NBTTagCompound> out = new ArrayList<>();
            for (RecycleStore.Entry e : store.list()) {
                // the slots show only the largest kinds; a few more for kinds whose item is gone
                if (out.size() >= RecycleSlots.SLOTS * 2) break;
                out.add(e.kind);
            }
            return out;
        }

        @Override
        public long count(NBTTagCompound kind) {
            return store.count(kind);
        }

        @Override
        public int stackSize(NBTTagCompound kind) {
            ItemStack p = proto(kind);
            return p == null ? 0 : Math.max(1, p.getMaxStackSize());
        }

        ItemStack proto(NBTTagCompound kind) {
            if (protos.containsKey(kind)) return protos.get(kind);
            ItemStack p = RecycleStore.stackOf(kind, 1);
            if (protos.size() > 512) protos.clear();
            protos.put(kind, p);
            return p;
        }

        @Override
        public long extract(NBTTagCompound kind, long max) {
            return store.extract(kind, max);
        }

        @Override
        public void add(NBTTagCompound kind, long n) {
            store.add(kind, n);
        }

        @Override
        public int version() {
            return store.version();
        }
    }

    private Source source;

    /**
     * Brings the view up to date before any access (server side): first the live stacks a caller shrank since the last
     * access are taken out of the store, then the view is made again if the tick or the store changed, and its stacks
     * with it. Returns the store's source, or null while the bin has no loaded nexus (it shows nothing then).
     */
    private Source sync() {
        if (worldObj == null || worldObj.isRemote) return null;
        RecycleStore store = store();
        if (store == null) {
            clearShown();
            return null;
        }
        if (source == null || source.store != store) {
            source = new Source(store);
            view = new RecycleSlots<>();
            clearShown();
            shownRev = -1;
        }
        if (shownRev == view.rev()) {
            // a caller shrank a stack it read: those items left
            for (int i = 0; i < shown.length; i++) {
                ItemStack s = shown[i];
                if (s == null || s.stackSize >= view.size(i)) continue;
                if (view.set(source, i, Math.max(0, s.stackSize)) != 0) markDirty();
                follow(i);
            }
        }
        view.refresh(source, worldObj.getTotalWorldTime());
        if (shownRev != view.rev()) {
            shownRev = view.rev();
            for (int i = 0; i < shown.length; i++) {
                NBTTagCompound k = view.kind(i);
                ItemStack p = k == null ? null : source.proto(k);
                if (p == null) shown[i] = null;
                else {
                    shown[i] = p.copy();
                    shown[i].stackSize = view.size(i);
                }
            }
        }
        return source;
    }

    private void clearShown() {
        for (int i = 0; i < shown.length; i++) shown[i] = null;
    }

    /** Keeps the live stack of a slot at what the view shows after the view changed it. */
    private void follow(int i) {
        ItemStack s = shown[i];
        if (s == null) return;
        int n = view.size(i);
        if (n <= 0) shown[i] = null;
        else s.stackSize = n;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return ALL;
    }

    @Override
    public boolean canInsertItem(int i, ItemStack s, int side) {
        return false;
    }

    @Override
    public boolean canExtractItem(int i, ItemStack s, int side) {
        return true;
    }

    @Override
    public int getSizeInventory() {
        return RecycleSlots.SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        if (i < 0 || i >= RecycleSlots.SLOTS || sync() == null) return null;
        return shown[i];
    }

    @Override
    public ItemStack decrStackSize(int i, int n) {
        if (i < 0 || i >= RecycleSlots.SLOTS || n <= 0) return null;
        Source s = sync();
        if (s == null || shown[i] == null) return null;
        ItemStack kind = shown[i];
        int took = view.take(s, i, n);
        if (took <= 0) return null;
        ItemStack out = kind.copy();
        out.stackSize = took;
        follow(i);
        markDirty();
        return out;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int i) {
        return null;
    }

    /**
     * Only ever takes out: a smaller stack of the slot's own kind (or none) takes the difference out of the store; a
     * larger one puts back what was taken from the slot this tick (a hopper that had no room); anything else is
     * refused. A slot that was emptied this tick still knows its kind, so a hopper that took the last item and puts
     * it back finds it again.
     */
    @Override
    public void setInventorySlotContents(int i, ItemStack s) {
        if (i < 0 || i >= RecycleSlots.SLOTS) return;
        Source src = sync();
        if (src == null) return;
        NBTTagCompound k = view.kind(i);
        ItemStack kind = shown[i] != null ? shown[i] : k == null ? null : src.proto(k);
        if (kind == null) return;
        if (s != null && s.stackSize > 0 && !same(kind, s)) return;
        int moved = view.set(src, i, s == null ? 0 : Math.max(0, s.stackSize));
        if (shown[i] == null && view.size(i) > 0) {
            // the slot was emptied this tick and got items back: its live stack again (a copy, the prototype is shared)
            shown[i] = kind.copy();
            shown[i].stackSize = view.size(i);
        } else follow(i);
        if (moved != 0) markDirty();
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage()
            && ItemStack.areItemStackTagsEqual(a, b);
    }

    @Override
    public String getInventoryName() {
        return "tile.fluxecho.recycle_bin.name";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer p) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && p.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int i, ItemStack s) {
        return false;
    }

    // ---- the screen

    @Override
    public ModularWindow createWindow(UIBuildContext ctx) {
        return RecycleGui.window(this, ctx);
    }

    // ---- saving and the client copy

    @Override
    public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        if (link != null) t.setIntArray("Nexus", link.clone());
        if (team != null) t.setString("Team", team.toString());
        t.setByte("Facing", (byte) facing);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        int[] l = t.getIntArray("Nexus");
        link = l.length == 3 ? l : null;
        try {
            team = t.hasKey("Team") ? UUID.fromString(t.getString("Team")) : null;
        } catch (IllegalArgumentException e) {
            team = null;
        }
        int f = t.getByte("Facing");
        facing = f >= 2 && f <= 5 ? f : 3;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound t = new NBTTagCompound();
        t.setByte("F", (byte) facing);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, t);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        int f = pkt.func_148857_g()
            .getByte("F");
        facing = f >= 2 && f <= 5 ? f : 3;
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }
}
