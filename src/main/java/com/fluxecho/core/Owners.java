package com.fluxecho.core;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.UsernameCache;

import gregtech.common.misc.spaceprojects.SpaceProjectManager;

/**
 * Who a machine works for. GT records the player who placed it; FluxEcho shares what a player learned with their
 * team, the same team GT's wireless network and space projects use. Thaumcraft and Blood Magic key their player data
 * by login name, which GT's stored owner name (a display name) is not, so names are looked up by UUID.
 */
public final class Owners {

    private Owners() {}

    /** The team's leader, or the player when not in a team; null for no player. */
    public static UUID team(UUID player) {
        if (player == null) return null;
        try {
            UUID leader = SpaceProjectManager.getLeader(player);
            return leader != null ? leader : player;
        } catch (RuntimeException e) {
            return player;
        }
    }

    /** The player when online on this server. */
    public static EntityPlayerMP online(UUID player) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || player == null || server.getConfigurationManager() == null) return null;
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP p && player.equals(p.getUniqueID())) return p;
        }
        return null;
    }

    /** The login name: from the player when online, else from Forge's cache of known names. */
    public static String loginName(UUID player) {
        EntityPlayerMP p = online(player);
        if (p != null) return p.getCommandSenderName();
        return player == null ? null : UsernameCache.getLastKnownUsername(player);
    }
}
