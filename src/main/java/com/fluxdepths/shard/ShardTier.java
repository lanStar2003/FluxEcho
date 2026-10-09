package com.fluxdepths.shard;

/**
 * What a Flux Shard Collector can do, set by the circuit in its core slot: no circuit runs it on steam; an LV to LuV
 * circuit runs it at that voltage, with that tier's speed, imprint slots and power draw. It condenses one ore every
 * {@link #ticks} ticks from the veins it is tuned to, paying {@link #energy} per tick (GT's steam units for the steam
 * mode, EU for the others). The fastest stays below GTNH's Void Miner I (2 ores a second without noble gases): a
 * pinhole into the depths never matches a tear.
 */
public enum ShardTier {

    // GT voltage tier, ticks, energy, imprints, drilling fluid per ore
    STEAM(0, 200, 8, 1, 0),
    LV(1, 50, 24, 2, 0),
    MV(2, 33, 96, 2, 20),
    HV(3, 25, 384, 3, 20),
    EV(4, 18, 1536, 3, 20),
    IV(5, 13, 6144, 4, 20),
    LUV(6, 11, 24576, 4, 20);

    /** GTNH's Void Miner I: two ores a second, the ceiling of every collector. */
    public static final double VOID_MINER_PER_SECOND = 2;
    /** Most imprint slots any tier uses. */
    public static final int MAX_IMPRINTS = 4;
    /** GT's voltage of each tier, ULV to LuV. */
    private static final long[] VOLTAGE = { 8, 32, 128, 512, 2048, 8192, 32768 };
    private static final String[] NAMES = { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV" };

    /** GT voltage tier, 0 for the steam mode. */
    public final int gtTier;
    /** Ticks per ore. */
    public final int ticks;
    /** Steam units (half a litre each, as GT stores steam) or EU per tick while working. */
    public final int energy;
    /** Imprints it uses; the ores are shared out between them, not added up. */
    public final int imprints;
    /** Litres of drilling fluid per ore, 0 when it needs none. */
    public final int fluidPerOre;

    ShardTier(int gtTier, int ticks, int energy, int imprints, int fluidPerOre) {
        this.gtTier = gtTier;
        this.ticks = ticks;
        this.energy = energy;
        this.imprints = imprints;
        this.fluidPerOre = fluidPerOre;
    }

    public boolean steam() {
        return this == STEAM;
    }

    /** The voltage it takes, 0 on steam. */
    public long voltage() {
        return steam() ? 0 : VOLTAGE[gtTier];
    }

    /** Litres of steam per tick in the steam mode. */
    public int steamLitres() {
        return energy * 2;
    }

    /** "LV", "LuV"; "Steam" for the steam mode. */
    public String label() {
        return steam() ? "Steam" : NAMES[gtTier];
    }

    public double perSecond() {
        return 20.0 / ticks;
    }

    public double perMinute() {
        return perSecond() * 60;
    }

    public double perHour() {
        return perSecond() * 3600;
    }

    /** The tier's colour, as GT colours its tier names (bronze for steam): glow strips, GUI, hologram. */
    public int color() {
        return switch (this) {
            case STEAM -> 0xD08A3C;
            case LV -> 0xC8D2DC;
            case MV -> 0x55E0FF;
            case HV -> 0xFFAA22;
            case EV -> 0xB866FF;
            case IV -> 0x5C7CFF;
            case LUV -> 0xFF66DD;
        };
    }

    /** Lower-case key used in lang keys, e.g. {@code luv}. */
    public String key() {
        return name().toLowerCase();
    }

    /** The tier a circuit of GT voltage tier {@code gtTier} gives; the steam mode for anything else. */
    public static ShardTier ofVoltageTier(int gtTier) {
        for (ShardTier t : values()) if (!t.steam() && t.gtTier == gtTier) return t;
        return STEAM;
    }
}
