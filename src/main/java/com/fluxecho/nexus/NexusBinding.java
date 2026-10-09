package com.fluxecho.nexus;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.fluxecho.core.Owners;
import com.fluxlite.item.ItemFluxTerminal;

/**
 * Binding a flux terminal to a Flux Nexus (blueprint 3.11): sneak-right-click the nexus's core with the terminal. The
 * terminal then shows that nexus and its research anywhere, and the player's team is bound to the nexus: everything
 * the bound teams researched counts there for all of them. The same again unbinds.
 */
public final class NexusBinding {

    static final String TAG = "feNexus";

    private NexusBinding() {}

    /** The nexus the terminal is bound to: dim, x, y, z; null for none. */
    public static int[] bound(ItemStack terminal) {
        if (terminal == null || !terminal.hasTagCompound()
            || !terminal.getTagCompound()
                .hasKey(TAG))
            return null;
        NBTTagCompound t = terminal.getTagCompound()
            .getCompoundTag(TAG);
        return new int[] { t.getInteger("Dim"), t.getInteger("X"), t.getInteger("Y"), t.getInteger("Z") };
    }

    /** The nexus of the first bound terminal the player carries (the held one first). */
    public static int[] bound(EntityPlayer p) {
        if (p == null) return null;
        ItemStack held = p.getHeldItem();
        if (held != null && held.getItem() instanceof ItemFluxTerminal && bound(held) != null) return bound(held);
        for (ItemStack s : p.inventory.mainInventory)
            if (s != null && s.getItem() instanceof ItemFluxTerminal && bound(s) != null) return bound(s);
        return null;
    }

    /** A terminal used on a block while sneaking: binds to (or unbinds from) a nexus core. */
    public static boolean use(ItemStack stack, EntityPlayer p, World w, int x, int y, int z) {
        if (!p.isSneaking()) return false;
        TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof TileNexus n)) return false;
        if (w.isRemote) return true;
        int dim = w.provider.dimensionId;
        int[] cur = bound(stack);
        boolean same = cur != null && cur[0] == dim && cur[1] == x && cur[2] == y && cur[3] == z;
        UUID team = Owners.team(p.getUniqueID());
        NBTTagCompound t = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        if (same) {
            t.removeTag(TAG);
            stack.setTagCompound(t.hasNoTags() ? null : t);
            n.unbind(team);
            p.addChatMessage(new ChatComponentTranslation("fluxecho.nexus.unbound"));
            return true;
        }
        boolean own = team != null && team.equals(n.team());
        if (!own && !n.openBinding()) {
            p.addChatMessage(new ChatComponentTranslation("fluxecho.nexus.binding_closed", n.ownerName()));
            return true;
        }
        NBTTagCompound b = new NBTTagCompound();
        b.setInteger("Dim", dim);
        b.setInteger("X", x);
        b.setInteger("Y", y);
        b.setInteger("Z", z);
        t.setTag(TAG, b);
        stack.setTagCompound(t);
        n.bind(team);
        p.addChatMessage(
            new ChatComponentTranslation(own ? "fluxecho.nexus.bound_own" : "fluxecho.nexus.bound", n.ownerName()));
        return true;
    }
}
