package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class QuestOrderTest {

    private static final String GTNH = "AAAA: §6Tier 0\nBBBB: §6Tier 1\nMmDiQmi9SX-d2PbI-qhBiw==: 123Technology\n";
    private static final String LAZY = "f1sy-CqyVk-tiJKjcEchBw==: §b私货 · 懒人AE";
    private static final String SHARDS = "WdrYyNhuWnSyAlsuPnbb2g==: §3通量深层 · 碎片采集器";

    @Test
    void appendsMissingLinesAtTheEnd() {
        assertEquals(GTNH + LAZY + "\n" + SHARDS + "\n", QuestOrder.merge(GTNH, Arrays.asList(LAZY, SHARDS)));
    }

    @Test
    void unchangedFileIsReturnedAsIs() {
        String text = GTNH + LAZY + "\n" + SHARDS + "\n";
        assertSame(text, QuestOrder.merge(text, Arrays.asList(LAZY, SHARDS)));
        String twice = QuestOrder.merge(QuestOrder.merge(GTNH, Arrays.asList(LAZY)), Arrays.asList(LAZY));
        assertEquals(GTNH + LAZY + "\n", twice);
    }

    @Test
    void renamedLineIsReplacedInPlace() {
        String text = "AAAA: a\nf1sy-CqyVk-tiJKjcEchBw==: old name\nBBBB: b\n";
        assertEquals("AAAA: a\n" + LAZY + "\nBBBB: b\n", QuestOrder.merge(text, Arrays.asList(LAZY)));
    }

    @Test
    void otherLinesStayUntouched() {
        String text = "AAAA:  spaced  name \nBBBB: b\n";
        assertEquals(text + LAZY + "\n", QuestOrder.merge(text, Arrays.asList(LAZY)));
    }

    @Test
    void dropsByteOrderMarkAndBlankLines() {
        String text = (char) 0xFEFF + "AAAA: a\n\nBBBB: b\n\n\n";
        assertEquals("AAAA: a\nBBBB: b\n" + LAZY + "\n", QuestOrder.merge(text, Arrays.asList(LAZY)));
    }

    @Test
    void keepsWindowsLineEndings() {
        assertEquals(
            "AAAA: a\r\nBBBB: b\r\n" + LAZY + "\r\n",
            QuestOrder.merge("AAAA: a\r\nBBBB: b\r\n", Arrays.asList(LAZY)));
    }

    @Test
    void handlesMissingFinalNewlineAndEmptyFile() {
        assertEquals("AAAA: a\n" + LAZY + "\n", QuestOrder.merge("AAAA: a", Arrays.asList(LAZY)));
        assertEquals(LAZY + "\n", QuestOrder.merge("", Arrays.asList(LAZY)));
    }

    @Test
    void idIsTextBeforeTheFirstColon() {
        assertEquals("f1sy-CqyVk-tiJKjcEchBw==", QuestOrder.id(LAZY));
        assertEquals("abc", QuestOrder.id(" abc "));
    }
}
