package com.fluxecho.campus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingFallEvent;

import com.fluxecho.FluxEcho;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.logic.LiftInput;
import com.fluxecho.logic.LiftShaft;
import com.fluxecho.logic.Parts;
import com.fluxecho.nexus.NexusNet;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 光梯 The light lift in the world (0.12.0, CITY.md §2.5; the rule is {@link LiftShaft}): the rides of the players on
 * both sides, and the collision hook the frame and the fittings ask ({@link #passes}).
 * <p>
 * A ride is the server's: the client of a player standing on a pad who presses jump (up) or sneak (down) asks for one
 * ({@link NexusNet#LIFT}: {@code X Y Z} the pad, {@code D} the direction); the server plans it from the blocks of the
 * pad's column, starts it, and answers ({@link NexusNet#LIFT_RIDE}: {@code D} the direction, 0 for none or for a ride
 * that ended; {@code X Z} the column; {@code From To Peak} the feet heights; {@code Bl} whether it bumps the ceiling
 * and sinks back). The client moves its player only once the answer came, so from the first move on both sides skip
 * the same collision boxes: the pads and our frame and fitting blocks in the column over the ride's span, while the
 * ride lasts, and our frame and fittings (not the pads) for {@link LiftShaft#GRACE} ticks after it. The server's check
 * of the client's moves (its own moves with the same boxes) then agrees with them and nothing rubber-bands. Any
 * player also passes our blocks in the two cells over a pad ({@link #boardable}), so a pad they fill can be boarded.
 * <p>
 * The server ends a ride when the feet reached its end, when the player left the column or the world, or when it lasted
 * {@link LiftShaft#GRACE} ticks longer than it should; it tells the client then. A rider takes no fall damage during
 * the ride and its grace, and is not taken for a flying cheat while riding.
 */
public final class Lift {

    /** One rider's ride, on one side. */
    static final class State {

        final UUID player;
        final int dim, x, z, lo, hi;
        final LiftShaft.Ride ride;
        final long start;
        boolean riding = true, turned;
        long graceUntil;
        /**
         * The client's feel (0.12.0): when the current leg began (the start, or where a blocked ride turned) and the
         * speed it carried into the leg, for {@link LiftShaft#ease}.
         */
        long legStart;
        double carried = Double.NaN;
        /** The vertical motion the client gave the rider on its last tick (for the view's widening). */
        double motion;

        State(UUID player, int dim, int x, int z, LiftShaft.Ride ride, long start) {
            this.player = player;
            this.dim = dim;
            this.x = x;
            this.z = z;
            int[] span = ride.span();
            lo = span[0];
            hi = span[1];
            this.ride = ride;
            this.start = start;
            this.legStart = start;
        }

        /** Where the feet go now. */
        double goal() {
            return ride.goal(turned);
        }

        /** Whether the ride (or its grace) still lasts at {@code now}. */
        boolean active(long now) {
            return riding || now <= graceUntil;
        }

        void end(long now) {
            riding = false;
            graceUntil = now + LiftShaft.GRACE;
        }
    }

    /** The server's rides (server thread only). */
    private static final Map<UUID, State> SERVER = new HashMap<>();
    /** The client's own ride (client thread only): the local player's. */
    private static volatile State client;

    private Lift() {}

    /** The server's handlers: the riders' ticks, their falls, leaving. */
    public static void register() {
        Handler h = new Handler();
        MinecraftForge.EVENT_BUS.register(h);
        FMLCommonHandler.instance()
            .bus()
            .register(h);
    }

    // ---- the collision hook (both sides)

    /**
     * Whether an entity passes through the block of ours at {@code (x, y, z)} ({@code pad}: a light-lift pad, else a
     * frame or fitting block) because it rides the lift there: only a riding player (or one in its grace), only in the
     * ride's column and span ({@link LiftShaft#passes}).
     */
    public static boolean passes(Entity e, int x, int y, int z, boolean pad) {
        if (!(e instanceof EntityPlayer) || e.worldObj == null) return false;
        State s = e.worldObj.isRemote ? client : SERVER.get(e.getUniqueID());
        if (s == null || s.x != x || s.z != z || y < s.lo || y > s.hi) return false;
        if (!e.getUniqueID()
            .equals(s.player) || e.dimension != s.dim) return false;
        long now = e.worldObj.getTotalWorldTime();
        return LiftShaft
            .passes(pad ? LiftShaft.Cell.PAD : LiftShaft.Cell.OURS, s.riding, !s.riding && now <= s.graceUntil);
    }

    /**
     * Whether a player passes through the block of ours at {@code (x, y, z)} to board the pad under it, ridden or not
     * ({@link LiftShaft#boardable}: our blocks in the two cells over a pad): a grown nexus's dais pad can be stood on
     * while the nexus is unformed and the conduits of its middle column fill the pad's standing room, the lower one
     * there or not. Read from the world's blocks alone, so the server and the client agree.
     */
    public static boolean boardable(Entity e, int x, int y, int z) {
        if (!(e instanceof EntityPlayer) || e.worldObj == null) return false;
        World w = e.worldObj;
        LiftShaft.Cell below = cell(w, x, y - 1, z);
        if (below != LiftShaft.Cell.PAD && below != LiftShaft.Cell.OURS && below != LiftShaft.Cell.OPEN) return false;
        return LiftShaft.boardable(cell(w, x, y, z), below, cell(w, x, y - 2, z));
    }

    /** Whether the player rides the lift (or is in the grace after a ride) on its side: no fall damage. */
    public static boolean riding(EntityPlayer p) {
        if (p == null || p.worldObj == null) return false;
        State s = p.worldObj.isRemote ? client : SERVER.get(p.getUniqueID());
        return s != null && s.player.equals(p.getUniqueID()) && s.active(p.worldObj.getTotalWorldTime());
    }

    /** What a block of the world is to a rider. */
    public static LiftShaft.Cell cell(World w, int x, int y, int z) {
        if (y < 0) return LiftShaft.Cell.SOLID;
        if (y > 255) return LiftShaft.Cell.OPEN;
        if (!w.blockExists(x, y, z)) return LiftShaft.Cell.SOLID;
        Block b = w.getBlock(x, y, z);
        if (b instanceof BlockFitting) {
            return BlockFitting.type(w.getBlockMetadata(x, y, z)) == Parts.F_LIFT ? LiftShaft.Cell.PAD
                : LiftShaft.Cell.OURS;
        }
        if (b instanceof BlockFrame) return LiftShaft.Cell.OURS;
        if (b.isAir(w, x, y, z)) return LiftShaft.Cell.OPEN;
        return b.getCollisionBoundingBoxFromPool(w, x, y, z) == null ? LiftShaft.Cell.OPEN : LiftShaft.Cell.SOLID;
    }

    /** Whether the block is a light-lift pad. */
    public static boolean pad(World w, int x, int y, int z) {
        return w.getBlock(x, y, z) instanceof BlockFitting
            && BlockFitting.type(w.getBlockMetadata(x, y, z)) == Parts.F_LIFT;
    }

    // ---- the server

    /** A client asks for a ride ({@link NexusNet#LIFT}): planned, started and answered, or refused. */
    public static void request(EntityPlayerMP p, NBTTagCompound t) {
        World w = p.worldObj;
        int x = t.getInteger("X"), y = t.getInteger("Y"), z = t.getInteger("Z"), dir = t.getInteger("D");
        long now = w.getTotalWorldTime();
        State was = SERVER.get(p.getUniqueID());
        LiftShaft.Ride ride = null;
        // the player must stand on (or have just jumped off) the pad it names, and not ride already: the window the
        // client asks in (LiftInput)
        boolean ok = (dir == 1 || dir == -1) && (was == null || !was.riding)
            && w.blockExists(x, y, z)
            && pad(w, x, y, z)
            && LiftInput.boards(p.posX, p.boundingBox.minY, p.posZ, x, y, z);
        if (ok) ride = LiftShaft.plan(yy -> cell(w, x, yy, z), y, dir);
        NBTTagCompound out = new NBTTagCompound();
        if (ride == null) {
            out.setInteger("D", 0);
            NexusNet.tell(p, NexusNet.LIFT_RIDE, out);
            return;
        }
        SERVER.put(p.getUniqueID(), new State(p.getUniqueID(), p.dimension, x, z, ride, now));
        p.fallDistance = 0;
        NexusNet.tell(p, NexusNet.LIFT_RIDE, write(ride, x, z));
    }

    /** A ride as the client hears of it. */
    static NBTTagCompound write(LiftShaft.Ride ride, int x, int z) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("D", ride.dir);
        t.setInteger("X", x);
        t.setInteger("Z", z);
        t.setDouble("From", ride.from);
        t.setDouble("To", ride.to);
        t.setDouble("Peak", ride.peak);
        t.setBoolean("Bl", ride.blocked);
        return t;
    }

    /** One server tick of a rider (after its moves were taken). */
    static void serverTick(EntityPlayerMP p) {
        State s = SERVER.get(p.getUniqueID());
        if (s == null) return;
        long now = p.worldObj.getTotalWorldTime();
        if (!s.riding) {
            if (now > s.graceUntil || p.dimension != s.dim) SERVER.remove(p.getUniqueID());
            else p.fallDistance = 0;
            return;
        }
        p.fallDistance = 0;
        double feet = p.boundingBox.minY;
        if (s.ride.blocked && !s.turned && LiftShaft.arrived(feet, s.ride.peak)) s.turned = true;
        boolean done = (!s.ride.blocked || s.turned) && LiftShaft.arrived(feet, s.ride.to);
        boolean lost = p.isDead || p.dimension != s.dim
            || !LiftShaft.onColumn(p.posX, p.posZ, s.x, s.z)
            || now - s.start > s.ride.ticks() + LiftShaft.GRACE;
        if (done || lost) {
            s.end(now);
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("D", 0);
            NexusNet.tell(p, NexusNet.LIFT_RIDE, t);
            return;
        }
        // rising through the open air is no flying cheat: the server's kick counts floating ticks
        resetFloating(p);
    }

    private static boolean floatingFailed;

    private static void resetFloating(EntityPlayerMP p) {
        if (floatingFailed || p.playerNetServerHandler == null) return;
        try {
            ObfuscationReflectionHelper.setPrivateValue(
                NetHandlerPlayServer.class,
                p.playerNetServerHandler,
                0,
                "floatingTickCount",
                "field_147365_f");
        } catch (RuntimeException ex) {
            floatingFailed = true;
            FluxEcho.LOG.warn("The light lift cannot reset the floating ticks; long rides may be taken for flying", ex);
        }
    }

    // ---- the client's ride (filled by the client handler)

    /**
     * The server's answer arrived ({@link NexusNet#LIFT_RIDE}, client thread): handed to the client handler, which
     * knows the local player.
     */
    public static void receiveClient(NBTTagCompound t) {
        ClientHook h = clientHook;
        if (h != null && t != null) h.received(t);
    }

    /** The ride the server started (or ended) for the local player (client thread). */
    public static void receive(NBTTagCompound t, EntityPlayer self) {
        if (self == null || self.worldObj == null) return;
        long now = self.worldObj.getTotalWorldTime();
        int dir = t.getInteger("D");
        State c = client;
        if (dir == 0) {
            if (c != null && c.riding) c.end(now);
            return;
        }
        LiftShaft.Ride ride = new LiftShaft.Ride(
            dir,
            t.getDouble("From"),
            t.getDouble("To"),
            t.getDouble("Peak"),
            t.getBoolean("Bl"));
        client = new State(self.getUniqueID(), self.dimension, t.getInteger("X"), t.getInteger("Z"), ride, now);
    }

    /** The client's handler of the answers (set by the client proxy). */
    public interface ClientHook {

        void received(NBTTagCompound t);
    }

    private static volatile ClientHook clientHook;

    public static void setClientHook(ClientHook h) {
        clientHook = h;
    }

    /**
     * One client tick of the local player's ride (before it moves): its motion towards the goal, gathering speed and
     * settling onto the pad ({@link LiftShaft#ease}; the server does not move the player, so the feel is the client's
     * alone), and back to the column's middle; the ride ends when the feet reached its end, when the player left the
     * column, or when it lasted {@link LiftShaft#GRACE} ticks longer than it should. Returns whether it rides now.
     */
    public static boolean clientTick(EntityPlayer self) {
        State c = client;
        if (c == null || self == null || self.worldObj == null) return false;
        long now = self.worldObj.getTotalWorldTime();
        if (!c.player.equals(self.getUniqueID()) || self.dimension != c.dim) {
            client = null;
            return false;
        }
        if (!c.riding) {
            if (now > c.graceUntil) client = null;
            else self.fallDistance = 0;
            return false;
        }
        self.fallDistance = 0;
        double feet = self.boundingBox.minY;
        if (c.ride.blocked && !c.turned && LiftShaft.arrived(feet, c.ride.peak)) {
            // the head bumped the ceiling: the way back down gathers speed afresh
            c.turned = true;
            c.legStart = now;
            c.carried = 0;
        }
        if ((!c.ride.blocked || c.turned) && LiftShaft.arrived(feet, c.ride.to)
            || !LiftShaft.onColumn(self.posX, self.posZ, c.x, c.z)
            || now - c.start > c.ride.ticks() + LiftShaft.GRACE) {
            c.end(now);
            self.motionY = 0;
            return false;
        }
        // the speed the player had towards the goal when the ride began (a jump's) carries on into it
        if (Double.isNaN(c.carried)) c.carried = Math.max(0, Math.signum(c.goal() - feet) * self.motionY);
        self.motionY = LiftShaft.ease(feet, c.goal(), (int) Math.min(Integer.MAX_VALUE, now - c.legStart), c.carried);
        c.motion = self.motionY;
        self.motionX = LiftShaft.steer(self.posX, c.x + 0.5);
        self.motionZ = LiftShaft.steer(self.posZ, c.z + 0.5);
        return true;
    }

    /** The vertical speed the local player's ride gave it on its last tick, blocks a tick; 0 when it rides none. */
    public static double clientSpeed() {
        State c = client;
        return c != null && c.riding ? c.motion : 0;
    }

    /** Whether the local player rides now (client thread). */
    public static boolean clientRiding() {
        State c = client;
        return c != null && c.riding;
    }

    /** The local player's ride, for drawing: {x, z, from, to, peak} and its direction; null when it rides none. */
    public static double[] clientRide() {
        State c = client;
        if (c == null || !c.riding) return null;
        return new double[] { c.x, c.z, c.ride.from, c.ride.to, c.ride.peak, c.ride.dir };
    }

    /** Leaving a world (client): no ride goes on into the next. */
    public static void clearClient() {
        client = null;
    }

    /** The server's handlers. */
    public static final class Handler {

        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof EntityPlayerMP p)) return;
            if (p.worldObj == null || p.worldObj.isRemote) return;
            serverTick(p);
        }

        /** No fall damage on a ride and for its grace. */
        @SubscribeEvent
        public void onFall(LivingFallEvent e) {
            if (!(e.entityLiving instanceof EntityPlayer p) || !riding(p)) return;
            e.distance = 0;
            e.setCanceled(true);
        }

        @SubscribeEvent
        public void onLeave(PlayerEvent.PlayerLoggedOutEvent e) {
            if (e.player != null) SERVER.remove(e.player.getUniqueID());
        }

        @SubscribeEvent
        public void onTravel(PlayerEvent.PlayerChangedDimensionEvent e) {
            if (e.player != null) SERVER.remove(e.player.getUniqueID());
        }
    }
}
