package com.fluxecho;

import java.io.File;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

import com.fluxecho.config.AeConfig;
import com.fluxecho.config.BeesConfig;
import com.fluxecho.config.BloodConfig;
import com.fluxecho.config.CampusConfig;
import com.fluxecho.config.CodexConfig;
import com.fluxecho.config.CropsConfig;
import com.fluxecho.config.EffectsConfig;
import com.fluxecho.config.GatesConfig;
import com.fluxecho.config.GeneralConfig;
import com.fluxecho.config.ManaConfig;
import com.fluxecho.config.MobsConfig;
import com.fluxecho.config.NexusConfig;
import com.fluxecho.config.QuestsConfig;
import com.fluxecho.config.ThaumConfig;

/**
 * {@code config/fluxecho.cfg}: the list of its sections and the upgrade of files an older FluxEcho wrote. Every module
 * can be switched off; its machines stay registered so placed ones do not vanish from a world, only the recipes and
 * the work stop. Since 0.17.0 the settings themselves live in one class per section ({@code com.fluxecho.config}, each
 * with its fields and its {@code load}), so that parallel work on different subsystems and buildings never edits the
 * same file; this class only lists them and keeps {@link #upgrade}.
 */
public final class Config {

    /** One section of the file: reads its settings (writing the defaults of new ones) into its static fields. */
    @FunctionalInterface
    public interface Section {

        void load(Configuration c);
    }

    /**
     * Every section, in load order. A new section (a subsystem, a building) adds its line at the end; the order does
     * not change the file, which Forge writes sorted by category and key.
     */
    static final Section[] SECTIONS = { GeneralConfig::load, QuestsConfig::load, BeesConfig::load, ThaumConfig::load,
        BloodConfig::load, MobsConfig::load, CropsConfig::load, ManaConfig::load, CodexConfig::load, NexusConfig::load,
        CampusConfig::load, EffectsConfig::load, AeConfig::load, GatesConfig::load };

    /** Written into the file; an older one is brought up to date by {@link #upgrade}. */
    private static final String VERSION = "0.15.0";

    private Config() {}

    public static void load(File file) {
        Configuration c = new Configuration(file, VERSION);
        upgrade(c);
        for (Section s : SECTIONS) s.load(c);
        if (c.hasChanged()) c.save();
    }

    /**
     * Brings a config file written by an older FluxEcho up to date: settings that are gone are removed, and settings
     * whose default changed are reset to the new default (0.5.0: the vis pedestal costs ten times the EU, so its
     * buffer and link module grew too).
     */
    private static void upgrade(Configuration c) {
        String was = c.getLoadedConfigVersion();
        if (VERSION.equals(was)) return;
        if (older(was, "0.5.0")) {
            if (c.hasCategory("enchanting")) c.removeCategory(c.getCategory("enchanting"));
            if (c.hasCategory(ThaumConfig.CATEGORY)) {
                ConfigCategory t = c.getCategory(ThaumConfig.CATEGORY);
                for (String k : new String[] { "visEuPerCentiVis", "visPerShard", "visMaxTicks", "pedestalBufferEU",
                    "linkModuleEuPerTick" }) t.remove(k);
            }
        }
        // 0.6.0: the Mana Echo Spring is tiered by its core; its rates are per LV now
        if (older(was, "0.6.0") && c.hasCategory(ManaConfig.CATEGORY)) {
            ConfigCategory m = c.getCategory(ManaConfig.CATEGORY);
            for (String k : new String[] { "manaPerTick", "manaPerPetal" }) m.remove(k);
        }
        // 0.9.2: a library keeps one sample on each shelf of its hall
        if (older(was, "0.9.2") && c.hasCategory(NexusConfig.CATEGORY)) c.getCategory(NexusConfig.CATEGORY)
            .remove("librarySamples");
        // 0.10.1: grading fills four deep (it was two); a file that kept the old default gets the new one
        if (older(was, "0.10.1") && c.hasCategory(CampusConfig.CATEGORY)) {
            ConfigCategory n = c.getCategory(CampusConfig.CATEGORY);
            if (n.containsKey("buildFillDepth") && bumpsFillDepth(
                n.get("buildFillDepth")
                    .getString()))
                n.remove("buildFillDepth");
        }
        // 0.15.0: new campuses keep room for their outer rings (312, it was 144); a file that kept the old default gets
        // the new one, a spacing a player chose stays
        if (older(was, "0.15.0") && c.hasCategory(CampusConfig.CATEGORY)) {
            ConfigCategory n = c.getCategory(CampusConfig.CATEGORY);
            if (n.containsKey("buildMinSpacing") && bumpsMinSpacing(
                n.get("buildMinSpacing")
                    .getString()))
                n.remove("buildMinSpacing");
        }
    }

    /** Whether a saved {@code buildMinSpacing} is the default up to 0.14.2, 144, which 0.15.0 raises to 312. */
    static boolean bumpsMinSpacing(String saved) {
        return saved != null && "144".equals(saved.trim());
    }

    /** Whether a saved {@code buildFillDepth} is the 0.10.0 default, 2, which 0.10.1 raises to 4. */
    static boolean bumpsFillDepth(String saved) {
        return saved != null && "2".equals(saved.trim());
    }

    /** Whether a file written by version {@code was} (null: before versions were written) predates {@code v}. */
    static boolean older(String was, String v) {
        if (was == null || was.isEmpty()) return true;
        String[] a = was.split("\\."), b = v.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? parse(a[i]) : 0, y = i < b.length ? parse(b[i]) : 0;
            if (x != y) return x < y;
        }
        return false;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
