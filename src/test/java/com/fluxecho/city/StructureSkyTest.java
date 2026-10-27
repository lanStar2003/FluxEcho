package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fluxecho.campus.FakeWorld;
import com.fluxecho.logic.Blueprint;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

import cpw.mods.fml.common.Loader;

/**
 * Pins the StructureLib behaviour that lets a building clip at the world's top (0.14.1, CITY.md §1.3 Vertical): the
 * structure check a multiblock runs ({@code check(..., false)}, as {@code TileMultiblock.check}) skips a cell the world
 * says does not exist, and a world says so for every cell over y 255 or under y 0. So a module whose highest cells lie
 * over the world's top (the Arcane Court's finial over Y0 223, the zenith's pod over Y0 218) forms with those cells
 * left out of its plan; should a StructureLib update start looking at them, this fails before any player sees a
 * building that cannot form.
 * <p>
 * StructureLib's check asks its mod class whether debugging is on, and that class registers its network channels as
 * it loads, which asks FML's {@code Loader} for the mod being loaded. Outside the game there is no loader, so for the
 * moment the class loads a bare one stands in ({@link #structureLib}): its active mod is none, as at any time no mod
 * is being loaded; it is taken away again once the class has loaded.
 */
@Tag("slow")
class StructureSkyTest {

    /** Loads StructureLib's mod class once, with a bare FML loader standing in while it does. */
    private static void structureLib() throws ReflectiveOperationException {
        Field instance = Loader.class.getDeclaredField("instance");
        instance.setAccessible(true);
        boolean none = instance.get(null) == null;
        if (none) {
            Class<?> u = Class.forName("sun.misc.Unsafe");
            Field f = u.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Method alloc = u.getMethod("allocateInstance", Class.class);
            instance.set(null, alloc.invoke(f.get(null), Loader.class));
        }
        try {
            Class.forName("com.gtnewhorizon.structurelib.StructureLib", true, StructureSkyTest.class.getClassLoader());
        } finally {
            if (none) instance.set(null, null);
        }
    }

    /** A part that notes every cell the check looks at, and holds only inside the world. */
    private static final class Noting implements IStructureElement<Object> {

        final List<int[]> seen = new ArrayList<>();

        @Override
        public boolean check(Object t, World w, int x, int y, int z) {
            seen.add(new int[] { x, y, z });
            return y >= 0 && y <= 255;
        }

        @Override
        public boolean spawnHint(Object t, World w, int x, int y, int z, ItemStack trigger) {
            return false;
        }

        @Override
        public boolean placeBlock(Object t, World w, int x, int y, int z, ItemStack trigger) {
            return false;
        }
    }

    /** A column of three cells: {@code A} over the controller, {@code B} under it (layers from the top). */
    private static final Blueprint COLUMN = new Blueprint(new String[][] { { "A" }, { "~" }, { "B" } });

    private static boolean check(Noting a, Noting b, int y) {
        IStructureDefinition<Object> def = StructureDefinition.builder()
            .addShape("main", StructureUtility.transpose(COLUMN.shape()))
            .addElement('A', a)
            .addElement('B', b)
            .build();
        return def.check(
            new Object(),
            "main",
            FakeWorld.create(),
            ExtendedFacing.of(ForgeDirection.NORTH),
            10,
            y,
            -20,
            COLUMN.ctrlA,
            COLUMN.ctrlB,
            COLUMN.ctrlC,
            false);
    }

    @Test
    void theCheckSkipsTheCellsOverTheWorldsTop() throws ReflectiveOperationException {
        structureLib();
        Noting a = new Noting(), b = new Noting();
        assertTrue(check(a, b, 255), "the structure holds with its top cell over the world");
        assertEquals(0, a.seen.size(), "the cell at y 256 is never looked at");
        assertEquals(1, b.seen.size());
        assertEquals(254, b.seen.get(0)[1]);
        // inside the world both are looked at
        Noting c = new Noting(), d = new Noting();
        assertTrue(check(c, d, 200));
        assertEquals(201, c.seen.get(0)[1]);
        assertEquals(199, d.seen.get(0)[1]);
        // and under the world's floor the same
        Noting e = new Noting(), f = new Noting();
        assertTrue(check(e, f, 0));
        assertEquals(1, e.seen.size());
        assertEquals(0, f.seen.size(), "the cell at y -1 is never looked at");
    }
}
