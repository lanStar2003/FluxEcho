package com.fluxecho.gate;

import java.util.List;
import java.util.Random;

import net.minecraft.block.BlockBreakable;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The glass of a room's roof: the real sky shows through (bees see it, crops get the sun), and like the rest of the
 * shell it is never broken.
 */
public class BlockInteriorGlass extends BlockBreakable {

    public BlockInteriorGlass() {
        super("fluxecho:gate/glass", Material.glass, false);
        setBlockName("fluxecho.interior_glass");
        setBlockUnbreakable();
        setResistance(6_000_000f);
        setStepSound(soundTypeGlass);
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess w, int x, int y, int z, Entity entity) {
        return false;
    }

    @Override
    public int getMobilityFlag() {
        return 2;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderBlockPass() {
        return 1;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {}
}
