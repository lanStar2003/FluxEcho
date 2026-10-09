package com.fluxecho.mixins.early;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.fluxecho.gate.Pins;

/**
 * A player leaving a world lets go of the chunks they kept there; and trimming a player's queue of chunks to send
 * down to the square round them keeps the ones they keep.
 */
@Mixin(PlayerManager.class)
public abstract class MixinPlayerManager {

    @Inject(method = "removePlayer", at = @At("HEAD"))
    private void fluxecho$letGo(EntityPlayerMP p, CallbackInfo ci) {
        Pins.leave((PlayerManager) (Object) this, p);
    }

    @Inject(method = "filterChunkLoadQueue", at = @At("HEAD"))
    private void fluxecho$rememberKept(EntityPlayerMP p, CallbackInfo ci) {
        Pins.beforeFilter(p);
    }

    @Inject(method = "filterChunkLoadQueue", at = @At("RETURN"))
    private void fluxecho$putKeptBack(EntityPlayerMP p, CallbackInfo ci) {
        Pins.afterFilter(p);
    }
}
