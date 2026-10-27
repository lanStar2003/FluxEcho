package com.fluxecho.logic.skin;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;

import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.ShapeKit;

/**
 * Which look each shape of each building is drawn with (style §3.9.9): by (building, shape name), never by the building
 * alone, so a building still standing as an old shape keeps its old look after its new shape has come.
 * <p>
 * Every shape of 0.11–0.16 ({@link #OLD_SHAPES}: the fifteen modules, the nexus's five phases, the library's Archive
 * and 0.9.2 hall) is {@link #LEGACY}: no variants, no developing, its accent only on its old glowing parts. Its lamp
 * windows are those parts (refactor §5.6): the pilasters (deck 9, {@code panel_lit}), the cornices (deck 11) and every
 * fitting the old renderer draws a glow overlay on; a building whose always-lit glow outshone 3% of its outer surface
 * by day took those parts into its lamps too ({@link #EXTRA}, derived in {@code LegacyGlowShareTest}). The cells of a
 * shape that are lamps are its mask ({@link #legacyMask}): the client rebuilds only their box when the lamps flip.
 */
public final class SkinProfiles {

    /** The old buildings' look (0.11–0.16): no variants, no developing, the accent on the old glowing parts. */
    public static final Profile LEGACY = new Profile("legacy", false, false);
    /**
     * A shape of the new materials (style §4): it develops, its materials carry variants. The buildings' versions (0.19
     * on) give their shapes their own profiles ({@link #register}); this is what they start from.
     */
    public static final Profile NEW_SHAPE = new Profile("new", true, true);

    /** The shapes of 0.16.0, {@code key:shape}: every one is {@link #LEGACY}. */
    public static final List<String> OLD_SHAPES = Collections.unmodifiableList(
        Arrays.asList(
            "arcane_court:main",
            "blood_covenant:main",
            "dome_generator:moored",
            "dome_generator:floating",
            "dome_generator:moored_36",
            "dome_generator:moored_32",
            "dome_generator:moored_28",
            "droplet_hangar:main",
            "dual_foil_vault:main",
            "eco_dome:main",
            "holo_table:main",
            "hunting_ground:main",
            "library:main",
            "library:hall",
            "mana_fountain:main",
            "nexus:phase1",
            "nexus:phase2",
            "nexus:phase3",
            "nexus:phase4",
            "nexus:phase5",
            "observatory:main",
            "resonance_gate:main",
            "root:well",
            "root:well_20",
            "root:well_14",
            "root:well_8",
            "root:root",
            "root:root_7",
            "root:root_in",
            "root:root_in_20",
            "root:root_in_14",
            "root:root_in_8",
            "sky_sails:main",
            "sophon_array:main",
            "weather_station:main"));

    /** The lamp parts of every old building, which its accent tints when lit. */
    private static final boolean[] LAMPS = new boolean[256];
    /** The parts an old building took into its lamps besides, by its key; they keep their own colour. */
    private static final Map<String, int[]> EXTRA = new HashMap<>();
    /** The parts that are a lamp of some building. */
    private static final boolean[] MAY = new boolean[256];

    static {
        LAMPS[Parts.deck(Parts.D_PANEL_LIT)] = true;
        LAMPS[Parts.deck(Parts.D_CORNICE)] = true;
        // every fitting 0.16's FittingRender draws a glow overlay on: the ones that give light (not the glazing)
        for (int m = 0; m < Parts.FITTING_TYPES; m++) if (Parts.FITTING_LIGHT[m] > 0) LAMPS[Parts.fitting(m)] = true;
        // over 3% by day without (LegacyGlowShareTest): the nexus's spokes and rings, the hall's lit foundation, the
        // sky platforms' floating foundation
        EXTRA.put("nexus", new int[] { Parts.frame(Parts.FR_BASE_LIT), Parts.frame(Parts.FR_RING) });
        EXTRA.put("library", new int[] { Parts.frame(Parts.FR_FOUNDATION) });
        for (String sky : new String[] { "droplet_hangar", "observatory", "sky_sails", "weather_station" })
            EXTRA.put(sky, new int[] { Parts.frame(Parts.FR_FLOAT) });
        System.arraycopy(LAMPS, 0, MAY, 0, LAMPS.length);
        for (int[] extra : EXTRA.values()) for (int p : extra) MAY[p] = true;
    }

    private static final Map<String, Profile> REGISTERED = new HashMap<>();
    private static final Map<String, Mask> MASKS = new ConcurrentHashMap<>();

    /** The masks worked out so far, by {@code key:shape} (each once, from its blueprint). */
    public static final Map<String, Mask> LEGACY_MASKS = Collections.unmodifiableMap(MASKS);

    private SkinProfiles() {}

    /** The look of a building's shape: {@link #LEGACY} for the old shapes and for any shape no version gave a look. */
    public static Profile profile(String key, String shape) {
        String id = key + ":" + shape;
        synchronized (REGISTERED) {
            Profile p = REGISTERED.get(id);
            if (p != null) return p;
        }
        return LEGACY;
    }

    /**
     * Gives a new shape its look (a building's version, 0.19 on).
     *
     * @throws IllegalArgumentException for an old shape, which stays {@link #LEGACY}
     */
    public static void register(String key, String shape, Profile p) {
        String id = key + ":" + shape;
        if (OLD_SHAPES.contains(id)) throw new IllegalArgumentException(id + " is an old shape");
        synchronized (REGISTERED) {
            REGISTERED.put(id, p);
        }
    }

