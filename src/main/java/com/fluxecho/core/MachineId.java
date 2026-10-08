package com.fluxecho.core;

/**
 * The GT machine ids of FluxEcho: fixed offsets from {@code general.firstMachineId}, so an id never moves when a
 * module is missing or switched off. {@link #RESERVED} ids are kept for machines still to come.
 */
public enum MachineId {

    BEE_IMPRINTER(0),
    BEE_INCUBATOR(1),
    ESSENTIA_ECHO(2),
    VIS_CHARGER(3),
    INSIGHT_ECHO(4),
    CRUCIBLE_ECHO(5),
    BLOOD_ECHO(6);

    /** Ids from the first one that belong to FluxEcho: 24530–24569 by default. */
    public static final int RESERVED = 40;

    public final int offset;

    MachineId(int offset) {
        this.offset = offset;
    }

    public int id(int first) {
        return first + offset;
    }
}
