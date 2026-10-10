package com.fluxecho.nexus;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentTranslation;

import com.fluxecho.Config;
import com.fluxecho.campus.Campus;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.campus.client.BuildClient;
import com.fluxecho.core.Directory;
import com.fluxecho.core.Owners;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.Formed;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.NexusShape;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.logic.RingSlots;
import com.fluxecho.matter.ItemEchoCrystal;
import com.fluxecho.research.Research;
import com.fluxecho.research.ResearchData;
import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.core.ServerEvents;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.modularui.api.forge.ItemStackHandler;
import com.gtnewhorizons.modularui.api.screen.ITileWithModularUI;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;

/**
 * The Flux Nexus (blueprint 3.3), phase I: anchored on its owner's team wireless network, it draws an upkeep, makes
 * flux compute for research (blueprint 3.5), runs the manifestation table ({@link Manifests}) and docks the modules
 * standing on its inner ring ({@link RingSlots}). Teams bound to it with a flux terminal share their research with it.
 */
public class TileNexus extends TileMultiblock implements ISidedInventory, ITileWithModularUI {

    public static final int INPUTS = 6, OUTPUTS = 2, PHASE = 1;
    /** The most modules an active campus docks: every site but the gate's. */
    public static final int MAX_CAMPUS_MODULES = 7;
    /** What the client copy shows is going on. */
    public static final int POWERED = 1, RESEARCHING = 2, MANIFESTING = 4;

    private static IStructureDefinition<TileMultiblock> definition, buildDefinition;

    final ItemStackHandler inv = new ItemStackHandler(INPUTS + OUTPUTS) {

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    };

    /** The campus round it: the construction jobs, their ledger, the hall sites (0.10.0). */
    private final Campus campus = new Campus(this);
    private final Set<UUID> bound = new LinkedHashSet<>();
    /** Whether teams other than the owner's may bind their terminals to it. */
    private boolean openBinding;
    private String research = "";
    private double researchDone;
    private UUID researchTeam;
    private String manifest = "";
    private int manifestTicks, manifestMax;
    private ItemStack pending;
    private boolean hologram;

    // live, server
    private boolean powered;
    private String status = "unformed";
    private Set<String> unlocked = Collections.emptySet();
    private int unlockedAge = 1000;
    private final List<TileModule> docked = new ArrayList<>();
    /**
     * Whether {@link #refreshDock} ran since the nexus loaded or last fell apart (before, {@link #docked} is empty).
     */
    private boolean dockKnown;
    private int activity, lastSent = -1;

    // the client's copy (and what the GUI syncs)
    public int clientActivity;
    public String clientResearch = "";
    public float clientResearchDone;
    public ItemStack clientMaking;
    public int clientDockedMask, clientOpen;
    public boolean clientHologram;
    public int clientCompute10;
    public long clientUpkeep;
    /** Slot -> module colour for the docked ones; drawn as bridges. */
    public int[] clientModuleColor = new int[RingSlots.SLOTS];
    /**
     * The client's copy of the campus (its job, state, projection inputs), from the description packet; replaced
     * wholesale on every update so the renderers never see it half-read.
     */
    public Campus.View clientCampus = new Campus.View();

    // what an open GUI shows that only the server knows (NexusGui syncs them)
    public String guiStatus = "unformed", guiResearch = "", guiManifest = "", guiBalance = "0", guiUnlocked = "";
    public long guiUpkeep;
    public boolean guiOpenBinding;
    public int guiCompute10, guiResearchPm, guiManifestPm, guiDocked, guiOpen, guiBound, guiRecords, guiResting;

    @Override
    protected Blueprint blueprint() {
        return NexusShape.PHASE_1;
    }

    @Override
    protected IStructureDefinition<TileMultiblock> definition() {
        if (definition == null)
            definition = define(true, new String[] { MAIN }, new Blueprint[] { blueprint() }, parts());
        return definition;
    }

    /** Phase I as {@link #MAIN}, and every phase as {@code phase1} to {@code phase5} for the projector and NEI. */
    @Override
    protected IStructureDefinition<TileMultiblock> buildDefinition() {
        if (buildDefinition == null) {
            String[] names = new String[NexusShape.PHASES + 1];
            Blueprint[] shapes = new Blueprint[names.length];
            names[0] = MAIN;
            shapes[0] = blueprint();
            for (int p = 1; p <= NexusShape.PHASES; p++) {
                names[p] = "phase" + p;
                shapes[p] = NexusShape.phase(p);
            }
            buildDefinition = define(false, names, shapes, parts());
        }
        return buildDefinition;
    }

