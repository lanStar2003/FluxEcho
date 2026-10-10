package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.BuildState.Pause;
import com.fluxecho.logic.BuildState.State;

class BuildStateTest {

    private static final Set<State> ENDED = EnumSet.of(State.DONE, State.CANCELLED);

    @Test
    void everyJobStartsWithASurvey() {
        assertEquals(State.SURVEY, BuildState.initial());
    }

    @Test
    void surveyEndsInProjectionUntilAMemberStarts() {
        for (Pause p : Pause.values())
            assertEquals(State.PROJECTING, BuildState.surveyed(State.SURVEY, false, p), "never started, kept " + p);
    }

    @Test
    void aStartedJobComesBackWaitingOrPaused() {
        for (Pause p : Pause.values()) {
            State expected = p == Pause.PLAYER ? State.PAUSED : State.WAITING;
            assertEquals(expected, BuildState.surveyed(State.SURVEY, true, p), "started, kept " + p);
        }
    }

    @Test
    void onlyASurveyCanEnd() {
        for (State s : State.values()) {
            if (s == State.SURVEY) continue;
            assertThrows(IllegalStateException.class, () -> BuildState.surveyed(s, false, Pause.NONE), s.name());
            assertThrows(IllegalStateException.class, () -> BuildState.surveyed(s, true, Pause.NONE), s.name());
        }
    }

    @Test
    void resurvey() {
        for (State s : State.values()) {
            if (ENDED.contains(s)) assertThrows(IllegalStateException.class, () -> BuildState.resurvey(s), s.name());
            else assertEquals(State.SURVEY, BuildState.resurvey(s), s.name());
        }
    }

    @Test
    void whereStartIsOffered() {
        for (State s : State.values()) for (Pause p : Pause.values()) {
            boolean expected = s == State.PROJECTING || s == State.WAITING || (s == State.PAUSED && p == Pause.PLAYER);
            assertEquals(expected, BuildState.canStart(s, p), s + " " + p);
        }
        assertTrue(BuildState.canStart(State.PAUSED, Pause.PLAYER), "开始 is the only way out of a player's pause");
        assertFalse(BuildState.canStart(State.PAUSED, Pause.MATERIALS), "a cause lifts by itself");
    }

    @Test
    void startNeedsThePause() throws NoSuchMethodException {
        // A one-argument canStart(State) cannot see a player's pause, so the 开始 button would stay grey on a job
        // only 开始 can resume; the API offers the two-argument form alone.
        assertThrows(NoSuchMethodException.class, () -> BuildState.class.getMethod("canStart", State.class));
        BuildState.class.getMethod("canStart", State.class, Pause.class);
    }

    @Test
    void start() {
        for (State s : State.values()) for (Pause p : Pause.values()) {
            if (BuildState.canStart(s, p)) assertEquals(State.BUILDING, BuildState.start(s, p), s + " " + p);
            else assertThrows(IllegalStateException.class, () -> BuildState.start(s, p), s + " " + p);
        }
        assertEquals(State.BUILDING, BuildState.start(State.PROJECTING, Pause.NONE), "the member's consent");
        assertEquals(State.BUILDING, BuildState.start(State.WAITING, Pause.NONE), "continuing after a re-survey");
        assertEquals(State.BUILDING, BuildState.start(State.PAUSED, Pause.PLAYER), "lifting the player's pause");
        assertThrows(IllegalStateException.class, () -> BuildState.start(State.PAUSED, Pause.MATERIALS));
        assertThrows(IllegalStateException.class, () -> BuildState.start(State.SURVEY, Pause.NONE), "survey first");
        assertThrows(IllegalStateException.class, () -> BuildState.start(State.BUILDING, Pause.NONE));
    }

    @Test
    void pauseTable() {
        for (State s : State.values()) for (Pause current : Pause.values()) for (Pause reason : Pause.values()) {
            boolean expected = reason != Pause.NONE && (s == State.BUILDING || s == State.WAITING
                || (s == State.PAUSED && (reason == Pause.PLAYER || current != Pause.PLAYER)));
            String what = s + " " + current + " -> " + reason;
            assertEquals(expected, BuildState.canPause(s, current, reason), what);
            if (reason == Pause.NONE)
                assertThrows(IllegalArgumentException.class, () -> BuildState.pause(s, current, reason), what);
            else if (expected) assertEquals(State.PAUSED, BuildState.pause(s, current, reason), what);
            else assertThrows(IllegalStateException.class, () -> BuildState.pause(s, current, reason), what);
        }
        assertThrows(IllegalArgumentException.class, () -> BuildState.pause(State.BUILDING, Pause.NONE, null));
    }

