package com.fluxecho.nexus;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.fluxecho.codex.ClientLedger;
import com.fluxecho.codex.EchoLedger;
import com.fluxecho.research.ResearchData;

/**
 * The team's Echo Codex entries as the nexus presses them into echo crystals: each entry rests for
 * {@code recordCooldownTicks} after use; the nexus takes the one that has rested longest, so a team that has done
 * more makes echo crystals faster.
 */
public final class Records {

    private Records() {}

    /** Entries in the team's codex, every category. */
    public static int count(UUID team) {
        if (team == null) return 0;
        NBTTagCompound snap = EchoLedger.get()
            .snapshot(team);
        int n = 0;
        for (Object o : snap.func_150296_c()) {
            if (ClientLedger.RESEARCH.equals(o)) continue;
            n += snap.getTagList((String) o, 8)
                .tagCount();
        }
        return n;
    }

    /** {category, key} of the entry to press next, or null while every entry rests. */
    public static String[] next(UUID team, long now, long cooldown) {
        if (team == null) return null;
        NBTTagCompound snap = EchoLedger.get()
            .snapshot(team);
        ResearchData data = ResearchData.get();
        String[] best = null;
        long bestAt = Long.MAX_VALUE;
        for (Object o : snap.func_150296_c()) {
            String cat = (String) o;
            NBTTagList keys = snap.getTagList(cat, 8);
            for (int i = 0; i < keys.tagCount(); i++) {
                String key = keys.getStringTagAt(i);
                long at = data.lastPressed(team, entry(cat, key));
                if (at != Long.MIN_VALUE && now - at < cooldown) continue;
                if (at < bestAt) {
                    bestAt = at;
                    best = new String[] { cat, key };
                }
            }
        }
        return best;
    }

    /** How many entries rest now. */
    public static int resting(UUID team, long now, long cooldown) {
        if (team == null) return 0;
        NBTTagCompound snap = EchoLedger.get()
            .snapshot(team);
        ResearchData data = ResearchData.get();
        int n = 0;
        for (Object o : snap.func_150296_c()) {
            String cat = (String) o;
            NBTTagList keys = snap.getTagList(cat, 8);
            for (int i = 0; i < keys.tagCount(); i++) {
                long at = data.lastPressed(team, entry(cat, keys.getStringTagAt(i)));
                if (at != Long.MIN_VALUE && now - at < cooldown) n++;
            }
        }
        return n;
    }

    public static String entry(String category, String key) {
        return category + ":" + key;
    }
}
