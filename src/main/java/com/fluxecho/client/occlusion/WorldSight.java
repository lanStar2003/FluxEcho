package com.fluxecho.client.occlusion;

import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.Formed;
import com.fluxecho.logic.occlusion.SightCells;
import com.fluxecho.logic.shape.Form;
import com.fluxecho.logic.shape.ShapeGeo;
import com.fluxecho.skin.BlockSkin;
import com.fluxecho.skin.ShapeAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The client world as a line of sight meets it (style FINAL §8.4.1), for the occlusion gate: an opaque vanilla cube,
 * one of our whole-block shapes and our rounds, slopes, soffits and copings block the whole cell; a rib, column, half,
 * step or louvre of ours blocks only its sight boxes ({@code ShapeGeo.occluders}), so the eye sees between ribs and
 * through louvres; a window, a grate, glass, water, leaves, plants and every other block that is not an opaque cube
 * block nothing, and neither does a cell a formed multiblock hides ({@code Formed.HIDE}). A frame block blocks where it
 * is a full cube, as our shapes take it for a neighbour. The height of the ground (the coarse test) is the world's
 * height map, unknown where the chunk is not loaded.
 */
@SideOnly(Side.CLIENT)
final class WorldSight implements SightCells {

    private final World w;

    WorldSight(World w) {
        this.w = w;
    }

    @Override
    public int kind(int x, int y, int z) {
        if (y < 0 || y > 255) return CLEAR;
        Block b = w.getBlock(x, y, z);
        if (b == null || b.getMaterial() == Material.air) return CLEAR;
        if (b instanceof BlockSkin s) {
            if ((Formed.clientFlags(x, y, z) & Formed.HIDE) != 0) return CLEAR;
            Form f = s.form(w.getBlockMetadata(x, y, z));
            if (f.seeThrough()) return CLEAR;
            return f.closed() ? SOLID : BOXES;
        }
        if (b instanceof BlockFrame) {
            int meta = w.getBlockMetadata(x, y, z);
            return BlockFrame.full(meta) && (Formed.clientFlags(x, y, z) & Formed.HIDE) == 0 ? SOLID : CLEAR;
        }
        return b.isOpaqueCube() ? SOLID : CLEAR;
    }

    @Override
    public List<double[]> boxes(int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        if (!(b instanceof BlockSkin s)) return Collections.emptyList();
        Form f = s.form(w.getBlockMetadata(x, y, z));
        return ShapeGeo.occluders(f, ShapeAccess.nbhd(w, x, y, z, s.family, ShapeAccess.hidden(w)));
    }

    @Override
    public int height(int x, int z) {
        return w.blockExists(x, 0, z) ? w.getHeightValue(x, z) : Integer.MIN_VALUE;
    }
}
