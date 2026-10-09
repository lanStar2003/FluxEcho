package com.fluxecho.nexus;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.fluxecho.frame.BlockFrame;

/**
 * The console stands of the formed multiblocks: the one under the nexus's controller, the library's reading desk.
 * Right-clicking one opens its structure's GUI, as its controller does, so a player inside a hall need not walk back
 * to the controller.
 */
public final class Consoles {

    private Consoles() {}

    /** {@link BlockFrame.Use}: true when the stand belongs to a formed structure (on both sides alike). */
    public static boolean use(World w, int x, int y, int z, EntityPlayer p, int side, int meta) {
        if (meta != BlockFrame.CONSOLE) return false;
        for (TileMultiblock m : NexusRegistry.loaded(w)) {
            if (!m.formed() || !m.consoleAt(x, y, z)) continue;
            if (!w.isRemote) BlockNexus.open(m, p);
            return true;
        }
        return false;
    }
}
