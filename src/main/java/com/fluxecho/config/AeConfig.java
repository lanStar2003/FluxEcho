package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

/**
 * The ae2 section of config/fluxecho.cfg: the Echo ME Provider. Since 0.17.0 each section of the config file is its own
 * class, listed in Config.SECTIONS, so that work on different subsystems (and later on each building) never edits the
 * same file; the keys, defaults, bounds and comments are the ones 0.16.0 shipped, word for word.
 */
public final class AeConfig {

    public static final String CATEGORY = "ae2";

    public static boolean aeEnabled = true;

    private AeConfig() {}

    public static void load(Configuration c) {
        c.setCategoryComment(CATEGORY, "Applied Energistics 2: the Echo ME Provider.");
        aeEnabled = c.getBoolean(
            "enabled",
            CATEGORY,
            aeEnabled,
            "Switch the Echo ME Provider off: no recipe, and placed ones offer no patterns.");
    }
}
