package com.fluxecho.core;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

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

    /** Empty: GT would store the first description it sees in its own lang file, in whatever language. */
    @Override
    public String[] getDescription() {
        return new String[0];
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
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
