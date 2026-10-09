package com.fluxecho.gate.client;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * A camera on the far side of a light gate: where the player's eye comes out when it looks (or steps) through. The
 * game draws the world from it as from the player; it is never in the world, never ticks and is never sent anywhere.
 */
final class GateCamera extends EntityLivingBase {

    private float eyeHeight = 0.12f;

    GateCamera(World w) {
        super(w);
        noClip = true;
    }

    /**
     * Stands at {@code (x, y, z)} looking to {@code yaw} and {@code pitch}, at any partial tick, and takes the
     * viewer's size and the shakes of their camera (hurt, death), so the picture moves with the player's.
     */
    void place(EntityLivingBase viewer, double x, double y, double z, float yaw, float pitch) {
        yOffset = viewer.yOffset;
        ySize = 0;
        width = viewer.width;
        height = viewer.height;
        eyeHeight = viewer.getEyeHeight();
        setPosition(x, y, z);
        prevPosX = lastTickPosX = x;
        prevPosY = lastTickPosY = y;
        prevPosZ = lastTickPosZ = z;
        rotationYaw = prevRotationYaw = rotationYawHead = prevRotationYawHead = yaw;
        rotationPitch = prevRotationPitch = pitch;
        hurtTime = viewer.hurtTime;
        maxHurtTime = viewer.maxHurtTime;
        attackedAtYaw = viewer.attackedAtYaw;
        deathTime = viewer.deathTime;
        boolean dead = viewer.getHealth() <= 0;
        if (dead != getHealth() <= 0) setHealth(dead ? 0 : 1);
    }

    @Override
    public float getEyeHeight() {
        return eyeHeight;
    }

    @Override
    public ItemStack getHeldItem() {
        return null;
    }

    @Override
    public ItemStack getEquipmentInSlot(int slot) {
        return null;
    }

    @Override
    public void setCurrentItemOrArmor(int slot, ItemStack stack) {}

    @Override
    public ItemStack[] getLastActiveItems() {
        return new ItemStack[5];
    }

    @Override
    public void onUpdate() {}

    @Override
    public void writeEntityToNBT(NBTTagCompound t) {}

    @Override
    public void readEntityFromNBT(NBTTagCompound t) {}
}
