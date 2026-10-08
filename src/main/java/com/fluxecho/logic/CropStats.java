package com.fluxecho.logic;

/**
 * The three stats of an IC2 crop (growth, gain, resistance, each 0-31) and which of them a programmed circuit copies
 * from a sample onto a crop imprint: 1 growth, 2 gain, 3 resistance, 4 all three. The crop itself never changes.
 */
public final class CropStats {

    public static final int MAX = 31;
    public static final int CIRCUIT_ALL = 4;

    public final int growth, gain, resistance;

    public CropStats(int growth, int gain, int resistance) {
        this.growth = clamp(growth);
        this.gain = clamp(gain);
        this.resistance = clamp(resistance);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(MAX, v));
    }

    /** Whether the circuit copies anything. */
    public static boolean copies(int circuit) {
        return circuit >= 1 && circuit <= CIRCUIT_ALL;
    }

    /** These stats with the circuit's ones taken from the donor; unchanged for a circuit that copies nothing. */
    public CropStats with(CropStats donor, int circuit) {
        boolean all = circuit == CIRCUIT_ALL;
        return new CropStats(
            all || circuit == 1 ? donor.growth : growth,
            all || circuit == 2 ? donor.gain : gain,
            all || circuit == 3 ? donor.resistance : resistance);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CropStats s && s.growth == growth && s.gain == gain && s.resistance == resistance;
    }

    @Override
    public int hashCode() {
        return (growth * 32 + gain) * 32 + resistance;
    }

    @Override
    public String toString() {
        return growth + "/" + gain + "/" + resistance;
    }
}
