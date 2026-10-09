package com.fluxecho.logic;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The room behind a light gate, block by block. Each module's room has the module's own shape (a domed greenhouse
 * for the eco dome, a long vaulted gallery for the library, an octagonal hall under a spire for the hub...), closed
 * all round by a shell that cannot be broken, lit, with its gate near the south wall facing into the room.
 * <p>
 * Coordinates are the room's own: the middle of the floor is {@code (0, 0, 0)}, the floor is the layer {@code y = 0}
 * and the room's air starts at {@code y = 1}. A cell is shell when it is not air but touches air, even only at a
 * corner, so the shell is closed for anything moving, flowing or shining.
 */
public final class RoomPlan {

    /** What one cell of the room is. */
    public enum Cell {

        OUT,
        AIR,
        FLOOR,
        /** The floor's glowing edge along the walls. */
        RIM,
        WALL,
        CEILING,
        PILLAR,
        LIGHT,
        /** A glowing band on the walls. */
        TRIM,
        GLASS;

        /** Whether it is shell. */
        public boolean solid() {
            return this != OUT && this != AIR;
        }

        /** The light it gives off. */
        public int light() {
            return this == LIGHT ? 15 : this == TRIM || this == RIM ? 12 : 0;
        }
    }

    public enum Footprint {
        RECT,
        CIRCLE,
        OCTAGON
    }

    public enum Roof {
        FLAT,
        /** A dome over the middle; {@code rise} squashes it. */
        DOME,
        /** A barrel vault along z. */
        VAULT,
        /** A pointed roof rising {@code rise} blocks per block in from the wall. */
        SPIRE
    }

    /** One room shape. {@code halfX} and {@code halfZ} are the floor's half widths, {@code wall} its wall height. */
    public static final class Template {

        public final String id;
        public final Footprint footprint;
        public final int halfX, halfZ, wall;
        public final Roof roof;
        public final double rise;
        /** A glass roof between ribs, for a real sky: bees see it, crops get the sun. */
        public final boolean glass;

        Template(String id, Footprint footprint, int halfX, int halfZ, int wall, Roof roof, double rise,
            boolean glass) {
            this.id = id;
            this.footprint = footprint;
            this.halfX = halfX;
            this.halfZ = footprint == Footprint.RECT ? halfZ : halfX;
            this.wall = wall;
            this.roof = roof;
            this.rise = rise;
            this.glass = glass;
        }
    }

    /** The rooms of blueprint 3.7, in the order the gate item cycles through them. */
    public static final List<Template> TEMPLATES = Collections.unmodifiableList(
        Arrays.asList(
            // the hub's city hall: an octagon under a low spire, a ring of gates later
            new Template("hall", Footprint.OCTAGON, 24, 24, 14, Roof.SPIRE, 0.5, false),
            // the eco dome: a glass dome over open ground
            new Template("garden", Footprint.CIRCLE, 28, 28, 6, Roof.DOME, 1.0, true),
            // the echo library: a long vaulted gallery of shelves
            new Template("gallery", Footprint.RECT, 8, 48, 12, Roof.VAULT, 1.0, false),
            // the arcane court: a tall octagon under a steep spire
            new Template("sanctum", Footprint.OCTAGON, 16, 16, 12, Roof.SPIRE, 1.25, false),
            // the blood altar: a round hall under a shallow dome
            new Template("altar", Footprint.CIRCLE, 18, 18, 10, Roof.DOME, 0.5, false),
            // the hunting ground: a holodeck, square and high
            new Template("arena", Footprint.RECT, 28, 28, 20, Roof.FLAT, 0, false),
            // the mana spring: a wide round garden under a flat glass roof
            new Template("spring", Footprint.CIRCLE, 36, 36, 14, Roof.FLAT, 0, true),
            // the sophon array: a dark hall of data
            new Template("datavoid", Footprint.RECT, 32, 32, 24, Roof.FLAT, 0, false),
            // the dual-vector vault: tall walls to hang the canvases on
            new Template("canvas", Footprint.RECT, 12, 36, 32, Roof.FLAT, 0, false),
            // the observatory: a small glass dome
            new Template("observatory", Footprint.CIRCLE, 14, 14, 8, Roof.DOME, 1.0, true)));

    public static final String DEFAULT = "hall";

    /** The template with this id, the default one for an unknown id. */
    public static Template template(String id) {
        for (Template t : TEMPLATES) if (t.id.equals(id)) return t;
        return TEMPLATES.get(0);
    }

    /** The template after this one, round again at the end. */
    public static Template next(String id) {
        int i = TEMPLATES.indexOf(template(id));
        return TEMPLATES.get((i + 1) % TEMPLATES.size());
    }

    public final Template t;
    /** The room's extent, shell included. */
    public final int minX, maxX, minY, maxY, minZ, maxZ;
    /** Where the gate stands: on the floor near the south wall, facing north into the room. */
    public final int gateZ;

