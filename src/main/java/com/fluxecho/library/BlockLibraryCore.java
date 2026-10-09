package com.fluxecho.library;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.fluxecho.nexus.BlockNexus;

/** The Echo Library's core: the middle of the front face of its cube of shelves. */
public class BlockLibraryCore extends BlockNexus {

    public BlockLibraryCore() {
        super("library", "library");
    }

    @Override
    public TileEntity createNewTileEntity(World w, int meta) {
        return new TileLibrary();
    }

    @Override
    protected void dropsMore(TileEntity te, World w, int x, int y, int z) {
        if (!(te instanceof TileLibrary l)) return;
        for (ItemStack s : l.contents()) w.spawnEntityInWorld(new EntityItem(w, x + 0.5, y + 0.5, z + 0.5, s.copy()));
        for (int i = 0; i < l.samples.getSlots(); i++) l.samples.setStackInSlot(i, null);
        for (int i = 0; i < l.desk.getSlots(); i++) l.desk.setStackInSlot(i, null);
    }
}
