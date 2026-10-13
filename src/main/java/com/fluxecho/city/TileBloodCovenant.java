package com.fluxecho.city;

import com.fluxecho.logic.BloodCovenantShape;
import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.UnitLayout;

/**
 * 血契坛 The Blood Covenant Altar (0.13.0, CITY.md §4.4): a pavilion, a stepped rotunda of three tiers on a diagonal
 * site (it prefers site 5, the dark corner behind the Archive), entered by four chamfer portals and run from the altar
 * on the dais at its heart, under the open oculus. Its units are eight orb cradles and the four altar bays' ritual
 * stones: 12 units, 12 slots (in the structures phase any item). Its shape is {@link BloodCovenantShape}; nothing of it
 * dissolves (the blood orb over the oculus is drawn, not built). TE id {@code fluxecho:blood_covenant}, core
 * {@code fluxecho:module_core} meta 3.
 */
public class TileBloodCovenant extends TileCityModule {

    /** Its accent colour, drawn in light only (CITY.md §1.1). */
    public static final int COLOR = 0xE0344A;

    @Override
    public String moduleKey() {
        return BloodCovenantShape.KEY;
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    public CityBuild build() {
        return BloodCovenantShape.BUILD;
    }

    @Override
    public UnitLayout layout() {
        return BloodCovenantShape.UNITS;
    }

    @Override
    public int coreMetaOf() {
        return ModuleCores.BLOOD_COVENANT;
    }

    @Override
    protected String dissolves() {
        return BloodCovenantShape.DISSOLVES;
    }

    @Override
    protected String passes() {
        return BloodCovenantShape.PASSES;
    }

    /** The plan page's tiers: tier 2 over the terrace at y7 and tier 3 over the slab at y12 (0.13.0). */
    @Override
    public int[] planLevels() {
        return BloodCovenantShape.planLevels();
    }
}