    public RoomPlan(Template t) {
        this.t = t;
        minX = -t.halfX - 1;
        maxX = t.halfX + 1;
        minZ = -t.halfZ - 1;
        maxZ = t.halfZ + 1;
        minY = 0;
        int top = 0;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) if (inFootprint(x, z)) top = Math.max(top, t.wall + rise(x, z));
        maxY = top + 1;
        int gz = 0;
        while (inside(0, 1, gz + 1)) gz++;
        gateZ = gz - 2;
    }

    public RoomPlan(String id) {
        this(template(id));
    }

    // ---- shape

    private boolean inFootprint(int x, int z) {
        int ax = Math.abs(x), az = Math.abs(z);
        switch (t.footprint) {
            case RECT:
                return ax <= t.halfX && az <= t.halfZ;
            case CIRCLE: {
                double r = t.halfX + 0.5;
                return x * x + z * z <= r * r;
            }
            default:
                return ax <= t.halfX && az <= t.halfX && ax + az <= Math.round(t.halfX * Math.sqrt(2));
        }
    }

    /** How far in from the wall a column is, in blocks, by the footprint's own measure. */
    private double inward(int x, int z) {
        int ax = Math.abs(x), az = Math.abs(z);
        switch (t.footprint) {
            case RECT:
                return Math.min(t.halfX - ax, t.halfZ - az);
            case CIRCLE:
                return t.halfX - Math.sqrt(x * x + z * z);
            default:
                return t.halfX - Math.max(Math.max(ax, az), (ax + az) / Math.sqrt(2));
        }
    }

    /** How far the roof rises over the walls above this column. */
    private int rise(int x, int z) {
        switch (t.roof) {
            case DOME: {
                double r = t.halfX + 0.5, d = r * r - x * x - z * z;
                return d <= 0 ? 0 : (int) Math.floor(t.rise * Math.sqrt(d));
            }
            case VAULT: {
                double r = t.halfX + 0.5, d = r * r - x * x;
                return d <= 0 ? 0 : (int) Math.floor(t.rise * Math.sqrt(d));
            }
            case SPIRE:
                return Math.max(0, (int) Math.floor(t.rise * inward(x, z)));
            default:
                return 0;
        }
    }

    /** Whether a cell is the room's air. */
    public boolean inside(int x, int y, int z) {
        return y >= 1 && inFootprint(x, z) && y <= t.wall + rise(x, z);
    }

    private boolean nearInside(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
            for (int dz = -1; dz <= 1; dz++) if (inside(x + dx, y + dy, z + dz)) return true;
        return false;
    }

    // ---- what each cell is

    public Cell cell(int x, int y, int z) {
        if (inside(x, y, z)) return Cell.AIR;
        if (!nearInside(x, y, z)) return Cell.OUT;
        if (y <= 0) return floor(x, z);
        if (y > t.wall || inside(x, y - 1, z)) return roof(x, y, z);
        return wall(x, y, z);
    }

    private Cell floor(int x, int z) {
        if (!inside(x, 1, z)) return Cell.FLOOR;
        if (!inside(x + 1, 1, z) || !inside(x - 1, 1, z) || !inside(x, 1, z + 1) || !inside(x, 1, z - 1))
            return Cell.RIM;
        // glowing tiles where there is no sun to light the floor
        if (!t.glass && Math.floorMod(x - 3, 6) == 0 && Math.floorMod(z - 3, 6) == 0) return Cell.LIGHT;
        return Cell.FLOOR;
    }

    private Cell roof(int x, int y, int z) {
        boolean over = inside(x, y - 1, z);
        if (t.glass && y > t.wall + 1 && !rib(x, z)) return Cell.GLASS;
        if (t.glass && y == t.wall + 1 && over && !rib(x, z) && t.roof == Roof.FLAT) return Cell.GLASS;
        if (over && Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0) return Cell.LIGHT;
        if (over && t.roof == Roof.VAULT && x == 0 && Math.floorMod(z, 4) == 0) return Cell.LIGHT;
        return Cell.CEILING;
    }

    /** The frame between the panes of a glass roof. */
    private boolean rib(int x, int z) {
        if (t.roof == Roof.DOME) {
            // eight ribs from the top down to the walls
            double d = Math.sqrt(x * x + z * z);
            if (d < 2) return true;
            double a = Math.atan2(z, x), step = Math.PI / 4;
            double off = a - Math.round(a / step) * step;
            return Math.abs(Math.sin(off)) * d < 0.75;
        }
        return Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0;
    }

    private Cell wall(int x, int y, int z) {
        if (pillar(x, z)) return Cell.PILLAR;
        if (y == 4 || t.wall >= 8 && y == t.wall) return Cell.TRIM;
        return Cell.WALL;
    }

    private boolean pillar(int x, int z) {
        if (t.footprint == Footprint.RECT) {
            int ax = Math.abs(x), az = Math.abs(z);
            boolean sideX = ax > t.halfX, sideZ = az > t.halfZ;
            if (sideX && sideZ) return true;
            return sideX && Math.floorMod(z, 8) == 0 || sideZ && Math.floorMod(x, 8) == 0;
        }
        double a = Math.atan2(z, x), d = Math.sqrt(x * x + z * z);
        // an octagon's corners; a circle gets one every eight or so blocks round
        int n = t.footprint == Footprint.OCTAGON ? 8 : Math.max(8, (int) Math.round(2 * Math.PI * t.halfX / 8));
        double step = 2 * Math.PI / n, phase = t.footprint == Footprint.OCTAGON ? step / 2 : 0;
        double off = a - phase - Math.round((a - phase) / step) * step;
        return Math.abs(Math.sin(off)) * d < 1.0;
    }

    // ---- in the world

    /** The room's gate, for a room whose floor's middle is at {@code (cx, floorY, cz)}. */
    public GateGeometry.Gate gate(int cx, int floorY, int cz) {
        return new GateGeometry.Gate(cx, floorY + 1, cz + gateZ, 2);
    }

    /** The blocks of a room whose floor's middle is at {@code (cx, floorY, cz)}, shell included. */
    public GateGeometry.Box box(int cx, int floorY, int cz) {
        return new GateGeometry.Box(cx + minX, floorY + minY, cz + minZ, cx + maxX, floorY + maxY, cz + maxZ);
    }
}
