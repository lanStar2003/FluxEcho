package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.WHITE;

import net.minecraft.client.renderer.Tessellator;

import com.fluxecho.FluxEcho;
import com.fluxecho.city.TileManaFountain;
import com.fluxecho.client.Motes;
import com.fluxecho.logic.ManaLook;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Mana Fountain Array's own visuals (0.13.0, CITY.md §4.6 Visuals), on top of what every city module shows
 * ({@link CityRender}); where everything goes and how it moves is {@link ManaLook}'s.
 * <ul>
 * <li>Formed: the obelisk's conduits and seat have given way to the mana spout, a spiral column of pale-blue motes
 * rising from the controller, two strands winding round each other, with a glow; undocked it burns low.</li>
 * <li>Docked: from each of the eight fountains' sockets a jet of pale-blue light arcs to the spout, drops of light
 * running along it; the magic circle in the court's floor turns, bright dashes and runes running round its outer
 * circle and ticks round its inner circle the other way, passing under the basins and the urns.</li>
 * <li>Powered (near): mana sparkles drift from the spout out through the colonnade's openings and settle over the
 * garden soil.</li>
 * </ul>
 * Budgets (CITY.md §1.3, quads a frame on top of {@link CityRender}'s, {@link ManaLook#quads}): far, the spout's glow
 * and the jets in two pieces each; mid, the spout's motes, the jets in six pieces and the circles; near, the spout,
 * the jets in twenty pieces with their halos and drops, the circles with their runes and ticks, and the sparkles. With
 * the plinth line, the plate and the sockets' samples ({@link ModuleLook#baseQuads}) that keeps the array under 2,000
 * near, 300 mid and 64 far ({@code ManaLookTest}).
 * <p>
 * Shader-safe as {@link CityRender}: everything in its frame ({@link CityFrame}) relative to the camera, quads only, a
 * normal towards the viewer on every vertex, no depth writes; all its light is added. Animation by the world time
 * alone, nothing counted, spawned or sent, so a frame drawn again behind a light gate is the same. Each part fails on
 * its own.
 */
@SideOnly(Side.CLIENT)
final class ManaFountainRender {

    private static final double AXIS = ManaLook.AXIS;
    /** How much a jet's halo is wider than its core; the circles', the runes' and the ticks' half-widths. */
    private static final double HALO = 3.5, CIRCLE_HALF = 0.035, RUNE_HALF = 0.03;

    private static final int SPOUT = 0, JETS = 1, CIRCLE = 2, SPARKLES = 3;
    private static final String[] NAMES = { "mana spout", "light jets", "magic circle", "mana sparkles" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    private ManaFountainRender() {}

    /**
     * Draws the Mana Fountain Array's own visuals; {@code p0} is its distance fade times how far it has formed,
     * {@code tier} how much to draw ({@link ModuleLook#tier}).
     */
    static void draw(TileManaFountain m, CityFrame f, double t, float p0, int tier) {
        if (p0 <= 0.01f) return;
        boolean docked = CityRender.docked(m), on = CityRender.on(m), nearby = tier == ModuleLook.NEAR;
        float life = p0 * (on ? 1f : docked ? 0.6f : 0.3f);
        int accent = m.moduleColor(), pale = Shapes.mix(accent, WHITE, 0.45);
        if (!OFF[SPOUT]) try {
            spout(f, t, p0, life, tier, accent, pale);
        } catch (Throwable x) {
            CityRender.recover();
            fail(SPOUT, x);
        } finally {
            CityRender.close();
        }
        if (!docked) return;
        if (!OFF[JETS]) try {
            jets(f, t, life, tier, accent, pale);
        } catch (Throwable x) {
            CityRender.recover();
            fail(JETS, x);
        } finally {
            CityRender.close();
        }
        if (tier != ModuleLook.FAR && !OFF[CIRCLE]) try {
            circle(f, t, life, nearby, accent, pale);
        } catch (Throwable x) {
            CityRender.recover();
            fail(CIRCLE, x);
        } finally {
            CityRender.close();
        }
        if (nearby && on && !OFF[SPARKLES]) try {
            sparkles(f, t, p0, pale);
        } catch (Throwable x) {
            CityRender.recover();
            fail(SPARKLES, x);
        } finally {
            CityRender.close();
        }
    }

    // ---- the spout

    /** The spout: its motes winding up the two strands (near and mid) and a glow round the column. */
    private static void spout(CityFrame f, double t, float p0, float life, int tier, int accent, int pale) {
        int n = ManaLook.SPOUT_MOTES[tier];
        Motes.begin();
        double mid = (ManaLook.SPOUT_FROM + ManaLook.SPOUT_TO) / 2;
        Motes.add(f.x(AXIS, AXIS), f.y(mid), f.z(AXIS, AXIS), 1.5, accent, 0.25f * life + 0.05f * p0);
        for (int k = 0; k < n; k++) {
            double[] p = ManaLook.spoutMote(k, n, t);
            Motes.add(f.x(p[0], p[2]), f.y(p[1]), f.z(p[0], p[2]), 0.08, pale, 0.9f * life * (float) p[3]);
        }
        Motes.end();
    }

    // ---- the jets

    /**
     * The jets from the fountains' sockets to the spout: each a band thinning as it goes, near with a halo and drops of
     * light running along it.
     */
    private static void jets(CityFrame f, double t, float life, int tier, int accent, int pale) {
        int n = ManaLook.JET_PIECES[tier];
        int jets = ManaLook.fountains()
            .size();
        boolean nearby = tier == ModuleLook.NEAR;
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        for (int pass = nearby ? 0 : 1; pass < 2; pass++) {
            // the halo first, then the core over it
            double widen = pass == 0 ? HALO : 1;
            tes.setColorRGBA_I(pass == 0 ? accent : pale, CityFrame.alpha255((pass == 0 ? 0.16f : 0.55f) * life));
            for (int i = 0; i < jets; i++) {
                double[] a = ManaLook.jetPoint(i, 0);
                for (int j = 1; j <= n; j++) {
                    double u0 = (j - 1) / (double) n, u1 = j / (double) n;
                    double[] b = ManaLook.jetPoint(i, u1);
                    f.taper(
                        tes,
                        a[0],
                        a[1],
                        a[2],
                        b[0],
                        b[1],
                        b[2],
                        2 * ManaLook.jetHalf(u0) * widen,
                        2 * ManaLook.jetHalf(u1) * widen);
                    a = b;
                }
            }
        }
        tes.draw();
        Shapes.end();
        if (!nearby) return;
        Motes.begin();
        for (int i = 0; i < jets; i++) for (int k = 0; k < ManaLook.JET_DROPS; k++) {
            double u = ManaLook.jetDrop(i, k, t);
            double[] p = ManaLook.jetPoint(i, u);
            Motes.add(
                f.x(p[0], p[2]),
                f.y(p[1]),
                f.z(p[0], p[2]),
                0.1,
                WHITE,
                0.7f * life * (float) Math.sin(Math.PI * u));
        }
        Motes.end();
    }

    // ---- the magic circle

    /**
     * The magic circle turning in the court's floor: the outer circle's open pieces, bright dashes running round them,
     * and near its runes riding it; the inner circle's open pieces and near its ticks turning the other way. All lie a
     * little over the floor's top, and where a basin or an urn stands on the floor the circle passes under it.
     */
    private static void circle(CityFrame f, double t, float life, boolean nearby, int accent, int pale) {
        int tier = nearby ? ModuleLook.NEAR : ModuleLook.MID;
        double y = ManaLook.CIRCLE_Y;
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        for (double[] a : ManaLook.outerPieces(tier)) {
            float k = life * (0.2f + 0.5f * (float) ManaLook.dash((a[0] + a[1]) / 2, t));
            tes.setColorRGBA_I(accent, CityFrame.alpha255(k));
            double[] p = ManaLook.onCircle(ManaLook.OUTER_R, a[0]), q = ManaLook.onCircle(ManaLook.OUTER_R, a[1]);
            flat(tes, f, p[0], p[1], q[0], q[1], y, CIRCLE_HALF);
        }
        tes.setColorRGBA_I(accent, CityFrame.alpha255(0.45f * life));
        for (double[] a : ManaLook.innerPieces(tier)) {
            double[] p = ManaLook.onCircle(ManaLook.INNER_R, a[0]), q = ManaLook.onCircle(ManaLook.INNER_R, a[1]);
            flat(tes, f, p[0], p[1], q[0], q[1], y, CIRCLE_HALF);
        }
        if (nearby) {
            tes.setColorRGBA_I(pale, CityFrame.alpha255(0.6f * life));
            for (int i = 0; i < ManaLook.RUNES; i++) {
                double[][] strokes = ManaLook.rune(i, t);
                if (strokes == null) continue;
                for (double[] s : strokes) flat(tes, f, s[0], s[1], s[2], s[3], y + 0.002, RUNE_HALF);
            }
            for (int j = 0; j < ManaLook.TICKS; j++) {
                double[] s = ManaLook.tick(j, t);
                flat(tes, f, s[0], s[1], s[2], s[3], y + 0.002, RUNE_HALF);
            }
        }
        tes.draw();
        Shapes.end();
    }

    /** A level band between two points of the frame at a height: a line lying on the floor. */
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

    // ---- the sparkles

    /** The mana sparkles drifting from the spout through the colonnade into the garden. */
    private static void sparkles(CityFrame f, double t, float p0, int pale) {
        Motes.begin();
        for (int k = 0; k < ManaLook.SPARKLES; k++) {
            double[] s = ManaLook.sparkle(k, t);
            Motes.add(f.x(s[0], s[2]), f.y(s[1]), f.z(s[0], s[2]), 0.07, pale, 0.85f * p0 * (float) s[3]);
        }
        Motes.end();
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Mana Fountain Array's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
