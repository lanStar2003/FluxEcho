package com.fluxecho.logic.skin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The box table (style §3.9): its entries, the state machine with its hold and its 200-tick power debounce, the
 * rebuild throttle (one rebuild a building every 10 seconds, four boxes a second for the city), the codec both ends
 * share, the lookup the renderers make, and the developing sweep. Pure logic: every number is the style sheet's.
 */
class SkinTableTest {

    private static final long NEXUS = SkinEntry.pack(0, 64, 0);

    // ---- keys: the Buildings rows

    /** The entry's key is the building's {@code Buildings} row (ids.md §2), never the specs' registration order. */
    @Test
    void theKeysAreTheBuildingsRowsOfTheLedger() throws IOException {
        List<String> ledger = new ArrayList<>();
        Pattern row = Pattern.compile("^\\| (\\d\\d) \\| ([a-z_]+) \\|");
        boolean in = false;
        for (String l : Files.readAllLines(new File("docs/dev/ids.md").toPath(), StandardCharsets.UTF_8)) {
            if (l.startsWith("## 2.")) in = true;
            else if (l.startsWith("## 3.")) break;
            Matcher m = row.matcher(l);
            if (in && m.find()) {
                assertEquals(ledger.size(), Integer.parseInt(m.group(1)), "rows in order");
                ledger.add(m.group(2));
            }
        }
        assertEquals(22, ledger.size(), "rows 00 to 21");
        assertEquals(ledger, SkinKeys.keys());
        for (int i = 0; i < ledger.size(); i++) {
            assertEquals(i, SkinKeys.row(ledger.get(i)));
            assertEquals(ledger.get(i), SkinKeys.key(i));
        }
        assertEquals(1, SkinKeys.row("nexus"));
        assertEquals(2, SkinKeys.row("library"));
        assertEquals(-1, SkinKeys.row("no_such_building"));
        assertNull(SkinKeys.key(22));
        assertNull(SkinKeys.key(-1));
    }

    // ---- the state machine (§3.9.4)

    @Test
    void aBuildingIsBuiltThenFormed() {
        SkinTrack t = SkinTrack.building(NEXUS, "eco_dome", "main", 2, new int[] { 10, 64, 10 }, box());
        assertEquals(SkinState.BUILDING, t.state());
        t.observe(100, false, false, false);
        assertEquals(SkinState.BUILDING, t.state(), "still building");
        assertEquals(0, t.entry().formedAt);
        t.observe(140, true, true, false);
        assertEquals(SkinState.FORMED, t.state(), "first formed");
        assertEquals(140, t.entry().formedAt, "the developing sweep runs from this tick");
    }

    @Test
    void anOldBuildingFirstSeenFormedIsFormedLongAgo() {
        SkinTrack t = SkinTrack.seen(NEXUS, "eco_dome", "main", 2, new int[] { 10, 64, 10 }, box(), true, true, 500);
        assertEquals(SkinState.FORMED, t.state());
        assertEquals(0, t.entry().formedAt, "no sweep for a building that stood before the table knew it");
        assertTrue(t.entry().powered, "the power first seen is taken at once");
        SkinTrack broken = SkinTrack
            .seen(NEXUS, "eco_dome", "main", 2, new int[] { 10, 64, 10 }, box(), false, false, 500);
        assertEquals(SkinState.HOLD, broken.state(), "a recorded building found apart is on hold");
    }

    @Test
    void holdComesBackOrGoesDormantAfterSixtySeconds() {
        SkinTrack t = formed();
        t.observe(1000, false, false, false);
        assertEquals(SkinState.HOLD, t.state(), "taken apart: hold");
        t.observe(1000 + 600, true, true, false);
        assertEquals(SkinState.FORMED, t.state(), "put back: formed again");
        assertEquals(0, t.entry().formedAt, "no second sweep");

        t.observe(3000, false, false, false);
        t.observe(3000 + 1199, false, false, false);
        assertEquals(SkinState.HOLD, t.state(), "59.95 s: still on hold");
        t.observe(3000 + 1200, false, false, false);
        assertEquals(SkinState.DORMANT, t.state(), "60 s apart and no repair: dormant");
        t.observe(9000, true, true, false);
        assertEquals(SkinState.FORMED, t.state(), "formed again");
    }

