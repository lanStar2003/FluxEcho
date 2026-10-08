package com.fluxecho;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every optional mod is only touched from its own module package, so FluxEcho loads without it. The dev classpath
 * always has Forestry (GT pulls it in), so a leak would never show in a dev run; this reads the compiled classes'
 * constant pools instead.
 */
class IsolationTest {

    /** Package prefix of the mod's classes, and the only FluxEcho package allowed to refer to them. */
    private static final Map<String, String> MODULES = new HashMap<>();

    static {
        MODULES.put("forestry/", "com/fluxecho/bees/");
        MODULES.put("thaumcraft/", "com/fluxecho/thaumcraft/");
        MODULES.put("WayofTime/", "com/fluxecho/blood/");
        MODULES.put("betterquesting/", "com/fluxecho/quest/bq/");
        MODULES.put("com/kuba6000/mobsinfo/", "com/fluxecho/mobs/");
        MODULES.put("ic2/", "com/fluxecho/crops/");
        MODULES.put("vazkii/botania/", "com/fluxecho/mana/");
        MODULES.put("appeng/", "com/fluxecho/ae/");
    }

    /** A class name at the start of a constant, or inside a descriptor ("Lforestry/...;"). */
    private static final Pattern REF = Pattern.compile(
        "(?:^|L)(forestry/|thaumcraft/|WayofTime/|betterquesting/|com/kuba6000/mobsinfo/|ic2/|vazkii/botania/|appeng/)");

    @Test
    void optionalModsStayInTheirModules() throws IOException {
        Path root = Paths.get("build", "classes", "java", "main");
        assertTrue(Files.isDirectory(root), "compiled classes not found at " + root.toAbsolutePath());
        List<String> leaks = new ArrayList<>();
        List<Path> classes;
        try (Stream<Path> s = Files.walk(root)) {
            classes = s.filter(
                p -> p.toString()
                    .endsWith(".class"))
                .collect(Collectors.toList());
        }
        for (Path p : classes) {
            String name = root.relativize(p)
                .toString()
                .replace('\\', '/');
            for (String constant : utf8Constants(p)) {
                Matcher m = REF.matcher(constant);
                while (m.find()) {
                    String allowed = MODULES.get(m.group(1));
                    if (!name.startsWith(allowed)) leaks.add(name + " -> " + constant);
                }
            }
        }
        assertTrue(leaks.isEmpty(), "optional mods referenced outside their module:\n" + String.join("\n", leaks));
    }

    /** The UTF-8 entries of a class file's constant pool: class names, descriptors, strings. */
    private static List<String> utf8Constants(Path classFile) throws IOException {
        List<String> out = new ArrayList<>();
        try (InputStream in = Files.newInputStream(classFile); DataInputStream d = new DataInputStream(in)) {
            d.readInt(); // magic
            d.readUnsignedShort(); // minor
            d.readUnsignedShort(); // major
            int count = d.readUnsignedShort();
            for (int i = 1; i < count; i++) {
                int tag = d.readUnsignedByte();
                switch (tag) {
                    case 1 -> out.add(d.readUTF());
                    case 3, 4 -> d.skipBytes(4);
                    case 5, 6 -> {
                        d.skipBytes(8);
                        i++; // long and double take two slots
                    }
                    case 7, 8, 16, 19, 20 -> d.skipBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> d.skipBytes(4);
                    case 15 -> d.skipBytes(3);
                    default -> throw new IOException("unknown constant tag " + tag + " in " + classFile);
                }
            }
        }
        return out;
    }
}
