package com.fluxecho.nexus;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentTranslation;

import com.fluxecho.Config;
import com.fluxecho.core.Directory;
import com.fluxecho.core.Owners;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.Formed;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.NexusShape;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.logic.RingSlots;
import com.fluxecho.matter.ItemEchoCrystal;
import com.fluxecho.research.Research;
import com.fluxecho.research.ResearchData;
import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.core.ServerEvents;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
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
    /** What the client copy shows is going on. */
    public static final int POWERED = 1, RESEARCHING = 2, MANIFESTING = 4;

    private static IStructureDefinition<TileMultiblock> definition;

    final ItemStackHandler inv = new ItemStackHandler(INPUTS + OUTPUTS) {

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    };

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
        if (definition == null) definition = build();
        return definition;
    }

    private static IStructureDefinition<TileMultiblock> build() {
        BlockFrame f = FrameModule.frame;
        return StructureDefinition.<TileMultiblock>builder()
            .addShape(MAIN, StructureUtility.transpose(NexusShape.PHASE_1.shape()))
            .addElement(NexusShape.BASE, new Recorded(NexusShape.BASE, StructureUtility.ofBlock(f, BlockFrame.BASE)))
            .addElement(NexusShape.LIT, new Recorded(NexusShape.LIT, StructureUtility.ofBlock(f, BlockFrame.BASE_LIT)))
            .addElement(
                NexusShape.PILLAR,
                new Recorded(NexusShape.PILLAR, StructureUtility.ofBlock(f, BlockFrame.PILLAR)))
            .addElement(
                NexusShape.CONDUIT,
                new Recorded(NexusShape.CONDUIT, StructureUtility.ofBlock(f, BlockFrame.CONDUIT)))
            .addElement(NexusShape.RING, new Recorded(NexusShape.RING, StructureUtility.ofBlock(f, BlockFrame.RING)))
            .addElement(NexusShape.SEAT, new Recorded(NexusShape.SEAT, StructureUtility.ofBlock(f, BlockFrame.SEAT)))
            .build();
    }

    @Override
    protected int flags(char ch) {
        return NexusShape.dissolves(ch) ? Formed.HIDE | Formed.PASS : 0;
    }

    @Override
    protected String descriptionKey() {
        return "nexus.structure";
    }

    /** The middle of its base: x, y (the base's level), z. */
    public int[] centre() {
        Blueprint b = blueprint();
        return cellPos(b.ctrlA, b.ctrlB, b.ctrlC + b.centreBack());
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

    /** Which modules on its ring it takes: its teams' formed modules on a slot, as many as its phase opens. */
    void refreshDock(long now) {
        int[] c = centre();
        int fx = front().offsetX, fz = front().offsetZ;
        Set<UUID> teams = teams();
        int open = has(Research.INNER_RING) ? RingSlots.open(PHASE) : 0;
        List<TileModule> found = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        for (TileModule m : NexusRegistry.modules(worldObj.provider.dimensionId)) {
            if (!m.formed() || m.isInvalid()) continue;
            int[] mc = m.centre();
            int slot = RingSlots.slotAt(mc[0] - c[0], mc[1] - c[1], mc[2] - c[2], Config.innerRadius, fx, fz);
            if (slot < 0) continue;
            if (!teams.contains(m.team())) {
                m.reject(this, slot, "other_team", now + 220);
                continue;
            }
            found.add(m);
            slots.add(slot);
        }
        docked.clear();
        boolean[] taken = new boolean[RingSlots.SLOTS];
        for (int k = 0; k < RingSlots.SLOTS; k++) for (int i = 0; i < found.size(); i++) {
            if (slots.get(i) != k) continue;
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

    private void undockAll() {
        docked.clear();
    }

    public List<TileModule> docked() {
        List<TileModule> out = new ArrayList<>();
        for (TileModule m : docked) if (m.nexus() == this) out.add(m);
        return out;
    }

    public int openSlots() {
        return has(Research.INNER_RING) ? RingSlots.open(PHASE) : 0;
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
