package com.fluxecho.nexus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import com.fluxecho.logic.ResearchTree;
import com.fluxecho.matter.MatterModule;
import com.fluxecho.research.Research;

/**
 * What the nexus's manifestation table makes (blueprint 3.1): each recipe opens with a research, takes its inputs from
 * the table's input slots, draws {@code manifestEuPerTick} on top of the upkeep for its time, and puts its result in
 * the output slots. The table runs the first open recipe its inputs pay for. The echo crystal also takes a resting
 * entry of the team's Echo Codex ({@link Records}).
 */
public final class Manifests {

    public static final class Recipe {

        public final String id, research;
        public final List<ResearchTree.Cost> inputs;
        private final Supplier<ItemStack> output;
        public final int ticks;
        /** Whether it presses a codex entry into its result (the echo crystal). */
        public final boolean record;

        public Recipe(String id, String research, String[] inputs, Supplier<ItemStack> output, int ticks,
            boolean record) {
            this.id = id;
            this.research = research;
            List<ResearchTree.Cost> l = new ArrayList<>();
            for (String s : inputs) l.add(ResearchTree.Cost.parse(s));
            this.inputs = Collections.unmodifiableList(l);
            this.output = output;
            this.ticks = ticks;
            this.record = record;
        }

        public ItemStack output() {
            ItemStack s = output.get();
            return s == null ? null : s.copy();
        }
    }

    private static final List<Recipe> ALL = new ArrayList<>();

    private Manifests() {}

    public static synchronized void register(Recipe r) {
        ALL.removeIf(x -> x.id.equals(r.id));
        ALL.add(r);
    }

    public static synchronized List<Recipe> all() {
        return new ArrayList<>(ALL);
    }

    public static synchronized Recipe get(String id) {
        for (Recipe r : ALL) if (r.id.equals(id)) return r;
        return null;
    }

    static void init() {
        register(
            new Recipe(
                "echo_crystal",
                Research.ECHO_CRYSTAL,
                new String[] { "ore:" + MatterModule.CRYSTAL + "*1" },
                () -> new ItemStack(MatterModule.echoCrystal),
                200,
                true));
    }
}
