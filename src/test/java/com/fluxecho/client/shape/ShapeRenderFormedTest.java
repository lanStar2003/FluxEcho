package com.fluxecho.client.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fluxecho.campus.BlockFitting;
import com.fluxecho.campus.FakeWorld;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.frame.Formed;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.shape.Form;
import com.fluxecho.logic.shape.Nbhd;
import com.fluxecho.logic.shape.Overlay;
import com.fluxecho.logic.shape.Quad;
import com.fluxecho.logic.shape.ShapeGeo;
import com.fluxecho.logic.shape.SkinBlocks;
import com.fluxecho.skin.BlockSkin;
import com.fluxecho.skin.ShapeAccess;
import com.fluxecho.skin.SkinModule;

/**
 * What the one renderer of the new families draws, read where the world meets {@code ShapeGeo} (0.17.0, style FINAL
 * §3.3, §3.9.8; {@link ShapeAccess}): every listed item has a closed mesh with a unit normal on every vertex, the
 * texture coordinates in the cell; a block's neighbours decide its faces (ours, solid, water, open), the frame's and
 * the fittings' whole cubes cull as solid; and a formed multiblock's hidden cell ({@link Formed#HIDE}) draws nothing
 * while its neighbours draw their faces toward it again, as the frame does. The block's own flags (opaque whole ids,
 * see-through shapes letting light through, lit shapes' light) are checked here too. The pixels themselves can only be
 * looked at in the game; this is everything that decides them.
 */
class ShapeRenderFormedTest {

    private static final long OWNER = Formed.key(0, 200, 0);
    private static Block water;

    @BeforeAll
    static void setUp() {
        SkinModule.create();
        water = new Block(Material.water) {

            @Override
            public boolean isOpaqueCube() {
                return false;
            }
        };
    }

    @AfterEach
    void clear() {
        Formed.clearClient();
    }

    @AfterAll
    static void tearDown() {
        Formed.clearClient();
    }

    /** A client world (the chunk builder's view: the client's table of hidden cells). */
    private static FakeWorld client() {
        FakeWorld w = FakeWorld.create();
        w.isRemote = true;
        return w;
    }

    private static boolean faces(List<Quad> qs, int face) {
        for (Quad q : qs) if (q.face == face) return true;
        return false;
    }

    private static void wellFormed(List<Quad> qs, String what) {
        assertFalse(qs.isEmpty(), what + " draws something");
        for (Quad q : qs) {
            for (int v = 0; v < 4; v++) {
                double nx = q.normal[3 * v], ny = q.normal[3 * v + 1], nz = q.normal[3 * v + 2];
                assertEquals(1, Math.sqrt(nx * nx + ny * ny + nz * nz), 1e-6, what + ": a unit normal on every vertex");
                for (int k = 0; k < 3; k++) {
                    double p = q.pos[3 * v + k];
                    assertTrue(p >= -1e-9 && p <= 1 + 1e-9, what + ": inside the cell");
                }
                for (int k = 0; k < 2; k++) {
                    // as the renderer maps it (a soffit's arc fitted into its cell); a rib's brace may reach past
                    double t = ShapeRender.fit(q, v, k), slack = q.face < 0 ? 0.25 : 1e-9;
                    assertTrue(t >= -slack && t <= 1 + slack, what + ": texture in the cell, " + t + " " + q.surface);
                }
            }
        }
    }

    /** Every listed item of every family (the creative tab's, NEI's) draws a closed, lit mesh. */
    @Test
    void everyItemDraws() {
        int n = 0;
        for (Family f : Family.values())
            for (boolean whole : new boolean[] { true, false }) for (int meta : SkinBlocks.listedMetas(f, whole)) {
                Form form = SkinBlocks.geometry(f, whole, meta);
                List<Quad> qs = ShapeGeo.item(form);
                wellFormed(qs, f + " " + meta);
                for (int face = 0; face < 6; face++)
                    if (form != Form.SOFFIT) assertTrue(faces(qs, face) || !form.wholeBlock(), f + " " + meta);
                n++;
            }
        assertEquals(36, n);
    }

