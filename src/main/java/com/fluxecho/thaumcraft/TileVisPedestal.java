package com.fluxecho.thaumcraft;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.Config;
import com.fluxecho.core.Owners;
import com.fluxecho.logic.VisFlow;

import gregtech.api.interfaces.tileentity.IEnergyConnected;
import gregtech.common.misc.WirelessNetworkManager;
import thaumcraft.common.Thaumcraft;

/**
 * Flux Vis Pedestal. Deep in the flux layer the aspects lie thick; the pedestal draws the primals up from there with
 * EU, live, into the wand floating above it. Power comes from GT cables (any side but the top, any voltage) into a
 * buffer, or, with a link module, from the team's GT wireless network. Modules: extraction (more vis per tick),
 * wireless (also fills what the team carries nearby), link (power from the wireless network).
 */
public class TileVisPedestal extends TileEntity implements IEnergyConnected {

    public static final int MODULE_SLOTS = 4;
    private static final int WIRELESS_PERIOD = 10, LINK_PERIOD = 20, SYNC_PERIOD = 10;

    private ItemStack wand;
    private final ItemStack[] modules = new ItemStack[MODULE_SLOTS];
    private long energy;
    private UUID owner;
    /** Whether vis flowed in the last second; synced for the animation. */
    private boolean charging;
    private boolean chargedThisTick;
    private int flowTicks;

    // ---- state

    public ItemStack wand() {
        return wand;
    }

    public boolean charging() {
        return charging;
    }

    public long energy() {
        return energy;
    }

    public static long capacity() {
        return Config.visPedestalBuffer;
    }

    public int count(ItemVisModule.Kind k) {
        int n = 0;
        for (ItemStack m : modules) if (ItemVisModule.Kind.of(m) == k) n++;
        return n;
    }

    public int installed() {
        int n = 0;
        for (ItemStack m : modules) if (m != null) n++;
        return n;
    }

    /** Centivis per primal per tick. */
    public int rate() {
        return VisFlow.rate(Config.visPedestalRate, Config.visModuleRate, count(ItemVisModule.Kind.EXTRACTION));
    }

    public void placedBy(EntityPlayer p) {
        owner = p.getUniqueID();
        markDirty();
    }

    private UUID team() {
        return Owners.team(owner);
    }

    // ---- what players do with it

    /** Puts one wand on the pedestal; false when it already holds one. */
    public boolean putWand(ItemStack s) {
        if (wand != null || !VisItems.chargeable(s)) return false;
        wand = s.copy();
        wand.stackSize = 1;
        changed();
        return true;
    }

    public ItemStack takeWand() {
        ItemStack w = wand;
        wand = null;
        if (w != null) changed();
        return w;
    }

    /** Installs one module; false when the slots are full or it is not a module. */
    public boolean install(ItemStack s) {
        if (ItemVisModule.Kind.of(s) == null) return false;
        ItemVisModule.Kind k = ItemVisModule.Kind.of(s);
        if (k != ItemVisModule.Kind.EXTRACTION && count(k) > 0) return false;
        for (int i = 0; i < modules.length; i++) if (modules[i] == null) {
            modules[i] = s.copy();
            modules[i].stackSize = 1;
            changed();
            return true;
        }
        return false;
    }

    /** Takes out the last module installed; null when there is none. */
    public ItemStack uninstall() {
        for (int i = modules.length - 1; i >= 0; i--) if (modules[i] != null) {
            ItemStack m = modules[i];
            modules[i] = null;
            changed();
            return m;
        }
        return null;
    }

    /** Everything to drop when the block breaks. */
    public ItemStack[] contents() {
        ItemStack[] out = new ItemStack[MODULE_SLOTS + 1];
        out[0] = wand;
        System.arraycopy(modules, 0, out, 1, MODULE_SLOTS);
        return out;
    }

