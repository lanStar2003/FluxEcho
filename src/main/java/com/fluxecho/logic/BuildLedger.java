package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The material account of one nexus's builder: raw credit per ingredient spec (in {@link PartRecipes#UNIT}s of
 * 1/65536 item, so a part's fractional recipe share is exact) and finished parts per part code. Items fed to the nexus
 * become credit, every placed block is charged here, and a block that fails to land is refunded exactly. Finished parts
 * are always used before raw credit, and part-only parts (the seat, the library core, the supply port) can only be paid
 * with a finished part.
 *
 * <p>
 * The cost scale (config {@code buildCostScale}, 0 to 10) multiplies raw lines only and rounds each line up; 0 makes
 * every placement free. The ledger knows nothing of NBT: the caller persists {@link #rawView()} and
 * {@link #partView()} and restores them with {@link #load}.
 *
 * <p>
 * {@link #need} keys raw lines by their spec and part lines by {@code part:<code>}, all in units. A part line says how
 * many more finished parts would still help: for a part-only part it is the only way to pay, for a craftable part it
 * is an alternative to raw lines that are still short and disappears once the raw credit covers that part. So the need
 * is empty exactly when the job is fully funded ({@link #funded}), and {@link #wants} turns finished parts away as
 * well as raw items once they would only pile up. {@link #bill} keeps just the lines a player must supply.
 */
public final class BuildLedger {

    /** Raw keys that are never withdrawn, because their items carry NBT the ledger cannot keep (echo crystals). */
    public static final Set<String> KEPT = Collections
        .unmodifiableSet(new HashSet<>(Collections.singletonList("ore:gemEchoCrystal")));

    /** The highest cost scale; larger values are clamped. */
    public static final double MAX_SCALE = 10;

    private final Map<String, Long> raw = new TreeMap<>();
    private final Map<Integer, Integer> parts = new TreeMap<>();
    private boolean usedPart;

    /** Raw credit of a key, in units. */
    public long raw(String key) {
        Long v = raw.get(key);
        return v == null ? 0 : v;
    }

    /** Finished parts of a code on hand. */
    public int part(int code) {
        Integer v = parts.get(code);
        return v == null ? 0 : v;
    }

    /** Adds raw credit (a whole item is {@link PartRecipes#UNIT}); the key is a raw spec, never a part key. */
    public void addRaw(String key, long units) {
        if (key == null || key.isEmpty() || PartRecipes.isPart(key))
            throw new IllegalArgumentException("raw key expected: " + key);
        if (units < 0) throw new IllegalArgumentException("negative credit " + units + " for " + key);
        if (units > 0) raw.put(key, raw(key) + units);
    }

    /** Adds finished parts (fed items, replaced own blocks, old shelves credited 1:1). */
    public void addPart(int code, int n) {
        if (n < 0) throw new IllegalArgumentException("negative part credit " + n + " for " + code);
        if (n > 0) parts.put(code, part(code) + n);
    }

    /**
     * Charges one placement of the part: a finished part if there is one, else each raw line of
     * {@link PartRecipes#cost} times the scale, rounded up. A part-only part needs a finished part unless the scale is
     * 0, which makes everything free. Returns false and changes nothing when the ledger cannot pay; after a successful
     * charge {@link #usedPart()} tells the caller what to pass back to {@link #refund}.
     */
    public boolean charge(int part, double scale) {
        usedPart = false;
        int have = part(part);
        if (have > 0) {
            setPart(part, have - 1);
            usedPart = true;
            return true;
        }
        double s = clamp(scale);
        if (s == 0) return true;
        if (PartRecipes.partOnly(part)) return false;
        Map<String, Long> cost = PartRecipes.cost(part);
        for (Map.Entry<String, Long> e : cost.entrySet()) if (raw(e.getKey()) < scaled(e.getValue(), s)) return false;
        for (Map.Entry<String, Long> e : cost.entrySet()) setRaw(e.getKey(), raw(e.getKey()) - scaled(e.getValue(), s));
        return true;
    }

    /** Whether the last successful {@link #charge} used a finished part (false when it used raw credit or was free). */
    public boolean usedPart() {
        return usedPart;
    }

    /**
     * Undoes one charge of the part: gives the finished part back when the charge used one, else the raw lines at the
     * scale. Pass the same scale as the charge so the refund is exact.
     */
    public void refund(int part, double scale, boolean usedPart) {
        if (usedPart) {
            addPart(part, 1);
            return;
        }
        double s = clamp(scale);
        if (s == 0 || PartRecipes.partOnly(part)) return;
        for (Map.Entry<String, Long> e : PartRecipes.cost(part)
            .entrySet()) addRaw(e.getKey(), scaled(e.getValue(), s));
    }

    /**
     * The remaining need of a plan whose unbuilt cells hold the given part counts, in units. Finished parts on hand
     * cover their cells first. The rest is the scaled raw cost (summed per placement, as {@link #charge} takes it)
     * less the raw balance, one line per raw key that is still short. A part-only part adds a {@code part:<code>} line
     * for every one still missing. A craftable part adds a {@code part:<code>} line only while one of its own raw
     * lines is short, for as many finished parts as would close the largest of those shortfalls (never more than are
     * missing): once the raw credit covers a part, a finished one would not help any more.
     * <p>
     * Only positive lines are returned. The result is empty exactly when the ledger can pay for every unbuilt cell
     * (or the scale is 0), so {@code need(...).isEmpty()} (or {@link #funded}) is the "fully funded" test, and it
     * agrees with {@code bill(need(...)).isEmpty()}.
     */
    public Map<String, Long> need(Map<Integer, Integer> unbuiltParts, double scale) {
        double s = clamp(scale);
        Map<String, Long> sum = new TreeMap<>();
        if (s == 0) return sum;
        Map<Integer, Long> craftable = new TreeMap<>();
        for (Map.Entry<Integer, Integer> e : unbuiltParts.entrySet()) {
            int code = e.getKey(), n = e.getValue() == null ? 0 : e.getValue();
            long missing = n - Math.min(n, part(code));
            if (missing <= 0) continue;
            if (PartRecipes.partOnly(code)) {
                sum.merge(PartRecipes.part(code), missing * PartRecipes.UNIT, Long::sum);
                continue;
            }
            Map<String, Long> cost = PartRecipes.cost(code);
            if (cost.isEmpty()) continue; // grass, dirt, air: EU only
            craftable.merge(code, missing, Long::sum);
            for (Map.Entry<String, Long> c : cost.entrySet())
                sum.merge(c.getKey(), missing * scaled(c.getValue(), s), Long::sum);
        }
        Map<String, Long> out = new TreeMap<>();
        for (Map.Entry<String, Long> e : sum.entrySet()) {
            long left = PartRecipes.isPart(e.getKey()) ? e.getValue() : e.getValue() - raw(e.getKey());
            if (left > 0) out.put(e.getKey(), left);
        }
        // a finished craftable part only helps while one of its raw lines is short
        for (Map.Entry<Integer, Long> e : craftable.entrySet()) {
            long useful = 0;
            for (Map.Entry<String, Long> c : PartRecipes.cost(e.getKey())
                .entrySet()) {
                Long left = out.get(c.getKey());
                if (left == null) continue;
                long per = scaled(c.getValue(), s);
                useful = Math.max(useful, (left + per - 1) / per);
            }
            useful = Math.min(useful, e.getValue());
            if (useful > 0) out.put(PartRecipes.part(e.getKey()), useful * PartRecipes.UNIT);
        }
        return out;
    }

    /**
     * Whether the ledger can pay for every unbuilt cell of the plan at the scale: finished parts and raw credit cover
     * all of them, so charging them all (in any order) succeeds. The same as {@code need(...).isEmpty()}.
     */
    public boolean funded(Map<Integer, Integer> unbuiltParts, double scale) {
        return need(unbuiltParts, scale).isEmpty();
    }

    /** Whether accepting more of a raw key or a {@code part:<code>} key still helps the given need (back-pressure). */
    public boolean wants(String key, Map<String, Long> need) {
        Long v = need.get(key);
        return v != null && v > 0;
    }

    /**
     * The lines of a need a player must still supply, largest first: the raw lines, and the part lines of part-only
     * parts (the part lines of craftable parts are alternatives to raw lines already listed).
     */
    public static Map<String, Long> bill(Map<String, Long> need) {
        List<Map.Entry<String, Long>> lines = new ArrayList<>();
        for (Map.Entry<String, Long> e : need.entrySet()) {
            int code = PartRecipes.partCode(e.getKey());
            if (e.getValue() > 0 && (code < 0 || PartRecipes.partOnly(code))) lines.add(e);
        }
        lines.sort((a, b) -> {
            int c = Long.compare(b.getValue(), a.getValue());
            return c != 0 ? c
                : a.getKey()
                    .compareTo(b.getKey());
        });
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : lines) out.put(e.getKey(), e.getValue());
        return out;
    }

    /** Whether a raw key's credit may be withdrawn as items. */
    public static boolean canWithdraw(String key) {
        return !KEPT.contains(key) && !PartRecipes.isPart(key);
    }

    /** Whole items that can be withdrawn per raw key (balance / UNIT), leaving out the {@link #KEPT} keys. */
    public Map<String, Integer> withdrawable() {
        Map<String, Integer> out = new TreeMap<>();
        for (Map.Entry<String, Long> e : raw.entrySet()) {
            if (!canWithdraw(e.getKey())) continue;
            long items = e.getValue() / PartRecipes.UNIT;
            if (items > 0) out.put(e.getKey(), (int) Math.min(Integer.MAX_VALUE, items));
        }
        return out;
    }

    /**
     * Takes whole items of a raw key out of the ledger, at most what {@link #withdrawable()} allows, and returns how
     * many were taken (0 for a {@link #KEPT} key).
     */
    public int take(String key, int items) {
        if (items <= 0 || !canWithdraw(key)) return 0;
        int n = (int) Math.min(items, raw(key) / PartRecipes.UNIT);
        if (n > 0) setRaw(key, raw(key) - n * PartRecipes.UNIT);
        return n;
    }

    /** The raw balances (read-only, sorted by key) for the caller to persist. */
    public Map<String, Long> rawView() {
        return Collections.unmodifiableMap(raw);
    }

    /** The finished parts on hand (read-only, sorted by code) for the caller to persist. */
    public Map<Integer, Integer> partView() {
        return Collections.unmodifiableMap(parts);
    }

    /** Replaces the whole ledger with saved balances, skipping empty, negative and malformed entries. */
    public void load(Map<String, Long> raw, Map<Integer, Integer> parts) {
        Map<String, Long> r = new TreeMap<>();
        Map<Integer, Integer> p = new TreeMap<>();
        if (raw != null) for (Map.Entry<String, Long> e : raw.entrySet()) {
            String key = e.getKey();
            Long v = e.getValue();
            if (key != null && !key.isEmpty() && !PartRecipes.isPart(key) && v != null && v > 0) r.put(key, v);
        }
        if (parts != null) for (Map.Entry<Integer, Integer> e : parts.entrySet()) {
            Integer v = e.getValue();
            if (e.getKey() != null && v != null && v > 0) p.put(e.getKey(), v);
        }
        this.raw.clear();
        this.raw.putAll(r);
        this.parts.clear();
        this.parts.putAll(p);
        usedPart = false;
    }

    /** One raw line of one placement at the scale, rounded up to a whole unit (0 when the scale is 0). */
    public static long scaled(long units, double scale) {
        double s = clamp(scale);
        if (s == 0 || units <= 0) return 0;
        if (s == 1) return units;
        // a hair below the product so a scale like 1.1 that is not exact in binary does not round a whole result up
        return (long) Math.ceil(units * s - 1e-6);
    }

    /** The scale limited to 0..{@link #MAX_SCALE}; NaN counts as 1. */
    public static double clamp(double scale) {
        if (Double.isNaN(scale)) return 1;
        return Math.max(0, Math.min(MAX_SCALE, scale));
    }

    private void setRaw(String key, long units) {
        if (units > 0) raw.put(key, units);
        else raw.remove(key);
    }

    private void setPart(int code, int n) {
        if (n > 0) parts.put(code, n);
        else parts.remove(code);
    }
}
