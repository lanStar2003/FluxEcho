package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

/**
 * The codex section of config/fluxecho.cfg: the tooltip lines of the Echo Codex. Since 0.17.0 each section of the
 * config file is its own class, listed in Config.SECTIONS, so that work on different subsystems (and later on each
 * building) never edits the same file; the keys, defaults, bounds and comments are the ones 0.16.0 shipped, word for
 * word.
 */
public final class CodexConfig {

    public static final String CATEGORY = "codex";

    public static boolean codexHints = true;

    private CodexConfig() {}

    public static void load(Configuration c) {
        c.setCategoryComment(CATEGORY, "The Echo Codex and the tooltip lines.");
        codexHints = c.getBoolean(
            "tooltipHints",
            CATEGORY,
            codexHints,
            "Client: a line under bees, seed bags, phials, ... saying whether your team has done it once (NEI shows it too).");
    }
}
