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

import com.fluxecho.logic.RoomPlan;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The shell of a light gate's room: floor, walls, roof, pillars and the lights in them, one meta each. Made with the
 * room and never broken: not by hand, explosions, withers or pistons. (Registered as {@code interior_floor}, the
 * 0.8.1 prototype's plot floor, whose metas 0 and 1 it keeps.)
 */
public class BlockInteriorShell extends Block {

    public static final int FLOOR = 0, RIM = 1, WALL = 2, CEILING = 3, PILLAR = 4, LIGHT = 5, TRIM = 6;
    private static final String[] TEXTURES = { "floor", "rim", "wall", "ceiling", "pillar", "light", "trim" };

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public BlockInteriorShell() {
        super(Material.rock);
        setBlockName("fluxecho.interior_shell");
        setBlockUnbreakable();
        setResistance(6_000_000f);
        setStepSound(soundTypeStone);
    }

    /** The meta for a cell of a room's plan; glass and air are not this block. */
    public static int meta(RoomPlan.Cell c) {
        switch (c) {
            case RIM:
                return RIM;
            case WALL:
                return WALL;
            case CEILING:
                return CEILING;
            case PILLAR:
                return PILLAR;
            case LIGHT:
                return LIGHT;
            case TRIM:
                return TRIM;
            default:
                return FLOOR;
        }
    }

    @Override
    public int getLightValue(IBlockAccess w, int x, int y, int z) {
        int m = w.getBlockMetadata(x, y, z);
        return m == LIGHT ? 15 : m == RIM || m == TRIM ? 12 : 0;
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
        icons = new IIcon[TEXTURES.length];
        for (int i = 0; i < TEXTURES.length; i++) icons[i] = r.registerIcon("fluxecho:gate/" + TEXTURES[i]);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int face, int meta) {
        return icons[meta >= 0 && meta < icons.length ? meta : 0];
    }
}
