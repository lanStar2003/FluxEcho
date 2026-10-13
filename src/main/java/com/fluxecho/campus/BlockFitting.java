package com.fluxecho.campus;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.FluxEcho;
import com.fluxecho.frame.FrameEvents;
import com.fluxecho.logic.Parts;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The campus fittings: the steel pieces a module's interior is furnished with, one meta per part ({@link Parts}
 * fitting metas). Three of them make bookcases together with the echo shelf (a plinth, three shelves, a crown, posts
 * between the units); the others are the gallery's rails, the stair treads, the windows and the hologram pedestals,
 * and (0.11.0) the city's sample sockets (a 2..14/16 column whose side faces each hold one item in a recess) and basin
 * rims (a 10/16 slab with a lit lip; water and beds lie beside it).
 * They are not opaque and let light through, so a room full of furniture stays lit; each has its own box. Drawn into
 * the chunk mesh by {@code client.FittingRender}, so they work with shader packs.
 */
public class BlockFitting extends Block {

    /** What a formed module does when one of its fittings is used (an archive's bookcase). */
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

    /** The rail's centre post and arm width. */
    private static final float RAIL_MIN = 6 / 16f, RAIL_MAX = 10 / 16f;
    /** How high a rail's collision reaches (like a fence); the city's climb test reads it. */
    public static final float RAIL_HEIGHT = 1.5f;
    /** The basin rim's top (0.11.0). */
    public static final float BASIN_TOP = 10 / 16f;

    @SideOnly(Side.CLIENT)
    private IIcon[] side, top, glows;

    public BlockFitting() {
        super(Material.iron);
        setBlockName("fluxecho.fitting");
        setHardness(3f);
        setResistance(15f);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(FluxEcho.TAB);
        setLightOpacity(0);
    }

    /** The meta clamped to a known fitting type; unknown metas behave like a plinth (a whole cube). */
    public static int type(int meta) {
        return meta >= 0 && meta < Parts.FITTING_TYPES ? meta : Parts.F_PLINTH;
    }

    /** The light a fitting of this meta gives. */
    public static int light(int meta) {
        return meta >= 0 && meta < Parts.FITTING_TYPES ? Parts.FITTING_LIGHT[meta] : 0;
    }

    /** Whether the meta is a whole cube: the plinth, the crown, the glaze and (0.12.0) the light-lift pad. */
    public static boolean full(int meta) {
        int t = type(meta);
        return t == Parts.F_PLINTH || t == Parts.F_CROWN || t == Parts.F_GLAZE || t == Parts.F_LIFT;
    }

    /**
     * The part's box without what depends on its neighbours: x0, y0, z0, x1, y1, z1. A rail's box is its centre post;
     * {@link #railArm} says which arms it has on top of that.
     */
    public static float[] box(int meta) {
        switch (type(meta)) {
            case Parts.F_POST:
                return new float[] { 2 / 16f, 0, 2 / 16f, 14 / 16f, 1, 14 / 16f };
            case Parts.F_RAIL:
                return new float[] { RAIL_MIN, 0, RAIL_MIN, RAIL_MAX, 1, RAIL_MAX };
            case Parts.F_TREAD:
                return new float[] { 0, 0, 0, 1, 0.5f, 1 };
            case Parts.F_PEDESTAL:
                return new float[] { 3 / 16f, 0, 3 / 16f, 13 / 16f, 12 / 16f, 13 / 16f };
            case Parts.F_SOCKET:
                // a column like the post: its side faces hold the recess the sample floats in
                return new float[] { 2 / 16f, 0, 2 / 16f, 14 / 16f, 1, 14 / 16f };
            case Parts.F_BASIN:
                // a 10/16 slab: too high to walk up, low enough to look over at the bed it rims
                return new float[] { 0, 0, 0, 1, BASIN_TOP, 1 };
            default:
                return new float[] { 0, 0, 0, 1, 1, 1 };
        }
    }

    /**
     * Whether a rail at x, y, z reaches out towards the horizontal direction d: it does when the neighbour there is
     * another rail, a post or an opaque cube. Safe on the chunk-building thread (it only reads blocks and metas).
     */
    public static boolean railArm(IBlockAccess w, int x, int y, int z, ForgeDirection d) {
        if (d.offsetY != 0) return false;
        int nx = x + d.offsetX, nz = z + d.offsetZ;
        Block b = w.getBlock(nx, y, nz);
        if (b instanceof BlockFitting) {
            int m = w.getBlockMetadata(nx, y, nz);
            return m == Parts.F_RAIL || m == Parts.F_POST;
        }
        return b.isOpaqueCube();
    }

