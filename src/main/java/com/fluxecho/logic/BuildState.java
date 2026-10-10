package com.fluxecho.logic;

/**
 * The life of a campus build job (establish, forum, a module, a repair) as a small state machine. Every transition is
 * a static method that takes the current state (and the current pause reason where it matters) and returns the next
 * state, or throws {@link IllegalStateException} for a move the job may not make, so the builder, the GUI buttons and
 * the tests all agree on one table. The job stores the {@link State} and the {@link Pause} reason; the reason is
 * {@link Pause#NONE} outside {@link State#PAUSED}, except that a re-survey keeps it until {@link #surveyed} has used
 * it.
 * <p>
 * The transitions:
 *
 * <pre>
 * (new job)                                      -&gt; SURVEY       initial()
 * SURVEY      survey done, never started         -&gt; PROJECTING   surveyed(s, false, p)
 * SURVEY      survey done, started before        -&gt; WAITING      surveyed(s, true, p), p not PLAYER
 * SURVEY      survey done, started, player pause -&gt; PAUSED       surveyed(s, true, PLAYER)
 * PROJECTING  a member presses 开始               -&gt; BUILDING     start(s, NONE)
 * WAITING     开始, or the builder picks it up     -&gt; BUILDING     start(s, NONE)
 * PAUSED      a member presses 开始 (PLAYER only) -&gt; BUILDING     start(s, PLAYER)
 * BUILDING    a cause stops the job               -&gt; PAUSED       pause(s, NONE, reason)
 * WAITING     a cause stops the job               -&gt; PAUSED       pause(s, NONE, reason)
 * PAUSED      another cause, or the player        -&gt; PAUSED       pause(s, current, reason)
 * PAUSED      the cause cleared (not PLAYER)      -&gt; BUILDING     resume(s, current)
 * BUILDING    the last stage commissioned         -&gt; DONE         finish(s)
 * not ended   a new survey is needed              -&gt; SURVEY       resurvey(s)
 * not ended   a member presses 取消 twice          -&gt; CANCELLED    cancel(s)
 * DONE, CANCELLED                                 (nothing leaves them)
 * </pre>
 *
 * Every job starts in SURVEY and, once its cells are classified, waits in PROJECTING until a member presses 开始: the
 * player sees the projection and the terrain it would clear before anything is touched. A job that was started keeps a
 * {@code started} flag of its own, so a re-survey (after a load, a site change or a new plan version) comes back to
 * WAITING instead of asking again, and to PAUSED when the player had paused it. Every pause but the player's resumes
 * by itself when its cause clears; the player's pause is lifted only by 开始.
 */
public final class BuildState {

    /** Where a job is. */
    public enum State {
        /** Classifying the plan's cells against the world; nothing is touched. */
        SURVEY,
        /** Classified and shown as a projection; waiting for a member's 开始 (consent). */
        PROJECTING,
        /** Started before and re-surveyed; the builder continues it on its next tick. */
        WAITING,
        /** Clearing and placing. */
        BUILDING,
        /** Stopped for a {@link Pause} reason. */
        PAUSED,
        /** Every stage placed or skipped and the result commissioned. */
        DONE,
        /** Cancelled by a member; what was built stays. */
        CANCELLED
    }

    /** Why a job is paused. */
    public enum Pause {
        /** Not paused. */
        NONE,
        /** No material credit for the next payable step. */
        MATERIALS,
        /** Not enough EU in the team's wireless network. */
        POWER,
        /** A cell lies in an unloaded chunk. */
        UNLOADED,
        /** A cell holds a block the builder may not touch (and skipping is off). */
        BLOCKED,
        /** A protection mod cancelled a break or a place. */
        PROTECTED,
        /** A member pressed 暂停. */
        PLAYER,
        /** The finished structure failed its check after repair. */
        INCOMPLETE
    }

    private BuildState() {}

    /** The state of a new job: {@link State#SURVEY}. */
    public static State initial() {
        return State.SURVEY;
    }

