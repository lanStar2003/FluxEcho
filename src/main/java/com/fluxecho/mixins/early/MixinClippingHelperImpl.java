package com.fluxecho.mixins.early;

import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.fluxecho.gate.client.Portal;

/** While the world is drawn from beyond a light gate, the frustum is what can be seen through the gate. */
@Mixin(ClippingHelperImpl.class)
public abstract class MixinClippingHelperImpl {

    @Inject(method = "getInstance", at = @At("RETURN"))
    private static void fluxecho$throughTheGate(CallbackInfoReturnable<ClippingHelper> cir) {
        Portal.planes(cir.getReturnValue());
    }
}
