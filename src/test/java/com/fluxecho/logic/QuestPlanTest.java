package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class QuestPlanTest {

    @Test
    void onlyJsonFilesTheLineNoLongerShipsAreStale() {
        String dir = "Quests/LazyAE-f1sy";
        List<String> shipped = Arrays
            .asList(dir + "/Core-a.json", dir + "/Cables-b.json", "QuestLines/LazyAE-f1sy/QuestLine.json");
        List<String> present = Arrays.asList("Core-a.json", "Old-c.json", "Cables-b.json", "notes.txt");
        assertEquals(Arrays.asList(dir + "/Old-c.json"), QuestPlan.stale(dir, present, shipped));
    }

    @Test
    void sameNameInAnotherFolderDoesNotCount() {
        List<String> shipped = Arrays.asList("QuestLines/X/Core-a.json");
        assertEquals(
            Arrays.asList("Quests/X/Core-a.json"),
            QuestPlan.stale("Quests/X", Arrays.asList("Core-a.json"), shipped));
    }
}
