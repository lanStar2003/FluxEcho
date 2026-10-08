package com.fluxecho.core;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.Config;
import com.fluxecho.client.MachineFx;
import com.fluxecho.codex.EchoLedger;
import com.fluxecho.codex.EchoNet;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GUITextureSet;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.modularui.IBindPlayerInventoryUI;
import gregtech.api.interfaces.modularui.IGetTitleColor;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * A FluxEcho machine: a GT single-block machine whose "recipe" the machine works out itself in {@link #work()}, the
 * way FluxDepths' collectors do. The sample a machine echoes sits in GT's special slot, which GT never empties while
 * the stack is left at its size; inputs and outputs are GT's usual slots.
 * <p>
 * It looks like the flux world rather than GT: the flux casing with its own animated front and strips in its colour
 * ({@link EchoTextures}), its own GUI with an animation of its work ({@link FluxMachineGui}), an optional hologram
 * (off by default; the GUI or a screwdriver on its side), and an effect above it while it works.
 */
public abstract class MTEEchoMachine extends MTEBasicMachine implements IBindPlayerInventoryUI, IGetTitleColor {

    private static final ITexture PIPE_OUT = TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT);

    /** GT's results of {@code checkRecipe}. */
    protected static final int NOTHING = 0, BLOCKED = 1, STARTED = 2;

    protected final MachineId kind;
    /** Lang key (under {@code fluxecho.status.}) of what the machine does or waits for; shown in Waila. */
    protected String status = "idle";
    private boolean hologram;

    // what an open GUI shows that only the server knows
    String guiStatus = "idle", guiInfo = "";
    long guiEU, guiEUCap;
    int guiEut;

    /** The machine registered under its id. */
    protected MTEEchoMachine(MachineId kind, int id, int inputs, int outputs) {
        super(id, "fluxecho." + kind.key, kind.english, kind.tier, 1, new String[0], inputs, outputs);
        this.kind = kind;
        EchoTextures.load(kind);
    }

    /** A placed copy, from {@code newMetaEntity}. */
    protected MTEEchoMachine(MachineId kind, String name, String[] description, ITexture[][][] textures, int inputs,
        int outputs) {
        super(name, kind.tier, 1, description, textures, inputs, outputs);
        this.kind = kind;
    }

    public MachineId kind() {
        return kind;
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

    // ---- what it shows

    /**
     * What it does or waits for, as a lang key under {@code fluxecho.status.}: its own status, or {@code no_power}
     * while it lacks power.
     */
    public String shownStatus() {
        if (mMaxProgresstime > 0 ? mStuttering : !"disabled".equals(status) && !hasEnoughEnergyToCheckRecipe())
            return "no_power";
        return status;
    }

    /**
     * One line about the machine's own state for its GUI and hologram, as a lang key under {@code fluxecho.} and its
     * arguments ({@link #encode}); empty for none.
     */
    protected String info() {
        return "";
    }

    /** A lang key and its arguments in one string, for {@link #info} (translated on the client). */
    protected static String encode(String key, Object... args) {
        StringBuilder b = new StringBuilder(key);
        for (Object a : args) b.append('\u0001')
            .append(a);
        return b.toString();
    }

    public boolean hologram() {
        return hologram;
    }

    public void setHologram(boolean on) {
        hologram = on;
        markDirty();
    }

    /** The GT voltage tier it works at. */
    public int voltageTier() {
        return mTier;
    }

    public long voltage() {
        return GTValues.V[mTier];
    }

    @Override
    public void onPostTick(IGregTechTileEntity base, long tick) {
        super.onPostTick(base, tick);
        if (base.isClientSide()) {
            if (base.isActive())
                MachineFx.seen(kind, base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
            return;
        }
        if (hologram && Config.machineHologramRange > 0 && tick % 10 == 0)
            EchoNet.holo(base, EchoNet.HOLO_MACHINE, holoData(), Config.machineHologramRange);
    }

    /** What the hologram shows. */
    public NBTTagCompound holoData() {
        IGregTechTileEntity b = getBaseMetaTileEntity();
        NBTTagCompound t = new NBTTagCompound();
        t.setString("s", shownStatus());
        t.setInteger("p", Math.max(0, mProgresstime));
        t.setInteger("m", mMaxProgresstime);
        t.setInteger("u", mMaxProgresstime > 0 ? mEUt : 0);
        t.setLong("e", b.getStoredEU());
        t.setLong("ec", b.getEUCapacity());
        t.setString("i", info());
        ItemStack sample = kind.sample ? sample() : null;
        if (sample != null) t.setTag("sm", sample.writeToNBT(new NBTTagCompound()));
        for (int i = 0; i < mOutputItems.length; i++) {
            ItemStack o = mInventory[getOutputSlot() + i];
            if (o != null) {
                t.setTag("o", o.writeToNBT(new NBTTagCompound()));
                break;
            }
        }
        return t;
    }

    /** A screwdriver on a side that is neither its front nor its output switches the hologram. */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer player, float x, float y, float z,
        ItemStack tool) {
        if (side == getBaseMetaTileEntity().getFrontFacing() || side == mMainFacing) {
            super.onScrewdriverRightClick(side, player, x, y, z, tool);
            return;
        }
        setHologram(!hologram);
        player.addChatMessage(
            new ChatComponentTranslation(hologram ? "fluxecho.machine.holo.on" : "fluxecho.machine.holo.off"));
    }

    @Override
    public byte getUpdateData() {
        return (byte) (mMainFacing.ordinal() & 7 | (hologram ? 0x40 : 0));
    }

    @Override
    public void onValueUpdate(byte value) {
        mMainFacing = ForgeDirection.getOrientation(value & 7);
        hologram = (value & 0x40) != 0;
    }

    /** The flux casing, its own front, the echo ring on top, strips in its colour, GT's pipe on the output side. */
    @Override
    public ITexture[] getTexture(IGregTechTileEntity base, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstone) {
        ForgeDirection front = mMainFacing == ForgeDirection.UNKNOWN ? facing : mMainFacing;
        ITexture casing = EchoTextures.casing(
            side == ForgeDirection.UP ? EchoTextures.CASING_TOP
                : side == ForgeDirection.DOWN ? EchoTextures.CASING_BOTTOM : EchoTextures.CASING_SIDE);
        if (side == front) return new ITexture[] { casing, EchoTextures.front(kind, active) };
        if (side == facing) return new ITexture[] { casing, PIPE_OUT };
        if (side == ForgeDirection.UP) return new ITexture[] { casing, EchoTextures.top(active) };
        if (side == ForgeDirection.DOWN) return new ITexture[] { casing };
        return new ITexture[] { casing, EchoTextures.strip(kind) };
    }

    // ---- GUI

    @Override
    public GUITextureSet getGUITextureSet() {
        return FluxMachineGui.TEXTURES;
    }

    @Override
    public int getGUIWidth() {
        return FluxMachineGui.WIDTH;
    }

    @Override
    public int getGUIHeight() {
        return FluxMachineGui.HEIGHT;
    }

    @Override
    public int getGUIColorization() {
        return 0xFFFFFF;
    }

    @Override
    public int getTitleColor() {
        return kind.accent;
    }

    @Override
    public void bindPlayerInventoryUI(ModularWindow.Builder builder, UIBuildContext context) {
        builder.bindPlayerInventory(context.getPlayer(), new Pos2d(7, FluxMachineGui.INVENTORY_Y), FluxMachineGui.SLOT);
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext context) {
        FluxMachineGui.build(this, builder);
    }

    @Override
    public void addGregTechLogo(ModularWindow.Builder builder) {
        builder.widget(
            new DrawableWidget().setDrawable(FluxMachineGui.LOGO)
                .setSize(17, 17)
                .setPos(150, 64));
    }

    /** Where GT puts the programmed circuit's slot (it subtracts one from each). */
    @Override
    public int getCircuitSlotX() {
        return 150;
    }

    @Override
    public int getCircuitSlotY() {
        return 64;
    }

    /** GT's battery slot, for the GUI. */
    SlotWidget battery(int x, int y) {
        return createChargerSlot(x, y);
    }

    public int inputCount() {
        return mInputSlotCount;
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
        tag.setString("feStatus", shownStatus());
    }

    @Override
    public void saveNBTData(NBTTagCompound t) {
        super.saveNBTData(t);
        t.setBoolean("feHolo", hologram);
    }

    @Override
    public void loadNBTData(NBTTagCompound t) {
        super.loadNBTData(t);
        hologram = t.getBoolean("feHolo");
    }

    @Override
    public void getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(stack, tip, accessor, config);
        EchoText.wailaBody(accessor.getNBTData(), tip);
    }
}
