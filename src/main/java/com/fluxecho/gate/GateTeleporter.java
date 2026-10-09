package com.fluxecho.gate;

import net.minecraft.entity.Entity;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

/** Puts whoever comes through a light gate exactly where the gate says, and builds no portal. */
final class GateTeleporter extends Teleporter {

    private final double x, y, z;
    private final float yaw, pitch;

    GateTeleporter(WorldServer world, double x, double y, double z, float yaw, float pitch) {
        super(world);
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public void placeInPortal(Entity e, double ox, double oy, double oz, float oyaw) {
        e.setLocationAndAngles(x, y, z, yaw, pitch);
        e.motionX = e.motionY = e.motionZ = 0;
    }

    @Override
    public boolean placeInExistingPortal(Entity e, double ox, double oy, double oz, float oyaw) {
        placeInPortal(e, ox, oy, oz, oyaw);
        return true;
    }

    @Override
    public boolean makePortal(Entity e) {
        return true;
    }

    @Override
    public void removeStalePortalLocations(long time) {}
}
