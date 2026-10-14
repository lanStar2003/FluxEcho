package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the generic drawing of a city module puts its lights (0.11.0, CITY.md §1.1 and §1.6 item 8), worked out from
 * its shape alone in the module's own frame ({@link ShapeKit}: x across, y up, z from the front). Every module kind
 * gets the same three: the light line along its plinth course, the name plate over its portal's canopy, and the faces
 * of its sockets an item shows in. Pure data, so the renderer keeps no geometry of its own and the tests can hold the
 * rules: the line runs on the outside of the walls only, never across a doorway; the plate floats in front of the
 * controller above the canopy; a socket's sample shows over the basin rim beside it; a ripple stays on its water.
 * Also how much a module draws for a camera ({@link #tier}) and what the shared drawing costs it there
 * ({@link #baseQuads}, 0.13.0), and which samples are bees.
 */
public final class ModuleLook {

    /** The plinth course's character (deck PANEL_DARK) and the height it runs at (the first layer over the base). */
    public static final char COURSE = 'w';
    public static final int COURSE_Y = 1;

    /**
     * How much of a module is drawn for a camera (CITY.md §1.3 budgets): {@link #NEAR} inside it or within the near
     * distance of its box (2,000 quads), {@link #MID} within the effect range (300), {@link #FAR} further out (64).
     */
    public static final int NEAR = 0, MID = 1, FAR = 2;

    /**
     * The sample socket's recess on each side face (in blocks from the block's foot): high on the column, so the
     * sample shows over a basin rim beside it ({@link #BASIN_TOP}, its lip {@link #LIP_TOP}), as every planter's
     * sockets stand. Its picture floats {@link #RECESS_IN} in from the column's face (between the face, two sixteenths
     * in, and the recess's back, four), its middle at {@link #PICTURE_MID}, {@link #PICTURE_HALF} each way.
     */
    public static final double RECESS_LOW = 11 / 16.0, RECESS_HIGH = 15 / 16.0, RECESS_IN = 2.7 / 16,
        PICTURE_MID = 13 / 16.0, PICTURE_HALF = 0.1;
    /** The basin rim: its slab's top, and its lip's top and the lip's band across the rim (from either edge). */
    public static final double BASIN_TOP = 10 / 16.0, LIP_TOP = 12 / 16.0, LIP_MIN = 6 / 16.0, LIP_MAX = 10 / 16.0;
    /** A player's eye over the floor they stand on. */
    public static final double EYE = 1.62;
    /** The most sides of a socket turned to a camera at once (0.15.0): one of each pair of opposite sides. */
    public static final int SOCKET_SIDES_SEEN = 2;

    /** The top of still water in a block: a source among sources, as the game draws it (one ninth below the top). */
    public static final double WATER_TOP = 8 / 9.0;

    private ModuleLook() {}

    /**
     * How much of a module to draw: {@link #NEAR} with the camera inside it or within {@code near} blocks of its box,
     * {@link #MID} within {@code range} blocks of its box, {@link #FAR} beyond.
     */
    public static int tier(boolean inside, double boxDistance, double near, double range) {
        if (inside || boxDistance <= near) return NEAR;
        return boxDistance <= range ? MID : FAR;
    }

    /**
     * Whether the side of a socket facing {@code (ox, oz)} (a unit step) is turned to a camera {@code (dx, dz)} from
     * the socket's middle (0.15.0): the camera lies out past the plane its recess's picture floats in, half a block
     * less {@link #RECESS_IN} from the middle. Otherwise the socket's column stands between the camera and that
     * recess, so its sample is not drawn there. Of two opposite sides at most one is, so at most
     * {@link #SOCKET_SIDES_SEEN} sides of a socket are.
     */
    public static boolean socketSideFacing(int ox, int oz, double dx, double dz) {
        return dx * ox + dz * oz > 0.5 - RECESS_IN;
    }

    /**
     * Where ripple rings may spread on a layer's water ({@code =}): {x + 0.5, z + 0.5, room} for every water cell, room
     * the distance from the cell's middle to the nearest cell of the layer that holds no water (the edge of its
     * square), at most {@code cap}. A ring round the middle no wider than its room lies on water all round.
     */
    public static List<double[]> water(CityBuild b, int y, double cap) {
        List<double[]> out = new ArrayList<>();
        int reach = (int) Math.ceil(cap) + 1;
        for (int x = 0; x < b.width(); x++) for (int z = 0; z < b.depth(); z++) {
            if (b.cell(x, y, z) != '=') continue;
            double room = cap;
            for (int i = -reach; i <= reach; i++) for (int j = -reach; j <= reach; j++) {
                int nx = x + i, nz = z + j;
                boolean wet = nx >= 0 && nx < b.width() && nz >= 0 && nz < b.depth() && b.cell(nx, y, nz) == '=';
                if (wet) continue;
                room = Math.min(room, Math.hypot(Math.max(0, Math.abs(i) - 0.5), Math.max(0, Math.abs(j) - 0.5)));
            }
            out.add(new double[] { x + 0.5, z + 0.5, room });
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Whether a sample is a bee, from its item's registry name ({@code mod:name}), so no bee mod's classes are named:
     * the name, cut into words at capitals, digits, underscores and dots, has the word {@code bee} alone or followed by
     * a bee's kind (drone, princess, queen, larva, larvae), as Forestry's {@code beeDroneGE}. Beef, beeswax, a bee's
     * comb or a beehouse are no bees.
     */
    public static boolean beeName(String registryName) {
        if (registryName == null) return false;
        String path = registryName.substring(registryName.indexOf(':') + 1);
        List<String> words = new ArrayList<>();
        StringBuilder w = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            boolean cut = !Character.isLetter(c)
                || Character.isUpperCase(c) && w.length() > 0 && !Character.isUpperCase(w.charAt(w.length() - 1));
            if (cut && w.length() > 0) {
                words.add(
                    w.toString()
                        .toLowerCase(java.util.Locale.ROOT));
                w.setLength(0);
            }
            if (Character.isLetter(c)) w.append(c);
        }
        if (w.length() > 0) words.add(
            w.toString()
                .toLowerCase(java.util.Locale.ROOT));
        for (int i = 0; i < words.size(); i++) {
            if (!words.get(i)
                .equals("bee")) continue;
            if (i + 1 == words.size()) return true;
            if (BEE_KINDS.contains(words.get(i + 1))) return true;
        }
        return false;
    }

    private static final List<String> BEE_KINDS = java.util.Arrays
        .asList("drone", "princess", "queen", "larva", "larvae");

    /**
     * The plinth course's faces on the outside of the walls: {x, z, outX, outZ, s} for every course cell (at
     * {@link #COURSE_Y}) and every side of it that looks out of the building (where the base layer has nothing, or,
     * 0.14.0, onto a way open to the outside, {@link #outside}), {@code s} its place round the building (0 to n-1, in
     * order of the angle round the box's middle, starting at the front middle and running to the right of someone
     * walking in). A doorway has no course, so the line stops there; a gate niche's soft course ({@code [}, CITY.md
     * §4.15) is the same block and keeps it.
     */
    public static List<int[]> course(CityBuild b) {
        int w = b.width(), d = b.depth();
        double mx = (w - 1) / 2.0, mz = (d - 1) / 2.0;
        boolean[][] open = outside(b);
        List<int[]> out = new ArrayList<>();
        List<Double> angle = new ArrayList<>();
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
            if (ShapeKit.checkedTwin(b.cell(x, COURSE_Y, z)) != COURSE) continue;
            for (int[] o : OUT) {
                int nx = x + o[0], nz = z + o[1];
                if (inside(b, nx, nz) && !open[nx + 1][nz + 1]) continue;
                out.add(new int[] { x, z, o[0], o[1], 0 });
                // from the front middle (pointing at -z) round to the right (+x)
                double fx = x + 0.5 + o[0] * 0.5 - (mx + 0.5), fz = z + 0.5 + o[1] * 0.5 - (mz + 0.5);
                double a = Math.atan2(fx, -fz);
                angle.add(a < 0 ? a + 2 * Math.PI : a);
            }
        }
        Integer[] order = new Integer[out.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (p, q) -> Double.compare(angle.get(p), angle.get(q)));
        for (int i = 0; i < order.length; i++) out.get(order[i])[4] = i;
        return Collections.unmodifiableList(out);
    }

    private static final int[][] OUT = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };

    /** Whether a column is part of the building: its base layer has a cell there (soft or checked). */
    static boolean inside(CityBuild b, int x, int z) {
        if (x < 0 || x >= b.width() || z < 0 || z >= b.depth()) return false;
        return b.cell(x, 0, z) != ShapeKit.NONE;
    }

    /**
     * The columns open to the outside at the plinth course's height (0.14.0), {@code [x + 1][z + 1]} for x from -1 to
     * the width and z from -1 to the depth: those beyond the box, and those reached from them through cells holding
     * nothing at that height, never through a doorway (must-be-air) or across anything standing there. A way through a
     * building open to the sky and the road, such as the Resonance Gate's passage under its bridge, is outside, though
     * the gate's floor runs under it; a hall's inside, which only its doors open onto, is not. Every module before the
     * gate has no such way, so their course is what it was.
     */
    static boolean[][] outside(CityBuild b) {
        int w = b.width(), d = b.depth();
        boolean[][] seen = new boolean[w + 2][d + 2];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int x = -1; x <= w; x++) for (int z = -1; z <= d; z++) {
            if (x >= 0 && x < w && z >= 0 && z < d) continue;
            seen[x + 1][z + 1] = true;
            queue.add(new int[] { x, z });
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] o : OUT) {
                int nx = c[0] + o[0], nz = c[1] + o[1];
                if (nx < 0 || nx >= w || nz < 0 || nz >= d || seen[nx + 1][nz + 1]) continue;
                if (b.cell(nx, COURSE_Y, nz) != ShapeKit.NONE) continue;
                seen[nx + 1][nz + 1] = true;
                queue.add(new int[] { nx, nz });
            }
        }
        return seen;
    }

    /**
     * Where the name plate's middle floats: on the controller's axis, over the middle of the portal's canopy (the
     * shape's decor in front of it, {@code z < 0}); without a canopy, three and a half blocks over the controller and
     * one and a half out from the front; for a centrally planned module (0.13.0, {@link #central}) over its central
     * console ({@link #overCentre}). {x, y, z} in the module's frame (fractional, the middle of the controller's
     * column at {@code x + 0.5}).
     */
    public static double[] plate(CityBuild b) {
        int[] c = b.controller();
        int top = Integer.MIN_VALUE, front = 0;
        for (int[] d : b.decor()) {
            if (d[2] >= 0) continue;
            top = Math.max(top, d[1]);
            front = Math.min(front, d[2]);
        }
        if (top == Integer.MIN_VALUE && central(b)) return overCentre(b);
        if (top == Integer.MIN_VALUE) return new double[] { c[0] + 0.5, c[1] + 3.6, -1.5 };
        // over the canopy's middle: it reaches from the front wall (z 0) out to the front of its outer row
        return new double[] { c[0] + 0.5, top + 1.75, front / 2.0 };
    }

    /** How high over the top of the column standing on a centrally planned module's controller its plate floats. */
    public static final double OVER_COLUMN = 1.75;

    /**
     * Whether a module is centrally planned (0.13.0, CITY.md §1.1: the pavilions): its controller stands over the
     * middle of its box, on the central console, not in a front wall.
     */
    public static boolean central(CityBuild b) {
        int[] c = b.controller();
        return c[0] == (b.width() - 1) / 2 && c[2] == (b.depth() - 1) / 2;
    }

    /**
     * A centrally planned module's plate (0.13.0): over the central console, three and a half blocks over the
     * controller, or, when a column stands on the controller (the Mana Fountain's obelisk), {@link #OVER_COLUMN} over
     * that column's top, so the plate never floats inside it.
     */
    static double[] overCentre(CityBuild b) {
        int[] c = b.controller();
        int y = c[1];
        while (y + 1 < b.height() && b.cell(c[0], y + 1, c[2]) != ShapeKit.NONE) y++;
        double over = y == c[1] ? c[1] + 3.6 : y + 1 + OVER_COLUMN;
        return new double[] { c[0] + 0.5, over, c[2] + 0.5 };
    }

    /** How high over its block's foot a lift-entered module's plate floats over its controller. */
    public static final double OVER_CONSOLE = 1.4;
    /** How far under the floor a module hanging in the air carries its second plate, over the lift's shaft. */
    public static final double BELLY = 2.2;

    /**
     * Every name plate of a module, {x, y, z} each in its frame ({@link #plate} for one with a portal; the Holo Sand
     * Table's over its middle, {@link HoloLook#plate}, 0.15.0). A module
     * entered by the light lift (CITY.md §1.1: the zenith's pod, the root; its shape holds a lift pad) has no portal:
     * its plate floats over the controller on its console, under the ceiling, {@link #OVER_CONSOLE} over the
     * controller block's foot. One that hangs in the air (its blueprint reaches down below its own floor, as the pod's
     * legs do) carries a second plate under its floor over the lift's shaft, {@link #BELLY} down, for the forum below.
     */
    public static List<double[]> plates(CityBuild b) {
        List<double[]> out = new ArrayList<>();
        // the Holo Sand Table's console stands at the table's front, off its middle (0.15.0): its plate floats over
        // the middle above the ring light, clear of the miniature over the table (HoloLook.plate)
        if (HoloTableShape.KEY.equals(b.key())) {
            out.add(HoloLook.plate());
            return Collections.unmodifiableList(out);
        }
        int[] pad = liftPad(b);
        if (pad == null) {
            out.add(plate(b));
            return Collections.unmodifiableList(out);
        }
        int[] c = b.controller();
        out.add(new double[] { c[0] + 0.5, c[1] + OVER_CONSOLE, c[2] + 0.5 });
        // the pod floating (0.16.0) hangs in the air on no legs: its own box, but the belly plate all the same
        if (b.offset()[1] > 0 || DomeGeneratorShape.KEY.equals(b.key()))
            out.add(new double[] { pad[0] + 0.5, pad[1] - BELLY, pad[2] + 0.5 });
        return Collections.unmodifiableList(out);
    }

    /** The light-lift pad of a module's shape {x, y, z} (the lowest, when it has more), or null when it has none. */
    public static int[] liftPad(CityBuild b) {
        for (int y = 0; y < b.height(); y++) for (int x = 0; x < b.width(); x++) for (int z = 0; z < b.depth(); z++) {
            if (b.cell(x, y, z) == LIFT) return new int[] { x, y, z };
        }
        return null;
    }

    /** The light-lift pad's character. */
    public static final char LIFT = '^';

    /**
     * The most quads a name plate takes (0.13.0): its pane and its two rules (15), and one a letter of its two lines
     * for up to 65 letters.
     */
    public static final int PLATE_QUADS = 80;

    /**
     * The forming sweep's quads as {@code CityRender} draws it through the module's box (the blueprint's, as the
     * module's {@code bounds()} gives it): a plane, and the four lines of its rim, each cut into pieces of at most
     * {@link DomeLook#PIECE} blocks as every line is ({@link DomeLook#pieces}): 17 for a 25 x 25 pavilion, 19 for the
     * 27 x 35 Eco Dome. (The Echo Dome Generator draws its own sweep, up its legs and then through its pod.)
     */
    public static int sweepQuads(CityBuild b) {
        Blueprint bp = b.blueprint();
        return 1 + 2 * DomeLook.pieces(bp.width()) + 2 * DomeLook.pieces(bp.depth());
    }

    /**
     * The most quads the drawing every city module shares takes a frame for one module at a tier (0.13.0, CITY.md §1.3,
     * {@link #tier}): the forming sweep while it forms ({@link #sweepQuads}); within the effect range its name plates
     * ({@link #plates}, seen within 48 blocks) and its plinth line ({@link #course}: a core a face, near a two-quad
     * halo too); near, the samples in its sockets (on each side of a socket turned to the camera, at most
     * {@link #SOCKET_SIDES_SEEN} of its four since 0.15.0, {@link #socketSideFacing}: a glow and up to two pictures).
     * A module kind's own visuals come on top; the sum must stay within 2,000 near, 300 mid and 64 far.
     */
    public static int baseQuads(CityBuild b, UnitLayout layout, int tier) {
        return baseQuads(b, layout, tier, true);
    }

    /**
     * {@link #baseQuads(CityBuild, UnitLayout, int)} for a shape whose plinth line is not drawn ({@code course} false,
     * 0.16.0: the deep root's chamber forms, whose course runs round the chamber's walls deep in the ground,
     * {@code RootLook#courseBuried}).
     */
    public static int baseQuads(CityBuild b, UnitLayout layout, int tier, boolean course) {
        int n = sweepQuads(b);
        if (tier == FAR) return n;
        n += plates(b).size() * PLATE_QUADS;
        int faces = course ? course(b).size() : 0;
        if (tier == MID) return n + faces;
        int sockets = 0;
        for (UnitLayout.Unit u : layout.units()) sockets += u.sockets.size();
        return n + faces * 3 + sockets * SOCKET_SIDES_SEEN * 3;
    }
}
