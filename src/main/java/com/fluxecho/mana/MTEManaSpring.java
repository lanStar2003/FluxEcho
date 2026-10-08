package com.fluxecho.mana;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.Config;
import com.fluxecho.codex.EchoNet;
import com.fluxecho.core.CoreCircuits;
import com.fluxecho.logic.ManaSpring;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.gui.modularui.GUITextureSet;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.modularui.IBindPlayerInventoryUI;
import gregtech.api.interfaces.modularui.IGetTitleColor;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import vazkii.botania.api.mana.IManaItem;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.common.block.tile.mana.TilePool;

/**
 * The Mana Echo Spring. Mana your flowers made flowed through the flux layer and left its echo there; the spring draws
 * that echo back up with EU, a mystical petal tuning each stretch of it, and lets it well up straight into the mana
 * pools around it (nearest first, each one full before the next) and into a mana tablet or ring in its charge slot. No
 * spreaders, no flowers to feed.
 * <p>
 * One machine for every stage, like FluxDepths' collector: the circuit in its core (LV to LuV) sets its voltage, how
 * much mana it draws (x4 per tier), how much a petal is worth (x4 too, so petals go at the same pace) and how far it
 * reaches ({@link ManaSpring}). It has its own casing, GUI and an optional hologram. A spring placed before 0.6.0 gets
 * an MV circuit, the tier it had.
 */
public class MTEManaSpring extends MTEBasicMachine implements IBindPlayerInventoryUI, IGetTitleColor {

    static final int CYCLE = 20, BUFFERED_CYCLES = 3, RESCAN_TICKS = 100, SHOWN_POOLS = 4;
    /** The core circuit sits in GT's special slot. */
    public static final int CORE = 3;

    public enum State {

        IDLE,
        WORKING,
        NO_CORE,
        NO_PETAL,
        NO_POOL,
        POOL_FULL,
        NO_POWER,
        DISABLED;

        public static State of(int ordinal) {
            State[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }
    }

    int buffer;
    private int pending;
    long credit, delivered;
    State state = State.IDLE;
    private boolean hologram, migrated = true;

    private final List<ChunkCoordinates> poolsAt = new ArrayList<>();
    private long nextScan;
    private int scannedTier = -1;

    private ItemStack coreSeen;
    private int coreSeenSize, tier;
    /** On the client: the tier the server last sent with GT's update byte (or an open GUI). */
    int clientTier;

    // what the GUI, Waila and the hologram show: the pools in reach, refreshed every second on the server
    int poolCount;
    long poolMana, poolCap;
    /** Fill of the nearest pools in tenths of a percent; -1 where there is none. */
    final int[] poolFill = new int[SHOWN_POOLS];
    // synced to an open GUI, from the server's config
    long guiEU, guiEUCap, guiEUt, guiPetal;
    int guiMana, guiRange, guiHeight, guiBufferCap;

    public MTEManaSpring(int id) {
        super(id, "fluxecho.mana_echo", "Mana Echo Spring", 2, 2, new String[0], 2, 0);
        Arrays.fill(poolFill, -1);
    }

