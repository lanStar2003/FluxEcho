package com.fluxecho.nexus;

import net.minecraft.item.ItemStack;

import com.fluxecho.core.EchoText;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;

/**
 * Lets StructureLib (and the NEI multiblock preview built on it) build a FluxEcho multiblock from its core in any
 * facing: the core turned to that facing does the work.
 */
final class InfoContainer<T extends TileMultiblock> implements IMultiblockInfoContainer<T> {

    private final String descriptionKey;

    InfoContainer(String descriptionKey) {
        this.descriptionKey = descriptionKey;
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly, T tile, ExtendedFacing facing) {
        int was = tile.facing;
        tile.facing = facing.getDirection()
            .ordinal();
        try {
            tile.construct(trigger, hintsOnly);
        } finally {
            tile.facing = was;
        }
    }

    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env, T tile,
        ExtendedFacing facing) {
        int was = tile.facing;
        tile.facing = facing.getDirection()
            .ordinal();
        try {
            return tile.survivalConstruct(trigger, elementBudget, env);
        } finally {
            tile.facing = was;
        }
    }

    @Override
    public String[] getDescription(ItemStack trigger) {
        return EchoText.lines(descriptionKey)
            .toArray(new String[0]);
    }
}
