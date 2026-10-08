package com.fluxecho.core;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.fluxecho.Config;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * FluxEcho's GT machines, each on its fixed id from {@link Config#firstMachineId}. The modules create their machines
 * and hand them over here; this class never touches an optional mod.
 */
public final class Machines {

    private static final Map<MachineId, ItemStack> STACKS = new EnumMap<>(MachineId.class);

    private Machines() {}

    /**
     * The id of a machine, refusing one another mod already uses: a silent overwrite would turn that mod's placed
     * machines into ours.
     */
    public static int claim(MachineId m) {
        int id = m.id(Config.firstMachineId);
        IMetaTileEntity taken = GregTechAPI.METATILEENTITIES[id];
        if (taken != null) throw new IllegalStateException(
            "FluxEcho: GT machine id " + id
                + " is already used by "
                + taken.getClass()
                    .getName()
                + ". Set general.firstMachineId in config/fluxecho.cfg to the start of "
                + MachineId.RESERVED
                + " free ids.");
        return id;
    }

    public static void put(MachineId m, IMetaTileEntity mte) {
        STACKS.put(m, mte.getStackForm(1));
    }

    public static ItemStack get(MachineId m) {
        ItemStack s = STACKS.get(m);
        return s == null ? null : s.copy();
    }
}
