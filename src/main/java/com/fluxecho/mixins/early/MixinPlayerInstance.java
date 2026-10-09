package com.fluxecho.mixins.early;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.fluxecho.gate.Pins;

/** A chunk a player keeps through a light gate is not taken from them when they move on. */
@Mixin(PlayerManager.PlayerInstance.class)
public abstract class MixinPlayerInstance {

    @Inject(method = "removePlayer", at = @At("HEAD"), cancellable = true)
    private void fluxecho$keep(EntityPlayerMP p, CallbackInfo ci) {
        if (Pins.holds(p, ((PlayerManager.PlayerInstance) (Object) this).chunkLocation)) ci.cancel();
    }
}
