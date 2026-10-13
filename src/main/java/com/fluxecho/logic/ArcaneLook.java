package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the Arcane Court's drawing puts its ink and its light (0.13.0, CITY.md §4.3 Visuals), worked out round the
 * spire's axis, the middle of the altar's column ({@link #AXIS}), in the court's own frame
 * ({@link ArcaneCourtShape}: x across, y up from the floor's base layer, z from the blueprint's front):
 * <ul>
 * <li>The finial's crystal: once the court forms, the finial's conduits and seat give way to a violet crystal turning
 * where they stood ({@link #crystalY}, {@link #crystalTurn}), over the lantern's cap.</li>
 * <li>The ink rings: three rings of black ink orbit the spire outside it, each at its own height, radius and tilt,
 * turning at its own speed while its tilt swings slowly round ({@link #inkPoint}); the brush lifts three times a ring,
 * so each has three gaps its stroke tapers into ({@link #inkHalf}). They are alpha-blended: black cannot be added.</li>
 * <li>Inside: the rune ring of the floor (its lit tiles at octagon radius 6) carries a ring of runes turning slowly
 * ({@link #runeStroke}); motes in the aspects' colours rise from the eight aspect pillars into the hall
 * ({@link #pillarMote}); the four cauldrons bubble over their rims ({@link #bubble}).</li>
 * <li>Powered: logograms of ink sink down the hollow spire's inside like ink in water, spreading as they fall
 * ({@link #glyph}, {@link #glyphPoint}).</li>
 * </ul>
 * Every motion is a function of the world time alone and repeats with its own period, so a frame drawn again behind a
 * light gate is the same frame. Pure arithmetic over the shape, so the tests hold the ink clear of the spire, the
 * glyphs inside its hollow, the runes on their tiles, the motes in the hall's air and the whole court within its quad
 * budgets ({@link #quads}).
 */
public final class ArcaneLook {

    /** The spire's axis across and deep in the court's frame: the middle of the altar's column. */
    public static final double AXIS = ArcaneCourtShape.MID + 0.5;

    // ---- the finial's crystal

    /** The crystal's middle height (the middle of the finial's three cells, y30..32), half-width and half-height. */
    public static final double CRYSTAL_Y = 31.5, CRYSTAL_R = 0.45, CRYSTAL_H = 1.2;
    /** How far it bobs up and down, and the periods (ticks) of its bob and of one turn. */
    public static final double CRYSTAL_BOB = 0.1;
    public static final int CRYSTAL_BOB_T = 160, CRYSTAL_TURN_T = 400;

    // ---- the ink rings

    /** How many ink rings orbit the spire. */
    public static final int RINGS = 3;
    /**
     * Each ring's middle height, radius and tilt (radians): low and wide to high and narrow, tilting more as it rises.
     */
    public static final double[] RING_Y = { 22.0, 25.5, 29.0 }, RING_R = { 11.5, 9.0, 6.0 },
        RING_TILT = { Math.toRadians(10), Math.toRadians(12), Math.toRadians(22) };
    /**
     * Each ring's period of one turn of its stroke round it (ticks; the sign is the way it turns) and of one swing of
     * its tilt round the spire (four turns of the stroke), and where its tilt starts.
     */
    public static final int[] RING_SPIN_T = { 1600, -1100, 800 }, RING_SWING_T = { 6400, -4400, 3200 };
    private static final double[] RING_SWING0 = { 0.3, 2.4, 4.4 };
    /** The brush's half-width on each ring at full pressure (blocks). */
    public static final double[] INK_HALF = { 0.24, 0.2, 0.16 };
    /** Where the brush lifts on each ring (a share of the way round), how wide each gap is and how long the taper. */
    private static final double[][] GAPS = { { 0.07, 0.41, 0.72 }, { 0.18, 0.52, 0.86 }, { 0.03, 0.36, 0.64 } };
    public static final double GAP_HALF = 0.035, TAPER = 0.07;
    /** The pieces a ring is cut into near, mid and far ({@link ModuleLook#NEAR}, {@code MID}, {@code FAR}). */
    public static final int[] INK_PIECES = { 48, 24, 12 };

    // ---- the rune ring

    /** The rune ring on the floor: its middle radius, the lines either side of it, the height its light lies at. */
    public static final double RUNE_R = 5.8, RUNE_IN = 5.4, RUNE_OUT = 6.2, RUNE_Y = 1.025;
    /** How many runes, a rune's half-size along the ring and across it, and the pieces of each line round it. */
    public static final int RUNES = 16, RUNE_PIECES = 48;
    public static final double RUNE_ALONG = 0.22, RUNE_ACROSS = 0.3;
    /** The ticks of one turn of the rune ring (it turns the other way to the lowest ink ring). */
    public static final int RUNE_TURN_T = 2400;
    /**
     * The runes' strokes, three a rune, {along0, across0, along1, across1} in a rune's own square (-1..1 each way):
     * an alphabet of eight, each rune of the ring one of them.
     */
    private static final double[][][] ALPHABET = { { { -0.6, -1, -0.6, 1 }, { -0.6, 0, 0.6, 1 }, { -0.6, 0, 0.6, -1 } },
        { { 0, -1, 0, 1 }, { -0.7, 0.4, 0.7, 0.4 }, { -0.5, -0.6, 0.5, -0.6 } },
        { { -0.7, -1, 0, 1 }, { 0, 1, 0.7, -1 }, { -0.4, -0.2, 0.4, -0.2 } },
        { { -0.6, 1, 0.6, 1 }, { 0.6, 1, -0.6, -1 }, { -0.6, -1, 0.6, -1 } },
        { { 0, -1, 0, 1 }, { 0, 1, 0.7, 0.3 }, { 0, 0.1, 0.7, -0.5 } },
        { { -0.7, -1, -0.7, 1 }, { 0.7, -1, 0.7, 1 }, { -0.7, 1, 0.7, -1 } },
        { { 0, -1, 0, 1 }, { -0.7, 1, 0, 0.2 }, { 0.7, 1, 0, 0.2 } },
        { { -0.7, 0.6, 0.7, 0.6 }, { -0.7, -0.6, 0.7, -0.6 }, { 0, 1, 0, -1 } } };
    public static final int STROKES = 3;

    // ---- the pillars' motes and the cauldrons' bubbles

    /**
     * The eight aspect pillars' colours, in unit order (Thaumcraft's aspect colours, by value only: aer, terra, ignis,
     * aqua, ordo, perditio lifted to show as light, praecantatio, potentia).
     */
    public static final int[] ASPECTS = { 0xFFFF7E, 0x56C000, 0xFF5A01, 0x3CD4FC, 0xD5D4EC, 0x8A8A8A, 0xCF00FF,
        0xC0FFFF };
    /** Motes a pillar sheds at once, the ticks one takes from its socket to fading, how far in and up it drifts. */
    public static final int MOTES = 5, MOTE_T = 160;
    public static final double MOTE_IN = 2.4, MOTE_UP = 1.6, MOTE_SWAY = 0.25;
    /** Bubbles a cauldron has at once and the ticks one takes to rise and burst over its rim. */
    public static final int BUBBLES = 6, BUBBLE_T = 50;
    /** How high over the rim's lip a bubble rises before it bursts. */
    public static final double BUBBLE_RISE = 0.55;

    // ---- the logograms

    /** Logograms sinking at once, the ticks one takes from the spire's top to the hall, where it starts and ends. */
    public static final int GLYPHS = 5, GLYPH_T = 600;
    public static final double GLYPH_TOP = 26.0, GLYPH_BOTTOM = 13.5;
    /** A logogram's radius at the top and at the bottom (ink spreads as it sinks), and how far it drifts and tilts. */
    public static final double GLYPH_R0 = 0.8, GLYPH_R1 = 2.5, GLYPH_DRIFT = 0.3, GLYPH_TILT = 0.2;
    /** The pieces of a logogram's ring, its tendrils and their pieces, a tendril's longest reach past the ring. */
    public static final int GLYPH_PIECES = 16, TENDRILS = 3, TENDRIL_PIECES = 2;
    public static final double TENDRIL = 0.7;
    /** A logogram's ink band's half-width at full pressure, and how wide its brush gaps are each way. */
    public static final double GLYPH_HALF = 0.07, GLYPH_GAP_HALF = 0.07;
    /** Where each logogram's brush lifts (two gaps each, a share of the way round). */
    private static final double[][] GLYPH_GAPS = { { 0.1, 0.55 }, { 0.3, 0.8 }, { 0.05, 0.62 }, { 0.22, 0.7 },
        { 0.4, 0.92 } };

    /** The quads of the crystal (an octahedron), and of a glow. */
    public static final int CRYSTAL_QUADS = 8, GLOW_QUADS = 1;

    /** The aspect pillars and the cauldrons, in unit order. */
    private static final List<UnitLayout.Unit> PILLARS, CAULDRONS;
    /** Each cauldron's basin rim cells {x, y, z} (its cells but the socket in the middle). */
    private static final List<int[][]> RIMS;

    static {
        List<UnitLayout.Unit> p = new ArrayList<>(), c = new ArrayList<>();
        List<int[][]> rims = new ArrayList<>();
        for (UnitLayout.Unit u : ArcaneCourtShape.UNITS.units()) {
            if (u.kind.equals("pillar")) p.add(u);
            else if (u.kind.equals("cauldron")) {
                c.add(u);
                List<int[]> rim = new ArrayList<>();
                for (int[] cell : u.cells) if (ArcaneCourtShape.cell(cell[0], cell[1], cell[2]) == 'u') rim.add(cell);
                rims.add(rim.toArray(new int[0][]));
            }
        }
        PILLARS = Collections.unmodifiableList(p);
        CAULDRONS = Collections.unmodifiableList(c);
        RIMS = Collections.unmodifiableList(rims);
        if (PILLARS.size() != ASPECTS.length || CAULDRONS.size() != 4)
            throw new IllegalStateException("the arcane court's pillars and cauldrons");
    }

    private ArcaneLook() {}

    /** The aspect pillars, in unit order. */
    public static List<UnitLayout.Unit> pillars() {
        return PILLARS;
    }

    /** The cauldrons, in unit order. */
    public static List<UnitLayout.Unit> cauldrons() {
        return CAULDRONS;
    }

    // ---- the crystal

    /** The crystal's middle height at the world time {@code t} (ticks). */
    public static double crystalY(double t) {
        return CRYSTAL_Y + CRYSTAL_BOB * Math.sin(2 * Math.PI * t / CRYSTAL_BOB_T);
    }

    /** How far the crystal has turned at {@code t} (radians). */
    public static double crystalTurn(double t) {
        return 2 * Math.PI * frac(t / CRYSTAL_TURN_T);
    }

    // ---- the ink rings

    /**
     * The point {@code s} of the way round ink ring {@code ring} (0 the lowest) at the world time {@code t}: {x, y, z}
     * in the court's frame. The ring turns round its own middle and its tilt swings round the spire, each with its
     * period, so the point repeats after {@link #ringPeriod}.
     */
    public static double[] inkPoint(int ring, double s, double t) {
        double phi = 2 * Math.PI * (s + t / RING_SPIN_T[ring]);
        double psi = RING_SWING0[ring] + 2 * Math.PI * t / RING_SWING_T[ring];
        double tilt = RING_TILT[ring], r = RING_R[ring];
        // e1 is the level axis the ring tilts about, e2 the ring's other axis, rising by the tilt
        double e1x = Math.cos(psi), e1z = Math.sin(psi);
        double e2x = -Math.sin(psi) * Math.cos(tilt), e2y = Math.sin(tilt), e2z = Math.cos(psi) * Math.cos(tilt);
        double c = Math.cos(phi), sn = Math.sin(phi);
        return new double[] { AXIS + r * (c * e1x + sn * e2x), RING_Y[ring] + r * sn * e2y,
            AXIS + r * (c * e1z + sn * e2z) };
    }

    /**
     * The ticks after which ink ring {@code ring} is where it was: one swing of its tilt (four turns of its stroke).
     */
    public static int ringPeriod(int ring) {
        return Math.abs(RING_SWING_T[ring]);
    }

    /**
     * The brush's half-width at the point {@code s} of the way round ink ring {@code ring} (the stroke turns with the
     * ring, so this does not change with time): none in its three gaps, tapering from nothing at a gap's edge over
     * {@link #TAPER}, and a pressure that swells and thins twice round.
     */
    public static double inkHalf(int ring, double s) {
        double g = 1;
        for (double gap : GAPS[ring]) g = Math.min(g, roundDistance(s, gap));
        return INK_HALF[ring] * brush(g, GAP_HALF, ring, s);
    }

    /** The share of the way round from {@code a} to {@code b} the shorter way (0 to 0.5). */
    private static double roundDistance(double a, double b) {
        double d = Math.abs(frac(a) - frac(b));
        return Math.min(d, 1 - d);
    }

    /**
     * A brush stroke's width (a share of its fullest) at {@code g} from the nearest gap's middle, for gaps
     * {@code gapHalf} each way: none in the gap, a taper over {@link #TAPER}, then a pressure that swells and thins.
     */
    private static double brush(double g, double gapHalf, int seed, double s) {
        if (g <= gapHalf) return 0;
        double taper = Math.min(1, (g - gapHalf) / TAPER);
        double pressure = 0.7 + 0.3 * Math.sin(2 * Math.PI * (2 * s + seed * 0.37));
        return taper * pressure;
    }

    /**
     * How many pieces of ink ring {@code ring} are drawn at a tier ({@link ModuleLook#tier}): of
     * {@link #INK_PIECES}, those with ink at either end (a piece wholly in a gap is left out).
     */
    public static int inkDrawn(int ring, int tier) {
        int n = INK_PIECES[tier], out = 0;
        for (int i = 0; i < n; i++)
            if (inkHalf(ring, i / (double) n) > 0 || inkHalf(ring, (i + 1) / (double) n) > 0) out++;
        return out;
    }

    // ---- the rune ring

    /** How far the rune ring has turned at {@code t} (radians; against the lowest ink ring's way). */
    public static double runeTurn(double t) {
        return -2 * Math.PI * frac(t / RUNE_TURN_T);
    }

    /** Which letter of the alphabet rune {@code i} of the ring is (the same for every court). */
    public static int runeLetter(int i) {
        return (int) (Mix.unit(0xA2C5L, i) * ALPHABET.length);
    }

    /**
     * Stroke {@code k} of rune {@code i} at the world time {@code t}: {x0, z0, x1, z1} in the court's frame, lying on
     * the floor's light at {@link #RUNE_Y}.
     */
    public static double[] runeStroke(int i, int k, double t) {
        double[] st = ALPHABET[runeLetter(i)][k];
        double mid = 2 * Math.PI * i / RUNES + runeTurn(t);
        double[] a = runePoint(mid, st[0], st[1]), b = runePoint(mid, st[2], st[3]);
        return new double[] { a[0], a[1], b[0], b[1] };
    }

    /** A point of a rune's square ({@code along}, {@code across}, -1..1) round the angle {@code mid}: {x, z}. */
    private static double[] runePoint(double mid, double along, double across) {
        double r = RUNE_R + across * RUNE_ACROSS, a = mid + along * RUNE_ALONG / RUNE_R;
        return new double[] { AXIS + r * Math.cos(a), AXIS + r * Math.sin(a) };
    }

    // ---- the pillars' motes

    /**
     * Mote {@code k} of aspect pillar {@code pillar} (unit order) at the world time {@code t}: {x, y, z, alpha} in the
     * court's frame. It leaves the pillar's face at one of its sockets' heights, drifts into the hall and up, swaying,
     * and fades out; every {@link #MOTE_T} ticks it starts again.
     */
    public static double[] pillarMote(int pillar, int k, double t) {
        UnitLayout.Unit u = PILLARS.get(pillar);
        int[] cell = u.cells.get(0);
        double fx = u.face[0], fz = u.face[1];
        double clock = t / MOTE_T + k / (double) MOTES + Mix.unit(0xA59EL, pillar) * 0.5;
        long cycle = (long) Math.floor(clock);
        double phase = clock - cycle;
        double h = Mix.unit(0xA5ECL, pillar, k, cycle);
        // a socket's height on the pillar (y2..4); sideways (along the wall) towards the middle of the pillar's flat
        // face, away from the chamfer beside it, swaying more as it leaves the wall
        double y = 2.25 + 2.5 * h + MOTE_UP * phase;
        double lx = -fz, lz = fx, along = (cell[0] + 0.5 - AXIS) * lx + (cell[2] + 0.5 - AXIS) * lz;
        double toMiddle = along > 0 ? -1 : 1;
        double side = toMiddle * (0.15 + 0.3 * Mix.unit(0x51DEL, pillar, k, cycle))
            + MOTE_SWAY * phase * Math.sin(phase * 5 + k);
        double out = 0.62 + MOTE_IN * phase;
        double x = cell[0] + 0.5 + fx * out + lx * side, z = cell[2] + 0.5 + fz * out + lz * side;
        return new double[] { x, y, z, Math.sin(Math.PI * phase) };
    }

    // ---- the cauldrons' bubbles

    /**
     * Bubble {@code k} of cauldron {@code cauldron} (unit order) at the world time {@code t}: {x, y, z, size, alpha}
     * in the court's frame. It rises from the lip of one of its basin rim's cells and bursts (a brief flash) at
     * {@link #BUBBLE_RISE} over it; every {@link #BUBBLE_T} ticks it starts again over another rim cell.
     */
    public static double[] bubble(int cauldron, int k, double t) {
        double clock = t / BUBBLE_T + k / (double) BUBBLES + cauldron * 0.13;
        long cycle = (long) Math.floor(clock);
        double phase = clock - cycle;
        int[][] rims = RIMS.get(cauldron);
        int[] rim = rims[(int) (Mix.unit(0xB0BL, cauldron, k, cycle) * rims.length)];
        double x = rim[0] + 0.25 + 0.5 * Mix.unit(0xB0B1L, cauldron, k, cycle),
            z = rim[2] + 0.25 + 0.5 * Mix.unit(0xB0B2L, cauldron, k, cycle);
        double y = rim[1] + ModuleLook.LIP_TOP + BUBBLE_RISE * phase;
        boolean burst = phase > 0.85;
        double size = burst ? 0.12 : 0.04 + 0.05 * phase, alpha = burst ? (1 - phase) / 0.15 : 0.35 + 0.4 * phase;
        return new double[] { x, y, z, size, alpha };
    }

    // ---- the logograms

    /**
     * Logogram {@code g} at the world time {@code t}: {x, y, z, radius, tilt, tiltDirection, turn, alpha} in the
     * court's frame. It sinks from {@link #GLYPH_TOP} to {@link #GLYPH_BOTTOM} inside the hollow spire over
     * {@link #GLYPH_T} ticks, spreading from {@link #GLYPH_R0} to {@link #GLYPH_R1}, drifting a little off the axis and
     * rocking, fading in and out; the {@link #GLYPHS} of them a fifth of the way apart.
     */
    public static double[] glyph(int g, double t) {
        double u = frac(t / GLYPH_T + g / (double) GLYPHS);
        double y = GLYPH_TOP - (GLYPH_TOP - GLYPH_BOTTOM) * u;
        double r = GLYPH_R0 + (GLYPH_R1 - GLYPH_R0) * u;
        double x = AXIS + GLYPH_DRIFT * Math.sin(2 * Math.PI * u + g),
            z = AXIS + GLYPH_DRIFT * Math.cos(2 * Math.PI * u * 0.7 + g * 2);
        double tilt = GLYPH_TILT * Math.sin(2 * Math.PI * u + g), dir = g * 1.3, turn = g * 1.1 + Math.PI * u;
        return new double[] { x, y, z, r, tilt, dir, turn, Math.pow(Math.sin(Math.PI * u), 0.8) };
    }

    /**
     * A point of logogram {@code gl} ({@link #glyph}) in its own tilted plane: {@code reach} out from its middle at
     * the angle {@code a} (radians, turned with the logogram). {x, y, z} in the court's frame.
     */
    public static double[] glyphPoint(double[] gl, double a, double reach) {
        double ang = a + gl[6], c = Math.cos(ang), s = Math.sin(ang);
        // tilt about the level axis at tiltDirection: the part across that axis rises or falls by sin(tilt)
        double ax = Math.cos(gl[5]), az = Math.sin(gl[5]);
        double along = c * ax + s * az, across = -c * az + s * ax;
        double lift = across * Math.sin(gl[4]), flat = across * Math.cos(gl[4]);
        return new double[] { gl[0] + reach * (along * ax - flat * az), gl[1] + reach * lift,
            gl[2] + reach * (along * az + flat * ax) };
    }

    /** The half-width of logogram {@code g}'s ink at the share {@code s} of the way round its ring. */
    public static double glyphHalf(int g, double s) {
        double d = 1;
        for (double gap : GLYPH_GAPS[g % GLYPH_GAPS.length]) d = Math.min(d, roundDistance(s, gap));
        return GLYPH_HALF * brush(d, GLYPH_GAP_HALF, g + 5, s);
    }

    /** The angle (radians, in the logogram's turned frame) tendril {@code k} of logogram {@code g} leaves its ring. */
    public static double tendrilAngle(int g, int k) {
        return 2 * Math.PI * (k + Mix.unit(0x7E4DL, g, k) * 0.6) / TENDRILS;
    }

    /** How far past its ring tendril {@code k} of logogram {@code g} reaches (blocks, at most {@link #TENDRIL}). */
    public static double tendrilReach(int g, int k) {
        return TENDRIL * (0.5 + 0.5 * Mix.unit(0x7E4EL, g, k));
    }

    /** How many pieces of logogram {@code g}'s ring are drawn: those with ink at either end. */
    public static int glyphDrawn(int g) {
        int out = 0;
        for (int i = 0; i < GLYPH_PIECES; i++)
            if (glyphHalf(g, i / (double) GLYPH_PIECES) > 0 || glyphHalf(g, (i + 1) / (double) GLYPH_PIECES) > 0) out++;
        return out;
    }

    // ---- the budget

    /**
     * The most quads the court's own visuals take a frame at a tier ({@link ModuleLook#tier}), formed, docked or not,
     * powered or not, counted as {@code ArcaneCourtRender} draws them: the crystal (an outer crystal, near and mid an
     * inner one too, and its glow); docked, the ink rings ({@link #inkDrawn}, near with a halo each piece); near and
     * docked, the rune ring's two lines and its runes, the pillars' motes and the cauldrons' bubbles and glows; near
     * and powered, the logograms (each drawn piece of a ring an ink band and a halo, each tendril's pieces the same).
     */
    public static int quads(int tier, boolean docked, boolean on) {
        boolean nearby = tier == ModuleLook.NEAR;
        int n = CRYSTAL_QUADS + (tier == ModuleLook.FAR ? 0 : CRYSTAL_QUADS) + GLOW_QUADS;
        if (docked) for (int r = 0; r < RINGS; r++) n += inkDrawn(r, tier) * (nearby ? 2 : 1);
        if (nearby && docked) {
            n += 2 * RUNE_PIECES + RUNES * STROKES;
            n += PILLARS.size() * MOTES;
            n += CAULDRONS.size() * (BUBBLES + GLOW_QUADS);
        }
        if (nearby && on) for (int g = 0; g < GLYPHS; g++) n += 2 * (glyphDrawn(g) + TENDRILS * TENDRIL_PIECES);
        return n;
    }

    /** The fractional part of {@code v} (0 to 1, also for a negative {@code v}). */
    static double frac(double v) {
        return v - Math.floor(v);
    }
}
