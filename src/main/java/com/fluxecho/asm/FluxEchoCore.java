package com.fluxecho.asm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Loads FluxEcho's few changes to Minecraft itself, all for the light gates: the folded zone generates empty, the
 * chunks a player keeps through a gate are not taken from them (see {@code com.fluxecho.gate.Pins}), the entities on
 * the far side are sent to them ({@code com.fluxecho.gate.Sight}), and on the client the world is drawn from beyond
 * a gate ({@code com.fluxecho.gate.client.Portal}).
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("FluxEcho Core")
@IFMLLoadingPlugin.TransformerExclusions("com.fluxecho.asm")
public class FluxEchoCore implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.fluxecho.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        List<String> m = new ArrayList<>(
            Arrays.asList(
                "MixinChunkProviderServer",
                "MixinPlayerManager",
                "MixinPlayerInstance",
                "MixinEntityTrackerEntry"));
        if (FMLLaunchHandler.side()
            .isClient()) {
            m.addAll(
                Arrays.asList(
                    "MixinEntityRenderer",
                    "MixinClippingHelperImpl",
                    "MixinActiveRenderInfo",
                    "MixinNetHandlerPlayClient",
                    // Angelica's culler: a pseudo mixin, nothing happens without Angelica
                    "MixinChunkGraphCuller"));
        }
        return m;
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
