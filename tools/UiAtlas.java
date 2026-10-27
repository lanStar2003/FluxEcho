import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Draws the atlas of the new UI ("clear-sky astrolabe", ui/FINAL.md section 1.8, 2.2) and writes its map. Every icon,
 * micro icon and FluxMono glyph is a stroke table in a small path language (below), drawn with Java2D; nine-slices,
 * glows and textures are computed. No font file is read, so every machine draws the same picture.
 * <p>
 * Output, from the repository root: {@code java tools/UiAtlas.java} writes
 * {@code src/main/resources/assets/fluxecho/textures/gui/ui/atlas@4x.png} (1024 x 1024), {@code atlas@2x.png} (512 x
 * 512, the 4x picture box-filtered to half), their {@code .png.mcmeta} (linear filtering, clamped edges) and
 * {@code src/main/java/com/fluxecho/logic/ui/AtlasMap.java}, the texel rectangles the game reads. {@code --check} only
 * compares. {@code AtlasTest} compiles this file and checks the committed map and pictures against it.
 * <p>
 * The path language, in grid units (icons 16 x 16, micro icons 8 x 8, glyphs 4 x 8 with the baseline at 8): {@code M x
 * y} move, {@code L x y} line, {@code A cx cy r a0 a1} arc (degrees, counter-clockwise from east, y up, sampled from
 * a0 to a1), {@code Z} close, {@code O cx cy r} circle, {@code E cx cy rx ry rot} ellipse, {@code R x y w h}
 * rectangle; {@code D cx cy r} filled dot, {@code B x y w h} filled box, {@code P x y x y ...} filled polygon;
 * {@code W w} stroke width; {@code S} strokes and {@code F} fills the path so far. Whatever path is left at the end is
 * stroked. Icons are white; the game tints them.
 */
public class UiAtlas {

    static final String TEX = "src/main/resources/assets/fluxecho/textures/gui/ui/";
    static final String MAP = "src/main/java/com/fluxecho/logic/ui/AtlasMap.java";
    static final int SIZE = 1024;

    // ------------------------------------------------------------------ regions (texels of the 4x picture)

    static final String[] REGION_NAMES = { "ICONS", "MICRO", "MONO_SMALL", "MONO_LARGE", "NINE", "GLOW",
        "MONO_SMALL_MORE", "TEXTURE", "RESERVE" };
    static final int[][] REGIONS = { { 0, 0, 1024, 384 }, { 0, 384, 640, 32 }, { 0, 416, 512, 128 },
        { 512, 416, 512, 288 }, { 0, 544, 256, 128 }, { 256, 544, 256, 128 }, { 0, 672, 512, 32 },
        { 0, 704, 1024, 128 }, { 0, 832, 1024, 192 } };

    static final int ICON = 64, MICRO = 32, SMALL_W = 20, SMALL_H = 32, LARGE_W = 48, LARGE_H = 96;
    /** Small glyphs per row of the small region, and the glyphs that fit there before the spill strip. */
    static final int SMALL_COLS = 25, SMALL_MAIN = 100, LARGE_COLS = 10;

    // ------------------------------------------------------------------ icons (1.8), 16 x 16 grid, stroke 1.5

    /** The octagon of the nexus, used by several sigils. */
    static final String OCT = "M 5.5 1.5 L 10.5 1.5 L 14.5 5.5 L 14.5 10.5 L 10.5 14.5 L 5.5 14.5 L 1.5 10.5 L 1.5 5.5 Z ";
    static final String CLOUD = "M 4.5 12.5 L 11.8 12.5 A 11.8 10.3 2.2 -90 90 A 8.4 8 3.4 20 160 A 4.5 10.2 2.3 95 270 Z ";

    static final String[][] SYSTEM = { { "close", "M 4 4 L 12 12 M 12 4 L 4 12" },
        { "back", "M 13 8 L 3.5 8 M 7.5 4 L 3.5 8 L 7.5 12" }, { "expand", "M 4 6 L 8 10 L 12 6" },
        { "collapse", "M 4 10 L 8 6 L 12 10" },
        { "console", "M 3.5 12.5 L 12.5 3.5 M 8 3.5 L 12.5 3.5 L 12.5 8 M 3.5 8 L 3.5 12.5 L 8 12.5" },
        { "menu", "M 3 4.5 L 13 4.5 M 3 8 L 13 8 M 3 11.5 L 13 11.5" },
        { "search", "O 7 7 4.2 M 10 10 L 13.5 13.5" },
        { "filter", "M 2.5 3 L 13.5 3 L 9.5 8 L 9.5 13 L 6.5 11.5 L 6.5 8 Z" },
        { "sort_up", "M 5 13 L 5 3 M 2.5 5.5 L 5 3 L 7.5 5.5 M 9.5 5 L 13.5 5 M 9.5 8.5 L 12.5 8.5 M 9.5 12 L 11.5 12" },
        { "sort_down", "M 5 3 L 5 13 M 2.5 10.5 L 5 13 L 7.5 10.5 M 9.5 4 L 11.5 4 M 9.5 7.5 L 12.5 7.5 M 9.5 11 L 13.5 11" },
        { "zoom_in", "O 7 7 4.2 M 10 10 L 13.5 13.5 M 5 7 L 9 7 M 7 5 L 7 9" },
        { "zoom_out", "O 7 7 4.2 M 10 10 L 13.5 13.5 M 5 7 L 9 7" },
        { "fit", "M 3 6 L 3 3 L 6 3 M 10 3 L 13 3 L 13 6 M 13 10 L 13 13 L 10 13 M 6 13 L 3 13 L 3 10" },
        { "locate", "O 8 8 4.5 M 8 1.5 L 8 5 M 8 11 L 8 14.5 M 1.5 8 L 5 8 M 11 8 L 14.5 8 D 8 8 1.1" },
        { "pin", "M 5.5 2.5 L 10.5 2.5 M 6.8 2.5 L 6.8 6.8 L 4.5 9.5 L 11.5 9.5 L 9.2 6.8 L 9.2 2.5 M 8 9.5 L 8 14" },
        { "unpin", "M 5.5 2.5 L 10.5 2.5 M 6.8 2.5 L 6.8 6.8 L 4.5 9.5 L 11.5 9.5 L 9.2 6.8 L 9.2 2.5 M 8 9.5 L 8 14 "
            + "M 2.5 2.5 L 13.5 13.5" },
        { "settings", "O 8 8 4.3 O 8 8 1.7 M 8 1.5 L 8 3.7 M 8 12.3 L 8 14.5 M 1.5 8 L 3.7 8 M 12.3 8 L 14.5 8 "
            + "M 3.4 3.4 L 5 5 M 11 11 L 12.6 12.6 M 12.6 3.4 L 11 5 M 5 11 L 3.4 12.6" },
        { "info", "O 8 8 6.2 M 8 7 L 8 11.5 D 8 4.7 1" },
        { "help", "O 8 8 6.2 M 6 6.4 A 8 6.2 2 165 -45 L 8 9.4 D 8 11.6 1" },
        { "warning", "M 8 1.8 L 14.5 13.5 L 1.5 13.5 Z M 8 6 L 8 9.5 D 8 11.5 0.95" },
        { "error", "O 8 8 6.2 M 5.6 5.6 L 10.4 10.4 M 10.4 5.6 L 5.6 10.4" },
        { "lock", "R 3.5 7.2 9 6.8 M 5.5 7.2 L 5.5 5.2 A 8 5.2 2.5 180 0 L 10.5 7.2 D 8 10.4 0.9" },
        { "unlock", "R 3.5 7.2 9 6.8 M 5.5 7.2 L 5.5 5.2 A 8 5.2 2.5 180 20 D 8 10.4 0.9" },
        { "play", "M 5 3 L 12.5 8 L 5 13 Z" }, { "pause", "W 2 M 5.5 3.5 L 5.5 12.5 M 10.5 3.5 L 10.5 12.5" },
        { "stop", "R 4 4 8 8" },
        { "retry", "A 8 8 5 135 -165 M 2.6 4.3 L 4.5 4.5 L 4.7 2.4" },
        { "take_out", "M 8 10 L 8 2.5 M 5 5.5 L 8 2.5 L 11 5.5 M 2.5 9 L 2.5 13.5 L 13.5 13.5 L 13.5 9" },
        { "put_in", "M 8 2.5 L 8 10 M 5 7 L 8 10 L 11 7 M 2.5 9 L 2.5 13.5 L 13.5 13.5 L 13.5 9" },
        { "take_all", "M 5.5 9.5 L 5.5 2.8 M 3.3 5 L 5.5 2.8 L 7.7 5 M 10.5 9.5 L 10.5 2.8 M 8.3 5 L 10.5 2.8 "
            + "L 12.7 5 M 2.5 9 L 2.5 13.5 L 13.5 13.5 L 13.5 9" },
        { "copy", "R 5.5 5.5 8 8 M 3 10.5 L 3 3 L 10.5 3" },
        { "link", "M 6.7 9.3 L 9.3 6.7 M 7.2 4.8 L 8.6 3.4 A 10.6 5.4 2.8 135 -45 L 11.2 8.8 M 8.8 11.2 L 7.4 12.6 "
            + "A 5.4 10.6 2.8 -45 -225 L 4.8 7.2" },
        { "external", "M 9 3 L 13 3 L 13 7 M 13 3 L 7.5 8.5 M 11 9.5 L 11 13 L 3 13 L 3 5 L 6.5 5" },
        { "keyboard", "R 1.8 4.5 12.4 7.5 M 4.3 7 L 5.3 7 M 7.5 7 L 8.5 7 M 10.7 7 L 11.7 7 M 5 9.6 L 11 9.6" },
        { "eye", "A 8 10.5 6.5 157.4 22.6 A 8 5.5 6.5 -22.6 -157.4 O 8 8 2" },
        { "eye_off", "A 8 10.5 6.5 157.4 22.6 A 8 5.5 6.5 -22.6 -157.4 O 8 8 2 M 2.5 13.5 L 13.5 2.5" },
        { "refresh", "A 8 8 5 20 160 M 1.8 6.1 L 3.3 8 L 5.2 6.4 A 8 8 5 200 340 M 14.2 9.9 L 12.7 8 L 10.8 9.6" } };

