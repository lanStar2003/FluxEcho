package com.fluxecho.quest;

import java.io.File;
import java.io.IOException;
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

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.Mods;
import com.fluxecho.logic.QuestOrder;
import com.fluxecho.logic.QuestPlan;
import com.fluxecho.quest.bq.QuestInjector;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * Installs the quest lines shipped in the jar ({@link QuestPack}) so that nobody has to run
 * {@code /bq_admin default load}:
 * <ul>
 * <li>In preInit the files go into {@code config/betterquesting/DefaultQuests} and the lines into
 * {@code QuestLinesOrder.txt}, so every later default load (by hand, or the GTNH core mod's after a pack update)
 * keeps them.</li>
 * <li>When a server has started, {@link QuestInjector} puts them straight into that world's quest database, so they
 * show up in every world, old or new, with progress kept.</li>
 * </ul>
 * Only the lines' own folders, order rows and quests are touched. A line whose mods are missing is skipped and its
 * installed files are left alone. No DefaultQuests folder is created when the instance has none: then the pack does
 * not use BetterQuesting's default quests, and the lines are only put into the worlds.
 */
public final class QuestInstaller {

    /** Machine ids baked into the shipped quests (build_quests.py). */
    private static final int QUEST_FLUXDEPTHS_FIRST_ID = 24520;

    private static final Notice NOTICE = new Notice();

    private QuestInstaller() {}

    private static boolean enabled() {
        return Config.installQuests && Mods.betterQuesting;
    }

    public static void preInit(File configDir) {
        if (!enabled()) return;
        FMLCommonHandler.instance()
            .bus()
            .register(NOTICE);
        File defaults = new File(new File(configDir, "betterquesting"), "DefaultQuests");
        File order = new File(defaults, "QuestLinesOrder.txt");
        if (!order.isFile()) {
            FluxEcho.LOG.info("No {}: the quest lines are only put into the worlds", order);
            return;
        }
        try {
            install(configDir, defaults, order);
        } catch (Exception e) {
            FluxEcho.LOG.error("Failed to install the quest files", e);
        }
    }

    public static void serverStarted() {
        NOTICE.reset();
        if (!enabled()) return;
        try {
            int[] changed = QuestInjector.inject(QuestPack.installable());
            if (changed[0] + changed[1] > 0) NOTICE.set(changed[0], changed[1]);
        } catch (Exception | LinkageError e) { // LinkageError: a BetterQuesting whose API changed
            FluxEcho.LOG.error("Failed to put the quest lines into the world", e);
        }
    }

    private static void install(File configDir, File defaults, File order) throws IOException {
        List<String> entries = new ArrayList<>();
        int written = 0, removed = 0;
        for (QuestPack.Line line : QuestPack.installable()) {
            if (Arrays.asList(line.requires)
                .contains("fluxdepths")) checkFluxDepthsIds();
            for (String path : line.files) {
                if (write(new File(defaults, path), QuestPack.read(path))) written++;
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

        if (written > 0 || removed > 0 || orderChanged) FluxEcho.LOG.info(
            "Quest files installed: {} written, {} removed, order file {}",
            written,
            removed,
            orderChanged ? "updated" : "unchanged");
        else FluxEcho.LOG.info("Quest files up to date ({} lines)", entries.size());
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

    /** Tells each player once per server start that the quest book changed. */
    public static final class Notice {

        private final Set<UUID> told = new HashSet<>();
        private int added, updated;

        void set(int added, int updated) {
            this.added = added;
            this.updated = updated;
        }

        void reset() {
            told.clear();
            added = updated = 0;
        }

        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (added + updated == 0 || !(e.player instanceof EntityPlayerMP p) || !told.add(p.getUniqueID())) return;
            p.addChatMessage(
                new ChatComponentTranslation("fluxecho.quests.synced", added, updated)
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
        }
    }
}
