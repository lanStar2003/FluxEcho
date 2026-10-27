package com.fluxecho.campus.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.fluxecho.campus.BlockFitting;
import com.fluxecho.client.shape.BedDraw;
import com.fluxecho.client.skin.ClientSkin;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.skin.LampRule;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the campus fittings into the chunk mesh, each in its own shape: the post between two shelf units a slim
 * column, the plinth and the crown whole cubes, the gallery rail a baluster with a glass panel reaching to each
 * neighbour it joins, the stair tread a half slab, the glazing a cut-out glass cube, the hologram pedestal a short
 * block; and (0.11.0) the city's sample socket a slim column with a recess in each side face, rimmed in cyan, that the
 * sample floats in (drawn after the world by the module), and the basin rim a 10/16 slab whose lit lip runs to every
 * neighbouring rim like a rail's arms, so a planter's bed or a pond is ringed by one line of light. Each part is drawn
 * with vanilla lighting, then its light line again at full brightness with smooth lighting off, only on the sides
 * where it can be seen: a post's and a unit's line only where no wall, shelf or post stands against it, a tread's nose
 * only where the stair is open. That keeps the lines lit under a shader pack.
 * <p>
 * 0.17.0 (refactor §5.6): every fitting that glows is a lamp window of the old building it belongs to. Its line follows
 * the client's box table: left out while the building's lamps are dark (by day, asleep, without power), drawn in the
 * building's accent while they are lit; a fitting outside every building of the table glows as before.
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
    /**
     * The socket (0.11.0): a column 2..14 sixteenths across, its recess on each side 5..11 across and 11..15 high
     * ({@link ModuleLook#RECESS_LOW} to {@link ModuleLook#RECESS_HIGH}: over a planter's basin rim and its lip, so the
     * sample shows), two sixteenths deep. The parts, in sixteenths: a foot and a head over the whole column, the core
     * between them up to the recesses' backs, and a post in each corner beside the recesses.
     */
    private static final double[][] SOCKET = { { 2, 0, 2, 14, 11, 14 }, { 2, 15, 2, 14, 16, 14 },
        { 4, 11, 4, 12, 15, 12 }, { 2, 11, 2, 5, 15, 5 }, { 11, 11, 2, 14, 15, 5 }, { 2, 11, 11, 5, 15, 14 },
        { 11, 11, 11, 14, 15, 14 } };
    private static final double SOCKET_MIN = 2 / 16.0, SOCKET_MAX = 14 / 16.0;
    /** The basin rim (0.11.0): its slab's top, and its lip: two sixteenths high, four wide, along the middle. */
    private static final double BASIN_TOP = ModuleLook.BASIN_TOP, LIP_TOP = ModuleLook.LIP_TOP,
        LIP_MIN = ModuleLook.LIP_MIN, LIP_MAX = ModuleLook.LIP_MAX;

    private final int id;

    public FittingRender(int id) {
        this.id = id;
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess w, int x, int y, int z, Block block, int modelId, RenderBlocks r) {
        if (!(block instanceof BlockFitting fit)) return false;
        int meta = w.getBlockMetadata(x, y, z);
        // what its glow is this time: an old building's lamp follows the box table (refactor §5.6)
        int c = ClientSkin.glow(w, x, y, z, Parts.fitting(meta));
        boolean drawn;
        switch (meta) {
            case Parts.F_POST:
                drawn = post(w, x, y, z, fit, meta, r, c);
                break;
            case Parts.F_RAIL:
                drawn = rail(w, x, y, z, fit, meta, r, c);
                break;
            case Parts.F_TREAD:
                drawn = tread(w, x, y, z, fit, meta, r, c);
                break;
            case Parts.F_PEDESTAL:
                drawn = pedestal(x, y, z, fit, meta, r, c);
                break;
            case Parts.F_SOCKET:
                drawn = socket(x, y, z, fit, meta, r, c);
                break;
            case Parts.F_BASIN:
                drawn = basin(w, x, y, z, fit, meta, r, c);
                break;
            case Parts.F_LIFT:
                drawn = lift(w, x, y, z, fit, meta, r, c);
                break;
            case Parts.F_BED:
                // the planting bed (0.17.0) is drawn with the new families' textures
                drawn = BedDraw.draw(w, x, y, z, fit, r);
                break;
            default:
                // the plinth, the crown, the glazing (whose shouldSideBeRendered hides the faces between two panes)
                drawn = cube(w, x, y, z, fit, meta, r, c);
        }
        r.setRenderBounds(0, 0, 0, 1, 1, 1);
        return drawn;
    }

    /** The upright between two units: a column with a lit seam up each side that is open to view. */
    private static boolean post(IBlockAccess w, int x, int y, int z, BlockFitting fit, int meta, RenderBlocks r,
        int c) {
        boolean drawn = PartDraw.box(r, fit, x, y, z, POST_MIN, 0, POST_MIN, POST_MAX, 1, POST_MAX);
        boolean[] lit = new boolean[6];
        for (int s = 2; s < 6; s++) lit[s] = open(w, x, y, z, s);
        return overlay(c, r, fit, x, y, z, meta, lit, POST_MIN, 0, POST_MIN, POST_MAX, 1, POST_MAX) || drawn;
    }

    /** A whole cube; the plinth's kick light and the crown's index strip on the sides that are open to view. */
    private static boolean cube(IBlockAccess w, int x, int y, int z, BlockFitting fit, int meta, RenderBlocks r,
        int c) {
        boolean drawn = PartDraw.box(r, fit, x, y, z, 0, 0, 0, 1, 1, 1);
        boolean[] lit = new boolean[6];
        for (int s = 2; s < 6; s++)
            lit[s] = (r.renderAllFaces || PartDraw.shows(w, x, y, z, fit, s)) && open(w, x, y, z, s);
        return overlay(c, r, fit, x, y, z, meta, lit, 0, 0, 0, 1, 1, 1) || drawn;
    }

    /**
     * The light-lift pad (0.12.0): a whole steel cube whose top is the lit iris of the shaft, at full brightness when
     * nothing covers it; its sides are plain steel.
     */
    private static boolean lift(IBlockAccess w, int x, int y, int z, BlockFitting fit, int meta, RenderBlocks r,
        int c) {
        boolean drawn = PartDraw.box(r, fit, x, y, z, 0, 0, 0, 1, 1, 1);
        boolean[] lit = new boolean[6];
        lit[1] = r.renderAllFaces || PartDraw.shows(w, x, y, z, fit, 1);
        return overlay(c, r, fit, x, y, z, meta, lit, 0, 0, 0, 1, 1, 1) || drawn;
    }

    /**
     * A gallery rail, like a glass pane: a steel baluster, and a panel to every side the block's
     * {@link BlockFitting#railArm} joins (another rail, a post, an opaque cube), so the drawing matches what the player
     * bumps into. The handrail's light line runs along both faces of each panel and round the baluster. Every face is
     * drawn: most of them lie inside the block, where no neighbour may hide them.
     */
    private static boolean rail(IBlockAccess w, int x, int y, int z, BlockFitting fit, int meta, RenderBlocks r,
        int c) {
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
        overlay(c, r, fit, x, y, z, meta, round, BALUSTER_MIN, 0, BALUSTER_MIN, BALUSTER_MAX, 1, BALUSTER_MAX);
        for (int s = 2; s < 6; s++) if (arm[s]) {
            double[] b = arm(s);
            // the panel's two broad faces: east and west for a panel running north-south, and the other way round
            boolean[] faces = s < 4 ? new boolean[] { false, false, false, false, true, true }
                : new boolean[] { false, false, true, true, false, false };
            overlay(c, r, fit, x, y, z, meta, faces, b[0], b[1], b[2], b[3], b[4], b[5]);
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
    private static boolean tread(IBlockAccess w, int x, int y, int z, BlockFitting fit, int meta, RenderBlocks r,
        int c) {
        boolean drawn = PartDraw.box(r, fit, x, y, z, 0, 0, 0, 1, TREAD_TOP, 1);
        boolean[] lit = new boolean[6];
        for (int s = 2; s < 6; s++) {
            int[] o = PartDraw.OFFSETS[s];
            int nx = x + o[0], nz = z + o[2];
            lit[s] = (r.renderAllFaces || PartDraw.shows(w, x, y, z, fit, s)) && !Neighbours.solid(w, nx, y, nz)
                && !Neighbours.fitting(w, nx, y, nz, Parts.F_TREAD);
        }
        return overlay(c, r, fit, x, y, z, meta, lit, 0, 0, 0, 1, TREAD_TOP, 1) || drawn;
    }

    /** The hologram pedestal: a short block whose collar's ring of light runs round all four sides. */
    private static boolean pedestal(int x, int y, int z, BlockFitting fit, int meta, RenderBlocks r, int c) {
        boolean drawn = PartDraw
            .box(r, fit, x, y, z, PEDESTAL_MIN, 0, PEDESTAL_MIN, PEDESTAL_MAX, PEDESTAL_TOP, PEDESTAL_MAX);
        boolean[] round = { false, false, true, true, true, true };
        return overlay(
            c,
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

    /**
     * The sample socket: a slim column whose four side faces each have a recess the sample floats in, the recess's
     * rim lit on every side (the rim lies on the column's face round the opening, so the overlay is cut out over the
     * opening itself). Every face of its parts is drawn: the recesses' floors and ceilings lie inside the block,
     * where the whole column's rule (a face at its top or foot is hidden against an opaque block) must not hide them.
     */
    private static boolean socket(int x, int y, int z, BlockFitting fit, int meta, RenderBlocks r, int c) {
        for (double[] b : SOCKET)
            PartDraw.inner(r, fit, x, y, z, null, b[0] / 16, b[1] / 16, b[2] / 16, b[3] / 16, b[4] / 16, b[5] / 16);
        boolean[] round = { false, false, true, true, true, true };
        overlay(c, r, fit, x, y, z, meta, round, SOCKET_MIN, 0, SOCKET_MIN, SOCKET_MAX, 1, SOCKET_MAX);
        return true;
    }

    /**
     * The basin rim: a 10/16 slab with a lip along its middle, two sixteenths high, that runs out to every side where
     * another basin rim stands at the same height (as a rail's arms do), its top and its sides lit; a rim alone shows
     * only the lip's knot in its middle.
     */
    private static boolean basin(IBlockAccess w, int x, int y, int z, BlockFitting fit, int meta, RenderBlocks r,
        int c) {
        PartDraw.box(r, fit, x, y, z, 0, 0, 0, 1, BASIN_TOP, 1);
        boolean[] arm = new boolean[6];
        for (int s = 2; s < 6; s++) {
            int[] o = PartDraw.OFFSETS[s];
            arm[s] = Neighbours.fitting(w, x + o[0], y, z + o[2], Parts.F_BASIN);
        }
        double[][] lip = lip(arm);
        for (double[] b : lip) PartDraw.inner(r, fit, x, y, z, null, b[0], b[1], b[2], b[3], b[4], b[5]);
        for (double[] b : lip) {
            boolean[] lit = { false, true, true, true, true, true };
            // an arm's end lies against the next rim's arm: no light there
            for (int s = 2; s < 6; s++) if (arm[s] && touches(b, s)) lit[s] = false;
            overlay(c, r, fit, x, y, z, meta, lit, b[0], b[1], b[2], b[3], b[4], b[5]);
        }
        return true;
    }

    /** The boxes of a basin rim's lip: the knot in the middle and an arm to each side that joins. */
    private static double[][] lip(boolean[] arm) {
        List<double[]> out = new ArrayList<>();
        out.add(new double[] { LIP_MIN, BASIN_TOP, LIP_MIN, LIP_MAX, LIP_TOP, LIP_MAX });
        if (arm[2]) out.add(new double[] { LIP_MIN, BASIN_TOP, 0, LIP_MAX, LIP_TOP, LIP_MIN });
        if (arm[3]) out.add(new double[] { LIP_MIN, BASIN_TOP, LIP_MAX, LIP_MAX, LIP_TOP, 1 });
        if (arm[4]) out.add(new double[] { 0, BASIN_TOP, LIP_MIN, LIP_MIN, LIP_TOP, LIP_MAX });
        if (arm[5]) out.add(new double[] { LIP_MAX, BASIN_TOP, LIP_MIN, 1, LIP_TOP, LIP_MAX });
        return out.toArray(new double[0][]);
    }

    /** Whether a box reaches the block's edge on side s (2 to 5). */
    private static boolean touches(double[] b, int s) {
        switch (s) {
            case 2:
                return b[2] <= 0;
            case 3:
                return b[5] >= 1;
            case 4:
                return b[0] <= 0;
            default:
                return b[3] >= 1;
        }
    }

    /** Whether the side s of a unit part is open to view (see {@link Neighbours#open}). */
    private static boolean open(IBlockAccess w, int x, int y, int z, int s) {
        int[] o = PartDraw.OFFSETS[s];
        return Neighbours.open(w, x + o[0], y + o[1], z + o[2]);
    }

    /**
     * Draws the meta's glow overlay on the chosen sides of a box, at full brightness with smooth lighting off, in the
     * colour the box table gives ({@link ClientSkin#glow}: white as 0.16 drew it, an old building's accent for its lit
     * lamps). Nothing while the breaking animation overrides the texture, when the meta does not glow, or when it is an
     * unlit lamp.
     */
    private static boolean overlay(int colour, RenderBlocks r, BlockFitting fit, int x, int y, int z, int meta,
        boolean[] sides, double x0, double y0, double z0, double x1, double y1, double z1) {
        if (r.hasOverrideBlockTexture() || colour == LampRule.DARK) return false;
        boolean ao = r.enableAO, lit = false;
        for (int s = 0; s < 6; s++) {
            if (!sides[s]) continue;
            IIcon g = fit.glow(meta, s);
            if (g == null) continue;
            if (!lit) {
                PartDraw.glowOn(r);
                // a lit lamp of an old building in its accent
                if (colour >= 0) Tessellator.instance.setColorOpaque_I(colour);
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
            case Parts.F_SOCKET:
                for (double[] b : SOCKET) PartDraw
                    .inventoryBox(r, fit, own, b[0] / 16, b[1] / 16, b[2] / 16, b[3] / 16, b[4] / 16, b[5] / 16);
                PartDraw.inventoryBox(r, fit, round, SOCKET_MIN, 0, SOCKET_MIN, SOCKET_MAX, 1, SOCKET_MAX);
                break;
            case Parts.F_BASIN: {
                // a straight piece running east-west, as it stands between two neighbours
                PartDraw.inventoryBox(r, fit, own, 0, 0, 0, 1, BASIN_TOP, 1);
                PartDraw.Icons lit = s -> s >= 1 ? fit.glow(meta, s) : null;
                for (double[] b : lip(new boolean[] { false, false, false, false, true, true })) {
                    PartDraw.inventoryBox(r, fit, own, b[0], b[1], b[2], b[3], b[4], b[5]);
                    PartDraw.inventoryBox(r, fit, lit, b[0], b[1], b[2], b[3], b[4], b[5]);
                }
                break;
            }
            case Parts.F_BED:
                BedDraw.inventory(fit, r);
                break;
            case Parts.F_LIFT:
                // the pad's iris on top
                PartDraw.inventoryBox(r, fit, own, 0, 0, 0, 1, 1, 1);
                PartDraw.inventoryBox(r, fit, s -> s == 1 ? fit.glow(meta, s) : null, 0, 0, 0, 1, 1, 1);
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
