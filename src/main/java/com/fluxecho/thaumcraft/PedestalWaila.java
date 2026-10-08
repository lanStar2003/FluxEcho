package com.fluxecho.thaumcraft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.fluxecho.core.EchoText;
import com.fluxecho.logic.Compact;

import gregtech.api.util.GTUtility;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * Waila on a Flux Vis Pedestal, like on a GT machine: stored EU, what comes in and what it spends, how fast it draws
 * vis, the modules, the wand and what it is doing. The numbers come from the server.
 */
public final class PedestalWaila implements IWailaDataProvider {

    private static final EnumChatFormatting[] STATE_COLOR = { EnumChatFormatting.GRAY, EnumChatFormatting.GREEN,
        EnumChatFormatting.AQUA, EnumChatFormatting.RED };

    public static void register(IWailaRegistrar r) {
        PedestalWaila w = new PedestalWaila();
        r.registerBodyProvider(w, BlockVisPedestal.class);
        r.registerNBTProvider(w, BlockVisPedestal.class);
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
        if (t == null || !t.hasKey("feEnergy")) return tip;
        int state = Math.max(0, Math.min(STATE_COLOR.length - 1, t.getByte("feState")));
        tip.add(STATE_COLOR[state] + EchoText.t("holo.state." + state));
        tip.add(
            EchoText.t(
                "waila.pedestal.energy",
                GTUtility.formatNumbers(t.getLong("feEnergy")),
                GTUtility.formatNumbers(t.getLong("feCap"))));
        tip.add(
            EchoText.t(
                "waila.pedestal.io",
                GTUtility.formatNumbers(t.getLong("feIn")),
                GTUtility.formatNumbers(t.getLong("feOut"))));
        tip.add(
            EchoText.t(
                "waila.pedestal.rate",
                Compact.visPerSecond(t.getInteger("feRate")),
                GTUtility.formatNumbers(t.getInteger("feEuPerCv") * 100L)));
        List<String> parts = new ArrayList<>();
        int extraction = t.getInteger("feExtraction");
        if (extraction > 0) parts.add(EchoText.t("holo.module.extraction", extraction));
        if (t.getBoolean("feWireless")) parts.add(EchoText.t("holo.module.wireless"));
        if (t.getBoolean("feLink")) parts.add(EchoText.t("holo.module.link"));
        tip.add(
            EchoText.t(
                "waila.pedestal.modules",
                parts.isEmpty() ? EchoText.t("holo.module.none") : String.join(" ", parts)));
        if (t.hasKey("feWand")) tip.add(
            EchoText.t(
                "waila.pedestal.wand",
                t.getString("feWand"),
                Compact.vis(t.getLong("feVis")),
                Compact.vis(t.getLong("feVisMax"))));
        else tip.add(EnumChatFormatting.DARK_GRAY + EchoText.t("holo.no_wand"));
        return tip;
    }

    @Override
    public List<String> getWailaTail(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x,
        int y, int z) {
        if (te instanceof TileVisPedestal p) p.writeWaila(tag);
        return tag;
    }
}
