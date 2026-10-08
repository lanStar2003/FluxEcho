package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Which installed quest files are left over from an older version of a line, inside the line's own folder. */
public final class QuestPlan {

    private QuestPlan() {}

    /**
     * @param dir     one of the line's own folders, relative to DefaultQuests ({@code "Quests/LazyAE-..."})
     * @param present file names found in that folder
     * @param shipped every file the line ships, relative to DefaultQuests
     * @return paths (relative to DefaultQuests) of the json files in {@code dir} that the line no longer ships
     */
    public static List<String> stale(String dir, Collection<String> present, Collection<String> shipped) {
        Set<String> keep = new HashSet<>(shipped);
        List<String> out = new ArrayList<>();
        for (String name : present) {
            String path = dir + "/" + name;
            if (name.endsWith(".json") && !keep.contains(path)) out.add(path);
        }
        out.sort(null);
        return out;
    }
}
