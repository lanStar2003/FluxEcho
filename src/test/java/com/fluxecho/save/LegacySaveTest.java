package com.fluxecho.save;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fluxecho.campus.CampusRegistry;
import com.fluxecho.campus.RecycleOverflow;
import com.fluxecho.city.TileCityModule;
import com.fluxecho.codex.EchoLedger;
import com.fluxecho.library.LibraryVault;
import com.fluxecho.library.TileLibrary;
import com.fluxecho.nexus.ItemBlockNexusCore;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.research.ResearchData;
import com.gtnewhorizons.modularui.api.forge.IItemHandler;

/**
 * The second guard of old saves (refactor §5.1 ②): the golden saves of 0.16.0, real NBT the 0.16.0 code wrote
 * ({@code saves/0.16.0}, made once by {@code save.gen.GoldenSaveGen} in a work tree of the tag), read by the current
 * code. Four typical campuses (HV, EV, IV and the user's LuV save of sixteen buildings, the sky ring on S0 to S6 and
 * the pod floating) come back with the same shape names, the same samples in the same slots, the same sites and the
 * same jobs at the same progress; laid into a test world from their saved cells, every building forms again as the
 * shape it stood as. Every module in every shape it may stand as, the Archive's 702 books, the saved world data and the
 * core item of the LuV nexus read back the same. Written again, every key the 0.16.0 tag had is still there.
 * <p>
 * The files are checked against their {@code MANIFEST.txt} first: a golden file is never edited, only made again by
 * the generator from the tag (which is never needed: 0.16.0 does not change).
 */
@Tag("slow")
class LegacySaveTest {

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

    // ---- the files

    @Test
    void theGoldenFilesAreTheOnesMadeFrom016() throws IOException {
        List<String> lines = Files.readAllLines(Golden.SAVES.resolve("MANIFEST.txt"), StandardCharsets.UTF_8);
        assertTrue(lines.contains("tag: 0.16.0"), "made from the tag 0.16.0");
        assertTrue(lines.contains("commit: 1cc50f2"), "at its commit");
        Map<String, String> listed = new TreeMap<>();
        for (String l : lines) {
            String[] p = l.split("\\s+");
            if (p.length == 2 && p[0].matches("[0-9a-f]{64}")) listed.put(p[1], p[0]);
        }
        List<String> files;
        try (Stream<Path> s = Files.walk(Golden.SAVES)) {
            files = s.filter(Files::isRegularFile)
                .map(
                    p -> Golden.SAVES.relativize(p)
                        .toString()
                        .replace('\\', '/'))
                .filter(p -> !p.equals("MANIFEST.txt") && !p.equals(".gitattributes"))
                .sorted()
                .collect(Collectors.toList());
        }
        assertEquals(new ArrayList<>(listed.keySet()), files, "every golden file is listed, and only they");
        for (Map.Entry<String, String> e : listed.entrySet())
            assertEquals(e.getValue(), sha256(Golden.SAVES.resolve(e.getKey())), e.getKey() + " is as it was made");
    }

