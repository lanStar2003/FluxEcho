package com.fluxecho.logic;

/**
 * A cell that keeps a multiblock from forming (0.14.2): where it is, and the lang keys of the block found there and of
 * the block the structure wants there (each translated on the reader's side, so a dedicated server names them in the
 * player's language). The campus builder names it when a job it raised pauses as 结构未成型, and a module's window and
 * the nexus's header name it while the structure stands unformed: before, a building that did not form said only that,
 * and a snow layer in one doorway out of thousands of cells was all but impossible to find.
 * <p>
 * It travels to the client as one string ({@link #encode}, {@link #decode}).
 */
public final class WrongCell {

    /** The lang key of air (a cell where a block is missing, or where the structure wants nothing). */
    public static final String AIR = "fluxecho.build.cell.air";
    /** Between the fields of {@link #encode}: no lang key holds it. */
    private static final char SEP = '|';

    public final int x, y, z;
    /** The lang keys (whole keys, translated as they are) of what is there and of what is wanted. */
    public final String found, wanted;

    public WrongCell(int x, int y, int z, String found, String wanted) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.found = found == null ? AIR : found;
        this.wanted = wanted == null ? AIR : wanted;
    }

    /** The coordinates as a player types them: {@code x, y, z}. */
    public String pos() {
        return x + ", " + y + ", " + z;
    }

    /** The cell as one string: {@code x,y,z|found|wanted}. */
    public String encode() {
        return x + "," + y + "," + z + SEP + found + SEP + wanted;
    }

    /** The cell of an {@link #encode}d string; null for an empty or malformed one (no cell to name). */
    public static WrongCell decode(String s) {
        if (s == null || s.isEmpty()) return null;
        int a = s.indexOf(SEP), b = a < 0 ? -1 : s.indexOf(SEP, a + 1);
        if (b < 0) return null;
        String[] p = s.substring(0, a)
            .split(",");
        if (p.length != 3) return null;
        try {
            return new WrongCell(
                Integer.parseInt(p[0].trim()),
                Integer.parseInt(p[1].trim()),
                Integer.parseInt(p[2].trim()),
                s.substring(a + 1, b),
                s.substring(b + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@link #encode}, or "" for no cell. */
    public static String encode(WrongCell c) {
        return c == null ? "" : c.encode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WrongCell c && c.x == x
            && c.y == y
            && c.z == z
            && c.found.equals(found)
            && c.wanted.equals(wanted);
    }

    @Override
    public int hashCode() {
        return ((x * 31 + y) * 31 + z) * 31 + found.hashCode() * 7 + wanted.hashCode();
    }

    @Override
    public String toString() {
        return pos() + ": " + found + " (wants " + wanted + ")";
    }
}
