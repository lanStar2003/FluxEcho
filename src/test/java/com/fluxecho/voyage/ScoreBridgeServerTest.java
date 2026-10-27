package com.fluxecho.voyage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.voyage.ScoreBridge;

/**
 * The bridge's writes on a real vanilla {@link Scoreboard}: the 31 objectives are registered once as plain dummy
 * objectives (what BetterQuesting's scoreboard task and {@code /scoreboard} expect), a value is written only when it
 * changes, a ladder never falls, nothing is created for a player who has nothing, and the cabin logs a player may
 * read are the buildings whose ladder has reached level 1.
 */
class ScoreBridgeServerTest {

    @Test
    void theObjectivesAreRegisteredOnceAsDummies() {
        Scoreboard board = new Scoreboard();
        assertEquals(31, ScoreBridgeServer.register(board));
        assertEquals(0, ScoreBridgeServer.register(board), "a second time adds nothing");
        for (String name : ScoreBridge.OBJECTIVES) {
            ScoreObjective o = board.getObjective(name);
            assertNotNull(o, name);
            assertSame(IScoreObjectiveCriteria.field_96641_b, o.getCriteria(), name + " is a dummy objective");
            assertEquals(name, o.getDisplayName());
        }
        Set<String> names = new LinkedHashSet<>();
        for (Object o : board.getScoreObjectives()) names.add(((ScoreObjective) o).getName());
        assertEquals(new LinkedHashSet<>(ScoreBridge.OBJECTIVES), names, "nothing else");
    }

    @Test
    void anObjectiveSomebodyMadeFirstIsKept() {
        Scoreboard board = new Scoreboard();
        // BetterQuesting makes the objective of a scoreboard task it sees first, as a dummy with the task's name
        ScoreObjective made = board.addScoreObjective("feRec", IScoreObjectiveCriteria.field_96641_b);
        made.setDisplayName("Records");
        assertEquals(30, ScoreBridgeServer.register(board));
        assertSame(made, board.getObjective("feRec"));
        assertEquals("Records", made.getDisplayName());
    }

    @Test
    void valuesAreWrittenWhenTheyChangeAndLaddersNeverFall() {
        Scoreboard board = new Scoreboard();
        ScoreBridgeServer.register(board);
        Map<String, Integer> v = new LinkedHashMap<>();
        v.put("feRec", 12);
        v.put("feRes", 4);
        v.put("fePhase", 2);
        v.put("feB02", 1);
        v.put("feB03", 0);
        assertEquals(4, ScoreBridgeServer.write(board, "Alex", v), "the four non-zero values");
        assertEquals(12, ScoreBridgeServer.score(board, "Alex", "feRec"));
        assertEquals(1, ScoreBridgeServer.score(board, "Alex", "feB02"));
        assertFalse(
            board.func_96510_d("Alex")
                .containsKey(board.getObjective("feB03")),
            "a zero is not written for a player who holds nothing");
        assertEquals(0, ScoreBridgeServer.write(board, "Alex", v), "nothing changed");

        // a building somebody raised further (a later version's level) and an unloaded nexus
        board.func_96529_a("Alex", board.getObjective("feB02"))
            .setScorePoints(3);
        v.put("fePhase", 0);
        v.put("feRes", 3);
        assertEquals(1, ScoreBridgeServer.write(board, "Alex", v), "only the research count moves");
        assertEquals(3, ScoreBridgeServer.score(board, "Alex", "feB02"), "the ladder keeps level 3");
        assertEquals(2, ScoreBridgeServer.score(board, "Alex", "fePhase"), "the phase reached stays");
        assertEquals(3, ScoreBridgeServer.score(board, "Alex", "feRes"), "a count follows the state down");
        assertEquals(-1, ScoreBridgeServer.score(board, "Steve", "feRec"), "nothing for another player");
        assertEquals(-1, ScoreBridgeServer.score(board, "Alex", "noSuchObjective"));
    }

    @Test
    void theCabinLogsAreTheBuildingsAtLevelOne() {
        Scoreboard board = new Scoreboard();
        assertTrue(
            ScoreBridgeServer.halls(board, "Alex")
                .isEmpty(),
            "no objectives yet");
        ScoreBridgeServer.register(board);
        Map<String, Integer> v = new LinkedHashMap<>();
        v.put("feB01", 1);
        v.put("feB04", 1);
        v.put("feB13", 1);
        v.put("feRec", 40);
        ScoreBridgeServer.write(board, "Alex", v);
        board.func_96529_a("Alex", board.getObjective("feB20"))
            .setScorePoints(0);
        assertEquals(
            new LinkedHashSet<>(Arrays.asList("nexus", "root", "hunting_ground")),
            ScoreBridgeServer.halls(board, "Alex"),
            "in row order; a ladder at 0 is no log");
    }
}
