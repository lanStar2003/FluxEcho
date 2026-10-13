package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.WHITE;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.fluxecho.FluxEcho;
import com.fluxecho.city.TileDualFoilVault;
import com.fluxecho.logic.CanvasLook;
import com.fluxecho.logic.DualFoilVaultShape;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.logic.VaultLook;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Dual-foil Vault's own visuals (0.14.0, CITY.md §4.2 Visuals), on top of what every city module shows
 * ({@link CityRender}) and the paintings of its canvases ({@link CanvasRender}); where everything goes is
 * {@link VaultLook}'s.
 * <ul>
 * <li>Forming: a parchment-white wash rises up the facade canvas behind a bright line, over the plane rising through
 * the box that every module shows ({@link #sweep}).</li>
 * <li>Formed: the facade canvas over the portal shows a collage of the items the vault holds, the most frequent in the
 * large middle tile ({@link #collage}); far off each tile is only a patch of its item's colour. Inside, the skylights
 * let static shafts of light down into the nave, slanting to the front, as bright as the time of day; each leaves a
 * pool of light on the floor or deck it lands on.</li>
 * <li>Docked: the facade's lit frame breathes in parchment, and the foil's rim shows a faint line.</li>
 * <li>Powered: the frame's light holds steady with a glint running round it, and the foil's rim glows like an event
 * horizon: a white-hot line round the black canvas with a bright arc running round it, a soft halo out over its frame,
 * the canvas darkening in from the rim. (Items flattening into the foil come with its function.)</li>
 * </ul>
 * Budgets (CITY.md §1.3, quads a frame on top of {@link CityRender}'s, {@link VaultLook#quads}): the collage nine
 * paintings (far nine patches), the shafts near, the frame's lines and the rim's pieces (near one a block with their
 * halos). With the plinth line, the plate and the canvases' paintings ({@link ModuleLook#baseQuads},
 * {@link CanvasLook#quads}) the vault stays under 2,000 near, 300 mid and 64 far ({@code VaultLookTest}).
 * <p>
 * Shader-safe as {@link CityRender}: in its frame ({@link CityFrame}) relative to the camera, quads only with the
 * viewer's normal, no depth writes, texturing switched on by hand. Animation by the world time alone (the shafts'
 * brightness by the time of day), nothing counted, spawned or sent, so a frame drawn again behind a light gate is the
 * same. Each part fails on its own.
 */
@SideOnly(Side.CLIENT)
final class VaultRender {

    /** Daylight, warm white, and the dark the foil deepens to. */
    private static final int DAY = 0xFFF4DC, VOID = 0x000000;
    /** The frame's and the rim's lines' widths, and their halos'. */
    private static final double LINE = 0.06, LINE_HALO = 0.35, RIM_LINE = 0.07;

    private static final int COLLAGE = 0, SHAFTS = 1, FRAME = 2, RIM = 3, SWEEP = 4;
    private static final String[] NAMES = { "facade collage", "skylight shafts", "facade frame", "foil rim",
        "facade sweep" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    private VaultRender() {}

    /**
     * Draws the Dual-foil Vault's own visuals; {@code p0} is its distance fade times how far it has formed,
     * {@code tier} how much to draw ({@link ModuleLook#tier}).
     */
    static void draw(TileDualFoilVault v, CityFrame f, double t, float p0, int tier) {
        if (p0 <= 0.01f) return;
        boolean docked = CityRender.docked(v), on = CityRender.on(v), nearby = tier == ModuleLook.NEAR;
        int accent = v.moduleColor();
        if (!OFF[COLLAGE]) try {
            collage(v, f, p0, tier);
        } catch (Throwable x) {
            CityRender.recover();
            fail(COLLAGE, x);
        } finally {
            CityRender.close();
        }
        if (nearby && !OFF[SHAFTS]) try {
            shafts(v, f, p0, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(SHAFTS, x);
        } finally {
            CityRender.close();
        }
        if (!docked || tier == ModuleLook.FAR) return;
        if (!OFF[FRAME]) try {
            frame(f, t, p0, nearby, on, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(FRAME, x);
        } finally {
            CityRender.close();
        }
        if (!OFF[RIM]) try {
            rim(f, t, p0, nearby, on, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(RIM, x);
        } finally {
            CityRender.close();
        }
    }

    // ---- the collage

    /**
     * The facade's collage: the vault's items ranked by how many of its units hold each ({@link VaultLook#ranking}),
     * painted on the tiles in rank order; near and mid whole paintings, far each tile's patch of colour.
     */
    private static void collage(TileDualFoilVault v, CityFrame f, float p, int tier) {
        int slots = v.samples()
            .getSlots();
        List<String> keys = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) keys.add(key(v.sample(i)));
        int[] ranks = VaultLook.ranking(keys);
        if (ranks.length == 0) return;
        List<CanvasLook.Field> tiles = new ArrayList<>(ranks.length);
        List<ItemStack> stacks = new ArrayList<>(ranks.length);
        boolean[] whole = new boolean[ranks.length];
        for (int r = 0; r < ranks.length; r++) {
            tiles.add(VaultLook.tileField(r));
            stacks.add(v.sample(ranks[r]));
            whole[r] = tier != ModuleLook.FAR;
        }
        CanvasRender.paint(f, tiles, stacks, whole, p, new int[] { Paintings.BUILDS });
    }

    /** An item's kind as the collage counts it: the item and its damage, or null for nothing. */
    private static String key(ItemStack s) {
        if (s == null || s.getItem() == null) return null;
        return Item.getIdFromItem(s.getItem()) + ":" + s.getItemDamage();
    }

    // ---- the shafts

    /**
     * The skylight shafts: each piece's two broad faces slanting down to the front, bright at the glass and fading to
     * a quarter where they stop, the two ends of each strip, and a pool of light where a shaft lands on a floor; all as
     * bright as the time of day.
     */
    private static void shafts(TileDualFoilVault v, CityFrame f, float p, int accent) {
        World w = v.getWorldObj();
        double day = VaultLook.daylight(w == null ? 6000 : w.getWorldTime());
        float top = (float) (0.16 * day) * p, low = top * 0.25f;
        if (top <= 0.005f) return;
        int rgb = Shapes.mix(DAY, accent, 0.3);
        double yTop = VaultLook.SHAFT_TOP;
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        List<double[]> all = VaultLook.shafts();
        for (int i = 0; i < all.size(); i++) {
            double[] s = all.get(i);
            double x0 = s[0], x1 = s[1], z = s[2], yb = s[3];
            double zt = VaultLook.shaftFront(z, yTop), zb = VaultLook.shaftFront(z, yb);
            for (int side = 0; side < 2; side++) {
                tes.setColorRGBA_I(rgb, CityFrame.alpha255(top));
                f.vertex(tes, x0, yTop, zt + side);
                f.vertex(tes, x1, yTop, zt + side);
                tes.setColorRGBA_I(rgb, CityFrame.alpha255(low));
                f.vertex(tes, x1, yb, zb + side);
                f.vertex(tes, x0, yb, zb + side);
            }
            // the strip's two ends: the first piece's left end and the last piece's right end
            boolean first = i == 0 || all.get(i - 1)[2] != z, last = i + 1 == all.size() || all.get(i + 1)[2] != z;
            for (int end = 0; end < 2; end++) {
                if (end == 0 ? !first : !last) continue;
                double x = end == 0 ? x0 : x1;
                tes.setColorRGBA_I(rgb, CityFrame.alpha255(top));
                f.vertex(tes, x, yTop, zt);
                f.vertex(tes, x, yTop, zt + 1);
                tes.setColorRGBA_I(rgb, CityFrame.alpha255(low));
                f.vertex(tes, x, yb, zb + 1);
                f.vertex(tes, x, yb, zb);
            }
            if (s[4] > 0) {
                tes.setColorRGBA_I(rgb, CityFrame.alpha255(0.5f * top));
                double y = yb + 0.02;
                f.vertex(tes, x0, y, zb);
                f.vertex(tes, x1, y, zb);
                f.vertex(tes, x1, y, zb + 1);
                f.vertex(tes, x0, y, zb + 1);
            }
        }
        tes.draw();
        Shapes.end();
    }

    // ---- the frame

    /**
     * The facade's lit frame: a line of parchment light along its middle on the front wall's outer face, breathing
     * while docked, steady with a glint running round it while powered; near with a soft halo.
     */
    private static void frame(CityFrame f, double t, float p, boolean nearby, boolean on, int accent) {
        double[][] lines = VaultLook.frame();
        double z = VaultLook.FRONT - 0.01, glint = VaultLook.glint(t);
        float base = (on ? 0.45f : 0.2f + 0.2f * (float) VaultLook.breath(t)) * p;
        double total = 0;
        for (double[] l : lines) total += Math.hypot(l[2] - l[0], l[3] - l[1]);
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        double run = 0;
        for (double[] l : lines) {
            double len = Math.hypot(l[2] - l[0], l[3] - l[1]);
            float k = base;
            if (on) {
                // the glint: brighter on the side it runs along
                double mid = (run + len / 2) / total, d = Math.abs(mid - glint);
                d = Math.min(d, 1 - d);
                k += 0.35f * p * (float) Math.exp(-d * d / 0.01);
            }
            run += len;
            tes.setColorRGBA_I(Shapes.mix(accent, WHITE, 0.3), CityFrame.alpha255(k));
            f.line(tes, l[0], l[1], z, l[2], l[3], z, LINE);
            if (nearby) {
                tes.setColorRGBA_I(accent, CityFrame.alpha255(0.3f * k));
                f.line(tes, l[0], l[1], z, l[2], l[3], z, LINE_HALO);
            }
        }
        tes.draw();
        Shapes.end();
    }

    // ---- the foil's rim

    /**
     * The foil's rim: docked a faint parchment line round the black canvas; powered the event horizon: the canvas
     * darkening in from the rim (near), then a white-hot line with a bright arc running round it and a halo out over
     * the frame. Near a piece a block, mid each side in its pieces at its mean brightness.
     */
    private static void rim(CityFrame f, double t, float p, boolean nearby, boolean on, int accent) {
        double z = VaultLook.BACK;
        int hot = Shapes.mix(accent, WHITE, 0.6);
        if (on && nearby) darkness(f, z, p);
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        if (nearby) {
            for (int i = 0; i < VaultLook.RIM_PIECES; i++) {
                double s0 = i, s1 = i + 1;
                double[] a = VaultLook.rimPoint(s0), b = VaultLook.rimPoint(s1);
                double h = on ? VaultLook.horizon(s0 + 0.5, t) : 0;
                float k = on ? (0.3f + 0.65f * (float) h) * p : 0.18f * p;
                tes.setColorRGBA_I(on ? Shapes.mix(accent, WHITE, 0.3 + 0.6 * h) : accent, CityFrame.alpha255(k));
                f.line(tes, a[0], a[1], z, b[0], b[1], z, RIM_LINE);
                if (on) halo(tes, f, a, b, z, Shapes.mix(accent, hot, h), 0.45f * k);
            }
        } else {
            double[][] corners = { VaultLook.rimPoint(0), VaultLook.rimPoint(VaultLook.FOIL_W),
                VaultLook.rimPoint(VaultLook.FOIL_W + VaultLook.FOIL_H),
                VaultLook.rimPoint(2 * VaultLook.FOIL_W + VaultLook.FOIL_H) };
            float k = (on ? 0.6f : 0.18f) * p;
            for (int i = 0; i < 4; i++) {
                double[] a = corners[i], b = corners[(i + 1) % 4];
                tes.setColorRGBA_I(on ? hot : accent, CityFrame.alpha255(k));
                f.line(tes, a[0], a[1], z, b[0], b[1], z, RIM_LINE);
                if (on) {
                    tes.setColorRGBA_I(accent, CityFrame.alpha255(0.3f * k));
                    f.line(tes, a[0], a[1], z, b[0], b[1], z, LINE_HALO * 2);
                }
            }
        }
        tes.draw();
        Shapes.end();
    }

    /**
     * A piece of the rim's halo: a band on the back wall from the rim out over the frame ({@link VaultLook#HALO}),
     * bright at the rim and gone at its outer edge.
     */
    private static void halo(Tessellator tes, CityFrame f, double[] a, double[] b, double z, int rgb, float k) {
        double mx = (a[0] + b[0]) / 2 - (VaultLook.FOIL_X + VaultLook.FOIL_W / 2.0),
            my = (a[1] + b[1]) / 2 - (VaultLook.FOIL_Y + VaultLook.FOIL_H / 2.0);
        // out from the field: square to the piece, away from the field's middle
        double dx = b[0] - a[0], dy = b[1] - a[1], l = Math.hypot(dx, dy);
        double ox = dy / l, oy = -dx / l;
        if (ox * mx + oy * my < 0) {
            ox = -ox;
            oy = -oy;
        }
        double o = VaultLook.HALO;
        tes.setColorRGBA_I(rgb, CityFrame.alpha255(k));
        f.vertex(tes, a[0], a[1], z);
        f.vertex(tes, b[0], b[1], z);
        tes.setColorRGBA_I(rgb, 0);
        f.vertex(tes, b[0] + ox * o, b[1] + oy * o, z);
        f.vertex(tes, a[0] + ox * o, a[1] + oy * o, z);
    }

    /**
     * The dark of the powered foil deepening in from its rim: four bands blended over the canvas, black at the rim and
     * clear {@link VaultLook#DARK} in, their corners mitred.
     */
    private static void darkness(CityFrame f, double z, float p) {
        double x0 = VaultLook.FOIL_X, y0 = VaultLook.FOIL_Y, x1 = x0 + VaultLook.FOIL_W, y1 = y0 + VaultLook.FOIL_H,
            d = VaultLook.DARK;
        double[][] outer = { { x0, y0 }, { x1, y0 }, { x1, y1 }, { x0, y1 } },
            inner = { { x0 + d, y0 + d }, { x1 - d, y0 + d }, { x1 - d, y1 - d }, { x0 + d, y1 - d } };
        int edge = CityFrame.alpha255(0.55f * p);
        Shapes.begin(false);
        Tessellator tes = CityFrame.start();
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            tes.setColorRGBA_I(VOID, edge);
            f.vertex(tes, outer[i][0], outer[i][1], z);
            f.vertex(tes, outer[j][0], outer[j][1], z);
            tes.setColorRGBA_I(VOID, 0);
            f.vertex(tes, inner[j][0], inner[j][1], z);
            f.vertex(tes, inner[i][0], inner[i][1], z);
        }
        tes.draw();
        Shapes.end();
    }

    // ---- the forming sweep

    /**
     * While the vault forms ({@code level} the sweep's height in the world, {@code k} how strongly it shows): a
     * parchment-white wash rising up the facade canvas behind a bright line across its frame.
     */
    static void sweep(TileDualFoilVault v, double level, float k) {
        if (k <= 0 || OFF[SWEEP]) return;
        try {
            CityFrame f = new CityFrame(v);
            double y = f.local(level);
            double[] band = VaultLook.sweep(y);
            if (band == null) return;
            double x0 = DualFoilVaultShape.FACADE_X0, x1 = DualFoilVaultShape.FACADE_X1 + 1, z = VaultLook.FRONT - 0.02;
            int paper = Shapes.mix(v.moduleColor(), WHITE, 0.4);
            Shapes.begin(true);
            Tessellator tes = CityFrame.start();
            tes.setColorRGBA_I(paper, 0);
            f.vertex(tes, x0, band[0], z);
            f.vertex(tes, x1, band[0], z);
            tes.setColorRGBA_I(paper, CityFrame.alpha255(0.5f * k));
            f.vertex(tes, x1, band[1], z);
            f.vertex(tes, x0, band[1], z);
            if (y <= DualFoilVaultShape.FACADE_Y1 + 1) {
                tes.setColorRGBA_I(WHITE, CityFrame.alpha255(0.85f * k));
                f.line(tes, x0, y, z, x1, y, z, 0.12);
            }
            tes.draw();
            Shapes.end();
        } catch (Throwable x) {
            CityRender.recover();
            fail(SWEEP, x);
        } finally {
            CityRender.close();
        }
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Dual-foil Vault's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
