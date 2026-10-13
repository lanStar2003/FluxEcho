package com.fluxecho.city;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.RootShape;
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
 * <p>
 * Over low ground the well is shallower (0.14.1, {@link RootShape#depth}): the shapes {@code well_20},
 * {@code well_14} and {@code well_8} are appended after the full well. The builder names the one the campus's base
 * layer gives it when it commissions the core ({@link #expectShape}); a core placed by hand names it itself once it
 * knows the nexus over it ({@link #shapeHere}), so its check and the projector agree. While that nexus is known only
 * the well its cell stands for is tried ({@link #tries}): the one at its depth, or on a controller at the full well's
 * cell (where 0.14.0 put every well, and where one dug by hand over low ground still stands) the full well. Its frame
 * and its renders follow its depth ({@link #well}).
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

    /** The build of the well it stands (or last stood) as. */
    @Override
    public CityBuild build() {
        return well().build;
    }

    /** The well it stands (or last stood) as (0.14.1): the full well, or a shallower one over low ground. */
    public RootShape.Well well() {
        RootShape.Well w = RootShape.named(shapeName());
        return w == null ? RootShape.well(RootShape.DEPTH) : w;
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

    /**
     * The Tidal Well, then the shallower wells over low ground (0.14.1), names only ever appended; the deep root and
     * the ley core join as shapes of the same module later.
     */
    @Override
    protected List<Shape> shapes() {
        if (shapes == null) {
            List<Shape> all = new ArrayList<>();
            for (RootShape.Well w : RootShape.wells()) all.add(new Shape(w.name, w.blueprint));
            shapes = Collections.unmodifiableList(all);
        }
        return shapes;
    }

    /**
     * Over a known nexus only the well its cell stands for (0.14.1, {@link RootShape#wellAt}: the depth its base layer
     * gives, or the full well where 0.14.0 dug it); any while the nexus is not known (a wrong one fails at its first
     * cell).
     */
    @Override
    protected boolean tries(Shape s) {
        TileNexus n = over();
        if (n == null) return true;
        RootShape.Well w = wellUnder(n);
        return w != null && s.name.equals(w.name);
    }

    /**
     * Under a known nexus, the well its cell stands for (0.14.1), so a core placed by hand is checked and built by the
     * projector at its depth; nothing while no nexus is known.
     */
    @Override
    protected String shapeHere() {
        TileNexus n = over();
        RootShape.Well w = n == null ? null : wellUnder(n);
        return w == null ? null : w.name;
    }

    /** The well its cell stands for under the nexus ({@link RootShape#wellAt}), or null. */
    private RootShape.Well wellUnder(TileNexus n) {
        int[] c = n.centre();
        return RootShape.wellAt(xCoord, yCoord, zCoord, c[0], c[1], c[2], n.front().offsetX, n.front().offsetZ);
    }

    /**
     * The loaded, formed nexus it lies under: the one whose root controller cell this is, at the depth its base layer
     * gives or (0.14.1) at the full well's where 0.14.0 put it (docked or not); null when none is.
     */
    public TileNexus over() {
        if (worldObj == null) return null;
        return over(NexusRegistry.nexuses(worldObj.provider.dimensionId));
    }

    /** The first nexus of the list, loaded in its world and formed, that it lies under ({@link #over()}). */
    TileNexus over(List<TileNexus> nexuses) {
        for (TileNexus n : nexuses) {
            if (n.isInvalid() || n.getWorldObj() != worldObj || !n.formed()) continue;
            if (wellUnder(n) != null) return n;
        }
        return null;
    }
}
