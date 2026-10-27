package com.fluxecho.logic.occlusion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The occlusion gate's judgement (style FINAL §8.4.1, refactor FINAL F06): which pieces of the light drawn after the
 * world are hidden behind nearer blocks, worked out by the client itself, so that with a shader pack whose depth buffer
 * is not the world's when the city's light is drawn (prototype P0) not one piece is dropped from the picture and none
 * shows through a wall.
 * <p>
 * <b>A round.</b> Every {@link #EVERY} client ticks (never in a render callback: a frame may draw many times, and every
 * draw must ask the same answer), the pieces are sorted near to far and a line of sight ({@link SightRay}) is cast from
 * the eye to each piece's sample points, centre first, until one is seen; a piece with any point seen is shown, a piece
 * with every point blocked counts a blocked round. At most {@link #MAX_RAYS} lines a round: a piece the budget does not
 * reach keeps its last answer, and pieces farther than {@link #FAR} are judged every second round only. A line that
 * crosses no building's box and starts and ends above the ground is seen without a cast (the coarse test).
 * <p>
 * <b>Hysteresis.</b> A piece hides after {@link #HIDE_AFTER} blocked rounds in a row and shows again after one round
 * seen, so a piece at a wall's edge does not flicker; a piece new to the gate is shown.
 * <p>
 * <b>Indoor pieces</b> (rule #12) are shown exactly while the eye is inside their box and cost no line.
 * <p>
 * <b>Modes</b> ({@code render.occlusion}): {@link Mode#GATE} judges every piece; {@link Mode#DEPTH} leaves the pieces
 * nearer than {@link #FAR} to the depth buffer (always shown here) and judges only the far ones, which a building or a
 * hill in between hides cheaper than drawing them.
 * <p>
 * The answers are published as one immutable map at the end of a round, so every query between two rounds, from any
 * thread and any number of times a frame, gives the same answer.
 */
public final class GateCore {

    /** Client ticks between two rounds. */
    public static final int EVERY = 4;
    /** The most lines of sight one round casts. */
    public static final int MAX_RAYS = 400;
    /** Blocked rounds in a row before a piece hides. */
    public static final int HIDE_AFTER = 2;
    /** Beyond this many blocks a piece is judged every second round (and, in depth mode, only beyond it). */
    public static final double FAR = 64;

    /** How the pieces are hidden: by this gate, or by the depth buffer with the gate for the far ones. */
    public enum Mode {
        GATE,
        DEPTH
    }

    /** What one round did. */
    public static final class Report {

        /** Lines cast, cells they walked, pieces judged this round. */
        public int rays, cells, judged;
        /** Pieces the budget did not reach, far pieces left for the next round, lines the coarse test saved. */
        public int deferred, skippedFar, coarse;
    }

    private static final class State {

        int blocked;
        boolean shown = true;
        long seen;
    }

    private final Map<Long, State> states = new HashMap<>();
    private volatile Map<Long, Boolean> shown = Collections.emptyMap();
    private long rounds;

    /** Whether a piece is shown: the last round's answer; true for a piece the gate has not judged. */
    public boolean visible(long id) {
        Boolean b = shown.get(id);
        return b == null || b;
    }

    /** How many rounds have run. */
    public synchronized long rounds() {
        return rounds;
    }

    /** Forgets every piece (the world closed). */
    public synchronized void clear() {
        states.clear();
        shown = Collections.emptyMap();
    }

    /**
     * Runs one round for the pieces the renderers drew last, seen from {@code eye} through {@code w}. {@code boxes} are
     * the buildings' boxes for the coarse test ({x0, y0, z0, x1, y1, z1}, inclusive cells; the pieces' own hosts are
     * added); null casts every line.
     */
    public synchronized Report round(List<GatePiece> pieces, double[] eye, SightCells w, Mode mode, List<int[]> boxes) {
        rounds++;
        Report rep = new Report();
        List<GatePiece> order = new ArrayList<>(pieces);
        order.sort(
            Comparator.comparingDouble((GatePiece p) -> p.distance(eye))
                .thenComparingLong(p -> p.id));
        List<int[]> coarse = null;
        if (boxes != null) {
            coarse = new ArrayList<>(boxes);
            for (GatePiece p : order) if (p.host != null) coarse.add(p.host);
        }
        int rays = 0;
        for (GatePiece p : order) {
            State s = states.computeIfAbsent(p.id, k -> new State());
            s.seen = rounds;
            if (p.interior != null) {
                set(s, inside(eye, p.interior));
                continue;
            }
            double d = p.distance(eye);
            if (mode == Mode.DEPTH && d < FAR || d > SightRay.MAX_LENGTH) {
                set(s, true);
                continue;
            }
            if (d >= FAR && rounds % 2 != 0) {
                rep.skippedFar++;
                continue;
            }
            boolean seen = false, decided = true;
            for (double[] t : p.samples) {
                if (coarse != null && !mayBlock(coarse, w, eye, t)) {
                    rep.coarse++;
                    seen = true;
                    break;
                }
                if (rays >= MAX_RAYS) {
                    decided = false;
                    break;
                }
                rays++;
                SightRay.Hit h = SightRay.cast(w, eye, t);
                rep.cells += h.cells;
                if (!h.blocked) {
                    seen = true;
                    break;
                }
            }
            if (!decided) {
                rep.deferred++;
                continue;
            }
            rep.judged++;
            if (seen) set(s, true);
            else if (++s.blocked >= HIDE_AFTER) s.shown = false;
        }
        rep.rays = rays;
        for (Iterator<State> it = states.values()
            .iterator(); it.hasNext();) if (it.next().seen != rounds) it.remove();
        Map<Long, Boolean> m = new HashMap<>();
        for (Map.Entry<Long, State> e : states.entrySet()) m.put(e.getKey(), e.getValue().shown);
        shown = Collections.unmodifiableMap(m);
        return rep;
    }

    private static void set(State s, boolean seen) {
        s.blocked = 0;
        s.shown = seen;
    }

    private static boolean inside(double[] p, int[] b) {
        return p[0] >= b[0] && p[0] < b[3] + 1 && p[1] >= b[1] && p[1] < b[4] + 1 && p[2] >= b[2] && p[2] < b[5] + 1;
    }

    /**
     * The coarse test: whether a line may be blocked at all, that is whether it crosses one of the boxes or either end
     * lies under the ground.
     */
    static boolean mayBlock(List<int[]> boxes, SightCells w, double[] eye, double[] t) {
        if (under(w, eye) || under(w, t)) return true;
        double dx = t[0] - eye[0], dy = t[1] - eye[1], dz = t[2] - eye[2];
        for (int[] b : boxes) {
            List<double[]> one = Collections.singletonList(
                new double[] { b[0] - eye[0], b[1] - eye[1], b[2] - eye[2], b[3] + 1 - eye[0], b[4] + 1 - eye[1],
                    b[5] + 1 - eye[2] });
            if (SightRay.crosses(one, 0, 0, 0, 0, 0, 0, dx, dy, dz)) return true;
        }
        return false;
    }

    private static boolean under(SightCells w, double[] p) {
        int h = w.height((int) Math.floor(p[0]), (int) Math.floor(p[2]));
        return h != Integer.MIN_VALUE && p[1] < h;
    }
}
