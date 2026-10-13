package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 生态穹 The Eco Dome (0.11.0, CITY.md §4.1): a greenhouse dome over a garden, the second module the nexus builds on a
 * hall site (it prefers site 2, to the nexus's right). A portal block (x 2..24, z 0..11: walls y1..11, lit pilasters
 * every 4, ground and upper windows, a cornice at y12, a flat roof) stands before a drum (radius 13.4 round
 * {@code (13, 21)}: a dark course, glazing y2..5 between eight lit ribs on the 45 degree lines, a cornice at y7) that
 * carries a hemispherical glass dome (the ball of radius 13.4 round {@code (13, 7, 21)}: glaze with eight lit
 * meridian ribs, lit latitude rings at y12 and y16, a glazed crown lantern y19..20).
 * <p>
 * Inside: the kit portal opens on a double-height vestibule with two holo pedestals; two mirrored spiral stairs (posts
 * {@code (5, 3)}, {@code (21, 3)}, twelve steps) climb to the portal block's upper floor (deck y6), which joins the
 * dome gallery, a 3-wide ring at deck y6 with a lit inner row and a rail. Two bridges (z 20..22) lead to the central
 * island ({@code oct(4, 5)} round the trunk at deck y6: a base rim with a rail, grass on top), which floats five above
 * the garden. The world tree's trunk (frame conduit y1..16, a seat at y17; both dissolve into the rendered tree)
 * rises through it, and a spiral of ten steps round the trunk climbs to the crown island ({@code oct(3, 4)} at deck
 * y11). On the ground: a pond (water d 1.5..3.6 round the trunk inside a basin rim to 4.6), open grass for the
 * player's own hives and crops, and the planters.
 * <p>
 * Twelve planters, each a row of three sockets inside a 3 x 5 basin rim (the whole bed is the unit, three slots; the
 * two
 * on the gallery leave the drum's cornice where their rim would reach it): seven on the ground, two on the island, two
 * on the gallery and one on the crown island: 36 slots. Their order is the slot
 * order and never changes ({@link #UNITS}).
 * <p>
 * The geometry is an exact port of the generator ({@code city/gen/b_hall.py}, {@code eco_dome()}) on {@link ShapeKit};
 * it uses the city legend unchanged. The generator allots 22 layers but draws only y0..y20, so the blueprint is 21
 * high ({@link #HEIGHT}). The trunk's spiral turns one way, so those ten steps are the shape's only cells that do not
 * mirror across x: the structure check maps cells exactly as {@link Blueprint#world} does (a test holds it to
 * StructureLib's own mapping), so they need not.
 * <p>
 * The gate niche (内境门位, CITY.md §4.15) in the back drum wall's middle ({@link #NICHE_X0}..{@link #NICHE_X1},
 * y{@link #NICHE_Y0}..{@link #NICHE_Y1}, z {@link #NICHE_Z}: the dark course, the glass and the back rib) is built
 * with the same blocks as soft cells ({@link ShapeKit.Shape#gateNiche}), so the structure check never looks at it and
 * the 内境 gate can open it later without changing the checked shape or the build plan's HARD steps.
 * <p>
 * Counts: checked 2,777, soft 774 (12 of them the niche), must-be-air 48, decor 14 (the canopy) = 3,613 steps (0.14.1:
 * the crown deck opened one more grass cell over the spiral, which had cost the head on its fourth step).
 */
public final class EcoDomeShape {

    public static final String KEY = "eco_dome";
    /** The hall sites the Eco Dome may stand on, preferred first: right, left, behind (CITY.md §2.2). */
    public static final int[] SITES = { 2, 6, 4 };
    /** The box: 27 across, 35 deep, 21 high (y0..y20); the axis at {@link #MID}. */
    public static final int WIDTH = 27, DEPTH = 35, HEIGHT = 21, MID = 13;
    /** The controller in the trumeau, at eye height over its console stand. */
    public static final int CTRL_X = 13, CTRL_Y = 2, CTRL_Z = 0;
    /** The trunk's column, the dome's centre height and radius. */
    public static final int TRUNK_X = 13, TRUNK_Z = 21, DOME_Y = 7;
    public static final double DOME_R = 13.4;
    /** The gallery and island deck, the crown island's deck. */
    public static final int DECK_Y = 6, CROWN_Y = 11;
    /** The gate niche (CITY.md §4.15): x 12..14, y 1..4 in the back drum wall (z 34); soft, built as wall. */
    public static final int NICHE_X0 = 12, NICHE_X1 = 14, NICHE_Y0 = 1, NICHE_Y1 = 4, NICHE_Z = 34;
    /**
     * The characters that give way to the rendered tree of light once the dome forms (not drawn): the trunk and its
     * seat. Of those, players pass through the seat only ({@link #PASSES}): the trunk runs through the floating
     * island's deck (y6) and the crown island's (y11) and is the newel of the spiral between them, so it keeps its
     * collision, as the walk test assumes.
     */
    public static final String DISSOLVES = "CZ", PASSES = "Z";
    /** The layer the generator allots; the top one is empty. */
    static final int GEN_HEIGHT = 22;
    /** The two vestibule stairs' posts (x; z 3) and the posts' height. */
    static final int[] STAIR_POSTS = { 5, 21 };
    static final int STAIR_Z = 3;

    /** The shape as the generator draws it (do not change it: every array is the shape itself). */
    public static final ShapeKit.Shape SHAPE = build();
    /** The planters, in slot order. */
    public static final UnitLayout UNITS = new UnitLayout(SHAPE);
    /** The checked shape for StructureLib: the soft cells left out (anything goes there). */
    public static final Blueprint BLUEPRINT = blueprint(SHAPE, HEIGHT);

    /** The column sets the stage rule needs: the footprint plan, its edge, the portal block. */
    private static final Set<Long> PLAN, EDGE, BLOCK, DOME_PLAN;
    /** The cells of the trunk's spiral. */
    private static final Set<Long> TRUNK_SPIRAL;

    static {
        Set<Long> dome = new LinkedHashSet<>(), block = new LinkedHashSet<>();
        for (int x = 0; x < WIDTH; x++) for (int z = 0; z < DEPTH; z++) {
            if (ShapeKit.hypot(x - MID, z - TRUNK_Z) <= DOME_R) dome.add(ShapeKit.key(x, z));
        }
        for (int x = 2; x < 25; x++) for (int z = 0; z < 12; z++) block.add(ShapeKit.key(x, z));
        Set<Long> plan = new LinkedHashSet<>(dome);
        plan.addAll(block);
        DOME_PLAN = Collections.unmodifiableSet(dome);
        BLOCK = Collections.unmodifiableSet(block);
        PLAN = Collections.unmodifiableSet(plan);
        EDGE = Collections.unmodifiableSet(ShapeKit.boundary(plan));
        Set<Long> sp = new LinkedHashSet<>();
        for (int[] c : ShapeKit.spiral(TRUNK_X, TRUNK_Z, 7, 10, 0, 1)) sp.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));
        TRUNK_SPIRAL = Collections.unmodifiableSet(sp);
        if (UNITS.size() != 12 || UNITS.slots() != 36) throw new IllegalStateException("the planters changed");
        for (int x = 0; x < WIDTH; x++) for (int z = 0; z < DEPTH; z++) if (SHAPE.get(x, GEN_HEIGHT - 1, z) != ' ')
            throw new IllegalStateException("the eco dome's top layer is no longer empty");
    }

    /** The Eco Dome as the generic module plan builds it ({@link BuildPlan#module}). */
    public static final CityBuild BUILD = new CityBuild(
        KEY,
        SHAPE,
        HEIGHT,
        BLUEPRINT,
        UNITS,
        Parts.moduleCore(ModuleCores.ECO_DOME),
        new CityBuild.Stages() {

            @Override
            public int stage(int x, int y, int z, char ch) {
                return EcoDomeShape.stage(x, y, z, ch);
            }

            @Override
            public int fitRank(int x, int y, int z, char ch) {
                return EcoDomeShape.fitRank(x, y, z, ch);
            }
        });

    private EcoDomeShape() {}

    /** The Eco Dome's job on a hall site ({@link BuildPlan#module} of {@link #BUILD}). */
    public static BuildPlan.Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        return BuildPlan.module(plan, site, cx, y0, cz, fx, fz, BUILD);
    }

    /** Where the Eco Dome's controller stands on a hall site: {x, y, z, facing ordinal}. */
    public static int[] controller(int site, int cx, int y0, int cz, int fx, int fz) {
        return BuildPlan.hallController(BUILD, site, cx, y0, cz, fx, fz);
    }

    /** The character of a cell: the checked legend, a soft character, {@code -} or a space (anything). */
    public static char cell(int x, int y, int z) {
        return SHAPE.get(x, y, z);
    }

    /** The blueprint cell of a point of the shape: {across, layer from the top, row from the front}. */
    public static int[] cellOf(int x, int y, int z) {
        return new int[] { x, HEIGHT - 1 - y, z };
    }

    /** The canopy over the doors, {x, y, z, part code}: two rows of ring segments at y5 (z -1 and -2, x 10..16). */
    public static List<int[]> decor() {
        List<int[]> out = new ArrayList<>();
        for (int[] d : SHAPE.decor) out.add(new int[] { d[0], d[1], d[2], ShapeKit.partOf((char) d[3]) });
        return out;
    }

    /** Whether the column (x, z) is part of the building's footprint plan (the dome's disc and the portal block). */
    public static boolean inPlan(int x, int z) {
        return PLAN.contains(ShapeKit.key(x, z));
    }

    /** Whether the column is on the edge of the plan: the walls of the portal block and the drum. */
    public static boolean onEdge(int x, int z) {
        return EDGE.contains(ShapeKit.key(x, z));
    }

    /**
     * Whether a column belongs to the portal block rather than the drum and dome (on the edge: the block's walls;
     * inside:
     * under its flat roof).
     */
    public static boolean blockWall(int x, int z) {
        long k = ShapeKit.key(x, z);
        return BLOCK.contains(k) && !(DOME_PLAN.contains(k) && z > 9);
    }

    /** Whether a cell is part of the gate niche (soft wall, CITY.md §4.15). */
    public static boolean inNiche(int x, int y, int z) {
        return z == NICHE_Z && x >= NICHE_X0 && x <= NICHE_X1 && y >= NICHE_Y0 && y <= NICHE_Y1;
    }

    /** Whether a cell is a step of the spiral round the trunk. */
    public static boolean trunkSpiral(int x, int y, int z) {
        return TRUNK_SPIRAL.contains(ShapeKit.Shape.pos(x, y, z));
    }

    /** Whether a cell is a step (or the post) of one of the two vestibule stairs. */
    public static boolean vestibuleStair(int x, int y, int z) {
        if (z >= STAIR_Z - 1 && z <= STAIR_Z + 1 && y >= 1 && y <= DECK_Y) {
            for (int px : STAIR_POSTS) if (Math.abs(x - px) <= 1) {
                char ch = cell(x, y, z);
                if (x == px && z == STAIR_Z) return ch == 'q';
                return ch == 'W' || ch == 't';
            }
        }
        return false;
    }

    /** Whether a cell is part of the crown island (deck y11 and its rail at y12, round the trunk). */
    public static boolean crownIsland(int x, int y, int z) {
        return (y == CROWN_Y || y == CROWN_Y + 1) && ShapeKit.octo(x - TRUNK_X, z - TRUNK_Z, 3, 4)
            && "B,r".indexOf(cell(x, y, z)) >= 0;
    }

    /**
     * The build stage of a cell that is not part of a planter (CITY.md §4.1): FLOOR the soft floor of y0; SHELL the
     * foundations, the walls up to the drum's cornice (y1..7), the door air, the trumeau and the lintel; DECK the y6
     * floors (gallery, bridges, the floating island), its rails and the vestibule stairs; UPPER the portal block above
     * the deck (y8..12); ROOF the dome over y8; FIT the trunk and its seat, the crown island and its spiral, the pond's
     * rim and water, the pedestals and the cladding anchors; COMMISSION the controller.
     */
    public static int stage(int x, int y, int z, char ch) {
        if (ch == ShapeKit.CONTROLLER) return BuildPlan.M_COMMISSION;
        if (ch == ShapeKit.AIR) return BuildPlan.M_SHELL;
        if (y == 0) return ShapeKit.soft(ch) ? BuildPlan.M_FLOOR : BuildPlan.M_SHELL;
        if (ch == 'C' || ch == 'Z' || ch == 'h' || ch == 'A' || ch == 'u' || ch == '=') return BuildPlan.M_FIT;
        if (trunkSpiral(x, y, z) || crownIsland(x, y, z)) return BuildPlan.M_FIT;
        if (vestibuleStair(x, y, z)) return BuildPlan.M_DECK;
        if (onEdge(x, z) && y <= 7) return BuildPlan.M_SHELL;
        if (y == DECK_Y || y == DECK_Y + 1 && ch == 'r') return BuildPlan.M_DECK;
        // above the deck: the portal block (its walls, cornice and flat roof) is UPPER, the dome is ROOF
        if (y >= 8) return blockWall(x, z) && y <= 12 ? BuildPlan.M_UPPER : BuildPlan.M_ROOF;
        throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
    }

    /**
     * The order within the FIT stage of a cell that is not part of a planter: the trunk and its seat, the crown
     * island and its spiral, then (after the planters) the pond's rim, its water, the pedestals and the anchors.
     */
    public static int fitRank(int x, int y, int z, char ch) {
        if (ch == 'C' || ch == 'Z') return -3;
        if (crownIsland(x, y, z)) return -2;
        if (trunkSpiral(x, y, z)) return -1;
        if (ch == 'u') return 1;
        if (ch == '=') return 2;
        if (ch == 'h') return 3;
        return 4;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the blueprint and the generator (a port of b_hall.py's eco_dome)

    /** The checked layers of a shape {@code height} high, for {@link Blueprint}: soft cells become spaces. */
    static Blueprint blueprint(ShapeKit.Shape s, int height) {
        String[][] layers = new String[height][s.D];
        for (int y = 0; y < height; y++) for (int z = 0; z < s.D; z++) {
            StringBuilder row = new StringBuilder(s.W);
            for (int x = 0; x < s.W; x++) {
                char ch = s.get(x, y, z);
                row.append(ShapeKit.soft(ch) ? ' ' : ch);
            }
            layers[height - 1 - y][z] = row.toString();
        }
        return new Blueprint(layers);
    }

    private static ShapeKit.Shape build() {
        final int W = WIDTH, D = DEPTH, H = GEN_HEIGHT, cz = TRUNK_Z, yc = DOME_Y;
        final double R0 = DOME_R;
        ShapeKit.Shape s = new ShapeKit.Shape(KEY, W, H, D);
        Set<Long> domePlan = new LinkedHashSet<>(), block = new LinkedHashSet<>();
        for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            if (ShapeKit.hypot(x - MID, z - cz) <= R0) domePlan.add(ShapeKit.key(x, z));
        }
        for (int x = 2; x < 25; x++) for (int z = 0; z < 12; z++) block.add(ShapeKit.key(x, z));
        Set<Long> plan = new LinkedHashSet<>(domePlan);
        plan.addAll(block);
        Set<Long> edge = ShapeKit.boundary(plan);
        Set<Long> inner = new LinkedHashSet<>(plan);
        inner.removeAll(edge);

        for (long k : plan) s.set(ShapeKit.keyX(k), 0, ShapeKit.keyZ(k), edge.contains(k) ? 'F' : '.');
        // walls: the portal block y1..y11 (cornice y12), the drum y1..y6 (cornice y7)
        for (long k : edge) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            boolean blk = block.contains(k) && !(domePlan.contains(k) && z > 9);
            int top = blk ? 11 : 6;
            int dx = Math.abs(x - MID);
            for (int y = 1; y <= top; y++) {
                char ch = 'W';
                if (y == 1) ch = 'w';
                else if (!blk) {
                    double ang = ShapeKit.angle(x - MID, z - cz);
                    ch = ShapeKit.offOctant(ang) < 4 ? 'L' : (2 <= y && y <= 5 ? 'G' : 'W');
                } else {
                    if (z == 0 && dx % 4 == 0 || (x == 2 || x == 24) && z % 4 == 0) ch = 'L';
                    else if (z == 0 && 2 <= y && y <= 4 && 7 <= dx && dx <= 9) ch = 'G';
                    else if (z == 0 && 8 <= y && y <= 10 && dx % 4 == 2) ch = 'G';
                    else if ((x == 2 || x == 24) && 8 <= y && y <= 10 && z % 4 == 2) ch = 'G';
                }
                s.set(x, y, z, ch);
            }
            s.set(x, blk ? 12 : 7, z, 'n');
        }
        for (long k : inner) {
            int z = ShapeKit.keyZ(k);
            if (block.contains(k) && z <= 9 || block.contains(k) && !domePlan.contains(k))
                s.set(ShapeKit.keyX(k), 12, z, 'W');
        }
        // the dome: the ball's cells with a face outside it, above the cornice
        for (int y = 8; y < H; y++) for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            if (!ShapeKit.inBall(x, y, z, MID, yc, cz, R0) || block.contains(ShapeKit.key(x, z)) && z <= 9) continue;
            if (!ShapeKit.ballShell(x, y, z, MID, yc, cz, R0)) continue;
            double rr = ShapeKit.hypot(x - MID, z - cz);
            double ang = ShapeKit.angle(x - MID, z - cz);
            boolean rib = ShapeKit.offOctant(ang) * rr * Math.PI / 180 < 0.75;
            char ch = rib || y == 12 || y == 16 ? 'L' : 'G';
            if (y >= 19) ch = 0 < rr && rr < 2.5 ? 'G' : 'L';
            s.set(x, y, z, ch);
        }
        ShapeKit.portal(s, 0, 5, true, MID);
        // two spiral stairs (posts (5, 3) and (21, 3)), twelve steps from stand 1 to stand 7, mirrored
        for (int i = 0; i < STAIR_POSTS.length; i++) {
            int cx = STAIR_POSTS[i], mirror = i == 0 ? 1 : -1;
            for (int y = 1; y < 7; y++) s.set(cx, y, STAIR_Z, 'q');
            for (int[] st : ShapeKit.spiral(cx, STAIR_Z, 1, 12, 0, mirror)) {
                s.set(st[0], st[1], st[2], (char) st[3]);
                s.asym.add(ShapeKit.Shape.pos(st[0], st[1], st[2]));
            }
        }
        // the portal block's upper floor (deck y6) round the wells and the entrance void (x 9..17, z <= 8)
        Set<Long> gallery = new LinkedHashSet<>();
        for (long k : inner) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            boolean well = false;
            for (int c : STAIR_POSTS) if (Math.abs(x - c) <= 1 && Math.abs(z - STAIR_Z) <= 1) well = true;
            if (block.contains(k) && z <= 11 && !well && !(9 <= x && x <= 17 && z <= 8)) gallery.add(k);
        }
        // the dome gallery ring, 3 wide, d 9.5..12.5
        Set<Long> ring = new LinkedHashSet<>();
        for (long k : inner) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            double d = ShapeKit.hypot(x - MID, z - cz);
            if (z > 8 && 9.5 <= d && d < 12.5) ring.add(k);
        }
        gallery.addAll(ring);
        // bridges to the central island along z 20..22
        Set<Long> bridges = new LinkedHashSet<>();
        for (int x = 3; x < 24; x++) {
            if (x >= 10 && x < 17) continue;
            for (int z = 20; z < 23; z++) bridges.add(ShapeKit.key(x, z));
        }
        Set<Long> deck = new LinkedHashSet<>(gallery);
        deck.addAll(bridges);
        for (long k : deck) s.set(ShapeKit.keyX(k), 6, ShapeKit.keyZ(k), 'd');
        for (long k : ring) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            if (ShapeKit.hypot(x - MID, z - cz) < 10.5 && !bridges.contains(k)) {
                s.set(x, 6, z, 'e');
                s.set(x, 7, z, 'r');
            }
        }
        // rails on the entrance void's edge, and along the bridges
        for (long k : gallery) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            if ((x == 8 || x == 18) && z <= 8 || z == 9 && 9 <= x && x <= 17) s.set(x, 7, z, 'r');
        }
        for (int x = 3; x < 24; x++) {
            if (x >= 9 && x < 18) continue;
            for (int z : new int[] { 19, 23 }) {
                if (s.get(x, 6, z) == ' ') s.set(x, 6, z, 'e');
                s.set(x, 7, z, 'r');
            }
        }
        // the floating island oct(4, 5) round the trunk: a base rim with a rail (open to the bridges), grass on top
        for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            if (!ShapeKit.octo(x - MID, z - cz, 4, 5)) continue;
            boolean edgeI = !ShapeKit.octo(x - MID, z - cz, 3, 4);
            s.set(x, 6, z, edgeI ? 'B' : ',');
            if (edgeI && !(20 <= z && z <= 22)) s.set(x, 7, z, 'r');
            else if (s.get(x, 7, z) == 'r') s.clear(x, 7, z);
        }
        // the world tree: a conduit trunk y1..y16 and a seat at y17 (both dissolve into the rendered tree)
        for (int y = 1; y < 17; y++) s.set(MID, y, cz, 'C');
        s.set(MID, 17, cz, 'Z');
        // the crown island (deck y11) oct(3, 4) round the trunk; a spiral round the trunk from stand 7 to stand 12
        List<int[]> sp = ShapeKit.spiral(MID, cz, 7, 10, 0, 1);
        Map<Long, Double> tops = new HashMap<>();
        for (int[] st : sp) {
            long k = ShapeKit.key(st[0], st[2]);
            double top = st[1] + (st[3] == 't' ? 0.5 : 1.0);
            tops.put(k, Math.max(tops.getOrDefault(k, 0.0), top));
        }
        for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            if (!ShapeKit.octo(x - MID, z - cz, 3, 4) || x == MID && z == cz) continue;
            Double top = tops.get(ShapeKit.key(x, z));
            // the stair opening: headroom over the last steps. A step whose top is t is crossed at t + 0.5 (the body
            // rises onto the next step while still over this one), so the deck must clear t + 0.5 + 1.8; 0.11.0 to
            // 0.14.0 opened only over tops above 9.2 and left the grass at (12, 11, 22) over step 3 in the head's way
            if (top != null && 8.7 < top && top < 12) continue;
            boolean edgeC = !ShapeKit.octo(x - MID, z - cz, 2, 3);
            s.set(x, 11, z, edgeC ? 'B' : ',');
            if (edgeC) s.set(x, 12, z, 'r');
        }
        for (int[] st : sp) {
            s.set(st[0], st[1], st[2], (char) st[3]);
            s.asym.add(ShapeKit.Shape.pos(st[0], st[1], st[2]));
        }
        // the ground: the pond round the trunk (water d 1.5..3.6 at y1 inside a basin rim), then the planters
        for (int x = 0; x < W; x++) for (int z = 12; z < D; z++) {
            if (!inner.contains(ShapeKit.key(x, z))) continue;
            double d = ShapeKit.hypot(x - MID, z - cz);
            if (1.5 <= d && d < 3.6) s.set(x, 1, z, '=');
            else if (3.6 <= d && d < 4.6) s.set(x, 1, z, 'u');
        }
        List<Object[]> units = new ArrayList<>();
        for (int sx = 1; sx >= -1; sx -= 2) {
            for (int sz = 1; sz >= -1; sz -= 2) {
                int cxp = MID + sx * 6, czp = cz + sz * 6;
                planter(
                    s,
                    edge,
                    units,
                    "G-" + (sx > 0 ? "R" : "L") + (sz > 0 ? "B" : "F"),
                    row(cxp, 1, czp - 1, false));
            }
            planter(s, edge, units, "G-" + (sx > 0 ? "R" : "L"), row(MID + sx * 9, 1, cz - 1, false));
        }
        planter(s, edge, units, "G-B", row(MID - 1, 1, cz + 9, true));
        planter(s, edge, units, "I-F", row(MID - 1, 7, cz - 3, true));
        planter(s, edge, units, "I-B", row(MID - 1, 7, cz + 3, true));
        for (int sx = 1; sx >= -1; sx -= 2) {
            planter(s, edge, units, "U-" + (sx > 0 ? "R" : "L"), row(MID + sx * 10, 7, cz + 4, false));
        }
        planter(s, edge, units, "C", row(MID - 1, 12, cz + 2, true));
        // open soil for the player's own hives and crops: the ground ring d 5..10 off the axes
        for (long k : inner) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            if (z < 12) continue;
            double d = ShapeKit.hypot(x - MID, z - cz);
            if (5.0 <= d && d < 10.0 && Math.abs(x - MID) > 1 && Math.abs(z - cz) > 1 && s.get(x, 1, z) == ' ')
                s.set(x, 0, z, ',');
        }
        for (int x : new int[] { 9, 17 }) s.set(x, 1, 6, 'h');
        // cladding anchors: the drum's lit ribs at y5
        for (long k : edge) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            boolean blk = block.contains(k) && !(domePlan.contains(k) && z > 9);
            if (!blk && s.get(x, 5, z) == 'L') s.set(x, 5, z, 'A');
        }
        // the gate niche in the back drum wall's middle: the same blocks (the dark course, the glass and the back
        // rib), soft, so the 内境 gate can open it later without a new blueprint
        s.gateNiche(MID - 1, MID + 1, 1, 4, D - 1);
        for (Object[] u : units) {
            @SuppressWarnings("unchecked")
            List<int[]> cells = (List<int[]>) u[1];
            s.unit((String) u[0], "planter", cells, 3, null);
        }
        return s;
    }

    /** Three cells in a row from {@code (x, y, z)}, along x or along z. */
    private static List<int[]> row(int x, int y, int z, boolean alongX) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) out.add(alongX ? new int[] { x + i, y, z } : new int[] { x, y, z + i });
        return out;
    }

    /**
     * A planter: its sockets, and a basin rim round them (the bounding box one cell out); both are the unit. A rim cell
     * on the plan's edge stays the wall's (the drum's cornice, where the two gallery planters reach it), so the band
     * has no slit under the glass: those two planters are thirteen cells.
     */
    private static void planter(ShapeKit.Shape s, Set<Long> edge, List<Object[]> units, String name,
        List<int[]> cells) {
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (int[] c : cells) {
            x0 = Math.min(x0, c[0]);
            x1 = Math.max(x1, c[0]);
            z0 = Math.min(z0, c[2]);
            z1 = Math.max(z1, c[2]);
        }
        int y = cells.get(0)[1];
        List<int[]> rim = new ArrayList<>();
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
            boolean own = false;
            for (int[] c : cells) if (c[0] == x && c[1] == y && c[2] == z) own = true;
            if (!own && !edge.contains(ShapeKit.key(x, z))) rim.add(new int[] { x, y, z });
        }
        for (int[] c : cells) s.set(c[0], c[1], c[2], 'o');
        for (int[] c : rim) s.set(c[0], c[1], c[2], 'u');
        List<int[]> all = new ArrayList<>(cells);
        all.addAll(rim);
        units.add(new Object[] { name, all });
    }

    /** The counts of the shape by character, for the tests. */
    static Map<Character, Integer> counts() {
        return SHAPE.counts();
    }

    /** Every layer as rows of characters, bottom up, for the checksum test. */
    static List<String> rows() {
        List<String> out = new ArrayList<>();
        for (int y = 0; y < GEN_HEIGHT; y++) for (int z = 0; z < DEPTH; z++) {
            char[] r = new char[WIDTH];
            for (int x = 0; x < WIDTH; x++) r[x] = cell(x, y, z);
            out.add(new String(r));
        }
        return out;
    }

    /** The standing states {x, z, h} the generator's self-check walks from: outside the doors. */
    public static List<int[]> walkStarts() {
        return Arrays.asList(new int[] { MID - 2, -1, 2 }, new int[] { MID + 2, -1, 2 });
    }
}
