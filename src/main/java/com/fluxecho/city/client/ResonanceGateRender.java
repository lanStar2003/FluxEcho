package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.WHITE;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.world.World;

import com.fluxecho.FluxEcho;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.city.TileCityModule;
import com.fluxecho.city.TileResonanceGate;
import com.fluxecho.client.Motes;
import com.fluxecho.logic.GateLook;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.logic.ResonanceGateShape;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.SkyLook;
import com.fluxecho.logic.SkyRing;
import com.fluxecho.nexus.ClientTiles;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Resonance Gate's own visuals (0.14.0, CITY.md §4.7 Visuals), on top of what every city module shows
 * ({@link CityRender}); where everything goes and how it moves is {@link GateLook}'s.
 * <ul>
 * <li>Formed: the spire core's conduits and seat have given way to the floating crystal spire over the bridge, an
 * elongated octahedron three wide and sixteen tall, hovering over the roof and slowly turning, a paler crystal glowing
 * inside it; six shards orbit its waist; a column of light rises from the resonance chamber's floor through the glass
 * over the core to its tip. Undocked it burns low.</li>
 * <li>Docked (idle): every eight seconds a resonance pulse, a ring of light, runs out from the spire's waist over the
 * wings.</li>
 * <li>Powered: the pulses come twice as often and the shards fall into line, one level ring turning faster.</li>
 * <li>A landmark ({@link #landmark}, past the range the rest is drawn in): the spire, its shards as points of light and
 * the pulse, drawn to {@link GateLook#LANDMARK} blocks and fading out over the last {@link GateLook#LANDMARK_FADE}. The
 * spire, its shards and the pulse fade by that distance inside the range as well (not by the effects' fade), so the far
 * tier and the landmark draw the same and meet without a seam.</li>
 * </ul>
 * Budgets (CITY.md §1.3, quads a frame on top of {@link CityRender}'s, {@link GateLook#quads}): the spire a crystal
 * (near and mid with its inner glow), the shards six crystals (far six points), the column near and mid, the pulse's
 * ring in {@link GateLook#RING_PIECES} pieces (near with a halo). With the plinth line, the plate and the sockets'
 * samples the gate stays under 2,000 near, 300 mid and 64 far, and the landmark under {@link GateLook#BUDGET}
 * ({@code GateLookTest}).
 * <p>
 * Shader-safe as {@link CityRender}: in its frame ({@link CityFrame}) relative to the camera, quads only with the
 * viewer's normal, no depth writes. Animation by the world time alone, nothing counted, spawned or sent, so a frame
 * drawn again behind a light gate is the same. Each part fails on its own.
 */
@SideOnly(Side.CLIENT)
final class ResonanceGateRender {

    private static final int CRYSTAL = 0xE6FFF6, STEEL = 0x1C3446;
    /** The pulse ring's core and halo widths. */
    private static final double RING_CORE = 0.12, RING_HALO = 0.8;

    private static final int SPIRE = 0, BEAM = 1, PULSE = 2;
    private static final String[] NAMES = { "crystal spire", "core column", "resonance pulse" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    private ResonanceGateRender() {}

    /**
     * Draws the Resonance Gate's own visuals; {@code p0} is its distance fade times how far it has formed, {@code cam}
     * the camera in its frame, {@code tier} how much to draw ({@link ModuleLook#tier}), {@code appear} how far it has
     * formed.
     */
    static void draw(TileResonanceGate g, CityFrame f, double t, float p0, double[] cam, int tier, float appear) {
        boolean docked = CityRender.docked(g), on = CityRender.on(g);
        float life = on ? 1f : docked ? 0.6f : 0.3f;
        // the landmark's parts fade by their own distance, so they carry on unbroken into the landmark's drawing
        float p = appear * GateLook.landmarkFade(spireDistance(cam));
        if (!OFF[SPIRE]) try {
            spire(g, f, t, p, life, tier, on);
        } catch (Throwable x) {
            CityRender.recover();
            fail(SPIRE, x);
        } finally {
            CityRender.close();
        }
        if (tier != ModuleLook.FAR && !OFF[BEAM]) try {
            beam(g, f, t, p0 * life, tier == ModuleLook.NEAR);
        } catch (Throwable x) {
            CityRender.recover();
            fail(BEAM, x);
        } finally {
            CityRender.close();
        }
        if (docked && !OFF[PULSE]) try {
            pulse(g, f, t, p * life, tier, on);
        } catch (Throwable x) {
            CityRender.recover();
            fail(PULSE, x);
        } finally {
            CityRender.close();
        }
    }

    /**
     * The landmark alone, for a camera past the range the rest is drawn in ({@code NexusRender} calls this or
     * {@link #draw}, never both): the spire and its shards as points of light and, while docked, the pulse, as the far
     * tier draws them. At most {@link GateLook#landmarkQuads} quads.
     */
    static void landmark(TileResonanceGate g, double t) {
        if (!g.formed()) return;
        boolean docked = CityRender.docked(g), on = CityRender.on(g);
        float life = on ? 1f : docked ? 0.6f : 0.3f;
        CityFrame f = new CityFrame(g);
        float p = GateLook.landmarkFade(spireDistance(CityFrame.camera(g)));
        if (p <= 0.01f) return;
        if (!OFF[SPIRE]) try {
            spire(g, f, t, p, life, ModuleLook.FAR, on);
        } catch (Throwable x) {
            CityRender.recover();
            fail(SPIRE, x);
        } finally {
            CityRender.close();
        }
        if (docked && !OFF[PULSE]) try {
            pulse(g, f, t, p * life, ModuleLook.FAR, on);
        } catch (Throwable x) {
            CityRender.recover();
            fail(PULSE, x);
        } finally {
            CityRender.close();
        }
    }

    /** How far the camera ({@code p}, in the gate's frame) is from the spire's waist, in blocks. */
    private static double spireDistance(double[] p) {
        double dx = p[0] - GateLook.AXIS_X, dy = p[1] - GateLook.WAIST, dz = p[2] - GateLook.AXIS_Z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---- the spire

    /**
     * The spire: the crystal turning slowly (near and mid a paler one glowing inside it the other way), its six shards
     * (near and mid as small crystals, near each with a glow; far as points of light) and a glow round its waist.
     */
    private static void spire(TileResonanceGate g, CityFrame f, double t, float p, float life, int tier, boolean on) {
        if (p <= 0.01f) return;
        int accent = g.moduleColor(), dark = Shapes.mix(accent, STEEL, 0.45), light = Shapes.mix(CRYSTAL, accent, 0.2);
        boolean far = tier == ModuleLook.FAR, nearby = tier == ModuleLook.NEAR;
        // under the weather station's platform at the lowest sky datum, or a block floating within its reach, the spire
        // is shorter (0.16.0)
        double top = spireTop(g, f), halfH = GateLook.halfHeight(top);
        double[] s = GateLook.spire(t, top);
        double cx = f.x(GateLook.AXIS_X, GateLook.AXIS_Z), cy = f.y(s[0]), cz = f.z(GateLook.AXIS_X, GateLook.AXIS_Z);
        float a = (0.7f * life + 0.2f) * p;
        Shapes.begin(false);
        Shapes.crystal(cx, cy, cz, GateLook.SPIRE_HALF_W, halfH, s[1], light, dark, a);
        double[][] shards = new double[GateLook.SHARDS][];
        for (int k = 0; k < GateLook.SHARDS; k++) {
            shards[k] = GateLook.shard(k, t, on);
            if (far) continue;
            double[] q = shards[k];
            Shapes.crystal(
                f.x(q[0], q[2]),
                f.y(q[1]),
                f.z(q[0], q[2]),
                GateLook.SHARD_HALF_W,
                GateLook.SHARD_HALF_H,
                q[3],
                light,
                dark,
                a);
        }
        if (!far) {
            Shapes.additive(true);
            Shapes.crystal(
                cx,
                cy,
                cz,
                GateLook.SPIRE_HALF_W * 0.45,
                halfH * 0.8,
                -1.5 * s[1],
                WHITE,
                accent,
                0.45f * life * p);
        }
        Shapes.end();
        Motes.begin();
        double dist = Math.sqrt(cx * cx + cy * cy + cz * cz);
        Motes.add(cx, cy, cz, 3 + dist * 0.004, accent, (0.25f * life + 0.05f) * p);
        if (far || nearby) for (double[] q : shards) Motes.add(
            f.x(q[0], q[2]),
            f.y(q[1]),
            f.z(q[0], q[2]),
            far ? 0.6 + dist * 0.003 : 0.55,
            accent,
            (far ? 0.6f : 0.35f) * life * p);
        Motes.end();
    }

    // ---- the column

    /**
     * The column of light in place of the spire core: from the resonance chamber's floor through the dissolved
     * conduits and seat and the glass over them up to the spire's foot; near, motes rising in it and a glow at the
     * seat.
     */
    private static void beam(TileResonanceGate g, CityFrame f, double t, float p, boolean nearby) {
        if (p <= 0.01f) return;
        int accent = g.moduleColor();
        double top = spireTop(g, f);
        double foot = GateLook.spire(t, top)[0] - GateLook.halfHeight(top);
        double x = f.x(GateLook.AXIS_X, GateLook.AXIS_Z), z = f.z(GateLook.AXIS_X, GateLook.AXIS_Z);
        Shapes.begin(true);
        Shapes.beam(
            x,
            f.y(GateLook.BEAM_FROM),
            z,
            f.y(foot),
            GateLook.BEAM_R,
            Shapes.mix(accent, WHITE, 0.3),
            0.5f * p,
            0.3f * p);
        Shapes.end();
        if (!nearby) return;
        Motes.begin();
        Motes.add(x, f.y(GateLook.BEAM_FROM + 2.5), z, 0.9, accent, 0.3f * p);
        for (int k = 0; k < GateLook.BEAM_MOTES; k++) {
            double[] m = GateLook.beamMote(k, t, foot);
            Motes.add(x, f.y(m[0]), z, 0.12, WHITE, 0.8f * p * (float) m[1]);
        }
        Motes.end();
    }

    // ---- the pulse

    /**
     * The resonance pulse: a level ring of light running out from the spire's waist (one every eight seconds docked,
     * twice as often powered), fading as it goes; near with a soft halo.
     */
    private static void pulse(TileResonanceGate g, CityFrame f, double t, float p, int tier, boolean on) {
        double[] ring = GateLook.pulse(t, on);
        if (ring == null || p <= 0.01f) return;
        float a = p * (float) ring[1];
        if (a <= 0.01f) return;
        int accent = g.moduleColor();
        int n = GateLook.RING_PIECES[tier];
        double y = GateLook.spire(t)[0];
        boolean nearby = tier == ModuleLook.NEAR;
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        for (int i = 0; i < n; i++) {
            double[] u = GateLook.onRing(ring[0], 2 * Math.PI * i / n),
                v = GateLook.onRing(ring[0], 2 * Math.PI * (i + 1) / n);
            tes.setColorRGBA_I(Shapes.mix(accent, WHITE, 0.35), CityFrame.alpha255(0.85f * a));
            f.line(tes, u[0], y, u[1], v[0], y, v[1], RING_CORE);
            if (nearby) {
                tes.setColorRGBA_I(accent, CityFrame.alpha255(0.2f * a));
                f.line(tes, u[0], y, u[1], v[0], y, v[1], RING_HALO);
            }
        }
        tes.draw();
        Shapes.end();
    }

    /**
     * Where the spire's top stands in the gate's frame ({@link GateLook#spireTop}, 0.16.0): under the deck of a sky
     * module whose light pillar falls on the spire (its platform's middle within {@link SkyLook#LINK_REACH} of the
     * spire's axis, as {@code SkyRender} links the two), or under the first block standing in the spire's column
     * ({@link #blockOver}), whichever is lower; else the full spire. The column covers what has no controller to ask:
     * a platform whose floating foundation is laid long before its controller, and one whose controller was broken.
     * The drawing of 0.15.0 wherever nothing floats over the gate.
     */
    static double spireTop(TileResonanceGate g, CityFrame f) {
        World w = g.getWorldObj();
        double sx = f.x(GateLook.AXIS_X, GateLook.AXIS_Z) + RenderManager.renderPosX,
            sz = f.z(GateLook.AXIS_X, GateLook.AXIS_Z) + RenderManager.renderPosZ;
        double deck = 0;
        for (TileMultiblock o : ClientTiles.all()) {
            if (!(o instanceof TileCityModule m) || o.getWorldObj() != w || o.isInvalid()) continue;
            ModuleSpec spec = ModuleSpecs.get(m.moduleKey());
            if (spec == null || spec.family != SiteFamily.SKY) continue;
            int[] mid = m.worldOf(SkyRing.MID, 0, SkyRing.MID);
            if (Math.hypot(mid[0] + 0.5 - sx, mid[2] + 0.5 - sz) > SkyLook.LINK_REACH) continue;
            double over = f.local(mid[1]);
            if (deck <= 0 || over < deck) deck = over;
        }
        return GateLook.spireTop(GateLook.lowerDeck(deck, blockOver(g, w)));
    }

    /**
     * The lowest layer of the spire's axis column, in the gate's frame, where a block that stops movement stands within
     * the spire's reach ({@link GateLook#OVER_FROM} to {@link GateLook#OVER_TO}), read from the client's world each
     * time as {@code SkyRender} reads its pillar's column (nothing is kept); 0 when the column is clear there or not
     * here. Snow and plants stop nothing, so they never shorten the spire.
     */
    private static int blockOver(TileResonanceGate g, World w) {
        if (w == null) return 0;
        for (int layer = GateLook.OVER_FROM; layer <= GateLook.OVER_TO; layer++) {
            int[] at = g.worldOf(ResonanceGateShape.MID, layer, ResonanceGateShape.CORE_Z);
            if (!w.blockExists(at[0], at[1], at[2])) continue;
            if (w.getBlock(at[0], at[1], at[2])
                .getMaterial()
                .blocksMovement()) return layer;
        }
        return 0;
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Resonance Gate's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
