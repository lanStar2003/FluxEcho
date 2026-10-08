package com.fluxecho.core;

/**
 * FluxEcho's GT machines and their ids: fixed offsets from {@code general.firstMachineId}, so an id never moves when
 * a module is missing or switched off. {@link #RESERVED} ids are kept for machines still to come.
 */
public enum MachineId {

    BEE_IMPRINTER(0, "bee_imprinter", 1, "Bee Imprinter"),
    BEE_INCUBATOR(1, "larva_incubator", 1, "Larva Incubator"),
    ESSENTIA_ECHO(2, "essentia_echo", 2, "Essentia Echo"),
    VIS_CHARGER(3, "vis_charger", 2, "Vis Charger"),
    INSIGHT_ECHO(4, "insight_echo", 1, "Insight Echo"),
    CRUCIBLE_ECHO(5, "crucible_echo", 2, "Crucible Echo"),
    BLOOD_ECHO(6, "blood_echo", 2, "Blood Echo"),
    MOB_ECHO(7, "mob_echo", 1, "Prey Echo"),
    INFUSION_ECHO(8, "infusion_echo", 2, "Infusion Echo"),
    SEED_IMPRINTER(9, "seed_imprinter", 1, "Seed Imprinter"),
    SEED_ECHO(10, "seed_echo", 1, "Seed Echo"),
    MANA_ECHO(11, "mana_echo", 2, "Mana Echo"),
    ENCHANT_ECHO(12, "enchant_echo", 2, "Enchant Echo"),
    GENE_ASSEMBLER(13, "gene_assembler", 1, "Gene Assembler");

    /** Ids from the first one that belong to FluxEcho: 24530–24569 by default. */
    public static final int RESERVED = 40;

    public final int offset;
    /** Name part of the GT machine ({@code "fluxecho." + key}), its textures, lang keys and recipe map. */
    public final String key;
    /** GT voltage tier: 1 LV, 2 MV. */
    public final int tier;
    /** GT's fallback name, for its own lang file; the real names come from FluxEcho's lang files. */
    public final String english;

    MachineId(int offset, String key, int tier, String english) {
        this.offset = offset;
        this.key = key;
        this.tier = tier;
        this.english = english;
    }

    public int id(int first) {
        return first + offset;
    }
}
