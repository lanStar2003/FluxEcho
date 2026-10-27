package com.fluxecho.skin;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.FluxEcho;
import com.fluxecho.campus.Builder;
import com.fluxecho.campus.Lift;
import com.fluxecho.client.shape.SkinIcons;
import com.fluxecho.frame.Formed;
import com.fluxecho.frame.FrameEvents;
import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.shape.Form;
import com.fluxecho.logic.shape.SkinBlocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A block of the new material families (0.17.0, style FINAL §3.1–§3.3): the pearl-white hull ({@code fluxecho:hull}),
 * the warm black footing ({@code footing}), and the skins of the first batch, moss bronze and white marble
 * ({@code skin_garden}), ink and paper ({@code skin_script}). Each family is two of these blocks, its whole-block id
 * and its shape-block id ({@code *_form}); the meta is the shape number (形号) in either ({@link SkinBlocks}).
 * <p>
 * Why two ids: a whole-block id is an opaque cube, so a wall of it culls, lights and shades exactly as stone does, at
 * stone's cost; a shape-block id is not, and draws the neighbour-aware shapes (rounds, slopes, ribs, soffits...) from
 * {@code ShapeGeo} through {@code client.shape.ShapeRender}, the one renderer both use. Shapes have no facing: a block
 * looks at its neighbours to decide its geometry, so a blueprint forms in all four facings.
 * <p>
 * What the builder and the city expect of a block of ours, these do too: they collide with {@code ShapeGeo}'s boxes,
 * the same the walk tests take; a formed multiblock may hide one ({@link Formed#HIDE}: drawn as nothing, its
 * neighbours' faces toward it drawn again) or let players through it ({@link Formed#PASS}), though the design dissolves
 * none of the new families in normal play (style FINAL §3.9.8, DECISIONS B9); a light-lift rider passes through them
 * as through the frame and the fittings ({@link Lift#passes}, {@code LiftShaft}'s OURS); placing or breaking one tells
 * the multiblocks around, unless the builder is at work ({@link Builder#QUIET}); nothing spawns on them.
 */
public class BlockSkin extends Block {

    /** Set by the client proxy ({@code client.shape.ShapeClient}): the one render type of every family's blocks. */
    public static int renderId = -1;

    /** The material family. */
    public final Family family;
    /** Whether this is the family's whole-block id (an opaque cube); else its shape-block id. */
    public final boolean whole;

    public BlockSkin(Family family, boolean whole) {
        super(Material.rock);
        this.family = family;
        this.whole = whole;
        setBlockName("fluxecho." + family.id);
        setHardness(family == Family.FOOTING ? 4f : 3f);
        setResistance(20f);
        setStepSound(soundTypeStone);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(FluxEcho.TAB);
        // Block's constructor asked isOpaqueCube before the family was set: say it again now
        opaque = whole;
        setLightOpacity(whole ? 255 : 0);
        // a shape's body cell holds no light of its own (its opacity is 255): its faces take their neighbours'
        useNeighborBrightness = !whole;
    }

    /** The registry name of the block: the family's, {@code _form} for the shape-block id. */
    public String registryName() {
        return whole ? family.id : family.id + SkinBlocks.FORM_SUFFIX;
    }

    /** The geometry the block has at a meta ({@link SkinBlocks#geometry}). */
    public Form form(int meta) {
        return SkinBlocks.geometry(family, whole, meta);
    }

    /** The part code of the block at a meta, -1 when that meta is no part ({@link SkinBlocks#code}). */
    public int code(int meta) {
        return SkinBlocks.code(family, whole, meta);
    }

    @Override
    public boolean isOpaqueCube() {
        return whole;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return whole;
    }

    @Override
    public int getRenderType() {
        return renderId;
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    /** Only the shapes the first batch gives a part code are listed (style FINAL §3.8.1). */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int m : SkinBlocks.listedMetas(family, whole)) list.add(new ItemStack(item, 1, m));
    }

    @Override
    public int getLightValue(IBlockAccess w, int x, int y, int z) {
        return SkinBlocks.light(form(w.getBlockMetadata(x, y, z)));
    }

    @Override
    public int getLightOpacity(IBlockAccess w, int x, int y, int z) {
        return whole ? 255 : SkinBlocks.lightOpacity(form(w.getBlockMetadata(x, y, z)));
    }

    /**
     * A whole cube is solid on every side. A shape is only where it is flat and full: the underside of a round, a
     * slope, a coping, a half and a step, and the top of a soffit; a round's crown and a slope's roof are not, so snow
     * does not settle on them and nothing hangs from them (style FINAL §3.3.5).
     */
    @Override
    public boolean isSideSolid(IBlockAccess w, int x, int y, int z, ForgeDirection side) {
        if (whole) return true;
        switch (form(w.getBlockMetadata(x, y, z))) {
            case WHOLE:
                return true;
            case ROUND:
            case SLOPE:
            case COPING:
            case HALF:
            case STEP:
                return side == ForgeDirection.DOWN;
            case SOFFIT:
                return side == ForgeDirection.UP;
            default:
                return false;
        }
    }

    @Override
    public boolean canCreatureSpawn(EnumCreatureType type, IBlockAccess w, int x, int y, int z) {
        return false;
    }

    /** The selection box: the union of the shape's collision boxes (a whole cell, or the lower half of a slab). */
    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess w, int x, int y, int z) {
        if (whole) {
            setBlockBounds(0, 0, 0, 1, 1, 1);
            return;
        }
        List<double[]> boxes = ShapeAccess.boxes(w, x, y, z, ShapeAccess.hidden(w));
        if (boxes.isEmpty()) {
            setBlockBounds(0, 0, 0, 1, 1, 1);
            return;
        }
        double[] u = boxes.get(0)
            .clone();
        for (double[] b : boxes) for (int i = 0; i < 3; i++) {
            u[i] = Math.min(u[i], b[i]);
            u[i + 3] = Math.max(u[i + 3], b[i + 3]);
        }
        setBlockBounds((float) u[0], (float) u[1], (float) u[2], (float) u[3], (float) u[4], (float) u[5]);
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBlockBounds(0, 0, 0, 1, 1, 1);
    }

    /**
     * The shape's own boxes ({@code ShapeGeo.boxes}), none where a formed multiblock lets players through
     * ({@link Formed#PASS}), on a light-lift rider's column ({@link Lift#passes}) or in the two cells over a pad that a
     * player boards ({@link Lift#boardable}).
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addCollisionBoxesToList(World w, int x, int y, int z, AxisAlignedBB mask, List list, Entity e) {
        if ((Formed.flags(w, x, y, z) & Formed.PASS) != 0) return;
        if (e != null && (Lift.passes(e, x, y, z, false) || Lift.boardable(e, x, y, z))) return;
        for (double[] b : ShapeAccess.boxes(w, x, y, z, ShapeAccess.hidden(w))) {
            AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(x + b[0], y + b[1], z + b[2], x + b[3], y + b[4], z + b[5]);
            if (mask.intersectsWith(bb)) list.add(bb);
        }
        setBlockBoundsBasedOnState(w, x, y, z);
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
     * A whole cube hides its faces against opaque cubes as stone does, but draws its face toward a neighbour a formed
     * multiblock hides (style FINAL §3.9.8, as the frame does). {@code x, y, z} is the neighbour's position.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockAccess w, int x, int y, int z, int side) {
        if ((Formed.clientFlags(x, y, z) & Formed.HIDE) != 0) return true;
        return super.shouldSideBeRendered(w, x, y, z, side);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        SkinIcons.register(r, family);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return SkinIcons.icon(family, meta, form(meta), side);
    }

    /** A whole cube's face in the world: the piece of its material's pattern this cell shows (patterns span cells). */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess w, int x, int y, int z, int side) {
        int meta = w.getBlockMetadata(x, y, z);
        return SkinIcons.face(w, x, y, z, family, meta, form(meta), side);
    }

    /** The hull is drawn at the player's {@code style.hullBrightness} (0.85 to 1.0, style FINAL §8.4 #7). */
    @Override
    @SideOnly(Side.CLIENT)
    public int colorMultiplier(IBlockAccess w, int x, int y, int z) {
        return SkinIcons.tint(family);
    }
}
