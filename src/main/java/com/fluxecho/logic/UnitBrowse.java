package com.fluxecho.logic;

/**
 * Browsing the units of a city module in its window (0.11.0): the unit list comes in pages, the arrows step round the
 * units, and whatever a client asks for is brought into range by the server. Pure arithmetic, shared by the window's
 * server side (which checks every selection) and its client side (which pages the list).
 */
public final class UnitBrowse {

    private UnitBrowse() {}

    /** Whether a unit index names a unit of a module with {@code count} units. */
    public static boolean valid(int unit, int count) {
        return unit >= 0 && unit < count;
    }

    /** A unit index brought into {@code 0..count-1} (0 for a module without units). */
    public static int clamp(int unit, int count) {
        return count <= 0 ? 0 : Math.max(0, Math.min(count - 1, unit));
    }

    /** The unit {@code by} along from {@code unit}, round from the last to the first and back. */
    public static int step(int unit, int by, int count) {
        return count <= 0 ? 0 : Math.floorMod(clamp(unit, count) + by, count);
    }

    /** How many pages a list of {@code count} units takes at {@code perPage} a page (at least one). */
    public static int pages(int count, int perPage) {
        return Math.max(1, (count + perPage - 1) / perPage);
    }

    /** The page a unit's line is on. */
    public static int pageOf(int unit, int perPage) {
        return Math.max(0, unit) / perPage;
    }

    /** A page brought into range, round from the last to the first and back. */
    public static int page(int page, int count, int perPage) {
        return Math.floorMod(page, pages(count, perPage));
    }

    /** The unit on a line of a page, or -1 for a line past the last unit. */
    public static int unitAt(int page, int line, int count, int perPage) {
        int u = page * perPage + line;
        return line < 0 || line >= perPage || u < 0 || u >= count ? -1 : u;
    }
}
