package com.fluxecho.city;

import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.ResonanceGateShape;
import com.fluxecho.logic.UnitLayout;

/**
 * 谐振塔 The Resonance Gate (0.14.0, CITY.md §4.7): the city's gatehouse on the gate (site 0), two wing towers either side
 * of the gate road joined by a bridge over its open passage, run from the kiosk in the road under the bridge. Its units
 * are six resonator columns in the resonance chamber on the bridge and four tuning racks in the wings' ground halls: 10
 * units, 24 slots (in the structures phase any item). Its shape is {@link ResonanceGateShape}; once it forms the spire
 * core's conduits and seat ({@code C}, {@code Z}) give way to the rendered floating crystal spire, and players pass
 * through them. TE id {@code fluxecho:resonance_gate}, core {@code fluxecho:module_core} meta 6.
 */
public class TileResonanceGate extends TileCityModule {

    /** Its accent colour, drawn in light only (CITY.md §1.1). */
    public static final int COLOR = 0x5CF2C2;

    @Override
    public String moduleKey() {
        return ResonanceGateShape.KEY;
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    public CityBuild build() {
        return ResonanceGateShape.BUILD;
    }

    @Override
    public UnitLayout layout() {
        return ResonanceGateShape.UNITS;
    }

    @Override
    public int coreMetaOf() {
        return ModuleCores.RESONANCE_GATE;
    }

    @Override
    protected String dissolves() {
        return ResonanceGateShape.DISSOLVES;
    }

    @Override
    protected String passes() {
        return ResonanceGateShape.PASSES;
    }
}
