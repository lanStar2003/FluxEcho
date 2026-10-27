package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fluxecho.city.TileCityModule;
import com.fluxecho.city.TileEcoDome;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.skin.SkinCodec;
import com.fluxecho.logic.skin.SkinEntry;
import com.fluxecho.logic.skin.SkinPalette;
import com.fluxecho.logic.skin.SkinProfiles;
import com.fluxecho.logic.skin.SkinState;
import com.fluxecho.nexus.NexusNet;
import com.fluxecho.nexus.TileModule;
import com.fluxecho.nexus.TileNexus;

/**
 * The box table between the server and its players ({@link SkinFeed}, style §3.9.3), in a test world: a nexus with an
 * Eco Dome on its site 2. A player gets the whole table once when they log in, once when they change dimension and
 * once when they come within 512 blocks of a campus (and keep it until 640); a change of an entry goes out as one
 * delta to the players who have the campus. Once the dome's chunk is unloaded its entry comes from the campus's
 * {@code Skn}, written and read back with the nexus.
 */
@Tag("slow")
class SkinSyncTest {

    private static final int NX = 0, NY = 64, NZ = 0, DX = 40, DY = 64, DZ = 0;

    /** What the feed sent: to whom, which kind, the payload. */
    private final List<Object[]> sent = new ArrayList<>();
    private FakeWorld world;
    private TileNexus nexus;
    private TileEcoDome dome;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        SkinFeed.clear();
        SkinFeed.sender = (player, kind, data) -> sent.add(new Object[] { player, kind, data });
        world = world();
        nexus = nexus(world);
        dome = dome(world, nexus);
    }

    @AfterEach
    void tearDown() {
        SkinFeed.clear();
        SkinFeed.sender = SkinFeed.NETWORK;
    }

    @Test
    void theFeedKeepsAnEntryForTheNexusAndEachBuilding() {
        SkinFeed f = feed(nexus);
        f.evaluate(20);
        List<SkinEntry> e = f.entries();
        assertEquals(2, e.size());
        SkinEntry n = find(e, "nexus"), d = find(e, "eco_dome");
        assertEquals(SkinState.FORMED, n.state);
        assertTrue(n.powered);
        assertEquals("phase1", n.shape);
        assertEquals(SkinPalette.NEXUS_CYAN, n.accent);
        assertEquals(SkinState.FORMED, d.state);
        assertTrue(d.powered, "docked and powered");
        assertEquals(0, d.formedAt, "it stood before the table knew it");
        assertEquals("main", d.shape);
        assertArrayEquals(new int[] { DX, DY, DZ }, d.ctrl);
        assertEquals(SkinPalette.accent("eco_dome"), d.accent);
        assertEquals(f.id(), d.campus);
        // its box is its shape's round the controller, and its lamps lie inside it
        int[] b = dome.bounds();
        assertArrayEquals(b, d.box);
        assertNotNull(d.lamp);
        for (int i = 0; i < 3; i++) assertTrue(d.lamp[i] >= d.box[i] && d.lamp[i + 3] <= d.box[i + 3]);
        assertNotNull(
            SkinProfiles.legacyMask(
                "eco_dome",
                "main",
                dome.build()
                    .blueprint()));
        // the nexus's lamps are its lift pad and its taken-in rings and spokes, inside its box
        assertNotNull(n.lamp);
    }

    @Test
    void loginSendsTheWholeTableOnce() throws IOException {
        feed(nexus).evaluate(20);
        SkinFeed.login("alice", 0, NX + 100, NZ);
        assertEquals(1, sent.size());
        SkinCodec.Table t = table(0);
        assertEquals("alice", sent.get(0)[0]);
        assertTrue(t.reset, "a login starts the client's table over");
        assertEquals(0, t.dim);
        assertEquals(2, t.entries.size());
        // nothing more while nothing changes
        SkinFeed.check("alice", 0, NX + 100, NZ);
        SkinFeed.check("alice", 0, NX + 120, NZ);
        assertEquals(1, sent.size());
    }

    @Test
    void comingWithin512BlocksSendsTheCampusOnce() throws IOException {
        feed(nexus).evaluate(20);
        SkinFeed.login("bob", 0, 2000, 0);
        assertEquals(1, sent.size());
        assertEquals(0, table(0).entries.size(), "nothing within 512 blocks");
        SkinFeed.check("bob", 0, 600, 0);
        assertEquals(1, sent.size(), "600 out: not yet");
        SkinFeed.check("bob", 0, 500, 0);
        assertEquals(2, sent.size(), "within 512");
        SkinCodec.Table t = table(1);
        assertFalse(t.reset, "a campus is added to what the client has");
        assertArrayEquals(new long[] { feed(nexus).id() }, t.campuses);
        assertEquals(2, t.entries.size());
        SkinFeed.check("bob", 0, 300, 0);
        SkinFeed.check("bob", 0, 600, 0);
        assertEquals(2, sent.size(), "once; 600 out it is kept (it is dropped past 640)");

        // a change goes to the players that have the campus: bob at 600 still has it
        setFormed(dome, false);
        feed(nexus).evaluate(40);
        assertEquals(3, sent.size());
        assertEquals(NexusNet.SKIN_DELTA, sent.get(2)[1]);
        SkinCodec.Delta d = delta(2);
        assertEquals(SkinState.HOLD, d.entry.state);
        assertEquals("eco_dome", d.entry.key());

        // past 640 it is dropped: no more deltas, and coming back sends it again
        SkinFeed.check("bob", 0, 700, 0);
        setFormed(dome, true);
        feed(nexus).evaluate(60);
        assertEquals(3, sent.size());
        SkinFeed.check("bob", 0, 400, 0);
        assertEquals(4, sent.size());
        assertEquals(NexusNet.SKIN_TABLE, sent.get(3)[1]);
        assertEquals(SkinState.FORMED, find(table(3).entries, "eco_dome").state);
    }

    @Test
    void changingDimensionSendsTheNewDimensionsTableOnce() throws IOException {
        feed(nexus).evaluate(20);
        SkinFeed.login("carol", 0, 10, 10);
        SkinFeed.changedDimension("carol", -1, 10, 10);
        assertEquals(2, sent.size());
        SkinCodec.Table nether = table(1);
        assertTrue(nether.reset, "the old dimension's table is dropped");
        assertEquals(-1, nether.dim);
        assertEquals(0, nether.entries.size());
        // a change in the overworld does not reach her in the nether
        setFormed(dome, false);
        feed(nexus).evaluate(40);
        assertEquals(2, sent.size());
        SkinFeed.changedDimension("carol", 0, 10, 10);
        assertEquals(3, sent.size());
        SkinCodec.Table back = table(2);
        assertTrue(back.reset);
        assertEquals(0, back.dim);
        assertEquals(SkinState.HOLD, find(back.entries, "eco_dome").state);
        // a respawn in the same dimension sends nothing; in another one it is a change of dimension
        SkinFeed.respawned("carol", 0, 10, 10);
        assertEquals(3, sent.size());
        SkinFeed.respawned("carol", 1, 0, 0);
        assertEquals(4, sent.size());
        assertTrue(table(3).reset);
        SkinFeed.logout("carol");
        setFormed(dome, true);
        feed(nexus).evaluate(60);
        assertEquals(4, sent.size(), "gone");
    }

    @Test
    void anUnloadedBuildingsEntryComesFromSkn() throws ReflectiveOperationException {
        SkinFeed f = feed(nexus);
        f.evaluate(20);
        SkinEntry before = find(f.entries(), "eco_dome");

        // the nexus is saved with its campus, the dome's chunk unloads, the nexus is read back as a new tile
        NBTTagCompound saved = new NBTTagCompound();
        saved.setBoolean("Formed", true);
        saved.setByte("Facing", (byte) 2);
        nexus.campus()
            .writeNBT(saved);
        NBTTagCompound campus = saved.getCompoundTag("Campus");
        assertTrue(campus.hasKey("Skn"), "the campus saves its table as Skn");
        world.unload(DX, DZ);
        TileNexus again = new TileNexus();
        again.readFromNBT(saved);
        world.put(again, NX, NY, NZ);
        setPowered(again, true);
        SkinFeed g = feed(again);
        g.evaluate(100);
        SkinEntry after = find(g.entries(), "eco_dome");
        assertEquals(before, after, "the entry it last knew");
        assertEquals(
            2,
            g.entries()
                .size());

        // the chunk loads again: the live tile takes over
        world.load(DX, DZ);
        TileEcoDome back = dome(world, again);
        setFormed(back, false);
        g.evaluate(120);
        assertEquals(SkinState.HOLD, find(g.entries(), "eco_dome").state);
    }

    @Test
    void aBuildingWhoseCoreIsGoneLeavesTheTable() throws IOException {
        SkinFeed f = feed(nexus);
        f.evaluate(20);
        SkinFeed.login("dan", 0, 0, 0);
        // the core is broken: its chunk is there, its tile is not
        world.unload(DX, DZ);
        world.load(DX, DZ);
        f.evaluate(40);
        assertEquals(
            1,
            f.entries()
                .size());
        assertEquals(2, sent.size());
        SkinCodec.Delta d = delta(1);
        assertNull(d.entry, "removed");
        assertEquals(SkinEntry.pack(DX, DY, DZ), d.id);
    }

    @Test
    void aMovedNexusTakesItsEntriesAlongAndEmptiesTheOldCampus() throws IOException {
        SkinFeed f = feed(nexus);
        f.evaluate(20);
        int[] c = nexus.centre();
        long old = SkinEntry.pack(c[0], c[1], c[2]);
        SkinFeed.login("dan", 0, 0, 0);
        assertEquals(1, sent.size());
        // the nexus stands 8 blocks further east now, its campus with it
        world.put(nexus, NX + 8, NY, NZ);
        c = nexus.centre();
        long moved = SkinEntry.pack(c[0], c[1], c[2]);
        f.evaluate(40);
        assertEquals(2, sent.size(), "the player that had the old campus is told it is empty");
        SkinCodec.Table t = table(1);
        assertEquals(1, t.campuses.length);
        assertEquals(old, t.campuses[0]);
        assertTrue(t.entries.isEmpty());
        assertEquals(
            2,
            f.entries()
                .size());
        for (SkinEntry e : f.entries()) assertEquals(moved, e.campus, e.key());
    }

    @Test
    void theLampPartsOfTheOldBuildingsAreTheDecksAndFittingsOfTheirMask() {
        // the dome's pilasters and cornices are lamps; its lit floor strips are not
        assertTrue(SkinProfiles.isLamp("eco_dome", Parts.deck(Parts.D_PANEL_LIT)));
        assertFalse(SkinProfiles.isLamp("eco_dome", Parts.deck(Parts.D_LIT)));
    }

    // ---- the world

    private static FakeWorld world() throws ReflectiveOperationException {
        FakeWorld w = FakeWorld.create();
        Field provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(w, new WorldProviderSurface());
        return w;
    }

    private static TileNexus nexus(FakeWorld w) throws ReflectiveOperationException {
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("Formed", true);
        t.setByte("Facing", (byte) 2);
        NBTTagCompound c = new NBTTagCompound();
        c.setString("St", "active");
        NBTTagList sites = new NBTTagList();
        NBTTagCompound s = new NBTTagCompound();
        s.setInteger("S", 2);
        s.setIntArray("P", new int[] { DX, DY, DZ });
        s.setString("K", "eco_dome");
        sites.appendTag(s);
        c.setTag("Sites", sites);
        t.setTag("Campus", c);
        TileNexus n = new TileNexus();
        n.readFromNBT(t);
        w.put(n, NX, NY, NZ);
        setPowered(n, true);
        return n;
    }

    private static TileEcoDome dome(FakeWorld w, TileNexus n) throws ReflectiveOperationException {
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("Formed", true);
        t.setByte("Facing", (byte) 4);
        TileEcoDome d = new TileEcoDome();
        d.readFromNBT(t);
        w.put(d, DX, DY, DZ);
        set(TileModule.class, d, "nexus", n);
        set(TileModule.class, d, "dockedUntil", Long.MAX_VALUE);
        set(TileModule.class, d, "dock", "docked");
        set(TileCityModule.class, d, "powered", true);
        return d;
    }

    private static void setPowered(TileNexus n, boolean on) throws ReflectiveOperationException {
        set(TileNexus.class, n, "powered", on);
    }

    private static void setFormed(com.fluxecho.nexus.TileMultiblock t, boolean on) {
        try {
            set(com.fluxecho.nexus.TileMultiblock.class, t, "formed", on);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Class<?> k, Object o, String name, Object v) throws ReflectiveOperationException {
        Field f = k.getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, v);
    }

    private static SkinFeed feed(TileNexus n) {
        return n.campus()
            .skin();
    }

    private SkinCodec.Table table(int i) throws IOException {
        assertEquals(NexusNet.SKIN_TABLE, sent.get(i)[1]);
        return SkinCodec.readTable(SkinFeed.bytes((NBTTagCompound) sent.get(i)[2]));
    }

    private SkinCodec.Delta delta(int i) throws IOException {
        assertEquals(NexusNet.SKIN_DELTA, sent.get(i)[1]);
        return SkinCodec.readDelta(SkinFeed.bytes((NBTTagCompound) sent.get(i)[2]));
    }

    private static SkinEntry find(List<SkinEntry> l, String key) {
        for (SkinEntry e : l) if (e.key()
            .equals(key)) return e;
        return null;
    }
}
