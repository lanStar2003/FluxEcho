package com.fluxecho.client.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.skin.SkinCodec;
import com.fluxecho.logic.skin.SkinEntry;
import com.fluxecho.logic.skin.SkinView;

/**
 * The client's box table across a change of dimension (0.17.0 review): the server sends the new dimension's table
 * right after the respawn packet, and the nexus channel's pump hands it to {@link ClientSkin} at the start of a client
 * tick, before vanilla handles the respawn and unloads the old world. The old world going away must not take the new
 * dimension's table with it, or the lamp windows there stop following the rule until the next login; a world of the
 * table's own dimension going away (leaving, or the other order of the two packets) still forgets it.
 */
class ClientSkinUnloadTest {

    /** The view a whole table of a dimension leaves, with no campus in it. */
    private static SkinView of(int dim) {
        return SkinView.EMPTY
            .table(new SkinCodec.Table(dim, true, new long[0], Collections.<SkinEntry>emptyList()), 0).view;
    }

    @Test
    void theOldWorldGoingAwayKeepsTheNewDimensionsTable() {
        SkinView nether = of(-1);
        assertEquals(-1, nether.dim);
        assertFalse(ClientSkin.unloadForgets(nether, 0), "the overworld unloading after the nether's table came");
        assertFalse(ClientSkin.unloadForgets(of(0), 1), "the end unloading after the overworld's table came");
    }

    @Test
    void theTablesOwnWorldGoingAwayForgetsIt() {
        assertTrue(ClientSkin.unloadForgets(of(0), 0), "leaving the game from the overworld");
        assertTrue(ClientSkin.unloadForgets(of(-1), -1), "leaving the game from the nether");
    }
}
