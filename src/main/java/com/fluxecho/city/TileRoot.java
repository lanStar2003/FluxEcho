package com.fluxecho.city;

import java.util.Collections;
import java.util.List;

import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.RootShape;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.UnitLayout;
import com.fluxecho.nexus.NexusRegistry;
import com.fluxecho.nexus.TileNexus;

/**
 * The root under the nexus (0.12.0, CITY.md §4.9), as this version builds it: 潮汐井 the Tidal Well, a shaft from the
 * lid under the forum down to Y0 - 26, entered by the light lift from the dais to the island in its tidal pool; on the
 * island the controller (one higher than a hall's, over its console), round the pool eight tide taps (one slot each,
 * the fuel of the tide's power from 0.19.0), three tidal rings over the water and a helix ramp up the wall. Its shape:
 * {@link RootShape}.
 * <p>
 * One module grows downward through three shapes ({@link RootShape#WELL} now; the deep root and the ley core in later
 * batches), always with its controller on the island. Once it forms its tidal rings ({@code R}) give way to the
 * rendered rings that rise and fall with the tide, and players pass through them ({@link RootShape#PASSES}). TE id
 * {@code fluxecho:root}, core {@code fluxecho:module_core} meta 8.
 */
public class TileRoot extends TileCityModule {

    /** Its accent colour (tide blue), drawn in light only (CITY.md §1.1); the deep root and the ley core get theirs. */
    public static final int COLOR = 0x3FC8E8;

    private static List<Shape> shapes;

    @Override
    public String moduleKey() {
        return RootShape.KEY;
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    public CityBuild build() {
        return RootShape.BUILD;
    }

    @Override
    public UnitLayout layout() {
        return RootShape.UNITS;
    }

    @Override
    public int coreMetaOf() {
        return ModuleCores.ROOT;
    }

    @Override
    protected String dissolves() {
        return RootShape.DISSOLVES;
    }

    @Override
    protected String passes() {
        return RootShape.PASSES;
    }

    /** The Tidal Well; the deep root and the ley core join as shapes of the same module later. */
    @Override
    protected List<Shape> shapes() {
        if (shapes == null) shapes = Collections.singletonList(new Shape(RootShape.WELL, RootShape.BLUEPRINT));
        return shapes;
    }

    /**
     * The loaded, formed nexus it lies under: the one whose root controller cell this is (docked or not); null when
     * none is.
     */
    public TileNexus over() {
        if (worldObj == null) return null;
        for (TileNexus n : NexusRegistry.nexuses(worldObj.provider.dimensionId)) {
            if (n.isInvalid() || n.getWorldObj() != worldObj || !n.formed()) continue;
            int[] c = n.centre();
            int[] at = RootShape
                .controller(SiteFamily.ROOT_SITE, c[0], c[1], c[2], n.front().offsetX, n.front().offsetZ);
            if (at[0] == xCoord && at[1] == yCoord && at[2] == zCoord) return n;
        }
        return null;
    }
}
