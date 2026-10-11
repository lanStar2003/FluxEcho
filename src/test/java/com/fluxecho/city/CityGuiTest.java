package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.ModuleMap;

/**
 * The module window's server side, on an Eco Dome: the selection is checked and goes round, the grid is the unit shown
 * looked up on every access (so a change of unit never leaves a stale slot mapping), it keeps one item a slot and
 * nothing past the unit's slots, and the client's grid only predicts. Also that the window's layout fits: the grid in
 * the unit panel, the plan in the plan panel, the tabs and the storeys in the window.
 */
class CityGuiTest {

    private static final Item ITEM = new Item();

    private static TileEcoDome dome() {
        TileEcoDome d = new TileEcoDome();
        d.xCoord = 100;
        d.yCoord = 70;
        d.zCoord = -40;
        d.place((UUID) null, null, 2);
        return d;
    }

    private static CityGui.Grid grid(TileEcoDome d, CityGui.Sel sel, int slots) {
        CityGui.View view = new CityGui.View(CityGui.map(d));
        CityGui.Link link = new CityGui.Link(d, sel, view);
        return new CityGui.Grid(d, () -> sel.unit, link, false, slots);
    }

    @Test
    void theSelectionIsCheckedAndGoesRound() {
        TileEcoDome d = dome();
        d.selectedUnit = 5;
        CityGui.Sel sel = new CityGui.Sel(d);
        assertEquals(5, sel.unit, "the window opens at the unit last shown");
        sel.set(12);
        sel.set(-1);
        assertEquals(5, sel.unit, "a unit the dome does not have is ignored");
        sel.set(7);
        assertEquals(7, sel.unit);
        assertEquals(7, d.selectedUnit, "written back for the next window");
        sel.step(5);
        assertEquals(0, sel.unit, "round from the last to the first");
        sel.step(-1);
        assertEquals(11, sel.unit);
        d.selectedUnit = 40;
        assertEquals(11, new CityGui.Sel(d).unit, "a stray saved selection is brought into range");
    }

    @Test
    void theGridIsTheUnitShownLookedUpOnEveryAccess() {
        TileEcoDome d = dome();
        CityGui.Sel sel = new CityGui.Sel(d);
        CityGui.Grid g = grid(d, sel, CityGui.gridSlots(d.layout()));
        assertEquals(3, g.getSlots(), "a planter has three slots");
        sel.set(2);
        int first = d.unitSlots(2)[0];
        ItemStack seeds = new ItemStack(ITEM, 5, 0);
        assertTrue(g.isItemValid(0, seeds));
        ItemStack left = g.insertItem(0, seeds, false);
        assertEquals(4, left.stackSize, "one item a slot");
        assertNotNull(d.sample(first));
        assertEquals(1, d.sample(first).stackSize);
        assertEquals(seeds, g.insertItem(0, seeds, false), "a slot that holds one takes no more");
        // another unit: the grid shows its slots at once
        sel.set(3);
        assertNull(g.getStackInSlot(0));
        assertNotNull(d.sample(first), "nothing moved");
        sel.set(2);
        assertNotNull(g.getStackInSlot(0));
        ItemStack out = g.extractItem(0, 64, false);
        assertEquals(1, out.stackSize);
        assertNull(d.sample(first));
        // setting a slot goes to the unit shown
        g.setStackInSlot(2, new ItemStack(ITEM, 1, 0));
        assertNotNull(d.sample(first + 2));
        assertEquals(1, g.getSlotLimit(0));
    }

    @Test
    void slotsPastTheUnitAreInert() {
        TileEcoDome d = dome();
        CityGui.Sel sel = new CityGui.Sel(d);
        CityGui.Grid g = grid(d, sel, 5);
        sel.set(4);
        ItemStack one = new ItemStack(ITEM, 1, 0);
        for (int k = 3; k < 5; k++) {
            assertEquals(-1, g.slot(k));
            assertFalse(g.isItemValid(k, one));
            assertEquals(one, g.insertItem(k, one, false));
            g.setStackInSlot(k, one);
            assertNull(g.getStackInSlot(k));
            assertNull(g.extractItem(k, 1, false));
        }
        assertEquals(0, d.sampleCount(), "nothing went anywhere");
        assertFalse(g.isItemValid(0, null));
        assertFalse(g.isItemValid(-1, one));
    }