    private static Object[] parts() {
        BlockFrame f = FrameModule.frame;
        return new Object[] { NexusShape.BASE, f, BlockFrame.BASE, NexusShape.LIT, f, BlockFrame.BASE_LIT,
            NexusShape.PILLAR, f, BlockFrame.PILLAR, NexusShape.CONDUIT, f, BlockFrame.CONDUIT, NexusShape.RING, f,
            BlockFrame.RING, NexusShape.SEAT, f, BlockFrame.SEAT, NexusShape.CONSOLE, f, BlockFrame.CONSOLE };
    }

    @Override
    protected Block coreBlock() {
        return NexusModule.core;
    }

    /**
     * The projector's (and NEI's) tier picks what it shows: 1 to 5 the nexus of that phase, 6 to 10 the same with its
     * open inner ring slots holding modules (previews only). In the world the projector builds only the phases the
     * nexus can reach today.
     */
    @Override
    protected void buildShape(ItemStack trigger, boolean hintsOnly) {
        // tiers past the last show the last, so NEI finds where its slider ends
        int tier = trigger == null ? 1 : Math.max(1, Math.min(2 * NexusShape.PHASES, trigger.stackSize));
        int phase = (tier - 1) % NexusShape.PHASES + 1;
        if (realWorld()) phase = Math.min(phase, PHASE);
        build("phase" + phase, NexusShape.phase(phase), trigger, hintsOnly);
        if (tier > NexusShape.PHASES && !realWorld()) previewModules(phase, trigger, hintsOnly);
    }

    /** Stands a module on each slot the phase opens, its front towards the nexus, for the preview. */
    private void previewModules(int phase, ItemStack trigger, boolean hintsOnly) {
        List<NexusModule.Preview> kinds = NexusModule.previews();
        if (kinds.isEmpty() || worldObj == null) return;
        int[] c = centre();
        int fx = front().offsetX, fz = front().offsetZ;
        for (int k = 0; k < RingSlots.open(phase); k++) {
            NexusModule.Preview kind = kinds.get(k % kinds.size());
            int[] o = RingSlots.offset(k, Config.innerRadius, fx, fz);
            int[] f = RingSlots.facing(-o[0], -o[1]);
            TileModule m = kind.tile.get();
            int[] cc = m.centreCell();
            // where its controller stands for its foundation's centre to sit on the slot
            int[] rel = m.blueprint()
                .world(cc[0], cc[1], cc[2], 0, 0, 0, f[0], f[1]);
            int x = c[0] + o[0] - rel[0], y = c[1] - rel[1], z = c[2] + o[1] - rel[2];
            worldObj.setBlock(x, y, z, kind.core, 0, 2);
            worldObj.setTileEntity(x, y, z, m);
            m.facing = facingOf(f[0], f[1]);
            ItemStack one = trigger == null ? null : trigger.copy();
            if (one != null) one.stackSize = 1;
            m.construct(one, hintsOnly);
        }
    }

    private static int facingOf(int fx, int fz) {
        for (int d = 2; d <= 5; d++) {
            net.minecraftforge.common.util.ForgeDirection f = net.minecraftforge.common.util.ForgeDirection
                .getOrientation(d);
            if (f.offsetX == fx && f.offsetZ == fz) return d;
        }
        return 2;
    }

    /** The console stand under the controller. */
    @Override
    public boolean consoleAt(int x, int y, int z) {
        return x == xCoord && y == yCoord - 1 && z == zCoord;
    }

    @Override
    protected int flags(char ch) {
        return NexusShape.dissolves(ch) ? Formed.HIDE | Formed.PASS : 0;
    }

    @Override
    protected String descriptionKey() {
        return "nexus.structure";
    }

    /** Its campus: the builder, its jobs and ledger. */
    public Campus campus() {
        return campus;
    }

    /** The campus changed something the client shows: save and send the description again. */
    public void syncCampus() {
        markDirty();
        sync();
    }

