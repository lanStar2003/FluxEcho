package com.fluxecho.campus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.campus.BuildJob;
import com.fluxecho.campus.Campus;
import com.fluxecho.client.FarDraw;
import com.fluxecho.client.FluxDraw;
import com.fluxecho.client.ShaderCompat;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.FxCodec;
import com.fluxecho.logic.GhostCells;
import com.fluxecho.nexus.ClientTiles;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The projection of a nexus's build job (0.10.0 「营造」, the user's 「中枢可以直接显示书库的投影」): while the job waits for
 * 开始, builds or is paused, every unbuilt cell near the viewer shows as a ghost of its own block, tinted cyan, added
 * onto the world and breathing (amber and slower while the job waits for materials or power). The parts' textures are
 * dark, so the ghost is a flat wash of the tint with the texture over it, and the creases of its surface (the rims of
 * walls and floors, corners, the sides of openings) are drawn as lines at least a pixel wide: it reads in daylight and
 * from across the campus, and the breathing only moves it between a little dimmer and full. A new projection is
 * revealed by a scan plane rising through the plan's box; while building, the lowest layer still to build is brighter
 * and its cells are edged with light. Blocked cells are red boxes seen through everything, the ground a new campus
 * will grade is edged in amber until the clearing is done (not for a repair, which grades nothing), and a finished job
 * sends a ring of hard light out over its floor.
 * Further than {@code buildGhostRange}, in {@code outline} mode, or while the job is still surveying, only the stages'
 * bounding boxes are outlined; {@code off} draws nothing.
 * <p>
 * It only streams what {@link BuildScan} worked out in the client tick (the selected cells, their faces' corners and
 * texture coordinates), so a frame drawn several times (the light gates draw the world again, {@link FarDraw}) costs no
 * world reads and looks the same each time; animation follows the world time alone. Shader-safe: drawn after the
 * world, skipped in the shadow pass, relative to {@code RenderManager.renderPos*}, quads only, a normal on every vertex
 * (a ghost face's own axis turned to the viewer; effect geometry facing the viewer as {@link Shapes} does), the block
 * atlas bound for the ghost as {@code LibraryRender} does, no depth writes, lighting, fog and alpha test off. Each part
 * (ghost, its creases, scan plane, lowest layer, blocked boxes, outlines, grading edge, completion ring) fails on its
 * own: an error is logged once and turns that part off for the session, and the GL state is restored whatever happens.
 */
@SideOnly(Side.CLIENT)
public final class BuildRender {

    private static final int CYAN = 0x4FE3FF, AMBER = 0xFFB347, RED = 0xFF4A4A, LIGHT = 0xE8FBFF;
    /** Ticks the scan plane takes to rise through a new projection, and the completion ring to spread. */
    static final int SCAN_TICKS = 80, RING_TICKS = 40;
    /** The most blocked cells boxed per nexus. */
    private static final int MAX_BLOCKED = 64;
    /** Quads per draw call before the tessellator is flushed. */
    private static final int FLUSH = 4096;
    /** The width of a thin line near by, the least width per block of distance (about a pixel), the longest piece. */
    private static final double LINE = 0.035, PIXEL = 0.0022, PIECE = 8;
    /**
     * The ghost's alphas at the top of a breath, cyan and amber: the textured faces (the textures are dark navy, so
     * this is high), the flat wash of the tint under them, and the creases while waiting for 开始 and while building
     * (fainter then: the lowest layer is lit instead).
     */
    private static final float TEXTURE = 0.75f, TEXTURE_AMBER = 0.9f, WASH = 0.09f, WASH_AMBER = 0.08f, CREASE = 0.55f,
        CREASE_BUILDING = 0.3f, CREASE_AMBER = 0.5f;

    /** The parts that can fail on their own; {@link #OFF} says which have. */
    private static final int GHOST = 0, PLANE = 1, LAYER = 2, BLOCKED = 3, OUTLINES = 4, EDGE = 5, RING = 6,
        CREASES = 7, ALL = 8;
    private static final String[] NAMES = { "ghost", "scan plane", "lowest layer", "blocked boxes", "outlines",
        "grading edge", "completion ring", "creases", "drawing" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    /** Whether the ghost's GL state is pushed (and must be popped). */
    private static boolean begun;
    /** The normal towards the viewer for effect geometry, as {@link Shapes} computes it. */
    private static float nx, ny = 1, nz;
    private static double camX, camY, camZ;

    private BuildRender() {}

    /** Draws after the world and behind light gates; called once by {@code CampusClient.register()}. */
    public static void register() {
        BuildRender h = new BuildRender();
        MinecraftForge.EVENT_BUS.register(h);
        FarDraw.add(h::onRenderLast);
    }

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        if (ShaderCompat.shadowPass() || OFF[ALL] || "off".equals(Config.buildProjection)) return;
        try {
            World w = Minecraft.getMinecraft().theWorld;
            if (w == null) return;
            double t = w.getTotalWorldTime() + e.partialTicks;
            camX = RenderManager.renderPosX;
            camY = RenderManager.renderPosY;
            camZ = RenderManager.renderPosZ;
            viewNormal();
            boolean full = "full".equals(Config.buildProjection);
            for (TileMultiblock m : ClientTiles.all()) {
                if (!(m instanceof TileNexus n) || m.getWorldObj() != w || m.isInvalid()) continue;
                Campus.View v = n.clientCampus;
                if (v == null || !v.active || !v.hasJob()) continue;
                nexus(n, v, t, full);
            }
        } catch (Throwable x) {
            recover();
            close();
            fail(ALL, x);
        }
    }

    private static void nexus(TileNexus n, Campus.View v, double t, boolean full) {
        BuildScan.Ghost g = BuildScan.ghost(n);
        // a ghost of another job's plan waits for the next scan
        if (g != null && !g.planKey.equals(v.planKey)) g = null;
        BuildState.State s = BuildScan.state(n);
        BuildState.Pause p = BuildScan.pause(n);
        int stage = BuildScan.stage(n);
        boolean ended = s == BuildState.State.DONE || s == BuildState.State.CANCELLED;
        int[] c = g != null ? new int[] { g.ox, g.oy, g.oz } : n.centre();
        double dist = g != null && g.bounds != null ? BuildScan.boxDistance(g.bounds, camX, camY, camZ)
            : Math.sqrt(sq(c[0] + 0.5 - camX) + sq(c[1] + 0.5 - camY) + sq(c[2] + 0.5 - camZ));
        if (dist > BuildScan.OUTLINE_RANGE) return;
        boolean amber = s == BuildState.State.PAUSED
            && (p == BuildState.Pause.MATERIALS || p == BuildState.Pause.POWER);
        boolean ghost = full && g != null
            && g.live
            && BuildScan.showsGhost(s)
            && g.bounds != null
            && dist <= Config.buildGhostRange;

        if (ghost) {
            float a = breath(t, amber);
            int tint = amber ? AMBER : CYAN;
            // the scan plane: its height while it rises, cells above it not drawn yet
            double plane = Double.POSITIVE_INFINITY;
            float rise = -1;
            if (v.projectedAt >= 0) {
                double age = t - v.projectedAt;
                if (age > -SCAN_TICKS && age < SCAN_TICKS) {
                    rise = (float) (Math.max(0, age) / SCAN_TICKS);
                    plane = g.bounds[1] + (g.bounds[4] + 1 - g.bounds[1]) * (double) rise;
                }
            }
            boolean building = s == BuildState.State.BUILDING || s == BuildState.State.WAITING;
            if (!OFF[GHOST] && g.faces.length > 0) try {
                ghost(g, tint, a, amber, building && g.layerStage == stage, plane);
            } catch (Throwable x) {
                recover();
                fail(GHOST, x);
            } finally {
                close();
            }
            if (!OFF[CREASES] && g.creases.length > 0) try {
                float ca = amber ? CREASE_AMBER : building ? CREASE_BUILDING : CREASE;
                creases(g, Shapes.mix(tint, 0xFFFFFF, 0.3), ca * a, plane);
            } catch (Throwable x) {
                recover();
                fail(CREASES, x);
            } finally {
                close();
            }
            if (!OFF[LAYER] && building && g.hasLayer() && g.layerStage == stage) try {
                layer(g, tint, 0.7f * a);
            } catch (Throwable x) {
                recover();
                fail(LAYER, x);
            } finally {
                close();
            }
            if (!OFF[PLANE] && rise >= 0) try {
                plane(g, plane, rise);
            } catch (Throwable x) {
                recover();
                fail(PLANE, x);
            } finally {
                close();
            }
        } else if (!ended && g != null && !OFF[OUTLINES]) try {
            outlines(g, stage);
        } catch (Throwable x) {
            recover();
            fail(OUTLINES, x);
        } finally {
            close();
        }

        // a repair grades nothing: it only clears what stands in its own cells
        boolean grades = !v.job.startsWith(BuildJob.REPAIR);
        if (!ended && grades && stage == 0 && g != null && g.edge.length > 0 && !OFF[EDGE]) try {
            edge(g);
        } catch (Throwable x) {
            recover();
            fail(EDGE, x);
        } finally {
            close();
        }
        if (!ended && v.blocked.length > 0 && !OFF[BLOCKED]) try {
            blocked(v.blocked, c, t);
        } catch (Throwable x) {
            recover();
            fail(BLOCKED, x);
        } finally {
            close();
        }
        if (Config.buildEffects && s == BuildState.State.DONE
            && v.finishedAt >= 0
            && g != null
            && g.bounds != null
            && !OFF[RING]) {
            double age = t - v.finishedAt;
            if (age >= 0 && age < RING_TICKS) try {
                ring(g, age / RING_TICKS);
            } catch (Throwable x) {
                recover();
                fail(RING, x);
            } finally {
                close();
            }
        }
    }

    /**
     * The ghost's breath, the share of its full brightness: 0.16/0.28 to 1 over three seconds, or 0.12/0.20 to 1 over
     * five while it waits for supplies (the contract's breathing ranges, as a swing rather than the alpha itself).
     */
    private static float breath(double t, boolean waiting) {
        float lo = waiting ? 0.12f / 0.20f : 0.16f / 0.28f;
        double period = waiting ? 100 : 60;
        return lo + (1 - lo) * (float) (0.5 + 0.5 * Math.sin(t * 2 * Math.PI / period));
    }

    // ---- the ghost

    /**
     * The GL state of the ghost and its creases: added onto the world, depth-tested but not written, and drawn in
     * front of a block face it lies on, as vanilla's block-breaking overlay is.
     */
    private static void ghostState() {
        FluxDraw.worldBegin();
        begun = true;
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-3f, -3f);
    }

    /**
     * The ghost's faces, {@code rel} of the way up its breath: first a flat wash of the tint, then the parts' own
     * textures from the block atlas over it (the lowest layer's brighter when {@code layerLit}).
     */
    private static void ghost(BuildScan.Ghost g, int tint, float rel, boolean amber, boolean layerLit, double plane) {
        ghostState();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        float wash = (amber ? WASH_AMBER : WASH) * rel;
        faces(g, tint, wash, layerLit ? wash * 2 : wash, plane);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        float tex = (amber ? TEXTURE_AMBER : TEXTURE) * rel;
        faces(g, tint, tex, layerLit ? tex * 1.5f : tex, plane);
    }

    /**
     * Streams the ghost's faces in one tint, textured or flat as the caller set the GL state (the texture coordinates
     * are given either way), the lowest layer's at {@code lit}. Faces of cells built since the selection are skipped;
     * while the scan plane rises ({@code plane} its world height, else infinite) faces above it are skipped and
     * upright faces it cuts through are cut at it.
     */
    private static void faces(BuildScan.Ghost g, int tint, float alpha, float lit, double plane) {
        double bx = g.ox - camX, by = g.oy - camY, bz = g.oz - camZ;
        boolean clip = plane != Double.POSITIVE_INFINITY;
        float h = clip ? (float) (plane - g.oy) : 0;
        int base = alpha255(alpha), bright = alpha255(lit);
        float[] xyz = g.xyz, uv = g.uv;
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        int quads = 0;
        for (int f = 0; f < g.faces.length; f++) {
            int packed = g.faces[f], sel = GhostCells.cell(packed), side = GhostCells.side(packed);
            if (g.gone.get(g.cells[sel])) continue;
            int p = f * 12, q = f * 8;
            float bottom = xyz[p + 1], top = xyz[p + 7];
            boolean cut = false;
            if (clip) {
                if (side == 1 ? top > h : bottom >= h) continue;
                cut = side >= 2 && top > h;
            }
            tes.setColorRGBA_I(tint, g.inLayer.get(sel) ? bright : base);
            faceNormal(tes, side, bx + xyz[p], by + bottom, bz + xyz[p + 2]);
            tes.addVertexWithUV(bx + xyz[p], by + xyz[p + 1], bz + xyz[p + 2], uv[q], uv[q + 1]);
            tes.addVertexWithUV(bx + xyz[p + 3], by + xyz[p + 4], bz + xyz[p + 5], uv[q + 2], uv[q + 3]);
            if (cut) {
                // the top corners come down to the plane, their v with them
                float k = (h - bottom) / (top - bottom);
                tes.addVertexWithUV(
                    bx + xyz[p + 6],
                    by + h,
                    bz + xyz[p + 8],
                    uv[q + 4],
                    uv[q + 3] + (uv[q + 5] - uv[q + 3]) * k);
                tes.addVertexWithUV(
                    bx + xyz[p + 9],
                    by + h,
                    bz + xyz[p + 11],
                    uv[q + 6],
                    uv[q + 1] + (uv[q + 7] - uv[q + 1]) * k);
            } else {
                tes.addVertexWithUV(bx + xyz[p + 6], by + xyz[p + 7], bz + xyz[p + 8], uv[q + 4], uv[q + 5]);
                tes.addVertexWithUV(bx + xyz[p + 9], by + xyz[p + 10], bz + xyz[p + 11], uv[q + 6], uv[q + 7]);
            }
            if (++quads >= FLUSH) {
                tes.draw();
                tes.startDrawingQuads();
                quads = 0;
            }
        }
        tes.draw();
    }

    /**
     * The creases of the ghost's surface as thin lines at least a pixel wide, so its shape reads from afar and in
     * daylight; those of cells built since the selection are skipped, and while the scan plane rises those above it
     * are skipped and upright ones it cuts through are cut at it.
     */
    private static void creases(BuildScan.Ghost g, int rgb, float a, double plane) {
        ghostState();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        double bx = g.ox - camX, by = g.oy - camY, bz = g.oz - camZ;
        boolean clip = plane != Double.POSITIVE_INFINITY;
        double h = clip ? plane - g.oy : 0;
        int ai = alpha255(a);
        float[] e = g.creases;
        Tessellator tes = start();
        int quads = 0;
        for (int k = 0; k < g.creaseCells.length; k++) {
            if (g.gone.get(g.cells[g.creaseCells[k]])) continue;
            int p = k * 6;
            double y0 = e[p + 1], y1 = e[p + 4];
            if (clip) {
                if (y1 > y0 ? y0 >= h : y0 > h) continue;
                y1 = Math.min(y1, h);
            }
            tes.setColorRGBA_I(rgb, ai);
            quads += line(tes, bx + e[p], by + y0, bz + e[p + 2], bx + e[p + 3], by + y1, bz + e[p + 5], LINE);
            if (quads >= FLUSH) {
                tes = restart(tes);
                quads = 0;
            }
        }
        finish(tes);
    }

    /** A ghost face's normal: its own axis, pointing to the side the viewer is on (the camera is at the origin). */
    private static void faceNormal(Tessellator tes, int side, double fx, double fy, double fz) {
        switch (side >> 1) {
            case 0:
                tes.setNormal(0f, fy > 0 ? -1f : 1f, 0f);
                return;
            case 1:
                tes.setNormal(0f, 0f, fz > 0 ? -1f : 1f);
                return;
            default:
                tes.setNormal(fx > 0 ? -1f : 1f, 0f, 0f);
        }
    }

    // ---- light on the ghost

    /** Thin lines round the top of each cell of the lowest layer still to build. */
    private static void layer(BuildScan.Ghost g, int tint, float a) {
        Shapes.begin(true);
        Tessellator tes = start();
        int quads = 0, ai = alpha255(a);
        for (int i : g.layer) {
            if (g.gone.get(i)) continue;
            BuildPlan.Step s = g.plan.steps.get(i);
            double x0 = s.x - camX, x1 = x0 + 1, z0 = s.z - camZ, z1 = z0 + 1, y = s.y + 1.02 - camY;
            tes.setColorRGBA_I(tint, ai);
            quads += line(tes, x0, y, z0, x1, y, z0, LINE);
            quads += line(tes, x1, y, z0, x1, y, z1, LINE);
            quads += line(tes, x1, y, z1, x0, y, z1, LINE);
            quads += line(tes, x0, y, z1, x0, y, z0, LINE);
            if (quads >= FLUSH) {
                tes = restart(tes);
                quads = 0;
            }
        }
        finish(tes);
        Shapes.end();
    }

    /**
     * The scan plane at world height {@code h}, {@code rise} of the way up: a faint sheet across the plan's box, a
     * glow just under it round the box's sides and a bright line where it meets them.
     */
    private static void plane(BuildScan.Ghost g, double h, float rise) {
        int[] b = g.bounds;
        double x0 = b[0] - camX, x1 = b[3] + 1 - camX, z0 = b[2] - camZ, z1 = b[5] + 1 - camZ, y = h - camY;
        double below = Math.max(b[1] - camY, y - 0.8);
        float fade = Math.max(0f, Math.min(1f, Math.min(rise * 10f, (1f - rise) * 6f)));
        if (fade <= 0) return;
        Shapes.begin(true);
        Shapes.plane(x0, y, z0, x1, z1, CYAN, 0.08f * fade);
        double[][] corner = { { x0, z0 }, { x1, z0 }, { x1, z1 }, { x0, z1 } };
        for (int k = 0; k < 4; k++) {
            double[] u = corner[k], w = corner[(k + 1) % 4];
            Shapes.quad3(
                new double[] { u[0], below, u[1] },
                new double[] { w[0], below, w[1] },
                new double[] { w[0], y, w[1] },
                new double[] { u[0], y, u[1] },
                CYAN,
                0f,
                0f,
                0.3f * fade,
                0.3f * fade);
        }
        Tessellator tes = start();
        tes.setColorRGBA_I(LIGHT, alpha255(0.85f * fade));
        for (int k = 0; k < 4; k++) {
            double[] u = corner[k], w = corner[(k + 1) % 4];
            line(tes, u[0], y, u[1], w[0], y, w[1], LINE * 1.7);
        }
        finish(tes);
        Shapes.end();
    }

    // ---- outlines, the grading edge, blocked cells, the ring

    /** Each stage's box as thin lines, the current stage's brighter. */
    private static void outlines(BuildScan.Ghost g, int stage) {
        if (g.stageBounds == null) return;
        Shapes.begin(true);
        Tessellator tes = start();
        int quads = 0;
        for (int s = 0; s < g.stageBounds.length; s++) {
            int[] b = g.stageBounds[s];
            if (b == null) continue;
            tes.setColorRGBA_I(CYAN, alpha255(s == stage ? 0.7f : 0.35f));
            quads += boxEdges(
                tes,
                b[0] - camX,
                b[1] - camY,
                b[2] - camZ,
                b[3] + 1 - camX,
                b[4] + 1 - camY,
                b[5] + 1 - camZ,
                LINE * 1.5);
            if (quads >= FLUSH) {
                tes = restart(tes);
                quads = 0;
            }
        }
        finish(tes);
        Shapes.end();
    }

    /**
     * The edge of the ground the plan clears, a faint amber line just over the floor level (Y0 + 1, the top of the
     * base layer), seen faintly through the hills it will take away.
     */
    private static void edge(BuildScan.Ghost g) {
        double y = g.oy + 1.05 - camY;
        Shapes.begin(true);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        edgePass(g, y, 0.1f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        edgePass(g, y, 0.3f);
        Shapes.end();
    }

    private static void edgePass(BuildScan.Ghost g, double y, float a) {
        Tessellator tes = start();
        int quads = 0, ai = alpha255(a);
        int[] e = g.edge;
        for (int k = 0; k + 3 < e.length; k += 4) {
            tes.setColorRGBA_I(AMBER, ai);
            quads += line(tes, e[k] - camX, y, e[k + 1] - camZ, e[k + 2] - camX, y, e[k + 3] - camZ, LINE * 1.5);
            if (quads >= FLUSH) {
                tes = restart(tes);
                quads = 0;
            }
        }
        finish(tes);
    }

    /** Red boxes round the blocked cells (packed relative to the centre {@code c}), seen through everything. */
    private static void blocked(int[] cells, int[] c, double t) {
        int n = Math.min(MAX_BLOCKED, cells.length);
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 0.2);
        Shapes.begin(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        Tessellator tes = start();
        tes.setColorRGBA_I(RED, alpha255(0.35f * pulse));
        for (int k = 0; k < n; k++) {
            double x0 = c[0] + FxCodec.dx(cells[k]) - camX - 0.02, y0 = c[1] + FxCodec.dy(cells[k]) - camY - 0.02,
                z0 = c[2] + FxCodec.dz(cells[k]) - camZ - 0.02;
            boxFaces(tes, x0, y0, z0, x0 + 1.04, y0 + 1.04, z0 + 1.04);
        }
        finish(tes);
        Shapes.additive(true);
        tes = start();
        int quads = 0;
        for (int k = 0; k < n; k++) {
            double x0 = c[0] + FxCodec.dx(cells[k]) - camX - 0.02, y0 = c[1] + FxCodec.dy(cells[k]) - camY - 0.02,
                z0 = c[2] + FxCodec.dz(cells[k]) - camZ - 0.02;
            tes.setColorRGBA_I(RED, alpha255(0.8f * pulse));
            quads += boxEdges(tes, x0, y0, z0, x0 + 1.04, y0 + 1.04, z0 + 1.04, LINE * 1.3);
            if (quads >= FLUSH) {
                tes = restart(tes);
                quads = 0;
            }
        }
        finish(tes);
        Shapes.end();
    }

    /**
     * The ring of hard light spreading over the new floor, {@code f} of the way: from the nexus out to the promenade's
     * edge for a new campus, from the middle of a module's box to its edge ({@link BuildScan.Ghost#ringX}).
     */
    private static void ring(BuildScan.Ghost g, double f) {
        double cx = g.ringX - camX, cz = g.ringZ - camZ, y = g.oy + 1.05 - camY;
        double radius = g.ringR;
        double r = Math.max(0.5, radius * (1 - (1 - f) * (1 - f)));
        float a = (float) (1 - f);
        Shapes.begin(true);
        Shapes.ring(cx, y, cz, Math.max(0, r - 3), r, 0, Math.PI * 2, CYAN, 0f, CYAN, 0.5f * a);
        Shapes.ring(cx, y + 0.01, cz, Math.max(0, r - 0.3), r, 0, Math.PI * 2, LIGHT, 0.9f * a, LIGHT, 0.9f * a);
        Shapes.end();
    }

    // ---- geometry

    /** The six faces of a box, for a fill (the normal is the viewer's, set when the batch started). */
    private static void boxFaces(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1) {
        t.addVertex(x0, y0, z0);
        t.addVertex(x1, y0, z0);
        t.addVertex(x1, y0, z1);
        t.addVertex(x0, y0, z1);
        t.addVertex(x0, y1, z0);
        t.addVertex(x0, y1, z1);
        t.addVertex(x1, y1, z1);
        t.addVertex(x1, y1, z0);
        t.addVertex(x0, y0, z0);
        t.addVertex(x0, y1, z0);
        t.addVertex(x1, y1, z0);
        t.addVertex(x1, y0, z0);
        t.addVertex(x0, y0, z1);
        t.addVertex(x1, y0, z1);
        t.addVertex(x1, y1, z1);
        t.addVertex(x0, y1, z1);
        t.addVertex(x0, y0, z0);
        t.addVertex(x0, y0, z1);
        t.addVertex(x0, y1, z1);
        t.addVertex(x0, y1, z0);
        t.addVertex(x1, y0, z0);
        t.addVertex(x1, y1, z0);
        t.addVertex(x1, y1, z1);
        t.addVertex(x1, y0, z1);
    }

    /** The twelve edges of a box as thin lines; returns the quads added. */
    private static int boxEdges(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1,
        double w) {
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
     * A thin line between two camera-relative points as bands turned to the camera (as {@code Shapes.string}), cut
     * into pieces of at most {@link #PIECE} blocks so each piece can be as wide as about a pixel where it is;
     * returns the quads added.
     */
    private static int line(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1, double w) {
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

    // ---- state

    /** The viewer's direction reversed, the normal {@link Shapes} gives effect geometry. */
    private static void viewNormal() {
        double yaw = Math.toRadians(RenderManager.instance.playerViewY),
            pitch = Math.toRadians(RenderManager.instance.playerViewX);
        nx = (float) (Math.sin(yaw) * Math.cos(pitch));
        ny = (float) Math.sin(pitch);
        nz = (float) (-Math.cos(yaw) * Math.cos(pitch));
    }

    /** Opens a batch of untextured quads facing the viewer (inside a {@link Shapes#begin}). */
    private static Tessellator start() {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setNormal(nx, ny, nz);
        return t;
    }

    private static void finish(Tessellator t) {
        t.draw();
    }

    /** Draws what the batch holds and opens another (the caller sets its colour again). */
    private static Tessellator restart(Tessellator t) {
        finish(t);
        return start();
    }

    /**
     * After an error: closes a batch it broke off, ours or one of {@link Shapes}' (the tessellator refuses to start
     * another while one is open, which would take every later drawing with it).
     */
    private static void recover() {
        try {
            Tessellator.instance.draw();
        } catch (Throwable ignored) {
            // nothing was open
        }
    }

    /** Restores the GL state of whichever part ran, on every path. */
    private static void close() {
        Shapes.end();
        if (begun) {
            begun = false;
            GL11.glPolygonOffset(0f, 0f);
            FluxDraw.worldEnd();
        }
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The build projection's {} failed and is off until the game restarts", NAMES[part], x);
    }

    private static int alpha255(float a) {
        return (int) (Math.max(0f, Math.min(1f, a)) * 255);
    }

    private static double sq(double d) {
        return d * d;
    }
}