    static final String[][] DOMAIN = { { "eu", "M 9.5 1.8 L 4 9 L 8 9 L 6.5 14.2 L 12 7 L 8 7 Z" },
        { "compute", "R 4.5 4.5 7 7 R 6.6 6.6 2.8 2.8 M 6 1.8 L 6 4.5 M 10 1.8 L 10 4.5 M 6 11.5 L 6 14.2 M 10 11.5 "
            + "L 10 14.2 M 1.8 6 L 4.5 6 M 1.8 10 L 4.5 10 M 11.5 6 L 14.2 6 M 11.5 10 L 14.2 10" },
        { "record", "M 6 2.5 L 10 2.5 L 9.2 7.5 L 13 8.5 L 13 11 L 3 11 L 3 8.5 L 6.8 7.5 Z M 3 13.8 L 13 13.8" },
        { "moon_0", "#moon 0" }, { "moon_1", "#moon 1" }, { "moon_2", "#moon 2" }, { "moon_3", "#moon 3" },
        { "moon_4", "#moon 4" }, { "moon_5", "#moon 5" }, { "moon_6", "#moon 6" }, { "moon_7", "#moon 7" },
        { "star", "M 8 1.5 L 9.6 6.4 L 14.5 8 L 9.6 9.6 L 8 14.5 L 6.4 9.6 L 1.5 8 L 6.4 6.4 Z" },
        { "route", "M 3 13 L 8 8 L 8 3.2 M 5.6 5.6 L 8 3.2 L 10.4 5.6 D 3 13 1.4 D 8 8 1.2" },
        { "manifest", "M 8 1.8 L 13.4 4.9 L 13.4 11.1 L 8 14.2 L 2.6 11.1 L 2.6 4.9 Z D 8 8 1.6" },
        { "build", "M 1.8 14 L 14.2 14 M 3 14 A 9 14 6 180 112 R 9.5 3 3.5 3.5 M 11.2 6.5 L 11.2 9" },
        { "clear", "M 13 2.5 L 8 7.5 M 5 6.8 L 9.2 11 L 7 14 L 2 9 Z M 4.5 10.5 L 6.5 12.5" },
        { "blocked", "O 8 8 6.2 M 4.6 8 L 11.4 8" },
        { "demolish", "M 2.8 13.2 L 8.6 7.4 M 6.8 3.8 L 12.2 9.2 L 13.8 7.6 L 8.4 2.2 Z" },
        { "recycle_bin", "R 3 6 10 8 M 2 6 L 14 6 M 6 6 L 6 3 L 10 3 L 10 6 M 6.5 8.5 L 6.5 11.5 M 9.5 8.5 L 9.5 11.5" },
        { "shield", "M 8 1.8 L 13 3.8 L 13 8.2 L 8 14.2 L 3 8.2 L 3 3.8 Z" },
        { "sample", "R 2.5 2.5 11 11 R 5 5 6 6" },
        { "phase_core", OCT + "P 8 4.8 11.2 8 8 11.2 4.8 8" },
        { "overlay", "M 2 6.5 L 8 3.5 L 14 6.5 L 8 9.5 Z M 2 9.8 L 8 12.8 L 14 9.8" },
        { "voltage", "M 2.5 4 L 11 4 L 14 8 L 11 12 L 2.5 12 Z D 5.6 8 1.1" },
        { "team", "O 6 5 2.2 M 2 13.5 A 6 13.5 4 180 0 O 11.5 6 1.8 M 11.8 9.6 A 11.5 13.5 3.3 75 0" },
        { "bind", "O 5 8 3 M 8 8 L 14 8 M 12 8 L 12 11 M 14 8 L 14 10.5" },
        { "temperature", "O 8 11.5 2.5 M 6.5 9.4 L 6.5 3 L 9.5 3 L 9.5 9.4 D 8 11.5 1.2 M 8 10.5 L 8 6" },
        { "humidity", "M 8 1.8 L 11.6 8.1 A 8 10 4 30 -210 Z" },
        { "sun", "O 8 8 3 M 8 1.5 L 8 3.4 M 8 12.6 L 8 14.5 M 1.5 8 L 3.4 8 M 12.6 8 L 14.5 8 M 3.4 3.4 L 4.7 4.7 "
            + "M 11.3 11.3 L 12.6 12.6 M 12.6 3.4 L 11.3 4.7 M 4.7 11.3 L 3.4 12.6" },
        { "rain", "M 4.5 9.5 L 11.8 9.5 A 11.8 7.3 2.2 -90 90 A 8.4 5 3.4 20 160 A 4.5 7.2 2.3 95 270 Z "
            + "M 5 11.8 L 4 14 M 8 11.8 L 7 14 M 11 11.8 L 10 14" },
        { "thunder", "M 4.5 9.5 L 11.8 9.5 A 11.8 7.3 2.2 -90 90 A 8.4 5 3.4 20 160 A 4.5 7.2 2.3 95 270 Z "
            + "M 9 10.5 L 7 12.8 L 9 12.8 L 7.6 15" },
        { "day", "M 1.8 12 L 14.2 12 M 4.3 12 A 8 12 3.7 180 0 M 8 4 L 8 6 M 3 6.6 L 4.5 7.9 M 13 6.6 L 11.5 7.9" },
        { "night", "#night" }, { "mana", "M 8 1.8 L 12.2 8 L 8 14.2 L 3.8 8 Z M 8 5.5 L 8 10.5" },
        { "aspect", "O 8 8 6.2 M 8 4 L 11.5 10.2 L 4.5 10.2 Z" },
        { "blood", "M 8 1.8 L 11.6 8.1 A 8 10 4 30 -210 Z F" },
        { "prey", "D 8 10.6 2.7 D 4.2 7.2 1.35 D 6.6 4.6 1.35 D 9.4 4.6 1.35 D 11.8 7.2 1.35" },
        { "seed", "M 8 2 L 11.5 6 L 11.5 10 L 8 14 L 4.5 10 L 4.5 6 Z M 8 5 L 8 11" },
        { "bee", "M 6 7.5 L 10 7.5 L 11 9 L 11 12 L 8 14 L 5 12 L 5 9 Z M 5 10 L 11 10 M 5.5 12 L 10.5 12 "
            + "O 5.4 4.6 2 O 10.6 4.6 2" },
        { "foil", "M 3 3 L 10 3 L 13 6 L 13 13 L 3 13 Z M 10 3 L 10 6 L 13 6" },
        { "unfold", "M 6.5 6.5 L 2.8 2.8 M 2.8 6.3 L 2.8 2.8 L 6.3 2.8 M 9.5 9.5 L 13.2 13.2 M 13.2 9.7 L 13.2 13.2 "
            + "L 9.7 13.2" },
        { "book", "M 1.8 4 L 6 3.4 L 8 4.4 L 10 3.4 L 14.2 4 L 14.2 13 L 10 12.4 L 8 13.4 L 6 12.4 L 1.8 13 Z "
            + "M 8 4.4 L 8 13.4" },
        { "card", "R 2.5 4 11 8 M 4.5 6.5 L 9 6.5 M 4.5 9 L 11.5 9" } };

