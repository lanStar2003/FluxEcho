package com.fluxecho.logic.skin;

/**
 * A look a building's shape is drawn with over its blocks (style §3.9.9): whether its new materials develop from their
 * blueprint colours when it first forms, and whether they carry the building's variant patterns. A shape name maps to
 * one profile in {@link SkinProfiles}; the old shapes of 0.11–0.16 all map to {@link SkinProfiles#LEGACY}.
 */
public final class Profile {

    public final String name;
    /** Whether a building of it shows blueprint colours while it is built and develops when it forms (§3.9.6). */
    public final boolean develops;
    /** Whether its materials carry the building's variant patterns (§3.5). */
    public final boolean variants;

    Profile(String name, boolean develops, boolean variants) {
        this.name = name;
        this.develops = develops;
        this.variants = variants;
    }

    @Override
    public String toString() {
        return name;
    }
}
