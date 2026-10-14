package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.AMBER;
import static com.fluxecho.client.FluxDraw.WHITE;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.fluxecho.FluxEcho;
import com.fluxecho.city.TileCityModule;
import com.fluxecho.city.TileDropletHangar;
import com.fluxecho.city.TileObservatory;
import com.fluxecho.city.TileResonanceGate;
import com.fluxecho.city.TileSkySails;
import com.fluxecho.city.TileWeatherStation;
import com.fluxecho.client.Motes;
import com.fluxecho.logic.GateLook;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.logic.SkyLook;
import com.fluxecho.logic.SkyRing;
import com.fluxecho.nexus.ClientTiles;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * What every sky module shows of its platform (0.16.0, CITY.md §4.12 "Sky-ring visuals shared"), on top of what every
 * city module shows ({@link CityRender}) and under its own visuals; where it goes is {@link SkyLook}'s. The platform's
 * underside glows by itself (the floating foundation's animated face, in the chunk mesh).
 * <ul>
 * <li>Formed: a light pillar falls from under the platform's middle to the ground under it (the first block standing in
 * its column, read from the client's world each time: nothing is kept), brightest where it meets the ground and fading
 * with height, a core of light in it, motes sinking down it and a glow where it lands. Over the gate it ends on the
 * Resonance Gate's floating spire when one stands under it, a resonance link in both colours. A landmark (CITY.md §1.3
 * "sky pillars"): past the effects' range {@link #landmark} draws the column, its core and the glow at its foot to
 * {@link SkyLook#LANDMARK} blocks, faded by its own distance in range too, so the two meet without a seam.</li>
 * <li>The catwalk's thin lit edges: under the outer edges of its deck and as two strips on it beside the rails, a row
 * at
 * a time where the row's deck stands in the world (a broken catwalk shows no line in the air), a pulse running out
 * from the pod to the platform; mid the same lines without their halos; none far.</li>
 * </ul>
 * In the module's colour while docked and powered, amber breathing while docked without power, a dim grey-blue
 * otherwise. Budgets (CITY.md §1.3, {@link SkyLook#quads}): the pillar 16 near and mid, 9 far and as the landmark; the
 * catwalk 66 near, 44 mid.
 * <p>
 * Shader-safe as {@link CityRender}: in the module's frame ({@link CityFrame}) relative to the camera, quads only, a
 * normal towards the viewer, additive light without depth writes, animation by the world time alone. Each part fails
 * on its own.
 */
@SideOnly(Side.CLIENT)
final class SkyRender {

    private static final int IDLE = 0x4A6A80;

    private static final int PILLAR = 0, WALK = 1;
    private static final String[] NAMES = { "light pillar", "catwalk edges" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    private SkyRender() {}

    /** Whether a module stands on a floating platform of the sky ring. */
    static boolean sky(TileCityModule m) {
        return m instanceof TileWeatherStation || m instanceof TileSkySails
            || m instanceof TileObservatory
            || m instanceof TileDropletHangar;
    }

    /**
     * Draws the platform's shared visuals of a formed sky module; {@code fade} is its effects' distance fade (the
     * pillar
     * fades by its own distance instead, as the landmark does), {@code appear} how far it has formed.
     */
    static void draw(TileCityModule m, CityFrame f, double t, float fade, float appear, int tier) {
        if (!OFF[PILLAR]) try {
            pillar(m, f, t, appear, tier);
        } catch (Throwable x) {
            CityRender.recover();
            fail(PILLAR, x);
        } finally {
            CityRender.close();
        }
        if (tier != ModuleLook.FAR && !OFF[WALK]) try {
            walk(m, f, t, fade * appear, tier == ModuleLook.NEAR);
        } catch (Throwable x) {
            CityRender.recover();
            fail(WALK, x);
        } finally {
            CityRender.close();
        }
    }

    /** The pillar alone, as the landmark past the effects' range ({@code NexusRender} calls this or {@link #draw}). */
    static void landmark(TileCityModule m, double t) {
        if (!m.formed() || OFF[PILLAR]) return;
        try {
            pillar(m, new CityFrame(m), t, 1f, ModuleLook.FAR);
        } catch (Throwable x) {
            CityRender.recover();
            fail(PILLAR, x);
        } finally {
            CityRender.close();
        }
    }

    /** The colour of the module's light in its dock state, and how bright it burns. */
    static int glow(TileCityModule m, double t, float[] life) {
        boolean docked = CityRender.docked(m), on = CityRender.on(m);
        life[0] = on ? 1f : docked ? 0.45f + 0.15f * (float) Math.sin(t * 0.1) : 0.3f;
        return on ? m.moduleColor() : docked ? AMBER : IDLE;
    }

    // ---- the light pillar

    /**
     * The light pillar under the platform's middle down to the ground under it, or to the Resonance Gate's spire when
     * one floats under it (a resonance link); its distance fade by its own middle.
     */
    private static void pillar(TileCityModule m, CityFrame f, double t, float appear, int tier) {
        World w = m.getWorldObj();
        if (w == null) return;
        float[] life = new float[1];
        int rgb = glow(m, t, life);
        double cx = f.x(SkyLook.CX, SkyLook.CZ), cz = f.z(SkyLook.CX, SkyLook.CZ), top = SkyLook.PILLAR_TOP;
        double foot = SkyLook.foot(standing(m, w));
        int link = 0;
        double[] spire = spire(m, w, cx, cz);
        if (spire != null && f.local(spire[1]) > foot && f.local(spire[1]) < top) {
            foot = f.local(spire[1]);
            link = (int) spire[3];
        }
        double yTop = f.y(top), yFoot = f.y(foot), mid = (yTop + yFoot) / 2;
        float a = appear * life[0] * SkyLook.landmarkFade(Math.sqrt(cx * cx + mid * mid + cz * cz));
        if (a <= 0.01f) return;
        int core = link != 0 ? Shapes.mix(rgb, link, 0.5) : Shapes.mix(rgb, WHITE, 0.35);
        Shapes.begin(true);
        Shapes.beam(cx, yFoot, cz, yTop, SkyLook.PILLAR_R, rgb, SkyLook.FOOT_ALPHA * a, SkyLook.HEAD_ALPHA * a);
        Shapes.beam(
            cx,
            yFoot,
            cz,
            yTop,
            SkyLook.CORE_R,
            core,
            1.4f * SkyLook.FOOT_ALPHA * a,
            0.5f * SkyLook.HEAD_ALPHA * a);
        Shapes.end();
        Motes.begin();
        double dist = Math.sqrt(cx * cx + yFoot * yFoot + cz * cz);
        Motes.add(cx, yFoot + 0.2, cz, 3.2 + dist * 0.004, link != 0 ? link : rgb, 0.45f * a);
        if (tier != ModuleLook.FAR) {
            Motes.add(cx, yTop - 0.15, cz, 4.5, rgb, 0.25f * a);
            for (int k = 0; k < SkyLook.FALL_MOTES; k++) {
                double[] q = SkyLook.fallMote(k, t, foot);
                double ox = 1.6 * Math.sin(k * 2.3 + t * 0.01), oz = 1.6 * Math.cos(k * 1.9 + t * 0.013);
                Motes.add(
                    f.x(SkyLook.CX + ox, SkyLook.CZ + oz),
                    f.y(q[0]),
                    f.z(SkyLook.CX + ox, SkyLook.CZ + oz),
                    0.35,
                    link != 0 && k % 2 == 1 ? link : core,
                    0.6f * a * (float) q[1]);
            }
        }
        Motes.end();
    }

    /**
     * The layer of the highest block standing in the pillar's column under the deck, in the module's frame (below
     * 0), or 1 when none was found within {@link SkyLook#SCAN}, or the column's chunk is not here.
     */
    private static int standing(TileCityModule m, World w) {
        int[] at = m.worldOf(SkyRing.MID, 0, SkyRing.MID);
        Chunk ch = w.getChunkFromBlockCoords(at[0], at[2]);
        if (ch == null || ch.isEmpty()) return 1;
        for (int dy = 1; dy <= SkyLook.SCAN; dy++) {
            int y = at[1] - dy;
            if (y < 0) return 1;
            Block b = w.getBlock(at[0], y, at[2]);
            Material mat = b.getMaterial();
            if (mat.blocksMovement() || mat.isLiquid()) return -dy;
        }
        return 1;
    }

    /**
     * The Resonance Gate's floating spire under the pillar, when one stands formed within {@link SkyLook#LINK_REACH}
     * blocks of its column: {x, y (the spire's tip, in the world), z, its colour}; null otherwise.
     */
    private static double[] spire(TileCityModule m, World w, double cx, double cz) {
        for (TileMultiblock o : ClientTiles.all()) {
            if (!(o instanceof TileResonanceGate g) || o.getWorldObj() != w || o.isInvalid() || !g.formed()) continue;
            CityFrame gf = new CityFrame(g);
            double sx = gf.x(GateLook.AXIS_X, GateLook.AXIS_Z), sz = gf.z(GateLook.AXIS_X, GateLook.AXIS_Z);
            if (Math.hypot(sx - cx, sz - cz) > SkyLook.LINK_REACH) continue;
            // the tip's height in the world: camera-relative plus the camera's; under a deck it would reach (F 28) the
            // spire is drawn shorter, so the tip is read from the same rule the gate draws it by (0.16.0)
            double top = GateLook.spireTop(gf.local(m.worldOf(SkyRing.MID, 0, SkyRing.MID)[1]));
            double tip = gf.y(top + GateLook.HOVER) + RenderManager.renderPosY;
            return new double[] { sx, tip, sz, g.moduleColor() };
        }
        return null;
    }

    // ---- the catwalk

    /**
     * The catwalk's lit edges: under its deck's two outer edges and two strips on its deck beside the rails, each row
     * where the deck cell on that side stands, a pulse running from the pod out to the platform; near a soft halo under
     * the under edges.
     */
    private static void walk(TileCityModule m, CityFrame f, double t, float p, boolean nearby) {
        World w = m.getWorldObj();
        if (w == null || p <= 0.01f) return;
        float[] life = new float[1];
        int rgb = glow(m, t, life);
        float a = p * life[0];
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        for (int[] row : SkyLook.walkRows()) {
            int z = row[0];
            float k = a * (0.35f + 0.55f * (float) SkyLook.walkPulse(z, t));
            int col = Shapes.mix(rgb, WHITE, 0.3 * SkyLook.walkPulse(z, t));
            for (int side = 0; side < 2; side++) {
                int cell = side == 0 ? row[1] : row[2];
                if (!stands(m, w, cell, z)) continue;
                double edge = side == 0 ? SkyLook.WALK_X0 : SkyLook.WALK_X1;
                double strip = side == 0 ? SkyLook.WALK_X0 + SkyLook.STRIP_IN : SkyLook.WALK_X1 - SkyLook.STRIP_IN;
                tes.setColorRGBA_I(col, CityFrame.alpha255(k));
                f.line(tes, edge, SkyLook.UNDER_Y, z, edge, SkyLook.UNDER_Y, z + 1, 0.05);
                tes.setColorRGBA_I(col, CityFrame.alpha255(0.8f * k));
                f.line(tes, strip, SkyLook.TOP_Y, z, strip, SkyLook.TOP_Y, z + 1, 0.04);
                if (nearby) {
                    tes.setColorRGBA_I(rgb, CityFrame.alpha255(0.2f * k));
                    f.line(tes, edge, SkyLook.UNDER_Y - 0.1, z, edge, SkyLook.UNDER_Y - 0.1, z + 1, 0.4);
                }
            }
        }
        tes.draw();
        Shapes.end();
    }

    /** Whether the catwalk's deck cell (x, deck, z) of the module's frame holds a block in the client's world. */
    private static boolean stands(TileCityModule m, World w, int x, int z) {
        int[] at = m.worldOf(x, 0, z);
        return !w.isAirBlock(at[0], at[1], at[2]);
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The sky ring's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
