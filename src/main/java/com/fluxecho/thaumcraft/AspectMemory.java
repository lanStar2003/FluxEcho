package com.fluxecho.thaumcraft;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

import com.fluxecho.codex.EchoNet;

import thaumcraft.api.aspects.Aspect;

/**
 * The aspects each team has held once, shared by all of the team's FluxEcho Thaumcraft machines, and the research
 * each player was seen to have finished. Saved with the world ({@code data/fluxecho_aspects.dat}); server side only.
 * <p>
 * Thaumcraft only has a player's research while they are online (after a restart an offline player has none), so
 * a machine checks research while its owner is online and remembers what it found here.
 */
public class AspectMemory extends WorldSavedData {

    static final String NAME = "fluxecho_aspects";

    private static AspectMemory instance;

    private final Map<UUID, Set<String>> known = new HashMap<>();
    private final Map<UUID, Set<String>> research = new HashMap<>();

    public AspectMemory(String name) {
        super(name);
    }

    /** The memory of the running server's world. */
    public static AspectMemory get() {
        if (instance == null) {
            World w = DimensionManager.getWorld(0);
            if (w == null) return new AspectMemory(NAME); // no world (yet): an empty memory that is not saved
            AspectMemory m = (AspectMemory) w.mapStorage.loadData(AspectMemory.class, NAME);
            if (m == null) {
                m = new AspectMemory(NAME);
                w.mapStorage.setData(NAME, m);
            }
            instance = m;
        }
        return instance;
    }

    /** Forget the loaded memory when a server stops, so the next single player world loads its own. */
    static void reset() {
        instance = null;
    }

    public boolean knows(UUID team, Aspect a) {
        Set<String> s = team == null || a == null ? null : known.get(team);
        return s != null && s.contains(a.getTag());
    }

    public int count(UUID team) {
        Set<String> s = team == null ? null : known.get(team);
        return s == null ? 0 : s.size();
    }

    /** The tags of the aspects the team knows. */
    public Set<String> tags(UUID team) {
        Set<String> s = team == null ? null : known.get(team);
        return s == null ? Collections.emptySet() : Collections.unmodifiableSet(s);
    }

    /** @return how many of the aspects were new to the team */
    public int learn(UUID team, Collection<Aspect> aspects) {
        if (team == null) return 0;
        Set<String> s = known.computeIfAbsent(team, k -> new LinkedHashSet<>());
        int added = 0;
        for (Aspect a : aspects) if (a != null && s.add(a.getTag())) added++;
        if (added > 0) {
            markDirty();
            EchoNet.ledgerChanged(team);
        }
        return added;
    }

    public boolean researched(UUID player, String key) {
        Set<String> s = player == null ? null : research.get(player);
        return s != null && s.contains(key);
    }

    public void addResearch(UUID player, String key) {
        if (player != null && research.computeIfAbsent(player, k -> new LinkedHashSet<>())
            .add(key)) markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        read(tag.getTagList("Teams", 10), "Team", "Aspects", known);
        read(tag.getTagList("Research", 10), "Player", "Keys", research);
    }

    private static void read(NBTTagList list, String idKey, String valuesKey, Map<UUID, Set<String>> into) {
        into.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            try {
                Set<String> s = new LinkedHashSet<>();
                for (String v : t.getString(valuesKey)
                    .split(",")) if (!v.isEmpty()) s.add(v);
                into.put(UUID.fromString(t.getString(idKey)), s);
            } catch (IllegalArgumentException ignored) {
                // a broken entry is dropped, the rest stays
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setTag("Teams", write(known, "Team", "Aspects"));
        tag.setTag("Research", write(research, "Player", "Keys"));
    }

    private static NBTTagList write(Map<UUID, Set<String>> from, String idKey, String valuesKey) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Set<String>> e : from.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString(
                idKey,
                e.getKey()
                    .toString());
            t.setString(valuesKey, String.join(",", e.getValue()));
            list.appendTag(t);
        }
        return list;
    }
}