    /**
     * A row of three hull blocks: the middle one's faces toward its two neighbours are culled (ours), the outer faces
     * drawn; a formed multiblock hiding the east one draws it as nothing, and the middle one's face toward it comes
     * back. The collision boxes do not change.
     */
    @Test
    void aHiddenNeighbourOpensTheFaceTowardIt() {
        FakeWorld w = client();
        w.set(0, 64, 0, SkinModule.hull, 0);
        w.set(1, 64, 0, SkinModule.hull, 10);
        w.set(2, 64, 0, SkinModule.hull, 0);
        Family hull = Family.HULL;
        assertEquals(Nbhd.ours(Form.WHOLE, true), ShapeAccess.code(w, 2, 64, 0, hull, ShapeAccess.hidden(w)));
        List<Quad> middle = ShapeAccess.quads(w, 1, 64, 0, ShapeAccess.hidden(w));
        wellFormed(middle, "the light seam");
        assertFalse(faces(middle, 4), "west culled");
        assertFalse(faces(middle, 5), "east culled");
        assertTrue(faces(middle, 2) && faces(middle, 3) && faces(middle, 1), "the open faces drawn");
        for (Quad q : middle) if (q.face >= 2) assertEquals(Overlay.LINE, q.overlay, "the line on its open sides");

        Formed.set(w, OWNER, Collections.singletonMap(Formed.key(2, 64, 0), Formed.HIDE));
        assertEquals(Nbhd.OPEN, ShapeAccess.code(w, 2, 64, 0, hull, ShapeAccess.hidden(w)));
        assertTrue(
            ShapeAccess.quads(w, 2, 64, 0, ShapeAccess.hidden(w))
                .isEmpty(),
            "the hidden one draws nothing");
        List<Quad> opened = ShapeAccess.quads(w, 1, 64, 0, ShapeAccess.hidden(w));
        assertTrue(faces(opened, 5), "the face toward the hidden one comes back");
        assertFalse(faces(opened, 4), "west still culled");
        assertEquals(
            ShapeAccess.boxes(w, 2, 64, 0, ShapeAccess.NOTHING)
                .size(),
            ShapeAccess.boxes(w, 2, 64, 0, ShapeAccess.hidden(w))
                .size(),
            "hiding does not change the boxes (letting through is PASS's, and BlockSkin's)");
        Formed.clearClient();
        assertFalse(faces(ShapeAccess.quads(w, 1, 64, 0, ShapeAccess.hidden(w)), 5), "unformed: culled again");
    }

    /** What a neighbour is to a shape: ours, solid, water or open; the frame's and fittings' whole cubes are solid. */
    @Test
    void neighbours() {
        FakeWorld w = client();
        BlockFrame frame = new BlockFrame();
        BlockFitting fitting = new BlockFitting();
        ShapeAccess.Hidden none = ShapeAccess.NOTHING;
        w.set(0, 0, 0, frame, Parts.FR_BASE);
        w.set(1, 0, 0, frame, Parts.FR_PILLAR);
        w.set(2, 0, 0, fitting, Parts.F_PLINTH);
        w.set(3, 0, 0, fitting, Parts.F_BED);
        w.set(4, 0, 0, water, 0);
        w.set(5, 0, 0, SkinModule.footingForm, 9);
        w.set(6, 0, 0, SkinModule.gardenForm, 2);
        assertEquals(Nbhd.SOLID, ShapeAccess.code(w, 0, 0, 0, Family.HULL, none), "a frame base");
        assertEquals(Nbhd.OPEN, ShapeAccess.code(w, 1, 0, 0, Family.HULL, none), "a pillar is no whole cube");
        assertEquals(Nbhd.SOLID, ShapeAccess.code(w, 2, 0, 0, Family.HULL, none), "a plinth");
        assertEquals(Nbhd.OPEN, ShapeAccess.code(w, 3, 0, 0, Family.HULL, none), "a planting bed");
        assertEquals(Nbhd.WATER, ShapeAccess.code(w, 4, 0, 0, Family.FOOTING, none));
        assertEquals(Nbhd.OPEN, ShapeAccess.code(w, 9, 0, 0, Family.HULL, none), "nothing there");
        // a stray meta of the shape id (the pool lip lives in the whole id) draws as a whole cube
        assertEquals(Nbhd.ours(Form.WHOLE, true), ShapeAccess.code(w, 5, 0, 0, Family.FOOTING, none));
        assertEquals(Nbhd.ours(Form.ROUND, false), ShapeAccess.code(w, 6, 0, 0, Family.HULL, none));
        // the frame's base hidden by a formed multiblock is open
        Formed.set(w, OWNER, Collections.singletonMap(Formed.key(0, 0, 0), Formed.HIDE));
        assertEquals(Nbhd.OPEN, ShapeAccess.code(w, 0, 0, 0, Family.HULL, ShapeAccess.hidden(w)));
    }

