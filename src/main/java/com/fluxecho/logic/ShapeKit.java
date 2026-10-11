package com.fluxecho.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The kit the City of Echoes' building shapes are drawn with (0.11.0): a port of the scratch generators' shared
 * {@code city/gen/kit.py} and of the helpers the hall generator ({@code b_hall.py}) uses, so every building's
 * {@code XShape} can port its generator line by line, the way {@link ArchiveShape} ported {@code archive.py}.
 * <p>
 * A {@link Shape} is a box of characters in the local frame every module uses: {@code x} across {@code 0..W-1}
 * ({@code MID} the middle), {@code y} up ({@code y0} the foundation layer people stand on), {@code z} from the front
 * (the side that faces the nexus). One legend ({@link #partOf}, CITY.md Appendix A) gives every part one character
 * across the whole city, so a shape never needs its own table. A cell is <em>checked</em> (the structure check looks
 * at it), <em>soft</em> ({@link #SOFT}: built but never checked, may be asymmetric), <em>must-be-air</em> ({@code -}),
 * or empty (a space: anything); decor cells lie outside the box and are built unchecked. A building's gate niche
 * (内境门位, CITY.md §4.15) is wall drawn in the soft twins of its parts ({@link Shape#gateNiche}): the same blocks,
 * left out of the check, so a later release can open it for its light gate without changing the checked shape.
 * <p>
 * The self-check every generator prints ({@link #summary}) is part of the kit: the counts, the mirror test across
 * {@code x} (soft cells and the listed asymmetric cells skipped), and a walk from outside the main door in half blocks
 * (steps up at most half a block, drops at most three, light lifts joining their pads) that must reach the controller
 * at eye height and every unit. Python's {@code math.hypot} and {@code atan2} become {@link Math#sqrt} of the exact
 * integer sum and {@link StrictMath#atan2}, which give the generator's cells (the shape tests compare checksums).
 */
public final class ShapeKit {

    /**
     * Soft cells: built, never checked. Deck, grass, water, dirt, lit and trim tiles, ramp blocks, treads, rails, and
     * a gate niche's dark course, lit panel and glass ({@code [ ! /}).
     */
    public static final String SOFT = ".,=;+_{}|[!/";
    /** The wall characters a gate niche keeps, and their soft twins (the same parts), in the same order. */
    static final String NICHE_WALL = "wLGW", NICHE_TWIN = "[!/{";
    /** A body can stand inside these: nothing, must-be-air, water. */
    static final String PASSABLE = " -=";
    /** Lower half slabs: one stands on them at {@code y + 0.5}. */
    static final String HALF = "t}";
    /** Fences (1.5 high): nobody stands on them. */
    static final String NOSTAND = "r|";
    /** The 10/16 basin rim and the 12/16 pedestal: they block walking and are not standable for the check. */
    static final String LOW = "uh";

    /** The controller's character and the must-be-air character. */
    public static final char CONTROLLER = '~', AIR = '-', NONE = ' ';

    /** The eight cells round a spiral's centre post in walking order (the Archive's ring). */
    static final int[][] RING8 = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 },
        { 1, -1 } };

    private ShapeKit() {}

    /** Whether the character is a soft cell (built, never checked). */
    public static boolean soft(char ch) {
        return SOFT.indexOf(ch) >= 0;
    }

    /** Whether the character is a cell the structure check looks at (a part or the controller). */
    public static boolean checked(char ch) {
        return ch != NONE && ch != AIR && !soft(ch);
    }

    /**
     * The checked character that places the same part as a soft one ({@code .} deck, {@code +} lit and {@code _} trim
     * tiles, the ramp's {@code { } |}, a gate niche's {@code [ ! /}); any other character itself (grass, dirt and water
     * have no checked twin). What a block looks like, whether the structure checks it or not.
     */
    public static char checkedTwin(char ch) {
        switch (ch) {
            case '.':
                return 'd';
            case '+':
                return 'e';
            case '_':
                return '#';
            case '{':
                return 'W';
            case '}':
                return 't';
            case '|':
                return 'r';
            case '[':
                return 'w';
            case '!':
                return 'L';
            case '/':
                return 'G';
            default:
                return ch;
        }
    }

    /**
     * The {@link Parts} code a legend character stands for (CITY.md Appendix A): frame, deck and fitting parts, the
     * soft floor parts (deck, lit and trim tiles, grass, dirt, water, a panel as a ramp block, a tread, a rail), a
     * gate niche's soft dark course, lit panel and glass, {@link Parts#AIR} for {@code -}; -1 for the controller (whose
     * part is the module's core) and for a space.
     *
     * @throws IllegalArgumentException for a character the legend does not have
     */
    public static int partOf(char ch) {
        switch (ch) {
            case 'F':
                return Parts.frame(Parts.FR_FOUNDATION);
            case 'B':
                return Parts.frame(Parts.FR_BASE);
            case 'g':
                return Parts.frame(Parts.FR_BASE_LIT);
            case 'P':
                return Parts.frame(Parts.FR_PILLAR);
            case 'C':
                return Parts.frame(Parts.FR_CONDUIT);
            case 'R':
                return Parts.frame(Parts.FR_RING);
            case 'Z':
                return Parts.frame(Parts.FR_SEAT);
            case 'K':
                return Parts.frame(Parts.FR_CONSOLE);
            case 'A':
                return Parts.frame(Parts.FR_ANCHOR);
            case 'd':
            case '.':
                return Parts.deck(Parts.D_DECK);
            case '#':
            case '_':
                return Parts.deck(Parts.D_TRIM);
            case 'e':
            case '+':
                return Parts.deck(Parts.D_LIT);
            case ':':
                return Parts.deck(Parts.D_GRATE);
            case '%':
                return Parts.deck(Parts.D_DARK);
            case 'k':
                return Parts.deck(Parts.D_SKIRT);
            case '*':
                return Parts.deck(Parts.D_WELL);
            case 'W':
            case '{':
                return Parts.deck(Parts.D_PANEL);
            case 'L':
            case '!':
                return Parts.deck(Parts.D_PANEL_LIT);
            case 'w':
            case '[':
                return Parts.deck(Parts.D_PANEL_DARK);
            case 'n':
                return Parts.deck(Parts.D_CORNICE);
            case 'q':
                return Parts.fitting(Parts.F_POST);
            case 'p':
                return Parts.fitting(Parts.F_PLINTH);
            case 'c':
                return Parts.fitting(Parts.F_CROWN);
            case 'r':
            case '|':
                return Parts.fitting(Parts.F_RAIL);
            case 't':
            case '}':
                return Parts.fitting(Parts.F_TREAD);
            case 'G':
            case '/':
                return Parts.fitting(Parts.F_GLAZE);
            case 'h':
                return Parts.fitting(Parts.F_PEDESTAL);
            case 'o':
                return Parts.fitting(Parts.F_SOCKET);
            case 'u':
                return Parts.fitting(Parts.F_BASIN);
            case '^':
                return Parts.fitting(Parts.F_LIFT);
            case ',':
                return Parts.GRASS;
            case ';':
                return Parts.DIRT;
            case '=':
                return Parts.WATER;
            case AIR:
                return Parts.AIR;
            case CONTROLLER:
            case NONE:
                return -1;
            default:
                throw new IllegalArgumentException("not a city legend character: '" + ch + "'");
        }
    }

    // ---- geometry helpers (kit.py) ----

    /** The octagon test {@code oct(half, k)} about a centre: {@code |du|, |dv| <= half} and {@code |du|+|dv| <= k}. */
    public static boolean octo(int du, int dv, int half, int k) {
        return Math.abs(du) <= half && Math.abs(dv) <= half && Math.abs(du) + Math.abs(dv) <= k;
    }

    /** The distance of a cell offset from a centre, as {@code math.hypot} gives it for whole numbers. */
    public static double hypot(int dx, int dz) {
        return Math.sqrt((double) dx * dx + (double) dz * dz);
    }

    /**
     * The angle of a cell offset seen from a centre in degrees, {@code [0, 360)}, measured from {@code +z} towards
     * {@code +x}: the generators' {@code math.degrees(math.atan2(dx, dz)) % 360}.
     */
    public static double angle(int dx, int dz) {
        double a = Math.toDegrees(StrictMath.atan2(dx, dz)) % 360;
        return a < 0 ? a + 360 : a;
    }

    /** How far an angle in degrees lies from the nearest multiple of 45: {@code min(a % 45, 45 - a % 45)}. */
    public static double offOctant(double angle) {
        double m = angle % 45;
        return Math.min(m, 45 - m);
    }

    /** The cells {x, z} of a disc of radius {@code r} round {@code (cx, cz)}, in the generator's order. */
    public static List<int[]> disc(int cx, int cz, double r) {
        List<int[]> out = new ArrayList<>();
        int rr = (int) r + 1;
        for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++) {
            if (hypot(dx, dz) <= r) out.add(new int[] { cx + dx, cz + dz });
        }
        return out;
    }

    /** The cells {x, z} of a ring {@code rIn <= d < rOut} round {@code (cx, cz)}, in the generator's order. */
    public static List<int[]> ring(int cx, int cz, double rIn, double rOut) {
        List<int[]> out = new ArrayList<>();
        int rr = (int) rOut + 1;
        for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++) {
            double d = hypot(dx, dz);
            if (rIn <= d && d < rOut) out.add(new int[] { cx + dx, cz + dz });
        }
        return out;
    }

    /** One long for a column (x, z), the key of the 2D cell sets. */
    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int keyX(long k) {
        return (int) (k >> 32);
    }

    public static int keyZ(long k) {
        return (int) k;
    }

    /** The cells of a 2D set (column keys) that touch a cell outside it across a side (4 neighbours). */
    public static Set<Long> boundary(Set<Long> cells) {
        Set<Long> out = new LinkedHashSet<>();
        for (long k : cells) {
            int x = keyX(k), z = keyZ(k);
            if (!cells.contains(key(x + 1, z)) || !cells.contains(key(x - 1, z))
                || !cells.contains(key(x, z + 1))
                || !cells.contains(key(x, z - 1))) out.add(k);
        }
        return out;
    }

    /** The boundary that is watertight across diagonal steps: cells with any of their 8 neighbours outside. */
    public static Set<Long> thickBoundary(Set<Long> cells) {
        Set<Long> out = new LinkedHashSet<>();
        for (long k : cells) {
            int x = keyX(k), z = keyZ(k);
            boolean edge = false;
            for (int dx = -1; dx <= 1 && !edge; dx++)
                for (int dz = -1; dz <= 1; dz++) if (!cells.contains(key(x + dx, z + dz))) edge = true;
            if (edge) out.add(k);
        }
        return out;
    }

    /** Whether the cell lies in the ball of radius {@code r} round {@code (cx, cy, cz)}. */
    public static boolean inBall(int x, int y, int z, int cx, int cy, int cz, double r) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= r;
    }

    /** Whether a cell of the ball is on its shell: in the ball, with a side neighbour outside it. */
    public static boolean ballShell(int x, int y, int z, int cx, int cy, int cz, double r) {
        if (!inBall(x, y, z, cx, cy, cz, r)) return false;
        return !(inBall(x + 1, y, z, cx, cy, cz, r) && inBall(x - 1, y, z, cx, cy, cz, r)
            && inBall(x, y + 1, z, cx, cy, cz, r)
            && inBall(x, y - 1, z, cx, cy, cz, r)
            && inBall(x, y, z + 1, cx, cy, cz, r)
            && inBall(x, y, z - 1, cx, cy, cz, r));
    }

    // ---- the hall helpers (b_hall.py) ----

    /**
     * An Archive-style spiral round a centre post: step {@code i} sits on the ring cell {@code (start + i) % 8}
     * ({@code x} mirrored when {@code mirror} is -1), each step half a block higher; {@code yFrom} is the standing
     * height before the first step. A step whose top is a whole block is a panel ({@code W}) one below it, the others
     * are treads ({@code t}). Returns {x, y, z, character} per step, lowest first.
     */
    public static List<int[]> spiral(int cx, int cz, int yFrom, int steps, int start, int mirror) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            int[] d = RING8[(start + i) % RING8.length];
            int dx = d[0] * mirror, dz = d[1];
            // the step's top in half blocks
            int halves = 2 * yFrom + i + 1;
            if (halves % 2 == 0) out.add(new int[] { cx + dx, halves / 2 - 1, cz + dz, 'W' });
            else out.add(new int[] { cx + dx, halves / 2, cz + dz, 't' });
        }
        return out;
    }

    /**
     * The kit portal in the front wall {@code z}: two 3 x 4 doors either side of a trumeau holding the console stand
     * (y1), the controller (y2) and two posts (y3, y4); every door cell must be air two deep; a cornice lintel at
     * {@code yLintel}; with {@code canopy}, a canopy of ring segments two cells out as decor (unchecked).
     */
    public static void portal(Shape s, int z, int yLintel, boolean canopy, int cx) {
        for (int x = cx - 3; x <= cx + 3; x++) for (int y = 1; y <= 4; y++) {
            if (x == cx) continue;
            s.set(x, y, z, AIR);
            s.set(x, y, z + 1, AIR);
        }
        s.set(cx, 1, z, 'K');
        s.set(cx, 2, z, CONTROLLER);
        s.controller = new int[] { cx, 2, z };
        s.set(cx, 3, z, 'q');
        s.set(cx, 4, z, 'q');
        for (int x = cx - 4; x <= cx + 4; x++) s.set(x, yLintel, z, 'n');
        if (canopy) {
            for (int x = cx - 3; x <= cx + 3; x++)
                for (int dz = 1; dz <= 2; dz++) s.decor.add(new int[] { x, yLintel, z - dz, 'R' });
        }
    }

    // ---- shapes ----

    /** A unit of a shape as its generator declares it: a name, a kind, its cells, its slots and its face. */
    public static final class Unit {

        public final String name, kind;
        /** Its cells {x, y, z}, in the order the generator lists them. */
        public final List<int[]> cells;
        public final int slots;
        /** The direction {fx, fz} its face looks, or null. */
        public final int[] face;

        Unit(String name, String kind, List<int[]> cells, int slots, int[] face) {
            this.name = name;
            this.kind = kind;
            List<int[]> c = new ArrayList<>();
            for (int[] p : cells) c.add(new int[] { p[0], p[1], p[2] });
            this.cells = Collections.unmodifiableList(c);
            this.slots = slots;
            this.face = face == null ? null : face.clone();
        }
    }

    /**
     * A shape being drawn, the generators' {@code Shape}: a box of characters (a space is nothing), its units, its
     * controller, the decor outside the box, the light-lift pads, and the cells the mirror test skips (spirals that
     * turn one way).
     */
    public static final class Shape {

        public final String name;
        public final int W, H, D, MID;
        private final char[] g;
        private final List<Unit> units = new ArrayList<>();
        /** The controller cell {x, y, z}, or null. */
        public int[] controller;
        /** Decor outside the box: {x, y, z, character}. */
        public final List<int[]> decor = new ArrayList<>();
        /** Light-lift pads {x, y, z}. */
        public final List<int[]> lifts = new ArrayList<>();
        /** Cells the mirror test skips, as {@link #pos} keys. */
        public final Set<Long> asym = new LinkedHashSet<>();
        /** The gate niche's cells ({@link #gateNiche}), as {@link #pos} keys. */
        public final Set<Long> niche = new LinkedHashSet<>();
        /** Whether the ground round the box counts as a floor at y0 (the paved apron). */
        public boolean groundOutside = true;

        public Shape(String name, int w, int h, int d) {
            this.name = name;
            W = w;
            H = h;
            D = d;
            MID = (w - 1) / 2;
            g = new char[w * h * d];
            java.util.Arrays.fill(g, NONE);
        }

        public boolean inb(int x, int y, int z) {
            return x >= 0 && x < W && y >= 0 && y < H && z >= 0 && z < D;
        }

        private int index(int x, int y, int z) {
            return (y * D + z) * W + x;
        }

        /** Sets a cell (a space empties it). */
        public void set(int x, int y, int z, char ch) {
            if (!inb(x, y, z)) throw new IllegalArgumentException(
                name + ": (" + x + "," + y + "," + z + ") out of " + W + "x" + H + "x" + D);
            g[index(x, y, z)] = ch;
        }

        /** Sets a cell and its mirror across {@code x}. */
        public void setm(int x, int y, int z, char ch) {
            set(x, y, z, ch);
            set(W - 1 - x, y, z, ch);
        }

        public char get(int x, int y, int z) {
            return inb(x, y, z) ? g[index(x, y, z)] : NONE;
        }

        public void clear(int x, int y, int z) {
            if (inb(x, y, z)) g[index(x, y, z)] = NONE;
        }

        /**
         * Declares a unit.
         *
         * @throws IllegalArgumentException when one of its cells is empty
         */
        public void unit(String name, String kind, List<int[]> cells, int slots, int[] face) {
            for (int[] c : cells) {
                if (get(c[0], c[1], c[2]) == NONE) throw new IllegalArgumentException(
                    this.name + " unit " + name + " cell " + c[0] + "," + c[1] + "," + c[2] + " is empty");
            }
            units.add(new Unit(name, kind, cells, slots, face));
        }

        /** The units in the order they were declared (the slot order). */
        public List<Unit> units() {
            return Collections.unmodifiableList(units);
        }

        /** Places a light-lift pad. */
        public void lift(int x, int y, int z) {
            set(x, y, z, '^');
            lifts.add(new int[] { x, y, z });
        }

        /**
         * Reserves the gate niche (内境门位, CITY.md §4.15) {@code x0..x1} by {@code y0..y1} in the wall at {@code z}:
         * every cell keeps its block as the soft twin of its wall character ({@code w L G W} become {@code [ ! / {}),
         * so it is built but never checked, and a later release can open the niche without changing the checked shape.
         *
         * @throws IllegalArgumentException when a cell is not one of those wall characters
         */
        public void gateNiche(int x0, int x1, int y0, int y1, int z) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) {
                char ch = get(x, y, z);
                int i = NICHE_WALL.indexOf(ch);
                if (ch == NONE || i < 0) throw new IllegalArgumentException(
                    name + ": gate niche cell " + x + "," + y + "," + z + " is '" + ch + "', not a wall");
                set(x, y, z, NICHE_TWIN.charAt(i));
                niche.add(pos(x, y, z));
            }
        }

        /** A cell as one long, for the asymmetric set. */
        public static long pos(int x, int y, int z) {
            return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
        }

        // ---- facts ----

        /** How many cells hold each character (spaces not counted). */
        public Map<Character, Integer> counts() {
            Map<Character, Integer> out = new TreeMap<>();
            for (char ch : g) if (ch != NONE) out.merge(ch, 1, Integer::sum);
            return out;
        }

        /** Checked cells: everything but spaces, soft cells and must-be-air. */
        public int checkedCount() {
            int n = 0;
            for (char ch : g) if (checked(ch)) n++;
            return n;
        }

        public int softCount() {
            int n = 0;
            for (char ch : g) if (soft(ch)) n++;
            return n;
        }

        public int airCount() {
            int n = 0;
            for (char ch : g) if (ch == AIR) n++;
            return n;
        }

        /** Steps a build of it takes: checked, soft, air and decor cells. */
        public int steps() {
            return checkedCount() + softCount() + airCount() + decor.size();
        }

        /**
         * The cells that break the mirror across {@code x}, {x, y, z, ch, mirrored ch}: soft cells are not part of the
         * checked blueprint, and the cells of {@link #asym} (and their mirrors) are skipped.
         */
        public List<int[]> symmetric() {
            List<int[]> bad = new ArrayList<>();
            for (int y = 0; y < H; y++) for (int z = 0; z < D; z++) for (int x = 0; x < W; x++) {
                char ch = get(x, y, z);
                if (ch == NONE) continue;
                char m = get(W - 1 - x, y, z);
                if (soft(ch) || soft(m)) continue;
                if (m != ch && !asym.contains(pos(x, y, z)) && !asym.contains(pos(W - 1 - x, y, z)))
                    bad.add(new int[] { x, y, z, ch, m });
            }
            return bad;
        }

        boolean solid(int x, int y, int z) {
            if (!inb(x, y, z)) return y <= 0 && groundOutside;
            char ch = get(x, y, z);
            if (y == 0 && ch == NONE && groundOutside) return true;
            return PASSABLE.indexOf(ch) < 0 && HALF.indexOf(ch) < 0;
        }

        boolean passable(int x, int y, int z) {
            if (!inb(x, y, z)) return y >= 1 || !groundOutside;
            if (y == 0 && get(x, y, z) == NONE && groundOutside) return false;
            return PASSABLE.indexOf(get(x, y, z)) >= 0;
        }

        /** The standing heights (in half blocks) the column offers, lowest first. */
        public List<Integer> floors(int x, int z) {
            List<Integer> out = new ArrayList<>();
            int hi = H + 2;
            for (int y = 0; y < hi; y++) {
                if (inb(x, y, z) && HALF.indexOf(get(x, y, z)) >= 0) {
                    if (passable(x, y + 1, z) && passable(x, y + 2, z)) out.add(2 * y + 1);
                    continue;
                }
                if (passable(x, y, z) && passable(x, y + 1, z)) {
                    int by = y - 1;
                    boolean in = inb(x, by, z);
                    char below = get(x, by, z);
                    boolean ok = solid(x, by, z) && (!in || LOW.indexOf(below) < 0 && NOSTAND.indexOf(below) < 0);
                    if (by < 0) ok = false;
                    if (in && below == '=') ok = false;
                    if (ok) out.add(2 * y);
                }
            }
            return out;
        }

        /**
         * Breadth-first over the standing states {x, z, h} (h in half blocks) from the starts: a step up of at most
         * half a block, a drop of at most three, the head clear over the crossing; a light-lift pad joins every pad of
         * its column. Columns up to {@code margin} outside the box count. Returns the states reached, as
         * {@link #state} keys.
         */
        public Set<Long> walk(List<int[]> starts, int margin) {
            Set<Long> seen = new HashSet<>();
            ArrayDeque<int[]> q = new ArrayDeque<>();
            for (int[] s : starts) {
                if (seen.add(state(s[0], s[1], s[2]))) q.add(s.clone());
            }
            Map<Long, List<Integer>> fl = new HashMap<>();
            Map<Long, List<Integer>> liftCols = new HashMap<>();
            for (int[] l : lifts) liftCols.computeIfAbsent(key(l[0], l[2]), k -> new ArrayList<>())
                .add(2 * (l[1] + 1));
            int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
            while (!q.isEmpty()) {
                int[] cur = q.poll();
                int x = cur[0], z = cur[1], h = cur[2];
                for (int[] d : dirs) {
                    int nx = x + d[0], nz = z + d[1];
                    if (nx < -margin || nx >= W + margin || nz < -margin || nz >= D + margin) continue;
                    List<Integer> f = fl.computeIfAbsent(key(nx, nz), k -> floors(nx, nz));
                    for (int h2 : f) {
                        if (h2 - h > 1 || h - h2 > 6) continue;
                        // the head must clear the higher of the two columns at the crossing
                        int top = Math.max(h, h2);
                        boolean ok = true;
                        for (int yy = top / 2; yy < (top + 4) / 2; yy++) {
                            if (!(passable(x, yy, z) || yy * 2 < h) || !(passable(nx, yy, nz) || yy * 2 < h2))
                                ok = false;
                        }
                        if (ok && seen.add(state(nx, nz, h2))) q.add(new int[] { nx, nz, h2 });
                    }
                }
                List<Integer> pads = liftCols.get(key(x, z));
                if (pads != null && pads.contains(h)) {
                    for (int h2 : pads) if (seen.add(state(x, z, h2))) q.add(new int[] { x, z, h2 });
                }
            }
            return seen;
        }

        /** A standing state as one long. */
        public static long state(int x, int z, int h) {
            return ((long) (x + 512) << 40) | ((long) (z + 512) << 20) | (h + 512);
        }

        public static int stateX(long s) {
            return (int) (s >>> 40) - 512;
        }

        public static int stateZ(long s) {
            return (int) ((s >>> 20) & 0xFFFFF) - 512;
        }

        public static int stateH(long s) {
            return (int) (s & 0xFFFFF) - 512;
        }

        /** The names of the units no reached standing state can use (empty when every unit is reachable). */
        public List<String> unitReach(Set<Long> reached) {
            Map<Long, List<Integer>> cols = columns(reached);
            List<String> missing = new ArrayList<>();
            int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
            for (Unit u : units) {
                boolean ok = false;
                if (u.face == null) {
                    for (int[] c : u.cells) for (int[] d : dirs) {
                        for (int h : cols.getOrDefault(key(c[0] + d[0], c[2] + d[1]), Collections.emptyList())) {
                            // reach: the cell lies within 0..3 blocks above the feet
                            if (h / 2.0 - 1 <= c[1] && c[1] <= h / 2.0 + 3) ok = true;
                        }
                    }
                } else {
                    Set<Long> own = new HashSet<>();
                    int ylo = Integer.MAX_VALUE;
                    for (int[] c : u.cells) {
                        own.add(pos(c[0], c[1], c[2]));
                        ylo = Math.min(ylo, c[1]);
                    }
                    Set<Long> fronts = new LinkedHashSet<>();
                    for (int[] c : u.cells) {
                        int nx = c[0] + u.face[0], nz = c[2] + u.face[1];
                        if (own.contains(pos(nx, c[1], nz))) continue;
                        fronts.add(key(nx, nz));
                    }
                    for (long f : fronts) for (int h : cols.getOrDefault(f, Collections.emptyList())) {
                        if (h / 2.0 - 1 <= ylo && ylo <= h / 2.0 + 3) ok = true;
                    }
                }
                if (!ok) missing.add(u.name);
            }
            return missing;
        }

        /** Whether a reached standing state beside the controller sees it at eye height. */
        public boolean controllerReach(Set<Long> reached) {
            if (controller == null) return false;
            int x = controller[0], y = controller[1], z = controller[2];
            int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
            for (long s : reached) {
                int rx = stateX(s), rz = stateZ(s), h = stateH(s);
                for (int[] d : dirs) {
                    if (rx == x + d[0] && rz == z + d[1] && Math.abs(h / 2.0 + 1.62 - (y + 0.5)) <= 0.7) return true;
                }
            }
            return false;
        }

        private static Map<Long, List<Integer>> columns(Set<Long> reached) {
            Map<Long, List<Integer>> cols = new HashMap<>();
            for (long s : reached) cols.computeIfAbsent(key(stateX(s), stateZ(s)), k -> new ArrayList<>())
                .add(stateH(s));
            return cols;
        }

        // ---- printing ----

        /** The layer {@code y} as rows from the front, for reading a shape in a test failure. */
        public String plan(int y) {
            StringBuilder b = new StringBuilder();
            for (int z = 0; z < D; z++) {
                for (int x = 0; x < W; x++) b.append(get(x, y, z));
                b.append('\n');
            }
            return b.toString();
        }
    }

    /** What a generator's self-check prints. */
    public static final class Summary {

        public final int checked, soft, air, decor, steps, units, slots, reached;
        public final boolean symmetric, controller;
        public final List<String> unreachable;
        public final Map<Character, Integer> counts;

        Summary(Shape s, List<int[]> starts) {
            counts = Collections.unmodifiableMap(new LinkedHashMap<>(s.counts()));
            checked = s.checkedCount();
            soft = s.softCount();
            air = s.airCount();
            decor = s.decor.size();
            steps = s.steps();
            units = s.units.size();
            int n = 0;
            for (Unit u : s.units) n += u.slots;
            slots = n;
            symmetric = s.symmetric()
                .isEmpty();
            if (starts != null) {
                Set<Long> r = s.walk(starts, 2);
                reached = r.size();
                controller = s.controllerReach(r);
                unreachable = Collections.unmodifiableList(s.unitReach(r));
            } else {
                reached = 0;
                controller = false;
                unreachable = Collections.emptyList();
            }
        }
    }

    /**
     * The generator's {@code summary}: counts, symmetry and, from the standing states {x, z, h} given (outside the main
     * door), the walk: how many states it reaches, whether it reaches the controller at eye height, and which units
     * it cannot.
     */
    public static Summary summary(Shape s, List<int[]> starts) {
        return new Summary(s, starts);
    }
}
