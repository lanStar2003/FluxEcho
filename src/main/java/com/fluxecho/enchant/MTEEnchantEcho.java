package com.fluxecho.enchant;

import java.util.Collections;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.fluxecho.Config;
import com.fluxecho.codex.Categories;
import com.fluxecho.core.EchoPattern;
import com.fluxecho.core.MTEEchoMachine;
import com.fluxecho.core.MachineId;

import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;

/**
 * Enchant Echo (MV). An enchanted book sits in the special slot and is kept; books and lapis in the inputs become
 * copies of it. EU and lapis go by the levels on the book. Enchantments on the config's blacklist are never copied.
 */
public class MTEEnchantEcho extends MTEEchoMachine {

    public MTEEnchantEcho(int id) {
        super(MachineId.ENCHANT_ECHO, id, 2, 1);
    }

    private MTEEnchantEcho(String name, String[] description, ITexture[][][] textures) {
        super(MachineId.ENCHANT_ECHO, name, description, textures, 2, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity te) {
        return new MTEEnchantEcho(mName, mDescriptionArray, mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return EnchantModule.map();
    }

    @Override
    protected boolean moduleEnabled() {
        return Config.enchantEnabled;
    }

    @Override
    protected Object[] tooltipArgs() {
        return new Object[] { Config.enchantEuPerLevel, Config.lapisPerLevel };
    }

    static boolean isLapis(ItemStack s) {
        if (s == null) return false;
        if (s.getItem() == Items.dye && s.getItemDamage() == 4) return true;
        int lapis = OreDictionary.getOreID("gemLapis");
        for (int id : OreDictionary.getOreIDs(s)) if (id == lapis) return true;
        return false;
    }

    private int slotOf(boolean lapis) {
        for (int i = 0; i < mInputSlotCount; i++) {
            ItemStack s = input(i);
            if (s != null && (lapis ? isLapis(s) : s.getItem() == Items.book)) return i;
        }
        return -1;
    }

    private ItemStack copy() {
        ItemStack c = sample().copy();
        c.stackSize = 1;
        return c;
    }

    @Override
    protected int work() {
        ItemStack sample = sample();
        if (!EnchantBooks.isBook(sample)) return idle("no_enchanted_book");
        if (EnchantBooks.hasBanned(sample)) return idle("enchant_banned");
        int levels = EnchantBooks.levels(sample);
        int lapisNeeded = levels * Config.lapisPerLevel;
        int book = slotOf(false);
        if (book < 0) return idle("no_book");
        int lapis = lapisNeeded > 0 ? slotOf(true) : -1;
        if (lapisNeeded > 0 && (lapis < 0 || input(lapis).stackSize < lapisNeeded)) return idle("no_lapis");
        ItemStack out = copy();
        if (!canOutput(out)) return blocked();

        input(book).stackSize--;
        if (lapisNeeded > 0) input(lapis).stackSize -= lapisNeeded;
        mOutputItems[0] = out;
        for (String k : EnchantBooks.keys(sample)) remember(Categories.ENCHANT, k);
        long eu = (long) levels * Config.enchantEuPerLevel;
        int maxEut = (int) GTValues.V[mTier];
        int ticks = (int) Math.max(20, (eu + maxEut - 1) / maxEut);
        return start((int) Math.max(1, (eu + ticks - 1) / ticks), ticks);
    }

    @Override
    public List<EchoPattern> echoPatterns() {
        ItemStack sample = sample();
        if (!Config.enchantEnabled || !EnchantBooks.isBook(sample) || EnchantBooks.hasBanned(sample))
            return Collections.emptyList();
        int lapis = EnchantBooks.levels(sample) * Config.lapisPerLevel;
        ItemStack[] in = lapis > 0 ? new ItemStack[] { new ItemStack(Items.book), new ItemStack(Items.dye, lapis, 4) }
            : new ItemStack[] { new ItemStack(Items.book) };
        return Collections.singletonList(new EchoPattern(in, new ItemStack[] { copy() }));
    }
}