    @Test
    void aRepairWaitingKeepsTheHold() {
        SkinTrack t = formed();
        t.observe(1000, false, false, false);
        // the campus queues its repair 200 ticks after the building fell apart; a repair queued or running keeps it
        t.observe(1000 + 5000, false, false, true);
        assertEquals(SkinState.HOLD, t.state(), "a repair is queued: hold however long");
        t.observe(1000 + 5020, false, false, false);
        assertEquals(SkinState.DORMANT, t.state(), "the repair is gone: dormant");
    }

    @Test
    void theLeapAscendsThenRemembers() {
        SkinTrack t = formed();
        t.ascend();
        assertEquals(SkinState.ASCENDING, t.state());
        t.observe(2000, false, false, false);
        assertEquals(SkinState.ASCENDING, t.state(), "the leap's states ignore the structure");
        t.memorialize();
        assertEquals(SkinState.MEMORIAL, t.state());
        t.observe(3000, false, false, false);
        t.observe(9000, true, true, false);
        assertEquals(SkinState.MEMORIAL, t.state(), "for ever");
        SkinTrack b = formed();
        b.memorialize();
        assertEquals(SkinState.FORMED, b.state(), "only an ascending building becomes a memorial");
    }

    @Test
    void theStateCodesNeverMove() {
        // saved in Skn and sent: the codes are the ledger's, append only
        assertEquals(0, SkinState.BUILDING.code);
        assertEquals(1, SkinState.FORMED.code);
        assertEquals(2, SkinState.HOLD.code);
        assertEquals(3, SkinState.DORMANT.code);
        assertEquals(4, SkinState.ASCENDING.code);
        assertEquals(5, SkinState.MEMORIAL.code);
        for (SkinState s : SkinState.values()) assertSame(s, SkinState.of(s.code));
        assertSame(SkinState.DORMANT, SkinState.of(99), "a code from a later version reads as dormant");
    }

    // ---- power: 200 ticks steady, at most once in 10 seconds

    @Test
    void powerChangesOnlyAfterTwoHundredSteadyTicks() {
        SkinTrack t = formed();
        assertTrue(t.entry().powered);
        t.observe(1000, true, false, false);
        assertTrue(t.entry().powered, "a dip is not sent");
        t.observe(1100, true, true, false);
        t.observe(1120, true, false, false);
        t.observe(1300, true, false, false);
        assertTrue(t.entry().powered, "180 ticks off: not yet");
        t.observe(1320, true, false, false);
        assertFalse(t.entry().powered, "200 ticks off: off");
        // back on at once, steady: the flip waits 200 ticks and at least 10 s after the last
        t.observe(1340, true, true, false);
        t.observe(1539, true, true, false);
        assertFalse(t.entry().powered);
        t.observe(1540, true, true, false);
        assertTrue(t.entry().powered);
    }

    @Test
    void aBuildingApartKeepsItsPowerBit() {
        SkinTrack t = formed();
        // apart, its tile cannot be powered (it is not formed): the hold looks as formed, so the bit stays
        for (long now = 1000; now < 1000 + 1100; now += 20) t.observe(now, false, false, false);
        assertEquals(SkinState.HOLD, t.state());
        assertTrue(t.entry().powered, "the hold keeps the bit it had");
    }

