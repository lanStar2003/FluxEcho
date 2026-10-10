package com.fluxecho.library.client;

import static com.fluxecho.client.FluxDraw.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.fluxecho.client.FluxDraw;
import com.fluxecho.client.Motes;
import com.fluxecho.client.Motifs;
import com.fluxecho.core.EchoText;
import com.fluxecho.library.TileLibrary;
import com.fluxecho.logic.LibraryShape;
import com.fluxecho.render.Shapes;

/**
 * The formed Echo Library (blueprint 3.7, "the five-dimensional bookshelf"). Standing as the Echo Archive (0.10.0) it
 * is drawn by {@link ArchiveRender}, formed or not (unformed, its plinth light shows that state): its books are in its
 * bookcases' chunk mesh, and the room around them lives. The rest of this class is the 0.9.2 hall, drawn as it was.
 * Outside, the codex it embodies floats open over its roof,
 * glyphs rising into it, and its foundation's rim glows in its dock state. Inside the hall, drawn only while the camera
 * is in it: each shelf's book shows its sample on the shelf's face, the book looked at names itself, a small codex
 * turns over the reading desk, and the opening in the ceiling becomes a shaft of shelves that rises further than the
 * attic could hold: it narrows faster than perspective would, to a light at its end. Undocked or without power it
 * dims.
 * <p>
 * Every GL state it pushes (the world state, a matrix, a motes batch, an open quad batch) is popped or closed on every
 * path, so an error part-way leaves nothing behind for the caller ({@code NexusRender}, which logs it once).
 */
public final class LibraryRender {

    private static final int WALL = 0x0B1424, WALL_DEEP = 0x05070E, GOLD = 0xFFD27A, COVER = 0x120A28;
    /** The shaft's depth (ceiling to roof, a hair short) and the half-size of its far end, in blocks. */
    private static final double DEPTH = 1.98, END = 0.35;
    private static final int SEGMENTS = 6;

    private LibraryRender() {}

    public static void draw(TileLibrary l, double t, float fade) {
        // the Archive shows its unformed state too (a dim plinth light where its walls stand); the hall does not
        if (l.isArchive()) {
            ArchiveRender.draw(l, t, fade);
            return;
        }
        if (!l.formed()) return;
        int[] c = l.centre();
        // the foundation's centre block: the hall from one above it, the ceiling at six, the roof's top at nine
        double cx = c[0] + 0.5, cz = c[2] + 0.5, floor = c[1] + 1.0;
        boolean docked = "docked".equals(l.clientDock), on = docked && l.clientPowered;
        float p = (on ? 1f : 0.35f) * fade;
        double sweep = l.sweepLevel();
        float appear = sweep == Double.MAX_VALUE ? 1f
            : (float) Math.max(0, Math.min(1, (System.currentTimeMillis() - l.formedAt) / 2000.0));
        double camX = RenderManager.renderPosX, camY = RenderManager.renderPosY, camZ = RenderManager.renderPosZ;
        double half = LibraryShape.SIZE / 2.0, wall = half - 1;
        boolean inside = Math.abs(camX - cx) < wall && Math.abs(camZ - cz) < wall
            && camY >= floor
            && camY < floor + LibraryShape.CEILING - 1;

        double roof = floor + LibraryShape.ROOF;
        Shapes.begin(false);
        try {
            Shapes.additive(true);
            codex(cx - camX, roof + 2.2 - camY, cz - camZ, 1.0, t, p);
            if (sweep != Double.MAX_VALUE) {
                double sy = sweep - camY;
                Shapes.plane(
                    cx - half - camX,
                    sy,
                    cz - half - camZ,
                    cx + half - camX,
                    cz + half - camZ,
                    VIOLET,
                    0.35f * (1 - appear));
                Shapes.square(cx - camX, sy, cz - camZ, half, 0.15, WHITE, 0.8f * (1 - appear));
            }
            float ring = docked ? (l.clientLends > 0 ? 0.5f + 0.3f * (float) Math.sin(t * 0.15) : 0.25f)
                : 0.3f * (float) (0.5 + 0.5 * Math.sin(t * 0.1));
            Shapes.square(cx - camX, floor - camY + 0.03, cz - camZ, half, 0.18, docked ? VIOLET : AMBER, ring * fade);
        } catch (Throwable x) {
            abandon();
            throw x;
        } finally {
            Shapes.end();
        }

        Motes.begin();
        try {
            for (int k = 0; k < 10; k++) {
                double f = (t * 0.01 + k / 10.0) % 1;
                double a = k * 2.3 + t * 0.01;
                double r = 1.6 * (1 - f);
                Motes.add(
                    cx - camX + Math.cos(a) * r,
                    roof - camY - 0.5 + f * 2.8,
                    cz - camZ + Math.sin(a) * r,
                    0.1,
                    k % 3 == 0 ? GOLD : VIOLET,
                    (float) Math.sin(f * Math.PI) * p);
            }
            Motes.add(cx - camX, roof + 2.2 - camY, cz - camZ, 1.1, VIOLET, 0.35f * p);
        } finally {
            Motes.end();
        }

        if (inside) hall(l, cx, floor, cz, t, p, appear * fade);
    }

