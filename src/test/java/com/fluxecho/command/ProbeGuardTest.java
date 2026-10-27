package com.fluxecho.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fluxecho.campus.BlockDeck;
import com.fluxecho.campus.BlockFitting;
import com.fluxecho.campus.CampusModule;
import com.fluxecho.campus.FakeWorld;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.FrameModule;
import com.fluxecho.logic.Parts;
import com.fluxecho.skin.SkinModule;

/**
 * What a check-up rig ({@code /fluxecho probe}, {@code swatch}) refuses to overwrite (0.17.0 review): a block entity,
 * as F06 had it, and a part of a campus. Without the second, a swatch wall or a probe set out by an operator standing
 * in their city cleared the walls, floors and fittings in its box (blocks without a block entity), and
 * {@code swatch clear} put grass and dirt back where the plaza had been. The rigs' own blocks, the new material
 * families and the planting bed, are no campus part in 0.17.0 (no plan lays them), so a rig built again or cleared
 * still replaces them.
 */
class ProbeGuardTest {

    private static BlockDeck oldDeck;
    private static BlockFitting oldFitting;
    private static BlockFrame oldFrame;
    private static Block stone;

    @BeforeAll
    static void setUp() {
        oldDeck = CampusModule.deck;
        oldFitting = CampusModule.fitting;
        oldFrame = FrameModule.frame;
        if (CampusModule.deck == null) CampusModule.deck = new BlockDeck();
        if (CampusModule.fitting == null) CampusModule.fitting = new BlockFitting();
        if (FrameModule.frame == null) FrameModule.frame = new BlockFrame();
        SkinModule.create();
        stone = new Block(Material.rock) {};
    }

    @AfterAll
    static void tearDown() {
        CampusModule.deck = oldDeck;
        CampusModule.fitting = oldFitting;
        FrameModule.frame = oldFrame;
    }

    /** A 3 x 3 x 3 world of stone round the origin, with one cell set. */
    private static FakeWorld world(Block b, int meta) {
        FakeWorld w = FakeWorld.create();
        w.loadedTileEntityList = new ArrayList<>();
        for (int x = 0; x < 3; x++) for (int y = 64; y < 67; y++) for (int z = 0; z < 3; z++) w.set(x, y, z, stone, 0);
        w.set(1, 65, 1, b, meta);
        return w;
    }

    private static final int[] BOX = { 0, 64, 0, 2, 66, 2 };

    @Test
    void aCampusPartStopsARig() {
        assertArrayEquals(new int[] { 1, 65, 1 }, ProbeWorld.occupied(world(CampusModule.deck, Parts.D_DECK), BOX));
        assertArrayEquals(
            new int[] { 1, 65, 1 },
            ProbeWorld.occupied(world(CampusModule.deck, Parts.D_PANEL_LIT), BOX),
            "an old building's wall");
        assertArrayEquals(
            new int[] { 1, 65, 1 },
            ProbeWorld.occupied(world(FrameModule.frame, Parts.FR_PILLAR), BOX),
            "the nexus's white rib");
        assertArrayEquals(
            new int[] { 1, 65, 1 },
            ProbeWorld.occupied(world(CampusModule.fitting, Parts.F_RAIL), BOX),
            "a gallery's rail");
        assertTrue(ProbeWorld.cityPart(CampusModule.deck, Parts.D_HULL_NUMBER), "the hull-number tile is a floor tile");
    }

    @Test
    void natureAndTheRigsOwnBlocksDoNot() {
        assertNull(ProbeWorld.occupied(world(stone, 0), BOX));
        assertNull(ProbeWorld.occupied(world(SkinModule.hull, 0), BOX), "the probes' hull wall");
        assertNull(ProbeWorld.occupied(world(SkinModule.hullForm, 2), BOX), "a swatch's round hull");
        assertNull(ProbeWorld.occupied(world(SkinModule.footing, 0), BOX), "the sun probe's light well");
        assertNull(ProbeWorld.occupied(world(CampusModule.fitting, Parts.F_BED), BOX), "the bed probe's beds");
        assertFalse(ProbeWorld.cityPart(stone, 0));
        assertNull(ProbeWorld.occupied(world(null, 0), BOX), "a cell the world has no block for");
    }
}
