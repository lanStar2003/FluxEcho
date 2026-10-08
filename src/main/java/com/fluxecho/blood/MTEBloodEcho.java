package com.fluxecho.blood;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import com.fluxecho.Config;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.MTEEchoMachine;
import com.fluxecho.core.MachineId;
import com.fluxecho.logic.BloodRates;

import WayofTime.alchemicalWizardry.api.items.interfaces.IBloodOrb;
import WayofTime.alchemicalWizardry.api.soulNetwork.SoulNetworkHandler;
import WayofTime.alchemicalWizardry.api.tile.IBloodAltar;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * Blood Echo (MV). A bound blood orb made in the altar sits in the special slot and sets the pace; EU and a little
 * meat become LP, no self-sacrifice needed.
 * <ul>
 * <li>No circuit or circuit 1: into the main tank of a blood altar nearby (like the Well of Suffering does, not
 * through the slow input buffer).</li>
 * <li>Circuit 2: into the orb owner's soul network, up to what the orb holds.</li>
 * </ul>
 * LP made waits in a small buffer until the altar or network has room, so a full altar stops the machine.
 */
public class MTEBloodEcho extends MTEEchoMachine {

    public static final int CIRCUIT_NETWORK = 2;
    /** Ticks per cycle. */
    private static final int CYCLE = 20;
    /** Cycles of LP the buffer holds before the machine waits. */
    private static final int BUFFERED_CYCLES = 3;
    private static final int RESCAN_TICKS = 100;

    /** LP made and not yet delivered. */
    private int buffer;
    /** LP already paid for by meat. */
    private int credit;
    /** LP the running cycle adds to the buffer when it ends. */
    private int pending;

    private int altarX, altarY, altarZ;
    private boolean altarKnown;
    private long nextScan;

    public MTEBloodEcho(int id) {
        super(MachineId.BLOOD_ECHO, id, 1, 1);
    }