    /** The inside of the hall: the shaft, the desk's codex, the books, the label of the one looked at. */
    private static void hall(TileLibrary l, double cx, double floor, double cz, double t, float p, float a) {
        double camX = RenderManager.renderPosX, camY = RenderManager.renderPosY, camZ = RenderManager.renderPosZ;
        // the opening's face, looking down into the hall: its normal and its centre
        double[] fc = { 0, -1, 0, cx, floor + LibraryShape.CEILING - 1, cz };
        Shapes.begin(false);
        try {
            GL11.glDepthMask(true);
            shaftWalls(fc, a);
            GL11.glDepthMask(false);
            Shapes.additive(true);
            shaftLights(fc, t, p * a);
            codex(cx - camX, floor + 1.9 - camY, cz - camZ, 0.55, t, p);
            books(l, t, p);
        } catch (Throwable x) {
            abandon();
            throw x;
        } finally {
            // popping the state puts the depth mask back as well
            Shapes.end();
        }

        Motes.begin();
        try {
            for (int k = 0; k < 12; k++) {
                double f = (t * 0.006 + k / 12.0) % 1;
                double ang = k * 2.1 + t * 0.004;
                double r = 0.6 + 1.6 * Motifs.hash(k * 7 + 3);
                Motes.add(
                    cx - camX + Math.cos(ang) * r,
                    floor + 1.2 + f * 5.2 - camY,
                    cz - camZ + Math.sin(ang) * r,
                    0.08,
                    k % 4 == 0 ? GOLD : VIOLET,
                    (float) Math.sin(f * Math.PI) * 0.7f * p);
            }
        } finally {
            Motes.end();
        }

        icons(l);
        label(l);
    }

    /**
     * Ends a quad batch an error broke off, so the tessellator is not left drawing (it refuses to start another while
     * one is open, which would take every later drawing with it).
     */
    private static void abandon() {
        try {
            Tessellator.instance.draw();
        } catch (Throwable ignored) {
            // nothing was open
        }
    }

    /**
     * Where shelf {@code i}'s book is: its face's centre (a hair off the shelf), the way out of it and to the right.
     */
    private static double[] face(TileLibrary l, int i, double off) {
        int[] s = l.shelfPos(i);
        ForgeDirection d = ForgeDirection.getOrientation(l.shelfFace(i));
        double x = s[0] + 0.5 + d.offsetX * (0.5 + off), y = s[1] + 0.5, z = s[2] + 0.5 + d.offsetZ * (0.5 + off);
        // the right of someone in the hall looking at the shelf
        return new double[] { x - RenderManager.renderPosX, y - RenderManager.renderPosY, z - RenderManager.renderPosZ,
            d.offsetZ, -d.offsetX, d.offsetX, d.offsetZ };
    }

    private static double[] corner(double[] f, double right, double up) {
        return new double[] { f[0] + f[3] * right, f[1] + up, f[2] + f[4] * right };
    }

