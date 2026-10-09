package com.fluxecho.gate;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.fluxecho.FluxEcho;
import com.fluxecho.logic.GateGeometry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The light gate: a glowing threshold plate; the frame and the membrane above it are drawn by the client. Placed
 * outside the flux interior it opens a plot there (or the plot its item remembers); the gate inside a plot cannot be
 * broken. Broken, it drops itself with its plot, so it opens onto the same plot wherever it is put down again.
 */
public class BlockLightGate extends BlockContainer {

    @SideOnly(Side.CLIENT)
    private IIcon top, side;

    public BlockLightGate() {
        super(Material.iron);
        setBlockName("fluxecho.light_gate");
        setCreativeTab(FluxEcho.TAB);
        setHardness(3f);
        setResistance(2000f);
        setLightLevel(0.6f);
        setStepSound(soundTypeMetal);
        setBlockBounds(0f, 0f, 0f, 1f, 0.125f, 1f);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileLightGate();
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
    public float getBlockHardness(World w, int x, int y, int z) {
        return w.getTileEntity(x, y, z) instanceof TileLightGate t && t.inside ? -1f
            : super.getBlockHardness(w, x, y, z);
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess w, int x, int y, int z, Entity entity) {
        return false;
    }

    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (w.isRemote || !(w.getTileEntity(x, y, z) instanceof TileLightGate t)) return;
        // the front faces whoever put it down
        t.facing = GateGeometry.sideOfYaw(placer.rotationYaw) + 2 & 3;
        Gates.placed(t, stack, placer instanceof EntityPlayer p ? p : null);
    }

    /** Drops itself with its plot, before the tile entity is gone; creative players get nothing. */
    @Override
    public void onBlockHarvested(World w, int x, int y, int z, int meta, EntityPlayer player) {
        if (w.isRemote || player.capabilities.isCreativeMode) return;
        if (w.getTileEntity(x, y, z) instanceof TileLightGate t && !t.inside)
            dropBlockAsItem(w, x, y, z, Gates.itemFor(t));
    }

    @Override
    public ArrayList<ItemStack> getDrops(World w, int x, int y, int z, int meta, int fortune) {
        return new ArrayList<>();
    }

    @Override
    public void breakBlock(World w, int x, int y, int z, Block block, int meta) {
        if (!w.isRemote) Gates.removed(w, x, y, z);
        super.breakBlock(w, x, y, z, block, meta);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        top = r.registerIcon("fluxecho:gate/threshold_top");
        side = r.registerIcon("fluxecho:gate/threshold_side");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int face, int meta) {
        return face == 1 ? top : side;
    }
}
