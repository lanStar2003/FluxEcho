package com.fluxecho.save.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fluxecho.campus.BuildJob;
import com.fluxecho.campus.Campus;
import com.fluxecho.campus.CampusRegistry;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.campus.RecycleOverflow;
import com.fluxecho.city.TileCityModule;
import com.fluxecho.codex.EchoLedger;
import com.fluxecho.library.LibraryVault;
import com.fluxecho.library.TileLibrary;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.Mix;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.RootShape;
import com.fluxecho.nexus.ItemBlockNexusCore;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.research.ResearchData;
import com.fluxecho.save.Golden;
import com.fluxecho.save.GoldenTiles;
import com.fluxecho.save.GoldenWorld;
import com.fluxecho.save.IdExport;
import com.fluxecho.save.ShapeCrc;
import com.gtnewhorizons.modularui.api.forge.ItemStackHandler;

/**
 * The one-off generator of the 0.16.0 golden saves and ID snapshot (refactor §6.4, card F02). It is written against
 * the API of 0.16.0, copied into a work tree of the tag 0.16.0 and run there once:
 *
 * <pre>
 * git -C D:/Code/FluxEcho worktree add --detach D:/Code/FluxEcho-wt/golden-0.16.0 0.16.0
 * (copy src/test/java/com/fluxecho/save/** into that tree)
 * ./gradlew --offline test --tests '*GoldenSaveGen*' -PgoldenGen -Dorg.gradle.workers.max=2
 * </pre>
 *
 * Everything lands under {@code build/golden/}: the ID snapshot ({@code ids/0.16.0.json}), the saves
 * ({@code saves/0.16.0/**}, copied into {@code src/test/resources/saves/0.16.0/} with a {@code MANIFEST.txt}) and the
 * checksums {@code OldShapesCrcTest} holds ({@code shapes-crc.txt}). Every tag in the saves is what the 0.16.0 code
 * itself wrote ({@code writeToNBT} of its tiles and saved data): the generator only sets state up through the code's
 * own calls (or a tag the code reads first) and lets it write.
 * <p>
 * Tagged {@code golden-gen}: no build runs it (the current code has moved on, and the files are frozen). It stays in
 * the repository, beside the tests that read its output, as the record of how they were made.
 */
@Tag("golden-gen")
class GoldenSaveGen {

    private static final String VERSION = "0.16.0", COMMIT = "1cc50f2";
    private static final Path OUT = Paths.get("build/golden");
    private static final long NOW = 2_468_000L;

    @BeforeAll
    static void setUp() {
        GoldenWorld.setUp();
        IdExport.registerSpecs();
        for (Map.Entry<String, Class<? extends TileMultiblock>> e : IdExport.structures()
            .entrySet()) GoldenWorld.map(e.getValue(), null, e.getKey());
        GoldenTiles.map();
        GoldenWorld.sampleItem();
    }

    @AfterAll
    static void tearDown() {
        GoldenWorld.tearDown();
    }

    // ---- the ID snapshot and the checksums

    @Test
    void ids() throws IOException {
        IdExport.write(OUT.resolve("ids/" + VERSION + ".json"), VERSION, COMMIT, IdExport.export());
    }

