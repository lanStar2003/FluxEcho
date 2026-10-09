package com.fluxecho.gate.client;

import static com.fluxecho.client.FluxDraw.CYAN;
import static com.fluxecho.client.FluxDraw.VIOLET;
import static com.fluxecho.logic.GateGeometry.HALF_WIDTH;
import static com.fluxecho.logic.GateGeometry.HEIGHT;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import com.fluxecho.client.FluxDraw;
import com.fluxecho.logic.GateGeometry;

/**
 * The parts of a light gate that are not the view through it: the frame (two pylons and a floating arc of segments,
 * breathing), the membrane when there is nothing to show through it, and the ripple over the screen while walking
 * through. World parts are drawn like the holograms, so shader packs light them right.
 */
final class GateDraw {

    private GateDraw() {}

    /** The gate's frame, in the gate's own space: x across, y up, z out of its front. */
    static void frame(GateGeometry.Gate g, double cx, double cy, double cz, float t, boolean linked) {
        float a = linked ? 0.85f : 0.35f, pulse = 0.75f + 0.25f * (float) Math.sin(t * 0.12);
        GL11.glPushMatrix();
        FluxDraw.worldBegin();
        FluxDraw.begin();
        GL11.glTranslated(g.cx() - cx, g.y - cy, g.cz() - cz);
        GL11.glRotatef(-90f * g.facing, 0f, 1f, 0f);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        tes.setNormal(0, 1, 0);
        double w = HALF_WIDTH;
        box(tes, -w - 0.22, 0, -0.09, -w - 0.04, HEIGHT + 0.2, 0.09, CYAN, a * pulse);
        box(tes, w + 0.04, 0, -0.09, w + 0.22, HEIGHT + 0.2, 0.09, CYAN, a * pulse);
        for (int i = 0; i <= 8; i++) {
            double x = -w + i * (2 * w / 8), k = x / w;
            double y = HEIGHT + 0.3 + 0.5 * (1 - k * k) + 0.06 * Math.sin(t * 0.08 + i * 0.9);
            int c = mix(CYAN, VIOLET, i / 8f);
            box(tes, x - 0.13, y - 0.05, -0.06, x + 0.13, y + 0.05, 0.06, c, a);
        }
        // the membrane's glowing edge
        edge(tes, -w, 0, -w + 0.1, HEIGHT, CYAN, a * 0.6f * pulse);
        edge(tes, w - 0.1, 0, w, HEIGHT, CYAN, a * 0.6f * pulse);
        edge(tes, -w, HEIGHT - 0.1, w, HEIGHT, VIOLET, a * 0.6f * pulse);
        tes.draw();
        FluxDraw.end();
        FluxDraw.worldEnd();
        GL11.glPopMatrix();
    }

    /** The membrane with nothing behind it: a sheet of light with bands rising through it. */
    static void membrane(GateGeometry.Gate g, double cx, double cy, double cz, float t, boolean linked, double front) {
        float a = linked ? (front > 0 ? 0.42f : 0.18f) : 0.12f;
        GL11.glPushMatrix();
        FluxDraw.worldBegin();
        FluxDraw.begin();
        GL11.glTranslated(g.cx() - cx, g.y - cy, g.cz() - cz);
        GL11.glRotatef(-90f * g.facing, 0f, 1f, 0f);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        tes.setNormal(0, 0, 1);
        double w = HALF_WIDTH;
        tes.setColorRGBA_I(CYAN, (int) (a * 255));
        tes.addVertex(-w, 0, 0);
        tes.addVertex(w, 0, 0);
        tes.setColorRGBA_I(VIOLET, (int) (a * 0.5f * 255));
        tes.addVertex(w, HEIGHT, 0);
        tes.addVertex(-w, HEIGHT, 0);
        if (linked) for (int k = 0; k < 3; k++) {
            double y = ((t * 0.02 + k / 3.0) % 1.0) * HEIGHT;
            edge(tes, -w, y, w, Math.min(HEIGHT, y + 0.12), FluxDraw.WHITE, a * 0.5f);
        }
        tes.draw();
        FluxDraw.end();
        FluxDraw.worldEnd();
        GL11.glPopMatrix();
    }

