package com.fluxecho.gate;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The floor of a plot in the flux interior (meta 0) and its glowing rim (meta 1): made with the plot, never broken,
 * so nobody falls through into the void below.
 */
public class BlockInteriorFloor extends Block {

    public static final int FLOOR = 0, RIM = 1;

    @SideOnly(Side.CLIENT)
    private IIcon floor, rim;

    public BlockInteriorFloor() {
        super(Material.rock);
        setBlockName("fluxecho.interior_floor");
        setBlockUnbreakable();
        setResistance(6_000_000f);
        setStepSound(soundTypeStone);
    }

    @Override
    public int getLightValue(IBlockAccess w, int x, int y, int z) {
        return w.getBlockMetadata(x, y, z) == RIM ? 12 : 0;
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess w, int x, int y, int z, Entity entity) {
        return false;
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {}

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        floor = r.registerIcon("fluxecho:gate/floor");
        rim = r.registerIcon("fluxecho:gate/rim");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int face, int meta) {
        return meta == RIM ? rim : floor;
    }
}
