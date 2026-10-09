package com.fluxecho.gate;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;

import com.fluxecho.logic.GateGeometry;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * The light gates' channel, server to client only: what lies behind a gate ({@link View}, then its sections one by
 * one), when that is no longer needed ({@link Drop}), and that the player is about to be taken through
 * ({@link Transit}).
 * Handlers run on the network thread and only queue the message; the client works through {@link #INBOX} on its own
 * thread, so this class never touches client code.
 */
public final class GateNet {

    public static final Queue<IMessage> INBOX = new ConcurrentLinkedQueue<>();

    private static SimpleNetworkWrapper channel;

    private GateNet() {}

    static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("fluxecho_gate");
        channel.registerMessage(OnView.class, View.class, 0, Side.CLIENT);
        channel.registerMessage(OnSection.class, Section.class, 1, Side.CLIENT);
        channel.registerMessage(OnDrop.class, Drop.class, 2, Side.CLIENT);
        channel.registerMessage(OnTransit.class, Transit.class, 3, Side.CLIENT);
    }

    static void send(EntityPlayerMP p, IMessage m) {
        if (channel != null) channel.sendTo(m, p);
    }

    public static class Inbox<M extends IMessage> implements IMessageHandler<M, IMessage> {

        @Override
        public IMessage onMessage(M msg, MessageContext ctx) {
            INBOX.add(msg);
            return null;
        }
    }

    public static final class OnView extends Inbox<View> {
    }

    public static final class OnSection extends Inbox<Section> {
    }

    public static final class OnDrop extends Inbox<Drop> {
    }

    public static final class OnTransit extends Inbox<Transit> {
    }

    /** Behind the gate at {@code (dim, x, y, z)} lies the space in front of the source gate, in {@code box}. */
    public static final class View implements IMessage {

        public int dim, x, y, z, facing;
        public int sourceDim, sx, sy, sz, sourceFacing;
        public int minX, minY, minZ, maxX, maxY, maxZ;

        public View() {}

        View(GateRegistry.Entry viewer, GateRegistry.Entry source, GateGeometry.Box box) {
            dim = viewer.dim;
            x = viewer.x;
            y = viewer.y;
            z = viewer.z;
            facing = viewer.facing;
            sourceDim = source.dim;
            sx = source.x;
            sy = source.y;
            sz = source.z;
            sourceFacing = source.facing;
            minX = box.minX;
            minY = box.minY;
            minZ = box.minZ;
            maxX = box.maxX;
            maxY = box.maxY;
            maxZ = box.maxZ;
        }

        public GateGeometry.Box box() {
            return new GateGeometry.Box(minX, minY, minZ, maxX, maxY, maxZ);
        }

        @Override
        public void toBytes(ByteBuf b) {
            for (int v : new int[] { dim, x, y, z, facing, sourceDim, sx, sy, sz, sourceFacing, minX, minY, minZ, maxX,
                maxY, maxZ }) b.writeInt(v);
        }

        @Override
        public void fromBytes(ByteBuf b) {
            dim = b.readInt();
            x = b.readInt();
            y = b.readInt();
            z = b.readInt();
            facing = b.readInt();
            sourceDim = b.readInt();
            sx = b.readInt();
            sy = b.readInt();
            sz = b.readInt();
            sourceFacing = b.readInt();
            minX = b.readInt();
            minY = b.readInt();
            minZ = b.readInt();
            maxX = b.readInt();
            maxY = b.readInt();
            maxZ = b.readInt();
        }
    }

    /** One section ({@link com.fluxecho.logic.MirrorSection}, encoded) of what the gate at x, y, z shows. */
    public static final class Section implements IMessage {

        public int dim, x, y, z;
        public long key;
        public byte[] data;

        public Section() {}

        Section(GateRegistry.Entry viewer, long key, byte[] data) {
            dim = viewer.dim;
            x = viewer.x;
            y = viewer.y;
            z = viewer.z;
            this.key = key;
            this.data = data;
        }

        @Override
        public void toBytes(ByteBuf b) {
            b.writeInt(dim);
            b.writeInt(x);
            b.writeInt(y);
            b.writeInt(z);
            b.writeLong(key);
            b.writeInt(data.length);
            b.writeBytes(data);
        }

        @Override
        public void fromBytes(ByteBuf b) {
            dim = b.readInt();
            x = b.readInt();
            y = b.readInt();
            z = b.readInt();
            key = b.readLong();
            int n = b.readInt();
            if (n < 0 || n > b.readableBytes()) throw new IllegalArgumentException("bad gate section length " + n);
            data = new byte[n];
            b.readBytes(data);
        }
    }

    /** The player moved away from the gate: forget what it showed. */
    public static final class Drop implements IMessage {

        public int dim, x, y, z;

        public Drop() {}

        Drop(GateRegistry.Entry viewer) {
            dim = viewer.dim;
            x = viewer.x;
            y = viewer.y;
            z = viewer.z;
        }

        @Override
        public void toBytes(ByteBuf b) {
            b.writeInt(dim);
            b.writeInt(x);
            b.writeInt(y);
            b.writeInt(z);
        }

        @Override
        public void fromBytes(ByteBuf b) {
            dim = b.readInt();
            x = b.readInt();
            y = b.readInt();
            z = b.readInt();
        }
    }

    /** The player is being taken through the gate at x, y, z into {@code targetDim}. */
    public static final class Transit implements IMessage {

        public int dim, x, y, z, targetDim;

        public Transit() {}

        Transit(GateRegistry.Entry gate, int targetDim) {
            dim = gate.dim;
            x = gate.x;
            y = gate.y;
            z = gate.z;
            this.targetDim = targetDim;
        }

        @Override
        public void toBytes(ByteBuf b) {
            b.writeInt(dim);
            b.writeInt(x);
            b.writeInt(y);
            b.writeInt(z);
            b.writeInt(targetDim);
        }

        @Override
        public void fromBytes(ByteBuf b) {
            dim = b.readInt();
            x = b.readInt();
            y = b.readInt();
            z = b.readInt();
            targetDim = b.readInt();
        }
    }
}
