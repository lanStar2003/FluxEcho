package com.fluxecho.nexus;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.fluxecho.core.Directory;
import com.fluxecho.core.Owners;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.research.Research;

/**
 * What the flux terminal's FluxEcho pages show (blueprint 3.11), built on the server for the player: the team's
 * machines, and the nexus the terminal is bound to with its research.
 */
public final class TerminalView {

    /** The terminal's pages; 0 is the flux network's dashboard. */
    public static final int NETWORK = 0, MACHINES = 1, CODEX = 2, RESEARCH = 3, NEXUS = 4;
    private static final int MAX_ROWS = 200;

    private TerminalView() {}

    public static NBTTagCompound build(EntityPlayerMP p, int page) {
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("term", true);
        t.setInteger("termPage", page);
        UUID team = Owners.team(p.getUniqueID());
        if (page == MACHINES) machines(t, team);
        else if (page == RESEARCH || page == NEXUS) nexus(t, p, team);
        return t;
    }

    private static void machines(NBTTagCompound t, UUID team) {
        List<Directory.Entry> all = Directory.of(team);
        long now = System.currentTimeMillis();
        NBTTagList l = new NBTTagList();
        int working = 0, problems = 0;
        for (Directory.Entry e : all) {
            if (e.level == Directory.WORKING) working++;
            if (e.level == Directory.PROBLEM) problems++;
            if (l.tagCount() >= MAX_ROWS) continue;
            NBTTagCompound r = new NBTTagCompound();
            r.setString("n", e.name);
            r.setString("s", e.status);
            r.setByte("l", (byte) e.level);
            r.setInteger("d", e.dim);
            r.setInteger("x", e.x);
            r.setInteger("y", e.y);
            r.setInteger("z", e.z);
            r.setInteger("a", (int) ((now - e.seen) / 1000));
            l.appendTag(r);
        }
        t.setTag("m", l);
        t.setInteger("total", all.size());
        t.setInteger("working", working);
        t.setInteger("problems", problems);
    }

    private static void nexus(NBTTagCompound t, EntityPlayerMP p, UUID team) {
        t.setInteger(
            "done",
            Research.done(team)
                .size());
        int total = 0;
        for (ResearchTree.Node n : ResearchTree.all()) if (n.phase <= ResearchTree.PHASE_NOW) total++;
        t.setInteger("nodes", total);
        int[] at = NexusBinding.bound(p);
        if (at == null) return;
        t.setIntArray("bound", at);
        TileNexus n = NexusRegistry.at(at[0], at[1], at[2], at[3]);
        t.setBoolean("loaded", n != null);
        if (n == null) return;
        t.setBoolean("member", n.member(p));
        t.setString("owner", n.ownerName());
        t.setBoolean("formed", n.formed());
        t.setBoolean("powered", n.powered());
        t.setString("status", n.status());
        t.setLong("upkeep", n.upkeep());
        t.setDouble("compute", n.computeRate());
        t.setString("research", n.research());
        t.setFloat("fraction", n.researchFraction());
        t.setInteger("open", n.openSlots());
        t.setInteger("boundTeams", n.boundCount());
        t.setString("balance", n.balance());
        t.setInteger(
            "unlocked",
            n.unlocked()
                .size());
        NBTTagList avail = new NBTTagList();
        int records = Records.count(team);
        for (ResearchTree.Node node : ResearchTree.all()) {
            if (ResearchTree.check(node, n.unlocked(), TileNexus.PHASE, records) == ResearchTree.Block.NONE) {
                NBTTagCompound r = new NBTTagCompound();
                r.setString("id", node.id);
                avail.appendTag(r);
            }
        }
        t.setTag("avail", avail);
        NBTTagList mods = new NBTTagList();
        for (TileModule m : n.docked()) {
            NBTTagCompound r = new NBTTagCompound();
            r.setString("k", m.moduleKey());
            r.setInteger("slot", m.slot());
            r.setInteger("c", m.moduleColor());
            r.setString("dock", m.dockStatus());
            mods.appendTag(r);
        }
        t.setTag("modules", mods);
    }
}
