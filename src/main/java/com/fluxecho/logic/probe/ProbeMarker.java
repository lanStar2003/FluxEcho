package com.fluxecho.logic.probe;

/**
 * The marker sign of an operator's check-up rig (0.17.0, refactor FINAL F06): a vanilla standing sign the command
 * plants at a fixed cell of the rig's {@link ProbeFrame}, its writing turned back at the operator. Its first line says
 * it is ours ({@link #TAG}), its second which rig it marks ({@link Kind#word}), its third names the rig for a
 * screenshot, its fourth carries the rig's few data (the bed rig's count of beds).
 * <p>
 * Why a sign: the rigs are real blocks in the world plus a little light drawn by the client (the probes' pieces, the
 * swatch wall's accents), and the client must know where a rig stands. A sign is a block entity whose text every
 * client receives with its chunk and keeps across a restart, so the rig needs no message kind, no saved data and no
 * id of its own; {@code swatch clear} and a second {@code probe} find the rig again by its sign, and the sign is a
 * label in the user's screenshots. The text is the format: once released it is only ever read, never changed.
 */
public final class ProbeMarker {

    /** The first line of every marker. */
    public static final String TAG = "[FluxEcho]";
    /** The longest line a sign shows. */
    public static final int LINE = 15;

    /** The rigs, with where each one's sign stands in its frame (cells ahead of and right of the operator). */
    public enum Kind {

        /** The swatch wall: its sign on the pool's front rim, left of the wall. */
        SWATCH("swatch", SwatchLayout.RIM, -SwatchLayout.CENTRE - 1),
        /** P0, the depth of the light drawn after the world. */
        DEPTH("probe depth", 2, -5),
        /** N0, the sky layer. */
        SKY("probe sky", 2, -2),
        /** The sun's path with the shader pack's tilt. */
        SUN("probe sun", 2, -3),
        /** S-P4, the planting beds. */
        BED("probe bed", 1, -10);

        /** The marker's second line. */
        public final String word;
        /** Where the sign stands: this far ahead of and right of the operator, on their floor. */
        public final int along, right;

        Kind(String word, int along, int right) {
            this.word = word;
            this.along = along;
            this.right = right;
        }

        /** The kind a second line names, or null. */
        public static Kind of(String word) {
            if (word == null) return null;
            for (Kind k : values()) if (k.word.equals(word.trim())) return k;
            return null;
        }
    }

    private ProbeMarker() {}

    /** The four lines of a marker: the tag, the kind, a label and data, each cut to {@link #LINE}. */
    public static String[] lines(Kind kind, String label, String data) {
        return new String[] { TAG, kind.word, cut(label), cut(data) };
    }

    /** The kind of rig a sign's lines mark, or null when the sign is no marker of ours. */
    public static Kind parse(String[] lines) {
        if (lines == null || lines.length < 2 || !TAG.equals(trim(lines[0]))) return null;
        return Kind.of(lines[1]);
    }

    /** The world cell of a rig's sign. */
    public static int[] signAt(Kind kind, ProbeFrame f) {
        return f.at(kind.along, kind.right, 0);
    }

    /** The meta of a rig's sign: its writing faces back at the operator. */
    public static int signMeta(ProbeFrame f) {
        return ProbeFrame.signMeta(-f.dx, -f.dz);
    }

    /** The frame of a rig whose sign of the given meta stands at {@code (sx, sy, sz)}: the inverse of the two above. */
    public static ProbeFrame frame(Kind kind, int sx, int sy, int sz, int meta) {
        int[] faces = ProbeFrame.signFacing(meta);
        int dx = -faces[0], dz = -faces[1], rx = -dz, rz = dx;
        return new ProbeFrame(
            sx - kind.along * dx - kind.right * rx,
            sy,
            sz - kind.along * dz - kind.right * rz,
            dx,
            dz);
    }

    /** A number carried on a marker's data line as {@code key=value}, or {@code fallback}. */
    public static int number(String data, String key, int fallback) {
        if (data == null) return fallback;
        String d = data.trim(), k = key + "=";
        if (!d.startsWith(k)) return fallback;
        try {
            return Integer.parseInt(
                d.substring(k.length())
                    .trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String cut(String s) {
        if (s == null) return "";
        return s.length() <= LINE ? s : s.substring(0, LINE);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