    @Test
    void theDebounceAloneLimitsAFlipToOnceInTenSeconds() {
        PowerDebounce d = new PowerDebounce(false);
        int flips = 0;
        boolean last = d.value();
        // power flapping every 100 ticks never gets through
        for (long now = 0; now < 4000; now += 20) {
            d.update((now / 100) % 2 == 0, now);
            if (d.value() != last) flips++;
            last = d.value();
        }
        assertEquals(0, flips);
        // steady on, steady off, steady on: flips at least 200 ticks apart
        long lastFlip = -1;
        for (long now = 4000; now < 8000; now += 20) {
            d.update((now / 1000) % 2 == 0, now);
            if (d.value() != last) {
                if (lastFlip >= 0) assertTrue(now - lastFlip >= PowerDebounce.STEADY, "at most once in 10 s");
                lastFlip = now;
            }
            last = d.value();
        }
        assertTrue(lastFlip > 0);
        assertEquals(200, PowerDebounce.STEADY);
        assertEquals(200, PowerDebounce.MIN_GAP);
    }

    // ---- the rebuild throttle (§3.9.10)

    @Test
    void oneRebuildABuildingInTenSeconds() {
        RebuildQueue q = new RebuildQueue();
        q.request(7, 0);
        assertEquals(Collections.singletonList(7L), q.poll(0));
        q.request(7, 20);
        q.request(7, 40);
        assertEquals(Collections.emptyList(), q.poll(40), "merged, and waiting");
        assertEquals(1, q.size(), "one request however many came");
        assertEquals(Collections.emptyList(), q.poll(199));
        assertEquals(Collections.singletonList(7L), q.poll(200), "10 s after the last");
        assertEquals(0, q.size());
        assertEquals(200, RebuildQueue.PER_BUILDING);
    }

    @Test
    void fourBoxesASecondForTheCity() {
        RebuildQueue q = new RebuildQueue();
        for (long id = 0; id < 20; id++) q.request(id, 0);
        List<Long> done = new ArrayList<>();
        int[] perSecond = new int[10];
        for (long now = 0; now < 200; now++) {
            List<Long> got = q.poll(now);
            perSecond[(int) (now / 20)] += got.size();
            done.addAll(got);
        }
        // first come first rebuilt, four in every second: 20 buildings in five seconds (§3.9.12)
        List<Long> order = new ArrayList<>();
        for (long id = 0; id < 20; id++) order.add(id);
        assertEquals(order, done);
        assertArrayEquals(new int[] { 4, 4, 4, 4, 4, 0, 0, 0, 0, 0 }, perSecond);
        // and never more than four in any 20 ticks
        q = new RebuildQueue();
        for (long id = 0; id < 50; id++) q.request(id, 0);
        List<Long> times = new ArrayList<>();
        for (long now = 0; now < 400; now++) for (Long ignored : q.poll(now)) times.add(now);
        for (int i = 4; i < times.size(); i++)
            assertTrue(times.get(i) - times.get(i - 4) >= 20, "five within a second at " + times.get(i));
        assertEquals(4, RebuildQueue.PER_SECOND);
    }

    @Test
    void aBuildingWaitingDoesNotHoldUpTheOthers() {
        RebuildQueue q = new RebuildQueue();
        q.request(1, 0);
        assertEquals(Collections.singletonList(1L), q.poll(0));
        q.request(1, 30);
        q.request(2, 30);
        assertEquals(Collections.singletonList(2L), q.poll(30), "building 1 waits its 10 s, 2 goes");
        q.forget(1);
        assertEquals(0, q.size(), "a building gone leaves the queue");
    }

    // ---- entries, the table, the codec

