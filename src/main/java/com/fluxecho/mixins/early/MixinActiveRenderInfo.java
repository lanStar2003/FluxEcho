package com.fluxecho.mixins.early;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.fluxecho.gate.client.Portal;

/**
 * A camera beyond a light gate: particles face it rather than the player, and it is in whatever the player's eye is
 * in (air or water), for fog and the field of view.
 */
@Mixin(ActiveRenderInfo.class)
public abstract class MixinActiveRenderInfo {

    @Inject(method = "updateRenderInfo", at = @At("TAIL"))
    private static void fluxecho$billboards(EntityPlayer p, boolean inverted, CallbackInfo ci) {
        Portal.billboards(inverted);
    }

    @Inject(method = "getBlockAtEntityViewpoint", at = @At("HEAD"), cancellable = true)
    private static void fluxecho$medium(World w, EntityLivingBase e, float pt, CallbackInfoReturnable<Block> cir) {
        Block b = Portal.medium(e);
        if (b != null) cir.setReturnValue(b);
    }
}
