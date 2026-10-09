package com.fluxecho.gate;

import java.util.ArrayList;
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
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.logic.FoldedZone;
import com.fluxecho.logic.GateGeometry;
import com.fluxecho.logic.GatePins;
import com.fluxecho.logic.RoomPlan;

/**
 * What happens when a light gate is put down or broken: making (or finding again) its room in the folded zone of the
 * gate's dimension, linking the two gates, keeping each room loaded so what is built there keeps working, and moving
 * the 0.8.1 prototype's plots into the zone.
 */
public final class Gates {

    private Gates() {}

    /** A gate was put down outside the zone: link it to its item's room, or to a new one. */
    static void placed(TileLightGate t, ItemStack stack, EntityPlayer player) {
        GateRegistry r = GateRegistry.get();
        if (r == null || !(t.getWorldObj() instanceof WorldServer w)) return;
        int plot = ItemBlockLightGate.plot(stack);
        GateRegistry.Entry inner = plot >= 0 ? r.insideOf(plot) : null;
        // a copy of an item whose room already has a gate outside (or whose room is elsewhere) opens a room of its own
        if (inner != null && (r.partnerOf(inner) != null || inner.dim != w.provider.dimensionId)) {
            plot = -1;
            inner = null;
        }
        if (inner == null) inner = makeRoom(
            r,
            w,
            plot >= 0 ? plot : r.newPlot(),
            ItemBlockLightGate.room(stack),
            w.getBiomeGenForCoords(t.xCoord, t.zCoord));
        GateRegistry.Entry outer = r
            .add(w.provider.dimensionId, t.xCoord, t.yCoord, t.zCoord, t.facing, inner.plot, false, 0);
        r.link(outer, inner);
        sync(r, outer);
        sync(r, inner);
        if (player != null) player.addChatMessage(
            new ChatComponentTranslation(
                "fluxecho.gate.opened",
                new ChatComponentTranslation("fluxecho.gate.room." + RoomPlan.template(inner.room).id),
                inner.plot + 1));
    }

    /** A gate block went away: unlink it; its room stays as it is for when the gate comes back. */
    static void removed(World w, int x, int y, int z) {
        GateRegistry r = GateRegistry.get();
        if (r == null) return;
        GateRegistry.Entry e = r.at(w.provider.dimensionId, x, y, z);
        if (e == null) return;
        GateRegistry.Entry partner = r.partnerOf(e);
        r.remove(e);
        if (partner != null) sync(r, partner);
    }