    /**
     * Over the whole screen while walking through: the last view through the gate ({@code texture}, or the flux
     * layer's colour when there was none), rippling out from the middle and fading at {@code alpha}.
     */
    static void overlay(int width, int height, int texture, float alpha, float seconds) {
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_TEXTURE_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 1, 0, 1, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Tessellator tes = Tessellator.instance;
        double aspect = width / (double) Math.max(1, height);
        double amp = 0.014 * Math.exp(-seconds * 1.8);
        if (texture >= 0) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glColor4f(1f, 1f, 1f, alpha);
            int nx = 24, ny = 14;
            tes.startDrawingQuads();
            for (int i = 0; i < nx; i++) for (int j = 0; j < ny; j++) {
                ripple(tes, i / (double) nx, j / (double) ny, aspect, amp, seconds);
                ripple(tes, (i + 1) / (double) nx, j / (double) ny, aspect, amp, seconds);
                ripple(tes, (i + 1) / (double) nx, (j + 1) / (double) ny, aspect, amp, seconds);
                ripple(tes, i / (double) nx, (j + 1) / (double) ny, aspect, amp, seconds);
            }
            tes.draw();
        } else {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            tes.startDrawingQuads();
            tes.setColorRGBA_I(0x0A1622, (int) (alpha * 230));
            tes.addVertex(0, 0, 0);
            tes.addVertex(1, 0, 0);
            tes.setColorRGBA_I(0x1F3C4E, (int) (alpha * 230));
            tes.addVertex(1, 1, 0);
            tes.addVertex(0, 1, 0);
            tes.draw();
        }
        // a ring of light running outwards, and a glow that fades
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        double r = seconds * 1.2, band = 0.06;
        float ringA = alpha * (float) Math.max(0, 0.5 - seconds * 0.3);
        if (ringA > 0.01f) {
            int segs = 48;
            tes.startDrawingQuads();
            for (int s = 0; s < segs; s++) {
                double a0 = s * Math.PI * 2 / segs, a1 = (s + 1) * Math.PI * 2 / segs;
                tes.setColorRGBA_I(CYAN, 0);
                tes.addVertex(0.5 + Math.cos(a0) * (r - band) / aspect, 0.5 + Math.sin(a0) * (r - band), 0);
                tes.addVertex(0.5 + Math.cos(a1) * (r - band) / aspect, 0.5 + Math.sin(a1) * (r - band), 0);
                tes.setColorRGBA_I(CYAN, (int) (ringA * 255));
                tes.addVertex(0.5 + Math.cos(a1) * r / aspect, 0.5 + Math.sin(a1) * r, 0);
                tes.addVertex(0.5 + Math.cos(a0) * r / aspect, 0.5 + Math.sin(a0) * r, 0);
            }
            tes.draw();
        }
        tes.startDrawingQuads();
        tes.setColorRGBA_I(CYAN, (int) (alpha * 255 * 0.18f * Math.exp(-seconds * 2)));
        tes.addVertex(0, 0, 0);
        tes.addVertex(1, 0, 0);
        tes.addVertex(1, 1, 0);
        tes.addVertex(0, 1, 0);
        tes.draw();
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopAttrib();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    /** One vertex of the rippled screen: the picture pushed out along rings around the middle. */
    private static void ripple(Tessellator tes, double u, double v, double aspect, double amp, float seconds) {
        double du = (u - 0.5) * aspect, dv = v - 0.5, r = Math.sqrt(du * du + dv * dv);
        double wave = r < 1e-4 ? 0 : amp * Math.sin(r * 30 - seconds * 12) / r;
        double tu = Math.max(0, Math.min(1, u + du / aspect * wave)), tv = Math.max(0, Math.min(1, v + dv * wave));
        tes.addVertexWithUV(u, v, 0, tu, tv);
    }

    private static void edge(Tessellator tes, double x0, double y0, double x1, double y1, int rgb, float a) {
        tes.setColorRGBA_I(rgb, (int) (Math.max(0, Math.min(1, a)) * 255));
        tes.addVertex(x0, y0, 0.01);
        tes.addVertex(x1, y0, 0.01);
        tes.addVertex(x1, y1, 0.01);
        tes.addVertex(x0, y1, 0.01);
    }

    private static void box(Tessellator tes, double x0, double y0, double z0, double x1, double y1, double z1, int rgb,
        float a) {
        tes.setColorRGBA_I(rgb, (int) (Math.max(0, Math.min(1, a)) * 255));
        // six faces; culling is off, so the winding does not matter
        double[][] f = { { x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0 },
            { x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1 }, { x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0 },
            { x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0 }, { x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1 },
            { x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1 } };
        for (double[] q : f) for (int i = 0; i < 12; i += 3) tes.addVertex(q[i], q[i + 1], q[i + 2]);
    }

    private static int mix(int a, int b, float k) {
        int r = (int) ((a >> 16 & 255) * (1 - k) + (b >> 16 & 255) * k),
            gr = (int) ((a >> 8 & 255) * (1 - k) + (b >> 8 & 255) * k),
            bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return r << 16 | gr << 8 | bl;
    }
}
