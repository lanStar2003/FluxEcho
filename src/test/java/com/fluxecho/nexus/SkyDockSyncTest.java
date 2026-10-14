package com.fluxecho.nexus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import com.fluxecho.city.TileObservatory;
import com.fluxecho.city.TileWeatherStation;
import com.fluxecho.logic.SkyRing;

/**
 * The client hears which sky sites a module is docked on (0.16.0, {@code Ds}, and their colours {@code DsC}): a bit
 * per slot S0..S7, only once one docks there (a nexus with none syncs as 0.15.0 synced it), so the masterplan draws the
 * platforms that stand and the light pillars under them; the inner ring's mask leaves them out, as it leaves out the
 * axis.
 */
class SkyDockSyncTest {

    /** A nexus whose docked modules are the test's. */
    private static final class Docked extends TileNexus {

        final List<TileModule> modules = new ArrayList<>();

        @Override
        public List<TileModule> docked() {
            return modules;
        }
    }

    @Test
    void theSkySitesThatStandAreSynced() {
        Docked n = new Docked();
        NBTTagCompound none = new NBTTagCompound();
        n.writeSync(none);
        assertFalse(none.hasKey("Ds"), "nothing docked on the sky ring: synced as before");
        assertFalse(none.hasKey("DsC"));

        TileWeatherStation w = new TileWeatherStation();
        ((TileModule) w).dock(n, 30, Long.MAX_VALUE);
        TileObservatory o = new TileObservatory();
        ((TileModule) o).dock(n, 34, Long.MAX_VALUE);
        n.modules.add(w);
        n.modules.add(o);
        NBTTagCompound t = new NBTTagCompound();
        n.writeSync(t);
        assertEquals(SkyRing.bit(30) | SkyRing.bit(34), t.getByte("Ds") & 0xFF);
        assertEquals(0, t.getByte("D"), "the inner ring's mask holds none of them");

        TileNexus client = new TileNexus();
        client.readSync(t);
        assertEquals(SkyRing.bit(30) | SkyRing.bit(34), client.clientSkyDocked);
        int[] colours = new int[SkyRing.SLOTS];
        colours[0] = TileWeatherStation.COLOR;
        colours[4] = TileObservatory.COLOR;
        assertArrayEquals(colours, client.clientSkyColor);
        TileNexus old = new TileNexus();
        old.readSync(none);
        assertEquals(0, old.clientSkyDocked);
        assertEquals(SkyRing.SLOTS, old.clientSkyColor.length);
    }
}
