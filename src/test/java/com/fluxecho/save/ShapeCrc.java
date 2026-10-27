package com.fluxecho.save;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import net.minecraft.nbt.NBTTagCompound;

import com.fluxecho.campus.BuildJob;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.city.TileCityModule;
import com.fluxecho.library.TileLibrary;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.LibraryShape;
import com.fluxecho.logic.LibraryUnits;
import com.fluxecho.logic.Mix;
import com.fluxecho.logic.NexusShape;
import com.fluxecho.logic.RootShape;
import com.fluxecho.logic.UnitLayout;
import com.fluxecho.nexus.TileMultiblock;

/**
 * Checksums of everything a building of 0.16.0 is checked and built by (refactor §5.1 ④): for every shape a structure
 * tile may stand as, its blueprint (every layer, every character, the controller's cell), the parts its characters are
 * built as and its unit table; and for every plan key the campus builder runs, the plan itself, step by step, at one
 * fixed campus. {@code OldShapesCrcTest} holds the values the 0.16.0 code gave, written out by
 * {@code save.gen.GoldenSaveGen}: an old building still forms, and an old job resumes on the very plan it started
 * (which is why {@code BuildJob.V} never needs to move).
 * <p>
 * Only what the structure and the plan are made of is summed; how a formed building looks (what dissolves, its
 * light, its colours) is not, so the restyling of 0.17.0 onwards does not touch these sums.
 */
public final class ShapeCrc {

    /** The fixed campus the plans are summed at: its centre and its front (north), and the seed of its plan. */
    public static final int CX = 1000, Y0 = 64, CZ = -2000, FX = 0, FZ = -1;

    private ShapeCrc() {}

    /** One row: what is summed and its checksums (geometry and characters, parts, units; or the plan's). */
    public static final class Row {

        public final String what;
        public final long[] crc;

        Row(String what, long... crc) {
            this.what = what;
            this.crc = crc;
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder("{ \"" + what + "\"");
            for (long c : crc) b.append(", \"")
                .append(Long.toHexString(c))
                .append('"');
            return b.append(" }")
                .toString();
        }
    }

    /** A running CRC-32 of ints and strings. */
    private static final class Sum {

        final CRC32 crc = new CRC32();

        Sum add(long v) {
            for (int i = 0; i < 8; i++) crc.update((int) (v >>> (8 * i)) & 0xFF);
            return this;
        }

        Sum add(String s) {
            byte[] b = (s == null ? "\u0000" : s).getBytes(StandardCharsets.UTF_8);
            add(b.length);
            crc.update(b, 0, b.length);
            return this;
        }

        Sum add(int[] a) {
            if (a == null) return add(-1);
            add(a.length);
            for (int v : a) add(v);
            return this;
        }

        long value() {
            return crc.getValue();
        }
    }

    /** The rows of every shape of every structure tile, by {@code <tile id>:<shape>}. */
    public static List<Row> shapes() {
        List<Row> out = new ArrayList<>();
        for (Map.Entry<String, Class<? extends TileMultiblock>> e : IdExport.structures()
            .entrySet()) {
            TileMultiblock probe = IdExport.make(e.getValue());
            for (TileMultiblock.Shape s : IdExport.shapes(probe)) {
                TileMultiblock t = IdExport.make(e.getValue());
                t.expectShape(s.name);
                out.add(new Row(e.getKey() + ":" + s.name, geometry(s.bp), parts(t, s), units(t, s)));
            }
        }
        return out;
    }

    /** The blueprint: its layers row by row, every character, and the controller's cell. */
    static long geometry(Blueprint bp) {
        Sum s = new Sum();
        String[][] layers = bp.shape();
        s.add(layers.length);
        for (String[] layer : layers) {
            s.add(layer.length);
            for (String row : layer) s.add(row);
        }
        return s.add(bp.ctrlA)
            .add(bp.ctrlB)
            .add(bp.ctrlC)
            .value();
    }

    /** The part every character of a city module's blueprint is built as; its build's key and frame. */
    static long parts(TileMultiblock t, TileMultiblock.Shape shape) {
        Sum s = new Sum();
        // the nexus and the library name their blocks in their own structure definitions: their characters are summed
        // with the blueprint
        if (!(t instanceof TileCityModule cm)) return 0;
        CityBuild b = cm.build();
        s.add(b.key())
            .add(b.width())
            .add(b.depth())
            .add(b.height())
            .add(b.offset())
            .add(b.controller());
        for (Blueprint.Cell c : shape.bp.cells()) {
            int[] l = b.localOf(c.a, c.b, c.c);
            s.add(c.ch)
                .add(b.part(l[0], l[1], l[2], c.ch));
        }
        return s.value();
    }

    /** The unit table it keeps samples by in the shape: order, names, kinds, slots, cells and sockets. */
    static long units(TileMultiblock t, TileMultiblock.Shape shape) {
        Sum s = new Sum();
        if (t instanceof TileCityModule cm) {
            UnitLayout layout = cm.layout();
            s.add(layout.size())
                .add(layout.slots());
            for (UnitLayout.Unit u : layout.units()) {
                s.add(u.index)
                    .add(u.name)
                    .add(u.kind)
                    .add(u.slots)
                    .add(u.firstSlot)
                    .add(u.face)
                    .add(u.floor)
                    .add(u.base);
                for (int[] c : u.cells) s.add(c);
                for (int[] c : u.sockets) s.add(c);
            }
            return s.add(
                cm.samples()
                    .getSlots())
                .value();
        }
        if (t instanceof TileLibrary) {
            if (TileLibrary.HALL.equals(shape.name)) {
                for (LibraryShape.Shelf h : LibraryShape.SHELVES) s.add(h.x)
                    .add(h.y)
                    .add(h.z)
                    .add(h.inX)
                    .add(h.inZ);
                return s.value();
            }
            for (LibraryUnits.Unit u : LibraryUnits.UNITS) s.add(u.index)
                .add(u.storey)
                .add(u.alongX ? 1 : 0)
                .add(u.x0)
                .add(u.z0)
                .add(u.yBody)
                .add(u.faceX)
                .add(u.faceZ)
                .add(u.call);
            return s.add(LibraryUnits.SLOTS)
                .value();
        }
        return s.value();
    }

