package com.fluxecho.city;

import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.DualFoilVaultShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.UnitLayout;

/**
 * 二向箔仓 The Dual-foil Vault (0.14.0, CITY.md §4.2): a windowless monolith on a hall site whose front is one giant
 * canvas; inside, a gallery nave whose long walls carry three tiers of canvas units under catwalks, and the foil on the
 * back wall. Its units are the 42 canvases and the foil, one slot each: 43 units, 43 slots (in the structures phase any
 * item; bulk storage painted into the canvases and the foil's destroy mode come with its function). Its shape is
 * {@link DualFoilVaultShape}; nothing dissolves (the paintings on the canvases are drawn over the blocks). TE id
 * {@code fluxecho:dual_foil_vault}, core {@code fluxecho:module_core} meta 1.
 */
public class TileDualFoilVault extends TileCityModule {

    /** Its accent colour, parchment, drawn in light only (CITY.md §1.1). */
    public static final int COLOR = 0xF2EBDD;

    @Override
    public String moduleKey() {
        return DualFoilVaultShape.KEY;
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    public CityBuild build() {
        return DualFoilVaultShape.BUILD;
    }

    @Override
    public UnitLayout layout() {
        return DualFoilVaultShape.UNITS;
    }

    @Override
    public int coreMetaOf() {
        return ModuleCores.DUAL_FOIL_VAULT;
    }

    @Override
    protected String dissolves() {
        return DualFoilVaultShape.DISSOLVES;
    }

    @Override
    protected String passes() {
        return DualFoilVaultShape.PASSES;
    }
}
