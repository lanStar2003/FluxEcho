package com.fluxecho.city;

import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.HoloTableShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.UnitLayout;

/**
 * 全息沙盘 The Holo Sand Table (0.15.0, CITY.md §4.11): the second building on the outer diagonals (it prefers O7, site
 * 17), a rotunda under a stepped shallow dome round a round table under a hanging ring light, with a double ramp up
 * the drum to a back gallery, entered by four chamfer portals and run from the console at the table's front. Its units
 * are the table (nine slots for map cards and saved views) and nine layer consoles without slots, each of which opens
 * the window on its layer: 10 units, 9 slots (in the structures phase any item). Its shape is {@link HoloTableShape};
 * once it forms the ring light's segments ({@code R}) give way to the rendered light ring, and players pass through
 * them (they hang over the table, out of everyone's reach). TE id {@code fluxecho:holo_table}, core
 * {@code fluxecho:module_core} meta 10.
 */
public class TileHoloTable extends TileCityModule {

    /** Its accent colour, drawn in light only (CITY.md §1.1). */
    public static final int COLOR = 0x66F0FF;

    @Override
    public String moduleKey() {
        return HoloTableShape.KEY;
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    public CityBuild build() {
        return HoloTableShape.BUILD;
    }

    @Override
    public UnitLayout layout() {
        return HoloTableShape.UNITS;
    }

    @Override
    public int coreMetaOf() {
        return ModuleCores.HOLO_TABLE;
    }

    @Override
    protected String dissolves() {
        return HoloTableShape.DISSOLVES;
    }

    @Override
    protected String passes() {
        return HoloTableShape.PASSES;
    }

    /** The plan page's gallery: the back gallery over the drum's ramps, where one stands on its deck at y6. */
    @Override
    public int[] planLevels() {
        return HoloTableShape.planLevels();
    }
}
