package com.fluxecho.library.client;

import static com.fluxecho.client.FluxDraw.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.fluxecho.client.Motifs;
import com.fluxecho.core.EchoText;
import com.fluxecho.library.ArchiveGui;
import com.fluxecho.library.ArchiveMap;
import com.fluxecho.library.TileLibrary;
import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.LibraryUnits;

/**
 * The drawn parts of the Echo Archive's GUI ({@code ArchiveGui}), in the library's look (the flux panes, cyan accents,
 * the library's violet): the window and its panels, the bookcase's header and the marks on its grid, the arrows, the
 * return slot's label, the floor plans of both storeys, the catalogue's lines and scroll bar, and the strip's counts.
 * Everything reads the client's copy of the books ({@link TileLibrary#clientBooks}) and the window's synced fields.
 * Client only.
 */
public final class ArchiveScreen {

    private static final RenderItem ITEMS = new RenderItem();
    /** The card desk's gold, as {@link LibraryScreen#chosen} draws it. */
    private static final int GOLD = 0xFFD27A;
    // the plans
    private static final int FLOOR = 0x0C1A28, WALL = 0x3A6A86, POST = 0x1A2C3C, VOID = 0x03070C, EDGE = 0x1E4656,
        STAIR = 0x24485A, LECTERN = 0x5A4A8A, EMPTY = 0x24384A, PART = 0x1C5C6E;
    private static final int UNITS = LibraryUnits.UNITS.size();

    /** The fixed marks of each storey's plan (walls, windows, posts, stairs, desk...), worked out once. */
    private static List<Mark> ground, gallery;

    private ArchiveScreen() {}

    private static float ticks() {
        return Minecraft.getSystemTime() / 50f;
    }

    // ---- the window

