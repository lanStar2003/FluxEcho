package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

/**
 * The quests section of config/fluxecho.cfg: whether FluxEcho puts its quest lines into BetterQuesting. Since 0.17.0
 * each section of the config file is its own class, listed in Config.SECTIONS, so that work on different subsystems
 * (and later on each building) never edits the same file; the keys, defaults, bounds and comments are the ones 0.16.0
 * shipped, word for word.
 */
public final class QuestsConfig {

    public static final String CATEGORY = "quests";

    public static boolean installQuests = true;

    private QuestsConfig() {}

    public static void load(Configuration c) {
        c.setCategoryComment(CATEGORY, "FluxEcho's quest lines for BetterQuesting.");
        installQuests = c.getBoolean(
            "install",
            CATEGORY,
            installQuests,
            "Put FluxEcho's quest lines into every world's quest database when it loads (missing quests added, changed ones updated, progress kept), and copy them into config/betterquesting/DefaultQuests and QuestLinesOrder.txt at startup so /bq_admin default load keeps them. Other quest lines are never touched.");
    }
}
