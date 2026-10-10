package com.fluxecho.campus.client;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

import com.fluxecho.campus.BlockFitting;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.UnitLook;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * What the chunk-mesh renderers of the shelf units and the campus fittings need to know about a neighbouring block:
 * its {@link UnitLook.Kind}, and whether it is a whole solid cube a face pressed against it cannot be seen past. Only
 * blocks and metadata are read (no tile entity, no {@code Formed}), so it is safe on the chunk-building thread.
 */
@SideOnly(Side.CLIENT)
public final class Neighbours {

    private Neighbours() {}

    /**
     * The unit-look kind of the block at x, y, z. The shelf and the three unit fittings are tested before opacity, so a
     * shelf is {@link UnitLook.Kind#SHELF} although the frame is a whole cube; the frame's other whole cubes count as
     * opaque even though the frame block is not an opaque cube to vanilla.
     */
    public static UnitLook.Kind kind(IBlockAccess w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        if (b instanceof BlockFrame) {
            int m = w.getBlockMetadata(x, y, z);
            if (m == BlockFrame.SHELF) return UnitLook.Kind.SHELF;
            return BlockFrame.full(m) ? UnitLook.Kind.OPAQUE : UnitLook.Kind.OTHER;
        }
        if (b instanceof BlockFitting) {
            switch (w.getBlockMetadata(x, y, z)) {
                case Parts.F_POST:
                    return UnitLook.Kind.POST;
                case Parts.F_PLINTH:
                    return UnitLook.Kind.PLINTH;
                case Parts.F_CROWN:
                    return UnitLook.Kind.CROWN;
                default:
                    // rails, treads, glazing and pedestals are seen past
                    return UnitLook.Kind.OTHER;
            }
        }
        return b.isOpaqueCube() ? UnitLook.Kind.OPAQUE : UnitLook.Kind.OTHER;
    }

    /** Whether the block at x, y, z is a whole solid cube: an opaque cube, a whole frame part, a plinth or a crown. */
    public static boolean solid(IBlockAccess w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        if (b.isOpaqueCube()) return true;
        if (b instanceof BlockFrame) return BlockFrame.full(w.getBlockMetadata(x, y, z));
        if (b instanceof BlockFitting) {
            int m = w.getBlockMetadata(x, y, z);
            return m == Parts.F_PLINTH || m == Parts.F_CROWN;
        }
        return false;
    }

    /**
     * Whether a unit part's side towards the block at x, y, z is open to view, so its light line is drawn there: not
     * against a solid cube, a shelf or a post ({@link UnitLook#bookFace}).
     */
    public static boolean open(IBlockAccess w, int x, int y, int z) {
        return UnitLook.bookFace(kind(w, x, y, z)) && !solid(w, x, y, z);
    }

    /** Whether the block at x, y, z is a fitting of the given meta. */
    public static boolean fitting(IBlockAccess w, int x, int y, int z, int meta) {
        return w.getBlock(x, y, z) instanceof BlockFitting && w.getBlockMetadata(x, y, z) == meta;
    }
}