    /** The 23 building sigils (ui 1.3.4, in the order of its table). */
    static final String[][] SIGILS = { { "sigil.drift_capsule", OCT + "D 8 8 1.8 M 9.6 13.6 L 13.6 9.6 M 12 14.3 L 14.3 12" },
        { "sigil.nexus", OCT + "P 8 4.8 11.2 8 8 11.2 4.8 8" },
        { "sigil.library", "R 2.5 3 2.8 10.5 R 6.3 3 2.8 10.5 M 10.2 4.2 L 12.4 3.4 L 14.2 13 L 12 13.8 Z" },
        { "sigil.eco_dome", "M 1.8 13 L 14.2 13 M 2.8 13 A 8 13 5.2 180 0 M 8 12.6 L 8 9.4 M 8 10.6 L 10 8.6 "
            + "M 8 11.2 L 6.3 9.5" },
        { "sigil.mana_fountain", "M 3.5 11.5 L 12.5 11.5 L 11 13.8 L 5 13.8 Z M 8 11.5 L 8 6.6 O 8 4.2 1.7 "
            + "O 4.9 6.8 1.4 O 11.1 6.8 1.4" },
        { "sigil.arcane_court", "M 8 1.5 L 10.6 12.6 L 5.4 12.6 Z A 8 10.8 5.6 210 -30" },
        { "sigil.blood_covenant", "O 8 8 6.3 S M 8 4 L 10 7.6 A 8 9 2.3 30 -210 Z F" },
        { "sigil.hunting_ground", "M 1.8 7 L 5 3.5 L 11 3.5 L 14.2 7 Z M 3 7 L 3 13.5 M 13 7 L 13 13.5 "
            + "M 5 12.6 L 11 9.6 M 9 9.4 L 11 9.6 L 10.2 11.4" },
        { "sigil.resonance_gate", "R 2.2 3.5 3 10.5 R 10.8 3.5 3 10.5 P 8 5 9.8 8.2 8 11.4 6.2 8.2" },
        { "sigil.dual_foil_vault", "M 3 4.2 L 9 2.6 L 9 12.2 L 3 13.8 Z M 9 2.6 L 13.8 5 L 13.8 14 L 9 12.2" },
        { "sigil.dome_generator", "M 1.6 13 L 14.4 13 M 1.6 13 A 8 13 6.4 180 0 M 4 8.8 L 8 10.6 L 12 8.8 "
            + "M 8 10.6 L 8 13 M 8 6.6 L 8 10.6" },
        { "sigil.tidal_well", "O 8 7 5.4 O 8 7 3.4 O 8 7 1.4 M 1.8 13.8 L 4.4 12.6 L 7 13.8 L 9.6 12.6 L 12.2 13.8 "
            + "L 14.2 12.9" },
        { "sigil.deep_root", "M 2 4 L 14 4 M 8 4 L 8 14.2 M 8 7 L 4.5 10.5 L 4.5 13.6 M 8 7 L 11.5 10.5 L 11.5 13.6" },
        { "sigil.earth_core", "O 8 8 5.2 D 8 8 2.6 M 8 0.8 L 8 2.4 M 8 13.6 L 8 15.2 M 0.8 8 L 2.4 8 M 13.6 8 "
            + "L 15.2 8" },
        { "sigil.sophon_array", "M 8 1.5 L 13.6 4.75 L 13.6 11.25 L 8 14.5 L 2.4 11.25 L 2.4 4.75 Z "
            + "M 5 6.2 L 11 6.2 L 8 11.2 Z M 8 1.5 L 5 6.2 M 8 1.5 L 11 6.2 M 2.4 4.75 L 5 6.2 M 13.6 4.75 L 11 6.2 "
            + "M 8 14.5 L 8 11.2" },
        { "sigil.holo_table", "M 1.8 13.6 L 14.2 13.6 M 4 13.6 L 4 10.6 M 12 13.6 L 12 10.6 M 3 10.6 L 13 10.6 "
            + "M 6.2 10.6 L 2.6 2.6 L 13.4 2.6 L 9.8 10.6" },
        { "sigil.sky_sails", "M 5 14.5 L 5 1.5 M 5 2.6 L 12.6 11.2 L 5 11.2 M 6.4 2 L 13.6 6.2 L 6.4 7.4" },
        { "sigil.observatory", "O 8 8 6.4 E 8 8 6.4 2.4 -30 D 8 8 1.2" },
        { "sigil.droplet_hangar", "M 8 1.5 L 11.7 8 A 8 10 4.2 28 -208 Z M 6.4 10.6 A 8 10 2.2 185 250" },
        { "sigil.weather_station", CLOUD + "M 9.8 3 L 14.6 3 M 13.2 1.6 L 14.6 3 L 13.2 4.4 M 9.8 1.6 L 11 3 L 9.8 4.4" },
        { "sigil.orbital_anchor", "R 6.5 6.5 3 3 R 1.4 6.8 3.6 2.4 R 11 6.8 3.6 2.4 M 5 8 L 6.5 8 M 9.5 8 L 11 8 "
            + "M 8 6.5 L 8 4.2 O 8 3 1.1" },
        { "sigil.source_heart", "M 8 13.6 L 3.2 7.6 A 5.5 6 2.8 215 20 A 10.5 6 2.8 160 -35 Z "
            + "P 8 6.2 8.6 7.6 10 8 8.6 8.4 8 9.8 7.4 8.4 6 8 7.4 7.6" },
        { "sigil.leap_ring", "E 8 6.6 4 5.1 0 D 8 14.3 1.1" } };

    /** Micro icons (8 x 8 grid, stroke 1). */
    static final String[][] MICROS = { { "micro.dot", "D 4 4 1.5" }, { "micro.diamond", "P 4 1 7 4 4 7 1 4" },
        { "micro.check", "M 1.3 4.4 L 3.3 6.4 L 6.9 2" }, { "micro.cross", "M 2 2 L 6 6 M 6 2 L 2 6" },
        { "micro.lock", "B 1.5 3.8 5 3.6 M 2.8 3.8 L 2.8 2.6 A 4 2.6 1.2 180 0 L 5.2 3.8" },
        { "micro.bolt", "P 4.8 0.6 1.6 4.6 3.8 4.6 3 7.4 6.4 3.4 4.2 3.4" },
        { "micro.star", "P 4 0.5 4.9 3.1 7.5 4 4.9 4.9 4 7.5 3.1 4.9 0.5 4 3.1 3.1" }, { "micro.moon", "#nightmicro" },
        { "micro.up", "P 4 1.5 7 5.6 1 5.6" }, { "micro.down", "P 4 6.5 7 2.4 1 2.4" },
        { "micro.left", "P 1.5 4 5.6 1 5.6 7" }, { "micro.right", "P 6.5 4 2.4 1 2.4 7" },
        { "micro.plus", "M 4 1 L 4 7 M 1 4 L 7 4" }, { "micro.minus", "M 1 4 L 7 4" },
        { "micro.exclaim", "W 1.3 M 4 1 L 4 4.8 D 4 6.6 0.8" }, { "micro.clock", "O 4 4 3 M 4 2.2 L 4 4 L 5.5 4.8" },
        { "micro.chain", "O 2.9 5.1 1.8 O 5.1 2.9 1.8" }, { "micro.eye", "E 4 4 3.4 2 0 D 4 4 1" },
        { "micro.flag", "M 1.5 7.5 L 1.5 1 M 1.5 1.3 L 6.5 1.3 L 5 2.8 L 6.5 4.3 L 1.5 4.3" },
        { "micro.pin", "M 2.5 1 L 5.5 1 M 3 1 L 3 3.4 L 1.8 4.6 L 6.2 4.6 L 5 3.4 L 5 1 M 4 4.6 L 4 7.5" } };

    // ------------------------------------------------------------------ FluxMono (1.4.1, 2.4): 4 x 8 skeletons

    static final Map<Character, String> GLYPH = new HashMap<>();

    static void g(char c, String path) {
        GLYPH.put(c, path);
    }

