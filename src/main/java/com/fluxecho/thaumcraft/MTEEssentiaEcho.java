package com.fluxecho.thaumcraft;

import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.fluxecho.Config;
import com.fluxecho.core.MachineId;

import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import thaumcraft.api.aspects.Aspect;

/**
 * Essentia Echo (MV). Synthesizes essentia of the aspects its team has held once, on demand, through an Essentia
 * Outlet next to it: an infusion matrix within 12 blocks of the outlet draws straight from it, and essentia tubes
 * pull from it. Each essentia costs its primal units in EU and in shard credit.
 * <p>
 * Its own "recipe" only takes in samples: a phial, jar or crystal put into the input teaches its aspects and goes
 * to the output. Shards stay in the input until they are needed.
 */
public class MTEEssentiaEcho extends MTEThaumMachine {

    /** Amperes it takes in, so that a big infusion is paid for quickly. */
    private static final int AMPERES = 4;

    public MTEEssentiaEcho(int id) {
        super(MachineId.ESSENTIA_ECHO, id, 2, 1);
    }

    private MTEEssentiaEcho(String name, String[] description, ITexture[][][] textures) {
        super(MachineId.ESSENTIA_ECHO, name, description, textures, 2, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTEEssentiaEcho(mName, mDescriptionArray, mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return TCRecipeMaps.essentiaEcho();
    }

    @Override
    protected int unitsPerShard() {
        return Config.essentiaPerShard;
    }

    /** Room for a whole infusion: GT's basic machines keep only 64 packets. */
    @Override
    public long maxEUStore() {
        return GTValues.V[mTier] * 2048L;
    }

    @Override
    public long maxAmperesIn() {
        return AMPERES;
    }

    @Override
    protected Object[] tooltipArgs() {
        return new Object[] { Config.essentiaEuPerUnit, Config.essentiaPerShard, AMPERES };
    }

    @Override
    protected int work() {
        UUID team = team();
        if (team == null) return idle("no_owner");
        learnFromInputs(team);
        for (int i = 0; i < mInputSlotCount; i++) {
            ItemStack s = input(i);
            if (!AspectSamples.isEssentia(s)) continue;
            ItemStack back = s.copy();
            if (!canOutput(back)) return blocked();
            s.stackSize = 0;
            mOutputItems[0] = back;
            return start(0, 20);
        }
        return idle(
            AspectMemory.get()
                .count(team) == 0 ? "no_aspect_known" : "ready");
    }

    /** Whether {@code amount} essentia of the aspect can be made right now; with {@code pay}, makes it. */
    public boolean pay(Aspect a, int amount, boolean pay) {
        if (!Config.thaumEnabled || a == null || amount <= 0) return false;
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te == null || !te.isAllowedToWork()) return false;
        UUID team = team();
        if (team == null || !AspectMemory.get()
            .knows(team, a)) return false;
        long units = (long) AspectSamples.units(a) * amount;
        long eu = units * Config.essentiaEuPerUnit;
        if (te.getStoredEU() < eu || !credit(units, false)) return false;
        if (pay) {
            credit(units, true);
            te.decreaseStoredEnergyUnits(eu, true);
        }
        return true;
    }

    /** How many essentia of the aspect could be made right now, up to {@code max}. */
    public int affordable(Aspect a, int max) {
        if (!pay(a, 1, false)) return 0;
        IGregTechTileEntity te = getBaseMetaTileEntity();
        long units = AspectSamples.units(a);
        long byEu = te.getStoredEU() / (units * Config.essentiaEuPerUnit);
        long byCredit = (credit + (long) shardsInInputs() * unitsPerShard()) / units;
        return (int) Math.max(0, Math.min(max, Math.min(byEu, byCredit)));
    }

    /** The outlet's chat line about its echo, translated on the client. */
    IChatComponent describe() {
        UUID team = team();
        return new ChatComponentTranslation(
            "fluxecho.essentia_echo.linked",
            team == null ? 0
                : AspectMemory.get()
                    .count(team),
            getBaseMetaTileEntity().getStoredEU());
    }
}
