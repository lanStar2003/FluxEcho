package com.fluxecho.thaumcraft;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.fluxecho.Config;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Owners;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ScanResult;
import thaumcraft.common.lib.research.ScanManager;

/**
 * Insight Echo (LV). An item its owner has scanned once sits in the special slot; each cycle takes a paper and gives
 * the owner that item's research points again, as if scanning it anew. Thaumcraft's own soft cap on the aspect pool
 * still applies. Only works while the owner is online (Thaumcraft keeps no data for offline players); points from a
 * cycle that ends while they are away wait until they are back.
 */
public class MTEInsightEcho extends MTEThaumMachine {

    /** Aspect tag to points the finished cycle still owes the owner. */
    private final AspectList owed = new AspectList();

    public MTEInsightEcho(int id) {
        super(MachineId.INSIGHT_ECHO, id, 1, 1);
    }

    private MTEInsightEcho(String name, String[] description, ITexture[][][] textures) {
        super(MachineId.INSIGHT_ECHO, name, description, textures, 1, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTEInsightEcho(mName, mDescriptionArray, mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return TCRecipeMaps.insightEcho();
    }

    /** No shards here: paper is its material. */
    @Override
    protected int unitsPerShard() {
        return 1;
    }

    @Override
    protected Object[] tooltipArgs() {
        return new Object[] { EchoText.seconds(Config.insightTicks), Config.insightEut };
    }

    private EntityPlayerMP owner() {
        return Owners.online(getBaseMetaTileEntity().getOwnerUuid());
    }

    private static ScanResult scanOf(ItemStack s) {
        return new ScanResult((byte) 1, Item.getIdFromItem(s.getItem()), s.getItemDamage(), null, "");
    }

    @Override
    protected int work() {
        UUID id = getBaseMetaTileEntity().getOwnerUuid();
        if (id == null) return idle("no_owner");
        EntityPlayerMP player = owner();
        if (player == null) return idle("owner_offline");
        grantOwed(player);

        ItemStack sample = sample();
        if (sample == null) return idle("no_scan_sample");
        ScanResult scan = scanOf(sample);
        if (!ScanManager.hasBeenScanned(player, scan)) return idle("not_scanned");
        AspectList aspects = ScanManager.getScanAspects(scan, world());
        if (aspects == null || aspects.size() == 0) return idle("no_scan_aspects");

        ItemStack in = input(0);
        if (in == null || in.getItem() != Items.paper) return idle("need_paper");
        in.stackSize--;
        owed.add(aspects);
        return start(Config.insightEut, Config.insightTicks);
    }

    /** Points are granted when the cycle ends. */
    @Override
    public void endProcess() {
        super.endProcess();
        EntityPlayerMP player = owner();
        if (player != null) grantOwed(player);
    }

    /** Gives the owner the points owed, for aspects they have discovered (the rest wait). */
    private void grantOwed(EntityPlayerMP player) {
        if (owed.size() == 0) return;
        String name = player.getCommandSenderName();
        for (Aspect a : owed.getAspects()) {
            if (a == null || !ThaumcraftApiHelper.hasDiscoveredAspect(name, a)) continue;
            ScanManager.checkAndSyncAspectKnowledge(player, a, owed.getAmount(a));
            owed.remove(a);
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound t) {
        super.saveNBTData(t);
        owed.writeToNBT(t, "feOwed");
    }

    @Override
    public void loadNBTData(NBTTagCompound t) {
        super.loadNBTData(t);
        owed.readFromNBT(t, "feOwed");
    }
}