    /** The middle of its base: x, y (the base's level), z. */
    public int[] centre() {
        Blueprint b = blueprint();
        return cellPos(b.ctrlA, b.height() - 1, b.ctrlC + b.centreBack());
    }

    // ---- teams and research

    /** The owner's team and every team bound to it. */
    public Set<UUID> teams() {
        Set<UUID> out = new LinkedHashSet<>();
        UUID t = team();
        if (t != null) out.add(t);
        out.addAll(bound);
        return out;
    }

    /** Whether the player may use it: the owner's team or a team bound to it. */
    public boolean member(EntityPlayer p) {
        return p != null && teams().contains(Owners.team(p.getUniqueID()));
    }

    public boolean openBinding() {
        return openBinding;
    }

    /** Lets other teams bind (or not); only the owner's team may. Unbinds the others when closed. */
    void setOpenBinding(boolean open) {
        openBinding = open;
        if (!open) bound.clear();
        unlockedAge = 1000;
        markDirty();
    }

    /** Binds a team (a flux terminal used on the nexus); its owner's team is always in. */
    void bind(UUID t) {
        if (t == null || t.equals(team()) || !bound.add(t)) return;
        unlockedAge = 1000;
        markDirty();
    }

    void unbind(UUID t) {
        if (t == null || !bound.remove(t)) return;
        unlockedAge = 1000;
        markDirty();
    }

    public int boundCount() {
        return bound.size();
    }

    /** What the teams bound to it have researched between them. */
    public Set<String> unlocked() {
        if (worldObj == null || worldObj.isRemote) return Collections.emptySet();
        if (++unlockedAge > 20) {
            unlocked = Research.union(teams());
            unlockedAge = 0;
        }
        return unlocked;
    }

    public boolean has(String id) {
        return unlocked().contains(id);
    }

    public String research() {
        return research;
    }

    /** How far the running research is, 0..1. */
    public float researchFraction() {
        ResearchTree.Node n = ResearchTree.get(research);
        if (n == null) return 0;
        long cost = ResearchTree.computeFor(n, Config.researchScale);
        return cost <= 0 ? 1 : (float) Math.min(1, researchDone / cost);
    }

    /** Flux compute per second it makes now. */
    public double computeRate() {
        if (!formed || !powered) return 0;
        return Config.nexusCompute * (has(Research.COMPUTE_1) ? 1.5 : 1);
    }

    /** EU/t it draws while formed, the manifestation table's share included. */
    public long upkeep() {
        double base = Config.nexusUpkeep * (has(Research.UPKEEP_1) ? 0.75 : 1);
        return (long) Math.ceil(base) + (manifest.isEmpty() ? 0 : Config.manifestEut);
    }

    /**
     * Starts a research for the player: it must be open to the nexus (requirements met by the bound teams, the
     * player's team's codex big enough) and the player must carry its items, which it takes.
     *
     * @return a lang key under {@code fluxecho.research.} saying what happened
     */
    public String startResearch(EntityPlayerMP p, String id) {
        if (!formed) return "fail.unformed";
        if (!member(p)) return "fail.not_member";
        if (!research.isEmpty()) return "fail.busy";
        ResearchTree.Node n = ResearchTree.get(id);
        if (n == null) return "fail.unknown";
        UUID team = Owners.team(p.getUniqueID());
        ResearchTree.Block block = ResearchTree.check(n, unlockedNow(), PHASE, Records.count(team));
        if (block != ResearchTree.Block.NONE) return "fail." + block.name()
            .toLowerCase();
        if (!Costs.takeFrom(p, n.costs)) return "fail.items";
        research = id;
        researchDone = 0;
        researchTeam = team;
        markDirty();
        sync();
        return "started";
    }

    /** Drops the running research; its items are not given back. */
    public String cancelResearch(EntityPlayerMP p) {
        if (!member(p)) return "fail.not_member";
        if (research.isEmpty()) return "fail.none";
        research = "";
        researchDone = 0;
        researchTeam = null;
        markDirty();
        sync();
        return "cancelled";
    }

    private Set<String> unlockedNow() {
        unlockedAge = 1000;
        return unlocked();
    }

    // ---- ticking

    @Override
    protected void formedChanged(boolean now) {
        if (now) Research.grant(team(), Research.ANCHOR);
        unlockedAge = 1000;
    }