    @Test
    void entriesRoundTripThroughTheCodec() throws IOException {
        SkinEntry a = sample();
        SkinEntry b = SkinEntry.builder()
            .campus(NEXUS)
            .key("nexus")
            .shape("phase3")
            .facing(5)
            .ctrl(0, 64, 0)
            .box(-12, 62, -12, 12, 90, 12)
            .state(SkinState.HOLD)
            .powered(false)
            .formedAt(0)
            .accent(SkinPalette.NEXUS_CYAN)
            .build();
        SkinCodec.Table t = new SkinCodec.Table(-1, true, new long[] { NEXUS }, Arrays.asList(a, b));
        SkinCodec.Table back = SkinCodec.readTable(SkinCodec.write(t));
        assertEquals(-1, back.dim);
        assertTrue(back.reset);
        assertArrayEquals(new long[] { NEXUS }, back.campuses);
        assertEquals(Arrays.asList(a, b), back.entries);
        assertArrayEquals(a.extra, back.entries.get(0).extra);
        assertArrayEquals(a.lamp, back.entries.get(0).lamp);
        assertNull(back.entries.get(1).lamp, "no lamp box");

        SkinCodec.Delta put = SkinCodec.readDelta(SkinCodec.write(SkinCodec.Delta.put(7, a)));
        assertEquals(7, put.dim);
        assertEquals(a, put.entry);
        SkinCodec.Delta gone = SkinCodec.readDelta(SkinCodec.write(SkinCodec.Delta.remove(7, NEXUS, a.id())));
        assertNull(gone.entry);
        assertEquals(NEXUS, gone.campus);
        assertEquals(a.id(), gone.id);
        assertThrows(IOException.class, () -> SkinCodec.readTable(new byte[] { 99 }), "a version it does not know");
    }

    @Test
    void anEntryIsSmall() throws IOException {
        // about 45 bytes an entry (§3.9.2): ours is a little more with the lamp box and the shape's name
        int n = SkinCodec
            .write(new SkinCodec.Table(0, false, new long[] { NEXUS }, Collections.singletonList(sample()))).length;
        assertTrue(n < 110, n + " bytes");
        byte[] tooLong = new byte[SkinEntry.EXTRA_MAX + 1];
        assertThrows(
            IllegalArgumentException.class,
            () -> SkinEntry.builder()
                .key("leap_ring")
                .extra(tooLong)
                .build(),
            "extra is at most 64 bytes");
    }

    @Test
    void positionsPackAndUnpack() {
        for (int[] p : new int[][] { { 0, 0, 0 }, { -30_000_000, 255, 29_999_999 }, { 12_345, 64, -6789 },
            { -1, 1, -1 } }) {
            long k = SkinEntry.pack(p[0], p[1], p[2]);
            assertArrayEquals(p, SkinEntry.unpack(k));
        }
        assertNotEquals(SkinEntry.pack(1, 2, 3), SkinEntry.pack(3, 2, 1));
    }

    @Test
    void theTableFindsTheSmallestBoxHoldingACell() {
        SkinEntry big = SkinEntry.builder()
            .campus(NEXUS)
            .key("dome_generator")
            .shape("moored")
            .ctrl(0, 110, 0)
            .box(-20, 64, -20, 20, 120, 20)
            .lamp(-18, 100, -18, 18, 120, 18)
            .build();
        SkinEntry small = SkinEntry.builder()
            .campus(NEXUS)
            .key("nexus")
            .shape("phase2")
            .ctrl(0, 66, 0)
            .box(-8, 64, -8, 8, 75, 8)
            .lamp(0, 64, 0, 0, 64, 0)
            .build();
        SkinTable t = SkinTable.of(Arrays.asList(big, small));
        assertSame(small, t.find(0, 70, 0), "inside both: the smaller box");
        assertSame(big, t.find(15, 70, 15));
        assertNull(t.find(30, 70, 30));
        assertSame(small, t.findLamp(0, 64, 0));
        assertSame(big, t.findLamp(5, 110, 5));
        assertNull(t.findLamp(5, 70, 5), "in the dome's box but not where its lamps are");
    }

    @Test
    void theTableReplacesByCampusAndBuilding() {
        SkinEntry a = sample();
        SkinTable t = SkinTable.EMPTY.put(a);
        assertEquals(1, t.size());
        SkinEntry a2 = a.toBuilder()
            .state(SkinState.HOLD)
            .build();
        t = t.put(a2);
        assertEquals(Collections.singletonList(a2), t.entries(), "the same building replaced");
        long other = SkinEntry.pack(5000, 64, 5000);
        SkinEntry c = a.toBuilder()
            .campus(other)
            .ctrl(5010, 64, 5010)
            .build();
        t = t.put(c);
        assertEquals(2, t.size());
        t = t.replaceCampuses(new long[] { NEXUS }, Collections.emptyList());
        assertEquals(Collections.singletonList(c), t.entries(), "the campus's whole table replaced");
        t = t.remove(other, c.id());
        assertEquals(0, t.size());
        assertSame(SkinTable.EMPTY.remove(NEXUS, 1), SkinTable.EMPTY);
    }