    @Test
    void theClientsGridOnlyPredicts() {
        TileEcoDome d = dome();
        CityGui.View view = new CityGui.View(CityGui.map(d));
        CityGui.Sel sel = new CityGui.Sel(d);
        CityGui.Link link = new CityGui.Link(d, sel, view);
        CityGui.Grid g = new CityGui.Grid(d, () -> view.unit, link, true, 3);
        ItemStack seeds = new ItemStack(ITEM, 5, 0);
        assertFalse(g.isItemValid(0, seeds), "nothing before the server says which unit");
        view.shown(9, d.unitCount());
        assertEquals(9, view.unit);
        assertEquals(1, view.listPage, "the list turns to the unit's page");
        assertTrue(g.isItemValid(0, seeds));
        assertEquals(4, g.insertItem(0, seeds, false).stackSize);
        assertNotNull(g.getStackInSlot(0));
        assertEquals(0, d.sampleCount(), "the client's grid does not touch the samples");
        assertEquals(1, g.extractItem(0, 1, false).stackSize);
        assertNull(g.getStackInSlot(0));
    }

    @Test
    void theWindowsPagesFit() {
        TileEcoDome d = dome();
        ModuleMap m = CityGui.map(d);
        int[] at = CityGui.mapPos(m);
        assertTrue(at[0] >= 8 && at[0] + m.w <= CityGui.PLAN_RIGHT - 2, "the plan in its panel across");
        assertTrue(at[1] >= CityGui.PAGE_TOP + 2 && at[1] + m.h <= CityGui.PAGE_BOTTOM - 2, "the plan in its panel");
        for (int slots : new int[] { 1, 3, 9, CityGui.GRID_MAX }) for (int k = 0; k < slots; k++) {
            int[] p = CityGui.gridPos(slots, k);
            assertTrue(p[0] >= CityGui.DETAIL_LEFT + 2 && p[0] + 18 <= CityGui.PANEL_RIGHT - 2, slots + " slots: " + k);
            assertTrue(p[1] >= CityGui.HEAD_Y + CityGui.HEAD_H && p[1] + 18 <= CityGui.HINT_Y, slots + " slots: " + k);
        }
        assertEquals(3, CityGui.gridSlots(d.layout()));
        int last = CityGui.TAB_X[CityGui.TAB_X.length - 1] + CityGui.TAB_W;
        assertTrue(last <= CityGui.W - 4 && CityGui.TAB_X[0] >= 6 + CityGui.HEADER_W, "the tabs beside the header");
        int storeys = Math.min(CityGui.STOREY_MAX, m.storeys()), cols = Math.min(storeys, CityGui.STOREY_COLS);
        int right = CityGui.SIDE_X + cols * (CityGui.STOREY_W + CityGui.STOREY_GAP) - CityGui.STOREY_GAP;
        int rows = (storeys + CityGui.STOREY_COLS - 1) / CityGui.STOREY_COLS;
        assertTrue(right <= CityGui.PANEL_RIGHT - 2, "the storey tabs across");
        assertTrue(
            CityGui.STOREY_Y + rows * (CityGui.STOREY_H + CityGui.STOREY_GAP) <= CityGui.INFO_Y - 3,
            "the storey tabs above the unit shown");
        assertTrue(CityGui.LIST_Y + CityGui.ROWS * CityGui.ROW_H <= CityGui.PAGE_BOTTOM - 2, "the list in its panel");
        assertTrue(CityGui.NAV_Y + CityGui.NAV_H <= CityGui.PAGE_BOTTOM - 2);
        assertTrue(CityGui.FOOT_Y + CityGui.FOOT_H <= CityGui.INV_Y - 3);
        assertTrue(CityGui.INV_Y + 76 + 4 <= CityGui.H, "the inventory in the window");
    }
}
