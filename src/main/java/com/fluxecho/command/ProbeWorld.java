package com.fluxecho.command;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.fluxecho.campus.Builder;
import com.fluxecho.campus.PartBlocks;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.probe.ProbeFrame;
import com.fluxecho.logic.probe.ProbeMarker;
import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.shape.FormCodes;
import com.fluxecho.logic.shape.SkinBlocks;
import com.fluxecho.skin.SkinModule;

/**
 * The world side of the operators' check-up rigs (0.17.0, refactor FINAL F06): where an operator stands, whether a
 * rig's box is free of block entities and campus parts (a rig never overwrites a chest, a machine or a building),
 * setting its blocks quietly (our blocks do not make the multiblocks round them look again while a rig goes up, as
 * {@link Builder#QUIET}), planting and finding its marker sign ({@link ProbeMarker}).
 */
final class ProbeWorld {

    /** The lowest and highest y a rig may touch. */
    static final int MIN_Y = 1, MAX_Y = 255;

    private ProbeWorld() {}

    /** The frame of an operator: the block their feet are in, the way they face. */
    static ProbeFrame frame(EntityPlayer p) {
        return ProbeFrame.facing(
            MathHelper.floor_double(p.posX),
            MathHelper.floor_double(p.boundingBox.minY + 0.01),
            MathHelper.floor_double(p.posZ),
            p.rotationYaw);
    }

    /** A world box {x0, y0, z0, x1, y1, z1} of a frame's box {along0, right0, up0, along1, right1, up1}. */
    static int[] box(ProbeFrame f, int[] v) {
        int[] a = f.at(v[0], v[1], v[2]), b = f.at(v[3], v[4], v[5]);
        return new int[] { Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]), Math.max(a[0], b[0]),
            Math.max(a[1], b[1]), Math.max(a[2], b[2]) };
    }

    /** Whether a box lies within the heights a rig may touch. */
    static boolean fits(int[] box) {
        return box[1] >= MIN_Y && box[4] <= MAX_Y;
    }

    /**
     * The first cell of a box a rig must not overwrite, or null when there is none: a block entity (a chest, a machine;
     * not our own marker signs, so a rig built again replaces its own), or a part of a campus ({@link #cityPart}), so a
     * rig set out in a city never takes a building's wall or a plaza's floor away.
     */
    static int[] occupied(World w, int[] box) {
        for (Object o : w.loadedTileEntityList) {
            TileEntity te = (TileEntity) o;
            if (te.isInvalid() || te.xCoord < box[0]
                || te.xCoord > box[3]
                || te.yCoord < box[1]
                || te.yCoord > box[4]
                || te.zCoord < box[2]
                || te.zCoord > box[5]) continue;
            if (te instanceof TileEntitySign s && ProbeMarker.parse(s.signText) != null) continue;
            return new int[] { te.xCoord, te.yCoord, te.zCoord };
        }
        // a block entity in a chunk the list does not hold yet, and the campus parts: ask every cell
        for (int x = box[0]; x <= box[3]; x++)
            for (int y = box[1]; y <= box[4]; y++) for (int z = box[2]; z <= box[5]; z++) {
                Block b = w.getBlock(x, y, z);
                if (b == null) continue;
                int meta = w.getBlockMetadata(x, y, z);
                if (b.hasTileEntity(meta) && b != Blocks.standing_sign || cityPart(b, meta))
                    return new int[] { x, y, z };
            }
        return null;
    }

    /**
     * Whether a block is a part of a campus ({@link PartBlocks#code}: the frame, the deck and the fittings, the cores,
     * the supply port and the recycle bin), which a rig never overwrites. The new material families and the planting
     * bed are the rigs' own blocks, which a rig built again or taken down replaces: in 0.17.0 no plan lays them yet
     * (once a building's plan does, 0.19 on, the rigs have to tell their own cells from the city's).
     */
    static boolean cityPart(Block b, int meta) {
        int code = PartBlocks.code(b, meta);
        return code >= 0 && FormCodes.family(code) == null && code != Parts.fitting(Parts.F_BED);
    }

    /** The block of a family's shape number: its whole-block id or its shape-block id. */
    static Block skin(Family f, int number) {
        return SkinModule.block(f, SkinBlocks.inWhole(f, number));
    }

    /** Sets a block, telling the clients and the neighbours. */
    static void set(World w, int[] at, Block b, int meta) {
        if (w.getBlock(at[0], at[1], at[2]) == b && w.getBlockMetadata(at[0], at[1], at[2]) == meta) return;
        w.setBlock(at[0], at[1], at[2], b, meta, 3);
    }

    /** Runs a rig's edit with the builder's quiet flag set, as the builder does. */
    static void quietly(Runnable edit) {
        boolean was = Builder.QUIET;
        Builder.QUIET = true;
        try {
            edit.run();
        } finally {
            Builder.QUIET = was;
        }
    }

    /** Plants a rig's marker sign. */
    static void sign(World w, ProbeMarker.Kind kind, ProbeFrame f, String label, String data) {
        int[] at = ProbeMarker.signAt(kind, f);
        w.setBlock(at[0], at[1], at[2], Blocks.standing_sign, ProbeMarker.signMeta(f), 3);
        TileEntity te = w.getTileEntity(at[0], at[1], at[2]);
        if (te instanceof TileEntitySign s) {
            String[] lines = ProbeMarker.lines(kind, label, data);
            System.arraycopy(lines, 0, s.signText, 0, 4);
            s.markDirty();
            w.markBlockForUpdate(at[0], at[1], at[2]);
        }
    }

    /** The nearest marker sign of a rig of {@code kind} within {@code range} of a point, or null. */
    static TileEntitySign marker(World w, ProbeMarker.Kind kind, double x, double y, double z, double range) {
        TileEntitySign best = null;
        double bestD = range * range;
        for (Object o : w.loadedTileEntityList) {
            if (!(o instanceof TileEntitySign s) || s.isInvalid() || ProbeMarker.parse(s.signText) != kind) continue;
            if (w.getBlock(s.xCoord, s.yCoord, s.zCoord) != Blocks.standing_sign) continue;
            double dx = s.xCoord + 0.5 - x, dy = s.yCoord + 0.5 - y, dz = s.zCoord + 0.5 - z,
                d = dx * dx + dy * dy + dz * dz;
            if (d <= bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /** The frame of the rig a marker sign marks. */
    static ProbeFrame frameOf(World w, TileEntitySign s, ProbeMarker.Kind kind) {
        return ProbeMarker.frame(kind, s.xCoord, s.yCoord, s.zCoord, w.getBlockMetadata(s.xCoord, s.yCoord, s.zCoord));
    }

    /** Removes a marker sign. */
    static void unsign(World w, TileEntitySign s) {
        w.setBlockToAir(s.xCoord, s.yCoord, s.zCoord);
    }

    /** "x, y, z". */
    static String at(int[] p) {
        return p[0] + ", " + p[1] + ", " + p[2];
    }
}
