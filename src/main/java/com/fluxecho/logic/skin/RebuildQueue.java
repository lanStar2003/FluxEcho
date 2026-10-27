package com.fluxecho.logic.skin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The client's throttle on rebuilding buildings for the box table (style §3.9.10, refactor §5.6): a building is rebuilt
 * at most once in 10 seconds (requests in between merge into one), and the city at most four boxes a second, first
 * asked first served; a building still waiting its 10 seconds does not hold up the ones behind it. Times are client
 * ticks.
 */
public final class RebuildQueue {

    /** The fewest ticks between two rebuilds of one building. */
    public static final int PER_BUILDING = 200;
    /** The most boxes rebuilt in any second (20 ticks) for the whole city. */
    public static final int PER_SECOND = 4;
    private static final int SECOND = 20;

    private final Set<Long> waiting = new LinkedHashSet<>();
    private final Map<Long, Long> last = new HashMap<>();
    private final Deque<Long> recent = new ArrayDeque<>();

    /** Asks for a rebuild of a building; a building already waiting stays where it is. */
    public void request(long id, long now) {
        waiting.add(id);
    }

    /** The buildings to rebuild now, in the order they were asked for; they leave the queue. */
    public List<Long> poll(long now) {
        while (!recent.isEmpty() && recent.peekFirst() <= now - SECOND) recent.pollFirst();
        int budget = PER_SECOND - recent.size();
        if (budget <= 0 || waiting.isEmpty()) return Collections.emptyList();
        List<Long> out = new ArrayList<>();
        for (Iterator<Long> it = waiting.iterator(); it.hasNext() && budget > 0;) {
            long id = it.next();
            Long at = last.get(id);
            if (at != null && now - at < PER_BUILDING) continue;
            it.remove();
            last.put(id, now);
            recent.addLast(now);
            out.add(id);
            budget--;
        }
        return out;
    }

    /** How many buildings wait. */
    public int size() {
        return waiting.size();
    }

    /** A building that is gone: out of the queue, its last rebuild forgotten. */
    public void forget(long id) {
        waiting.remove(id);
        last.remove(id);
    }

    /** Everything forgotten (a new world). */
    public void clear() {
        waiting.clear();
        last.clear();
        recent.clear();
    }
}
