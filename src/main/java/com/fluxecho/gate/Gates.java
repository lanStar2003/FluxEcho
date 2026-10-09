package com.fluxecho.gate;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.logic.GateGeometry;

/**
 * What happens when a light gate is put down or broken: opening (or reopening) its plot in the flux interior, linking
 * the two gates, and keeping each plot's chunks loaded so what is built there keeps working.
 */
public final class Gates {

    private Gates() {}

    /** A gate was put down outside the interior: link it to its item's plot, or to a new one. */
    static void placed(TileLightGate t, ItemStack stack, EntityPlayer player) {
        GateRegistry r = GateRegistry.get();
        if (r == null || !GateModule.dimensionReady) {
            if (player != null) player.addChatMessage(new ChatComponentTranslation("fluxecho.gate.no_interior"));
            return;
        }
        int plot = ItemBlockLightGate.plot(stack);
        GateRegistry.Entry inner = plot >= 0 ? r.insideOf(plot) : null;
        // a copy of an item whose plot already has a gate outside opens a plot of its own
        if (inner != null && r.partnerOf(inner) != null) {
            plot = -1;
            inner = null;
        }
        if (plot < 0) plot = r.newPlot();
        if (inner == null) inner = makePlot(r, plot);
        if (inner == null) {
            if (player != null) player.addChatMessage(new ChatComponentTranslation("fluxecho.gate.no_interior"));
            return;
        }
        GateRegistry.Entry outer = r
            .add(t.getWorldObj().provider.dimensionId, t.xCoord, t.yCoord, t.zCoord, t.facing, plot, false, 0);
        r.link(outer, inner);
        t.inside = false;
        t.linked = true;
        t.changed();
        setLinked(inner, true);
        if (player != null) player.addChatMessage(new ChatComponentTranslation("fluxecho.gate.opened", plot + 1));
    }

    /** A gate block went away: unlink it; its plot stays as it is for when the gate comes back. */
    static void removed(World w, int x, int y, int z) {
        GateRegistry r = GateRegistry.get();
        if (r == null) return;
        GateRegistry.Entry e = r.at(w.provider.dimensionId, x, y, z);
        if (e == null) return;
        GateRegistry.Entry partner = r.partnerOf(e);
        r.remove(e);
        GateServer.forget(e.id);
        if (partner != null) {
            GateServer.forget(partner.id);
            setLinked(partner, false);
        }
    }

    /** The item a broken gate drops: it remembers the plot. */
    static ItemStack itemFor(TileLightGate t) {
        ItemStack s = new ItemStack(GateModule.gate);
        GateRegistry r = GateRegistry.get();
        GateRegistry.Entry e = r == null ? null
            : r.at(t.getWorldObj().provider.dimensionId, t.xCoord, t.yCoord, t.zCoord);
        if (e != null) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("plot", e.plot);
            s.setTagCompound(tag);
        }
        return s;
    }

    private static void setLinked(GateRegistry.Entry e, boolean linked) {
        World w = DimensionManager.getWorld(e.dim);
        if (w == null || !w.blockExists(e.x, e.y, e.z)) return;
        TileEntity te = w.getTileEntity(e.x, e.y, e.z);
        if (te instanceof TileLightGate t && t.linked != linked) {
            t.linked = linked;
            t.changed();
        }
    }

    static WorldServer interior() {
        MinecraftServer s = MinecraftServer.getServer();
        return s == null || !GateModule.dimensionReady ? null : s.worldServerForDimension(Config.gateDimension);
    }

    /** Lays out a plot's floor and rim, puts its own gate on it and keeps it loaded. */
    private static GateRegistry.Entry makePlot(GateRegistry r, int plot) {
        WorldServer w = interior();
        if (w == null) return null;
        int px = GateGeometry.plotX(plot), y = GateGeometry.FLOOR_Y, h = GateGeometry.PLOT_HALF;
        for (int dx = -h; dx <= h; dx++) for (int dz = -h; dz <= h; dz++) {
            boolean rim = Math.abs(dx) == h || Math.abs(dz) == h;
            w.setBlock(px + dx, y, dz, GateModule.floor, rim ? BlockInteriorFloor.RIM : BlockInteriorFloor.FLOOR, 2);
        }
        GateGeometry.Gate g = GateGeometry.plotGate(plot);
        w.setBlock(g.x, g.y, g.z, GateModule.gate, 0, 2);
        if (!(w.getTileEntity(g.x, g.y, g.z) instanceof TileLightGate t)) return null;
        t.facing = g.facing;
        t.inside = true;
        t.changed();
        GateRegistry.Entry e = r.add(Config.gateDimension, g.x, g.y, g.z, g.facing, plot, true, 0);
        keepLoaded(w, plot);
        FluxEcho.LOG.info("Opened flux interior plot {} at x={} in dimension {}", plot + 1, px, Config.gateDimension);
        return e;
    }

    private static void keepLoaded(World w, int plot) {
        ForgeChunkManager.Ticket ticket = ForgeChunkManager
            .requestTicket(FluxEcho.instance, w, ForgeChunkManager.Type.NORMAL);
        if (ticket == null) {
            FluxEcho.LOG.warn(
                "No chunk loading ticket left for flux interior plot {}; it only runs while someone is there",
                plot + 1);
            return;
        }
        ticket.getModData()
            .setInteger("plot", plot);
        force(ticket, plot);
    }

    private static void force(ForgeChunkManager.Ticket ticket, int plot) {
        int px = GateGeometry.plotX(plot), h = GateGeometry.PLOT_HALF;
        for (int cx = (px - h) >> 4; cx <= (px + h) >> 4; cx++) for (int cz = -h >> 4; cz <= h >> 4; cz++)
            ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(cx, cz));
    }

    /** Brings back the plots' chunk loading when the interior loads. */
    static final class Tickets implements ForgeChunkManager.LoadingCallback {

        @Override
        public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) {
            for (ForgeChunkManager.Ticket t : tickets) {
                if (world.provider.dimensionId == Config.gateDimension && t.getModData()
                    .hasKey("plot"))
                    force(
                        t,
                        t.getModData()
                            .getInteger("plot"));
                else ForgeChunkManager.releaseTicket(t);
            }
        }
    }
}
