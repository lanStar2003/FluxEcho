package com.fluxecho.client.probe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.fluxecho.FluxEcho;
import com.fluxecho.logic.probe.ProbeFrame;
import com.fluxecho.logic.probe.ProbeMarker;
import com.fluxecho.logic.probe.SwatchLayout;
import com.fluxecho.logic.skin.SkinEntry;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The check-up rigs the client knows of (0.17.0, refactor FINAL F06): once a second it looks through the loaded block
 * entities for our marker signs ({@link ProbeMarker}) and keeps an immutable list of the rigs they mark, each with its
 * {@link ProbeFrame}, which the probes' renderers and the occlusion gate's source read; for the swatch walls it also
 * works out which cells show which light ({@link SwatchLayout#glows}) and has their chunks built again when a wall
 * comes or goes, so the chunk renderer ({@link SwatchLooks}) tints them. Nothing is sent: the signs come with their
 * chunks.
 */
@SideOnly(Side.CLIENT)
public final class ProbeMarkers {

    /** Client ticks between two looks. */
    static final int EVERY = 20;

    /** One rig: what it is, its sign, its frame and its sign's data line. */
    public static final class Marker {

        public final ProbeMarker.Kind kind;
        public final int[] sign;
        public final ProbeFrame frame;
        public final String data;

        Marker(ProbeMarker.Kind kind, int[] sign, ProbeFrame frame, String data) {
            this.kind = kind;
            this.sign = sign;
            this.frame = frame;
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Marker m && m.kind == kind && m.frame.equals(frame);
        }

        @Override
        public int hashCode() {
            return kind.hashCode() * 31 + frame.hashCode();
        }
    }

    private static volatile List<Marker> markers = Collections.emptyList();
    private static volatile Map<Long, SwatchLayout.Glow> glows = Collections.emptyMap();
    private static long ticks;
    private static boolean registered, failed;

    private ProbeMarkers() {}

    /** Every rig now. */
    public static List<Marker> all() {
        return markers;
    }

    /** The rigs of one kind now. */
    public static List<Marker> of(ProbeMarker.Kind kind) {
        List<Marker> out = new ArrayList<>();
        for (Marker m : markers) if (m.kind == kind) out.add(m);
        return out;
    }

    /** The light a swatch wall gives the cell, or null. Safe on any thread. */
    public static SwatchLayout.Glow swatch(int x, int y, int z) {
        Map<Long, SwatchLayout.Glow> g = glows;
        return g.isEmpty() ? null : g.get(SkinEntry.pack(x, y, z));
    }

    static synchronized void register() {
        if (registered) return;
        registered = true;
        Events e = new Events();
        FMLCommonHandler.instance()
            .bus()
            .register(e);
        MinecraftForge.EVENT_BUS.register(e);
    }

    /** Looks for the marker signs in the client world. */
    private static void scan(World w) {
        List<Marker> found = new ArrayList<>();
        for (Object o : w.loadedTileEntityList) {
            if (!(o instanceof TileEntitySign s) || s.isInvalid()) continue;
            ProbeMarker.Kind k = ProbeMarker.parse(s.signText);
            if (k == null || w.getBlock(s.xCoord, s.yCoord, s.zCoord) != Blocks.standing_sign) continue;
            int meta = w.getBlockMetadata(s.xCoord, s.yCoord, s.zCoord);
            found.add(
                new Marker(
                    k,
                    new int[] { s.xCoord, s.yCoord, s.zCoord },
                    ProbeMarker.frame(k, s.xCoord, s.yCoord, s.zCoord, meta),
                    s.signText.length > 3 ? s.signText[3] : ""));
        }
        List<Marker> was = markers;
        if (found.equals(was)) return;
        markers = Collections.unmodifiableList(found);
        List<Marker> before = swatches(was), after = swatches(found);
        if (before.equals(after)) return;
        Map<Long, SwatchLayout.Glow> g = new HashMap<>();
        for (Marker m : after) g.putAll(SwatchLayout.glows(m.frame));
        glows = Collections.unmodifiableMap(g);
        // the walls that came and went are built again with their light
        for (Marker m : before) if (!after.contains(m)) rebuild(w, m);
        for (Marker m : after) if (!before.contains(m)) rebuild(w, m);
    }

    private static List<Marker> swatches(List<Marker> l) {
        List<Marker> out = new ArrayList<>();
        for (Marker m : l) if (m.kind == ProbeMarker.Kind.SWATCH) out.add(m);
        return out;
    }

    private static void rebuild(World w, Marker m) {
        int[] b = SwatchLayout.box(m.frame);
        w.markBlockRangeForRenderUpdate(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    /** Forgets the rigs (the world closed). */
    static void clear() {
        markers = Collections.emptyList();
        glows = Collections.emptyMap();
    }

    /** The client tick and the world unload. */
    public static final class Events {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END || failed) return;
            World w = Minecraft.getMinecraft().theWorld;
            if (w == null) {
                if (!markers.isEmpty()) clear();
                return;
            }
            if (++ticks % EVERY != 0) return;
            try {
                scan(w);
            } catch (Throwable t) {
                failed = true;
                clear();
                FluxEcho.LOG.error("Looking for the swatch wall's and the probes' signs failed; they stay dark", t);
            }
        }

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload e) {
            if (e.world != null && e.world.isRemote) clear();
        }
    }
}
