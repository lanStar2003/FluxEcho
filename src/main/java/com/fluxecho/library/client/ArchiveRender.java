package com.fluxecho.library.client;

import static com.fluxecho.client.FluxDraw.AMBER;
import static com.fluxecho.client.FluxDraw.CYAN;
import static com.fluxecho.client.FluxDraw.VIOLET;
import static com.fluxecho.client.FluxDraw.WHITE;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.fluxecho.FluxEcho;
import com.fluxecho.campus.PartBlocks;
import com.fluxecho.client.FluxDraw;
import com.fluxecho.client.Motes;
import com.fluxecho.client.Motifs;
import com.fluxecho.library.TileLibrary;
import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.LibraryUnits;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The formed Echo Archive (0.10.0, the user's 「参考原版图书馆」: a library that looks like a real one). Its books stand in
 * its bookcases in the chunk mesh ({@link LibraryBooks}); this draws the life of the room around them.
 * <ul>
 * <li>Outside, a line of light runs along the plinth course of its walls in its dock state: cyan while docked and
 * powered (a pulse running round the building while it lends), amber while docked without power, dim otherwise. While
 * it forms, the sweep plane rises through its box. Not formed (a panel mined, the last cells not yet built), the line
 * stays, dimmer, along the stretches of wall that stand, and nothing else is drawn.</li>
 * <li>Inside: a chandelier of three slowly turning rings of hard light hangs high over the nave (y10 to y12 over z17),
 * a crystal at its heart; a soft band of light runs along the bookcases' crowns from the doors to the back every
 * twelve seconds; the skylight in the ridge lets a faint column of daylight (moonlight at night) down onto the reading
 * desk, dust drifting in it; over the desk the codex turns its pages, glyphs rise from it, and the hall's book well
 * hangs high over it as a narrowing shaft of shelf rims and book spines made of light; each pedestal shows a picture
 * of a book from the range beside it, the next one every six seconds; and the bookcase under the crosshair is outlined
 * and labelled ({@link ArchiveLook}).</li>
 * </ul>
 * The inside is drawn only while the camera is in the Archive or within {@link #NEAR} blocks of it (through the doors
 * and windows the depth test hides what the walls cover). Undocked or without power the light inside dims, as the
 * hall's does; the daylight does not.
 * <p>
 * Shader-safe as {@link Shapes}: drawn after the world (called from {@code NexusRender}, which skips the shadow pass
 * and is registered with {@code FarDraw}), relative to {@code RenderManager.renderPos*}, quads only, a normal towards
 * the viewer on every vertex, no depth writes, texturing switched on by hand after the state is popped. Animation
 * follows the world time alone and nothing here counts, spawns or sends, so a frame drawn again behind a light gate
 * looks the same. Per frame it allocates nothing larger than a few small arrays; its tables (pedestals and their
 * books, the plinth course) are worked out once from the shape. Each part fails on its own: an error is logged once
 * and turns that part off until the game restarts, and the GL state is restored whatever happens.
 */
@SideOnly(Side.CLIENT)
final class ArchiveRender {

    private static final int GOLD = 0xFFD27A, LIGHT = 0xE8FBFF, CROWN_LIGHT = 0xB89CFF, IDLE = 0x4A6A80, SUN = 0xFFF1D6,
        MOON = 0x8FA8FF;
    /** How near (blocks from its box) the camera must be for the inside to be drawn when it is not in it. */
    static final double NEAR = 24;

    /** The chandelier's middle (across, along the hall), its rings' heights, radii, segments and turning speeds. */
    private static final double CHANDELIER_X = ArchiveShape.MID + 0.5, CHANDELIER_Z = 17.5;
    private static final double[] RING_Y = { 10.2, 11.0, 11.8 }, RING_IN = { 3.05, 2.15, 1.25 },
        RING_OUT = { 3.45, 2.45, 1.5 }, RING_SPIN = { 0.003, -0.0045, 0.007 };
    private static final int[] RING_SEGMENTS = { 8, 6, 4 };
    /** Half the angle (radians) left open between two segments of a ring. */
    private static final double RING_GAP = 0.045;
    /** The crown sweep: its period (ticks), the band's width (blocks), and how far the upper storey's lags behind. */
    private static final double SWEEP_TICKS = 240, SWEEP_WIDTH = 2.5, UPPER_LAG = 3;
    /** The reading desk's middle and its top, in the shape. */
    private static final double DESK_X = ArchiveShape.DESK[0] + 0.5, DESK_Z = ArchiveShape.DESK[2] + 0.5,
        DESK_TOP = ArchiveShape.DESK[1] + 1;
    /**
     * The book well over the desk: its foot (clear of the gallery's edge and rail, which run just behind the desk) and
     * head (under the clerestory), its half-size there, and its segments.
     */
    private static final double WELL_LOW = ArchiveShape.DECK_Y + 2.2, WELL_HIGH = ArchiveShape.ROOF_Y - 0.4,
        WELL_BASE = 1.15, WELL_END = 0.3;
    private static final int WELL_SEGMENTS = 6;
    /**
     * The skylight's column: it starts at the underside of the ridge's glass (two panes, z28 and z29) and its foot is
     * a little before the desk's middle, so it stays clear of the gallery's edge just behind the desk (z30).
     */
    private static final double SKY_TOP = ArchiveShape.RIDGE_Y, SKY_Z = DESK_Z - 0.5, SKY_FOOT_Z = DESK_Z - 0.3;
    /** A pedestal's book shows this long (ticks) before the next; the picture's half-size and height over the top. */
    private static final double SHOW_TICKS = 120, PICTURE = 0.2, PICTURE_UP = 0.5;
    /** The height of a pedestal (12 sixteenths). */
    private static final double PEDESTAL_TOP = 0.75;
    /** The plinth course's light: its height in the shape, how far off the wall, its core and halo half-heights. */
    private static final double COURSE_Y = 1.5, COURSE_OFF = 0.04, COURSE_CORE = 0.035, COURSE_HALO = 0.16;

    /** The pedestals {x, y, z}, and for each the book places of the range nearest it. */
    private static final int[][] PEDESTALS, PEDESTAL_SLOTS;
    /** The plinth course's cells: {x, z, outX, outZ, s}, s their place along the walls round the building. */
    private static final int[][] COURSE;
    private static final int PERIMETER = 2 * (ArchiveShape.WIDTH + ArchiveShape.DEPTH);
    /** Scratch of one draw: the place each pedestal shows (-1 for none), and how far it has faded in. */
    private static final int[] SHOWN;
    private static final float[] SHOW;

    /** The parts that can fail on their own; {@link #OFF} says which have. */
    private static final int COURSE_LINE = 0, SWEEP = 1, CHANDELIER = 2, CROWNS = 3, SKYLIGHT = 4, DESK = 5,
        PEDESTALS_PART = 6, PICTURES = 7, LOOK = 8, LABEL = 9, ALL = 10;
    private static final String[] NAMES = { "plinth light", "forming sweep", "chandelier", "crown sweep", "skylight",
        "desk", "pedestals", "pedestal pictures", "bookcase outline", "bookcase label", "drawing" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    /** Whether a part opened the world state itself (and it must be ended), and turned the depth test off. */
    private static boolean begun, depthOff;

    static {
        List<int[]> peds = ArchiveShape.pedestals();
        PEDESTALS = peds.toArray(new int[0][]);
        PEDESTAL_SLOTS = new int[PEDESTALS.length][];
        for (int j = 0; j < PEDESTALS.length; j++) PEDESTAL_SLOTS[j] = nearestRange(PEDESTALS[j]);
        SHOWN = new int[PEDESTALS.length];
        SHOW = new float[PEDESTALS.length];
        COURSE = course();
    }

    private ArchiveRender() {}

    /**
     * Draws an Archive; {@code t} is the world time in ticks, {@code fade} the nexus effects' distance fade. One that
     * is not formed shows only its plinth light, dim, along the stretches of its walls that stand.
     */
    static void draw(TileLibrary l, double t, float fade) {
        if (OFF[ALL]) return;
        try {
            ArchiveFrame f = new ArchiveFrame(l);
            if (!l.formed()) {
                if (!OFF[COURSE_LINE]) try {
                    course(f, l, t, fade);
                } catch (Throwable x) {
                    recover();
                    fail(COURSE_LINE, x);
                } finally {
                    close();
                }
                return;
            }
            boolean on = "docked".equals(l.clientDock) && l.clientPowered;
            double sweep = l.sweepLevel();
            float appear = sweep == Double.MAX_VALUE ? 1f
                : (float) Math
                    .max(0, Math.min(1, (System.currentTimeMillis() - l.formedAt) / (double) TileMultiblock.SWEEP_MS));
            // a: how much of the room is there; p: its own light, dimmed while it is idle
            float a = fade * appear, p = (on ? 1f : 0.35f) * a;

            if (!OFF[COURSE_LINE]) try {
                course(f, l, t, fade * (0.3f + 0.7f * appear));
            } catch (Throwable x) {
                recover();
                fail(COURSE_LINE, x);
            } finally {
                close();
            }
            if (sweep != Double.MAX_VALUE && !OFF[SWEEP]) try {
                sweep(l, sweep, (1 - appear) * fade);
            } catch (Throwable x) {
                recover();
                fail(SWEEP, x);
            } finally {
                close();
            }

            double[] cam = ArchiveFrame.camera(l);
            if (!ArchiveShape.inside(cam[0], cam[1], cam[2]) && ArchiveFrame.boxDistance(cam) > NEAR) return;

            if (!OFF[CHANDELIER]) try {
                chandelier(f, t, p);
            } catch (Throwable x) {
                recover();
                fail(CHANDELIER, x);
            } finally {
                close();
            }
            if (!OFF[CROWNS]) try {
                crowns(f, t, p);
            } catch (Throwable x) {
                recover();
                fail(CROWNS, x);
            } finally {
                close();
            }
            if (!OFF[SKYLIGHT]) try {
                skylight(f, l.getWorldObj(), t, a);
            } catch (Throwable x) {
                recover();
                fail(SKYLIGHT, x);
            } finally {
                close();
            }
            if (!OFF[DESK]) try {
                desk(f, t, p);
            } catch (Throwable x) {
                recover();
                fail(DESK, x);
            } finally {
                close();
            }
            boolean shows = false;
            if (!OFF[PEDESTALS_PART]) try {
                shows = pedestals(l, f, t, p);
            } catch (Throwable x) {
                recover();
                fail(PEDESTALS_PART, x);
                shows = false;
            } finally {
                close();
            }
            if (shows && !OFF[PICTURES]) try {
                pictures(l, f, t, p);
            } catch (Throwable x) {
                recover();
                fail(PICTURES, x);
            } finally {
                close();
            }
            ArchiveLook.Target g = null;
            if (!OFF[LOOK]) try {
                g = ArchiveLook.find(l);
                if (g != null) ArchiveLook.outline(f, g, t, a);
            } catch (Throwable x) {
                recover();
                fail(LOOK, x);
                g = null;
            } finally {
                close();
            }
            if (g != null && !OFF[LABEL]) try {
                ArchiveLook.label(l, f, g, a);
            } catch (Throwable x) {
                recover();
                fail(LABEL, x);
            } finally {
                close();
            }
        } catch (Throwable x) {
            recover();
            close();
            fail(ALL, x);
        }
    }

    // ---- outside

    /**
     * The light along the plinth course, in the dock state: cyan docked and powered (a pulse running round while it
     * lends), amber docked without power, dim when not docked, dimmer still when not formed. A bright core and a soft
     * halo, between the pilasters. An unformed Archive lights only the cells whose plinth course block stands (a
     * library core alone, or an Archive half built, shows no line in the air), each looked up in the world without an
     * array.
     */
    private static void course(ArchiveFrame f, TileLibrary l, double t, float fade) {
        boolean formed = l.formed(), docked = formed && "docked".equals(l.clientDock), on = docked && l.clientPowered;
        World w = l.getWorldObj();
        int code = ArchiveShape.partOf(ArchiveShape.COURSE);
        Block wall = formed ? null : PartBlocks.block(code);
        int wallMeta = PartBlocks.meta(code);
        if (!formed && (w == null || wall == null)) return;
        int rgb;
        float a;
        if (!formed) {
            rgb = IDLE;
            a = 0.25f;
        } else if (on) {
            rgb = CYAN;
            a = 0.6f;
        } else if (docked) {
            rgb = AMBER;
            a = 0.3f + 0.25f * (float) (0.5 + 0.5 * Math.sin(t * 0.1));
        } else {
            rgb = IDLE;
            a = 0.35f;
        }
        a *= fade;
        boolean lending = on && l.clientLends > 0;
        double pulse = t * 0.5 % PERIMETER;
        Shapes.begin(true);
        Tessellator tes = ArchiveFrame.start();
        for (int[] c : COURSE) {
            if (!formed) {
                int x = l.worldX(c[0], c[1]), y = l.worldY(1), z = l.worldZ(c[0], c[1]);
                if (w.getBlock(x, y, z) != wall || w.getBlockMetadata(x, y, z) != wallMeta) continue;
            }
            float k = a;
            if (lending) {
                double d = Math.abs(c[4] + 0.5 - pulse);
                d = Math.min(d, PERIMETER - d);
                k += 0.6f * fade * (float) Math.exp(-d * d / 8);
            }
            double ax, az, bx, bz;
            if (c[2] != 0) {
                ax = bx = (c[2] < 0 ? c[0] : c[0] + 1) + c[2] * COURSE_OFF;
                az = c[1];
                bz = c[1] + 1;
            } else {
                az = bz = (c[3] < 0 ? c[1] : c[1] + 1) + c[3] * COURSE_OFF;
                ax = c[0];
                bx = c[0] + 1;
            }
            int core = ArchiveFrame.alpha255(k);
            tes.setColorRGBA_I(rgb, core);
            f.vertex(tes, ax, COURSE_Y - COURSE_CORE, az);
            f.vertex(tes, bx, COURSE_Y - COURSE_CORE, bz);
            f.vertex(tes, bx, COURSE_Y + COURSE_CORE, bz);
            f.vertex(tes, ax, COURSE_Y + COURSE_CORE, az);
            f.softBand(tes, ax, az, bx, bz, COURSE_Y, COURSE_HALO, rgb, 0.45f * k, 0.45f * k);
        }
        tes.draw();
        Shapes.end();
    }

    /** The forming sweep: a plane of light rising through the Archive's box, its rim bright. */
    private static void sweep(TileLibrary l, double level, float k) {
        if (k <= 0) return;
        int[] b = l.bounds();
        if (b == null) return;
        double camX = RenderManager.renderPosX, camZ = RenderManager.renderPosZ;
        double x0 = b[0] - camX, x1 = b[3] + 1 - camX, z0 = b[2] - camZ, z1 = b[5] + 1 - camZ,
            y = level - RenderManager.renderPosY;
        Shapes.begin(true);
        Shapes.plane(x0, y, z0, x1, z1, VIOLET, 0.3f * k);
        Tessellator tes = ArchiveFrame.start();
        tes.setColorRGBA_I(WHITE, ArchiveFrame.alpha255(0.8f * k));
        ArchiveFrame.lineRel(tes, x0, y, z0, x1, y, z0, 0.15);
        ArchiveFrame.lineRel(tes, x1, y, z0, x1, y, z1, 0.15);
        ArchiveFrame.lineRel(tes, x1, y, z1, x0, y, z1, 0.15);
        ArchiveFrame.lineRel(tes, x0, y, z1, x0, y, z0, 0.15);
        tes.draw();
        Shapes.end();
    }

    // ---- inside

    /**
     * The chandelier: three rings of hard light (segments with gaps, a bright inner edge, a lit rim, a faint halo)
     * turning slowly and each its own way, a crystal at their heart on a thread from the ridge, lights riding the
     * rings, and a faint pool of its light on the floor below.
     */
    private static void chandelier(ArchiveFrame f, double t, float p) {
        if (p <= 0.01f) return;
        double cx = f.x(CHANDELIER_X, CHANDELIER_Z), cz = f.z(CHANDELIER_X, CHANDELIER_Z);
        Shapes.begin(true);
        for (int k = 0; k < RING_Y.length; k++) {
            double y = f.y(RING_Y[k]), spin = t * RING_SPIN[k], r0 = RING_IN[k], r1 = RING_OUT[k];
            int n = RING_SEGMENTS[k];
            for (int s = 0; s < n; s++) {
                double a0 = spin + s * 2 * Math.PI / n + RING_GAP, a1 = spin + (s + 1) * 2 * Math.PI / n - RING_GAP;
                Shapes.ring(cx, y, cz, r0, r1, a0, a1, LIGHT, 0.55f * p, CYAN, 0.3f * p);
                Shapes.band(cx, cz, r1, y - 0.07, y + 0.07, a0, a1, CYAN, 0.5f * p, 0.5f * p);
                Shapes.band(cx, cz, r0, y - 0.04, y + 0.04, a0, a1, VIOLET, 0.4f * p, 0.4f * p);
            }
            Shapes.ring(cx, y, cz, r1, r1 + 0.45, 0, 2 * Math.PI, CYAN, 0.14f * p, CYAN, 0f);
        }
        Shapes.string(cx, f.y(RING_Y[0] - 0.6), cz, cx, f.y(ArchiveShape.RIDGE_Y), cz, 0.035, CYAN, 0f, 0.35f * p);
        Shapes.crystal(cx, f.y(RING_Y[1]), cz, 0.2, 0.36, t * 0.012, LIGHT, VIOLET, 0.85f * p);
        Shapes.disc(cx, f.y(1.03), cz, 3.4, CYAN, 0.07f * p, 0f);
        Shapes.end();

        Motes.begin();
        for (int k = 0; k < RING_Y.length; k++) {
            double y = f.y(RING_Y[k]), r = (RING_IN[k] + RING_OUT[k]) / 2;
            for (int m = 0; m < 3; m++) {
                double ang = t * RING_SPIN[k] + (m + 0.5) * 2 * Math.PI / 3 + k;
                Motes.add(cx + Math.cos(ang) * r, y, cz + Math.sin(ang) * r, 0.1, LIGHT, 0.55f * p);
            }
        }
        Motes.add(cx, f.y(RING_Y[1]), cz, 0.7, VIOLET, 0.3f * p);
        Motes.end();
    }

    /**
     * A soft band of light running along the bookcases' crowns, from the doors to the back every twelve seconds (the
     * upper storey's a little behind): each crown's face lights as the band passes it.
     */
    private static void crowns(ArchiveFrame f, double t, float p) {
        float a = 0.5f * p;
        if (a <= 0.01f) return;
        double zb = -2 * SWEEP_WIDTH + t % SWEEP_TICKS / SWEEP_TICKS * (ArchiveShape.DEPTH + 4 * SWEEP_WIDTH);
        Shapes.begin(true);
        Tessellator tes = ArchiveFrame.start();
        for (LibraryUnits.Unit u : LibraryUnits.UNITS) {
            double band = u.storey == 0 ? zb : zb - UPPER_LAG, y = u.yBody + 3.5;
            for (int i = 0; i < 3; i++) {
                int x = u.x(i), z = u.z(i);
                double ax, az, bx, bz;
                if (u.faceX != 0) {
                    ax = bx = (u.faceX > 0 ? x + 1 : x) + u.faceX * 0.015;
                    az = z;
                    bz = z + 1;
                } else {
                    az = bz = (u.faceZ > 0 ? z + 1 : z) + u.faceZ * 0.015;
                    ax = x;
                    bx = x + 1;
                }
                float k0 = a * glow(az - band), k1 = a * glow(bz - band);
                if (k0 < 0.004f && k1 < 0.004f) continue;
                f.softBand(tes, ax, az, bx, bz, y, 0.32, CROWN_LIGHT, k0, k1);
            }
        }
        tes.draw();
        Shapes.end();
    }

    private static float glow(double d) {
        return (float) Math.exp(-d * d / (SWEEP_WIDTH * SWEEP_WIDTH));
    }

    /**
     * The skylight: a faint column from the ridge's glass down onto the reading desk, widening as it falls, a pool
     * of it on the desk and the floor round it, and dust drifting down in it. Daylight by day, a fainter moonlight at
     * night, nothing where the world has no sky.
     */
    private static void skylight(ArchiveFrame f, World w, double t, float a) {
        if (w == null || w.provider.hasNoSky || a <= 0.01f) return;
        float sun = Math.max(0f, Math.min(1f, (w.getSunBrightness(1f) - 0.2f) / 0.8f));
        float s = a * (0.3f + 0.7f * sun);
        int rgb = Shapes.mix(MOON, SUN, sun);
        Shapes.begin(true);
        Tessellator tes = ArchiveFrame.start();
        frustum(tes, f, SKY_TOP, SKY_Z, 0.45, 0.85, 0.06f * s, 4.0, SKY_FOOT_Z, 0.95, 0.72, 0.11f * s, rgb);
        frustum(tes, f, 4.0, SKY_FOOT_Z, 0.95, 0.72, 0.11f * s, DESK_TOP + 0.05, SKY_FOOT_Z, 1.05, 0.75, 0f, rgb);
        tes.draw();
        Shapes.disc(f.x(DESK_X, DESK_Z), f.y(DESK_TOP + 0.01), f.z(DESK_X, DESK_Z), 0.45, rgb, 0.2f * s, 0.05f * s);
        Shapes.disc(f.x(DESK_X, SKY_FOOT_Z), f.y(1.02), f.z(DESK_X, SKY_FOOT_Z), 1.4, rgb, 0.08f * s, 0f);
        Shapes.end();

        Motes.begin();
        for (int k = 0; k < 10; k++) {
            double h = Motifs.hash(k * 13 + 5), g = Motifs.hash(k * 29 + 11);
            double fall = (t * 0.0012 + h) % 1;
            double ly = SKY_TOP - 0.4 - fall * (SKY_TOP - 0.4 - DESK_TOP - 0.6);
            double lx = DESK_X + (g - 0.5) * 2 * (0.4 + 0.5 * fall) + 0.1 * Math.sin(t * 0.02 + k);
            double lz = SKY_Z + 0.2 * fall + (h - 0.5) * 2 * (0.6 + 0.1 * fall);
            Motes.add(f.x(lx, lz), f.y(ly), f.z(lx, lz), 0.035, rgb, (float) Math.sin(fall * Math.PI) * 0.45f * s);
        }
        Motes.end();
    }

    /**
     * The four sides of an upright box of light narrowing or widening from one height to another: half-sizes across
     * and along the hall, the middle's z and the alpha at each end (the middle across is the axis).
     */
    private static void frustum(Tessellator t, ArchiveFrame f, double y0, double z0, double hx0, double hz0, float a0,
        double y1, double z1, double hx1, double hz1, float a1, int rgb) {
        int i0 = ArchiveFrame.alpha255(a0), i1 = ArchiveFrame.alpha255(a1);
        for (int s = 0; s < 4; s++) {
            double sx0 = side(s, 0), sz0 = side(s, 1), sx1 = side(s + 1, 0), sz1 = side(s + 1, 1);
            t.setColorRGBA_I(rgb, i0);
            f.vertex(t, DESK_X + sx0 * hx0, y0, z0 + sz0 * hz0);
            f.vertex(t, DESK_X + sx1 * hx0, y0, z0 + sz1 * hz0);
            t.setColorRGBA_I(rgb, i1);
            f.vertex(t, DESK_X + sx1 * hx1, y1, z1 + sz1 * hz1);
            f.vertex(t, DESK_X + sx0 * hx1, y1, z1 + sz0 * hz1);
        }
    }

    /** Corner {@code k} (round the square) of a box's cross-section: the sign across (axis 0) or along (axis 1). */
    private static double side(int k, int axis) {
        int c = Math.floorMod(k, 4);
        if (axis == 0) return c == 0 || c == 3 ? -1 : 1;
        return c < 2 ? -1 : 1;
    }

    /**
     * Over the reading desk: the codex turning its pages, glyphs rising from the desk, and high over it, from above the
     * gallery up to the clerestory, the book well as a narrowing shaft of glowing shelf rims and book spines with a
     * light at its head.
     */
    private static void desk(ArchiveFrame f, double t, float p) {
        if (p <= 0.01f) return;
        double dx = f.x(DESK_X, DESK_Z), dz = f.z(DESK_X, DESK_Z);
        Shapes.begin(true);
        LibraryRender.codex(dx, f.y(DESK_TOP + 0.9), dz, 0.55, t, p);
        Tessellator tes = ArchiveFrame.start();
        well(tes, f, t, p);
        tes.draw();
        Shapes.end();

        Motes.begin();
        for (int k = 0; k < 12; k++) {
            double fr = (t * 0.006 + k / 12.0) % 1;
            double ang = k * 2.1 + t * 0.004;
            double r = 0.6 + 1.6 * Motifs.hash(k * 7 + 3);
            // flattened along the hall, so they rise clear of the gallery's edge behind the desk
            double lx = DESK_X + Math.cos(ang) * r, lz = DESK_Z - 0.7 + Math.sin(ang) * r * 0.45;
            Motes.add(
                f.x(lx, lz),
                f.y(DESK_TOP + 0.2 + fr * 5.2),
                f.z(lx, lz),
                0.08,
                k % 4 == 0 ? GOLD : VIOLET,
                (float) Math.sin(fr * Math.PI) * 0.7f * p);
        }
        Motes.end();
    }

    private static double wellY(int i) {
        return WELL_LOW + (WELL_HIGH - WELL_LOW) * i / WELL_SEGMENTS;
    }

    /** Half-size of the well at segment boundary {@code i}: from its foot narrowing to its head. */
    private static double wellHalf(int i) {
        return WELL_BASE + (WELL_END - WELL_BASE) * Math.pow(i / (double) WELL_SEGMENTS, 0.8);
    }

    /**
     * The book well as the 0.9.2 hall drew it in its ceiling, hanging in the open over the desk (light only, no walls:
     * there is no ceiling here to set it into): a rim of light at each step up, rows of book spines up its sides in
     * violet, cyan and gold, the head shining.
     */
    private static void well(Tessellator tes, ArchiveFrame f, double t, float a) {
        for (int i = 0; i <= WELL_SEGMENTS; i++) {
            double h = wellHalf(i) - 0.02, y = wellY(i);
            float fa = a * (1f - i / (float) (WELL_SEGMENTS + 2));
            tes.setColorRGBA_I(VIOLET, ArchiveFrame.alpha255(0.5f * fa));
            for (int s = 0; s < 4; s++) {
                f.line(
                    tes,
                    DESK_X + side(s, 0) * h,
                    y,
                    DESK_Z + side(s, 1) * h,
                    DESK_X + side(s + 1, 0) * h,
                    y,
                    DESK_Z + side(s + 1, 1) * h,
                    0.045);
            }
        }
        for (int i = 0; i < WELL_SEGMENTS; i++) {
            double ym = (wellY(i) + wellY(i + 1)) / 2, hm = (wellHalf(i) + wellHalf(i + 1)) / 2 - 0.03;
            double step = wellY(i + 1) - wellY(i);
            float fa = a * (0.6f - 0.08f * i);
            for (int s = 0; s < 4; s++) for (int k = 0; k < 5; k++) {
                double hash = Motifs.hash(i * 131 + s * 17 + k);
                if (hash < 0.25) continue;
                double u = -hm + (k + 0.5) * hm * 2 / 5;
                double len = (0.3 + 0.35 * hash) * step * 0.7;
                double lx, lz;
                switch (s) {
                    case 0:
                        lx = DESK_X + u;
                        lz = DESK_Z - hm;
                        break;
                    case 1:
                        lx = DESK_X + hm;
                        lz = DESK_Z + u;
                        break;
                    case 2:
                        lx = DESK_X - u;
                        lz = DESK_Z + hm;
                        break;
                    default:
                        lx = DESK_X - hm;
                        lz = DESK_Z - u;
                }
                int col = hash < 0.5 ? VIOLET : hash < 0.8 ? CYAN : GOLD;
                float tw = 0.6f + 0.4f * (float) Math.sin(t * 0.05 + hash * 20);
                tes.setColorRGBA_I(col, ArchiveFrame.alpha255(fa * tw));
                f.line(tes, lx, ym - len / 2, lz, lx, ym + len / 2, lz, 0.06 * (1 - i * 0.1));
            }
        }
        double y = wellY(WELL_SEGMENTS) - 0.02, h = wellHalf(WELL_SEGMENTS);
        float pulse = 0.6f + 0.4f * (float) Math.sin(t * 0.07);
        tes.setColorRGBA_I(VIOLET, ArchiveFrame.alpha255(0.25f * a * pulse));
        for (int r = 0; r < 3; r++) {
            double rr = h * (0.4 + r * 0.3);
            f.vertex(tes, DESK_X - rr, y, DESK_Z - rr);
            f.vertex(tes, DESK_X + rr, y, DESK_Z - rr);
            f.vertex(tes, DESK_X + rr, y, DESK_Z + rr);
            f.vertex(tes, DESK_X - rr, y, DESK_Z + rr);
        }
    }

    /**
     * The pedestals' emitters: a glow on each top, sparks rising, and a soft light behind the picture it shows. Works
     * out which book each shows now ({@link #SHOWN}); true when any shows one.
     */
    private static boolean pedestals(TileLibrary l, ArchiveFrame f, double t, float p) {
        boolean any = false;
        for (int j = 0; j < PEDESTALS.length; j++) {
            SHOWN[j] = shown(l, j, t);
            any |= SHOWN[j] >= 0;
        }
        if (p <= 0.01f) return false;
        Motes.begin();
        for (int j = 0; j < PEDESTALS.length; j++) {
            int[] c = PEDESTALS[j];
            double lx = c[0] + 0.5, lz = c[2] + 0.5, top = c[1] + PEDESTAL_TOP, x = f.x(lx, lz), z = f.z(lx, lz);
            Motes.add(x, f.y(top + 0.04), z, 0.2, CYAN, 0.3f * p);
            for (int m = 0; m < 2; m++) {
                double fr = (t * 0.012 + j * 0.37 + m * 0.5) % 1;
                Motes.add(x, f.y(top + 0.05 + fr * 0.6), z, 0.04, CYAN, (float) Math.sin(fr * Math.PI) * 0.5f * p);
            }
            if (SHOWN[j] >= 0) Motes.add(x, f.y(top + PICTURE_UP + bob(t, j)), z, 0.34, VIOLET, 0.22f * p * SHOW[j]);
        }
        Motes.end();
        return any;
    }

    private static double bob(double t, int j) {
        return 0.04 * Math.sin(t * 0.05 + j);
    }

    /**
     * The place pedestal {@code j} shows now, -1 for none: its range's books in turn, the next every
     * {@link #SHOW_TICKS} (the pedestals out of step), fading out and in at the change ({@link #SHOW}).
     */
    private static int shown(TileLibrary l, int j, double t) {
        int[] slots = PEDESTAL_SLOTS[j];
        int n = 0;
        for (int s : slots) if (l.book(s) != null) n++;
        SHOW[j] = 0f;
        if (n == 0) return -1;
        double c = (t + j * 37) / SHOW_TICKS;
        long cycle = (long) Math.floor(c);
        double phase = c - cycle;
        SHOW[j] = (float) Math.min(1, Math.min(phase, 1 - phase) * 8);
        int pick = (int) Math.floorMod(cycle, (long) n);
        for (int s : slots) if (l.book(s) != null && pick-- == 0) return s;
        return -1;
    }

    /**
     * The pictures over the pedestals: each shown book's picture, facing the camera round the vertical, blocks' first
     * and then items', each render pass in its colour (as the 0.9.2 hall drew its books' pictures). A picture that
     * cannot be had here is left out.
     */
    private static void pictures(TileLibrary l, ArchiveFrame f, double t, float p) {
        worldState();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.05f);
        double yaw = Math.toRadians(RenderManager.instance.playerViewY);
        // the camera's right, round the vertical
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        Minecraft mc = Minecraft.getMinecraft();
        Tessellator tes = Tessellator.instance;
        for (int sheet = 0; sheet <= 1; sheet++) {
            mc.getTextureManager()
                .bindTexture(sheet == 0 ? TextureMap.locationBlocksTexture : TextureMap.locationItemsTexture);
            tes.startDrawingQuads();
            ArchiveFrame.normal(tes);
            for (int j = 0; j < PEDESTALS.length; j++) {
                if (SHOWN[j] < 0) continue;
                ItemStack s = l.book(SHOWN[j]);
                float alpha = 0.9f * p * SHOW[j];
                if (s == null || s.getItem() == null || alpha <= 0.02f) continue;
                int[] c = PEDESTALS[j];
                double lx = c[0] + 0.5, lz = c[2] + 0.5, ly = c[1] + PEDESTAL_TOP + PICTURE_UP + bob(t, j);
                try {
                    if (s.getItem()
                        .getSpriteNumber() != sheet) continue;
                    picture(tes, s, f.x(lx, lz), f.y(ly), f.z(lx, lz), rx, rz, alpha);
                } catch (RuntimeException e) {
                    // a modded item whose picture cannot be had here shows none
                }
            }
            tes.draw();
        }
    }

    private static void picture(Tessellator tes, ItemStack s, double x, double y, double z, double rx, double rz,
        float alpha) {
        Item item = s.getItem();
        int passes = item.requiresMultipleRenderPasses() ? item.getRenderPasses(s.getItemDamage()) : 1;
        double h = PICTURE, dx = rx * h, dz = rz * h;
        int ai = ArchiveFrame.alpha255(alpha);
        for (int pass = 0; pass < passes; pass++) {
            IIcon ic = item.getIcon(s, pass);
            if (ic == null) continue;
            int rgb = item.getColorFromItemStack(s, pass);
            tes.setColorRGBA_I(rgb & 0xFFFFFF, ai);
            // top left, bottom left, bottom right, top right as the viewer sees it
            tes.addVertexWithUV(x - dx, y + h, z - dz, ic.getMinU(), ic.getMinV());
            tes.addVertexWithUV(x - dx, y - h, z - dz, ic.getMinU(), ic.getMaxV());
            tes.addVertexWithUV(x + dx, y - h, z + dz, ic.getMaxU(), ic.getMaxV());
            tes.addVertexWithUV(x + dx, y + h, z + dz, ic.getMaxU(), ic.getMinV());
        }
    }

    // ---- tables

    /**
     * The book places of the range beside a pedestal: the ground storey's bookcases of the range pair (both runs) on
     * the pedestal's side whose run is nearest it along the hall. The pedestals by the desk take the last range.
     */
    private static int[] nearestRange(int[] ped) {
        boolean left = ped[0] < ArchiveShape.MID;
        int nearest = Integer.MIN_VALUE, best = Integer.MAX_VALUE;
        for (LibraryUnits.Unit u : LibraryUnits.UNITS) {
            if (!ranged(u, left)) continue;
            int d = Math.abs(u.z0 - ped[2]);
            if (d < best) {
                best = d;
                nearest = u.z0;
            }
        }
        List<Integer> slots = new ArrayList<>();
        for (LibraryUnits.Unit u : LibraryUnits.UNITS) {
            if (!ranged(u, left) || Math.abs(u.z0 - nearest) > 1) continue;
            for (int k = 0; k < 9; k++) slots.add(u.index * 9 + k);
        }
        int[] out = new int[slots.size()];
        for (int i = 0; i < out.length; i++) out[i] = slots.get(i);
        return out;
    }

    /** Whether a bookcase stands in a ground-storey range (not the front or back wall, not a side wall) on a side. */
    private static boolean ranged(LibraryUnits.Unit u, boolean left) {
        return u.storey == 0 && u.alongX
            && u.z0 > 1
            && u.z0 < ArchiveShape.DEPTH - 2
            && (u.x0 < ArchiveShape.MID) == left;
    }

    /** The plinth course's cells on the outside of the walls, with the way out and their place round the building. */
    private static int[][] course() {
        int w = ArchiveShape.WIDTH, d = ArchiveShape.DEPTH;
        List<int[]> out = new ArrayList<>();
        for (int z = 0; z < d; z++) for (int x = 0; x < w; x++) {
            if (ArchiveShape.cell(x, 1, z) != ArchiveShape.COURSE) continue;
            int ox = x == 0 ? -1 : x == w - 1 ? 1 : 0, oz = ox != 0 ? 0 : z == 0 ? -1 : z == d - 1 ? 1 : 0;
            if (ox == 0 && oz == 0) continue;
            // round the walls: the front left to right, the right side front to back, the back, the left side
            int s = oz < 0 ? x : ox > 0 ? w + z : oz > 0 ? w + d + (w - 1 - x) : 2 * w + d + (d - 1 - z);
            out.add(new int[] { x, z, ox, oz, s });
        }
        return out.toArray(new int[0][]);
    }

    // ---- state

    /** Opens the world state for a part that draws with its own texture or text ({@link #close} ends it). */
    static void worldState() {
        if (begun) return;
        FluxDraw.worldBegin();
        begun = true;
    }

    /** Turns the depth test off for the rest of a part ({@link #close} turns it on again). */
    static void depthOff() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        depthOff = true;
    }

    /**
     * After an error: ends the motes' batch if it was open, then a quad batch the error broke off (the tessellator
     * refuses to start another while one is open, which would take every later drawing with it).
     */
    private static void recover() {
        try {
            Motes.end();
        } catch (Throwable ignored) {
            // the motes' state is popped whatever happens
        }
        try {
            Tessellator.instance.draw();
        } catch (Throwable ignored) {
            // nothing was open
        }
    }

    /** Restores the GL state of whichever part ran, on every path. */
    private static void close() {
        try {
            Motes.end();
        } catch (Throwable ignored) {
            // the motes' state is popped whatever happens
        }
        Shapes.end();
        if (depthOff) {
            depthOff = false;
            // by hand: Angelica follows glEnable / glDisable, not what popping the state restores
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        if (begun) {
            begun = false;
            FluxDraw.worldEnd();
        }
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Echo Archive's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
