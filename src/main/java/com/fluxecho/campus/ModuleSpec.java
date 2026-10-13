package com.fluxecho.campus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.SiteFamily;

/**
 * A kind of module the campus builder can raise for a nexus: what research opens it, which family of sites it stands on
 * ({@link SiteFamily}: the halls for the Echo Archive and the Eco Dome, the zenith for the Echo Dome Generator, the
 * diagonals for the pavilions since 0.13.0), how
 * large it is, which of the family's sites it prefers, the part its controller is placed as, its colour, and how its
 * build plan and controller position follow from a site. The builder creates a module job for a spec once a nexus's
 * teams have its research (see {@link Campus#tick}); everything else about the module (its tile, its shape) belongs to
 * the module itself. Specs are registered in {@link ModuleSpecs} by the module's own setup code (the library's from
 * {@code LibraryModule.init}).
 */
public final class ModuleSpec {

    /** The build plan of the module on a site, for the nexus centre {@code (cx, y0, cz)} facing {@code (fx, fz)}. */
    public interface PlanFactory {

        BuildPlan.Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz);
    }

    /** Where the module's controller stands on a site: {x, y, z, facing ordinal}. */
    public interface ControllerFactory {

        int[] controller(int site, int cx, int y0, int cz, int fx, int fz);
    }

    /**
     * What a module on the nexus's axis (0.12.0: the zenith over it, the root under it, {@link SiteFamily#axis}) takes
     * of the campus, as the hall's footprint and site box say it for a hall: its box seen from above, the columns the
     * campus jobs leave alone, the boxes in space nothing else touches, and whether a campus of the height has room for
     * it. Every box is in world coordinates for the nexus centre {@code (cx, y0, cz)} facing {@code (fx, fz)}.
     */
    public interface Envelope {

        /** Its box seen from above, {x0, z0, x1, z1} (inclusive), with {@code margin} cells round it. */
        int[] footprint(int site, int cx, int cz, int fx, int fz, int margin);

        /**
         * The keep-out boxes the campus jobs (establish, forum, refresh) leave alone once it stands: what it puts on
         * the forum (the zenith's legs, each column over its leg's heights: {x0, z0, x1, z1, yFrom, yTo}), nothing for
         * what floats over it or lies under it. A four-number box {x0, z0, x1, z1} takes whole columns.
         */
        List<int[]> keepOut(int site, int cx, int y0, int cz, int fx, int fz);

        /**
         * The boxes {x0, y0, z0, x1, y1, z1} (inclusive) in which nothing but its own jobs takes a block down: the
         * space it takes, in the air or under the ground, not whole columns (the nexus's dais under the zenith, and the
         * forum over the root, stay the nexus's own).
         */
        List<int[]> protect(int site, int cx, int y0, int cz, int fx, int fz);

        /**
         * Why a campus whose base layer is {@code y0} has no room for it (a lang key under {@code fluxecho.}, such as
         * {@code build.module.why.height}), or null when it fits.
         */
        String noRoom(int y0);
    }

    /** The module key: the tile's {@code moduleKey()} and the {@code <module>} of its job key. */
    public final String key;
    /** The family of sites it stands on (0.11.0; every module before is a hall). */
    public final SiteFamily family;
    /** The research id that opens it. */
    public final String research;
    /** Its footprint across, its depth from its front row, its height. */
    public final int width, depth, height;
    /** The sites of its family it may stand on, in order of preference (a site taken by another module is skipped). */
    public final int[] sites;
    /** The {@link com.fluxecho.logic.Parts} code its controller is placed as (consumed at commissioning). */
    public final int corePart;
    /** Its colour (bridges, holograms, the projection). */
    public final int colour;

    private final PlanFactory plans;
    private final ControllerFactory controllers;
    private final BooleanSupplier enabled;
    /**
     * What an axis module takes of the campus (0.12.0); null for the halls and the pavilions (their room is their
     * height alone, {@link #noRoom}).
     */
    private final Envelope envelope;

    public ModuleSpec(String key, String research, int width, int depth, int height, int[] sites, int corePart,
        int colour, PlanFactory plans, ControllerFactory controllers) {
        this(key, research, width, depth, height, sites, corePart, colour, plans, controllers, () -> true);
    }

    /** A hall module (the family every module before 0.11.0 has). */
    public ModuleSpec(String key, String research, int width, int depth, int height, int[] sites, int corePart,
        int colour, PlanFactory plans, ControllerFactory controllers, BooleanSupplier enabled) {
        this(
            SiteFamily.HALL,
            key,
            research,
            width,
            depth,
            height,
            sites,
            corePart,
            colour,
            plans,
            controllers,
            enabled);
    }

    /**
     * @param family  the family of sites it stands on; every site must be one of the family's
     * @param enabled whether the builder may start new jobs for it now (a config switch); a job already running goes
     *                on either way
     * @throws IllegalArgumentException for a key that is not a plain name or a site outside the family
     */
    public ModuleSpec(SiteFamily family, String key, String research, int width, int depth, int height, int[] sites,
        int corePart, int colour, PlanFactory plans, ControllerFactory controllers, BooleanSupplier enabled) {
        this(family, key, research, width, depth, height, sites, corePart, colour, plans, controllers, enabled, null);
    }

    /**
     * A module of any family; an axis family's ({@link SiteFamily#axis}, 0.12.0) gives its {@link Envelope}.
     *
     * @throws IllegalArgumentException for a key that is not a plain name, a site outside the family, or an axis
     *                                  family without an envelope
     */
    public ModuleSpec(SiteFamily family, String key, String research, int width, int depth, int height, int[] sites,
        int corePart, int colour, PlanFactory plans, ControllerFactory controllers, BooleanSupplier enabled,
        Envelope envelope) {
        if (family.axis() && envelope == null) throw new IllegalArgumentException(key + ": an axis module's envelope");
        if (key == null || key.isEmpty() || key.indexOf('@') >= 0 || key.indexOf(':') >= 0) {
            throw new IllegalArgumentException("a module key is a plain name: " + key);
        }
        for (int site : sites) {
            if (!family.hasSite(site)) throw new IllegalArgumentException(key + ": site " + site + " is no " + family);
        }
        this.family = family;
        this.key = key;
        this.research = research;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.sites = sites.clone();
        this.corePart = corePart;
        this.colour = colour;
        this.plans = plans;
        this.controllers = controllers;
        this.enabled = enabled == null ? () -> true : enabled;
        this.envelope = envelope;
    }

    /** Whether it stands on the nexus's axis (0.12.0): it takes no room on the inner ring. */
    public boolean axis() {
        return family.axis();
    }

    /**
     * Why a campus whose base layer is {@code y0} has no room for it (a lang key under {@code fluxecho.}), or null when
     * it fits. An axis module asks its {@link Envelope}. A module on the ground (a hall, a pavilion) stands on the
     * campus's base layer, so it fits while its shape's top cell, {@code y0 + height - 1}, stays inside the world
     * ({@code build.module.why.height} above y 255): every hall, and every pavilion but the Arcane Court (33 high, so
     * a base at y 223 at most, 0.13.0), fits every base a nexus core may be placed at; the clear box over the shape
     * needs no room, as the builder leaves the cells above the world alone.
     */
    public String noRoom(int y0) {
        if (envelope != null) return envelope.noRoom(y0);
        return y0 + height - 1 > WORLD_TOP ? "build.module.why.height" : null;
    }

    /** The highest y a block can stand at. */
    private static final int WORLD_TOP = 255;

    /**
     * The keep-out boxes the campus jobs leave alone once the module stands on the site: a hall's whole
     * {@link #siteBox} as whole columns {x0, z0, x1, z1}, a pavilion's apron and allee row by row (0.13.0), an axis
     * module's own boxes ({@link Envelope#keepOut}, which may be height-ranged {x0, z0, x1, z1, yFrom, yTo}).
     */
    public List<int[]> keepOut(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        if (envelope != null) return envelope.keepOut(site, cx, y0, cz, fx, fz);
        if (family == SiteFamily.PAVILION) return pavilionBoxes(site, cx, cz, fx, fz);
        return Collections.singletonList(siteBox(plan, site, cx, y0, cz, fx, fz));
    }

    /**
     * A pavilion's site (0.13.0) as whole-column boxes {x0, z0, x1, z1} in the world: every row of its apron
     * {@code oct(14, 18)} (the building, its clear margin and its annex inside) and of its diagonal allee
     * ({@link CampusPlan#pavilionSite}), so the boxes hold its floor exactly. One box round the allee would take in a
     * great square of the garden between the halls and the pavilions, and reach the promenade ring.
     */
    private static List<int[]> pavilionBoxes(int site, int cx, int cz, int fx, int fz) {
        List<int[]> out = new ArrayList<>();
        for (int[] b : CampusPlan.rowBoxes(CampusPlan.pavilionSite(site))) {
            int[] p = CampusPlan.toWorld(b[0], b[1], fx, fz), q = CampusPlan.toWorld(b[2], b[3], fx, fz);
            out.add(
                new int[] { cx + Math.min(p[0], q[0]), cz + Math.min(p[1], q[1]), cx + Math.max(p[0], q[0]),
                    cz + Math.max(p[1], q[1]) });
        }
        return out;
    }

    /**
     * The boxes {x0, y0, z0, x1, y1, z1} in which nothing but the module's own jobs takes a block down once it stands
     * on the site: a hall's {@link #siteBox} from bedrock to the sky, a pavilion's keep-out boxes from bedrock to the
     * sky (0.13.0), an axis module's own space ({@link Envelope#protect}).
     */
    public List<int[]> protect(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        if (envelope != null) return envelope.protect(site, cx, y0, cz, fx, fz);
        if (family == SiteFamily.PAVILION) {
            List<int[]> out = new ArrayList<>();
            for (int[] b : pavilionBoxes(site, cx, cz, fx, fz)) out.add(new int[] { b[0], 0, b[1], b[2], 255, b[3] });
            return out;
        }
        int[] b = siteBox(plan, site, cx, y0, cz, fx, fz);
        return Collections.singletonList(new int[] { b[0], 0, b[1], b[2], 255, b[3] });
    }

    /** The build plan of the module on the site. */
    public BuildPlan.Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        return plans.plan(plan, site, cx, y0, cz, fx, fz);
    }

    /** Where its controller stands on the site: {x, y, z, facing ordinal}. */
    public int[] controller(int site, int cx, int y0, int cz, int fx, int fz) {
        return controllers.controller(site, cx, y0, cz, fx, fz);
    }

    /** Whether the module may stand on the site. */
    public boolean hasSite(int site) {
        for (int s : sites) if (s == site) return true;
        return false;
    }

    /** Whether the builder may start new jobs for it now. */
    public boolean enabled() {
        return enabled.getAsBoolean();
    }

    /** Its job key on the site: {@code module:<key>@<site>}. */
    public String jobKey(int site) {
        return BuildPlan.moduleKey(key, site);
    }

    /**
     * The world box {x0, z0, x1, z1} (inclusive) of everything its job lays on the site (0.10.1): the footprint with
     * one cell round it, and whatever its plan reaches beyond ({@link BuildPlan#extent}: the Archive's forecourt, apron
     * and entrance canopy). A campus job keeps out of this box once a module stands on the site, so its grading never
     * takes up the module's floor or canopy.
     */
    public int[] siteBox(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        int[] box = footprint(site, cx, cz, fx, fz, 1);
        int[] e = BuildPlan.extent(plan(plan, site, cx, y0, cz, fx, fz));
        if (e == null) return box;
        return new int[] { Math.min(box[0], e[0]), Math.min(box[1], e[1]), Math.max(box[2], e[2]),
            Math.max(box[3], e[3]) };
    }

    /**
     * The world box {x0, z0, x1, z1} (inclusive) its footprint takes on the site, with {@code margin} cells round it,
     * for a nexus centred at {@code (cx, cz)} facing {@code (fx, fz)}: a hall's {@code width x depth} from its front
     * row, a pavilion's octagon round its centre.
     *
     * @throws UnsupportedOperationException for a family this version does not build on
     */
    public int[] footprint(int site, int cx, int cz, int fx, int fz, int margin) {
        if (envelope != null) return envelope.footprint(site, cx, cz, fx, fz, margin);
        if (family == SiteFamily.PAVILION) {
            int[] c = CampusPlan.diagonalCentre(site);
            int half = CampusPlan.DIAGONAL_SIZE / 2 + margin;
            int[] a = CampusPlan.toWorld(c[0] - half, c[1] - half, fx, fz),
                b = CampusPlan.toWorld(c[0] + half, c[1] + half, fx, fz);
            return new int[] { cx + Math.min(a[0], b[0]), cz + Math.min(a[1], b[1]), cx + Math.max(a[0], b[0]),
                cz + Math.max(a[1], b[1]) };
        }
        if (family != SiteFamily.HALL) throw new UnsupportedOperationException("no " + family + " modules yet");
        int[] ax = CampusPlan.siteAxis(site);
        int t0 = CampusPlan.HALL_FRONT - margin, t1 = CampusPlan.HALL_FRONT + depth - 1 + margin;
        int s0 = -(width / 2) - margin, s1 = (width - 1) / 2 + margin;
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (int t : new int[] { t0, t1 }) for (int s : new int[] { s0, s1 }) {
            // along the axis t, across it s (clockwise): (a, r) = t * axis + s * (axis turned clockwise)
            int a = t * ax[0] - s * ax[1], r = t * ax[1] + s * ax[0];
            int[] d = CampusPlan.toWorld(a, r, fx, fz);
            x0 = Math.min(x0, cx + d[0]);
            z0 = Math.min(z0, cz + d[1]);
            x1 = Math.max(x1, cx + d[0]);
            z1 = Math.max(z1, cz + d[1]);
        }
        return new int[] { x0, z0, x1, z1 };
    }
}
