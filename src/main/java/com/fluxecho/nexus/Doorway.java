package com.fluxecho.nexus;

import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidBlock;

import com.fluxecho.logic.MustBeAir;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

/**
 * The must-be-air cell ({@code -}) of a checked FluxEcho structure (0.14.2): it holds when the cell is air or holds
 * only what weather and plants leave in an open doorway ({@link MustBeAir}: a snow layer, tall grass, a vine, fire),
 * and fails for anything solid or fluid. StructureLib's own {@code -} ({@link StructureUtility#isAir()}) wants real
 * air,
 * so a snow layer that settled in a doorway while the campus raised a building kept it from ever forming.
 * <p>
 * Only the checked definition ({@link TileMultiblock#define} with {@code record}) has it; hints and placing are
 * StructureLib's own, and the definition the projector builds with keeps StructureLib's element, so a projector still
 * empties the cell. The element notes nothing ({@link Recorded}): a doorway is no part of the structure's drawing.
 */
public final class Doorway implements IStructureElement<TileMultiblock> {

    private final IStructureElement<TileMultiblock> air = StructureUtility.isAir();

    Doorway() {}

    /**
     * Whether the cell may stand in a structure as one of its must-be-air cells: air, or a replaceable block that is no
     * fluid ({@link MustBeAir#open}). The campus builder judges the doorways it cleared by the same rule.
     */
    public static boolean open(World w, int x, int y, int z) {
        if (w.isAirBlock(x, y, z)) return true;
        Block b = w.getBlock(x, y, z);
        boolean liquid = b.getMaterial()
            .isLiquid() || b instanceof IFluidBlock;
        return MustBeAir.open(false, b.isReplaceable(w, x, y, z), liquid);
    }

    @Override
    public boolean check(TileMultiblock t, World w, int x, int y, int z) {
        return open(w, x, y, z);
    }

    @Override
    public boolean couldBeValid(TileMultiblock t, World w, int x, int y, int z, ItemStack trigger) {
        return open(w, x, y, z);
    }

    @Override
    public boolean spawnHint(TileMultiblock t, World w, int x, int y, int z, ItemStack trigger) {
        return air.spawnHint(t, w, x, y, z, trigger);
    }

    @Override
    public boolean placeBlock(TileMultiblock t, World w, int x, int y, int z, ItemStack trigger) {
        return air.placeBlock(t, w, x, y, z, trigger);
    }

    @Override
    @SuppressWarnings("deprecation")
    public PlaceResult survivalPlaceBlock(TileMultiblock t, World w, int x, int y, int z, ItemStack trigger,
        IItemSource s, EntityPlayerMP actor, Consumer<IChatComponent> chat) {
        return air.survivalPlaceBlock(t, w, x, y, z, trigger, s, actor, chat);
    }

    @Override
    public BlocksToPlace getBlocksToPlace(TileMultiblock t, World w, int x, int y, int z, ItemStack trigger,
        AutoPlaceEnvironment env) {
        return air.getBlocksToPlace(t, w, x, y, z, trigger, env);
    }

    @Override
    public PlaceResult survivalPlaceBlock(TileMultiblock t, World w, int x, int y, int z, ItemStack trigger,
        AutoPlaceEnvironment env) {
        return air.survivalPlaceBlock(t, w, x, y, z, trigger, env);
    }
}
