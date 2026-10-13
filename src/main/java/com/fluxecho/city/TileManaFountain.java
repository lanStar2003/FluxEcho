package com.fluxecho.city;

import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.ManaFountainShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.UnitLayout;

/**
 * 魔力泉阵 The Mana Fountain Array (0.13.0, CITY.md §4.6): a pavilion, a cloister round an open court on a diagonal
 * site (it prefers site 1, the welcoming garden beside the gate), entered by four chamfer portals and run from the
 * obelisk at the court's centre. Its units are eight fountains round the obelisk and eight flower urns inside the
 * colonnade: 16 units, 16 slots (in the structures phase any item). Its shape is {@link ManaFountainShape}; once it
 * forms the obelisk's conduits and seat ({@code C}, {@code Z}) give way to the rendered mana spout, and players pass
 * through them (they stand on the controller, over everyone's head). TE id {@code fluxecho:mana_fountain}, core
 * {@code fluxecho:module_core} meta 5.
 */
public class TileManaFountain extends TileCityModule {

    /** Its accent colour, drawn in light only (CITY.md §1.1). */
    public static final int COLOR = 0x6AA8FF;

    @Override
    public String moduleKey() {
        return ManaFountainShape.KEY;
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    public CityBuild build() {
        return ManaFountainShape.BUILD;
    }

    @Override
    public UnitLayout layout() {
        return ManaFountainShape.UNITS;
    }

    @Override
    public int coreMetaOf() {
        return ModuleCores.MANA_FOUNTAIN;
    }

    @Override
    protected String dissolves() {
        return ManaFountainShape.DISSOLVES;
    }

    @Override
    protected String passes() {
        return ManaFountainShape.PASSES;
    }
}
