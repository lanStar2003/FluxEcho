package com.fluxecho.quest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.fluxecho.FluxEcho;
import com.fluxecho.Mods;
import com.google.gson.Gson;

/**
 * The quest lines shipped in the jar: {@code assets/fluxecho/quests}, written by {@code quests/build_quests.py} in
 * BetterQuesting's DefaultQuests layout, with {@code index.json} listing each line's mods, folders and files.
 */
public final class QuestPack {

    private static final String ROOT = "/assets/fluxecho/quests/";

    private QuestPack() {}

    /** The lines whose mods are all loaded; the others are logged and left out. */
    public static List<Line> installable() throws IOException {
        Index index;
        try (Reader r = new InputStreamReader(open("index.json"), StandardCharsets.UTF_8)) {
            index = new Gson().fromJson(r, Index.class);
        }
        List<Line> out = new ArrayList<>();
        for (Line line : index.lines) {
            if (Mods.allLoaded(Arrays.asList(line.requires))) out.add(line);
            else FluxEcho.LOG.info("Quest line {} skipped: needs {}", line.order, Arrays.toString(line.requires));
        }
        return out;
    }

    /** @param path a file of a line, relative to DefaultQuests */
    public static byte[] read(String path) throws IOException {
        try (InputStream in = open("DefaultQuests/" + path)) {
            return IOUtils.toByteArray(in);
        }
    }

    private static InputStream open(String path) throws IOException {
        InputStream in = QuestPack.class.getResourceAsStream(ROOT + path);
        if (in == null) throw new IOException("missing resource " + ROOT + path);
        return in;
    }

    /** One entry of {@code index.json}. */
    public static final class Line {

        /** The line's row in QuestLinesOrder.txt: {@code "<id>: <name>"}. */
        public String order;
        public String[] requires = new String[0];
        /** The line's own folders, relative to DefaultQuests. */
        public String[] dirs = new String[0];
        /** Every file of the line, relative to DefaultQuests. */
        public String[] files = new String[0];
    }

    private static final class Index {

        Line[] lines = new Line[0];
    }
}
