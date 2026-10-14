package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which module kinds a campus offers to build now, and where each would stand (0.14.1, M11.1 §3): the one decision
 * behind both the campus's look for new jobs ({@code Campus.autoJobs}) and its module list ({@code Campus.modules}, the
 * 建造 page and the masterplan's signs), so the list can never say one thing while the offers do another.
 * <p>
 * Up to 0.14.0 a campus offered ring modules only while the inner ring had room by the nexus's phase (2 at phase I, 4
 * at II, at most 7), so a phase-II campus never offered more than four of the eight inner buildings although every
 * site stood empty. There is no such room any more: a kind is offered when its research is done, the nexus has grown
 * to the phase of both its research node and a site of its, the campus has the height or depth for it, the config lets
 * it be built, no member cancelled it lately, and one of its candidate sites ({@link #candidates}) is free. Kinds are
 * looked at in their order (registration order); a kind offered takes its site, so the kinds after it look past it.
 * <p>
 * A kind that is not offered says why (a lang key under {@code fluxecho.}): {@link #PHASE}, {@link #GROUND} when its
 * sites wait only for the campus job that lays their ground (0.15.0: the outer establish; {@link #GROUND_DECLINED}
 * while that job was cancelled lately, {@link #GROUND_WAITS} while it waits for the jobs before it), the campus's
 * height or depth ({@link Kind#noRoom}), {@link #OFF}, {@link #DECLINED}, {@link #LEGACY_HALL} when a docked 0.9.2
 * library hall is all that stands between it and a site (the campus then offers to retire the hall), {@link #TRIMMED}
 * when every site of its that is open reaches into ground a neighbouring campus holds for good (0.15.0: its inner
 * square, or an outer diagonal it took first), {@link #NO_SITE} when anything else stands in the way. What stands on a
 * site is the campus's to say ({@link Sites}); {@link #blocker} and {@link #siteBlocker} work it out from the boxes
 * the campus knows.
 * <p>
 * Of a kind's free sites the one taken first is the one that shares no ground with a neighbouring campus's outer
 * diagonal it has not claimed ({@link Sites#contested}), so a campus between neighbours placed at the old spacing does
 * not take the ground one of them may need while it has ground of its own; then the one that shares the least, then
 * the one the split takes the fewest cells of ({@link Sites#lost}), then the first in its order. So the kind adapts
 * instead of refusing (0.15.0, the standing rule that every researched building can be built): when no site of its is
 * whole, it is offered on one the split takes cells of, and the campus then claims that site, so the split gives the
 * whole of it to this campus from then on ({@link CampusSplit}). Every site of the inner city shares and loses
 * nothing, so there the first free site in order is taken, as before 0.15.0.
 */
public final class CityOffer {

    /** The prefix of the reasons, under {@code fluxecho.}. */
    public static final String WHY = "build.module.why.";
    /** The nexus has not grown to the phase of the kind's research or of any of its sites (0.12.0). */
    public static final String PHASE = WHY + "phase";
    /** The config turned the kind off (the Echo Archive's {@code archiveEnabled}). */
    public static final String OFF = WHY + "off";
    /** A member cancelled its job lately: it is offered again a day after. */
    public static final String DECLINED = WHY + "declined";
    /** A docked 0.9.2 library hall is all that stands in the way: 开始 on the retire job takes it down. */
    public static final String LEGACY_HALL = WHY + "legacy_hall";
    /** Something else stands on every site it may take (a module of another kind, a stray core, another team's). */
    public static final String NO_SITE = WHY + "no_site";
    /**
     * Its sites are open at the phase, but the campus job that lays their ground has not run yet (0.15.0: the outer
     * establish, {@link SiteFamily#ground}), which lays it once it is offered and started. The text gives no order:
     * the job may not be offered yet (the nexus does not stand, the campus has not looked since the research).
     */
    public static final String GROUND = WHY + "ground";
    /**
     * As {@link #GROUND}, but a member cancelled the job that lays the ground lately (0.15.0): the campus offers it
     * again a day after, so there is no job to press 开始 on until then.
     */
    public static final String GROUND_DECLINED = WHY + "ground_declined";
    /**
     * As {@link #GROUND}, but the job that lays the ground is not offered yet because jobs before it are still to run
     * (0.15.0: the establish, the forum, a refresh, a repair of the nexus, or a retire job of a 0.9.2 hall), so there
     * is no job of the ground to press 开始 on until they are done.
     */
    public static final String GROUND_WAITS = WHY + "ground_waits";
    /**
     * Every site of its open at the phase reaches into ground a neighbouring campus holds for good (0.15.0,
     * {@link CampusSplit}: its inner square, or an outer diagonal it took first): no site is left to adapt to.
     */
    public static final String TRIMMED = WHY + "trimmed";
    /**
     * Its sites are the sky ring's, open at the phase, but the Echo Dome Generator's pod does not stand floating
     * (0.16.0, {@link SiteFamily#POD_FLOATS}): it is moored (its release job takes the legs away), or there is no pod
     * at all (its dome job is offered by itself). The text says the pod must be released first.
     */
    public static final String POD = WHY + "pod";

    /** What {@link Sites#blocker} says of a free site, and of a site something other than a 0.9.2 hall stands on. */
    public static final int FREE = -1, OTHER = -2;

    /** One module kind as the campus offers it. */
    public static final class Kind {

        /** Its module key, and the research id that opens it. */
        public final String key, research;
        /** The family of sites it stands on. */
        public final SiteFamily family;
        /** The nexus phase its research node belongs to (1 when it has none). */
        public final int researchPhase;
        /** Whether the config lets the campus start new jobs for it. */
        public final boolean enabled;
        /** Why the campus has no room for it (its height or depth, a lang key under {@code fluxecho.}), or null. */
        public final String noRoom;
        private final int[] sites;

        /**
         * @param sites its sites in order of preference, every one of its family's
         * @throws IllegalArgumentException for a site outside the family
         */
        public Kind(String key, String research, SiteFamily family, int[] sites, int researchPhase, boolean enabled,
            String noRoom) {
            for (int s : sites) if (!family.hasSite(s)) throw new IllegalArgumentException(key + ": " + s);
            this.key = key;
            this.research = research;
            this.family = family;
            this.sites = sites.clone();
            this.researchPhase = researchPhase;
            this.enabled = enabled;
            this.noRoom = noRoom;
        }

        /** Its sites in order of preference (a copy). */
        public int[] sites() {
            return sites.clone();
        }

        /** Its first preference, or -1 for a kind without sites. */
        int first() {
            return sites.length > 0 ? sites[0] : -1;
        }
    }

    /** What stands on the campus's sites. */
    public interface Sites {

        /**
         * What stands in the way of the kind on the site: {@link #FREE}, {@link #OTHER}, or the inner ring slot (0 to
         * 7) of the docked 0.9.2 library hall that alone stands in the way.
         */
        int blocker(Kind kind, int site);

        /**
         * Whether the whole of the site's ground is the campus's own (0.15.0): of two free sites that share as much
         * ground with the neighbours' outer diagonals ({@link #contested}), an outer site that reaches across the
         * split into a neighbouring campus's ground ({@link CampusSplit}) is taken after a whole one. Every site is
         * whole unless the campus says otherwise.
         */
        default boolean whole(Kind kind, int site) {
            return true;
        }

        /**
         * How much of the site's ground the split gives to neighbouring campuses (0.15.0,
         * {@link CampusSplit#siteLoss}): 0 for a whole site, the cells it would take from them for a site the campus
         * may still claim, -1 for one it may not (a neighbour holds part of it for good). By default 0 for a
         * {@link #whole} site and -1 for any other.
         */
        default int lost(Kind kind, int site) {
            return whole(kind, site) ? 0 : -1;
        }

        /**
         * How many outer diagonals of neighbouring campuses, not claimed yet, the site shares ground with (0.15.0,
         * {@link CampusSplit#contested}): of the free sites a kind may take, one that shares ground with none is taken
         * first, so a campus does not take the site a neighbour may need when it has one no neighbour can use. A site
         * the split takes cells of is always contested (a cell is lost only on a neighbour's outer diagonal). 0 by
         * default.
         */
        default int contested(Kind kind, int site) {
            return 0;
        }
    }

    /** One kind and where it stands now. */
    public static final class Offer {

        public final Kind kind;
        /**
         * The site it is offered on, or would be built on next, or (with {@link #LEGACY_HALL}) the site the hall
         * stands on; -1 when none.
         */
        public final int site;
        /** Whether its research is done. */
        public final boolean researched;
        /** Why it is not offered (a lang key under {@code fluxecho.}); null when it is offered now. */
        public final String reason;
        /** The ring slot of the docked 0.9.2 hall in its way ({@link #LEGACY_HALL}), else -1. */
        public final int hall;

        Offer(Kind kind, int site, boolean researched, String reason, int hall) {
            this.kind = kind;
            this.site = site;
            this.researched = researched;
            this.reason = reason;
            this.hall = hall;
        }

        /** Whether the campus offers it now: researched, and nothing stops it. */
        public boolean offered() {
            return researched && reason == null;
        }

        @Override
        public String toString() {
            return kind.key + (offered() ? "@" + site
                : !researched ? " needs research"
                    : " " + reason + (site >= 0 ? "@" + site : "") + (hall >= 0 ? " hall " + hall : ""));
        }
    }

    private CityOffer() {}

    /**
     * The sites a kind may take at the phase, in order: its own preference list, then the rest of its family's sites
     * (so a kind whose favourite sites another kind took, or a player moved it off, still has its family's others),
     * only those open at the phase ({@link SiteFamily#opensAt}) on ground already laid ({@link SiteFamily#ground},
     * asked of {@code groundLaid}). Never one of the sky ring's berths (0.16.0, {@link SkyRing#berth}): those are the
     * orbit job's.
     */
    public static int[] candidates(Kind k, int phase, Predicate<String> groundLaid) {
        String ground = k.family.ground();
        if (ground != null && (groundLaid == null || !groundLaid.test(ground))) return new int[0];
        Set<Integer> order = new LinkedHashSet<>();
        for (int s : k.sites) order.add(s);
        for (int s : k.family.sites()) if (!SkyRing.berth(s)) order.add(s);
        List<Integer> open = new ArrayList<>();
        for (int s : order) if (SiteFamily.opensAt(s) <= phase) open.add(s);
        int[] out = new int[open.size()];
        for (int i = 0; i < out.length; i++) out[i] = open.get(i);
        return out;
    }

    /**
     * Looks at every kind in order (0.14.1): {@code standing} holds the keys of the kinds that already stand or have a
     * job (they are left out of the answer: the campus knows where those stand); every other kind gets an
     * {@link Offer}, those offered taking their sites from the kinds after them.
     *
     * @param researched whether a research id is done
     * @param declined   module key → the world tick until which it is not offered again
     * @param groundLaid whether the campus job that lays a family's ground ran ({@link SiteFamily#ground})
     */
    public static List<Offer> look(List<Kind> kinds, int phase, Predicate<String> researched, Set<String> standing,
        Sites sites, Map<String, Long> declined, long now, Predicate<String> groundLaid) {
        return look(kinds, phase, researched, standing, sites, declined, now, groundLaid, null);
    }

    /**
     * {@link #look(List, int, Predicate, Set, Sites, Map, long, Predicate)}, told as well whether the job that lays a
     * family's ground waits for the jobs before it ({@code groundWaits}, asked of {@link SiteFamily#ground}; null for
     * never): a kind whose sites wait for that ground then says {@link #GROUND_WAITS}.
     */
    public static List<Offer> look(List<Kind> kinds, int phase, Predicate<String> researched, Set<String> standing,
        Sites sites, Map<String, Long> declined, long now, Predicate<String> groundLaid,
        Predicate<String> groundWaits) {
        List<Offer> out = new ArrayList<>();
        Set<Integer> claimed = new HashSet<>();
        for (Kind k : kinds) {
            if (standing != null && standing.contains(k.key)) continue;
            int[] open = candidates(k, phase, groundLaid);
            if (!researched.test(k.research)) {
                int s = firstFree(k, open, claimed, sites);
                out.add(new Offer(k, s >= 0 ? s : k.first(), false, null, -1));
                continue;
            }
            String why = null;
            if (phase < k.researchPhase || candidates(k, phase, g -> true).length == 0) why = PHASE;
            else if (open.length == 0) why = groundReason(k.family.ground(), declined, now, groundWaits);
            else if (k.noRoom != null) why = k.noRoom;
            else if (!k.enabled) why = OFF;
            else if (declined(declined, k.key, now)) why = DECLINED;
            if (why != null) {
                // where it would go once nothing stops it: any of its sites when the phase or the ground holds it
                boolean any = PHASE.equals(why) || GROUND.equals(why)
                    || GROUND_DECLINED.equals(why)
                    || GROUND_WAITS.equals(why)
                    || POD.equals(why);
                int s = firstFree(k, any ? allSites(k) : open, claimed, sites);
                out.add(new Offer(k, s, true, why, -1));
                continue;
            }
            // the free site that shares the least ground with the neighbours' unclaimed outer diagonals, then the one
            // the split takes the fewest cells of, then the first in its order (0.15.0); every site of the inner city
            // shares none and loses none, so there it is the first free one, as before
            int hallSite = -1, hall = -1, site = -1;
            long best = Long.MAX_VALUE;
            boolean anyClaimable = false;
            for (int s : open) {
                int lost = sites.lost(k, s);
                if (lost < 0) continue;
                anyClaimable = true;
                if (claimed.contains(s)) continue;
                int b = sites.blocker(k, s);
                if (b == FREE) {
                    long rank = rank(sites, k, s, lost);
                    if (rank < best) {
                        best = rank;
                        site = s;
                    }
                    if (rank == 0) break;
                    continue;
                }
                if (lost == 0 && b >= 0 && hall < 0) {
                    hall = b;
                    hallSite = s;
                }
            }
            if (site >= 0) {
                claimed.add(site);
                out.add(new Offer(k, site, true, null, -1));
            } else if (hall >= 0) out.add(new Offer(k, hallSite, true, LEGACY_HALL, hall));
            else if (!anyClaimable) out.add(new Offer(k, -1, true, TRIMMED, -1));
            else out.add(new Offer(k, -1, true, NO_SITE, -1));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The next free candidate of the kind from {@code from} in direction {@code dir} (◀ ▶ on the 建造 page, before the
     * job touched anything), cycling through its candidates at the phase; from a site that is no candidate, the first
     * free one. -1 when no other is free. Of the free ones it goes to those that share the least ground with the
     * neighbours' unclaimed outer diagonals ({@link Sites#contested}), and of those to the ones the split takes the
     * fewest cells of ({@link Sites#lost}), the next of them in the direction; one a neighbour holds part of for good
     * never.
     */
    public static int move(Kind k, int from, int dir, int phase, Predicate<String> groundLaid, Sites sites) {
        int[] c = candidates(k, phase, groundLaid);
        int n = c.length, start = -1;
        for (int i = 0; i < n; i++) if (c[i] == from) start = i;
        int site = -1;
        long best = Long.MAX_VALUE;
        for (int step = 1; step <= n; step++) {
            int s = c[start < 0 ? step - 1 : Math.floorMod(start + (dir < 0 ? -step : step), n)];
            if (s == from || sites.blocker(k, s) != FREE) continue;
            int lost = sites.lost(k, s);
            if (lost < 0) continue;
            long rank = rank(sites, k, s, lost);
            if (rank == 0) return s;
            if (rank < best) {
                best = rank;
                site = s;
            }
        }
        return site;
    }

    /**
     * How a free site the kind may claim ranks (0.15.0, the lower the better): by how many of the neighbours' unclaimed
     * outer diagonals it shares ground with ({@link Sites#contested}), then by how many of its cells the split takes
     * ({@link Sites#lost}, at least 0). 0 for a site no neighbour may want, which every site of the inner city is.
     */
    private static long rank(Sites sites, Kind k, int site, int lost) {
        int contested = sites.contested(k, site);
        return ((long) Math.max(0, contested) << 32) | Math.max(0, lost);
    }

    /**
     * Why a kind whose sites wait for their family's ground is not offered (0.15.0): the job that lays it was cancelled
     * lately ({@link #GROUND_DECLINED}), waits for the jobs before it ({@link #GROUND_WAITS}), or is to be started or
     * offered ({@link #GROUND}); the sky ring's (0.16.0) because the pod does not float yet ({@link #POD}).
     */
    private static String groundReason(String ground, Map<String, Long> declined, long now,
        Predicate<String> groundWaits) {
        if (SiteFamily.POD_FLOATS.equals(ground)) return POD;
        if (declined(declined, ground, now)) return GROUND_DECLINED;
        if (ground != null && groundWaits != null && groundWaits.test(ground)) return GROUND_WAITS;
        return GROUND;
    }

    /**
     * A module standing near the nexus on no site the campus recorded (0.9.2's inner ring, a stray core): the keep-out
     * box {x0, z0, x1, z1} the campus jobs leave round it (null for one too far out to matter), the column of the
     * centre of its foundation, and the ring slot it docks on when it is a docked 0.9.2 library hall the campus may
     * retire (-1 for any other).
     */
    public static final class Stray {

        final int[] keepOut;
        final int x, z, hall;

        public Stray(int[] keepOut, int x, int z, int hall) {
            this.keepOut = keepOut == null ? null : keepOut.clone();
            this.x = x;
            this.z = z;
            this.hall = hall;
        }
    }

    /**
     * What stands in the way of a ground module whose footprint (with its one-cell margin) is {@code footprint}
     * {x0, z0, x1, z1}: {@link #OTHER} when a keep-out box of a recorded site's module overlaps it, or a stray module
     * that is no docked 0.9.2 hall does (its keep-out box overlaps it or its centre lies in it); else the slot of the
     * first docked 0.9.2 hall that does; else {@link #FREE}.
     */
    public static int blocker(int[] footprint, List<int[]> keepOut, List<Stray> strays) {
        for (int[] k : keepOut) if (overlap(footprint, k)) return OTHER;
        int hall = FREE;
        for (Stray s : strays) {
            boolean in = s.keepOut != null && overlap(footprint, s.keepOut)
                || s.x >= Math.min(footprint[0], footprint[2]) && s.x <= Math.max(footprint[0], footprint[2])
                    && s.z >= Math.min(footprint[1], footprint[3])
                    && s.z <= Math.max(footprint[1], footprint[3]);
            if (!in) continue;
            if (s.hall < 0) return OTHER;
            if (hall == FREE) hall = s.hall;
        }
        return hall;
    }

    /**
     * What stands in the way of a module whose site is the world columns {@code cells} {x, z} (0.15.0, an outer
     * diagonal: its apron and allée, {@link CampusSplit#siteCells}), cell for cell rather than by the site's square,
     * whose corners reach past its octagon to where a neighbour's site may lie without the two sharing a cell:
     * {@link #OTHER} when a keep-out box of a recorded site's module holds one of its cells, or a stray module that is
     * no docked 0.9.2 hall does (its keep-out box holds one, or its centre is one); else the slot of the first docked
     * 0.9.2 hall that does; else {@link #FREE}.
     */
    public static int siteBlocker(List<int[]> cells, List<int[]> keepOut, List<Stray> strays) {
        if (cells == null || cells.isEmpty()) return FREE;
        int[] bounds = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        Set<Long> at = new HashSet<>();
        for (int[] c : cells) {
            bounds[0] = Math.min(bounds[0], c[0]);
            bounds[1] = Math.min(bounds[1], c[1]);
            bounds[2] = Math.max(bounds[2], c[0]);
            bounds[3] = Math.max(bounds[3], c[1]);
            at.add(CampusPlan.key(c[0], c[1]));
        }
        if (keepOut != null) for (int[] k : keepOut) if (holdsAny(cells, bounds, k)) return OTHER;
        int hall = FREE;
        if (strays != null) for (Stray s : strays) {
            boolean in = s.keepOut != null && holdsAny(cells, bounds, s.keepOut)
                || at.contains(CampusPlan.key(s.x, s.z));
            if (!in) continue;
            if (s.hall < 0) return OTHER;
            if (hall == FREE) hall = s.hall;
        }
        return hall;
    }

    /** Whether the box {x0, z0, x1, z1, ...} holds one of the columns, whose bounding box is {@code bounds}. */
    private static boolean holdsAny(List<int[]> cells, int[] bounds, int[] box) {
        if (!overlap(bounds, box)) return false;
        int x0 = Math.min(box[0], box[2]), x1 = Math.max(box[0], box[2]), z0 = Math.min(box[1], box[3]),
            z1 = Math.max(box[1], box[3]);
        for (int[] c : cells) if (c[0] >= x0 && c[0] <= x1 && c[1] >= z0 && c[1] <= z1) return true;
        return false;
    }

    /**
     * Whether two boxes {x0, z0, x1, z1} overlap (a height-ranged keep-out box {x0, z0, x1, z1, yFrom, yTo} by its
     * columns).
     */
    public static boolean overlap(int[] a, int[] b) {
        int ax0 = Math.min(a[0], a[2]), ax1 = Math.max(a[0], a[2]), az0 = Math.min(a[1], a[3]),
            az1 = Math.max(a[1], a[3]);
        int bx0 = Math.min(b[0], b[2]), bx1 = Math.max(b[0], b[2]), bz0 = Math.min(b[1], b[3]),
            bz1 = Math.max(b[1], b[3]);
        return ax0 <= bx1 && bx0 <= ax1 && az0 <= bz1 && bz0 <= az1;
    }

    private static boolean declined(Map<String, Long> declined, String key, long now) {
        Long until = declined == null ? null : declined.get(key);
        return until != null && now < until;
    }

    /** Its preference list, then the rest of its family's sites (never a berth), whatever the phase. */
    private static int[] allSites(Kind k) {
        Set<Integer> order = new LinkedHashSet<>();
        for (int s : k.sites) order.add(s);
        for (int s : k.family.sites()) if (!SkyRing.berth(s)) order.add(s);
        int[] out = new int[order.size()];
        int i = 0;
        for (int s : order) out[i++] = s;
        return out;
    }

    /**
     * Of the sites no kind took in this look that nothing stands on and that the campus may claim, the one that ranks
     * first ({@link #rank}: the least ground shared with the neighbours' unclaimed outer diagonals, then the fewest
     * cells the split takes, then the first in order); -1 when there is none.
     */
    private static int firstFree(Kind k, int[] candidates, Set<Integer> claimed, Sites sites) {
        int site = -1;
        long best = Long.MAX_VALUE;
        for (int s : candidates) {
            if (claimed.contains(s) || sites.blocker(k, s) != FREE) continue;
            int lost = sites.lost(k, s);
            if (lost < 0) continue;
            long rank = rank(sites, k, s, lost);
            if (rank == 0) return s;
            if (rank < best) {
                best = rank;
                site = s;
            }
        }
        return site;
    }
}
