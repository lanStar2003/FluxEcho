package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.logic.LiftShaft;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.shape.FormCodes;
import com.fluxecho.skin.BlockSkin;
import com.fluxecho.skin.SkinModule;

/**
 * A light-lift shaft through the new material families (0.17.0, critique feasibility #19, refactor FINAL F04): the
 * hull, the footing and the skins are ours to a rider as the frame and the fittings are ({@code LiftShaft.Cell.OURS}),
 * so a shaft whose column runs through a hull floor, a footing course or a skin wall still rides up and down, and a
 * pad under one of them can still be boarded. Without it the nexus's, the pod's and the sky decks' shafts would stop
 * under the first new block.
 */
class ShaftClearTest {

    private static final int X = 3, Z = -5;
    private static Block air, stone;
    private static BlockFitting oldFitting;
    private static BlockFrame oldFrame;

    @BeforeAll
    static void setUp() {
        oldFitting = CampusModule.fitting;
        oldFrame = FrameModule.frame;
        if (CampusModule.fitting == null) CampusModule.fitting = new BlockFitting();
        if (FrameModule.frame == null) FrameModule.frame = new BlockFrame();
        SkinModule.create();
        air = new Block(Material.air) {};
        stone = new Block(Material.rock) {};
    }

    @AfterAll
    static void tearDown() {
        CampusModule.fitting = oldFitting;
        FrameModule.frame = oldFrame;
    }

    /** A world whose column at (X, Z) is air from y 0 up, but stone at y 63 and the cells given. */
    private static FakeWorld column(Object[][] cells) {
        FakeWorld w = FakeWorld.create();
        for (int y = 0; y < 256; y++) w.set(X, y, Z, air, 0);
        w.set(X, 63, Z, stone, 0);
        for (Object[] c : cells) w.set(X, (Integer) c[0], Z, (Block) c[1], (Integer) c[2]);
        return w;
    }

    @Test
    void everyBlockOfTheNewFamiliesIsOurs() {
        FakeWorld w = FakeWorld.create();
        int n = 0;
        for (BlockSkin b : SkinModule.all()) for (int meta = 0; meta < 16; meta++) {
            w.set(0, 64, 0, b, meta);
            assertEquals(LiftShaft.Cell.OURS, Lift.cell(w, 0, 64, 0), b.registryName() + " " + meta);
            n++;
        }
        assertEquals(8 * 16, n);
        // as the frame and the fittings are; a pad is a pad
        w.set(0, 64, 0, FrameModule.frame, Parts.FR_PILLAR);
        assertEquals(LiftShaft.Cell.OURS, Lift.cell(w, 0, 64, 0));
        w.set(0, 64, 0, CampusModule.fitting, Parts.F_LIFT);
        assertEquals(LiftShaft.Cell.PAD, Lift.cell(w, 0, 64, 0));
        w.set(0, 64, 0, stone, 0);
        assertEquals(LiftShaft.Cell.SOLID, Lift.cell(w, 0, 64, 0), "anything else stops a ride");
    }

    /**
     * A shaft from a pad at y 64 to one at y 74 through a footing course (y 66), a hull floor of a round shape (y 69),
     * a garden skin wall (y 71) and a hull slab (y 72): it rides up all the way and back down, unblocked.
     */
    @Test
    void aShaftThroughTheNewFamiliesRides() {
        FakeWorld w = column(
            new Object[][] { { 64, CampusModule.fitting, Parts.F_LIFT }, { 66, SkinModule.footing, 0 },
                { 69, SkinModule.hullForm, FormCodes.number(98) }, { 71, SkinModule.garden, 0 },
                { 72, SkinModule.hullForm, 4 }, { 74, CampusModule.fitting, Parts.F_LIFT } });
        LiftShaft.Column col = y -> Lift.cell(w, X, y, Z);
        LiftShaft.Ride up = LiftShaft.plan(col, 64, 1);
        assertNotNull(up, "a ride up");
        assertFalse(up.blocked, "nothing of ours blocks it");
        assertEquals(75, up.to, 1e-9);
        LiftShaft.Ride down = LiftShaft.plan(col, 74, -1);
        assertNotNull(down, "a ride down");
        assertFalse(down.blocked);
        assertEquals(65, down.to, 1e-9);
        // the same column with stone where the hull floor is stops under it
        w.set(X, 69, Z, stone, 0);
        LiftShaft.Ride bumped = LiftShaft.plan(col, 64, 1);
        assertTrue(bumped == null || bumped.blocked, "stone stops a ride");
    }

    /** A rider passes through them while riding and in the grace after; a player boards a pad under one. */
    @Test
    void ridersPassAndBoard() {
        FakeWorld w = column(
            new Object[][] { { 64, CampusModule.fitting, Parts.F_LIFT }, { 65, SkinModule.block(Family.HULL, true), 0 },
                { 66, SkinModule.block(Family.SKIN_SCRIPT, false), 7 } });
        LiftShaft.Cell self = Lift.cell(w, X, 65, Z), above = Lift.cell(w, X, 66, Z);
        assertTrue(LiftShaft.passes(self, true, false));
        assertTrue(LiftShaft.passes(self, false, true), "in the grace after a ride");
        assertFalse(LiftShaft.passes(self, false, false), "never otherwise");
        assertTrue(LiftShaft.boardable(self, LiftShaft.Cell.PAD, LiftShaft.Cell.SOLID), "the cell over the pad");
        assertTrue(LiftShaft.boardable(above, self, LiftShaft.Cell.PAD), "the one over that");
    }
}
