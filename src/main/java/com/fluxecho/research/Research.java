package com.fluxecho.research;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.fluxecho.codex.EchoNet;
import com.fluxecho.logic.ResearchTree;

/**
 * Server side: what the teams have researched ({@link ResearchData}). Research belongs to a team; a nexus works with
 * everything the teams bound to it have done together ({@link #union}).
 */
public final class Research {

    public static final String ANCHOR = ResearchTree.ANCHOR, INNER_RING = ResearchTree.INNER_RING,
        ECHO_CRYSTAL = ResearchTree.ECHO_CRYSTAL, LIBRARY = ResearchTree.LIBRARY,
        LIBRARY_REACH = ResearchTree.LIBRARY_REACH, COMPUTE_1 = ResearchTree.COMPUTE_1,
        UPKEEP_1 = ResearchTree.UPKEEP_1, GRIT_YIELD = ResearchTree.GRIT_YIELD, ECO_DOME = ResearchTree.ECO_DOME,
        PHASE_2 = ResearchTree.PHASE_2, DOME = ResearchTree.DOME, TIDAL_WELL = ResearchTree.TIDAL_WELL,
        ARCANE_COURT = ResearchTree.ARCANE_COURT, BLOOD_COVENANT = ResearchTree.BLOOD_COVENANT,
        HUNTING_GROUND = ResearchTree.HUNTING_GROUND, MANA_FOUNTAIN = ResearchTree.MANA_FOUNTAIN;

    private Research() {}

    public static boolean teamHas(UUID team, String id) {
        return team != null && ResearchData.get()
            .has(team, id);
    }

    public static Set<String> done(UUID team) {
        return ResearchData.get()
            .done(team);
    }

    /** Everything the teams have done between them. */
    public static Set<String> union(Collection<UUID> teams) {
        Set<String> out = new LinkedHashSet<>();
        ResearchData d = ResearchData.get();
        for (UUID t : teams) out.addAll(d.done(t));
        return out;
    }

    /** Marks the node done for the team and tells its players. */
    public static boolean grant(UUID team, String id) {
        boolean added = ResearchData.get()
            .grant(team, id);
        if (added) EchoNet.ledgerChanged(team);
        return added;
    }

    /**
     * Marks every node that can be researched in this version done for the team (an operator's
     * {@code /fluxecho research all}; {@link ResearchTree#availableNow}: the greyed placeholders of later phases stay
     * open) and tells its players, as a finished research does; returns how many were new.
     */
    public static int grantAll(UUID team) {
        if (team == null) return 0;
        ResearchData d = ResearchData.get();
        int added = 0;
        for (ResearchTree.Node n : ResearchTree.availableNow()) if (d.grant(team, n.id)) added++;
        if (added > 0) EchoNet.ledgerChanged(team);
        return added;
    }

    /**
     * Clears every research of the team (an operator's {@code /fluxecho research reset}) and tells its players;
     * returns how many there were.
     */
    public static int reset(UUID team) {
        if (team == null) return 0;
        int n = ResearchData.get()
            .revokeAll(team);
        if (n > 0) EchoNet.ledgerChanged(team);
        return n;
    }
}
