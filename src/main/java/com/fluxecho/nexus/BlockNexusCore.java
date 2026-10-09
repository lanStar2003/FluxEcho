package com.fluxecho.nexus;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** The Flux Nexus's core: the controller in the front rim of its base. */
public class BlockNexusCore extends BlockNexus {

    public BlockNexusCore() {
        super("nexus", "nexus");
    }

    @Override
    public TileEntity createNewTileEntity(World w, int meta) {
        return new TileNexus();
    }
}