    /** A pool lip beside water carries its lip on the top and toward the water, a spout its outlet toward the open. */
    @Test
    void lipAndSpout() {
        FakeWorld w = client();
        w.set(0, 0, 0, SkinModule.footing, 9);
        w.set(1, 0, 0, water, 0);
        List<Quad> lip = ShapeAccess.quads(w, 0, 0, 0, ShapeAccess.NOTHING);
        boolean top = false, east = false;
        for (Quad q : lip) {
            if (q.face == 1) top |= q.overlay == Overlay.LIP;
            if (q.face == 5) east |= q.overlay == Overlay.LIP;
            if (q.face == 4) assertEquals(Overlay.NONE, q.overlay, "no lip toward the air");
        }
        assertTrue(top && east);
        w.set(0, 0, 0, SkinModule.footing, 11);
        for (Quad q : ShapeAccess.quads(w, 0, 0, 0, ShapeAccess.NOTHING))
            if (q.face == 4) assertEquals(Overlay.SPOUT, q.overlay, "the outlet toward the open air");
    }

    /**
     * The blocks' own flags: a whole id is an opaque cube that takes all light; a shape id is not, its see-through
     * shapes let light through and its bodies do not; lit shapes give their light (style FINAL §3.5).
     */
    @Test
    void theBlocksFlags() {
        FakeWorld w = client();
        for (BlockSkin b : SkinModule.all()) {
            assertEquals(b.whole, b.isOpaqueCube(), b.registryName());
            assertEquals(b.whole, b.renderAsNormalBlock(), b.registryName());
        }
        w.set(0, 0, 0, SkinModule.hull, 11);
        assertEquals(10, SkinModule.hull.getLightValue(w, 0, 0, 0), "the keel");
        w.set(0, 0, 0, SkinModule.hull, 10);
        assertEquals(7, SkinModule.hull.getLightValue(w, 0, 0, 0), "the light seam");
        w.set(0, 0, 0, SkinModule.script, 13);
        assertEquals(9, SkinModule.script.getLightValue(w, 0, 0, 0), "the lantern panel");
        w.set(0, 0, 0, SkinModule.footingForm, 5);
        assertEquals(6, SkinModule.footingForm.getLightValue(w, 0, 0, 0), "a step's nose");
        assertEquals(255, SkinModule.footingForm.getLightOpacity(w, 0, 0, 0), "a step shades as a slab");
        w.set(0, 0, 0, SkinModule.hullForm, 6);
        assertEquals(0, SkinModule.hullForm.getLightOpacity(w, 0, 0, 0), "a rib lets light through");
        w.set(0, 0, 0, SkinModule.scriptForm, 7);
        assertEquals(0, SkinModule.scriptForm.getLightOpacity(w, 0, 0, 0), "the ink lattice lets light through");
        w.set(0, 0, 0, SkinModule.hull, 0);
        assertEquals(0, SkinModule.hull.getLightValue(w, 0, 0, 0));
        assertEquals(255, SkinModule.hull.getLightOpacity(w, 0, 0, 0));
        // a half slab's box is its lower half
        w.set(0, 0, 0, SkinModule.hullForm, 4);
        List<double[]> boxes = ShapeAccess.boxes(w, 0, 0, 0, ShapeAccess.NOTHING);
        assertEquals(1, boxes.size());
        assertEquals(0.5, boxes.get(0)[4], 1e-9);
    }
}