    /**
     * The end of a survey: PROJECTING for a job no member has started yet; for a started job, PAUSED when its kept
     * pause reason is {@link Pause#PLAYER} (the caller keeps that reason), otherwise WAITING with no reason.
     *
     * @param started whether a member has pressed 开始 on this job before
     * @param kept    the pause reason the job had before the re-survey
     */
    public static State surveyed(State s, boolean started, Pause kept) {
        require(s == State.SURVEY, s, "finish a survey");
        if (!started) return State.PROJECTING;
        return kept == Pause.PLAYER ? State.PAUSED : State.WAITING;
    }

    /**
     * A new survey (after a load, a site change or a new plan version): from any state but DONE and CANCELLED back to
     * SURVEY. The caller keeps the job's {@code started} flag and pause reason for {@link #surveyed}.
     */
    public static State resurvey(State s) {
        require(!ended(s), s, "re-survey");
        return State.SURVEY;
    }

    /**
     * Whether 开始 can be pressed: PROJECTING, WAITING, or PAUSED by a player (the only way to lift a player's pause).
     * This is the test for the 开始 button. There is deliberately no overload without the pause: the state alone
     * cannot tell a player's pause, which 开始 lifts, from the other pauses, which lift by themselves.
     */
    public static boolean canStart(State s, Pause p) {
        return s == State.PROJECTING || s == State.WAITING || (s == State.PAUSED && p == Pause.PLAYER);
    }

    /**
     * 开始: PROJECTING or WAITING to BUILDING (this is the member's consent), or PAUSED by a player back to BUILDING.
     * The caller sets the pause reason to NONE.
     */
    public static State start(State s, Pause current) {
        require(canStart(s, current), s, "start");
        return State.BUILDING;
    }

    /**
     * Whether the job can be paused for this reason now: from BUILDING or WAITING for any reason; from PAUSED when the
     * new reason is the player's or the current one is not (a cause never overrides the player's pause, but the
     * player may pause a job that waits for materials). NONE is never a reason.
     */
    public static boolean canPause(State s, Pause current, Pause reason) {
        if (reason == Pause.NONE) return false;
        if (s == State.BUILDING || s == State.WAITING) return true;
        return s == State.PAUSED && (reason == Pause.PLAYER || current != Pause.PLAYER);
    }

    /**
     * A pause: BUILDING or WAITING to PAUSED, or PAUSED to PAUSED with a new reason (see {@link #canPause}). The caller
     * stores {@code reason} as the job's pause.
     *
     * @throws IllegalArgumentException when the reason is {@link Pause#NONE}
     */
    public static State pause(State s, Pause current, Pause reason) {
        if (reason == null || reason == Pause.NONE) throw new IllegalArgumentException("a pause needs a reason");
        require(canPause(s, current, reason), s, "pause for " + reason + " (now " + current + ")");
        return State.PAUSED;
    }

    /** Whether a pause lifts by itself once its cause clears: every reason but the player's (and NONE). */
    public static boolean autoResumes(Pause p) {
        return p != Pause.NONE && p != Pause.PLAYER;
    }

    /**
     * The cause of a pause cleared: PAUSED to BUILDING, for every reason but the player's (that one needs
     * {@link #start}). The caller sets the pause reason to NONE.
     */
    public static State resume(State s, Pause current) {
        require(s == State.PAUSED && autoResumes(current), s, "resume from " + current);
        return State.BUILDING;
    }

    /** The last stage commissioned: BUILDING to DONE. */
    public static State finish(State s) {
        require(s == State.BUILDING, s, "finish");
        return State.DONE;
    }

    /** 取消 (two clicks in the GUI): any state but DONE and CANCELLED to CANCELLED. */
    public static State cancel(State s) {
        require(!ended(s), s, "cancel");
        return State.CANCELLED;
    }

    /** Whether the job is over: DONE or CANCELLED. Nothing leaves these states. */
    public static boolean ended(State s) {
        return s == State.DONE || s == State.CANCELLED;
    }

    /**
     * Whether a member has consented and the job is not over: WAITING, BUILDING or PAUSED. Only such a job accepts
     * items from the supply port and other automation. A started job that is being re-surveyed is in SURVEY and does
     * not count here; the caller may add its own {@code started} flag for that short time.
     */
    public static boolean consented(State s) {
        return s == State.WAITING || s == State.BUILDING || s == State.PAUSED;
    }

    private static void require(boolean legal, State s, String move) {
        if (!legal) throw new IllegalStateException("A build job in " + s + " cannot " + move + ".");
    }
}
