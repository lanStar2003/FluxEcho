package com.fluxecho.logic.skin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fluxecho.Tools;

/**
 * The textures of the seven-row recolouring are what their generators draw (0.17.0): {@code tools/NexusTextures.java}
 * and {@code tools/CampusTextures.java} are run into a scratch folder and every image they draw is pixel for pixel the
 * committed one (and every animation's {@code .mcmeta} the same), so no texture of theirs was painted by hand or left
 * behind when a generator changed. Slow: it compiles and runs both generators.
 */
@Tag("slow")
class GeneratedTexturesTest {

    private static final Path ASSETS = Paths.get("src/main/resources/assets/fluxecho/textures");

    @Test
    void theNexusTexturesAreTheGenerators(@TempDir Path out) throws IOException {
        same("NexusTextures", out);
    }

    @Test
    void theCampusTexturesAreTheGenerators(@TempDir Path out) throws IOException {
        same("CampusTextures", out);
    }

    private static void same(String tool, Path out) throws IOException {
        Tools.call(tool, "main", new Class<?>[] { String[].class }, (Object) new String[] { out + File.separator });
        List<Path> drawn;
        try (Stream<Path> s = Files.walk(out)) {
            drawn = s.filter(Files::isRegularFile)
                .collect(Collectors.toList());
        }
        assertTrue(drawn.size() > 50, tool + " drew " + drawn.size() + " files");
        List<String> differ = new ArrayList<>();
        for (Path p : drawn) {
            Path rel = out.relativize(p), committed = ASSETS.resolve(rel.toString());
            if (!Files.isRegularFile(committed)) {
                differ.add(rel + " is not committed");
                continue;
            }
            if (p.toString()
                .endsWith(".png")) {
                if (!samePixels(ImageIO.read(p.toFile()), ImageIO.read(committed.toFile()))) differ.add(rel.toString());
            } else assertArrayEquals(Files.readAllBytes(committed), Files.readAllBytes(p), rel.toString());
        }
        assertEquals(0, differ.size(), tool + ": committed textures that differ from what it draws: " + differ);
    }

    private static boolean samePixels(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++) {
            int p = a.getRGB(x, y), q = b.getRGB(x, y);
            // a transparent pixel is transparent whatever colour it carries
            if (p >>> 24 == 0 && q >>> 24 == 0) continue;
            if (p != q) return false;
        }
        return true;
    }
}
