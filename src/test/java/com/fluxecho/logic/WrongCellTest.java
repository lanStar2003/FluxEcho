package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The cell that keeps a structure from forming (0.14.2) travels to the client as one string ({@code Wc} on the build
 * page, a module window's and the nexus header's sync) and comes back the same; nothing comes back from nothing or from
 * a broken string, so the screens fall back to the plain hint.
 */
class WrongCellTest {

    @Test
    void aWrongCellTravelsAsOneString() {
        // the user's gate: a snow layer in the right wing's door onto the passage
        WrongCell c = new WrongCell(158, 80, 207, "tile.snow.name", WrongCell.AIR);
        assertEquals("158,80,207|tile.snow.name|fluxecho.build.cell.air", c.encode());
        assertEquals(c, WrongCell.decode(c.encode()));
        assertEquals(
            c.hashCode(),
            WrongCell.decode(c.encode())
                .hashCode());
        assertEquals("158, 80, 207", c.pos());
        WrongCell far = new WrongCell(-29_999_999, 0, -12, "gt.blockcasings.5.name", "tile.fluxecho.frame.3.name");
        assertEquals(far, WrongCell.decode(WrongCell.encode(far)));
        assertNotEquals(c, far);
    }

    @Test
    void nothingComesBackFromNothing() {
        assertEquals("", WrongCell.encode(null));
        assertNull(WrongCell.decode(""));
        assertNull(WrongCell.decode(null));
        assertNull(WrongCell.decode("1,2|a"), "one field missing");
        assertNull(WrongCell.decode("1,2|a|b"), "two coordinates");
        assertNull(WrongCell.decode("1,2,x|a|b"), "not a number");
        assertEquals(WrongCell.AIR, new WrongCell(0, 0, 0, null, null).found, "no block is air");
        assertEquals(WrongCell.AIR, new WrongCell(0, 0, 0, null, null).wanted);
    }
}