    private MTEManaSpring(String name, String[] description, ITexture[][][] textures) {
        super(name, 2, 2, description, textures, 2, 0);
        Arrays.fill(poolFill, -1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTEManaSpring(mName, mDescriptionArray, mTextures);
    }

    // ---- what it is

    /** GT voltage tier of the core circuit, 0 without one (the client knows it from GT's update byte). */
    public int tier() {
        IGregTechTileEntity b = getBaseMetaTileEntity();
        if (b != null && b.isClientSide()) return clientTier;
        ItemStack core = mInventory[CORE];
        if (core != coreSeen || core != null && core.stackSize != coreSeenSize) {
            coreSeen = core;
            coreSeenSize = core == null ? 0 : core.stackSize;
            tier = CoreCircuits.tier(core);
        }
        return tier;
    }

    public int manaPerTick() {
        return ManaSpring.manaPerTick(Config.manaPerTick, tier());
    }

    public long euPerTick() {
        return ManaSpring.euPerTick(Config.manaPerTick, Config.euPerMana, tier());
    }

    public long petalWorth() {
        return ManaSpring.manaPerPetal(Config.manaPerPetal, tier());
    }

    public int range() {
        return ManaSpring.range(Config.manaRange, tier());
    }

    public int height() {
        return ManaSpring.height(Config.manaHeight, tier());
    }

    public int petalSlot() {
        return getInputSlot();
    }

    public int chargeSlot() {
        return getInputSlot() + 1;
    }

    public State state() {
        return state;
    }

    public int buffer() {
        return buffer;
    }

    public int bufferCap() {
        return Math.max(1, manaPerTick() * CYCLE * BUFFERED_CYCLES);
    }

    public long credit() {
        return credit;
    }

    public long delivered() {
        return delivered;
    }

    public boolean hologram() {
        return hologram;
    }

    public void setHologram(boolean on) {
        hologram = on;
        markDirty();
    }

    static boolean isPetal(ItemStack s) {
        return s != null && s.getItem() != null && s.getItem() == GameRegistry.findItem("Botania", "petal");
    }

    static boolean chargeable(ItemStack s) {
        return s != null && s.stackSize == 1 && s.getItem() instanceof IManaItem;
    }

    /** Mana the item in the charge slot still takes, 0 for none. */
    private int chargeRoom() {
        ItemStack s = mInventory[chargeSlot()];
        if (!chargeable(s)) return 0;
        IManaItem m = (IManaItem) s.getItem();
        if (!m.canReceiveManaFromPool(s, (TileEntity) getBaseMetaTileEntity())) return 0;
        return Math.max(0, m.getMaxMana(s) - m.getMana(s));
    }

    /** The charge item's fill in tenths of a percent, -1 for none. */
    int chargeFill() {
        ItemStack s = mInventory[chargeSlot()];
        if (!chargeable(s)) return -1;
        IManaItem m = (IManaItem) s.getItem();
        int max = m.getMaxMana(s);
        return max <= 0 ? 1000 : (int) Math.min(1000, (long) m.getMana(s) * 1000 / max);
    }

    // ---- power: the core's voltage

    @Override
    public boolean isEnetInput() {
        return tier() > 0;
    }

    @Override
    public long maxEUInput() {
        return ManaSpring.voltage(tier());
    }

    @Override
    public long maxEUStore() {
        return Math.max(32, ManaSpring.voltage(tier())) * 64L;
    }

    @Override
    public long getMinimumStoredEU() {
        return ManaSpring.voltage(tier()) * 16L;
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

    private int idle(State why) {
        state = why;
        return DID_NOT_FIND_RECIPE;
    }

    @Override
    public int checkRecipe() {
        if (!Config.manaEnabled) return idle(State.DISABLED);
        if (tier() <= 0) return idle(State.NO_CORE);
        if (pools().isEmpty() && chargeRoom() <= 0) return idle(State.NO_POOL);
        int amount = manaPerTick() * CYCLE;
        if (buffer >= amount * BUFFERED_CYCLES) return idle(State.POOL_FULL);
        long worth = petalWorth();
        int petals = ManaSpring.petalsNeeded(credit, amount, worth);
        if (petals > 0) {
            ItemStack in = mInventory[petalSlot()];
            if (!isPetal(in) || in.stackSize < petals) return idle(State.NO_PETAL);
            in.stackSize -= petals;
            credit += petals * worth;
        }
        credit -= amount;
        pending = amount;
        mEUt = (int) Math.min(Integer.MAX_VALUE, euPerTick());
        mMaxProgresstime = CYCLE;
        state = State.WORKING;
        return FOUND_AND_SUCCESSFULLY_USED_RECIPE;
    }

    @Override
    public void endProcess() {
        buffer += pending;
        pending = 0;
        flush();
    }

    @Override
    public void onPostTick(IGregTechTileEntity base, long tick) {
        if (base.isServerSide() && !migrated) migrate(base);
        super.onPostTick(base, tick);
        if (!base.isServerSide()) return;
        if (mMaxProgresstime <= 0) {
            if (tier() <= 0) state = State.NO_CORE;
            else if (!hasEnoughEnergyToCheckRecipe()) state = State.NO_POWER;
            else if (state == State.NO_POWER || state == State.NO_CORE) {
                state = State.IDLE;
                // GT looks for work again only every 30 s unless the inventory changed
                base.markInventoryBeenModified();
            }
        } else if (mStuttering) state = State.NO_POWER;
        else if (state == State.NO_POWER) state = State.WORKING;
        if (tick % 20 == 0) {
            flush();
            survey();
            if (state == State.POOL_FULL || state == State.NO_POOL) base.markInventoryBeenModified();
        }
        if (hologram && Config.manaHologramRange > 0 && tick % 10 == 0)
            EchoNet.holo(base, EchoNet.HOLO_MANA, holoData(), Config.manaHologramRange);
    }

    /** Hands the buffer on: the charge item first, then the pools nearest first, as much as each takes. */
    private void flush() {
        if (buffer <= 0 || !Config.manaEnabled) return;
        int room = chargeRoom();
        if (room > 0) {
            ItemStack s = mInventory[chargeSlot()];
            int send = Math.min(buffer, room);
            ((IManaItem) s.getItem()).addMana(s, send);
            buffer -= send;
            delivered += send;
        }
        for (IManaPool p : pools()) {
            if (buffer <= 0) return;
            int send = Math.min(buffer, room(p));
            if (send <= 0) continue;
            p.recieveMana(send);
            buffer -= send;
            delivered += send;
        }
    }

    /** Mana a pool still takes. */
    private int room(IManaPool p) {
        if (p.isFull()) return 0;
        if (p instanceof TilePool tp) return Math.max(0, tp.manaCap - tp.getCurrentMana());
        return manaPerTick() * CYCLE; // unknown capacity: a little at a time
    }

    /** Counts the pools in reach and how full they are, for the GUI, Waila and the hologram. */
    private void survey() {
        List<IManaPool> pools = pools();
        poolCount = pools.size();
        poolMana = 0;
        poolCap = 0;
        Arrays.fill(poolFill, -1);
        for (int i = 0; i < pools.size(); i++) {
            IManaPool p = pools.get(i);
            int mana = Math.max(0, p.getCurrentMana());
            int cap = p instanceof TilePool tp ? tp.manaCap : Math.max(mana, 1);
            poolMana += mana;
            poolCap += cap;
            if (i < SHOWN_POOLS) poolFill[i] = cap <= 0 ? 0 : (int) Math.min(1000, (long) mana * 1000 / cap);
        }
    }

    /** The pools in reach, nearest first; the search runs again every few seconds and whenever the tier changes. */
    private List<IManaPool> pools() {
        World w = getBaseMetaTileEntity().getWorld();
        int t = tier();
        if (w.getTotalWorldTime() >= nextScan || t != scannedTier) {
            nextScan = w.getTotalWorldTime() + RESCAN_TICKS;
            scannedTier = t;
            scan(w);
        }
        List<IManaPool> out = new ArrayList<>(poolsAt.size());
        for (ChunkCoordinates c : poolsAt)
            if (w.getTileEntity(c.posX, c.posY, c.posZ) instanceof IManaPool p) out.add(p);
        return out;
    }

    private void scan(World w) {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        int x0 = te.getXCoord(), y0 = te.getYCoord(), z0 = te.getZCoord();
        int r = range(), h = height();
        poolsAt.clear();
        for (int dy = -h; dy <= h; dy++) {
            int y = y0 + dy;
            if (y < 0 || y >= w.getHeight()) continue;
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) if (w.getTileEntity(x0 + dx, y, z0 + dz) instanceof IManaPool)
                    poolsAt.add(new ChunkCoordinates(x0 + dx, y, z0 + dz));
        }
        poolsAt.sort((a, b) -> Integer.compare(dist(a, x0, y0, z0), dist(b, x0, y0, z0)));
    }

    private static int dist(ChunkCoordinates c, int x, int y, int z) {
        int dx = c.posX - x, dy = c.posY - y, dz = c.posZ - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * A Mana Echo placed before 0.6.0 loaded for the first time: it was an MV machine, so it gets an MV circuit. Its
     * petals stay; whatever else sat in its real slots (its input, its output, now the charge slot) comes out into the
     * world. GT's own slots (programmed circuit, displays) are only cleared: their stacks are not real items.
     */
    private void migrate(IGregTechTileEntity base) {
        migrated = true;
        List<ItemStack> loose = new ArrayList<>();
        for (int i = 0; i < mInventory.length; i++) {
            ItemStack s = mInventory[i];
            if (s == null) continue;
            boolean real = i == petalSlot() || i == chargeSlot() || i == CORE;
            boolean keep = i == petalSlot() && isPetal(s) || i == chargeSlot() && chargeable(s)
                || i == CORE && CoreCircuits.tier(s) > 0;
            if (keep) continue;
            if (real) loose.add(s);
            mInventory[i] = null;
        }
        if (mInventory[CORE] == null) mInventory[CORE] = CoreCircuits.example(2);
        World w = base.getWorld();
        for (ItemStack s : loose) w.spawnEntityInWorld(
            new EntityItem(w, base.getXCoord() + 0.5, base.getYCoord() + 1.2, base.getZCoord() + 0.5, s));
        markDirty();
    }

    /** Hoppers bring petals and empty tablets; a full one may be taken out of the charge slot. */
    @Override
    public boolean allowPutStack(IGregTechTileEntity base, int index, ForgeDirection side, ItemStack stack) {
        if (side == mMainFacing || stack == null) return false;
        if (index == petalSlot()) return isPetal(stack);
        if (index == chargeSlot()) return mInventory[index] == null && chargeable(stack);
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity base, int index, ForgeDirection side, ItemStack stack) {
        return side != mMainFacing && index == chargeSlot() && chargeable(stack) && chargeRoom() <= 0;
    }

    /** A screwdriver switches the hologram. */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer player, float x, float y, float z,
        ItemStack tool) {
        setHologram(!hologram);
        player.addChatMessage(
            new ChatComponentTranslation(hologram ? "fluxecho.spring.holo.on" : "fluxecho.spring.holo.off"));
    }

    // ---- looks

    @Override
    public byte getUpdateData() {
        return (byte) (mMainFacing.ordinal() & 7 | (tier() & 7) << 3 | (hologram ? 0x40 : 0));
    }

    @Override
    public void onValueUpdate(byte value) {
        mMainFacing = ForgeDirection.getOrientation(value & 7);
        clientTier = Math.min(ManaSpring.MAX_TIER, value >> 3 & 7);
        hologram = (value & 0x40) != 0;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity base, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstone) {
        ForgeDirection front = mMainFacing == ForgeDirection.UNKNOWN ? facing : mMainFacing;
        if (side == front)
            return new ITexture[] { ManaTextures.casing(ManaTextures.CASING_SIDE), ManaTextures.spring(active) };
        if (side == ForgeDirection.UP)
            return new ITexture[] { ManaTextures.casing(ManaTextures.CASING_TOP), ManaTextures.grate(active) };
        if (side == ForgeDirection.DOWN) return new ITexture[] { ManaTextures.casing(ManaTextures.CASING_BOTTOM) };
        return new ITexture[] { ManaTextures.casing(ManaTextures.CASING_SIDE), ManaTextures.strip(tier()) };
    }

    // ---- GUI

    @Override
    public GUITextureSet getGUITextureSet() {
        return ManaGui.TEXTURES;
    }

    @Override
    public int getGUIWidth() {
        return ManaGui.WIDTH;
    }

    @Override
    public int getGUIHeight() {
        return ManaGui.HEIGHT;
    }

    @Override
    public int getGUIColorization() {
        return 0xFFFFFF;
    }

    @Override
    public int getTitleColor() {
        return ManaGui.TITLE;
    }

    @Override
    public void bindPlayerInventoryUI(ModularWindow.Builder builder, UIBuildContext context) {
        builder.bindPlayerInventory(context.getPlayer(), new Pos2d(7, ManaGui.INVENTORY_Y), ManaGui.SLOT);
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext context) {
        ManaGui.build(this, builder);
    }

    @Override
    public void addGregTechLogo(ModularWindow.Builder builder) {
        builder.widget(
            new DrawableWidget().setDrawable(ManaGui.LOGO)
                .setSize(17, 17)
                .setPos(150, 64));
    }

    // ---- saving, tooltips, Waila, hologram

    @Override
    public void saveNBTData(NBTTagCompound t) {
        super.saveNBTData(t);
        t.setInteger("feBuffer", buffer);
        t.setLong("feCredit", credit);
        t.setInteger("fePending", pending);
        t.setLong("feDelivered", delivered);
        t.setBoolean("feHolo", hologram);
        t.setBoolean("feSpring", true);
    }

    @Override
    public void loadNBTData(NBTTagCompound t) {
        super.loadNBTData(t);
        buffer = Math.max(0, t.getInteger("feBuffer"));
        credit = Math.max(0, t.getLong("feCredit"));
        pending = Math.max(0, t.getInteger("fePending"));
        delivered = Math.max(0, t.getLong("feDelivered"));
        hologram = t.getBoolean("feHolo");
        // only a Mana Echo saved in a world before 0.6.0 (it always wrote feBuffer) is migrated; a spring placed from
        // an item that carries some NBT (covers, colour) is not, or it would get a free circuit
        migrated = t.getBoolean("feSpring") || !t.hasKey("feBuffer");
    }

    /** Empty: GT would store the first description it sees in its own lang file, in whatever language. */
    @Override
    public String[] getDescription() {
        return new String[0];
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        Collections.addAll(tooltip, ManaText.description());
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        tag.setTag("feSpring", holoData());
    }

    @Override
    public void getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(stack, tip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        if (tag.hasKey("feSpring")) ManaText.waila(tag.getCompoundTag("feSpring"), tip);
    }

    /** What the hologram and Waila show. */
    public NBTTagCompound holoData() {
        IGregTechTileEntity b = getBaseMetaTileEntity();
        NBTTagCompound t = new NBTTagCompound();
        t.setByte("t", (byte) tier());
        t.setByte("s", (byte) state.ordinal());
        t.setInteger("p", Math.max(0, mProgresstime));
        t.setInteger("m", mMaxProgresstime);
        t.setInteger("b", buffer);
        t.setInteger("bc", bufferCap());
        t.setLong("c", credit);
        t.setLong("d", delivered);
        t.setLong("e", b.getStoredEU());
        t.setLong("ec", b.getEUCapacity());
        t.setInteger("n", poolCount);
        t.setLong("pm", poolMana);
        t.setLong("pc", poolCap);
        t.setIntArray("pf", poolFill.clone());
        t.setInteger("cf", chargeFill());
        t.setInteger("mt", manaPerTick());
        t.setLong("et", euPerTick());
        t.setLong("pw", petalWorth());
        t.setInteger("r", range());
        t.setInteger("hh", height());
        ItemStack petals = mInventory[petalSlot()];
        t.setInteger("pe", isPetal(petals) ? petals.stackSize : 0);
        t.setBoolean("h", hologram);
        return t;
    }
}
