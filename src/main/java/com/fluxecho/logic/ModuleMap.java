package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The floor plans of a city module as its window draws them (0.11.0; the Echo Archive's {@code ArchiveMap} made
 * general): one plan a storey, the module's box seen from above with the front (its doors) at the bottom and the
 * across axis running to the right of someone walking in, {@link #cell} pixels a block and a one-pixel margin round it.
 * The storeys are the floors its units stand on ({@link UnitLayout#storeys()}); a storey's plan shows what stands at
 * that height over the floor below it ({@link Mark}), and every unit of the storey as its cells.
 * <p>
 * It says what each block of a storey's plan is, where each unit's cells lie on its storey's plan, and which unit a
 * point of a plan is nearest, so what is drawn and what a click picks always agree. Pure arithmetic over a module's
 * {@link CityBuild} and {@link UnitLayout}, free of client classes so the common window code can name it; worked out
 * once per module kind.
 */
public final class ModuleMap {

    /** What one block of a storey's plan shows. */
    public enum Mark {
        /** Nothing to stand on: outside the building, or open to the storey below. */
        NONE,
        /** A floor (deck, a frame top, a soft floor). */
        FLOOR,
        /** A lit floor strip (a gallery's edge). */
        LIT,
        /** Grass or soil. */
        GRASS,
        /** Water. */
        WATER,
        /** A wall, a cornice or another solid part standing at the storey's height. */
        WALL,
        /** A wall block whose four neighbours are solid too: the inside of a mass (a roof seen from its storey). */
        SOLID,
        /** Glazing. */
        GLAZE,
        /** A lit pilaster or rib. */
        PILASTER,
        /** A doorway (must-be-air cells). */
        DOOR,
        /** A gallery rail. */
        RAIL,
        /** A post. */
        POST,
        /** A stair tread. */
        STAIR,
        /** A pedestal, a basin rim or a socket that is no unit's: furniture. */
        FIXTURE,
        /** A console stand. */
        CONSOLE,
        /** The controller. */
        CORE,
        /** A part that gives way to the module's drawing once it forms (the Eco Dome's trunk). */
        TRUNK,
        /** A light-lift pad (0.12.0): where the lift comes in. */
        LIFT
    }

    /** The most pixels a block takes on a plan. */
    public static final int MAX_CELL = 3;
    /** How far from a unit's cell, in pixels, a click still picks the unit. */
    public static final double REACH = 3;

    /** The module's box across and deep, in blocks. */
    public final int width, depth;
    /** Pixels a block, and a plan's size in pixels (the margin included). */
    public final int cell, w, h;

    private final UnitLayout layout;
    private final int[] storeys;
    /** The plan of each storey, {@code [storey][x * depth + z]}. */
    private final Mark[][] marks;
    /** The tiles of each unit's cells on its storey's plan, {x0, y0, x1, y1} with the far edges exclusive. */
    private final List<List<int[]>> tiles;

    /**
     * The plans of a module whose plans may take at most {@code maxW} by {@code maxH} pixels; a block takes
     * {@link #MAX_CELL} pixels where that fits, fewer for a large module, at least one.
     *
     * @param dissolves the characters that give way once the module forms (drawn as {@link Mark#TRUNK})
     */
    public ModuleMap(CityBuild build, UnitLayout layout, String dissolves, int maxW, int maxH) {
        this.layout = layout;
        width = build.width();
        depth = build.depth();
        cell = Math.max(1, Math.min(MAX_CELL, Math.min((maxW - 2) / width, (maxH - 2) / depth)));
        w = width * cell + 2;
        h = depth * cell + 2;
        storeys = layout.storeys();
        marks = new Mark[storeys.length][];
        for (int s = 0; s < storeys.length; s++)
            marks[s] = plan(build, storeys[s] + (flush(build, layout, storeys[s]) ? 1 : 0), dissolves);
        List<List<int[]>> t = new ArrayList<>();
        for (UnitLayout.Unit u : layout.units()) {
            List<int[]> own = new ArrayList<>();
            for (int[] c : u.cells) if (c[1] == u.floor) own.add(tile(c[0], c[2]));
            t.add(Collections.unmodifiableList(own));
        }
        tiles = Collections.unmodifiableList(t);
    }

    // ---- the frame of a plan

    /** The plan's x of a point of the module (for a whole number, the left edge of that block). */
    public double px(double x) {
        return 1 + x * cell;
    }

    /** The plan's y of a point of the module (for a whole number, the lower edge of that block on the plan). */
    public double py(double z) {
        return 1 + (depth - z) * cell;
    }

    /** A block's tile on a plan, {x0, y0, x1, y1} in pixels (the far edges exclusive). */
    public int[] tile(int x, int z) {
        return new int[] { (int) px(x), (int) py(z + 1), (int) px(x + 1), (int) py(z) };
    }

    // ---- storeys

    /** How many storeys (plans) there are. */
    public int storeys() {
        return storeys.length;
    }

    /** The height the units of a storey stand at (the y of their cells in the module's frame). */
    public int level(int storey) {
        return storeys[storey];
    }

    /** The storey a unit stands on, or -1 outside the layout. */
    public int storeyOf(int unit) {
        return unit < 0 || unit >= layout.size() ? -1 : layout.storey(unit);
    }

    /**
     * The storey a height of the module belongs to, for someone standing there: the highest storey whose level is at
     * most half a block above it; -1 below the first.
     */
    public int storeyAt(double y) {
        int out = -1;
        for (int s = 0; s < storeys.length; s++) if (storeys[s] <= y + 0.5) out = s;
        return out;
    }

    /** What a block of a storey's plan shows. */
    public Mark mark(int storey, int x, int z) {
        if (storey < 0 || storey >= storeys.length || x < 0 || x >= width || z < 0 || z >= depth) return Mark.NONE;
        return marks[storey][x * depth + z];
    }

    // ---- units

    /** The units of a storey, in slot order. */
    public List<Integer> units(int storey) {
        List<Integer> out = new ArrayList<>();
        for (UnitLayout.Unit u : layout.units()) if (layout.storey(u.index) == storey) out.add(u.index);
        return out;
    }

    /** The tiles of a unit's cells on its storey's plan; empty outside the layout. */
    public List<int[]> tiles(int unit) {
        return unit < 0 || unit >= tiles.size() ? Collections.emptyList() : tiles.get(unit);
    }

    /** The box round a unit's tiles, {x0, y0, x1, y1}; null outside the layout. */
    public int[] bounds(int unit) {
        List<int[]> t = tiles(unit);
        if (t.isEmpty()) return null;
        int[] b = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        for (int[] r : t) {
            b[0] = Math.min(b[0], r[0]);
            b[1] = Math.min(b[1], r[1]);
            b[2] = Math.max(b[2], r[2]);
            b[3] = Math.max(b[3], r[3]);
        }
        return b;
    }

    /**
     * The unit of the storey with a cell nearest to a point of that storey's plan, within {@link #REACH} pixels; -1
     * when none is that near.
     */
    public int nearest(int storey, double x, double y) {
        int best = -1;
        double bestD = REACH + 1e-9;
        for (UnitLayout.Unit u : layout.units()) {
            if (layout.storey(u.index) != storey) continue;
            for (int[] t : tiles.get(u.index)) {
                double dx = Math.max(0, Math.max(t[0] - x, x - t[2])), dy = Math.max(0, Math.max(t[1] - y, y - t[3]));
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d < bestD) {
                    bestD = d;
                    best = u.index;
                }
            }
        }
        return best;
    }

    // ---- what a block shows

    /** The floor parts a unit set flush into a floor sits among (0.12.0). */
    private static final String FLOORS = "dBFg:e%#*";

    /**
     * Whether the units of a storey are set flush into its floor (0.12.0: the Tidal Well's taps lie in the pool's rim
     * at
     * the level of the walkway, people standing one higher): a unit cell of that level has a floor part beside it at
     * the same level. Its plan then shows what stands one level up, over that floor, as for a storey whose units stand
     * on it (the Eco Dome's planters).
     */
    static boolean flush(CityBuild b, UnitLayout layout, int level) {
        for (UnitLayout.Unit u : layout.units()) {
            if (u.floor != level) continue;
            for (int[] c : u.cells) {
                if (c[1] != level) continue;
                for (int[] o : SIDES) {
                    int x = c[0] + o[0], z = c[2] + o[1];
                    if (x < 0 || x >= b.width() || z < 0 || z >= b.depth()) continue;
                    if (FLOORS.indexOf(b.cell(x, level, z)) >= 0) return true;
                }
            }
        }
        return false;
    }

    /** A storey's plan at height {@code y}: every block, then walls inside a mass made {@link Mark#SOLID}. */
    private Mark[] plan(CityBuild b, int y, String dissolves) {
        Mark[] out = new Mark[width * depth];
        for (int x = 0; x < width; x++)
            for (int z = 0; z < depth; z++) out[x * depth + z] = classify(b, x, y, z, dissolves);
        Mark[] done = out.clone();
        for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) {
            if (out[x * depth + z] != Mark.WALL) continue;
            boolean mass = true;
            for (int[] o : SIDES) {
                int nx = x + o[0], nz = z + o[1];
                if (nx < 0 || nx >= width || nz < 0 || nz >= depth || !solid(out[nx * depth + nz])) mass = false;
            }
            if (mass) done[x * depth + z] = Mark.SOLID;
        }
        return done;
    }

    private static final int[][] SIDES = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private static boolean solid(Mark m) {
        return m == Mark.WALL || m == Mark.SOLID || m == Mark.GLAZE || m == Mark.PILASTER;
    }

    /**
     * What the block at {@code (x, z)} of a storey at height {@code y} shows: what stands there, else the floor under
     * it (grass, water, a lit strip, a floor), else nothing.
     */
    public static Mark classify(CityBuild b, int x, int y, int z, String dissolves) {
        char a = b.cell(x, y, z);
        if (a != ShapeKit.NONE && dissolves != null && dissolves.indexOf(a) >= 0) return Mark.TRUNK;
        switch (a) {
            case ShapeKit.AIR:
                return Mark.DOOR;
            case ShapeKit.CONTROLLER:
                return Mark.CORE;
            case 'K':
                return Mark.CONSOLE;
            case '^':
                return Mark.LIFT;
            case 'G':
            case '/':
                return Mark.GLAZE;
            case 'L':
            case '!':
                return Mark.PILASTER;
            case 'r':
            case '|':
                return Mark.RAIL;
            case 'q':
                return Mark.POST;
            case 't':
            case '}':
                return Mark.STAIR;
            case 'h':
            case 'o':
            case 'u':
                return Mark.FIXTURE;
            case '=':
                return Mark.WATER;
            case ',':
            case ';':
                return Mark.GRASS;
            case '+':
                return Mark.LIT;
            case '.':
            case '_':
                return Mark.FLOOR;
            case ShapeKit.NONE:
                return floor(y > 0 ? b.cell(x, y - 1, z) : ShapeKit.NONE);
            default:
                return Mark.WALL;
        }
    }

    /** What an empty block shows of the floor under it. */
    private static Mark floor(char f) {
        switch (f) {
            case ShapeKit.NONE:
            case ShapeKit.AIR:
                return Mark.NONE;
            case '^':
                return Mark.LIFT;
            case 'u':
            case 'o':
            case 'h':
                // a basin rim, a socket or a pedestal set flush into the floor stays furniture
                return Mark.FIXTURE;
            case ',':
            case ';':
                return Mark.GRASS;
            case '=':
                return Mark.WATER;
            case 'e':
            case '+':
                return Mark.LIT;
            default:
                return Mark.FLOOR;
        }
    }
}
