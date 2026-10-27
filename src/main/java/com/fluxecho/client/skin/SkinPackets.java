package com.fluxecho.client.skin;

import java.io.IOException;

import net.minecraft.nbt.NBTTagCompound;

import com.fluxecho.FluxEcho;
import com.fluxecho.logic.skin.SkinCodec;
import com.fluxecho.nexus.NexusNet;

/**
 * Where the client takes the campuses' box table ({@code SkinTable}, style FINAL 3.9.3) that the server sends on the
 * nexus channel: the whole table of the campuses within 512 blocks ({@link NexusNet#SKIN_TABLE}) and one changed entry
 * ({@link NexusNet#SKIN_DELTA}). The payload is the table's bytes ({@link SkinCodec}) under {@code B}; they go to the
 * client's snapshot ({@link ClientSkin}). Called only on the client thread, from the channel's client pump.
 */
public final class SkinPackets {

    private SkinPackets() {}

    /** Called on the client thread, whether a screen listens or not. */
    public static void receive(int kind, NBTTagCompound data) {
        byte[] b = data == null ? new byte[0] : data.getByteArray("B");
        try {
            if (kind == NexusNet.SKIN_TABLE) ClientSkin.table(SkinCodec.readTable(b));
            else if (kind == NexusNet.SKIN_DELTA) ClientSkin.delta(SkinCodec.readDelta(b));
        } catch (IOException e) {
            FluxEcho.LOG.warn("A box table packet ({} bytes) could not be read; it is ignored", b.length, e);
        }
    }
}
