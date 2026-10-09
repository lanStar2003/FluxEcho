package com.fluxecho.gate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.WorldEvent;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.logic.GateGeometry;
import com.fluxecho.logic.MirrorSection;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The server side of the light gates, once a tick: takes players who walked into a gate through it, catches anyone
 * falling off a plot, and sends each player near a gate what lies behind it, section by section, then the sections
 * that change while they stay.
 */
public final class GateServer {

    /** What a gate shows of its partner's side, in blocks. */
    static final int DEPTH = 40, HALF_WIDTH = 20, BELOW = 8, ABOVE = 24;
    /** Sections sent per player per tick, and ticks before the same section is sent again. */
    private static final int BUDGET = 6, RESEND = 10;

    private static final Map<UUID, double[]> LAST = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
    private static final Map<UUID, Map<Integer, Sub>> SUBS = new HashMap<>();
    /** Sections changed since the last tick, per dimension; only watched dimensions are kept. */
    private static final Map<Integer, Set<Long>> DIRTY = new HashMap<>();
    private static final Set<Integer> WATCHED = new HashSet<>();
    private static long tick;

    /** One player watching through one gate. */
    private static final class Sub {

        final GateRegistry.Entry viewer, source;
        final GateGeometry.Box box;
        final LinkedHashSet<Long> pending = new LinkedHashSet<>();
        final Map<Long, Long> sentAt = new HashMap<>();

        Sub(GateRegistry.Entry viewer, GateRegistry.Entry source) {
            this.viewer = viewer;
            this.source = source;
            box = GateGeometry.view(source.gate(), DEPTH, HALF_WIDTH, BELOW, ABOVE);
            // nearest the gate first
            List<long[]> all = new ArrayList<>();
            for (int sx = box.minX >> 4; sx <= box.maxX >> 4; sx++)
                for (int sy = box.minY >> 4; sy <= box.maxY >> 4; sy++)
                    for (int sz = box.minZ >> 4; sz <= box.maxZ >> 4; sz++) {
                        long dx = sx * 16 + 8 - source.x, dy = sy * 16 + 8 - source.y, dz = sz * 16 + 8 - source.z;
                        all.add(new long[] { MirrorSection.key(sx, sy, sz), dx * dx + dy * dy + dz * dz });
                    }
            all.sort((a, b) -> Long.compare(a[1], b[1]));
            for (long[] k : all) pending.add(k[0]);
        }
    }

    GateServer() {}

    /** A gate is gone: nobody watches through it any more. */
    static void forget(int gateId) {
        for (Map<Integer, Sub> m : SUBS.values()) m.remove(gateId);
    }

