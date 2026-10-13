package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 血契坛 The Blood Covenant Altar (0.13.0, CITY.md §4.4): a pavilion that rises in three stepped tiers, a ziggurat
 * crossed with a rotunda. Tier 1's walls stand on the plan's octagon {@code oct(12, 16)} (y1..6, lit pilasters every 4
 * on the flat faces, lit jambs at the four chamfer portals), under the slab at y7 ({@code oct(12, 16)} less
 * {@code oct(8, 11)}, its edge a cornice) whose terrace carries a rail at y8; tier 2's walls stand on
 * {@code oct(9, 12)} (y8..11, a dark course at y8, lit ribs on the eight 0/45 degree lines) under the slab at y12 (less
 * {@code oct(5, 7)}); tier 3's on {@code oct(6, 8)} (y13..15) under the roof at y16, open over the altar in the oculus
 * {@code oct(2, 3)} with its lit rim. Inside, a stepped funnel rises toward the sky: the outer ring six high, the
 * middle ring to y11, the inner ring to y15.
 * <p>
 * The four chamfer portals lead to the dais at the heart: a block ring {@code oct(2, 2)} (one stands on it at y2)
 * inside a ring of treads {@code oct(3, 4)}, the altar's console on it at y2 and the controller over it at y3, at eye
 * height for someone on the dais. (The dais's middle cell under the console is left empty, as the generator draws it:
 * the blocks round it close it in.) Eight orb cradles (a plinth and a socket) stand at octagon radius 6; four altar
 * bays on the flat-face axes at radius 9, each a 5 x 5 square marked in soft trim on the floor for the player's own
 * blood altar, hold a ritual stone (a socket) in their middle; a soft lit ritual circle at octagon radius 8 with four
 * diagonal spokes lies in the floor. 12 units, 12 slots; their order is the slot order and never changes
 * ({@link #UNITS}). Nothing dissolves: the blood orb over the altar is drawn, not built.
 * <p>
 * The geometry is an exact port of the generator ({@code city/gen/b_pav.py}, {@code blood_covenant()}) on
 * {@link ShapeKit}'s pavilion helpers; it uses the city legend unchanged and mirrors across x but for its gate niche
 * (内境门位, CITY.md §4.15: the back flat face's middle, {@code x 11..13, y 1..4, z 24}), which is soft wall
 * ({@link ShapeKit#backNiche}). On a diagonal site its blueprint's front faces along A toward the nexus and its
 * controller stands over the octagon's centre ({@link BuildPlan#pavilionController}) at Y0 + 3.
 * <p>
 * Counts: checked 1,694, soft 397 (12 of them the niche), must-be-air 160 (four portals) = 2,251 steps.
 */
public final class BloodCovenantShape {

    public static final String KEY = "blood_covenant";
    /**
     * The pavilion sites it may stand on, preferred first: back-left, the dark corner behind the Archive, then 3, 7, 1
     * (CITY.md §2.2).
     */
    public static final int[] SITES = { 5, 3, 7, 1 };
    /** The box: 25 across and deep, 17 high (y0..y16); the octagon's middle at {@link #MID}. */
    public static final int WIDTH = ShapeKit.PAV_SIZE, DEPTH = ShapeKit.PAV_SIZE, HEIGHT = 17, MID = ShapeKit.PAV_C;
    /** The controller over the altar's console on the dais, at eye height for someone standing on the dais. */
    public static final int CTRL_X = MID, CTRL_Y = 3, CTRL_Z = MID;
    /** Tier 1's walls rise to {@code TIER1_TOP}; the slab over it, tier 2's walls, the slab over them. */
    public static final int TIER1_TOP = 6, SLAB1_Y = 7, TIER2_FROM = 8, TIER2_TO = 11, SLAB2_Y = 12;
    /** Tier 3's walls and the roof with its oculus over the altar. */
    public static final int TIER3_FROM = 13, TIER3_TO = 15, ROOF_Y = 16;
    /** The terrace's rail on the slab at y7. */
    public static final int RAIL_Y = 8;
    /** Nothing gives way once the altar forms: its blood orb is drawn over the oculus, not built. */
    public static final String DISSOLVES = "", PASSES = "";

    /** The shape as the generator draws it (do not change it: every array is the shape itself). */
    public static final ShapeKit.Shape SHAPE = build();
    /** The units in slot order: eight orb cradles, then the four bays' ritual stones. */
    public static final UnitLayout UNITS = new UnitLayout(SHAPE);
    /** The checked shape for StructureLib: the soft cells left out (anything goes there). */
    public static final Blueprint BLUEPRINT = ShapeKit.blueprint(SHAPE, HEIGHT);

    /** Tier 1's walls (the plan's thick edge), the portals' columns, and tier 2's walls. */
    private static final Set<Long> EDGE, DOORS, TIER2;

    static {
        Set<Long> plan = ShapeKit.planCells();
        EDGE = Collections.unmodifiableSet(ShapeKit.thickEdge(plan));
        DOORS = Collections.unmodifiableSet(ShapeKit.portalCells(EDGE));
        TIER2 = Collections.unmodifiableSet(ShapeKit.thickEdge(ShapeKit.planCells(9, 12)));
        if (UNITS.size() != 12 || UNITS.slots() != 12) throw new IllegalStateException("the blood covenant's units");
        if (SHAPE.H != HEIGHT) throw new IllegalStateException("the blood covenant is no longer 17 high");
    }

    /** The Blood Covenant Altar as the generic module plan builds it ({@link BuildPlan#module} on a pavilion site). */
    public static final CityBuild BUILD = new CityBuild(
        KEY,
        SHAPE,
        HEIGHT,
        BLUEPRINT,
        UNITS,
        Parts.moduleCore(ModuleCores.BLOOD_COVENANT),
        new CityBuild.Stages() {

            @Override
            public int stage(int x, int y, int z, char ch) {
                return BloodCovenantShape.stage(x, y, z, ch);
            }

            @Override
            public int fitRank(int x, int y, int z, char ch) {
                return BloodCovenantShape.fitRank(x, y, z, ch);
            }
        });

    private BloodCovenantShape() {}

    /**
     * The Blood Covenant Altar's job on a pavilion site ({@link BuildPlan#module} of {@link #BUILD}).
     *
     * @throws IllegalArgumentException if the site is not a pavilion site or the front not a horizontal unit vector
     */
    public static BuildPlan.Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        if (SiteFamily.of(site) != SiteFamily.PAVILION)
            throw new IllegalArgumentException("not a pavilion site: " + site);
        return BuildPlan.module(plan, site, cx, y0, cz, fx, fz, BUILD);
    }

    /** Where the Blood Covenant Altar's controller stands on a pavilion site: {x, y, z, facing ordinal}. */
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

    /** Whether the column (x, z) carries tier 1's walls (the plan's thick edge). */
    public static boolean onEdge(int x, int z) {
        return EDGE.contains(ShapeKit.key(x, z));
    }

    /** Whether the column (x, z) is one of the four chamfer portals' (their opening and its two-deep air). */
    public static boolean inPortal(int x, int z) {
        return DOORS.contains(ShapeKit.key(x, z));
    }

    /**
     * The levels the module window's plan page shows after the floor the units stand on (0.13.0, M10 "blood
     * floor/tiers"): tier 2 at the foot of its walls over the terrace at y7, and tier 3 over the slab at y12. Nobody's
     * unit stands up there; the pages show the stepped tiers round the open funnel.
     */
    public static int[] planLevels() {
        return new int[] { TIER2_FROM, TIER3_FROM };
    }

    /** Whether the column (x, z) carries tier 2's walls (the thick edge of {@code oct(9, 12)}). */
    public static boolean onTier2(int x, int z) {
        return TIER2.contains(ShapeKit.key(x, z));
    }

    /** Whether a cell is part of the gate niche (soft wall, CITY.md §4.15). */
    public static boolean inNiche(int x, int y, int z) {
        return ShapeKit.pavilionNiche(x, y, z);
    }

    /**
     * The build stage of a cell that is not part of a unit (CITY.md §4.4): FLOOR the soft floor of y0 with its bays'
     * trim and its ritual circle; SHELL the foundations and tier 1 (its walls y1..6, the portals' air and lintels, the
     * gate niche with its wall); DECK the slab at y7, tier 2's walls and the slab at y12; ROOF tier 3, the roof with
     * its oculus rim and the terrace's rail; FIT the dais and the altar's console; COMMISSION the controller. UPPER
     * stays empty.
     */
    public static int stage(int x, int y, int z, char ch) {
        if (ch == ShapeKit.CONTROLLER) return BuildPlan.M_COMMISSION;
        if (ch == ShapeKit.AIR) return BuildPlan.M_SHELL;
        if (y == 0) return ShapeKit.soft(ch) ? BuildPlan.M_FLOOR : BuildPlan.M_SHELL;
        if (ch == 'B' || ch == 't' || ch == 'K') return BuildPlan.M_FIT;
        if (y <= TIER1_TOP && (onEdge(x, z) || inPortal(x, z))) return BuildPlan.M_SHELL;
        if (y == SLAB1_Y || y == SLAB2_Y || y >= TIER2_FROM && y <= TIER2_TO && onTier2(x, z)) return BuildPlan.M_DECK;
        if (ch == 'r' && y == RAIL_Y || y >= TIER3_FROM) return BuildPlan.M_ROOF;
        throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
    }

    /**
     * The order within FIT of a cell that is not part of a unit: before the units the dais, then the altar's console on
     * it (the cradles and the ritual stones follow as their units).
     */
    public static int fitRank(int x, int y, int z, char ch) {
        return ch == 'K' ? -1 : -2;
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
    // the generator (a port of b_pav.py's blood_covenant)

    private static ShapeKit.Shape build() {
        final int N = ShapeKit.PAV_SIZE, C = ShapeKit.PAV_C;
        ShapeKit.Shape s = new ShapeKit.Shape(KEY, N, HEIGHT, N);
        ShapeKit.Shell sh = ShapeKit.shell(s, TIER1_TOP, 4, new int[0]);
        // the tiers' slabs: y7 covers oct(12, 16) less oct(8, 11), y12 oct(9, 12) less oct(5, 7), y16 oct(6, 8) less
        // the oculus; each slab's outer ring is a cornice
        int[][] tiers = { { 12, 16, 8, 11, SLAB1_Y }, { 9, 12, 5, 7, SLAB2_Y }, { 6, 8, 2, 3, ROOF_Y } };
        for (int[] t : tiers) {
            for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) {
                int du = x - C, dv = z - C;
                if (!ShapeKit.octo(du, dv, t[0], t[1]) || ShapeKit.octo(du, dv, t[2], t[3])) continue;
                s.set(x, t[4], z, ShapeKit.octo(du, dv, t[0] - 1, t[1] - 1) ? 'W' : 'n');
            }
        }
        // the upper tiers' walls: a dark course at the bottom, lit ribs on the eight 0/45 degree lines
        int[][] walls = { { 9, 12, TIER2_FROM, TIER2_TO }, { 6, 8, TIER3_FROM, TIER3_TO } };
        for (int[] w : walls) {
            for (long k : ShapeKit.thickEdge(ShapeKit.planCells(w[0], w[1]))) {
                int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
                boolean rib = du == 0 || dv == 0 || Math.abs(du) == Math.abs(dv);
                for (int y = w[2]; y <= w[3]; y++) s.set(x, y, z, rib ? 'L' : y == w[2] ? 'w' : 'W');
            }
        }
        // the parapet on the terrace (a rail), and the oculus's lit rim
        for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) {
            int du = x - C, dv = z - C;
            if (ShapeKit.octo(du, dv, 12, 16) && !ShapeKit.octo(du, dv, 11, 15)) s.set(x, RAIL_Y, z, 'r');
        }
        for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) {
            int du = x - C, dv = z - C;
            if (ShapeKit.octo(du, dv, 3, 4) && !ShapeKit.octo(du, dv, 2, 3)) s.set(x, ROOF_Y, z, 'L');
        }
        // the dais: a block ring oct(2, 2) (its middle left empty) inside a ring of treads oct(3, 4), at y1
        for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) {
            int du = x - C, dv = z - C;
            if (ShapeKit.octo(du, dv, 2, 2) && (du != 0 || dv != 0)) s.set(x, 1, z, 'B');
            else if (ShapeKit.octo(du, dv, 3, 4) && !ShapeKit.octo(du, dv, 2, 2)) s.set(x, 1, z, 't');
        }
        s.set(C, CTRL_Y - 1, C, 'K');
        s.set(C, CTRL_Y, C, ShapeKit.CONTROLLER);
        s.controller = new int[] { C, CTRL_Y, C };

        List<Object[]> units = new ArrayList<>();
        // eight orb cradles at octagon radius 6: a plinth at y1, a socket at y2
        int[][] cradles = { { C + 6, C }, { C - 6, C }, { C, C + 6 }, { C, C - 6 }, { C + 4, C + 4 }, { C - 4, C + 4 },
            { C + 4, C - 4 }, { C - 4, C - 4 } };
        for (int i = 0; i < cradles.length; i++) {
            int x = cradles[i][0], z = cradles[i][1];
            s.set(x, 1, z, 'p');
            s.set(x, 2, z, 'o');
            List<int[]> cells = new ArrayList<>();
            cells.add(new int[] { x, 1, z });
            cells.add(new int[] { x, 2, z });
            units.add(new Object[] { "cradle-" + (i + 1), "cradle", cells, 1, null });
        }
        // four ritual stones (sockets) in the middle of the altar bays, each bay a 5 x 5 square marked in soft trim
        int[][] faces = { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };
        for (int i = 0; i < faces.length; i++) {
            int bx = C + faces[i][0] * 9, bz = C + faces[i][1] * 9;
            for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) {
                if ((Math.abs(a) == 2 || Math.abs(b) == 2) && s.get(bx + a, 0, bz + b) == '.')
                    s.set(bx + a, 0, bz + b, '_');
            }
            s.set(bx, 1, bz, 'o');
            List<int[]> cells = new ArrayList<>();
            cells.add(new int[] { bx, 1, bz });
            units.add(new Object[] { "bay-" + (i + 1), "ritual", cells, 1, null });
        }
        // the floor: the ritual circle (soft lit) at octagon radius 8 and the four diagonal spokes from the dais
        for (long k : sh.plan) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            int r = ShapeKit.onorm(du, dv);
            if (s.get(x, 0, z) == '.' && (r == 8 || r >= 4 && r <= 8 && Math.abs(du) == Math.abs(dv)))
                s.set(x, 0, z, '+');
        }
        // cladding anchors: the four flat faces' middles at y5
        for (long k : sh.edge) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            char ch = s.get(x, 5, z);
            if ((du == 0 || dv == 0) && (ch == 'W' || ch == 'L')) s.set(x, 5, z, 'A');
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
