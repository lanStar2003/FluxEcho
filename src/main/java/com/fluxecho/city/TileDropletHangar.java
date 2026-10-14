package com.fluxecho.city;

import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.DropletHangarShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.UnitLayout;

/**
 * 水滴机库 The Droplet Hangar (0.16.0, CITY.md §4.12): the sky ring's module on S6 (site 36), or S2, eight droplet
 * cradles on a floating platform and the control kiosk at its front under a canopy ring. Its units are the eight
 * cradles (a 3 x 3 basin rim round a socket), one slot each (a droplet; in the structures phase any item), of the
 * kind {@code droplet_cradle}. Its shape is {@link DropletHangarShape}; once it forms the canopy ring gives way to its
 * rendered light ring, and players pass through it (it hangs over the kiosk, out of reach). TE id
 * {@code fluxecho:droplet_hangar}, core {@code fluxecho:module_core} meta 13.
 */
public class TileDropletHangar extends TileCityModule {

    /** Its accent colour, drawn in light only (CITY.md §1.1). */
    public static final int COLOR = 0xD8E4F0;

    @Override
    public String moduleKey() {
        return DropletHangarShape.KEY;
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    public CityBuild build() {
        return DropletHangarShape.BUILD;
    }

    @Override
    public UnitLayout layout() {
        return DropletHangarShape.UNITS;
    }

    @Override
    public int coreMetaOf() {
        return ModuleCores.DROPLET_HANGAR;
    }

    @Override
    protected String dissolves() {
        return DropletHangarShape.DISSOLVES;
    }

    @Override
    protected String passes() {
        return DropletHangarShape.PASSES;
    }
}
