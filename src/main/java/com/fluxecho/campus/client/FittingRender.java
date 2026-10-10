package com.fluxecho.campus.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.fluxecho.campus.BlockFitting;
import com.fluxecho.logic.Parts;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the campus fittings into the chunk mesh, each in its own shape: the post between two shelf units a slim
 * column, the plinth and the crown whole cubes, the gallery rail a baluster with a glass panel reaching to each
 * neighbour it joins, the stair tread a half slab, the glazing a cut-out glass cube, the hologram pedestal a short
 * block. Each part is drawn with vanilla lighting, then its light line again at full brightness with smooth lighting
 * off, only on the sides where it can be seen: a post's and a unit's line only where no wall, shelf or post stands
 * against it, a tread's nose only where the stair is open. That keeps the lines lit under a shader pack.
 */
@SideOnly(Side.CLIENT)
public final class FittingRender implements ISimpleBlockRenderingHandler {

    /** The post's column. */
    private static final double POST_MIN = 2 / 16.0, POST_MAX = 14 / 16.0;
    /** The rail's baluster, and the thickness of its glass panels. */
    private static final double BALUSTER_MIN = 6 / 16.0, BALUSTER_MAX = 10 / 16.0, PANE_MIN = 7 / 16.0,
        PANE_MAX = 9 / 16.0;
    private static final double TREAD_TOP = 0.5;
    private static final double PEDESTAL_MIN = 3 / 16.0, PEDESTAL_MAX = 13 / 16.0, PEDESTAL_TOP = 12 / 16.0;

    private final int id;

    public FittingRender(int id) {
        this.id = id;
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess w, int x, int y, int z, Block block, int modelId, RenderBlocks r) {
        if (!(block instanceof BlockFitting fit)) return false;
        int meta = w.getBlockMetadata(x, y, z);
        boolean drawn;
        switch (meta) {
            case Parts.F_POST:
                drawn = post(w, x, y, z, fit, meta, r);
                break;
            case Parts.F_RAIL:
                drawn = rail(w, x, y, z, fit, meta, r);
                break;
            case Parts.F_TREAD:
                drawn = tread(w, x, y, z, fit, meta, r);
                break;
            case Parts.F_PEDESTAL:
                drawn = pedestal(x, y, z, fit, meta, r);
                break;
            default:
                // the plinth, the crown, the glazing (whose shouldSideBeRendered hides the faces between two panes)
                drawn = cube(w, x, y, z, fit, meta, r);
        }
        r.setRenderBounds(0, 0, 0, 1, 1, 1);
        return drawn;
    }

    /** The upright between two units: a column with a lit seam up each side that is open to view. */
    private static boolean post(IBlockAccess w, int x, int y, int z, BlockFitting fit, int meta, RenderBlocks r) {
        boolean drawn = PartDraw.box(r, fit, x, y, z, POST_MIN, 0, POST_MIN, POST_MAX, 1, POST_MAX);
        boolean[] lit = new boolean[6];
        for (int s = 2; s < 6; s++) lit[s] = open(w, x, y, z, s);
        return overlay(r, fit, x, y, z, meta, lit, POST_MIN, 0, POST_MIN, POST_MAX, 1, POST_MAX) || drawn;
    }

    /** A whole cube; the plinth's kick light and the crown's index strip on the sides that are open to view. */
    private static boolean cube(IBlockAccess w, int x, int y, int z, BlockFitting fit, int meta, RenderBlocks r) {
        boolean drawn = PartDraw.box(r, fit, x, y, z, 0, 0, 0, 1, 1, 1);
        boolean[] lit = new boolean[6];
        for (int s = 2; s < 6; s++)
            lit[s] = (r.renderAllFaces || PartDraw.shows(w, x, y, z, fit, s)) && open(w, x, y, z, s);
        return overlay(r, fit, x, y, z, meta, lit, 0, 0, 0, 1, 1, 1) || drawn;
    }