    @Override
    protected void serverTick() {
        long t = worldObj.getTotalWorldTime();
        // the campus first, formed or not: the establish job raises the nexus round its own core
        if (Config.nexusEnabled && Config.campusEnabled) campus.tick();
        if (!formed || !Config.nexusEnabled) {
            status = formed ? "disabled" : "unformed";
            powered = false;
            if (t % 100 == 0) {
                undockAll();
                Directory.report(
                    worldObj,
                    xCoord,
                    yCoord,
                    zCoord,
                    team(),
                    "tile.fluxecho.nexus.name",
                    "fluxecho.nexus.status." + status,
                    Directory.PROBLEM);
            }
            send();
            return;
        }
        UUID team = team();
        long eu = upkeep();
        powered = team != null && (eu <= 0 || GTWirelessBackend.INSTANCE.add(team, BigInteger.valueOf(-eu)));
        if (powered && eu > 0) ServerEvents.addTeamTick(team, 0, eu, eu, true);
        if (!powered) status = team == null ? "no_owner" : "no_power";
        else {
            status = "ready";
            runResearch();
            runManifest(t);
        }
        if (t % 100 == 0) {
            refreshDock(t);
            Directory.report(
                worldObj,
                xCoord,
                yCoord,
                zCoord,
                team(),
                "tile.fluxecho.nexus.name",
                "fluxecho.nexus.status." + status,
                "researching".equals(status) || "manifesting".equals(status) ? Directory.WORKING
                    : "ready".equals(status) ? Directory.IDLE : Directory.PROBLEM);
        }
        if (t % 40 == 0 && (!research.isEmpty() || !manifest.isEmpty())) lastSent = -1;
        send();
    }

    private void runResearch() {
        if (research.isEmpty()) return;
        ResearchTree.Node n = ResearchTree.get(research);
        if (n == null) {
            research = "";
            return;
        }
        status = "researching";
        researchDone += computeRate() / 20.0;
        if (researchDone < ResearchTree.computeFor(n, Config.researchScale)) return;
        UUID team = researchTeam != null ? researchTeam : team();
        Research.grant(team, research);
        tell(team, "fluxecho.research.done", "fluxecho.research.node." + research);
        research = "";
        researchDone = 0;
        researchTeam = null;
        unlockedAge = 1000;
        markDirty();
        sync();
    }

