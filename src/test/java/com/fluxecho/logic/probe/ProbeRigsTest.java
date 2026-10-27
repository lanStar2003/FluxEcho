package com.fluxecho.logic.probe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.occlusion.GateCore;
import com.fluxecho.logic.occlusion.GatePiece;
import com.fluxecho.logic.occlusion.SightCells;
import com.fluxecho.logic.probe.ProbeRigs.Depth;

/**
 * The check-up rigs' frames, marker signs and commands (0.17.0, refactor FINAL F06): a frame snaps the operator's yaw
 * to the compass; a marker sign names its rig and gives its frame back in all four facings; the probe depth rig's
 * pieces are hidden or shown by the occlusion gate as the probe means them to be (P0: behind the wall hidden, behind
 * the window shown); the sun rig's mast and well; the bed rows; the command words.
 */
class ProbeRigsTest {

    @Test
    void aFrameSnapsTheYawToTheCompass() {
        ProbeFrame s = ProbeFrame.facing(0, 64, 0, 10), w = ProbeFrame.facing(0, 64, 0, 100),
            n = ProbeFrame.facing(0, 64, 0, -170), e = ProbeFrame.facing(0, 64, 0, 260);
        assertArrayEquals(new int[] { 0, 1 }, new int[] { s.dx, s.dz }, "yaw 10: south");
        assertArrayEquals(new int[] { -1, 0 }, new int[] { w.dx, w.dz }, "yaw 100: west");
        assertArrayEquals(new int[] { 0, -1 }, new int[] { n.dx, n.dz }, "yaw -170: north");
        assertArrayEquals(new int[] { 1, 0 }, new int[] { e.dx, e.dz }, "yaw 260: east");
        // facing south the right hand is west
        assertArrayEquals(new int[] { -1, 64, 3 }, s.at(3, 1, 0));
        assertEquals(0, s.yaw(0), 1e-4);
        assertEquals(180, Math.abs(s.yaw(2)), 1e-4, "turned round: north");
        for (int m = 0; m < 16; m += 4) {
            int[] f = ProbeFrame.signFacing(m);
            assertEquals(m, ProbeFrame.signMeta(f[0], f[1]));
        }
        assertArrayEquals(new int[] { 0, 1 }, ProbeFrame.signFacing(0), "sign meta 0 faces south");
        assertArrayEquals(new int[] { 1, 0 }, ProbeFrame.signFacing(12), "meta 12 east");
    }

    @Test
    void aMarkerGivesItsRigsFrameBack() {
        for (ProbeMarker.Kind k : ProbeMarker.Kind.values()) for (int yaw = 0; yaw < 360; yaw += 90) {
            ProbeFrame f = ProbeFrame.facing(120, 70, -45, yaw);
            int[] at = ProbeMarker.signAt(k, f);
            int meta = ProbeMarker.signMeta(f);
            int[] faces = ProbeFrame.signFacing(meta);
            assertEquals(-f.dx, faces[0], "the sign's writing faces the operator");
            assertEquals(-f.dz, faces[1]);
            assertEquals(f, ProbeMarker.frame(k, at[0], at[1], at[2], meta), k + " facing " + yaw);
            String[] lines = ProbeMarker.lines(k, "一二三四五六七八九十一二三四五六", "n=12");
            assertSame(k, ProbeMarker.parse(lines));
            for (String l : lines) assertTrue(l.length() <= ProbeMarker.LINE, "a sign shows 15 a line");
        }
        assertNull(ProbeMarker.parse(new String[] { "[FluxEcho]", "probe moon", "", "" }));
        assertNull(ProbeMarker.parse(new String[] { "Welcome", "swatch", "", "" }), "a player's sign is no marker");
        assertNull(ProbeMarker.parse(null));
        assertEquals(12, ProbeMarker.number("n=12", "n", 0));
        assertEquals(7, ProbeMarker.number("x", "n", 7));
        assertEquals(7, ProbeMarker.number("n=x", "n", 7));
        Set<String> cells = new HashSet<>();
        ProbeFrame f = new ProbeFrame(0, 64, 0, 1, 0);
        for (ProbeMarker.Kind k : ProbeMarker.Kind.values()) {
            int[] a = ProbeMarker.signAt(k, f);
            cells.add(a[0] + "," + a[2]);
        }
        assertEquals(
            ProbeMarker.Kind.values().length,
            cells.size(),
            "rigs set out from one spot keep their signs apart");
    }

    /** The depth rig as the eye sees it: the wall solid, the window clear. */
    private static SightCells depthWorld(ProbeFrame f) {
        Set<String> solid = new HashSet<>();
        for (int r = Depth.LEFT; r <= Depth.RIGHT; r++) for (int u = 0; u <= Depth.TOP; u++) if (!Depth.window(r, u)) {
            int[] c = f.at(Depth.WALL, r, u);
            solid.add(c[0] + "," + c[1] + "," + c[2]);
        }
        return (x, y, z) -> solid.contains(x + "," + y + "," + z) ? SightCells.SOLID : SightCells.CLEAR;
    }

