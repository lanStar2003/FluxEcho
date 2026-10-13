package com.fluxecho.city;

import com.fluxecho.logic.ArcaneCourtShape;
import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.UnitLayout;

/**
 * 秘法庭 The Arcane Court (0.13.0, CITY.md §4.3): the first pavilion, an octagonal hall under a steep hollow spire on a
 * diagonal site (it prefers site 7, beside the gate), entered by four chamfer portals and run from the altar at its
 * heart. Its units are the infusion circle (eight sockets), four cauldron alcoves, eight aspect pillars (three sockets
 * each) and eight research desks: 21 units, 44 slots (in the structures phase any item). Its shape is
 * {@link ArcaneCourtShape}; once it forms the finial's conduits and seat ({@code C}, {@code Z}) give way to the
 * rendered
 * crystal, and players pass through them (they stand on the spire's cap, out of everyone's reach). TE id
 * {@code fluxecho:arcane_court}, core {@code fluxecho:module_core} meta 2.
 */
public class TileArcaneCourt extends TileCityModule {

    /** Its accent colour, drawn in light only (CITY.md §1.1). */
    public static final int COLOR = 0xC77DFF;

    @Override
    public String moduleKey() {
        return ArcaneCourtShape.KEY;
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    public CityBuild build() {
        return ArcaneCourtShape.BUILD;
    }

    @Override
    public UnitLayout layout() {
        return ArcaneCourtShape.UNITS;
    }

    @Override
    public int coreMetaOf() {
        return ModuleCores.ARCANE_COURT;
    }

    @Override
    protected String dissolves() {
        return ArcaneCourtShape.DISSOLVES;
    }

    @Override
    protected String passes() {
        return ArcaneCourtShape.PASSES;
    }
}
