package com.fluxecho.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The shipped quest lines have the shape QuestInjector and QuestInstaller rely on. */
class QuestPackTest {

    private static final String ROOT = "/assets/fluxecho/quests/";

    @Test
    void everyLineIsCompleteAndShipped() throws IOException {
        JsonArray lines;
        try (Reader r = new InputStreamReader(open("index.json"), StandardCharsets.UTF_8)) {
            lines = new JsonParser().parse(r)
                .getAsJsonObject()
                .getAsJsonArray("lines");
        }
        assertFalse(lines.size() == 0, "no quest lines in index.json");
        for (JsonElement e : lines) {
            JsonObject line = e.getAsJsonObject();
            String order = line.get("order")
                .getAsString();
            String chapter = line.getAsJsonArray("dirs")
                .get(0)
                .getAsString();
            String quests = line.getAsJsonArray("dirs")
                .get(1)
                .getAsString();
            int lineFiles = 0;
            Set<String> placed = new TreeSet<>(), defined = new TreeSet<>();
            for (JsonElement f : line.getAsJsonArray("files")) {
                String path = f.getAsString();
                open("DefaultQuests/" + path).close();
                if (path.equals(chapter + "/QuestLine.json")) lineFiles++;
                else if (path.startsWith(chapter + "/")) placed.add(path.substring(chapter.length() + 1));
                else if (path.startsWith(quests + "/")) defined.add(path.substring(quests.length() + 1));
                else throw new AssertionError(path + " is outside the folders of " + order);
            }
            assertEquals(1, lineFiles, order + " needs exactly one QuestLine.json");
            assertFalse(defined.isEmpty(), order + " has no quests");
            assertEquals(defined, placed, order + ": every quest needs a position in the line and vice versa");
        }
    }

    private static InputStream open(String path) {
        InputStream in = QuestPackTest.class.getResourceAsStream(ROOT + path);
        assertNotNull(in, "missing resource " + ROOT + path);
        return in;
    }
}
