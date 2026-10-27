package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fluxecho.city.TileEcoDome;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.skin.LampRule;
import com.fluxecho.logic.skin.SkinEntry;
import com.fluxecho.logic.skin.SkinState;
import com.fluxecho.logic.skin.SkinTable;
import com.fluxecho.nexus.TileNexus;

/**
 * The NEI preview's box table (style §3.9.7): when our info container builds a building in a preview world, the
 * preview gets one entry, formed, powered, by day, whose box is the shape's box round the controller, held by the
 * preview world alone (weakly, so a closed preview drops it); the resolver gives that entry for every cell in the box
 * and nothing outside. So a preview shows a building as it stands by day: its lamp windows unlit.
 */
@Tag("slow")
class PreviewSkinTest {

    @Test
    void aPreviewGetsOneEntryOverTheShapesBox() throws ReflectiveOperationException {
        FakeWorld preview = world();
        TileEcoDome dome = new TileEcoDome();
        NBTTagCompound t = new NBTTagCompound();
        t.setByte("Facing", (byte) 3);
        dome.readFromNBT(t);
        preview.put(dome, 0, 64, 0);

        SkinFeed.preview(dome);
        SkinTable table = SkinTable.preview(preview);
        assertEquals(1, table.size(), "one entry");
        SkinEntry e = table.entries()
            .get(0);
        assertArrayEquals(dome.bounds(), e.box, "the shape's box round its controller");
        assertEquals("eco_dome", e.key());
        assertEquals("main", e.shape);
        assertEquals(3, e.facing);
        assertArrayEquals(new int[] { 0, 64, 0 }, e.ctrl);
        assertEquals(SkinState.FORMED, e.state);
        assertTrue(e.powered);

        // every cell of the box resolves to it, nothing outside
        int[] b = e.box;
        for (int x = b[0] - 1; x <= b[3] + 1; x += 3)
            for (int y = b[1] - 1; y <= b[4] + 1; y += 3) for (int z = b[2] - 1; z <= b[5] + 1; z += 3) {
                boolean in = x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5];
                SkinEntry r = SkinTable.resolve(preview, x, y, z);
                if (in) assertSame(e, r, x + "," + y + "," + z);
                else assertNull(r, x + "," + y + "," + z);
            }

        // built again (another facing, the slider moved): still one entry, the new one
        dome.readFromNBT(tag(5));
        preview.put(dome, 0, 64, 0);
        SkinFeed.preview(dome);
        assertEquals(
            1,
            SkinTable.preview(preview)
                .size());
        assertEquals(
            5,
            SkinTable.preview(preview)
                .entries()
                .get(0).facing);

        // by day: the preview's lamp windows are dark, its other glowing parts as 0.16 drew them
        SkinEntry now = SkinTable.resolve(preview, 0, 64, 0);
        assertEquals(LampRule.DARK, LampRule.glow(now, Parts.deck(Parts.D_PANEL_LIT), SkinTable.PREVIEW_TIME));
        assertEquals(LampRule.UNTOUCHED, LampRule.glow(now, Parts.deck(Parts.D_LIT), SkinTable.PREVIEW_TIME));

        // another world has none
        assertNull(SkinTable.preview(world()));
        assertNull(SkinTable.resolve(world(), 0, 64, 0));
    }

    @Test
    void theNexusPreviewIsTheNexus() throws ReflectiveOperationException {
        FakeWorld preview = world();
        TileNexus n = new TileNexus();
        n.readFromNBT(tag(2));
        preview.put(n, 10, 70, 10);
        SkinFeed.preview(n);
        SkinEntry e = SkinTable.preview(preview)
            .entries()
            .get(0);
        assertEquals("nexus", e.key());
        assertArrayEquals(n.bounds(), e.box);
    }

    @Test
    void theRegistryHoldsAPreviewWorldWeakly() throws Exception {
        FakeWorld preview = world();
        TileEcoDome dome = new TileEcoDome();
        dome.readFromNBT(tag(2));
        preview.put(dome, 0, 64, 0);
        SkinFeed.preview(dome);
        assertEquals(
            1,
            SkinTable.preview(preview)
                .size());
        // the preview closes: nothing but the registry knows its world, which must not keep it
        WeakReference<FakeWorld> ref = new WeakReference<>(preview);
        preview = null;
        dome = null;
        for (int i = 0; i < 100 && ref.get() != null; i++) {
            System.gc();
            Thread.sleep(20);
        }
        assertNull(ref.get(), "a closed preview's world is not kept");
    }

    private static NBTTagCompound tag(int facing) {
        NBTTagCompound t = new NBTTagCompound();
        t.setByte("Facing", (byte) facing);
        return t;
    }

    private static FakeWorld world() throws ReflectiveOperationException {
        FakeWorld w = FakeWorld.create();
        Field provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(w, new WorldProviderSurface());
        return w;
    }
}