    @Test
    void shapeChecksums() throws IOException {
        Path f = OUT.resolve("shapes-crc.txt");
        Files.createDirectories(f.getParent());
        try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
            w.write("    // shapes: geometry and characters, parts, units (" + VERSION + ", " + COMMIT + ")\n");
            for (ShapeCrc.Row r : ShapeCrc.shapes()) w.write("        " + r + ",\n");
            w.write("    // plans: steps, clearing columns\n");
            for (ShapeCrc.Row r : ShapeCrc.planRows()) w.write("        " + r + ",\n");
        }
    }

    // ---- the four typical campuses

    @Test
    void campuses() throws IOException {
        for (Golden.Tier t : Golden.TIERS) new Campus016(t).build()
            .save();
    }

    /** A typical campus raised by the real plans, its tiles formed by the real check, then saved. */
    private static final class Campus016 {

        final Golden.Tier t;
        final GoldenWorld g = new GoldenWorld();
        final GoldenTiles.Nexus n = new GoldenTiles.Nexus();
        int[] c;
        int fx, fz;
        /** The recorded sites {x, y, z} and their module keys. */
        final Map<Integer, int[]> sites = new TreeMap<>();
        final Map<Integer, String> kinds = new TreeMap<>();
        final Set<String> laid = new LinkedHashSet<>();
        /** The module tiles standing, by site. */
        final Map<Integer, TileMultiblock> tiles = new TreeMap<>();
        BuildJob job;
        final List<BuildJob> queue = new ArrayList<>();
        final List<String> built = new ArrayList<>();

        Campus016(Golden.Tier t) {
            this.t = t;
        }

        Campus016 build() {
            n.done.addAll(Arrays.asList(t.research));
            setWorldTime(g, NOW);
            g.place(n, new int[] { t.x, t.y0 + 2, t.z, t.facing }, Golden.OWNER);
            c = n.centre();
            assertEquals(t.y0, c[1]);
            fx = n.front().offsetX;
            fz = n.front().offsetZ;
            sync();
            // phase I: the nexus and its forum, the Archive, the Eco Dome
            lay(
                BuildJob.establish(
                    n.campus()
                        .keepOut()));
            GoldenWorld.recheck(n);
            assertTrue(n.formed(), t.name + ": the nexus stands " + n.firstWrong());
            module("library", 4);
            if (t.phase == 1) {
                inProgress("eco_dome", 2, 0.5);
                return this;
            }
            module("eco_dome", 2);
            // phase II: the stage, the third hall, the pavilions, the gate, the pod moored and the Tidal Well
            grow(2);
            module("dual_foil_vault", 6);
            module("mana_fountain", 1);
            module("arcane_court", 7);
            module("hunting_ground", 3);
            module("blood_covenant", 5);
            module("resonance_gate", 0);
            module("dome_generator", 8);
            module("root", 9);
            if (t.phase == 2) {
                repairInProgress("eco_dome", 2);
                return this;
            }
            // phase III: the outer ring laid, the Sophon Array and the Holo Sand Table
            grow(3);
            BuildJob outer = BuildJob.outer(
                n.campus()
                    .outerKeepOut(),
                Collections.emptyList());
            lay(outer);
            laid.add(BuildPlan.OUTER);
            sync();
            module("sophon_array", 11);
            if (t.phase == 3) {
                inProgress("holo_table", 17, 0.3);
                return this;
            }
            module("holo_table", 17);
            // phase IV, as the user's save: the pod released and floating, the sky ring on S0 to S6, the Deep Root
            grow(4);
            GoldenTiles.Pod pod = (GoldenTiles.Pod) tiles.get(8);
            int datum = DomeGeneratorShape.podDatum(pod.yCoord, c[1]);
            lay(BuildJob.release(datum));
            GoldenWorld.recheck(pod);
            assertTrue(pod.formed(), t.name + ": the pod floats " + pod.firstWrong());
            assertEquals(DomeGeneratorShape.FLOATING, pod.shapeName());
            sky("weather_station", 30, datum);
            sky("sky_sails", 32, datum);
            sky("observatory", 34, datum);
            sky("droplet_hangar", 36, datum);
            RootShape.Deep deep = RootShape.deepFor(c[1], RootShape.forBase(c[1]));
            BuildJob deepen = BuildJob.deepen(ModuleSpecs.get(RootShape.KEY), deep.name);
            lay(deepen);
            TileMultiblock root = tiles.get(9);
            GoldenWorld.recheck(root);
            assertTrue(root.formed(), t.name + ": the deep root " + root.firstWrong());
            assertEquals(deep.name, root.shapeName());
            // a repair of the weather station waits to be started, another is queued
            job = BuildJob.repairModule(ModuleSpecs.get("weather_station"), 30);
            queue.add(BuildJob.repairModule(ModuleSpecs.get("observatory"), 34));
            return this;
        }

        /** The nexus's campus as the tag its tile reads: active, the sites, the grounds, the jobs. */
        NBTTagCompound campusTag() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("St", "active");
            tag.setBoolean("Hint", true);
            tag.setBoolean("Skip", false);
            NBTTagList s = new NBTTagList();
            for (Map.Entry<Integer, int[]> e : sites.entrySet()) {
                NBTTagCompound site = new NBTTagCompound();
                site.setInteger("S", e.getKey());
                site.setIntArray("P", e.getValue());
                site.setString("K", kinds.get(e.getKey()));
                s.appendTag(site);
            }
            tag.setTag("Sites", s);
            if (job != null) tag.setTag("Job", job.write());
            NBTTagList q = new NBTTagList();
            for (BuildJob j : queue) q.appendTag(j.write());
            tag.setTag("Queue", q);
            tag.setInteger("Pv", BuildJob.GRADE_V);
            tag.setBoolean("Fb", false);
            if (!laid.isEmpty()) {
                NBTTagList gl = new NBTTagList();
                for (String k : laid) gl.appendTag(new NBTTagString(k));
                tag.setTag("Gl", gl);
            }
            NBTTagCompound tile = new NBTTagCompound();
            tile.setTag("Campus", tag);
            return tile;
        }

        /** Gives the campus what the generator knows so far. */
        void sync() {
            n.campus()
                .readNBT(campusTag());
        }

        BuildPlan.Plan plan(BuildJob j) {
            return BuildJob.planFor(
                j.planKey(),
                new CampusPlan(Mix.seed(n.xCoord, n.yCoord, n.zCoord)),
                c[0],
                c[1],
                c[2],
                fx,
                fz,
                n.xCoord,
                n.yCoord,
                n.zCoord,
                j.keepOut(),
                j.neighbours(),
                j.regainFrom(),
                j.retiresOuter(),
                j.patch(),
                j.form());
        }

        BuildPlan.Plan lay(BuildJob j) {
            BuildPlan.Plan p = plan(j);
            assertNotNull(p, t.name + ": no plan for " + j.planKey());
            g.lay(p);
            return p;
        }

        void grow(int phase) {
            lay(BuildJob.grow(phase));
            n.regrow(phase);
            GoldenWorld.recheck(n);
            assertTrue(n.formed(), t.name + ": the nexus of phase " + phase + " " + n.firstWrong());
            assertEquals("phase" + phase, n.shapeName());
        }

        /** Raises a module by its job's real plan, commissions its controller and checks it forms. */
        TileMultiblock module(String key, int site) {
            return raise(BuildJob.module(ModuleSpecs.get(key), site), key, site);
        }

        void sky(String key, int site, int deck) {
            BuildJob j = BuildJob.module(ModuleSpecs.get(key), site);
            // the deck the job carries (BuildJob.deckAt): the pod's floor that stood when it was offered
            NBTTagCompound tag = j.write();
            tag.setInteger("Sd", deck);
            raise(BuildJob.read(tag), key, site);
            TileMultiblock pod = tiles.get(8);
            GoldenWorld.recheck(pod);
            assertTrue(pod.formed(), t.name + ": the pod with the door to " + site + " open " + pod.firstWrong());
        }

        TileMultiblock raise(BuildJob j, String key, int site) {
            ModuleSpec spec = ModuleSpecs.get(key);
            lay(j);
            int[] at = controller(j);
            TileMultiblock tile = make(key);
            g.place(tile, at, Golden.OWNER);
            tile.expectShape(j.shapeFor(spec, c[1]));
            GoldenWorld.recheck(tile);
            assertTrue(tile.formed(), t.name + ": " + key + "@" + site + " " + tile.firstWrong());
            tiles.put(site, tile);
            sites.put(site, new int[] { at[0], at[1], at[2] });
            kinds.put(site, spec.key);
            built.add(key);
            sync();
            return tile;
        }

        TileMultiblock make(String key) {
            if (key.equals(DomeGeneratorShape.KEY)) {
                GoldenTiles.Pod p = new GoldenTiles.Pod();
                p.over = n;
                return p;
            }
            if (key.equals(RootShape.KEY)) {
                GoldenTiles.Root r = new GoldenTiles.Root();
                r.over = n;
                return r;
            }
            Class<? extends TileMultiblock> k = IdExport.structures()
                .get("fluxecho:" + key);
            assertNotNull(k, key);
            return IdExport.make(k);
        }

        /** Where the campus commissions the job's controller ({@code Campus.moduleController}). */
        int[] controller(BuildJob j) {
            try {
                Method m = Campus.class.getDeclaredMethod("moduleController", BuildJob.class);
                m.setAccessible(true);
                return (int[]) m.invoke(n.campus(), j);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        /**
         * A module job the builder is part way through: its plan's first steps down (and done), the job building,
         * started by the owner, at the stage of the step it is on; its controller not yet placed.
         */
        void inProgress(String key, int site, double share) {
            BuildJob j = BuildJob.module(ModuleSpecs.get(key), site);
            BuildPlan.Plan p = plan(j);
            int k = (int) (p.steps.size() * share);
            for (int i = 0; i < k; i++) {
                BuildPlan.Step s = p.steps.get(i);
                if (BuildPlan.digs(s.kind)) continue;
                g.set(s.x, s.y, s.z, s.kind == BuildPlan.AIR ? -1 : s.part);
            }
            set(j, "state", BuildState.State.BUILDING);
            set(j, "stage", p.steps.get(k).stage);
            set(j, "started", true);
            set(j, "startedBy", Golden.OWNER);
            set(j, "touched", true);
            set(j, "projectedAt", NOW - 6000);
            BitSet done = (BitSet) get(j, "done");
            done.set(0, k);
            job = j;
        }

        /** A repair of a standing module, paused for materials a few steps in, and a growth declined. */
        void repairInProgress(String key, int site) {
            BuildJob j = BuildJob.repairModule(ModuleSpecs.get(key), site);
            set(j, "state", BuildState.State.PAUSED);
            set(j, "pause", BuildState.Pause.MATERIALS);
            set(j, "started", true);
            set(j, "startedBy", Golden.OWNER);
            set(j, "touched", true);
            BitSet done = (BitSet) get(j, "done");
            done.set(0, 40);
            done.set(100, 140);
            job = j;
            queue.add(BuildJob.grow(3));
        }

        /** Saves the nexus, the module tiles, the world and what the generator knows of them. */
        void save() throws IOException {
            // every tile as it stands once everything is down
            GoldenWorld.recheck(n);
            assertTrue(n.formed(), t.name + ": the nexus at the end " + n.firstWrong());
            for (Map.Entry<Integer, TileMultiblock> e : tiles.entrySet()) {
                GoldenWorld.recheck(e.getValue());
                assertTrue(
                    e.getValue()
                        .formed(),
                    t.name + ": site "
                        + e.getKey()
                        + " at the end "
                        + e.getValue()
                            .firstWrong());
            }
            // the modules' samples: every unit full, the last unit selected
            NBTTagCompound modules = new NBTTagCompound();
            int b = 0;
            for (Map.Entry<Integer, TileMultiblock> e : tiles.entrySet()) {
                TileMultiblock tile = e.getValue();
                NBTTagCompound tag = new NBTTagCompound();
                tile.writeToNBT(tag);
                fill(
                    tile,
                    tag,
                    100 * (Arrays.asList(Golden.TIERS)
                        .indexOf(t) + 1) + b++);
                tile.readFromNBT(tag);
                NBTTagCompound out = new NBTTagCompound();
                tile.writeToNBT(out);
                modules.setTag(String.valueOf(e.getKey()), out);
            }
            // the nexus: its campus, its inventory, a bound team, a research and a manifestation running
            NBTTagCompound tag = new NBTTagCompound();
            n.writeToNBT(tag);
            NBTTagCompound campus = campusTag().getCompoundTag("Campus");
            ledger(campus);
            tag.setTag("Campus", campus);
            ItemStackHandler inv = new ItemStackHandler(8);
            for (int i = 0; i < 6; i += 2) inv.setStackInSlot(i, Golden.sample(90, i));
            inv.setStackInSlot(6, Golden.sample(90, 6));
            tag.setTag("Inv", inv.serializeNBT());
            NBTTagList bound = new NBTTagList();
            bound.appendTag(new NBTTagString(Golden.BOUND.toString()));
            tag.setTag("Bound", bound);
            tag.setBoolean("OpenBinding", true);
            tag.setString("Research", t.phase < 4 ? "phase_" + (t.phase + 1) : "");
            tag.setDouble("ResearchDone", t.phase < 4 ? 1234.5 * t.phase : 0);
            tag.setString("ResearchTeam", t.team.toString());
            tag.setString("Manifest", "library_card");
            tag.setInteger("ManifestTicks", 37);
            tag.setInteger("ManifestMax", 200);
            tag.setTag(
                "Pending",
                Golden.sample(91, t.phase)
                    .writeToNBT(new NBTTagCompound()));
            tag.setBoolean("Holo", t.phase % 2 == 0);
            n.readFromNBT(tag);
            NBTTagCompound nexus = new NBTTagCompound();
            n.writeToNBT(nexus);

            NBTTagCompound info = new NBTTagCompound();
            info.setString("Tier", t.name);
            info.setInteger("Phase", t.phase);
            info.setString("Team", t.team.toString());
            info.setIntArray("Centre", c);
            NBTTagList research = new NBTTagList();
            for (String r : t.research) research.appendTag(new NBTTagString(r));
            info.setTag("Research", research);
            NBTTagList names = new NBTTagList();
            for (String k : built) names.appendTag(new NBTTagString(k));
            info.setTag("Built", names);
            info.setInteger("Cells", g.parts.size());
            // the shape every tile stood as (a tile of one shape saves no name for it)
            NBTTagCompound shapes = new NBTTagCompound();
            shapes.setString("nexus", n.shapeName());
            for (Map.Entry<Integer, TileMultiblock> e : tiles.entrySet()) shapes.setString(
                String.valueOf(e.getKey()),
                e.getValue()
                    .shapeName());
            info.setTag("Shapes", shapes);

            Path dir = t.dir(Golden.OUT);
            Golden.write(dir.resolve("nexus.dat"), nexus);
            Golden.write(dir.resolve("modules.dat"), modules);
            Golden.write(dir.resolve("world.dat"), g.write());
            Golden.write(dir.resolve("info.dat"), info);
            if (t.name.equals("luv")) {
                // the core of the user's nexus, broken: it carries the campus, its credit and the manifestation
                NBTTagCompound core = ItemBlockNexusCore.carry(n);
                Golden.write(Golden.OUT.resolve("items/nexus_core.dat"), core);
            }
            for (TileMultiblock tile : tiles.values()) GoldenWorld.clearCells(tile);
            GoldenWorld.clearCells(n);
        }

        /** The campus ledger: credit in raw materials and in parts, an item in the intake, the recycle store. */
        void ledger(NBTTagCompound campus) {
            NBTTagCompound l = new NBTTagCompound(), raw = new NBTTagCompound(), parts = new NBTTagCompound();
            // raw credit by ingredient spec, in 1/65536 of an item (PartRecipes.UNIT)
            raw.setLong("ore:plateStainlessSteel", 65_536L * 4096 * t.phase + 12_345);
            raw.setLong("ore:circuitHV", 65_536L * 64 * t.phase);
            raw.setLong("ore:gemEchoCrystal", 65_536L * 3);
            parts.setInteger(String.valueOf(Parts.frame(Parts.FR_BASE)), 512 * t.phase);
            parts.setInteger(String.valueOf(Parts.deck(Parts.D_DECK)), 2048 + t.phase);
            parts.setInteger(String.valueOf(Parts.fitting(Parts.F_LIFT)), 3);
            parts.setInteger(String.valueOf(Parts.LIBRARY_CORE), 1);
            l.setTag("Raw", raw);
            l.setTag("Parts", parts);
            campus.setTag("Ledger", l);
            ItemStackHandler intake = new ItemStackHandler(1);
            intake.setStackInSlot(0, Golden.sample(92, t.phase));
            campus.setTag("BuildIn", intake.serializeNBT());
            NBTTagList recycle = new NBTTagList();
            for (int i = 0; i < 5; i++) {
                NBTTagCompound e = new NBTTagCompound();
                e.setTag(
                    "K",
                    Golden.sample(93, i)
                        .writeToNBT(new NBTTagCompound()));
                e.getCompoundTag("K")
                    .removeTag("Count");
                e.setLong("N", 1000L * (i + 1) + t.phase);
                recycle.appendTag(e);
            }
            campus.setTag("Recycle", recycle);
            NBTTagCompound declined = new NBTTagCompound();
            declined.setLong(t.phase < 4 ? BuildPlan.growKey(t.phase + 1) : "sky_sails", NOW + 24000);
            campus.setTag("Declined", declined);
        }
    }

    /** Fills every slot a module keeps with the building's samples and selects its last unit. */
    private static void fill(TileMultiblock tile, NBTTagCompound tag, int building) {
        if (tile instanceof TileCityModule m) {
            tag.setTag(
                "Samples",
                Golden.fullHandler(
                    building,
                    m.samples()
                        .getSlots()));
            tag.setInteger("Unit", Math.max(0, m.unitCount() - 1));
        } else if (tile instanceof TileLibrary) {
            tag.setTag("Samples", Golden.fullHandler(building, TileLibrary.SLOTS));
            ItemStackHandler desk = new ItemStackHandler(2);
            desk.setStackInSlot(0, Golden.sample(building, 9000));
            tag.setTag("Desk", desk.serializeNBT());
            tag.setInteger("Selected", 77);
            tag.setInteger("Unit", 41);
            tag.setBoolean("Refilled", true);
        }
    }

    // ---- every module in every shape it may stand as, the library both ways

    @Test
    void everyShape() throws IOException {
        NBTTagCompound all = new NBTTagCompound();
        int b = 500, x = 0;
        for (Map.Entry<String, Class<? extends TileMultiblock>> e : IdExport.structures()
            .entrySet()) {
            NBTTagCompound shapes = new NBTTagCompound();
            for (TileMultiblock.Shape s : IdExport.shapes(IdExport.make(e.getValue()))) {
                TileMultiblock tile = IdExport.make(e.getValue());
                tile.xCoord = 64 * x++;
                tile.yCoord = 70;
                tile.zCoord = -32 * x;
                tile.place(Golden.OWNER, "golden", 2 + x % 4);
                tile.expectShape(s.name);
                if (tile instanceof com.fluxecho.nexus.TileNexus nx) nx.regrow(Integer.parseInt(s.name.substring(5)));
                NBTTagCompound tag = new NBTTagCompound();
                tile.writeToNBT(tag);
                fill(tile, tag, b++);
                tile.readFromNBT(tag);
                NBTTagCompound out = new NBTTagCompound();
                tile.writeToNBT(out);
                assertEquals(s.name, out.getString("Shape"), e.getKey());
                shapes.setTag(s.name, out);
            }
            all.setTag(e.getKey(), shapes);
        }
        Golden.write(Golden.OUT.resolve("modules/every_shape.dat"), all);
    }

    // ---- the saved world data

    @Test
    void savedData() throws IOException {
        ResearchData research = new ResearchData("fluxecho_research");
        for (Golden.Tier t : Golden.TIERS) {
            for (String id : t.research) research.grant(t.team, id);
            research.pressed(t.team, "bee:Forestry.speciesForest", NOW - t.phase * 100L);
        }
        research.grant(Golden.BOUND, "anchor");
        write("fluxecho_research", research);

        EchoLedger ledger = new EchoLedger("fluxecho_ledger");
        String[][] records = { { "bee", "Forestry.speciesForest" }, { "bee", "Forestry.speciesMeadows" },
            { "tree", "forestry.treeOak" }, { "aspect", "aer" }, { "mob", "Zombie" }, { "crop", "wheat" },
            { "infusion", "thaumcraft:ItemThaumometer" }, { "orb", "AWWayofTime:weakBloodOrb" } };
        for (Golden.Tier t : Golden.TIERS)
            for (int i = 0; i < t.phase * 2; i++) ledger.record(t.team, records[i][0], records[i][1]);
        write("fluxecho_ledger", ledger);

        NBTTagCompound vault = new NBTTagCompound();
        NBTTagList teams = new NBTTagList();
        for (Golden.Tier t : Golden.TIERS) {
            NBTTagCompound team = new NBTTagCompound();
            team.setString("Team", t.team.toString());
            NBTTagList items = new NBTTagList();
            for (int i = 0; i < 20 * t.phase; i++) items.appendTag(
                Golden.sample(80 + t.phase, i)
                    .writeToNBT(new NBTTagCompound()));
            team.setTag("Items", items);
            teams.appendTag(team);
        }
        vault.setTag("Teams", teams);
        LibraryVault v = new LibraryVault("fluxecho_vault");
        v.readFromNBT(vault);
        write("fluxecho_vault", v);

        NBTTagCompound overflow = new NBTTagCompound();
        NBTTagList over = new NBTTagList();
        NBTTagCompound e = new NBTTagCompound();
        e.setString("T", Golden.TIERS[3].team.toString());
        NBTTagList l = new NBTTagList();
        for (int i = 0; i < 3; i++) {
            NBTTagCompound k = new NBTTagCompound();
            NBTTagCompound kind = Golden.sample(94, i)
                .writeToNBT(new NBTTagCompound());
            kind.removeTag("Count");
            k.setTag("K", kind);
            k.setLong("N", 7_000_000_000L + i);
            l.appendTag(k);
        }
        e.setTag("L", l);
        over.appendTag(e);
        overflow.setTag("Teams", over);
        RecycleOverflow r = new RecycleOverflow("fluxecho_recycle");
        r.readFromNBT(overflow);
        write("fluxecho_recycle", r);

        NBTTagCompound registry = new NBTTagCompound();
        NBTTagList campuses = new NBTTagList();
        for (Golden.Tier t : Golden.TIERS) {
            NBTTagCompound c = new NBTTagCompound();
            c.setIntArray("E", new int[] { 0, t.x, t.y0 + 2, t.z, t.phase >= 3 ? 220 : 90 });
            if (t.phase >= 3) c.setInteger("Q", 5);
            campuses.appendTag(c);
        }
        registry.setTag("Campuses", campuses);
        registry.setIntArray("Ports", new int[] { 0, 130, 65, -330 });
        CampusRegistry reg = new CampusRegistry("fluxecho_campus");
        reg.readFromNBT(registry);
        write("fluxecho_campus", reg);

        // a light gate at the HV campus and its interior's gate in the folded zone, paired, the room a garden
        NBTTagCompound gates = new NBTTagCompound();
        gates.setInteger("nextId", 3);
        gates.setInteger("nextPlot", 1);
        NBTTagList list = new NBTTagList();
        for (int i = 1; i <= 2; i++) {
            NBTTagCompound gate = new NBTTagCompound();
            gate.setInteger("id", i);
            gate.setInteger("dim", i == 1 ? 0 : -1024);
            gate.setInteger("x", i == 1 ? 140 : 1 << 20);
            gate.setInteger("y", i == 1 ? 65 : 64);
            gate.setInteger("z", i == 1 ? -300 : 7);
            gate.setByte("facing", (byte) (i == 1 ? 3 : 2));
            gate.setInteger("plot", 0);
            gate.setBoolean("inside", i == 2);
            gate.setInteger("partner", 3 - i);
            gate.setString("room", "garden");
            list.appendTag(gate);
        }
        gates.setTag("gates", list);
        com.fluxecho.gate.GateRegistry gr = new com.fluxecho.gate.GateRegistry("fluxecho_gates");
        gr.readFromNBT(gates);
        write("fluxecho_gates", gr);
    }

    private static void write(String name, net.minecraft.world.WorldSavedData d) throws IOException {
        NBTTagCompound data = new NBTTagCompound();
        d.writeToNBT(data);
        // as the game's MapStorage writes it: the data under "data"
        NBTTagCompound file = new NBTTagCompound();
        file.setTag("data", data);
        Golden.write(Golden.OUT.resolve("data/" + name + ".dat"), file);
    }

    /** The world's clock (the test world keeps it in a field of its own), so a tile's formed time is a real one. */
    private static void setWorldTime(GoldenWorld g, long time) {
        try {
            Field f = g.world.getClass()
                .getDeclaredField("time");
            f.setAccessible(true);
            f.setLong(g.world, time);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- reflection on the job's own fields (the builder sets them as it works)

    private static void set(Object o, String name, Object v) {
        try {
            Field f = o.getClass()
                .getDeclaredField(name);
            f.setAccessible(true);
            f.set(o, v);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(name, e);
        }
    }

    private static Object get(Object o, String name) {
        try {
            Field f = o.getClass()
                .getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(name, e);
        }
    }
}