    // ---- the client's view: what it draws, and when a lamp flips

    @Test
    void theClientDrawsWhatItWasToldAndFlipsLampsThroughTheThrottle() {
        SkinEntry dome = sample().toBuilder()
            .ctrl(64, 66, 0)
            .box(54, 64, -10, 74, 90, 10)
            .lamp(56, 70, -8, 72, 88, 8)
            .build();
        // a table arrives by day: the lamps are drawn dark at once, and the dome's lamp box is rebuilt once
        SkinView.Change c = SkinView.EMPTY
            .table(new SkinCodec.Table(0, true, new long[] { NEXUS }, Collections.singletonList(dome)), 6000);
        SkinView v = c.view;
        assertEquals(Collections.singletonList(Arrays.toString(dome.lamp)), boxes(c));
        int pilaster = com.fluxecho.logic.Parts.deck(com.fluxecho.logic.Parts.D_PANEL_LIT);
        assertEquals(LampRule.DARK, v.glow(60, 75, 0, pilaster));
        assertEquals(
            LampRule.UNTOUCHED,
            v.glow(60, 75, 0, com.fluxecho.logic.Parts.deck(com.fluxecho.logic.Parts.D_LIT)));
        assertEquals(LampRule.UNTOUCHED, v.glow(200, 75, 0, pilaster), "no entry here: as 0.16 drew it");
        assertEquals(Collections.emptyList(), v.due(6000));

        // dusk: the dome is 64 blocks out, its lamps are due two seconds after 12,500
        assertEquals(Collections.emptyList(), v.due(12_539));
        assertEquals(Collections.singletonList(dome.id()), v.due(12_540));
        RebuildQueue q = new RebuildQueue();
        for (long id : v.due(12_540)) q.request(id, 1000);
        c = v.flip(q.poll(1000), 12_540);
        v = c.view;
        assertEquals(Collections.singletonList(Arrays.toString(dome.lamp)), boxes(c), "only the lamp box is rebuilt");
        assertEquals(dome.accent, v.glow(60, 75, 0, pilaster), "lit in the dome's accent");
        assertEquals(Collections.emptyList(), v.due(12_600));

        // the power goes off (a delta): the lamps go dark through the queue, not at once
        c = v.delta(
            SkinCodec.Delta.put(
                0,
                dome.toBuilder()
                    .powered(false)
                    .build()),
            13_000);
        v = c.view;
        assertEquals(Collections.emptyList(), boxes(c), "a changed entry waits for the throttle");
        assertEquals(dome.accent, v.glow(60, 75, 0, pilaster), "still drawn lit");
        assertEquals(Collections.singletonList(dome.id()), v.due(13_000));
        v = v.flip(Collections.singletonList(dome.id()), 13_000).view;
        assertEquals(LampRule.DARK, v.glow(60, 75, 0, pilaster));

        // removed: its box is rebuilt as 0.16 drew it
        c = v.delta(SkinCodec.Delta.remove(0, NEXUS, dome.id()), 13_100);
        assertEquals(Collections.singletonList(Arrays.toString(dome.lamp)), boxes(c));
        assertEquals(LampRule.UNTOUCHED, c.view.glow(60, 75, 0, pilaster));

        // a table of another dimension starts over
        c = v.table(new SkinCodec.Table(-1, true, new long[0], Collections.emptyList()), 13_200);
        assertEquals(-1, c.view.dim);
        assertEquals(0, c.view.size());
        // a delta of another dimension than the view's is not for it
        assertSame(c.view, c.view.delta(SkinCodec.Delta.put(0, dome), 13_300).view);
    }

