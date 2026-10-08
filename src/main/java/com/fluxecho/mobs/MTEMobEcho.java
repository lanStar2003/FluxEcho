package com.fluxecho.mobs;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.MTEEchoMachine;
import com.fluxecho.core.MachineId;
import com.fluxecho.logic.ItemFilter;
import com.fluxecho.logic.KillCost;
import com.kuba6000.mobsinfo.api.MobRecipe;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;

/**
 * Prey Echo (LV). A prey imprint in the special slot (kept) names a mob its owner killed once; each cycle the machine
 * drops that mob's loot as if the owner had killed it again, from MobsInfo's drop table, EU in proportion to the
 * mob's health. A weapon in the input adds its Looting and wears by one point per kill. Bosses cost many times more
 * and can be switched off.
 */
public class MTEMobEcho extends MTEEchoMachine {

    private static ItemFilter blacklist;

    public MTEMobEcho(int id) {
        super(MachineId.MOB_ECHO, id, 1, 4);
    }

    private MTEMobEcho(String name, String[] description, ITexture[][][] textures) {
        super(MachineId.MOB_ECHO, name, description, textures, 1, 4);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTEMobEcho(mName, mDescriptionArray, mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return MobModule.map();
    }

    @Override
    protected boolean moduleEnabled() {
        return Config.mobsEnabled;
    }

    @Override
    protected Object[] tooltipArgs() {
        return new Object[] { Config.mobEuPerHealth, EchoText.seconds(Config.mobMinTicks), Config.mobBossMultiplier };
    }

    private static synchronized ItemFilter blacklist() {
        if (blacklist == null) blacklist = new ItemFilter(Config.mobDropBlacklist);
        return blacklist;
    }

    static boolean banned(ItemStack s) {
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(s.getItem());
        return id != null && blacklist().matches(id.modId + ":" + id.name, s.getItemDamage());
    }

    /** The drops of one kill, alike stacks merged, at most as many stacks as there are output slots. */
    private List<ItemStack> drops(MobRecipe r, int looting) {
        List<ItemStack> out = new ArrayList<>();
        ItemStack[] raw;
        try {
            raw = r.generateRandomOutputs(world(), world().rand, looting, true, false);
        } catch (RuntimeException e) {
            FluxEcho.LOG.warn("MobsInfo could not roll the drops of {}", r.entityName, e);
            return out;
        }
        for (ItemStack s : raw) {
            if (s == null || s.stackSize <= 0 || banned(s)) continue;
            boolean merged = false;
            for (ItemStack o : out) {
                if (o.isItemEqual(s) && ItemStack.areItemStackTagsEqual(o, s)
                    && o.stackSize + s.stackSize <= o.getMaxStackSize()) {
                    o.stackSize += s.stackSize;
                    merged = true;
                    break;
                }
            }
            if (!merged && out.size() < mOutputItems.length) out.add(s.copy());
        }
        return out;
    }

    /** The weapon's Looting and how worn it is. */
    @Override
    protected String info() {
        ItemStack weapon = input(0);
        if (weapon == null) return encode("mob_echo.gui_bare");
        int looting = EnchantmentHelper.getEnchantmentLevel(Enchantment.looting.effectId, weapon);
        int left = weapon.isItemStackDamageable()
            ? (int) Math
                .round(100.0 * (weapon.getMaxDamage() - weapon.getItemDamage()) / Math.max(1, weapon.getMaxDamage()))
            : 100;
        return encode("mob_echo.gui", looting, left);
    }

    @Override
    protected int work() {
        String mob = MobImprints.mob(sample());
        if (mob == null) return idle("no_mob_imprint");
        MobRecipe r = MobImprints.recipe(mob);
        if (r == null) return idle("no_mob_drops");
        boolean boss = r.entity instanceof IBossDisplayData;
        if (boss && !Config.mobBosses) return idle("boss_disabled");

        ItemStack weapon = input(0);
        int looting = weapon == null ? 0 : EnchantmentHelper.getEnchantmentLevel(Enchantment.looting.effectId, weapon);
        List<ItemStack> drops = drops(r, looting);
        ItemStack[] out = drops.toArray(new ItemStack[0]);
        if (out.length > 0 && !canOutput(out)) return blocked();

        if (weapon != null && weapon.isItemStackDamageable() && weapon.attemptDamageItem(1, world().rand)) {
            weapon.stackSize--;
            if (weapon.stackSize <= 0) mInventory[getInputSlot()] = null;
        }
        for (int i = 0; i < mOutputItems.length; i++) mOutputItems[i] = i < out.length ? out[i] : null;
        KillCost cost = KillCost.of(
            r.maxEntityHealth,
            Config.mobEuPerHealth,
            boss ? Config.mobBossMultiplier : 1,
            (int) GTValues.V[mTier],
            Config.mobMinTicks);
        return start(cost.eut, cost.ticks);
    }
}
