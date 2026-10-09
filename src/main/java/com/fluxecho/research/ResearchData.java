package com.fluxecho.research;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

/**
 * Which research each team has done, and when each of its Echo Codex entries was last pressed into an echo crystal.
 * Saved with the world ({@code data/fluxecho_research.dat}); server side only.
 */
public class ResearchData extends WorldSavedData {

    static final String NAME = "fluxecho_research";

    private static ResearchData instance;

    private final Map<UUID, Set<String>> done = new HashMap<>();
    /** Per team: "category:key" -> world time the entry was last used. */
    private final Map<UUID, Map<String, Long>> pressed = new HashMap<>();

    public ResearchData(String name) {
        super(name);
    }

    public static ResearchData get() {
        if (instance == null) {
            World w = DimensionManager.getWorld(0);
            if (w == null) return new ResearchData(NAME);
            ResearchData d = (ResearchData) w.mapStorage.loadData(ResearchData.class, NAME);
            if (d == null) {
                d = new ResearchData(NAME);
                w.mapStorage.setData(NAME, d);
            }
            instance = d;
        }
        return instance;
    }

    public static void reset() {
        instance = null;
    }

    public boolean has(UUID team, String id) {
        Set<String> s = team == null ? null : done.get(team);
        return s != null && s.contains(id);
    }

    public Set<String> done(UUID team) {
        Set<String> s = team == null ? null : done.get(team);
        return s == null ? Collections.emptySet() : Collections.unmodifiableSet(s);
    }

    /** @return whether it was new */
    public boolean grant(UUID team, String id) {
        if (team == null || id == null) return false;
        boolean added = done.computeIfAbsent(team, k -> new LinkedHashSet<>())
            .add(id);
        if (added) markDirty();
        return added;
    }

    /** World time the team last pressed the entry into a crystal; {@link Long#MIN_VALUE} for never. */
    public long lastPressed(UUID team, String entry) {
        Map<String, Long> m = team == null ? null : pressed.get(team);
        Long t = m == null ? null : m.get(entry);
        return t == null ? Long.MIN_VALUE : t;
    }

    public void pressed(UUID team, String entry, long time) {
        if (team == null) return;
        pressed.computeIfAbsent(team, k -> new HashMap<>())
            .put(entry, time);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        done.clear();
        pressed.clear();
        NBTTagList list = tag.getTagList("Teams", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            UUID team;
            try {
                team = UUID.fromString(t.getString("Team"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            NBTTagList ids = t.getTagList("Done", 8);
            Set<String> s = new LinkedHashSet<>();
            for (int j = 0; j < ids.tagCount(); j++) s.add(ids.getStringTagAt(j));
            done.put(team, s);
            NBTTagCompound p = t.getCompoundTag("Pressed");
            Map<String, Long> m = new HashMap<>();
            for (Object o : p.func_150296_c()) m.put((String) o, p.getLong((String) o));
            if (!m.isEmpty()) pressed.put(team, m);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        Set<UUID> teams = new LinkedHashSet<>(done.keySet());
        teams.addAll(pressed.keySet());
        NBTTagList list = new NBTTagList();
        for (UUID team : teams) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("Team", team.toString());
            NBTTagList ids = new NBTTagList();
            for (String id : done(team)) ids.appendTag(new NBTTagString(id));
            t.setTag("Done", ids);
            NBTTagCompound p = new NBTTagCompound();
            Map<String, Long> m = pressed.get(team);
            if (m != null) for (Map.Entry<String, Long> e : m.entrySet()) p.setLong(e.getKey(), e.getValue());
            t.setTag("Pressed", p);
            list.appendTag(t);
        }
        tag.setTag("Teams", list);
    }
}
