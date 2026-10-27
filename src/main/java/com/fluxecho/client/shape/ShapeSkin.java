package com.fluxecho.client.shape;

import net.minecraft.world.IBlockAccess;

import com.fluxecho.logic.skin.SkinPalette;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Where {@link ShapeRender} asks the box table ({@code SkinTable}, style FINAL §3.9) how a cell of the new families
 * looks beyond its block: the building's accent, whether its glows are awake, whether its lamp windows are lit, the
 * keel's colour. The table belongs to the box-table work ({@code client/skin}); it plugs itself in with
 * {@link ShapeRender#skin}. Until then, and for every cell outside every box, the answer is
 * {@link Look#DEFAULT}: the material as it is, the glows awake, the lamps dark, the keel cyan (style FINAL §3.9.3, the
 * row for an entry that has not arrived). The renderer may ask from the chunk builder's threads, so an implementation
 * reads an immutable snapshot.
 */
@SideOnly(Side.CLIENT)
@FunctionalInterface
public interface ShapeSkin {

    /** No table: every cell looks {@link Look#DEFAULT}. */
    ShapeSkin NONE = (w, x, y, z) -> null;

    /** The look of the box the cell lies in, or null for the default. */
    Look at(IBlockAccess w, int x, int y, int z);

    /** What the box table says about a cell, as the renderer uses it. Immutable. */
    final class Look {

        /**
         * Outside every box, or before the table arrives: no accent (light seams and lamps in warm white), glows awake,
         * lamps dark (by day), the keel in the nexus cyan.
         */
        public static final Look DEFAULT = new Look(SkinPalette.NONE, true, false, SkinPalette.NEXUS_CYAN);

        /** The building's accent ({@code SkinPalette.accent}), or {@link SkinPalette#NONE}. */
        public final int accent;
        /** Whether the glows (keel, light seams, noses, lips) are lit; false for a dormant building (§3.9.4). */
        public final boolean awake;
        /** Whether the lamp windows are lit (G6: formed or holding, powered, at night). */
        public final boolean lamps;
        /** The keel's edge colour: the nexus cyan, warm white once the city is a memorial (§6.7). */
        public final int keel;

        public Look(int accent, boolean awake, boolean lamps, int keel) {
            this.accent = accent;
            this.awake = awake;
            this.lamps = lamps;
            this.keel = keel;
        }

        /** The colour a light seam's or a lamp's edge is tinted: the accent, warm white without one. */
        public int light() {
            return accent == SkinPalette.NONE ? SkinPalette.WARM_WHITE : accent;
        }
    }
}
