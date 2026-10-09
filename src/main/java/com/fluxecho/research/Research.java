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
        UPKEEP_1 = ResearchTree.UPKEEP_1, GRIT_YIELD = ResearchTree.GRIT_YIELD;

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
}
