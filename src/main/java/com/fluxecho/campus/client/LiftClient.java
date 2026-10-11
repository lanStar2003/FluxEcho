package com.fluxecho.campus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.fluxecho.FluxEcho;
import com.fluxecho.campus.Lift;
import com.fluxecho.logic.LiftInput;
import com.fluxecho.logic.LiftLook;
import com.fluxecho.nexus.NexusNet;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The light lift on the client (0.12.0, {@link Lift}): a press of jump (up) or sneak (down) while the local player
 * stands on a pad asks the server for a ride, once per press (the key must be let go before the next); the server's
 * answer starts the ride, and every tick before the player moves its motion is set towards the ride's goal and back
 * to the column's middle ({@link Lift#clientTick}). While it waits for an answer nothing changes, so the player only
 * moves through blocks once the server lets it through them too.
 * <p>
 * The keys are read live at the start of the player's tick ({@link LiftInput}): vanilla applies them before it ticks
 * the world and jumps later in the same tick, so the player still stands on the pad when the press is seen; the pad
 * it stood on is also remembered for a moment, so a press seen after the jump took it off the pad still asks.
 * <p>
 * The ride's feel: it gathers speed and settles onto its pad ({@code LiftShaft.ease}), the view widens a little with
 * the speed ({@link LiftLook#fov}, the vanilla field-of-view hook, so it eases in and out as sprinting does), a soft
 * rush of air sounds as it sets off and a chime as it arrives (for the rider alone, from the client tick); the beam,
 * its hoops and its motes are {@link LiftRender}'s.
 */
@SideOnly(Side.CLIENT)
public final class LiftClient {

    /** How long a request waits for its answer before the player may ask again. */
    private static final int WAIT = 20;

    private final LiftInput input = new LiftInput();
    private boolean rodeWas;
    private long waitUntil = Long.MIN_VALUE;
    private static boolean soundFailed;

    private LiftClient() {}

    /** Called once by {@link CampusClient#register()}. */
    public static void register() {
        LiftClient h = new LiftClient();
        FMLCommonHandler.instance()
            .bus()
            .register(h);
        MinecraftForge.EVENT_BUS.register(h);
        Lift.setClientHook(h::received);
    }

    private void received(NBTTagCompound t) {
        waitUntil = Long.MIN_VALUE;
        Lift.receive(t, Minecraft.getMinecraft().thePlayer);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (p == null || e.player != p || p.worldObj == null || !p.worldObj.isRemote) return;
        // the keys' live state: the movement input still holds last tick's until the player's living update
        boolean keys = mc.currentScreen == null && mc.gameSettings != null;
        boolean jump = keys && mc.gameSettings.keyBindJump.getIsKeyPressed();
        boolean sneak = keys && mc.gameSettings.keyBindSneak.getIsKeyPressed();
        boolean riding = Lift.clientTick(p);
        long now = p.worldObj.getTotalWorldTime();
        if (riding != rodeWas) sound(p, riding);
        rodeWas = riding;
        boolean free = !riding && !Lift.clientRiding() && (waitUntil == Long.MIN_VALUE || now >= waitUntil);
        int[] ask = input.tick(now, jump, sneak, padUnder(p), p.posX, p.boundingBox.minY, p.posZ, free);
        if (ask != null) {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("X", ask[0]);
            t.setInteger("Y", ask[1]);
            t.setInteger("Z", ask[2]);
            t.setInteger("D", ask[3]);
            NexusNet.ask(NexusNet.LIFT, t);
            waitUntil = now + WAIT;
        }
    }

    /**
     * A soft rush of air as a ride sets off, a chime as it ends; heard by the rider alone and quietly. A sound that
     * cannot be played is given up for the session.
     */
    private static void sound(EntityPlayer p, boolean start) {
        if (soundFailed) return;
        try {
            if (start) p.worldObj.playSound(p.posX, p.posY, p.posZ, "mob.endermen.portal", 0.22f, 1.7f, false);
            else p.worldObj.playSound(p.posX, p.posY, p.posZ, "random.orb", 0.18f, 1.5f, false);
        } catch (RuntimeException ex) {
            soundFailed = true;
            FluxEcho.LOG.warn("The light lift's sounds failed and are off until the game restarts", ex);
        }
    }

    /** The view widens a little with the ride's speed (the game eases the field of view towards this each tick). */
    @SubscribeEvent
    public void onFov(FOVUpdateEvent e) {
        if (e.entity == null || e.entity != Minecraft.getMinecraft().thePlayer || !Lift.clientRiding()) return;
        e.newfov *= (float) LiftLook.fov(Lift.clientSpeed());
    }

    /** The pad the player stands on (on the ground): {x, y, z}, or null. */
    private static int[] padUnder(EntityPlayer p) {
        if (!p.onGround) return null;
        int x = MathHelper.floor_double(p.posX), z = MathHelper.floor_double(p.posZ);
        int y = MathHelper.floor_double(p.boundingBox.minY - 0.01);
        return Lift.pad(p.worldObj, x, y, z) ? new int[] { x, y, z } : null;
    }

    @SubscribeEvent
    public void onUnload(WorldEvent.Unload e) {
        if (e.world != null && e.world.isRemote) {
            Lift.clearClient();
            waitUntil = Long.MIN_VALUE;
            rodeWas = false;
            input.reset();
        }
    }
}
