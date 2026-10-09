package com.fluxecho.logic;

/**
 * The slots of the Flux Nexus's inner ring (blueprint 3.3): eight places on a circle round the nexus's centre, slot 0
 * straight ahead of its front and the others every 45 degrees clockwise (seen from above). A module is docked on a
 * slot when the centre of its foundation is within {@link #TOLERANCE} blocks of the slot's centre, across, and within
 * {@link #RISE} blocks up or down.
 */
public final class RingSlots {

    public static final int SLOTS = 8, TOLERANCE = 3, RISE = 6;

    private RingSlots() {}

    /**
     * The slot's centre relative to the nexus's centre: {dx, dz}.
     *
     * @param fx the nexus's front, a horizontal unit vector
     */
    public static int[] offset(int slot, int radius, int fx, int fz) {
        double angle = Math.toRadians(45.0 * Math.floorMod(slot, SLOTS));
        double ahead = Math.cos(angle) * radius, right = Math.sin(angle) * radius;
        // clockwise from the front seen from above: (-fz, fx)
        double dx = ahead * fx - right * fz, dz = ahead * fz + right * fx;
        return new int[] { (int) Math.round(dx), (int) Math.round(dz) };
    }

    /** The slot a module centred at {@code (dx, dy, dz)} from the nexus's centre sits on, or -1. */
    public static int slotAt(int dx, int dy, int dz, int radius, int fx, int fz) {
        if (Math.abs(dy) > RISE) return -1;
        for (int k = 0; k < SLOTS; k++) {
            int[] o = offset(k, radius, fx, fz);
            if (Math.abs(o[0] - dx) <= TOLERANCE && Math.abs(o[1] - dz) <= TOLERANCE) return k;
        }
        return -1;
    }

    /** Modules a nexus of the phase can dock on its inner ring (blueprint 3.2: HV 2, EV 4, IV 8). */
    public static int open(int phase) {
        return phase <= 0 ? 0 : phase == 1 ? 2 : phase == 2 ? 4 : SLOTS;
    }
}
