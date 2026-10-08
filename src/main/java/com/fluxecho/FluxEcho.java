package com.fluxecho;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * FluxEcho: the flux layer remembers what you did once and lets it echo. Each module takes the repetitive part of a
 * magic mod off your hands once you have done it for real: bees (Forestry), Thaumcraft, Blood Magic. It also ships
 * the quest lines of FluxEcho and FluxDepths and installs them into BetterQuesting.
 */
@Mod(
    modid = FluxEcho.MODID,
    name = FluxEcho.NAME,
    version = Tags.VERSION,
    dependencies = "required-after:gregtech;after:Forestry;after:Thaumcraft;after:AWWayofTime;after:betterquesting;"
        + "after:fluxdepths;after:appliedenergistics2;after:ae2fc")
public class FluxEcho {

    public static final String MODID = "fluxecho";
    public static final String NAME = "FluxEcho";
    public static final Logger LOG = LogManager.getLogger("FluxEcho");

    public static final CreativeTabs TAB = new CreativeTabs(MODID) {

        @Override
        public Item getTabIconItem() {
            return Items.ender_pearl;
        }
    };

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        Config.load(e.getSuggestedConfigurationFile());
        Mods.detect();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {}

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent e) {}

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent e) {}
}
