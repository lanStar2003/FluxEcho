package com.fluxecho.save;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fluxecho.campus.BuildJob;
import com.fluxecho.campus.Campus;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.city.CityModule;
import com.fluxecho.city.TileCityModule;
import com.fluxecho.core.MachineId;
import com.fluxecho.frame.BlockFrame;
import com.fluxecho.library.LibraryModule;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.FoldedZone;
import com.fluxecho.logic.LibraryShape;
import com.fluxecho.logic.LibraryUnits;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.NexusShape;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.logic.RoomPlan;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.UnitLayout;
import com.fluxecho.nexus.Manifests;
import com.fluxecho.nexus.TileMultiblock;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import cpw.mods.fml.relauncher.FMLInjectionData;

/**
 * The permanent IDs of the mod as the running code (and, where only a registration call names them, its sources) has
 * them, by category: registry names, tile entity ids, metas, part codes, sites, plan keys, step kinds and stages, the
 * job states and the module status ordinals, network kinds and channels, config keys, saved world data names, the
 * unit tables of every building, shape names, research and manifestation ids, quest keys and ids, ore names, lang keys
 * and GT machine ids (refactor §5.1 ①).
 * <p>
 * The same export runs twice: once, by {@code save.gen.GoldenSaveGen} in a work tree of the tag 0.16.0, to write the
 * frozen snapshot {@code src/test/resources/ids/0.16.0.json}; and in every build, by {@code IdFreezeTest}, on the
 * current code, which must still have every ID of the snapshot with the same value (new ones are fine). So this class
 * only uses what both the 0.16.0 code and the current code have. A value is what must not change about the ID: the
 * number of a code or a kind, the type of a config key, the name and slot count of a unit; an ID whose name is all
 * that matters has the empty value.
 */
public final class IdExport {

    /** The categories, in the order the snapshot lists them. */
    public static final String[] CATEGORIES = { "block", "item", "tile_entity", "meta", "part", "site", "plan_key",
        "step_kind", "plan_stage", "job_state", "module_status", "net", "config", "world_data", "unit", "shape",
        "research", "manifest", "quest_id", "quest_key", "ore", "lang_en_US", "lang_zh_CN", "gt_machine",
        "codex_category", "gate" };

    private IdExport() {}

