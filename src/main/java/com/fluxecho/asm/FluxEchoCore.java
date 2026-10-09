package com.fluxecho.asm;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Loads FluxEcho's few changes to Minecraft itself, all for the light gates: the folded zone generates empty, and
 * the chunks a player keeps through a gate are not taken from them (see {@code com.fluxecho.gate.Pins}).
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
        return Arrays.asList("MixinChunkProviderServer", "MixinPlayerManager", "MixinPlayerInstance");
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
