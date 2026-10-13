package com.fluxecho.city;

import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.HuntingGroundShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.UnitLayout;

/**
 * 回响猎场 The Echo Hunting Ground (0.13.0, CITY.md §4.5): a pavilion, an open octagonal amphitheatre on a diagonal
 * site (it prefers site 3, behind the nexus on its right), entered by four chamfer portals and vomitoria under its
 * terraces, run from the holo emitter at the arena's centre. Its units are twelve imprint consoles on the arena's rim:
 * 12 units, 12 slots (in the structures phase any item). Its shape is {@link HuntingGroundShape}; once it forms the
 * velarium's ring segments ({@code R}) give way to the rendered canopy, and players pass through them (they hang over
 * the top terraces, out of everyone's reach). TE id {@code fluxecho:hunting_ground}, core {@code fluxecho:module_core}
 * meta 4.
 */
public class TileHuntingGround extends TileCityModule {

    /** Its accent colour, drawn in light only (CITY.md §1.1). */
    public static final int COLOR = 0xFF9A3C;

    @Override
    public String moduleKey() {
        return HuntingGroundShape.KEY;
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    public CityBuild build() {
        return HuntingGroundShape.BUILD;
    }

    @Override
    public UnitLayout layout() {
        return HuntingGroundShape.UNITS;
    }

    @Override
    public int coreMetaOf() {
        return ModuleCores.HUNTING_GROUND;
    }

    @Override
    protected String dissolves() {
        return HuntingGroundShape.DISSOLVES;
    }

    @Override
    protected String passes() {
        return HuntingGroundShape.PASSES;
    }
}
