package com.fluxecho.logic;

/**
 * Where the Resonance Gate's drawing puts its light (0.14.0, CITY.md §4.7 Visuals), worked out in the gate's frame
 * ({@link ResonanceGateShape}: x across, {@code x = 15 + R}; y up from the base layer; z from the front,
 * {@code z = A - 23}) round the spire's axis over the bridge's middle ({@link #AXIS_X}, {@link #AXIS_Z}):
 * <ul>
 * <li>Formed: once the spire core's conduits and seat give way, the floating crystal spire stands over the bridge, an
 * elongated octahedron three wide and 16 tall from {@code Y0+14} to {@code Y0+30} ({@link #spire}),
 * hovering a little over the roof and slowly turning; six shards orbit it round its waist ({@link #shard}); a column of
 * light rises from the chamber's floor through the dissolved core and the glass over it to the spire's tip. Undocked
 * it burns low.</li>
 * <li>Docked (idle): every {@link #PULSE_T} ticks (8 s) a resonance pulse, a ring of light, runs out from the spire's
 * waist over the wings ({@link #pulse}).</li>
 * <li>Powered (active): the pulses quicken ({@link #PULSE_ON_T}) and the shards fall into line, one level ring
 * round the waist turning faster.</li>
 * <li>A landmark: beyond the effects' range the spire, its shards as points of light and the pulse are drawn to
 * {@link #LANDMARK} blocks, fading out over the last {@link #LANDMARK_FADE} ({@link #landmarkQuads}, never more than
 * {@link #BUDGET} quads); the far tier draws the same, so the two meet without a seam.</li>
 * </ul>
 * Every motion is a function of the world time alone, so a frame drawn again behind a light gate is the same frame.
 * Pure arithmetic over the shape, so the tests hold the spire, its shards and its pulse in the air over the gate and
 * the gate within CITY.md §1.3's budgets ({@link #quads}).
 */
public final class GateLook {

    /** The spire's axis across and deep: over the spire core's column in the bridge's middle. */
    public static final double AXIS_X = ResonanceGateShape.MID + 0.5, AXIS_Z = ResonanceGateShape.CORE_Z + 0.5;
    /** Where the spire stands (its foot tip and its top tip, CITY.md §4.7 {@code Y0+14…+30}) and its waist. */
    public static final double SPIRE_FROM = ResonanceGateShape.BRIDGE_ROOF + 1, SPIRE_TO = SPIRE_FROM + 16,
        WAIST = (SPIRE_FROM + SPIRE_TO) / 2;
    /** Its half-width at the waist (to a corner of its square section: three wide) and its half-height. */
    public static final double SPIRE_HALF_W = 1.5, SPIRE_HALF_H = (SPIRE_TO - SPIRE_FROM) / 2;
    /** How high it hovers over its place, and how far it bobs about that, every {@link #BOB_T} ticks. */
    public static final double HOVER = 0.3, BOB = 0.15;
    /** The ticks of one bob and of one slow turn. */
    public static final int BOB_T = 200, TURN_T = 1200;

    /** The shards: how many, how far from the axis they orbit, their half-width and half-height. */
    public static final int SHARDS = 6;
    public static final double SHARD_R = 3.4, SHARD_HALF_W = 0.3, SHARD_HALF_H = 0.9;
    /** The idle shards' heights over or under the waist, in shard order (scattered), and how far they bob. */
    private static final double[] SHARD_DY = { 2.5, -1.5, 0.5, -2.5, 1.5, -0.5 };
    public static final double SHARD_BOB = 0.2;
    /** The ticks of one orbit idle and powered (faster, in line), and of one of a shard's own turns. */
    public static final int ORBIT_T = 480, ORBIT_ON_T = 240, SHARD_TURN_T = 90;

    /** The resonance pulse: every {@link #PULSE_T} ticks docked, every {@link #PULSE_ON_T} powered. */
    public static final int PULSE_T = 160, PULSE_ON_T = 80;
    /** How many ticks a pulse's ring takes to run out, and from and to how far from the axis it runs. */
    public static final int RING_T = 64;
    public static final double RING_FROM = SPIRE_HALF_W + 0.3, RING_TO = 15;
    /** The pieces a ring is drawn in near, mid and far (far also the landmark's). */
    public static final int[] RING_PIECES = { 48, 24, 16 };

    /** The column of light from the chamber's floor (the bridge's deck top) up to the spire's foot. */
    public static final double BEAM_FROM = ResonanceGateShape.DECK_Y + 1, BEAM_R = 0.3;
    /** The motes rising in the column near. */
    public static final int BEAM_MOTES = 6, BEAM_MOTE_T = 70;

    /** How far the landmark is drawn (blocks from the spire's waist), how long it fades out, and its budget. */
    public static final double LANDMARK = 512, LANDMARK_FADE = 64;
    public static final int BUDGET = 400;

    /** The quads of a crystal ({@code Shapes.crystal}: four faces up, four down) and of a glow. */
    public static final int CRYSTAL_QUADS = 8, GLOW_QUADS = 1, BEAM_QUADS = 4;

    private GateLook() {}

    // ---- the spire

