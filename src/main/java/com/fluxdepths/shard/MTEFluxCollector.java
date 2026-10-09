package com.fluxdepths.shard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.fluxdepths.Config;
import com.fluxdepths.holo.HoloNet;
import com.fluxdepths.item.ItemImprint;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.fluid.FluidStackTank;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GUITextureSet;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.modularui.IBindPlayerInventoryUI;
import gregtech.api.interfaces.modularui.IGetTitleColor;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.render.TextureFactory;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * The Flux Shard Collector: one machine for the whole game. The circuit in its core slot sets what it is: no circuit
 * runs it on steam, an LV to LuV circuit at that voltage, speed, imprint count and power draw ({@link ShardTier}). It
 * takes any GT drill head (the better, the rarer it wears) and, from MV on, drilling fluid. It has its own casing, GUI
 * and an optional hologram (off until switched on, with the button or a screwdriver).
 * <p>
 * The ids of the seven old collectors (steam to IV) are this machine too: a placed one keeps its contents (sorted into
 * the new slots) and gets a circuit of its old tier in its core; an old item crafts into this one.
 */
public class MTEFluxCollector extends MTEBasicMachine implements IBindPlayerInventoryUI, IGetTitleColor {

    /** Drilling fluid the tank holds; steam GT stores for the steam mode (in its half-litre units: 32000 L). */
    static final int TANK = 16_000, STEAM = 16_000;
    /** The core circuit sits in GT's special slot. */
    public static final int CORE = 3;

    private final ShardState state = new ShardState();
    /** GT voltage tier an old collector at this id had: its circuit when it is first loaded (0: none). */
    private final int legacyTier;
    private boolean hologram;
    private boolean migrated = true;
    private ItemStack coreSeen;
    private int coreSeenSize;
    private ShardTier tier = ShardTier.STEAM;
    /** On the client: the tier the server last sent with GT's update byte. */
    private ShardTier clientTier = ShardTier.STEAM;

    // what an open GUI shows, synced from the server (see CollectorGui)
    public long guiEU, guiEUCap, guiSteam;
    public String guiVein = "";

    public MTEFluxCollector(int id, String name, String english, int legacyTier) {
        super(id, name, english, 1, 2, new String[0], 1 + ShardTier.MAX_IMPRINTS, 4);
        this.legacyTier = legacyTier;
    }

    private MTEFluxCollector(String name, String[] description, ITexture[][][] textures, int legacyTier) {
        super(name, 1, 2, description, textures, 1 + ShardTier.MAX_IMPRINTS, 4);
        this.legacyTier = legacyTier;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTEFluxCollector(mName, mDescriptionArray, mTextures, legacyTier);
    }

    // ---- what it is

    /** The tier the core circuit gives (the client knows it from GT's update byte). */
    public ShardTier tier() {
        IGregTechTileEntity b = getBaseMetaTileEntity();
        if (b != null && b.isClientSide()) return clientTier;
        ItemStack core = mInventory[CORE];
        if (core != coreSeen || core != null && core.stackSize != coreSeenSize) {
            coreSeen = core;
            coreSeenSize = core == null ? 0 : core.stackSize;
            tier = CoreCircuits.shardTier(core);
        }
        return tier;
    }

    public ShardState state() {
        return state;
    }

    public int headSlot() {
        return getInputSlot();
    }

    public int imprintSlot(int i) {
        return getInputSlot() + 1 + i;
    }

    boolean fits(ItemStack stack) {
        return canOutput(stack);
    }

    FluidStack tank() {
        return getFillableStack();
    }

    FluidStackTank drillingTank() {
        return fluidTank;
    }

    public boolean hologram() {
        return hologram;
    }

    public void setHologram(boolean on) {
        hologram = on;
        markDirty();
    }

    /** The vein of the last ore, as VisualProspecting names it; empty before the first. */
    public String veinName() {
        Veins.Vein v = Veins.get(state.lastVein);
        return v == null ? "" : v.displayName();
    }

    // ---- power: steam with no circuit, else the circuit's voltage

    @Override
    public boolean isSteampowered() {
        return tier().steam();
    }

    @Override
    public long maxSteamStore() {
        return tier().steam() ? STEAM : 0;
    }

