package com.fluxecho.voyage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.fluxecho.FluxEcho;
import com.fluxecho.codex.EchoLedger;
import com.fluxecho.codex.EchoNet;
import com.fluxecho.core.Owners;
import com.fluxecho.logic.voyage.ScoreBridge;
import com.fluxecho.nexus.NexusRegistry;
import com.fluxecho.nexus.TileModule;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.research.Research;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The server side of the scoreboard bridge, version 1 (narrative §7.4, §9.2 N1): every few seconds, and when a player
 * logs in, it works out each online player's team state ({@link ScoreBridge.State}) and writes it into the overworld's
 * vanilla scoreboard under the player's name, which is where BetterQuesting's scoreboard task reads it. Only the
 * vanilla API is used, so it is harmless without BetterQuesting.
 * <p>
 * The records and the research come from the saved ledgers ({@link EchoLedger}, {@link Research}) of the player's own
 * team, as the Echo Codex shows them; the nexus's phase and the buildings standing formed come from the nexuses and
 * modules loaded now, for every team bound to them. The scoreboard itself is what remembers them while they are
 * unloaded: a building's ladder and the phase only rise ({@link ScoreBridge#next}), so a player's buildings keep their
 * level 1 once seen formed, and old saves get theirs as soon as their city loads. The union of the teams bound to one
 * nexus and the bridge's own save come with {@code fluxecho_voyage} in 0.18.
 * <p>
 * A building reaching level 1 also opens its cabin log in the Codex ({@link #halls}), so the team's ledger is sent
 * again when one does.
 */
public final class ScoreBridgeServer {

    /** Ticks between two sweeps over the online players. */
    static final int PERIOD = 100;

    private ScoreBridgeServer() {}

    public static void init() {
        FMLCommonHandler.instance()
            .bus()
            .register(new Events());
    }

    /**
     * Registers the 31 narrative objectives that are missing as dummy objectives, their names as display names (L18:
     * all at once, the values come with their features); an objective that already exists, made by a player or by
     * BetterQuesting for a task, is kept as it is. Returns how many were added.
     */
    public static int register(Scoreboard board) {
        int added = 0;
        for (String name : ScoreBridge.OBJECTIVES) {
            if (board.getObjective(name) != null) continue;
            board.addScoreObjective(name, IScoreObjectiveCriteria.field_96641_b)
                .setDisplayName(name);
            added++;
        }
        return added;
    }

    /**
     * Writes a player's values ({@link ScoreBridge#values}), each through {@link ScoreBridge#next}: only what changed,
     * and no zero where the player holds nothing yet. Objectives that are not registered are skipped. Returns how many
     * changed.
     */
    public static int write(Scoreboard board, String player, Map<String, Integer> values) {
        // keyed by objective, though the deobfuscated signature says by name
        Map<?, ?> held = board.func_96510_d(player);
        int changed = 0;
        for (Map.Entry<String, Integer> e : values.entrySet()) {
            ScoreObjective o = board.getObjective(e.getKey());
            if (o == null) continue;
            Score s = (Score) held.get(o);
            Integer current = s == null ? null : s.getScorePoints();
            int v = ScoreBridge.next(e.getKey(), current, e.getValue());
            if (current == null ? v == 0 : current == v) continue;
            board.func_96529_a(player, o)
                .setScorePoints(v);
            changed++;
        }
        return changed;
    }

    /** What the player holds in an objective; -1 for nothing (no such objective or no score). */
    public static int score(Scoreboard board, String player, String objective) {
        ScoreObjective o = board.getObjective(objective);
        Score s = o == null ? null
            : (Score) board.func_96510_d(player)
                .get(o);
        return s == null ? -1 : s.getScorePoints();
    }

    /**
     * The buildings whose cabin log the player may read: those whose ladder ({@code feBxx}) has reached level 1, in
     * row order.
     */
    public static Set<String> halls(Scoreboard board, String player) {
        Set<String> out = new LinkedHashSet<>();
        for (int row = 0; row <= 20; row++) {
            if (score(board, player, ScoreBridge.building(row)) >= 1) out.add(ScoreBridge.buildingKey(row));
        }
        return out;
    }

    /** The cabin logs of an online player, read from the server's scoreboard; empty without a world. */
    public static Set<String> halls(EntityPlayerMP p) {
        Scoreboard board = board();
        return board == null || p == null ? Collections.emptySet() : halls(board, p.getCommandSenderName());
    }

    private static Scoreboard board() {
        World w = DimensionManager.getWorld(0);
        return w == null ? null : w.getScoreboard();
    }

    /** Writes the values of the players (all online ones on a sweep, one on its login). */
    static void sweep(Collection<EntityPlayerMP> players) {
        if (players.isEmpty()) return;
        Scoreboard board = board();
        if (board == null) return;
        register(board);
        Map<UUID, Integer> phases = new HashMap<>();
        Map<UUID, Set<String>> formed = new HashMap<>();
        loaded(phases, formed);
        Map<UUID, Map<String, Integer>> byTeam = new HashMap<>();
        Set<UUID> raised = new LinkedHashSet<>();
        for (EntityPlayerMP p : players) {
            UUID team = Owners.team(p.getUniqueID());
            if (team == null) continue;
            Map<String, Integer> values = byTeam
                .computeIfAbsent(team, t -> ScoreBridge.values(state(t, phases, formed)));
            String name = p.getCommandSenderName();
            Set<String> before = halls(board, name);
            if (write(board, name, values) > 0 && !halls(board, name).equals(before)) raised.add(team);
        }
        for (UUID team : raised) EchoNet.ledgerChanged(team);
    }

    /**
     * The phase of every formed nexus loaded now and the modules standing formed, for each team bound to them; a
     * module that has no nexus counts for its owner's team.
     */
    private static void loaded(Map<UUID, Integer> phases, Map<UUID, Set<String>> formed) {
        for (TileNexus n : NexusRegistry.allNexuses()) {
            if (n.isInvalid() || !n.formed()) continue;
            for (UUID t : n.teams()) {
                phases.merge(t, n.phase(), Math::max);
                formed.computeIfAbsent(t, k -> new LinkedHashSet<>())
                    .add("nexus");
            }
        }
        for (TileModule m : NexusRegistry.allModules()) {
            if (m.isInvalid() || !m.formed()) continue;
            TileNexus n = m.nexus();
            Collection<UUID> teams = n != null ? n.teams() : Collections.singleton(m.team());
            for (UUID t : teams) {
                if (t != null) formed.computeIfAbsent(t, k -> new LinkedHashSet<>())
                    .add(m.moduleKey());
            }
        }
    }

    /** A team's state: its codex entries by category, its research, and what the loaded city shows of it. */
    static ScoreBridge.State state(UUID team, Map<UUID, Integer> phases, Map<UUID, Set<String>> formed) {
        ScoreBridge.State s = new ScoreBridge.State();
        NBTTagCompound snap = EchoLedger.get()
            .snapshot(team);
        for (Object o : snap.func_150296_c()) {
            String category = (String) o;
            if (category.startsWith("@")) continue; // the Codex's own keys (the research) are no category
            s.records.put(
                category,
                snap.getTagList(category, 8)
                    .tagCount());
        }
        s.research.addAll(Research.done(team));
        s.phase = phases.getOrDefault(team, 0);
        Set<String> f = formed.get(team);
        if (f != null) s.formed.addAll(f);
        return s;
    }

    public static final class Events {

        private boolean failed;

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null || server.getTickCounter() % PERIOD != 0 || server.getConfigurationManager() == null)
                return;
            run(players(server.getConfigurationManager().playerEntityList));
        }

        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.player instanceof EntityPlayerMP p) run(Collections.singletonList(p));
        }

        private void run(Collection<EntityPlayerMP> players) {
            try {
                sweep(players);
            } catch (RuntimeException ex) {
                // a broken world or module must not stop the server; say it once per run
                if (!failed) FluxEcho.LOG.error("The scoreboard bridge failed", ex);
                failed = true;
            }
        }

        private static List<EntityPlayerMP> players(List<?> list) {
            List<EntityPlayerMP> out = new ArrayList<>();
            for (Object o : list) if (o instanceof EntityPlayerMP p) out.add(p);
            return out;
        }
    }
}
