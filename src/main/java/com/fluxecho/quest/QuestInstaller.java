package com.fluxecho.quest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import org.apache.commons.io.IOUtils;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.Mods;
import com.fluxecho.logic.QuestOrder;
import com.fluxecho.logic.QuestPlan;
import com.google.gson.Gson;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * Installs the quest lines shipped in the jar ({@code assets/fluxecho/quests}, written by
 * {@code quests/build_quests.py}) into {@code config/betterquesting/DefaultQuests}, and lists them in
 * {@code QuestLinesOrder.txt}. Runs in preInit, before BetterQuesting loads a new world's quests and before the GTNH
 * core mod reloads them after a pack update.
 * <p>
 * Only the lines' own folders and order entries are touched: a file is written when its bytes differ, a file is
 * removed only from the line's own folder, and the order file only gets our lines replaced in place or appended.
 * A line whose mods are missing is skipped and its installed files are left alone. Nothing is created when the
 * instance has no DefaultQuests: then the pack does not use BetterQuesting's default quests.
 */
public final class QuestInstaller {

    private static final String ROOT = "/assets/fluxecho/quests/";
    /** Machine ids baked into the shipped quests (build_quests.py). */
    private static final int QUEST_FLUXDEPTHS_FIRST_ID = 24520;

    private QuestInstaller() {}

    public static void run(File configDir) {
        if (!Config.installQuests || !Mods.betterQuesting) return;
        File defaults = new File(configDir, "betterquesting/DefaultQuests");
        File order = new File(defaults, "QuestLinesOrder.txt");
        if (!order.isFile()) {
            FluxEcho.LOG.info("No {}: quest lines not installed", order);
            return;
        }
        try {
            if (install(configDir, defaults, order)) FMLCommonHandler.instance()
                .bus()
                .register(new Hint());
        } catch (Exception e) {
            FluxEcho.LOG.error("Failed to install the quest lines", e);
        }
    }

    /** @return whether any file changed */
    private static boolean install(File configDir, File defaults, File order) throws IOException {
        Index index;
        try (Reader r = new InputStreamReader(resource("index.json"), StandardCharsets.UTF_8)) {
            index = new Gson().fromJson(r, Index.class);
        }
        List<String> entries = new ArrayList<>();
        int written = 0, removed = 0;
        for (Line line : index.lines) {
            if (!Mods.allLoaded(Arrays.asList(line.requires))) {
                FluxEcho.LOG.info("Quest line {} skipped: needs {}", line.order, Arrays.toString(line.requires));
                continue;
            }
            if (Arrays.asList(line.requires)
                .contains("fluxdepths")) checkFluxDepthsIds();
            for (String path : line.files) {
                if (write(new File(defaults, path), readResource("DefaultQuests/" + path))) written++;
            }
            Set<String> shipped = new HashSet<>(Arrays.asList(line.files));
            for (String dir : line.dirs) {
                String[] present = new File(defaults, dir).list();
                if (present == null) continue;
                for (String path : QuestPlan.stale(dir, Arrays.asList(present), shipped)) {
                    if (new File(defaults, path).delete()) removed++;
                }
            }
            entries.add(line.order);
        }

        String text = new String(Files.readAllBytes(order.toPath()), StandardCharsets.UTF_8);
        String merged = QuestOrder.merge(text, entries);
        boolean orderChanged = !merged.equals(text);
        if (orderChanged) {
            File backup = new File(
                configDir,
                "fluxecho/backup/QuestLinesOrder-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())
                    + ".txt");
            backup.getParentFile()
                .mkdirs();
            Files.copy(order.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            write(order, merged.getBytes(StandardCharsets.UTF_8));
        }

        boolean changed = written > 0 || removed > 0 || orderChanged;
        if (changed) FluxEcho.LOG.info(
            "Quest lines installed: {} files written, {} removed, order file {}. Run /bq_admin default load to load them.",
            written,
            removed,
            orderChanged ? "updated" : "unchanged");
        else FluxEcho.LOG.info("Quest lines up to date ({} lines)", entries.size());
        return changed;
    }

    /** Writes the file when its content differs, through a temporary file next to it. */
    private static boolean write(File file, byte[] data) throws IOException {
        if (file.isFile() && Arrays.equals(Files.readAllBytes(file.toPath()), data)) return false;
        file.getParentFile()
            .mkdirs();
        File tmp = new File(file.getParentFile(), file.getName() + ".fluxecho-tmp");
        Files.write(tmp.toPath(), data);
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    private static InputStream resource(String path) throws IOException {
        InputStream in = QuestInstaller.class.getResourceAsStream(ROOT + path);
        if (in == null) throw new IOException("missing resource " + ROOT + path);
        return in;
    }

    private static byte[] readResource(String path) throws IOException {
        try (InputStream in = resource(path)) {
            return IOUtils.toByteArray(in);
        }
    }

    /** The shard collector quests name FluxDepths' machines by id; warn when its config moved them. */
    private static void checkFluxDepthsIds() {
        try {
            int first = Class.forName("com.fluxdepths.Config")
                .getField("shardsFirstId")
                .getInt(null);
            if (first != QUEST_FLUXDEPTHS_FIRST_ID) FluxEcho.LOG.warn(
                "FluxDepths' shard_collectors.firstMachineId is {}, the shard collector quests show the machines at {}: their icons and item tasks will be wrong",
                first,
                QUEST_FLUXDEPTHS_FIRST_ID);
        } catch (ReflectiveOperationException | LinkageError e) {
            FluxEcho.LOG.debug("Could not read FluxDepths' machine ids", e);
        }
    }

    /** Tells whoever can run {@code /bq_admin} once per launch that the quest files changed. */
    public static final class Hint {

        private final Set<UUID> told = new HashSet<>();

        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (!(e.player instanceof EntityPlayerMP p) || !p.canCommandSenderUseCommand(2, "bq_admin")) return;
            if (!told.add(p.getUniqueID())) return;
            p.addChatMessage(
                new ChatComponentTranslation("fluxecho.quests.updated")
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
        }
    }

    /** {@code index.json}. */
    private static final class Index {

        Line[] lines = new Line[0];
    }

    private static final class Line {

        String order;
        String[] requires = new String[0];
        String[] dirs = new String[0];
        String[] files = new String[0];
    }
}
