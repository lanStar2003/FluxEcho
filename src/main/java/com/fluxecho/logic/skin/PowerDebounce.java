package com.fluxecho.logic.skin;

/**
 * A building's power bit as the box table sends it (style §3.9.4): a change counts only once the new value has held
 * for 200 ticks, and a building's bit changes at most once in 10 seconds, so a grid that flickers never makes the
 * client rebuild a building's lamps over and over. Server side; the observations come every few ticks.
 */
public final class PowerDebounce {

    /** Ticks a new value must hold before it counts. */
    public static final int STEADY = 200;
    /** The fewest ticks between two changes (10 seconds). */
    public static final int MIN_GAP = 200;

    private boolean value, candidate;
    private long since, last = Long.MIN_VALUE / 2;

    /** A bit that starts at a known value (as first seen, or as saved). */
    public PowerDebounce(boolean value) {
        this.value = value;
        candidate = value;
    }

    /** The value the table sends. */
    public boolean value() {
        return value;
    }

    /** One observation of the raw power at a tick; returns whether the value changed. */
    public boolean update(boolean raw, long now) {
        if (raw == value) {
            candidate = value;
            return false;
        }
        if (raw != candidate) {
            candidate = raw;
            since = now;
            return false;
        }
        if (now - since >= STEADY && now - last >= MIN_GAP) {
            value = raw;
            last = now;
            return true;
        }
        return false;
    }
}
