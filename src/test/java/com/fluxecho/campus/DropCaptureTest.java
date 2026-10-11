package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

/**
 * The capture of what a block spills while the builder takes it down (0.10.1): only while a capture is open, only in
 * its world, only near its cell; every stack it takes is kept (a copy) and handed back once when it closes.
 */
class DropCaptureTest {

    /** A stack of no item (its count and tag are what is checked) marked with a tag. */
    private static ItemStack marked(int count, String mark) {
        ItemStack s = new ItemStack((Item) null, count, 0);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Mark", mark);
        s.setTagCompound(tag);
        return s;
    }

    private static List<String> marks(List<ItemStack> l) {
        List<String> out = new ArrayList<>();
        for (ItemStack s : l) out.add(
            s.getTagCompound()
                .getString("Mark") + "x"
                + s.stackSize);
        return out;
    }

    @Test
    void theSpillOfACellIsCaughtAndNothingElse() {
        Object world = new Object(), other = new Object();
        assertFalse(DropCapture.take(world, 10.5, 64.5, -19.5, marked(1, "before")), "nothing is open");
        ItemStack chest = marked(27, "chest");
        DropCapture.begin(world, 10, 64, -20);
        List<ItemStack> got;
        try {
            assertTrue(DropCapture.active());
            assertTrue(DropCapture.take(world, 10.5, 64.5, -19.5, chest), "the chest's contents");
            assertTrue(DropCapture.take(world, 8.0, 62.0, -22.0, marked(1, "low")), "two blocks out, inclusive");
            assertTrue(DropCapture.take(world, 12.99, 66.99, -17.01, marked(1, "high")));
            assertFalse(DropCapture.take(world, 13.0, 64.5, -19.5, marked(1, "far")), "three blocks out");
            assertFalse(DropCapture.take(world, 7.99, 64.5, -19.5, marked(1, "far")));
            assertFalse(DropCapture.take(world, 10.5, 67.0, -19.5, marked(1, "far")));
            assertFalse(DropCapture.take(world, 10.5, 64.5, -17.0, marked(1, "far")));
            assertFalse(DropCapture.take(other, 10.5, 64.5, -19.5, marked(1, "elsewhere")), "another world");
            assertFalse(DropCapture.take(world, 10.5, 64.5, -19.5, null));
            assertFalse(DropCapture.take(world, 10.5, 64.5, -19.5, marked(0, "empty")));
        } finally {
            got = DropCapture.end();
        }
        assertEquals(Arrays.asList("chestx27", "lowx1", "highx1"), marks(got));
        assertNotSame(chest, got.get(0), "a copy: the entity's own stack goes with the entity");
        assertFalse(DropCapture.active());
        assertTrue(
            DropCapture.end()
                .isEmpty(),
            "handed back once");
        assertFalse(DropCapture.take(world, 10.5, 64.5, -19.5, marked(1, "after")), "closed");
    }

    @Test
    void itemsOfAChunkThatLoadsAreNotTaken() {
        Object world = new Object();
        DropCapture.begin(world, 10, 64, -20);
        List<ItemStack> got;
        try {
            // a chunk loading in the capture box hands its saved items to the world with the same event: they are in
            // their chunk already, and a cancelled one would stay there and be saved again
            assertFalse(DropCapture.caught(world, true, 10.5, 64.5, -19.5, marked(5, "saved")));
            assertTrue(DropCapture.caught(world, false, 10.5, 64.5, -19.5, marked(3, "spilled")), "a fresh spawn");
            assertFalse(DropCapture.caught(world, false, 20.5, 64.5, -19.5, marked(1, "far")));
        } finally {
            got = DropCapture.end();
        }
        assertEquals(Arrays.asList("spilledx3"), marks(got));
    }
}
