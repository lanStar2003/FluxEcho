package com.fluxecho.logic.skin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fluxecho.city.TileCityModule;
import com.fluxecho.frame.Formed;
import com.fluxecho.library.TileLibrary;
import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.Parts;
import com.fluxecho.nexus.TileModule;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.save.IdExport;

/**
 * Every shape a building of 0.16.0 may stand as (the 35 of {@code OldShapesCrcTest}), read from the structure tiles
 * themselves: its blueprint, the part each character is built as by the tile's own table (a city module's
 * {@link CityBuild#part}, the nexus's and the library's structure parts written out below as their tiles list them),
 * and which of its cells give way to the formed building's drawing ({@link Formed#HIDE}). The masks and the glow shares
 * of the old buildings are worked out from these.
 */
final class OldShapes {

    /** The part code a controller cell stands for here: a core block, opaque, without a glow overlay. */
    static final int CORE = -100;

    /** One old shape. */
    static final class Shape {

        final String structure, key, name;
        final Blueprint bp;
        /** The part of every cell [across][layer from the top][row], -1 for a space. */
        final int[][][] part;
        /** Whether the cell gives way to the formed building's drawing. */
        final boolean[][][] hidden;

        Shape(String structure, String key, String name, Blueprint bp, int[][][] part, boolean[][][] hidden) {
            this.structure = structure;
            this.key = key;
            this.name = name;
            this.bp = bp;
            this.part = part;
            this.hidden = hidden;
        }

        String id() {
            return key + ":" + name;
        }
    }

    private static List<Shape> all;

    private OldShapes() {}

    static synchronized List<Shape> all() {
        if (all != null) return all;
        List<Shape> out = new ArrayList<>();
        try {
            Method flags = TileMultiblock.class.getDeclaredMethod("flags", char.class, int.class, int.class, int.class);
            flags.setAccessible(true);
            for (Map.Entry<String, Class<? extends TileMultiblock>> e : IdExport.structures()
                .entrySet()) {
                TileMultiblock probe = IdExport.make(e.getValue());
                for (TileMultiblock.Shape s : IdExport.shapes(probe)) {
                    TileMultiblock t = IdExport.make(e.getValue());
                    t.expectShape(s.name);
                    String key = t instanceof TileModule m ? m.moduleKey() : "nexus";
                    Blueprint bp = s.bp;
                    int w = bp.width(), h = bp.height(), d = bp.depth();
                    int[][][] part = new int[w][h][d];
                    boolean[][][] hidden = new boolean[w][h][d];
                    for (int a = 0; a < w; a++) for (int b = 0; b < h; b++) for (int c = 0; c < d; c++) {
                        char ch = bp.at(a, b, c);
                        int p = partOf(t, s.name, ch, a, b, c);
                        part[a][b][c] = p;
                        if (p == CORE || p >= 0 && p != Parts.AIR && p != Parts.WATER) {
                            int[] at = t.cellPos(a, b, c);
                            hidden[a][b][c] = ((Integer) flags.invoke(t, ch, at[0], at[1], at[2]) & Formed.HIDE) != 0;
                        }
                    }
                    out.add(new Shape(e.getKey(), key, s.name, bp, part, hidden));
                }
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        all = Collections.unmodifiableList(out);
        return all;
    }

    /** The part a cell is built as, by the structure's own table; -1 for a space, {@link #CORE} for the controller. */
    static int partOf(TileMultiblock t, String shape, char ch, int a, int b, int c) {
        if (ch == ' ') return -1;
        if (ch == '~') return CORE;
        if (ch == '-') return Parts.AIR;
        if (t instanceof TileCityModule cm) {
            CityBuild build = cm.build();
            int[] l = build.localOf(a, b, c);
            return build.part(l[0], l[1], l[2], ch);
        }
        if (t instanceof TileNexus) return nexus(ch);
        if (t instanceof TileLibrary) return TileLibrary.HALL.equals(shape) ? hall(ch) : ArchiveShape.partOf(ch);
        throw new IllegalStateException("no part table for " + t);
    }

    /** {@code TileNexus.parts()}: the frame's base, lit base, pillar, conduit, ring, seat, console and the lift pad. */
    static int nexus(char ch) {
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
                throw new IllegalArgumentException("not a nexus character: '" + ch + "'");
        }
    }

    /** {@code TileLibrary.parts()} for the 0.9.2 hall: foundation, pillar, shelf, base, console. */
    static int hall(char ch) {
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
                throw new IllegalArgumentException("not a hall character: '" + ch + "'");
        }
    }
}
