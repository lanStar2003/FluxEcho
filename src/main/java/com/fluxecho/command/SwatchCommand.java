package com.fluxecho.command;

import net.minecraft.block.Block;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.fluxecho.logic.probe.ProbeFrame;
import com.fluxecho.logic.probe.ProbeMarker;
import com.fluxecho.logic.probe.SwatchLayout;

/**
 * {@code /fluxecho swatch} and {@code swatch clear} (0.17.0, style FINAL §8.6, DECISIONS B11, ids.md §18): the swatch
 * wall of {@link SwatchLayout}, set out of real blocks in front of the operator (its middle column on their line of
 * sight, its face eight cells ahead, a mirror pool between), or built again where one already stands within
 * {@value #NEAR} blocks; {@code clear} takes the nearest one within {@value #NEAR} down again, the pool's water and the
 * ground course put back as grass over dirt. A wall is never set over a chest, a machine or a building: if a block
 * entity or a campus part stands in its box, nothing is touched. The accents and the lit lamps are the client's
 * ({@code client/probe/SwatchLooks}), found from the wall's marker sign.
 */
final class SwatchCommand {

    /** How near a standing wall is built again or cleared. */
    static final double NEAR = 64;
    private static final String P = "fluxecho.cmd.";

    private SwatchCommand() {}

    static void build(EntityPlayer p) {
        World w = p.worldObj;
        TileEntitySign old = ProbeWorld.marker(w, ProbeMarker.Kind.SWATCH, p.posX, p.posY, p.posZ, NEAR);
        ProbeFrame f = old != null ? ProbeWorld.frameOf(w, old, ProbeMarker.Kind.SWATCH) : ProbeWorld.frame(p);
        int[] box = SwatchLayout.box(f);
        if (!ProbeWorld.fits(box)) throw new CommandException(P + "probe.height");
        int[] te = ProbeWorld.occupied(w, box);
        if (te != null) throw new CommandException(P + "probe.blocked", ProbeWorld.at(te));
        ProbeWorld.quietly(() -> {
            for (SwatchLayout.Placement pl : SwatchLayout.placements()) {
                int[] at = f.at(pl.along, pl.right, pl.up);
                switch (pl.what) {
                    case SKIN -> ProbeWorld.set(w, at, ProbeWorld.skin(pl.family, pl.number), pl.number);
                    case VANILLA -> {
                        Block b = Block.getBlockFromName(pl.block);
                        ProbeWorld.set(w, at, b == null ? Blocks.stone : b, pl.meta);
                    }
                    case WATER -> ProbeWorld.set(w, at, Blocks.water, 0);
                    default -> {
                        if (!w.isAirBlock(at[0], at[1], at[2])) w.setBlockToAir(at[0], at[1], at[2]);
                    }
                }
            }
        });
        ProbeWorld.sign(w, ProbeMarker.Kind.SWATCH, f, "样板墙 swatch", "v0.17");
        int[] origin = f.at(SwatchLayout.BACK, 0, 0);
        p.addChatMessage(
            new ChatComponentTranslation(P + (old != null ? "swatch.rebuilt" : "swatch.built"), ProbeWorld.at(origin)));
        if (old == null) p.addChatMessage(new ChatComponentTranslation(P + "swatch.rows"));
    }

    static void clear(EntityPlayer p) {
        World w = p.worldObj;
        TileEntitySign s = ProbeWorld.marker(w, ProbeMarker.Kind.SWATCH, p.posX, p.posY, p.posZ, NEAR);
        if (s == null) throw new CommandException(P + "swatch.none", (int) NEAR);
        ProbeFrame f = ProbeWorld.frameOf(w, s, ProbeMarker.Kind.SWATCH);
        int[] te = ProbeWorld.occupied(w, SwatchLayout.box(f));
        if (te != null) throw new CommandException(P + "probe.blocked", ProbeWorld.at(te));
        ProbeWorld.quietly(() -> {
            // from the top down, so nothing is left hanging for a moment; the ground back last
            java.util.List<int[]> cells = SwatchLayout.restore();
            cells.sort((a, b) -> Integer.compare(b[2], a[2]));
            for (int[] c : cells) {
                int[] at = f.at(c[0], c[1], c[2]);
                if (c[3] == 1) ProbeWorld.set(w, at, Blocks.grass, 0);
                else if (c[3] == 2) ProbeWorld.set(w, at, Blocks.dirt, 0);
                else if (!w.isAirBlock(at[0], at[1], at[2])) w.setBlockToAir(at[0], at[1], at[2]);
            }
        });
        ProbeWorld.unsign(w, s);
        p.addChatMessage(
            new ChatComponentTranslation(P + "swatch.cleared", ProbeWorld.at(f.at(SwatchLayout.BACK, 0, 0))));
    }
}