    /** A block changed in a dimension; the sections around it are sent again to whoever watches them. */
    static void changed(int dim, int x, int y, int z) {
        if (!WATCHED.contains(dim) || y < 0 || y > 255) return;
        Set<Long> d = DIRTY.computeIfAbsent(dim, k -> new HashSet<>());
        int sx = x >> 4, sy = y >> 4, sz = z >> 4;
        d.add(MirrorSection.key(sx, sy, sz));
        // light spreads across the section's faces
        if ((x & 15) == 0) d.add(MirrorSection.key(sx - 1, sy, sz));
        if ((x & 15) == 15) d.add(MirrorSection.key(sx + 1, sy, sz));
        if ((y & 15) == 0 && sy > 0) d.add(MirrorSection.key(sx, sy - 1, sz));
        if ((y & 15) == 15 && sy < 15) d.add(MirrorSection.key(sx, sy + 1, sz));
        if ((z & 15) == 0) d.add(MirrorSection.key(sx, sy, sz - 1));
        if ((z & 15) == 15) d.add(MirrorSection.key(sx, sy, sz + 1));
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load e) {
        if (!e.world.isRemote) e.world.addWorldAccess(new Listener(e.world.provider.dimensionId));
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.player.getUniqueID();
        LAST.remove(id);
        COOLDOWN.remove(id);
        SUBS.remove(id);
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        // the client dropped its world and everything it was shown
        SUBS.remove(e.player.getUniqueID());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !GateModule.dimensionReady) return;
        tick++;
        GateRegistry r = GateRegistry.get();
        MinecraftServer server = MinecraftServer.getServer();
        if (r == null || server == null || server.getConfigurationManager() == null) return;
        try {
            for (Object o : new ArrayList<Object>(server.getConfigurationManager().playerEntityList))
                if (o instanceof EntityPlayerMP p) player(r, p);
            spreadChanges();
        } catch (RuntimeException ex) {
            FluxEcho.LOG.error("Light gates failed this tick", ex);
        }
    }

    private static void player(GateRegistry r, EntityPlayerMP p) {
        UUID id = p.getUniqueID();
        int dim = p.dimension;
        double x = p.posX, y = p.boundingBox.minY, z = p.posZ;
        if (dim == Config.gateDimension && y < 0) {
            rescue(p);
            return;
        }
        List<GateRegistry.Entry> gates = r.inDim(dim);
        double[] last = LAST.put(id, new double[] { x, y, z, dim });
        int cool = COOLDOWN.getOrDefault(id, 0);
        if (cool > 0) COOLDOWN.put(id, cool - 1);
        else if (last != null && last[3] == dim && p.ridingEntity == null && p.riddenByEntity == null) {
            for (GateRegistry.Entry g : gates) {
                if (g.partner == 0 || Math.abs(g.x + 0.5 - x) > 8 || Math.abs(g.z + 0.5 - z) > 8) continue;
                if (g.gate()
                    .entered(last[0], last[1], last[2], x, y, z)) {
                    GateRegistry.Entry to = r.partnerOf(g);
                    if (to != null) {
                        through(p, g, to);
                        return;
                    }
                }
            }
        }
        watch(r, p, gates);
    }

    /** Takes the player through {@code from} and out of {@code to}, keeping where they were on the pane. */
    private static void through(EntityPlayerMP p, GateRegistry.Entry from, GateRegistry.Entry to) {
        GateGeometry.Gate a = from.gate(), b = to.gate();
        double[] q = GateGeometry.exit(a, b, p.posX, p.boundingBox.minY, p.posZ, 0.35);
        float yaw = GateGeometry.exitYaw(a, b, p.rotationYaw), pitch = p.rotationPitch;
        GateNet.send(p, new GateNet.Transit(from, to.dim));
        UUID id = p.getUniqueID();
        SUBS.remove(id);
        if (to.dim == p.dimension) p.playerNetServerHandler.setPlayerLocation(q[0], q[1], q[2], yaw, pitch);
        else {
            MinecraftServer server = MinecraftServer.getServer();
            WorldServer target = server.worldServerForDimension(to.dim);
            server.getConfigurationManager()
                .transferPlayerToDimension(p, to.dim, new GateTeleporter(target, q[0], q[1], q[2], yaw, pitch));
        }
        p.fallDistance = 0;
        LAST.put(id, new double[] { q[0], q[1], q[2], to.dim });
        COOLDOWN.put(id, 10);
    }

    /** Fell off a plot: back onto it, in front of its gate. */
    private static void rescue(EntityPlayerMP p) {
        GateGeometry.Gate g = GateGeometry.plotGate(GateGeometry.plotOf(p.posX));
        p.fallDistance = 0;
        p.playerNetServerHandler.setPlayerLocation(g.cx(), g.y, g.cz() - 3, p.rotationYaw, p.rotationPitch);
        LAST.remove(p.getUniqueID());
    }

    /** Starts and stops showing the player what lies behind the gates near them, and sends what is due. */
    private static void watch(GateRegistry r, EntityPlayerMP p, List<GateRegistry.Entry> gates) {
        Map<Integer, Sub> subs = SUBS.computeIfAbsent(p.getUniqueID(), k -> new HashMap<>());
        int range = Config.gateViewRange;
        if (range > 0) for (GateRegistry.Entry g : gates) {
            if (g.partner == 0 || subs.containsKey(g.id) || distSq(p, g) > range * range) continue;
            GateRegistry.Entry source = r.partnerOf(g);
            if (source == null) continue;
            Sub s = new Sub(g, source);
            subs.put(g.id, s);
            GateNet.send(p, new GateNet.View(g, source, s.box));
        }
        for (Iterator<Sub> it = subs.values()
            .iterator(); it.hasNext();) {
            Sub s = it.next();
            if (s.viewer.dim != p.dimension || range <= 0 || distSq(p, s.viewer) > (range + 8) * (range + 8)) {
                it.remove();
                if (s.viewer.dim == p.dimension) GateNet.send(p, new GateNet.Drop(s.viewer));
            }
        }
        int budget = BUDGET;
        for (Sub s : subs.values()) {
            if (budget <= 0) break;
            if (s.pending.isEmpty()) continue;
            WorldServer w = world(s.source.dim);
            if (w == null) continue;
            for (Iterator<Long> it = s.pending.iterator(); it.hasNext() && budget > 0;) {
                long k = it.next();
                Long at = s.sentAt.get(k);
                if (at != null && tick - at < RESEND) continue;
                it.remove();
                s.sentAt.put(k, tick);
                GateNet.send(p, new GateNet.Section(s.viewer, k, read(w, k).encode()));
                budget--;
            }
        }
    }

    private static double distSq(Entity p, GateRegistry.Entry g) {
        double dx = g.x + 0.5 - p.posX, dy = g.y + 1.5 - p.posY, dz = g.z + 0.5 - p.posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static WorldServer world(int dim) {
        WorldServer w = DimensionManager.getWorld(dim);
        if (w == null) {
            MinecraftServer s = MinecraftServer.getServer();
            if (s != null) w = s.worldServerForDimension(dim);
        }
        return w;
    }

    /** One section of a world as it is now. */
    private static MirrorSection read(WorldServer w, long key) {
        int sx = MirrorSection.keyX(key), sy = MirrorSection.keyY(key), sz = MirrorSection.keyZ(key);
        MirrorSection m = new MirrorSection();
        Chunk c = w.getChunkFromChunkCoords(sx, sz);
        for (int y = 0; y < 16; y++) {
            int wy = sy * 16 + y;
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int i = MirrorSection.index(x, y, z);
                Block b = c.getBlock(x, wy, z);
                m.ids[i] = (char) Block.getIdFromBlock(b);
                m.meta[i] = (char) c.getBlockMetadata(x, wy, z);
                int sky = c.getSavedLightValue(EnumSkyBlock.Sky, x, wy, z),
                    block = c.getSavedLightValue(EnumSkyBlock.Block, x, wy, z);
                m.light[i] = (byte) (sky << 4 | block & 15);
            }
        }
        byte[] biomes = c.getBiomeArray();
        System.arraycopy(biomes, 0, m.biomes, 0, Math.min(biomes.length, MirrorSection.COLUMNS));
        return m;
    }

    /** Hands this tick's changed sections to whoever watches them. */
    private static void spreadChanges() {
        WATCHED.clear();
        for (Map<Integer, Sub> subs : SUBS.values()) {
            for (Sub s : subs.values()) {
                WATCHED.add(s.source.dim);
                Set<Long> d = DIRTY.get(s.source.dim);
                if (d != null) for (long k : d)
                    if (s.box.touchesSection(MirrorSection.keyX(k), MirrorSection.keyY(k), MirrorSection.keyZ(k)))
                        s.pending.add(k);
            }
        }
        DIRTY.clear();
    }

    /** Hears every block change of one world. */
    static final class Listener implements IWorldAccess {

        private final int dim;

        Listener(int dim) {
            this.dim = dim;
        }

        @Override
        public void markBlockForUpdate(int x, int y, int z) {
            changed(dim, x, y, z);
        }

        @Override
        public void markBlockForRenderUpdate(int x, int y, int z) {
            changed(dim, x, y, z);
        }

        @Override
        public void markBlockRangeForRenderUpdate(int x0, int y0, int z0, int x1, int y1, int z1) {
            // a light update's few blocks; whole chunks are not worth sending again
            if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 64) return;
            for (int x = x0; x <= x1; x += Math.max(1, x1 - x0)) for (int y = y0; y <= y1; y += Math.max(1, y1 - y0))
                for (int z = z0; z <= z1; z += Math.max(1, z1 - z0)) changed(dim, x, y, z);
        }

        @Override
        public void playSound(String sound, double x, double y, double z, float volume, float pitch) {}

        @Override
        public void playSoundToNearExcept(EntityPlayer except, String sound, double x, double y, double z, float volume,
            float pitch) {}

        @Override
        public void spawnParticle(String name, double x, double y, double z, double vx, double vy, double vz) {}

        @Override
        public void onEntityCreate(Entity e) {}

        @Override
        public void onEntityDestroy(Entity e) {}

        @Override
        public void playRecord(String record, int x, int y, int z) {}

        @Override
        public void broadcastSound(int id, int x, int y, int z, int data) {}

        @Override
        public void playAuxSFX(EntityPlayer player, int id, int x, int y, int z, int data) {}

        @Override
        public void destroyBlockPartially(int breaker, int x, int y, int z, int progress) {}

        @Override
        public void onStaticEntitiesChanged() {}
    }
}
