package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The masterplan draws the six feet of the zenith's legs only while the pod stands on them (0.16.0): the pod's tile by
 * the shape it last stood as first, docked or not; then a release job (the legs still stand while it runs) and a dome
 * job that builds the pod floating; with nothing known, on its legs.
 */
class PodViewTest {

    @Test
    void thePodsTileSaysItFirst() {
        assertFalse(PodView.moored(DomeGeneratorShape.FLOATING, false, false), "a floating pod that came apart floats");
        assertFalse(PodView.moored(DomeGeneratorShape.FLOATING, false, true), "floated while its legs come down");
        assertTrue(PodView.moored(DomeGeneratorShape.MOORED, false, false));
        assertTrue(PodView.moored(DomeGeneratorShape.MOORED_28, true, false), "its tile, over a job queued to float");
        for (DomeGeneratorShape.Moor m : DomeGeneratorShape.moors())
            assertTrue(PodView.moored(m.name, false, false), m.name);
    }

    @Test
    void withNoTileTheJobsSayIt() {
        assertTrue(PodView.moored(null, false, true), "a release job with no tile: the legs still stand");
        assertTrue(PodView.moored(null, true, true), "the release says so over a queued floating repair");
        assertFalse(PodView.moored(null, true, false), "a queued floating repair: it will float");
        assertTrue(PodView.moored(null, false, false), "nothing known: on its legs");
    }
}
