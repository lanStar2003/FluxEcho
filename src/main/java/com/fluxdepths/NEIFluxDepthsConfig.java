package com.fluxdepths;

import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.fluxdepths.nei.VeinHandler;
import com.fluxdepths.shard.Collectors;

import codechicken.nei.SearchField;
import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.search.ModNameFilter;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.common.blocks.ItemMachines;

/**
 * NEI plugin (NEI finds it by its name).
 * <ul>
 * <li>The Flux Shard Collector's own page ({@link VeinHandler}): a tab of its own with the collector as its icon and
 * catalyst. The old tiered collectors are hidden.</li>
 * <li>The collectors are GT machines, items of GregTech's machine block, so NEI's "@mod" search only finds them under
 * GregTech. This replaces the "@" search with one that also finds any GT machine under the mod its name starts with
 * ({@code "fluxdepths.shard.collector"} under FluxDepths). NEI keeps only the last provider registered for a prefix,
 * so FluxEcho ships the same filter: whichever wins, both mods' machines are found.</li>
 * </ul>
 */
public class NEIFluxDepthsConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        API.addSearchProvider(
            new SearchField.SearchParserProvider('@', "modName", EnumChatFormatting.LIGHT_PURPLE, Filter::new));

        VeinHandler veins = new VeinHandler();
        API.registerRecipeHandler(veins);
        API.registerUsageHandler(veins);
        ItemStack collector = Collectors.main();
        HandlerInfo.Builder info = new HandlerInfo.Builder(VeinHandler.ID, FluxDepths.NAME, FluxDepths.MODID)
            .setHeight(VeinHandler.HEIGHT)
            .setWidth(VeinHandler.WIDTH)
            .setMaxRecipesPerPage(2);
        if (collector != null) {
            info.setDisplayStack(collector);
            API.addRecipeCatalyst(collector, VeinHandler.ID);
        }
        // NEI rebuilds its tab table when it loads handler info, merging what mods hand it; whichever comes first, the
        // tab keeps its size and icon
        HandlerInfo built = info.build();
        GuiRecipeTab.handlerMap.put(VeinHandler.ID, built);
        GuiRecipeTab.handlerAdderFromIMC.put(VeinHandler.ID, built);
        for (ItemStack old : Collectors.legacy()) API.hideItem(old);
    }

    @Override
    public String getName() {
        return FluxDepths.NAME;
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
