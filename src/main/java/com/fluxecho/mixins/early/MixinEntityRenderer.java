package com.fluxecho.mixins.early;

import net.minecraft.client.renderer.EntityRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.fluxecho.gate.client.Portal;

/**
 * The real view through a light gate ({@link Portal}): the far sides are drawn just before the world is, once the
 * mouse has turned the player; a camera beyond a gate bobs like the player's; and its near plane is laid on the far
 * membrane once Angelica has taken the frame's projection.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @Inject(
        method = "updateCameraAndRender",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderWorld(FJ)V"),
        require = 0)
    private void fluxecho$beforeWorld(float pt, CallbackInfo ci) {
        Portal.beforeWorld(pt);
    }

    @Inject(
        method = "updateCameraAndRender",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;renderWorld(FJ)V",
            shift = At.Shift.AFTER),
        require = 0)
    private void fluxecho$afterWorld(float pt, CallbackInfo ci) {
        Portal.afterWorld();
    }

    @Inject(
        method = { "setupCameraTransform", "renderHand" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;setupViewBobbing(F)V",
            shift = At.Shift.AFTER),
        require = 0)
    private void fluxecho$bob(float pt, int pass, CallbackInfo ci) {
        Portal.bob(pt);
    }

    @Inject(
        method = "renderWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/culling/ClippingHelperImpl;getInstance()Lnet/minecraft/client/renderer/culling/ClippingHelper;"),
        require = 0)
    private void fluxecho$oblique(float pt, long nanos, CallbackInfo ci) {
        Portal.oblique();
    }
}
