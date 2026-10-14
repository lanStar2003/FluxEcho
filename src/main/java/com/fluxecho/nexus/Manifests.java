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
 * entry of the team's Echo Codex ({@link Records}). Since 0.16.0 a recipe may also draw power of its own on top of
 * the table's share ({@link Recipe#extraEut}) and wait for a nexus of a phase ({@link Recipe#minPhase}): manifest
 * matter, which BLUEPRINT 3.6 has the nexus make from phase III on at a great cost in power.
 */
public final class Manifests {

    public static final class Recipe {

        public final String id, research;
        public final List<ResearchTree.Cost> inputs;
        private final Supplier<ItemStack> output;
        public final int ticks;
        /** Whether it presses a codex entry into its result (the echo crystal). */
        public final boolean record;
        /**
         * EU/t it draws while it runs on top of the table's {@code manifestEuPerTick} (0.16.0: manifest matter's LuV
         * power); 0 for the recipes before it.
         */
        public final long extraEut;
        /**
         * The lowest phase of the nexus that may run it (0.16.0: III for manifest matter); 1 for the recipes before it,
         * which every nexus runs.
         */
        public final int minPhase;

        public Recipe(String id, String research, String[] inputs, Supplier<ItemStack> output, int ticks,
            boolean record) {
            this(id, research, inputs, output, ticks, record, 0, 1);
        }

        /** A recipe that draws power of its own while it runs and waits for a nexus of a phase (0.16.0). */
        public Recipe(String id, String research, String[] inputs, Supplier<ItemStack> output, int ticks,
            boolean record, long extraEut, int minPhase) {
            this.id = id;
            this.research = research;
            List<ResearchTree.Cost> l = new ArrayList<>();
            for (String s : inputs) l.add(ResearchTree.Cost.parse(s));
            this.inputs = Collections.unmodifiableList(l);
            this.output = output;
            this.ticks = ticks;
            this.record = record;
            this.extraEut = Math.max(0, extraEut);
            this.minPhase = Math.max(1, minPhase);
        }

        public ItemStack output() {
            ItemStack s = output.get();
            return s == null ? null : s.copy();
        }

        /** Whether a nexus of the phase may run it ({@link #minPhase}). */
        public boolean runsAt(int phase) {
            return phase >= minPhase;
        }

        /** EU/t the table draws while it runs: the table's share of the upkeep ({@code base}) and its own extra. */
        public long tableEut(long base) {
            return base + extraEut;
        }
    }

    /**
     * EU/t the table draws for the recipe running ({@code id}, empty for none): nothing while it is idle, the table's
     * share ({@code base}) and the recipe's own extra while one runs; a recipe no longer registered draws the share.
     */
    public static long tableEut(String id, long base) {
        if (id == null || id.isEmpty()) return 0;
        Recipe r = get(id);
        return r == null ? base : r.tableEut(base);
    }

    /** The id of memory filament's recipe (0.15.0). */
    public static final String MEMORY_FILAMENT = "memory_filament";
    /** The ids of manifest matter's and the anti-gravity lattice's recipes (0.16.0). */
    public static final String MANIFEST_MATTER = "manifest_matter", ANTI_GRAVITY_LATTICE = "anti_gravity_lattice";

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
        // resonant glass (0.12.0, BLUEPRINT 3.6): borosilicate glass tuned with phase alloy, four panes an ingot
        register(
            new Recipe(
                "resonant_glass",
                Research.PHASE_2,
                new String[] { "ore:" + MatterModule.PHASE_ALLOY + "*1", "ore:ingotBorosilicateGlass*4" },
                () -> MatterModule.resonantGlass(4),
                400,
                false));
        // memory filament (0.15.0, BLUEPRINT 3.6): the wiremill's phase alloy wire coated with an echo crystal (any,
        // whatever it holds), an ingot's eight wires a crystal. It opens with phase II, so the phase III research that
        // takes it can be paid for; the wiremill's IV power is what holds it back until then.
        register(
            new Recipe(
                MEMORY_FILAMENT,
                Research.PHASE_2,
                new String[] { "ore:" + MatterModule.PHASE_ALLOY_WIRE + "*" + MatterModule.WIRES_PER_INGOT,
                    "ore:" + MatterModule.ECHO_CRYSTAL + "*1" },
                () -> MatterModule.memoryFilament(MatterModule.WIRES_PER_INGOT),
                400,
                false));
        // manifest matter (0.16.0, BLUEPRINT 3.6): a little of the flux layer made matter, from phase alloy, an echo
        // crystal (any, whatever it holds) and an LuV circuit, four a run. The nexus and the Sophon Array come first:
        // it opens with the Sophon Array's research and only a nexus of phase III or later makes it, a minute a run at
        // the LuV power it draws on top of the table's share (BLUEPRINT 3.6: it takes a great deal of power)
        register(
            new Recipe(
                MANIFEST_MATTER,
                Research.SOPHON_ARRAY,
                new String[] { "ore:" + MatterModule.PHASE_ALLOY + "*2", "ore:" + MatterModule.ECHO_CRYSTAL + "*1",
                    "ore:circuitLuV*1" },
                () -> MatterModule.manifestMatter(MatterModule.MANIFEST_MATTER_YIELD),
                MatterModule.MANIFEST_MATTER_TICKS,
                false,
                MatterModule.MANIFEST_MATTER_EUT,
                MatterModule.MANIFEST_MATTER_PHASE));
        // the anti-gravity lattice (0.16.0, BLUEPRINT 3.6): manifest matter set in resonant glass, four a run; it opens
        // with the sky ring, whose floating foundations it is the lattice of
        register(
            new Recipe(
                ANTI_GRAVITY_LATTICE,
                Research.SKY_RING,
                new String[] { "ore:" + MatterModule.MANIFEST_MATTER + "*1",
                    "ore:" + MatterModule.RESONANT_GLASS + "*4" },
                () -> MatterModule.antiGravityLattice(MatterModule.LATTICE_YIELD),
                MatterModule.LATTICE_TICKS,
                false));
    }
}
