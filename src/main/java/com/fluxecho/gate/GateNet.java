package com.fluxecho.gate;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * The light gates' channel, server to client only: the player is being taken through a gate ({@link Transit}), so
 * the client can hold the view through it over the step. Everything else a client needs it has in its own world:
 * both sides of a gate are loaded for it. Handlers run on the network thread and only queue the message; the client
 * works through {@link #INBOX} on its own thread, so this class never touches client code.
 */
public final class GateNet {

    public static final Queue<IMessage> INBOX = new ConcurrentLinkedQueue<>();

    private static SimpleNetworkWrapper channel;

    private GateNet() {}

    static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("fluxecho_gate");
        channel.registerMessage(OnTransit.class, Transit.class, 0, Side.CLIENT);
    }

    static void send(EntityPlayerMP p, IMessage m) {
        if (channel != null) channel.sendTo(m, p);
    }

    public static final class OnTransit implements IMessageHandler<Transit, IMessage> {

        @Override
        public IMessage onMessage(Transit msg, MessageContext ctx) {
            INBOX.add(msg);
            return null;
        }
    }

    /** The player walked into the gate at {@code (x, y, z)} of dimension {@code dim}. */
    public static final class Transit implements IMessage {

        public int dim, x, y, z;

        public Transit() {}

        Transit(GateRegistry.Entry gate) {
            dim = gate.dim;
            x = gate.x;
            y = gate.y;
            z = gate.z;
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
}
