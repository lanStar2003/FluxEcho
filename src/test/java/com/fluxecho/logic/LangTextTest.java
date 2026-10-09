package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Some mods' mob names have blanks they fill in themselves (Thaumcraft's "%s血腥主教%s"); a prey imprint showed them
 * as they were.
 */
class LangTextTest {

    @Test
    void blanksAreFound() {
        assertTrue(LangText.hasBlanks("%s血腥主教%s"));
        assertTrue(LangText.hasBlanks("邪术构装体%s"));
        assertTrue(LangText.hasBlanks("%1$s Warden %2$s"));
        assertTrue(LangText.hasBlanks("%d hits"));
        assertFalse(LangText.hasBlanks("50% faster"));
        assertFalse(LangText.hasBlanks("僵尸"));
        assertFalse(LangText.hasBlanks(null));
    }

    @Test
    void blanksAreLeftOut() {
        assertEquals("血腥主教", LangText.withoutBlanks("%s血腥主教%s"));
        assertEquals("邪术构装体", LangText.withoutBlanks("邪术构装体%s"));
        assertEquals("Warden", LangText.withoutBlanks("%1$s Warden %2$s"));
        assertEquals("50% faster", LangText.withoutBlanks("50% faster"));
    }
}
