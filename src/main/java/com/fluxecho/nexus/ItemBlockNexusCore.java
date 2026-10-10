package com.fluxecho.nexus;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.Config;
import com.fluxecho.campus.BlockDeck;
import com.fluxecho.campus.BlockFitting;
import com.fluxecho.campus.BuildJob;
import com.fluxecho.campus.Campus;
import com.fluxecho.campus.CampusRegistry;
import com.fluxecho.core.EchoText;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.LiftRule;
import com.fluxecho.logic.NexusShape;
import com.gtnewhorizons.modularui.api.forge.ItemStackHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Flux Nexus core as an item (0.10.0 「营造」). Clicking the top of the ground puts the core two above the clicked
 * block ({@link LiftRule}): the clicked layer becomes the nexus base, the cell above it the console stand, and the
 * core sits at eye height where the finished nexus keeps its controller. The nexus's campus then becomes active and
 * its establish job is surveyed and projected, waiting for a member's 开始; nothing in the world is touched before.
 * Sneaking, clicking a side, or clicking a frame, deck or fitting block places the core the 0.9.2 way: a legacy
 * nexus the player builds by hand.
 * <p>
 * A lifted placement is refused when the nexus could not grow there: too close to the top of the world, no open sky
 * over the core, its base on a module, or another campus within {@code Config.buildMinSpacing}. Only the server
 * decides a lifted placement; the client applies the same rule and then waits for the block instead of guessing, so
 * a refused or moved placement never leaves a ghost block or a wrong stack count behind.
 * <p>
 * A core broken off a nexus ({@link BlockNexusCore}) carries the campus's credit ({@link #CREDIT}: the ledger's raw
 * credit and parts, the spoils) and the manifestation that was still running ({@link #PENDING}, {@link #MANIFEST});
 * placing that core puts both back into the new nexus, lifted or not.
 */
public class ItemBlockNexusCore extends ItemBlockNexus {

    /** Item NBT: the campus credit {Raw{key: units}, Parts{code: n}, Spoils[item...]}. */
    public static final String CREDIT = "BuildCredit";
    /** Item NBT: the unfinished manifestation's product (an item stack). */
    public static final String PENDING = "Pending";
    /** Item NBT: how far that manifestation was {Id, Ticks, Max}. */
    public static final String MANIFEST = "Manifest";
    /** Blocks above the base the nexus needs below the top of the world for a lifted placement (Y0 + 30 ≤ 255). */
    public static final int HEADROOM = 30;

    public ItemBlockNexusCore(Block block) {
        super(block);
    }

    // ---- placing

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer p, World w, int x, int y, int z, int side, float hx,
        float hy, float hz) {
        if (stack == null || stack.stackSize <= 0) return false;
        int[] g = ground(w, x, y, z, side);
        if (g == null || lift(w, p, g) == 0) return super.onItemUse(stack, p, w, x, y, z, side, hx, hy, hz);
        // the server alone places a lifted core; the client sees the block (and its stack) when the server sends them
        if (w.isRemote) return true;
        return placeLifted(stack, p, w, g[0], g[1], g[2], hx, hy, hz);
    }

    /**
     * The block whose top counts as clicked, {x, y, z, side}: the clicked block, or the ground under a plant, a vine
     * or a thin snow layer that a normal placement would replace. Null when that ground is not there (a vine over air),
     * which places the core the normal way.
     */
    static int[] ground(World w, int x, int y, int z, int side) {
        Block b = w.getBlock(x, y, z);
        boolean soft = b == Blocks.snow_layer ? (w.getBlockMetadata(x, y, z) & 7) < 1
            : b == Blocks.vine || b == Blocks.tallgrass || b == Blocks.deadbush || b.isReplaceable(w, x, y, z);
        if (!soft) return new int[] { x, y, z, side };
        if (y <= 0) return null;
        Block below = w.getBlock(x, y - 1, z);
        if (below.isReplaceable(w, x, y - 1, z)) return null;
        return new int[] { x, y - 1, z, LiftRule.TOP };
    }

    /** {@link LiftRule} with the world's facts about the clicked ground {x, y, z, side}. */
    static int lift(World w, EntityPlayer p, int[] g) {
        int x = g[0], y = g[1], z = g[2];
        if (y < 0 || y + LiftRule.LIFT > 255) return 0;
        Block clicked = w.getBlock(x, y, z);
        boolean ours = clicked instanceof BlockFrame || clicked instanceof BlockDeck || clicked instanceof BlockFitting;
        return LiftRule.lift(g[3], p.isSneaking(), ours, free(w, x, y + 1, z), free(w, x, y + 2, z));
    }

    /** Whether a cell can take a block: replaceable (air included) and no entity standing in it. */
    static boolean free(World w, int x, int y, int z) {
        if (y < 0 || y > 255
            || !w.getBlock(x, y, z)
                .isReplaceable(w, x, y, z))
            return false;
        return w.checkNoEntityCollision(AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1));
    }

    /** Places the core two above the ground block (x, y0, z), checks first, then raises the establish job. */
    private boolean placeLifted(ItemStack stack, EntityPlayer p, World w, int x, int y0, int z, float hx, float hy,
        float hz) {
        int cy = y0 + LiftRule.LIFT;
        if (!p.canPlayerEdit(x, cy, z, LiftRule.TOP, stack)) return false;
        boolean campus = Config.nexusEnabled && Config.campusEnabled;
        if (campus) {
            IChatComponent no = refusal(w, p, x, cy, z);
            if (no != null) {
                p.addChatMessage(no);
                return false;
            }
        }
        Block block = field_150939_a;
        if (!w.canPlaceEntityOnSide(block, x, cy, z, false, LiftRule.TOP, p, stack)) return false;
        int meta = block.onBlockPlaced(w, x, cy, z, LiftRule.TOP, hx, hy, hz, getMetadata(stack.getItemDamage()));
        if (!placeBlockAt(stack, p, w, x, cy, z, LiftRule.TOP, hx, hy, hz, meta)) return true;
        w.playSoundEffect(
            x + 0.5,
            cy + 0.5,
            z + 0.5,
            block.stepSound.func_150496_b(),
            (block.stepSound.getVolume() + 1f) / 2f,
            block.stepSound.getPitch() * 0.8f);
        stack.stackSize--;
        // the owner and the front are set by now (onBlockPlacedBy), so the campus knows its centre
        if (campus && w.getTileEntity(x, cy, z) instanceof TileNexus n) {
            p.addChatMessage(
                new ChatComponentTranslation(
                    n.campus()
                        .establish(p)));
        }
        return true;
    }

    /** Why the nexus cannot grow round a core at (x, cy, z) facing away from the player; null when it can. */
    private static IChatComponent refusal(World w, EntityPlayer p, int x, int cy, int z) {
        ForgeDirection f = ForgeDirection.getOrientation(facingFor(p));
        Blueprint b = NexusShape.PHASE_1;
        int[] c = b.world(b.ctrlA, b.height() - 1, b.ctrlC + b.centreBack(), x, cy, z, f.offsetX, f.offsetZ);
        if (c[1] + HEADROOM > 255) return new ChatComponentTranslation("fluxecho.build.too_high");
        if (Config.buildRequireSky && !w.canBlockSeeTheSky(x, cy, z))
            return new ChatComponentTranslation("fluxecho.build.no_sky");
        if (onModule(w, box(b, x, cy, z, f))) return new ChatComponentTranslation("fluxecho.build.on_module");
        int[] near = tooClose(w, c);
        if (near != null)
            return new ChatComponentTranslation("fluxecho.build.too_close", near[0] + ", " + near[1] + ", " + near[2]);
        return null;
    }

    /** The front a core placed by the entity gets: it faces the placer (as {@link BlockNexus#onBlockPlacedBy}). */
    static int facingFor(EntityLivingBase placer) {
        int look = MathHelper.floor_double(placer.rotationYaw * 4f / 360f + 0.5) & 3;
        int[] front = { 2, 5, 3, 4 };
        return front[look];
    }

    /** The block box {x0, y0, z0, x1, y1, z1} the phase-I nexus round a core at (x, y, z) takes up. */
    static int[] box(Blueprint b, int x, int y, int z, ForgeDirection f) {
        int[] p = b.world(0, 0, 0, x, y, z, f.offsetX, f.offsetZ);
        int[] q = b.world(b.width() - 1, b.height() - 1, b.depth() - 1, x, y, z, f.offsetX, f.offsetZ);
        return new int[] { Math.min(p[0], q[0]), Math.min(p[1], q[1]), Math.min(p[2], q[2]), Math.max(p[0], q[0]),
            Math.max(p[1], q[1]), Math.max(p[2], q[2]) };
    }

    /**
     * Whether the box overlaps a loaded module: its checked bounds, or its foundation round its centre when unchecked.
     */
    static boolean onModule(World w, int[] box) {
        for (TileModule m : NexusRegistry.modules(w.provider.dimensionId)) {
            if (m.isInvalid() || m.getWorldObj() != w) continue;
            int[] mb = m.bounds();
            if (mb == null) {
                int[] mc = m.centre();
                int[] k = BuildPlan.moduleKeepOut(mc);
                mb = new int[] { Math.min(k[0], k[2]), mc[1] - 1, Math.min(k[1], k[3]), Math.max(k[0], k[2]),
                    mc[1] + 16, Math.max(k[1], k[3]) };
            }
            if (box[0] <= mb[3] && mb[0] <= box[3]
                && box[1] <= mb[4]
                && mb[1] <= box[4]
                && box[2] <= mb[5]
                && mb[2] <= box[5]) return true;
        }
        return false;
    }

    /**
     * The centre of a campus, or of a loaded nexus that has none yet (a 0.9.2 nexus may lay its forum later), closer
     * than {@code Config.buildMinSpacing} (Chebyshev) to the centre {@code c}; null when there is none.
     */
    static int[] tooClose(World w, int[] c) {
        int spacing = Config.buildMinSpacing;
        if (spacing <= 0) return null;
        int[] near = CampusRegistry.tooClose(w, c[0], c[2], spacing);
        if (near != null) return near;
        for (TileNexus n : NexusRegistry.nexuses(w.provider.dimensionId)) {
            if (n.isInvalid() || n.getWorldObj() != w) continue;
            int[] o = n.centre();
            if (Math.max(Math.abs(o[0] - c[0]), Math.abs(o[2] - c[2])) < spacing) return o;
        }
        return null;
    }

    /** Places the block as usual, then (server side) puts back what a broken core carried. */
    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer p, World w, int x, int y, int z, int side, float hx,
        float hy, float hz, int meta) {
        if (!super.placeBlockAt(stack, p, w, x, y, z, side, hx, hy, hz, meta)) return false;
        if (!w.isRemote && w.getTileEntity(x, y, z) instanceof TileNexus n) restore(stack, n);
        return true;
    }

    // ---- what a broken core carries

    /**
     * Whether a nexus has anything for its core to carry ({@link #carry}): credit in the ledger, spoils, launches in
     * flight that were paid for, or a manifestation running. Takes nothing.
     */
    public static boolean carries(TileNexus n) {
        Campus c = n.campus();
        for (long v : c.ledger()
            .rawView()
            .values()) if (v > 0) return true;
        for (int v : c.ledger()
            .partView()
            .values()) if (v > 0) return true;
        ItemStackHandler h = c.spoils();
        for (int i = 0; i < h.getSlots(); i++) if (h.getStackInSlot(i) != null) return true;
        BuildJob j = c.job();
        if (j != null) for (BuildJob.Flight f : j.flights()) if (f.charged) return true;
        return n.making() != null;
    }

    /**
     * What a nexus being broken hands to its core item: the campus credit (in-flight launches refunded first) and the
     * running manifestation. Empties the ledger and the spoils it took, so nothing can be had twice. An empty tag when
     * there is nothing to carry.
     */
    public static NBTTagCompound carry(TileNexus n) {
        NBTTagCompound out = new NBTTagCompound();
        Campus c = n.campus();
        c.refundFlights();
        NBTTagCompound raw = new NBTTagCompound(), parts = new NBTTagCompound();
        for (Map.Entry<String, Long> e : c.ledger()
            .rawView()
            .entrySet()) if (e.getValue() > 0) raw.setLong(e.getKey(), e.getValue());
        for (Map.Entry<Integer, Integer> e : c.ledger()
            .partView()
            .entrySet()) if (e.getValue() > 0) parts.setInteger(String.valueOf(e.getKey()), e.getValue());
        NBTTagList spoils = new NBTTagList();
        ItemStackHandler h = c.spoils();
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s != null) spoils.appendTag(s.writeToNBT(new NBTTagCompound()));
        }
        if (!raw.hasNoTags() || !parts.hasNoTags() || spoils.tagCount() > 0) {
            NBTTagCompound credit = new NBTTagCompound();
            credit.setTag("Raw", raw);
            credit.setTag("Parts", parts);
            credit.setTag("Spoils", spoils);
            out.setTag(CREDIT, credit);
            c.ledger()
                .load(null, null);
            for (int i = 0; i < h.getSlots(); i++) if (h.getStackInSlot(i) != null) h.setStackInSlot(i, null);
        }
        ItemStack made = n.making();
        if (made != null) {
            out.setTag(PENDING, made.writeToNBT(new NBTTagCompound()));
            NBTTagCompound tile = new NBTTagCompound();
            n.writeToNBT(tile);
            NBTTagCompound m = new NBTTagCompound();
            m.setString("Id", tile.getString("Manifest"));
            m.setInteger("Ticks", tile.getInteger("ManifestTicks"));
            m.setInteger("Max", tile.getInteger("ManifestMax"));
            out.setTag(MANIFEST, m);
        }
        return out;
    }

    /**
     * Puts back what the core item carries into a freshly placed nexus: the running manifestation (only into a nexus
     * that is not manifesting) and the credit, added to what the ledger and spoils hold. Server side.
     */
    static void restore(ItemStack stack, TileNexus n) {
        NBTTagCompound t = stack.getTagCompound();
        if (t == null) return;
        if (t.hasKey(PENDING, 10) && n.making() == null
            && n.manifest()
                .isEmpty()) {
            ItemStack made = ItemStack.loadItemStackFromNBT(t.getCompoundTag(PENDING));
            if (made != null) {
                // the manifestation's state is private to the tile: write it, put the run in, read it back
                NBTTagCompound m = t.getCompoundTag(MANIFEST), tile = new NBTTagCompound();
                n.writeToNBT(tile);
                int max = Math.max(1, m.getInteger("Max"));
                String id = m.getString("Id");
                tile.setString("Manifest", id.isEmpty() ? "restored" : id);
                tile.setInteger("ManifestTicks", Math.max(0, Math.min(max, m.getInteger("Ticks"))));
                tile.setInteger("ManifestMax", max);
                tile.setTag("Pending", made.writeToNBT(new NBTTagCompound()));
                n.readFromNBT(tile);
            }
        }
        if (t.hasKey(CREDIT, 10)) {
            NBTTagCompound credit = t.getCompoundTag(CREDIT);
            Campus c = n.campus();
            Map<String, Long> raw = new TreeMap<>(
                c.ledger()
                    .rawView());
            NBTTagCompound r = credit.getCompoundTag("Raw");
            for (Object k : r.func_150296_c()) raw.merge((String) k, r.getLong((String) k), Long::sum);
            Map<Integer, Integer> parts = new TreeMap<>(
                c.ledger()
                    .partView());
            NBTTagCompound pt = credit.getCompoundTag("Parts");
            for (Object k : pt.func_150296_c()) {
                try {
                    parts.merge(Integer.parseInt((String) k), pt.getInteger((String) k), Integer::sum);
                } catch (NumberFormatException ignored) {}
            }
            c.ledger()
                .load(raw, parts);
            NBTTagList spoils = credit.getTagList("Spoils", 10);
            ItemStackHandler h = c.spoils();
            World w = n.getWorldObj();
            for (int i = 0; i < spoils.tagCount(); i++) {
                ItemStack s = ItemStack.loadItemStackFromNBT(spoils.getCompoundTagAt(i));
                for (int j = 0; j < h.getSlots() && s != null; j++) s = h.insertItem(j, s, false);
                // more spoils than slots (a nexus that already had some): the rest falls at the core
                if (s != null && w != null)
                    w.spawnEntityInWorld(new EntityItem(w, n.xCoord + 0.5, n.yCoord + 1.5, n.zCoord + 0.5, s));
            }
        }
        n.markDirty();
    }

    // ---- tooltip

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tip, boolean advanced) {
        super.addInformation(stack, player, tip, advanced);
        tip.addAll(EchoText.lines("nexus.place_hint"));
        NBTTagCompound t = stack.getTagCompound();
        if (t == null) return;
        if (t.hasKey(CREDIT, 10)) {
            NBTTagCompound c = t.getCompoundTag(CREDIT);
            int kinds = c.getCompoundTag("Raw")
                .func_150296_c()
                .size();
            NBTTagCompound pt = c.getCompoundTag("Parts");
            int parts = 0;
            for (Object k : pt.func_150296_c()) parts += pt.getInteger((String) k);
            int spoils = c.getTagList("Spoils", 10)
                .tagCount();
            tip.add(EchoText.t("nexus.carries_credit", kinds, parts, spoils));
        }
        if (t.hasKey(PENDING, 10)) {
            ItemStack made = ItemStack.loadItemStackFromNBT(t.getCompoundTag(PENDING));
            if (made != null) tip.add(EchoText.t("nexus.carries_pending", made.stackSize, made.getDisplayName()));
        }
    }

    /** A core that carries something shimmers, so it is not mistaken for a new one. */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack, int pass) {
        NBTTagCompound t = stack.getTagCompound();
        return t != null && (t.hasKey(CREDIT) || t.hasKey(PENDING)) || super.hasEffect(stack, pass);
    }
}
