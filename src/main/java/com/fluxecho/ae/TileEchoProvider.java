package com.fluxecho.ae;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoPattern;
import com.fluxecho.core.MTEEchoMachine;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridNotification;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Echo ME Provider. Sits on an AE network (one channel) next to echo machines, offers AE's autocrafting what those
 * machines can make right now ({@link MTEEchoMachine#echoPatterns}), pushes a job's ingredients into the machine and
 * brings whatever lands in the machines' output slots back into the network once a second. Built on AE2's public
 * API only: a grid node of its own, no AE base classes.
 */
public class TileEchoProvider extends TileEntity implements IGridBlock, IActionHost, ICraftingProvider {

    private static final int PERIOD = 20;

    private IGridNode node;
    private NBTTagCompound nodeTag;
    private int playerId = -1;
    private int ticks;
    private final Set<EchoPatternDetails> offered = new LinkedHashSet<>();
    private boolean dirty = true;

    // ---------------------------------------------------------------- node life

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        if (node == null) {
            node = AEApi.instance()
                .createGridNode(this);
            if (nodeTag != null) node.loadFromNBT("node", nodeTag);
            nodeTag = null;
            if (playerId >= 0) node.setPlayerID(playerId);
            node.updateState();
        }
        if (++ticks % PERIOD != 0 && !dirty) return;
        dirty = false;
        refreshPatterns();
        returnOutputs();
    }

    private void drop() {
        if (node != null) {
            node.destroy();
            node = null;
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        drop();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        drop();
    }

    void placedBy(EntityPlayer p) {
        playerId = AEApi.instance()
            .registries()
            .players()
            .getID(p);
        if (node != null) node.setPlayerID(playerId);
    }

    /** A neighbour changed: look at the machines again on the next tick. */
    void neighbourChanged() {
        dirty = true;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("fePlayer", playerId);
        if (node != null) node.saveToNBT("node", tag);
        else if (nodeTag != null && nodeTag.hasKey("node")) tag.setTag("node", nodeTag.getTag("node"));
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        playerId = tag.hasKey("fePlayer") ? tag.getInteger("fePlayer") : -1;
        nodeTag = tag;
    }

    // ---------------------------------------------------------------- the machines

    private List<MTEEchoMachine> machines() {
        List<MTEEchoMachine> out = new ArrayList<>();
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity t = worldObj.getTileEntity(xCoord + d.offsetX, yCoord + d.offsetY, zCoord + d.offsetZ);
            if (t instanceof IGregTechTileEntity gt && gt.getMetaTileEntity() instanceof MTEEchoMachine m) out.add(m);
        }
        return out;
    }

    private void refreshPatterns() {
        Set<EchoPatternDetails> now = new LinkedHashSet<>();
        if (Config.aeEnabled) for (MTEEchoMachine m : machines()) {
            try {
                for (EchoPattern p : m.echoPatterns()) if (p.outputs.length > 0) now.add(new EchoPatternDetails(p));
            } catch (RuntimeException e) {
                FluxEcho.LOG.debug("An echo machine failed to list its patterns", e);
            }
        }
        if (now.equals(offered)) return;
        offered.clear();
        offered.addAll(now);
        IGrid grid = node == null ? null : node.getGrid();
        if (grid != null) grid.postEvent(new MENetworkCraftingPatternChange(this, node));
    }

    private void returnOutputs() {
        if (node == null || !node.isActive()) return;
        IGrid grid = node.getGrid();
        IStorageGrid storage = grid == null ? null : grid.getCache(IStorageGrid.class);
        if (storage == null) return;
        MachineSource src = new MachineSource(this);
        for (MTEEchoMachine m : machines()) {
            for (int i = 0; i < m.outputSlots(); i++) {
                ItemStack s = m.outputSlot(i);
                if (s == null || s.stackSize <= 0) continue;
                IAEItemStack left = storage.getItemInventory()
                    .injectItems(
                        AEApi.instance()
                            .storage()
                            .createItemStack(s),
                        Actionable.MODULATE,
                        src);
                s.stackSize = left == null ? 0 : (int) left.getStackSize();
                m.clearOutputSlot(i);
            }
        }
    }

    // ---------------------------------------------------------------- crafting

    @Override
    public void provideCrafting(ICraftingProviderHelper helper) {
        for (EchoPatternDetails d : offered) helper.addCraftingOption(this, d);
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails details, InventoryCrafting table) {
        if (!(details instanceof EchoPatternDetails d) || !Config.aeEnabled) return false;
        List<ItemStack> in = new ArrayList<>();
        for (int i = 0; i < table.getSizeInventory(); i++) {
            ItemStack s = table.getStackInSlot(i);
            if (s != null) in.add(s.copy());
        }
        ItemStack[] stacks = in.toArray(new ItemStack[0]);
        for (MTEEchoMachine m : machines()) {
            if (!m.echoPatterns()
                .contains(d.pattern)) continue;
            if (m.acceptInputs(stacks)) return true;
        }
        return false;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    /** How many patterns it offers, for a right-click. */
    int offeredCount() {
        return offered.size();
    }

    boolean online() {
        return node != null && node.isActive();
    }

    // ---------------------------------------------------------------- grid block

    @Override
    public double getIdlePowerUsage() {
        return 1;
    }

    @Override
    public EnumSet<GridFlags> getFlags() {
        return EnumSet.of(GridFlags.REQUIRE_CHANNEL);
    }

    @Override
    public boolean isWorldAccessible() {
        return true;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public AEColor getGridColor() {
        return AEColor.Transparent;
    }

    @Override
    public void onGridNotification(GridNotification notification) {}

    @Override
    public void setNetworkStatus(IGrid grid, int channelsInUse) {}

    @Override
    public EnumSet<ForgeDirection> getConnectableSides() {
        return EnumSet.allOf(ForgeDirection.class);
    }

    @Override
    public IGridHost getMachine() {
        return this;
    }

    @Override
    public void gridChanged() {
        dirty = true;
    }

    @Override
    public ItemStack getMachineRepresentation() {
        return AEModule.provider == null ? null : new ItemStack(AEModule.provider);
    }

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return node;
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return AECableType.SMART;
    }

    @Override
    public void securityBreak() {
        worldObj.func_147480_a(xCoord, yCoord, zCoord, true);
    }

    @Override
    public IGridNode getActionableNode() {
        return node;
    }
}
