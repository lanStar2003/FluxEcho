package com.fluxecho.gate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.logic.FoldedZone;
import com.fluxecho.logic.GateGeometry;
import com.fluxecho.logic.GatePins;
import com.fluxecho.logic.GateSight;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The server side of the light gates, once a tick, for each player: takes them through a gate they walked into (a
 * step within the same world: their client keeps everything), keeps both sides of the gates near them loaded for
 * them ({@link Pins}) and in their sight ({@link Sight}), and catches anyone who got out of a room.
 */
public final class GateServer {

    /** Ticks between working out again which chunks each player keeps. */
    private static final int REPIN = 5;

    private static final Map<UUID, double[]> LAST = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
    private static long tick;
    private static boolean oldPlotsMoved;

    GateServer() {}

    /** Blocks within which the far side of a gate is kept for a player: a little past where they can see it. */
    static double keepRange() {
        return Math.max(16, Config.gateViewRange) + 16;
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.player.getUniqueID();
        LAST.remove(id);
        COOLDOWN.remove(id);
        if (e.player instanceof EntityPlayerMP p) Sight.forget(p);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        tick++;
        GateRegistry r = GateRegistry.get();
        MinecraftServer server = MinecraftServer.getServer();
        if (r == null || server == null || server.getConfigurationManager() == null) return;
        try {
            if (!oldPlotsMoved) {
                oldPlotsMoved = true;
                Gates.moveOldPlots(r);
            }
            for (Object o : new ArrayList<Object>(server.getConfigurationManager().playerEntityList))
                if (o instanceof EntityPlayerMP p) player(r, p);
        } catch (RuntimeException ex) {
            FluxEcho.LOG.error("Light gates failed this tick", ex);
        }
    }

    /** The server stopped: a world loaded next starts afresh. */
    static void stopped() {
        LAST.clear();
        COOLDOWN.clear();
        Pins.clear();
        Sight.clear();
        oldPlotsMoved = false;
    }

    private static void player(GateRegistry r, EntityPlayerMP p) {
        UUID id = p.getUniqueID();
        int dim = p.dimension;
        if (dim == Config.gateDimension && GateModule.oldInterior) {
            leaveOldInterior(p);
            return;
        }
        double x = p.posX, y = p.boundingBox.minY, z = p.posZ;
        if (FoldedZone.contains(x, z) && y < 0) {
            rescue(r, p);
            return;
        }
        List<GateRegistry.Entry> gates = r.inDim(dim);
        double[] last = LAST.put(id, new double[] { x, y, z, dim });
        int cool = COOLDOWN.getOrDefault(id, 0);
        if (cool > 0) COOLDOWN.put(id, cool - 1);
        else if (last != null && last[3] == dim && p.ridingEntity == null && p.riddenByEntity == null) {
            for (GateRegistry.Entry g : gates) {
                if (g.partner == 0 && !g.inside || Math.abs(g.x + 0.5 - x) > 8 || Math.abs(g.z + 0.5 - z) > 8) continue;
                if (!g.gate()
                    .entered(last[0], last[1], last[2], x, y, z)) continue;
                GateRegistry.Entry to = r.partnerOf(g);
                if (to != null && to.dim == dim) {
                    through(r, p, g, to);
                    return;
                }
                if (to == null && g.inside) {
                    // the gate outside is gone: nobody stays shut in
                    toSpawn(p);
                    return;
                }
            }
        }
        if ((tick + p.getEntityId()) % REPIN == 0) {
            keep(r, p, gates);
            if ((tick + p.getEntityId()) % 40 == 0)
                for (GateRegistry.Entry g : gates) if (near(p, g, 64)) Gates.sync(r, g);
        }
        Pins.tick(p);
    }

    /**
     * Takes the player through {@code from} and out of {@code to}, keeping where they were on the pane. Both are in
     * the player's world: they are moved, and what they keep is worked out for where they land before the server
     * notices they moved, so nothing they had is taken away.
     */
    private static void through(GateRegistry r, EntityPlayerMP p, GateRegistry.Entry from, GateRegistry.Entry to) {
        GateGeometry.Gate a = from.gate(), b = to.gate();
        double[] q = GateGeometry.exit(a, b, p.posX, p.boundingBox.minY, p.posZ, 0.35);
        float yaw = GateGeometry.exitYaw(a, b, p.rotationYaw), pitch = p.rotationPitch;
        GateNet.send(p, new GateNet.Transit(from));
        p.playerNetServerHandler.setPlayerLocation(q[0], q[1], q[2], yaw, pitch);
        p.fallDistance = 0;
        keep(r, p, r.inDim(p.dimension));
        UUID id = p.getUniqueID();
        LAST.put(id, new double[] { q[0], q[1], q[2], to.dim });
        COOLDOWN.put(id, 10);
    }

