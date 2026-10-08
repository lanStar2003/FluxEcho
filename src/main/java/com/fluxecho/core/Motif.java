package com.fluxecho.core;

/**
 * How a machine shows its work: the animation in the middle of its GUI and on its NEI page, the effect above it in
 * the world while it works, and the icon of its hologram. Each echo machine has its own.
 */
public enum Motif {
    /** A scanning beam over a sample: the imprinters. */
    SCAN,
    /** Honeycomb cells filling: the Larva Incubator. */
    COMB,
    /** A double helix turning: the Gene Assembler. */
    HELIX,
    /** Essentia spiralling into a vortex. */
    VORTEX,
    /** Glyphs rising off a page: the Insight Echo. */
    GLYPHS,
    /** A bubbling crucible. */
    CAULDRON,
    /** A runic matrix with orbiting stones: the Infusion Echo. */
    RUNES,
    /** Blood dripping into a chalice. */
    BLOOD,
    /** A crosshair and claw marks: the Prey Echo. */
    PREY,
    /** A sprout growing out of the soil: the Seed Echo. */
    SPROUT,
    /** Mana welling up like a fountain: the Mana Echo Spring (its GUI is its own). */
    FOUNTAIN;
}
