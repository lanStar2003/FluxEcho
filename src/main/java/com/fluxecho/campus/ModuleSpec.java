package com.fluxecho.campus;

import java.util.function.BooleanSupplier;

import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CampusPlan;

/**
 * A kind of module the campus builder can raise for a nexus: what research opens it, how large it is, which hall sites
 * it prefers, the part its controller is placed as, its colour, and how its build plan and controller position follow
 * from a site. The builder creates a module job for a spec once a nexus's teams have its research (see
 * {@link Campus#tick}); everything else about the module (its tile, its shape) belongs to the module itself. Specs are
 * registered in {@link ModuleSpecs} by the module's own setup code (the library's from {@code LibraryModule.init}).
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

    /** The module key: the tile's {@code moduleKey()} and the {@code <module>} of its job key. */
    public final String key;
    /** The research id that opens it. */
    public final String research;
    /** Its footprint across, its depth from its front row, its height. */
    public final int width, depth, height;
    /** The hall sites it may stand on, in order of preference. */
    public final int[] sites;
    /** The {@link com.fluxecho.logic.Parts} code its controller is placed as (consumed at commissioning). */
    public final int corePart;
    /** Its colour (bridges, holograms, the projection). */
    public final int colour;

    private final PlanFactory plans;
    private final ControllerFactory controllers;
    private final BooleanSupplier enabled;

    public ModuleSpec(String key, String research, int width, int depth, int height, int[] sites, int corePart,
        int colour, PlanFactory plans, ControllerFactory controllers) {
        this(key, research, width, depth, height, sites, corePart, colour, plans, controllers, () -> true);
    }

    /**
     * @param enabled whether the builder may start new jobs for it now (a config switch); a job already running goes
     *                on either way
     */
    public ModuleSpec(String key, String research, int width, int depth, int height, int[] sites, int corePart,
        int colour, PlanFactory plans, ControllerFactory controllers, BooleanSupplier enabled) {
        if (key == null || key.isEmpty() || key.indexOf('@') >= 0 || key.indexOf(':') >= 0) {
            throw new IllegalArgumentException("a module key is a plain name: " + key);
        }
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
     * The world box {x0, z0, x1, z1} (inclusive) its footprint takes on the site, with {@code margin} cells round it,
     * for a nexus centred at {@code (cx, cz)} facing {@code (fx, fz)}.
     */
    public int[] footprint(int site, int cx, int cz, int fx, int fz, int margin) {
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
