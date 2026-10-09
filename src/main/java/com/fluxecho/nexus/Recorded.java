package com.fluxecho.nexus;

import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureElement;

/**
 * A structure element that notes where StructureLib found it: so a multiblock knows which of its blocks is which part
 * exactly as StructureLib laid it out, whichever way it faces.
 */
final class Recorded implements IStructureElement<TileMultiblock> {

    private final char ch;
    private final IStructureElement<TileMultiblock> inner;

    Recorded(char ch, IStructureElement<TileMultiblock> inner) {
        this.ch = ch;
        this.inner = inner;
    }

    @Override
    public boolean check(TileMultiblock t, World w, int x, int y, int z) {
        boolean ok = inner.check(t, w, x, y, z);
        if (ok) t.record(ch, x, y, z);
        return ok;
    }

    @Override
    public boolean couldBeValid(TileMultiblock t, World w, int x, int y, int z, ItemStack trigger) {
        return inner.couldBeValid(t, w, x, y, z, trigger);
    }

    @Override
    public boolean spawnHint(TileMultiblock t, World w, int x, int y, int z, ItemStack trigger) {
        return inner.spawnHint(t, w, x, y, z, trigger);
    }

    @Override
    public boolean placeBlock(TileMultiblock t, World w, int x, int y, int z, ItemStack trigger) {
        return inner.placeBlock(t, w, x, y, z, trigger);
    }

    @Override
    @SuppressWarnings("deprecation")
    public PlaceResult survivalPlaceBlock(TileMultiblock t, World w, int x, int y, int z, ItemStack trigger,
        IItemSource s, EntityPlayerMP actor, Consumer<IChatComponent> chat) {
        return inner.survivalPlaceBlock(t, w, x, y, z, trigger, s, actor, chat);
    }

    @Override
    public BlocksToPlace getBlocksToPlace(TileMultiblock t, World w, int x, int y, int z, ItemStack trigger,
        AutoPlaceEnvironment env) {
        return inner.getBlocksToPlace(t, w, x, y, z, trigger, env);
    }

    @Override
    public PlaceResult survivalPlaceBlock(TileMultiblock t, World w, int x, int y, int z, ItemStack trigger,
        AutoPlaceEnvironment env) {
        return inner.survivalPlaceBlock(t, w, x, y, z, trigger, env);
    }
}
