package com.fluxecho.city;

import java.util.Arrays;
import java.util.List;

import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.UnitLayout;
import com.fluxecho.nexus.NexusRegistry;
import com.fluxecho.nexus.TileNexus;

/**
 * 回响穹顶发生器 The Echo Dome Generator (0.12.0, CITY.md §4.8): the saucer pod over the nexus at Y0 + 40 that will
 * project the team's dome, standing on six curved legs from the forum; inside, one ring room round the light-lift pad
 * with the controller and seven ward consoles (one slot each, a function core per dome function from 0.19.0). Its shape
 * is {@link DomeGeneratorShape}.
 * <p>
 * Two shapes ({@link DomeGeneratorShape#MOORED} on its legs, {@link DomeGeneratorShape#FLOATING} the pod alone): the
 * floating one is only tried once the nexus it stands over has grown to phase IV (the job that releases the legs
 * comes with the sky ring), so at phases II and III a pod whose legs were broken comes apart instead of floating. The
 * nexus's preview shows it floating from phase IV.
 * <p>
 * Once it forms its legs ({@code P}) and emitter nodes ({@code C}) give way to the rendered ribs and arcs, and players
 * pass through both ({@link DomeGeneratorShape#PASSES}). TE id {@code fluxecho:dome_generator}, core
 * {@code fluxecho:module_core} meta 7.
 */
public class TileDomeGenerator extends TileCityModule {

    /** Its accent colour (guard blue), drawn in light only (CITY.md §1.1). */
    public static final int COLOR = 0x7DB8FF;

    private static List<Shape> shapes;

    @Override
    public String moduleKey() {
        return DomeGeneratorShape.KEY;
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    public CityBuild build() {
        return DomeGeneratorShape.BUILD;
    }

    @Override
    public UnitLayout layout() {
        return DomeGeneratorShape.UNITS;
    }

    @Override
    public int coreMetaOf() {
        return ModuleCores.DOME_GENERATOR;
    }

    @Override
    protected String dissolves() {
        return DomeGeneratorShape.DISSOLVES;
    }

    @Override
    protected String passes() {
        return DomeGeneratorShape.PASSES;
    }

    /** On its legs first, then floating. */
    @Override
    protected List<Shape> shapes() {
        if (shapes == null) shapes = Arrays.asList(
            new Shape(DomeGeneratorShape.MOORED, DomeGeneratorShape.MOORED_BP),
            new Shape(DomeGeneratorShape.FLOATING, DomeGeneratorShape.FLOATING_BP));
        return shapes;
    }

    /** Floating only over a nexus of phase IV or more (CITY.md §3); on its legs always. */
    @Override
    protected boolean tries(Shape s) {
        if (!DomeGeneratorShape.FLOATING.equals(s.name)) return true;
        TileNexus n = under();
        return n != null && n.phase() >= DomeGeneratorShape.FLOAT_PHASE;
    }

    @Override
    protected void previewPhase(int phase) {
        previewShape(phase >= DomeGeneratorShape.FLOAT_PHASE ? DomeGeneratorShape.FLOATING : DomeGeneratorShape.MOORED);
    }

    /**
     * The loaded, formed nexus it stands over: the one whose zenith controller cell this is (docked or not, so a
     * module that came apart can still find the nexus it would float over); null when none is.
     */
    public TileNexus under() {
        if (worldObj == null) return null;
        for (TileNexus n : NexusRegistry.nexuses(worldObj.provider.dimensionId)) {
            if (n.isInvalid() || n.getWorldObj() != worldObj || !n.formed()) continue;
            int[] c = n.centre();
            int[] at = DomeGeneratorShape
                .controller(SiteFamily.ZENITH_SITE, c[0], c[1], c[2], n.front().offsetX, n.front().offsetZ);
            if (at[0] == xCoord && at[1] == yCoord && at[2] == zCoord) return n;
        }
        return null;
    }
}
