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
 * The root under the nexus (0.12.0, CITY.md §4.9): 潮汐井 the Tidal Well, a shaft from the lid under the forum down to
 * Y0 - 26, entered by the light lift from the dais to the island in its tidal pool; on the island the controller (one
 * higher than a hall's, over its console), round the pool eight tide taps (one slot each, the fuel of the tide's power
 * from 0.19.0), three tidal rings over the water and a helix ramp up the wall. Its shape: {@link RootShape}.
 * <p>
 * One module grows downward through its shapes, always with its controller on the island: the well, and since 0.16.0
 * 深根 the deep root ({@link RootShape.Deep}), the ley core in a later batch. Once it forms its tidal rings ({@code R})
 * and the deep root's conduits ({@code C}) give way to their drawing, and players pass through them
 * ({@link RootShape#PASSES}). TE id {@code fluxecho:root}, core {@code fluxecho:module_core} meta 8.
 * <p>
 * Over low ground the well is shallower (0.14.1, {@link RootShape#depth}): the shapes {@code well_20},
 * {@code well_14} and {@code well_8} are appended after the full well. The builder names the one the campus's base
 * layer gives it when it commissions the core ({@link #expectShape}); a core placed by hand names it itself once it
 * knows the nexus over it ({@link #shapeHere}), so its check and the projector agree. While that nexus is known only
 * the well its cell stands for is tried ({@link #tries}): the one at its depth, or on a controller at the full well's
 * cell (where 0.14.0 put every well, and where one dug by hand over low ground still stands) the full well. Its frame
 * and its renders follow its depth ({@link #well}).
 * <p>
 * The deep root (0.16.0, "root by room") is appended after the wells in its six forms: a chamber of 13 or 7 layers
 * under the full well, or the roots come down into a well of any depth. Under a nexus of phase IV the one form the
 * well its cell stands for grows into ({@link RootShape#deepFor}) is tried too, before the well
 * ({@link #matchOrder}): an in-well form holds its well whole, so a well that has its fittings is found as the deep
 * root. Every root tile keeps 16 samples ({@link RootShape#SLOTS}): the taps' eight, then the bays'; a well shows the
 * first eight, and a save of a well's eight loads into them.
 */
public class TileRoot extends TileCityModule {

    /** Its accent colour (tide blue), drawn in light only (CITY.md §1.1); the ley core gets its own. */
    public static final int COLOR = 0x3FC8E8;
    /** The deep root's accent colour (root green, CITY.md §1.1, 0.16.0). */
    public static final int DEEP_COLOR = 0x5FD3A8;

    private static List<Shape> shapes;

    @Override
    public String moduleKey() {
        return RootShape.KEY;
    }

    /** Tide blue as a well, root green as the deep root (0.16.0). */
    @Override
    public int moduleColor() {
        return deep() != null ? DEEP_COLOR : COLOR;
    }

    /** The build of the shape it stands (or last stood) as: a well's, or (0.16.0) a form of the deep root's. */
    @Override
    public CityBuild build() {
        RootShape.Deep d = deep();
        return d != null ? d.build : well().build;
    }

    /**
     * The well it stands (or last stood) as (0.14.1): the full well, or a shallower one over low ground; as the deep
     * root (0.16.0) the well that form grows from, whose frame its renders keep ({@link #base}).
     */
    public RootShape.Well well() {
        RootShape.Deep d = deep();
        if (d != null) return d.well;
        RootShape.Well w = RootShape.named(shapeName());
        return w == null ? RootShape.well(RootShape.DEPTH) : w;
    }

    /** The form of the deep root it stands (or last stood) as (0.16.0), or null while it is a well. */
    public RootShape.Deep deep() {
        return RootShape.deep(shapeName());
    }

    /**
     * Where its well's frame lies in the frame of the shape it stands as (0.16.0, {@link RootShape#base}): the layer of
     * the well's floor slab, the chamber's height under a chamber, 0 for a well and an in-well form.
     */
    public int base() {
        return RootShape.base(shapeName());
    }

    /**
     * The block a point of its well's own frame is at (0.16.0): {@link #worldOf} of the point lifted by {@link #base},
     * since the shape it stands as may be the deep root's, whose frame starts at the chamber's floor slab. A lookup
     * from the well's frame (the renders' well look) goes through here, never {@link #worldOf} directly.
     */
    public int[] wellWorldOf(int lx, int ly, int lz) {
        return worldOf(lx, ly + base(), lz);
    }

    /** The well's eight taps; as the deep root (0.16.0) the taps and its eight bays. */
    @Override
    public UnitLayout layout() {
        RootShape.Deep d = deep();
        return d != null ? d.units : RootShape.UNITS;
    }

    /** Sixteen for every root tile (0.16.0): a well that grows into the deep root keeps its samples where they are. */
    @Override
    protected int sampleSlots() {
        return RootShape.SLOTS;
    }

    /** 深根 the deep root for its forms (0.16.0), else the Tidal Well. */
    @Override
    public String nameKey() {
        return deep() != null ? "module.root.deep" : super.nameKey();
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
     * The Tidal Well, then the shallower wells over low ground (0.14.1), then (0.16.0) the deep root's forms, names
     * only ever appended; the ley core joins as a shape of the same module later.
     */
    @Override
    protected List<Shape> shapes() {
        if (shapes == null) {
            List<Shape> all = new ArrayList<>();
            for (RootShape.Well w : RootShape.wells()) all.add(new Shape(w.name, w.blueprint));
            for (RootShape.Deep d : RootShape.deeps()) all.add(new Shape(d.name, d.blueprint));
            shapes = Collections.unmodifiableList(all);
        }
        return shapes;
    }

    /**
     * Over a known nexus only the well its cell stands for (0.14.1, {@link RootShape#wellAt}: the depth its base layer
     * gives, or the full well where 0.14.0 dug it) and (0.16.0) once the nexus has grown to phase IV the deep root's
     * form that well grows into ({@link RootShape#deepFor}); any while the nexus is not known (a wrong one fails at its
     * first cell).
     */
    @Override
    protected boolean tries(Shape s) {
        TileNexus n = over();
        if (n == null) return true;
        RootShape.Well w = wellUnder(n);
        if (w == null) return false;
        if (s.name.equals(w.name)) return true;
        return n.phase() >= RootShape.DEEP_PHASE && s.name.equals(RootShape.deepFor(n.centre()[1], w).name);
    }

    /**
     * The deep root's forms first (0.16.0), then the shape it stood as, then the others: an in-well form holds its well
     * whole, and a chamber form the well's every cell but its floor slab's grate, so in the default order a well that
     * has its fittings would stay a well for good. {@link #tries} leaves one form at most over a known nexus, so this
     * costs one more check that fails at a well of phase IV.
     */
    @Override
    protected List<Shape> matchOrder(Shape cur) {
        List<Shape> out = new ArrayList<>();
        for (Shape s : shapes()) if (RootShape.deep(s.name) != null) out.add(s);
        if (!out.contains(cur)) out.add(cur);
        for (Shape s : shapes()) if (!out.contains(s)) out.add(s);
        return out;
    }

    /**
     * Under a known nexus, the well its cell stands for (0.14.1), so a core placed by hand is checked and built by the
     * projector at its depth; nothing while no nexus is known. A deep root that came apart (0.16.0) keeps its form
     * while the nexus stands at phase IV, so it is repaired whole and not named back to its well.
     */
    @Override
    protected String shapeHere() {
        TileNexus n = over();
        RootShape.Well w = n == null ? null : wellUnder(n);
        if (w == null) return null;
        if (n.phase() >= RootShape.DEEP_PHASE && shapeName().equals(RootShape.deepFor(n.centre()[1], w).name))
            return null;
        return w.name;
    }

    /** The nexus's preview stands the full deep root from phase IV (0.16.0), the full well before. */
    @Override
    protected void previewPhase(int phase) {
        previewShape(phase >= RootShape.DEEP_PHASE ? RootShape.DEEP : RootShape.WELL);
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