    /**
     * Works out which chunks the player keeps where they are now (see {@link GatePins#wanted}), and which gates they
     * see through.
     */
    private static void keep(GateRegistry r, EntityPlayerMP p, List<GateRegistry.Entry> gates) {
        List<GatePins.Linked> linked = new ArrayList<>();
        List<GateSight.Line> sight = new ArrayList<>();
        double range = keepRange();
        for (GateRegistry.Entry g : gates) {
            GateGeometry.Box far = g.partner == 0 ? null : Gates.farBox(r, g);
            if (far == null) continue;
            linked.add(new GatePins.Linked(g.gate(), far));
            GateRegistry.Entry to = r.partnerOf(g);
            if (to != null && to.dim == p.dimension && near(p, g, range))
                sight.add(new GateSight.Line(g.gate(), to.gate()));
        }
        Sight.set(p, sight);
        GateGeometry.Box room = null;
        GateGeometry.Gate outside = null;
        int plot = FoldedZone.plotAt(p.posX, p.posZ);
        GateRegistry.Entry inner = plot >= 0 ? r.insideOf(plot) : null;
        if (inner != null && inner.dim == p.dimension) {
            room = Gates.roomBox(inner);
            GateRegistry.Entry o = r.partnerOf(inner);
            if (o != null && o.dim == p.dimension) outside = o.gate();
        }
        WorldServer w = p.getServerForPlayer();
        int hold = Config.gateHoldBase ? w.getPlayerManager().playerViewRadius + 1 : 0;
        Set<Long> wanted = GatePins
            .wanted(p.posX, p.boundingBox.minY, p.posZ, linked, keepRange(), room, outside, hold);
        Pins.set(p, wanted);
    }

    private static boolean near(EntityPlayerMP p, GateRegistry.Entry g, double range) {
        double dx = g.x + 0.5 - p.posX, dz = g.z + 0.5 - p.posZ;
        return dx * dx + dz * dz <= range * range;
    }

    /** Got out of a room and fell: back in, in front of the room's gate, or to the world's spawn without one. */
    private static void rescue(GateRegistry r, EntityPlayerMP p) {
        int plot = FoldedZone.plotAt(p.posX, p.posZ);
        GateRegistry.Entry inner = plot >= 0 ? r.insideOf(plot) : null;
        p.fallDistance = 0;
        p.motionY = 0;
        if (inner != null && inner.dim == p.dimension) {
            p.playerNetServerHandler.setPlayerLocation(inner.x + 0.5, inner.y, inner.z - 2.5, 180f, 0f);
        } else toSpawn(p);
        LAST.remove(p.getUniqueID());
    }

    /** To the spawn of the world the player is in. */
    private static void toSpawn(EntityPlayerMP p) {
        WorldServer w = p.getServerForPlayer();
        ChunkCoordinates s = w.getSpawnPoint();
        p.fallDistance = 0;
        p.playerNetServerHandler.setPlayerLocation(
            s.posX + 0.5,
            w.getTopSolidOrLiquidBlock(s.posX, s.posZ),
            s.posZ + 0.5,
            p.rotationYaw,
            0f);
        LAST.remove(p.getUniqueID());
        COOLDOWN.put(p.getUniqueID(), 10);
    }

    /** In the 0.8.1 prototype's interior dimension, which no gate leads to any more: to the overworld's spawn. */
    private static void leaveOldInterior(EntityPlayerMP p) {
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer w = server.worldServerForDimension(0);
        ChunkCoordinates s = w.getSpawnPoint();
        double y = w.getTopSolidOrLiquidBlock(s.posX, s.posZ);
        server.getConfigurationManager()
            .transferPlayerToDimension(p, 0, new GateTeleporter(w, s.posX + 0.5, y, s.posZ + 0.5, p.rotationYaw, 0f));
        p.fallDistance = 0;
        LAST.remove(p.getUniqueID());
    }
}
