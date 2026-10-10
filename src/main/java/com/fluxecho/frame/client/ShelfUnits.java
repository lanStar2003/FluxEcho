package com.fluxecho.frame.client;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.fluxecho.campus.client.Neighbours;
import com.fluxecho.campus.client.PartDraw;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.Formed;
import com.fluxecho.library.client.LibraryBooks;
import com.fluxecho.logic.UnitLook;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The echo shelf as a body of a shelf unit (blueprint 0.10.0, the Echo Archive): a column of one to three shelves
 * standing on a plinth under a crown is a bookcase, and each of its shelves is drawn as a compartment instead of a
 * plain block. Every side that is open to view ({@link UnitLook#bookFace}) is set back: a back panel three pixels in,
 * a board along the foot and the head, a thin side wall where the run of compartments ends, a corner post where two
 * open sides meet; and if the body holds a book ({@link LibraryBooks}), a few spines in its colour stand on the lower
 * board. A side that faces a post (the slit between the unit and the post shows it) is a plain side panel. Boards,
 * walls and panels share one even board texture. Shelves that are not in such a column (the 0.9.2 library hall) keep
 * their old look.
 * <p>
 * The look is decided from the neighbouring blocks only ({@link Neighbours}), as the chunk mesh may be built on another
 * thread. Every piece is drawn with vanilla lighting; the spines are tinted quads, lit like the shelf around them.
 */
@SideOnly(Side.CLIENT)
final class ShelfUnits {

    /**
     * The back panel of a compartment, its boards (also its side walls, corner posts and the side panels towards a
     * post) and the grey spine tinted per book; registered when the blocks atlas stitches.
     */
    static IIcon niche, board, spine;

    /** The most shelves one unit stacks between its plinth and its crown. */
    private static final int BODIES = 3;
    /** How far the back panel is set back, its thickness, the boards' and the side walls' thickness. */
    private static final double RECESS = 3 / 16.0, INSET = 4 / 16.0, BOARD = 1 / 16.0, JAMB = 1 / 16.0;
    private static final double PX = 1 / 16.0;
    /** The colour of the pages seen on the top of a book. */
    private static final float PAGE_R = 0.86f, PAGE_G = 0.82f, PAGE_B = 0.70f;

    private ShelfUnits() {}

    /** Draws the shelf at x, y, z as a unit body; false (nothing drawn) when it is not one. */
    static boolean draw(IBlockAccess w, int x, int y, int z, BlockFrame frame, RenderBlocks r) {
        if (!body(w, x, y, z)) return false;
        // open: the side shows a compartment; post: the side faces a post, and the slit between the unit and the post
        // shows it, so it is a plain board panel JAMB thick (not the loose shelf's painted books and light marks)
        boolean[] open = new boolean[6], post = new boolean[6];
        boolean any = false;
        for (int s = 2; s < 6; s++) {
            UnitLook.Kind k = sideKind(w, x, y, z, s);
            open[s] = UnitLook.bookFace(k);
            post[s] = k == UnitLook.Kind.POST;
            any |= open[s];
        }
        // walled in on every side: nothing would show the compartment
        if (!any) return false;

        double ox0 = open[4] ? INSET : 0, ox1 = open[5] ? 1 - INSET : 1;
        double oz0 = open[2] ? INSET : 0, oz1 = open[3] ? 1 - INSET : 1;
        double x0 = post[4] ? JAMB : ox0, x1 = post[5] ? 1 - JAMB : ox1;
        double z0 = post[2] ? JAMB : oz0, z1 = post[3] ? 1 - JAMB : oz1;
        PartDraw.box(r, frame, x, y, z, x0, 0, z0, x1, 1, z1);
        if (post[4]) PartDraw.inner(r, frame, x, y, z, board, 0, 0, oz0, JAMB, 1, oz1);
        if (post[5]) PartDraw.inner(r, frame, x, y, z, board, 1 - JAMB, 0, oz0, 1, 1, oz1);
        if (post[2]) PartDraw.inner(r, frame, x, y, z, board, x0, 0, 0, x1, 1, JAMB);
        if (post[3]) PartDraw.inner(r, frame, x, y, z, board, x0, 0, 1 - JAMB, x1, 1, 1);

        for (int a = 2; a < 4; a++) for (int b = 4; b < 6; b++) {
            if (!open[a] || !open[b]) continue;
            double cx = b == 4 ? 0 : 1 - INSET, cz = a == 2 ? 0 : 1 - INSET;
            PartDraw.inner(r, frame, x, y, z, board, cx, 0, cz, cx + INSET, 1, cz + INSET);
        }

        int colour = LibraryBooks.colour(x, y, z);
        for (int s = 2; s < 6; s++) if (open[s]) compartment(w, x, y, z, frame, r, s, open, colour);

        // the shelf's light lines on the plain sides that show, as on a loose shelf (not on the panels towards a post)
        if (!r.hasOverrideBlockTexture()) {
            boolean ao = r.enableAO, lit = false;
            r.setRenderBounds(x0, 0, z0, x1, 1, z1);
            for (int s = 2; s < 6; s++) {
                IIcon g = frame.glow(s, BlockFrame.SHELF);
                if (open[s] || post[s] || g == null || !(r.renderAllFaces || PartDraw.shows(w, x, y, z, frame, s)))
                    continue;
                if (!lit) PartDraw.glowOn(r);
                lit = true;
                PartDraw.face(r, frame, x, y, z, s, g);
            }
            if (lit) PartDraw.glowOff(r, ao);
        }
        r.setRenderBounds(0, 0, 0, 1, 1, 1);
        return true;
    }

    /**
     * Whether the shelf at x, y, z is a unit body: a column of at most {@link #BODIES} shelves, this one among them,
     * standing on a plinth and ending under a crown ({@link UnitLook#niche} for the blocks right above and below).
     */
    static boolean body(IBlockAccess w, int x, int y, int z) {
        UnitLook.Kind below = Neighbours.kind(w, x, y - 1, z), above = Neighbours.kind(w, x, y + 1, z);
        if (!UnitLook.niche(below, above)) return false;
        int shelves = 1;
        UnitLook.Kind k = below;
        for (int i = 1; k == UnitLook.Kind.SHELF; k = Neighbours.kind(w, x, y - ++i, z))
            if (++shelves > BODIES) return false;
        if (k != UnitLook.Kind.PLINTH) return false;
        k = above;
        for (int i = 1; k == UnitLook.Kind.SHELF; k = Neighbours.kind(w, x, y + ++i, z))
            if (++shelves > BODIES) return false;
        return k == UnitLook.Kind.CROWN;
    }

    /** Whether the side s of the shelf at x, y, z shows books (its neighbour there is open to view). */
    private static boolean bookFace(IBlockAccess w, int x, int y, int z, int s) {
        return UnitLook.bookFace(sideKind(w, x, y, z, s));
    }

    /** The kind of the neighbour on the horizontal side s of x, y, z. */
    private static UnitLook.Kind sideKind(IBlockAccess w, int x, int y, int z, int s) {
        int[] o = PartDraw.OFFSETS[s];
        return Neighbours.kind(w, x + o[0], y, z + o[2]);
    }

    /**
     * One open side's compartment: the back panel, the boards at the foot and the head, a side wall at each end where
     * the run does not go on into the neighbour's compartment, and the books. A side wall runs as deep as the back
     * panel, which stops at it, so a run's end (the slit beside a post) shows only board.
     */
    private static void compartment(IBlockAccess w, int x, int y, int z, BlockFrame frame, RenderBlocks r, int s,
        boolean[] open, int colour) {
        boolean alongX = s < 4;
        int low = alongX ? 4 : 2, high = alongX ? 5 : 3;
        // each end of the run: a corner post (another open side), the neighbour's compartment going on, or a side wall
        boolean wallLow = !open[low] && !runs(w, x, y, z, low, s);
        boolean wallHigh = !open[high] && !runs(w, x, y, z, high, s);
        double p0 = open[low] ? INSET : 0, p1 = open[high] ? 1 - INSET : 1;
        double a0 = wallLow ? JAMB : p0, a1 = wallHigh ? 1 - JAMB : p1;

        piece(r, frame, x, y, z, niche, s, a0, a1, 0, 1, RECESS, INSET);
        if (wallLow) piece(r, frame, x, y, z, board, s, 0, JAMB, 0, 1, 0, INSET);
        if (wallHigh) piece(r, frame, x, y, z, board, s, 1 - JAMB, 1, 0, 1, 0, INSET);
        piece(r, frame, x, y, z, board, s, a0, a1, 0, BOARD, 0, RECESS);
        piece(r, frame, x, y, z, board, s, a0, a1, 1 - BOARD, 1, 0, RECESS);
        if (colour != 0) books(w, x, y, z, frame, r, s, low, high, a0, a1, colour);
    }

    /**
     * Whether the run of compartments on side s goes on through the neighbour towards side {@code end}: that
     * neighbour is a unit body whose side s is open too, so the two compartments are one long shelf.
     */
    private static boolean runs(IBlockAccess w, int x, int y, int z, int end, int s) {
        int[] o = PartDraw.OFFSETS[end];
        int nx = x + o[0], nz = z + o[2];
        return Neighbours.kind(w, nx, y, nz) == UnitLook.Kind.SHELF && body(w, nx, y, nz) && bookFace(w, nx, y, nz, s);
    }

    /**
     * A box of a compartment given in the side's own terms: {@code a} along the side (0 at its west or north end),
     * {@code d} the depth in from the face; every face drawn (most lie inside the block), with the icon on all of them.
     */
    private static void piece(RenderBlocks r, BlockFrame frame, int x, int y, int z, IIcon icon, int s, double a0,
        double a1, double y0, double y1, double d0, double d1) {
        double[] b = box(s, a0, a1, y0, y1, d0, d1);
        PartDraw.inner(r, frame, x, y, z, icon, b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    /** A box given along and into side s, in block coordinates: x0, y0, z0, x1, y1, z1. */
    private static double[] box(int s, double a0, double a1, double y0, double y1, double d0, double d1) {
        switch (s) {
            case 2:
                return new double[] { a0, y0, d0, a1, y1, d1 };
            case 3:
                return new double[] { a0, y0, 1 - d1, a1, y1, 1 - d0 };
            case 4:
                return new double[] { d0, y0, a0, d1, y1, a1 };
            default:
                return new double[] { 1 - d1, y0, a0, 1 - d0, y1, a1 };
        }
    }

    /**
     * Three to five spines of the body's book on the lower board, between {@code a0} and {@code a1}: each one to three
     * pixels wide and nine to thirteen tall, its shade varied a little, the whole group placed differently in every
     * compartment. All of it follows from the position, so a re-mesh draws the same books.
     */
    private static void books(IBlockAccess w, int x, int y, int z, BlockFrame frame, RenderBlocks r, int s, int low,
        int high, double a0, double a1, int colour) {
        IIcon icon = r.hasOverrideBlockTexture() ? r.overrideBlockTexture : spine;
        if (icon == null) return;
        long h = mix(Formed.key(x, y, z) * 31 + s);
        int n = 3 + pick(h, 3);
        double[] width = new double[n], height = new double[n], gap = new double[n];
        double room = (a1 - a0) / PX - 1, used = 0;
        int count = 0;
        for (int i = 0; i < n; i++) {
            h = mix(h);
            double wd = 1 + pick(h, 5) * 0.5, gp = i == 0 ? 0 : pick(h >>> 8, 3) == 0 ? 0.5 : 0;
            if (used + gp + wd > room) break;
            width[i] = wd;
            gap[i] = gp;
            height[i] = 9 + pick(h >>> 16, 5);
            used += gp + wd;
            count++;
        }
        h = mix(h);
        double along = a0 / PX + 0.5 + (room - used) * (pick(h, 1000) / 1000.0);

        Tessellator t = Tessellator.instance;
        t.setBrightness(frame.getMixedBrightnessForBlock(w, x, y, z));
        float cr = (colour >> 16 & 255) / 255f, cg = (colour >> 8 & 255) / 255f, cb = (colour & 255) / 255f;
        for (int i = 0; i < count; i++) {
            along += gap[i];
            h = mix(h);
            float tone = 0.72f + 0.4f * (pick(h, 1000) / 1000f);
            double front = 0.5 + 0.5 * pick(h >>> 12, 2), col = pick(h >>> 24, 17 - (int) Math.ceil(width[i]));
            double[] b = box(s, along * PX, (along + width[i]) * PX, BOARD, BOARD + height[i] * PX, front * PX, RECESS);
            double bx0 = x + b[0], by0 = y + b[1], bz0 = z + b[2], bx1 = x + b[3], by1 = y + b[4], bz1 = z + b[5];
            tint(t, cr, cg, cb, tone * PartDraw.shade(s));
            PartDraw.quad(t, s, bx0, by0, bz0, bx1, by1, bz1, icon, col, 0, col + width[i], 16);
            tint(t, cr, cg, cb, tone * 0.85f * PartDraw.shade(low));
            double depth = (RECESS / PX) - front;
            PartDraw.quad(t, low, bx0, by0, bz0, bx1, by1, bz1, icon, 0, 0, depth, 16);
            PartDraw.quad(t, high, bx0, by0, bz0, bx1, by1, bz1, icon, 0, 0, depth, 16);
            t.setColorOpaque_F(PAGE_R * tone, PAGE_G * tone, PAGE_B * tone);
            PartDraw.quad(t, 1, bx0, by0, bz0, bx1, by1, bz1, icon, col, 6, col + width[i], 8);
            along += width[i];
        }
    }

    private static void tint(Tessellator t, float r, float g, float b, float f) {
        t.setColorOpaque_F(Math.min(1f, r * f), Math.min(1f, g * f), Math.min(1f, b * f));
    }

    /** A well-mixed 64-bit hash step (SplitMix64's finaliser). */
    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A number from 0 to n - 1 taken from the hash. */
    private static int pick(long h, int n) {
        return (int) ((h >>> 33) % n);
    }
}
