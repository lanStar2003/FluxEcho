package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 秘法庭 The Arcane Court (0.13.0, CITY.md §4.3): the first building of the PAVILION family, the reference the other
 * three pavilions copy. An octagonal hall ({@code oct(12, 16)}, walls y1..12 with slit glazing y3..10 between lit
 * pilasters every 3 on the flat faces, lit jambs at the four chamfer portals, a cornice at y12) open to a steep stepped
 * spire: octagon rings shrinking one step every two layers from radius 12 to 8 (y13..22), then one a layer to 3
 * (y23..27), lit ribs on the eight 0/45 degree lines and glazed bands at y16 and y20, a glazed lantern ring (y28) and
 * its lit cap (y29), and the finial (two conduits y30..31, a seat y32) that gives way to a floating violet crystal once
 * the court forms. The spire is hollow: from the altar one looks thirty blocks up.
 * <p>
 * Inside, the four chamfer portals lead along the diagonals to the central altar (the controller on its console) in the
 * infusion circle (eight sockets at octagon radius 4, one unit of eight slots); four cauldron alcoves (a 3 x 3 basin
 * rim
 * round a socket) on the flat faces; eight aspect pillars against the flat walls (plinth, three sockets, crown); eight
 * research desks (a console with a socket on it); a soft lit rune ring at octagon radius 6 with its spokes in the
 * floor.
 * 21 units, 44 slots; their order is the slot order and never changes ({@link #UNITS}).
 * <p>
 * The geometry is an exact port of the generator ({@code city/gen/b_pav.py}, {@code arcane_court()}) on
 * {@link ShapeKit}'s pavilion helpers; it uses the city legend unchanged and mirrors across x but for its gate niche
 * (内境门位, CITY.md §4.15: the back flat face's middle, {@code x 11..13, y 1..4, z 24}), which is soft wall
 * ({@link ShapeKit#backNiche}). On a diagonal site its blueprint's front faces along A toward the nexus and its
 * controller stands over the octagon's centre ({@link BuildPlan#pavilionController}).
 * <p>
 * Counts: checked 2,058, soft 397 (12 of them the niche), must-be-air 160 (four portals) = 2,615 steps.
 */
public final class ArcaneCourtShape {

    public static final String KEY = "arcane_court";
    /** The pavilion sites it may stand on, preferred first: front-left beside the gate, then 1, 5, 3 (CITY.md §2.2). */
    public static final int[] SITES = { 7, 1, 5, 3 };
    /** The box: 25 across and deep, 33 high (y0..y32); the octagon's middle at {@link #MID}. */
    public static final int WIDTH = ShapeKit.PAV_SIZE, DEPTH = ShapeKit.PAV_SIZE, HEIGHT = 33, MID = ShapeKit.PAV_C;
    /** The controller on the central altar's console, at eye height. */
    public static final int CTRL_X = MID, CTRL_Y = 2, CTRL_Z = MID;
    /** The hall's walls rise to their cornice at {@code WALL_TOP}; the spire starts above it. */
    public static final int WALL_TOP = 12, SPIRE_FROM = 13;
    /** The lantern ring, its cap, and the finial's layers (two conduits and the seat). */
    public static final int LANTERN_Y = 28, CAP_Y = 29, FINIAL_FROM = 30, FINIAL_TO = 32;
    /**
     * The characters that give way to the rendered crystal once the court forms: the finial's conduits and seat.
     * Players pass through them as well ({@link #PASSES}): they stand on the spire's cap, thirty blocks over the floor,
     * where no one walks (the walk test holds it).
     */
    public static final String DISSOLVES = "CZ", PASSES = "CZ";

    /** The shape as the generator draws it (do not change it: every array is the shape itself). */
    public static final ShapeKit.Shape SHAPE = build();
    /** The units in slot order: the infusion circle, four cauldrons, eight aspect pillars, eight desks. */
    public static final UnitLayout UNITS = new UnitLayout(SHAPE);
    /** The checked shape for StructureLib: the soft cells left out (anything goes there). */
    public static final Blueprint BLUEPRINT = ShapeKit.blueprint(SHAPE, HEIGHT);

    /** The plan's walls (its thick edge) and the portals' columns, for the stage rule. */
    private static final Set<Long> EDGE, DOORS;

    static {
        Set<Long> plan = ShapeKit.planCells();
        EDGE = Collections.unmodifiableSet(ShapeKit.thickEdge(plan));
        DOORS = Collections.unmodifiableSet(ShapeKit.portalCells(EDGE));
        if (UNITS.size() != 21 || UNITS.slots() != 44) throw new IllegalStateException("the arcane court's units");
        if (SHAPE.H != HEIGHT) throw new IllegalStateException("the arcane court is no longer 33 high");
    }

    /** The Arcane Court as the generic module plan builds it ({@link BuildPlan#module} on a pavilion site). */
    public static final CityBuild BUILD = new CityBuild(
        KEY,
        SHAPE,
        HEIGHT,
        BLUEPRINT,
        UNITS,
        Parts.moduleCore(ModuleCores.ARCANE_COURT),
        new CityBuild.Stages() {

            @Override
            public int stage(int x, int y, int z, char ch) {
                return ArcaneCourtShape.stage(x, y, z, ch);
            }

            @Override
            public int fitRank(int x, int y, int z, char ch) {
                return ArcaneCourtShape.fitRank(x, y, z, ch);
            }
        });

    private ArcaneCourtShape() {}

    /**
     * The Arcane Court's job on a pavilion site ({@link BuildPlan#module} of {@link #BUILD}).
     *
     * @throws IllegalArgumentException if the site is not a pavilion site or the front not a horizontal unit vector
     */
    public static BuildPlan.Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        if (SiteFamily.of(site) != SiteFamily.PAVILION)
            throw new IllegalArgumentException("not a pavilion site: " + site);
        return BuildPlan.module(plan, site, cx, y0, cz, fx, fz, BUILD);
    }

    /** Where the Arcane Court's controller stands on a pavilion site: {x, y, z, facing ordinal}. */
    public static int[] controller(int site, int cx, int y0, int cz, int fx, int fz) {
        return BuildPlan.pavilionController(BUILD, site, cx, y0, cz, fx, fz);
    }

    /** The character of a cell: the checked legend, a soft character, {@code -} or a space (anything). */
    public static char cell(int x, int y, int z) {
        return SHAPE.get(x, y, z);
    }

    /** The blueprint cell of a point of the shape: {across, layer from the top, row from the front}. */
    public static int[] cellOf(int x, int y, int z) {
        return new int[] { x, HEIGHT - 1 - y, z };
    }

    /** Whether the column (x, z) carries the hall's walls (the plan's thick edge). */
    public static boolean onEdge(int x, int z) {
        return EDGE.contains(ShapeKit.key(x, z));
    }

    /** Whether the column (x, z) is one of the four chamfer portals' (their opening and its two-deep air). */
    public static boolean inPortal(int x, int z) {
        return DOORS.contains(ShapeKit.key(x, z));
    }

    /** Whether a cell is part of the gate niche (soft wall, CITY.md §4.15). */
    public static boolean inNiche(int x, int y, int z) {
        return ShapeKit.pavilionNiche(x, y, z);
    }

    /**
     * The build stage of a cell that is not part of a unit (CITY.md §4.3): FLOOR the soft floor of y0 with its rune
     * ring; SHELL the foundations, the walls y1..12 with their cornice, the portals' air and lintels (the gate niche
     * with its wall); ROOF the spire's rings bottom-up from y13 (its cladding anchors on the ribs at y13 among them),
     * the lantern and its cap; FIT the altar's console and the finial; COMMISSION the controller. DECK and UPPER stay
     * empty.
     */
    public static int stage(int x, int y, int z, char ch) {
        if (ch == ShapeKit.CONTROLLER) return BuildPlan.M_COMMISSION;
        if (ch == ShapeKit.AIR) return BuildPlan.M_SHELL;
        if (y == 0) return ShapeKit.soft(ch) ? BuildPlan.M_FLOOR : BuildPlan.M_SHELL;
        if (ch == 'C' || ch == 'Z' || ch == 'K') return BuildPlan.M_FIT;
        if (y <= WALL_TOP && (onEdge(x, z) || inPortal(x, z))) return BuildPlan.M_SHELL;
        if (y >= SPIRE_FROM) return BuildPlan.M_ROOF;
        throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
    }

    /**
     * The order within FIT of a cell that is not part of a unit: after the units the altar's console, then the finial.
     */
    public static int fitRank(int x, int y, int z, char ch) {
        return ch == 'K' ? 1 : 2;
    }

    /** The standing states {x, z, h} the generator's self-check walks from: outside the four portals. */
    public static List<int[]> walkStarts() {
        return ShapeKit.pavilionStarts();
    }

    /** The counts of the shape by character, for the tests. */
    static Map<Character, Integer> counts() {
        return SHAPE.counts();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the generator (a port of b_pav.py's arcane_court)

    private static ShapeKit.Shape build() {
        final int N = ShapeKit.PAV_SIZE, C = ShapeKit.PAV_C;
        ShapeKit.Shape s = new ShapeKit.Shape(KEY, N, HEIGHT, N);
        ShapeKit.Shell sh = ShapeKit.shell(s, WALL_TOP, 3, ShapeKit.range(3, 11));
        for (long k : sh.edge) s.set(ShapeKit.keyX(k), WALL_TOP, ShapeKit.keyZ(k), 'n');
        ShapeKit.centreController(s, CTRL_Y);
        // the roof ring over the gallery at y13 and the spire: octagon shells shrinking by one cell every two layers,
        // then one a layer
        int y = SPIRE_FROM;
        int[][] steps = { { 12, 2 }, { 11, 2 }, { 10, 2 }, { 9, 2 }, { 8, 2 }, { 7, 1 }, { 6, 1 }, { 5, 1 }, { 4, 1 },
            { 3, 1 } };
        for (int[] st : steps) {
            int h = st[0], layers = st[1];
            int k = ShapeKit.octK(h), ki = ShapeKit.octK(h - 1);
            for (int yy = y; yy < y + layers; yy++) {
                for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) {
                    int du = x - C, dv = z - C;
                    if (!ShapeKit.octo(du, dv, h, k) || ShapeKit.octo(du, dv, h - 1, ki)) continue;
                    boolean rib = du == 0 || dv == 0 || Math.abs(du) == Math.abs(dv);
                    s.set(x, yy, z, rib ? 'L' : yy == 16 || yy == 20 ? 'G' : 'W');
                }
            }
            y += layers;
        }
        // the lantern (y28, its lit ring y29 under the cap) and the finial (conduits y30..31, seat y32) that dissolves
        // into the floating crystal
        for (int yy = y; yy <= y + 1; yy++) {
            for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) {
                if (!ShapeKit.octo(x - C, z - C, 2, 3) || x == C && z == C) continue;
                s.set(x, yy, z, yy == y ? 'G' : 'L');
            }
        }
        for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) {
            if (ShapeKit.octo(x - C, z - C, 1, 1)) s.set(x, y + 1, z, 'W');
        }
        s.set(C, y + 2, C, 'C');
        s.set(C, y + 3, C, 'C');
        s.set(C, y + 4, C, 'Z');
        if (y + 5 != HEIGHT) throw new IllegalStateException("the finial ends at y" + (y + 4));

        List<Object[]> units = new ArrayList<>();
        // the infusion circle: eight sockets at octagon radius 4 round the altar
        int[][] ring = { { C + 4, C }, { C - 4, C }, { C, C + 4 }, { C, C - 4 }, { C + 3, C + 3 }, { C - 3, C + 3 },
            { C + 3, C - 3 }, { C - 3, C - 3 } };
        List<int[]> circle = new ArrayList<>();
        for (int[] p : ring) {
            s.set(p[0], 1, p[1], 'o');
            circle.add(new int[] { p[0], 1, p[1] });
        }
        units.add(new Object[] { "infusion", "infusion", circle, 8, null });
        // cauldron alcoves on the four flat faces: a 3x3 basin rim round a socket, centre radius 8
        int[][] faces = { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };
        for (int i = 0; i < faces.length; i++) {
            int cx = C + faces[i][0] * 8, cz = C + faces[i][1] * 8;
            List<int[]> cells = new ArrayList<>();
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                s.set(cx + a, 1, cz + b, a == 0 && b == 0 ? 'o' : 'u');
                cells.add(new int[] { cx + a, 1, cz + b });
            }
            units.add(new Object[] { "cauldron-" + (i + 1), "cauldron", cells, 1, null });
        }
        // aspect pillars against each flat wall, four cells either side of the alcove: plinth, three sockets, crown
        int n = 0;
        for (int[] f : faces) {
            for (int side : new int[] { -4, 4 }) {
                int x = f[0] == 0 ? C + side : C + f[0] * 11, z = f[0] == 0 ? C + f[1] * 11 : C + side;
                char[] parts = { 'p', 'o', 'o', 'o', 'c' };
                List<int[]> cells = new ArrayList<>();
                for (int yy = 1; yy <= 5; yy++) {
                    s.set(x, yy, z, parts[yy - 1]);
                    cells.add(new int[] { x, yy, z });
                }
                n++;
                units.add(new Object[] { "aspect-" + n, "pillar", cells, 3, new int[] { -f[0], -f[1] } });
            }
        }
        // research desks: a console with a socket on it, eight round the infusion circle off the portal paths
        int[][] desks = { { C + 7, C + 4 }, { C - 7, C + 4 }, { C + 7, C - 4 }, { C - 7, C - 4 }, { C + 4, C + 7 },
            { C - 4, C + 7 }, { C + 4, C - 7 }, { C - 4, C - 7 } };
        for (int i = 0; i < desks.length; i++) {
            int x = desks[i][0], z = desks[i][1];
            s.set(x, 1, z, 'K');
            s.set(x, 2, z, 'o');
            List<int[]> cells = new ArrayList<>();
            cells.add(new int[] { x, 1, z });
            cells.add(new int[] { x, 2, z });
            units.add(new Object[] { "desk-" + (i + 1), "desk", cells, 1, null });
        }
        // the ritual floor: the rune ring at octagon radius 6 and its spokes in soft lit tiles
        for (long k : sh.plan) {
            if (sh.edge.contains(k)) continue;
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            int r = ShapeKit.onorm(du, dv);
            if (r == 6 || r <= 6 && (du == 0 || dv == 0 || Math.abs(du) == Math.abs(dv))) {
                if (s.get(x, 0, z) == '.') s.set(x, 0, z, '+');
            }
        }
        // cladding anchors: the eight spire ribs at y13
        for (long k : sh.edge) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            if ((du == 0 || dv == 0 || Math.abs(du) == Math.abs(dv)) && s.get(x, SPIRE_FROM, z) == 'L')
                s.set(x, SPIRE_FROM, z, 'A');
        }
        for (Object[] u : units) {
            @SuppressWarnings("unchecked")
            List<int[]> cells = (List<int[]>) u[2];
            s.unit((String) u[0], (String) u[1], cells, (Integer) u[3], (int[]) u[4]);
        }
        ShapeKit.backNiche(s);
        return s;
    }
}