    /** The pane, the page's panels, the frame round the grid, and the lines over the strip and the inventory. */
    public static void background(ArchiveGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            begin();
            pane(0, 0, w, h, 1f);
            if (v.page == ArchiveGui.SHELF) {
                panel(6, ArchiveGui.PAGE_TOP, ArchiveGui.SHELF_RIGHT, ArchiveGui.PAGE_BOTTOM, 0x120A28);
                panel(
                    ArchiveGui.MAP_LEFT,
                    ArchiveGui.PAGE_TOP,
                    ArchiveGui.PANEL_RIGHT,
                    ArchiveGui.PAGE_BOTTOM,
                    0x0C1A28);
                // the bookcase: a violet frame round the grid, lines running in to its back
                double x0 = ArchiveGui.GRID_X - 3, y0 = ArchiveGui.GRID_Y - 3, x1 = ArchiveGui.GRID_X + 57,
                    y1 = ArchiveGui.GRID_Y + 57;
                gradient(x0, y0, x1, y1, 0x120A28, 0.9f, DEEP, 0.9f);
                float t = ticks();
                double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
                for (int i = 0; i < 5; i++) {
                    double f = (t * 0.01 + i / 5.0) % 1, hw = (x1 - x0) / 2 * (1 - f);
                    frame(cx - hw, cy - hw, cx + hw, cy + hw, VIOLET, 0.12f * (float) f);
                }
                frame(x0, y0, x1, y1, VIOLET, 0.6f);
                rect(9, ArchiveGui.DROP_Y - 2, ArchiveGui.SHELF_RIGHT - 3, ArchiveGui.DROP_Y - 1, SEAM, 1f);
            } else panel(6, ArchiveGui.PAGE_TOP, ArchiveGui.PANEL_RIGHT, ArchiveGui.PAGE_BOTTOM, 0x0C1A28);
            rect(8, ArchiveGui.STRIP_Y, w - 8, ArchiveGui.STRIP_Y + 1, SEAM, 1f);
            rect(8, ArchiveGui.INV_Y - 3, w - 8, ArchiveGui.INV_Y - 2, SEAM, 1f);
            end();
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void panel(double x0, double y0, double x1, double y1, int top) {
        gradient(x0, y0, x1, y1, top, 0.9f, DEEP, 0.9f);
        frame(x0, y0, x1, y1, SEAM, 1f);
    }

    /** The Archive's name on the left, its dock state on the right (red when docked without power). */
    public static void header(TileLibrary l, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            begin();
            hgradient(1, 1, w * 0.6, h - 1, VIOLET, 0.3f, VIOLET, 0f);
            rect(1, h - 1, w - 1, h, VIOLET, 0.6f);
            end();
            String title = EchoText.t("library.gui.archive");
            text(title, 4, 3, VIOLET, 1f);
            boolean docked = "docked".equals(l.guiDock);
            String status = EchoText.t("dock." + (docked && !l.guiPowered ? "no_power" : l.guiDock));
            right(
                fit(status, (int) w - 12 - font().getStringWidth(title)),
                (int) w - 4,
                3,
                docked ? (l.guiPowered ? GREEN : RED) : AMBER,
                1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    // ---- the bookcase page

    /** The bookcase shown: its call number, its fill and where it stands. */
    public static void unitHead(TileLibrary l, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            int u = l.guiUnit;
            if (u < 0 || u >= UNITS) {
                text("…", 0, 1, DIM, 1f);
                return;
            }
            int n = l.unitFill(u);
            String fill = EchoText.t("library.gui.fill", n);
            right(fill, w, 1, n >= 9 ? VIOLET : n > 0 ? CYAN : DIM, 1f);
            text(fit(l.callNumber(u), (int) w - font().getStringWidth(fill) - 4), 0, 1, WHITE, 1f);
            small(fit(where(u), (int) (w / 0.75f)), 0, 10.5, 0.75f, DIM, 1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** The header's tooltip: the bookcase, where it stands, and its books by place. */
    public static List<String> unitTip(TileLibrary l) {
        int u = l.guiUnit;
        if (u < 0 || u >= UNITS) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        out.add(EchoText.t("library.gui.unit.tip", l.callNumber(u), l.unitFill(u)));
        out.add(EnumChatFormatting.GRAY + where(u));
        for (int k = 0; k < 9; k++) {
            ItemStack s = l.book(u * 9 + k);
            if (s != null) out.add(EchoText.t("library.gui.unit.book", place(k), name(s)));
        }
        return out;
    }

    /** A mark on a place of the grid: gold round the book the card desk writes for. */
    public static void place(TileLibrary l, int k, float x, float y, float w, float h) {
        int u = l.guiUnit;
        if (u < 0 || u >= UNITS || ArchiveGui.chosenSlot(l) != u * 9 + k) return;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            begin();
            frame(0, 0, w, h, GOLD, 0.9f);
            corners(0, 0, w, h, 3, GOLD, 1f);
            end();
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** An arrow button: one chevron to the neighbouring bookcase, two to the one five along. */
    public static void arrows(float x, float y, float w, float h, boolean right, boolean twice) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            begin();
            gradient(0, 0, w, h, PANE, 1f, DEEP, 1f);
            frame(0, 0, w, h, SEAM, 1f);
            int n = twice ? 2 : 1;
            double span = 4 * n + (n - 1), x0 = Math.floor((w - span) / 2.0), cy = Math.floor(h / 2.0);
            for (int c = 0; c < n; c++) chevron(x0 + c * 5, cy, right, CYAN);
            end();
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** A chevron four pixels wide, its point to the right or the left, centred on {@code cy}. */
    private static void chevron(double x0, double cy, boolean right, int rgb) {
        for (int i = 0; i < 4; i++) {
            double col = right ? x0 + i : x0 + 3 - i, half = 3 - i;
            rect(col, cy - half, col + 1, cy + half + 1, rgb, 1f);
        }
    }

    /** The return slot's mark: a faint arrow down into a book's spine. */
    public static void dropMark(float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            begin();
            double cx = Math.floor(w / 2.0);
            for (int i = 0; i < 4; i++) rect(cx - (3 - i), 4 + i, cx + (3 - i) + 1, 5 + i, VIOLET, 0.45f);
            rect(cx - 4, 11, cx + 5, 13, VIOLET, 0.3f);
            end();
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** Beside the return slot: its name, and what it does (or that the bookcase is full). */
    public static void drop(TileLibrary l, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            int u = l.guiUnit;
            boolean full = u >= 0 && u < UNITS && l.unitFill(u) >= 9;
            small(fit(EchoText.t("library.gui.drop"), (int) (w / 0.75f)), 0, 2, 0.75f, VIOLET, 1f);
            small(
                fit(EchoText.t(full ? "library.gui.drop_full" : "library.gui.drop_hint"), (int) (w / 0.6f)),
                0,
                10.5,
                0.6f,
                full ? AMBER : DIM,
                1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    // ---- the plans

    /**
     * A storey's floor plan: the walls (windows lit, the doors open), the posts, the stairs, the desk and lecterns, the
     * gallery's open middle; every bookcase a tile coloured by its fill (dim when empty, cyan as it fills, violet when
     * full); the bookcase shown outlined white with its book face in cyan, the one under the mouse in cyan; the player
     * where they stand, looking where they look; and under the plan, the storey's name and books.
     */
    public static void map(TileLibrary l, int storey, int hover, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            map0(l, storey, hover, w);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void map0(TileLibrary l, int storey, int hover, float w) {
        float t = ticks();
        int kept = 0, places = 0;
        begin();
        rect(0, 0, ArchiveMap.W, ArchiveMap.H, FLOOR, 0.95f);
        Tessellator q = start(GL11.GL_QUADS);
        for (Mark m : plan(storey)) put(q, m.x0, m.y0, m.x1, m.y1, m.rgb, m.a);
        for (LibraryUnits.Unit u : LibraryUnits.UNITS) {
            if (u.storey != storey) continue;
            int n = l.unitFill(u.index);
            kept += n;
            places += 9;
            int[] r = ArchiveMap.tile(u.index);
            put(q, r[0] + 0.5, r[1] + 0.5, r[2] - 0.5, r[3] - 0.5, fillColour(n), n == 0 ? 0.85f : 1f);
        }
        q.draw();
        int sel = l.guiUnit;
        boolean here = sel >= 0 && sel < UNITS && ArchiveMap.storey(sel) == storey;
        if (hover >= 0 && hover != sel) {
            int[] r = ArchiveMap.tile(hover);
            frame(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, CYAN, 0.8f);
        }
        if (here) {
            int[] r = ArchiveMap.tile(sel);
            float a = 0.65f + 0.35f * (float) Math.sin(t * 0.25);
            rect(r[0] - 2, r[1] - 2, r[2] + 2, r[3] + 2, WHITE, 0.12f * a);
            frame(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, WHITE, a);
            // the side its books face: up the plan is toward the back wall (+z), right is +x
            LibraryUnits.Unit u = LibraryUnits.UNITS.get(sel);
            if (u.faceZ > 0) rect(r[0] - 1, r[1] - 1, r[2] + 1, r[1], CYAN, 1f);
            else if (u.faceZ < 0) rect(r[0] - 1, r[3], r[2] + 1, r[3] + 1, CYAN, 1f);
            else if (u.faceX > 0) rect(r[2], r[1] - 1, r[2] + 1, r[3] + 1, CYAN, 1f);
            else rect(r[0] - 1, r[1] - 1, r[0], r[3] + 1, CYAN, 1f);
        }
        you(l, storey, t);
        end();
        String label = EchoText.t("library.gui.map_storey", EchoText.t("library.gui.storey." + storey), kept, places);
        smallCentered(fit(label, (int) (w / 0.75f)), w / 2.0, ArchiveMap.H + 3, 0.75f, here ? WHITE : DIM, 1f);
    }

    /** The colour of a bookcase's tile for its books: dim when empty, cyan growing brighter, violet when full. */
    private static int fillColour(int n) {
        if (n <= 0) return EMPTY;
        if (n >= 9) return VIOLET;
        return Motifs.mix(PART, CYAN, (n - 1) / 7.0);
    }

    /** The player on the plan of the storey they stand on (inside the Archive's box), and where they look. */
    private static void you(TileLibrary l, int storey, float t) {
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        if (p == null || l.getWorldObj() == null || p.worldObj != l.getWorldObj()) return;
        double feet = p.boundingBox.minY;
        double[] a = l.localPoint(p.posX, feet, p.posZ);
        if (a[0] < 0 || a[0] > ArchiveShape.WIDTH
            || a[2] < 0
            || a[2] > ArchiveShape.DEPTH
            || a[1] < -1
            || a[1] > ArchiveShape.HEIGHT) return;
        if ((a[1] < ArchiveShape.DECK_Y + 0.5 ? 0 : 1) != storey) return;
        double mx = Math.max(0, Math.min(ArchiveMap.W, ArchiveMap.px(a[0]))),
            my = Math.max(0, Math.min(ArchiveMap.H, ArchiveMap.py(a[2])));
        Vec3 look = p.getLook(1f);
        double[] b = l.localPoint(p.posX + look.xCoord, feet, p.posZ + look.zCoord);
        double dx = b[0] - a[0], dz = b[2] - a[2], len = Math.sqrt(dx * dx + dz * dz);
        if (len > 1e-3) Motifs.line(mx, my, mx + dx / len * 5, my - dz / len * 5, 1, WHITE, 0.7f);
        Motifs.dot(mx, my, 2.5, WHITE, 0.6f + 0.4f * (float) Math.sin(t * 0.3));
    }

    /** The tooltip over a plan: the bookcase under the mouse, where it stands, and what a click does. */
    public static List<String> mapTip(TileLibrary l, int unit) {
        if (unit < 0 || unit >= UNITS) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        out.add(EchoText.t("library.gui.unit.tip", l.callNumber(unit), l.unitFill(unit)));
        out.add(EnumChatFormatting.GRAY + where(unit));
        out.add(EchoText.t("library.gui.map.click"));
        return out;
    }

    /** One rectangle of a plan's fixed marks. */
    private static final class Mark {

        final double x0, y0, x1, y1;
        final int rgb;
        final float a;

        Mark(double x0, double y0, double x1, double y1, int rgb, float a) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.rgb = rgb;
            this.a = a;
        }
    }

    /** The fixed marks of a storey's plan, read from {@link ArchiveShape} once. */
    private static List<Mark> plan(int storey) {
        List<Mark> cached = storey == 0 ? ground : gallery;
        if (cached != null) return cached;
        List<Mark> out = new ArrayList<>();
        // the walls are read at the storey's middle book row, the front at door height on the ground
        int yWall = storey == 0 ? 3 : 9, yFront = storey == 0 ? 2 : 9;
        for (int x = ArchiveMap.X0; x <= ArchiveMap.X1; x++) for (int z = ArchiveMap.Z0; z <= ArchiveMap.Z1; z++) {
            double x0 = ArchiveMap.px(x), x1 = ArchiveMap.px(x + 1), y0 = ArchiveMap.py(z + 1), y1 = ArchiveMap.py(z);
            if (storey == 1) {
                char deck = ArchiveShape.cell(x, ArchiveShape.DECK_Y, z);
                if (deck == ArchiveShape.ANY) {
                    out.add(new Mark(x0, y0, x1, y1, VOID, 1f));
                    continue;
                }
                if (deck == ArchiveShape.EDGE) out.add(new Mark(x0, y0, x1, y1, EDGE, 0.9f));
            }
            if (stair(x, z)) out.add(new Mark(x0, y0, x1, y1, STAIR, 0.9f));
            if (ArchiveShape.cell(x, yWall, z) == ArchiveShape.POST)
                out.add(new Mark(x0 + 0.5, y0 + 0.5, x1 - 0.5, y1 - 0.5, POST, 1f));
            if (storey == 0) {
                char floor = ArchiveShape.cell(x, 1, z);
                boolean desk = x == ArchiveShape.DESK[0] && z == ArchiveShape.DESK[2];
                if (floor == ArchiveShape.CONSOLE) out.add(new Mark(x0, y0, x1, y1, desk ? GOLD : LECTERN, 0.9f));
                else if (floor == ArchiveShape.PEDESTAL) out.add(new Mark(x0 + 1, y0 + 1, x1 - 1, y1 - 1, CYAN, 0.45f));
            }
        }
        // the walls round it: windows lit, the doors a faint threshold
        double w = ArchiveMap.W, h = ArchiveMap.H;
        for (int z = ArchiveMap.Z0; z <= ArchiveMap.Z1; z++) {
            double y0 = ArchiveMap.py(z + 1), y1 = ArchiveMap.py(z);
            wall(out, 0, y0, 1, y1, ArchiveShape.cell(0, yWall, z));
            wall(out, w - 1, y0, w, y1, ArchiveShape.cell(ArchiveShape.WIDTH - 1, yWall, z));
        }
        for (int x = ArchiveMap.X0; x <= ArchiveMap.X1; x++) {
            double x0 = ArchiveMap.px(x), x1 = ArchiveMap.px(x + 1);
            wall(out, x0, 0, x1, 1, ArchiveShape.cell(x, yWall, ArchiveShape.DEPTH - 1));
            wall(out, x0, h - 1, x1, h, ArchiveShape.cell(x, yFront, 0));
        }
        out.add(new Mark(0, 0, 1, 1, WALL, 1f));
        out.add(new Mark(w - 1, 0, w, 1, WALL, 1f));
        out.add(new Mark(0, h - 1, 1, h, WALL, 1f));
        out.add(new Mark(w - 1, h - 1, w, h, WALL, 1f));
        List<Mark> done = Collections.unmodifiableList(out);
        if (storey == 0) ground = done;
        else gallery = done;
        return done;
    }

    /** A piece of the wall line: glazing lit, a door a faint threshold, anything else the wall. */
    private static void wall(List<Mark> out, double x0, double y0, double x1, double y1, char ch) {
        if (ch == ArchiveShape.AIR) out.add(new Mark(x0, y0, x1, y1, CYAN, 0.25f));
        else if (ch == ArchiveShape.GLAZE) out.add(new Mark(x0, y0, x1, y1, CYAN, 0.7f));
        else out.add(new Mark(x0, y0, x1, y1, WALL, 1f));
    }

    /** Whether a column of the Archive holds a step of a spiral stair (the way between the storeys). */
    private static boolean stair(int x, int z) {
        for (int y = 1; y <= ArchiveShape.DECK_Y + 1; y++) if (ArchiveShape.isStair(x, y, z)) return true;
        return false;
    }

    /** One more quad of a batch begun with {@code start(GL_QUADS)}. */
    private static void put(Tessellator q, double x0, double y0, double x1, double y1, int rgb, float a) {
        q.setColorRGBA_I(rgb, (int) (Math.max(0, Math.min(1, a)) * 255));
        q.addVertex(x1, y0, 0);
        q.addVertex(x0, y0, 0);
        q.addVertex(x0, y1, 0);
        q.addVertex(x1, y1, 0);
    }

    // ---- the catalogue

    /** The catalogue's title and how many books; what to do when there are none. */
    public static void listHead(TileLibrary l, ArchiveGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            int n = v.books(l).length;
            String title = EchoText.t("library.gui.catalogue", n);
            text(title, 1, 1, VIOLET, 1f);
            int tw = font().getStringWidth(title) + 8;
            smallRight(fit(EchoText.t("library.gui.catalogue_hint"), (int) ((w - tw) / 0.75f)), w, 2.5, 0.75f, DIM, 1f);
            if (n == 0) {
                double ly = 16;
                for (String line : wrap(EchoText.t("library.gui.catalogue_empty"), (int) ((w - 4) / 0.75f), 4)) {
                    small(line, 2, ly, 0.75f, DIM, 1f);
                    ly += 9;
                }
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * One line of the catalogue: the book's icon and name, its bookcase's call number and place (violet, with a bar,
     * when that bookcase is shown), and the card mark at the end (gold for the book the card desk writes for).
     */
    public static void row(TileLibrary l, ArchiveGui.View v, int line, boolean cardHover, float x, float y, float w,
        float h) {
        int slot = v.slotAt(l, line);
        ItemStack s = slot < 0 ? null : l.book(slot);
        if (s == null) return;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            int unit = slot / 9;
            boolean shown = unit == l.guiUnit, chosen = ArchiveGui.chosenSlot(l) == slot;
            begin();
            if (line % 2 == 0) rect(0, 0, w, h, SEAM, 0.35f);
            if (shown) rect(0, 0, 2, h, VIOLET, 0.9f);
            double cx = w - ArchiveGui.CARD_W + 2.5;
            int cc = chosen ? GOLD : cardHover ? CYAN : DIM;
            float ca = chosen || cardHover ? 1f : 0.6f;
            frame(cx, 1.5, cx + 7, h - 1.5, cc, ca);
            rect(cx + 2, 4, cx + 5, 5, cc, ca);
            rect(cx + 2, 6, cx + 5, 7, cc, ca);
            end();
            String where = EchoText.t("library.gui.row.where", l.callNumber(unit), place(slot % 9));
            double right = w - ArchiveGui.CARD_W - 2;
            float whereW = font().getStringWidth(where) * 0.75f;
            smallRight(where, right, 2.5, 0.75f, shown ? VIOLET : CYAN, 1f);
            int nameW = (int) ((right - whereW - 18) / 0.75f);
            if (nameW > 6) small(fit(name(s), nameW), 14, 2.5, 0.75f, WHITE, 1f);
            icon(s, 3, 1, 0.56f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** A catalogue line's tooltip: the book, where it is, and what a click does there. */
    public static List<String> rowTip(TileLibrary l, ArchiveGui.View v, int line, boolean onCard) {
        int slot = v.slotAt(l, line);
        ItemStack s = slot < 0 ? null : l.book(slot);
        if (s == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        out.add(name(s));
        out.add(EnumChatFormatting.GRAY + EchoText.t("library.gui.row.where", l.callNumber(slot / 9), place(slot % 9)));
        if (onCard)
            out.add(EchoText.t(ArchiveGui.chosenSlot(l) == slot ? "library.gui.card.chosen" : "library.gui.card.tip"));
        else {
            out.add(EchoText.t("library.gui.row.go"));
            out.add(EchoText.t("library.gui.row.card"));
        }
        return out;
    }

    /** The catalogue's scroll bar: the part of the list shown. */
    public static void scrollbar(TileLibrary l, ArchiveGui.View v, float x, float y, float w, float h) {
        int n = v.books(l).length;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            begin();
            rect(0, 0, w, h, DEEP, 1f);
            frame(0, 0, w, h, SEAM, 1f);
            if (n > ArchiveGui.ROWS) {
                double size = Math.max(6, h * ArchiveGui.ROWS / (double) n);
                double top = (h - size) * v.scroll / (double) (n - ArchiveGui.ROWS);
                rect(1, top + 1, w - 1, top + size - 1, CYAN, 0.7f);
            }
            end();
        } finally {
            GL11.glPopMatrix();
        }
    }

    // ---- the strip

    /** The book count, the lending line (amber when not docked) and the vault's line while it holds books. */
    public static void info(TileLibrary l, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            int lw = (int) ((w - 2) / 0.75f), vw = (int) ((ArchiveGui.REFILL_X - ArchiveGui.INFO_X - 3) / 0.75f);
            boolean vault = l.guiVault > 0, docked = "docked".equals(l.guiDock);
            small(fit(EchoText.t("library.gui.kept", l.clientSamples, l.capacity()), lw), 0, 1, 0.75f, WHITE, 1f);
            String lend = docked ? EchoText.t("library.gui.lends", l.guiLends) : EchoText.t("library.gui.undocked");
            small(fit(lend, vault ? vw : lw), 0, 9, 0.75f, docked ? CYAN : AMBER, 1f);
            if (vault) small(fit(EchoText.t("library.gui.vault", l.guiVault), vw), 0, 17, 0.75f, VIOLET, 1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** The chosen book's tooltip: which book the card desk writes for, and where it stands. */
    public static List<String> chosenTip(TileLibrary l) {
        List<String> out = new ArrayList<>();
        out.add(EchoText.t("library.gui.chosen"));
        int slot = ArchiveGui.chosenSlot(l);
        ItemStack s = slot < 0 ? null : l.book(slot);
        if (s == null) {
            out.add(EchoText.t("library.gui.chosen_none"));
            return out;
        }
        out.add(name(s));
        out.add(EnumChatFormatting.GRAY + EchoText.t("library.gui.row.where", l.callNumber(slot / 9), place(slot % 9)));
        out.add(EchoText.t("library.gui.chosen_hint"));
        return out;
    }

    // ---- words

    /**
     * Where a bookcase stands, in words: the storey, then its wall or range and the side of the range it faces, read
     * from its call number (such as {@code G-L3a-2}: ground, left range 3, the side toward the door, the second).
     */
    public static String where(int unit) {
        LibraryUnits.Unit u = LibraryUnits.UNITS.get(unit);
        String[] parts = u.call.split("-");
        String zone = parts.length > 1 ? parts[1] : "", z;
        if (zone.length() >= 3 && (zone.charAt(0) == 'L' || zone.charAt(0) == 'R')) {
            char face = zone.charAt(zone.length() - 1);
            z = EchoText.t(
                "library.gui.zone." + zone.charAt(0),
                zone.substring(1, zone.length() - 1),
                EchoText.t("library.gui.face." + face));
        } else z = EchoText.t("library.gui.zone." + zone);
        return EchoText.t("library.gui.where", EchoText.t("library.gui.storey." + u.storey), z);
    }

    /** A place of a bookcase in words: its shelf (from the top) and its place on it (from the left). */
    private static String place(int k) {
        return EchoText.t("library.gui.place", k / 3 + 1, k % 3 + 1);
    }

    /** A book's name; a broken item names itself by its id. */
    private static String name(ItemStack s) {
        try {
            return s.getDisplayName();
        } catch (RuntimeException e) {
            return String.valueOf(s.getItem());
        }
    }

    /** The text broken into lines of at most {@code width} font pixels, at most {@code max} lines. */
    private static List<String> wrap(String s, int width, int max) {
        @SuppressWarnings("unchecked")
        List<String> lines = font().listFormattedStringToWidth(s, width);
        if (lines.size() <= max) return lines;
        List<String> out = new ArrayList<>(lines.subList(0, max));
        out.set(max - 1, fit(out.get(max - 1) + "…", width));
        return out;
    }

    /** An item at a scale, its corner at (x, y). */
    private static void icon(ItemStack s, double x, double y, float scale) {
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, 0);
        GL11.glScalef(scale, scale, 1);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        try {
            ITEMS.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), s, 0, 0);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glPopMatrix();
        }
    }
}
