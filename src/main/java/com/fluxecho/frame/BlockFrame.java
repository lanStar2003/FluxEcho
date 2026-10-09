package com.fluxecho.frame;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.FluxEcho;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The flux frame (blueprint 3.13): the scaffolding the Flux Nexus and its modules are built of, one meta per part.
 * While the multiblock is only built these are its blocks; once it forms, some give way to what the multiblock draws in
 * their place ({@link Formed}). Drawn into the chunk mesh by {@code client.FrameRender}, so they work with shader
 * packs; the bright parts are drawn at full brightness.
 */
public class BlockFrame extends Block {

    public static final int BASE = 0, BASE_LIT = 1, PILLAR = 2, CONDUIT = 3, RING = 4, SEAT = 5, FOUNDATION = 6,
        SHELF = 7, CONSOLE = 8, TYPES = 9;
    public static final String[] NAMES = { "base", "base_lit", "pillar", "conduit", "ring", "seat", "foundation",
        "shelf", "console" };
    private static final int[] LIGHT = { 0, 7, 0, 12, 8, 15, 4, 8, 10 };

    /** What a formed multiblock does when one of its frame blocks is used (a library's shelf, its desk). */
    public interface Use {

        /** Whether it handled the click; called on both sides. */
        boolean use(World w, int x, int y, int z, EntityPlayer p, int side, int meta);
    }

    private static final List<Use> USES = new ArrayList<>();

    public static void onUse(Use u) {
        USES.add(u);
    }

    /** Set by the client proxy. */
    public static int renderId = -1;

    @SideOnly(Side.CLIENT)
    private IIcon[] top, side, glowTop, glowSide;
    @SideOnly(Side.CLIENT)
    public IIcon core, crystal;

    public BlockFrame() {
        super(Material.iron);
        setBlockName("fluxecho.frame");
        setHardness(5f);
        setResistance(30f);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 2);
        setCreativeTab(FluxEcho.TAB);
        setLightOpacity(0);
    }

    /** Whether the meta is a whole cube. */
    public static boolean full(int meta) {
        return meta == BASE || meta == BASE_LIT || meta == FOUNDATION || meta == SHELF || meta >= TYPES;
    }

    /** The part's box: x0, y0, z0, x1, y1, z1. */
    public static float[] box(int meta) {
        switch (meta) {
            case PILLAR:
                return new float[] { 5 / 16f, 0, 5 / 16f, 11 / 16f, 1, 11 / 16f };
            case CONDUIT:
                return new float[] { 4 / 16f, 0, 4 / 16f, 12 / 16f, 1, 12 / 16f };
            case RING:
                return new float[] { 0, 5 / 16f, 0, 1, 11 / 16f, 1 };
            case SEAT:
                return new float[] { 0, 0, 0, 1, 8 / 16f, 1 };
            case CONSOLE:
                return new float[] { 1 / 16f, 0, 1 / 16f, 15 / 16f, 1, 15 / 16f };
            default:
                return new float[] { 0, 0, 0, 1, 1, 1 };
        }
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < TYPES; i++) list.add(new ItemStack(item, 1, i));
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return renderId;
    }

    @Override
    public int getLightValue(IBlockAccess w, int x, int y, int z) {
        int meta = w.getBlockMetadata(x, y, z);
        return meta >= 0 && meta < TYPES ? LIGHT[meta] : 0;
    }

    @Override
    public boolean isSideSolid(IBlockAccess w, int x, int y, int z, ForgeDirection side) {
        return full(w.getBlockMetadata(x, y, z));
    }

    @Override
    public boolean canCreatureSpawn(net.minecraft.entity.EnumCreatureType type, IBlockAccess w, int x, int y, int z) {
        return false;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess w, int x, int y, int z) {
        float[] b = box(w.getBlockMetadata(x, y, z));
        setBlockBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBlockBounds(0, 0, 0, 1, 1, 1);
    }

    /** Parts that gave way to a formed multiblock's drawing let players through. */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addCollisionBoxesToList(World w, int x, int y, int z, AxisAlignedBB mask, List list, Entity e) {
        if ((Formed.flags(w, x, y, z) & Formed.PASS) != 0) return;
        setBlockBoundsBasedOnState(w, x, y, z);
        super.addCollisionBoxesToList(w, x, y, z, mask, list, e);
    }

    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int side, float hx, float hy,
        float hz) {
        int meta = w.getBlockMetadata(x, y, z);
        for (Use u : USES) if (u.use(w, x, y, z, p, side, meta)) return true;
        return false;
    }

    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(w, x, y, z, placer, stack);
        FrameEvents.changed(w, x, y, z);
    }

    @Override
    public void breakBlock(World w, int x, int y, int z, Block block, int meta) {
        super.breakBlock(w, x, y, z, block, meta);
        FrameEvents.changed(w, x, y, z);
    }

    /** Two whole cubes side by side hide the faces between them, unless the other one is hidden. */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockAccess w, int x, int y, int z, int side) {
        ForgeDirection d = ForgeDirection.getOrientation(side);
        int ox = x - d.offsetX, oy = y - d.offsetY, oz = z - d.offsetZ;
        if (w.getBlock(x, y, z) == this && full(w.getBlockMetadata(x, y, z))
            && full(w.getBlockMetadata(ox, oy, oz))
            && (Formed.clientFlags(x, y, z) & Formed.HIDE) == 0) return false;
        return super.shouldSideBeRendered(w, x, y, z, side);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        top = new IIcon[TYPES];
        side = new IIcon[TYPES];
        glowTop = new IIcon[TYPES];
        glowSide = new IIcon[TYPES];
        for (int i = 0; i < TYPES; i++) {
            String p = FluxEcho.MODID + ":frame/" + NAMES[i];
            top[i] = r.registerIcon(p + "_top");
            side[i] = r.registerIcon(p + "_side");
        }
        glowTop[BASE_LIT] = r.registerIcon(FluxEcho.MODID + ":frame/base_lit_top_glow");
        glowTop[FOUNDATION] = r.registerIcon(FluxEcho.MODID + ":frame/foundation_top_glow");
        glowSide[RING] = r.registerIcon(FluxEcho.MODID + ":frame/ring_side_glow");
        glowSide[SHELF] = r.registerIcon(FluxEcho.MODID + ":frame/shelf_side_glow");
        glowTop[CONSOLE] = r.registerIcon(FluxEcho.MODID + ":frame/console_top_glow");
        glowSide[CONSOLE] = r.registerIcon(FluxEcho.MODID + ":frame/console_side_glow");
        core = r.registerIcon(FluxEcho.MODID + ":frame/conduit_core");
        crystal = r.registerIcon(FluxEcho.MODID + ":frame/seat_crystal");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        int m = meta >= 0 && meta < TYPES ? meta : 0;
        return s <= 1 ? top[m] : side[m];
    }

    /** The overlay drawn at full brightness on a face, or null. */
    @SideOnly(Side.CLIENT)
    public IIcon glow(int s, int meta) {
        if (meta < 0 || meta >= TYPES) return null;
        return s == 1 ? glowTop[meta] : s >= 2 ? glowSide[meta] : null;
    }
}
