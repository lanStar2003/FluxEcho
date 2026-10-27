package com.fluxecho.command;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.fluxecho.Mods;
import com.fluxecho.campus.CampusModule;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.probe.ProbeFrame;
import com.fluxecho.logic.probe.ProbeMarker;
import com.fluxecho.logic.probe.ProbeRigs;
import com.fluxecho.logic.probe.ProbeRigs.Depth;
import com.fluxecho.logic.probe.ProbeRigs.Sun;
import com.fluxecho.logic.shape.Family;
import com.fluxecho.mana.BedFlowers;

/**
 * {@code /fluxecho probe depth | sky | sun | bed} (0.17.0, refactor FINAL F06 and §7.1, ids.md §18): hidden check-ups
 * for the operator, each setting out a rig in front of them that the user looks at with the shader pack on, and the
 * same command again near a standing rig takes it down.
 * <ul>
 * <li>{@code depth} (P0): a hull wall with a window and the five pieces of light {@code DepthProbeRender} draws round
 * it;</li>
 * <li>{@code sky} (N0): only the marker sign; the client draws the sky layer's aurora to the north and the world-fixed
 * one to the south ({@code SkyLayer});</li>
 * <li>{@code sun}: a hull mast and a footing light well, with the lines to the tilted sun the client draws
 * ({@code SunProbeRender});</li>
 * <li>{@code bed} (S-P4): rows of planting beds (fitting 14), one Botania flower of every kind placed on each as a
 * player would place it; the ones that do not take are listed in the chat. Botania's classes stay in the
 * {@code mana} package ({@link BedFlowers}).</li>
 * </ul>
 * A rig never overwrites a block entity, and clears its volume of natural blocks so nothing stands between the eye and
 * what it shows.
 */
final class ProbeCommands {

    /** How near a standing rig of the same kind is taken down by the same command. */
    static final double NEAR = 32;

    private ProbeCommands() {}

    static void depth(EntityPlayer p) {
        World w = p.worldObj;
        TileEntitySign old = ProbeWorld.marker(w, ProbeMarker.Kind.DEPTH, p.posX, p.posY, p.posZ, NEAR);
        if (old != null) {
            ProbeFrame f = ProbeWorld.frameOf(w, old, ProbeMarker.Kind.DEPTH);
            ProbeWorld.quietly(
                () -> {
                    for (int r = Depth.LEFT; r <= Depth.RIGHT; r++)
                        for (int u = 0; u <= Depth.TOP; u++) air(w, f.at(Depth.WALL, r, u));
                });
            ProbeWorld.unsign(w, old);
            p.addChatMessage(new ChatComponentTranslation("fluxecho.cmd.probe.depth.off", ProbeWorld.at(old(old))));
            return;
        }
        ProbeFrame f = ProbeWorld.frame(p);
        int[] box = ProbeWorld.box(f, Depth.VOLUME);
        check(w, box);
        ProbeWorld.quietly(() -> {
            clear(w, box);
            for (int r = Depth.LEFT; r <= Depth.RIGHT; r++) for (int u = 0; u <= Depth.TOP; u++) {
                int[] at = f.at(Depth.WALL, r, u);
                if (Depth.window(r, u)) ProbeWorld.set(w, at, ProbeWorld.skin(Family.HULL, 8), 8);
                else ProbeWorld.set(w, at, ProbeWorld.skin(Family.HULL, 0), 0);
            }
        });
        ProbeWorld.sign(w, ProbeMarker.Kind.DEPTH, f, "深度 P0", "");
        p.addChatMessage(
            new ChatComponentTranslation("fluxecho.cmd.probe.depth.on", ProbeWorld.at(f.at(Depth.WALL, 0, 0))));
    }

    static void sky(EntityPlayer p) {
        World w = p.worldObj;
        TileEntitySign old = ProbeWorld.marker(w, ProbeMarker.Kind.SKY, p.posX, p.posY, p.posZ, NEAR);
        if (old != null) {
            ProbeWorld.unsign(w, old);
            p.addChatMessage(new ChatComponentTranslation("fluxecho.cmd.probe.sky.off", ProbeWorld.at(old(old))));
            return;
        }
        ProbeFrame f = ProbeWorld.frame(p);
        int[] at = ProbeMarker.signAt(ProbeMarker.Kind.SKY, f);
        check(w, new int[] { at[0], at[1], at[2], at[0], at[1], at[2] });
        ProbeWorld.sign(w, ProbeMarker.Kind.SKY, f, "天幕 N0", "");
        p.addChatMessage(new ChatComponentTranslation("fluxecho.cmd.probe.sky.on", ProbeWorld.at(at)));
    }

