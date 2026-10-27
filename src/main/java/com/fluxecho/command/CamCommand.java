package com.fluxecho.command;

import java.util.Locale;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.core.Owners;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.probe.CamPoints;
import com.fluxecho.nexus.NexusRegistry;
import com.fluxecho.nexus.TileNexus;

/**
 * {@code /fluxecho cam <A|B|C|D|E>} (0.17.0, masterplan FINAL §7.3, refactor FINAL F06): puts the operator at one of
 * the city's five camera points of {@link CamPoints}, in the frame of their own team's nexus (the nearest loaded one in
 * their world): their feet in the point's cell, looking the point's way and tilted as it says, flying, so the points
 * whose building is not up yet can still be stood at. The acceptance card's camera set is three screenshots from
 * each. A point inside solid blocks is refused, unless the operator is in creative mode, where nothing smothers them.
 */
final class CamCommand {

    private static final String P = "fluxecho.cmd.";

    private CamCommand() {}

    static void go(EntityPlayer p, CamPoints.Point point) {
        if (!(p instanceof EntityPlayerMP mp)) throw new CommandException(P + "not_player");
        World w = p.worldObj;
        TileNexus n = teamNexus(w, Owners.team(p.getUniqueID()), p.posX, p.posY, p.posZ);
        if (n == null) throw new CommandException(P + "cam.no_nexus");
        int[] c = n.centre();
        ForgeDirection f = n.front();
        CamPoints.Place at = CamPoints
            .place(point, c[0], c[1], c[2], f.offsetX, f.offsetZ, DomeGeneratorShape.datum(c[1]));
        int bx = MathHelper.floor_double(at.x), by = MathHelper.floor_double(at.y), bz = MathHelper.floor_double(at.z);
        if (by < 1 || by > 254) throw new CommandException(P + "probe.height");
        if (!p.capabilities.isCreativeMode && (solid(w, bx, by, bz) || solid(w, bx, by + 1, bz)))
            throw new CommandException(P + "cam.blocked", point.name(), bx + ", " + by + ", " + bz);
        p.capabilities.allowFlying = true;
        p.capabilities.isFlying = true;
        p.sendPlayerAbilities();
        p.fallDistance = 0;
        if (mp.ridingEntity != null) mp.mountEntity(null);
        mp.playerNetServerHandler.setPlayerLocation(at.x, at.y, at.z, at.yaw, at.pitch);
        p.addChatMessage(
            new ChatComponentTranslation(
                P + "cam.done",
                point.name(),
                new ChatComponentTranslation(
                    P + "cam.point."
                        + point.name()
                            .toLowerCase(Locale.ROOT)),
                bx + ", " + by + ", " + bz,
                n.xCoord + ", " + n.yCoord + ", " + n.zCoord));
    }

    /** The team's nexus nearest a point among the loaded ones in a world, or null. */
    private static TileNexus teamNexus(World w, UUID team, double x, double y, double z) {
        TileNexus best = null;
        double bestD = Double.MAX_VALUE;
        for (TileNexus n : NexusRegistry.nexuses(w.provider.dimensionId)) {
            if (n.isInvalid() || n.getWorldObj() != w || !team.equals(n.team())) continue;
            int[] c = n.centre();
            double dx = c[0] + 0.5 - x, dy = c[1] + 0.5 - y, dz = c[2] + 0.5 - z, d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    /** Whether a cell would smother a player standing in it. */
    private static boolean solid(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        return b.getMaterial()
            .blocksMovement() && b.isNormalCube();
    }
}
