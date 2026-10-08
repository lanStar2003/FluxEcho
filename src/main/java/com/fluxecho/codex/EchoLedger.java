package com.fluxecho.codex;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

/**
 * What each team has done once and the flux layer now echoes, by category ({@link Categories}): the species they
 * imprinted, the prey they hunted, the infusions they made... The machines write it, the Echo Codex and the item
 * tooltips read it. Saved with the world ({@code data/fluxecho_ledger.dat}); server side only, the clients get their
 * team's copy through {@link EchoNet}.
 * <p>
 * Categories that a module already keeps elsewhere (the aspects of Thaumcraft's {@code AspectMemory}) are read
 * through a {@link #source} instead of being copied here.
 */
public class EchoLedger extends WorldSavedData {

    static final String NAME = "fluxecho_ledger";

    private static EchoLedger instance;
    private static final Map<String, Function<UUID, Collection<String>>> SOURCES = new LinkedHashMap<>();

    private final Map<UUID, Map<String, Set<String>>> teams = new HashMap<>();

    public EchoLedger(String name) {
        super(name);
    }

    /** The ledger of the running server's world. */
    public static EchoLedger get() {
        if (instance == null) {
            World w = DimensionManager.getWorld(0);
            if (w == null) return new EchoLedger(NAME); // no world (yet): an empty ledger that is not saved
            EchoLedger l = (EchoLedger) w.mapStorage.loadData(EchoLedger.class, NAME);
            if (l == null) {
                l = new EchoLedger(NAME);
                w.mapStorage.setData(NAME, l);
            }
            instance = l;
        }
        return instance;
    }

    /** Forget the loaded ledger when a server stops, so the next single player world loads its own. */
    public static void reset() {
        instance = null;
    }

    /** A category a module keeps itself; read whenever the ledger is sent. */
    public static void source(String category, Function<UUID, Collection<String>> keys) {
        SOURCES.put(category, keys);
    }

    /**
     * Notes that the team has done it once.
     *
     * @return whether it was new
     */
    public boolean record(UUID team, String category, String key) {
        if (team == null || category == null || key == null || key.isEmpty()) return false;
        boolean added = teams.computeIfAbsent(team, k -> new LinkedHashMap<>())
            .computeIfAbsent(category, k -> new LinkedHashSet<>())
            .add(key);
        if (added) {
            markDirty();
            EchoNet.ledgerChanged(team);
        }
        return added;
    }

    public boolean has(UUID team, String category, String key) {
        Map<String, Set<String>> t = team == null ? null : teams.get(team);
        Set<String> s = t == null ? null : t.get(category);
        return s != null && s.contains(key);
    }

    public Set<String> keys(UUID team, String category) {
        Map<String, Set<String>> t = team == null ? null : teams.get(team);
        Set<String> s = t == null ? null : t.get(category);
        return s == null ? Collections.emptySet() : Collections.unmodifiableSet(s);
    }

    /** Everything the team has, the sources included: what a client gets. */
    public NBTTagCompound snapshot(UUID team) {
        NBTTagCompound out = new NBTTagCompound();
        Map<String, Set<String>> t = team == null ? null : teams.get(team);
        if (t != null) for (Map.Entry<String, Set<String>> e : t.entrySet()) out.setTag(e.getKey(), list(e.getValue()));
        if (team != null) for (Map.Entry<String, Function<UUID, Collection<String>>> e : SOURCES.entrySet()) {
            try {
                out.setTag(
                    e.getKey(),
                    list(
                        e.getValue()
                            .apply(team)));
            } catch (RuntimeException ignored) {
                // a module that fails to answer just shows nothing
            }
        }
        return out;
    }

    private static NBTTagList list(Collection<String> keys) {
        NBTTagList l = new NBTTagList();
        for (String k : keys) l.appendTag(new NBTTagString(k));
        return l;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        teams.clear();
        NBTTagList list = tag.getTagList("Teams", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            try {
                UUID team = UUID.fromString(t.getString("Team"));
                Map<String, Set<String>> cats = new LinkedHashMap<>();
                NBTTagCompound c = t.getCompoundTag("Done");
                for (Object o : c.func_150296_c()) {
                    String cat = (String) o;
                    NBTTagList keys = c.getTagList(cat, 8);
                    Set<String> s = new LinkedHashSet<>();
                    for (int j = 0; j < keys.tagCount(); j++) s.add(keys.getStringTagAt(j));
                    cats.put(cat, s);
                }
                teams.put(team, cats);
            } catch (IllegalArgumentException ignored) {
                // a broken entry is dropped, the rest stays
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Map<String, Set<String>>> e : teams.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString(
                "Team",
                e.getKey()
                    .toString());
            NBTTagCompound c = new NBTTagCompound();
            for (Map.Entry<String, Set<String>> cat : e.getValue()
                .entrySet()) c.setTag(cat.getKey(), list(cat.getValue()));
            t.setTag("Done", c);
            list.appendTag(t);
        }
        tag.setTag("Teams", list);
    }
}
