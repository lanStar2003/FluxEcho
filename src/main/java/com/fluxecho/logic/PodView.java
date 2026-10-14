package com.fluxecho.logic;

/**
 * Whether the masterplan draws the zenith's pod standing on its legs (0.16.0): the six feet on the forum are drawn
 * only then, never under a pod that floats. What the client knows decides it, most trusted first: the Echo Dome
 * Generator's tile at the zenith's controller column, by the shape it last stood as (docked or not, so a floating pod
 * that came apart still floats and a moored one still stands); else the campus's jobs, where a release job means the
 * legs still stand (it exists only while they do) and a dome job that builds the pod floating means it floats; else, as
 * every pod before phase IV, on its legs.
 */
public final class PodView {

    private PodView() {}

    /**
     * Whether the pod is drawn on its legs. {@code lastShape} is the shape name the pod's tile last stood as, null
     * when the client sees no pod there; {@code jobFloats} whether a dome job not over (current or queued) builds the
     * pod floating; {@code releasing} whether the release job is not over.
     */
    public static boolean moored(String lastShape, boolean jobFloats, boolean releasing) {
        if (lastShape != null) return DomeGeneratorShape.moored(lastShape) != null;
        if (releasing) return true;
        return !jobFloats;
    }
}
