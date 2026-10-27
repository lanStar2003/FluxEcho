package com.fluxecho.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDeadBush;
import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.BlockSnowBlock;
import net.minecraft.block.BlockStaticLiquid;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.BlockVine;
import net.minecraft.block.material.Material;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fluxecho.campus.FakeWorld;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.WrongCell;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

import cpw.mods.fml.common.Loader;

/**
 * The checked structure's must-be-air cell (0.14.2): the user's Resonance Gate and Dual-foil Vault never formed
 * because it snowed while the campus raised them, and a snow layer (meta 0) settled in two of the gate's doorways and
 * four of the vault's after their AIR steps had emptied them; StructureLib's own {@code -} wants real air. Every
 * FluxEcho multiblock checks itself with {@link TileMultiblock#define}, so a room of three cells stands for all of
 * them:
 * a doorway over the controller, a wall block under it. The doorway takes air and what weather and plants leave there
 * (a snow layer up to meta 6, tall grass, a vine, a dead bush, fire), and refuses anything solid or fluid (a full snow
 * layer, a snow block, a flower, water, lava, a wall block); the structure that is built and previewed keeps
 * StructureLib's own element. When the room does not form, {@link TileMultiblock#firstWrong} names the cell that keeps
 * it from forming, what is there and what should be.
 * <p>
 * StructureLib's check needs its mod class loaded, which outside the game needs a bare FML loader standing in while it
 * loads (as {@code StructureSkyTest} does it).
 */
@Tag("slow")
class DoorwayRoomTest {

    /** A doorway over the controller, a wall block under it (layers from the top). */
    private static final Blueprint ROOM = new Blueprint(new String[][] { { "-" }, { "~" }, { "A" } });
    private static final int X = 10, Y = 64, Z = -20, WALL_META = 3;

    private static Block wall, air, stone, snow, snowBlock, grass, vine, deadBush, fire, flower, water, lava;
    private static IStructureDefinition<TileMultiblock> checked, built;

    /** A multiblock of the room: the definitions are the test's. */
    private static final class Room extends TileMultiblock {

        @Override
        protected Blueprint blueprint() {
            return ROOM;
        }

        @Override
        protected IStructureDefinition<TileMultiblock> definition() {
            return checked;
        }

        @Override
        protected IStructureDefinition<TileMultiblock> buildDefinition() {
            return built;
        }

        @Override
        protected Block coreBlock() {
            return null;
        }

        @Override
        protected int flags(char ch) {
            return 0;
        }

        @Override
        protected String descriptionKey() {
            return "room";
        }
    }

