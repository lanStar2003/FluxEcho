package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RemovalTest {

    @Test
    void ourLooseBlocksAreCreditedFluidsBecomeAirTheRestIsHarvested() {
        assertEquals(Removal.Way.CREDIT, Removal.way(false, true, true));
        assertEquals(Removal.Way.CREDIT, Removal.way(false, true, false), "a credit needs no player");
        assertEquals(Removal.Way.PLAIN, Removal.way(true, false, true), "a fluid gives nothing");
        assertEquals(Removal.Way.HARVEST, Removal.way(false, false, true), "stone, ores, machines, chests");
        assertEquals(Removal.Way.PLAIN, Removal.way(false, false, false), "no player to break it");
    }

    @Test
    void onlyAHarvestWaitsForRoomInTheStore() {
        assertTrue(Removal.needsRoom(false, false));
        assertFalse(Removal.needsRoom(true, false), "water and lava put nothing in the store");
        assertFalse(Removal.needsRoom(false, true), "our loose blocks go to the ledger");
    }

    @Test
    void anEmptyCellIsDoneHoweverTheBreakAnswered() {
        assertEquals(Removal.After.DONE, Removal.after(true, true, false, false));
        // a block that removed itself in onBlockHarvested: setting air again answers false, the cell is empty anyway
        assertEquals(Removal.After.DONE, Removal.after(false, true, false, false));
    }

    @Test
    void aBlockThatWaitedForItsHarvestIsCleared() {
        // removedByPlayer answered true but kept the block for its harvest, which did not remove it either
        assertEquals(Removal.After.CLEAR, Removal.after(true, false, true, false));
    }

    @Test
    void theWaterIceLeavesBecomesAir() {
        assertEquals(Removal.After.CLEAR, Removal.after(true, false, false, true));
        assertEquals(Removal.After.CLEAR, Removal.after(false, false, false, true));
    }

    @Test
    void aBlockThatWouldNotGoOrWasReplacedIsRefused() {
        assertEquals(Removal.After.REFUSED, Removal.after(false, false, true, false), "it refused to go");
        assertEquals(Removal.After.REFUSED, Removal.after(true, false, false, false), "something else stands there");
        assertEquals(Removal.After.REFUSED, Removal.after(false, false, false, false));
    }
}