    static {
        g('0', "M 1 0 L 3 0 L 4 1 L 4 7 L 3 8 L 1 8 L 0 7 L 0 1 Z M 3.1 2 L 0.9 6");
        g('1', "M 0.8 1.6 L 2.4 0 L 2.4 8 M 0.6 8 L 4 8");
        g('2', "M 0 1 L 1 0 L 3 0 L 4 1 L 4 3.4 L 0 7 L 0 8 L 4 8");
        g('3', "M 0 0 L 4 0 L 4 3 L 3 4 L 1.4 4 M 3 4 L 4 5 L 4 7 L 3 8 L 0 8");
        g('4', "M 3 8 L 3 0 L 0 5.5 L 4 5.5");
        g('5', "M 4 0 L 0 0 L 0 3.6 L 3 3.6 L 4 4.6 L 4 7 L 3 8 L 0 8");
        g('6', "M 3.6 0 L 1 0 L 0 1 L 0 7 L 1 8 L 3 8 L 4 7 L 4 4.6 L 3 3.6 L 0 3.6");
        g('7', "M 0 0 L 4 0 L 4 1 L 1.4 8");
        g('8', "M 1 0 L 3 0 L 4 1 L 4 2.8 L 3 3.8 L 1 3.8 L 0 2.8 L 0 1 Z "
            + "M 1 3.8 L 0 4.8 L 0 7 L 1 8 L 3 8 L 4 7 L 4 4.8 L 3 3.8");
        g('9', "M 4 4.4 L 1 4.4 L 0 3.4 L 0 1 L 1 0 L 3 0 L 4 1 L 4 7 L 3 8 L 0.4 8");
        g('A', "M 0 8 L 0 1 L 1 0 L 3 0 L 4 1 L 4 8 M 0 4.6 L 4 4.6");
        g('B', "M 0 0 L 3 0 L 4 1 L 4 2.8 L 3 3.8 L 4 4.8 L 4 7 L 3 8 L 0 8 Z M 0 3.8 L 3 3.8");
        g('C', "M 4 0 L 1 0 L 0 1 L 0 7 L 1 8 L 4 8");
        g('D', "M 0 0 L 3 0 L 4 1 L 4 7 L 3 8 L 0 8 Z");
        g('E', "M 4 0 L 0 0 L 0 8 L 4 8 M 0 4 L 3 4");
        g('F', "M 4 0 L 0 0 L 0 8 M 0 4 L 3 4");
        g('G', "M 4 0 L 1 0 L 0 1 L 0 7 L 1 8 L 4 8 L 4 4.4 L 2.4 4.4");
        g('H', "M 0 0 L 0 8 M 4 0 L 4 8 M 0 4 L 4 4");
        g('I', "M 1 0 L 3 0 M 2 0 L 2 8 M 1 8 L 3 8");
        g('J', "M 1.4 0 L 4 0 L 4 7 L 3 8 L 1 8 L 0 7 L 0 6");
        g('K', "M 0 0 L 0 8 M 4 0 L 0.6 4 L 4 8");
        g('L', "M 0 0 L 0 8 L 4 8");
        g('M', "M 0 8 L 0 0 L 2 3.2 L 4 0 L 4 8");
        g('N', "M 0 8 L 0 0 L 4 8 L 4 0");
        g('O', "M 1 0 L 3 0 L 4 1 L 4 7 L 3 8 L 1 8 L 0 7 L 0 1 Z");
        g('P', "M 0 8 L 0 0 L 3 0 L 4 1 L 4 3 L 3 4 L 0 4");
        g('Q', "M 1 0 L 3 0 L 4 1 L 4 7 L 3 8 L 1 8 L 0 7 L 0 1 Z M 2.4 6 L 4 8.4");
        g('R', "M 0 8 L 0 0 L 3 0 L 4 1 L 4 3 L 3 4 L 0 4 M 2 4 L 4 8");
        g('S', "M 4 0 L 1 0 L 0 1 L 0 3 L 1 4 L 3 4 L 4 5 L 4 7 L 3 8 L 0 8");
        g('T', "M 0 0 L 4 0 M 2 0 L 2 8");
        g('U', "M 0 0 L 0 7 L 1 8 L 3 8 L 4 7 L 4 0");
        g('V', "M 0 0 L 2 8 L 4 0");
        g('W', "M 0 0 L 0.7 8 L 2 3.6 L 3.3 8 L 4 0");
        g('X', "M 0 0 L 4 8 M 4 0 L 0 8");
        g('Y', "M 0 0 L 2 4 L 4 0 M 2 4 L 2 8");
        g('Z', "M 0 0 L 4 0 L 0 8 L 4 8");
        g('!', "M 2 0 L 2 5.4 B 1.4 6.8 1.2 1.2");
        g('"', "M 1 0 L 1 2 M 3 0 L 3 2");
        g('#', "M 1.3 0.6 L 1.3 7.4 M 2.7 0.6 L 2.7 7.4 M 0 2.6 L 4 2.6 M 0 5.4 L 4 5.4");
        g('$', "M 4 1 L 1 1 L 0 2 L 0 3 L 1 4 L 3 4 L 4 5 L 4 6 L 3 7 L 0 7 M 2 -0.2 L 2 8.2");
        g('%', "M 4 0 L 0 8 B 0 0.4 1.4 1.4 B 2.6 6.2 1.4 1.4");
        g('&', "M 4 8 L 0.6 3 L 0.6 1 L 1.6 0 L 2.6 0 L 3.6 1 L 3.6 2.4 L 0 5.6 L 0 7 L 1 8 L 2.4 8 L 4 5.6");
        g('\'', "M 2 0 L 2 2.4");
        g('(', "M 3 0 L 1.6 1.4 L 1.6 6.6 L 3 8");
        g(')', "M 1 0 L 2.4 1.4 L 2.4 6.6 L 1 8");
        g('*', "M 2 1.2 L 2 6.8 M 0.2 2.6 L 3.8 5.4 M 3.8 2.6 L 0.2 5.4");
        g('+', "M 2 2 L 2 6 M 0 4 L 4 4");
        g(',', "M 2.2 6.6 L 2.2 8 L 1.4 9.2");
        g('-', "M 0.6 4 L 3.4 4");
        g('.', "B 1.4 6.8 1.2 1.2");
        g('/', "M 4 0 L 0 8");
        g(':', "B 1.4 1.8 1.2 1.2 B 1.4 6.8 1.2 1.2");
        g(';', "B 1.4 1.8 1.2 1.2 M 2.2 6.6 L 2.2 8 L 1.4 9.2");
        g('<', "M 4 1 L 0.6 4 L 4 7");
        g('=', "M 0 2.8 L 4 2.8 M 0 5.2 L 4 5.2");
        g('>', "M 0 1 L 3.4 4 L 0 7");
        g('?', "M 0 1 L 1 0 L 3 0 L 4 1 L 4 2.8 L 2 4.4 L 2 5.4 B 1.4 6.8 1.2 1.2");
        g('@', "M 4 8 L 1 8 L 0 7 L 0 1 L 1 0 L 3 0 L 4 1 L 4 6 L 2 6 L 2 3 L 4 3");
        g('[', "M 3 0 L 1.6 0 L 1.6 8 L 3 8");
        g('\\', "M 0 0 L 4 8");
        g(']', "M 1 0 L 2.4 0 L 2.4 8 L 1 8");
        g('^', "M 0.6 2.4 L 2 0.4 L 3.4 2.4");
        g('_', "M 0 9 L 4 9");
        g('`', "M 1.4 0 L 2.6 1.4");
        g('{', "M 3 0 L 2.2 0 L 1.6 0.6 L 1.6 3.4 L 0.6 4 L 1.6 4.6 L 1.6 7.4 L 2.2 8 L 3 8");
        g('|', "M 2 -0.4 L 2 8.4");
        g('}', "M 1 0 L 1.8 0 L 2.4 0.6 L 2.4 3.4 L 3.4 4 L 2.4 4.6 L 2.4 7.4 L 1.8 8 L 1 8");
        g('~', "M 0 4.6 L 1 3.6 L 3 4.6 L 4 3.6");
        g((char) 0x00B7, "B 1.4 3.4 1.2 1.2"); // middle dot
        g((char) 0x00D7, "M 0.6 2.6 L 3.4 5.4 M 3.4 2.6 L 0.6 5.4"); // multiplication sign
        g((char) 0x00B1, "M 2 1 L 2 5 M 0 3 L 4 3 M 0 7 L 4 7"); // plus-minus
        g((char) 0x00B0, "M 1 0 L 3 0 L 3 2 L 1 2 Z"); // degree
        g((char) 0x2190, "M 4 4 L 0.4 4 M 2 2.4 L 0.4 4 L 2 5.6"); // left arrow
        g((char) 0x2192, "M 0 4 L 3.6 4 M 2 2.4 L 3.6 4 L 2 5.6"); // right arrow
        g((char) 0x2191, "M 2 8 L 2 0.4 M 0.4 2 L 2 0.4 L 3.6 2"); // up arrow
        g((char) 0x2193, "M 2 0 L 2 7.6 M 0.4 6 L 2 7.6 L 3.6 6"); // down arrow
        g((char) 0x25C6, "P 2 1.6 4 4 2 6.4 0 4"); // black diamond
        g((char) 0x25B6, "P 0.4 1 4 4 0.4 7"); // black right-pointing triangle
        g((char) 0x2726, "P 2 0.4 2.7 3.3 4 4 2.7 4.7 2 7.6 1.3 4.7 0 4 1.3 3.3"); // four-pointed star
        g((char) 0x25A4, "R 0 0.6 4 6.8 M 0 3 L 4 3 M 0 5.4 L 4 5.4"); // square with horizontal fill
        g((char) 0x26A0, "M 2 0 L 4 7.6 L 0 7.6 Z M 2 3 L 2 5 B 1.6 6 0.8 0.8"); // warning sign
        g((char) 0x2212, "M 0.6 4 L 3.4 4"); // minus sign
    }

