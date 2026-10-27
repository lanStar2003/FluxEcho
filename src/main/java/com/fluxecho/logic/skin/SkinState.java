package com.fluxecho.logic.skin;

/**
 * Where a building of the box table stands (style §3.9.4): built for the first time, formed, on hold after it fell
 * apart, dormant after a minute apart with no repair coming, and the leap's two states. The server alone moves an entry
 * from one to the next ({@link SkinTrack}); the client draws by it.
 * <p>
 * The codes are saved (the campus's {@code Skn}) and sent ({@code SKIN_TABLE}, {@code SKIN_DELTA}), so they never move:
 * a new state takes the next code.
 */
public enum SkinState {

    /** Never formed, its module job running: the new materials show their blueprint colours (§3.9.6). */
    BUILDING(0),
    /** Formed. */
    FORMED(1),
    /** Formed once, apart now (a block broken, an explosion, a repair under way): drawn as formed. */
    HOLD(2),
    /** Apart for a minute with no repair queued or running: its glowing overlays are off. */
    DORMANT(3),
    /** The leap is under way (§6.6): from S12 on its windows and seams go out with the light city. */
    ASCENDING(4),
    /** After the leap, for ever (§6.7): windows lit by G6 as before, the keel warm white, a line of gold. */
    MEMORIAL(5);

    /** The code saved and sent. */
    public final int code;

    SkinState(int code) {
        this.code = code;
    }

    /**
     * Whether a building in this state is awake, the first of the lamp windows' three conditions (G6): formed or on
     * hold, and after the leap the memorial state, whose contract lights its windows as before (§3.9.2).
     */
    public boolean awake() {
        return this == FORMED || this == HOLD || this == MEMORIAL;
    }

    /** The state of a code; a code of a later version reads as {@link #DORMANT}, which lights nothing. */
    public static SkinState of(int code) {
        for (SkinState s : values()) if (s.code == code) return s;
        return DORMANT;
    }
}
