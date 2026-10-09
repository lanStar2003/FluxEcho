package com.fluxecho.gate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.fluxecho.logic.GatePins;

/**
 * Chunks a player keeps watching besides the square around them (see {@link GatePins}): the server sends them like
 * any other chunk, with every change and every machine's update after, and does not take them away when the player
 * moves on, not even across a gate. The mixins in {@code com.fluxecho.mixins.early} call in here; everything runs on
 * the server thread.
 */
public final class Pins {

    private static final class Held {

        final PlayerManager manager;
        final Set<Long> chunks = new HashSet<>();

        Held(PlayerManager manager) {
            this.manager = manager;
        }
    }

    private static final Map<EntityPlayerMP, Held> HELD = new HashMap<>();
    private static List<ChunkCoordIntPair> queued;

    private Pins() {}

    private static long key(ChunkCoordIntPair c) {
        return GatePins.key(c.chunkXPos, c.chunkZPos);
    }

    /** Whether the player keeps this chunk, so the server must not take it away from them. */
    public static boolean holds(EntityPlayerMP p, ChunkCoordIntPair c) {
        Held h = HELD.get(p);
        return h != null && h.chunks.contains(key(c));
    }

    /**
     * The player leaves this world (logging out, another dimension, respawning): they let go of everything they kept
     * there. Called as the server starts taking them out of it, before it lets go of the square around them.
     */
    public static void leave(PlayerManager pm, EntityPlayerMP p) {
        Held h = HELD.get(p);
        if (h == null || h.manager != pm) return;
        HELD.remove(p);
        int r = pm.playerViewRadius, mx = (int) p.managedPosX >> 4, mz = (int) p.managedPosZ >> 4;
        for (long k : h.chunks) {
            if (GatePins.inSquare(k, mx, mz, r)) continue;
            PlayerManager.PlayerInstance i = pm.getOrCreateChunkWatcher(GatePins.keyX(k), GatePins.keyZ(k), false);
            if (i != null) i.removePlayer(p);
        }
    }

    /**
     * The player keeps exactly these chunks of the world they are in. New ones are sent to them; those let go of are
     * taken away unless they are in the square round where the player is or was last placed (the server looks after
     * those itself).
     */
    public static void set(EntityPlayerMP p, Set<Long> wanted) {
        WorldServer w = p.getServerForPlayer();
        if (w == null) return;
        PlayerManager pm = w.getPlayerManager();
        Held h = HELD.get(p);
        if (h != null && h.manager != pm) {
            leave(h.manager, p);
            h = null;
        }
        if (h == null) {
            if (wanted.isEmpty()) return;
            h = new Held(pm);
            HELD.put(p, h);
        }
        int r = pm.playerViewRadius;
        int mx = (int) p.managedPosX >> 4, mz = (int) p.managedPosZ >> 4;
        int px = (int) p.posX >> 4, pz = (int) p.posZ >> 4;
        for (Iterator<Long> it = h.chunks.iterator(); it.hasNext();) {
            long k = it.next();
            if (wanted.contains(k)) continue;
            it.remove();
            if (GatePins.inSquare(k, mx, mz, r) || GatePins.inSquare(k, px, pz, r)) continue;
            PlayerManager.PlayerInstance i = pm.getOrCreateChunkWatcher(GatePins.keyX(k), GatePins.keyZ(k), false);
            if (i != null) i.removePlayer(p);
        }
        for (long k : wanted) {
            if (!h.chunks.add(k)) continue;
            PlayerManager.PlayerInstance i = pm.getOrCreateChunkWatcher(GatePins.keyX(k), GatePins.keyZ(k), true);
            if (!i.playersWatchingChunk.contains(p)) i.addPlayer(p);
        }
        if (h.chunks.isEmpty()) HELD.remove(p);
    }

    /** Lets go of everything, without telling anyone: the server is stopping. */
    public static void clear() {
        HELD.clear();
        queued = null;
    }

    /**
     * Kept chunks still waiting to be sent go out like any other: the server only sends a chunk that has had one
     * tick, and a kept chunk far from everyone gets none on its own.
     */
    public static void tick(EntityPlayerMP p) {
        Held h = HELD.get(p);
        if (h == null || p.loadedChunks.isEmpty()) return;
        WorldServer w = p.getServerForPlayer();
        for (Object o : p.loadedChunks) {
            ChunkCoordIntPair c = (ChunkCoordIntPair) o;
            if (!h.chunks.contains(key(c)) || !w.getChunkProvider()
                .chunkExists(c.chunkXPos, c.chunkZPos)) continue;
            Chunk chunk = w.getChunkFromChunkCoords(c.chunkXPos, c.chunkZPos);
            if (!chunk.func_150802_k()) chunk.func_150804_b(false);
        }
    }

    /**
     * Before the server trims the player's queue of chunks to send down to the square round them: remembers the kept
     * ones in it, which {@link #afterFilter} puts back.
     */
    public static void beforeFilter(EntityPlayerMP p) {
        queued = null;
        Held h = HELD.get(p);
        if (h == null) return;
        for (Object o : p.loadedChunks) {
            ChunkCoordIntPair c = (ChunkCoordIntPair) o;
            if (!h.chunks.contains(key(c))) continue;
            if (queued == null) queued = new ArrayList<>();
            queued.add(c);
        }
    }

    @SuppressWarnings("unchecked")
    public static void afterFilter(EntityPlayerMP p) {
        List<ChunkCoordIntPair> q = queued;
        queued = null;
        if (q == null) return;
        for (ChunkCoordIntPair c : q) if (!p.loadedChunks.contains(c)) p.loadedChunks.add(c);
    }
}
