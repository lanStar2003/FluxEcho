package com.fluxecho.nexus;

import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.fluxecho.campus.BuildJob;
import com.fluxecho.campus.Campus;
import com.fluxecho.core.Directory;
import com.fluxecho.core.EchoText;
import com.fluxecho.library.TileLibrary;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.Compact;
import com.fluxecho.logic.PartRecipes;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * Waila on the Flux Nexus's core and its modules' cores, GT-like: what it does, power and compute, the research, the
 * ring, and what its campus builds (营造：job, progress, stage, and the first missing item); a module's dock, power and
 * what it holds. The numbers come from the server.
 */
public final class NexusWaila implements IWailaDataProvider {

    public static void register(IWailaRegistrar r) {
        NexusWaila w = new NexusWaila();
        r.registerBodyProvider(w, BlockNexus.class);
        r.registerNBTProvider(w, BlockNexus.class);
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public List<String> getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        NBTTagCompound t = accessor.getNBTData();
        if (t == null) return tip;
        if (t.hasKey("feNexus")) {
            String status = t.getString("feNexus");
            EnumChatFormatting c = "researching".equals(status) || "manifesting".equals(status)
                || Directory.BUILDING.equals(status) ? EnumChatFormatting.GREEN
                    : "ready".equals(status) ? EnumChatFormatting.AQUA : EnumChatFormatting.GOLD;
            tip.add(c + EchoText.t("nexus.status." + status));
            build(t, tip);
            if (t.getBoolean("feFormed")) {
                tip.add(EchoText.t("waila.nexus.power", Compact.si(t.getLong("feUpkeep")), t.getDouble("feCompute")));
                String r = t.getString("feResearch");
                tip.add(
                    r.isEmpty() ? EnumChatFormatting.GRAY + EchoText.t("nexus.gui.no_research_short")
                        : EchoText.t(
                            "waila.nexus.research",
                            EchoText.t("research.node." + r),
                            Math.round(t.getFloat("feDone") * 100)));
                tip.add(EchoText.t("waila.nexus.ring", t.getInteger("feDocked"), t.getInteger("feOpen")));
            }
            tip.add(EnumChatFormatting.GRAY + EchoText.t("waila.owner", t.getString("feOwner")));
        } else if (t.hasKey("feDock")) {
            String dock = t.getString("feDock");
            boolean ok = "docked".equals(dock) && t.getBoolean("fePowered");
            tip.add(
                (ok ? EnumChatFormatting.GREEN : EnumChatFormatting.GOLD)
                    + EchoText.t("dock." + ("docked".equals(dock) && !t.getBoolean("fePowered") ? "no_power" : dock)));
            tip.add(
                EchoText.t("waila.library", t.getInteger("feSamples"), t.getInteger("feCap"), t.getInteger("feLends")));
            tip.add(EnumChatFormatting.GRAY + EchoText.t("waila.owner", t.getString("feOwner")));
        }
        return tip;
    }

    /** The campus's line: the job, how far it is, its stage (or why it waits), and what it misses most. */
    private static void build(NBTTagCompound t, List<String> tip) {
        if (!t.hasKey("feBuildK")) return;
        String name = BuildJob.name(t.getString("feBuildK"), t.getString("feBuildPk"))
            .getUnformattedText();
        int state = t.getByte("feBuildSt"), pause = t.getByte("feBuildPs");
        String what = NexusGui.phaseText(t.getString("feBuildPk"), state, pause, t.getByte("feBuildSg"));
        boolean paused = state == BuildState.State.PAUSED.ordinal();
        tip.add(
            (paused ? EnumChatFormatting.GOLD : EnumChatFormatting.AQUA)
                + EchoText.t("build.waila", name, t.getInteger("feBuildPct"), what));
        if (t.hasKey("feBuildMi")) {
            ItemStack s = ItemStack.loadItemStackFromNBT(t.getCompoundTag("feBuildMi"));
            if (s != null) tip.add(
                EnumChatFormatting.GRAY
                    + EchoText.t("build.waila_missing", s.getDisplayName(), Compact.si(t.getLong("feBuildMn"))));
        }
    }

    /** Writes the campus's line for {@link #build}: only while an active campus has a job that is not over. */
    private static void build(TileNexus n, NBTTagCompound tag) {
        Campus c = n.campus();
        BuildJob j = c.job();
        if (!c.active() || j == null || BuildState.ended(j.state())) return;
        tag.setString("feBuildK", j.key());
        tag.setString("feBuildPk", j.planKey());
        tag.setByte(
            "feBuildSt",
            (byte) j.state()
                .ordinal());
        tag.setByte(
            "feBuildPs",
            (byte) j.pause()
                .ordinal());
        tag.setByte("feBuildSg", (byte) j.stage());
        int total = c.total();
        tag.setInteger("feBuildPct", total <= 0 ? 0 : (int) Math.min(100, c.placed() * 100L / total));
        for (Map.Entry<String, Long> e : c.bill()
            .entrySet()) {
            ItemStack s = Campus.stackOf(e.getKey(), 1);
            if (s == null) continue;
            tag.setTag("feBuildMi", s.writeToNBT(new NBTTagCompound()));
            tag.setLong("feBuildMn", (e.getValue() + PartRecipes.UNIT - 1) / PartRecipes.UNIT);
            break;
        }
    }

    @Override
    public List<String> getWailaTail(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x,
        int y, int z) {
        if (te instanceof TileNexus n) {
            tag.setString("feNexus", Directory.nexusStatus(n));
            build(n, tag);
            tag.setBoolean("feFormed", n.formed());
            tag.setLong("feUpkeep", n.formed() ? n.upkeep() : 0);
            tag.setDouble("feCompute", n.computeRate());
            tag.setString("feResearch", n.research());
            tag.setFloat("feDone", n.researchFraction());
            tag.setInteger(
                "feDocked",
                n.docked()
                    .size());
            tag.setInteger("feOpen", n.openSlots());
            tag.setString("feOwner", n.ownerName());
        } else if (te instanceof TileLibrary l) {
            tag.setString("feDock", l.dockStatus());
            tag.setBoolean("fePowered", l.powered());
            tag.setInteger("feSamples", l.sampleCount());
            tag.setInteger("feCap", l.capacity());
            tag.setInteger("feLends", l.lendsShown());
            tag.setString("feOwner", l.ownerName());
        }
        return tag;
    }
}
