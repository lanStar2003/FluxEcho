package com.fluxecho.quest.bq;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.fluxecho.FluxEcho;
import com.fluxecho.quest.QuestPack;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestDatabase;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineDatabase;
import betterquesting.api.utils.NBTConverter;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.questing.QuestInstance;
import betterquesting.questing.QuestLine;

/**
 * Puts FluxEcho's quest lines straight into the running world's BetterQuesting database, the way
 * {@code /bq_admin default load} would but without touching anybody else's lines. Called once the server has started:
 * BetterQuesting loads the world's quests, and the GTNH core mod reloads them after a pack update, while it starts.
 * <p>
 * A missing quest or line is added (a new line goes to the end of the list). A quest whose content differs is
 * rewritten with its progress kept, as the default load keeps it. A line is rewritten as a whole; quests that dropped
 * out of it and are in no other line are deleted. Content is compared after a round trip through BetterQuesting's own
 * classes, so defaults it fills in do not count as a change.
 */
public final class QuestInjector {

    private QuestInjector() {}

    /** @return quests and lines {added, updated or removed} */
    public static int[] inject(List<QuestPack.Line> lines) throws IOException {
        IQuestDatabase quests = QuestingAPI.getAPI(ApiReference.QUEST_DB);
        IQuestLineDatabase chapters = QuestingAPI.getAPI(ApiReference.LINE_DB);
        int added = 0, updated = 0;
        for (QuestPack.Line line : lines) {
            NBTTagCompound chapter = null;
            NBTTagList entries = new NBTTagList();
            for (String path : line.files) {
                NBTTagCompound nbt = read(path);
                if (path.startsWith("Quests/")) {
                    UUID id = NBTConverter.UuidValueType.QUEST.readId(nbt);
                    IQuest quest = quests.get(id);
                    if (quest == null) {
                        quests.createNew(id)
                            .readFromNBT(nbt);
                        added++;
                    } else if (!same(quest, nbt)) {
                        NBTTagCompound progress = quest.writeProgressToNBT(new NBTTagCompound(), null);
                        quest.readFromNBT(nbt);
                        quest.readProgressFromNBT(progress, false);
                        updated++;
                    }
                } else if (path.endsWith("/QuestLine.json")) chapter = nbt;
                else entries.appendTag(nbt);
            }
            if (chapter == null) throw new IOException("no QuestLine.json in " + line.order);
            chapter.setTag("quests", entries);

            UUID id = NBTConverter.UuidValueType.QUEST_LINE.readId(chapter);
            IQuestLine existing = chapters.get(id);
            if (existing == null) {
                chapters.createNew(id)
                    .readFromNBT(chapter, false);
                chapters.getOrderIndex(id); // puts a line missing from the order at the end
                added++;
            } else if (!same(existing, chapter)) {
                Set<UUID> dropped = new HashSet<>(existing.keySet());
                existing.readFromNBT(chapter, false);
                dropped.removeAll(existing.keySet());
                for (UUID q : dropped) {
                    if (!inAnyLine(chapters, q) && quests.remove(q) != null) updated++;
                }
                updated++;
            }
        }
        if (added + updated > 0) {
            SaveLoadHandler.INSTANCE.markDirty();
            FluxEcho.LOG.info("Quest lines put into the world: {} added, {} updated", added, updated);
        } else FluxEcho.LOG.info("Quest lines in the world up to date ({} lines)", lines.size());
        return new int[] { added, updated };
    }

    /** Reads a shipped file the way the default load reads DefaultQuests. */
    private static NBTTagCompound read(String path) throws IOException {
        try (Reader r = new InputStreamReader(new ByteArrayInputStream(QuestPack.read(path)), StandardCharsets.UTF_8)) {
            JsonObject json = new JsonParser().parse(r)
                .getAsJsonObject();
            return NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);
        }
    }

    private static boolean same(IQuest quest, NBTTagCompound nbt) {
        QuestInstance fresh = new QuestInstance();
        fresh.readFromNBT(nbt);
        return fresh.writeToNBT(new NBTTagCompound())
            .equals(quest.writeToNBT(new NBTTagCompound()));
    }

    private static boolean same(IQuestLine line, NBTTagCompound nbt) {
        QuestLine fresh = new QuestLine();
        fresh.readFromNBT(nbt, false);
        return fresh.writeToNBT(new NBTTagCompound(), true)
            .equals(line.writeToNBT(new NBTTagCompound(), true));
    }

    private static boolean inAnyLine(IQuestLineDatabase chapters, UUID quest) {
        for (IQuestLine line : chapters.values()) {
            if (line.containsKey(quest)) return true;
        }
        return false;
    }
}
