package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 二向箔仓 The Dual-foil Vault (0.14.0, CITY.md §4.2): the hall-family module that keeps the team's items painted into
 * canvases (bulk storage and the foil's destroy mode come with its function, 0.19.0 on). It prefers site 6, to the
 * nexus's left. Outside, a windowless monolith 27 high, the tallest building on the inner ring: walls y1..23 (a dark
 * course at y1, lit pilasters every four on the long walls and every five across, lit corners), a cornice band at y24,
 * a roof at y25 with six glazed skylight strips ({@code z = 3 mod 6}, x 4..22) under raised ridges at y26, a sawtooth
 * crown; its whole front over the kit portal is one giant canvas, the facade canvas (x {@link #FACADE_X0}..
 * {@link #FACADE_X1}, y {@link #FACADE_Y0}..{@link #FACADE_Y1}: a lit frame round 17 x 12 canvas cells, a cladding
 * anchor at each corner).
 * <p>
 * Inside: one nave 25 x 33 open to the roof, a picture gallery at the scale of a turbine hall. Both long walls carry
 * three tiers of seven canvas units (a plinth row at y1, y9 and y17, canvas cells three wide and four high over it, a
 * crown row over them, posts between the units). Two corner tower stairs (posts {@code (2, 2)} and {@code (24, 2)}, one
 * spiral of 32 steps each from stand 1 to stand 17, turning opposite ways) climb to the catwalks, three wide along the
 * long walls at deck y8 and y16 with lit edges and rails, and the front landings round the stairs (x 1..6 and 20..25,
 * z 1..6); at deck y16 an upper cross walk (z 31..33) passes over the foil. The back wall holds the foil (13 x 11 black
 * canvas, x 7..19, y 3..13, in a lit frame) over the desk (a console stand at {@code (13, 1, 30)} between two holo
 * pedestals).
 * <p>
 * 43 units of one slot each, in slot order ({@link #UNITS}): the 42 canvases (a unit is a column of 3 plinths, 12
 * canvas cells and 3 crowns, named by tier {@code G M U}, side {@code L R} and place 1..7 from the front:
 * {@code GL-1} … {@code UR-7}), then the foil (kind {@code foil}). Their order is the slot order and never changes.
 * <p>
 * The gate niche (内境门位, CITY.md §4.15) is the foil's lower middle ({@link #NICHE_X0}..{@link #NICHE_X1}, y
 * {@link #NICHE_Y0}..{@link #NICHE_Y1}, z {@link #NICHE_Z}): you step into the painting. Its twelve canvas cells are
 * the soft canvas twin {@code $} (the same block, built with the back wall, never checked), and they are left out of
 * the foil unit, whose cells must stay checked; the foil keeps its other 131 cells.
 * <p>
 * The geometry is an exact port of the generator ({@code city/gen/b_hall.py}, {@code vault()}) on {@link ShapeKit}; it
 * uses the city legend unchanged. It mirrors across x, the corner stairs too: their spirals turn opposite ways, so the
 * right one is the left one's mirror image (the generator still lists their steps as {@link ShapeKit.Shape#asym}, as
 * it does every spiral's). Nothing dissolves.
 * <p>
 * Counts: checked 4,644, soft 837 (12 of them the niche), must-be-air 48, decor 14 (the canopy) = 5,543 steps.
 */
public final class DualFoilVaultShape {

    public static final String KEY = "dual_foil_vault";
    /** The hall sites the Vault may stand on, preferred first: left, right, behind (CITY.md §2.2). */
    public static final int[] SITES = { 6, 2, 4 };
    /** The box: 27 across, 35 deep, 27 high (y0..y26); the axis at {@link #MID}. */
    public static final int WIDTH = 27, DEPTH = 35, HEIGHT = 27, MID = 13;
    /** The controller in the trumeau, at eye height over its console stand. */
    public static final int CTRL_X = 13, CTRL_Y = 2, CTRL_Z = 0;
    /** The walls rise to {@code WALL_TOP}; the cornice band, the roof and the skylights' ridges over them. */
    public static final int WALL_TOP = 23, CORNICE_Y = 24, ROOF_Y = 25, RIDGE_Y = 26;
    /** The two catwalk decks (people stand one higher, on y9 and y17). */
    public static final int[] DECKS = { 8, 16 };
    /** The upper cross walk over the foil: deck y16, rows z {@code CROSS_FROM..CROSS_TO}, x 4..22. */
    public static final int CROSS_FROM = 31, CROSS_TO = 33;
    /** The two corner tower stairs' centre posts {x, z}, and their steps (stand 1 to stand 17). */
    public static final int[][] STAIRS = { { 2, 2 }, { 24, 2 } };
    public static final int STAIR_STEPS = 32;
    /** The canvas tiers' plinth rows (the canvas cells y+1..y+4 over them, the crown row at y+5). */
    public static final int[] TIERS = { 1, 9, 17 };
    /** The canvas units along each long wall: seven, from z {@code CANVAS_FROM} every {@code CANVAS_STEP}, 3 deep. */
    public static final int CANVAS_FROM = 5, CANVAS_STEP = 4, CANVAS_PER_SIDE = 7;
    /** The facade canvas on the front wall: its lit frame x 4..22, y 7..20, the canvas cells inside it. */
    public static final int FACADE_X0 = 4, FACADE_X1 = 22, FACADE_Y0 = 7, FACADE_Y1 = 20;
    /** The foil on the back wall (z {@link #NICHE_Z}): its lit frame x 6..20, y 2..14, the black canvas inside it. */
    public static final int FOIL_X0 = 6, FOIL_X1 = 20, FOIL_Y0 = 2, FOIL_Y1 = 14;
    /** The gate niche (CITY.md §4.15): x 12..14, y 3..6 in the foil (z 34); soft canvas, built with the back wall. */
    public static final int NICHE_X0 = 12, NICHE_X1 = 14, NICHE_Y0 = 3, NICHE_Y1 = 6, NICHE_Z = 34;
    /**
     * The floor the foil is used from: the nave's (people stand on y1 before the desk), though its lowest cells hang
     * over its frame at y3.
     */
    public static final int FOIL_FLOOR = 1;
    /** The desk's console stand under the foil, between the two holo pedestals (x 10 and 16). */
    public static final int DESK_X = 13, DESK_Y = 1, DESK_Z = 30;
    /** Nothing gives way to the module's drawing once it forms, and nothing lets players through. */
    public static final String DISSOLVES = "", PASSES = "";

    /** The shape as the generator draws it (do not change it: every array is the shape itself). */
    public static final ShapeKit.Shape SHAPE = build();
    /**
     * The units in slot order: the 42 canvases (tier by tier, left wall then right, front to back), then the foil,
     * which is used from the nave's floor ({@link #FOIL_FLOOR}) and shows on its plan in the window.
     */
    public static final UnitLayout UNITS = new UnitLayout(SHAPE, Collections.singletonMap("foil", FOIL_FLOOR));
    /** The checked shape for StructureLib: the soft cells left out (anything goes there). */
    public static final Blueprint BLUEPRINT = ShapeKit.blueprint(SHAPE, HEIGHT);

    static {
        if (UNITS.size() != 43 || UNITS.slots() != 43) throw new IllegalStateException("the vault's units changed");
    }

    /** The Dual-foil Vault as the generic module plan builds it ({@link BuildPlan#module}). */
    public static final CityBuild BUILD = new CityBuild(
        KEY,
        SHAPE,
        HEIGHT,
        BLUEPRINT,
        UNITS,
        Parts.moduleCore(ModuleCores.DUAL_FOIL_VAULT),
        new CityBuild.Stages() {

            @Override
            public int stage(int x, int y, int z, char ch) {
                return DualFoilVaultShape.stage(x, y, z, ch);
            }

            @Override
            public int fitRank(int x, int y, int z, char ch) {
                return DualFoilVaultShape.fitRank(x, y, z, ch);
            }
        });

    private DualFoilVaultShape() {}

    /** The Dual-foil Vault's job on a hall site ({@link BuildPlan#module} of {@link #BUILD}). */
    public static BuildPlan.Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        return BuildPlan.module(plan, site, cx, y0, cz, fx, fz, BUILD);
    }

    /** Where the Dual-foil Vault's controller stands on a hall site: {x, y, z, facing ordinal}. */
    public static int[] controller(int site, int cx, int y0, int cz, int fx, int fz) {
        return BuildPlan.hallController(BUILD, site, cx, y0, cz, fx, fz);
    }

    /** The character of a cell: the checked legend, a soft character, {@code -} or a space (anything). */
    public static char cell(int x, int y, int z) {
        return y < HEIGHT ? SHAPE.get(x, y, z) : ShapeKit.NONE;
    }

    /** The blueprint cell of a point of the shape: {across, layer from the top, row from the front}. */
    public static int[] cellOf(int x, int y, int z) {
        return new int[] { x, HEIGHT - 1 - y, z };
    }

    /** Whether the column is on the box's edge: the walls. */
    public static boolean onEdge(int x, int z) {
        return x == 0 || x == WIDTH - 1 || z == 0 || z == DEPTH - 1;
    }

    /** Whether a roof cell is a glazed skylight strip: rows {@code z = 3 mod 6}, x 4..22. */
    public static boolean skylight(int x, int z) {
        return z % 6 == 3 && x >= 4 && x <= 22;
    }

    /** Whether a cell is part of the gate niche (soft canvas in the foil, CITY.md §4.15). */
    public static boolean inNiche(int x, int y, int z) {
        return z == NICHE_Z && x >= NICHE_X0 && x <= NICHE_X1 && y >= NICHE_Y0 && y <= NICHE_Y1;
    }

    /** Whether a point {@code (x, y)} of the back wall lies on the foil's canvas, inside its frame (the niche too). */
    public static boolean inFoil(int x, int y) {
        return x > FOIL_X0 && x < FOIL_X1 && y > FOIL_Y0 && y < FOIL_Y1;
    }

    /** Whether a point of the front wall {@code (x, y)} lies on the facade canvas's field (inside its lit frame). */
    public static boolean inFacade(int x, int y) {
        return x > FACADE_X0 && x < FACADE_X1 && y > FACADE_Y0 && y < FACADE_Y1;
    }

    /** Whether a cell is a step of one of the two corner tower stairs or one of their posts. */
    public static boolean onStair(int x, int y, int z) {
        if (SHAPE.asym.contains(ShapeKit.Shape.pos(x, y, z))) return true;
        for (int[] p : STAIRS) if (x == p[0] && z == p[1] && y >= 1 && y <= DECKS[1]) return true;
        return false;
    }

    /**
     * The build stage of a cell that is not part of a unit (CITY.md §4.2): FLOOR the soft floor of y0; SHELL the
     * foundations, the walls y1..7 (the gate niche with its wall), the door air, the trumeau and the lintel; DECK the
     * catwalks (y8 and y16) with their lit edges and rails, the front landings, the cross walk and the two corner
     * stairs with their posts; UPPER the walls y8..23 (the facade canvas among them); ROOF the cornice band, the roof
     * with its skylights and their ridges (y24..26); FIT the desk's console stand, the holo pedestals and the cladding
     * anchors; COMMISSION the controller.
     */
    public static int stage(int x, int y, int z, char ch) {
        if (ch == ShapeKit.CONTROLLER) return BuildPlan.M_COMMISSION;
        if (ch == ShapeKit.AIR) return BuildPlan.M_SHELL;
        if (y == 0) return ShapeKit.soft(ch) ? BuildPlan.M_FLOOR : BuildPlan.M_SHELL;
        if (ch == 'h' || ch == 'A' || ch == 'K' && !onEdge(x, z)) return BuildPlan.M_FIT;
        if (onStair(x, y, z)) return BuildPlan.M_DECK;
        if (onEdge(x, z)) {
            if (y <= 7) return BuildPlan.M_SHELL;
            if (y <= WALL_TOP) return BuildPlan.M_UPPER;
            if (y == CORNICE_Y) return BuildPlan.M_ROOF;
        } else {
            for (int d : DECKS) if (y == d || y == d + 1) return BuildPlan.M_DECK;
            if (y == ROOF_Y || y == RIDGE_Y) return BuildPlan.M_ROOF;
        }
        throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
    }

    /**
     * The order within FIT of a cell that is not part of a unit: after the units (the canvases, then the foil) the
     * desk's console stand, the holo pedestals, then the cladding anchors.
     */
    public static int fitRank(int x, int y, int z, char ch) {
        if (ch == 'K') return 1;
        return ch == 'h' ? 2 : 3;
    }

    /** The standing states {x, z, h} the generator's self-check walks from: outside the doors. */
    public static List<int[]> walkStarts() {
        return Collections.unmodifiableList(Arrays.asList(new int[] { MID - 2, -1, 2 }, new int[] { MID + 2, -1, 2 }));
    }

    /** The counts of the shape by character, for the tests. */
    static Map<Character, Integer> counts() {
        return SHAPE.counts();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the generator (a port of b_hall.py's vault)

    private static ShapeKit.Shape build() {
        final int W = WIDTH, D = DEPTH;
        ShapeKit.Shape s = new ShapeKit.Shape(KEY, W, HEIGHT, D);
        // the foundation ring round the soft floor; the walls y1..23 (a dark course at y1, lit pilasters every four
        // along the long walls and every five across, lit corners) and the cornice band at y24
        for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            boolean edge = onEdge(x, z);
            s.set(x, 0, z, edge ? 'F' : '.');
            if (!edge) continue;
            for (int y = 1; y < CORNICE_Y; y++) {
                char ch = 'W';
                if (y == 1) ch = 'w';
                else if ((x == 0 || x == W - 1) && z % 4 == 0 || (z == 0 || z == D - 1) && Math.abs(x - MID) % 5 == 0)
                    ch = 'L';
                if ((x == 0 || x == W - 1) && (z == 0 || z == D - 1)) ch = 'L';
                s.set(x, y, z, ch);
            }
            s.set(x, CORNICE_Y, z, 'n');
        }
        // the roof (y25), its glazed skylight strips under raised ridges (y26)
        for (int x = 1; x < W - 1; x++) for (int z = 1; z < D - 1; z++) {
            boolean sky = skylight(x, z);
            s.set(x, ROOF_Y, z, sky ? 'G' : 'W');
            if (sky) s.set(x, RIDGE_Y, z, 'W');
        }
        // the facade: the canvas field x 4..22, y 7..20 framed in lit panels
        for (int x = FACADE_X0; x <= FACADE_X1; x++) for (int y = FACADE_Y0; y <= FACADE_Y1; y++) {
            boolean frame = x == FACADE_X0 || x == FACADE_X1 || y == FACADE_Y0 || y == FACADE_Y1;
            s.set(x, y, 0, frame ? 'L' : 'Q');
        }
        ShapeKit.portal(s, 0, 5, true, MID);
        // the catwalks: x 1..3 and 23..25 at deck y8 (stand 9) and y16 (stand 17), the landings x 1..6, z 1..6 round
        // the stair wells, lit edges and rails on the nave's side
        for (int yd : DECKS) {
            for (int z = 1; z < 34; z++) {
                for (int x : new int[] { 1, 2, 3, 23, 24, 25 }) if (!inWell(x, z)) s.set(x, yd, z, 'd');
                if (z >= 7) {
                    s.set(3, yd, z, 'e');
                    s.set(23, yd, z, 'e');
                    s.set(3, yd + 1, z, 'r');
                    s.set(23, yd + 1, z, 'r');
                }
            }
            for (int x : new int[] { 4, 5, 6, 20, 21, 22 }) for (int z = 1; z < 7; z++) s.set(x, yd, z, 'd');
            for (int x : new int[] { 6, 20 }) for (int z = 1; z < 7; z++) {
                s.set(x, yd, z, 'e');
                s.set(x, yd + 1, z, 'r');
            }
            for (int x : new int[] { 4, 5, 6, 20, 21, 22 }) {
                s.set(x, yd, 6, 'e');
                s.set(x, yd + 1, 6, 'r');
            }
        }
        // the upper cross walk over the foil (deck y16, z 31..33), its rail on the nave's edge, open to the catwalks
        for (int x = 4; x < 23; x++) {
            for (int z = CROSS_FROM; z <= CROSS_TO; z++) s.set(x, DECKS[1], z, 'd');
            s.set(x, DECKS[1], CROSS_FROM, 'e');
            s.set(x, DECKS[1] + 1, CROSS_FROM, 'r');
        }
        s.clear(3, 17, 31);
        s.clear(23, 17, 31);
        for (int z : new int[] { 32, 33 }) {
            s.clear(3, 17, z);
            s.clear(23, 17, z);
        }
        // the corner tower stairs: centre posts (2, 2) and (24, 2), 32 steps from stand 1 to stand 17, mirrored
        for (int i = 0; i < STAIRS.length; i++) {
            int cx = STAIRS[i][0], cz = STAIRS[i][1], mirror = i == 0 ? 1 : -1;
            for (int y = 1; y < 17; y++) s.set(cx, y, cz, 'q');
            for (int[] st : ShapeKit.spiral(cx, cz, 1, STAIR_STEPS, 0, mirror)) {
                s.set(st[0], st[1], st[2], (char) st[3]);
                s.asym.add(ShapeKit.Shape.pos(st[0], st[1], st[2]));
            }
        }
        // the canvases on the long walls: three tiers (plinth y1/y9/y17, canvas four high, crown over it), seven units
        // a side, posts between them
        List<Object[]> units = new ArrayList<>();
        String[] tiers = { "G", "M", "U" };
        for (int t = 0; t < TIERS.length; t++) {
            int yp = TIERS[t];
            for (int side = 0; side < 2; side++) {
                int xw = side == 0 ? 0 : W - 1, fx = side == 0 ? 1 : -1;
                for (int k = 0; k < CANVAS_PER_SIDE; k++) {
                    int z0 = CANVAS_FROM + CANVAS_STEP * k;
                    List<int[]> cells = new ArrayList<>();
                    for (int z = z0; z < z0 + 3; z++) {
                        s.set(xw, yp, z, 'p');
                        cells.add(new int[] { xw, yp, z });
                        for (int y = yp + 1; y < yp + 5; y++) {
                            s.set(xw, y, z, 'Q');
                            cells.add(new int[] { xw, y, z });
                        }
                        s.set(xw, yp + 5, z, 'c');
                        cells.add(new int[] { xw, yp + 5, z });
                    }
                    for (int z : new int[] { z0 - 1, z0 + 3 }) for (int y = yp; y < yp + 6; y++) s.set(xw, y, z, 'q');
                    units.add(
                        new Object[] { tiers[t] + (side == 0 ? "L" : "R") + "-" + (k + 1), cells,
                            new int[] { fx, 0 } });
                }
            }
        }
        // the foil on the back wall: black canvas x 7..19, y 3..13 in a lit frame
        List<int[]> foil = new ArrayList<>();
        for (int x = FOIL_X0; x <= FOIL_X1; x++) for (int y = FOIL_Y0; y <= FOIL_Y1; y++) {
            if (x == FOIL_X0 || x == FOIL_X1 || y == FOIL_Y0 || y == FOIL_Y1) s.set(x, y, D - 1, 'L');
            else {
                s.set(x, y, D - 1, 'Q');
                foil.add(new int[] { x, y, D - 1 });
            }
        }
        // the gate niche is the foil's lower middle, x 12..14, y 3..6: you step into the painting. The same canvas,
        // soft, and left out of the foil unit (a unit's cells are always checked), so the 内境 gate can open it later
        // without a new blueprint; the foil keeps its other 131 cells
        s.gateNiche(MID - 1, MID + 1, NICHE_Y0, NICHE_Y1, D - 1);
        List<int[]> kept = new ArrayList<>();
        for (int[] c : foil) if (!s.niche.contains(ShapeKit.Shape.pos(c[0], c[1], c[2]))) kept.add(c);
        units.add(new Object[] { "foil", kept, new int[] { 0, -1 } });
        // the desk under the foil: its console stand between two holo pedestals; anchors at the facade's corners
        s.set(DESK_X, DESK_Y, DESK_Z, 'K');
        for (int x : new int[] { 10, 16 }) s.set(x, 1, DESK_Z, 'h');
        for (int[] a : new int[][] { { 4, 7 }, { 22, 7 }, { 4, 20 }, { 22, 20 } }) s.set(a[0], a[1], 0, 'A');
        for (Object[] u : units) {
            String name = (String) u[0];
            @SuppressWarnings("unchecked")
            List<int[]> cells = (List<int[]>) u[1];
            s.unit(name, "foil".equals(name) ? "foil" : "canvas", cells, 1, (int[]) u[2]);
        }
        return s;
    }

    /** Whether the column (x, z) lies in one of the two stair wells (the post and the ring of steps round it). */
    private static boolean inWell(int x, int z) {
        for (int[] p : STAIRS) if (Math.abs(x - p[0]) <= 1 && Math.abs(z - p[1]) <= 1) return true;
        return false;
    }
}
