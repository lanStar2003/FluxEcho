package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The sky ring's sites and plans (0.16.0, CITY.md §2.5, §4.12, M13 §5): the sites' axes, centres and names, the shared
 * helpers that took a sky site for an inner one, the controller of every sky module on every module site and every
 * front (radial 23, or 24 for the hangar, two over the deck, facing the pod), the catwalk (55 soft cells) and the
 * pod's door (12 cells) each job lays and opens, the cells of the pod's protected box it is let into, its envelope,
 * and its plan: the checked cells exactly where the structure check looks, the stages in order, the door last of
 * DECOR, the clear box in the air, the clip at the world's top that never takes a unit cell or the controller, and the
 * ground modules' boxes under the ring stopping two under the lowest deck.
 */
class SkyRingTest {

    /** The four modules' builds, the sites each may take, its controller's row from the front. */
    private static final CityBuild[] BUILDS = { WeatherStationShape.BUILD, SkySailsShape.BUILD, ObservatoryShape.BUILD,
        DropletHangarShape.BUILD };
    /**
     * Every module's job steps at a base whose deck is in the world: the shape's, the catwalk's 55 and the door's 12.
     */
    private static final int[] JOB_STEPS = { 699, 761, 1099, 587 };
    /** The four horizontal fronts a nexus may face. */
    private static final int[][] FRONTS = { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };
    private static final int[] MODULE_SITES = { 30, 32, 34, 36 };

