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
import io.netty.buffer.ByteBuf;

/**
 * Sends each player their team's ledger: when they log in, and at the end of a tick in which the team's ledger
 * changed. In 1.7.10 handlers run on the Netty thread, so the client queues the copy for its main thread.
 */
public final class EchoNet {

    private static SimpleNetworkWrapper channel;
    private static final Set<UUID> CHANGED = new HashSet<>();
    private static final Queue<NBTTagCompound> INBOX = new ConcurrentLinkedQueue<>();

    private EchoNet() {}

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(FluxEcho.MODID);
        channel.registerMessage(Handler.class, MsgLedger.class, 0, Side.CLIENT);
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
}
