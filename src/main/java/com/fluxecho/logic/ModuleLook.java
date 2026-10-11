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
 * Also how much a module draws for a camera ({@link #tier}), and which samples are bees.
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
     * {@link #COURSE_Y}) and every side of it that looks out of the building (where the base layer has nothing),
     * {@code s} its place round the building (0 to n-1, in order of the angle round the box's middle, starting at the
     * front middle and running to the right of someone walking in). A doorway has no course, so the line stops there;
     * a gate niche's soft course ({@code [}, CITY.md §4.15) is the same block and keeps it.
     */
    public static List<int[]> course(CityBuild b) {
        int w = b.width(), d = b.depth();
        double mx = (w - 1) / 2.0, mz = (d - 1) / 2.0;
        List<int[]> out = new ArrayList<>();
        List<Double> angle = new ArrayList<>();
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
            if (ShapeKit.checkedTwin(b.cell(x, COURSE_Y, z)) != COURSE) continue;
            for (int[] o : OUT) {
                int nx = x + o[0], nz = z + o[1];
                if (inside(b, nx, nz)) continue;
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
     * Where the name plate's middle floats: on the controller's axis, over the middle of the portal's canopy (the
     * shape's decor in front of it, {@code z < 0}); without a canopy, three and a half blocks over the controller and
     * one and a half out from the front. {x, y, z} in the module's frame (fractional, the middle of the controller's
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
        if (top == Integer.MIN_VALUE) return new double[] { c[0] + 0.5, c[1] + 3.6, -1.5 };
        // over the canopy's middle: it reaches from the front wall (z 0) out to the front of its outer row
        return new double[] { c[0] + 0.5, top + 1.75, front / 2.0 };
    }
}
