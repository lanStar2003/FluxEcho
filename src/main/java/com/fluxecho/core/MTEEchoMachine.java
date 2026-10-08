package com.fluxecho.core;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.fluxecho.codex.EchoLedger;

import gregtech.api.interfaces.ITexture;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.util.GTUtility;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * A FluxEcho machine: a GT single-block machine whose "recipe" the machine works out itself in {@link #work()}, the
 * way FluxDepths' collectors do. The sample a machine echoes sits in GT's special slot, which GT never empties while
 * the stack is left at its size; inputs and outputs are GT's usual slots.
 */
public abstract class MTEEchoMachine extends MTEBasicMachine {

    /** GT's results of {@code checkRecipe}. */
    protected static final int NOTHING = 0, BLOCKED = 1, STARTED = 2;

    protected final MachineId kind;
    /** Lang key (under {@code fluxecho.status.}) of what the machine does or waits for; shown in Waila. */
    protected String status = "idle";

    /** The machine registered under its id. */
    protected MTEEchoMachine(MachineId kind, int id, int inputs, int outputs) {
        super(
            id,
            "fluxecho." + kind.key,
            kind.english,
            kind.tier,
            1,
            new String[0],
            inputs,
            outputs,
            EchoTextures.overlays(kind));
        this.kind = kind;
    }

    /** A placed copy, from {@code newMetaEntity}. */
    protected MTEEchoMachine(MachineId kind, String name, String[] description, ITexture[][][] textures, int inputs,
        int outputs) {
        super(name, kind.tier, 1, description, textures, inputs, outputs);
        this.kind = kind;
    }

    /** Whether the module is switched on in the config; when off the machine stays but does nothing. */
    protected abstract boolean moduleEnabled();

    /** One cycle: check the sample and inputs, fill {@code mOutputItems}, consume, and {@link #start}. */
    protected abstract int work();

    /** Values for the {@code %s} in the tooltip ({@code fluxecho.<key>.tip}). */
    protected Object[] tooltipArgs() {
        return new Object[0];
    }

    @Override
    public final int checkRecipe() {
        if (!moduleEnabled()) return idle("disabled");
        return work();
    }

    protected int idle(String why) {
        status = why;
        return NOTHING;
    }

    /** After {@code canOutput} said no (which already counted the blocked output for GT). */
    protected int blocked() {
        status = "output_full";
        return BLOCKED;
    }

    protected int start(int eut, int ticks) {
        mEUt = eut;
        mMaxProgresstime = Math.max(1, ticks);
        status = "working";
        return STARTED;
    }

    /** The programmed circuit's number, 0 without one. */
    protected int circuit() {
        if (!allowSelectCircuit()) return 0;
        ItemStack c = mInventory[getCircuitSlot()];
        return GTUtility.isAnyIntegratedCircuit(c) ? c.getItemDamage() : 0;
    }

    /** The stack in the n-th input slot (from 0). */
    protected ItemStack input(int n) {
        return mInventory[getInputSlot() + n];
    }

    /** The sample in the special slot. */
    protected ItemStack sample() {
        return mInventory[getSpecialSlotIndex()];
    }

    protected World world() {
        return getBaseMetaTileEntity().getWorld();
    }

    /** The team of the player who placed the machine (see {@link Owners}); null when GT recorded no owner. */
    public UUID team() {
        return Owners.team(getBaseMetaTileEntity().getOwnerUuid());
    }

    /** Notes in the team's ledger that this was done once (see {@link EchoLedger}). */
    protected void remember(String category, String key) {
        EchoLedger.get()
            .record(team(), category, key);
    }

    /**
     * What the machine can make right now with a known result, for the Echo ME Provider: usually one pattern for the
     * sample in its special slot. None by default.
     */
    public List<EchoPattern> echoPatterns() {
        return Collections.emptyList();
    }

    /** Puts the stacks into the input slots: all of them, or none when they do not fit. */
    public boolean acceptInputs(ItemStack[] stacks) {
        int first = getInputSlot();
        ItemStack[] slots = new ItemStack[mInputSlotCount];
        for (int i = 0; i < slots.length; i++)
            slots[i] = mInventory[first + i] == null ? null : mInventory[first + i].copy();
        for (ItemStack in : stacks) {
            if (in == null) continue;
            int left = in.stackSize;
            int max = Math.min(in.getMaxStackSize(), getInventoryStackLimit());
            for (int i = 0; i < slots.length && left > 0; i++) {
                ItemStack s = slots[i];
                if (s == null || !s.isItemEqual(in) || !ItemStack.areItemStackTagsEqual(s, in)) continue;
                int move = Math.min(left, max - s.stackSize);
                if (move > 0) {
                    s.stackSize += move;
                    left -= move;
                }
            }
            for (int i = 0; i < slots.length && left > 0; i++) {
                if (slots[i] != null) continue;
                ItemStack s = in.copy();
                s.stackSize = Math.min(left, max);
                slots[i] = s;
                left -= s.stackSize;
            }
            if (left > 0) return false;
        }
        for (int i = 0; i < slots.length; i++) mInventory[first + i] = slots[i];
        getBaseMetaTileEntity().markInventoryBeenModified();
        return true;
    }

    /** Number of output slots. */
    public int outputSlots() {
        return mOutputItems.length;
    }

    /** The stack in the n-th output slot (from 0), live: the caller may shrink it. */
    public ItemStack outputSlot(int n) {
        return mInventory[getOutputSlot() + n];
    }

    /** Clears an emptied output slot. */
    public void clearOutputSlot(int n) {
        ItemStack s = mInventory[getOutputSlot() + n];
        if (s != null && s.stackSize <= 0) mInventory[getOutputSlot() + n] = null;
        getBaseMetaTileEntity().markInventoryBeenModified();
    }

    /** Empty: GT would store the first description it sees in its own lang file, in whatever language. */
    @Override
    public String[] getDescription() {
        return new String[0];
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        tooltip.add(EchoText.machineType(kind.key));
        tooltip.addAll(EchoText.lines(kind.key + ".tip", tooltipArgs()));
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        tag.setString("feStatus", status);
    }

    @Override
    public void getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(stack, tip, accessor, config);
        EchoText.wailaBody(accessor.getNBTData(), tip);
    }
}
