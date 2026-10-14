package com.fluxecho.city;

import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.SkySailsShape;
import com.fluxecho.logic.UnitLayout;

/**
 * 天光帆阵 The Sky Sail Array (0.16.0, CITY.md §4.12): the sky ring's module on S2 (site 32), or S6, a bridge cabin on a
 * floating platform with six masts behind it in three pairs, between which three huge rendered light sails spread,
 * turned toward the sun. Its units are the six masts' plinths and sockets, one slot each (a resonant-glass sail; in the
 * structures phase any item). Its shape is {@link SkySailsShape}; nothing of it dissolves. TE id
 * {@code fluxecho:sky_sails}, core {@code fluxecho:module_core} meta 11.
 */
public class TileSkySails extends TileCityModule {

    /** Its accent colour, drawn in light only (CITY.md §1.1). */
    public static final int COLOR = 0xFFD27A;

    @Override
    public String moduleKey() {
        return SkySailsShape.KEY;
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    public CityBuild build() {
        return SkySailsShape.BUILD;
    }

    @Override
    public UnitLayout layout() {
        return SkySailsShape.UNITS;
    }

    @Override
    public int coreMetaOf() {
        return ModuleCores.SKY_SAILS;
    }

    @Override
    protected String dissolves() {
        return SkySailsShape.DISSOLVES;
    }

    @Override
    protected String passes() {
        return SkySailsShape.PASSES;
    }
}
