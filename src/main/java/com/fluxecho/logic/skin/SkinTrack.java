package com.fluxecho.logic.skin;

/**
 * The server's record of one building of the box table: its entry as last sent, its state machine (style §3.9.4) and
 * its debounced power bit. The campus's feed observes the building every second while its tile is loaded and keeps the
 * record as it is while it is not (and saves it, {@code Skn}, so a building in an unloaded chunk still has its entry
 * after a restart).
 * <p>
 * The machine: a module job running on a site never formed makes it {@link SkinState#BUILDING}; its first forming
 * makes it {@link SkinState#FORMED} with {@code formedAt}; falling apart puts it on {@link SkinState#HOLD}, which looks
 * as formed; forming again makes it formed again; a minute apart with no repair queued or running makes it
 * {@link SkinState#DORMANT} until it forms again; the leap makes it {@link SkinState#ASCENDING} and then, for ever,
 * {@link SkinState#MEMORIAL}. While a building is apart its power bit keeps the value it had (its tile cannot be
 * powered unformed, and a hold must look as formed).
 */
public final class SkinTrack {

    /** Ticks apart, with no repair coming, before a hold goes dormant (60 seconds). */
    public static final int DORMANT_AFTER = 1200;

    private SkinEntry entry;
    private SkinState state;
    private long formedAt, apartSince = -1;
    private final PowerDebounce power;

    private SkinTrack(SkinEntry look, SkinState state, long formedAt, boolean powered, long apartSince) {
        this.state = state;
        this.formedAt = formedAt;
        this.apartSince = apartSince;
        power = new PowerDebounce(powered);
        entry = look;
        rebuild();
    }

    /** A building whose module job runs on a site that never formed. */
    public static SkinTrack building(long campus, String key, String shape, int facing, int[] ctrl, int[] box) {
        return new SkinTrack(look(campus, key, shape, facing, ctrl, box), SkinState.BUILDING, 0, false, -1);
    }

    /**
     * A building first seen standing (a campus of 0.16, a record lost): formed long ago when it is formed now (no
     * sweep), on hold when it is apart; its power as seen now.
     */
    public static SkinTrack seen(long campus, String key, String shape, int facing, int[] ctrl, int[] box,
        boolean formed, boolean awake, long now) {
        return new SkinTrack(
            look(campus, key, shape, facing, ctrl, box),
            formed ? SkinState.FORMED : SkinState.HOLD,
            0,
            awake,
            formed ? -1 : now);
    }

    /** A record read back ({@code Skn}): the entry as last sent, apart since the tick saved with it (-1: not apart). */
    public static SkinTrack saved(SkinEntry entry, long apartSince) {
        return new SkinTrack(entry, entry.state, entry.formedAt, entry.powered, apartSince);
    }

    private static SkinEntry look(long campus, String key, String shape, int facing, int[] ctrl, int[] box) {
        return SkinEntry.builder()
            .campus(campus)
            .key(key)
            .shape(shape)
            .facing(facing)
            .ctrl(ctrl[0], ctrl[1], ctrl[2])
            .box(box)
            .accent(SkinPalette.accentOr(key, shape, 0xFFFFFF))
            .build();
    }

    public SkinState state() {
        return state;
    }

    /** The entry as it stands now. */
    public SkinEntry entry() {
        return entry;
    }

    /** The tick it fell apart, or -1 while it stands (saved with the entry). */
    public long apartSince() {
        return apartSince;
    }

    /**
     * One observation of the building's tile: whether it is formed, whether it is awake (formed, docked and powered,
     * {@link LampRule#isAwake}), and whether a repair of its site is queued or running.
     */
    public void observe(long now, boolean formed, boolean awake, boolean repairQueued) {
        switch (state) {
            case BUILDING:
                if (formed) {
                    state = SkinState.FORMED;
                    formedAt = now;
                }
                break;
            case FORMED:
                if (!formed) {
                    state = SkinState.HOLD;
                    apartSince = now;
                }
                break;
            case HOLD:
                if (formed) state = SkinState.FORMED;
                else if (!repairQueued && apartSince >= 0 && now - apartSince >= DORMANT_AFTER)
                    state = SkinState.DORMANT;
                break;
            case DORMANT:
                if (formed) state = SkinState.FORMED;
                break;
            default:
                // the leap's states do not follow the structure
        }
        if (formed) apartSince = -1;
        else if (apartSince < 0 && state != SkinState.BUILDING) apartSince = now;
        // apart, its tile cannot be powered: the bit keeps what it was
        if (formed) power.update(awake, now);
        else if (state == SkinState.BUILDING) power.update(false, now);
        rebuild();
    }

    /** The leap begins (the ascension's S00): every building ascends. */
    public void ascend() {
        state = SkinState.ASCENDING;
        rebuild();
    }

    /** The leap relights the building (S18): an ascending building becomes a memorial, for ever. */
    public void memorialize() {
        if (state == SkinState.ASCENDING) state = SkinState.MEMORIAL;
        rebuild();
    }

    /**
     * Takes a new look of the building (its shape, facing, boxes, accent) as its tile now gives it, keeping its state;
     * the shape it forms as may change (a well grown deeper).
     */
    public void look(String shape, int facing, int[] box, int[] lamp, int accent) {
        entry = entry.toBuilder()
            .shape(shape)
            .facing(facing)
            .box(box)
            .lamp(lamp)
            .accent(accent)
            .build();
        rebuild();
    }

    private void rebuild() {
        entry = entry.toBuilder()
            .state(state)
            .formedAt(formedAt)
            .powered(power.value())
            .build();
    }
}