    @Override
    public boolean isEnetInput() {
        return !tier().steam();
    }

    @Override
    public long maxEUInput() {
        return tier().voltage();
    }

    @Override
    public long maxEUStore() {
        return Math.max(32, tier().voltage()) * 64L;
    }

    @Override
    public long getMinimumStoredEU() {
        return tier().steam() ? 1000 : tier().voltage() * 16L;
    }

    @Override
    public long maxAmperesIn() {
        return 2;
    }

    @Override
    public int rechargerSlotCount() {
        return 0;
    }

    @Override
    public int dechargerSlotCount() {
        return 0;
    }

    // ---- work

    @Override
    public int getCapacity() {
        return TANK;
    }

    @Override
    public boolean isFluidInputAllowed(FluidStack f) {
        return f != null && f.getFluid() == ItemList.sDrillingFluid;
    }

    @Override
    public int checkRecipe() {
        return ShardWork.check(this);
    }

    @Override
    public void endProcess() {
        state.produced++;
    }

    @Override
    public void onPostTick(IGregTechTileEntity base, long tick) {
        if (base.isServerSide() && !migrated) migrate(base);
        super.onPostTick(base, tick);
        if (!base.isServerSide()) return;
        if (mMaxProgresstime <= 0) {
            // GT does not even look for work without the steam or EU to start
            if (!hasEnoughEnergyToCheckRecipe()) state.status = ShardState.Status.NO_POWER;
            else if (state.status == ShardState.Status.NO_POWER) state.status = ShardState.Status.IDLE;
        } else if (mStuttering) state.status = ShardState.Status.NO_POWER;
        else if (state.status == ShardState.Status.NO_POWER) state.status = ShardState.Status.WORKING;
        if (hologram && Config.hologramRange > 0 && tick % 10 == 0) HoloNet.send(this);
    }

    /**
     * An old collector loaded for the first time as this machine: its items move into the new slots (imprints, the
     * drill head; anything else to the outputs, or out into the world), and it gets a circuit of its old tier.
     */
    private void migrate(IGregTechTileEntity base) {
        migrated = true;
        List<ItemStack> loose = new ArrayList<>();
        for (int i = 0; i < mInventory.length; i++) if (mInventory[i] != null) {
            loose.add(mInventory[i]);
            mInventory[i] = null;
        }
        if (legacyTier > 0) mInventory[CORE] = CoreCircuits.example(legacyTier);
        for (ItemStack s : loose) {
            if (mInventory[CORE] == null && CoreCircuits.tier(s) > 0 && s.stackSize == 1) {
                mInventory[CORE] = s;
                continue;
            }
            if (mInventory[headSlot()] == null && DrillHeads.uses(s) > 0) {
                mInventory[headSlot()] = s;
                continue;
            }
            if (ItemImprint.vein(s) != null && place(s, imprintSlot(0), ShardTier.MAX_IMPRINTS)) continue;
            if (place(s, getOutputSlot(), mOutputItems.length)) continue;
            World w = base.getWorld();
            w.spawnEntityInWorld(
                new EntityItem(w, base.getXCoord() + 0.5, base.getYCoord() + 1.2, base.getZCoord() + 0.5, s));
        }
        markDirty();
    }

    private boolean place(ItemStack s, int first, int count) {
        for (int i = first; i < first + count; i++) if (mInventory[i] == null) {
            mInventory[i] = s;
            return true;
        }
        return false;
    }

    /** Hoppers and conveyors put drill heads and imprints in; the core only by hand. */
    @Override
    public boolean allowPutStack(IGregTechTileEntity base, int index, ForgeDirection side, ItemStack stack) {
        if (side == mMainFacing || stack == null) return false;
        if (index == headSlot()) return DrillHeads.uses(stack) > 0;
        if (index >= imprintSlot(0) && index < imprintSlot(ShardTier.MAX_IMPRINTS))
            return mInventory[index] == null && ItemImprint.vein(stack) != null;
        return false;
    }

