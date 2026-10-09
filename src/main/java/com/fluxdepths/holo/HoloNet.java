package com.fluxdepths.holo;

import net.minecraft.nbt.NBTTagCompound;

import com.fluxdepths.Config;
import com.fluxdepths.client.HoloClient;
import com.fluxdepths.shard.MTEFluxCollector;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.ByteBuf;

/**
 * The collectors' holograms: twice a second a collector with its hologram on tells the players around it what to show
 * ({@link MTEFluxCollector#holoData}). Nothing is sent while it is off, or when the hologram range is 0.
 */
public final class HoloNet {

    private static SimpleNetworkWrapper net;

    private HoloNet() {}

    public static void init() {
        net = NetworkRegistry.INSTANCE.newSimpleChannel("fluxdepths");
        net.registerMessage(Handler.class, Message.class, 0, Side.CLIENT);
    }

    public static void send(MTEFluxCollector m) {
        IGregTechTileEntity b = m.getBaseMetaTileEntity();
        if (net == null || b == null || b.getWorld() == null) return;
        int dim = b.getWorld().provider.dimensionId;
        net.sendToAllAround(
            new Message(dim, b.getXCoord(), b.getYCoord(), b.getZCoord(), m.holoData()),
            new NetworkRegistry.TargetPoint(
                dim,
                b.getXCoord() + 0.5,
                b.getYCoord() + 0.5,
                b.getZCoord() + 0.5,
                Config.hologramRange + 8));
    }

    public static final class Message implements IMessage {

        public int dim, x, y, z;
        public NBTTagCompound data;

        public Message() {}

        Message(int dim, int x, int y, int z, NBTTagCompound data) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            dim = buf.readInt();
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
            data = ByteBufUtils.readTag(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(dim);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            ByteBufUtils.writeTag(buf, data);
        }
    }

    public static final class Handler implements IMessageHandler<Message, IMessage> {

        @Override
        public IMessage onMessage(Message m, MessageContext ctx) {
            HoloClient.receive(m.dim, m.x, m.y, m.z, m.data);
            return null;
        }
    }
}