    static void sun(EntityPlayer p) {
        World w = p.worldObj;
        TileEntitySign old = ProbeWorld.marker(w, ProbeMarker.Kind.SUN, p.posX, p.posY, p.posZ, NEAR);
        if (old != null) {
            ProbeFrame f = ProbeWorld.frameOf(w, old, ProbeMarker.Kind.SUN);
            ProbeWorld.quietly(() -> {
                for (int[] c : Sun.mast()) air(w, f.at(c[0], c[1], c[2]));
                for (int[] c : Sun.well()) air(w, f.at(c[0], c[1], c[2]));
            });
            ProbeWorld.unsign(w, old);
            p.addChatMessage(new ChatComponentTranslation("fluxecho.cmd.probe.sun.off", ProbeWorld.at(old(old))));
            return;
        }
        ProbeFrame f = ProbeWorld.frame(p);
        int[] box = ProbeWorld.box(f, Sun.VOLUME);
        check(w, box);
        ProbeWorld.quietly(() -> {
            clear(w, box);
            for (int[] c : Sun.mast()) ProbeWorld.set(w, f.at(c[0], c[1], c[2]), ProbeWorld.skin(Family.HULL, 7), 7);
            for (int[] c : Sun.well()) ProbeWorld.set(w, f.at(c[0], c[1], c[2]), ProbeWorld.skin(Family.FOOTING, 0), 0);
        });
        ProbeWorld.sign(w, ProbeMarker.Kind.SUN, f, "日轨 -40°", "");
        p.addChatMessage(
            new ChatComponentTranslation("fluxecho.cmd.probe.sun.on", ProbeWorld.at(f.at(Sun.MAST_ALONG, 0, 0))));
    }

    static void bed(EntityPlayer p) {
        World w = p.worldObj;
        TileEntitySign old = ProbeWorld.marker(w, ProbeMarker.Kind.BED, p.posX, p.posY, p.posZ, NEAR);
        if (old != null) {
            ProbeFrame f = ProbeWorld.frameOf(w, old, ProbeMarker.Kind.BED);
            int n = ProbeMarker.number(old.signText[3], "n", ProbeRigs.Bed.PER_ROW);
            ProbeWorld.quietly(() -> {
                for (int i = 0; i < n; i++) {
                    int[] b = ProbeRigs.Bed.bed(i);
                    for (int up = 2; up >= 0; up--) air(w, f.at(b[0], b[1], up));
                }
            });
            ProbeWorld.unsign(w, old);
            p.addChatMessage(new ChatComponentTranslation("fluxecho.cmd.probe.bed.off", ProbeWorld.at(old(old))));
            return;
        }
        if (!Mods.botania) throw new CommandException("fluxecho.cmd.probe.bed.no_botania");
        List<ItemStack> kinds = BedFlowers.kinds();
        ProbeFrame f = ProbeWorld.frame(p);
        int[] box = ProbeWorld.box(f, ProbeRigs.Bed.volume(kinds.size()));
        check(w, box);
        List<String> failed = new ArrayList<>();
        ProbeWorld.quietly(() -> {
            clear(w, box);
            for (int i = 0; i < kinds.size(); i++) {
                int[] b = f.at(ProbeRigs.Bed.bed(i)[0], ProbeRigs.Bed.bed(i)[1], 0);
                ProbeWorld.set(w, b, CampusModule.fitting, Parts.F_BED);
                if (!plant(p, w, b, kinds.get(i))) failed.add(
                    kinds.get(i)
                        .getDisplayName());
            }
        });
        ProbeWorld.sign(w, ProbeMarker.Kind.BED, f, "花床 S-P4", "n=" + kinds.size());
        p.addChatMessage(
            new ChatComponentTranslation(
                "fluxecho.cmd.probe.bed.on",
                ProbeWorld.at(f.at(ProbeRigs.Bed.FIRST, 0, 0)),
                kinds.size(),
                kinds.size() - failed.size()));
        if (failed.isEmpty()) p.addChatMessage(new ChatComponentTranslation("fluxecho.cmd.probe.bed.all"));
        else p.addChatMessage(
            new ChatComponentTranslation(
                "fluxecho.cmd.probe.bed.failed",
                failed.size(),
                new ChatComponentText(String.join(", ", failed))));
    }

    /**
     * Places a flower on a bed the way a player's right click on the bed's top does (the item's own placing, so a
     * special flower gets its kind), and whether it took: the flower's block stands on the bed after.
     */
    private static boolean plant(EntityPlayer p, World w, int[] bed, ItemStack kind) {
        ItemStack s = kind.copy();
        if (!(s.getItem() instanceof ItemBlock ib)) return false;
        Block want = ib.field_150939_a;
        boolean used;
        try {
            used = s.getItem()
                .onItemUse(s, p, w, bed[0], bed[1], bed[2], 1, 0.5f, 1f, 0.5f);
        } catch (RuntimeException e) {
            return false;
        }
        return used && w.getBlock(bed[0], bed[1] + 1, bed[2]) == want;
    }

    /** Refuses a box out of the world's heights or with a block entity in it. */
    private static void check(World w, int[] box) {
        if (!ProbeWorld.fits(box)) throw new CommandException("fluxecho.cmd.probe.height");
        int[] te = ProbeWorld.blockEntityIn(w, box);
        if (te != null) throw new CommandException("fluxecho.cmd.probe.blocked", ProbeWorld.at(te));
    }

    /** Clears a box of what stands in it (no block entity stands there: {@link #check}). */
    private static void clear(World w, int[] box) {
        for (int y = box[4]; y >= box[1]; y--) for (int x = box[0]; x <= box[3]; x++)
            for (int z = box[2]; z <= box[5]; z++) if (!w.isAirBlock(x, y, z)) w.setBlockToAir(x, y, z);
    }

    private static void air(World w, int[] at) {
        if (!w.isAirBlock(at[0], at[1], at[2])) w.setBlockToAir(at[0], at[1], at[2]);
    }

    private static int[] old(TileEntitySign s) {
        return new int[] { s.xCoord, s.yCoord, s.zCoord };
    }
}
