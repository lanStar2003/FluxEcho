package com.fluxecho.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.voyage.ScoreBridge;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The quest book's scoreboard tasks and volume II's new quests (0.17.0, narrative §7.2): every scoreboard task reads
 * an objective the scoreboard bridge registers ({@link ScoreBridge#OBJECTIVES}) and v1 writes, as a plain dummy
 * objective, with a target the objective can reach; the line {@code line/echo_general} runs codex → drift → tools →
 * ten → kinds → forty with the narrative's conditions; any one echo machine does for "tools".
 */
class QuestScoreTest {

    /** Every shipped quest by key, from the generated files (the key is not in a file: its id is). */
    private static Map<String, JsonObject> quests(String lineDir) throws IOException {
        Map<String, JsonObject> out = new HashMap<>();
        for (JsonElement e : QuestPackTest.json("index.json")
            .getAsJsonArray("lines")) {
            JsonObject line = e.getAsJsonObject();
            String dir = line.getAsJsonArray("dirs")
                .get(1)
                .getAsString();
            if (lineDir != null && !dir.startsWith("Quests/" + lineDir + "-")) continue;
            for (JsonElement f : line.getAsJsonArray("files")) {
                String path = f.getAsString();
                if (path.startsWith("Quests/")) out.put(path, QuestPackTest.json("DefaultQuests/" + path));
            }
        }
        return out;
    }

    private static JsonObject byKey(Map<String, JsonObject> quests, String key) throws Exception {
        long[] id = QuestPackTest.ids(key);
        for (JsonObject q : quests.values()) if (q.get("questIDHigh:4")
            .getAsLong() == id[0]
            && q.get("questIDLow:4")
                .getAsLong() == id[1])
            return q;
        throw new AssertionError(key + " is not shipped");
    }

    private static List<JsonObject> tasks(JsonObject q) {
        List<JsonObject> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> t : q.getAsJsonObject("tasks:9")
            .entrySet())
            out.add(
                t.getValue()
                    .getAsJsonObject());
        return out;
    }

    private static JsonObject props(JsonObject q) {
        return q.getAsJsonObject("properties:10")
            .getAsJsonObject("betterquesting:10");
    }

    @Test
    void everyScoreboardTaskReadsAnObjectiveTheBridgeWrites() throws IOException {
        int seen = 0;
        for (Map.Entry<String, JsonObject> q : quests(null).entrySet()) {
            for (JsonObject t : tasks(q.getValue())) {
                if (!"bq_standard:scoreboard".equals(
                    t.get("taskID:8")
                        .getAsString()))
                    continue;
                seen++;
                String name = t.get("scoreName:8")
                    .getAsString();
                assertTrue(ScoreBridge.V1.contains(name), q.getKey() + ": " + name + " has no value in 0.17 (v1)");
                assertEquals(
                    "dummy",
                    t.get("type:8")
                        .getAsString(),
                    q.getKey());
                assertEquals(
                    "MORE_OR_EQUAL",
                    t.get("operation:8")
                        .getAsString(),
                    q.getKey());
                int target = t.get("target:3")
                    .getAsInt();
                assertTrue(target >= 1 && target <= ScoreBridge.top(name), q.getKey() + ": target " + target);
            }
        }
        assertEquals(3, seen, "ten, kinds and forty");
    }

    @Test
    void volumeTwoRunsFromTheCodexToFortyLines() throws Exception {
        Map<String, JsonObject> general = quests("FluxEchoGeneral");
        String[] chain = { "echo_general/codex", "echo_general/drift", "echo_general/tools", "echo_general/ten",
            "echo_general/kinds", "echo_general/forty" };
        assertEquals(chain.length, general.size(), "narrative §7.2: 1 → 6");
        for (int i = 1; i < chain.length; i++) {
            JsonObject q = byKey(general, chain[i]);
            long[] pre = QuestPackTest.ids(chain[i - 1]);
            JsonObject p = q.getAsJsonObject("preRequisites:9")
                .getAsJsonObject("0:10");
            assertEquals(
                pre[0],
                p.get("questIDHigh:4")
                    .getAsLong(),
                chain[i]);
            assertEquals(
                pre[1],
                p.get("questIDLow:4")
                    .getAsLong(),
                chain[i]);
        }
        assertEquals(
            "bq_standard:checkbox",
            tasks(byKey(general, "echo_general/drift")).get(0)
                .get("taskID:8")
                .getAsString());
        int[][] want = { { 10 }, { 4 }, { 40 } };
        String[] names = { "feRec", "feCat", "feRec" };
        for (int i = 0; i < 3; i++) {
            JsonObject t = tasks(byKey(general, chain[3 + i])).get(0);
            assertEquals(
                names[i],
                t.get("scoreName:8")
                    .getAsString(),
                chain[3 + i]);
            assertEquals(
                want[i][0],
                t.get("target:3")
                    .getAsInt(),
                chain[3 + i]);
        }
        JsonObject tools = byKey(general, "echo_general/tools");
        assertEquals(
            "OR",
            props(tools).get("taskLogic:8")
                .getAsString(),
            "any one echo machine");
        List<Integer> machines = new ArrayList<>();
        for (JsonObject t : tasks(tools)) {
            assertEquals(
                "bq_standard:retrieval",
                t.get("taskID:8")
                    .getAsString());
            JsonObject item = t.getAsJsonObject("requiredItems:9")
                .getAsJsonObject("0:10");
            assertEquals(
                "gregtech:gt.blockmachines",
                item.get("id:8")
                    .getAsString());
            machines.add(
                item.get("Damage:2")
                    .getAsInt() - 24530);
        }
        assertEquals(Arrays.asList(0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 13), machines, "offsets 3 and 12 are retired");
    }
}