    /**
     * A gallery rail, like a glass pane: a steel baluster, and a panel to every side the block's
     * {@link BlockFitting#railArm} joins (another rail, a post, an opaque cube), so the drawing matches what the player
     * bumps into. The handrail's light line runs along both faces of each panel and round the baluster. Every face is
     * drawn: most of them lie inside the block, where no neighbour may hide them.
     */
    private static boolean rail(IBlockAccess w, int x, int y, int z, BlockFitting fit, int meta, RenderBlocks r) {
        boolean[] arm = new boolean[6];
        for (int s = 2; s < 6; s++) arm[s] = BlockFitting.railArm(w, x, y, z, ForgeDirection.getOrientation(s));
        PartDraw.inner(
            r,
            fit,
            x,
            y,
            z,
            fit.icon(Parts.F_RAIL, 1),
            BALUSTER_MIN,
            0,
            BALUSTER_MIN,
            BALUSTER_MAX,
            1,
            BALUSTER_MAX);
        for (int s = 2; s < 6; s++) if (arm[s]) {
            double[] b = arm(s);
            PartDraw.inner(r, fit, x, y, z, null, b[0], b[1], b[2], b[3], b[4], b[5]);
        }
        boolean[] round = { false, false, true, true, true, true };
        overlay(r, fit, x, y, z, meta, round, BALUSTER_MIN, 0, BALUSTER_MIN, BALUSTER_MAX, 1, BALUSTER_MAX);
        for (int s = 2; s < 6; s++) if (arm[s]) {
            double[] b = arm(s);
            // the panel's two broad faces: east and west for a panel running north-south, and the other way round
            boolean[] faces = s < 4 ? new boolean[] { false, false, false, false, true, true }
                : new boolean[] { false, false, true, true, false, false };
            overlay(r, fit, x, y, z, meta, faces, b[0], b[1], b[2], b[3], b[4], b[5]);
        }
        return true;
    }

    /** The glass panel from the baluster to the block's edge towards side s. */
    private static double[] arm(int s) {
        switch (s) {
            case 2:
                return new double[] { PANE_MIN, 0, 0, PANE_MAX, 1, BALUSTER_MIN };
            case 3:
                return new double[] { PANE_MIN, 0, BALUSTER_MAX, PANE_MAX, 1, 1 };
            case 4:
                return new double[] { 0, 0, PANE_MIN, BALUSTER_MIN, 1, PANE_MAX };
            default:
                return new double[] { BALUSTER_MAX, 0, PANE_MIN, 1, 1, PANE_MAX };
        }
    }

    /** A stair tread: a half slab whose lit nose shows on every side the stair is open. */
    private static boolean tread(IBlockAccess w, int x, int y, int z, BlockFitting fit, int meta, RenderBlocks r) {
        boolean drawn = PartDraw.box(r, fit, x, y, z, 0, 0, 0, 1, TREAD_TOP, 1);
        boolean[] lit = new boolean[6];
        for (int s = 2; s < 6; s++) {
            int[] o = PartDraw.OFFSETS[s];
            int nx = x + o[0], nz = z + o[2];
            lit[s] = (r.renderAllFaces || PartDraw.shows(w, x, y, z, fit, s)) && !Neighbours.solid(w, nx, y, nz)
                && !Neighbours.fitting(w, nx, y, nz, Parts.F_TREAD);
        }
        return overlay(r, fit, x, y, z, meta, lit, 0, 0, 0, 1, TREAD_TOP, 1) || drawn;
    }

    /** The hologram pedestal: a short block whose collar's ring of light runs round all four sides. */
    private static boolean pedestal(int x, int y, int z, BlockFitting fit, int meta, RenderBlocks r) {
        boolean drawn = PartDraw
            .box(r, fit, x, y, z, PEDESTAL_MIN, 0, PEDESTAL_MIN, PEDESTAL_MAX, PEDESTAL_TOP, PEDESTAL_MAX);
        boolean[] round = { false, false, true, true, true, true };
        return overlay(
            r,
            fit,
            x,
            y,
            z,
            meta,
            round,
            PEDESTAL_MIN,
            0,
            PEDESTAL_MIN,
            PEDESTAL_MAX,
            PEDESTAL_TOP,
            PEDESTAL_MAX) || drawn;
    }

