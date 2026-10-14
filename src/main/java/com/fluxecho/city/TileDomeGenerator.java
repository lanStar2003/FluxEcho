package com.fluxecho.city;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.ModuleCores;
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
 * comes with the sky ring, 0.16.0), so at phases II and III a pod whose legs were broken comes apart instead of
 * floating; a pod that last stood floating is also tried floating while no formed nexus is known, so it stays up
 * while its nexus is apart. The nexus's preview shows it floating from phase IV. Floating, its blueprint and its build
 * are the pod's
 * own box (0.16.0), so it covers nothing under its floor, and its datum is where its controller stands.
 * <p>
 * Over high ground the pod stands lower (0.14.1, {@link DomeGeneratorShape#datum}): on shorter legs, as the moored
 * shapes {@code moored_36}, {@code moored_32} and {@code moored_28} appended after the two. The builder names the one
 * the campus's base layer gives it when it commissions the core ({@link #expectShape}); a core placed by hand names it
 * itself once it knows the nexus under it ({@link #shapeHere}), so its check and the projector agree. While that
 * nexus is known only the shape its cell stands for is tried ({@link #tries}): the one at its datum, or on a controller
 * at the full legs' cell (where 0.14.0 put every pod, and where one built by hand over high ground still stands) the
 * full legs. Its frame, its renders and its plan follow its datum ({@link #datum}).
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

    /**
     * The build of the shape it stands (or last stood) as: the moored one at its datum; floating, the pod in its own
     * box (0.16.0, {@link DomeGeneratorShape#FLOATING_BUILD}; up to 0.15.0 the full legs' build).
     */
    @Override
    public CityBuild build() {
        DomeGeneratorShape.Moor m = DomeGeneratorShape.moored(shapeName());
        return m == null ? DomeGeneratorShape.FLOATING_BUILD : m.build;
    }

    /**
     * Its sky datum F, the pod floor's height over the nexus's base layer (0.14.1): a moored shape's own; floating
     * (0.16.0), where its controller stands over the base layer of the nexus it is docked to (as the client heard it,
     * on the client; {@link DomeGeneratorShape#podDatum}), which is exact for a pod built by hand on the full legs over
     * high ground too (up to 0.15.0 it was the datum that base layer gives a pod built now); 40 when that nexus is not
     * known.
     */
    public int datum() {
        DomeGeneratorShape.Moor m = DomeGeneratorShape.moored(shapeName());
        if (m != null) return m.datum;
        int[] c = null;
        if (worldObj != null && worldObj.isRemote) c = clientNexus;
        else {
            TileNexus n = nexus();
            if (n == null) n = under();
            if (n != null) c = n.centre();
        }
        if (c == null || c.length < 2) return DomeGeneratorShape.FLOOR_UP;
        int f = DomeGeneratorShape.podDatum(yCoord, c[1]);
        return DomeGeneratorShape.isDatum(f) ? f : DomeGeneratorShape.datum(c[1]);
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

    /**
     * On its legs first, then floating; then on the shorter legs over high ground (0.14.1), names only ever appended.
     */
    @Override
    protected List<Shape> shapes() {
        if (shapes == null) {
            List<Shape> all = new ArrayList<>();
            all.add(new Shape(DomeGeneratorShape.MOORED, DomeGeneratorShape.MOORED_BP));
            all.add(new Shape(DomeGeneratorShape.FLOATING, DomeGeneratorShape.FLOATING_BP));
            for (DomeGeneratorShape.Moor m : DomeGeneratorShape.moors()) {
                if (!DomeGeneratorShape.MOORED.equals(m.name)) all.add(new Shape(m.name, m.blueprint));
            }
            shapes = Collections.unmodifiableList(all);
        }
        return shapes;
    }

    /**
     * Floating only over a nexus of phase IV or more (CITY.md §3), or (0.16.0), while no formed nexus is known, when it
     * last stood floating: a released pod has no legs to stand on, so it stays formed while its nexus is apart (a
     * broken block, its own repair) as a moored pod does, rather than coming apart, turning its dissolved cells back
     * into blocks and leaving its sky modules without the pod they float by. On its legs: over a known nexus only the
     * moored shape its cell stands for (0.14.1, {@link DomeGeneratorShape#mooredAt}: the datum its base layer gives,
     * or the full legs where 0.14.0 stood them), at any datum while the nexus is not known (a wrong one fails at its
     * first cell).
     */
    @Override
    protected boolean tries(Shape s) {
        TileNexus n = under();
        if (DomeGeneratorShape.FLOATING.equals(s.name))
            return n == null ? DomeGeneratorShape.FLOATING.equals(shapeName())
                : n.phase() >= DomeGeneratorShape.FLOAT_PHASE;
        if (n == null) return true;
        DomeGeneratorShape.Moor m = mooredOver(n);
        return m != null && s.name.equals(m.name);
    }

    /**
     * Over a known nexus, the moored shape its cell stands for (0.14.1), so a core placed by hand is checked and built
     * by the projector at its datum; nothing while it would float there, or while no nexus is known.
     */
    @Override
    protected String shapeHere() {
        TileNexus n = under();
        if (n == null) return null;
        if (DomeGeneratorShape.FLOATING.equals(shapeName()) && n.phase() >= DomeGeneratorShape.FLOAT_PHASE) return null;
        DomeGeneratorShape.Moor m = mooredOver(n);
        return m == null ? null : m.name;
    }

    /** The moored generator its cell stands for over the nexus ({@link DomeGeneratorShape#mooredAt}), or null. */
    private DomeGeneratorShape.Moor mooredOver(TileNexus n) {
        int[] c = n.centre();
        return DomeGeneratorShape
            .mooredAt(xCoord, yCoord, zCoord, c[0], c[1], c[2], n.front().offsetX, n.front().offsetZ);
    }

    @Override
    protected void previewPhase(int phase) {
        previewShape(phase >= DomeGeneratorShape.FLOAT_PHASE ? DomeGeneratorShape.FLOATING : DomeGeneratorShape.MOORED);
    }

    /**
     * The loaded, formed nexus it stands over: the one whose zenith controller cell this is, at the datum its base
     * layer gives or (0.14.1) on the full legs where 0.14.0 put it (docked or not, so a module that came apart can
     * still find the nexus it would float over); null when none is.
     */
    public TileNexus under() {
        if (worldObj == null) return null;
        return under(NexusRegistry.nexuses(worldObj.provider.dimensionId));
    }

    /** The first nexus of the list, loaded in its world and formed, that it stands over ({@link #under()}). */
    TileNexus under(List<TileNexus> nexuses) {
        for (TileNexus n : nexuses) {
            if (n.isInvalid() || n.getWorldObj() != worldObj || !n.formed()) continue;
            if (mooredOver(n) != null) return n;
        }
        return null;
    }
}