    /** A screwdriver switches the hologram. */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer player, float x, float y, float z,
        ItemStack tool) {
        setHologram(!hologram);
        player.addChatMessage(new ChatComponentTranslation(hologram ? "fluxdepths.holo.on" : "fluxdepths.holo.off"));
    }

    // ---- looks

    @Override
    public byte getUpdateData() {
        return (byte) (mMainFacing.ordinal() & 7 | (tier().ordinal() & 7) << 3 | (hologram ? 0x40 : 0));
    }

    @Override
    public void onValueUpdate(byte value) {
        mMainFacing = ForgeDirection.getOrientation(value & 7);
        ShardTier[] all = ShardTier.values();
        clientTier = all[Math.min(all.length - 1, value >> 3 & 7)];
        hologram = (value & 0x40) != 0;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity base, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstone) {
        ForgeDirection front = mMainFacing == ForgeDirection.UNKNOWN ? facing : mMainFacing;
        if (side == front)
            return new ITexture[] { ShardTextures.casing(ShardTextures.CASING_SIDE), ShardTextures.core(active) };
        ITexture casing = ShardTextures.casing(
            side == ForgeDirection.UP ? ShardTextures.CASING_TOP
                : side == ForgeDirection.DOWN ? ShardTextures.CASING_BOTTOM : ShardTextures.CASING_SIDE);
        if (side == facing) return new ITexture[] { casing, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT) };
        if (side == ForgeDirection.UP) return new ITexture[] { casing, ShardTextures.top(active) };
        if (side == ForgeDirection.DOWN) return new ITexture[] { casing };
        return new ITexture[] { casing, ShardTextures.strip(tier()) };
    }

    // ---- GUI

    @Override
    public GUITextureSet getGUITextureSet() {
        return CollectorGui.TEXTURES;
    }

    @Override
    public int getGUIWidth() {
        return CollectorGui.WIDTH;
    }

    @Override
    public int getGUIHeight() {
        return CollectorGui.HEIGHT;
    }

    @Override
    public int getGUIColorization() {
        return 0xFFFFFF;
    }

    @Override
    public int getTitleColor() {
        return CollectorGui.TITLE;
    }

    @Override
    public void bindPlayerInventoryUI(ModularWindow.Builder builder, UIBuildContext context) {
        builder.bindPlayerInventory(context.getPlayer(), new Pos2d(7, CollectorGui.INVENTORY_Y), CollectorGui.SLOT);
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext context) {
        CollectorGui.build(this, builder);
    }

    @Override
    public void addGregTechLogo(ModularWindow.Builder builder) {
        builder.widget(
            new DrawableWidget().setDrawable(CollectorGui.LOGO)
                .setSize(17, 17)
                .setPos(150, 64));
    }

    // ---- saving, tooltips, Waila

    @Override
    public void saveNBTData(NBTTagCompound t) {
        super.saveNBTData(t);
        state.save(t);
        t.setBoolean("fdHolo", hologram);
        t.setBoolean("fdV3", true);
    }

    @Override
    public void loadNBTData(NBTTagCompound t) {
        super.loadNBTData(t);
        state.load(t);
        hologram = t.getBoolean("fdHolo");
        migrated = t.getBoolean("fdV3");
    }

    /** Empty: GT would store the first description it sees in its own lang file, in whatever language. */
    @Override
    public String[] getDescription() {
        return new String[0];
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        Collections.addAll(tooltip, ShardText.description());
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        ShardText.wailaData(this, tag);
    }

    @Override
    public void getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(stack, tip, accessor, config);
        ShardText.wailaBody(accessor.getNBTData(), tip);
    }

    /** What the hologram shows, sent to the players nearby. */
    public NBTTagCompound holoData() {
        IGregTechTileEntity b = getBaseMetaTileEntity();
        NBTTagCompound t = new NBTTagCompound();
        t.setByte("t", (byte) tier().ordinal());
        t.setByte("s", (byte) state.status.ordinal());
        t.setInteger("p", Math.max(0, mProgresstime));
        t.setInteger("m", mMaxProgresstime);
        t.setLong("e", b.getStoredEU());
        t.setLong("c", b.getEUCapacity());
        t.setLong("st", b.getStoredSteam());
        t.setLong("sc", b.getSteamCapacity());
        t.setString("v", veinName());
        t.setInteger("h", state.headUses);
        t.setInteger("i", state.imprints);
        t.setLong("o", state.produced);
        FluidStack f = getFillableStack();
        t.setInteger("f", f == null ? 0 : f.amount);
        return t;
    }
}
