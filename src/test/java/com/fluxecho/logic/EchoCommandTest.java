package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The arguments of {@code /fluxecho} (0.10.1): every use the command has, case-blind, nothing else accepted; the
 * tab completion of each word; and the nexus the build commands act on (the nearest within 128 blocks).
 */
class EchoCommandTest {

    private static EchoCommand.Parsed p(String... args) {
        return EchoCommand.parse(args);
    }

    @Test
    void researchForTheSenderOrANamedPlayer() {
        EchoCommand.Parsed a = p("research", "all");
        assertEquals(EchoCommand.Action.RESEARCH_ALL, a.action);
        assertNull(a.player, "the sender's own team");
        EchoCommand.Parsed r = p("research", "reset", "Ann");
        assertEquals(EchoCommand.Action.RESEARCH_RESET, r.action);
        assertEquals("Ann", r.player, "the name is kept as typed");
        assertEquals(EchoCommand.Action.RESEARCH_ALL, p("Research", "ALL", "bob").action);
        assertNull(p("research"));
        assertNull(p("research", "some"));
        assertNull(p("research", "all", "Ann", "Bob"), "one player at most");
        assertNull(p("research", "all", ""));
    }

    @Test
    void theBuildCommands() {
        EchoCommand.Parsed toggle = p("build", "free");
        assertEquals(EchoCommand.Action.BUILD_FREE, toggle.action);
        assertNull(toggle.free, "no word: toggled");
        assertEquals(Boolean.TRUE, p("build", "free", "on").free);
        assertEquals(Boolean.FALSE, p("build", "FREE", "Off").free);
        assertNull(p("build", "free", "maybe"));
        assertNull(p("build", "free", "on", "now"));
        assertEquals(EchoCommand.Action.BUILD_FINISH, p("build", "finish").action);
        assertEquals(EchoCommand.Action.BUILD_STATUS, p("BUILD", "status").action);
        assertNull(p("build", "finish", "now"));
        assertNull(p("build", "status", "x"));
        assertNull(p("build"));
        assertNull(p("build", "go"));
        assertNull(p());
        assertNull(p("help"));
        assertNull(EchoCommand.parse(null));
    }

    @Test
    void completion() {
        List<String> players = Arrays.asList("Ann", "anton", "Bob");
        assertEquals(Arrays.asList("research", "build"), EchoCommand.complete(new String[] { "" }, players));
        assertEquals(Collections.singletonList("research"), EchoCommand.complete(new String[] { "RE" }, players));
        assertEquals(Arrays.asList("all", "reset"), EchoCommand.complete(new String[] { "research", "" }, players));
        assertEquals(
            Arrays.asList("free", "finish", "status"),
            EchoCommand.complete(new String[] { "build", "" }, players));
        assertEquals(Arrays.asList("free", "finish"), EchoCommand.complete(new String[] { "build", "f" }, players));
        assertEquals(
            Arrays.asList("Ann", "anton"),
            EchoCommand.complete(new String[] { "research", "all", "an" }, players),
            "player names, case-blind");
        assertEquals(Arrays.asList("on", "off"), EchoCommand.complete(new String[] { "build", "free", "o" }, players));
        assertTrue(
            EchoCommand.complete(new String[] { "build", "finish", "" }, players)
                .isEmpty());
        assertTrue(
            EchoCommand.complete(new String[] { "research", "some", "" }, players)
                .isEmpty());
        assertTrue(
            EchoCommand.complete(new String[] { "research", "all", "Ann", "" }, players)
                .isEmpty());
        assertTrue(
            EchoCommand.complete(new String[] { "x", "" }, players)
                .isEmpty());
        assertTrue(
            EchoCommand.complete(new String[0], players)
                .isEmpty());
    }

    @Test
    void theNearestNexusWithin128Blocks() {
        List<double[]> centres = Arrays
            .asList(new double[] { 100, 64, 100 }, new double[] { 10, 64, 0 }, new double[] { 0, 64, 200 });
        assertEquals(1, EchoCommand.nearest(centres, 0, 64, 0));
        assertEquals(0, EchoCommand.nearest(centres, 90, 70, 90));
        assertEquals(-1, EchoCommand.nearest(centres, 0, 64, -300), "none within range");
        assertEquals(
            0,
            EchoCommand.nearest(Collections.singletonList(new double[] { 128, 0, 0 }), 0, 0, 0),
            "128 blocks is still in range");
        assertEquals(-1, EchoCommand.nearest(Collections.singletonList(new double[] { 129, 0, 0 }), 0, 0, 0));
        assertEquals(-1, EchoCommand.nearest(Collections.emptyList(), 0, 0, 0));
    }
}
