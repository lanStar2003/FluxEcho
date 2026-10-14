package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the drawing of every sky module shares (0.16.0, CITY.md §4.12 "Sky-ring visuals shared"), worked out in a sky
 * module's frame ({@link SkyRing}: x across, y up from its deck, the floating platform's layer, z back from its front,
 * where the catwalk's mouth is):
 * <ul>
 * <li>The light pillar: under the platform's middle ({@link #CX}, {@link #CZ}) a soft column of light falls from the
 * platform's underside ({@link #PILLAR_TOP}) to the ground under it (the first block that stands in its column, at most
 * {@link #SCAN} down), brightest where it meets the ground and fading with height, a core of light inside it and a few
 * motes sinking down it ({@link #fallMote}); where it meets the ground, a glow. Over the gate (S0) it ends on the
 * Resonance Gate's floating spire when one stands within {@link #LINK_REACH} of its column, drawn as a resonance link
 * in the two colours. It is a landmark (CITY.md §1.3 "sky pillars"): drawn to {@link #LANDMARK} blocks, fading out over
 * the last {@link #LANDMARK_FADE}, and the same drawing within the effects' range, so the two meet without a seam.</li>
 * <li>The catwalk's thin lit edges ({@link #walkRows}): along the outer edges of the catwalk's deck under it, and as
 * two
 * lit strips on its deck along the rails, a row at a time where the row's deck stands, a pulse of light running out
 * from the pod to the platform ({@link #walkPulse}).</li>
 * </ul>
 * Every motion is a function of the world time alone, so a frame drawn again behind a light gate is the same frame;
 * the counts are what the renderer draws ({@link #pillarQuads}, {@link #walkQuads}).
 */
public final class SkyLook {

    /** The platform's middle, under which the pillar falls. */
    public static final double CX = SkyRing.MID + 0.5, CZ = SkyRing.MID + 0.5;
    /** The pillar's half-width and its core's, and its top: just under the platform's floating foundation. */
    public static final double PILLAR_R = 4.5, CORE_R = 1.1, PILLAR_TOP = -0.04;
    /** How far under the deck the ground is looked for (blocks), and the ground taken when nothing is found. */
    public static final int SCAN = 64, NO_GROUND = DomeGeneratorShape.FLOOR_UP - 1;
    /** The pillar's alpha where it meets the ground and under the platform (it fades with height). */
    public static final float FOOT_ALPHA = 0.3f, HEAD_ALPHA = 0.06f;
    /** The motes sinking down the pillar near and mid, and the ticks one takes from the platform to the ground. */
    public static final int FALL_MOTES = 6, FALL_T = 160;
    /**
     * How near (blocks across) the Resonance Gate's spire must stand to the pillar's column for the pillar to end on
     * it.
     */
    public static final double LINK_REACH = 4;
    /** How far the pillar is drawn (blocks from its middle), how long it fades out, and its budget (CITY.md §1.3). */
    public static final double LANDMARK = 512, LANDMARK_FADE = 64;
    public static final int BUDGET = 400;

    /**
     * The catwalk in the module's frame: its rows' z (radial 11 to 21 on the site's axis, in front of the platform's
     * front row at radial {@link SkyRing#FRONT}), and its deck's outer edges across.
     */
    public static final int WALK_Z0 = SkyRing.CATWALK_FROM - SkyRing.FRONT,
        WALK_Z1 = SkyRing.CATWALK_TO - SkyRing.FRONT;
    public static final double WALK_X0 = SkyRing.MID - SkyRing.CATWALK_HALF,
        WALK_X1 = SkyRing.MID + SkyRing.CATWALK_HALF + 1;
    /** Where the edges are drawn: just under the deck, and as strips on its top a little in from the rails. */
    public static final double UNDER_Y = -0.03, TOP_Y = 1.025, STRIP_IN = 0.07;
    /** The ticks the walk's pulse takes from the pod to the platform, and its rest before the next one (ticks). */
    public static final int WALK_T = 60, WALK_REST = 40;

    /** The quads of a column of light ({@code Shapes.beam}: two planes in two halves) and of a glow. */
    public static final int BEAM_QUADS = 4, GLOW_QUADS = 1;
    /** The lines of one catwalk row: the two edges under it and the two strips on its deck. */
    public static final int ROW_LINES = 4;

    private static final List<int[]> ROWS;

    static {
        List<int[]> rows = new ArrayList<>();
        for (int z = WALK_Z0; z <= WALK_Z1; z++)
            rows.add(new int[] { z, SkyRing.MID - SkyRing.CATWALK_HALF, SkyRing.MID + SkyRing.CATWALK_HALF });
        ROWS = Collections.unmodifiableList(rows);
    }

    private SkyLook() {}

    // ---- the pillar

    /** How much of the pillar shows at a distance from its middle: 1, fading to 0 over its last blocks. */
    public static float landmarkFade(double distance) {
        return (float) Math.max(0, Math.min(1, (LANDMARK - distance) / LANDMARK_FADE));
    }

    /**
     * The pillar's foot in the module's frame: the top of the highest block found standing in its column under the
     * deck ({@code standing}, a layer of the frame, below 0), or {@link #NO_GROUND} under the deck when none was
     * found within {@link #SCAN}.
     */
    public static double foot(int standing) {
        if (standing >= 0 || standing < -SCAN) return -NO_GROUND;
        return standing + 1;
    }

    /**
     * Where sinking mote {@code k} of the pillar is at the world time {@code t} between its top and its foot
     * ({@code foot}, below the top): {y, alpha}, the alpha rising and falling over its way down.
     */
    public static double[] fallMote(int k, double t, double foot) {
        double u = ArcaneLook.frac(t / FALL_T + k / (double) FALL_MOTES);
        return new double[] { PILLAR_TOP + (foot - PILLAR_TOP) * u, StrictMath.sin(Math.PI * u) };
    }

    /**
     * The quads the pillar takes a frame at a tier ({@link ModuleLook#tier}; the landmark draws it far): its column and
     * its core, the glow at its foot and, near and mid, the glow under the platform and its sinking motes.
     */
    public static int pillarQuads(int tier) {
        int n = 2 * BEAM_QUADS + GLOW_QUADS;
        if (tier == ModuleLook.FAR) return n;
        return n + GLOW_QUADS + FALL_MOTES;
    }

    // ---- the catwalk

    /**
     * The catwalk's rows in the module's frame, from the pod out to the platform: {z, the deck's left cell x, its right
     * cell x}. A row's edges are drawn where its deck cell on that side stands.
     */
    public static List<int[]> walkRows() {
        return ROWS;
    }

    /**
     * How bright the walk's pulse makes row {@code z} at the world time {@code t}: 0 to 1, a pulse running from the pod
     * (the first row) out to the platform every {@link #WALK_T} + {@link #WALK_REST} ticks.
     */
    public static double walkPulse(int z, double t) {
        double cycle = WALK_T + WALK_REST, at = t % cycle;
        if (at < 0) at += cycle;
        double head = (WALK_Z1 + 1 - WALK_Z0 + 4) * at / WALK_T - 2, d = z - WALK_Z0 + 0.5 - head;
        return StrictMath.exp(-d * d / 3);
    }

    /**
     * The quads the catwalk's edges take a frame at a tier: near every row's four lines and a halo under each of its
     * two under edges; mid the four lines of every row; far none (thinner than a pixel).
     */
    public static int walkQuads(int tier) {
        if (tier == ModuleLook.FAR) return 0;
        int rows = ROWS.size();
        return tier == ModuleLook.NEAR ? rows * (ROW_LINES + 2) : rows * ROW_LINES;
    }

    /**
     * The quads every sky module's shared drawing takes a frame at a tier: the pillar and the catwalk's edges, while it
     * stands formed. A module's own visuals and the drawing of every city module ({@link ModuleLook#baseQuads}) come
     * on top.
     */
    public static int quads(int tier) {
        return pillarQuads(tier) + walkQuads(tier);
    }
}