    @BeforeAll
    static void setUp() throws ReflectiveOperationException {
        structureLib();
        wall = new Block(Material.iron) {

            // as our frame blocks: the meta is the item's damage, which StructureLib's ofBlock compares
            @Override
            public int damageDropped(int meta) {
                return meta;
            }
        }.setBlockName("roomWall");
        air = new Block(Material.air) {};
        stone = new Block(Material.rock) {}.setBlockName("roomStone");
        snow = new BlockSnow() {}.setBlockName("snow");
        snowBlock = new BlockSnowBlock() {};
        grass = new BlockTallGrass() {};
        vine = new BlockVine();
        deadBush = new BlockDeadBush() {};
        fire = new BlockFire() {};
        flower = new Block(Material.plants) {};
        water = new BlockStaticLiquid(Material.water) {};
        lava = new BlockStaticLiquid(Material.lava) {};
        checked = TileMultiblock
            .define(true, new String[] { TileMultiblock.MAIN }, new Blueprint[] { ROOM }, 'A', wall, WALL_META);
        built = TileMultiblock
            .define(false, new String[] { TileMultiblock.MAIN }, new Blueprint[] { ROOM }, 'A', wall, WALL_META);
    }

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
            Class.forName("com.gtnewhorizon.structurelib.StructureLib", true, DoorwayRoomTest.class.getClassLoader());
        } finally {
            if (none) instance.set(null, null);
        }
    }

    /** The room in a world of its own: the given block in the doorway, the wall under the controller (or not). */
    private static Room room(Block door, int meta, boolean wallThere) {
        FakeWorld w = FakeWorld.create();
        if (door != null) w.set(X, Y + 1, Z, door, meta);
        if (wallThere) w.set(X, Y - 1, Z, wall, WALL_META);
        Room r = w.put(new Room(), X, Y, Z);
        r.place(null, null, ForgeDirection.NORTH.ordinal());
        return r;
    }

    /**
     * Whether the structure checks out with the given block in the doorway, as {@code TileMultiblock.check} runs it.
     */
    private static boolean forms(IStructureDefinition<TileMultiblock> def, Block door, int meta) {
        Room r = room(door, meta, true);
        return def.check(
            r,
            TileMultiblock.MAIN,
            r.getWorldObj(),
            ExtendedFacing.of(ForgeDirection.NORTH),
            X,
            Y,
            Z,
            ROOM.ctrlA,
            ROOM.ctrlB,
            ROOM.ctrlC,
            false);
    }

    @Test
    void aDoorwayTakesWhatTheWeatherLeaves() {
        assertTrue(forms(checked, null, 0), "an empty doorway");
        assertTrue(forms(checked, air, 0), "air");
        for (int meta = 0; meta <= 6; meta++) assertTrue(forms(checked, snow, meta), "a snow layer of meta " + meta);
        assertTrue(forms(checked, grass, 1), "tall grass");
        assertTrue(forms(checked, vine, 0), "a vine");
        assertTrue(forms(checked, deadBush, 0), "a dead bush");
        assertTrue(forms(checked, fire, 0), "fire");
    }

    @Test
    void aDoorwayRefusesAnythingSolidOrFluid() {
        assertFalse(forms(checked, snow, 7), "a full snow layer is no longer replaceable");
        assertFalse(forms(checked, snowBlock, 0), "a snow block");
        assertFalse(forms(checked, flower, 0), "a flower");
        assertFalse(forms(checked, water, 0), "water");
        assertFalse(forms(checked, lava, 0), "lava");
        assertFalse(forms(checked, stone, 0), "a block a player set down");
        assertFalse(forms(checked, wall, WALL_META), "a part of ours where the doorway is");
        // and the wall under the controller is still checked
        Room r = room(snow, 0, false);
        assertFalse(
            checked.check(
                r,
                TileMultiblock.MAIN,
                r.getWorldObj(),
                ExtendedFacing.of(ForgeDirection.NORTH),
                X,
                Y,
                Z,
                ROOM.ctrlA,
                ROOM.ctrlB,
                ROOM.ctrlC,
                false),
            "the missing wall block");
    }

    /**
     * Only the checked structure takes the weather: the structure that is built and previewed (the projector, NEI)
     * keeps StructureLib's own must-be-air element, which wants real air, so a projector still empties the cell.
     */
    @Test
    void theBuiltStructureKeepsStructureLibsOwnAir() {
        assertInstanceOf(
            Doorway.class,
            ((StructureDefinition<TileMultiblock>) checked).getElements()
                .get(TileMultiblock.MUST_BE_AIR));
        assertSame(
            StructureUtility.isAir(),
            ((StructureDefinition<TileMultiblock>) built).getElements()
                .get(TileMultiblock.MUST_BE_AIR));
        assertTrue(forms(built, air, 0));
        assertFalse(forms(built, snow, 0), "StructureLib's own wants real air");
    }

    /**
     * A room that does not form names the first cell that keeps it from forming: a block in the doorway where air
     * should be, a wall block missing where the wall should be; the cells walked from the top layer down. A room whose
     * doorway holds only snow and whose wall stands has none.
     */
    @Test
    void theFirstWrongCellIsNamed() {
        assertEquals(
            new WrongCell(X, Y + 1, Z, "tile.roomStone.name", WrongCell.AIR),
            room(stone, 0, true).firstWrong(),
            "a stone in the doorway, which wants air");
        assertEquals(
            new WrongCell(X, Y - 1, Z, WrongCell.AIR, "tile.roomWall.name"),
            room(snow, 0, false).firstWrong(),
            "the wall block is missing");
        assertEquals(
            new WrongCell(X, Y + 1, Z, "tile.snow.name", WrongCell.AIR),
            room(snow, 7, false).firstWrong(),
            "the doorway comes first");
        assertNull(room(snow, 0, true).firstWrong(), "snow in the doorway keeps nothing from forming");
        assertNull(room(null, 0, true).firstWrong());
        WrongCell door = new WrongCell(X, Y + 1, Z, "tile.roomStone.name", WrongCell.AIR);
        assertEquals(door, room(stone, 0, true).firstWrong(TileMultiblock.MAIN));
        assertEquals(door, room(stone, 0, true).firstWrong("no such shape"), "an unknown name is the current shape");
    }
}