    static String chars(int... cps) {
        StringBuilder b = new StringBuilder();
        for (int c : cps) b.append((char) c);
        return b.toString();
    }

    /** The small glyphs in order: ASCII 0x20-0x7E, then the symbols. */
    static final String SMALL;
    static final String SMALL_EXTRA = chars(0x00B7, 0x00D7, 0x00B1, 0x00B0, 0x2190, 0x2192, 0x2191, 0x2193, 0x25C6,
        0x25B6, 0x2726, 0x25A4, 0x26A0);
    /** The large glyphs in order: digits, punctuation and the SI letters, the roman numerals. */
    static final String LARGE = "0123456789.,:%+" + chars(0x2212) + "kMGTIVX";

    static {
        StringBuilder b = new StringBuilder();
        for (char c = 0x20; c < 0x7F; c++) b.append(c);
        SMALL = b.append(SMALL_EXTRA)
            .toString();
    }

    static int smallX(int i) {
        return i < SMALL_MAIN ? i % SMALL_COLS * SMALL_W : (i - SMALL_MAIN) * SMALL_W;
    }

    static int smallY(int i) {
        return i < SMALL_MAIN ? REGIONS[2][1] + i / SMALL_COLS * SMALL_H : REGIONS[6][1];
    }

    static int largeX(int i) {
        return REGIONS[3][0] + i % LARGE_COLS * LARGE_W;
    }

    static int largeY(int i) {
        return REGIONS[3][1] + i / LARGE_COLS * LARGE_H;
    }

    // ------------------------------------------------------------------ the sprite table

    /** One sprite: its name, its texel rectangle, its nine-slice border, and what draws it. */
    static final class Sprite {

        final String name, constant;
        final int x, y, w, h, border;
        final String path;

        Sprite(String name, String constant, int x, int y, int w, int h, int border, String path) {
            this.name = name;
            this.constant = constant;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.border = border;
            this.path = path;
        }
    }

    static List<Sprite> sprites() {
        List<Sprite> out = new ArrayList<>();
        List<String[]> icons = new ArrayList<>();
        for (String[][] set : new String[][][] { SYSTEM, DOMAIN, SIGILS }) for (String[] i : set) icons.add(i);
        for (int i = 0; i < icons.size(); i++) {
            int x, y;
            if (i < 96) {
                x = i % 16 * ICON;
                y = i / 16 * ICON;
            } else {
                x = (i - 96) % 16 * ICON;
                y = REGIONS[8][1] + (i - 96) / 16 * ICON;
            }
            String name = icons.get(i)[0];
            out.add(new Sprite(name, "I_" + constant(name), x, y, ICON, ICON, 0, icons.get(i)[1]));
        }
        for (int i = 0; i < MICROS.length; i++) {
            String name = MICROS[i][0];
            out.add(
                new Sprite(
                    name,
                    "M_" + constant(name.substring("micro.".length())),
                    i * MICRO,
                    REGIONS[1][1],
                    MICRO,
                    MICRO,
                    0,
                    MICROS[i][1]));
        }
        int nx = REGIONS[4][0], ny = REGIONS[4][1], gx = REGIONS[5][0], gy = REGIONS[5][1], tx = REGIONS[7][0],
            ty = REGIONS[7][1];
        named(out, "shadow.panel", nx, ny, 80, 80, 32);
        named(out, "shadow.card", nx + 80, ny, 80, 80, 32);
        named(out, "cut.6", nx + 160, ny, 56, 56, 24);
        named(out, "cut.4", nx + 216, ny, 40, 40, 16);
        named(out, "cut.3", nx + 160, ny + 56, 32, 32, 12);
        named(out, "cut.2", nx + 192, ny + 56, 24, 24, 8);
        for (int i = 0; i < 4; i++) named(out, "dot." + i, gx + i * 64, gy, 64, 64, 0);
        named(out, "star.4", gx, gy + 64, 64, 64, 0);
        named(out, "star.8", gx + 64, gy + 64, 64, 64, 0);
        named(out, "halo", gx + 128, gy + 64, 64, 64, 0);
        named(out, "spark", gx + 192, gy + 64, 16, 16, 0);
        named(out, "grain", tx, ty, 128, 128, 0);
        named(out, "stripes", tx + 128, ty, 64, 64, 0);
        for (int i = 0; i < 3; i++) named(out, "nebula." + i, tx + 192 + i * 128, ty, 128, 128, 0);
        named(out, "galaxy", tx + 576, ty, 256, 64, 0);
        named(out, "backing", tx + 832, ty, 32, 32, 16);
        return out;
    }

    static void named(List<Sprite> out, String name, int x, int y, int w, int h, int border) {
        out.add(new Sprite(name, "S_" + constant(name), x, y, w, h, border, null));
    }

    static String constant(String name) {
        return name.replace('.', '_')
            .toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ drawing

    public static void main(String[] args) throws IOException {
        boolean check = args.length > 0 && args[0].equals("--check");
        BufferedImage four = atlas4x(), two = half(four);
        String src = source();
        if (check) {
            boolean ok = src.equals(read(MAP));
            ok &= samePixels(four, ImageIO.read(Paths.get(TEX + "atlas@4x.png").toFile()));
            ok &= samePixels(two, ImageIO.read(Paths.get(TEX + "atlas@2x.png").toFile()));
            System.out.println(ok ? "the atlas is up to date" : "the atlas is stale; run java tools/UiAtlas.java");
            if (!ok) System.exit(1);
            return;
        }
        Files.createDirectories(Paths.get(TEX));
        ImageIO.write(four, "png", Paths.get(TEX + "atlas@4x.png").toFile());
        ImageIO.write(two, "png", Paths.get(TEX + "atlas@2x.png").toFile());
        String meta = "{\n  \"texture\": { \"blur\": true, \"clamp\": true }\n}\n";
        Files.write(Paths.get(TEX + "atlas@4x.png.mcmeta"), meta.getBytes(StandardCharsets.UTF_8));
        Files.write(Paths.get(TEX + "atlas@2x.png.mcmeta"), meta.getBytes(StandardCharsets.UTF_8));
        Files.write(Paths.get(MAP), src.getBytes(StandardCharsets.UTF_8));
        System.out.println("wrote the atlas (" + sprites().size() + " sprites, " + SMALL.length() + " + "
            + LARGE.length() + " glyphs) and " + MAP);
    }

    static String read(String path) throws IOException {
        Path p = Paths.get(path);
        return Files.exists(p) ? new String(Files.readAllBytes(p), StandardCharsets.UTF_8) : "";
    }

    static boolean samePixels(BufferedImage a, BufferedImage b) {
        if (b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++) if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
        return true;
    }

    static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        return g;
    }

    /** The 4x atlas: white shapes with alpha, transparent elsewhere. */
    public static BufferedImage atlas4x() {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        for (Sprite s : sprites()) {
            if (s.path != null) {
                boolean micro = s.name.startsWith("micro.");
                double unit = micro ? MICRO / 8.0 : ICON / 16.0;
                drawCell(g, s, unit, micro ? 1.0 : 1.5);
            } else computed(img, s);
        }
        for (int i = 0; i < SMALL.length(); i++) glyph(g, SMALL.charAt(i), smallX(i), smallY(i), false);
        for (int i = 0; i < LARGE.length(); i++) glyph(g, LARGE.charAt(i), largeX(i), largeY(i), true);
        g.dispose();
        return img;
    }

    static void drawCell(Graphics2D g, Sprite s, double unit, double width) {
        Graphics2D c = (Graphics2D) g.create();
        c.clipRect(s.x, s.y, s.w, s.h);
        if (s.path.startsWith("#")) special(c, s, unit);
        else {
            AffineTransform t = new AffineTransform(unit, 0, 0, unit, s.x, s.y);
            path(c, s.path, t, width * unit, BasicStroke.CAP_BUTT);
        }
        c.dispose();
    }

