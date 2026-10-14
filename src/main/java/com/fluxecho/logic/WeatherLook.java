package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the Weather Station's drawing puts its light (0.16.0, CITY.md §4.12 Visuals: "vanes turn with the weather, the
 * cloud thickens before rain, rare flickers of mini lightning"), worked out in its frame ({@link WeatherStationShape}:
 * x across, y up from the deck, z back from the front):
 * <ul>
 * <li>Formed, the weather mast (its pillars, conduit and seat dissolve) is a slim steel mast with a line of light up
 * it ({@link #MAST_X}, {@link #MAST_Z}), an anemometer of {@link #CUPS} cups turning at its top ({@link #ROTOR_Y}) and
 * a
 * wind vane under it swinging with the wind ({@link #vaneYaw}); over each vane pylon's ring a small rotor of
 * {@link #BLADES} blades turns ({@link #PYLON_ROTOR_Y}).</li>
 * <li>Over the mast a cloud churns slowly ({@link #puff}): soft puffs orbiting its middle ({@link #CLOUD_Y}), more of
 * them, larger and darker as it thickens ({@link #density}): a fair-weather wisp in clear weather, thickening over the
 * {@link #LEAD} ticks before the rain the server has coming (the forecast the station is told), full and grey while it
 * rains, darkest in a thunderstorm. The rotors turn faster as the weather worsens, in three steps ({@link #wind}).</li>
 * <li>Now and then a mini lightning flickers ({@link #flash}): rarely in fair weather, more often as rain gathers and
 * in
 * a storm; a jagged bolt from inside the cloud down onto the mast's top, or across inside the cloud, for a few
 * stuttering ticks, the cloud lighting up with it ({@link #bolt}).</li>
 * </ul>
 * Every motion is a function of the world time, the weather the client has and the forecast alone (the flashes from a
 * hash of the time's window and the station's seed), so a frame drawn again behind a light gate is the same frame. The
 * counts are what the renderer draws ({@link #quads}, {@link #landmarkQuads}).
 */
public final class WeatherLook {

    /** The mast's axis (its column's middle), its foot and its top (over the seat's layer, y13). */
    public static final double MAST_X = WeatherStationShape.MAST_X + 0.5, MAST_Z = WeatherStationShape.MAST_Z + 0.5,
        MAST_FOOT = 1, MAST_TOP = WeatherStationShape.SEAT_Y + 0.7, MAST_W = 0.17;
    /** The anemometer at the mast's top: its height, its arms' length, its cups' half-size and how many there are. */
    public static final double ROTOR_Y = WeatherStationShape.SEAT_Y + 0.35, ARM = 1.15, CUP = 0.22;
    public static final int CUPS = 3;
    /** The wind vane under it: its height and its length (its tail half of it). */
    public static final double VANE_Y = WeatherStationShape.SEAT_Y - 0.6, VANE_LEN = 1.7;
    /** The pylons' rotors over their rings: their height, their blades' length and how many there are. */
    public static final double PYLON_ROTOR_Y = WeatherStationShape.PYLON_RING + 1.35, BLADE = 0.95;
    public static final int BLADES = 4;
    /** The pylons' axes across, and deep. */
    public static final double PYLON_Z = WeatherStationShape.PYLON_Z + 0.5;
    public static final double[] PYLON_X = { SkyRing.MID - WeatherStationShape.PYLON_DX + 0.5,
        SkyRing.MID + WeatherStationShape.PYLON_DX + 0.5 };

    /** The cloud's middle over the mast, its radius and how high it is. */
    public static final double CLOUD_Y = MAST_TOP + 4.6, CLOUD_R = 5.2, CLOUD_H = 2.2;
    /** Its puffs near, mid and far, and the sides each is drawn with at each tier. */
    public static final int[] PUFFS = { 18, 10, 6 }, PUFF_SIDES = { 8, 6, 4 };
    /** The ticks one churn of the cloud takes. */
    public static final int CHURN_T = 2400;
    /** How long before the rain the cloud starts to thicken (ticks), and how thick it is in fair weather. */
    public static final int LEAD = 3600;
    public static final double FAIR = 0.2;
    /** Where the cloud's puffs show: those whose own share is under {@code FAIR_SHOW + (1 - FAIR_SHOW) * density}. */
    public static final double FAIR_SHOW = 0.35;

    /**
     * The flashes: the window of ticks a flash may come in, how many ticks it stutters over, the pieces of its bolt and
     * the chance of one a window in fair weather, as rain gathers, and in a thunderstorm.
     */
    public static final int FLASH_WINDOW = 40, FLASH_TICKS = 6, BOLT_PIECES = 7;
    public static final double CALM_CHANCE = 0.03, GATHER_CHANCE = 0.12, STORM_CHANCE = 0.35;
    /** The ticks of the stutter a flash is lit on. */
    private static final boolean[] LIT = { true, true, false, true, false, true };

    /** The ticks the rotors take for a turn at each wind step (calm, brisk, storm). */
    public static final int[] TURN_T = { 90, 46, 22 };
    /** The densities the wind steps up at. */
    public static final double BRISK = 0.45, STORM = 0.8;

    /** The quads of a glow and of the steel strut of the mast (its four sides). */
    public static final int GLOW_QUADS = 1, STRUT_QUADS = 4;

    private WeatherLook() {}

    // ---- the weather

    /**
     * How thick the cloud is, 0 to 1: {@link #FAIR} in fair weather; thickening over the {@link #LEAD} ticks before the
     * rain the server has coming ({@code rainIn}, the ticks left; negative when none is known or it rains already);
     * full while it rains ({@code rain}, the world's rain strength 0..1) and darker still in a thunderstorm
     * ({@code thunder}, 0..1).
     */
    public static double density(double rain, double thunder, double rainIn) {
        double coming = rainIn < 0 ? 0 : clamp(1 - rainIn / LEAD);
        double d = Math.max(FAIR + (1 - FAIR) * 0.7 * coming, FAIR + (1 - FAIR) * 0.85 * clamp(rain));
        return clamp(d + 0.15 * clamp(thunder));
    }

    /**
     * The world tick the rain is due, as the station tells its clients: the server's countdown ({@code rainTime}, the
     * ticks until the weather turns) from now while it does not rain, -1 while it does. A constant while the countdown
     * runs, since both move a tick a tick.
     */
    public static long rainAt(boolean raining, long now, int rainTime) {
        return raining ? -1 : now + Math.max(0, rainTime);
    }

    /**
     * Whether the forecast told the clients ({@code sent}) must be told again for {@code now}: the rain came, stopped
     * or
     * moved (a command, a night slept through) by more than a few seconds.
     */
    public static boolean forecastChanged(long sent, long now) {
        if (sent < 0 || now < 0) return sent != now;
        return Math.abs(now - sent) > 60;
    }

    /** The wind's step at a density: 0 calm, 1 brisk, 2 storm. */
    public static int wind(double density) {
        return density >= STORM ? 2 : density >= BRISK ? 1 : 0;
    }

    /**
     * How far the anemometer has turned at the world time {@code t} (radians, as seen modulo a cup's share of a turn):
     * a turn every {@link #TURN_T} ticks of its wind's step. Each step is a function of the time alone, so it turns
     * smoothly; it changes its pace only as the weather crosses a step.
     */
    public static double rotor(double t, double density) {
        return 2 * Math.PI * ArcaneLook.frac(t / TURN_T[wind(density)]);
    }

    /** How far the pylons' rotors have turned (radians), the other way and a little slower than the anemometer. */
    public static double pylonRotor(double t, double density, int pylon) {
        return -2 * Math.PI * ArcaneLook.frac(t / (TURN_T[wind(density)] * 1.4) + pylon * 0.37);
    }

    /**
     * Which way the wind vane points (radians from the frame's +x towards +z): swinging slowly with the wind, more
     * restless as the weather worsens (a bounded swing, so a change of weather never jerks it).
     */
    public static double vaneYaw(double t, double density) {
        return 0.8 + 0.7 * StrictMath.sin(t / 1700)
            + 0.3 * StrictMath.sin(t / 530 + 1)
            + 0.18 * density * StrictMath.sin(t * 0.37);
    }

    // ---- the cloud

    /**
     * Puff {@code i} of the cloud ({@code i < PUFFS[0]}) at the world time {@code t} and a density: {x, y, z, size,
     * alpha, shade} in the frame, alpha 0 for one the cloud is too thin to show; shade 0 (light) to 1 (storm-dark).
     * The puffs orbit the cloud's middle, each on its own ring and height, churning slowly; as the cloud thickens more
     * of them show, each fading in, and they grow.
     */
    public static double[] puff(int i, double t, double density) {
        double share = unit(i, 0);
        double ring = 0.35 + 0.65 * StrictMath.sqrt(unit(i, 1)), r = CLOUD_R * ring * (0.75 + 0.25 * density);
        double turn = 2 * Math.PI * (unit(i, 2) + t / (CHURN_T * (0.8 + 0.4 * unit(i, 3))));
        double y = CLOUD_Y + CLOUD_H * (unit(i, 4) - 0.5) * (1.2 - ring * 0.6) + 0.3 * StrictMath.sin(t / 90 + i * 1.7);
        double size = (1.4 + 1.2 * unit(i, 5)) * (0.7 + 0.6 * density) * (1.15 - 0.35 * ring);
        double show = FAIR_SHOW + (1 - FAIR_SHOW) * density;
        double alpha = clamp((show - share) / 0.1) * (0.32 + 0.4 * density);
        double shade = clamp(density * 0.8 + (CLOUD_Y - y) * 0.08 - 0.1);
        return new double[] { MAST_X + r * StrictMath.cos(turn), y, MAST_Z + r * StrictMath.sin(turn), size, alpha,
            shade };
    }

    /** The puffs drawn at a tier: the first {@link #PUFFS} of the cloud's. */
    public static int puffs(int tier) {
        return PUFFS[tier];
    }

    // ---- the lightning

    /**
     * The flash lit at the world time {@code t} for a station of seed {@code seed}, or -1 when none is: the window it
     * belongs to (a flash comes at most once a {@link #FLASH_WINDOW} ticks, by a chance that grows as rain gathers and
     * in a storm, stuttering over {@link #FLASH_TICKS} ticks).
     */
    public static long flash(long seed, double t, double density, double thunder) {
        long w = (long) Math.floor(t / FLASH_WINDOW);
        double chance = thunder > 0.3 ? STORM_CHANCE : density >= BRISK ? GATHER_CHANCE : CALM_CHANCE;
        if (Mix.unit(seed, w, 1) >= chance) return -1;
        double start = w * (double) FLASH_WINDOW + Math.floor(Mix.unit(seed, w, 2) * (FLASH_WINDOW - FLASH_TICKS));
        int k = (int) Math.floor(t - start);
        if (k < 0 || k >= FLASH_TICKS || !LIT[k]) return -1;
        return w;
    }

    /**
     * The bolt of the flash of window {@code w}: {@link #BOLT_PIECES} + 1 points {x, y, z} in the frame, jagged. Most
     * strike down from inside the cloud onto the mast's top (the anemometer is its lightning rod); the rest run across
     * inside the cloud.
     */
    public static double[][] bolt(long seed, long w) {
        double a = 2 * Math.PI * Mix.unit(seed, w, 3), r = CLOUD_R * 0.55 * Mix.unit(seed, w, 4);
        double[] from = { MAST_X + r * StrictMath.cos(a), CLOUD_Y - 0.4, MAST_Z + r * StrictMath.sin(a) };
        double[] to;
        if (Mix.unit(seed, w, 5) < 0.65) to = new double[] { MAST_X, MAST_TOP + 0.15, MAST_Z };
        else {
            double b = a + Math.PI * (0.6 + 0.8 * Mix.unit(seed, w, 6));
            to = new double[] { MAST_X + CLOUD_R * 0.7 * StrictMath.cos(b), CLOUD_Y - 0.2 - Mix.unit(seed, w, 7),
                MAST_Z + CLOUD_R * 0.7 * StrictMath.sin(b) };
        }
        double[][] out = new double[BOLT_PIECES + 1][];
        double len = StrictMath.sqrt(sq(to[0] - from[0]) + sq(to[1] - from[1]) + sq(to[2] - from[2]));
        for (int k = 0; k <= BOLT_PIECES; k++) {
            double u = k / (double) BOLT_PIECES, jag = k == 0 || k == BOLT_PIECES ? 0 : 0.16 * len;
            out[k] = new double[] { from[0] + (to[0] - from[0]) * u + jag * (Mix.unit(seed, w, 10 + 3 * k) - 0.5),
                from[1] + (to[1] - from[1]) * u + jag * 0.5 * (Mix.unit(seed, w, 11 + 3 * k) - 0.5),
                from[2] + (to[2] - from[2]) * u + jag * (Mix.unit(seed, w, 12 + 3 * k) - 0.5) };
        }
        return out;
    }

    // ---- the mast and the rotors

    /**
     * The anemometer's cups at a turn: {x, z} of each cup's middle round the mast's axis at {@link #ROTOR_Y}, the arms
     * from the axis to them.
     */
    public static List<double[]> cups(double turn) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < CUPS; i++) {
            double a = turn + i * 2 * Math.PI / CUPS;
            out.add(new double[] { MAST_X + ARM * StrictMath.cos(a), MAST_Z + ARM * StrictMath.sin(a) });
        }
        return out;
    }

    /** The tips {x, z} of a pylon's blades at a turn, round its axis at {@link #PYLON_ROTOR_Y}. */
    public static List<double[]> blades(int pylon, double turn) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < BLADES; i++) {
            double a = turn + i * 2 * Math.PI / BLADES;
            out.add(new double[] { PYLON_X[pylon] + BLADE * StrictMath.cos(a), PYLON_Z + BLADE * StrictMath.sin(a) });
        }
        return out;
    }

    // ---- the budget

    /** The quads of a line {@code length} long, in the 8-block pieces the renderer cuts it into. */
    private static int line(double length) {
        return DomeLook.pieces(length);
    }

    /** The quads of the mast, its rotors and its vane near and mid: none far. */
    public static int mastQuads(int tier) {
        if (tier == ModuleLook.FAR) return 0;
        boolean near = tier == ModuleLook.NEAR;
        // the strut and the line up it; the cups' arms, near with the cups and a glow on the hub; the vane (its shaft
        // and its tail); each pylon's blades, near with a glow on the hub
        int n = STRUT_QUADS + line(MAST_TOP - MAST_FOOT);
        n += CUPS * (near ? 2 : 1) + (near ? GLOW_QUADS : 0);
        n += 2;
        n += PYLON_X.length * (BLADES + (near ? GLOW_QUADS : 0));
        return n;
    }

    /** The quads of the cloud at a tier: each of its puffs as a soft fan of its sides. */
    public static int cloudQuads(int tier) {
        return PUFFS[tier] * PUFF_SIDES[tier];
    }

    /**
     * The quads of a flash while it is lit: its bolt's pieces, near and mid a halo along them, and the cloud's glow.
     */
    public static int flashQuads(int tier) {
        return BOLT_PIECES * (tier == ModuleLook.FAR ? 1 : 2) + GLOW_QUADS;
    }

    /**
     * The most quads the station's own drawing takes a frame at a tier ({@link ModuleLook#tier}), with a flash lit:
     * the mast and its rotors, the cloud and the flash. The shared sky drawing ({@link SkyLook#quads}) and the drawing
     * of
     * every city module ({@link ModuleLook#baseQuads}) come on top.
     */
    public static int quads(int tier) {
        return mastQuads(tier) + cloudQuads(tier) + flashQuads(tier);
    }

    /** The most quads its landmark takes past the effects' range: the cloud as far, and a flash. */
    public static int landmarkQuads() {
        return cloudQuads(ModuleLook.FAR) + flashQuads(ModuleLook.FAR);
    }

    private static double unit(int i, int k) {
        return Mix.unit(0x57EA7E2L, i, k);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double sq(double d) {
        return d * d;
    }
}
