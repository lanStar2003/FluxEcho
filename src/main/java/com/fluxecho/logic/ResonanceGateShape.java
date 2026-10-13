package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 谐振塔 The Resonance Gate (0.14.0, CITY.md §4.7): the city's gatehouse on site 0, the gate family's reference. Two
 * wing towers (9 x 17, walls y1..15 with lit pilasters every four, tall windows y3..6 and y10..13 on the outer faces,
 * upper windows on the fronts, cornice and roof y16, lit crown posts y17) flank the gate road; over the 13-wide
 * passage between them a bridge (x 9..21, z 5..11: deck y8 with a cornice under its edges at y7, glazed side walls
 * y9..12, roof y13) holds the resonance chamber. The passage under the bridge stays open seven high and is never built
 * but for the kiosk in the road and the floor the road leaves unpaved ({@link CampusPlan#gatePassageFloor}): the gate
 * road, its node and threshold widenings and the four establish pylons stay the nexus's.
 * <p>
 * The controller stands on that kiosk under the bridge (its console y1, the controller y2 at eye height for someone
 * on the road), facing the nexus. Each wing has a ground hall (a front door three wide, a door onto the passage two
 * deep), two tuning racks on its outer wall, a holo pedestal, and a tower stair round a post (16 steps, from y1 to the
 * upper hall's deck y8); from both upper halls openings y9..12 lead into the resonance chamber, where six resonator
 * columns stand against the bridge's side walls round the spire core (three conduits and a seat, which give way to
 * the rendered floating crystal spire once the gate forms, {@link #DISSOLVES}). 10 units, 24 slots; their order is
 * the slot order and never changes ({@link #UNITS}).
 * <p>
 * The geometry is an exact port of the generator ({@code city/gen/b_gate.py}, {@code resonance_gate()}): the local
 * frame is {@code x = 15 + R}, {@code z = A - 23}, its front (z 0) faces the nexus; it mirrors across x but for the two
 * tower stairs, which turn opposite ways ({@link ShapeKit.Shape#asym}). It has no gate niche (内境门位): it is the gate.
 * <p>
 * Counts: checked 2,370, soft 268 (the wings' floors and the passage cells the road leaves unpaved), must-be-air 136
 * (the wing doors and the bridge openings) = 2,774 steps.
 */
public final class ResonanceGateShape {

    public static final String KEY = "resonance_gate";
    /** The gate's one site (CITY.md §2.2). */
    public static final int[] SITES = { 0 };
    /** The box: 31 across, 17 deep, 18 high; the passage's middle (R 0) at {@link #MID}. */
    public static final int WIDTH = 31, DEPTH = 17, HEIGHT = 18, MID = 15;
    /** The two wings across: x 0..8 and 22..30 (R -15..-7 and 7..15); the passage between them, x 9..21. */
    public static final int LEFT_TO = 8, RIGHT_FROM = 22;
    /** The controller on the kiosk in the road under the bridge, at eye height: {@code (A 30, R 0, Y0 + 2)}. */
    public static final int CTRL_X = MID, CTRL_Y = 2, CTRL_Z = 7;
    /** The wings' walls rise to {@code WALL_TOP}, their cornice and roof at {@code WING_ROOF}, crown posts above. */
    public static final int WALL_TOP = 15, WING_ROOF = 16;
    /** The upper halls' and the bridge's deck. */
    public static final int DECK_Y = 8;
    /** The bridge over the passage: z {@code BRIDGE_FROM..BRIDGE_TO}, its walls to {@code BRIDGE_WALL}, roof above. */
    public static final int BRIDGE_FROM = 5, BRIDGE_TO = 11, BRIDGE_WALL = 12, BRIDGE_ROOF = 13;
    /** The spire core in the bridge's middle: conduits y9..11 and the seat y12 at {@code (15, y, 8)}. */
    public static final int CORE_Z = 8, CORE_FROM = 9, SEAT_Y = 12;
    /** The two tower stairs' centre posts {x, z}. */
    public static final int[][] WELLS = { { 4, 13 }, { 26, 13 } };
    /**
     * The characters that give way to the rendered floating crystal spire once the gate forms: the spire core's
     * conduits and seat. Players pass through them as well ({@link #PASSES}), as through the nexus's: the chamber's
     * deck runs under them, so with them gone a player can step into the core's column (the walk test documents it).
     */
    public static final String DISSOLVES = "CZ", PASSES = "CZ";

    /** The shape as the generator draws it (do not change it: every array is the shape itself). */
    public static final ShapeKit.Shape SHAPE = build();
    /** The units in slot order: six resonators, then four racks. */
    public static final UnitLayout UNITS = new UnitLayout(SHAPE);
    /** The checked shape for StructureLib: the soft cells left out (anything goes there). */
    public static final Blueprint BLUEPRINT = ShapeKit.blueprint(SHAPE, HEIGHT);

    static {
        if (UNITS.size() != 10 || UNITS.slots() != 24) throw new IllegalStateException("the resonance gate's units");
    }

    /** The Resonance Gate as the generic module plan builds it ({@link BuildPlan#module} on the gate site). */
    public static final CityBuild BUILD = new CityBuild(
        KEY,
        SHAPE,
        HEIGHT,
        BLUEPRINT,
        UNITS,
        Parts.moduleCore(ModuleCores.RESONANCE_GATE),
        new CityBuild.Stages() {

            @Override
            public int stage(int x, int y, int z, char ch) {
                return ResonanceGateShape.stage(x, y, z, ch);
            }

            @Override
            public int fitRank(int x, int y, int z, char ch) {
                return ResonanceGateShape.fitRank(x, y, z, ch);
            }
        });

    private ResonanceGateShape() {}

    /**
     * The Resonance Gate's job on the gate site ({@link BuildPlan#module} of {@link #BUILD}).
     *
     * @throws IllegalArgumentException if the site is not the gate or the front not a horizontal unit vector
     */
    public static BuildPlan.Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        if (SiteFamily.of(site) != SiteFamily.GATE || !SiteFamily.ringSite(site))
            throw new IllegalArgumentException("not the gate site: " + site);
        return BuildPlan.module(plan, site, cx, y0, cz, fx, fz, BUILD);
    }

    /** Where the Resonance Gate's controller stands on the gate site: {x, y, z, facing ordinal}. */
    public static int[] controller(int site, int cx, int y0, int cz, int fx, int fz) {
        return BuildPlan.gateController(BUILD, site, cx, y0, cz, fx, fz);
    }

    /** The character of a cell: the checked legend, a soft character, {@code -} or a space (anything). */
    public static char cell(int x, int y, int z) {
        return y < HEIGHT ? SHAPE.get(x, y, z) : ShapeKit.NONE;
    }

    /** The blueprint cell of a point of the shape: {across, layer from the top, row from the front}. */
    public static int[] cellOf(int x, int y, int z) {
        return new int[] { x, HEIGHT - 1 - y, z };
    }

    /** Whether the column x lies in one of the two wings (x 0..8 or 22..30), not over the passage. */
    public static boolean inWing(int x) {
        return x >= 0 && x <= LEFT_TO || x >= RIGHT_FROM && x < WIDTH;
    }

    /** Whether a cell is a step of one of the two tower stairs or one of their posts. */
    public static boolean onStair(int x, int y, int z) {
        if (SHAPE.asym.contains(ShapeKit.Shape.pos(x, y, z))) return true;
        for (int[] w : WELLS) if (x == w[0] && z == w[1] && y >= 1 && y <= DECK_Y) return true;
        return false;
    }

    /**
     * The build stage of a cell that is not part of a unit (CITY.md §4.7): FLOOR the soft floor of y0 (the wings' halls
     * and the passage cells the road leaves unpaved); SHELL the wings' foundations and walls y1..7 with their doors'
     * air and lintels, and the bridge openings' air; DECK the wings' y8 (the upper halls' deck and the walls' band),
     * both tower stairs with their posts, and the bridge's deck with the cornice under its edges; UPPER the wings
     * y9..17 (their walls, cornices, roofs and crown posts, the crowns raised after the cornice they stand on, as the
     * stage runs bottom-up) and the bridge's side walls; ROOF the bridge's roof alone; FIT the kiosk's console, the
     * holo pedestals and the spire core; COMMISSION the controller.
     */
    public static int stage(int x, int y, int z, char ch) {
        if (ch == ShapeKit.CONTROLLER) return BuildPlan.M_COMMISSION;
        if (ch == ShapeKit.AIR) return BuildPlan.M_SHELL;
        if (y == 0) return ShapeKit.soft(ch) ? BuildPlan.M_FLOOR : BuildPlan.M_SHELL;
        if (ch == 'K' || ch == 'C' || ch == 'Z' || ch == 'h') return BuildPlan.M_FIT;
        if (inWing(x)) {
            if (onStair(x, y, z)) return BuildPlan.M_DECK;
            if (y < DECK_Y) return BuildPlan.M_SHELL;
            if (y == DECK_Y) return BuildPlan.M_DECK;
            return BuildPlan.M_UPPER;
        }
        if (z >= BRIDGE_FROM && z <= BRIDGE_TO) {
            if (y == DECK_Y - 1 || y == DECK_Y) return BuildPlan.M_DECK;
            if (y <= BRIDGE_WALL) return BuildPlan.M_UPPER;
            if (y == BRIDGE_ROOF) return BuildPlan.M_ROOF;
        }
        throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
    }

    /**
     * The order within FIT of a cell that is not part of a unit: before the units (the resonators, then the racks) the
     * kiosk's console; after them the holo pedestals, then the spire core, bottom-up.
     */
    public static int fitRank(int x, int y, int z, char ch) {
        if (ch == 'K') return -1;
        return ch == 'h' ? 1 : 2;
    }

    /**
     * The standing states {x, z, h} the generator's self-check walks from: on the road before the passage either side
     * of the middle, and before the left wing's front door.
     */
    public static List<int[]> walkStarts() {
        return Collections.unmodifiableList(
            Arrays.asList(new int[] { MID - 2, -1, 2 }, new int[] { MID + 2, -1, 2 }, new int[] { 2, -1, 2 }));
    }

    /** The counts of the shape by character, for the tests. */
    static Map<Character, Integer> counts() {
        return SHAPE.counts();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the generator (a port of b_gate.py's resonance_gate)

    private static ShapeKit.Shape build() {
        final int W = WIDTH, D = DEPTH;
        ShapeKit.Shape s = new ShapeKit.Shape(KEY, W, HEIGHT, D);
        int[][] wings = { { 0, LEFT_TO }, { RIGHT_FROM, W - 1 } };
        List<Object[]> units = new ArrayList<>();
        for (int[] wing : wings) {
            int x0 = wing[0], x1 = wing[1];
            for (int x = x0; x <= x1; x++) {
                for (int z = 0; z < D; z++) {
                    boolean edge = x == x0 || x == x1 || z == 0 || z == D - 1;
                    s.set(x, 0, z, edge ? 'F' : '.');
                    if (!edge) continue;
                    boolean outer = x == (x0 == 0 ? 0 : W - 1);
                    for (int y = 1; y < 16; y++) {
                        char ch = 'W';
                        if (y == 1) ch = 'w';
                        else if ((x == x0 || x == x1) && (z == 0 || z == D - 1)) ch = 'L';
                        else if ((z == 0 || z == D - 1) && Math.abs(x - MID) % 4 == 3) ch = 'L';
                        else if ((x == x0 || x == x1) && z % 4 == 0) ch = 'L';
                        else if (outer && (3 <= y && y <= 6 || 10 <= y && y <= 13) && z % 4 == 2) ch = 'G';
                        else if (z == 0 && 10 <= y && y <= 13 && Math.abs(x - MID) % 4 == 1) ch = 'G';
                        s.set(x, y, z, ch);
                    }
                    s.set(x, 16, z, 'n');
                }
            }
            for (int x = x0 + 1; x < x1; x++) for (int z = 1; z < D - 1; z++) s.set(x, 16, z, 'W');
            // crown: lit parapet posts at the corners and mid-sides, y17
            for (int[] c : new int[][] { { x0, 0 }, { x1, 0 }, { x0, D - 1 }, { x1, D - 1 }, { x0, 8 }, { x1, 8 } })
                s.set(c[0], 17, c[1], 'L');
        }
        // wing doors: front (z 0..1) x 3..5 / 25..27, and on the passage side (x 8,7 / 22,23) at z 7..9
        for (int x : new int[] { 3, 4, 5, 25, 26, 27 }) {
            for (int y = 1; y < 5; y++) {
                s.set(x, y, 0, '-');
                s.set(x, y, 1, '-');
            }
            s.set(x, 5, 0, 'n');
        }
        for (int z = 7; z < 10; z++) {
            for (int y = 1; y < 5; y++) for (int x : new int[] { 7, 8, 22, 23 }) s.set(x, y, z, '-');
            for (int x : new int[] { 8, 22 }) s.set(x, 5, z, 'n');
        }
        // upper floors (deck y8) and the tower stairs (centre posts (4, 13) and (26, 13), 16 steps, stand 1 -> 9)
        for (int[] wing : wings) {
            for (int x = wing[0] + 1; x < wing[1]; x++) for (int z = 1; z < D - 1; z++) {
                if (!inWell(x, z)) s.set(x, DECK_Y, z, 'd');
            }
        }
        for (int i = 0; i < WELLS.length; i++) {
            int cx = WELLS[i][0], cz = WELLS[i][1], mirror = i == 0 ? 1 : -1;
            for (int y = 1; y < 9; y++) s.set(cx, y, cz, 'q');
            for (int[] st : ShapeKit.spiral(cx, cz, 1, 16, 6, mirror)) {
                s.set(st[0], st[1], st[2], (char) st[3]);
                s.asym.add(ShapeKit.Shape.pos(st[0], st[1], st[2]));
            }
        }
        // the bridge: deck y8 over x 9..21, z 5..11; walls z 5 and 11 at y9..y12; roof y13; openings into the wings
        for (int x = 9; x < 22; x++) {
            for (int z = BRIDGE_FROM; z <= BRIDGE_TO; z++) {
                s.set(x, DECK_Y, z, 'd');
                s.set(x, BRIDGE_ROOF, z, 'W');
            }
            for (int z : new int[] { BRIDGE_FROM, BRIDGE_TO }) {
                s.set(x, 7, z, 'n');
                for (int y = 9; y < 13; y++) {
                    s.set(x, y, z, (y == 10 || y == 11) && x % 2 == 1 ? 'G' : x == 9 || x == 21 ? 'L' : 'W');
                }
                s.set(x, BRIDGE_ROOF, z, 'n');
            }
        }
        for (int z = 6; z < 11; z++) for (int y = 9; y < 13; y++) {
            s.set(8, y, z, '-');
            s.set(22, y, z, '-');
        }
        // the spire core in the bridge's middle and the glass cell over it
        for (int y = CORE_FROM; y < SEAT_Y; y++) s.set(MID, y, CORE_Z, 'C');
        s.set(MID, SEAT_Y, CORE_Z, 'Z');
        s.set(MID, BRIDGE_ROOF, CORE_Z, 'G');
        // six resonator columns against the bridge's side walls: plinth y9, two sockets y10..y11, crown y12
        int k = 0;
        for (int[] side : new int[][] { { 6, 1 }, { 10, -1 } }) {
            int z = side[0];
            for (int x : new int[] { 11, 15, 19 }) {
                int[][] cells = { { x, 9, z, 'p' }, { x, 10, z, 'o' }, { x, 11, z, 'o' }, { x, 12, z, 'c' } };
                k++;
                units.add(unit(s, "resonator-" + k, "resonator", cells, 2, new int[] { 0, side[1] }));
            }
        }
        // the kiosk: console stand y1 and the controller y2 in the road under the bridge, facing the nexus
        s.set(MID, 1, CTRL_Z, 'K');
        s.set(MID, 2, CTRL_Z, ShapeKit.CONTROLLER);
        s.controller = new int[] { CTRL_X, CTRL_Y, CTRL_Z };
        // passage floor: soft deck where the gate road has no paving (|R| 4..6 outside the node and the threshold)
        for (int x = 9; x < 22; x++) for (int z = 0; z < D; z++) {
            int r = Math.abs(x - MID), a = z + 23;
            boolean road = r <= 3 || r <= 5 && (25 <= a && a <= 29 || a >= 34);
            if (!road) s.set(x, 0, z, '.');
        }
        // tuning racks: two per wing ground hall against the outer wall (plinth, three sockets, crown, y1..y5)
        for (int[] wall : new int[][] { { 1, 1 }, { W - 2, -1 } }) {
            int xw = wall[0];
            for (int z : new int[] { 4, 8 }) {
                int[][] cells = { { xw, 1, z, 'p' }, { xw, 2, z, 'o' }, { xw, 3, z, 'o' }, { xw, 4, z, 'o' },
                    { xw, 5, z, 'c' } };
                k++;
                units.add(unit(s, "rack-" + k, "rack", cells, 3, new int[] { wall[1], 0 }));
            }
        }
        for (int x : new int[] { 4, 26 }) s.set(x, 1, 5, 'h');
        for (int[] c : new int[][] { { 0, 0 }, { 8, 0 }, { 22, 0 }, { 30, 0 } }) s.set(c[0], 15, c[1], 'A');
        for (Object[] u : units) {
            @SuppressWarnings("unchecked")
            List<int[]> cells = (List<int[]>) u[2];
            s.unit((String) u[0], (String) u[1], cells, (Integer) u[3], (int[]) u[4]);
        }
        return s;
    }

    /** Sets a unit's cells {x, y, z, character} and returns its declaration, made once the shape is drawn. */
    private static Object[] unit(ShapeKit.Shape s, String name, String kind, int[][] cells, int slots, int[] face) {
        List<int[]> at = new ArrayList<>();
        for (int[] c : cells) {
            s.set(c[0], c[1], c[2], (char) c[3]);
            at.add(new int[] { c[0], c[1], c[2] });
        }
        return new Object[] { name, kind, at, slots, face };
    }

    /** Whether the column (x, z) lies in one of the two stair wells (the post and the ring of steps round it). */
    private static boolean inWell(int x, int z) {
        for (int[] w : WELLS) if (Math.abs(x - w[0]) <= 1 && Math.abs(z - w[1]) <= 1) return true;
        return false;
    }
}