    /** One glyph in its cell: small glyphs 4-texel strokes, large 10-texel; lower case as small capitals. */
    static void glyph(Graphics2D g, char ch, int cx, int cy, boolean large) {
        char key = ch < 128 ? Character.toUpperCase(ch) : ch;
        String path = GLYPH.get(key);
        if (path == null) return;
        boolean smallCap = ch >= 'a' && ch <= 'z';
        double sx = large ? 8.5 : 3, sy = large ? 9.4 : 3, ox = large ? 7 : 2, oy = large ? 7 : 2;
        // small capitals: the cap height shrinks to 5/8 and sits on the baseline
        double syc = smallCap ? sy * 5 / 8 : sy, oyc = smallCap ? oy + 3 * sy : oy;
        Graphics2D c = (Graphics2D) g.create();
        c.clipRect(cx, cy, large ? LARGE_W : SMALL_W, large ? LARGE_H : SMALL_H);
        AffineTransform t = new AffineTransform(sx, 0, 0, syc, cx + ox, cy + oyc);
        path(c, path, t, large ? 10 : 4, BasicStroke.CAP_SQUARE);
        c.dispose();
    }

    /**
     * Draws a path of the little language through {@code t} (grid units to texels); {@code width} is the stroke
     * width in texels (the {@code W} command gives it in units).
     */
    static void path(Graphics2D g, String src, AffineTransform t, double width, int cap) {
        String[] tok = src.trim()
            .split("\\s+");
        Path2D.Double p = new Path2D.Double();
        boolean open = false;
        double unit = Math.sqrt(Math.abs(t.getDeterminant()));
        double w = width;
        int i = 0;
        while (i < tok.length) {
            String cmd = tok[i++];
            switch (cmd) {
                case "M" -> {
                    Point2D a = t.transform(new Point2D.Double(num(tok[i]), num(tok[i + 1])), null);
                    p.moveTo(a.getX(), a.getY());
                    open = true;
                    i += 2;
                }
                case "L" -> {
                    Point2D a = t.transform(new Point2D.Double(num(tok[i]), num(tok[i + 1])), null);
                    if (open) p.lineTo(a.getX(), a.getY());
                    else p.moveTo(a.getX(), a.getY());
                    open = true;
                    i += 2;
                }
                case "A" -> {
                    double cx = num(tok[i]), cy = num(tok[i + 1]), r = num(tok[i + 2]), a0 = num(tok[i + 3]),
                        a1 = num(tok[i + 4]);
                    i += 5;
                    int n = Math.max(8, (int) Math.ceil(Math.abs(a1 - a0) / 4));
                    for (int k = 0; k <= n; k++) {
                        double a = Math.toRadians(a0 + (a1 - a0) * k / n);
                        Point2D q = t.transform(new Point2D.Double(cx + r * Math.cos(a), cy - r * Math.sin(a)), null);
                        if (k == 0 && !open) p.moveTo(q.getX(), q.getY());
                        else p.lineTo(q.getX(), q.getY());
                    }
                    open = true;
                }
                case "Z" -> {
                    p.closePath();
                    open = false;
                }
                case "O" -> {
                    ellipse(p, t, num(tok[i]), num(tok[i + 1]), num(tok[i + 2]), num(tok[i + 2]), 0);
                    open = false;
                    i += 3;
                }
                case "E" -> {
                    ellipse(p, t, num(tok[i]), num(tok[i + 1]), num(tok[i + 2]), num(tok[i + 3]), num(tok[i + 4]));
                    open = false;
                    i += 5;
                }
                case "R" -> {
                    double x = num(tok[i]), y = num(tok[i + 1]), rw = num(tok[i + 2]), rh = num(tok[i + 3]);
                    i += 4;
                    poly(p, t, new double[] { x, y, x + rw, y, x + rw, y + rh, x, y + rh });
                    open = false;
                }
                case "D" -> {
                    Path2D.Double d = new Path2D.Double();
                    ellipse(d, t, num(tok[i]), num(tok[i + 1]), num(tok[i + 2]), num(tok[i + 2]), 0);
                    g.fill(d);
                    i += 3;
                }
                case "B" -> {
                    double x = num(tok[i]), y = num(tok[i + 1]), bw = num(tok[i + 2]), bh = num(tok[i + 3]);
                    i += 4;
                    Path2D.Double d = new Path2D.Double();
                    poly(d, t, new double[] { x, y, x + bw, y, x + bw, y + bh, x, y + bh });
                    g.fill(d);
                }
                case "P" -> {
                    List<Double> v = new ArrayList<>();
                    while (i < tok.length && isNum(tok[i])) v.add(num(tok[i++]));
                    double[] xy = new double[v.size()];
                    for (int k = 0; k < xy.length; k++) xy[k] = v.get(k);
                    Path2D.Double d = new Path2D.Double();
                    poly(d, t, xy);
                    g.fill(d);
                }
                case "W" -> w = num(tok[i++]) * unit;
                case "S" -> {
                    stroke(g, p, w, cap);
                    p = new Path2D.Double();
                    open = false;
                }
                case "F" -> {
                    p.closePath();
                    g.fill(p);
                    p = new Path2D.Double();
                    open = false;
                }
                default -> throw new IllegalArgumentException("unknown command " + cmd + " in " + src);
            }
        }
        stroke(g, p, w, cap);
    }

    static void stroke(Graphics2D g, Shape p, double w, int cap) {
        g.setStroke(new BasicStroke((float) w, cap, BasicStroke.JOIN_MITER, 2f));
        g.draw(p);
    }

    static boolean isNum(String s) {
        char c = s.charAt(0);
        return c == '-' || c == '.' || Character.isDigit(c);
    }

    static double num(String s) {
        return Double.parseDouble(s);
    }

    static void poly(Path2D.Double p, AffineTransform t, double[] xy) {
        for (int k = 0; k < xy.length; k += 2) {
            Point2D a = t.transform(new Point2D.Double(xy[k], xy[k + 1]), null);
            if (k == 0) p.moveTo(a.getX(), a.getY());
            else p.lineTo(a.getX(), a.getY());
        }
        p.closePath();
    }

