package com.fluxecho.thaumcraft;

import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.fluxecho.Config;
import com.fluxecho.core.MachineId;
import com.fluxecho.logic.VisCharge;

import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import thaumcraft.api.aspects.Aspect;

/**
 * Vis Charger (MV), retired: the Flux Vis Pedestal replaced it, and its recipe now turns it into one. Placed ones
 * keep working. Charges a wand, sceptre, staff or vis amulet from EU with every primal its team has learned,
 * no node needed. The wand stays in the input while it charges and goes to the output once full; a cycle is capped
 * (see {@link VisCharge}), so a big staff takes a few. Shards in the inputs pay the shard credit.
 */
public class MTEVisCharger extends MTEThaumMachine {

    private static final int CV_PER_VIS = 100;

    /** Centivis per primal the running cycle adds when it ends. */
    private int[] pending;

    public MTEVisCharger(int id) {
        super(MachineId.VIS_CHARGER, id, 2, 1);
    }

    private MTEVisCharger(String name, String[] description, ITexture[][][] textures) {
        super(MachineId.VIS_CHARGER, name, description, textures, 2, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTEVisCharger(mName, mDescriptionArray, mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return TCRecipeMaps.visCharger();
    }

    @Override
    protected int unitsPerShard() {
        return Config.visPerShard;
    }

    @Override
    protected Object[] tooltipArgs() {
        return new Object[] { Config.visEuPerCentiVis * CV_PER_VIS, Config.visPerShard };
    }

    private int wandSlot() {
        for (int i = 0; i < mInputSlotCount; i++) if (VisItems.chargeable(input(i))) return i;
        return -1;
    }

    @Override
    protected int work() {
        UUID team = team();
        if (team == null) return idle("no_owner");
        learnFromInputs(team);
        int slot = wandSlot();
        if (slot < 0) return idle("no_wand");
        ItemStack wand = input(slot);

        // only primals: addRealVis takes nothing else (and claims it did)
        List<Aspect> primals = Aspect.getPrimalAspects();
        int[] room = new int[primals.size()];
        boolean anyKnown = false;
        for (int i = 0; i < room.length; i++) {
            if (!AspectMemory.get()
                .knows(team, primals.get(i))) continue;
            anyKnown = true;
            room[i] = VisItems.room(wand, primals.get(i));
        }
        if (!anyKnown) return idle("no_primal_known");

        VisCharge plan = VisCharge.plan(room, Config.visEuPerCentiVis, (int) GTValues.V[mTier], Config.visMaxTicks);
        if (plan.total() == 0) {
            // full: out it goes
            ItemStack done = wand.copy();
            if (!canOutput(done)) return blocked();
            wand.stackSize = 0;
            mOutputItems[0] = done;
            return start(0, 1);
        }
        long vis = (plan.total() + CV_PER_VIS - 1) / CV_PER_VIS;
        if (!credit(vis, true)) return idle("no_shard");
        pending = plan.add;
        return start(plan.eut, plan.ticks);
    }

    /** The cycle is done: the vis goes into the wand still in the input. */
    @Override
    public void endProcess() {
        super.endProcess();
        int[] add = pending;
        pending = null;
        int slot = wandSlot();
        if (add == null || slot < 0) return;
        ItemStack wand = input(slot);
        List<Aspect> primals = Aspect.getPrimalAspects();
        for (int i = 0; i < add.length && i < primals.size(); i++) VisItems.add(wand, primals.get(i), add[i]);
    }

    @Override
    public void saveNBTData(NBTTagCompound t) {
        super.saveNBTData(t);
        if (pending != null) t.setIntArray("fePending", pending);
    }

    @Override
    public void loadNBTData(NBTTagCompound t) {
        super.loadNBTData(t);
        pending = t.hasKey("fePending") ? t.getIntArray("fePending") : null;
    }
}
