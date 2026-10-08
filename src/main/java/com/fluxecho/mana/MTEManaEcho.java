package com.fluxecho.mana;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import com.fluxecho.Config;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.MTEEchoMachine;
import com.fluxecho.core.MachineId;
import com.fluxecho.logic.BloodRates;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.common.block.tile.mana.TilePool;

/**
 * Mana Echo (MV). EU and a few mystical petals become mana, sent into the mana pools around it: the nearest one
 * first, and when it is full the next nearest, until every pool in range is full. No generating flowers to feed: the
 * machine's recipe takes a mana pool and a spreader, the proof that mana was made by hand once. Mana made waits in a
 * small buffer until a pool has room, so the machine stops once all of them are full.
 */
public class MTEManaEcho extends MTEEchoMachine {

    private static final int CYCLE = 20, BUFFERED_CYCLES = 3, RESCAN_TICKS = 100;

    private int buffer, credit, pending;
    /** Where the pools in range stand, nearest first; refreshed every few seconds. */
    private final List<ChunkCoordinates> poolsAt = new ArrayList<>();
    private long nextScan;

    public MTEManaEcho(int id) {
        super(MachineId.MANA_ECHO, id, 1, 1);
    }

    private MTEManaEcho(String name, String[] description, ITexture[][][] textures) {
        super(MachineId.MANA_ECHO, name, description, textures, 1, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTEManaEcho(mName, mDescriptionArray, mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return ManaModule.map();
    }

    @Override
    protected boolean moduleEnabled() {
        return Config.manaEnabled;
    }

    @Override
    protected Object[] tooltipArgs() {
        return new Object[] { Config.manaPerTick, Config.euPerMana, Config.manaPerPetal, Config.manaRange,
            Config.manaHeight };
    }

    static boolean isPetal(ItemStack s) {
        return s != null && s.getItem() != null && s.getItem() == GameRegistry.findItem("Botania", "petal");
    }

    @Override
    protected int work() {
        int amount = Config.manaPerTick * CYCLE;
        if (pools().isEmpty()) return idle("no_pool");
        if (buffer >= amount * BUFFERED_CYCLES) return idle("pool_full");
        int petals = BloodRates.meatNeeded(credit, amount, Config.manaPerPetal);
        if (petals > 0) {
            ItemStack in = input(0);
            if (!isPetal(in) || in.stackSize < petals) return idle("no_petal");
            in.stackSize -= petals;
            credit += petals * Config.manaPerPetal;
        }
        credit -= amount;
        pending = amount;
        return start(Config.manaPerTick * Config.euPerMana, CYCLE);
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
        // GT only rechecks an idle machine every 30 s; the pool may have room again sooner
        if ("pool_full".equals(status) || "no_pool".equals(status)) te.markInventoryBeenModified();
    }

    /** Mana a pool still takes. */
    private static int room(IManaPool p) {
        if (p.isFull()) return 0;
        if (p instanceof TilePool tp) return Math.max(0, tp.manaCap - tp.getCurrentMana());
        return Config.manaPerTick * CYCLE; // unknown capacity: a little at a time
    }

    /** Hands the buffer to the pools, nearest first, as much as each one takes. */
    private void flush() {
        if (buffer <= 0 || !Config.manaEnabled) return;
        for (IManaPool p : pools()) {
            int send = Math.min(buffer, room(p));
            if (send <= 0) continue;
            p.recieveMana(send);
            buffer -= send;
            if (buffer <= 0) return;
        }
    }

    /** The pools in range, nearest first. The search runs again every few seconds, so new pools join in. */
    private List<IManaPool> pools() {
        World w = world();
        if (w.getTotalWorldTime() >= nextScan) {
            nextScan = w.getTotalWorldTime() + RESCAN_TICKS;
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
        int r = Config.manaRange, h = Config.manaHeight;
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
        tag.setInteger("feBuffer", buffer);
    }

    @Override
    public void getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(stack, tip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        if (tag.hasKey("feBuffer"))
            tip.add(EchoText.t("mana_echo.waila", Config.manaPerTick, tag.getInteger("feBuffer")));
    }
}