    /** Whether the side s of a unit part is open to view (see {@link Neighbours#open}). */
    private static boolean open(IBlockAccess w, int x, int y, int z, int s) {
        int[] o = PartDraw.OFFSETS[s];
        return Neighbours.open(w, x + o[0], y + o[1], z + o[2]);
    }

    /**
     * Draws the meta's glow overlay on the chosen sides of a box, at full brightness with smooth lighting off. Nothing
     * while the breaking animation overrides the texture, or when the meta does not glow.
     */
    private static boolean overlay(RenderBlocks r, BlockFitting fit, int x, int y, int z, int meta, boolean[] sides,
        double x0, double y0, double z0, double x1, double y1, double z1) {
        if (r.hasOverrideBlockTexture()) return false;
        boolean ao = r.enableAO, lit = false;
        for (int s = 0; s < 6; s++) {
            if (!sides[s]) continue;
            IIcon g = fit.glow(meta, s);
            if (g == null) continue;
            if (!lit) {
                PartDraw.glowOn(r);
                r.setRenderBounds(x0, y0, z0, x1, y1, z1);
                lit = true;
            }
            PartDraw.face(r, fit, x, y, z, s, g);
        }
        if (lit) PartDraw.glowOff(r, ao);
        return lit;
    }

    @Override
    public void renderInventoryBlock(Block block, int meta, int modelId, RenderBlocks r) {
        if (!(block instanceof BlockFitting fit)) return;
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        PartDraw.Icons own = s -> fit.icon(meta, s);
        PartDraw.Icons round = s -> s >= 2 ? fit.glow(meta, s) : null;
        switch (meta) {
            case Parts.F_POST:
                PartDraw.inventoryBox(r, fit, own, POST_MIN, 0, POST_MIN, POST_MAX, 1, POST_MAX);
                PartDraw.inventoryBox(r, fit, round, POST_MIN, 0, POST_MIN, POST_MAX, 1, POST_MAX);
                break;
            case Parts.F_RAIL: {
                // a straight piece running east-west, as it stands between two neighbours
                IIcon steel = fit.icon(Parts.F_RAIL, 1);
                PartDraw.Icons broad = s -> s == 2 || s == 3 ? fit.glow(meta, s) : null;
                PartDraw.inventoryBox(r, fit, s -> steel, BALUSTER_MIN, 0, BALUSTER_MIN, BALUSTER_MAX, 1, BALUSTER_MAX);
                PartDraw.inventoryBox(r, fit, round, BALUSTER_MIN, 0, BALUSTER_MIN, BALUSTER_MAX, 1, BALUSTER_MAX);
                for (int s = 4; s < 6; s++) {
                    double[] b = arm(s);
                    PartDraw.inventoryBox(r, fit, own, b[0], b[1], b[2], b[3], b[4], b[5]);
                    PartDraw.inventoryBox(r, fit, broad, b[0], b[1], b[2], b[3], b[4], b[5]);
                }
                break;
            }
            case Parts.F_TREAD:
                PartDraw.inventoryBox(r, fit, own, 0, 0, 0, 1, TREAD_TOP, 1);
                PartDraw.inventoryBox(r, fit, round, 0, 0, 0, 1, TREAD_TOP, 1);
                break;
            case Parts.F_PEDESTAL:
                PartDraw
                    .inventoryBox(r, fit, own, PEDESTAL_MIN, 0, PEDESTAL_MIN, PEDESTAL_MAX, PEDESTAL_TOP, PEDESTAL_MAX);
                PartDraw.inventoryBox(
                    r,
                    fit,
                    round,
                    PEDESTAL_MIN,
                    0,
                    PEDESTAL_MIN,
                    PEDESTAL_MAX,
                    PEDESTAL_TOP,
                    PEDESTAL_MAX);
                break;
            default:
                PartDraw.inventoryBox(r, fit, own, 0, 0, 0, 1, 1, 1);
                PartDraw.inventoryBox(r, fit, round, 0, 0, 0, 1, 1, 1);
        }
        t.draw();
        r.setRenderBounds(0, 0, 0, 1, 1, 1);
        GL11.glTranslatef(0.5f, 0.5f, 0.5f);
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return id;
    }
}
