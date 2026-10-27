package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.EnumPlantType;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fluxecho.logic.Parts;

/**
 * The planting bed, fitting 14 (0.17.0, style FINAL §3.7, refactor FINAL S-P4): a trough 12/16 high with a warm lip
 * (light 4) that bears what grows on grass and what grows on farmland, plains plants (flowers, saplings, and so
 * Botania's flowers, which are plains plants) and crops, and nothing of the desert, the nether, caves or water; a
 * crop grows on it as on watered farmland. Code 46 both ways, its own item name. The hull-number tile, deck 13 at code
 * 29, comes with it. Only the game can show a Botania flower standing on the bed ({@code probe bed}); this is the rule
 * it asks.
 */
class PlantBedTest {

    private static BlockDeck oldDeck;
    private static BlockFitting oldFitting;
    private static BlockFitting fitting;
    private static BlockDeck deck;

    @BeforeAll
    static void setUp() {
        oldDeck = CampusModule.deck;
        oldFitting = CampusModule.fitting;
        CampusModule.deck = deck = new BlockDeck();
        CampusModule.fitting = fitting = new BlockFitting();
    }

    @AfterAll
    static void tearDown() {
        CampusModule.deck = oldDeck;
        CampusModule.fitting = oldFitting;
    }

    /** A plant of a kind, as a sapling or a flower answers the soil it is set on. */
    private static IPlantable plant(EnumPlantType type) {
        return new IPlantable() {

            @Override
            public EnumPlantType getPlantType(IBlockAccess world, int x, int y, int z) {
                return type;
            }

            @Override
            public Block getPlant(IBlockAccess world, int x, int y, int z) {
                return null;
            }

            @Override
            public int getPlantMetadata(IBlockAccess world, int x, int y, int z) {
                return 0;
            }
        };
    }

    @Test
    void theBedBearsPlainsPlantsAndCrops() {
        FakeWorld w = FakeWorld.create();
        w.set(0, 64, 0, fitting, Parts.F_BED);
        w.set(2, 64, 0, fitting, Parts.F_PLINTH);
        for (EnumPlantType t : EnumPlantType.values()) {
            boolean grows = t == EnumPlantType.Plains || t == EnumPlantType.Crop;
            assertEquals(grows, fitting.canSustainPlant(w, 0, 64, 0, ForgeDirection.UP, plant(t)), t + " on the bed");
            if (grows) assertFalse(
                fitting.canSustainPlant(w, 2, 64, 0, ForgeDirection.UP, plant(t)),
                t + " on a plinth: as vanilla says, not");
        }
        for (ForgeDirection d : new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.DOWN, ForgeDirection.EAST })
            assertFalse(fitting.canSustainPlant(w, 0, 64, 0, d, plant(EnumPlantType.Plains)), "only on top: " + d);
        assertTrue(fitting.isFertile(w, 0, 64, 0), "a crop grows as on watered farmland");
        assertFalse(fitting.isFertile(w, 2, 64, 0));
    }

    @Test
    void theBedIsATroughWithAWarmLip() {
        assertTrue(BlockFitting.known(Parts.F_BED));
        assertEquals(Parts.F_BED, BlockFitting.type(Parts.F_BED));
        assertEquals(4, BlockFitting.light(Parts.F_BED));
        assertFalse(BlockFitting.full(Parts.F_BED));
        assertArrayEquals(new float[] { 0, 0, 0, 1, 12 / 16f, 1 }, BlockFitting.box(Parts.F_BED));
        FakeWorld w = FakeWorld.create();
        w.set(0, 64, 0, fitting, Parts.F_BED);
        assertEquals(4, fitting.getLightValue(w, 0, 64, 0));
        assertTrue(fitting.isSideSolid(w, 0, 64, 0, ForgeDirection.DOWN));
        assertFalse(fitting.isSideSolid(w, 0, 64, 0, ForgeDirection.UP), "nothing stands on its soil but plants");
        // the unregistered metas between the 0.16.0 run and the bed still behave like a plinth
        assertEquals(Parts.F_PLINTH, BlockFitting.type(12));
    }

    @Test
    void itsCodeAndNameGoBothWays() {
        assertEquals(46, Parts.fitting(Parts.F_BED));
        assertSame(fitting, PartBlocks.block(46));
        assertEquals(Parts.F_BED, PartBlocks.meta(46));
        assertEquals(46, PartBlocks.code(fitting, Parts.F_BED));
        assertEquals(-1, PartBlocks.code(fitting, 12), "fitting 12 is nobody's yet");
        assertEquals(-1, PartBlocks.code(fitting, 15));
        Item item = new Item();
        List<ItemStack> listed = new ArrayList<>();
        fitting.getSubBlocks(item, null, listed);
        assertEquals(Parts.FITTING_TYPES + 1, listed.size());
        assertEquals(
            Parts.F_BED,
            listed.get(listed.size() - 1)
                .getItemDamage());
        assertEquals(
            "tile.fluxecho.fitting.bed",
            new ItemBlockFitting(fitting).getUnlocalizedName(new ItemStack(item, 1, Parts.F_BED)));
    }

    @Test
    void theHullNumberTile() {
        assertEquals(29, Parts.deck(Parts.D_HULL_NUMBER));
        assertSame(deck, PartBlocks.block(29));
        assertEquals(Parts.D_HULL_NUMBER, PartBlocks.meta(29));
        assertEquals(29, PartBlocks.code(deck, Parts.D_HULL_NUMBER));
        assertEquals(-1, PartBlocks.code(deck, 14), "deck 14 waits for 0.22.0");
        assertEquals(0, BlockDeck.light(Parts.D_HULL_NUMBER), "its light is the box table's to draw");
        Item item = new Item();
        List<ItemStack> listed = new ArrayList<>();
        deck.getSubBlocks(item, null, listed);
        assertEquals(Parts.DECK_TYPES + 1, listed.size());
        assertEquals(
            "tile.fluxecho.deck.hull_number",
            new ItemBlockDeck(deck).getUnlocalizedName(new ItemStack(item, 1, Parts.D_HULL_NUMBER)));
    }

    /** Both items are named and explained in both languages. */
    @Test
    void bothLanguagesNameThem() throws IOException {
        for (String lang : new String[] { "en_US", "zh_CN" }) {
            String text = new String(
                Files.readAllBytes(Paths.get("src/main/resources/assets/fluxecho/lang/" + lang + ".lang")),
                StandardCharsets.UTF_8);
            for (String key : new String[] { "tile.fluxecho.fitting.bed.name", "fluxecho.fitting.bed.tip",
                "tile.fluxecho.deck.hull_number.name", "fluxecho.deck.hull_number.tip" })
                assertTrue(text.contains("\n" + key + "="), lang + " lacks " + key);
        }
    }
}
