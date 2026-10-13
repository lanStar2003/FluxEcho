package com.fluxecho.city;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.Config;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.campus.PartBlocks;
import com.fluxecho.core.Directory;
import com.fluxecho.frame.Formed;
import com.fluxecho.gate.Sight;
import com.fluxecho.library.BookPacket;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.ShapeKit;
import com.fluxecho.logic.UnitLayout;
import com.fluxecho.nexus.ClientTiles;
import com.fluxecho.nexus.NexusNet;
import com.fluxecho.nexus.NexusRegistry;
import com.fluxecho.nexus.TileModule;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.core.ServerEvents;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.modularui.api.forge.ItemStackHandler;
import com.gtnewhorizons.modularui.api.screen.ITileWithModularUI;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;

/**
 * A module of the City of Echoes (0.11.0): a walk-in building the nexus raises on one of its sites, whose furniture is
 * made of whole units ({@link UnitLayout}) that hold samples. This is the part every city module shares, so a new
 * building is mostly data: its shape ({@link CityBuild}), the characters of its shape that dissolve once it forms, its
 * colour and its core's meta ({@link BlockModuleCore}).
 * <p>
 * The structure is the shape's blueprint with every character mapped to the block the campus builder places for it
 * ({@link ShapeKit#partOf}, {@link PartBlocks}), so a module the builder finished forms. It docks on its site like the
 * Echo Archive ({@link TileModule}); docked and paid its upkeep ({@code moduleUpkeepEuPerTick}) from its owner's
 * wireless network, it is powered.
 * <p>
 * Its units keep one item a slot ({@link #samples}); a click on any cell of a unit uses the unit ({@link UnitUse}). The
 * client keeps a copy of every sample ({@link #clientSamples}): all of them in the tile's description, the slots that
 * changed in {@link NexusNet#UNIT_DELTA} packets ({@link BookPacket} codes both, as for the Archive's books), so a
 * module's socket items can be drawn and its window shown without resending the whole building.
 * <p>
 * NBT: {@code Samples} (the handler), {@code Unit} (the unit its window shows), and the multiblock's own keys.
 * Description: {@code P} powered, {@code Dk} dock status, {@code Rv}/{@code Ep} the sample revision and the tile's
 * life, and the samples ({@code B}/{@code Bi}).
 */
public abstract class TileCityModule extends TileModule implements ITileWithModularUI {

    /** The structure definitions of every city module kind, by module key: checked, and plain for building. */
    private static final Map<String, IStructureDefinition<TileMultiblock>> DEFINITIONS = new ConcurrentHashMap<>(),
        BUILD_DEFINITIONS = new ConcurrentHashMap<>();

    /** The samples, one item a slot; nothing goes in once the tile is gone. */
    protected final ItemStackHandler samples;
    /** The unit the module window shows: set by a click on a unit and by the window (server side). */
    public int selectedUnit;
    /**
     * Set (server side, never saved) by a click on a unit just before its window opens, so the window opens on its
     * Units page; the window clears it.
     */
    boolean openOnUnit;
    private boolean powered;
    /** Slots whose sample changed since the last delta packet. */
    private final BitSet changed;
    /** Delta packets sent so far, and a number for this tile's life (both tell the client what is newer). */
    private int rev;
    private final int epoch = ThreadLocalRandom.current()
        .nextInt();

    // the client's copy
    public boolean clientPowered;
    public String clientDock = "unformed";
    /** The samples by slot, as the client last heard them. */
    public final ItemStack[] clientSamples;
    /** Bumped whenever {@link #clientSamples} changes, so drawing code can rebuild what it keeps. */
    public int clientSampleVersion;
    /** Per slot, the revision its sample came with; the tile life they belong to. */
    private final int[] clientStamp;
    private int clientEpoch;
    private boolean described;