    @Test
    void theSitesAxesCentresAndNames() {
        int[][] axes = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 } };
        int[][] centres = { { 32, 0 }, { 23, 23 }, { 0, 32 }, { -23, 23 }, { -32, 0 }, { -23, -23 }, { 0, -32 },
            { 23, -23 } };
        for (int k = 0; k < 8; k++) {
            int site = 30 + k;
            assertTrue(SkyRing.skySite(site));
            assertEquals(k, SkyRing.slot(site));
            assertEquals(k % 2 == 1, SkyRing.berth(site));
            assertEquals(k % 2 == 0, SkyRing.platformSite(site));
            assertArrayEquals(axes[k], SkyRing.axis(site));
            // the shared helpers no longer fold a sky site onto the inner ring (30 mod 8 is 6, the -R hall)
            assertArrayEquals(axes[k], CampusPlan.siteAxis(site));
            assertArrayEquals(centres[k], SkyRing.centre(site));
            assertArrayEquals(centres[k], CampusPlan.moduleCentre(site, 35));
            assertTrue(CampusPlan.inEnvelope(site, 0, 0, centres[k][0], centres[k][1]));
            assertEquals("build.gui.site_s" + k, SiteFamily.labelKey(site));
            assertEquals(SiteFamily.SKY, SiteFamily.of(site));
            assertEquals(1 << k, SkyRing.bit(site));
        }
        assertFalse(SkyRing.skySite(29));
        assertFalse(SkyRing.skySite(38));
        assertEquals(-1, SkyRing.slot(8));
        assertEquals(0, SkyRing.bit(8));
        assertThrows(IllegalArgumentException.class, () -> SkyRing.axis(8));
        // the platform oct(10, 14) round (32, 0): its front row at radial 22, its back at 42
        assertTrue(CampusPlan.inEnvelope(30, 0, 0, 22, 0));
        assertTrue(CampusPlan.inEnvelope(30, 0, 0, 42, 0));
        assertFalse(CampusPlan.inEnvelope(30, 0, 0, 21, 0));
        assertFalse(CampusPlan.inEnvelope(30, 0, 0, 22, 5), "the octagon's cut corner");
        assertFalse(CampusPlan.inEnvelope(30, 0, 0, 0, 0), "not the gatehouse's");
        // the sky ring opens at phase IV, its berths at V; its ground is the floating pod
        assertEquals(4, SiteFamily.opensAt(30));
        assertEquals(5, SiteFamily.opensAt(31));
        assertEquals(SiteFamily.POD_FLOATS, SiteFamily.SKY.ground());
    }

    @Test
    void theControllerOnEverySiteAndFront() {
        for (CityBuild b : BUILDS)
            for (int site : MODULE_SITES) for (int[] f : FRONTS) for (int datum : DomeGeneratorShape.datums()) {
                int cx = 100, y0 = 64, cz = -200;
                int[] at = SkyRing.controller(b, site, cx, y0, cz, f[0], f[1], datum);
                int[] ax = SkyRing.axis(site);
                int[] l = CampusPlan.toLocal(at[0] - cx, at[2] - cz, f[0], f[1]);
                int radial = SkyRing.FRONT + b.controller()[2];
                assertArrayEquals(new int[] { radial * ax[0], radial * ax[1] }, l, b.key() + "@" + site);
                assertEquals(y0 + datum + 2, at[1], "two over the deck, the pod's controller's height");
                // it faces the pod: against its site's axis
                int[] back = CampusPlan.toWorld(-ax[0], -ax[1], f[0], f[1]);
                assertEquals(BuildPlan.facingOrdinal(back[0], back[1]), at[3]);
                // the module's middle column stands on the platform's centre
                Blueprint bp = b.blueprint();
                int[] mid = b.cellOf(10, 0, 10);
                int[] fr = BuildPlan.front(at[3]);
                int[] w = bp.world(mid[0], mid[1], mid[2], at[0], at[1], at[2], fr[0], fr[1]);
                int[] c = CampusPlan.toLocal(w[0] - cx, w[2] - cz, f[0], f[1]);
                assertArrayEquals(SkyRing.centre(site), c, b.key() + "@" + site);
                assertEquals(y0 + datum, w[1], "the deck is the pod's floor");
            }
        assertThrows(
            IllegalArgumentException.class,
            () -> SkyRing.controller(WeatherStationShape.BUILD, 31, 0, 64, 0, 0, -1, 40),
            "a berth takes no module");
        assertThrows(
            IllegalArgumentException.class,
            () -> SkyRing.controller(WeatherStationShape.BUILD, 30, 0, 64, 0, 0, -1, 39));
    }

    @Test
    void theCatwalkAndTheDoor() {
        for (int site : MODULE_SITES) {
            int[] ax = SkyRing.axis(site);
            List<int[]> walk = SkyRing.catwalk(site);
            assertEquals(55, walk.size());
            int deck = 0, rails = 0;
            for (int[] c : walk) {
                int t = c[0] * ax[0] + c[1] * ax[1], s = -c[0] * ax[1] + c[1] * ax[0];
                assertTrue(t >= 11 && t <= 21);
                if (c[3] == '.') {
                    deck++;
                    assertEquals(0, c[2]);
                    assertTrue(Math.abs(s) <= 1);
                } else {
                    rails++;
                    assertEquals('|', c[3]);
                    assertEquals(1, c[2]);
                    assertEquals(2, Math.abs(s));
                }
                assertTrue(ShapeKit.soft((char) c[3]), "the catwalk is soft: no structure checks it");
            }
            assertEquals(33, deck);
            assertEquals(22, rails);
            for (int datum : DomeGeneratorShape.datums()) {
                List<int[]> door = SkyRing.doorCells(site, datum);
                assertEquals(12, door.size());
                Set<String> cols = new HashSet<>();
                for (int[] c : door) {
                    int t = c[0] * ax[0] + c[1] * ax[1], s = -c[0] * ax[1] + c[1] * ax[0];
                    assertEquals(10, t, "the pod's flat face");
                    assertTrue(Math.abs(s) <= 1);
                    assertTrue(c[2] >= datum + 1 && c[2] <= datum + 4, "y1..y4 over the pod's floor");
                    cols.add(c[0] + "," + c[1]);
                }
                assertEquals(3, cols.size());
                // the cells it is let into: its door and its catwalk's first row
                List<int[]> exempt = SkyRing.exempt(site, 0, 64, 0, 0, -1, datum);
                assertEquals(17, exempt.size());
            }
        }
        // the generator's first catwalk cells for S0: (11, -1), (11, 0), (11, 1) and the rails (11, -2), (11, 2)
        List<int[]> s0 = SkyRing.catwalk(30);
        assertArrayEquals(new int[] { 11, -1, 0, '.' }, s0.get(0));
        assertArrayEquals(new int[] { 11, 2, 1, '|' }, s0.get(4));
        // a floating pod's plan clears the first row of an open door's catwalk only over it
        assertTrue(SkyRing.catwalkMouth(SkyRing.bit(30), 11, 2));
        assertTrue(SkyRing.catwalkMouth(SkyRing.bit(30), 11, -2));
        assertFalse(SkyRing.catwalkMouth(SkyRing.bit(30), 11, 3));
        assertFalse(SkyRing.catwalkMouth(SkyRing.bit(30), 10, 0));
        assertFalse(SkyRing.catwalkMouth(SkyRing.bit(32), 11, 0));
        assertTrue(SkyRing.catwalkMouth(SkyRing.bit(32), 0, 11));
        assertFalse(SkyRing.catwalkMouth(0, 11, 0));
    }

    @Test
    void theEnvelope() {
        for (int y0 = CampusBand.LOWEST; y0 <= CampusBand.HIGHEST; y0++)
            assertNull(SkyRing.noRoom(y0), "every base a core may be placed at: " + y0);
        assertEquals("build.module.why.height", SkyRing.noRoom(226));
        assertEquals(64 + 40 - 2, SkyRing.groundTop(64));
        assertEquals(212 + 28 - 2, SkyRing.groundTop(212));
        // S0 facing north: A is -z, R is +x (toWorld(a, r) = (r, -a))
        int[] fp = SkyRing.footprint(30, 0, 0, 0, -1, 0);
        assertArrayEquals(new int[] { -10, -42, 10, -11 }, fp);
        List<int[]> p = SkyRing.protect(30, 0, 64, 0, 0, -1, 40, 13);
        assertEquals(2, p.size());
        assertArrayEquals(new int[] { -11, 103, -43, 11, 104 + 13 + 2, -21 }, p.get(0), "the platform's box");
        assertArrayEquals(new int[] { -2, 104, -21, 2, 105, -11 }, p.get(1), "the catwalk");
    }

    @Test
    void theJobsPlanOnEverySiteAndFront() {
        for (int i = 0; i < BUILDS.length; i++) for (int site : MODULE_SITES) for (int[] f : FRONTS) {
            CityBuild b = BUILDS[i];
            int cx = 30, y0 = 64, cz = -500, datum = 40, deck = y0 + datum;
            BuildPlan.Plan plan = BuildPlan.sky(site, cx, y0, cz, f[0], f[1], datum, b);
            String what = b.key() + "@" + site + " front " + f[0] + "," + f[1];
            assertEquals(BuildPlan.moduleKey(b.key(), site), plan.key);
            assertEquals(SkyRing.K_STAGES, plan.stages);
            assertEquals(JOB_STEPS[i], plan.steps.size(), what);
            int[] at = SkyRing.controller(b, site, cx, y0, cz, f[0], f[1], datum);
            int[] fr = BuildPlan.front(at[3]);
            // the HARD steps are exactly the cells the structure check looks at, and the controller is the last step
            Set<Long> checked = new HashSet<>();
            for (Blueprint.Cell c : b.blueprint()
                .cells()) {
                if (c.ch == ShapeKit.AIR) continue;
                int[] w = b.blueprint()
                    .world(c.a, c.b, c.c, at[0], at[1], at[2], fr[0], fr[1]);
                checked.add(ShapeKit.Shape.pos(w[0], w[1], w[2]));
            }
            Set<Long> hard = new HashSet<>();
            int soft = 0, air = 0;
            for (BuildPlan.Step s : plan.steps) {
                if (s.kind == BuildPlan.HARD) hard.add(ShapeKit.Shape.pos(s.x, s.y, s.z));
                if (s.kind == BuildPlan.SOFT) soft++;
                if (s.kind == BuildPlan.AIR) air++;
            }
            assertEquals(checked, hard, what);
            assertEquals(55, soft, what + ": the catwalk");
            assertEquals(airOf(b) + 12, air, what + ": the shape's air and the door");
            BuildPlan.Step last = plan.steps.get(plan.steps.size() - 1);
            assertEquals(SkyRing.K_COMMISSION, last.stage);
            assertArrayEquals(new int[] { at[0], at[1], at[2] }, new int[] { last.x, last.y, last.z });
            // DECOR: the catwalk at the deck and a cell over it, then the twelve door cells last
            List<BuildPlan.Step> decor = new ArrayList<>();
            for (BuildPlan.Step s : plan.steps) if (s.stage == SkyRing.K_DECOR) decor.add(s);
            assertEquals(67, decor.size(), what);
            for (int k = 0; k < 55; k++) {
                assertEquals(BuildPlan.SOFT, decor.get(k).kind);
                assertTrue(decor.get(k).y == deck || decor.get(k).y == deck + 1);
            }
            for (int k = 55; k < 67; k++) {
                BuildPlan.Step s = decor.get(k);
                assertEquals(BuildPlan.AIR, s.kind);
                int[] l = CampusPlan.toLocal(s.x - cx, s.z - cz, f[0], f[1]);
                int[] ax = SkyRing.axis(site);
                assertEquals(10, l[0] * ax[0] + l[1] * ax[1], "the pod's door");
            }
            // the stages never go back, the platform first
            int stage = -1;
            for (BuildPlan.Step s : plan.steps) {
                assertTrue(s.stage >= stage);
                stage = s.stage;
            }
            assertEquals(SkyRing.K_FLOAT, plan.steps.get(0).stage);
            assertEquals(deck, plan.steps.get(0).y, "the floating foundation first");
            // every unit one contiguous group
            int group = BuildPlan.NO_GROUP, groups = 0;
            for (BuildPlan.Step s : plan.steps) if (s.group != group) {
                if (s.group != BuildPlan.NO_GROUP) groups++;
                group = s.group;
            }
            assertEquals(
                b.units()
                    .size(),
                groups,
                what);
            // the clear box in the air only, never graded: nothing under the deck but its one cell
            for (BuildPlan.Column c : plan.clear) {
                assertFalse(c.grade);
                assertTrue(c.yFrom >= deck - 1, what);
            }
        }
    }

    /** The must-be-air cells of a build's shape. */
    private static int airOf(CityBuild b) {
        int n = 0;
        for (int y = 0; y < b.height(); y++) for (int z = 0; z < b.depth(); z++)
            for (int x = 0; x < b.width(); x++) if (b.cell(x, y, z) == ShapeKit.AIR) n++;
        return n;
    }

    @Test
    void overHighGroundThePlansClipButNeverAUnitOrTheController() {
        // the highest deck is 253 (Y0 225 at the sky datum 28): every unit cell and the controller stay in the world
        for (CityBuild b : BUILDS) for (int y0 = 200; y0 <= CampusBand.HIGHEST; y0++) {
            int datum = DomeGeneratorShape.datum(y0);
            for (int legacy : new int[] { datum, 40 }) {
                if (y0 + legacy + SkyRing.STAND_TOP > 255) continue;
                BuildPlan.Plan plan = BuildPlan.sky(30, 0, y0, 0, 0, -1, legacy, b);
                int units = 0;
                for (BuildPlan.Step s : plan.steps) {
                    assertTrue(s.y >= 0 && s.y <= 255);
                    if (s.group != BuildPlan.NO_GROUP) units++;
                }
                int want = 0;
                for (List<int[]> u : b.units()) want += u.size();
                assertEquals(want, units, b.key() + " at Y0 " + y0 + ", F " + legacy);
                assertEquals(SkyRing.K_COMMISSION, plan.steps.get(plan.steps.size() - 1).stage);
                assertEquals(y0 + legacy + 2, BuildPlan.standTop(plan));
            }
        }
        // at Y0 225 the sails' masts clip to two cells, the catwalk tops out at y 254
        BuildPlan.Plan sails = BuildPlan.sky(32, 0, 225, 0, 0, -1, 28, SkySailsShape.BUILD);
        int top = 0;
        for (BuildPlan.Step s : sails.steps) top = Math.max(top, s.y);
        assertEquals(255, top);
    }

    @Test
    void theGroundModulesStopUnderTheLowestDeck() {
        // the Dual-foil Vault, the tallest building on a hall site: its clear box reached Y0 + 29, three cells into the
        // deck at the sky datum 28 (Y0 >= 212); it stops at Y0 + 26 there, and is the same as before under a higher
        // deck
        for (int y0 : new int[] { 64, 203, 207, 211, 212, 225 }) {
            BuildPlan.Plan vault = DualFoilVaultShape.plan(new CampusPlan(7L), 6, 0, y0, 0, 0, -1);
            int top = Integer.MIN_VALUE;
            for (BuildPlan.Column c : vault.clear) top = Math.max(top, c.yTo);
            int was = y0 + 26 + 1 + BuildPlan.MODULE_HEADROOM;
            assertEquals(Math.min(was, SkyRing.groundTop(y0)), top, "Y0 " + y0);
            for (BuildPlan.Step s : vault.steps) assertTrue(s.y <= SkyRing.groundTop(y0), "no step moves");
            // and every sky module's job over it starts a cell under its deck
            for (CityBuild b : BUILDS) {
                BuildPlan.Plan sky = BuildPlan.sky(36, 0, y0, 0, 0, -1, DomeGeneratorShape.datum(y0), b);
                for (BuildPlan.Column c : sky.clear) assertTrue(c.yFrom > SkyRing.groundTop(y0));
                for (BuildPlan.Step s : sky.steps) assertTrue(s.y > SkyRing.groundTop(y0));
            }
        }
        // the plans under a deck at 40 are those of 0.15.0
        BuildPlan.Plan low = DualFoilVaultShape.plan(new CampusPlan(7L), 6, 0, 64, 0, 0, -1);
        int top = Integer.MIN_VALUE;
        for (BuildPlan.Column c : low.clear) top = Math.max(top, c.yTo);
        assertEquals(64 + 29, top);
    }

    @Test
    void aFloatingPodLeavesAnOpenDoorsCatwalkStanding() {
        int cx = 0, y0 = 64, cz = 0;
        BuildPlan.Plan closed = BuildPlan.floating(SiteFamily.ZENITH_SITE, cx, y0, cz, 0, -1, 40, 0);
        BuildPlan.Plan open = BuildPlan
            .floating(SiteFamily.ZENITH_SITE, cx, y0, cz, 0, -1, 40, SkyRing.bit(30) | SkyRing.bit(34));
        assertEquals(closed.clear.size(), open.clear.size());
        assertEquals(closed.steps.size() - 24, open.steps.size(), "two doors of 12 glazing cells left open");
        int raised = 0;
        for (int i = 0; i < closed.clear.size(); i++) {
            BuildPlan.Column a = closed.clear.get(i), b = open.clear.get(i);
            assertEquals(a.x, b.x);
            assertEquals(a.z, b.z);
            if (a.yFrom != b.yFrom) {
                raised++;
                assertEquals(a.yFrom + 2, b.yFrom, "over the catwalk's deck and rails");
            }
        }
        assertEquals(10, raised, "the first rows of two catwalks");
        // the catwalk's first row lies in those columns, under their clearing
        for (int site : new int[] { 30, 34 }) for (int[] c : SkyRing.catwalk(site)) {
            int[] ax = SkyRing.axis(site);
            if (c[0] * ax[0] + c[1] * ax[1] != 11) continue;
            int[] w = CampusPlan.toWorld(c[0], c[1], 0, -1);
            boolean found = false;
            for (BuildPlan.Column col : open.clear) if (col.x == cx + w[0] && col.z == cz + w[1]) {
                found = true;
                assertTrue(col.yFrom > y0 + 40 + c[2]);
            }
            assertTrue(found);
        }
    }

    /**
     * The walk from the pod to a sky module on S0 (the fact report's composite): one shape in the campus frame
     * (x = R + 11, z = A + 11, y up from the pod's floor) holding the pod as it is built (its doors glazed), the
     * catwalk's soft deck and rails from radial 11 to 21, and the module on its platform from radial 22, its units and
     * controller declared where they stand. With {@code open} the pod's door on the axis is emptied, as the module's
     * job does. The walk starts on the pod's lift pad, where the light lift lands.
     */
    static ShapeKit.Shape composite(ShapeKit.Shape module, boolean open) {
        ShapeKit.Shape s = new ShapeKit.Shape("sky_walk_" + module.name, 23, 16, 56);
        s.groundOutside = false;
        ShapeKit.Shape pod = DomeGeneratorShape.SHAPE;
        Set<Long> door = new HashSet<>();
        for (int[] c : DomeGeneratorShape.doorCells(0)) door.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));
        for (int y = 0; y < pod.H; y++) for (int z = 0; z < pod.D; z++) for (int x = 0; x < pod.W; x++) {
            char ch = pod.get(x, y, z);
            if (ch == ShapeKit.NONE) continue;
            if (open && door.contains(ShapeKit.Shape.pos(x, y, z))) continue;
            // the pod's (x, z) is campus (A 10 - z, R 10 - x)
            s.set(21 - x, y, 21 - z, ch);
        }
        for (int[] c : SkyRing.catwalk(30)) s.set(c[1] + 11, c[2], c[0] + 11, (char) c[3]);
        for (int y = 0; y < module.H; y++) for (int z = 0; z < module.D; z++) for (int x = 0; x < module.W; x++) {
            char ch = module.get(x, y, z);
            // the module's (x, z) is campus (A 22 + z, R x - 10): it is mirror-symmetric, so either side is the same
            if (ch != ShapeKit.NONE) s.set(x + 1, y, z + 33, ch);
        }
        for (ShapeKit.Unit u : module.units()) {
            List<int[]> cells = new ArrayList<>();
            for (int[] c : u.cells) cells.add(new int[] { c[0] + 1, c[1], c[2] + 33 });
            s.unit(u.name, u.kind, cells, u.slots, null);
        }
        int[] c = module.controller;
        s.controller = new int[] { c[0] + 1, c[1], c[2] + 33 };
        return s;
    }

    /** The composite's start: on the pod's lift pad, where the light lift lands. */
    static List<int[]> padStart() {
        List<int[]> out = new ArrayList<>();
        out.add(new int[] { 11, 11, 2 });
        return out;
    }

    @Test
    void fromThePodsPadThroughItsDoorAndOverTheCatwalkToEveryUnit() {
        for (ShapeKit.Shape m : new ShapeKit.Shape[] { WeatherStationShape.SHAPE, SkySailsShape.SHAPE,
            ObservatoryShape.SHAPE, DropletHangarShape.SHAPE }) {
            ShapeKit.Summary open = ShapeKit.summary(composite(m, true), padStart());
            assertTrue(open.controller, m.name + ": the controller at eye height");
            assertTrue(open.unreachable.isEmpty(), m.name + ": " + open.unreachable);
            // with its door glazed nobody reaches the platform from the pod
            ShapeKit.Shape shut = composite(m, false);
            Set<Long> reached = shut.walk(padStart(), 2);
            for (long st : reached) assertTrue(
                ShapeKit.Shape.stateZ(st) < 33,
                m.name + ": the platform is reached through a glazed door at " + ShapeKit.Shape.stateX(st));
            ShapeKit.Summary closed = ShapeKit.summary(shut, padStart());
            assertFalse(closed.controller);
        }
    }

    /**
     * The NEI city preview (phase IV) stands the four on sites of their own, off the inner ring: the ring's room is
     * all the inner city's, and the sails and the hangar each take one of the two sites they share.
     */
    @Test
    void thePreviewStandsTheFourOnTheirOwnSites() {
        List<int[]> preferred = new ArrayList<>();
        for (int i = 0; i < 8; i++) preferred.add(new int[] { i == 0 ? 0 : i });
        preferred.add(WeatherStationShape.SITES);
        preferred.add(SkySailsShape.SITES);
        preferred.add(ObservatoryShape.SITES);
        preferred.add(DropletHangarShape.SITES);
        List<PreviewSites.Place> places = PreviewSites.places(preferred, SiteFamily.openRingSites(4));
        assertEquals(12, places.size());
        int[] sky = new int[4];
        for (int i = 0; i < 4; i++) sky[i] = places.get(8 + i).site;
        assertArrayEquals(new int[] { 30, 32, 34, 36 }, sky);
    }
}
