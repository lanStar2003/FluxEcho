package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 魔力泉阵 The Mana Fountain Array (0.13.0, CITY.md §4.6): a pavilion that is a cloister, low and open, the welcoming
 * garden by the gate. Its outer wall stands on the plan's octagon {@code oct(12, 16)} (y1..6, glazing y3..4 between lit
 * pilasters every 2 on the flat faces, almost an arcade, lit jambs at the four chamfer portals); a glass roof at y7
 * covers the arcade ring ({@code oct(12, 16)} less {@code oct(9, 12)}: glazing with panel ribs on the eight 0/45 degree
 * lines, its edge a cornice); an inner colonnade on the {@code oct(9, 12)} line (a post every other cell, y1..5, under
 * a cornice architrave at y6, open on the four diagonals) rings the open court {@code oct(8, 11)}.
 * <p>
 * In the middle of the court stands the obelisk: the controller on its console, then four conduits (y3..6) and a seat
 * (y7) that give way to the rendered mana spout once the array forms. Round it at octagon radius 6 eight fountains (a
 * basin rim three by three round water, a socket floating over the water at y2); just inside the colonnade eight flower
 * urns (a plinth and a socket). 16 units, 16 slots; their order is the slot order and never changes ({@link #UNITS}).
 * The arcade ring's floor off the axes and the diagonals is garden soil (grass) for the player's own flowers and mana
 * pools; a soft lit magic circle (octagon radius 2 and 8, and the axes) lies in the court's floor.
 * <p>
 * The geometry is an exact port of the generator ({@code city/gen/b_pav.py}, {@code mana_fountain()}) on
 * {@link ShapeKit}'s pavilion helpers; it uses the city legend unchanged and mirrors across x but for its gate niche
 * (内境门位, CITY.md §4.15: the back flat face's middle, {@code x 11..13, y 1..4, z 24}), which is soft wall
 * ({@link ShapeKit#backNiche}). The generator gives it a box nine high ({@link #GEN_HEIGHT}) but draws y0..y7 only, so
 * the blueprint is eight high ({@link #HEIGHT}). On a diagonal site its blueprint's front faces along A toward the
 * nexus and its controller stands over the octagon's centre ({@link BuildPlan#pavilionController}).
 * <p>
 * Counts: checked 1,107, soft 405 (12 of them the niche, 8 the fountains' water), must-be-air 160 (four portals) =
 * 1,672 steps.
 */
public final class ManaFountainShape {

    public static final String KEY = "mana_fountain";
    /**
     * The pavilion sites it may stand on, preferred first: front-right beside the gate, then 7, 3, 5 (CITY.md §2.2).
     */
    public static final int[] SITES = { 1, 7, 3, 5 };
    /** The box: 25 across and deep, 8 high (y0..y7); the octagon's middle at {@link #MID}. */
    public static final int WIDTH = ShapeKit.PAV_SIZE, DEPTH = ShapeKit.PAV_SIZE, HEIGHT = 8, MID = ShapeKit.PAV_C;
    /** The generator's box is a layer higher than it draws (its top layer stays empty). */
    static final int GEN_HEIGHT = 9;
    /** The controller on the obelisk's console at the court's centre, at eye height. */
    public static final int CTRL_X = MID, CTRL_Y = 2, CTRL_Z = MID;
    /** The outer wall rises to {@code WALL_TOP}; the glass roof and the colonnade's architrave. */
    public static final int WALL_TOP = 6, ROOF_Y = 7, ARCHITRAVE_Y = 6;
    /** The obelisk over the controller: its conduits and its seat. */
    public static final int OBELISK_FROM = 3, OBELISK_TO = 7;
    /**
     * The characters that give way to the rendered mana spout once the array forms: the obelisk's conduits and seat.
     * Players pass through them as well ({@link #PASSES}): they stand on the controller, over the head of anyone beside
     * it (the walk test holds it), and nothing stands on them.
     */
    public static final String DISSOLVES = "CZ", PASSES = "CZ";

    /** The shape as the generator draws it (do not change it: every array is the shape itself). */
    public static final ShapeKit.Shape SHAPE = build();
    /** The units in slot order: eight fountains, then eight flower urns. */
    public static final UnitLayout UNITS = new UnitLayout(SHAPE);
    /** The checked shape for StructureLib: the soft cells left out (anything goes there). */
    public static final Blueprint BLUEPRINT = ShapeKit.blueprint(SHAPE, HEIGHT);

    /** The plan's walls (its thick edge), the portals' columns and the colonnade's ring. */
    private static final Set<Long> EDGE, DOORS, COLONNADE;

    static {
        Set<Long> plan = ShapeKit.planCells();
        EDGE = Collections.unmodifiableSet(ShapeKit.thickEdge(plan));
        DOORS = Collections.unmodifiableSet(ShapeKit.portalCells(EDGE));
        Set<Long> col = ShapeKit.planCells(9, 12);
        col.removeAll(ShapeKit.planCells(8, 11));
        COLONNADE = Collections.unmodifiableSet(col);
        if (UNITS.size() != 16 || UNITS.slots() != 16) throw new IllegalStateException("the mana fountain's units");
        for (int x = 0; x < WIDTH; x++) for (int z = 0; z < DEPTH; z++) {
            if (SHAPE.get(x, GEN_HEIGHT - 1, z) != ShapeKit.NONE)
                throw new IllegalStateException("the mana fountain's top layer is no longer empty");
        }
    }

    /** The Mana Fountain Array as the generic module plan builds it ({@link BuildPlan#module} on a pavilion site). */
    public static final CityBuild BUILD = new CityBuild(
        KEY,
        SHAPE,
        HEIGHT,
        BLUEPRINT,
        UNITS,
        Parts.moduleCore(ModuleCores.MANA_FOUNTAIN),
        new CityBuild.Stages() {

            @Override
            public int stage(int x, int y, int z, char ch) {
                return ManaFountainShape.stage(x, y, z, ch);
            }

            @Override
            public int fitRank(int x, int y, int z, char ch) {
                return ManaFountainShape.fitRank(x, y, z, ch);
            }
        });

    private ManaFountainShape() {}

    /**
     * The Mana Fountain Array's job on a pavilion site ({@link BuildPlan#module} of {@link #BUILD}).
     *
     * @throws IllegalArgumentException if the site is not a pavilion site or the front not a horizontal unit vector
     */
    public static BuildPlan.Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        if (SiteFamily.of(site) != SiteFamily.PAVILION)
            throw new IllegalArgumentException("not a pavilion site: " + site);
        return BuildPlan.module(plan, site, cx, y0, cz, fx, fz, BUILD);
    }

    /** Where the Mana Fountain Array's controller stands on a pavilion site: {x, y, z, facing ordinal}. */
    public static int[] controller(int site, int cx, int y0, int cz, int fx, int fz) {
        return BuildPlan.pavilionController(BUILD, site, cx, y0, cz, fx, fz);
    }

    /** The character of a cell: the checked legend, a soft character, {@code -} or a space (anything). */
    public static char cell(int x, int y, int z) {
        return y < HEIGHT ? SHAPE.get(x, y, z) : ShapeKit.NONE;
    }

    /** The blueprint cell of a point of the shape: {across, layer from the top, row from the front}. */
    public static int[] cellOf(int x, int y, int z) {
        return new int[] { x, HEIGHT - 1 - y, z };
    }

    /** Whether the column (x, z) carries the outer wall (the plan's thick edge). */
    public static boolean onEdge(int x, int z) {
        return EDGE.contains(ShapeKit.key(x, z));
    }

    /** Whether the column (x, z) is one of the four chamfer portals' (their opening and its two-deep air). */
    public static boolean inPortal(int x, int z) {
        return DOORS.contains(ShapeKit.key(x, z));
    }

    /** Whether the column (x, z) lies on the colonnade's ring ({@code oct(9, 12)} less {@code oct(8, 11)}). */
    public static boolean onColonnade(int x, int z) {
        return COLONNADE.contains(ShapeKit.key(x, z));
    }

    /** Whether a cell is part of the gate niche (soft wall, CITY.md §4.15). */
    public static boolean inNiche(int x, int y, int z) {
        return ShapeKit.pavilionNiche(x, y, z);
    }

    /**
     * The build stage of a cell that is not part of a unit (CITY.md §4.6): FLOOR the floor of y0 (the court, the garden
     * soil, the magic circle); SHELL the foundations and the outer wall (y1..6 with its anchors, the gate niche with
     * its wall), the portals' air and lintels; DECK the colonnade's posts and its architrave; ROOF the glass roof with
     * its cornice; FIT the obelisk (its console, conduits and seat) and the fountains' water; COMMISSION the
     * controller. UPPER stays empty.
     */
    public static int stage(int x, int y, int z, char ch) {
        if (ch == ShapeKit.CONTROLLER) return BuildPlan.M_COMMISSION;
        if (ch == ShapeKit.AIR) return BuildPlan.M_SHELL;
        if (y == 0) return ShapeKit.soft(ch) ? BuildPlan.M_FLOOR : BuildPlan.M_SHELL;
        if (ch == 'K' || ch == 'C' || ch == 'Z' || ch == '=') return BuildPlan.M_FIT;
        if (y <= WALL_TOP && (onEdge(x, z) || inPortal(x, z))) return BuildPlan.M_SHELL;
        if (y <= ARCHITRAVE_Y && onColonnade(x, z)) return BuildPlan.M_DECK;
        if (y == ROOF_Y) return BuildPlan.M_ROOF;
        throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
    }

    /**
     * The order within FIT of a cell that is not part of a unit: before the units the obelisk, bottom-up; after them
     * (the fountains, then the urns) the fountains' water, last, so it never runs over a rim not yet laid (as the Eco
     * Dome's pool).
     */
    public static int fitRank(int x, int y, int z, char ch) {
        return ch == '=' ? 1 : -1;
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
    // the generator (a port of b_pav.py's mana_fountain)

    private static ShapeKit.Shape build() {
        final int N = ShapeKit.PAV_SIZE, C = ShapeKit.PAV_C;
        ShapeKit.Shape s = new ShapeKit.Shape(KEY, N, GEN_HEIGHT, N);
        ShapeKit.Shell sh = ShapeKit.shell(s, WALL_TOP, 2, new int[] { 3, 4 });
        // the glass roof over the arcade ring: glazing, lit ribs on the eight lines, the outer ring a cornice
        for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) {
            int du = x - C, dv = z - C;
            if (!ShapeKit.octo(du, dv, 12, 16) || ShapeKit.octo(du, dv, 9, 12)) continue;
            boolean rib = du == 0 || dv == 0 || Math.abs(du) == Math.abs(dv);
            s.set(x, ROOF_Y, z, !ShapeKit.octo(du, dv, 11, 15) ? 'n' : rib ? 'W' : 'G');
        }
        // the colonnade: an architrave on the oct(9, 12) line, a post under it every other cell but by the diagonals
        for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) {
            int du = x - C, dv = z - C;
            if (!ShapeKit.octo(du, dv, 9, 12) || ShapeKit.octo(du, dv, 8, 11)) continue;
            s.set(x, ARCHITRAVE_Y, z, 'n');
            boolean portalAxis = Math.abs(Math.abs(du) - Math.abs(dv)) <= 1;
            if ((du + dv) % 2 == 0 && !portalAxis) for (int y = 1; y < ARCHITRAVE_Y; y++) s.set(x, y, z, 'q');
        }
        // the obelisk: the controller on its console, its conduits and its seat over it
        ShapeKit.centreController(s, CTRL_Y);
        for (int y = OBELISK_FROM; y < OBELISK_TO; y++) s.set(C, y, C, 'C');
        s.set(C, OBELISK_TO, C, 'Z');

        List<Object[]> units = new ArrayList<>();
        // eight fountains at octagon radius 6: a basin rim round water, a socket floating over the water
        int[][] fountains = { { C + 6, C }, { C - 6, C }, { C, C + 6 }, { C, C - 6 }, { C + 4, C + 4 },
            { C - 4, C + 4 }, { C + 4, C - 4 }, { C - 4, C - 4 } };
        for (int i = 0; i < fountains.length; i++) {
            int fx = fountains[i][0], fz = fountains[i][1];
            List<int[]> cells = new ArrayList<>();
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) s.set(fx, 1, fz, '=');
                else {
                    s.set(fx + a, 1, fz + b, 'u');
                    cells.add(new int[] { fx + a, 1, fz + b });
                }
            }
            s.set(fx, 2, fz, 'o');
            cells.add(new int[] { fx, 2, fz });
            units.add(new Object[] { "fountain-" + (i + 1), "fountain", cells, 1, null });
        }
        // eight flower urns just inside the colonnade: a plinth and a socket
        int[][] urns = { { C + 8, C + 2 }, { C + 8, C - 2 }, { C - 8, C + 2 }, { C - 8, C - 2 }, { C + 2, C + 8 },
            { C - 2, C + 8 }, { C + 2, C - 8 }, { C - 2, C - 8 } };
        for (int i = 0; i < urns.length; i++) {
            int x = urns[i][0], z = urns[i][1];
            s.set(x, 1, z, 'p');
            s.set(x, 2, z, 'o');
            List<int[]> cells = new ArrayList<>();
            cells.add(new int[] { x, 1, z });
            cells.add(new int[] { x, 2, z });
            units.add(new Object[] { "urn-" + (i + 1), "urn", cells, 1, null });
        }
        // the floor: the magic circle (soft lit) at octagon radius 2 and 8 and on the axes in the court; garden soil
        // in the arcade ring, off the axes and the diagonals
        for (long k : sh.plan) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            int r = ShapeKit.onorm(du, dv);
            if (s.get(x, 0, z) != '.') continue;
            if (r <= 8 && (r == 2 || r == 8 || du == 0 || dv == 0)) s.set(x, 0, z, '+');
            else if (r >= 10 && Math.abs(Math.abs(du) - Math.abs(dv)) > 2 && du != 0 && dv != 0) s.set(x, 0, z, ',');
        }
        // cladding anchors: the four flat faces' middles at y5
        for (long k : sh.edge) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k), du = x - C, dv = z - C;
            char ch = s.get(x, 5, z);
            if ((du == 0 || dv == 0) && (ch == 'W' || ch == 'L' || ch == 'G')) s.set(x, 5, z, 'A');
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