    /** Each book's cover: a rim of light and a dark plate the sample's picture goes on. */
    private static void books(TileLibrary l, double t, float p) {
        for (int i = 0; i < l.capacity(); i++) {
            ItemStack s = l.book(i);
            if (s == null) continue;
            double[] f = face(l, i, 0.011);
            float glow = (0.45f + 0.25f * (float) Math.sin(t * 0.05 + i * 0.7)) * p;
            Shapes.quad3(
                corner(f, -0.42, 0.42),
                corner(f, -0.42, -0.42),
                corner(f, 0.42, -0.42),
                corner(f, 0.42, 0.42),
                i % 5 == 0 ? GOLD : VIOLET,
                glow,
                glow,
                glow,
                glow);
        }
        Shapes.additive(false);
        for (int i = 0; i < l.capacity(); i++) {
            if (l.book(i) == null) continue;
            double[] f = face(l, i, 0.012);
            Shapes.quad3(
                corner(f, -0.36, 0.36),
                corner(f, -0.36, -0.36),
                corner(f, 0.36, -0.36),
                corner(f, 0.36, 0.36),
                COVER,
                0.95f,
                0.95f,
                0.95f,
                0.95f);
        }
    }

    /**
     * The samples' pictures on their books: blocks' first, then items', each pass in its colour. The world state is
     * ended and an open batch closed whatever happens.
     */
    private static void icons(TileLibrary l) {
        Minecraft mc = Minecraft.getMinecraft();
        Tessellator tes = Tessellator.instance;
        boolean drawing = false;
        FluxDraw.worldBegin();
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
            for (int sheet = 0; sheet <= 1; sheet++) {
                mc.getTextureManager()
                    .bindTexture(sheet == 0 ? TextureMap.locationBlocksTexture : TextureMap.locationItemsTexture);
                tes.startDrawingQuads();
                drawing = true;
                for (int i = 0; i < l.capacity(); i++) {
                    ItemStack s = l.book(i);
                    if (s == null || s.getItem() == null) continue;
                    try {
                        if (s.getItem()
                            .getSpriteNumber() != sheet) continue;
                        icon(tes, s, face(l, i, 0.013));
                    } catch (RuntimeException e) {
                        // a modded item whose picture cannot be had here keeps a bare cover
                    }
                }
                drawing = false;
                tes.draw();
            }
        } finally {
            if (drawing) abandon();
            FluxDraw.worldEnd();
        }
    }

    private static void icon(Tessellator tes, ItemStack s, double[] f) {
        Item item = s.getItem();
        int passes = item.requiresMultipleRenderPasses() ? item.getRenderPasses(s.getItemDamage()) : 1;
        double h = 0.3;
        for (int pass = 0; pass < passes; pass++) {
            IIcon ic = item.getIcon(s, pass);
            if (ic == null) continue;
            int rgb = item.getColorFromItemStack(s, pass);
            tes.setColorRGBA_I(rgb & 0xFFFFFF, 255);
            tes.setNormal((float) f[5], 0f, (float) f[6]);
            double[] tl = corner(f, -h, h), bl = corner(f, -h, -h), br = corner(f, h, -h), tr = corner(f, h, h);
            tes.addVertexWithUV(tl[0], tl[1], tl[2], ic.getMinU(), ic.getMinV());
            tes.addVertexWithUV(bl[0], bl[1], bl[2], ic.getMinU(), ic.getMaxV());
            tes.addVertexWithUV(br[0], br[1], br[2], ic.getMaxU(), ic.getMaxV());
            tes.addVertexWithUV(tr[0], tr[1], tr[2], ic.getMaxU(), ic.getMinV());
        }
    }

    /** The name of the book the player looks at (or what an empty shelf takes), over its shelf. */
    private static void label(TileLibrary l) {
        MovingObjectPosition hit = Minecraft.getMinecraft().objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        int i = l.shelfAt(hit.blockX, hit.blockY, hit.blockZ);
        if (i < 0 || hit.sideHit != l.shelfFace(i)) return;
        ItemStack s = l.book(i);
        String line = s == null ? EchoText.t("library.shelf.label_empty") : s.getDisplayName();
        double[] f = face(l, i, 0.3);
        float px = 1 / 96f;
        int w = Math.min(220, font().getStringWidth(line) + 10), h = 12;
        FluxDraw.worldBegin();
        try {
            GL11.glPushMatrix();
            // the matrix is popped on every path, so a failing label cannot leave the stack one deeper
            try {
                GL11.glTranslated(f[0], f[1] + 0.62, f[2]);
                GL11.glRotatef(-RenderManager.instance.playerViewY, 0f, 1f, 0f);
                GL11.glRotatef(RenderManager.instance.playerViewX, 1f, 0f, 0f);
                GL11.glScalef(-px, -px, px);
                GL11.glTranslatef(-w / 2f, -h / 2f, 0);
                FluxDraw.begin();
                pane(0, 0, w, h, 0.85f);
                rect(1, h - 1, w - 1, h, s == null ? SEAM : VIOLET, 0.8f);
                FluxDraw.end();
                text(fit(line, w - 8), 5, 2, s == null ? DIM : WHITE, 1f);
            } catch (Throwable x) {
                abandon();
                throw x;
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            FluxDraw.worldEnd();
        }
    }

    /** A point of the shaft: {@code u, v} across the opening, {@code d} up into it. */
    private static double[] at(double[] fc, double u, double v, double d) {
        return new double[] { fc[3] + u - RenderManager.renderPosX, fc[4] + d - RenderManager.renderPosY,
            fc[5] + v - RenderManager.renderPosZ };
    }

    private static double depth(int i) {
        return DEPTH * i / SEGMENTS;
    }

    /** Half-size of the shaft at segment boundary {@code i}: from the 5-wide opening down to the far end. */
    private static double half(int i) {
        double f = i / (double) SEGMENTS;
        return 2.5 + (END - 2.5) * Math.pow(f, 0.8);
    }

    private static void shaftWalls(double[] fc, float a) {
        for (int i = 0; i < SEGMENTS; i++) {
            double d0 = depth(i), d1 = depth(i + 1), h0 = half(i), h1 = half(i + 1);
            int c0 = Shapes.mix(WALL, WALL_DEEP, i / (double) SEGMENTS),
                c1 = Shapes.mix(WALL, WALL_DEEP, (i + 1) / (double) SEGMENTS);
            for (int s = 0; s < 4; s++) {
                double[] p0 = corner(fc, s, h0, d0), p1 = corner(fc, s + 1, h0, d0), p2 = corner(fc, s + 1, h1, d1),
                    p3 = corner(fc, s, h1, d1);
                Shapes.quadShade(p0, p1, p2, p3, c0, c1, a);
            }
        }
        double h = half(SEGMENTS), d = depth(SEGMENTS);
        Shapes.quad3(
            corner(fc, 0, h, d),
            corner(fc, 1, h, d),
            corner(fc, 2, h, d),
            corner(fc, 3, h, d),
            WALL_DEEP,
            a,
            a,
            a,
            a);
    }

    /** Corner {@code k} (0..3, round the square) of the shaft's cross-section at depth {@code d}. */
    private static double[] corner(double[] fc, int k, double h, double d) {
        int[][] signs = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
        int[] sg = signs[Math.floorMod(k, 4)];
        return at(fc, sg[0] * h, sg[1] * h, d);
    }

    /** The shelves' light: a glowing rim at each step up, rows of book spines on the walls, the far end shining. */
    private static void shaftLights(double[] fc, double t, float a) {
        for (int i = 0; i <= SEGMENTS; i++) {
            double h = half(i) - 0.02, d = depth(i);
            float fa = a * (1f - i / (float) (SEGMENTS + 2));
            for (int s = 0; s < 4; s++) {
                double[] p0 = corner(fc, s, h, d), p1 = corner(fc, s + 1, h, d);
                Shapes.string(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], 0.05, VIOLET, 0.55f * fa, 0.55f * fa);
            }
        }
        // book spines: short bright strokes up the walls, a hue each
        for (int i = 0; i < SEGMENTS; i++) {
            double dm = (depth(i) + depth(i + 1)) / 2, hm = (half(i) + half(i + 1)) / 2 - 0.03;
            float fa = a * (0.6f - 0.08f * i);
            for (int s = 0; s < 4; s++) for (int k = 0; k < 7; k++) {
                double hash = Motifs.hash(i * 131 + s * 17 + k);
                if (hash < 0.25) continue;
                double u = -hm + (k + 0.5) * hm * 2 / 7;
                double len = (0.25 + 0.3 * hash) * DEPTH / 3;
                double[] a0 = wallPoint(fc, s, u, hm, dm - len / 2), a1 = wallPoint(fc, s, u, hm, dm + len / 2);
                int col = hash < 0.5 ? VIOLET : hash < 0.8 ? CYAN : GOLD;
                float tw = 0.6f + 0.4f * (float) Math.sin(t * 0.05 + hash * 20);
                Shapes.string(a0[0], a0[1], a0[2], a1[0], a1[1], a1[2], 0.06 * (1 - i * 0.1), col, fa * tw, fa * tw);
            }
        }
        double h = half(SEGMENTS), d = depth(SEGMENTS) - 0.02;
        float pulse = 0.6f + 0.4f * (float) Math.sin(t * 0.07);
        for (int r = 0; r < 3; r++) {
            double rr = h * (0.4 + r * 0.3);
            Shapes.quad3(
                at(fc, -rr, -rr, d),
                at(fc, rr, -rr, d),
                at(fc, rr, rr, d),
                at(fc, -rr, rr, d),
                VIOLET,
                0.25f * a * pulse,
                0.25f * a * pulse,
                0.25f * a * pulse,
                0.25f * a * pulse);
        }
    }

    /** A point on wall {@code s} of the shaft: {@code u} along the wall, at half-size {@code h} and depth {@code d}. */
    private static double[] wallPoint(double[] fc, int s, double u, double h, double d) {
        switch (Math.floorMod(s, 4)) {
            case 0:
                return at(fc, u, -h, d);
            case 1:
                return at(fc, h, u, d);
            case 2:
                return at(fc, -u, h, d);
            default:
                return at(fc, -h, -u, d);
        }
    }

    /**
     * The codex floating open, its pages turning; {@code k} scales it. Camera-relative, inside a {@link Shapes#begin};
     * the Echo Archive's desk shows it too ({@link ArchiveRender}).
     */
    static void codex(double x, double y, double z, double k, double t, float a) {
        double spin = t * 0.01, open = Math.toRadians(70 + 35 * Math.sin(t * 0.03));
        double ax = Math.cos(spin), az = Math.sin(spin);
        double bob = Math.sin(t * 0.04) * 0.12 * k;
        double[] s0 = { x - ax * 0.5 * k, y + bob, z - az * 0.5 * k },
            s1 = { x + ax * 0.5 * k, y + bob, z + az * 0.5 * k };
        for (int side = -1; side <= 1; side += 2) {
            double ang = side * open / 2;
            // the page runs out from the spine, tilted up by the open angle
            double ox = -az * Math.cos(ang) * 0.7 * k * side, oz = ax * Math.cos(ang) * 0.7 * k * side,
                oy = Math.sin(Math.abs(ang)) * 0.7 * k;
            double[] p2 = { s1[0] + ox, s1[1] + oy, s1[2] + oz }, p3 = { s0[0] + ox, s0[1] + oy, s0[2] + oz };
            Shapes.quad3(s0, s1, p2, p3, VIOLET, 0.5f * a, 0.5f * a, 0.15f * a, 0.15f * a);
            for (int l = 1; l <= 4; l++) {
                double f = l / 5.0;
                double[] q0 = { s0[0] + ox * f, s0[1] + oy * f, s0[2] + oz * f },
                    q1 = { s1[0] + ox * f, s1[1] + oy * f, s1[2] + oz * f };
                Shapes.string(q0[0], q0[1], q0[2], q1[0], q1[1], q1[2], 0.03 * k, GOLD, 0.5f * a, 0.5f * a);
            }
        }
        Shapes.string(s0[0], s0[1], s0[2], s1[0], s1[1], s1[2], 0.08 * k, WHITE, 0.7f * a, 0.7f * a);
    }
}
