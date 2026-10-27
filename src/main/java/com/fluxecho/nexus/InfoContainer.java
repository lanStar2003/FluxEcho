package com.fluxecho.nexus;

import net.minecraft.item.ItemStack;

import com.fluxecho.campus.SkinFeed;
import com.fluxecho.core.EchoText;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;

/**
 * Lets StructureLib (and the NEI multiblock preview built on it) build a FluxEcho multiblock from its core in any
 * facing: the core turned to that facing does the work. A building built in a preview gets its entry in the preview's
 * box table (0.17.0), so the preview draws it as it stands by day.
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
            // a preview's building gets its box table entry (0.17.0, style §3.9.7)
            if (!tile.realWorld()) SkinFeed.preview(tile);
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
            int n = tile.survivalConstruct(trigger, elementBudget, env);
            if (!tile.realWorld()) SkinFeed.preview(tile);
            return n;
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
