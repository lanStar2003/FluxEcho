package com.fluxecho.library.client;

import static com.fluxecho.client.FluxDraw.*;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.fluxecho.campus.CampusModule;
import com.fluxecho.client.FluxDraw;
import com.fluxecho.core.EchoText;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.library.TileLibrary;
import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.LibraryUnits;
import com.fluxecho.logic.Parts;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Echo Archive's bookcase under the crosshair (the user's 「整组为单位」: a bookcase is used as a whole): a thin
 * outline round its nine shelf bodies, the nine places marked on its face and the one looked at lit, and a small label
 * over it with its call number, how full it is ({@code n/9}, and a 3 x 3 map of its places as the player sees them),
 * and the name of the book looked at. It answers what a right-click there would act on before the click.
 * <p>
 * The bookcase is found as a click finds it ({@link TileLibrary#unitAt} with the point the look meets the block), from
 * {@code Minecraft.objectMouseOver} once a draw. That is the player's own look, so nothing is drawn when the camera is
 * not near the player's reach (a light gate drawing the world again from its far side). The outline is depth-tested
 * and added onto the world; the label is drawn over everything, as a name plate is, so the bookcase itself cannot cut
 * it. The caller ({@link ArchiveRender}) restores the GL state these leave on every path.
 */
@SideOnly(Side.CLIENT)
final class ArchiveLook {

    private static final int LIGHT = 0xE8FBFF;
    /** How far (blocks) the point looked at may be from the camera: the reach and a margin. */
    private static final double REACH = 8;
    /** How far the outline stands off the bookcase, and its lines' widths. */
    private static final double OFF = 0.025, EDGE = 0.022, RIM = 0.03, DIVIDER = 0.016;
    /** The label's pixel size (blocks), its widest, and where it floats: in front of the crown, off the face. */
    private static final float PX = 1 / 96f;
    private static final int MAX_WIDTH = 220;
    private static final double LABEL_OUT = 0.45;

    /** What the crosshair rests on: a bookcase and, when it is a shelf body, its place (else -1). */
    static final class Target {

        final LibraryUnits.Unit unit;
        final int slot;

        Target(LibraryUnits.Unit unit, int slot) {
            this.unit = unit;
            this.slot = slot;
        }
    }

    private ArchiveLook() {}

    /** The bookcase of this Archive the player looks at, or null. */
    static Target find(TileLibrary l) {
        MovingObjectPosition hit = Minecraft.getMinecraft().objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || hit.hitVec == null)
            return null;
        double hx = hit.hitVec.xCoord - RenderManager.renderPosX, hy = hit.hitVec.yCoord - RenderManager.renderPosY,
            hz = hit.hitVec.zCoord - RenderManager.renderPosZ;
        if (hx * hx + hy * hy + hz * hz > REACH * REACH) return null;
        int x = hit.blockX, y = hit.blockY, z = hit.blockZ;
        int[] c = l.local(x, y, z);
        if (c[0] < 0 || c[0] >= ArchiveShape.WIDTH
            || c[1] < 0
            || c[1] >= ArchiveShape.HEIGHT
            || c[2] < 0
            || c[2] >= ArchiveShape.DEPTH) return null;
        World w = l.getWorldObj();
        if (w == null || !part(w.getBlock(x, y, z), w.getBlockMetadata(x, y, z))) return null;
        int unit = l.unitAt(x, y, z, hit.sideHit, hit.hitVec.xCoord, hit.hitVec.zCoord);
        if (unit < 0 || unit >= LibraryUnits.UNITS.size()) return null;
        int slot = l.slotAt(x, y, z);
        return new Target(LibraryUnits.UNITS.get(unit), slot / 9 == unit ? slot : -1);
    }

    /** Whether the block is one a bookcase is made of: a shelf body, a post, a plinth or a crown. */
    private static boolean part(Block b, int meta) {
        if (b == FrameModule.frame) return meta == BlockFrame.SHELF;
        return b == CampusModule.fitting && (meta == Parts.F_POST || meta == Parts.F_PLINTH || meta == Parts.F_CROWN);
    }

    /**
     * The outline: the box of the nine bodies, its face's rim brighter with the places divided, and the place looked
     * at filled. Opens and closes its own {@link Shapes} state.
     */
    static void outline(ArchiveFrame f, Target g, double t, float a) {
        LibraryUnits.Unit u = g.unit;
        float k = a * (0.85f + 0.15f * (float) Math.sin(t * 0.15));
        double x0 = Math.min(u.x(0), u.x(2)), x1 = Math.max(u.x(0), u.x(2)) + 1;
        double z0 = Math.min(u.z(0), u.z(2)), z1 = Math.max(u.z(0), u.z(2)) + 1;
        double y0 = u.yBody, y1 = u.yBody + 3;
        // along the run from lo to hi; the face's plane a little off it
        double lo = u.alongX ? x0 : z0, hi = u.alongX ? x1 : z1;
        double face = u.faceX != 0 ? (u.faceX > 0 ? x1 : x0) + u.faceX * (OFF + 0.005)
            : (u.faceZ > 0 ? z1 : z0) + u.faceZ * (OFF + 0.005);
        Shapes.begin(true);
        Tessellator tes = ArchiveFrame.start();
        tes.setColorRGBA_I(CYAN, ArchiveFrame.alpha255(0.45f * k));
        f.boxEdges(tes, x0 - OFF, y0 - OFF, z0 - OFF, x1 + OFF, y1 + OFF, z1 + OFF, EDGE);
        tes.setColorRGBA_I(LIGHT, ArchiveFrame.alpha255(0.8f * k));
        rect(tes, f, u, face, lo, hi, y0, y1, RIM);
        tes.setColorRGBA_I(CYAN, ArchiveFrame.alpha255(0.25f * k));
        for (int i = 1; i < 3; i++) {
            faceLine(tes, f, u, face, lo + i, y0, lo + i, y1, DIVIDER);
            faceLine(tes, f, u, face, lo, y0 + i, hi, y0 + i, DIVIDER);
        }
        if (g.slot >= 0) {
            int[] c = LibraryUnits.cellOfSlot(g.slot);
            double along = u.alongX ? c[0] : c[2];
            tes.setColorRGBA_I(VIOLET, ArchiveFrame.alpha255(0.16f * k));
            facePoint(tes, f, u, face, along, c[1]);
            facePoint(tes, f, u, face, along + 1, c[1]);
            facePoint(tes, f, u, face, along + 1, c[1] + 1);
            facePoint(tes, f, u, face, along, c[1] + 1);
            tes.setColorRGBA_I(LIGHT, ArchiveFrame.alpha255(0.6f * k));
            rect(tes, f, u, face, along + 0.04, along + 0.96, c[1] + 0.04, c[1] + 0.96, DIVIDER);
        }
        tes.draw();
        Shapes.end();
    }

    /** A rectangle on the bookcase's face: from {@code a0} to {@code a1} along the run, {@code y0} to {@code y1}. */
    private static void rect(Tessellator t, ArchiveFrame f, LibraryUnits.Unit u, double face, double a0, double a1,
        double y0, double y1, double w) {
        faceLine(t, f, u, face, a0, y0, a1, y0, w);
        faceLine(t, f, u, face, a1, y0, a1, y1, w);
        faceLine(t, f, u, face, a1, y1, a0, y1, w);
        faceLine(t, f, u, face, a0, y1, a0, y0, w);
    }

    private static void faceLine(Tessellator t, ArchiveFrame f, LibraryUnits.Unit u, double face, double a0, double y0,
        double a1, double y1, double w) {
        if (u.faceX != 0) f.line(t, face, y0, a0, face, y1, a1, w);
        else f.line(t, a0, y0, face, a1, y1, face, w);
    }

    private static void facePoint(Tessellator t, ArchiveFrame f, LibraryUnits.Unit u, double face, double along,
        double y) {
        if (u.faceX != 0) f.vertex(t, face, y, along);
        else f.vertex(t, along, y, face);
    }

    /**
     * The label, facing the camera in front of the bookcase's crown: the call number and fill, a map of the nine
     * places (filled ones violet, the one looked at framed), and the book looked at (or that its place is empty).
     * Opens the world state and turns the depth test off; the caller ends both ({@link ArchiveRender}).
     */
    static void label(TileLibrary l, ArchiveFrame f, Target g, float a) {
        LibraryUnits.Unit u = g.unit;
        String head = EchoText.t("library.look.unit", u.call, l.unitFill(u.index));
        String book = null;
        boolean has = false;
        if (g.slot >= 0) {
            ItemStack s = l.book(g.slot);
            has = s != null;
            book = has ? name(s) : EchoText.t("library.look.empty");
        }
        int text = font().getStringWidth(head);
        if (book != null) text = Math.max(text, font().getStringWidth(book));
        int w = Math.min(MAX_WIDTH, 26 + text + 6), h = book != null ? 24 : 21;
        double lx = u.x(1) + 0.5 + u.faceX * (0.5 + LABEL_OUT), lz = u.z(1) + 0.5 + u.faceZ * (0.5 + LABEL_OUT);
        double ly = u.yBody + 3.5;

        ArchiveRender.worldState();
        ArchiveRender.depthOff();
        GL11.glPushMatrix();
        // the matrix is popped on every path, so a failing label cannot leave the stack one deeper
        try {
            GL11.glTranslated(f.x(lx, lz), f.y(ly), f.z(lx, lz));
            GL11.glRotatef(-RenderManager.instance.playerViewY, 0f, 1f, 0f);
            GL11.glRotatef(RenderManager.instance.playerViewX, 1f, 0f, 0f);
            GL11.glScalef(-PX, -PX, PX);
            GL11.glTranslatef(-w / 2f, -h / 2f, 0);
            FluxDraw.begin();
            pane(0, 0, w, h, 0.85f * a);
            FluxDraw.rect(1, h - 1, w - 1, h, VIOLET, 0.8f * a);
            int gy = (h - 17) / 2;
            for (int k = 0; k < 9; k++) {
                int gx = 4 + k % 3 * 6, cy = gy + k / 3 * 6;
                boolean filled = l.book(u.index * 9 + k) != null;
                FluxDraw.rect(gx, cy, gx + 5, cy + 5, filled ? VIOLET : SEAM, (filled ? 0.95f : 0.7f) * a);
                if (u.index * 9 + k == g.slot) frame(gx - 1, cy - 1, gx + 6, cy + 6, WHITE, 0.9f * a);
            }
            FluxDraw.end();
            FluxDraw.text(fit(head, w - 30), 26, 3, CYAN, a);
            if (book != null) FluxDraw.text(fit(book, w - 30), 26, 13, has ? WHITE : DIM, a);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** A book's name; one whose name cannot be had here shows as a question mark. */
    private static String name(ItemStack s) {
        try {
            return s.getDisplayName();
        } catch (RuntimeException e) {
            return "?";
        }
    }
}
