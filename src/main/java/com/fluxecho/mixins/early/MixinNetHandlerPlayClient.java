package com.fluxecho.mixins.early;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.fluxecho.gate.client.Portal;

/** The server moves the player: through a light gate, they come out where their eye already is, still moving. */
@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerPlayClient {

    @Inject(method = "handlePlayerPosLook", at = @At("HEAD"))
    private void fluxecho$beforeJump(S08PacketPlayerPosLook packet, CallbackInfo ci) {
        Portal.beforeJump();
    }

    @Inject(method = "handlePlayerPosLook", at = @At("RETURN"))
    private void fluxecho$afterJump(S08PacketPlayerPosLook packet, CallbackInfo ci) {
        Portal.afterJump();
    }
}