    /** The rail's outline: its centre post stretched to the block's edge on every side it has an arm. */
    private static float[] railBox(IBlockAccess w, int x, int y, int z) {
        float x0 = railArm(w, x, y, z, ForgeDirection.WEST) ? 0 : RAIL_MIN;
        float x1 = railArm(w, x, y, z, ForgeDirection.EAST) ? 1 : RAIL_MAX;
        float z0 = railArm(w, x, y, z, ForgeDirection.NORTH) ? 0 : RAIL_MIN;
        float z1 = railArm(w, x, y, z, ForgeDirection.SOUTH) ? 1 : RAIL_MAX;
        return new float[] { x0, 0, z0, x1, 1, z1 };
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < Parts.FITTING_TYPES; i++) list.add(new ItemStack(item, 1, i));
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
        return light(w.getBlockMetadata(x, y, z));
    }

    /** The whole cubes are solid on every side, a tread and a basin rim on their undersides only. */
    @Override
    public boolean isSideSolid(IBlockAccess w, int x, int y, int z, ForgeDirection side) {
        int meta = w.getBlockMetadata(x, y, z);
        if (full(meta)) return true;
        int t = type(meta);
        return (t == Parts.F_TREAD || t == Parts.F_BASIN) && side == ForgeDirection.DOWN;
    }

    @Override
    public boolean canCreatureSpawn(EnumCreatureType type, IBlockAccess w, int x, int y, int z) {
        return false;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess w, int x, int y, int z) {
        int meta = w.getBlockMetadata(x, y, z);
        float[] b = type(meta) == Parts.F_RAIL ? railBox(w, x, y, z) : box(meta);
        setBlockBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBlockBounds(0, 0, 0, 1, 1, 1);
    }

    /**
     * A rail collides as its centre post and one bar per arm, half a block taller than itself like a fence. A
     * light-lift rider passes through the fittings (and the pads) on the column of its ride (0.12.0,
     * {@link Lift#passes}), and any player through the fittings (not the pads) in the two cells over a pad, so the pad
     * can be boarded ({@link Lift#boardable}).
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addCollisionBoxesToList(World w, int x, int y, int z, AxisAlignedBB mask, List list, Entity e) {
        int meta = w.getBlockMetadata(x, y, z);
        if (e != null && Lift.passes(e, x, y, z, type(meta) == Parts.F_LIFT)) return;
        if (e != null && type(meta) != Parts.F_LIFT && Lift.boardable(e, x, y, z)) return;
        if (type(meta) != Parts.F_RAIL) {
            setBlockBoundsBasedOnState(w, x, y, z);
            super.addCollisionBoxesToList(w, x, y, z, mask, list, e);
            return;
        }
        setBlockBounds(RAIL_MIN, 0, RAIL_MIN, RAIL_MAX, RAIL_HEIGHT, RAIL_MAX);
        super.addCollisionBoxesToList(w, x, y, z, mask, list, e);
        if (railArm(w, x, y, z, ForgeDirection.WEST)) {
            setBlockBounds(0, 0, RAIL_MIN, RAIL_MIN, RAIL_HEIGHT, RAIL_MAX);
            super.addCollisionBoxesToList(w, x, y, z, mask, list, e);
        }
        if (railArm(w, x, y, z, ForgeDirection.EAST)) {
            setBlockBounds(RAIL_MAX, 0, RAIL_MIN, 1, RAIL_HEIGHT, RAIL_MAX);
            super.addCollisionBoxesToList(w, x, y, z, mask, list, e);
        }
        if (railArm(w, x, y, z, ForgeDirection.NORTH)) {
            setBlockBounds(RAIL_MIN, 0, 0, RAIL_MAX, RAIL_HEIGHT, RAIL_MIN);
            super.addCollisionBoxesToList(w, x, y, z, mask, list, e);
        }
        if (railArm(w, x, y, z, ForgeDirection.SOUTH)) {
            setBlockBounds(RAIL_MIN, 0, RAIL_MAX, RAIL_MAX, RAIL_HEIGHT, 1);
            super.addCollisionBoxesToList(w, x, y, z, mask, list, e);
        }
        setBlockBoundsBasedOnState(w, x, y, z);
    }

    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int side, float hx, float hy,
        float hz) {
        int meta = w.getBlockMetadata(x, y, z);
        for (Use u : USES) if (u.use(w, x, y, z, p, side, meta)) return true;
        return false;
    }

    @Override
    public void onBlockAdded(World w, int x, int y, int z) {
        super.onBlockAdded(w, x, y, z);
        if (!Builder.QUIET) FrameEvents.changed(w, x, y, z);
    }

    @Override
    public void breakBlock(World w, int x, int y, int z, Block block, int meta) {
        super.breakBlock(w, x, y, z, block, meta);
        if (!Builder.QUIET) FrameEvents.changed(w, x, y, z);
    }

    /**
     * Whether the face of the fitting next to x, y, z (the neighbour's position, as vanilla passes it) is drawn. Faces
     * inside the block's edge always are; a face on the edge is hidden against an opaque cube, two panes of glaze hide
     * the faces between them, and so do two plinths or crowns (or two basin rims) side by side. Worked out from the
     * metas, not from the block's shared bounds, so it does not matter which block set them last.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockAccess w, int x, int y, int z, int side) {
        ForgeDirection d = ForgeDirection.getOrientation(side);
        int ox = x - d.offsetX, oy = y - d.offsetY, oz = z - d.offsetZ;
        if (d == ForgeDirection.UNKNOWN || w.getBlock(ox, oy, oz) != this)
            return super.shouldSideBeRendered(w, x, y, z, side);
        int self = type(w.getBlockMetadata(ox, oy, oz));
        if (!onEdge(w, ox, oy, oz, self, d)) return true;
        Block other = w.getBlock(x, y, z);
        if (other == this) {
            int m = type(w.getBlockMetadata(x, y, z));
            if (self == Parts.F_GLAZE && m == Parts.F_GLAZE) return false;
            // two basin rims side by side are one slab: the faces between them are hidden (0.11.0)
            if (self == Parts.F_BASIN && m == Parts.F_BASIN && d.offsetY == 0) return false;
            boolean solid = self == Parts.F_PLINTH || self == Parts.F_CROWN;
            if (solid && (m == Parts.F_PLINTH || m == Parts.F_CROWN)) return false;
        }
        return !other.isOpaqueCube();
    }

    /** Whether the fitting's face towards d lies on the block's edge. */
    private static boolean onEdge(IBlockAccess w, int x, int y, int z, int type, ForgeDirection d) {
        if (type == Parts.F_RAIL) return d.offsetY != 0 || railArm(w, x, y, z, d);
        float[] b = box(type);
        switch (d) {
            case DOWN:
                return b[1] <= 0;
            case UP:
                return b[4] >= 1;
            case NORTH:
                return b[2] <= 0;
            case SOUTH:
                return b[5] >= 1;
            case WEST:
                return b[0] <= 0;
            case EAST:
                return b[3] >= 1;
            default:
                return true;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        side = new IIcon[Parts.FITTING_TYPES];
        top = new IIcon[Parts.FITTING_TYPES];
        glows = new IIcon[Parts.FITTING_TYPES];
        for (int i = 0; i < Parts.FITTING_TYPES; i++) {
            String p = FluxEcho.MODID + ":fitting/" + Parts.FITTING_NAMES[i];
            side[i] = r.registerIcon(p);
            top[i] = r.registerIcon(p + "_top");
            if (Parts.FITTING_LIGHT[i] > 0) glows[i] = r.registerIcon(p + "_glow");
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        return icon(meta, s);
    }

    /** The texture of a face: the top one on top and bottom, the side one around. */
    @SideOnly(Side.CLIENT)
    public IIcon icon(int meta, int s) {
        int t = type(meta);
        return s <= 1 ? top[t] : side[t];
    }

    /** The overlay drawn at full brightness on a face, or null when this meta does not glow. */
    @SideOnly(Side.CLIENT)
    public IIcon glow(int meta, int s) {
        return meta >= 0 && meta < Parts.FITTING_TYPES ? glows[meta] : null;
    }
}
