package com.fluxecho.mixins.early;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.fluxecho.gate.client.Portal;

/**
 * Angelica's culler hides what is behind walls from the camera. The camera beyond a light gate stands outside the room
 * it looks into (or in the rock behind a gate in a cave), so drawing from there hides nothing; the gate's own frustum
 * keeps the work small. Does nothing without Angelica; the flag is set by name, so a different Angelica only loses
 * this.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.cull.graph.ChunkGraphCuller", remap = false)
public abstract class MixinChunkGraphCuller {

    @Inject(method = "initSearch", at = @At("RETURN"), remap = false, require = 0)
    private void fluxecho$seeThrough(CallbackInfo ci) {
        if (Portal.seeThrough()) Portal.noOcclusion(this);
    }
}