    private static List<String> boxes(SkinView.Change c) {
        List<String> out = new ArrayList<>();
        for (int[] b : c.dirty) out.add(Arrays.toString(b));
        return out;
    }

    // ---- developing (§3.9.6)

    @Test
    void aNewBuildingDevelopsFromTheBottomUpInTwoSeconds() {
        SkinEntry building = sample().toBuilder()
            .state(SkinState.BUILDING)
            .box(0, 64, 0, 10, 83, 10)
            .build();
        Profile skin = SkinProfiles.NEW_SHAPE;
        assertEquals(Double.NEGATIVE_INFINITY, SkinDevelop.developedTo(building, skin, 1000), "all blueprint");
        SkinEntry formed = building.toBuilder()
            .state(SkinState.FORMED)
            .formedAt(1000)
            .build();
        assertEquals(64, SkinDevelop.developedTo(formed, skin, 1000), 1e-9, "the sweep starts at the bottom");
        assertEquals(74, SkinDevelop.developedTo(formed, skin, 1020), 1e-9, "half way after one second");
        assertEquals(Double.POSITIVE_INFINITY, SkinDevelop.developedTo(formed, skin, 1040), "done");
        // a late client sees it done; a building formed before the table knew it never sweeps
        assertEquals(Double.POSITIVE_INFINITY, SkinDevelop.developedTo(formed, skin, 9000));
        assertEquals(
            Double.POSITIVE_INFINITY,
            SkinDevelop.developedTo(
                formed.toBuilder()
                    .formedAt(0)
                    .build(),
                skin,
                1000));
        assertEquals(40, SkinDevelop.SWEEP_TICKS, "the forming sweep's 2 s (TileMultiblock.SWEEP_MS)");
        // the old buildings never show blueprint colours (§3.9.9)
        assertFalse(SkinProfiles.LEGACY.develops);
        assertEquals(Double.POSITIVE_INFINITY, SkinDevelop.developedTo(building, SkinProfiles.LEGACY, 1000));
        assertTrue(SkinDevelop.developed(formed, skin, 70, 1020));
        assertFalse(SkinDevelop.developed(formed, skin, 80, 1020));
    }

    @Test
    void blueprintColourHalvesTheSaturation() {
        // HSV saturation × 0.5, hue and value kept
        assertEquals(0xFF8080, SkinDevelop.blueprintColour(0xFF0000));
        assertEquals(0xFFFFFF, SkinDevelop.blueprintColour(0xFFFFFF), "white stays white (the hull is not touched)");
        assertEquals(0x808080, SkinDevelop.blueprintColour(0x808080));
        int c = SkinDevelop.blueprintColour(0x7BE08A);
        float[] hsv = java.awt.Color.RGBtoHSB(0x7B, 0xE0, 0x8A, null),
            half = java.awt.Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, null);
        assertEquals(hsv[1] / 2, half[1], 0.01);
        assertEquals(hsv[2], half[2], 0.01);
        assertEquals(hsv[0], half[0], 0.01);
    }

    // ---- helpers

    static int[] box() {
        return new int[] { 0, 64, 0, 20, 80, 20 };
    }

    static SkinTrack formed() {
        return SkinTrack.seen(NEXUS, "eco_dome", "main", 2, new int[] { 10, 64, 10 }, box(), true, true, 0);
    }

    static SkinEntry sample() {
        return SkinEntry.builder()
            .campus(NEXUS)
            .key("eco_dome")
            .shape("main")
            .facing(3)
            .ctrl(40, 66, -12)
            .box(28, 64, -40, 52, 90, -12)
            .lamp(30, 70, -38, 50, 88, -14)
            .state(SkinState.FORMED)
            .powered(true)
            .formedAt(123_456_789L)
            .accent(SkinPalette.accent("eco_dome"))
            .variant(2)
            .extra(new byte[] { 1, 2, 3 })
            .build();
    }
}
