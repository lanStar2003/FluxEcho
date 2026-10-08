package com.fluxecho;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fluxecho.ae.AEModule;
import com.fluxecho.bees.BeeModule;
import com.fluxecho.blood.BloodModule;
import com.fluxecho.codex.CodexModule;
import com.fluxecho.codex.EchoLedger;
import com.fluxecho.core.RecipeCheck;
import com.fluxecho.crops.CropModule;
import com.fluxecho.enchant.EnchantModule;
import com.fluxecho.mana.ManaModule;
import com.fluxecho.mobs.MobModule;
import com.fluxecho.quest.QuestInstaller;
import com.fluxecho.thaumcraft.TCModule;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;

/**
 * FluxEcho: the flux layer remembers what you did once and lets it echo. Each module takes the repetitive part of a
 * mod off your hands once you have done it for real: bees, trees and butterflies (Forestry), Thaumcraft, Blood Magic,
 * prey (MobsInfo), crops (IC2), mana (Botania), enchanted books. The Echo Codex shows what your team has done once,
 * the Echo ME Provider hands the machines to AE2's autocrafting. It also ships the quest lines of FluxEcho and
 * FluxDepths and puts them into BetterQuesting by itself.
 * <p>
 * A module is only called when its mod is loaded ({@link Mods}), so none of an absent mod's classes are touched.
 */
@Mod(
    modid = FluxEcho.MODID,
    name = FluxEcho.NAME,
    version = Tags.VERSION,
    dependencies = "required-after:gregtech;after:Forestry;after:Thaumcraft;after:AWWayofTime;after:betterquesting;"
        + "after:fluxdepths;after:appliedenergistics2;after:ae2fc;after:mobsinfo;after:IC2;after:berriespp;"
        + "after:Botania;after:Waila")
public class FluxEcho {

    public static final String MODID = "fluxecho";
    public static final String NAME = "FluxEcho";
    public static final Logger LOG = LogManager.getLogger("FluxEcho");

    @SidedProxy(clientSide = "com.fluxecho.ClientProxy", serverSide = "com.fluxecho.CommonProxy")
    public static CommonProxy proxy;

    public static final CreativeTabs TAB = new CreativeTabs(MODID) {

        @Override
        public Item getTabIconItem() {
            return CodexModule.codex != null ? CodexModule.codex
                : Mods.forestry && BeeModule.imprint != null ? BeeModule.imprint : Items.ender_pearl;
        }
    };

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        Config.load(e.getSuggestedConfigurationFile());
        Mods.detect();
        QuestInstaller.preInit(e.getModConfigurationDirectory());
        CodexModule.preInit();
        if (Mods.forestry) BeeModule.preInit();
        if (Mods.thaumcraft) TCModule.preInit();
        if (Mods.mobsInfo) MobModule.preInit();
        if (Mods.ic2) CropModule.preInit();
        if (Mods.ae2) AEModule.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        // registered even when a module is off, so placed machines keep their blocks
        if (Mods.forestry) BeeModule.machines();
        if (Mods.thaumcraft) TCModule.machines();
        if (Mods.bloodMagic) BloodModule.machines();
        if (Mods.mobsInfo) MobModule.machines();
        if (Mods.ic2) CropModule.machines();
        if (Mods.botania) ManaModule.machines();
        EnchantModule.machines();
        proxy.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent e) {
        CodexModule.postInit();
        if (Mods.forestry) BeeModule.postInit();
        if (Mods.thaumcraft) TCModule.postInit();
        if (Mods.bloodMagic) BloodModule.postInit();
        if (Mods.mobsInfo) MobModule.postInit();
        if (Mods.ic2) CropModule.postInit();
        if (Mods.botania) ManaModule.postInit();
        EnchantModule.postInit();
        if (Mods.ae2) AEModule.postInit();
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent e) {
        if (Mods.forestry) BeeModule.loadComplete();
        if (Mods.ic2) CropModule.loadComplete();
        RecipeCheck.run();
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent e) {
        QuestInstaller.serverStarted();
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent e) {
        EchoLedger.reset();
        if (Mods.thaumcraft) TCModule.serverStopped();
    }
}