    @Test
    void pauseRules() {
        assertEquals(State.PAUSED, BuildState.pause(State.BUILDING, Pause.NONE, Pause.MATERIALS));
        assertEquals(State.PAUSED, BuildState.pause(State.WAITING, Pause.NONE, Pause.UNLOADED), "after a load");
        assertEquals(State.PAUSED, BuildState.pause(State.PAUSED, Pause.MATERIALS, Pause.POWER), "a new cause");
        assertEquals(State.PAUSED, BuildState.pause(State.PAUSED, Pause.MATERIALS, Pause.PLAYER), "the player wins");
        assertEquals(State.PAUSED, BuildState.pause(State.PAUSED, Pause.PLAYER, Pause.PLAYER), "pressed again");
        assertThrows(
            IllegalStateException.class,
            () -> BuildState.pause(State.PAUSED, Pause.PLAYER, Pause.MATERIALS),
            "a cause never replaces the player's pause");
        assertThrows(
            IllegalStateException.class,
            () -> BuildState.pause(State.PROJECTING, Pause.NONE, Pause.PLAYER),
            "nothing to pause before consent");
        assertThrows(IllegalStateException.class, () -> BuildState.pause(State.SURVEY, Pause.NONE, Pause.UNLOADED));
        assertThrows(IllegalStateException.class, () -> BuildState.pause(State.DONE, Pause.NONE, Pause.PLAYER));
    }

    @Test
    void autoResume() {
        for (Pause p : Pause.values())
            assertEquals(p != Pause.NONE && p != Pause.PLAYER, BuildState.autoResumes(p), p.name());
    }

    @Test
    void resume() {
        for (State s : State.values()) for (Pause p : Pause.values()) {
            if (s == State.PAUSED && BuildState.autoResumes(p))
                assertEquals(State.BUILDING, BuildState.resume(s, p), s + " " + p);
            else assertThrows(IllegalStateException.class, () -> BuildState.resume(s, p), s + " " + p);
        }
        assertThrows(
            IllegalStateException.class,
            () -> BuildState.resume(State.PAUSED, Pause.PLAYER),
            "only 开始 lifts the player's pause");
    }

    @Test
    void finish() {
        for (State s : State.values()) {
            if (s == State.BUILDING) assertEquals(State.DONE, BuildState.finish(s));
            else assertThrows(IllegalStateException.class, () -> BuildState.finish(s), s.name());
        }
    }

    @Test
    void cancelFromAnyLiveState() {
        for (State s : State.values()) {
            if (ENDED.contains(s)) assertThrows(IllegalStateException.class, () -> BuildState.cancel(s), s.name());
            else assertEquals(State.CANCELLED, BuildState.cancel(s), s.name());
        }
    }

    @Test
    void endedAndConsented() {
        for (State s : State.values()) {
            assertEquals(ENDED.contains(s), BuildState.ended(s), s.name());
            boolean consented = s == State.WAITING || s == State.BUILDING || s == State.PAUSED;
            assertEquals(consented, BuildState.consented(s), s.name());
        }
        assertFalse(BuildState.consented(State.PROJECTING), "the supply port refuses before 开始");
    }

    @Test
    void consentIsTheOnlyWayFromProjectionToBuilding() {
        // every transition out of PROJECTING other than start leads somewhere else
        assertEquals(State.SURVEY, BuildState.resurvey(State.PROJECTING));
        assertEquals(State.CANCELLED, BuildState.cancel(State.PROJECTING));
        for (Pause p : Pause.values()) {
            assertFalse(BuildState.canPause(State.PROJECTING, Pause.NONE, p));
            assertThrows(IllegalStateException.class, () -> BuildState.resume(State.PROJECTING, p));
        }
        assertThrows(IllegalStateException.class, () -> BuildState.finish(State.PROJECTING));
    }

    @Test
    void aWholeJob() {
        Pause pause = Pause.NONE;
        State s = BuildState.initial();
        s = BuildState.surveyed(s, false, pause);
        assertEquals(State.PROJECTING, s);
        s = BuildState.start(s, pause);
        boolean started = true;
        assertEquals(State.BUILDING, s);
        // the materials run out, then the player pauses as well
        s = BuildState.pause(s, pause, Pause.MATERIALS);
        pause = Pause.MATERIALS;
        s = BuildState.pause(s, pause, Pause.PLAYER);
        pause = Pause.PLAYER;
        assertEquals(State.PAUSED, s);
        // the world is reloaded: re-survey, and the player's pause holds
        s = BuildState.resurvey(s);
        s = BuildState.surveyed(s, started, pause);
        assertEquals(State.PAUSED, s);
        assertFalse(BuildState.autoResumes(pause));
        assertTrue(BuildState.canStart(s, pause));
        s = BuildState.start(s, pause);
        pause = Pause.NONE;
        // the power dips and comes back
        s = BuildState.pause(s, pause, Pause.POWER);
        pause = Pause.POWER;
        s = BuildState.resume(s, pause);
        pause = Pause.NONE;
        assertEquals(State.BUILDING, s);
        // another reload while building: back to WAITING, and the builder carries on
        s = BuildState.surveyed(BuildState.resurvey(s), started, pause);
        assertEquals(State.WAITING, s);
        s = BuildState.start(s, pause);
        s = BuildState.finish(s);
        assertEquals(State.DONE, s);
        assertTrue(BuildState.ended(s));
    }
}