    static void ellipse(Path2D.Double p, AffineTransform t, double cx, double cy, double rx, double ry, double rot) {
        int n = 64;
        double c = Math.cos(Math.toRadians(rot)), s = Math.sin(Math.toRadians(rot));
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * k / n, ex = rx * Math.cos(a), ey = ry * Math.sin(a);
            Point2D q = t.transform(new Point2D.Double(cx + ex * c - ey * s, cy + ex * s + ey * c), null);
            if (k == 0) p.moveTo(q.getX(), q.getY());
            else p.lineTo(q.getX(), q.getY());
        }
        p.closePath();
    }

    /** Icons drawn by code: the eight moon phases and the crescent of night. */
    static void special(Graphics2D g, Sprite s, double unit) {
        String[] tok = s.path.substring(1)
            .split(" ");
        AffineTransform t = new AffineTransform(unit, 0, 0, unit, s.x, s.y);
        switch (tok[0]) {
            case "moon" -> {
                int phase = Integer.parseInt(tok[1]);
                path(g, "O 8 8 5.6", t, 1.5 * unit, BasicStroke.CAP_BUTT);
                Path2D.Double lit = moon(8, 8, 5.6, phase * Math.PI / 4);
                if (lit != null) g.fill(t.createTransformedShape(lit));
            }
            case "night" -> g.fill(t.createTransformedShape(crescent(8, 8, 6)));
            case "nightmicro" -> g.fill(t.createTransformedShape(crescent(4, 4, 3.2)));
            default -> throw new IllegalArgumentException(s.path);
        }
    }

    /**
     * The lit part of the moon at phase angle {@code theta} (0 new, pi/2 first quarter, pi full): the limb on one
     * side and the terminator, a half ellipse whose half-width is r cos(theta). Waxing is lit on the right.
     */
    static Path2D.Double moon(double cx, double cy, double r, double theta) {
        double k = Math.cos(theta);
        if (k > 0.999) return null;
        boolean waxing = Math.sin(theta) >= 0;
        double side = waxing ? 1 : -1;
        Path2D.Double p = new Path2D.Double();
        int n = 32;
        for (int i = 0; i <= n; i++) {
            double a = -Math.PI / 2 + Math.PI * i / n;
            double x = cx + side * r * Math.cos(a), y = cy - r * Math.sin(a);
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        for (int i = n; i >= 0; i--) {
            double a = -Math.PI / 2 + Math.PI * i / n;
            p.lineTo(cx + side * k * r * Math.cos(a), cy - r * Math.sin(a));
        }
        p.closePath();
        return p;
    }

    /** A crescent: the disc less a slightly smaller disc shifted to the right, so it opens to the right. */
    static Shape crescent(double cx, double cy, double r) {
        Area a = new Area(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        double r2 = r * 0.9, shift = r * 0.55;
        a.subtract(new Area(new Ellipse2D.Double(cx + shift - r2, cy - r2 - r * 0.12, 2 * r2, 2 * r2)));
        return a;
    }

    // ------------------------------------------------------------------ computed sprites

    static void computed(BufferedImage img, Sprite s) {
        String n = s.name;
        if (n.startsWith("shadow.")) shadow(img, s, n.equals("shadow.panel") ? 32 : 24);
        else if (n.startsWith("cut.")) cut(img, s, Integer.parseInt(n.substring(4)));
        else if (n.startsWith("dot.")) field(img, s, (x, y) -> dot(Integer.parseInt(n.substring(4)), x, y));
        else if (n.equals("star.4")) field(img, s, (x, y) -> star(x, y, false));
        else if (n.equals("star.8")) field(img, s, (x, y) -> star(x, y, true));
        else if (n.equals("halo")) field(img, s, (x, y) -> {
            double r = Math.hypot(x, y);
            return Math.exp(-Math.pow((r - 0.7) / 0.11, 2)) * edge(r);
        });
        else if (n.equals("spark")) field(img, s, (x, y) -> {
            double d = Math.abs(x) + Math.abs(y);
            return clamp((0.9 - d) / 0.15) * (0.55 + 0.45 * (1 - d));
        });
        else if (n.equals("grain")) grain(img, s);
        else if (n.equals("stripes")) stripes(img, s);
        else if (n.startsWith("nebula.")) nebula(img, s, Integer.parseInt(n.substring(7)));
        else if (n.equals("galaxy")) galaxy(img, s);
        else if (n.equals("backing")) backing(img, s);
        else throw new IllegalArgumentException(n);
    }

    interface Field {

        double at(double x, double y);
    }

    /** Fills a cell with white at the alpha a field gives, x and y from -1 to 1 across the cell, 4x4 samples. */
    static void field(BufferedImage img, Sprite s, Field f) {
        for (int py = 0; py < s.h; py++) for (int px = 0; px < s.w; px++) {
            double sum = 0;
            for (int j = 0; j < 4; j++) for (int i = 0; i < 4; i++) {
                double x = ((px + (i + 0.5) / 4) / s.w) * 2 - 1, y = ((py + (j + 0.5) / 4) / s.h) * 2 - 1;
                sum += clamp(f.at(x, y));
            }
            put(img, s.x + px, s.y + py, sum / 16);
        }
    }

    static void put(BufferedImage img, int x, int y, double alpha) {
        int a = (int) Math.round(clamp(alpha) * 255);
        img.setRGB(x, y, a == 0 ? 0 : a << 24 | 0xFFFFFF);
    }

    static double clamp(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /** Fades a radial field to nothing at the cell's edge. */
    static double edge(double r) {
        return clamp((1 - r) / 0.12);
    }

    static double dot(int kind, double x, double y) {
        double r = Math.hypot(x, y);
        double v = switch (kind) {
            case 0 -> Math.exp(-Math.pow(r / 0.18, 2));
            case 1 -> Math.exp(-Math.pow(r / 0.34, 2));
            case 2 -> Math.pow(Math.max(0, 1 - r), 2);
            default -> Math.exp(-Math.pow(r / 0.55, 2)) * (1 - r);
        };
        return v * edge(r);
    }

    static double star(double x, double y, boolean eight) {
        double r = Math.hypot(x, y);
        double core = Math.exp(-Math.pow(r / 0.12, 2));
        double fall = Math.pow(Math.max(0, 1 - r), 1.6);
        double spikes = Math.exp(-Math.pow(y / 0.035, 2)) * fall + Math.exp(-Math.pow(x / 0.035, 2)) * fall;
        if (eight) {
            double u = (x + y) / Math.sqrt(2), v = (x - y) / Math.sqrt(2);
            spikes += 0.6 * (Math.exp(-Math.pow(u / 0.03, 2)) + Math.exp(-Math.pow(v / 0.03, 2)))
                * Math.pow(Math.max(0, 1 - r * 1.4), 1.6);
        }
        return (core + spikes) * edge(r);
    }

    /** A drop shadow nine-slice: full under the panel, falling off over {@code extent} texels outside it. */
    static void shadow(BufferedImage img, Sprite s, int extent) {
        int b = s.border;
        double sigma = extent / 2.2;
        for (int py = 0; py < s.h; py++) for (int px = 0; px < s.w; px++) {
            double x = px + 0.5, y = py + 0.5;
            double dx = Math.max(0, Math.max(b - x, x - (s.w - b))), dy = Math.max(0, Math.max(b - y, y - (s.h - b)));
            double d = Math.hypot(dx, dy);
            put(img, s.x + px, s.y + py, Math.exp(-Math.pow(d / sigma, 2)));
        }
    }

    /** The chamfer mask of a cut: top-right corner cut at 45 degrees, the other corners bevelled by one pixel. */
    static void cut(BufferedImage img, Sprite s, int cutPx) {
        Graphics2D g = graphics(img);
        int c = cutPx * 4, e = 4, w = s.w, h = s.h;
        Path2D.Double p = new Path2D.Double();
        poly(p, new AffineTransform(1, 0, 0, 1, s.x, s.y), new double[] { e, 0, w - c, 0, w, c, w, h - e, w - e, h, e, h, 0, h - e, 0, e });
        g.fill(p);
        g.dispose();
    }

    /** Deterministic hash noise in 0..1. */
    static double hash(long x, long y, long seed) {
        long h = x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL + seed * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 32;
        return (h >>> 11) / (double) (1L << 53);
    }

    /** Value noise on a lattice of {@code cell} texels, wrapping every {@code period} lattice cells in x. */
    static double noise(double x, double y, double cell, int period, long seed) {
        double fx = x / cell, fy = y / cell;
        long ix = (long) Math.floor(fx), iy = (long) Math.floor(fy);
        double tx = fx - ix, ty = fy - iy;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        double a = hash(Math.floorMod(ix, period), iy, seed), b = hash(Math.floorMod(ix + 1, period), iy, seed);
        double c = hash(Math.floorMod(ix, period), iy + 1, seed), d = hash(Math.floorMod(ix + 1, period), iy + 1, seed);
        return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty;
    }

    static double fbm(double x, double y, double cell, int period, long seed) {
        double v = 0, amp = 0.5, total = 0;
        for (int o = 0; o < 4; o++) {
            v += amp * noise(x, y, cell, period, seed + o * 101);
            total += amp;
            cell /= 2;
            period *= 2;
            amp /= 2;
        }
        return v / total;
    }

    static void grain(BufferedImage img, Sprite s) {
        for (int y = 0; y < s.h; y++) for (int x = 0; x < s.w; x++) put(img, s.x + x, s.y + y, hash(x, y, 7));
    }

    /** 45-degree stripes, 8 texels on and 8 off, tiling. */
    static void stripes(BufferedImage img, Sprite s) {
        for (int y = 0; y < s.h; y++) for (int x = 0; x < s.w; x++) {
            double on = 0;
            for (int j = 0; j < 4; j++) for (int i = 0; i < 4; i++) {
                double d = (x + (i + 0.5) / 4 + y + (j + 0.5) / 4) % 16;
                if (d < 8) on++;
            }
            put(img, s.x + x, s.y + y, on / 16);
        }
    }

    static void nebula(BufferedImage img, Sprite s, int k) {
        double ox = k * 0.17, oy = -k * 0.11;
        for (int y = 0; y < s.h; y++) for (int x = 0; x < s.w; x++) {
            double u = (x + 0.5) / s.w * 2 - 1 + ox, v = (y + 0.5) / s.h * 2 - 1 + oy;
            double r = Math.hypot(u * (1 + 0.2 * k), v);
            double density = fbm(x, y, 40, 1 << 20, 31 + k) * 1.35 - 0.25;
            double fall = clamp((1 - r) / 0.55);
            put(img, s.x + x, s.y + y, clamp(density) * fall * fall);
        }
    }

    /** The Milky Way band: a soft horizontal band with clumps, wrapping left to right. */
    static void galaxy(BufferedImage img, Sprite s) {
        for (int y = 0; y < s.h; y++) for (int x = 0; x < s.w; x++) {
            double mid = s.h / 2.0 + (noise(x, 0, 64, 4, 5) - 0.5) * 10;
            double width = 9 + 6 * noise(x, 0, 32, 8, 6);
            double band = Math.exp(-Math.pow((y + 0.5 - mid) / width, 2));
            double clumps = 0.45 + 0.55 * fbm(x, y, 32, 8, 9);
            put(img, s.x + x, s.y + y, band * clumps * clamp(Math.min(y + 0.5, s.h - y - 0.5) / 6));
        }
    }

    /** The soft-edged backing behind light text in the world: full inside, 4 px (16 texel) soft edge. */
    static void backing(BufferedImage img, Sprite s) {
        for (int y = 0; y < s.h; y++) for (int x = 0; x < s.w; x++) {
            double d = Math.min(Math.min(x + 0.5, s.w - x - 0.5), Math.min(y + 0.5, s.h - y - 0.5));
            double t = clamp(d / s.border);
            put(img, s.x + x, s.y + y, t * t * (3 - 2 * t));
        }
    }

    /** The 2x atlas: each 2 x 2 block of the 4x one averaged with premultiplied alpha. */
    public static BufferedImage half(BufferedImage four) {
        int w = four.getWidth() / 2, h = four.getHeight() / 2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double a = 0, r = 0, gr = 0, b = 0;
            for (int j = 0; j < 2; j++) for (int i = 0; i < 2; i++) {
                int c = four.getRGB(2 * x + i, 2 * y + j);
                double ca = (c >>> 24) / 255.0;
                a += ca;
                r += ca * (c >> 16 & 255);
                gr += ca * (c >> 8 & 255);
                b += ca * (c & 255);
            }
            if (a == 0) continue;
            int ia = (int) Math.round(a / 4 * 255);
            if (ia == 0) continue;
            out.setRGB(
                x,
                y,
                ia << 24 | (int) Math.round(r / a) << 16 | (int) Math.round(gr / a) << 8 | (int) Math.round(b / a));
        }
        return out;
    }

    // ------------------------------------------------------------------ AtlasMap.java

    static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= 0x20 && c < 0x7F && c != '"' && c != (char) 92) b.append(c);
            else if (c == '"') b.append((char) 92)
                .append('"');
            else if (c == (char) 92) b.append((char) 92)
                .append((char) 92);
            else b.append((char) 92)
                .append('u')
                .append(String.format("%04X", (int) c));
        }
        return b.toString();
    }

    /** The source of {@code AtlasMap.java}, formatted the way spotless leaves it. */
    public static String source() {
        List<Sprite> sprites = sprites();
        int icons = SYSTEM.length + DOMAIN.length + SIGILS.length, micro = MICROS.length;
        StringBuilder o = new StringBuilder();
        o.append("package com.fluxecho.logic.ui;\n\n");
        o.append("/**\n");
        o.append(" * Where every sprite and FluxMono glyph of the new UI sits in {@code textures/gui/ui/atlas@4x.png}, in texels\n");
        o.append(" * of that 1024 x 1024 picture ({@code atlas@2x.png} is the same at half size, so the same rectangles over 1024\n");
        o.append(" * are its texture coordinates). Generated by {@code tools/UiAtlas.java}: do not edit by hand, run\n");
        o.append(" * {@code java tools/UiAtlas.java}; {@code AtlasTest} checks that this file and the pictures are its output.\n");
        o.append(" * <p>\n");
        o.append(" * Sprites are numbered: the icons first ({@code I_*}, " + icons + "), then the micro icons ({@code M_*}, "
            + micro + "),\n");
        o.append(" * then the nine-slices, glows and textures ({@code S_*}). A nine-slice has a border, the same on all four\n");
        o.append(" * sides; every other sprite has 0. Glyph cells are found by index in {@link #SMALL} and {@link #LARGE}.\n");
        o.append(" */\n");
        o.append("public final class AtlasMap {\n\n");
        o.append("    private AtlasMap() {}\n\n");
        o.append("    /** Width and height of the 4x atlas, texels. */\n");
        o.append("    public static final int SIZE = " + SIZE + ";\n\n");
        o.append("    // regions\n");
        for (int r = 0; r < REGIONS.length; r++)
            o.append("    public static final int R_" + REGION_NAMES[r] + " = " + r + ";\n");
        o.append("    public static final int REGIONS = " + REGIONS.length + ";\n\n");
        o.append("    // sprites\n");
        for (int i = 0; i < sprites.size(); i++) {
            o.append("    public static final int " + sprites.get(i).constant + " = " + i + ";\n");
            if (i == icons - 1) o.append("    public static final int ICON_COUNT = " + icons + ";\n");
            if (i == icons + micro - 1) o.append("    public static final int MICRO_COUNT = " + micro + ";\n");
        }
        o.append("    public static final int COUNT = " + sprites.size() + ";\n\n");
        o.append("    // glyph cells\n");
        o.append("    public static final int SMALL_W = " + SMALL_W + ";\n");
        o.append("    public static final int SMALL_H = " + SMALL_H + ";\n");
        o.append("    public static final int LARGE_W = " + LARGE_W + ";\n");
        o.append("    public static final int LARGE_H = " + LARGE_H + ";\n");
        o.append("    /** The small glyphs in cell order: ASCII 0x20-0x7E, then the symbols. */\n");
        o.append("    public static final String SMALL;\n");
        o.append("    /** The large glyphs in cell order. */\n");
        o.append("    public static final String LARGE = \"" + escape(LARGE) + "\";\n\n");
        o.append("    private static final int[] REGION = new int[REGIONS * 4];\n");
        o.append("    private static final int[] RECT = new int[COUNT * 4];\n");
        o.append("    private static final int[] BORDER = new int[COUNT];\n");
        o.append("    private static final String[] NAME = new String[COUNT];\n\n");
        o.append("    static {\n");
        o.append("        StringBuilder small = new StringBuilder();\n");
        o.append("        for (char c = 0x20; c < 0x7F; c++) small.append(c);\n");
        o.append("        small.append(\"" + escape(SMALL_EXTRA) + "\");\n");
        o.append("        SMALL = small.toString();\n");
        for (int r = 0; r < REGIONS.length; r++) {
            int[] a = REGIONS[r];
            o.append("        region(R_" + REGION_NAMES[r] + ", " + a[0] + ", " + a[1] + ", " + a[2] + ", " + a[3] + ");\n");
        }
        for (Sprite s : sprites) o.append(
            "        sprite(" + s.constant + ", \"" + s.name + "\", " + s.x + ", " + s.y + ", " + s.w + ", " + s.h + ", "
                + s.border + ");\n");
        o.append("    }\n\n");
        o.append("    private static void region(int r, int x, int y, int w, int h) {\n");
        o.append("        REGION[r * 4] = x;\n");
        o.append("        REGION[r * 4 + 1] = y;\n");
        o.append("        REGION[r * 4 + 2] = w;\n");
        o.append("        REGION[r * 4 + 3] = h;\n");
        o.append("    }\n\n");
        o.append("    private static void sprite(int id, String name, int x, int y, int w, int h, int border) {\n");
        o.append("        RECT[id * 4] = x;\n");
        o.append("        RECT[id * 4 + 1] = y;\n");
        o.append("        RECT[id * 4 + 2] = w;\n");
        o.append("        RECT[id * 4 + 3] = h;\n");
        o.append("        BORDER[id] = border;\n");
        o.append("        NAME[id] = name;\n");
        o.append("    }\n\n");
        o.append("    /** A region {x, y, w, h}. */\n");
        o.append("    public static int[] region(int r) {\n");
        o.append("        return new int[] { REGION[r * 4], REGION[r * 4 + 1], REGION[r * 4 + 2], REGION[r * 4 + 3] };\n");
        o.append("    }\n\n");
        for (String[] f : new String[][] { { "x", "0" }, { "y", "1" }, { "w", "2" }, { "h", "3" } }) {
            o.append("    public static int " + f[0] + "(int id) {\n");
            o.append("        return RECT[id * 4" + (f[1].equals("0") ? "" : " + " + f[1]) + "];\n");
            o.append("    }\n\n");
        }
        o.append("    /** The nine-slice border of a sprite, texels (0: not a nine-slice). */\n");
        o.append("    public static int border(int id) {\n");
        o.append("        return BORDER[id];\n");
        o.append("    }\n\n");
        o.append("    public static String name(int id) {\n");
        o.append("        return NAME[id];\n");
        o.append("    }\n\n");
        o.append("    /** Left of small glyph {@code i}: " + SMALL_COLS + " to a row, the rest in the spill strip. */\n");
        o.append("    public static int smallX(int i) {\n");
        o.append("        return i < " + SMALL_MAIN + " ? i % " + SMALL_COLS + " * SMALL_W : (i - " + SMALL_MAIN
            + ") * SMALL_W;\n");
        o.append("    }\n\n");
        o.append("    public static int smallY(int i) {\n");
        o.append("        return i < " + SMALL_MAIN + " ? " + REGIONS[2][1] + " + i / " + SMALL_COLS + " * SMALL_H : "
            + REGIONS[6][1] + ";\n");
        o.append("    }\n\n");
        o.append("    public static int largeX(int i) {\n");
        o.append("        return " + REGIONS[3][0] + " + i % " + LARGE_COLS + " * LARGE_W;\n");
        o.append("    }\n\n");
        o.append("    public static int largeY(int i) {\n");
        o.append("        return " + REGIONS[3][1] + " + i / " + LARGE_COLS + " * LARGE_H;\n");
        o.append("    }\n");
        o.append("}\n");
        return o.toString();
    }
}
