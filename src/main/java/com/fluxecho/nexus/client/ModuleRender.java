package com.fluxecho.nexus.client;

import com.fluxecho.city.TileCityModule;
import com.fluxecho.city.client.CityRender;
import com.fluxecho.library.TileLibrary;
import com.fluxecho.library.client.LibraryRender;
import com.fluxecho.nexus.TileModule;

/**
 * Draws each kind of module of the Flux Nexus in its formed shape: the library (the 0.9.2 hall and the Echo Archive)
 * its own way, every city module of 0.11.0 through the one city renderer, which adds each kind's own visuals.
 */
final class ModuleRender {

    private ModuleRender() {}

    static void draw(TileModule m, double t, float fade) {
        if (m instanceof TileLibrary l) LibraryRender.draw(l, t, fade);
        else if (m instanceof TileCityModule c) CityRender.draw(c, t, fade);
    }

    /** A module past the effects' range: its landmark alone, if it has one (0.12.0: the zenith's dome). */
    static void landmark(TileModule m, double t) {
        if (m instanceof TileCityModule c) CityRender.landmark(c, t);
    }
}
