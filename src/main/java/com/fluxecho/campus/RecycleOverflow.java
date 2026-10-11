package com.fluxecho.campus;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

/**
 * What a broken nexus core could not carry of its campus's recycle store (0.10.1), kept for the nexus's team in the
 * save ({@code data/fluxecho_recycle.dat}) until a nexus of that team has room for it again.
 * <p>
 * A core item carries the store only as far as its NBT stays small enough to travel ({@code RECYCLE_BYTES}). Dropping
 * the rest at the core would lose it: an item entity keeps only a byte's count once saved, a stack of more than its
 * size is not a stack, and it despawns after five minutes. So nothing is dropped and nothing is split: the kinds that
 * did not
 * fit go into the team's put-aside store whole, and every nexus of the team moves them back into its own recycle store
 * ({@link #drainInto}) as far as that store has room ({@link RecycleStore#full}), the largest counts first, so the
 * nexus tile never grows past what its chunk can save. The members then take them out at the recycle bin or with
 * 取出 as usual. Server side only; a team is keyed by its UUID, a nexus without a team by {@link #NO_TEAM}.
 */
public class RecycleOverflow extends WorldSavedData {

    static final String NAME = "fluxecho_recycle";
    /** The key for a nexus that belongs to no team (no owner). */
    static final UUID NO_TEAM = UUID.nameUUIDFromBytes("[FluxEcho] no team".getBytes());

    private final Map<UUID, RecycleStore> teams = new LinkedHashMap<>();

    public RecycleOverflow(String name) {
        super(name);
    }

    /** The put-aside stores of the save, or null while the overworld is not loaded (nothing can be kept then). */
    static RecycleOverflow get() {
        World w = DimensionManager.getWorld(0);
        if (w == null || w.mapStorage == null) return null;
        RecycleOverflow d = (RecycleOverflow) w.mapStorage.loadData(RecycleOverflow.class, NAME);
        if (d == null) {
            d = new RecycleOverflow(NAME);
            w.mapStorage.setData(NAME, d);
        }
        return d;
    }

    private static UUID key(UUID team) {
        return team == null ? NO_TEAM : team;
    }

    /**
     * Puts entries {@code {K: kind, N: count}} aside for the team (merged with what it has put aside already).
     * Returns false when they cannot be kept (no overworld): the caller must then keep them some other way.
     */
    public static boolean put(UUID team, NBTTagList entries) {
        RecycleOverflow d = get();
        if (d == null) return false;
        d.add(team, entries);
        return true;
    }

    /**
     * Moves what the team put aside into a nexus's recycle store, as far as it has room; returns the kinds moved (0
     * when nothing is put aside, or no overworld).
     */
    public static int drainInto(UUID team, RecycleStore store) {
        RecycleOverflow d = get();
        return d == null ? 0 : d.drain(team, store);
    }

    // ---- the data itself (no world needed)

    /** Adds entries {@code {K, N}} to the team's put-aside store; broken entries are skipped. */
    void add(UUID team, NBTTagList entries) {
        if (entries == null || entries.tagCount() == 0) return;
        RecycleStore s = teams.computeIfAbsent(key(team), k -> new RecycleStore());
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound e = entries.getCompoundTagAt(i);
            if (e.hasKey("K", 10)) s.add(e.getCompoundTag("K"), e.getLong("N"));
        }
        if (s.isEmpty()) teams.remove(key(team));
        markDirty();
    }

    /** Moves the team's put-aside kinds into the store while it has room ({@link RecycleStore#moveInto}). */
    int drain(UUID team, RecycleStore into) {
        RecycleStore s = teams.get(key(team));
        if (s == null || into == null) return 0;
        int moved = RecycleStore.moveInto(s, into);
        if (s.isEmpty()) teams.remove(key(team));
        if (moved > 0) markDirty();
        return moved;
    }

    /** What the team has put aside (a live view; null when nothing). */
    RecycleStore of(UUID team) {
        return teams.get(key(team));
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        teams.clear();
        NBTTagList l = t.getTagList("Teams", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound e = l.getCompoundTagAt(i);
            UUID team;
            try {
                team = UUID.fromString(e.getString("T"));
            } catch (IllegalArgumentException x) {
                continue;
            }
            RecycleStore s = new RecycleStore();
            s.read(e.getTagList("L", 10));
            if (!s.isEmpty()) teams.put(team, s);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Iterator<Map.Entry<UUID, RecycleStore>> it = teams.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<UUID, RecycleStore> e = it.next();
            if (e.getValue()
                .isEmpty()) {
                it.remove();
                continue;
            }
            NBTTagCompound c = new NBTTagCompound();
            c.setString(
                "T",
                e.getKey()
                    .toString());
            c.setTag(
                "L",
                e.getValue()
                    .write());
            l.appendTag(c);
        }
        t.setTag("Teams", l);
    }
}
