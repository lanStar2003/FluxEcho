package com.fluxecho.save;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.gtnewhorizons.modularui.api.forge.ItemStackHandler;

/**
 * Where the golden saves of 0.16.0 lie and what they are made of (refactor §5.1 ②, §6.4): the four typical campuses
 * (HV, EV, IV and the user's LuV save) with the teams, research and buildings each had, the sample stacks that fill
 * every unit, and the file format (Minecraft's own gzipped NBT, the format of {@code level.dat} and
 * {@code data/*.dat}).
 * Shared by the generator, which ran once in a work tree of the tag 0.16.0, and {@code LegacySaveTest}, which reads the
 * files with the current code.
 */
public final class Golden {

    /** The golden saves in the repository, and where the generator writes them. */
    public static final Path SAVES = Paths.get("src/test/resources/saves/0.16.0"),
        OUT = Paths.get("build/golden/saves/0.16.0");

    /** The owner every golden building was placed by, and a second team bound to every nexus. */
    public static final UUID OWNER = UUID.fromString("0f1e2d3c-4b5a-4968-8776-655443322110"),
        BOUND = UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");

    private Golden() {}

    /** One typical campus: where its nexus stands and faces, the phase it grew to and its teams' research. */
    public static final class Tier {

        public final String name;
        /** The nexus centre (its base layer Y0 under the controller, which stands at Y0 + 2). */
        public final int x, y0, z;
        /** The nexus's facing (a horizontal ForgeDirection ordinal: 2 north, 3 south, 4 west, 5 east). */
        public final int facing;
        public final int phase;
        public final UUID team;
        public final String[] research;

        Tier(String name, int x, int y0, int z, int facing, int phase, UUID team, String... research) {
            this.name = name;
            this.x = x;
            this.y0 = y0;
            this.z = z;
            this.facing = facing;
            this.phase = phase;
            this.team = team;
            this.research = research;
        }

        /** The folder of its files. */
        public Path dir(Path root) {
            return root.resolve("campus")
                .resolve(name);
        }
    }

    private static final String[] HV = { "anchor", "inner_ring", "echo_crystal", "library", "library_reach",
        "compute_1", "upkeep_1", "grit_yield", "eco_dome" };
    private static final String[] EV = { "phase_2", "tidal_well", "dome", "arcane_court", "blood_covenant",
        "hunting_ground", "mana_fountain", "resonance_gate", "dual_foil_vault" };
    private static final String[] IV = { "phase_3", "outer_ring", "sophon_array", "holo_table" };
    private static final String[] LUV = { "phase_4", "sky_ring", "weather_station", "sky_sails", "observatory",
        "droplet_hangar", "deep_root" };

    /**
     * The four campuses: an HV campus of phase I (the Archive standing, the Eco Dome half built), an EV one of phase II
     * (eleven buildings: the three halls, the four pavilions, the gate, the moored pod and the Tidal Well), an IV one
     * of
     * phase III (the outer ring laid, the Sophon Array standing, the Holo Sand Table being built) and the LuV one of
     * the
     * user's own save: phase IV, sixteen buildings, the sky ring on S0 to S6 (sites 30 to 36), the pod released and
     * floating, the root grown into the Deep Root. Each faces another way and stands somewhere else.
     */
    public static final Tier[] TIERS = {
        new Tier("hv", 120, 64, -340, 2, 1, UUID.fromString("10000000-0000-4000-8000-000000000001"), HV),
        new Tier("ev", 1544, 71, 816, 5, 2, UUID.fromString("20000000-0000-4000-8000-000000000002"), join(HV, EV)),
        new Tier(
            "iv",
            -2400,
            64,
            3104,
            3,
            3,
            UUID.fromString("30000000-0000-4000-8000-000000000003"),
            join(HV, EV, IV)),
        new Tier(
            "luv",
            5200,
            66,
            -4712,
            4,
            4,
            UUID.fromString("40000000-0000-4000-8000-000000000004"),
            join(HV, EV, IV, LUV)) };

    private static String[] join(String[]... parts) {
        List<String> out = new ArrayList<>();
        for (String[] p : parts) for (String s : p) out.add(s);
        return out.toArray(new String[0]);
    }

    /** The tier of a name. */
    public static Tier tier(String name) {
        for (Tier t : TIERS) if (t.name.equals(name)) return t;
        throw new IllegalArgumentException(name);
    }

    // ---- samples

    /**
     * The sample of a slot of a building: one of the golden item, its damage made of the building and the slot (every
     * slot of a building another; a damage is saved as a short, so it stays under 32,000), every seventh carrying a tag
     * of its own (a sample's NBT must survive too).
     */
    public static ItemStack sample(int building, int slot) {
        ItemStack s = new ItemStack(GoldenWorld.sampleItem(), 1, (building * 7919 + slot) % 32_000);
        if (slot % 7 == 3) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("Golden", building + "/" + slot);
            t.setInteger("Slot", slot);
            s.setTagCompound(t);
        }
        return s;
    }

    /** A handler of {@code n} slots holding the building's samples in every slot, as NBT. */
    public static NBTTagCompound fullHandler(int building, int n) {
        ItemStackHandler h = new ItemStackHandler(n);
        for (int i = 0; i < n; i++) h.setStackInSlot(i, sample(building, i));
        return h.serializeNBT();
    }

    // ---- files

    public static NBTTagCompound read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return CompressedStreamTools.readCompressed(in);
        }
    }

    public static void write(Path file, NBTTagCompound t) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            CompressedStreamTools.writeCompressed(t, out);
        }
    }

    // ---- comparing tags

    /**
     * The keys of {@code was} that {@code now} lacks, as paths: every key of a compound, and of the compounds in a
     * list every key any of them has (a list's order is its writer's business; its keys are the format).
     */
    public static List<String> missingKeys(NBTTagCompound was, NBTTagCompound now) {
        List<String> out = new ArrayList<>();
        missing("", was, now, out);
        return out;
    }

    private static void missing(String path, NBTBase was, NBTBase now, List<String> out) {
        if (was instanceof NBTTagCompound w) {
            if (!(now instanceof NBTTagCompound n)) {
                out.add(path + " (not a compound now)");
                return;
            }
            for (Object o : w.func_150296_c()) {
                String k = (String) o;
                if (!n.hasKey(k)) out.add(path + "/" + k);
                else missing(path + "/" + k, w.getTag(k), n.getTag(k), out);
            }
        } else if (was instanceof NBTTagList w && w.func_150303_d() == 10) {
            if (!(now instanceof NBTTagList n)) {
                out.add(path + " (not a list now)");
                return;
            }
            Set<String> before = new LinkedHashSet<>(), after = new LinkedHashSet<>();
            for (int i = 0; i < w.tagCount(); i++) before.addAll(keys(w.getCompoundTagAt(i)));
            for (int i = 0; i < n.tagCount(); i++) after.addAll(keys(n.getCompoundTagAt(i)));
            for (String k : before) if (!after.contains(k)) out.add(path + "[]/" + k);
        }
    }

    private static Set<String> keys(NBTTagCompound t) {
        Set<String> out = new LinkedHashSet<>();
        for (Object o : t.func_150296_c()) out.add((String) o);
        return out;
    }
}
