package com.fluxecho.logic.skin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.CRC32;

import com.fluxecho.logic.Parts;

/**
 * The generator of the old buildings' lamp masks (refactor §5.6, {@code LEGACY_MASKS}), run in the tests: from every
 * old shape's character map ({@link OldShapes}) it takes the cells of the parts that are lamp windows (the pilasters,
 * deck 9; the cornices, deck 11; every fitting 0.16's {@code FittingRender} draws a glow overlay on, all but the
 * glazing) and, for a building whose always-lit glow outshone 3% of its outer surface by day, the parts it takes in
 * besides ({@link #EXTRA}). Its result is what the runtime's {@link SkinProfiles#legacyMask} must give, and its
 * checksums are pinned in {@link LegacyGlowShareTest}.
 */
final class LegacyMaskGen {

    /** The lamp parts every old building has: refactor §5.6, the fittings listed by the code (the light table). */
    static final Set<Integer> LAMPS = new TreeSet<>();

    static {
        LAMPS.add(Parts.deck(Parts.D_PANEL_LIT));
        LAMPS.add(Parts.deck(Parts.D_CORNICE));
        for (int m = 0; m < Parts.FITTING_TYPES; m++) if (Parts.FITTING_LIGHT[m] > 0) LAMPS.add(Parts.fitting(m));
    }

    /**
     * The parts taken into a building's mask besides, by its key: {@link LegacyGlowShareTest} derives them (the fewest
     * always-lit parts, largest first, that bring every shape of the building to 3% by day) and checks this table.
     */
    static final Map<String, int[]> EXTRA = new LinkedHashMap<>();

    static {
        EXTRA.put("nexus", new int[] { Parts.frame(Parts.FR_BASE_LIT), Parts.frame(Parts.FR_RING) });
        EXTRA.put("library", new int[] { Parts.frame(Parts.FR_FOUNDATION) });
        for (String sky : new String[] { "droplet_hangar", "observatory", "sky_sails", "weather_station" })
            EXTRA.put(sky, new int[] { Parts.frame(Parts.FR_FLOAT) });
    }

    private LegacyMaskGen() {}

    /** Whether the part is a lamp of the building. */
    static boolean lamp(String key, int part) {
        if (LAMPS.contains(part)) return true;
        int[] extra = EXTRA.get(key);
        return extra != null && Arrays.stream(extra)
            .anyMatch(p -> p == part);
    }

    /** The mask's cells {across, layer from the top, row}, in the blueprint's order (layer, row, across). */
    static List<int[]> cells(OldShapes.Shape s) {
        List<int[]> out = new ArrayList<>();
        int w = s.bp.width(), h = s.bp.height(), d = s.bp.depth();
        for (int b = 0; b < h; b++) for (int c = 0; c < d; c++) for (int a = 0; a < w; a++)
            if (s.part[a][b][c] >= 0 && lamp(s.key, s.part[a][b][c])) out.add(new int[] { a, b, c });
        return out;
    }

    /** The checksum the tests pin: the count, then every cell's three coordinates. */
    static long crc(List<int[]> cells) {
        CRC32 crc = new CRC32();
        add(crc, cells.size());
        for (int[] c : cells) for (int v : c) add(crc, v);
        return crc.getValue();
    }

    private static void add(CRC32 crc, int v) {
        for (int i = 0; i < 4; i++) crc.update(v >>> 8 * i & 255);
    }
}
