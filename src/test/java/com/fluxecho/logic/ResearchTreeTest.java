package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ResearchTreeTest {

    @Test
    void oneRootAndEveryRequirementComesEarlier() {
        int roots = 0;
        Set<String> seen = new HashSet<>();
        for (ResearchTree.Node n : ResearchTree.all()) {
            if (n.root()) roots++;
            for (String r : n.requires) {
                assertNotNull(ResearchTree.get(r), n.id + " needs " + r);
                assertTrue(seen.contains(r), n.id + " needs " + r + ", listed later: no cycles");
            }
            seen.add(n.id);
        }
        assertEquals(1, roots);
        assertTrue(
            ResearchTree.get(ResearchTree.ANCHOR)
                .root());
    }

    /**
     * What an operator's {@code /fluxecho research all} grants: every node the star map lets a team research in this
     * version (0.12.0: phase II, the tidal well and the dome too), never the greyed placeholders of later phases.
     */
    @Test
    void onlyTheNodesOfThisVersionAreAvailable() {
        Set<String> now = new HashSet<>();
        for (ResearchTree.Node n : ResearchTree.availableNow()) now.add(n.id);
        assertTrue(now.contains(ResearchTree.PHASE_2));
        assertTrue(now.contains(ResearchTree.TIDAL_WELL));
        assertTrue(now.contains(ResearchTree.DOME));
        assertTrue(now.contains(ResearchTree.ANCHOR));
        assertTrue(now.contains(ResearchTree.LIBRARY), "the library's research opens the Echo Archive");
        for (ResearchTree.Node n : ResearchTree.all()) {
            // the star map greys a node out exactly when it is not available, however far the nexus has grown
            boolean greyed = ResearchTree.check(n, Collections.emptySet(), 99, 999) == ResearchTree.Block.LATER_PHASE;
            assertEquals(!greyed, ResearchTree.availableNow(n), n.id);
            assertEquals(n.phase <= ResearchTree.PHASE_NOW, now.contains(n.id), n.id);
        }
        // granting them all leaves nothing the star map could still start in this version
        for (ResearchTree.Node n : ResearchTree.all()) {
            ResearchTree.Block b = ResearchTree.check(n, now, ResearchTree.PHASE_NOW, 999);
            assertTrue(b == ResearchTree.Block.DONE || b == ResearchTree.Block.LATER_PHASE, n.id + ": " + b);
        }
        assertFalse(ResearchTree.availableNow((ResearchTree.Node) null));
    }

    @Test
    void positionsAreOnTheMapAndApart() {
        for (ResearchTree.Node a : ResearchTree.all()) {
            assertTrue(
                a.x >= 16 && a.x <= ResearchTree.WIDTH - 16 && a.y >= 16 && a.y <= ResearchTree.HEIGHT - 16,
                a.id);
            for (ResearchTree.Node b : ResearchTree.all())
                if (a != b) assertTrue(Math.hypot(a.x - b.x, a.y - b.y) >= 24, a.id + " too close to " + b.id);
        }
    }

    /** How near a line between two stars may pass any other star's middle: clear of its disc and most of its arms. */
    private static final double LINK_CLEAR = 9;

    /** How far a point lies from the segment between two others. */
    private static double fromSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
        return Math.hypot(px - ax - t * dx, py - ay - t * dy);
    }

    // The star map as StarMapScreen draws it, in GUI pixels. The map's scale is the smaller of (w - 48) / WIDTH and
    // (h - 70) / HEIGHT, and the map is centred. A star has radius r = 7 * scale; once done, its two arms reach 1.4 r
    // and are 1.4 pixels wide, and its dot is a square 0.7 r across. A node's name is drawn centred under it at 0.75 of
    // the font's size, its top 2 pixels below r. A constellation's name is drawn at 0.6 * scale of the font's size,
    // kept within 0.75 and 1.2, its top 4 pixels over its point. The font is Minecraft's: 8 pixels high, ascii.png's
    // advances, 9 pixels for a CJK glyph; zh_CN's unicode font draws ASCII a little narrower, which only leaves more
    // room.

    /** The lang files whose names the star map writes, as LangTest reads them. */
    private static final Path LANG = Paths.get("src", "main", "resources", "assets", "fluxecho", "lang");
    /**
     * The GUI sizes {width, height} the map is checked at: the smallest Minecraft makes (a 4:3 screen at GUI scale
     * Auto), 1280 x 720 and 2560 x 1440 at Auto (426 x 240), 1920 x 1080 at Auto (480 x 270), and larger ones up to
     * 1920 x 1080 at GUI scale 1.
     */
    private static final int[][] GUIS = { { 320, 240 }, { 426, 240 }, { 480, 270 }, { 512, 288 }, { 640, 360 },
        { 683, 384 }, { 853, 480 }, { 960, 540 }, { 1280, 720 }, { 1920, 1080 } };
    /** How far apart, in GUI pixels, any two things the map draws stay. */
    private static final double GAP = 0.5;
    /** The star map's header and footer bands, in GUI pixels, which no node's name may run into. */
    private static final int HEADER = 24, FOOTER = 30;

    /** {scale, left, top} of the map on a GUI of w x h. */
    private static double[] place(int w, int h) {
        double scale = Math.min((w - 48.0) / ResearchTree.WIDTH, (h - 70.0) / ResearchTree.HEIGHT);
        return new double[] { scale, (w - ResearchTree.WIDTH * scale) / 2,
            30 + (h - 70 - ResearchTree.HEIGHT * scale) / 2 };
    }

    /** The boxes {x0, y0, x1, y1} of a done star, its largest look: its two arms and its dot. */
    private static double[][] star(ResearchTree.Node n, double[] m) {
        double cx = m[1] + n.x * m[0], cy = m[2] + n.y * m[0], r = 7 * m[0], arm = 1.4 * r, dot = 0.35 * r;
        return new double[][] { { cx - arm, cy - 0.7, cx + arm, cy + 0.7 }, { cx - 0.7, cy - arm, cx + 0.7, cy + arm },
            { cx - dot, cy - dot, cx + dot, cy + dot } };
    }

    /** The box of a node's name under its star. */
    private static double[] name(ResearchTree.Node n, double[] m, Map<String, String> lang) {
        double cx = m[1] + n.x * m[0], top = m[2] + n.y * m[0] + 7 * m[0] + 2;
        double half = textWidth(text(lang, "research.node." + n.id)) * 0.75 / 2;
        return new double[] { cx - half, top, cx + half, top + 8 * 0.75 };
    }

    /** The box of a constellation's name. */
    private static double[] title(ResearchTree.Branch b, double[] m, Map<String, String> lang) {
        double size = Math.max(0.75, Math.min(1.2, m[0] * 0.6));
        double cx = m[1] + b.labelX * m[0], top = m[2] + b.labelY * m[0] - 4;
        double half = textWidth(text(lang, "research.branch." + b.key)) * size / 2;
        return new double[] { cx - half, top, cx + half, top + 8 * size };
    }

    /** Whether two boxes come nearer each other than {@link #GAP}. */
    private static boolean meet(double[] a, double[] b) {
        return a[0] < b[2] + GAP && b[0] < a[2] + GAP && a[1] < b[3] + GAP && b[1] < a[3] + GAP;
    }

    /** The width Minecraft 1.7.10's font gives a string at full size, in GUI pixels. */
    static int textWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += advance(s.charAt(i));
        return w;
    }

    /** One glyph's advance: ascii.png's (most letters 6, the narrow ones less, a space 4), 9 for a CJK glyph. */
    private static int advance(char c) {
        if (c >= 128) return 9;
        if ("!,.:;i|".indexOf(c) >= 0) return 2;
        if ("'`l".indexOf(c) >= 0) return 3;
        if (" I[]t".indexOf(c) >= 0) return 4;
        if ("\"()*<>fk{}".indexOf(c) >= 0) return 5;
        if ("@~".indexOf(c) >= 0) return 7;
        return 6;
    }

    /** A lang file's keys and texts. */
    private static Map<String, String> lang(String file) throws IOException {
        Map<String, String> m = new HashMap<>();
        for (String line : Files.readAllLines(LANG.resolve(file), StandardCharsets.UTF_8)) {
            String l = line.startsWith("﻿") ? line.substring(1) : line;
            int eq = l.indexOf('=');
            if (eq > 0 && !l.startsWith("#")) m.put(l.substring(0, eq), l.substring(eq + 1));
        }
        return m;
    }

    private static String text(Map<String, String> lang, String key) {
        String s = lang.get("fluxecho." + key);
        assertNotNull(s, key);
        return s;
    }

    /** The model's font gives the widths Minecraft's does for a few names. */
    @Test
    void textWidthsAreMinecrafts() {
        assertEquals(68, textWidth("Arcane Court"));
        assertEquals(104, textWidth("Echo Hunting Ground"));
        assertEquals(106, textWidth("Blood Covenant Altar"));
        assertEquals(27, textWidth("秘法庭"));
    }

    /**
     * Nothing the star map writes lies on anything else it draws, in either language and at every GUI size down to
     * the smallest: no star and no node's name on a constellation's name, no node's name on another node's star or
     * name, and every node's name on the screen between the header and the footer. Each name is as wide as its lang
     * string makes it, so the English names, far wider than the Chinese ones, are what this keeps apart (0.13.0:
     * 'Arcane Court' and 'Echo Hunting Ground' ran together on one row, and 'Blood Covenant Altar' lay on the eco
     * dome's star and touched the echo branch's name below 640 x 360; since 0.9.0 'Echo Crystallization' had crossed
     * the anchor's arm and 'Echo Library' the arm of 'Lending Across Worlds' at 426 x 240).
     */
    @Test
    void starsAndNamesKeepOffEachOther() throws IOException {
        for (String file : new String[] { "en_US.lang", "zh_CN.lang" }) {
            Map<String, String> lang = lang(file);
            for (int[] gui : GUIS) {
                double[] m = place(gui[0], gui[1]);
                String at = " (" + file + ", " + gui[0] + " x " + gui[1] + ")";
                for (ResearchTree.Node n : ResearchTree.all()) {
                    double[] name = name(n, m, lang);
                    assertTrue(
                        name[0] >= 0 && name[2] <= gui[0] && name[1] >= HEADER && name[3] <= gui[1] - FOOTER,
                        n.id + "'s name off the map" + at);
                    for (ResearchTree.Branch b : ResearchTree.Branch.values()) {
                        double[] title = title(b, m, lang);
                        assertFalse(meet(name, title), n.id + "'s name on the name of " + b.key + at);
                        for (double[] s : star(n, m))
                            assertFalse(meet(s, title), n.id + "'s star on the name of " + b.key + at);
                    }
                    for (ResearchTree.Node o : ResearchTree.all()) {
                        if (o == n) continue;
                        for (double[] s : star(o, m))
                            assertFalse(meet(name, s), n.id + "'s name on " + o.id + "'s star" + at);
                        assertFalse(meet(name, name(o, m, lang)), n.id + "'s name on " + o.id + "'s" + at);
                    }
                }
            }
        }
    }

    /**
     * A line between two stars (a node and one it requires) never runs through a third star, so the map shows no
     * requirement that is not there (0.13.0: the line from the echo crystal to the Arcane Court ran through the
     * library's star, and the one from phase II to the Echo Hunting Ground through the eco dome's). The frame's spine
     * is the one exception: phase II's line down to the dome runs through the inner ring and the anchor, the chain
     * phase II itself stands on.
     */
    @Test
    void noLinkRunsThroughAThirdStar() {
        for (ResearchTree.Node n : ResearchTree.all()) for (String id : n.requires) {
            ResearchTree.Node r = ResearchTree.get(id);
            if (r.id.equals(ResearchTree.PHASE_2) && n.id.equals(ResearchTree.DOME)) continue;
            for (ResearchTree.Node m : ResearchTree.all()) {
                if (m == n || m == r) continue;
                double d = fromSegment(m.x, m.y, r.x, r.y, n.x, n.y);
                assertTrue(d >= LINK_CLEAR, r.id + " -> " + n.id + " passes " + d + " from " + m.id);
            }
        }
    }

    @Test
    void everyNodeOfThisPhaseCanBeReachedAndCosts() {
        Set<String> done = new HashSet<>();
        done.add(ResearchTree.ANCHOR);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (ResearchTree.Node n : ResearchTree.all())
                if (ResearchTree.check(n, done, ResearchTree.PHASE_NOW, 1000) == ResearchTree.Block.NONE) {
                    assertTrue(n.compute > 0, n.id + " takes compute");
                    assertFalse(n.costs.isEmpty(), n.id + " takes items");
                    grew |= done.add(n.id);
                }
        }
        for (ResearchTree.Node n : ResearchTree.all()) {
            if (n.phase <= ResearchTree.PHASE_NOW) assertTrue(done.contains(n.id), n.id + " reachable");
            else assertFalse(done.contains(n.id), n.id + " waits for a later phase");
        }
    }

    @Test
    void blocks() {
        ResearchTree.Node echo = ResearchTree.get(ResearchTree.ECHO_CRYSTAL);
        Set<String> anchor = Collections.singleton(ResearchTree.ANCHOR);
        assertEquals(ResearchTree.Block.REQUIRES, ResearchTree.check(echo, Collections.emptySet(), 1, 10));
        assertEquals(ResearchTree.Block.RECORDS, ResearchTree.check(echo, anchor, 1, 4));
        assertEquals(ResearchTree.Block.NONE, ResearchTree.check(echo, anchor, 1, 5));
        assertEquals(ResearchTree.Block.DONE, ResearchTree.check(ResearchTree.get(ResearchTree.ANCHOR), anchor, 1, 0));
        assertEquals(
            ResearchTree.Block.LATER_PHASE,
            ResearchTree.check(ResearchTree.get(ResearchTree.DOME), anchor, 1, 0));
    }

    /** The Eco Dome's research (0.11.0): phase I, after the inner ring and the echo crystal, with real costs. */
    @Test
    void theEcoDome() {
        ResearchTree.Node n = ResearchTree.get(ResearchTree.ECO_DOME);
        assertNotNull(n);
        assertEquals(ResearchTree.Branch.ECHO, n.branch);
        assertEquals(1, n.phase);
        assertEquals(2, ResearchTree.PHASE_NOW, "0.12.0 opens phase II");
        assertTrue(ResearchTree.availableNow(n));
        assertEquals(java.util.Arrays.asList(ResearchTree.INNER_RING, ResearchTree.ECHO_CRYSTAL), n.requires);
        assertEquals(16_000, n.compute);
        assertEquals(3, n.costs.size());
        assertEquals("gemEchoCrystal", n.costs.get(0).name);
        assertEquals(4, n.costs.get(0).count);
        assertEquals("treeSapling", n.costs.get(1).name);
        assertEquals(16, n.costs.get(1).count);
        assertEquals("blockGlass", n.costs.get(2).name);
        assertEquals(32, n.costs.get(2).count);
        Set<String> ring = new HashSet<>();
        ring.add(ResearchTree.ANCHOR);
        ring.add(ResearchTree.INNER_RING);
        assertEquals(ResearchTree.Block.REQUIRES, ResearchTree.check(n, ring, 1, 999));
        ring.add(ResearchTree.ECHO_CRYSTAL);
        assertEquals(ResearchTree.Block.NONE, ResearchTree.check(n, ring, 1, 999));
    }

    /**
     * Phase II made real (0.12.0, CITY.md §5.2 and Appendix B): its node is researched on a nexus of phase I and opens
     * phase II; the tidal well and the dome need it and a nexus of phase II; all three cost titanium plates and EV
     * circuits.
     */
    @Test
    void phaseTwo() {
        ResearchTree.Node p2 = ResearchTree.get(ResearchTree.PHASE_2), dome = ResearchTree.get(ResearchTree.DOME),
            well = ResearchTree.get(ResearchTree.TIDAL_WELL);
        assertEquals(ResearchTree.PHASE_2, ResearchTree.phaseResearch(2));
        assertEquals(2, ResearchTree.phaseOf(ResearchTree.PHASE_2));
        assertEquals(0, ResearchTree.phaseOf(ResearchTree.DOME));
        assertEquals(0, ResearchTree.phaseOf(null));
        assertEquals(0, ResearchTree.phaseOf("phase_x"));
        assertEquals(2, p2.phase);
        assertEquals(1, p2.nexusPhase(), "researched at phase I");
        assertEquals(2, dome.nexusPhase());
        assertEquals(2, well.nexusPhase());
        assertEquals(ResearchTree.Branch.FRAME, p2.branch);
        assertEquals(ResearchTree.Branch.GUARD, dome.branch);
        assertEquals(ResearchTree.Branch.ENERGY, well.branch);
        assertEquals(java.util.Arrays.asList(ResearchTree.INNER_RING), p2.requires);
        assertEquals(java.util.Arrays.asList(ResearchTree.PHASE_2), dome.requires);
        assertEquals(java.util.Arrays.asList(ResearchTree.PHASE_2, ResearchTree.UPKEEP_1), well.requires);
        for (ResearchTree.Node n : new ResearchTree.Node[] { p2, dome, well }) {
            assertTrue(n.compute >= 30_000, n.id);
            Set<String> names = new HashSet<>();
            for (ResearchTree.Cost c : n.costs) names.add(c.name);
            assertTrue(names.contains("plateTitanium") && names.contains("circuitEV"), n.id + ": EV hull and circuits");
            // the phase's own materials (0.12.0, BLUEPRINT 3.6): phase alloy for all three
            assertTrue(names.contains("ingotPhaseAlloy"), n.id + ": phase alloy");
        }
        // resonant glass is manifested once phase II is researched, so only the nodes after it can ask for it
        for (ResearchTree.Node n : new ResearchTree.Node[] { dome, well }) {
            boolean glass = false;
            for (ResearchTree.Cost c : n.costs) glass |= c.ore && "glassResonant".equals(c.name);
            assertTrue(glass, n.id + ": resonant glass");
        }
        for (ResearchTree.Cost c : p2.costs) assertFalse("glassResonant".equals(c.name), "phase II cannot need it");
        Set<String> done = new HashSet<>();
        done.add(ResearchTree.ANCHOR);
        done.add(ResearchTree.INNER_RING);
        assertEquals(ResearchTree.Block.NONE, ResearchTree.check(p2, done, 1, 999), "phase II starts at phase I");
        done.add(ResearchTree.PHASE_2);
        assertEquals(ResearchTree.Block.LATER_PHASE, ResearchTree.check(dome, done, 1, 999), "the dome needs phase II");
        assertEquals(ResearchTree.Block.NONE, ResearchTree.check(dome, done, 2, 999));
    }

    /**
     * The Arcane Court's research (0.13.0, CITY.md Appendix B): a phase II node on the echo branch after phase II and
     * the echo crystal, researched on a nexus of phase II, with the phase's EV costs and the enchanting table it scales
     * up (vanilla, so no optional mod's item stands in a cost).
     */
    @Test
    void theArcaneCourt() {
        ResearchTree.Node n = ResearchTree.get(ResearchTree.ARCANE_COURT);
        assertNotNull(n);
        assertEquals(ResearchTree.Branch.ECHO, n.branch);
        assertEquals(2, n.phase);
        assertEquals(2, n.nexusPhase());
        assertTrue(ResearchTree.availableNow(n));
        assertEquals(java.util.Arrays.asList(ResearchTree.PHASE_2, ResearchTree.ECHO_CRYSTAL), n.requires);
        assertEquals(30_000, n.compute);
        assertEquals(0, ResearchTree.phaseOf(ResearchTree.ARCANE_COURT), "it opens no phase");
        Set<String> names = new HashSet<>();
        for (ResearchTree.Cost c : n.costs) {
            names.add(c.name);
            if (!c.ore) assertTrue(c.name.startsWith("minecraft:"), "a vanilla item: " + c.name);
        }
        assertTrue(names.contains("plateTitanium") && names.contains("circuitEV") && names.contains("ingotPhaseAlloy"));
        assertTrue(names.contains("minecraft:enchanting_table"));
        Set<String> done = new HashSet<>();
        done.add(ResearchTree.ANCHOR);
        done.add(ResearchTree.INNER_RING);
        done.add(ResearchTree.PHASE_2);
        assertEquals(ResearchTree.Block.REQUIRES, ResearchTree.check(n, done, 2, 999), "the echo crystal first");
        done.add(ResearchTree.ECHO_CRYSTAL);
        assertEquals(ResearchTree.Block.LATER_PHASE, ResearchTree.check(n, done, 1, 999), "a nexus of phase II");
        assertEquals(ResearchTree.Block.NONE, ResearchTree.check(n, done, 2, 999));
        assertEquals(ResearchTree.ARCANE_COURT, com.fluxecho.logic.ArcaneCourtShape.KEY, "the module is its node");
    }

    /**
     * The other three pavilions' research (0.13.0, CITY.md Appendix B): like the Arcane Court's, phase II nodes on the
     * echo branch after phase II and the echo crystal, with the phase's EV costs and one themed vanilla item each (the
     * Blood Covenant Altar's nether wart, the Echo Hunting Ground's bows, the Mana Fountain Array's poppies); every
     * node is its module's key, and the four pavilions stand apart on the star map.
     */
    @Test
    void theOtherPavilions() {
        String[] ids = { ResearchTree.BLOOD_COVENANT, ResearchTree.HUNTING_GROUND, ResearchTree.MANA_FOUNTAIN };
        String[] themed = { "minecraft:nether_wart", "minecraft:bow", "minecraft:red_flower" };
        String[] keys = { BloodCovenantShape.KEY, HuntingGroundShape.KEY, ManaFountainShape.KEY };
        for (int i = 0; i < ids.length; i++) {
            ResearchTree.Node n = ResearchTree.get(ids[i]);
            assertNotNull(n, ids[i]);
            assertEquals(keys[i], n.id, "the module is its node");
            assertEquals(ResearchTree.Branch.ECHO, n.branch);
            assertEquals(2, n.phase);
            assertTrue(ResearchTree.availableNow(n), "research all grants it");
            assertEquals(java.util.Arrays.asList(ResearchTree.PHASE_2, ResearchTree.ECHO_CRYSTAL), n.requires);
            assertEquals(30_000, n.compute);
            assertEquals(0, ResearchTree.phaseOf(ids[i]), "it opens no phase");
            Set<String> names = new HashSet<>();
            for (ResearchTree.Cost c : n.costs) {
                names.add(c.name);
                if (!c.ore) assertTrue(c.name.startsWith("minecraft:"), "a vanilla item: " + c.name);
            }
            assertTrue(
                names.contains("plateTitanium") && names.contains("circuitEV") && names.contains("ingotPhaseAlloy"));
            assertTrue(names.contains(themed[i]), ids[i] + " takes " + themed[i]);
            Set<String> done = new HashSet<>();
            done.add(ResearchTree.ANCHOR);
            done.add(ResearchTree.INNER_RING);
            done.add(ResearchTree.PHASE_2);
            assertEquals(ResearchTree.Block.REQUIRES, ResearchTree.check(n, done, 2, 999), "the echo crystal first");
            done.add(ResearchTree.ECHO_CRYSTAL);
            assertEquals(ResearchTree.Block.LATER_PHASE, ResearchTree.check(n, done, 1, 999), "a nexus of phase II");
            assertEquals(ResearchTree.Block.NONE, ResearchTree.check(n, done, 2, 999));
        }
        Set<String> now = new HashSet<>();
        for (ResearchTree.Node n : ResearchTree.availableNow()) now.add(n.id);
        assertTrue(now.contains(ResearchTree.ARCANE_COURT) && now.containsAll(java.util.Arrays.asList(ids)));
    }

    @Test
    void costs() {
        ResearchTree.Cost c = ResearchTree.Cost.parse("ore:gemFluxCrystal*16");
        assertTrue(c.ore);
        assertEquals("gemFluxCrystal", c.name);
        assertEquals(16, c.count);
        c = ResearchTree.Cost.parse("item:minecraft:dye@4*3");
        assertFalse(c.ore);
        assertEquals("minecraft:dye", c.name);
        assertEquals(4, c.meta);
        assertEquals(3, c.count);
        assertEquals(1, ResearchTree.Cost.parse("item:minecraft:book").count);
        assertThrows(IllegalArgumentException.class, () -> ResearchTree.Cost.parse("book*2"));
        assertThrows(IllegalArgumentException.class, () -> ResearchTree.Cost.parse("item:book*2"));
        assertThrows(IllegalArgumentException.class, () -> ResearchTree.Cost.parse("ore:x*0"));
    }

    @Test
    void scaledCompute() {
        ResearchTree.Node n = ResearchTree.get(ResearchTree.LIBRARY);
        assertEquals(n.compute / 2, ResearchTree.computeFor(n, 0.5));
        assertEquals(1, ResearchTree.computeFor(n, 1e-9));
        assertEquals(0, ResearchTree.computeFor(ResearchTree.get(ResearchTree.ANCHOR), 1));
    }
}