    /** Every category of the running code, each sorted by key. */
    public static Map<String, Map<String, String>> export() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (String c : CATEGORIES) out.put(c, new TreeMap<>());
        registrations(out);
        metas(out.get("meta"));
        parts(out.get("part"));
        sites(out.get("site"));
        planKeys(out.get("plan_key"));
        stepKinds(out.get("step_kind"), out.get("plan_stage"));
        states(out.get("job_state"), out.get("module_status"));
        net(out.get("net"));
        config(out.get("config"), out.get("gt_machine"));
        buildings(out.get("unit"), out.get("shape"));
        research(out.get("research"), out.get("manifest"));
        quests(out.get("quest_id"), out.get("quest_key"));
        lang(out.get("lang_en_US"), "en_US");
        lang(out.get("lang_zh_CN"), "zh_CN");
        machines(out.get("gt_machine"));
        codex(out.get("codex_category"));
        gates(out.get("gate"));
        return out;
    }

    // ---- registrations read from the calls

    private static void registrations(Map<String, Map<String, String>> out) {
        for (SourceScan.Call c : SourceScan.calls("GameRegistry\\.registerBlock")) {
            List<String> a = c.args;
            String name = a.size() >= 3 && a.get(1)
                .endsWith(".class") ? a.get(2) : a.get(1);
            out.get("block")
                .put(c.src.mod() + ":" + SourceScan.eval(c.src, name), "");
        }
        for (SourceScan.Call c : SourceScan.calls("GameRegistry\\.registerItem")) out.get("item")
            .put(c.src.mod() + ":" + SourceScan.eval(c.src, c.args.get(1)), "");
        for (SourceScan.Call c : SourceScan.calls("GameRegistry\\.registerTileEntity")) out.get("tile_entity")
            .put(SourceScan.eval(c.src, c.args.get(1)), "");
        for (SourceScan.Call c : SourceScan.calls("OreDictionary\\.registerOre")) out.get("ore")
            .put(SourceScan.eval(c.src, c.args.get(0)), "");
        for (SourceScan.Source s : SourceScan.sources()) {
            if (!s.text.contains("extends WorldSavedData")) continue;
            String name = SourceScan.savedDataName(s);
            if (name != null) out.get("world_data")
                .put(name, "");
        }
    }

    /**
     * The tile entity classes the sources register, by id; a class the test cannot load (an optional mod's) is left
     * out.
     */
    public static Map<String, Class<?>> tileClasses() {
        Map<String, Class<?>> out = new TreeMap<>();
        for (SourceScan.Call c : SourceScan.calls("GameRegistry\\.registerTileEntity")) {
            String cls = c.args.get(0);
            if (!cls.endsWith(".class")) continue;
            String simple = cls.substring(0, cls.length() - ".class".length())
                .trim();
            String fqn = c.src.resolve(simple);
            Class<?> k = fqn == null ? null : SourceScan.load(fqn);
            if (k != null) out.put(SourceScan.eval(c.src, c.args.get(1)), k);
        }
        return out;
    }

    // ---- metas, part codes, sites, plans

    private static void metas(Map<String, String> m) {
        for (int i = 0; i < BlockFrame.NAMES.length; i++) m.put("fluxecho:frame@" + i, BlockFrame.NAMES[i]);
        for (int i = 0; i < Parts.DECK_NAMES.length; i++) m.put("fluxecho:deck@" + i, Parts.DECK_NAMES[i]);
        for (int i = 0; i < Parts.FITTING_NAMES.length; i++) m.put("fluxecho:fitting@" + i, Parts.FITTING_NAMES[i]);
        for (int i = 0; i < ModuleCores.KEYS.length; i++) m.put("fluxecho:module_core@" + i, ModuleCores.KEYS[i]);
        // the vis modules' kinds (an item of the Thaumcraft package; its kind enum loads without Thaumcraft)
        Class<?> kind = SourceScan.load("com.fluxecho.thaumcraft.ItemVisModule$Kind");
        if (kind != null && kind.isEnum()) for (Object k : kind.getEnumConstants())
            m.put("fluxecho:vis_module@" + ((Enum<?>) k).ordinal(), ((Enum<?>) k).name());
    }

    private static void parts(Map<String, String> m) {
        for (Field f : Parts.class.getFields()) {
            if (!constant(f) || f.getType() != int.class) continue;
            String n = f.getName();
            int v = intOf(f);
            // the counts of a family grow as metas are appended: not IDs
            if (n.endsWith("_TYPES") || n.equals("MODULE_CORES")) continue;
            if (n.startsWith("FR_")) m.put(n, String.valueOf(Parts.frame(v)));
            else if (n.startsWith("D_")) m.put(n, String.valueOf(Parts.deck(v)));
            else if (n.startsWith("F_")) m.put(n, String.valueOf(Parts.fitting(v)));
            else m.put(n, String.valueOf(v));
        }
        for (int i = 0; i < ModuleCores.KEYS.length; i++)
            m.put("module_core:" + ModuleCores.KEYS[i], String.valueOf(Parts.moduleCore(i)));
    }

    private static void sites(Map<String, String> m) {
        for (int s = 0; s < 128; s++) {
            SiteFamily f = SiteFamily.of(s);
            if (f != null) m.put(String.format("%03d", s), f.name());
        }
        m.put("ZENITH_SITE", String.valueOf(SiteFamily.ZENITH_SITE));
        m.put("ROOT_SITE", String.valueOf(SiteFamily.ROOT_SITE));
    }

    private static void planKeys(Map<String, String> m) {
        for (Class<?> c : new Class<?>[] { BuildPlan.class, BuildJob.class, SiteFamily.class })
            for (Field f : c.getFields()) if (constant(f) && f.getType() == String.class)
                m.put(c.getSimpleName() + "." + f.getName(), String.valueOf(get(f)));
        for (int p = 1; p <= NexusShape.PHASES; p++) m.put("establish@" + p, BuildPlan.establishKey(p));
        for (int p = 2; p <= NexusShape.PHASES; p++) m.put("grow@" + p, BuildPlan.growKey(p));
        for (int s = 0; s < 8; s++) m.put("retire@" + s, BuildPlan.retireKey(s));
        // a module job's key names its module and site: the value is what the current code parses out of it
        for (ModuleSpec spec : specs()) for (int site : spec.sites) m.put(
            spec.jobKey(site),
            ModuleSpecs.moduleOf(spec.jobKey(site)) + "@" + ModuleSpecs.siteOf(spec.jobKey(site)));
    }

    /**
     * Registers every spec of {@link #specs()} the test JVM has not registered yet, as the mod's init does: the
     * builder finds a module job's plan through the registry, so every test that makes a module plan calls this first
     * (whatever ran before it in the same JVM).
     */
    public static void registerSpecs() {
        for (ModuleSpec s : specs()) if (ModuleSpecs.get(s.key) == null) ModuleSpecs.register(s);
    }

    /** The specs of every building 0.16.0 builds, as the game registers them (the library's first). */
    public static List<ModuleSpec> specs() {
        List<ModuleSpec> out = new ArrayList<>();
        out.add(LibraryModule.spec());
        for (Method m : CityModule.class.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 0
                || m.getReturnType() != ModuleSpec.class) continue;
            try {
                out.add((ModuleSpec) m.invoke(null));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot make the spec " + m.getName(), e);
            }
        }
        out.sort((a, b) -> a.key.compareTo(b.key));
        return out;
    }

    private static void stepKinds(Map<String, String> kinds, Map<String, String> stages) {
        for (Field f : BuildPlan.class.getFields()) {
            if (!constant(f)) continue;
            String n = f.getName();
            if (f.getType() == byte.class) kinds.put(n, String.valueOf(get(f)));
            // a stage of a plan, by its number (a job saves the stage it is on); a plan's count of stages is not an ID
            else if (f.getType() == int.class && n.matches("[A-Z]_[A-Z_]+") && !n.endsWith("_STAGES"))
                stages.put("BuildPlan." + n, String.valueOf(intOf(f)));
        }
    }

    private static void states(Map<String, String> jobs, Map<String, String> status) {
        for (BuildState.State s : BuildState.State.values()) jobs.put("State." + s.name(), String.valueOf(s.ordinal()));
        for (BuildState.Pause p : BuildState.Pause.values()) jobs.put("Pause." + p.name(), String.valueOf(p.ordinal()));
        for (Campus.ModuleStatus s : Campus.ModuleStatus.values()) status.put(s.name(), String.valueOf(s.ordinal()));
    }

    // ---- network

    private static void net(Map<String, String> m) {
        for (String c : new String[] { "com.fluxecho.nexus.NexusNet", "com.fluxecho.codex.EchoNet",
            "com.fluxlite.net.Kinds" }) {
            Class<?> k = SourceScan.load(c);
            if (k == null) continue;
            for (Field f : k.getFields()) if (constant(f) && (f.getType() == int.class || f.getType() == byte.class))
                m.put(k.getSimpleName() + "." + f.getName(), String.valueOf(get(f)));
        }
        for (SourceScan.Call c : SourceScan.calls("NetworkRegistry\\.INSTANCE\\.newSimpleChannel")) {
            String channel = SourceScan.eval(c.src, c.args.get(0));
            m.put("channel:" + channel, "");
            for (SourceScan.Call r : SourceScan.calls("[\\w.]+\\.registerMessage")) {
                if (r.src != c.src || r.args.size() < 4) continue;
                String msg = r.args.get(1)
                    .replace(".class", "")
                    .trim();
                String side = r.args.get(3)
                    .replace("Side.", "")
                    .trim();
                m.put("message:" + channel + "/" + msg + "/" + side, r.args.get(2));
            }
        }
    }

    // ---- config

    private static final Pattern PROPERTY = Pattern.compile("\\s*([BIDS]):(\"[^\"]+\"|[^=<\\s]+)\\s*(=(.*)|<).*");

    private static void config(Map<String, String> m, Map<String, String> machines) {
        File dir = new File("build/tmp/ids-export");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("cannot make " + dir);
        try {
            Field home = FMLInjectionData.class.getDeclaredField("minecraftHome");
            home.setAccessible(true);
            if (home.get(null) == null) home.set(null, dir.getAbsoluteFile());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FML's Minecraft home cannot be set", e);
        }
        for (String[] mod : new String[][] { { "fluxecho", "com.fluxecho.Config" },
            { "fluxlite", "com.fluxlite.Config" }, { "fluxdepths", "com.fluxdepths.Config" } }) {
            File f = new File(dir, mod[0] + ".cfg");
            if (f.exists() && !f.delete()) throw new IllegalStateException("cannot delete " + f);
            try {
                Class.forName(mod[1])
                    .getMethod("load", File.class)
                    .invoke(null, f);
                List<String> category = new ArrayList<>();
                for (String l : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                    String t = l.trim();
                    if (t.startsWith("#") || t.isEmpty()) continue;
                    if (t.endsWith("{")) {
                        String c = t.substring(0, t.length() - 1)
                            .trim();
                        if (c.startsWith("\"")) c = c.substring(1, c.length() - 1);
                        category.add(c);
                        continue;
                    }
                    if (t.equals("}")) {
                        if (!category.isEmpty()) category.remove(category.size() - 1);
                        continue;
                    }
                    Matcher p = PROPERTY.matcher(l);
                    if (!p.matches() || category.isEmpty()) continue;
                    String key = p.group(2);
                    if (key.startsWith("\"")) key = key.substring(1, key.length() - 1);
                    String full = mod[0] + ":" + String.join(".", category) + "." + key;
                    m.put(
                        full,
                        p.group(1) + (p.group(3)
                            .equals("<") ? "[]" : ""));
                    // the first GT machine id of a mod is a config key whose default is an ID of the world's machines
                    if (key.equals("firstMachineId")) machines.put(
                        full,
                        p.group(4)
                            .trim());
                }
            } catch (ReflectiveOperationException | IOException e) {
                throw new IllegalStateException("cannot write the config of " + mod[0], e);
            }
        }
    }

    // ---- buildings: their shapes and unit tables

    /** The structure tiles the sources register, by tile entity id: the nexus, the library and the city modules. */
    public static Map<String, Class<? extends TileMultiblock>> structures() {
        Map<String, Class<? extends TileMultiblock>> out = new TreeMap<>();
        for (Map.Entry<String, Class<?>> e : tileClasses().entrySet()) {
            Class<?> k = e.getValue();
            if (TileMultiblock.class.isAssignableFrom(k) && !Modifier.isAbstract(k.getModifiers()))
                out.put(e.getKey(), k.asSubclass(TileMultiblock.class));
        }
        return out;
    }

    /** A new tile of the class (a structure tile always has a constructor without arguments). */
    public static TileMultiblock make(Class<? extends TileMultiblock> k) {
        try {
            return k.getConstructor()
                .newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot make " + k.getName(), e);
        }
    }

    /** The shapes a structure tile may stand as, in its own order ({@code TileMultiblock.shapes}). */
    @SuppressWarnings("unchecked")
    public static List<TileMultiblock.Shape> shapes(TileMultiblock t) {
        try {
            Method m = TileMultiblock.class.getDeclaredMethod("shapes");
            m.setAccessible(true);
            return (List<TileMultiblock.Shape>) m.invoke(t);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot ask the shapes of " + t.getClass(), e);
        }
    }

    /**
     * The shape a tile of the class stands (or last stood) as when its saved tag has no {@code Shape}: every module of
     * a single shape saved by 0.16.0 is such a tag (only a tile that was told its shape writes one), so this is the
     * shape all of them are read as.
     */
    public static String legacyShape(Class<? extends TileMultiblock> k) {
        TileMultiblock t = make(k);
        try {
            t.readFromNBT(new net.minecraft.nbt.NBTTagCompound());
        } catch (RuntimeException e) {
            return "unreadable: " + e;
        }
        return t.shapeName();
    }

    /** The unit tables of every building of the running code, as the snapshot's {@code unit} category has them. */
    public static Map<String, String> units() {
        Map<String, String> units = new TreeMap<>(), shapes = new TreeMap<>();
        buildings(units, shapes);
        return units;
    }

    private static void buildings(Map<String, String> units, Map<String, String> shapes) {
        for (Map.Entry<String, Class<? extends TileMultiblock>> e : structures().entrySet()) {
            String id = e.getKey();
            TileMultiblock probe = make(e.getValue());
            List<TileMultiblock.Shape> all = shapes(probe);
            for (int i = 0; i < all.size(); i++) shapes.put(id + ":" + all.get(i).name, "");
            // the shape a save without a shape name reads as: a formed module of one shape never wrote one
            shapes.put(id + "#legacy", legacyShape(e.getValue()));
            for (TileMultiblock.Shape s : all) {
                TileMultiblock t = make(e.getValue());
                t.expectShape(s.name);
                if (t instanceof TileCityModule cm) {
                    List<UnitLayout.Unit> l = cm.layout()
                        .units();
                    for (UnitLayout.Unit u : l) units.put(
                        String.format("%s/%s#%03d", id, s.name, u.index),
                        u.name + "|" + u.kind + "|" + u.slots + "|" + u.firstSlot);
                    units.put(id + "/" + s.name + ":units", String.valueOf(l.size()));
                    units.put(
                        id + "/" + s.name + ":handler",
                        String.valueOf(
                            cm.samples()
                                .getSlots()));
                }
            }
        }
        // the library keeps its books in one handler: the Archive's 78 bookcases of nine, the 0.9.2 hall's shelves
        for (LibraryUnits.Unit u : LibraryUnits.UNITS)
            units.put(String.format("fluxecho:library/main#%03d", u.index), u.call + "|bookcase|9|" + u.index * 9);
        units.put("fluxecho:library/main:units", String.valueOf(LibraryUnits.UNITS.size()));
        units.put("fluxecho:library/main:handler", String.valueOf(LibraryUnits.SLOTS));
        units.put("fluxecho:library/hall:handler", String.valueOf(LibraryShape.SHELVES.size()));
    }

    // ---- research, manifestations, quests

    private static void research(Map<String, String> research, Map<String, String> manifest) {
        for (ResearchTree.Node n : ResearchTree.all()) research.put(n.id, "");
        try {
            Method init = Manifests.class.getDeclaredMethod("init");
            init.setAccessible(true);
            if (Manifests.get("echo_crystal") == null) init.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot register the manifestations", e);
        }
        if (Manifests.get("eco_dome_core") == null) CityModule.postInit();
        if (Manifests.get("library_core") == null) LibraryModule.postInit();
        for (Manifests.Recipe r : Manifests.all()) manifest.put(r.id, "");
    }

    private static void quests(Map<String, String> ids, Map<String, String> keys) {
        Path root = Paths.get("src/main/resources/assets/fluxecho/quests/DefaultQuests");
        Map<String, String> shipped = new TreeMap<>();
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.filter(
                x -> x.toString()
                    .endsWith(".json"))
                .collect(Collectors.toList())) {
                String rel = root.relativize(p)
                    .toString()
                    .replace('\\', '/');
                // the layout files of a line repeat its quests' ids; the quests and the lines are their own files
                if (rel.startsWith("QuestLines/") && !rel.endsWith("/QuestLine.json")) continue;
                // the quest's own id is a field of the file's top object (its prerequisites name others inside)
                JsonObject o = new JsonParser().parse(new String(Files.readAllBytes(p), StandardCharsets.UTF_8))
                    .getAsJsonObject();
                for (String kind : new String[] { "questID", "questLineID" }) {
                    if (!o.has(kind + "High:4") || !o.has(kind + "Low:4")) continue;
                    String id = new UUID(
                        o.get(kind + "High:4")
                            .getAsLong(),
                        o.get(kind + "Low:4")
                            .getAsLong()).toString();
                    shipped.put(id, kind.equals("questLineID") ? "line" : "quest");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the quests", e);
        }
        ids.putAll(shipped);
        // the keys the ids are made from: every quoted key in the generator whose uuid5 is a shipped id
        UUID ns = null;
        List<String> literals = new ArrayList<>();
        try (Stream<Path> s = Files.walk(Paths.get("quests"))) {
            for (Path p : s.filter(
                x -> x.toString()
                    .endsWith(".py"))
                .sorted()
                .collect(Collectors.toList())) {
                String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                Matcher n = Pattern.compile("NS\\s*=\\s*uuid\\.UUID\\(\"([0-9a-f-]+)\"\\)")
                    .matcher(text);
                if (n.find()) ns = UUID.fromString(n.group(1));
                Matcher q = Pattern.compile("[\"']([a-z0-9_]+/[a-z0-9_/]+)[\"']")
                    .matcher(text);
                while (q.find()) literals.add(q.group(1));
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the quest generator", e);
        }
        if (ns == null) throw new IllegalStateException("no quest namespace in quests/*.py");
        for (String k : literals) {
            String id = uuid5(ns, k).toString();
            if (shipped.containsKey(id)) keys.put(k, id);
        }
    }

    /** The name-based UUID (version 5, SHA-1) Python's {@code uuid.uuid5} makes. */
    public static UUID uuid5(UUID ns, String name) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            byte[] b = new byte[16];
            long hi = ns.getMostSignificantBits(), lo = ns.getLeastSignificantBits();
            for (int i = 0; i < 8; i++) {
                b[i] = (byte) (hi >>> (56 - 8 * i));
                b[8 + i] = (byte) (lo >>> (56 - 8 * i));
            }
            sha.update(b);
            sha.update(name.getBytes(StandardCharsets.UTF_8));
            byte[] h = sha.digest();
            h[6] = (byte) ((h[6] & 0x0f) | 0x50);
            h[8] = (byte) ((h[8] & 0x3f) | 0x80);
            long m = 0, l = 0;
            for (int i = 0; i < 8; i++) {
                m = (m << 8) | (h[i] & 0xff);
                l = (l << 8) | (h[8 + i] & 0xff);
            }
            return new UUID(m, l);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- lang, machines, codex

    private static void lang(Map<String, String> m, String language) {
        for (String mod : new String[] { "fluxecho", "fluxlite", "fluxdepths" }) {
            Path p = Paths.get("src/main/resources/assets", mod, "lang", language + ".lang");
            try {
                for (String l : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    if (l.startsWith("#") || !l.contains("=")) continue;
                    String key = l.substring(0, l.indexOf('='))
                        .trim();
                    if (!key.isEmpty()) m.put(mod + ":" + key, "");
                }
            } catch (IOException e) {
                throw new IllegalStateException("cannot read " + p, e);
            }
        }
    }

    private static void machines(Map<String, String> m) {
        for (MachineId id : MachineId.values()) m.put(id.name(), id.offset + " " + id.key);
    }

    private static void codex(Map<String, String> m) {
        Class<?> k = SourceScan.load("com.fluxecho.codex.Categories");
        if (k == null) return;
        for (Field f : k.getFields())
            if (constant(f) && f.getType() == String.class) m.put(f.getName(), String.valueOf(get(f)));
    }

    /**
     * The folded zone's layout (where every gate's interior lies in the interior dimension) and the ids of the
     * interior room templates a gate's NBT names.
     */
    private static void gates(Map<String, String> m) {
        for (Field f : FoldedZone.class.getFields())
            if (constant(f) && f.getType() == int.class) m.put("FoldedZone." + f.getName(), String.valueOf(intOf(f)));
        for (RoomPlan.Template t : RoomPlan.TEMPLATES) m.put("room:" + t.id, "");
        m.put("RoomPlan.DEFAULT", RoomPlan.DEFAULT);
    }

    // ---- reflection helpers

    private static boolean constant(Field f) {
        int mod = f.getModifiers();
        return Modifier.isStatic(mod) && Modifier.isFinal(mod) && Modifier.isPublic(mod);
    }

    private static Object get(Field f) {
        try {
            return f.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int intOf(Field f) {
        return ((Number) get(f)).intValue();
    }

    // ---- the snapshot file

    /** The snapshot's text: a JSON object with its version and commit, then every category, keys sorted. */
    public static String json(String version, String commit, Map<String, Map<String, String>> ids) {
        StringBuilder b = new StringBuilder();
        b.append("{\n  \"version\": ")
            .append(quote(version))
            .append(",\n  \"commit\": ")
            .append(quote(commit))
            .append(
                ",\n  \"note\": \"Every permanent ID of this version, exported from its own code by save.IdExport (refactor 5.1). Frozen: IdFreezeTest checks that the current code still has each one with the same value.\",\n  \"ids\": {");
        boolean firstCat = true;
        for (Map.Entry<String, Map<String, String>> c : ids.entrySet()) {
            b.append(firstCat ? "\n" : ",\n")
                .append("    ")
                .append(quote(c.getKey()))
                .append(": {");
            firstCat = false;
            boolean first = true;
            for (Map.Entry<String, String> e : c.getValue()
                .entrySet()) {
                b.append(first ? "\n" : ",\n")
                    .append("      ")
                    .append(quote(e.getKey()))
                    .append(": ")
                    .append(quote(e.getValue()));
                first = false;
            }
            b.append(first ? "}" : "\n    }");
        }
        return b.append("\n  }\n}\n")
            .toString();
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\')
                .append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"')
            .toString();
    }

    public static void write(Path file, String version, String commit, Map<String, Map<String, String>> ids)
        throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(json(version, commit, ids));
        }
    }

    /** The categories of a snapshot file, in its order. */
    public static Map<String, Map<String, String>> read(Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Object> top = new Gson().fromJson(r, new TypeToken<Map<String, Object>>() {}.getType());
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> ids = (Map<String, Map<String, String>>) top.get("ids");
            Map<String, Map<String, String>> out = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, String>> e : ids.entrySet())
                out.put(e.getKey(), new TreeMap<>(e.getValue()));
            return out;
        }
    }
}