    // ---- plans

    /**
     * The plan keys summed, each with the form its job carries: every establish and growth, the forum, the outer
     * establish, the release, every module job on every site of its spec, the pod floating (doors shut and open),
     * every sky module at the deck of 40, and every deep root's form (its deepen part and its whole).
     */
    public static Map<String, BuildPlan.Plan> plans() {
        Map<String, BuildPlan.Plan> out = new LinkedHashMap<>();
        IdExport.registerSpecs();
        for (int p = 1; p <= NexusShape.PHASES; p++) put(out, BuildPlan.establishKey(p), null);
        for (int p = 2; p <= NexusShape.PHASES; p++) put(out, BuildPlan.growKey(p), null);
        put(out, BuildPlan.FORUM, null);
        put(out, BuildPlan.OUTER, null);
        put(out, BuildPlan.RELEASE, form("Pf", 40));
        for (ModuleSpec spec : IdExport.specs()) for (int site : spec.sites) {
            String key = spec.jobKey(site);
            put(out, key, null);
            if (DomeGeneratorShape.KEY.equals(spec.key)) {
                put(out, key, form("Pf", 40));
                NBTTagCompound open = form("Pf", 40);
                open.setInteger("Pd", 0xFF);
                put(out, key, open);
            }
            if (spec.family == com.fluxecho.logic.SiteFamily.SKY) put(out, key, form("Sd", 40));
            if (RootShape.KEY.equals(spec.key)) for (String deep : deepForms()) {
                NBTTagCompound f = new NBTTagCompound();
                f.setString("Rs", deep);
                put(out, key, f);
            }
        }
        return out;
    }

    /** The names of the deep root's forms (every shape of the root that is one). */
    static List<String> deepForms() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Class<? extends TileMultiblock>> e : IdExport.structures()
            .entrySet()) {
            if (!e.getKey()
                .equals("fluxecho:" + RootShape.KEY)) continue;
            for (TileMultiblock.Shape s : IdExport.shapes(IdExport.make(e.getValue())))
                if (RootShape.deep(s.name) != null) out.add(s.name);
        }
        Collections.sort(out);
        return out;
    }

    private static NBTTagCompound form(String key, int v) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger(key, v);
        return t;
    }

    private static void put(Map<String, BuildPlan.Plan> out, String key, NBTTagCompound formTag) {
        BuildJob.Form form = formTag == null ? null : BuildJob.Form.read(formTag);
        out.put(key + (form == null ? "" : " " + form.signature()), plan(key, form));
    }

    /** The plan of a key with the form its job carries (or none), at the fixed campus. */
    static BuildPlan.Plan plan(String key, BuildJob.Form form) {
        CampusPlan cp = new CampusPlan(Mix.seed(CX, Y0 + 2, CZ));
        return BuildJob.planFor(
            key,
            cp,
            CX,
            Y0,
            CZ,
            FX,
            FZ,
            CX,
            Y0 + 2,
            CZ,
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            false,
            null,
            form);
    }

    /**
     * The row of a plan by its name as {@link #planRows} writes it: {@code plan <key>}, then the form its job carries
     * as {@code BuildJob.Form#signature} gives it ({@code |f<pod>,<doors>}, {@code |s<deck>}, {@code |r<root>}).
     */
    public static Row planRow(String name) {
        IdExport.registerSpecs();
        String rest = name.substring("plan ".length());
        int space = rest.indexOf(' ');
        String key = space < 0 ? rest : rest.substring(0, space);
        BuildJob.Form form = null;
        if (space >= 0) {
            NBTTagCompound t = new NBTTagCompound();
            for (String f : rest.substring(space + 1)
                .split("[|]")) {
                if (f.isEmpty()) continue;
                char c = f.charAt(0);
                String v = f.substring(1);
                if (c == 'f') {
                    String[] pd = v.split(",");
                    t.setInteger("Pf", Integer.parseInt(pd[0]));
                    t.setInteger("Pd", Integer.parseInt(pd[1]));
                } else if (c == 's') t.setInteger("Sd", Integer.parseInt(v));
                else if (c == 'r') t.setString("Rs", v);
            }
            form = BuildJob.Form.read(t);
        }
        return row(name, plan(key, form));
    }

    /** The rows of every plan: its steps (cell, part, stage, group, kind) and its clearing columns. */
    public static List<Row> planRows() {
        List<Row> out = new ArrayList<>();
        for (Map.Entry<String, BuildPlan.Plan> e : plans().entrySet()) out.add(row("plan " + e.getKey(), e.getValue()));
        return out;
    }

    private static Row row(String name, BuildPlan.Plan p) {
        if (p == null) return new Row(name, 0L, 0L);
        Sum s = new Sum();
        s.add(p.steps.size())
            .add(p.stages);
        for (BuildPlan.Step st : p.steps) s.add(st.x)
            .add(st.y)
            .add(st.z)
            .add(st.part)
            .add(st.stage)
            .add(st.group)
            .add(st.kind);
        Sum c = new Sum();
        c.add(p.clear.size());
        for (BuildPlan.Column col : p.clear) c.add(col.x)
            .add(col.z)
            .add(col.yFrom)
            .add(col.yTo)
            .add(col.grade ? 1 : 0);
        return new Row(name, s.value(), c.value());
    }
}