    @Test
    void theDepthProbesPiecesAreHiddenAndShownAsMeant() {
        for (int yaw = 0; yaw < 360; yaw += 90) {
            ProbeFrame f = ProbeFrame.facing(10, 64, 20, yaw);
            int[] sign = ProbeMarker.signAt(ProbeMarker.Kind.DEPTH, f);
            List<GatePiece> pieces = new ArrayList<>();
            for (Depth.Piece p : Depth.Piece.values()) pieces.add(Depth.gatePiece(f, sign, p));
            double[] eye = f.at(0.0, 0.0, 1.62);
            GateCore g = new GateCore();
            SightCells w = depthWorld(f);
            for (int i = 0; i < 3; i++) g.round(pieces, eye, w, GateCore.Mode.GATE, new ArrayList<>());
            for (Depth.Piece p : Depth.Piece.values()) {
                boolean shown = g.visible(ProbeRigs.pieceId(sign, p.ordinal()));
                boolean expect = !p.behind() || p == Depth.Piece.BACK_WINDOW;
                assertEquals(expect, shown, p + " facing " + yaw);
            }
        }
        assertTrue(Depth.Piece.BACK_DEPTH.depthTest && !Depth.Piece.BACK_DEPTH.gated, "P0: the depth buffer alone");
        assertTrue(Depth.Piece.BACK_GATE.gated && !Depth.Piece.BACK_GATE.depthTest, "the gate alone");
        assertTrue(Depth.Piece.FRONT_DEPTH.depthTest && !Depth.Piece.FRONT_PLAIN.depthTest);
        assertTrue(Depth.window(Depth.WINDOW_LEFT + 1, Depth.WINDOW_HIGH), "the window is two by two");
        assertFalse(Depth.window(0, 1));
    }

    @Test
    void theSunRigsMastAndWell() {
        assertEquals(
            ProbeRigs.Sun.MAST_HEIGHT,
            ProbeRigs.Sun.mast()
                .size());
        assertEquals(
            8 * ProbeRigs.Sun.WELL_HEIGHT,
            ProbeRigs.Sun.well()
                .size(),
            "a ring of eight round the open middle");
        ProbeFrame f = new ProbeFrame(0, 64, 0, 0, 1);
        double[] top = ProbeRigs.Sun.mastTop(f);
        assertEquals(64 + 12, top[1], 1e-9, "the line leaves the mast's top");
        int[] v = ProbeRigs.Sun.VOLUME;
        for (int[] c : ProbeRigs.Sun.mast()) assertTrue(c[0] >= v[0] && c[0] <= v[3] && c[2] <= v[5]);
        for (int[] c : ProbeRigs.Sun.well()) assertTrue(c[1] >= v[1] && c[1] <= v[4]);
        assertNotNull(ProbeRigs.Sun.sail(f));
    }

    @Test
    void theBedRows() {
        assertArrayEquals(new int[] { 3, -8, 0 }, ProbeRigs.Bed.bed(0));
        assertArrayEquals(new int[] { 3, 7, 0 }, ProbeRigs.Bed.bed(15));
        assertArrayEquals(new int[] { 5, -8, 0 }, ProbeRigs.Bed.bed(16), "a walkway between the rows");
        assertArrayEquals(new int[] { 3, -8, 0, 7, 7, 3 }, ProbeRigs.Bed.volume(40));
        int[] sign = ProbeMarker.signAt(ProbeMarker.Kind.BED, new ProbeFrame(0, 64, 0, 0, 1));
        int[] v = ProbeRigs.Bed.volume(200);
        int along = ProbeMarker.Kind.BED.along;
        assertTrue(along < v[0] || ProbeMarker.Kind.BED.right < v[1], "the bed rig's sign stands outside its rows");
        assertEquals(64, sign[1]);
    }

    @Test
    void theCommandWords() {
        assertTrue(ProbeArgs.claims(new String[] { "Probe", "depth" }));
        assertFalse(ProbeArgs.claims(new String[] { "build", "free" }));
        assertSame(ProbeArgs.Action.PROBE_DEPTH, ProbeArgs.parse(new String[] { "probe", "DEPTH" }).action);
        assertSame(ProbeArgs.Action.PROBE_SKY, ProbeArgs.parse(new String[] { "probe", "sky" }).action);
        assertSame(ProbeArgs.Action.PROBE_SUN, ProbeArgs.parse(new String[] { "probe", "sun" }).action);
        assertSame(ProbeArgs.Action.PROBE_BED, ProbeArgs.parse(new String[] { "probe", "bed" }).action);
        assertSame(ProbeArgs.Action.SWATCH, ProbeArgs.parse(new String[] { "swatch" }).action);
        assertSame(ProbeArgs.Action.SWATCH_CLEAR, ProbeArgs.parse(new String[] { "swatch", "clear" }).action);
        ProbeArgs.Parsed cam = ProbeArgs.parse(new String[] { "cam", "c" });
        assertSame(ProbeArgs.Action.CAM, cam.action);
        assertSame(CamPoints.Point.C, cam.point);
        assertNull(ProbeArgs.parse(new String[] { "probe" }));
        assertNull(ProbeArgs.parse(new String[] { "probe", "moon" }));
        assertNull(ProbeArgs.parse(new String[] { "swatch", "2" }), "the second swatch comes with 0.20.0");
        assertNull(ProbeArgs.parse(new String[] { "cam", "F" }));
        assertNull(ProbeArgs.parse(new String[] { "cam" }));
        assertEquals(java.util.Arrays.asList("probe", "swatch", "cam"), ProbeArgs.complete(new String[] { "" }));
        assertEquals(java.util.Arrays.asList("sky", "sun"), ProbeArgs.complete(new String[] { "probe", "s" }));
        assertEquals(java.util.Collections.singletonList("clear"), ProbeArgs.complete(new String[] { "swatch", "" }));
        assertEquals(
            5,
            ProbeArgs.complete(new String[] { "cam", "" })
                .size());
    }
}
