package com.fluxecho.campus;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.fluxecho.core.Owners;
import com.fluxecho.nexus.NexusRegistry;
import com.fluxecho.nexus.TileNexus;

/**
 * The supply port's tile: a one-slot inventory open to hoppers, pipes and export buses on every side that feeds the
 * job its linked nexus is building. It only accepts what that job still needs, and only once a member started it
 * ({@link Campus#offer}), so automation cannot fill a ledger nobody asked for; whatever it accepts is credited to the
 * nexus's ledger at once and again every tick, so the slot is nearly always empty.
 * <p>
 * The link is the nexus controller's position (NBT {@code Nexus}). The establish (and forum) job links the port it
 * places to its own nexus ({@link #linkAt}); a port placed by hand links to the nearest loaded nexus of the placer's
 * team within {@link #RANGE} blocks, and looks again by itself while its nexus is gone. The port belongs to that team
 * from then on: only its members can link it by hand or take back what it holds ({@link #mayUse}), so a port that
 * lost its nexus cannot be taken over (and its automation turned to feed another team's ledger).
 */
public class TileSupplyPort extends TileEntity implements ISidedInventory {

    /** How far (blocks, to the nexus centre) a hand-placed port looks for its nexus. */
    public static final int RANGE = 128;
    private static final int[] SLOTS = { 0 };

    private ItemStack slot;
    /** The linked nexus controller {x, y, z}, or null. */
    private int[] link;
    /** The team whose nexus a hand-placed port looks for. */
    private UUID team;
    /** The way its front faces (a horizontal ForgeDirection ordinal). */
    private int facing = 3;

    // live, server
    private TileNexus cached;
    private int idle;
    private long validAt = Long.MIN_VALUE;
    private Item validItem;
    private int validMeta;
    private boolean validResult;

    /**
     * Links the port at the position (if one stands there) to the nexus; the campus builder calls it after placing one.
     */
    public static void linkAt(World w, int x, int y, int z, TileNexus nexus) {
        if (w == null || nexus == null || !w.blockExists(x, y, z)) return;
        if (w.getTileEntity(x, y, z) instanceof TileSupplyPort p) {
            p.link(nexus);
            p.faceWalkway(nexus);
        }
    }

    // ---- the link

    /** Links the port to the nexus and takes over its owner's team. */
    public void link(TileNexus nexus) {
        link = new int[] { nexus.xCoord, nexus.yCoord, nexus.zCoord };
        team = nexus.team();
        cached = nexus;
        markDirty();
    }

    /** Forgets the link (the team stays, so a new nexus of the team is found by itself). */
    public void unlink() {
        link = null;
        cached = null;
        markDirty();
    }

    public boolean linked() {
        return link != null;
    }

    /** The linked nexus controller {x, y, z}, or null. */
    public int[] linkPos() {
        return link == null ? null : link.clone();
    }

    /** The linked nexus while it is loaded, else null. Server side. */
    public TileNexus nexus() {
        if (link == null || worldObj == null || worldObj.isRemote) return null;
        if (cached != null && !cached.isInvalid()
            && cached.getWorldObj() == worldObj
            && cached.xCoord == link[0]
            && cached.yCoord == link[1]
            && cached.zCoord == link[2]) return cached;
        cached = null;
        if (!worldObj.blockExists(link[0], link[1], link[2])) return null;
        if (worldObj.getTileEntity(link[0], link[1], link[2]) instanceof TileNexus n && !n.isInvalid()) cached = n;
        return cached;
    }

    /**
     * Whether the player may link the port or take from it: a member of the team it belongs to (or of its linked
     * nexus), or anyone while it belongs to no team yet.
     */
    public boolean mayUse(EntityPlayer p) {
        if (p == null) return false;
        if (team == null || team.equals(Owners.team(p.getUniqueID()))) return true;
        TileNexus n = nexus();
        return n != null && n.member(p);
    }

    /**
     * Links to the nearest loaded nexus of the player's team within {@link #RANGE} and remembers the team; returns the
     * nexus, or null when there is none (the team is remembered either way). Nothing changes for a player who may not
     * use the port ({@link #mayUse}): a port keeps the team it belongs to.
     */
    public TileNexus linkFor(EntityPlayer p) {
        if (p == null || worldObj == null || worldObj.isRemote || !mayUse(p)) return null;
        if (team == null) team = Owners.team(p.getUniqueID());
        markDirty();
        TileNexus n = nearest(team);
        if (n != null) link(n);
        return n;
    }

    /** The team the port belongs to, or null before anyone linked it. */
    public UUID team() {
        return team;
    }

