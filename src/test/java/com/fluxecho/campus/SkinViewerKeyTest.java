package com.fluxecho.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fluxecho.nexus.NexusNet;

/**
 * Who a viewer of the box table is (0.17.0 review): a respawn (a death, the way back from the End) makes a new
 * {@link EntityPlayerMP} for the same player. Keyed by the player object, the old one stayed a viewer for good: every
 * change of its campuses went to the client twice, and once the player logged out the packets queued up on the closed
 * connection the old object still held. Keyed by the player's UUID ({@link SkinFeed#viewer}), the new object is the
 * same viewer: a respawn in the same dimension sends nothing (the client kept its world and its table), one in
 * another dimension sends that dimension's table, and a logout forgets the player whichever object it comes with.
 */
class SkinViewerKeyTest {

    private final List<Object[]> sent = new ArrayList<>();

    @BeforeEach
    void setUp() {
        SkinFeed.clear();
        SkinFeed.sender = (player, kind, data) -> sent.add(new Object[] { player, kind, data });
    }

    @AfterEach
    void tearDown() {
        SkinFeed.sender = SkinFeed.NETWORK;
        SkinFeed.clear();
    }

    /** A bare server player with the UUID (no constructor runs: it would need a server and a world). */
    private static EntityPlayerMP player(UUID id) throws ReflectiveOperationException {
        Class<?> u = Class.forName("sun.misc.Unsafe");
        Field f = u.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Method alloc = u.getMethod("allocateInstance", Class.class);
        EntityPlayerMP p = (EntityPlayerMP) alloc.invoke(f.get(null), EntityPlayerMP.class);
        for (Field e : Entity.class.getDeclaredFields()) if (e.getType() == UUID.class) {
            e.setAccessible(true);
            e.set(p, id);
        }
        assertEquals(id, p.getUniqueID());
        return p;
    }

    @Test
    void aRespawnedPlayerIsTheSameViewer() throws ReflectiveOperationException {
        UUID id = UUID.fromString("0d9e5a4c-5f0e-4a43-9a1e-2b7a8f3c1d10");
        EntityPlayerMP before = player(id), after = player(id);
        assertNotSame(before, after);
        assertEquals(SkinFeed.viewer(before), SkinFeed.viewer(after));

        SkinFeed.login(before, 0, 0, 0);
        assertEquals(1, sent.size(), "a login sends the whole table once");
        assertEquals(id, sent.get(0)[0], "to the player's key, which the network sender finds online");
        assertEquals(NexusNet.SKIN_TABLE, sent.get(0)[1]);

        SkinFeed.respawned(after, 0, 0, 0);
        assertEquals(1, sent.size(), "a respawn in the same dimension: the client kept its table");

        SkinFeed.respawned(after, -1, 0, 0);
        assertEquals(2, sent.size(), "a respawn in another dimension: that dimension's table");

        SkinFeed.logout(after);
        SkinFeed.respawned(before, -1, 0, 0);
        assertEquals(3, sent.size(), "after the logout the player is nobody's viewer any more, the old object neither");
    }

    @Test
    void otherKeysStayAsTheyAre() {
        assertEquals("alice", SkinFeed.viewer("alice"));
    }
}
