package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CompactTest {

    @Test
    void smallNumbersStayWhole() {
        assertEquals("0", Compact.si(0));
        assertEquals("950", Compact.si(950));
        assertEquals("999", Compact.si(999));
    }

    @Test
    void largeNumbersKeepThreeDigits() {
        assertEquals("1.00K", Compact.si(1000));
        assertEquals("1.20K", Compact.si(1204));
        assertEquals("45.0M", Compact.si(45_000_000));
        assertEquals("400K", Compact.si(400_000));
        assertEquals("1.00M", Compact.si(999_999));
        assertEquals("9.22E", Compact.si(Long.MAX_VALUE));
        assertEquals("-1.50K", Compact.si(-1500));
    }

    @Test
    void visPerSecondFromCentivisPerTick() {
        assertEquals("5", Compact.visPerSecond(25));
        assertEquals("15", Compact.visPerSecond(75));
        assertEquals("6.6", Compact.visPerSecond(33));
        assertEquals("0", Compact.visPerSecond(0));
    }

    @Test
    void visIsWholeVis() {
        assertEquals("25", Compact.vis(2599));
        assertEquals("0", Compact.vis(-5));
    }

    @Test
    void fractionIsClamped() {
        assertEquals(0f, Compact.fraction(5, 0));
        assertEquals(0f, Compact.fraction(-1, 10));
        assertEquals(0.5f, Compact.fraction(5, 10));
        assertEquals(1f, Compact.fraction(50, 10));
    }
}
