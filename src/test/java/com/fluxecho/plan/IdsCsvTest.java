package com.fluxecho.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.Tools;

/**
 * The ID ledger {@code docs/dev/ids.md} is the only source of the IDs; {@code docs/dev/ids.csv} is its export by
 * {@code tools/IdsCsv.java}, which later tests (the ID freeze, the message kinds, the parts) read. This keeps the two
 * the same, and the CSV well formed.
 */
class IdsCsvTest {

    static final Set<String> STATUSES = new HashSet<>(Arrays.asList("已发布", "新增", "本版", "预留", "作废"));

    static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    @Test
    void theCsvIsTheExportOfTheLedger() throws IOException {
        String csv = (String) Tools.call("IdsCsv", "csv", new Class<?>[] { String.class }, read("docs/dev/ids.md"));
        assertEquals(csv, read("docs/dev/ids.csv"), "docs/dev/ids.csv is stale: run java tools/IdsCsv.java");
    }

    @Test
    void everyRowHasAClassAValueAndAKnownStatus() throws IOException {
        List<List<String>> rows = Csv.parse(read("docs/dev/ids.csv"));
        assertEquals(
            Arrays.asList("class", "section", "value", "meaning", "owner", "since", "status", "source"),
            rows.get(0));
        // the ledger had 1,076 rows when it moved into the repository; rows are only ever added
        assertTrue(rows.size() - 1 >= 1076, "rows were dropped from the ledger");
        for (List<String> r : rows.subList(1, rows.size())) {
            assertEquals(8, r.size(), "row " + r);
            assertFalse(
                r.get(0)
                    .isEmpty(),
                "row without a class: " + r);
            assertFalse(
                r.get(2)
                    .isEmpty(),
                "row without a value: " + r);
            assertTrue(STATUSES.contains(r.get(6)), "unknown status in " + r);
        }
    }
}