    /** The nearest loaded nexus within range whose teams include the team, or null. */
    private TileNexus nearest(UUID t) {
        if (t == null) return null;
        TileNexus best = null;
        double bestD = (double) RANGE * RANGE;
        for (TileNexus n : NexusRegistry.nexuses(worldObj.provider.dimensionId)) {
            if (n.isInvalid() || n.getWorldObj() != worldObj
                || !n.teams()
                    .contains(t))
                continue;
            int[] c = n.centre();
            double dx = c[0] - xCoord, dy = c[1] - yCoord, dz = c[2] - zCoord;
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    /**
     * Turns the front to the campus's main axis (the walkway from the gate to the nexus), so a port the builder stood
     * beside the axis faces the people walking along it; a port on the axis itself faces the nexus.
     */
    void faceWalkway(TileNexus nexus) {
        int[] c = nexus.centre();
        int fx = nexus.front().offsetX, fz = nexus.front().offsetZ;
        int a = (xCoord - c[0]) * fx + (zCoord - c[2]) * fz;
        int tx = c[0] + a * fx - xCoord, tz = c[2] + a * fz - zCoord;
        if (tx == 0 && tz == 0) {
            tx = c[0] - xCoord;
            tz = c[2] - zCoord;
        }
        setFacing(Math.abs(tx) >= Math.abs(tz) ? (tx >= 0 ? 5 : 4) : (tz >= 0 ? 3 : 2));
    }

    public int facing() {
        return facing;
    }

    public void setFacing(int f) {
        facing = f >= 2 && f <= 5 ? f : 3;
        markDirty();
        if (worldObj != null && !worldObj.isRemote) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    // ---- feeding the nexus

    /** What the linked nexus's job takes of the stack: the rest, or null when it took all (or the stack was null). */
    public ItemStack feed(ItemStack s, boolean simulate) {
        if (s == null) return null;
        TileNexus n = nexus();
        return n == null ? s
            : n.campus()
                .offer(s, simulate);
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        long now = worldObj.getTotalWorldTime();
        if (slot != null) {
            // what the job does not want stays; it is offered again every half second instead of every tick
            if (idle > 0) idle--;
            else {
                int before = slot.stackSize;
                slot = feed(slot, false);
                if (slot != null && slot.stackSize == before) idle = 10;
                else markDirty();
            }
        }
        if (now % 100 == 0 && team != null) relink();
    }

    /** A lost nexus (its core broken, its position empty) is let go and the nearest one of the team taken instead. */
    private void relink() {
        if (link != null) {
            if (!worldObj.blockExists(link[0], link[1], link[2]) || nexus() != null) return;
            unlink();
        }
        TileNexus n = nearest(team);
        if (n != null) link(n);
    }

    // ---- the inventory: one slot on every side

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return SLOTS;
    }

    @Override
    public boolean canInsertItem(int i, ItemStack s, int side) {
        return isItemValidForSlot(i, s);
    }

    /** Leftovers the job turned down can be pulled out again, so a pipe never jams on them. */
    @Override
    public boolean canExtractItem(int i, ItemStack s, int side) {
        return i == 0;
    }

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        return i == 0 ? slot : null;
    }

    @Override
    public ItemStack decrStackSize(int i, int n) {
        if (i != 0 || slot == null || n <= 0) return null;
        ItemStack out;
        if (slot.stackSize <= n) {
            out = slot;
            slot = null;
        } else out = slot.splitStack(n);
        markDirty();
        return out;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int i) {
        return null;
    }

    /** What is put in is credited at once; only what the job turned down stays in the slot. */
    @Override
    public void setInventorySlotContents(int i, ItemStack s) {
        if (i != 0) return;
        slot = s == null || s.stackSize <= 0 ? null : s;
        if (slot != null && worldObj != null && !worldObj.isRemote) {
            slot = feed(slot, false);
            idle = 0;
        }
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "tile.fluxecho.supply_port.name";
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

    /**
     * True only while the linked nexus is loaded and its started job takes some of the stack. Pipes ask this often, so
     * the answer for one item kind is kept for the rest of the tick.
     */
    @Override
    public boolean isItemValidForSlot(int i, ItemStack s) {
        if (i != 0 || s == null || s.getItem() == null || worldObj == null || worldObj.isRemote) return false;
        long now = worldObj.getTotalWorldTime();
        if (now == validAt && s.getItem() == validItem && s.getItemDamage() == validMeta && !s.hasTagCompound())
            return validResult;
        ItemStack left = feed(s, true);
        boolean takes = left == null || left.stackSize < s.stackSize;
        validAt = now;
        validItem = s.getItem();
        validMeta = s.getItemDamage();
        validResult = takes;
        if (s.hasTagCompound()) validAt = Long.MIN_VALUE;
        return takes;
    }

    // ---- saving and the client copy

    @Override
    public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        if (slot != null) t.setTag("Slot", slot.writeToNBT(new NBTTagCompound()));
        if (link != null) t.setIntArray("Nexus", link.clone());
        if (team != null) t.setString("Team", team.toString());
        t.setByte("Facing", (byte) facing);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        slot = t.hasKey("Slot") ? ItemStack.loadItemStackFromNBT(t.getCompoundTag("Slot")) : null;
        int[] l = t.getIntArray("Nexus");
        link = l.length == 3 ? l : null;
        try {
            team = t.hasKey("Team") ? UUID.fromString(t.getString("Team")) : null;
        } catch (IllegalArgumentException e) {
            team = null;
        }
        int f = t.getByte("Facing");
        facing = f >= 2 && f <= 5 ? f : 3;
        cached = null;
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
