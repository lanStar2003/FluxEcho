package com.fluxecho.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import net.minecraftforge.client.event.RenderWorldLastEvent;

import com.fluxecho.FluxEcho;

/**
 * What FluxEcho draws after the world (holograms, machine effects, trails), drawn once more into the simple view
 * through a light gate, with {@code RenderManager}'s camera standing on the far side. Each part draws relative to that
 * camera like it does in the world, so the far side's holograms show in the gate.
 */
public final class FarDraw {

    private static final List<Consumer<RenderWorldLastEvent>> PARTS = new CopyOnWriteArrayList<>();
    private static boolean failed;

    private FarDraw() {}

    public static void add(Consumer<RenderWorldLastEvent> part) {
        PARTS.add(part);
    }

    public static void draw(RenderWorldLastEvent e) {
        if (failed) return;
        for (Consumer<RenderWorldLastEvent> p : PARTS) try {
            p.accept(e);
        } catch (Throwable t) {
            failed = true;
            FluxEcho.LOG
                .error("Drawing holograms into a light gate's view failed; the gates leave them out from now on", t);
            return;
        }
    }
}
