package com.fluxecho.campus;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.fluxecho.Config;
import com.fluxecho.campus.client.BuildClient;
import com.fluxecho.gate.Sight;
import com.fluxecho.logic.BuildState;
import com.fluxecho.nexus.NexusNet;
import com.fluxecho.nexus.TileNexus;

/**
 * Sends what the campus builder does to the players near a nexus, over the nexus's own channel ({@link NexusNet}):
 * the effects of its launches and clears ({@link NexusNet#BUILD_FX}) and its progress numbers
 * ({@link NexusNet#BUILD_STATE}). The nexus's description packet carries only what changes on state transitions
 * ({@link Campus#writeSync}), so the chunk round the nexus is not re-meshed every second; everything that moves goes
 * here. Players are picked with {@link Sight#near} (directly or through a light gate) within the nexus effect range
 * plus 64 blocks.
 * <p>
 * Both packets carry the nexus's controller as {@code Dim/X/Y/Z} ({@link NexusNet#at}) and its centre as {@code Ce}
 * (cells are packed relative to that centre with {@link com.fluxecho.logic.FxCodec}). {@code BUILD_FX}: {@code T}
 * base world tick; launches {@code P} int[] cells, {@code M} byte[] part codes, {@code L} byte[] flight ticks,
 * {@code O} byte[] launch tick minus {@code T}; clears {@code C} int[] cells, {@code Cb} int[]
 * {@code blockId << 4 | meta}. {@code BUILD_STATE}: {@code W} world tick, {@code K}/{@code Pk} job and plan key (empty
 * without a job), {@code St}/{@code Ps}/{@code Sg} state, pause, stage, {@code Pl}/{@code To} placed and total,
 * {@code Cl}/{@code Bk}/{@code Sk}/{@code Un} cleared, blocked, skipped, unloaded, {@code Tf}/{@code Fd} cells the
 * grading fills and has filled, {@code Eu} EU last tick, {@code Et} estimated ticks left, {@code Fl} cells in flight.
 * The campus sends it when the numbers change and every {@link Campus#STATE_HEARTBEAT} ticks while it has a job, so a
 * player who comes near later is told too.
 */
public final class CampusNet {

    /** How much farther than the nexus effects the build effects are sent. */
    public static final int EXTRA_RANGE = 64;

    private CampusNet() {}

    /**
     * The launches and clears of the last few ticks (called every four ticks while the batch is not empty, and at once
     * when a page of it filled up): one packet a page, so each stays small. The builder resets the batch right after
     * this returns, so everything is copied out here.
     */
    public static void sendFx(TileNexus nexus, FxBatch batch) {
        if (batch == null || batch.isEmpty()) return;
        List<EntityPlayerMP> to = audience(nexus);
        if (to.isEmpty()) return;
        for (FxBatch.Page page : batch.pages()) {
            if (page.launches() == 0 && page.clears() == 0) continue;
            NBTTagCompound t = head(nexus);
            t.setLong("T", page.base());
            if (page.launches() > 0) {
                t.setIntArray("P", page.launchCells());
                t.setByteArray("M", page.launchParts());
                t.setByteArray("L", page.launchFlights());
                t.setByteArray("O", page.launchOffsets());
            }
            if (page.clears() > 0) {
                t.setIntArray("C", page.clearCells());
                t.setIntArray("Cb", page.clearBlocks());
            }
            for (EntityPlayerMP p : to) NexusNet.tell(p, NexusNet.BUILD_FX, t);
        }
    }

    /** The job's progress numbers changed, or a heartbeat is due (called at most every ten ticks). */
    public static void sendState(TileNexus nexus) {
        List<EntityPlayerMP> to = audience(nexus);
        if (to.isEmpty()) return;
        NBTTagCompound t = head(nexus);
        World w = nexus.getWorldObj();
        t.setLong("W", w.getTotalWorldTime());
        Campus c = nexus.campus();
        BuildJob j = c.job();
        if (j == null) {
            t.setString("K", "");
        } else {
            t.setString("K", j.key());
            t.setString("Pk", j.planKey());
            t.setByte(
                "St",
                (byte) j.state()
                    .ordinal());
            t.setByte(
                "Ps",
                (byte) j.pause()
                    .ordinal());
            t.setByte("Sg", (byte) j.stage());
            t.setInteger("Pl", c.placed());
            t.setInteger("To", c.total());
            t.setInteger("Cl", c.cleared());
            t.setInteger("Bk", c.blocked());
            t.setInteger("Sk", c.skipped());
            t.setInteger("Un", c.unloaded());
            t.setInteger("Tf", j.toFill());
            t.setInteger("Fd", j.filled());
            t.setLong("Eu", c.euPerTick());
            // the estimate only matters while the job can still move
            t.setLong("Et", BuildState.ended(j.state()) ? 0 : c.etaTicks());
            t.setShort(
                "Fl",
                (short) j.flights()
                    .size());
        }
        for (EntityPlayerMP p : to) NexusNet.tell(p, NexusNet.BUILD_STATE, t);
    }

    /** The players who should see the nexus's building: near it, or looking at it through a gate. */
    private static List<EntityPlayerMP> audience(TileNexus nexus) {
        World w = nexus == null ? null : nexus.getWorldObj();
        if (w == null || w.isRemote) return java.util.Collections.emptyList();
        int[] c = nexus.centre();
        return Sight.near(w, c[0] + 0.5, c[1] + 0.5, c[2] + 0.5, Config.nexusEffectRange + EXTRA_RANGE);
    }

    /** Where the packet is about: the nexus's controller and its centre. */
    private static NBTTagCompound head(TileNexus nexus) {
        NBTTagCompound t = NexusNet
            .at(nexus.getWorldObj().provider.dimensionId, nexus.xCoord, nexus.yCoord, nexus.zCoord);
        t.setIntArray("Ce", nexus.centre());
        return t;
    }

    /** The client got a build packet ({@link NexusNet} calls this on the client thread). */
    public static void receive(int kind, NBTTagCompound data) {
        if (data == null) return;
        if (kind == NexusNet.BUILD_FX) BuildClient.onFx(data);
        else if (kind == NexusNet.BUILD_STATE) BuildClient.onState(data);
    }
}