    protected TileCityModule() {
        // a module kind's build and layout are static data, so the constructor may ask for them
        int n = layout().slots();
        samples = new ItemStackHandler(n) {

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack s) {
                return !isInvalid() && s != null && slot >= 0 && slot < getSlots();
            }

            @Override
            protected void onContentsChanged(int slot) {
                markDirty();
                changed.set(slot);
            }
        };
        changed = new BitSet(n);
        clientSamples = new ItemStack[n];
        clientStamp = new int[n];
        Arrays.fill(clientStamp, Integer.MIN_VALUE);
    }

    // ---- what a module kind gives

    /** Its shape as the campus builder builds it. */
    public abstract CityBuild build();

    /** Its units, in slot order. */
    public abstract UnitLayout layout();

    /** Its core's meta ({@link BlockModuleCore}, {@code ModuleCores}). */
    public abstract int coreMetaOf();

    /**
     * The characters of its shape that give way to its drawing once it forms (the Eco Dome's trunk and seat): their
     * blocks are not drawn ({@link Formed#HIDE}).
     */
    protected abstract String dissolves();

    /**
     * Of the characters that dissolve, those players also pass through ({@link Formed#PASS}). The others keep their
     * collision, so a dissolved cell that people stand next to or walk round (the Eco Dome's trunk, which runs through
     * its island decks) never opens a shaft to fall down. None unless a module kind says so.
     */
    protected String passes() {
        return "";
    }

    /**
     * The levels of its shape the window's plan page shows after its units' storeys, for the building alone (0.13.0,
     * {@link com.fluxecho.logic.ModuleMap#overview}): none unless a module kind gives them (the Blood Covenant Altar's
     * upper tiers).
     */
    public int[] planLevels() {
        return new int[0];
    }

    // ---- the multiblock

    @Override
    protected Blueprint blueprint() {
        return build().blueprint();
    }

    /**
     * Whether the block could belong to it (0.12.0): it lies in the box of one of its shapes round the controller
     * ({@link Blueprint#inBox}), not in the cube of {@link #reach()}. A city module's box is its real extent, and the
     * cube of a tall one reaches far past it: the zenith's 49-high box would make a cube of 50 each way round a
     * controller 42 over the forum, over every hall site, and its leftover blocks of ours there would stop being loose
     * for the campus's terrain rule. The watchers of frame changes and the use of units ({@link UnitUse}) ask it too;
     * every cell of every shape lies in its box, so neither misses one.
     */
    @Override
    public boolean covers(World w, int x, int y, int z) {
        if (w != worldObj) return false;
        ForgeDirection f = front();
        for (Shape s : shapes()) if (s.bp.inBox(x, y, z, xCoord, yCoord, zCoord, f.offsetX, f.offsetZ)) return true;
        return false;
    }

    @Override
    protected int[] centreCell() {
        CityBuild b = build();
        return b.cellOf((b.width() - 1) / 2, 0, (b.depth() - 1) / 2);
    }

    @Override
    protected IStructureDefinition<TileMultiblock> definition() {
        return DEFINITIONS.computeIfAbsent(moduleKey(), k -> define(true));
    }

    @Override
    protected IStructureDefinition<TileMultiblock> buildDefinition() {
        return BUILD_DEFINITIONS.computeIfAbsent(moduleKey(), k -> define(false));
    }

    /**
     * The structure of its shapes ({@link #MAIN} for most; the Echo Dome Generator's {@code moored} and
     * {@code floating}, 0.12.0): every character of the blueprints as the block and meta the campus builder places for
     * it. {@code -} is StructureLib's own "must be air" and the controller is skipped.
     */
    private IStructureDefinition<TileMultiblock> define(boolean record) {
        List<Shape> all = shapes();
        String[] names = new String[all.size()];
        Blueprint[] bps = new Blueprint[all.size()];
        Set<Character> seen = new LinkedHashSet<>();
        for (int i = 0; i < names.length; i++) {
            names[i] = all.get(i).name;
            bps[i] = all.get(i).bp;
            for (Blueprint.Cell c : bps[i].cells()) seen.add(c.ch);
        }
        List<Object> parts = new ArrayList<>();
        for (char ch : seen) {
            if (ch == ShapeKit.AIR || ch == ShapeKit.CONTROLLER || ch == ShapeKit.NONE) continue;
            int code = ShapeKit.partOf(ch);
            Block b = PartBlocks.block(code);
            if (b == null) throw new IllegalStateException("no block for " + moduleKey() + "'s '" + ch + "'");
            parts.add(ch);
            parts.add(b);
            parts.add(PartBlocks.meta(code));
        }
        return define(record, names, bps, parts.toArray());
    }

    @Override
    protected Block coreBlock() {
        return CityModule.core;
    }

    @Override
    protected int coreMeta() {
        return coreMetaOf();
    }

    @Override
    protected int flags(char ch) {
        if (dissolves().indexOf(ch) < 0) return 0;
        return passes().indexOf(ch) >= 0 ? Formed.HIDE | Formed.PASS : Formed.HIDE;
    }

    @Override
    protected String descriptionKey() {
        return moduleKey() + ".structure";
    }

    /**
     * Placed by hand. On the controller cell of one of its sites (of a nexus loaded nearby) the core faces the way the
     * campus builder commissions it there, towards the nexus, whichever way the player stood, as the Archive's does.
     */
    @Override
    public void place(EntityPlayer placer, int front) {
        int site = siteFront();
        super.place(placer, site >= 0 ? site : front);
    }

    /** The front its core has on the controller cell of one of its sites of a loaded nexus; -1 elsewhere. */
    private int siteFront() {
        if (worldObj == null) return -1;
        ModuleSpec spec = ModuleSpecs.get(moduleKey());
        if (spec == null || !spec.enabled()) return -1;
        for (TileMultiblock m : NexusRegistry.loaded(worldObj)) {
            if (!(m instanceof TileNexus n)) continue;
            int[] c = n.centre();
            ForgeDirection f = n.front();
            for (int site : spec.sites) {
                int[] at = spec.controller(site, c[0], c[1], c[2], f.offsetX, f.offsetZ);
                if (at != null && at.length > 3 && at[0] == xCoord && at[1] == yCoord && at[2] == zCoord) return at[3];
            }
        }
        return -1;
    }

    /**
     * The console stand under the controller opens the module's window; a console that is part of a unit (the Echo
     * Dome Generator's ward consoles, 0.12.0) is the unit's, used as a unit ({@link UnitUse}).
     */
    @Override
    public boolean consoleAt(int x, int y, int z) {
        int[] l = local(x, y, z);
        return build().cell(l[0], l[1], l[2]) == 'K' && layout().unitOfCell(l[0], l[1], l[2]) < 0;
    }

    // ---- the local frame

    /** A block as a point of the module's shape: {x, y, z} (it may lie outside the shape). */
    public int[] local(int x, int y, int z) {
        int[] c = cellAt(x, y, z);
        return build().localOf(c[0], c[1], c[2]);
    }

    /** A point in the world as a point of the shape (fractional): {x, y, z} with y counted from the foundation. */
    public double[] localPoint(double x, double y, double z) {
        ForgeDirection f = front();
        double[] p = blueprint().point(x, y, z, xCoord, yCoord, zCoord, f.offsetX, f.offsetZ);
        int[] o = build().offset();
        return new double[] { p[0] - o[0], p[1] - o[1], p[2] - o[2] };
    }

    /** The block a point of the shape is at, {x, y, z}. */
    public int[] worldOf(int lx, int ly, int lz) {
        int[] c = build().cellOf(lx, ly, lz);
        return cellPos(c[0], c[1], c[2]);
    }

    /** A horizontal direction {dx, dz} of the shape's frame as a world direction {dx, dz}. */
    public int[] worldDir(int lx, int lz) {
        ForgeDirection f = front();
        // as Blueprint.world maps across onto (fz, -fx) and the depth onto (-fx, -fz)
        return new int[] { lx * f.offsetZ - lz * f.offsetX, -lx * f.offsetX - lz * f.offsetZ };
    }

    // ---- units and samples

    /** How many units it has. */
    public int unitCount() {
        return layout().size();
    }

    /** The unit a block of the formed module belongs to, or -1. */
    public int unitAt(int x, int y, int z) {
        int[] l = local(x, y, z);
        return layout().unitOfCell(l[0], l[1], l[2]);
    }

    /** The slot a socket block shows, or -1 for any other block. */
    public int slotAt(int x, int y, int z) {
        int[] l = local(x, y, z);
        return layout().slotOfSocket(l[0], l[1], l[2]);
    }

    /**
     * The slot a sneaking empty hand takes back from at a block of a unit (0.14.0): the slot its socket shows; for a
     * unit without sockets, whose slots show over its whole field (the Dual-foil Vault's canvases and foil), its last
     * filled slot wherever it is clicked; -1 when there is none (an empty unit, a block of no socket of a unit that has
     * sockets).
     */
    public int takeSlot(int unit, int x, int y, int z) {
        int slot = slotAt(x, y, z);
        if (slot >= 0 || unit < 0 || unit >= unitCount() || !layout().unit(unit).sockets.isEmpty()) return slot;
        int[] s = unitSlots(unit);
        for (int i = s[1] - 1; i >= 0; i--) if (sample(s[0] + i) != null) return s[0] + i;
        return -1;
    }

    /** The block {x, y, z} of the socket a slot shows in, or null when the slot has none. */
    public int[] slotCell(int slot) {
        int[] c = layout().socketOf(slot);
        return c == null ? null : worldOf(c[0], c[1], c[2]);
    }

    /** The blocks {x, y, z} of a unit's cells, or an empty list outside the layout. */
    public List<int[]> unitCells(int unit) {
        List<int[]> out = new ArrayList<>();
        if (unit < 0 || unit >= unitCount()) return out;
        for (int[] c : layout().unit(unit).cells) out.add(worldOf(c[0], c[1], c[2]));
        return out;
    }

    /** The unit's first slot and its slot count: {first, count}; {0, 0} outside the layout. */
    public int[] unitSlots(int unit) {
        if (unit < 0 || unit >= unitCount()) return new int[] { 0, 0 };
        UnitLayout.Unit u = layout().unit(unit);
        return new int[] { u.firstSlot, u.slots };
    }

    /** The sample in a slot: the server's on the server, the client's copy on the client; null for none. */
    public ItemStack sample(int slot) {
        if (slot < 0 || slot >= samples.getSlots()) return null;
        return worldObj != null && worldObj.isRemote ? clientSamples[slot] : samples.getStackInSlot(slot);
    }

    /** The samples' handler (the window's slots work on it; on the client it is the window's copy). */
    public ItemStackHandler samples() {
        return samples;
    }

    /** Samples in the unit (0 to its slot count), from {@link #sample}. */
    public int unitFill(int unit) {
        int[] s = unitSlots(unit);
        int n = 0;
        for (int i = 0; i < s[1]; i++) if (sample(s[0] + i) != null) n++;
        return n;
    }

    /** The unit's first empty slot, or -1 when it is full. */
    public int firstFree(int unit) {
        int[] s = unitSlots(unit);
        for (int i = 0; i < s[1]; i++) if (sample(s[0] + i) == null) return s[0] + i;
        return -1;
    }

    /** Samples it keeps, from {@link #sample}. */
    public int sampleCount() {
        int n = 0;
        for (int i = 0; i < samples.getSlots(); i++) if (sample(i) != null) n++;
        return n;
    }

    /** The unit's label (its generator's name, such as {@code G-RB}); empty outside the layout. */
    public String unitName(int unit) {
        return unit < 0 || unit >= unitCount() ? "" : layout().unit(unit).name;
    }

    /** The unit's kind (lang {@code fluxecho.unit.<kind>}); empty outside the layout. */
    public String unitKind(int unit) {
        return unit < 0 || unit >= unitCount() ? "" : layout().unit(unit).kind;
    }

    /** Selects the unit the window shows (server side; anything outside the layout is ignored). */
    public void selectUnit(int unit) {
        if (unit < 0 || unit >= unitCount() || unit == selectedUnit) return;
        selectedUnit = unit;
        markDirty();
    }

    // ---- power

    /**
     * Whether it is docked and paid its upkeep this tick (the server's state; {@link #clientPowered} on the client).
     */
    public boolean powered() {
        return powered;
    }

    /** The EU/t it draws while docked. */
    public long upkeep() {
        return Config.moduleUpkeep;
    }

    @Override
    protected void serverTick() {
        super.serverTick();
        long t = worldObj.getTotalWorldTime();
        boolean was = powered;
        TileNexus n = nexus();
        UUID team = team();
        if (formed && n != null && Config.nexusEnabled && team != null) {
            long eu = upkeep();
            powered = eu <= 0 || GTWirelessBackend.INSTANCE.add(team, BigInteger.valueOf(-eu));
            if (powered && eu > 0) ServerEvents.addTeamTick(team, 0, eu, eu, true);
        } else powered = false;
        if (t % 100 == 0) {
            String dock = dockStatus();
            Directory.report(
                worldObj,
                xCoord,
                yCoord,
                zCoord,
                team,
                "tile.fluxecho.module_core." + moduleKey() + ".name",
                "fluxecho.dock." + (powered || !"docked".equals(dock) ? dock : "no_power"),
                !"docked".equals(dock) || !powered ? Directory.PROBLEM : Directory.IDLE);
        }
        if (was != powered) sync();
        sendSamples();
    }

    // ---- standing, breaking

    /**
     * Whether the tile still stands in its world: not removed, and still the tile at its block. Its window closes once
     * it does not, so nothing is put into a module that was broken while the window stayed open.
     */
    public boolean standing() {
        World w = worldObj;
        return !isInvalid() && w != null
            && w.blockExists(xCoord, yCoord, zCoord)
            && w.getTileEntity(xCoord, yCoord, zCoord) == this;
    }

    /** The core is being broken: every sample falls out at the core, and the handler is emptied. */
    void takeDown(World w, int x, int y, int z) {
        for (int i = 0; i < samples.getSlots(); i++) {
            ItemStack s = samples.getStackInSlot(i);
            if (s == null) continue;
            w.spawnEntityInWorld(new EntityItem(w, x + 0.5, y + 0.5, z + 0.5, s.copy()));
            samples.setStackInSlot(i, null);
        }
        changed.clear();
    }

    /** Everything its units hold. */
    public List<ItemStack> contents() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < samples.getSlots(); i++)
            if (samples.getStackInSlot(i) != null) out.add(samples.getStackInSlot(i));
        return out;
    }

    // ---- the samples on the client

    /**
     * Sends the samples that changed (at most {@link BookPacket#DELTA_SLOTS} a tick, the rest the next) to the
     * players near the module or watching its chunk.
     */
    private void sendSamples() {
        if (changed.isEmpty()) return;
        List<BookPacket.Book> out = new ArrayList<>();
        for (int i = changed.nextSetBit(0); i >= 0
            && out.size() < BookPacket.DELTA_SLOTS; i = changed.nextSetBit(i + 1)) {
            ItemStack s = samples.getStackInSlot(i);
            out.add(new BookPacket.Book(i, s == null ? null : s.writeToNBT(new NBTTagCompound())));
            changed.clear(i);
        }
        rev++;
        Set<EntityPlayerMP> to = audience();
        if (to.isEmpty()) return;
        NBTTagCompound t = NexusNet.at(worldObj.provider.dimensionId, xCoord, yCoord, zCoord);
        t.setInteger("Rv", rev);
        t.setInteger("Ep", epoch);
        BookPacket.write(t, out);
        for (EntityPlayerMP p : to) NexusNet.tell(p, NexusNet.UNIT_DELTA, t);
    }

    /** Who hears of sample changes: the players within the effect range and 64 of it, and those watching its chunk. */
    private Set<EntityPlayerMP> audience() {
        int[] c = centre();
        Set<EntityPlayerMP> to = new LinkedHashSet<>(
            Sight.near(worldObj, c[0] + 0.5, c[1] + 0.5, c[2] + 0.5, Config.nexusEffectRange + 64));
        if (worldObj instanceof WorldServer ws) {
            for (Object o : ws.playerEntities) if (o instanceof EntityPlayerMP p && ws.getPlayerManager()
                .isPlayerWatchingChunk(p, xCoord >> 4, zCoord >> 4)) to.add(p);
        }
        return to;
    }

    @Override
    protected void writeSync(NBTTagCompound t) {
        super.writeSync(t);
        t.setBoolean("P", powered);
        t.setString("Dk", dockStatus());
        t.setInteger("Rv", rev);
        t.setInteger("Ep", epoch);
        List<BookPacket.Book> all = new ArrayList<>();
        for (int i = 0; i < samples.getSlots(); i++) {
            ItemStack s = samples.getStackInSlot(i);
            if (s != null) all.add(new BookPacket.Book(i, s.writeToNBT(new NBTTagCompound())));
        }
        BookPacket.write(t, all);
    }

    @Override
    protected void readSync(NBTTagCompound t) {
        super.readSync(t);
        clientPowered = t.getBoolean("P");
        clientDock = t.getString("Dk");
        int ep = t.getInteger("Ep"), r = t.getInteger("Rv");
        if (!described || ep != clientEpoch) {
            // another life of the tile on the server: nothing heard before counts
            Arrays.fill(clientStamp, Integer.MIN_VALUE);
            clientEpoch = ep;
            described = true;
        }
        int n = clientSamples.length;
        ItemStack[] next = new ItemStack[n];
        BookPacket
            .read(t, (slot, stack, id, meta) -> { if (slot >= 0 && slot < n) next[slot] = stackOf(stack, id, meta); });
        boolean touched = false;
        // a delta newer than this description (it may overtake it) keeps its samples
        for (int i = 0; i < n; i++) {
            if (r < clientStamp[i]) continue;
            clientStamp[i] = r;
            if (!ItemStack.areItemStacksEqual(clientSamples[i], next[i])) {
                clientSamples[i] = next[i];
                touched = true;
            }
        }
        touched |= replayParked();
        if (touched) clientSampleVersion++;
    }

    /** A delta packet for this module (client thread): applied where it is newer than what a slot holds. */
    private boolean applyDelta(NBTTagCompound t) {
        int k = t.getInteger("Rv");
        boolean[] touched = { false };
        BookPacket.read(t, (slot, stack, id, meta) -> {
            if (slot < 0 || slot >= clientSamples.length || k <= clientStamp[slot]) return;
            clientStamp[slot] = k;
            ItemStack s = stackOf(stack, id, meta);
            if (!ItemStack.areItemStacksEqual(clientSamples[slot], s)) {
                clientSamples[slot] = s;
                touched[0] = true;
            }
        });
        return touched[0];
    }

    /** A sample as a packet carried it: whole, or its item and metadata only; null for none (or an unknown item). */
    private static ItemStack stackOf(NBTTagCompound stack, int id, int meta) {
        if (stack != null) return ItemStack.loadItemStackFromNBT(stack);
        if (id < 0) return null;
        Item item = Item.getItemById(id);
        return item == null ? null : new ItemStack(item, 1, (short) meta);
    }

    /** Delta packets for a module the client has not got (yet): kept a while for the description that comes. */
    private static final List<Parked> PARKED = new ArrayList<>();
    private static final int PARKED_MAX = 128;
    private static final long PARKED_MS = 30_000;

    private static final class Parked {

        final int dim, x, y, z;
        final NBTTagCompound t;
        final long at;

        Parked(NBTTagCompound t, long at) {
            this.t = t;
            this.at = at;
            dim = t.getInteger("Dim");
            x = t.getInteger("X");
            y = t.getInteger("Y");
            z = t.getInteger("Z");
        }
    }

    /**
     * A {@link NexusNet#UNIT_DELTA} packet arrived (client thread): the module it names takes it, or it waits for that
     * module's description (a packet may overtake the chunk that brings the module).
     */
    public static void receiveDelta(NBTTagCompound t) {
        if (t == null) return;
        int dim = t.getInteger("Dim"), x = t.getInteger("X"), y = t.getInteger("Y"), z = t.getInteger("Z");
        for (TileMultiblock m : ClientTiles.all()) {
            if (!(m instanceof TileCityModule c) || m.isInvalid() || c.xCoord != x || c.yCoord != y || c.zCoord != z)
                continue;
            World w = c.getWorldObj();
            if (w == null || w.provider.dimensionId != dim) continue;
            if (!c.described || t.getInteger("Ep") != c.clientEpoch) break;
            if (c.applyDelta(t)) c.clientSampleVersion++;
            return;
        }
        synchronized (PARKED) {
            long now = System.currentTimeMillis();
            PARKED.removeIf(p -> now - p.at > PARKED_MS);
            while (PARKED.size() >= PARKED_MAX) PARKED.remove(0);
            PARKED.add(new Parked(t, now));
        }
    }

    /** After a description: the parked deltas of this module's life, oldest first; whether any changed a slot. */
    private boolean replayParked() {
        if (worldObj == null) return false;
        int dim = worldObj.provider.dimensionId;
        List<NBTTagCompound> mine = new ArrayList<>();
        synchronized (PARKED) {
            for (Iterator<Parked> it = PARKED.iterator(); it.hasNext();) {
                Parked p = it.next();
                if (p.dim != dim || p.x != xCoord || p.y != yCoord || p.z != zCoord) continue;
                it.remove();
                if (p.t.getInteger("Ep") == clientEpoch) mine.add(p.t);
            }
        }
        mine.sort((a, b) -> Integer.compare(a.getInteger("Rv"), b.getInteger("Rv")));
        boolean touched = false;
        for (NBTTagCompound t : mine) touched |= applyDelta(t);
        return touched;
    }

    /** Leaving a world: no parked packet belongs to the next one. */
    public static void clearClient() {
        synchronized (PARKED) {
            PARKED.clear();
        }
    }

    // ---- saving

    @Override
    public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setTag("Samples", samples.serializeNBT());
        t.setInteger("Unit", selectedUnit);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        // read through a handler of the saved size, so a save never resizes (and empties) the module's own
        ItemStackHandler saved = new ItemStackHandler(0);
        saved.deserializeNBT(t.getCompoundTag("Samples"));
        for (int i = 0; i < samples.getSlots(); i++)
            samples.setStackInSlot(i, i < saved.getSlots() ? saved.getStackInSlot(i) : null);
        selectedUnit = Math.max(0, Math.min(Math.max(0, unitCount() - 1), t.getInteger("Unit")));
        changed.clear();
    }

    @Override
    public ModularWindow createWindow(UIBuildContext ctx) {
        return CityGui.window(this, ctx);
    }
}