    /** The item a broken gate drops: it remembers the room. */
    static ItemStack itemFor(TileLightGate t) {
        ItemStack s = new ItemStack(GateModule.gate);
        GateRegistry r = GateRegistry.get();
        GateRegistry.Entry e = r == null ? null
            : r.at(t.getWorldObj().provider.dimensionId, t.xCoord, t.yCoord, t.zCoord);
        if (e != null) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("plot", e.plot);
            GateRegistry.Entry inner = r.insideOf(e.plot);
            if (inner != null) tag.setString("room", RoomPlan.template(inner.room).id);
            s.setTagCompound(tag);
        }
        return s;
    }

    /** What a gate shows: all of the room for the gate outside, the outside in front of its partner for the room's. */
    static GateGeometry.Box farBox(GateRegistry r, GateRegistry.Entry e) {
        GateRegistry.Entry p = r.partnerOf(e);
        if (p == null || p.dim != e.dim) return null;
        if (e.inside) return GatePins.outsideView(p.gate());
        return roomBox(p);
    }

    /** The blocks of the room whose own gate this is. */
    static GateGeometry.Box roomBox(GateRegistry.Entry inner) {
        if (inner.roomBox == null) inner.roomBox = new RoomPlan(inner.room)
            .box(FoldedZone.centerX(inner.plot), FoldedZone.FLOOR_Y, FoldedZone.centerZ(inner.plot));
        return inner.roomBox;
    }

    /** Puts the registry's word into the gate's tile, when it is loaded, and tells the clients if it changed. */
    static void sync(GateRegistry r, GateRegistry.Entry e) {
        World w = DimensionManager.getWorld(e.dim);
        if (w == null || !w.blockExists(e.x, e.y, e.z)) return;
        TileEntity te = w.getTileEntity(e.x, e.y, e.z);
        if (!(te instanceof TileLightGate t)) return;
        boolean changed = t.link(r.partnerOf(e), farBox(r, e));
        if (t.facing != e.facing || t.inside != e.inside) {
            t.facing = e.facing;
            t.inside = e.inside;
            changed = true;
        }
        if (changed) t.changed();
    }

    /** Builds a room in the folded zone of {@code w}, puts its own gate in it and keeps it loaded. */
    static GateRegistry.Entry makeRoom(GateRegistry r, WorldServer w, int plot, String template, BiomeGenBase biome) {
        RoomPlan plan = new RoomPlan(template);
        int cx = FoldedZone.centerX(plot), cz = FoldedZone.centerZ(plot), fy = FoldedZone.FLOOR_Y;
        long start = System.nanoTime();
        Rooms.build(w, plan, cx, fy, cz, biome == null ? Zone.biome(w) : biome);
        GateGeometry.Gate g = plan.gate(cx, fy, cz);
        w.setBlock(g.x, g.y, g.z, GateModule.gate, 0, 2);
        if (w.getTileEntity(g.x, g.y, g.z) instanceof TileLightGate t) {
            t.facing = g.facing;
            t.inside = true;
            t.changed();
        }
        GateRegistry.Entry old = r.insideOf(plot);
        if (old != null) r.remove(old);
        GateRegistry.Entry e = r.add(w.provider.dimensionId, g.x, g.y, g.z, g.facing, plot, true, 0);
        e.room = plan.t.id;
        keepLoaded(w, plot, plan.box(cx, fy, cz));
        FluxEcho.LOG.info(
            "Made light gate room {} ({}) at {} {} {} in dimension {}, in {} ms",
            plot + 1,
            plan.t.id,
            cx,
            fy,
            cz,
            w.provider.dimensionId,
            (System.nanoTime() - start) / 1_000_000);
        return e;
    }

    // ---- keeping rooms loaded

    private static void keepLoaded(World w, int plot, GateGeometry.Box box) {
        List<ChunkCoordIntPair> chunks = new ArrayList<>();
        for (int x = box.minX >> 4; x <= box.maxX >> 4; x++)
            for (int z = box.minZ >> 4; z <= box.maxZ >> 4; z++) chunks.add(new ChunkCoordIntPair(x, z));
        int i = 0;
        while (i < chunks.size()) {
            ForgeChunkManager.Ticket ticket = ForgeChunkManager
                .requestTicket(FluxEcho.instance, w, ForgeChunkManager.Type.NORMAL);
            if (ticket == null) {
                FluxEcho.LOG.warn(
                    "No chunk loading ticket left for light gate room {}; part of it only runs while someone is near",
                    plot + 1);
                return;
            }
            int n = Math.min(ticket.getChunkListDepth(), chunks.size() - i);
            int[] list = new int[n * 2];
            for (int j = 0; j < n; j++) {
                list[j * 2] = chunks.get(i + j).chunkXPos;
                list[j * 2 + 1] = chunks.get(i + j).chunkZPos;
            }
            ticket.getModData()
                .setInteger("room", plot);
            ticket.getModData()
                .setIntArray("chunks", list);
            force(ticket, list);
            i += n;
        }
    }

    private static void force(ForgeChunkManager.Ticket ticket, int[] list) {
        for (int j = 0; j + 1 < list.length; j += 2)
            ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(list[j], list[j + 1]));
    }

    /** Brings back the rooms' chunk loading when a world loads; the 0.8.1 plots' tickets are let go. */
    static final class Tickets implements ForgeChunkManager.LoadingCallback {

        @Override
        public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) {
            for (ForgeChunkManager.Ticket t : tickets) {
                if (t.getModData()
                    .hasKey("room"))
                    force(
                        t,
                        t.getModData()
                            .getIntArray("chunks"));
                else ForgeChunkManager.releaseTicket(t);
            }
        }
    }

    // ---- the 0.8.1 prototype

    /**
     * Gives every gate whose plot is in the 0.8.1 prototype's interior dimension a room in its own dimension's folded
     * zone instead. What was built on the old plots stays there.
     */
    static void moveOldPlots(GateRegistry r) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        for (GateRegistry.Entry e : r.all()) {
            if (!e.inside || e.dim != Config.gateDimension) continue;
            GateRegistry.Entry outer = r.partnerOf(e);
            r.remove(e);
            if (outer == null || outer.dim == Config.gateDimension) continue;
            WorldServer w = server.worldServerForDimension(outer.dim);
            if (w == null) continue;
            GateRegistry.Entry inner = makeRoom(
                r,
                w,
                r.newPlot(),
                RoomPlan.DEFAULT,
                w.getBiomeGenForCoords(outer.x, outer.z));
            outer.plot = inner.plot;
            r.link(outer, inner);
            sync(r, outer);
            sync(r, inner);
            FluxEcho.LOG.info(
                "Moved the light gate at {} {} {} (dimension {}) from the old flux interior to room {} in its own dimension",
                outer.x,
                outer.y,
                outer.z,
                outer.dim,
                inner.plot + 1);
        }
    }
}
