package com.fluxecho.gate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.fluxecho.logic.GateSight;

/**
 * What each player can see through the light gates near them (see {@link GateSight}), on the server: the entity
 * tracker sends them the mobs and players on the far side, and machines there send them their holograms, as if they
 * stood where the gate lets them look out. {@link GateServer} keeps it up to date; everything runs on the server
 * thread.
 */
public final class Sight {

    private static final Map<EntityPlayerMP, List<GateSight.Line>> LINES = new HashMap<>();

    private Sight() {}

    static void set(EntityPlayerMP p, List<GateSight.Line> lines) {
        if (lines.isEmpty()) LINES.remove(p);
        else LINES.put(p, lines);
    }

    static void forget(EntityPlayerMP p) {
        LINES.remove(p);
    }

    static void clear() {
        LINES.clear();
    }

    private static List<GateSight.Line> lines(EntityPlayerMP p) {
        List<GateSight.Line> l = LINES.get(p);
        return l == null || p.isDead ? Collections.emptyList() : l;
    }

    /**
     * For the entity tracker, which measures how far an entity is from a player along x and along z: where the player
     * stands, or the place they look out from that is nearest the entity. {@code x} is the player's own coordinate.
     */
    public static double trackX(EntityPlayerMP p, Entity e, double x) {
        List<GateSight.Line> l = lines(p);
        if (l.isEmpty() || e == null) return x;
        return GateSight.nearestFlat(GateSight.viewpoints(p.posX, p.posY, p.posZ, l), e.posX, e.posZ)[0];
    }

    public static double trackZ(EntityPlayerMP p, Entity e, double z) {
        List<GateSight.Line> l = lines(p);
        if (l.isEmpty() || e == null) return z;
        return GateSight.nearestFlat(GateSight.viewpoints(p.posX, p.posY, p.posZ, l), e.posX, e.posZ)[2];
    }

    /** How far the player is from a point, through the gates near them if that is nearer; squared. */
    public static double distanceSq(EntityPlayerMP p, double x, double y, double z) {
        List<GateSight.Line> l = lines(p);
        if (l.isEmpty()) return p.getDistanceSq(x, y, z);
        return GateSight.distanceSq(GateSight.viewpoints(p.posX, p.posY, p.posZ, l), x, y, z);
    }

    /** The players of a world within {@code range} of a point, directly or through a gate. */
    public static List<EntityPlayerMP> near(World w, double x, double y, double z, double range) {
        List<EntityPlayerMP> out = new ArrayList<>();
        if (w == null) return out;
        double r2 = range * range;
        for (Object o : w.playerEntities) if (o instanceof EntityPlayerMP p && distanceSq(p, x, y, z) <= r2) out.add(p);
        return out;
    }
}
