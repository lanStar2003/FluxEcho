package com.fluxecho.mixins.early;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.player.EntityPlayerMP;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.fluxecho.gate.Sight;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Players see the mobs and players on the far side of the light gates near them: an entity counts as near a player
 * when it is near the place the player looks out from through a gate.
 */
@Mixin(EntityTrackerEntry.class)
public abstract class MixinEntityTrackerEntry {

    @Shadow
    public Entity myEntity;

    @ModifyExpressionValue(
        method = "tryStartWachingThis",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/entity/player/EntityPlayerMP;posX:D",
            opcode = Opcodes.GETFIELD),
        require = 0)
    private double fluxecho$seenX(double x, @Local(argsOnly = true) EntityPlayerMP p) {
        return Sight.trackX(p, myEntity, x);
    }

    @ModifyExpressionValue(
        method = "tryStartWachingThis",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/entity/player/EntityPlayerMP;posZ:D",
            opcode = Opcodes.GETFIELD),
        require = 0)
    private double fluxecho$seenZ(double z, @Local(argsOnly = true) EntityPlayerMP p) {
        return Sight.trackZ(p, myEntity, z);
    }
}
