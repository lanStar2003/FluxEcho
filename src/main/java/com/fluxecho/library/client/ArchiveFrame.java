package com.fluxecho.library.client;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.library.TileLibrary;
import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.Blueprint;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Echo Archive's own frame ({@link ArchiveShape}: {@code x} across, {@code y} up, {@code z} back from the front),
 * placed where one library stands and turned the way it faces, relative to the camera. The Archive's effects are worked
 * out in the shape's coordinates, where its bookcases, pedestals and desk are listed, and this maps them into the
 * world the way {@link Blueprint#point} maps the world into them: the cell {@code (x, y, z)} of the shape is the block
 * between the points {@code (x, y, z)} and {@code (x + 1, y + 1, z + 1)}. The frame is mirrored relative to the world
 * (see {@link ArchiveShape}), which is harmless for drawing: it is only a change of coordinates.
 * <p>
 * It also draws the thin lines and soft bands the effects are made of into one open tessellator batch (one draw for
 * many lines, where {@code Shapes.string} draws each line on its own), as camera-facing bands at least about a pixel
 * wide, with the viewer's normal on every vertex as {@code Shapes} gives it. Positions are relative to
 * {@code RenderManager.renderPos*}, so the light gates can draw them again from their far side. Made once a draw; it
 * holds nothing between frames.
 */
@SideOnly(Side.CLIENT)
final class ArchiveFrame {

    /** The least width of a line per block of distance (about a pixel), and its longest piece. */
    private static final double PIXEL = 0.0022, PIECE = 8;
    /** The normal towards the viewer, as {@code Shapes.begin} works it out, for the batches of this draw. */
    private static float nx, ny = 1, nz;

    /** The library's front. */
    final int fx, fz;
    /** The middle of the controller's block, relative to the camera, and the same point in the shape. */
    private final double cx, cy, cz, midA, midY, midB;

    ArchiveFrame(TileLibrary l) {
        ForgeDirection f = l.front();
        fx = f.offsetX;
        fz = f.offsetZ;
        Blueprint bp = ArchiveShape.BLUEPRINT;
        midA = bp.ctrlA + 0.5;
        midB = bp.ctrlC + 0.5;
        midY = bp.height() - 1 - bp.ctrlB;
        cx = l.xCoord + 0.5 - RenderManager.renderPosX;
        cy = l.yCoord - RenderManager.renderPosY;
        cz = l.zCoord + 0.5 - RenderManager.renderPosZ;
        viewNormal();
    }

    /** The camera-relative world x of the shape's point {@code (lx, *, lz)}. */
    double x(double lx, double lz) {
        // the inverse of Blueprint.point: across runs along (fz, -fx), back from the front along (-fx, -fz)
        return cx + (lx - midA) * fz - (lz - midB) * fx;
    }

    /** The camera-relative world y of the shape's height {@code ly}. */
    double y(double ly) {
        return cy + ly - midY;
    }

    /** The camera-relative world z of the shape's point {@code (lx, *, lz)}. */
    double z(double lx, double lz) {
        return cz - (lx - midA) * fx - (lz - midB) * fz;
    }

    /** Adds the shape's point as a vertex of the open batch. */
    void vertex(Tessellator t, double lx, double ly, double lz) {
        t.addVertex(x(lx, lz), y(ly), z(lx, lz));
    }

    /** A thin line between two points of the shape; returns the quads added. */
    int line(Tessellator t, double lx0, double ly0, double lz0, double lx1, double ly1, double lz1, double w) {
        return lineRel(t, x(lx0, lz0), y(ly0), z(lx0, lz0), x(lx1, lz1), y(ly1), z(lx1, lz1), w);
    }

    /** The twelve edges of a box of the shape as thin lines; returns the quads added. */
    int boxEdges(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1, double w) {
        int q = 0;
        for (int k = 0; k < 2; k++) {
            double y = k == 0 ? y0 : y1;
            q += line(t, x0, y, z0, x1, y, z0, w);
            q += line(t, x1, y, z0, x1, y, z1, w);
            q += line(t, x1, y, z1, x0, y, z1, w);
            q += line(t, x0, y, z1, x0, y, z0, w);
        }
        q += line(t, x0, y0, z0, x0, y1, z0, w);
        q += line(t, x1, y0, z0, x1, y1, z0, w);
        q += line(t, x1, y0, z1, x1, y1, z1, w);
        q += line(t, x0, y0, z1, x0, y1, z1, w);
        return q;
    }

    /**
     * A soft upright band of light between two points of the shape at the same heights: brightest along {@code yc},
     * fading out to nothing {@code half} above and below; {@code a0} at the first end, {@code a1} at the second.
     */
    void softBand(Tessellator t, double lx0, double lz0, double lx1, double lz1, double yc, double half, int rgb,
        float a0, float a1) {
        int i0 = alpha255(a0), i1 = alpha255(a1);
        for (int k = 0; k < 2; k++) {
            double yo = k == 0 ? yc - half : yc + half;
            t.setColorRGBA_I(rgb, 0);
            vertex(t, lx0, yo, lz0);
            vertex(t, lx1, yo, lz1);
            t.setColorRGBA_I(rgb, i1);
            vertex(t, lx1, yc, lz1);
            t.setColorRGBA_I(rgb, i0);
            vertex(t, lx0, yc, lz0);
        }
    }

    /**
     * Where the camera is in the shape's coordinates (fractional; it may lie outside the shape), as
     * {@link ArchiveShape#inside} takes it.
     */
    static double[] camera(TileLibrary l) {
        return l.localPoint(RenderManager.renderPosX, RenderManager.renderPosY, RenderManager.renderPosZ);
    }

    /** How far a point of the shape is from the shape's box (0 inside it), in blocks. */
    static double boxDistance(double[] p) {
        double dx = Math.max(0, Math.max(-p[0], p[0] - ArchiveShape.WIDTH));
        double dy = Math.max(0, Math.max(-p[1], p[1] - ArchiveShape.HEIGHT));
        double dz = Math.max(0, Math.max(-p[2], p[2] - ArchiveShape.DEPTH));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---- batches of camera-relative quads

    /** The viewer's direction reversed, the normal {@code Shapes} gives effect geometry. */
    private static void viewNormal() {
        double yaw = Math.toRadians(RenderManager.instance.playerViewY),
            pitch = Math.toRadians(RenderManager.instance.playerViewX);
        nx = (float) (Math.sin(yaw) * Math.cos(pitch));
        ny = (float) Math.sin(pitch);
        nz = (float) (-Math.cos(yaw) * Math.cos(pitch));
    }

    /** Opens a batch of untextured quads facing the viewer (inside a {@code Shapes.begin}); the caller draws it. */
    static Tessellator start() {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setNormal(nx, ny, nz);
        return t;
    }

    /** The viewer's normal on the open batch again (a textured batch sets its own before its vertices). */
    static void normal(Tessellator t) {
        t.setNormal(nx, ny, nz);
    }

    /**
     * A thin line between two camera-relative points as bands turned to the camera, cut into pieces of at most
     * {@link #PIECE} blocks so each piece can be as wide as about a pixel where it is; returns the quads added.
     */
    static int lineRel(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1, double w) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) return 0;
        int pieces = Math.max(1, (int) Math.ceil(len / PIECE));
        for (int k = 0; k < pieces; k++) {
            double f0 = k / (double) pieces, f1 = (k + 1) / (double) pieces;
            piece(t, x0 + dx * f0, y0 + dy * f0, z0 + dz * f0, x0 + dx * f1, y0 + dy * f1, z0 + dz * f1, w);
        }
        return pieces;
    }

    private static void piece(Tessellator t, double ax, double ay, double az, double bx, double by, double bz,
        double w) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double mx = (ax + bx) / 2, my = (ay + by) / 2, mz = (az + bz) / 2;
        // sideways: across the line and across the line of sight
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-9) return;
        double width = Math.max(w, Math.sqrt(mx * mx + my * my + mz * mz) * PIXEL);
        double k = width / 2 / len;
        sx *= k;
        sy *= k;
        sz *= k;
        t.addVertex(ax - sx, ay - sy, az - sz);
        t.addVertex(ax + sx, ay + sy, az + sz);
        t.addVertex(bx + sx, by + sy, bz + sz);
        t.addVertex(bx - sx, by - sy, bz - sz);
    }

    static int alpha255(float a) {
        return (int) (Math.max(0f, Math.min(1f, a)) * 255);
    }
}
