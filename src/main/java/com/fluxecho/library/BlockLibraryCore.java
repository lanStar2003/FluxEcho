package com.fluxecho.library;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.fluxecho.nexus.BlockNexus;

/**
 * The Echo Library's core: the controller in the front wall, between the doors. Taking it down puts the library's
 * books into the team's vault ({@link LibraryVault}), where the next library of the team takes them in again; a library
 * without a team drops them.
 */
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
        if (w.isRemote || !(te instanceof TileLibrary l)) return;
        l.takeDown(w, x, y, z);
    }
}