    private MTEBloodEcho(String name, String[] description, ITexture[][][] textures) {
        super(MachineId.BLOOD_ECHO, name, description, textures, 1, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTEBloodEcho(mName, mDescriptionArray, mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return BloodModule.map();
    }

    @Override
    public boolean allowSelectCircuit() {
        return true;
    }

    @Override
    protected boolean moduleEnabled() {
        return Config.bloodEnabled;
    }

    @Override
    protected Object[] tooltipArgs() {
        int[] r = Config.lpPerTick;
        return new Object[] { BloodRates.lpPerTick(r, 1), BloodRates.lpPerTick(r, 6), Config.euPerLp, Config.lpPerMeat,
            Config.altarRange, Config.altarHeight };
    }

    private boolean network() {
        return circuit() == CIRCUIT_NETWORK;
    }

    private IBloodOrb orb() {
        ItemStack s = sample();
        return s != null && s.getItem() instanceof IBloodOrb o ? o : null;
    }

    private String orbOwner() {
        String owner = sample() == null ? null : SoulNetworkHandler.getOwnerName(sample());
        return owner == null || owner.isEmpty() ? null : owner;
    }

    static boolean isMeat(ItemStack s) {
        if (s == null) return false;
        Item i = s.getItem();
        if (i == Items.rotten_flesh || i == Items.beef || i == Items.porkchop || i == Items.chicken || i == Items.fish)
            return true;
        int raw = OreDictionary.getOreID("listAllmeatraw");
        for (int id : OreDictionary.getOreIDs(s)) if (id == raw) return true;
        return false;
    }

    @Override
    protected int work() {
        IBloodOrb orb = orb();
        if (orb == null) return idle("no_orb");
        String owner = orbOwner();
        if (owner == null) return idle("orb_unbound");
        int rate = BloodRates.lpPerTick(Config.lpPerTick, orb.getOrbLevel());
        if (rate <= 0) return idle("disabled");
        int amount = rate * CYCLE;

        if (network()) {
            if (SoulNetworkHandler.getCurrentEssence(owner) >= orb.getMaxEssence()) return idle("network_full");
        } else if (altar() == null) return idle("no_altar");
        if (buffer >= amount * BUFFERED_CYCLES) return idle(network() ? "network_full" : "altar_full");

        int meat = BloodRates.meatNeeded(credit, amount, Config.lpPerMeat);
        if (meat > 0) {
            ItemStack in = input(0);
            if (!isMeat(in) || in.stackSize < meat) return idle("no_meat");
            in.stackSize -= meat;
            credit += meat * Config.lpPerMeat;
        }
        credit -= amount;
        pending = amount;
        return start(rate * Config.euPerLp, CYCLE);
    }

    @Override
    public void endProcess() {
        super.endProcess();
        buffer += pending;
        pending = 0;
        flush();
    }

    @Override
    public void onPostTick(IGregTechTileEntity te, long tick) {
        super.onPostTick(te, tick);
        if (!te.isServerSide() || tick % 20 != 0) return;
        flush();
        // GT only rechecks an idle machine every 30 s; the altar or network may have room again sooner
        if ("altar_full".equals(status) || "network_full".equals(status) || "no_altar".equals(status)) {
            te.markInventoryBeenModified();
        }
    }

    /** Hands the buffer to the altar or network, as much as fits. */
    private void flush() {
        if (buffer <= 0 || !Config.bloodEnabled) return;
        if (network()) {
            IBloodOrb orb = orb();
            String owner = orbOwner();
            if (orb == null || owner == null) return;
            buffer -= Math.max(0, SoulNetworkHandler.addCurrentEssenceToMaximum(owner, buffer, orb.getMaxEssence()));
        } else {
            IBloodAltar a = altar();
            if (a == null) return;
            int send = BloodRates
                .altarShare(buffer, a.getCapacity() - a.getCurrentBlood(), a.getSelfSacrificeMultiplier());
            if (send <= 0) return;
            a.sacrificialDaggerCall(send, false);
            a.startCycle();
            buffer -= send;
        }
    }

    /** The nearest altar in range: the last one found while it stands, else a fresh search every few seconds. */
    private IBloodAltar altar() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        World w = te.getWorld();
        if (altarKnown && w.getTileEntity(altarX, altarY, altarZ) instanceof IBloodAltar a) return a;
        altarKnown = false;
        if (w.getTotalWorldTime() < nextScan) return null;
        nextScan = w.getTotalWorldTime() + RESCAN_TICKS;
        int x0 = te.getXCoord(), y0 = te.getYCoord(), z0 = te.getZCoord();
        int r = Config.altarRange, h = Config.altarHeight;
        IBloodAltar best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dy = -h; dy <= h; dy++) {
            int y = y0 + dy;
            if (y < 0 || y >= w.getHeight()) continue;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                TileEntity t = w.getTileEntity(x0 + dx, y, z0 + dz);
                int d = dx * dx + dy * dy + dz * dz;
                if (t instanceof IBloodAltar a && d < bestDist) {
                    best = a;
                    bestDist = d;
                    altarX = x0 + dx;
                    altarY = y;
                    altarZ = z0 + dz;
                }
            }
        }
        altarKnown = best != null;
        return best;
    }

    @Override
    public void saveNBTData(NBTTagCompound t) {
        super.saveNBTData(t);
        t.setInteger("feBuffer", buffer);
        t.setInteger("feCredit", credit);
        t.setInteger("fePending", pending);
    }

    @Override
    public void loadNBTData(NBTTagCompound t) {
        super.loadNBTData(t);
        buffer = Math.max(0, t.getInteger("feBuffer"));
        credit = Math.max(0, t.getInteger("feCredit"));
        pending = Math.max(0, t.getInteger("fePending"));
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        IBloodOrb orb = orb();
        tag.setInteger("feRate", orb == null ? 0 : BloodRates.lpPerTick(Config.lpPerTick, orb.getOrbLevel()));
        tag.setInteger("feBuffer", buffer);
        tag.setBoolean("feNetwork", network());
    }

    @Override
    public void getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(stack, tip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        if (!tag.hasKey("feRate")) return;
        tip.add(
            EchoText.t(
                tag.getBoolean("feNetwork") ? "blood_echo.waila_network" : "blood_echo.waila_altar",
                tag.getInteger("feRate"),
                tag.getInteger("feBuffer")));
    }
}
