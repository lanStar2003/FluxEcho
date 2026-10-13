package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.fluxecho.Config;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.city.CityModule;
import com.fluxecho.library.LibraryModule;

/**
 * What a campus offers to build ({@link CityOffer}, 0.14.1), with the real module specs in their registration order
 * and the real site geometry (footprints, the keep-out boxes of the modules standing, a 0.9.2 hall on its old ring
 * slot): every researched building is offered as soon as a site of its is free, whatever the ring's room was. Up to
 * 0.14.0 a phase-II campus offered ring modules only while fewer than {@code min(RingSlots.open(2), 7)} = 4 stood or
 * waited, so the user's campus (the Archive, the Eco Dome and two pavilions on the ring) was offered nothing more: the
 * hunting ground, the mana fountain, the gate and the vault said "the ring is full" with four sites empty.
 */
class CityOfferTest {

    private static final int CX = 100, Y0 = 64, CZ = -200;
    private static final int[][] FACINGS = { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 } };
    /** The research ids of 0.13.0's buildings, and all of 0.14.0's. */
    private static final List<String> R013 = Arrays.asList(
        "library",
        "eco_dome",
        "dome",
        "tidal_well",
        "arcane_court",
        "blood_covenant",
        "hunting_ground",
        "mana_fountain"), R014 = new ArrayList<>(R013);

    static {
        R014.add("resonance_gate");
        R014.add("dual_foil_vault");
    }

    /** The specs in registration order (the library's first, then the city's). */
    private static final List<ModuleSpec> SPECS = Arrays.asList(
        LibraryModule.spec(),
        CityModule.ecoDome(),
        CityModule.domeGenerator(),
        CityModule.root(),
        CityModule.arcaneCourt(),
        CityModule.bloodCovenant(),
        CityModule.huntingGround(),
        CityModule.manaFountain(),
        CityModule.resonanceGate(),
        CityModule.dualFoilVault());

    private static ModuleSpec spec(String key) {
        for (ModuleSpec s : SPECS) if (s.key.equals(key)) return s;
        throw new IllegalArgumentException(key);
    }

    /** The keep-out boxes of a module standing on a site, by key, site, facing and seed (worked out once). */
    private static final Map<String, List<int[]>> BOXES = new HashMap<>();

    /**
     * A campus as the offer sees it: the modules standing (recorded sites), the jobs waiting, the 0.9.2 halls docked
     * on old ring slots, the research, the cancelled kinds; the nexus at (CX, Y0, CZ) facing {@code (fx, fz)}.
     */
    private static final class Model implements CityOffer.Sites {

        final int fx, fz;
        final long seed;
        final CampusPlan plan;
        final Map<Integer, String> built = new TreeMap<>(), jobs = new TreeMap<>();
        final Set<Integer> halls = new TreeSet<>();
        final Set<String> research = new HashSet<>();
        final Map<String, Long> declined = new HashMap<>();
        int phase = 2;
        long now;
        /** How far the docked 0.9.2 halls stand off their slot points, {dx, dz} (a hall docks up to 3 off). */
        int[] shift = { 0, 0 };

        Model(int[] facing, long seed) {
            fx = facing[0];
            fz = facing[1];
            this.seed = seed;
            plan = new CampusPlan(seed);
        }

        CityOffer.Kind kind(String key) {
            return spec(key).kind(Y0);
        }

        List<CityOffer.Offer> look() {
            List<CityOffer.Kind> kinds = new ArrayList<>();
            for (ModuleSpec s : SPECS) kinds.add(s.kind(Y0));
            Set<String> standing = new HashSet<>(built.values());
            standing.addAll(jobs.values());
            return CityOffer.look(kinds, phase, research::contains, standing, this, declined, now, g -> false);
        }

        /** One look: every kind offered gets its job; returns them as "key@site". */
        List<String> offerAll() {
            List<String> told = new ArrayList<>();
            Set<Integer> sites = new HashSet<>();
            for (CityOffer.Offer o : look()) {
                if (!o.offered()) continue;
                assertTrue(sites.add(o.site), "two offers on " + o.site);
                assertFalse(built.containsKey(o.site) || jobs.containsKey(o.site), "an offer on a taken site " + o);
                jobs.put(o.site, o.kind.key);
                told.add(o.kind.key + "@" + o.site);
            }
            return told;
        }

        void buildAll() {
            built.putAll(jobs);
            jobs.clear();
        }

        CityOffer.Offer offerOf(String key) {
            for (CityOffer.Offer o : look()) if (o.kind.key.equals(key)) return o;
            return null;
        }

        int[] hallCentre(int slot) {
            int[] o = RingSlots.offset(slot, Config.innerRadius, fx, fz);
            return new int[] { CX + o[0] + shift[0], Y0, CZ + o[1] + shift[1] };
        }

        @Override
        public int blocker(CityOffer.Kind kind, int site) {
            if (built.containsKey(site) || jobs.containsKey(site)) return CityOffer.OTHER;
            ModuleSpec s = spec(kind.key);
            if (s.axis()) return CityOffer.FREE;
            List<int[]> boxes = new ArrayList<>();
            for (Map.Entry<Integer, String> e : built.entrySet()) {
                ModuleSpec b = spec(e.getValue());
                String k = b.key + "@" + e.getKey() + ":" + fx + "," + fz + ":" + seed;
                boxes.addAll(BOXES.computeIfAbsent(k, x -> b.keepOut(plan, e.getKey(), CX, Y0, CZ, fx, fz)));
            }
            List<CityOffer.Stray> strays = new ArrayList<>();
            for (int slot : halls) {
                int[] c = hallCentre(slot);
                strays.add(new CityOffer.Stray(BuildPlan.moduleKeepOut(c), c[0], c[2], slot));
            }
            return CityOffer.blocker(s.footprint(site, CX, CZ, fx, fz, 1), boxes, strays);
        }
    }

    private static Model fresh(int phase, List<String> research) {
        Model m = new Model(FACINGS[0], 42L);
        m.phase = phase;
        m.research.addAll(research);
        return m;
    }

    @Test
    void everyResearchedBuildingIsOfferedAtPhaseTwoOnItsFirstPreference() {
        Model m = fresh(2, R014);
        assertEquals(
            Arrays.asList(
                "library@4",
                "eco_dome@2",
                "dome_generator@8",
                "root@9",
                "arcane_court@7",
                "blood_covenant@5",
                "hunting_ground@3",
                "mana_fountain@1",
                "resonance_gate@0",
                "dual_foil_vault@6"),
            m.offerAll(),
            "all ten in one look, ring room or not");
        for (String told : new ArrayList<>(m.jobs.values())) {
            ModuleSpec s = spec(told);
            int site = -1;
            for (Map.Entry<Integer, String> e : m.jobs.entrySet()) if (e.getValue()
                .equals(told)) site = e.getKey();
            assertEquals(s.sites[0], site, told + " on its first preference");
        }
        m.buildAll();
        assertTrue(
            m.offerAll()
                .isEmpty());
        assertDisjoint(m);
    }

    /**
     * The user's campus (2026-10-13, 0.13.0, from its save: Grown 2, Sites {2 eco_dome, 4 library, 5 blood_covenant,
     * 7 arcane_court, 8 dome_generator, 9 root}, nothing queued or declined): the hunting ground and the mana fountain
     * are offered at once, and with 0.14.0's research the gate and the vault too.
     */
    @Test
    void theUsersCampusIsOfferedItsMissingBuildings() {
        Model m = fresh(2, R013);
        m.built.put(2, "eco_dome");
        m.built.put(4, "library");
        m.built.put(5, "blood_covenant");
        m.built.put(7, "arcane_court");
        m.built.put(8, "dome_generator");
        m.built.put(9, "root");
        // what 0.14.0 counted against its room of four: the four ring modules standing
        assertEquals(4, SiteFamily.ringCount(2, 4, 5, 7, 8, 9));
        assertEquals(4, Math.min(RingSlots.open(2), 7), "0.14.0's room at phase II");
        assertEquals(8, SiteFamily.openRingSites(2), "0.14.1's");
        assertEquals(Arrays.asList("hunting_ground@3", "mana_fountain@1"), m.offerAll());
        assertNull(m.offerOf("resonance_gate").reason);
        assertFalse(
            m.offerOf("resonance_gate").researched,
            "the gate and the vault wait for their research, not for room");
        m.buildAll();
        m.research.addAll(R014);
        assertEquals(Arrays.asList("resonance_gate@0", "dual_foil_vault@6"), m.offerAll());

        // and with both researched at once
        Model all = fresh(2, R014);
        all.built.putAll(m.built);
        for (int s : new int[] { 0, 1, 3, 6 }) all.built.remove(s);
        assertEquals(
            Arrays.asList("hunting_ground@3", "mana_fountain@1", "resonance_gate@0", "dual_foil_vault@6"),
            all.offerAll());
    }

    /**
     * A docked 0.9.2 library hall on the old inner ring stands on a city site (on slots 1 and 7 its keep-out also
     * reaches the gate's): the buildings that need that site say {@link CityOffer#LEGACY_HALL} with the hall's slot,
     * the campus offers the hall's retire job, and once it is down they are offered there.
     */
    @Test
    void aLegacyHallIsNamedAndItsSiteOfferedOnceItRetires() {
        Model four = fresh(2, R014);
        four.halls.add(4);
        assertEquals(
            Arrays.asList(
                "library@2",
                "eco_dome@6",
                "dome_generator@8",
                "root@9",
                "arcane_court@7",
                "blood_covenant@5",
                "hunting_ground@3",
                "mana_fountain@1",
                "resonance_gate@0"),
            four.offerAll());
        CityOffer.Offer vault = four.offerOf("dual_foil_vault");
        assertEquals(CityOffer.LEGACY_HALL, vault.reason);
        assertEquals(4, vault.hall);
        assertEquals(4, vault.site, "where the hall stands");
        four.buildAll();
        four.halls.clear();
        assertEquals(Collections.singletonList("dual_foil_vault@4"), four.offerAll());
        four.buildAll();
        assertDisjoint(four);

        Model one = fresh(2, R014);
        one.halls.add(1);
        List<String> told = one.offerAll();
        assertEquals(8, told.size(), told.toString());
        for (String k : new String[] { "mana_fountain", "resonance_gate" }) {
            CityOffer.Offer o = one.offerOf(k);
            assertEquals(CityOffer.LEGACY_HALL, o.reason, k);
            assertEquals(1, o.hall, k);
        }
        assertEquals(1, one.offerOf("mana_fountain").site);
        assertEquals(0, one.offerOf("resonance_gate").site);
        one.buildAll();
        one.halls.clear();
        assertEquals(Arrays.asList("mana_fountain@1", "resonance_gate@0"), one.offerAll());

        // a stray module that is no docked 0.9.2 hall (another team's, a lone core) is something else on the site
        int[] c = one.hallCentre(4);
        int[] foot = spec("dual_foil_vault").footprint(4, CX, CZ, 1, 0, 1);
        assertEquals(
            CityOffer.OTHER,
            CityOffer.blocker(
                foot,
                Collections.emptyList(),
                Arrays.asList(
                    new CityOffer.Stray(BuildPlan.moduleKeepOut(c), c[0], c[2], 4),
                    new CityOffer.Stray(null, c[0], c[2], -1))));
        assertEquals(
            4,
            CityOffer.blocker(
                foot,
                Collections.emptyList(),
                Collections.singletonList(new CityOffer.Stray(BuildPlan.moduleKeepOut(c), c[0], c[2], 4))));
        assertEquals(CityOffer.FREE, CityOffer.blocker(foot, Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    void aCancelledBuildingWaitsADayThenReturns() {
        Model m = fresh(2, R014);
        m.offerAll();
        m.jobs.values()
            .remove("dual_foil_vault");
        m.declined.put("dual_foil_vault", m.now + 24000);
        m.buildAll();
        assertTrue(
            m.offerAll()
                .isEmpty());
        CityOffer.Offer o = m.offerOf("dual_foil_vault");
        assertEquals(CityOffer.DECLINED, o.reason);
        assertEquals(6, o.site, "where it would go");
        for (CityOffer.Offer other : m.look()) assertEquals("dual_foil_vault", other.kind.key, "only it waits");
        m.now += 24000;
        assertEquals(Collections.singletonList("dual_foil_vault@6"), m.offerAll());
    }

    /**
     * At phase I the halls are offered and nothing else: the pavilions, the gate and the axis families open at phase
     * II, and the Dual-foil Vault, a hall, belongs to a phase-II research node (an operator's research of everything
     * must not bring it in early).
     */
    @Test
    void phaseOneOffersTheHallsAlone() {
        Model m = fresh(1, R014);
        assertEquals(Arrays.asList("library@4", "eco_dome@2"), m.offerAll());
        for (CityOffer.Offer o : m.look()) assertEquals(CityOffer.PHASE, o.reason, o.kind.key);
        assertEquals(6, m.offerOf("dual_foil_vault").site, "where it will go");
        assertEquals(8, m.offerOf("dome_generator").site);
        assertEquals(3, SiteFamily.openRingSites(1));
        // the height and the config still say no
        CityOffer.Kind high = new CityOffer.Kind(
            "dome_generator",
            "dome",
            SiteFamily.ZENITH,
            new int[] { 8 },
            2,
            true,
            "build.module.why.height");
        CityOffer.Kind off = new CityOffer.Kind("library", "library", SiteFamily.HALL, new int[] { 4 }, 1, false, null);
        List<CityOffer.Offer> l = CityOffer.look(
            Arrays.asList(high, off),
            2,
            r -> true,
            Collections.emptySet(),
            (k, s) -> CityOffer.FREE,
            null,
            0,
            null);
        assertEquals("build.module.why.height", l.get(0).reason);
        assertEquals(CityOffer.OFF, l.get(1).reason);
        assertFalse(
            l.get(1)
                .offered());
    }

    /**
     * A thousand campuses, each researching the ten buildings in a random order while members move projections round
     * (◀ ▶, {@link CityOffer#move}) and start some of them between looks, every facing: each ends with all ten built,
     * every one on a site of its own family, no two on one site and no two ground footprints touching.
     */
    @Test
    void anyResearchOrderAndAnyMovesPlaceEveryBuilding() {
        for (int run = 0; run < 1000; run++) {
            Random r = new Random(run);
            Model m = new Model(FACINGS[run % 4], run % 4);
            List<String> order = new ArrayList<>(R014);
            Collections.shuffle(order, r);
            for (String id : order) {
                m.research.add(id);
                m.offerAll();
                for (Integer site : new ArrayList<>(m.jobs.keySet())) {
                    if (r.nextInt(3) != 0) continue;
                    String key = m.jobs.get(site);
                    int to = CityOffer.move(m.kind(key), site, r.nextBoolean() ? 1 : -1, m.phase, g -> false, m);
                    if (to < 0) continue;
                    assertTrue(spec(key).hasSite(to), key + " moved to " + to);
                    m.jobs.remove(site);
                    m.jobs.put(to, key);
                }
                for (Integer site : new ArrayList<>(m.jobs.keySet()))
                    if (r.nextBoolean()) m.built.put(site, m.jobs.remove(site));
            }
            for (int look = 0; look < 3; look++) {
                m.offerAll();
                m.buildAll();
            }
            assertEquals(10, m.built.size(), "run " + run + ": " + m.built);
            assertEquals(10, new HashSet<>(m.built.values()).size(), "run " + run);
            for (Map.Entry<Integer, String> e : m.built.entrySet())
                assertTrue(spec(e.getValue()).hasSite(e.getKey()), "run " + run + ": " + e);
            assertDisjoint(m);
        }
    }

    /** No two ground modules of the campus touch: their footprints (no margin) are pairwise disjoint. */
    private static void assertDisjoint(Model m) {
        List<int[]> feet = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Map.Entry<Integer, String> e : m.built.entrySet()) {
            ModuleSpec s = spec(e.getValue());
            if (s.axis()) continue;
            feet.add(s.footprint(e.getKey(), CX, CZ, m.fx, m.fz, 0));
            names.add(e.getValue() + "@" + e.getKey());
        }
        for (int i = 0; i < feet.size(); i++) for (int j = i + 1; j < feet.size(); j++)
            assertFalse(CityOffer.overlap(feet.get(i), feet.get(j)), names.get(i) + " and " + names.get(j));
    }

    /**
     * A kind's candidates: its preference list, then its family's other sites, only those open at the phase on laid
     * ground; the families further out wait for their ground and open site by site.
     */
    @Test
    void theCandidatesAreThePreferencesThenTheFamily() {
        CityOffer.Kind eco = spec("eco_dome").kind(Y0);
        assertArrayEquals(new int[] { 2, 6, 4 }, CityOffer.candidates(eco, 1, null));
        CityOffer.Kind picky = new CityOffer.Kind("x", "x", SiteFamily.HALL, new int[] { 6 }, 1, true, null);
        assertArrayEquals(new int[] { 6, 4, 2 }, CityOffer.candidates(picky, 1, null));
        assertArrayEquals(new int[0], CityOffer.candidates(spec("arcane_court").kind(Y0), 1, null));
        assertArrayEquals(new int[] { 7, 1, 5, 3 }, CityOffer.candidates(spec("arcane_court").kind(Y0), 2, null));
        CityOffer.Kind outer = new CityOffer.Kind("o", "o", SiteFamily.OUTER_DIAG, new int[] { 13 }, 3, true, null);
        assertArrayEquals(new int[0], CityOffer.candidates(outer, 5, g -> false), "its ground is not laid");
        assertArrayEquals(
            new int[] { 13, 11, 15, 17 },
            CityOffer.candidates(outer, 3, SiteFamily.OUTER_GROUND::equals));
        CityOffer.Kind flank = new CityOffer.Kind("f", "f", SiteFamily.OUTER_FLANK, new int[0], 4, true, null);
        assertArrayEquals(new int[] { 20, 22, 24, 26 }, CityOffer.candidates(flank, 4, g -> true));
        assertEquals(8, CityOffer.candidates(flank, 5, g -> true).length);
        assertThrows(
            IllegalArgumentException.class,
            () -> new CityOffer.Kind("y", "y", SiteFamily.HALL, new int[] { 1 }, 1, true, null));

        // ◀ ▶ walk the free candidates round, past a taken one, and never land on the site they start from
        Model m = fresh(2, R014);
        m.built.put(6, "dual_foil_vault");
        CityOffer.Kind lib = m.kind("library");
        assertEquals(2, CityOffer.move(lib, 4, 1, 2, null, m));
        assertEquals(2, CityOffer.move(lib, 4, -1, 2, null, m), "6 is taken: round to 2 the other way");
        assertEquals(4, CityOffer.move(lib, 2, 1, 2, null, m));
        m.built.put(2, "eco_dome");
        assertEquals(-1, CityOffer.move(lib, 4, 1, 2, null, m), "nowhere else free");
    }

    /**
     * The ground a retire job works ({@link BuildPlan#retireBox}, 0.14.1). A 0.9.2 hall docks up to
     * {@link RingSlots#TOLERANCE} blocks off its slot point, so a neighbour may be offered and built next to it first
     * (its footprint misses the hall's keep-out box) with its apron in the ring that keep-out box adds round the hall.
     * The box comes back to the hall's bounds on that side: the job never works, nor marks blocked, a cell of the
     * neighbour's ground, and every column of the hall still comes down. Before, the box was the hall's keep-out box
     * alone, and next to a hall on slot 3 standing 3 blocks off it took in the Echo Archive's apron on site 4.
     */
    @Test
    void aRetireJobKeepsOffItsNeighboursGround() {
        Model m = fresh(2, R014);
        m.shift = new int[] { -3, -1 };
        m.halls.add(3);
        m.offerAll();
        m.buildAll();
        assertEquals("library", m.built.get(4), "the Archive built on 4 next to the hall");
        int[] c = m.hallCentre(3), bounds = hallBounds(c);
        List<int[]> ground = ground(m);
        int[] k = BuildPlan.moduleKeepOut(c);
        assertTrue(meets(ground, k), "the hall's keep-out box alone takes in the Archive's apron");
        int[] box = BuildPlan.retireBox(c, bounds, ground);
        BuildPlan.Plan p = BuildPlan.retire(m.plan, BuildPlan.retireKey(3), CX, Y0, CZ, m.fx, m.fz, box);
        Set<Long> cleared = new HashSet<>();
        for (BuildPlan.Column col : p.clear) {
            assertFalse(meets(ground, new int[] { col.x, col.z, col.x, col.z }), "it clears " + col.x + "," + col.z);
            cleared.add(column(col.x, col.z));
        }
        for (BuildPlan.Step st : p.steps)
            assertFalse(meets(ground, new int[] { st.x, st.z, st.x, st.z }), "it lays " + st.x + "," + st.z);
        for (int x = bounds[0]; x <= bounds[3]; x++) for (int z = bounds[2]; z <= bounds[5]; z++)
            assertTrue(cleared.contains(column(x, z)), "the hall's column " + x + "," + z);

        // every shift a docked hall may stand at, on every slot, for every facing: the box takes in the hall's bounds
        // and no column of its neighbours' ground or plans, and the hall's bounds meet none
        int cases = 0;
        for (int[] f : FACINGS) for (int slot = 0; slot < RingSlots.SLOTS; slot++)
            for (int dx = -RingSlots.TOLERANCE; dx <= RingSlots.TOLERANCE; dx++)
                for (int dz = -RingSlots.TOLERANCE; dz <= RingSlots.TOLERANCE; dz++) {
                    Model s = new Model(f, 42L);
                    s.research.addAll(R014);
                    s.shift = new int[] { dx, dz };
                    s.halls.add(slot);
                    s.offerAll();
                    s.buildAll();
                    int[] hc = s.hallCentre(slot), hb = hallBounds(hc);
                    List<int[]> g = ground(s);
                    String at = " (slot " + slot + ", shift " + dx + "," + dz + ", facing " + Arrays.toString(f) + ")";
                    assertFalse(meets(g, new int[] { hb[0], hb[2], hb[3], hb[5] }), "the hall meets a neighbour" + at);
                    int[] b = BuildPlan.retireBox(hc, hb, g);
                    assertTrue(b[0] <= hb[0] && b[1] <= hb[2] && b[2] >= hb[3] && b[3] >= hb[5], "the hall" + at);
                    assertFalse(meets(g, b), "the box meets a neighbour's ground" + at);
                    for (Map.Entry<Integer, String> e : s.built.entrySet())
                        for (int[] cell : planColumns(s, e.getValue(), e.getKey()))
                            assertFalse(meets(Collections.singletonList(b), cell), e.getValue() + "'s plan" + at);
                    cases++;
                }
        assertEquals(4 * 8 * 49, cases);
    }

    /** The 0.9.2 hall's bounds round the centre of its foundation, {x0, y0, z0, x1, y1, z1}: 13 x 13. */
    private static int[] hallBounds(int[] c) {
        return new int[] { c[0] - 6, c[1], c[2] - 6, c[0] + 6, c[1] + 12, c[2] + 6 };
    }

    private static long column(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** The protected boxes of a module standing on a site, by key, site, facing and seed (worked out once). */
    private static final Map<String, List<int[]>> PROTECTS = new HashMap<>();
    /** The columns {x, z, x, z} a module's plan takes on a site, by key, site, facing and seed (worked out once). */
    private static final Map<String, List<int[]>> PLAN_COLUMNS = new HashMap<>();

    /**
     * The ground of the modules standing in the model, as column boxes {x0, z0, x1, z1, ...}: their keep-out boxes
     * and their protected boxes, as the campus hands them to the retire box.
     */
    private static List<int[]> ground(Model m) {
        List<int[]> out = new ArrayList<>();
        for (Map.Entry<Integer, String> e : m.built.entrySet()) {
            ModuleSpec b = spec(e.getValue());
            String k = b.key + "@" + e.getKey() + ":" + m.fx + "," + m.fz + ":" + m.seed;
            out.addAll(BOXES.computeIfAbsent(k, x -> b.keepOut(m.plan, e.getKey(), CX, Y0, CZ, m.fx, m.fz)));
            for (int[] p : PROTECTS.computeIfAbsent(k, x -> b.protect(m.plan, e.getKey(), CX, Y0, CZ, m.fx, m.fz)))
                out.add(new int[] { p[0], p[2], p[3], p[5] });
        }
        return out;
    }

    private static List<int[]> planColumns(Model m, String key, int site) {
        return PLAN_COLUMNS.computeIfAbsent(key + "@" + site + ":" + m.fx + "," + m.fz + ":" + m.seed, x -> {
            Set<Long> seen = new HashSet<>();
            List<int[]> out = new ArrayList<>();
            for (BuildPlan.Step st : spec(key).plan(m.plan, site, CX, Y0, CZ, m.fx, m.fz).steps)
                if (seen.add(column(st.x, st.z))) out.add(new int[] { st.x, st.z, st.x, st.z });
            return out;
        });
    }

    /**
     * Whether a column box {x0, z0, x1, z1, ...} of the list meets the columns of {@code box} {x0, z0, x1, z1, ...}.
     */
    private static boolean meets(List<int[]> boxes, int[] box) {
        for (int[] b : boxes)
            if (Math.max(b[0], b[2]) >= Math.min(box[0], box[2]) && Math.min(b[0], b[2]) <= Math.max(box[0], box[2])
                && Math.max(b[1], b[3]) >= Math.min(box[1], box[3])
                && Math.min(b[1], b[3]) <= Math.max(box[1], box[3])) return true;
        return false;
    }

    /**
     * The star map's texts name the ring sites each phase opens ({@link SiteFamily#openRingSites}: 3 at phase I, 8 at
     * phase II), in both languages, so they cannot drift from the sites again: 0.14.0's still said a phase I nexus
     * docks 2 modules and a phase II one 4, the room 0.14.1 took away.
     */
    @Test
    void theResearchTextsNameTheSitesEachPhaseOpens() throws java.io.IOException {
        for (String file : new String[] { "en_US.lang", "zh_CN.lang" }) {
            Map<String, String> lang = new HashMap<>();
            for (String line : java.nio.file.Files.readAllLines(
                java.nio.file.Paths.get("src", "main", "resources", "assets", "fluxecho", "lang", file),
                java.nio.charset.StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.startsWith("#")) lang.put(line.substring(0, eq), line.substring(eq + 1));
            }
            String ring = lang.get("fluxecho.research.node.inner_ring.desc"),
                two = lang.get("fluxecho.research.node.phase_2.desc");
            assertTrue(names(ring, SiteFamily.openRingSites(1)), file + ": " + ring);
            assertTrue(names(two, SiteFamily.openRingSites(2)), file + ": " + two);
            assertFalse(names(ring, RingSlots.open(1)), file + " still names 0.14.0's room: " + ring);
            assertFalse(names(two, RingSlots.open(2)), file + " still names 0.14.0's room: " + two);
        }
    }

    /** Whether a text names a number on its own (not as a digit of a longer number). */
    private static boolean names(String text, int n) {
        return java.util.regex.Pattern.compile("(?<!\\d)" + n + "(?!\\d)")
            .matcher(text)
            .find();
    }
}