    /**
     * The spire at the world time {@code t}: {y, spin}, its waist's height (hovering {@link #HOVER} over its place and
     * bobbing {@link #BOB} about that) and how far it has turned (radians, one turn every {@link #TURN_T} ticks).
     */
    public static double[] spire(double t) {
        double y = WAIST + HOVER + BOB * Math.sin(2 * Math.PI * t / BOB_T);
        return new double[] { y, 2 * Math.PI * ArcaneLook.frac(t / TURN_T) };
    }

    /**
     * The spire's half-width at the height {@code y} when its waist is at {@code waist}: the octahedron narrowing
     * from {@link #SPIRE_HALF_W} at the waist to a point at either tip (to a corner of its square section).
     */
    public static double halfWidth(double y, double waist) {
        return Math.max(0, SPIRE_HALF_W * (1 - Math.abs(y - waist) / SPIRE_HALF_H));
    }

    // ---- the shards

    /**
     * Shard {@code k} at the world time {@code t}: {x, y, z, spin}. Idle they orbit at scattered heights about the
     * waist, each bobbing; powered they fall into line, one level ring riding the spire's waist and orbiting twice as
     * fast.
     */
    public static double[] shard(int k, double t, boolean on) {
        double orbit = ArcaneLook.frac(t / (on ? ORBIT_ON_T : ORBIT_T) + k / (double) SHARDS);
        double a = 2 * Math.PI * orbit;
        double y = on ? spire(t)[0]
            : WAIST + HOVER + SHARD_DY[k] + SHARD_BOB * Math.sin(2 * Math.PI * (t / BOB_T + k / (double) SHARDS));
        double spin = 2 * Math.PI * ArcaneLook.frac(t / SHARD_TURN_T + k * 0.37);
        return new double[] { AXIS_X + SHARD_R * Math.cos(a), y, AXIS_Z + SHARD_R * Math.sin(a), spin };
    }

    // ---- the pulse

    /**
     * The resonance pulse's ring at the world time {@code t}: {radius, alpha}, or null between pulses. A ring starts
     * at the spire's waist every {@link #PULSE_T} ticks ({@link #PULSE_ON_T} powered), runs out from
     * {@link #RING_FROM} to {@link #RING_TO} over {@link #RING_T} ticks, quick at first, and fades as it goes.
     */
    public static double[] pulse(double t, boolean on) {
        int period = on ? PULSE_ON_T : PULSE_T;
        double age = ArcaneLook.frac(t / period) * period;
        if (age >= RING_T) return null;
        double u = age / RING_T;
        double r = RING_FROM + (RING_TO - RING_FROM) * (1 - (1 - u) * (1 - u));
        return new double[] { r, Math.pow(1 - u, 1.5) };
    }

    /** The point of a ring of radius {@code r} at the angle {@code a} round the axis: {x, z}. */
    public static double[] onRing(double r, double a) {
        return new double[] { AXIS_X + r * Math.cos(a), AXIS_Z + r * Math.sin(a) };
    }

    // ---- the beam

    /** The height a rising mote {@code k} of the column has reached at the world time {@code t}, and its alpha. */
    public static double[] beamMote(int k, double t, double top) {
        double u = ArcaneLook.frac(t / BEAM_MOTE_T + k / (double) BEAM_MOTES);
        return new double[] { BEAM_FROM + (top - BEAM_FROM) * u, Math.sin(Math.PI * u) };
    }

    // ---- the landmark

    /** How much of the landmark shows at a distance from the spire's waist: 1, fading to 0 over its last blocks. */
    public static float landmarkFade(double distance) {
        return (float) Math.max(0, Math.min(1, (LANDMARK - distance) / LANDMARK_FADE));
    }

    // ---- the budget

    /** The quads of a ring of light at a tier: each piece's chord (at its widest) in its pieces, near with a halo. */
    public static int ringQuads(int tier) {
        int n = RING_PIECES[tier];
        double chord = 2 * RING_TO * Math.sin(Math.PI / n);
        int q = n * DomeLook.pieces(chord);
        return tier == ModuleLook.NEAR ? 2 * q : q;
    }

    /**
     * The most quads the gate's own visuals take a frame at a tier ({@link ModuleLook#tier}), counted as
     * {@code ResonanceGateRender} draws them: the spire (near and mid with its inner glow) and its glow; the shards
     * (near and mid as crystals, near each with a glow; far as points of light); near and mid the column of light (near
     * with its motes); docked the pulse's ring. The shared drawing ({@link ModuleLook#baseQuads}) comes on top.
     */
    public static int quads(int tier, boolean docked, boolean on) {
        boolean far = tier == ModuleLook.FAR, nearby = tier == ModuleLook.NEAR;
        int n = CRYSTAL_QUADS + GLOW_QUADS + (far ? 0 : CRYSTAL_QUADS);
        n += SHARDS * (far ? GLOW_QUADS : CRYSTAL_QUADS + (nearby ? GLOW_QUADS : 0));
        if (!far) n += BEAM_QUADS + (nearby ? BEAM_MOTES + GLOW_QUADS : 0);
        if (docked) n += ringQuads(tier);
        return n;
    }

    /** The most quads the landmark takes a frame, at any distance: the far tier's drawing. */
    public static int landmarkQuads(boolean docked, boolean on) {
        return quads(ModuleLook.FAR, docked, on);
    }
}
