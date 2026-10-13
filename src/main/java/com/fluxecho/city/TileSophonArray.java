package com.fluxecho.city;

import net.minecraft.nbt.NBTTagCompound;

import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.SophonArrayShape;
import com.fluxecho.logic.UnitLayout;

/**
 * 智子阵列 The Sophon Array (0.15.0, CITY.md §4.10): the first building on the outer diagonals (it prefers O1, site 11),
 * a podium holding a data hall under a lattice sphere on four struts, entered by four chamfer portals and run from the
 * console at the hall's centre under the glazed oculus. Its units are 48 memory racks of three core sockets each: 48
 * units, 144 slots (in the structures phase any item). Its shape is {@link SophonArrayShape}; once it forms the struts
 * and the lattice ({@code P}) give way to the rendered geodesic frame round the light point, and players pass through
 * them (they stand on the podium's roof and over it, where nobody walks). TE id {@code fluxecho:sophon_array}, core
 * {@code fluxecho:module_core} meta 9.
 */
public class TileSophonArray extends TileCityModule {

    /** Its accent colour, drawn in light only (CITY.md §1.1). */
    public static final int COLOR = 0xFF8CE6;

    /**
     * Client: when the client heard the array turn on or off (system milliseconds), 0 when it never heard it change
     * since it first heard of the array. Its light point unfolds into the icosahedron and the circuit canopy from this
     * moment and folds back from it ({@code SophonLook.unfold}); a client that arrives late sees it as it is, as the
     * forming sweep is never replayed.
     */
    public long powerFlip;
    /** Client: whether a description has been heard yet, so the first one sets no flip. */
    private boolean heard;

    @Override
    public String moduleKey() {
        return SophonArrayShape.KEY;
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    public CityBuild build() {
        return SophonArrayShape.BUILD;
    }

    @Override
    public UnitLayout layout() {
        return SophonArrayShape.UNITS;
    }

    @Override
    public int coreMetaOf() {
        return ModuleCores.SOPHON_ARRAY;
    }

    @Override
    protected String dissolves() {
        return SophonArrayShape.DISSOLVES;
    }

    @Override
    protected String passes() {
        return SophonArrayShape.PASSES;
    }

    /** Client: notes when the power changes (the description says it), for the unfolding's start. */
    @Override
    protected void readSync(NBTTagCompound t) {
        boolean was = clientPowered;
        super.readSync(t);
        if (heard && was != clientPowered) powerFlip = System.currentTimeMillis();
        heard = true;
    }
}
