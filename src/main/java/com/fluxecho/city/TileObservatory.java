package com.fluxecho.city;

import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.ObservatoryShape;
import com.fluxecho.logic.UnitLayout;

/**
 * 观星台 The Observatory (0.16.0, CITY.md §4.12): the sky ring's module over the Archive (S4, site 34), a vestibule on a
 * floating platform opening into an octagonal observing room under a stepped dome with a glazed meridian slit, the
 * telescope pier in the middle and seven lens cases round the wall. Its units are the seven lens cases, one slot each
 * (in the structures phase any item). Its shape is {@link ObservatoryShape}; once it forms the whole pier, its console,
 * pillars and seat, gives way to the rendered telescope under a floating ring lens, and players pass through it; the
 * kit portal's console under the controller, of the same character, keeps its block ({@link #dissolvesAt}). TE id
 * {@code fluxecho:observatory}, core {@code fluxecho:module_core} meta 12.
 */
public class TileObservatory extends TileCityModule {

    /** Its accent colour, drawn in light only (CITY.md §1.1). */
    public static final int COLOR = 0xB9C8FF;

    @Override
    public String moduleKey() {
        return ObservatoryShape.KEY;
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    public CityBuild build() {
        return ObservatoryShape.BUILD;
    }

    @Override
    public UnitLayout layout() {
        return ObservatoryShape.UNITS;
    }

    @Override
    public int coreMetaOf() {
        return ModuleCores.OBSERVATORY;
    }

    @Override
    protected String dissolves() {
        return ObservatoryShape.DISSOLVES;
    }

    @Override
    protected String passes() {
        return ObservatoryShape.PASSES;
    }

    /** Only the pier's column dissolves: the portal's console under the controller stays. */
    @Override
    protected boolean dissolvesAt(int x, int y, int z) {
        return ObservatoryShape.pier(x, y, z);
    }
}