    private static void tell(UUID team, String key, String nodeKey) {
        for (Object o : net.minecraft.server.MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP p && team != null && team.equals(Owners.team(p.getUniqueID())))
                p.addChatMessage(new ChatComponentTranslation(key, new ChatComponentTranslation(nodeKey)));
        }
    }

    private void runManifest(long now) {
        if (manifest.isEmpty()) {
            startManifest(now);
            return;
        }
        if (manifestTicks < manifestMax) {
            manifestTicks++;
            status = "manifesting";
            return;
        }
        if (pending != null && !deliver(pending)) {
            status = "output_full";
            return;
        }
        pending = null;
        manifest = "";
        manifestTicks = manifestMax = 0;
        markDirty();
        sync();
    }

    private void startManifest(long now) {
        Set<String> open = unlocked();
        ItemStack[] in = new ItemStack[INPUTS];
        for (int i = 0; i < INPUTS; i++) in[i] = inv.getStackInSlot(i);
        for (Manifests.Recipe r : Manifests.all()) {
            if (!open.contains(r.research)) continue;
            int[] take = Costs.plan(r.inputs, in);
            if (take == null) continue;
            ItemStack out = r.output();
            if (out == null) continue;
            String[] rec = null;
            if (r.record) {
                rec = Records.next(team(), now, Config.recordCooldown);
                if (rec == null) {
                    status = "records_resting";
                    continue;
                }
                out = ItemEchoCrystal.of(rec[0], rec[1], out.stackSize);
            }
            if (!fits(out)) {
                status = "output_full";
                return;
            }
            if (rec != null) ResearchData.get()
                .pressed(team(), Records.entry(rec[0], rec[1]), now);
            for (int i = 0; i < INPUTS; i++) if (take[i] > 0) inv.extractItem(i, take[i], false);
            manifest = r.id;
            pending = out;
            manifestTicks = 0;
            manifestMax = Math.max(1, r.ticks);
            status = "manifesting";
            markDirty();
            sync();
            return;
        }
    }

    private boolean fits(ItemStack s) {
        ItemStack left = s.copy();
        for (int i = INPUTS; i < INPUTS + OUTPUTS && left != null; i++) left = inv.insertItem(i, left, true);
        return left == null;
    }

    private boolean deliver(ItemStack s) {
        if (!fits(s)) return false;
        ItemStack left = s.copy();
        for (int i = INPUTS; i < INPUTS + OUTPUTS && left != null; i++) left = inv.insertItem(i, left, false);
        return true;
    }

    /**
     * Which modules it takes: its teams' formed modules, as many as it has room for ({@link #openSlots}). A module is
     * found, in this order of precedence, (1) on the hall site the campus recorded for it, (2) standing on a hall site
     * of an active campus (its centre within 3 blocks of where the site puts a module of its kind, at most one block
     * up or down, its front towards the nexus): it is adopted and the site recorded, or (3) on a slot of the 0.9.2
     * inner ring. A module on a site docks under the site's number, which points the same way as the ring slot of that
     * number. Docked and rejected modules get a lease of 220 ticks.
     */
    void refreshDock(long now) {
        int[] c = centre();
        int fx = front().offsetX, fz = front().offsetZ;
        Set<UUID> teams = teams();
        int open = openSlots();
        boolean active = campus.active();
        List<TileModule> found = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        List<Integer> ranks = new ArrayList<>();
        Set<TileModule> seen = new HashSet<>();
        // (1) the modules on recorded hall sites
        for (int site = 0; site < RingSlots.SLOTS; site++) {
            int[] at = campus.siteController(site);
            if (at == null || !worldObj.blockExists(at[0], at[1], at[2])) continue;
            if (!(worldObj.getTileEntity(at[0], at[1], at[2]) instanceof TileModule m) || m.isInvalid() || !m.formed())
                continue;
            seen.add(m);
            if (!teams.contains(m.team())) {
                m.reject(this, site, "other_team", now + 220);
                continue;
            }
            found.add(m);
            slots.add(site);
            ranks.add(0);
        }
        for (TileModule m : NexusRegistry.modules(worldObj.provider.dimensionId)) {
            if (!m.formed() || m.isInvalid() || m.getWorldObj() != worldObj || seen.contains(m)) continue;
            int[] mc = m.centre();
            int slot, rank;
            // (2) a module standing on a hall site of an active campus is adopted; (3) else the 0.9.2 ring
            int site = active ? hallSite(m, mc, c, fx, fz) : -1;
            if (site >= 0) {
                slot = site;
                rank = 1;
            } else {
                slot = RingSlots.slotAt(mc[0] - c[0], mc[1] - c[1], mc[2] - c[2], Config.innerRadius, fx, fz);
                rank = 2;
            }
            if (slot < 0) continue;
            if (!teams.contains(m.team())) {
                m.reject(this, slot, "other_team", now + 220);
                continue;
            }
            if (rank == 1) campus.recordSite(site, m.xCoord, m.yCoord, m.zCoord);
            found.add(m);
            slots.add(slot);
            ranks.add(rank);
        }
        docked.clear();
        boolean[] taken = new boolean[RingSlots.SLOTS];
        for (int rank = 0; rank <= 2; rank++) for (int k = 0; k < RingSlots.SLOTS; k++) {
            for (int i = 0; i < found.size(); i++) {
                if (ranks.get(i) != rank || slots.get(i) != k) continue;
                TileModule m = found.get(i);
                if (taken[k]) m.reject(this, k, "slot_taken", now + 220);
                else if (docked.size() >= open) m.reject(this, k, open == 0 ? "ring_closed" : "ring_full", now + 220);
                else {
                    taken[k] = true;
                    docked.add(m);
                    m.dock(this, k, now + 220);
                }
            }
        }
        dockKnown = true;
    }

    /**
     * The hall site a module stands on, or -1: its module kind has a campus spec that builds on that site, its centre
     * is within 3 blocks of the site's module centre for the kind's depth (campus frame), at most one block up or down
     * from the nexus's base, and its front points back along the site's axis towards the nexus.
     */
    private static int hallSite(TileModule m, int[] mc, int[] c, int fx, int fz) {
        ModuleSpec spec = ModuleSpecs.get(m.moduleKey());
        if (spec == null || Math.abs(mc[1] - c[1]) > 1) return -1;
        int[] local = CampusPlan.toLocal(mc[0] - c[0], mc[2] - c[2], fx, fz);
        int site = CampusPlan.hallSiteAt(local[0], local[1], spec.depth);
        if (site < 0 || !spec.hasSite(site)) return -1;
        int[] axis = CampusPlan.siteAxis(site);
        int[] facing = CampusPlan.toLocal(m.front().offsetX, m.front().offsetZ, fx, fz);
        return facing[0] == -axis[0] && facing[1] == -axis[1] ? site : -1;
    }

    private void undockAll() {
        docked.clear();
        dockKnown = false;
    }

    /**
     * Whether the docked modules are known: the nexus looked for them since it loaded or formed again. Until then
     * {@link #docked} is empty although modules may stand docked, so nothing should be decided from it.
     */
    public boolean dockKnown() {
        return dockKnown;
    }

    public List<TileModule> docked() {
        List<TileModule> out = new ArrayList<>();
        for (TileModule m : docked) if (m.nexus() == this) out.add(m);
        return out;
    }

    /**
     * How many modules it can dock: the slots its phase opens once its teams have the inner ring, and on an active
     * campus at most seven (every site but the gate's).
     */
    public int openSlots() {
        int open = has(Research.INNER_RING) ? RingSlots.open(PHASE) : 0;
        return campus.active() ? Math.min(open, MAX_CAMPUS_MODULES) : open;
    }

    public boolean powered() {
        return powered;
    }

    /** The owner team's wireless balance, for the GUI. */
    public String balance() {
        UUID t = team();
        return t == null ? "0"
            : GTWirelessBackend.INSTANCE.getBalance(t)
                .toString();
    }

    /** Research done by the bound teams, comma-separated, for the GUI. */
    public String unlockedList() {
        return String.join(",", unlocked());
    }

    private int records, resting;
    private long recordsAt = Long.MIN_VALUE;

    private void countRecords() {
        if (worldObj == null) return;
        long now = worldObj.getTotalWorldTime();
        if (now - recordsAt < 20) return;
        recordsAt = now;
        records = Records.count(team());
        resting = Records.resting(team(), now, Config.recordCooldown);
    }

    /** Entries in the owner team's codex (counted once a second). */
    public int recordCount() {
        countRecords();
        return records;
    }

    /** Entries resting after being pressed into an echo crystal. */
    public int restingCount() {
        countRecords();
        return resting;
    }

    public String status() {
        return status;
    }

    public String manifest() {
        return manifest;
    }

    public float manifestFraction() {
        return manifestMax <= 0 ? 0 : Math.min(1f, manifestTicks / (float) manifestMax);
    }

    public ItemStack making() {
        return pending;
    }

    public boolean hologram() {
        return hologram;
    }

    public void setHologram(boolean on) {
        hologram = on;
        markDirty();
        sync();
    }

    /** Sends the client copy when what it shows changed. */
    private void send() {
        int a = (powered ? POWERED : 0) | (research.isEmpty() ? 0 : RESEARCHING)
            | (manifest.isEmpty() ? 0 : MANIFESTING);
        int mask = 0;
        for (TileModule m : docked()) if (m.slot() >= 0) mask |= 1 << m.slot();
        int key = a | mask << 3 | openSlots() << 11;
        activity = a;
        if (key != lastSent) {
            lastSent = key;
            sync();
        }
    }

    // ---- client copy

    @Override
    protected void writeSync(NBTTagCompound t) {
        t.setByte("A", (byte) activity);
        t.setString("R", research);
        t.setFloat("RD", researchFraction());
        if (pending != null) t.setTag("M", pending.writeToNBT(new NBTTagCompound()));
        int mask = 0;
        int[] colors = new int[RingSlots.SLOTS];
        for (TileModule m : docked()) if (m.slot() >= 0) {
            mask |= 1 << m.slot();
            colors[m.slot()] = m.moduleColor();
        }
        t.setByte("D", (byte) mask);
        t.setIntArray("DC", colors);
        t.setByte("Op", (byte) openSlots());
        t.setBoolean("H", hologram);
        t.setInteger("C", (int) Math.round(computeRate() * 10));
        t.setLong("U", formed ? upkeep() : 0);
        // the campus: only what changes on its state transitions; the progress numbers go over NexusNet
        NBTTagCompound cp = new NBTTagCompound();
        campus.writeSync(cp);
        t.setTag("Cp", cp);
    }

    @Override
    protected void readSync(NBTTagCompound t) {
        clientActivity = t.getByte("A");
        clientResearch = t.getString("R");
        clientResearchDone = t.getFloat("RD");
        clientMaking = t.hasKey("M") ? ItemStack.loadItemStackFromNBT(t.getCompoundTag("M")) : null;
        clientDockedMask = t.getByte("D") & 0xFF;
        int[] c = t.getIntArray("DC");
        if (c.length == RingSlots.SLOTS) clientModuleColor = c;
        clientOpen = t.getByte("Op");
        clientHologram = t.getBoolean("H");
        clientCompute10 = t.getInteger("C");
        clientUpkeep = t.getLong("U");
        Campus.View v = new Campus.View();
        if (t.hasKey("Cp")) v.read(t.getCompoundTag("Cp"));
        clientCampus = v;
        // progress numbers sent before this transition are out of date now
        if (worldObj != null) BuildClient.viewChanged(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, v);
    }

    // ---- saving

    @Override
    public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setTag("Inv", inv.serializeNBT());
        NBTTagList b = new NBTTagList();
        for (UUID u : bound) b.appendTag(new NBTTagString(u.toString()));
        t.setTag("Bound", b);
        t.setBoolean("OpenBinding", openBinding);
        t.setString("Research", research);
        t.setDouble("ResearchDone", researchDone);
        if (researchTeam != null) t.setString("ResearchTeam", researchTeam.toString());
        t.setString("Manifest", manifest);
        t.setInteger("ManifestTicks", manifestTicks);
        t.setInteger("ManifestMax", manifestMax);
        if (pending != null) t.setTag("Pending", pending.writeToNBT(new NBTTagCompound()));
        t.setBoolean("Holo", hologram);
        campus.writeNBT(t);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        inv.deserializeNBT(t.getCompoundTag("Inv"));
        bound.clear();
        NBTTagList b = t.getTagList("Bound", 8);
        for (int i = 0; i < b.tagCount(); i++) {
            try {
                bound.add(UUID.fromString(b.getStringTagAt(i)));
            } catch (IllegalArgumentException ignored) {}
        }
        openBinding = t.getBoolean("OpenBinding");
        research = t.getString("Research");
        researchDone = t.getDouble("ResearchDone");
        try {
            researchTeam = t.hasKey("ResearchTeam") ? UUID.fromString(t.getString("ResearchTeam")) : null;
        } catch (IllegalArgumentException e) {
            researchTeam = null;
        }
        manifest = t.getString("Manifest");
        manifestTicks = t.getInteger("ManifestTicks");
        manifestMax = t.getInteger("ManifestMax");
        pending = t.hasKey("Pending") ? ItemStack.loadItemStackFromNBT(t.getCompoundTag("Pending")) : null;
        hologram = t.getBoolean("Holo");
        campus.readNBT(t);
    }

    @Override
    public void validate() {
        super.validate();
        if (realWorld() && !worldObj.isRemote) NexusRegistry.add(this);
    }

    @Override
    protected void gone() {
        super.gone();
        NexusRegistry.remove(this);
    }

    // ---- GUI

    @Override
    public ModularWindow createWindow(UIBuildContext ctx) {
        return NexusGui.window(this, ctx);
    }

    // ---- inventory, for pipes and hoppers: inputs in, outputs out

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        int[] all = new int[INPUTS + OUTPUTS];
        for (int i = 0; i < all.length; i++) all[i] = i;
        return all;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack s, int side) {
        return slot < INPUTS;
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack s, int side) {
        return slot >= INPUTS;
    }

    @Override
    public int getSizeInventory() {
        return INPUTS + OUTPUTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inv.getStackInSlot(slot);
    }

    @Override
    public ItemStack decrStackSize(int slot, int n) {
        return inv.extractItem(slot, n, false);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack s) {
        inv.setStackInSlot(slot, s);
    }

    @Override
    public String getInventoryName() {
        return "tile.fluxecho.nexus.name";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer p) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && p.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack s) {
        return slot < INPUTS;
    }

}
