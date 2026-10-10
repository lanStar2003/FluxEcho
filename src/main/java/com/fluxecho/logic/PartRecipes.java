package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The crafting recipes of every campus part, as plain data: one table drives both the shaped crafting recipes (a
 * Minecraft-side registrar turns each {@link Recipe} into a shaped recipe) and the builder's material cost, so building
 * a block from raw credit always costs exactly what crafting it by hand would. Ingredient specs are
 * {@code ore:<name>}, {@code item:<mod>:<name>[@meta]} (the {@link ResearchTree.Cost#parse} syntax without a count,
 * meta {@code 32767} meaning any) or {@code part:<code>} for another part (a {@link Parts} code).
 *
 * <p>
 * {@link #cost(int)} expands {@code part:} ingredients recursively into raw specs, in {@link #UNIT}s of 1/65536 item.
 * The expansion must be exact at every level (the ingredient count times the sub-cost must divide by the yield); the
 * table is checked when the class loads and a bad entry throws there, so a mistake cannot reach a running game.
 *
 * <p>
 * The Crystal Seat's recipe needs a GregTech HV hull, which has no spec, so it is not in this table (the frame module
 * keeps registering it); the seat, the library core and the supply port are part-only: the builder takes a finished
 * item for them and never expands them into raw credit.
 */
public final class PartRecipes {

    /** One item in ledger credit: costs and balances are counted in 1/65536 of an item. */
    public static final long UNIT = 65536;

    /** The prefix of an ingredient spec (and a ledger key) that names another part. */
    public static final String PART_PREFIX = "part:";

    /**
     * One shaped recipe: {@code yield} of {@code part} from up to three pattern rows of equal width, where every
     * non-space character is a key of {@code keys} and every key is an ingredient spec. The pattern array must not be
     * modified.
     */
    public static final class Recipe {

        public final int part, yield;
        /** An English label for logs (the frame parts keep the names they had in the frame module). */
        public final String name;
        public final String[] pattern;
        public final Map<Character, String> keys;

        Recipe(int part, int yield, String name, String[] pattern, Map<Character, String> keys) {
            this.part = part;
            this.yield = yield;
            this.name = name;
            this.pattern = pattern;
            this.keys = Collections.unmodifiableMap(new LinkedHashMap<>(keys));
        }

        /** How many times the key character appears in the pattern. */
        public int count(char key) {
            int n = 0;
            for (String row : pattern) for (int i = 0; i < row.length(); i++) if (row.charAt(i) == key) n++;
            return n;
        }

        /** Every ingredient spec with its total count in the pattern, in key order. */
        public Map<String, Integer> ingredients() {
            Map<String, Integer> out = new LinkedHashMap<>();
            for (Map.Entry<Character, String> e : keys.entrySet())
                out.merge(e.getValue(), count(e.getKey()), Integer::sum);
            return out;
        }
    }

    private static final Map<Integer, Recipe> RECIPES;
    private static final List<Recipe> ALL;
    private static final Set<Integer> PART_ONLY;
    private static final Map<Integer, Map<String, Long>> COSTS;

    static {
        List<Recipe> list = new ArrayList<>();
        String stoneBrick = "item:minecraft:stonebrick@32767", ssPlate = "ore:plateStainlessSteel",
            ssRod = "ore:stickStainlessSteel", steelPlate = "ore:plateSteel", grit = "ore:dustFluxGrit",
            crystal = "ore:gemFluxCrystal", echo = "ore:gemEchoCrystal", glowDust = "item:minecraft:glowstone_dust",
            pane = "item:minecraft:glass_pane", glowstone = "ore:dustGlowstone", coal = "ore:dustCoal",
            steelRod = "ore:stickSteel";
        String base = part(Parts.frame(Parts.FR_BASE)), baseLit = part(Parts.frame(Parts.FR_BASE_LIT)),
            pillar = part(Parts.frame(Parts.FR_PILLAR)), deck = part(Parts.deck(Parts.D_DECK)),
            trim = part(Parts.deck(Parts.D_TRIM)), lit = part(Parts.deck(Parts.D_LIT)),
            panel = part(Parts.deck(Parts.D_PANEL)), panelLit = part(Parts.deck(Parts.D_PANEL_LIT)),
            panelDark = part(Parts.deck(Parts.D_PANEL_DARK));

        // frame parts: the patterns of FrameModule.postInit (the Crystal Seat is left out, see the class comment)
        list.add(
            recipe(
                Parts.frame(Parts.FR_BASE),
                8,
                "Flux Frame Base",
                rows("BSB", "SGS", "BSB"),
                'B',
                stoneBrick,
                'S',
                ssPlate,
                'G',
                grit));
        list.add(
            recipe(
                Parts.frame(Parts.FR_BASE_LIT),
                4,
                "Lit Frame Base",
                rows("GBG", "BCB", "GBG"),
                'G',
                glowDust,
                'B',
                base,
                'C',
                crystal));
        list.add(
            recipe(Parts.frame(Parts.FR_PILLAR), 4, "Frame Pillar", rows("R R", "RGR", "R R"), 'R', ssRod, 'G', grit));
        list.add(
            recipe(
                Parts.frame(Parts.FR_CONDUIT),
                1,
                "Flux Conduit",
                rows(" C ", "GPG", " C "),
                'C',
                crystal,
                'G',
                pane,
                'P',
                pillar));
        list.add(recipe(Parts.frame(Parts.FR_RING), 4, "Ring Segment", rows("SCS", "SSS"), 'S', ssPlate, 'C', crystal));
        list.add(
            recipe(
                Parts.frame(Parts.FR_FOUNDATION),
                8,
                "Module Foundation",
                rows("BSB", "SES", "BSB"),
                'B',
                base,
                'S',
                ssPlate,
                'E',
                echo));
        list.add(
            recipe(
                Parts.frame(Parts.FR_SHELF),
                32,
                "Echo Shelf",
                rows("SBS", "BEB", "SBS"),
                'S',
                steelPlate,
                'B',
                "item:minecraft:bookshelf",
                'E',
                echo));
        list.add(
            recipe(
                Parts.frame(Parts.FR_CONSOLE),
                2,
                "Console Stand",
                rows("SCS", " P ", " L "),
                'S',
                ssPlate,
                'C',
                crystal,
                'P',
                pillar,
                'L',
                baseLit));

        // deck parts
        list.add(
            recipe(
                Parts.deck(Parts.D_DECK),
                16,
                "Campus Deck",
                rows("SSS", "SPS", "SSS"),
                'S',
                "ore:stone",
                'P',
                steelPlate));
        list.add(recipe(Parts.deck(Parts.D_TRIM), 8, "Trim Deck", rows("DDD", "DcD", "DDD"), 'D', deck, 'c', coal));
        list.add(recipe(Parts.deck(Parts.D_LIT), 8, "Lit Deck", rows("DgD", "DDD", "DgD"), 'D', deck, 'g', glowstone));
        list.add(
            recipe(
                Parts.deck(Parts.D_GRATE),
                8,
                "Grate Deck",
                rows("DDD", "DbD", "DDD"),
                'D',
                deck,
                'b',
                "item:minecraft:iron_bars"));
        list.add(recipe(Parts.deck(Parts.D_DARK), 8, "Dark Deck", rows("DTD", "TDT", "DTD"), 'D', deck, 'T', trim));
        list.add(
            recipe(Parts.deck(Parts.D_SKIRT), 8, "Skirt Deck", rows("TTT", "TgT", "TTT"), 'T', trim, 'g', glowstone));
        list.add(
            recipe(Parts.deck(Parts.D_CHEVRON), 8, "Chevron Deck", rows("LTL", "TLT", "LTL"), 'L', lit, 'T', trim));
        list.add(
            recipe(
                Parts.deck(Parts.D_WELL),
                4,
                "Light Well Deck",
                rows("L L", " G ", "L L"),
                'L',
                lit,
                'G',
                "item:minecraft:glowstone"));
        list.add(
            recipe(
                Parts.deck(Parts.D_PANEL),
                16,
                "Wall Panel",
                rows("BBB", "BPB", "BBB"),
                'B',
                stoneBrick,
                'P',
                steelPlate));
        list.add(
            recipe(
                Parts.deck(Parts.D_PANEL_LIT),
                8,
                "Lit Wall Panel",
                rows("PPP", "gPg", "PPP"),
                'P',
                panel,
                'g',
                glowstone));
        list.add(
            recipe(
                Parts.deck(Parts.D_PANEL_DARK),
                8,
                "Dark Wall Panel",
                rows("PPP", "PcP", "PPP"),
                'P',
                panel,
                'c',
                coal));
        list.add(
            recipe(
                Parts.deck(Parts.D_CORNICE),
                8,
                "Cornice Panel",
                rows("PPP", "gSg", "PPP"),
                'P',
                panel,
                'S',
                ssPlate,
                'g',
                glowstone));

        // fitting parts
        list.add(
            recipe(
                Parts.fitting(Parts.F_POST),
                8,
                "Shelf Post",
                rows("PrP", "PgP", "PrP"),
                'P',
                panel,
                'r',
                steelRod,
                'g',
                glowstone));
        list.add(
            recipe(
                Parts.fitting(Parts.F_PLINTH),
                8,
                "Shelf Plinth",
                rows("KKK", "KgK", "KKK"),
                'K',
                panelDark,
                'g',
                glowstone));
        list.add(
            recipe(
                Parts.fitting(Parts.F_CROWN),
                8,
                "Shelf Crown",
                rows("LLL", "LPL", "LLL"),
                'L',
                panelLit,
                'P',
                steelPlate));
        list.add(
            recipe(
                Parts.fitting(Parts.F_RAIL),
                8,
                "Gallery Rail",
                rows("r r", "rGr", "r r"),
                'r',
                steelRod,
                'G',
                "ore:paneGlass"));
        list.add(
            recipe(Parts.fitting(Parts.F_TREAD), 8, "Stair Tread", rows("PgP", "PPP"), 'P', panel, 'g', glowstone));
        list.add(
            recipe(
                Parts.fitting(Parts.F_GLAZE),
                8,
                "Steel Glaze",
                rows("GGG", "GPG", "GGG"),
                'G',
                "ore:blockGlass",
                'P',
                steelPlate));
        list.add(
            recipe(
                Parts.fitting(Parts.F_PEDESTAL),
                4,
                "Pedestal",
                rows(" L ", "LBL", " L "),
                'L',
                panelLit,
                'B',
                baseLit));

        // the supply port: crafted by hand, taken as a finished item by the builder
        list.add(
            recipe(
                Parts.SUPPLY_PORT,
                1,
                "Supply Port",
                rows("PCP", "PHP", "PPP"),
                'P',
                steelPlate,
                'H',
                "item:minecraft:hopper",
                'C',
                crystal));

        Set<Integer> partOnly = new HashSet<>();
        partOnly.add(Parts.frame(Parts.FR_SEAT));
        partOnly.add(Parts.LIBRARY_CORE);
        partOnly.add(Parts.SUPPLY_PORT);

        Map<Integer, Recipe> byPart = new TreeMap<>();
        for (Recipe r : list) {
            if (byPart.put(r.part, r) != null) throw new IllegalStateException("two recipes for part " + r.part);
        }
        RECIPES = Collections.unmodifiableMap(byPart);
        ALL = Collections.unmodifiableList(new ArrayList<>(byPart.values()));
        PART_ONLY = Collections.unmodifiableSet(partOnly);
        COSTS = computeCosts(RECIPES, PART_ONLY);
    }

    private PartRecipes() {}

    /** The recipe making the part, or null when it has none here (grass, dirt, air, the seat, the library core). */
    public static Recipe get(int part) {
        return RECIPES.get(part);
    }

    /** Every recipe of the table, ordered by part code. */
    public static List<Recipe> all() {
        return ALL;
    }

    /** Whether the builder needs a finished item of this part (the seat, the library core and the supply port). */
    public static boolean partOnly(int part) {
        return PART_ONLY.contains(part);
    }

    /**
     * The raw cost of one part in {@link #UNIT}s, keyed by raw spec (never a {@code part:} key), expanded recursively.
     * Empty for part-only parts, grass, dirt, air and codes without a recipe.
     */
    public static Map<String, Long> cost(int part) {
        Map<String, Long> c = COSTS.get(part);
        return c == null ? Collections.<String, Long>emptyMap() : c;
    }

    /** The ingredient spec (and ledger key) of a part: {@code part:<code>}. */
    public static String part(int code) {
        return PART_PREFIX + code;
    }

    /** The part code of a {@code part:<code>} spec, or -1 when the spec is not a part spec. */
    public static int partCode(String spec) {
        if (spec == null || !spec.startsWith(PART_PREFIX)) return -1;
        String digits = spec.substring(PART_PREFIX.length());
        if (digits.isEmpty() || digits.length() > 3) return -1;
        for (int i = 0; i < digits.length(); i++) if (!Character.isDigit(digits.charAt(i))) return -1;
        return Integer.parseInt(digits);
    }

    /** Whether the spec names a part rather than a raw ore or item. */
    public static boolean isPart(String spec) {
        return partCode(spec) >= 0;
    }

    private static String[] rows(String... rows) {
        return rows;
    }

    /** Builds a recipe from a pattern and alternating key characters and ingredient specs. */
    static Recipe recipe(int part, int yield, String name, String[] pattern, Object... keys) {
        if (keys.length % 2 != 0) throw new IllegalStateException(name + ": keys must come in pairs");
        Map<Character, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keys.length; i += 2) {
            char ch = (Character) keys[i];
            if (map.put(ch, (String) keys[i + 1]) != null) throw new IllegalStateException(name + ": key twice " + ch);
        }
        Recipe r = new Recipe(part, yield, name, pattern, map);
        check(r);
        return r;
    }

    /** Checks the shape of a recipe and that every raw key is a valid {@link ResearchTree.Cost} spec. */
    private static void check(Recipe r) {
        if (r.yield < 1 || r.yield > 64) throw new IllegalStateException(r.name + ": yield " + r.yield);
        if (r.pattern.length < 1 || r.pattern.length > 3) throw new IllegalStateException(r.name + ": rows");
        int width = r.pattern[0].length();
        if (width < 1 || width > 3) throw new IllegalStateException(r.name + ": width " + width);
        Set<Character> used = new HashSet<>();
        for (String row : r.pattern) {
            if (row.length() != width) throw new IllegalStateException(r.name + ": rows of different widths");
            for (int i = 0; i < row.length(); i++) {
                char ch = row.charAt(i);
                if (ch == ' ') continue;
                if (!r.keys.containsKey(ch)) throw new IllegalStateException(r.name + ": no key for " + ch);
                used.add(ch);
            }
        }
        if (used.isEmpty()) throw new IllegalStateException(r.name + ": empty pattern");
        for (Map.Entry<Character, String> e : r.keys.entrySet()) {
            if (e.getKey() == ' ' || !used.contains(e.getKey()))
                throw new IllegalStateException(r.name + ": unused key " + e.getKey());
            String spec = e.getValue();
            if (spec == null || spec.indexOf('*') >= 0) throw new IllegalStateException(r.name + ": spec " + spec);
            if (spec.startsWith(PART_PREFIX)) {
                if (partCode(spec) < 0) throw new IllegalStateException(r.name + ": bad part spec " + spec);
            } else {
                try {
                    ResearchTree.Cost.parse(spec);
                } catch (RuntimeException ex) {
                    throw new IllegalStateException(r.name + ": bad spec " + spec, ex);
                }
            }
        }
    }

    /**
     * Expands every recipe that is not part-only into raw costs per part. Throws when an expansion is not exact, when a
     * recipe refers to a part without a recipe or to a part-only part, or when recipes refer to each other in a loop.
     */
    static Map<Integer, Map<String, Long>> computeCosts(Map<Integer, Recipe> recipes, Set<Integer> partOnly) {
        Map<Integer, Map<String, Long>> done = new HashMap<>();
        for (Recipe r : recipes.values()) {
            if (!partOnly.contains(r.part)) expand(r.part, recipes, partOnly, done, new HashSet<Integer>());
        }
        return Collections.unmodifiableMap(done);
    }

    private static Map<String, Long> expand(int part, Map<Integer, Recipe> recipes, Set<Integer> partOnly,
        Map<Integer, Map<String, Long>> done, Set<Integer> visiting) {
        Map<String, Long> known = done.get(part);
        if (known != null) return known;
        Recipe r = recipes.get(part);
        if (r == null) throw new IllegalStateException("no recipe for part " + part);
        if (partOnly.contains(part)) throw new IllegalStateException("part-only part " + part + " used as ingredient");
        if (!visiting.add(part)) throw new IllegalStateException("recipe loop through part " + part);
        Map<String, Long> sum = new TreeMap<>();
        for (Map.Entry<Character, String> e : r.keys.entrySet()) {
            long count = r.count(e.getKey());
            String spec = e.getValue();
            int sub = partCode(spec);
            if (sub < 0) {
                add(sum, spec, count * UNIT, r);
            } else {
                for (Map.Entry<String, Long> s : expand(sub, recipes, partOnly, done, visiting).entrySet())
                    add(sum, s.getKey(), count * s.getValue(), r);
            }
        }
        visiting.remove(part);
        Map<String, Long> out = Collections.unmodifiableMap(sum);
        done.put(part, out);
        return out;
    }

    /** Adds {@code total / yield} to the key, throwing when it does not divide exactly. */
    private static void add(Map<String, Long> sum, String key, long total, Recipe r) {
        if (total % r.yield != 0)
            throw new IllegalStateException(r.name + ": " + key + " does not divide exactly by the yield " + r.yield);
        sum.merge(key, total / r.yield, Long::sum);
    }
}
