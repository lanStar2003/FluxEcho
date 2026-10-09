package com.fluxecho.nexus;

import net.minecraft.nbt.NBTTagCompound;

/**
 * A module of the Flux Nexus (blueprint 3.7): its own multiblock, docked on a slot of the nexus's ring when the centre
 * of its foundation stands there. The nexus decides which modules it takes ({@link TileNexus#refreshDock}); a module
 * that has not heard from its nexus for a while counts as undocked.
 */
public abstract class TileModule extends TileMultiblock {

    private TileNexus nexus;
    private int slot = -1;
    private long dockedUntil;
    private String dock = "no_nexus";

    // the client's copy
    public int clientSlot = -1;
    public boolean clientDocked;
    /** The docked nexus's centre: x, y, z. */
    public int[] clientNexus;

    /** Lang key part of the module ({@code fluxecho.module.<key>}). */
    public abstract String moduleKey();

    /** The module's colour: its bridge, its hologram. */
    public abstract int moduleColor();

    /** The blueprint cell at the centre of its foundation: across, layer from the top, row from the front. */
    protected abstract int[] centreCell();

    /** Where its foundation's centre is. */
    public int[] centre() {
        int[] c = centreCell();
        return cellPos(c[0], c[1], c[2]);
    }

    void dock(TileNexus n, int slot, long until) {
        boolean changed = nexus != n || this.slot != slot || !"docked".equals(dock);
        nexus = n;
        this.slot = slot;
        dockedUntil = until;
        dock = "docked";
        if (changed) sync();
    }

    void reject(TileNexus n, int slot, String why, long until) {
        boolean changed = nexus != null || !why.equals(dock);
        nexus = null;
        this.slot = slot;
        dockedUntil = until;
        dock = why;
        if (changed) sync();
    }

    /** The nexus it is docked on, or null. */
    public TileNexus nexus() {
        if (nexus == null || nexus.isInvalid() || !nexus.formed() || worldObj == null) return null;
        return worldObj.getTotalWorldTime() <= dockedUntil ? nexus : null;
    }

    /** Why it is (not) docked, a lang key part under {@code fluxecho.dock.}. */
    public String dockStatus() {
        if (!formed) return "unformed";
        if (nexus() != null) return "docked";
        if (worldObj != null && worldObj.getTotalWorldTime() > dockedUntil) return "no_nexus";
        return dock;
    }

    public int slot() {
        return slot;
    }

    @Override
    protected void serverTick() {
        if (worldObj.getTotalWorldTime() > dockedUntil && nexus != null) {
            nexus = null;
            dock = "no_nexus";
            sync();
        }
    }

    @Override
    public void validate() {
        super.validate();
        if (realWorld() && !worldObj.isRemote) NexusRegistry.add(this);
    }

    @Override
    protected void gone() {
        super.gone();
        NexusRegistry.remove(this);
    }

    @Override
    protected void writeSync(NBTTagCompound t) {
        TileNexus n = nexus();
        t.setByte("Slot", (byte) slot);
        t.setBoolean("Docked", n != null);
        if (n != null) {
            int[] c = n.centre();
            t.setIntArray("Nexus", c);
        }
    }

    @Override
    protected void readSync(NBTTagCompound t) {
        clientSlot = t.getByte("Slot");
        clientDocked = t.getBoolean("Docked");
        clientNexus = t.hasKey("Nexus") ? t.getIntArray("Nexus") : null;
    }
}
