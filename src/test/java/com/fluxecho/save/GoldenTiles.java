package com.fluxecho.save;

import java.util.HashSet;
import java.util.Set;

import com.fluxecho.city.TileDomeGenerator;
import com.fluxecho.city.TileRoot;
import com.fluxecho.nexus.TileNexus;

/**
 * The few tiles a golden campus needs to stand in a test world without a server, as the form tests make them: a
 * nexus that knows its teams' research (which lives in the server's saved data) and tells no team it formed, and the
 * pod and the root that find that nexus over or under them (which a server finds in its registry of loaded nexuses).
 * They save under the real tiles' ids ({@link GoldenWorld#map}), so their tags are the real tiles' tags.
 */
public final class GoldenTiles {

    private GoldenTiles() {}

    /** A nexus whose teams have the research the test names. */
    public static class Nexus extends TileNexus {

        public final Set<String> done = new HashSet<>();

        @Override
        public boolean has(String id) {
            return done.contains(id);
        }

        @Override
        protected void formedChanged(boolean now) {
            // the anchor's grant and the teams' message need the server; the phase follows the shape it stands as
        }
    }

    /** An Echo Dome Generator that finds the test's nexus under it. */
    public static class Pod extends TileDomeGenerator {

        public TileNexus over;

        @Override
        public TileNexus under() {
            return over;
        }
    }

    /** A root that finds the test's nexus over it. */
    public static class Root extends TileRoot {

        public TileNexus over;

        @Override
        public TileNexus over() {
            return over;
        }
    }

    /** Maps the three under the real tiles' ids. */
    public static void map() {
        GoldenWorld.map(TileNexus.class, Nexus.class, "fluxecho:nexus");
        GoldenWorld.map(TileDomeGenerator.class, Pod.class, "fluxecho:dome_generator");
        GoldenWorld.map(TileRoot.class, Root.class, "fluxecho:root");
    }
}
