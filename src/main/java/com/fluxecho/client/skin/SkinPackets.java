package com.fluxecho.client.skin;

import net.minecraft.nbt.NBTTagCompound;

import com.fluxecho.FluxEcho;
import com.fluxecho.nexus.NexusNet;

/**
 * Where the client takes the campuses' box table ({@code SkinTable}, style FINAL 3.9.3) that the server sends on the
 * nexus channel: the whole table of the campuses within 512 blocks ({@link NexusNet#SKIN_TABLE}) and one changed entry
 * ({@link NexusNet#SKIN_DELTA}). The two kinds are routed here from 0.17.0 on (the version's interface commit), so the
 * box table's own work never has to touch the channel; until that work fills this in, a packet is only noted in the
 * debug log.
 */
public final class SkinPackets {

    private SkinPackets() {}

    /** Called on the client thread, whether a screen listens or not. */
    public static void receive(int kind, NBTTagCompound data) {
        FluxEcho.LOG.debug(
            "Box table packet {} ({} keys) arrived before the box table is drawn; ignored",
            kind == NexusNet.SKIN_TABLE ? "SKIN_TABLE" : "SKIN_DELTA",
            data == null ? 0
                : data.func_150296_c()
                    .size());
    }
}
