package com.fluxecho.nexus;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.fluxecho.Config;
import com.fluxecho.core.Owners;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.research.Research;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
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
 * The nexus's own channel: the research star map asks the server about a nexus, starts and drops research there, and
 * gets the answers. Both ends queue what arrives and handle it on their main thread.
 */
public final class NexusNet {

    /** Client to server. */
    public static final int ASK_MAP = 0, START = 1, CANCEL = 2;
    /** Server to client. */
    public static final int MAP = 0, RESULT = 1;

    private static SimpleNetworkWrapper channel;
    private static final Queue<Object[]> SERVER_INBOX = new ConcurrentLinkedQueue<>();
    private static final Queue<Object[]> CLIENT_INBOX = new ConcurrentLinkedQueue<>();

    /** Set by the client: what to do with answers. */
    public interface ClientSink {

        void receive(int kind, NBTTagCompound data);
    }

    private static volatile ClientSink sink;

    private NexusNet() {}

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("fluxecho_nx");
        channel.registerMessage(ToServer.class, Msg.class, 0, Side.SERVER);
        channel.registerMessage(ToClient.class, Msg.class, 1, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(new Pump());
    }

    public static void setSink(ClientSink s) {
        sink = s;
    }

    public static void ask(int kind, NBTTagCompound data) {
        if (channel != null) channel.sendToServer(new Msg(kind, data));
    }

    public static void tell(EntityPlayerMP p, int kind, NBTTagCompound data) {
        if (channel != null) channel.sendTo(new Msg(kind, data), p);
    }

    /** Where a request points: dim, x, y, z. */
    public static NBTTagCompound at(int dim, int x, int y, int z) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("Dim", dim);
        t.setInteger("X", x);
        t.setInteger("Y", y);
        t.setInteger("Z", z);
        return t;
    }

    private static TileNexus nexus(NBTTagCompound t) {
        return NexusRegistry.at(t.getInteger("Dim"), t.getInteger("X"), t.getInteger("Y"), t.getInteger("Z"));
    }

    static void handle(EntityPlayerMP p, int kind, NBTTagCompound data) {
        TileNexus n = nexus(data);
        if (kind == ASK_MAP) {
            tell(p, MAP, map(n, p, data));
            return;
        }
        if (n == null) {
            result(p, "fail.not_loaded");
            return;
        }
        String r = kind == START ? n.startResearch(p, data.getString("Id")) : kind == CANCEL ? n.cancelResearch(p) : "";
        if (!r.isEmpty()) result(p, r);
        tell(p, MAP, map(n, p, data));
    }

    private static void result(EntityPlayerMP p, String key) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("Key", key);
        tell(p, RESULT, t);
    }

    /** What the star map shows about the nexus, for the player. */
    static NBTTagCompound map(TileNexus n, EntityPlayerMP p, NBTTagCompound at) {
        NBTTagCompound t = (NBTTagCompound) at.copy();
        t.setBoolean("Found", n != null);
        UUID team = Owners.team(p.getUniqueID());
        t.setInteger("Records", Records.count(team));
        NBTTagList own = new NBTTagList();
        for (String id : Research.done(team)) own.appendTag(new NBTTagString(id));
        t.setTag("Own", own);
        if (n == null) return t;
        t.setBoolean("Member", n.member(p));
        t.setBoolean("Formed", n.formed());
        t.setBoolean("Powered", n.powered());
        t.setInteger("Phase", TileNexus.PHASE);
        t.setDouble("Compute", n.computeRate());
        t.setString("Research", n.research());
        t.setFloat("Done", n.researchFraction());
        t.setString("Owner", n.ownerName());
        t.setInteger("Bound", n.boundCount());
        t.setDouble("Scale", Config.researchScale);
        NBTTagList open = new NBTTagList();
        for (String id : n.unlocked()) open.appendTag(new NBTTagString(id));
        t.setTag("Open", open);
        // the costs as the server reads them, so a changed tree on the server shows right
        NBTTagCompound compute = new NBTTagCompound();
        for (ResearchTree.Node node : ResearchTree.all())
            compute.setLong(node.id, ResearchTree.computeFor(node, Config.researchScale));
        t.setTag("Cost", compute);
        return t;
    }

    public static final class Msg implements IMessage {

        int kind;
        NBTTagCompound data;

        public Msg() {}

        Msg(int kind, NBTTagCompound data) {
            this.kind = kind;
            this.data = data;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            kind = buf.readByte();
            data = ByteBufUtils.readTag(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(kind);
            ByteBufUtils.writeTag(buf, data == null ? new NBTTagCompound() : data);
        }
    }

    public static final class ToServer implements IMessageHandler<Msg, IMessage> {

        @Override
        public IMessage onMessage(Msg m, MessageContext ctx) {
            SERVER_INBOX.add(new Object[] { ctx.getServerHandler().playerEntity, m.kind, m.data });
            return null;
        }
    }

    public static final class ToClient implements IMessageHandler<Msg, IMessage> {

        @Override
        public IMessage onMessage(Msg m, MessageContext ctx) {
            CLIENT_INBOX.add(new Object[] { m.kind, m.data });
            return null;
        }
    }

    public static final class Pump {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.START) return;
            Object[] m;
            while ((m = SERVER_INBOX.poll()) != null) {
                try {
                    handle((EntityPlayerMP) m[0], (Integer) m[1], (NBTTagCompound) m[2]);
                } catch (RuntimeException ex) {
                    com.fluxecho.FluxEcho.LOG.warn("Nexus request failed", ex);
                }
            }
        }

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.START) return;
            Object[] m;
            while ((m = CLIENT_INBOX.poll()) != null) {
                ClientSink s = sink;
                if (s != null) s.receive((Integer) m[0], (NBTTagCompound) m[1]);
            }
        }
    }
}
