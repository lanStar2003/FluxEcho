package com.fluxecho.city;

import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.EcoDomeShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.UnitLayout;

/**
 * 生态穹 The Eco Dome (0.11.0, CITY.md §4.1): a greenhouse dome over a garden with floating islands round a world tree,
 * twelve planters of three sockets each (36 samples: bees, saplings, seeds, butterflies; in the structures phase any
 * item). Its shape is {@link EcoDomeShape}; once it forms the tree's trunk ({@code C}) and seat ({@code Z}) give way to
 * the rendered tree of light. The trunk keeps its collision ({@link EcoDomeShape#PASSES}): it runs through the floating
 * island's and the crown island's decks and is the newel of the spiral between them, so a trunk players passed through
 * would leave a shaft down to the pond. TE id {@code fluxecho:eco_dome}, core {@code fluxecho:module_core} meta 0.
 */
public class TileEcoDome extends TileCityModule {

    /** Its accent colour, drawn in light only (CITY.md §1.1). */
    public static final int COLOR = 0x7BE08A;

    @Override
    public String moduleKey() {
        return EcoDomeShape.KEY;
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    public CityBuild build() {
        return EcoDomeShape.BUILD;
    }

    @Override
    public UnitLayout layout() {
        return EcoDomeShape.UNITS;
    }

    @Override
    public int coreMetaOf() {
        return ModuleCores.ECO_DOME;
    }

    @Override
    protected String dissolves() {
        return EcoDomeShape.DISSOLVES;
    }

    @Override
    protected String passes() {
        return EcoDomeShape.PASSES;
    }
}