    private static String sha256(Path p) throws IOException {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(p));
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format("%02x", x));
            return b.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- the four campuses

    /**
     * A typical campus of 0.16.0 read by the current code: the nexus and its campus (sites, grounds, ledger, recycle
     * store, the job and its progress, the queue), every module (shape, samples, selected unit), then every building
     * checked again where its saved cells stand, and every tag written again with every key it had.
     */
    @ParameterizedTest
    @ValueSource(strings = { "hv", "ev", "iv", "luv" })
    void aCampusOf016ReadsBackWholeAndStandsAgain(String tier) throws IOException {
        Path dir = Golden.tier(tier)
            .dir(Golden.SAVES);
        NBTTagCompound info = Golden.read(dir.resolve("info.dat")), nexusTag = Golden.read(dir.resolve("nexus.dat")),
            modules = Golden.read(dir.resolve("modules.dat"));
        GoldenWorld g = GoldenWorld.read(Golden.read(dir.resolve("world.dat")));
        assertEquals(info.getInteger("Cells"), g.parts.size(), tier + ": every saved cell");
        NBTTagCompound shapes = info.getCompoundTag("Shapes");

        GoldenTiles.Nexus n = new GoldenTiles.Nexus();
        NBTTagList research = info.getTagList("Research", 8);
        for (int i = 0; i < research.tagCount(); i++) n.done.add(research.getStringTagAt(i));
        g.world.put(n, nexusTag.getInteger("x"), nexusTag.getInteger("y"), nexusTag.getInteger("z"));
        n.readFromNBT(nexusTag);
        assertEquals(shapes.getString("nexus"), n.shapeName(), tier + ": the nexus's shape");
        assertEquals(info.getInteger("Phase"), n.grown(), tier + ": the phase it grew to");
        assertArrayEquals(info.getIntArray("Centre"), n.centre(), tier + ": its centre");
        checkCampus(tier, n, nexusTag.getCompoundTag("Campus"));

        Map<Integer, TileMultiblock> tiles = new TreeMap<>();
        for (Object o : modules.func_150296_c()) {
            int site = Integer.parseInt((String) o);
            NBTTagCompound tag = modules.getCompoundTag((String) o);
            TileMultiblock t = tileFor(tag.getString("id"), n);
            g.world.put(t, tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"));
            t.readFromNBT(tag);
            String what = tier + " site " + site + " (" + tag.getString("id") + ")";
            assertEquals(shapes.getString(String.valueOf(site)), t.shapeName(), what + ": its shape");
            checkSamples(what, t, tag);
            int[] at = n.campus()
                .siteController(site);
            assertArrayEquals(new int[] { t.xCoord, t.yCoord, t.zCoord }, at, what + ": its site's controller");
            tiles.put(site, t);
        }
        assertEquals(
            info.getTagList("Built", 8)
                .tagCount(),
            tiles.size(),
            tier + ": every building");

        try {
            // every building forms again where its saved cells stand, as the shape it stood as
            GoldenWorld.recheck(n);
            assertTrue(n.formed(), tier + ": the nexus stands again " + n.firstWrong());
            assertEquals(shapes.getString("nexus"), n.shapeName(), tier + ": as the phase it was");
            for (Map.Entry<Integer, TileMultiblock> e : tiles.entrySet()) {
                TileMultiblock t = e.getValue();
                GoldenWorld.recheck(t);
                String what = tier + " site " + e.getKey();
                assertTrue(t.formed(), what + " stands again " + t.firstWrong());
                assertEquals(shapes.getString(String.valueOf(e.getKey())), t.shapeName(), what + ": as it stood");
            }
            // written again: every key of 0.16.0 is there, the campus's state as it was
            NBTTagCompound again = new NBTTagCompound();
            n.writeToNBT(again);
            assertNoneMissing(tier + " nexus", nexusTag, again);
            for (String k : new String[] { "Inv", "Bound", "Research", "ResearchDone", "ResearchTeam", "Manifest",
                "ManifestTicks", "ManifestMax", "Pending", "Grown", "OwnerMost", "OwnerLeast", "Facing" })
                assertSubsumed(tier + " nexus " + k, nexusTag.getTag(k), again.getTag(k));
            NBTTagCompound was = nexusTag.getCompoundTag("Campus"), is = again.getCompoundTag("Campus");
            for (String k : new String[] { "St", "Sites", "Ledger", "BuildIn", "Recycle", "Fb", "Pv", "Gl", "Declined",
                "Queue" }) if (was.hasKey(k)) assertSubsumed(tier + " campus " + k, was.getTag(k), is.getTag(k));
            if (was.hasKey("Job")) for (String k : new String[] { "K", "Pk", "S", "St", "Ps", "Sg", "Go", "D", "ByM",
                "ByL", "To", "Pa", "Ko" })
                if (was.getCompoundTag("Job")
                    .hasKey(k))
                    assertSubsumed(
                        tier + " job " + k,
                        was.getCompoundTag("Job")
                            .getTag(k),
                        is.getCompoundTag("Job")
                            .getTag(k));
            for (Map.Entry<Integer, TileMultiblock> e : tiles.entrySet()) {
                NBTTagCompound tag = modules.getCompoundTag(String.valueOf(e.getKey())), out = new NBTTagCompound();
                e.getValue()
                    .writeToNBT(out);
                String what = tier + " site " + e.getKey();
                assertNoneMissing(what, tag, out);
                for (String k : new String[] { "id", "Samples", "Unit", "Selected", "Desk", "Facing", "OwnerMost",
                    "OwnerLeast", "Shape" })
                    if (tag.hasKey(k)) assertSubsumed(what + " " + k, tag.getTag(k), out.getTag(k));
            }
        } finally {
            for (TileMultiblock t : tiles.values()) GoldenWorld.clearCells(t);
            GoldenWorld.clearCells(n);
        }
    }

    /** The campus as the current code read it: the sites, the grounds, the job and its progress, the queue. */
    private static void checkCampus(String tier, TileNexus n, NBTTagCompound was) {
        com.fluxecho.campus.Campus c = n.campus();
        assertTrue(c.active(), tier + ": an active campus");
        NBTTagList sites = was.getTagList("Sites", 10);
        List<Integer> recorded = new ArrayList<>();
        for (int i = 0; i < sites.tagCount(); i++) {
            NBTTagCompound s = sites.getCompoundTagAt(i);
            recorded.add(s.getInteger("S"));
            assertArrayEquals(
                s.getIntArray("P"),
                c.siteController(s.getInteger("S")),
                tier + " site " + s.getInteger("S"));
        }
        assertEquals(new TreeSet<>(recorded), new TreeSet<>(c.recordedSites()), tier + ": the recorded sites");
        NBTTagList laid = was.getTagList("Gl", 8);
        for (int i = 0; i < laid.tagCount(); i++)
            assertTrue(c.groundLaid(laid.getStringTagAt(i)), tier + ": the ground " + laid.getStringTagAt(i));
        if (was.hasKey("Job")) {
            NBTTagCompound j = was.getCompoundTag("Job");
            assertNotNull(c.job(), tier + ": its job");
            assertEquals(
                j.getString("K"),
                c.job()
                    .key(),
                tier + ": the job");
            assertEquals(
                j.getString("Pk"),
                c.job()
                    .planKey());
            assertEquals(
                j.getInteger("S"),
                c.job()
                    .site());
            assertEquals(
                j.getString("St"),
                c.job()
                    .state()
                    .name(),
                tier + ": the job's state");
            assertEquals(
                bits(j.getIntArray("D")),
                c.job()
                    .placed(),
                tier + ": the job's progress");
        }
        NBTTagList q = was.getTagList("Queue", 10);
        List<String> queued = new ArrayList<>();
        for (int i = 0; i < q.tagCount(); i++) queued.add(
            q.getCompoundTagAt(i)
                .getString("K"));
        assertEquals(queued, c.queue(), tier + ": the queue");
        NBTTagCompound raw = was.getCompoundTag("Ledger")
            .getCompoundTag("Raw");
        for (Object k : raw.func_150296_c()) assertEquals(
            raw.getLong((String) k),
            (long) c.ledger()
                .rawView()
                .get(k),
            tier + " credit " + k);
    }

    private static int bits(int[] words) {
        int n = 0;
        for (int w : words) n += Integer.bitCount(w);
        return n;
    }

    /** The tile of a saved id: the current class the id is registered with (the pod and the root knowing the nexus). */
    private static TileMultiblock tileFor(String id, TileNexus n) {
        if (id.equals("fluxecho:dome_generator")) {
            GoldenTiles.Pod p = new GoldenTiles.Pod();
            p.over = n;
            return p;
        }
        if (id.equals("fluxecho:root")) {
            GoldenTiles.Root r = new GoldenTiles.Root();
            r.over = n;
            return r;
        }
        Class<? extends TileMultiblock> k = IdExport.structures()
            .get(id);
        assertNotNull(k, "a structure tile is registered as " + id);
        return IdExport.make(k);
    }

    /** Every sample of the tag is in its slot, the same stack (item, count, damage and tag). */
    private static void checkSamples(String what, TileMultiblock t, NBTTagCompound tag) {
        IItemHandler h = t instanceof TileCityModule m ? m.samples() : t instanceof TileLibrary l ? l.samples() : null;
        if (h == null) return;
        NBTTagCompound saved = tag.getCompoundTag("Samples");
        NBTTagList items = saved.getTagList("Items", 10);
        assertTrue(h.getSlots() >= saved.getInteger("Size"), what + ": no slot lost");
        Map<Integer, NBTTagCompound> bySlot = new HashMap<>();
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound s = (NBTTagCompound) items.getCompoundTagAt(i)
                .copy();
            bySlot.put(s.getInteger("Slot"), s);
            s.removeTag("Slot");
        }
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            NBTTagCompound want = bySlot.get(i);
            if (want == null) {
                assertTrue(s == null, what + ": slot " + i + " was empty");
                continue;
            }
            assertNotNull(s, what + ": the sample of slot " + i);
            // the handler writes its counts as ints, a stack as a byte: the same stack either way
            assertEquals(
                want.getShort("id"),
                (short) net.minecraft.item.Item.getIdFromItem(s.getItem()),
                what + " slot " + i);
            assertEquals(want.getInteger("Count"), s.stackSize, what + ": the count of slot " + i);
            assertEquals(want.getShort("Damage"), (short) s.getItemDamage(), what + ": the sample of slot " + i);
            assertEquals(
                want.getCompoundTag("tag"),
                s.hasTagCompound() ? s.getTagCompound() : new NBTTagCompound(),
                what + ": the tag of slot " + i);
        }
    }

    // ---- every module in every shape, the library

    @Test
    void everyModuleInEveryShapeReadsBackAsItself() throws IOException {
        NBTTagCompound all = Golden.read(Golden.SAVES.resolve("modules/every_shape.dat"));
        int read = 0;
        for (Object o : all.func_150296_c()) {
            String id = (String) o;
            NBTTagCompound shapes = all.getCompoundTag(id);
            for (Object s : shapes.func_150296_c()) {
                NBTTagCompound tag = shapes.getCompoundTag((String) s);
                Class<? extends TileMultiblock> k = IdExport.structures()
                    .get(id);
                assertNotNull(k, id);
                TileMultiblock t = IdExport.make(k);
                t.readFromNBT(tag);
                String what = id + " as " + s;
                assertEquals(s, t.shapeName(), what);
                checkSamples(what, t, tag);
                NBTTagCompound out = new NBTTagCompound();
                t.writeToNBT(out);
                assertNoneMissing(what, tag, out);
                for (String key : new String[] { "Samples", "Unit", "Shape", "Facing", "Grown", "Desk", "Selected" })
                    if (tag.hasKey(key)) assertSubsumed(what + " " + key, tag.getTag(key), out.getTag(key));
                read++;
            }
        }
        assertEquals(35, read, "every shape of every structure of 0.16.0");
    }

    // ---- the saved world data

    @Test
    void theSavedWorldDataReadBack() throws IOException {
        Map<String, WorldSavedData> kinds = new TreeMap<>();
        kinds.put("fluxecho_research", new ResearchData("fluxecho_research"));
        kinds.put("fluxecho_ledger", new EchoLedger("fluxecho_ledger"));
        kinds.put("fluxecho_vault", new LibraryVault("fluxecho_vault"));
        kinds.put("fluxecho_recycle", new RecycleOverflow("fluxecho_recycle"));
        kinds.put("fluxecho_campus", new CampusRegistry("fluxecho_campus"));
        kinds.put("fluxecho_gates", new com.fluxecho.gate.GateRegistry("fluxecho_gates"));
        for (Map.Entry<String, WorldSavedData> e : kinds.entrySet()) {
            NBTTagCompound was = Golden.read(Golden.SAVES.resolve("data/" + e.getKey() + ".dat"))
                .getCompoundTag("data");
            e.getValue()
                .readFromNBT(was);
            NBTTagCompound is = new NBTTagCompound();
            e.getValue()
                .writeToNBT(is);
            assertNoneMissing(e.getKey(), was, is);
            assertSubsumed(e.getKey(), was, is);
        }
        ResearchData research = (ResearchData) kinds.get("fluxecho_research");
        for (Golden.Tier t : Golden.TIERS)
            assertEquals(new TreeSet<>(Arrays.asList(t.research)), new TreeSet<>(research.done(t.team)), t.name);
        EchoLedger ledger = (EchoLedger) kinds.get("fluxecho_ledger");
        assertTrue(ledger.has(Golden.TIERS[3].team, "orb", "AWWayofTime:weakBloodOrb"), "the LuV team's orb");
    }

    // ---- the core item

    /**
     * The core of the LuV nexus, broken by 0.16.0, put back where it stood: the campus carries on with its sites and
     * grounds, the credit and the recycle store go back in, and the campus it carries again has every key it had.
     */
    @Test
    void theCoreOf016CarriesItsCampusBack() throws IOException, ReflectiveOperationException {
        NBTTagCompound core = Golden.read(Golden.SAVES.resolve("items/nexus_core.dat")),
            carried = core.getCompoundTag(ItemBlockNexusCore.CAMPUS);
        int[] pos = carried.getIntArray("Pos");
        GoldenWorld g = new GoldenWorld();
        GoldenTiles.Nexus n = new GoldenTiles.Nexus();
        g.place(n, new int[] { pos[0], pos[1], pos[2], 2 }, Golden.OWNER);
        ItemStack stack = new ItemStack(GoldenWorld.sampleItem());
        stack.setTagCompound(core);
        Method restore = ItemBlockNexusCore.class.getDeclaredMethod("restore", ItemStack.class, TileNexus.class);
        restore.setAccessible(true);
        restore.invoke(null, stack, n);
        com.fluxecho.campus.Campus c = n.campus();
        assertTrue(c.active(), "the campus carries on");
        assertEquals(
            carried.getByte("F"),
            n.front()
                .ordinal(),
            "facing as it stood");
        assertEquals(carried.getInteger("Ph"), n.grown(), "grown as it was");
        NBTTagList sites = carried.getTagList("Sites", 10);
        assertEquals(16, sites.tagCount(), "the user's sixteen buildings");
        for (int i = 0; i < sites.tagCount(); i++) {
            NBTTagCompound s = sites.getCompoundTagAt(i);
            assertArrayEquals(s.getIntArray("P"), c.siteController(s.getInteger("S")), "site " + s.getInteger("S"));
        }
        assertTrue(c.groundLaid("establish:outer"), "the outer ring stays laid");
        NBTTagCompound credit = core.getCompoundTag(ItemBlockNexusCore.CREDIT), raw = credit.getCompoundTag("Raw"),
            parts = credit.getCompoundTag("Parts");
        for (Object k : raw.func_150296_c()) assertEquals(
            raw.getLong((String) k),
            (long) c.ledger()
                .rawView()
                .get(k),
            "credit " + k);
        for (Object k : parts.func_150296_c()) assertEquals(
            parts.getInteger((String) k),
            (int) c.ledger()
                .partView()
                .get(Integer.parseInt((String) k)),
            "part " + k);
        long recycled = 0;
        NBTTagList r = credit.getTagList("Recycle", 10);
        for (int i = 0; i < r.tagCount(); i++) recycled += r.getCompoundTagAt(i)
            .getLong("N");
        assertEquals(
            recycled,
            c.recycle()
                .total(),
            "the recycle store");
        assertNotNull(n.making(), "the manifestation it carried runs on");
        assertNoneMissing("the core's campus", carried, c.carried());
    }

    // ---- comparing tags

    private static void assertNoneMissing(String what, NBTTagCompound was, NBTTagCompound now) {
        List<String> missing = Golden.missingKeys(was, now);
        if (!missing.isEmpty()) fail(what + " lost keys of 0.16.0: " + missing);
    }

    /**
     * Everything in {@code was} is in {@code now} with the same value: every key of a compound (recursively), every
     * element of a list matched by some element (a writer's order is its own), every other tag equal.
     */
    static void assertSubsumed(String what, NBTBase was, NBTBase now) {
        if (!subsumed(was, now)) fail(what + ": 0.16.0 had " + was + ", now " + now);
    }

    private static boolean subsumed(NBTBase was, NBTBase now) {
        if (was == null) return true;
        if (now == null) return false;
        if (was instanceof NBTTagCompound w) {
            if (!(now instanceof NBTTagCompound n)) return false;
            for (Object o : w.func_150296_c()) if (!subsumed(w.getTag((String) o), n.getTag((String) o))) return false;
            return true;
        }
        if (was instanceof NBTTagList w) {
            if (!(now instanceof NBTTagList n)) return false;
            List<NBTBase> want = elements(w), left = new ArrayList<>(elements(n));
            if (left.size() < want.size()) return false;
            outer: for (int i = 0; i < want.size(); i++) {
                // the same place first (a list keeps its order unless its writer sorts it otherwise)
                if (i < left.size() && left.get(i) != null && subsumed(want.get(i), left.get(i))) {
                    left.set(i, null);
                    continue;
                }
                for (int k = 0; k < left.size(); k++) if (left.get(k) != null && subsumed(want.get(i), left.get(k))) {
                    left.set(k, null);
                    continue outer;
                }
                return false;
            }
            return true;
        }
        // a number is the same number whatever width its writer gave it (a handler's counts are ints, a stack's bytes)
        if (was instanceof NBTBase.NBTPrimitive w && now instanceof NBTBase.NBTPrimitive n)
            return w.func_150286_g() == n.func_150286_g() && w.func_150291_c() == n.func_150291_c();
        return was.equals(now);
    }

    /** The tags of a list, in its order (read from the list itself: its getters copy or cast by type). */
    @SuppressWarnings("unchecked")
    private static List<NBTBase> elements(NBTTagList l) {
        try {
            for (java.lang.reflect.Field f : NBTTagList.class.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                return (List<NBTBase>) f.get(l);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("a list without its tags");
    }
}
