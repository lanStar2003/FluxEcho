package com.fluxecho.core;

/**
 * FluxEcho's GT machines and their ids: fixed offsets from {@code general.firstMachineId}, so an id never moves when
 * a module is missing or switched off. {@link #RESERVED} ids are kept for machines still to come. Offsets of removed
 * machines are never handed out again: 3 (Vis Charger, replaced by the Flux Vis Pedestal) and 12 (Enchant Echo).
 */
public enum MachineId {

    BEE_IMPRINTER(0, "bee_imprinter", 1, "Bee Imprinter", 0xFFC233, Motif.SCAN, true),
    BEE_INCUBATOR(1, "larva_incubator", 1, "Larva Incubator", 0xFF9F3D, Motif.COMB, true),
    ESSENTIA_ECHO(2, "essentia_echo", 2, "Essentia Echo", 0xB37CFF, Motif.VORTEX, false),
    INSIGHT_ECHO(4, "insight_echo", 1, "Insight Echo", 0x8FB8FF, Motif.GLYPHS, true),
    CRUCIBLE_ECHO(5, "crucible_echo", 2, "Crucible Echo", 0x5FD3A8, Motif.CAULDRON, false),
    BLOOD_ECHO(6, "blood_echo", 2, "Blood Echo", 0xE0303A, Motif.BLOOD, true),
    MOB_ECHO(7, "mob_echo", 1, "Prey Echo", 0xFF6A3D, Motif.PREY, true),
    INFUSION_ECHO(8, "infusion_echo", 2, "Infusion Echo", 0xD38CFF, Motif.RUNES, true),
    SEED_IMPRINTER(9, "seed_imprinter", 1, "Seed Imprinter", 0x9BE36A, Motif.SCAN, true),
    SEED_ECHO(10, "seed_echo", 1, "Seed Echo", 0x5ED65A, Motif.SPROUT, true),
    MANA_ECHO(11, "mana_echo", 1, "Mana Echo Spring", 0x46C8FF, Motif.FOUNTAIN, false),
    GENE_ASSEMBLER(13, "gene_assembler", 1, "Gene Assembler", 0x7DFFB0, Motif.HELIX, false);

    /** Ids from the first one that belong to FluxEcho: 24530–24569 by default. */
    public static final int RESERVED = 40;

    public final int offset;
    /** Name part of the GT machine ({@code "fluxecho." + key}), its textures, lang keys and recipe map. */
    public final String key;
    /** GT voltage tier: 1 LV, 2 MV (the Mana Echo Spring takes its tier from the circuit in its core). */
    public final int tier;
    /** GT's fallback name, for its own lang file; the real names come from FluxEcho's lang files. */
    public final String english;
    /** The machine's colour: its glow strips, GUI accents, hologram and effects. */
    public final int accent;
    /** How it shows its work (GUI, NEI, world). */
    public final Motif motif;
    /** Whether it keeps a sample in GT's special slot. */
    public final boolean sample;

    MachineId(int offset, String key, int tier, String english, int accent, Motif motif, boolean sample) {
        this.offset = offset;
        this.key = key;
        this.tier = tier;
        this.english = english;
        this.accent = accent;
        this.motif = motif;
        this.sample = sample;
    }

    public int id(int first) {
        return first + offset;
    }
}
