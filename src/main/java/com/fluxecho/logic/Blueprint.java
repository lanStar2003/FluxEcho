package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * A multiblock's shape as StructureLib's {@code transpose} takes it: horizontal layers from the top down, each a list
 * of rows from the front (the controller's row) to the back, each row a string across. A space is "anything", a
 * {@code ~} the controller.
 * <p>
 * Every FluxEcho blueprint is mirror-symmetric across, so it reads the same whichever way StructureLib runs its
 * across axis; the depth axis runs from the controller's front face into the structure, against the way the
 * controller faces. {@link #world} turns a cell into a block position on those terms.
 */
public final class Blueprint {

    /** [layer from the top][row from the front] = string across. */
    private final String[][] layers;
    /** The controller's cell: across, layer from the top, row from the front. */
    public final int ctrlA, ctrlB, ctrlC;

    public Blueprint(String[][] layers) {
        this.layers = layers;
        int a = -1, b = -1, c = -1;
        for (int i = 0; i < layers.length; i++) for (int j = 0; j < layers[i].length; j++) {
            int k = layers[i][j].indexOf('~');
            if (k >= 0) {
                if (a >= 0) throw new IllegalArgumentException("two controllers");
                a = k;
                b = i;
                c = j;
            }
        }
        if (a < 0) throw new IllegalArgumentException("no controller");
        ctrlA = a;
        ctrlB = b;
        ctrlC = c;
    }

    /** A copy for StructureLib's {@code transpose}. */
    public String[][] shape() {
        String[][] out = new String[layers.length][];
        for (int i = 0; i < layers.length; i++) out[i] = layers[i].clone();
        return out;
    }

    public int height() {
        return layers.length;
    }

    public int depth() {
        int d = 0;
        for (String[] l : layers) d = Math.max(d, l.length);
        return d;
    }

    public int width() {
        int w = 0;
        for (String[] l : layers) for (String r : l) w = Math.max(w, r.length());
        return w;
    }

    /** The character at a cell; a space outside the drawn part. */
    public char at(int a, int b, int c) {
        if (b < 0 || b >= layers.length || c < 0 || c >= layers[b].length) return ' ';
        String row = layers[b][c];
        return a < 0 || a >= row.length() ? ' ' : row.charAt(a);
    }

    /** A cell and its character. */
    public static final class Cell {

        public final int a, b, c;
        public final char ch;

        Cell(int a, int b, int c, char ch) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.ch = ch;
        }
    }

    /** Every cell that is not a space. */
    public List<Cell> cells() {
        List<Cell> out = new ArrayList<>();
        for (int b = 0; b < layers.length; b++) for (int c = 0; c < layers[b].length; c++) {
            String row = layers[b][c];
            for (int a = 0; a < row.length(); a++) if (row.charAt(a) != ' ') out.add(new Cell(a, b, c, row.charAt(a)));
        }
        return out;
    }

    /** How many cells hold the character. */
    public int count(char ch) {
        int n = 0;
        for (Cell cell : cells()) if (cell.ch == ch) n++;
        return n;
    }

    /** Whether every row reads the same backwards (the shape is mirror-symmetric across). */
    public boolean symmetric() {
        int w = width();
        for (String[] l : layers) for (String r : l) {
            String padded = String.format("%-" + w + "s", r);
            if (!new StringBuilder(padded).reverse()
                .toString()
                .equals(padded)) return false;
        }
        return true;
    }

    /**
     * The block a cell is at, for a controller at {@code (x, y, z)} whose front faces {@code (fx, 0, fz)} (one of the
     * four horizontal unit vectors): {x, y, z}.
     */
    public int[] world(int a, int b, int c, int x, int y, int z, int fx, int fz) {
        int across = a - ctrlA, up = ctrlB - b, back = c - ctrlC;
        // across runs to the right of someone facing the controller's front; with a symmetric shape either way works
        int rx = fz, rz = -fx;
        return new int[] { x + across * rx - back * fx, y + up, z + across * rz - back * fz };
    }

    /** The centre of the layer the controller is in, in blocks from the controller (the across axis ignored). */
    public int centreBack() {
        return (depth() - 1) / 2 - ctrlC;
    }
}
