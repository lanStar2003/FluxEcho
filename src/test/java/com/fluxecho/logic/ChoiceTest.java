package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChoiceTest {

    @Test
    void singleCandidateNeedsNoCircuit() {
        assertEquals(0, Choice.pick(1, 0));
        assertEquals(0, Choice.pick(1, 7));
    }

    @Test
    void severalCandidatesNeedTheirCircuit() {
        assertEquals(-1, Choice.pick(3, 0));
        assertEquals(0, Choice.pick(3, 1));
        assertEquals(2, Choice.pick(3, 3));
        assertEquals(-1, Choice.pick(3, 4));
    }

    @Test
    void noCandidatesPickNothing() {
        assertEquals(-1, Choice.pick(0, 1));
    }
}
