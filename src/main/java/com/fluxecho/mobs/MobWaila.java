package com.fluxecho.mobs;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.fluxecho.Config;
import com.fluxecho.codex.Categories;
import com.fluxecho.codex.ClientLedger;
import com.fluxecho.core.EchoText;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaEntityAccessor;
import mcp.mobius.waila.api.IWailaEntityProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/** Waila on a mob: whether your team has its prey imprint, or how to get one. */
public final class MobWaila implements IWailaEntityProvider {

    public static void register(IWailaRegistrar r) {
        r.registerBodyProvider(new MobWaila(), EntityLivingBase.class);
    }

    @Override
    public Entity getWailaOverride(IWailaEntityAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(Entity entity, List<String> tip, IWailaEntityAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public List<String> getWailaBody(Entity entity, List<String> tip, IWailaEntityAccessor accessor,
        IWailaConfigHandler config) {
        if (!Config.codexHints || entity instanceof EntityPlayer) return tip;
        String mob = MobImprints.name(entity);
        if (mob == null) return tip;
        if (ClientLedger.has(Categories.MOB, mob)) tip.add(EnumChatFormatting.AQUA + EchoText.t("codex.done"));
        else tip.add(EnumChatFormatting.DARK_GRAY + EchoText.t("codex.todo." + Categories.MOB));
        return tip;
    }

    @Override
    public List<String> getWailaTail(Entity entity, List<String> tip, IWailaEntityAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, Entity ent, NBTTagCompound tag, World world) {
        return tag;
    }
}