    private void changed() {
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    // ---- ticking

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) {
            if (charging && wand != null) PedestalFx.flow(worldObj, xCoord, yCoord, zCoord);
            return;
        }
        if (!Config.thaumEnabled) return;
        long t = worldObj.getTotalWorldTime();
        if (count(ItemVisModule.Kind.LINK) > 0 && t % LINK_PERIOD == 0) link();
        if (wand != null) chargeOne(wand, rate());
        if (count(ItemVisModule.Kind.WIRELESS) > 0 && t % WIRELESS_PERIOD == 0) wireless();
        if (chargedThisTick) flowTicks = 20;
        else if (flowTicks > 0) flowTicks--;
        chargedThisTick = false;
        if (t % SYNC_PERIOD == 0 && charging != (flowTicks > 0)) {
            charging = flowTicks > 0;
            changed();
        }
    }

    private void chargeOne(ItemStack s, int rate) {
        VisFlow f = VisItems.charge(s, rate, Config.visEuPerCentiVis, energy);
        if (f.eu <= 0) return;
        energy -= f.eu;
        chargedThisTick = true;
        markDirty();
    }

    /** The wireless module: what the team carries within range, in this world. */
    private void wireless() {
        UUID team = team();
        if (team == null) return;
        double range = Config.visWirelessRange, r2 = range * range;
        int rate = rate() * WIRELESS_PERIOD;
        for (Object o : worldObj.playerEntities) {
            if (!(o instanceof EntityPlayer p) || !team.equals(Owners.team(p.getUniqueID()))) continue;
            if (p.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) > r2) continue;
            for (ItemStack s : p.inventory.mainInventory) if (VisItems.chargeable(s)) chargeOne(s, rate);
        }
    }

    /** The link module: tops the buffer up from the team's GT wireless network. */
    private void link() {
        if (owner == null || energy >= capacity() / 2) return;
        long want = Math.min(capacity() - energy, (long) Config.visLinkEut * LINK_PERIOD);
        try {
            WirelessNetworkManager.strongCheckOrAddUser(owner);
            long have = WirelessNetworkManager.getUserEU(owner)
                .min(BigInteger.valueOf(want))
                .longValue();
            if (have > 0 && WirelessNetworkManager.addEUToGlobalEnergyMap(owner, -have)) {
                energy += have;
                markDirty();
            }
        } catch (RuntimeException ignored) {
            // the wireless network is not there (yet): cables only
        }
    }

    // ---- GT power

    @Override
    public long injectEnergyUnits(ForgeDirection side, long voltage, long amperage) {
        if (voltage <= 0 || amperage <= 0 || !inputEnergyFrom(side)) return 0;
        long amps = Math.min(amperage, (capacity() - energy) / voltage);
        if (amps <= 0) return 0;
        energy += amps * voltage;
        markDirty();
        return amps;
    }

    @Override
    public boolean inputEnergyFrom(ForgeDirection side) {
        return side != ForgeDirection.UP;
    }

    @Override
    public boolean outputsEnergyTo(ForgeDirection side) {
        return false;
    }

    @Override
    public byte getColorization() {
        return -1;
    }

    @Override
    public byte setColorization(byte color) {
        return -1;
    }

    // ---- saving and syncing

    @Override
    public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setLong("Energy", energy);
        if (owner != null) {
            t.setLong("OwnerMost", owner.getMostSignificantBits());
            t.setLong("OwnerLeast", owner.getLeastSignificantBits());
        }
        writeShown(t);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        energy = Math.max(0, t.getLong("Energy"));
        owner = t.hasKey("OwnerMost") ? new UUID(t.getLong("OwnerMost"), t.getLong("OwnerLeast")) : null;
        readShown(t);
    }

    /** What the client needs: the wand, the modules and whether it is charging. */
    private void writeShown(NBTTagCompound t) {
        if (wand != null) t.setTag("Wand", wand.writeToNBT(new NBTTagCompound()));
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < modules.length; i++) if (modules[i] != null) {
            NBTTagCompound m = modules[i].writeToNBT(new NBTTagCompound());
            m.setByte("Slot", (byte) i);
            list.appendTag(m);
        }
        t.setTag("Modules", list);
        t.setBoolean("Charging", charging);
    }

    private void readShown(NBTTagCompound t) {
        wand = t.hasKey("Wand") ? ItemStack.loadItemStackFromNBT(t.getCompoundTag("Wand")) : null;
        Arrays.fill(modules, null);
        NBTTagList list = t.getTagList("Modules", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound m = list.getCompoundTagAt(i);
            int slot = m.getByte("Slot");
            if (slot >= 0 && slot < modules.length) modules[slot] = ItemStack.loadItemStackFromNBT(m);
        }
        charging = t.getBoolean("Charging");
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound t = new NBTTagCompound();
        writeShown(t);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, t);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readShown(pkt.func_148857_g());
    }

    /** Thaumcraft's own particles, client side only. */
    static final class PedestalFx {

        private PedestalFx() {}

        static void flow(World w, int x, int y, int z) {
            if (w.rand.nextInt(3) != 0) return;
            double a = w.rand.nextDouble() * Math.PI * 2;
            double sx = x + 0.5 + Math.cos(a) * 0.45, sz = z + 0.5 + Math.sin(a) * 0.45, sy = y + 0.6;
            Thaumcraft.proxy.wispFX3(
                w,
                sx,
                sy,
                sz,
                x + 0.5,
                y + 1.25,
                z + 0.5,
                0.25f + w.rand.nextFloat() * 0.15f,
                w.rand.nextInt(6),
                true,
                -0.02f);
        }
    }
}
