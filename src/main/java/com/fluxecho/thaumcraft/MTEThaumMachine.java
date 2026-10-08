package com.fluxecho.thaumcraft;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.fluxecho.Config;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.MTEEchoMachine;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Owners;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

/**
 * A FluxEcho Thaumcraft machine. It learns aspects for its team from essentia and shards put into it (or held in
 * the hand when right-clicking it), and pays for its work with "shard credit": each vis crystal shard taken from the
 * inputs is worth a number of units, the rest is kept for next time.
 */
public abstract class MTEThaumMachine extends MTEEchoMachine {

    /** Units already paid for by shards. */
    protected int credit;

    protected MTEThaumMachine(MachineId kind, int id, int inputs, int outputs) {
        super(kind, id, inputs, outputs);
    }

    protected MTEThaumMachine(MachineId kind, String name, String[] description, ITexture[][][] textures, int inputs,
        int outputs) {
        super(kind, name, description, textures, inputs, outputs);
    }

    /** Units one shard is worth in this machine. */
    protected abstract int unitsPerShard();

    @Override
    protected boolean moduleEnabled() {
        return Config.thaumEnabled;
    }

    /** Teaches the team every aspect in the inputs (shards and essentia stay where they are). */
    protected void learnFromInputs(UUID team) {
        if (team == null) return;
        for (int i = 0; i < mInputSlotCount; i++) {
            List<Aspect> taught = AspectSamples.taught(input(i));
            if (!taught.isEmpty()) AspectMemory.get()
                .learn(team, taught);
        }
    }

    /** Shards in the inputs. */
    protected int shardsInInputs() {
        int n = 0;
        for (int i = 0; i < mInputSlotCount; i++) {
            ItemStack s = input(i);
            if (AspectSamples.isShard(s)) n += s.stackSize;
        }
        return n;
    }

    /** Whether the credit and the shards in the inputs cover the units; with {@code pay}, takes them. */
    protected boolean credit(long units, boolean pay) {
        if (units <= credit) {
            if (pay) credit -= (int) units;
            return true;
        }
        long missing = units - credit;
        long shards = (missing + unitsPerShard() - 1) / unitsPerShard();
        if (shards > shardsInInputs()) return false;
        if (!pay) return true;
        for (int i = 0; i < mInputSlotCount && shards > 0; i++) {
            ItemStack s = input(i);
            if (!AspectSamples.isShard(s)) continue;
            int take = (int) Math.min(shards, s.stackSize);
            s.stackSize -= take;
            if (s.stackSize <= 0) mInventory[getInputSlot() + i] = null;
            shards -= take;
            credit += take * unitsPerShard();
        }
        credit -= (int) units;
        return true;
    }

    /** Whether the owner has finished the research: asked while they are online, remembered for when they are not. */
    protected static boolean researched(UUID owner, String key) {
        if (key == null || key.isEmpty()) return true;
        AspectMemory m = AspectMemory.get();
        if (m.researched(owner, key)) return true;
        EntityPlayerMP p = Owners.online(owner);
        if (p == null || !ThaumcraftApiHelper.isResearchComplete(p.getCommandSenderName(), key)) return false;
        m.addResearch(owner, key);
        return true;
    }

    /** Whether the team has learned every aspect in the list. */
    protected static boolean knowsAll(UUID team, AspectList aspects) {
        if (aspects == null) return true;
        for (Aspect a : aspects.getAspects()) if (a != null && !AspectMemory.get()
            .knows(team, a)) return false;
        return true;
    }

    /** Primal units of the essentia in the list (see {@link AspectSamples#units}). */
    protected static long units(AspectList aspects) {
        long u = 0;
        if (aspects != null) for (Aspect a : aspects.getAspects())
            if (a != null) u += (long) AspectSamples.units(a) * aspects.getAmount(a);
        return u;
    }

    /** Right-click with essentia or a shard in hand: learn it, keep it. */
    @Override
    public boolean onRightclick(IGregTechTileEntity te, EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        List<Aspect> taught = AspectSamples.taught(held);
        if (taught.isEmpty()) return super.onRightclick(te, player);
        if (te.isServerSide()) {
            UUID team = team();
            if (team == null) player.addChatMessage(new ChatComponentTranslation("fluxecho.aspect.no_owner"));
            else {
                int added = AspectMemory.get()
                    .learn(team, taught);
                player.addChatMessage(
                    new ChatComponentTranslation(
                        added > 0 ? "fluxecho.aspect.learned" : "fluxecho.aspect.known",
                        names(taught),
                        AspectMemory.get()
                            .count(team)));
            }
        }
        return true;
    }

    static String names(List<Aspect> aspects) {
        StringBuilder b = new StringBuilder();
        for (Aspect a : aspects) {
            if (b.length() > 0) b.append(", ");
            b.append(a.getName());
        }
        return b.toString();
    }

    @Override
    public void saveNBTData(NBTTagCompound t) {
        super.saveNBTData(t);
        t.setInteger("feCredit", credit);
    }

    @Override
    public void loadNBTData(NBTTagCompound t) {
        super.loadNBTData(t);
        credit = Math.max(0, t.getInteger("feCredit"));
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        UUID team = team();
        tag.setInteger(
            "feKnown",
            team == null ? 0
                : AspectMemory.get()
                    .count(team));
        tag.setInteger("feCredit", credit);
    }

    @Override
    public void getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(stack, tip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        if (tag.hasKey("feKnown"))
            tip.add(EchoText.t("aspect.waila", tag.getInteger("feKnown"), tag.getInteger("feCredit")));
    }
}
