package com.fluxecho.codex;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import com.fluxecho.FluxEcho;
import com.fluxecho.client.HoloStore;
import com.fluxecho.core.Owners;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
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
 * FluxEcho's channel. It sends each player their team's ledger: when they log in, and at the end of a tick in which
 * the team's ledger changed. In 1.7.10 handlers run on the Netty thread, so the client queues the copy for its main
 * thread. It also carries what machine holograms show ({@link #holo}) to the players near them.
 */
public final class EchoNet {

    private static SimpleNetworkWrapper channel;
    private static final Set<UUID> CHANGED = new HashSet<>();
    private static final Queue<NBTTagCompound> INBOX = new ConcurrentLinkedQueue<>();

    private EchoNet() {}

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(FluxEcho.MODID);
        channel.registerMessage(Handler.class, MsgLedger.class, 0, Side.CLIENT);
        channel.registerMessage(HoloHandler.class, MsgHolo.class, 1, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(new Events());
    }

    /** The team's ledger changed; its online players get it at the end of the tick. */
    public static void ledgerChanged(UUID team) {
        if (team != null) synchronized (CHANGED) {
            CHANGED.add(team);
        }
    }

    static void send(EntityPlayerMP p) {
        if (channel == null) return;
        UUID team = Owners.team(p.getUniqueID());
        channel.sendTo(
            new MsgLedger(
                EchoLedger.get()
                    .snapshot(team)),
            p);
    }

    /** Kinds of hologram. */
    public static final byte HOLO_MANA = 1;

    /** Tells the players within {@code range} (plus a margin) of the machine what its hologram shows. */
    public static void holo(IGregTechTileEntity b, byte kind, NBTTagCompound data, int range) {
        if (channel == null || b == null || b.getWorld() == null) return;
        int dim = b.getWorld().provider.dimensionId;
        channel.sendToAllAround(
            new MsgHolo(kind, dim, b.getXCoord(), b.getYCoord(), b.getZCoord(), data),
            new NetworkRegistry.TargetPoint(
                dim,
                b.getXCoord() + 0.5,
                b.getYCoord() + 0.5,
                b.getZCoord() + 0.5,
                range + 8));
    }

    public static final class Events {

        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.player instanceof EntityPlayerMP p) send(p);
        }

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Set<UUID> teams;
            synchronized (CHANGED) {
                if (CHANGED.isEmpty()) return;
                teams = new HashSet<>(CHANGED);
                CHANGED.clear();
            }
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null || server.getConfigurationManager() == null) return;
            for (Object o : server.getConfigurationManager().playerEntityList) {
                if (o instanceof EntityPlayerMP p && teams.contains(Owners.team(p.getUniqueID()))) send(p);
            }
        }

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.START) return;
            NBTTagCompound t;
            while ((t = INBOX.poll()) != null) ClientLedger.set(t);
        }
    }

    public static final class MsgLedger implements IMessage {

        NBTTagCompound data;

        public MsgLedger() {}

        MsgLedger(NBTTagCompound data) {
            this.data = data;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            data = ByteBufUtils.readTag(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeTag(buf, data);
        }
    }

    public static final class Handler implements IMessageHandler<MsgLedger, IMessage> {

        @Override
        public IMessage onMessage(MsgLedger msg, MessageContext ctx) {
            if (msg.data != null) INBOX.add(msg.data);
            return null;
        }
    }

    public static final class MsgHolo implements IMessage {

        byte kind;
        int dim, x, y, z;
        NBTTagCompound data;

        public MsgHolo() {}

        MsgHolo(byte kind, int dim, int x, int y, int z, NBTTagCompound data) {
            this.kind = kind;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            kind = buf.readByte();
            dim = buf.readInt();
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
            data = ByteBufUtils.readTag(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(kind);
            buf.writeInt(dim);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            ByteBufUtils.writeTag(buf, data);
        }
    }

    public static final class HoloHandler implements IMessageHandler<MsgHolo, IMessage> {

        @Override
        public IMessage onMessage(MsgHolo msg, MessageContext ctx) {
            if (msg.data != null) HoloStore.receive(msg.kind, msg.dim, msg.x, msg.y, msg.z, msg.data);
            return null;
        }
    }
}
