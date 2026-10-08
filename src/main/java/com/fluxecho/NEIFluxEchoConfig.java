package com.fluxecho;

import java.util.EnumSet;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.fluxecho.core.EchoRecipeMaps;
import com.fluxecho.core.MachineId;
import com.fluxecho.mana.SpringNei;
import com.fluxecho.nei.FluxRecipeHandler;

import codechicken.nei.SearchField;
import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.search.ModNameFilter;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.common.blocks.ItemMachines;

/**
 * NEI plugin (NEI finds it by its name): the echo machines' own pages ({@link FluxRecipeHandler}, the Mana Echo
 * Spring's {@link SpringNei}), and the "@" search.
 * FluxEcho's machines are GT machines, items of GregTech's machine block, so
 * NEI's "@mod" search only finds them under GregTech. This replaces the "@" search with one that also finds any GT
 * machine under the mod its name starts with ({@code "fluxecho.bee.imprinter"} under FluxEcho).
 * <p>
 * NEI keeps only the last provider registered for a prefix, so FluxDepths ships the same filter: whichever wins, both
 * mods' machines are found.
 */
public class NEIFluxEchoConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        API.addSearchProvider(
            new SearchField.SearchParserProvider('@', "modName", EnumChatFormatting.LIGHT_PURPLE, Filter::new));
        if (Mods.botania) SpringNei.register();
        FluxRecipeHandler.registerAll(EnumSet.complementOf(EnumSet.of(MachineId.MANA_ECHO)), EchoRecipeMaps::get);
    }

    @Override
    public String getName() {
        return FluxEcho.NAME;
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }

    /** NEI's mod name filter, plus GT machines under the mod that named them. */
    private static final class Filter extends ModNameFilter {

        private final Pattern pattern;

        Filter(Pattern pattern) {
            super(pattern);
            this.pattern = pattern;
        }

        @Override
        public boolean matches(ItemStack stack) {
            if (super.matches(stack)) return true;
            if (stack == null || !(stack.getItem() instanceof ItemMachines)) return false;
            IMetaTileEntity mte = ItemMachines.getMetaTileEntity(stack);
            String name = mte == null ? null : mte.getMetaName();
            int dot = name == null ? -1 : name.indexOf('.');
            if (dot <= 0) return false;
            ModContainer mod = Loader.instance()
                .getIndexedModList()
                .get(name.substring(0, dot));
            return mod != null && !"gregtech".equals(mod.getModId())
                && pattern.matcher(mod.getName())
                    .find();
        }
    }
}
