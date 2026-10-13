package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.WHITE;

import net.minecraft.client.renderer.Tessellator;

import com.fluxecho.FluxEcho;
import com.fluxecho.city.TileArcaneCourt;
import com.fluxecho.client.Motes;
import com.fluxecho.logic.ArcaneLook;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.logic.UnitLayout;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Arcane Court's own visuals (0.13.0, CITY.md §4.3 Visuals), on top of what every city module shows
 * ({@link CityRender}); where everything goes and how it moves is {@link ArcaneLook}'s.
 * <ul>
 * <li>Formed: the finial's conduits and seat have given way to a violet crystal turning over the lantern's cap, a
 * smaller one turning the other way inside it; undocked it burns low.</li>
 * <li>Docked: three rings of black ink orbit the spire at their own heights, tilts and speeds, their strokes tapering
 * into the gaps where the brush lifted; near, a violet halo behind the ink. Inside (near): the rune ring turns on the
 * floor's lit ring, motes in the aspects' colours rise from the eight aspect pillars, and the four cauldrons bubble
 * over their rims.</li>
 * <li>Powered (near): logograms of ink sink down the hollow spire's inside like ink in water, spreading as they
 * fall.</li>
 * </ul>
 * The ink is alpha-blended, not added (black cannot be added), over its added halo; the light (the crystal's inner
 * crystal, the halos, the runes, the motes and the bubbles) is added. Budgets (CITY.md §1.3, quads a frame on top of
 * {@link CityRender}'s, {@link ArcaneLook#quads}): far, the crystal and the rings in 12 pieces each (45); mid, the
 * crystals and the rings in 24 pieces each (80); near, the crystals, the rings in 48 pieces each with their halos (259
 * together), the rune ring (144), the motes (40), the cauldrons (28) and, powered, the logograms (198). With the plinth
 * line, the plate
 * and the sockets' samples ({@link ModuleLook#baseQuads}) that keeps the court under 2,000 near, 300 mid and 64 far
 * ({@code ArcaneLookTest}).
 * <p>
 * Shader-safe as {@link CityRender}: everything in its frame ({@link CityFrame}) relative to the camera, quads only, a
 * normal towards the viewer on every vertex, no depth writes; animation by the world time alone, nothing counted,
 * spawned or sent, so a frame drawn again behind a light gate is the same. Each part fails on its own.
 */
@SideOnly(Side.CLIENT)
final class ArcaneCourtRender {

    /** The ink's colour (black with a violet cast), the crystal's light, the logograms' ink. */
    private static final int INK = 0x0C0714, CRYSTAL = 0xF0DCFF, GLYPH_INK = 0x120A1C;
    private static final double AXIS = ArcaneLook.AXIS;
    /** The rune ring's lines' half-widths and the runes' strokes'. */
    private static final double LINE_HALF = 0.03, STROKE_HALF = 0.035;

    private static final int CRYSTAL_PART = 0, RINGS = 1, RUNES = 2, MOTES = 3, CAULDRONS = 4, GLYPHS = 5;
    private static final String[] NAMES = { "finial crystal", "ink rings", "rune ring", "aspect motes",
        "bubbling cauldrons", "sinking logograms" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    private ArcaneCourtRender() {}

    /**
     * Draws the Arcane Court's own visuals; {@code p0} is its distance fade times how far it has formed, {@code tier}
     * how much to draw ({@link ModuleLook#tier}).
     */
    static void draw(TileArcaneCourt c, CityFrame f, double t, float p0, int tier) {
        if (p0 <= 0.01f) return;
        boolean docked = CityRender.docked(c), on = CityRender.on(c), nearby = tier == ModuleLook.NEAR;
        float life = p0 * (on ? 1f : docked ? 0.6f : 0.3f);
        int accent = c.moduleColor();
        if (!OFF[CRYSTAL_PART]) try {
            crystal(f, t, p0, life, tier, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(CRYSTAL_PART, x);
        } finally {
            CityRender.close();
        }
        if (!docked) return;
        if (!OFF[RINGS]) try {
            rings(f, t, life, tier, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(RINGS, x);
        } finally {
            CityRender.close();
        }
        if (!nearby) return;
        if (!OFF[RUNES]) try {
            runes(f, t, life, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(RUNES, x);
        } finally {
            CityRender.close();
        }
        if (!OFF[MOTES]) try {
            motes(f, t, life);
        } catch (Throwable x) {
            CityRender.recover();
            fail(MOTES, x);
        } finally {
            CityRender.close();
        }
        if (!OFF[CAULDRONS]) try {
            cauldrons(f, t, life, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(CAULDRONS, x);
        } finally {
            CityRender.close();
        }
        if (on && !OFF[GLYPHS]) try {
            glyphs(f, t, p0, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(GLYPHS, x);
        } finally {
            CityRender.close();
        }
    }

    // ---- the crystal

    /**
     * The finial's crystal, turning and bobbing over the lantern's cap where the conduits and the seat stood: an outer
     * crystal blended, near and mid a smaller one added inside turning the other way, and its glow.
     */
    private static void crystal(CityFrame f, double t, float p0, float life, int tier, int accent) {
        double turn = ArcaneLook.crystalTurn(t);
        double cx = f.x(AXIS, AXIS), cy = f.y(ArcaneLook.crystalY(t)), cz = f.z(AXIS, AXIS);
        Shapes.begin(false);
        Shapes.crystal(
            cx,
            cy,
            cz,
            ArcaneLook.CRYSTAL_R,
            ArcaneLook.CRYSTAL_H,
            turn,
            CRYSTAL,
            accent,
            0.8f * life + 0.15f * p0);
        if (tier != ModuleLook.FAR) {
            Shapes.additive(true);
            Shapes.crystal(
                cx,
                cy,
                cz,
                ArcaneLook.CRYSTAL_R * 0.5,
                ArcaneLook.CRYSTAL_H * 0.5,
                -1.6 * turn,
                WHITE,
                accent,
                0.6f * life);
        }
        Shapes.end();
        Motes.begin();
        Motes.add(cx, cy, cz, 1.6, accent, 0.3f * life + 0.05f * p0);
        Motes.end();
    }

    // ---- the ink rings

    /**
     * The three ink rings, each cut into the tier's pieces ({@link ArcaneLook#INK_PIECES}), a piece wholly in a gap
     * left out: near, first a violet halo behind the ink (added), then the ink itself, alpha-blended over it.
     */
    private static void rings(CityFrame f, double t, float life, int tier, int accent) {
        int n = ArcaneLook.INK_PIECES[tier];
        if (tier == ModuleLook.NEAR) {
            Shapes.begin(true);
            Tessellator tes = CityFrame.start();
            tes.setColorRGBA_I(accent, CityFrame.alpha255(0.22f * life));
            strokes(tes, f, t, n, 4.4);
            tes.draw();
            Shapes.end();
        }
        // black cannot be added: the ink blends over what is behind it
        Shapes.begin(false);
        Tessellator tes = CityFrame.start();
        tes.setColorRGBA_I(INK, CityFrame.alpha255(0.9f * life));
        strokes(tes, f, t, n, 2);
        tes.draw();
        Shapes.end();
    }

    /** Every ring's strokes into the open batch, {@code widen} times the brush's half-width wide. */
    private static void strokes(Tessellator tes, CityFrame f, double t, int n, double widen) {
        for (int r = 0; r < ArcaneLook.RINGS; r++) {
            double[] a = ArcaneLook.inkPoint(r, 0, t);
            double ha = ArcaneLook.inkHalf(r, 0);
            for (int i = 1; i <= n; i++) {
                double s = i / (double) n;
                double[] b = ArcaneLook.inkPoint(r, s, t);
                double hb = ArcaneLook.inkHalf(r, s);
                if (ha > 0 || hb > 0) f.taper(tes, a[0], a[1], a[2], b[0], b[1], b[2], ha * widen, hb * widen);
                a = b;
                ha = hb;
            }
        }
    }

    // ---- inside

    /**
     * The rune ring on the floor's lit ring: two thin lines round it and the runes between them, turning slowly, their
     * light added a little over the floor.
     */
    private static void runes(CityFrame f, double t, float life, int accent) {
        double y = ArcaneLook.RUNE_Y;
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        int light = Shapes.mix(accent, WHITE, 0.3);
        tes.setColorRGBA_I(accent, CityFrame.alpha255(0.4f * life));
        int n = ArcaneLook.RUNE_PIECES;
        for (double r : new double[] { ArcaneLook.RUNE_IN, ArcaneLook.RUNE_OUT }) {
            for (int i = 0; i < n; i++) {
                double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
                flat(
                    tes,
                    f,
                    AXIS + r * Math.cos(a0),
                    AXIS + r * Math.sin(a0),
                    AXIS + r * Math.cos(a1),
                    AXIS + r * Math.sin(a1),
                    y,
                    LINE_HALF);
            }
        }
        for (int i = 0; i < ArcaneLook.RUNES; i++) {
            float k = life * (0.5f + 0.2f * (float) Math.sin(t * 0.05 + i * 1.7));
            tes.setColorRGBA_I(light, CityFrame.alpha255(k));
            for (int s = 0; s < ArcaneLook.STROKES; s++) {
                double[] st = ArcaneLook.runeStroke(i, s, t);
                flat(tes, f, st[0], st[1], st[2], st[3], y, STROKE_HALF);
            }
        }
        tes.draw();
        Shapes.end();
    }

    /** A level band between two points of the shape at a height: a line lying on the floor. */
    private static void flat(Tessellator tes, CityFrame f, double x0, double z0, double x1, double z1, double y,
        double half) {
        double dx = x1 - x0, dz = z1 - z0, l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-9) return;
        double sx = -dz / l * half, sz = dx / l * half;
        f.vertex(tes, x0 - sx, y, z0 - sz);
        f.vertex(tes, x0 + sx, y, z0 + sz);
        f.vertex(tes, x1 + sx, y, z1 + sz);
        f.vertex(tes, x1 - sx, y, z1 - sz);
    }

    /** The motes rising from the aspect pillars into the hall, each pillar in its aspect's colour. */
    private static void motes(CityFrame f, double t, float life) {
        Motes.begin();
        for (int p = 0; p < ArcaneLook.pillars()
            .size(); p++) {
            for (int k = 0; k < ArcaneLook.MOTES; k++) {
                double[] m = ArcaneLook.pillarMote(p, k, t);
                Motes
                    .add(f.x(m[0], m[2]), f.y(m[1]), f.z(m[0], m[2]), 0.09, ArcaneLook.ASPECTS[p], (float) m[3] * life);
            }
        }
        Motes.end();
    }

    /** The cauldrons: a soft glow over each and its bubbles rising and bursting over the basin rim. */
    private static void cauldrons(CityFrame f, double t, float life, int accent) {
        int light = Shapes.mix(accent, WHITE, 0.45);
        Motes.begin();
        for (int c = 0; c < ArcaneLook.cauldrons()
            .size(); c++) {
            UnitLayout.Unit u = ArcaneLook.cauldrons()
                .get(c);
            int[] s = u.sockets.get(0);
            double gx = s[0] + 0.5, gz = s[2] + 0.5;
            float breath = 0.15f + 0.05f * (float) Math.sin(t * 0.07 + c * 1.9);
            Motes.add(f.x(gx, gz), f.y(s[1] + 0.85), f.z(gx, gz), 1.1, accent, breath * life);
            for (int k = 0; k < ArcaneLook.BUBBLES; k++) {
                double[] b = ArcaneLook.bubble(c, k, t);
                Motes.add(f.x(b[0], b[2]), f.y(b[1]), f.z(b[0], b[2]), b[3], light, (float) b[4] * 0.7f * life);
            }
        }
        Motes.end();
    }

    // ---- the logograms

    /**
     * The logograms sinking down the hollow spire: each a ring of ink in its own tilted plane, broken where the brush
     * lifted, with tendrils curling out of it; a violet halo added behind, the ink blended over it.
     */
    private static void glyphs(CityFrame f, double t, float p0, int accent) {
        double[][] gl = new double[ArcaneLook.GLYPHS][];
        for (int g = 0; g < gl.length; g++) gl[g] = ArcaneLook.glyph(g, t);
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        for (int g = 0; g < gl.length; g++) {
            tes.setColorRGBA_I(accent, CityFrame.alpha255(0.3f * p0 * (float) gl[g][7]));
            glyph(tes, f, gl[g], g, 3);
        }
        tes.draw();
        Shapes.end();
        Shapes.begin(false);
        tes = CityFrame.start();
        for (int g = 0; g < gl.length; g++) {
            tes.setColorRGBA_I(GLYPH_INK, CityFrame.alpha255(0.75f * p0 * (float) gl[g][7]));
            glyph(tes, f, gl[g], g, 1);
        }
        tes.draw();
        Shapes.end();
    }

    /**
     * One logogram into the open batch, its bands {@code widen} times as wide as its ink: the drawn pieces of its ring
     * ({@link ArcaneLook#glyphDrawn}) and its tendrils.
     */
    private static void glyph(Tessellator tes, CityFrame f, double[] gl, int g, double widen) {
        int n = ArcaneLook.GLYPH_PIECES;
        double r = gl[3];
        for (int i = 0; i < n; i++) {
            double s0 = i / (double) n, s1 = (i + 1) / (double) n;
            double h0 = ArcaneLook.glyphHalf(g, s0) * widen, h1 = ArcaneLook.glyphHalf(g, s1) * widen;
            if (h0 <= 0 && h1 <= 0) continue;
            double a0 = 2 * Math.PI * s0, a1 = 2 * Math.PI * s1;
            vertex(tes, f, ArcaneLook.glyphPoint(gl, a0, r - h0));
            vertex(tes, f, ArcaneLook.glyphPoint(gl, a1, r - h1));
            vertex(tes, f, ArcaneLook.glyphPoint(gl, a1, r + h1));
            vertex(tes, f, ArcaneLook.glyphPoint(gl, a0, r + h0));
        }
        double half = ArcaneLook.GLYPH_HALF * 0.8 * widen;
        for (int k = 0; k < ArcaneLook.TENDRILS; k++) {
            double a = ArcaneLook.tendrilAngle(g, k), reach = ArcaneLook.tendrilReach(g, k);
            for (int j = 0; j < ArcaneLook.TENDRIL_PIECES; j++) {
                double u0 = j / (double) ArcaneLook.TENDRIL_PIECES, u1 = (j + 1) / (double) ArcaneLook.TENDRIL_PIECES;
                // the tendril curls as it reaches out, thinning to a point
                double b0 = a + 0.5 * u0 * reach / r, b1 = a + 0.5 * u1 * reach / r;
                double w0 = half * (1 - u0), w1 = half * (1 - u1);
                vertex(tes, f, ArcaneLook.glyphPoint(gl, b0 - w0 / r, r + reach * u0));
                vertex(tes, f, ArcaneLook.glyphPoint(gl, b1 - w1 / r, r + reach * u1));
                vertex(tes, f, ArcaneLook.glyphPoint(gl, b1 + w1 / r, r + reach * u1));
                vertex(tes, f, ArcaneLook.glyphPoint(gl, b0 + w0 / r, r + reach * u0));
            }
        }
    }

    /** A point of the court's frame as a vertex of the open batch. */
    private static void vertex(Tessellator tes, CityFrame f, double[] p) {
        f.vertex(tes, p[0], p[1], p[2]);
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Arcane Court's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