    /** Whether the part is a lamp window of the old building. */
    public static boolean isLamp(String key, int part) {
        if (part < 0 || part >= LAMPS.length) return false;
        if (LAMPS[part]) return true;
        int[] extra = key == null ? null : EXTRA.get(key);
        if (extra != null) for (int p : extra) if (p == part) return true;
        return false;
    }

    /** Whether the part is a lamp of some old building: a renderer asks the table only for these. */
    public static boolean mayBeLamp(int part) {
        return part >= 0 && part < MAY.length && MAY[part];
    }

    /** Whether the building's accent tints the part's glow when lit (style §3.9.9); a taken-in part keeps its own. */
    public static boolean tinted(int part) {
        return part >= 0 && part < LAMPS.length && LAMPS[part];
    }

    // ---- the masks

    /**
     * The part a character of an old shape's blueprint is built as, by the structure's own table: the nexus's frame and
     * lift pad ({@code TileNexus}), the library's Archive ({@link ArchiveShape#partOf}) and 0.9.2 hall
     * ({@code TileLibrary}), the city legend ({@link ShapeKit#partOf}) for the modules; -1 for a space, the controller
     * and a character the table does not have.
     */
    public static int partOf(String key, String shape, char ch) {
        if (ch == ' ' || ch == ShapeKit.CONTROLLER) return -1;
        if (ch == ShapeKit.AIR) return Parts.AIR;
        try {
            if ("nexus".equals(key)) return nexus(ch);
            if ("library".equals(key)) return "hall".equals(shape) ? hall(ch) : ArchiveShape.partOf(ch);
            return ShapeKit.partOf(ch);
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    private static int nexus(char ch) {
        switch (ch) {
            case 'B':
                return Parts.frame(Parts.FR_BASE);
            case 'G':
                return Parts.frame(Parts.FR_BASE_LIT);
            case 'P':
                return Parts.frame(Parts.FR_PILLAR);
            case 'C':
                return Parts.frame(Parts.FR_CONDUIT);
            case 'R':
                return Parts.frame(Parts.FR_RING);
            case 'S':
                return Parts.frame(Parts.FR_SEAT);
            case 'K':
                return Parts.frame(Parts.FR_CONSOLE);
            case 'V':
                return Parts.fitting(Parts.F_LIFT);
            default:
                return -1;
        }
    }

    private static int hall(char ch) {
        switch (ch) {
            case 'F':
                return Parts.frame(Parts.FR_FOUNDATION);
            case 'P':
                return Parts.frame(Parts.FR_PILLAR);
            case 'H':
                return Parts.frame(Parts.FR_SHELF);
            case 'B':
                return Parts.frame(Parts.FR_BASE);
            case 'K':
                return Parts.frame(Parts.FR_CONSOLE);
            default:
                return -1;
        }
    }

    /**
     * The lamp cells of a building's shape, from its blueprint (worked out once a shape, then kept in
     * {@link #LEGACY_MASKS}).
     */
    public static Mask legacyMask(String key, String shape, Blueprint bp) {
        return MASKS.computeIfAbsent(key + ":" + shape, id -> Mask.of(key, shape, bp));
    }

    /** A shape's lamp cells, {across, layer from the top, row} in its blueprint, in the blueprint's order. */
    public static final class Mask {

        /** The cells packed: across in bits 0–10, the layer in 11–21, the row in 22–32. */
        private final long[] cells;
        private final int[] bounds;
        private final long crc;

        private Mask(long[] cells) {
            this.cells = cells;
            CRC32 c = new CRC32();
            add(c, cells.length);
            int[] b = null;
            for (int i = 0; i < cells.length; i++) {
                int[] p = cell(i);
                for (int v : p) add(c, v);
                if (b == null) b = new int[] { p[0], p[1], p[2], p[0], p[1], p[2] };
                for (int k = 0; k < 3; k++) {
                    b[k] = Math.min(b[k], p[k]);
                    b[k + 3] = Math.max(b[k + 3], p[k]);
                }
            }
            bounds = b;
            crc = c.getValue();
        }

        static Mask of(String key, String shape, Blueprint bp) {
            int w = bp.width(), h = bp.height(), d = bp.depth();
            long[] buf = new long[16];
            int n = 0;
            for (int b = 0; b < h; b++) for (int c = 0; c < d; c++) for (int a = 0; a < w; a++) {
                int part = partOf(key, shape, bp.at(a, b, c));
                if (!isLamp(key, part)) continue;
                if (n == buf.length) buf = Arrays.copyOf(buf, n * 2);
                buf[n++] = a | (long) b << 11 | (long) c << 22;
            }
            return new Mask(Arrays.copyOf(buf, n));
        }

        public int size() {
            return cells.length;
        }

        /** The i-th cell {across, layer from the top, row}. */
        public int[] cell(int i) {
            long k = cells[i];
            return new int[] { (int) (k & 0x7FF), (int) (k >> 11 & 0x7FF), (int) (k >> 22 & 0x7FF) };
        }

        /** The box of the cells {a0, b0, c0, a1, b1, c1} in the blueprint, or null for a shape without lamps. */
        public int[] bounds() {
            return bounds == null ? null : bounds.clone();
        }

        /** The CRC-32 of the count and every cell's three numbers (four bytes each, low first). */
        public long crc() {
            return crc;
        }

        private static void add(CRC32 crc, int v) {
            for (int i = 0; i < 4; i++) crc.update(v >>> 8 * i & 255);
        }
    }
}
