package com.fluxecho.nexus.client;

import com.fluxecho.library.TileLibrary;
import com.fluxecho.library.client.LibraryRender;
import com.fluxecho.nexus.TileModule;

/** Draws each kind of module of the Flux Nexus in its formed shape. */
final class ModuleRender {

    private ModuleRender() {}

    static void draw(TileModule m, double t, float fade) {
        if (m instanceof TileLibrary l) LibraryRender.draw(l, t, fade);
    }
}
