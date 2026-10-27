package com.fluxecho.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import com.fluxecho.campus.BuildJob;
import com.fluxecho.campus.Campus;
import com.fluxecho.campus.RecycleStore;
import com.fluxecho.core.Owners;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.Compact;
import com.fluxecho.logic.EchoCommand;
import com.fluxecho.logic.probe.ProbeArgs;
import com.fluxecho.nexus.NexusGui;
import com.fluxecho.nexus.NexusRegistry;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.research.Research;

/**
 * {@code /fluxecho}, the operators' command (0.10.1, permission level 2), for the user's 「直接一次性全部开完研究」 and
 * 「创造模式不需要再放入材料」:
 * <ul>
 * <li>{@code research all [player]} completes every research of the player's team (the sender's when no player is
 * named), {@code research reset [player]} clears it; the team's players get it as when a research finishes;</li>
 * <li>{@code build free [on|off]} makes every job of the nearest nexus free, or not (no word: toggled);
 * {@code build finish} starts the nearest nexus's current job if needed, makes it free and runs it to the end at once
 * ({@link Campus#finishNow}); {@code build status} shows its job and counts. The nexus is the nearest loaded one
 * within {@value EchoCommand#RANGE} blocks of the sender, in the sender's dimension.</li>
 * <li>0.17.0's check-ups, read by {@link ProbeArgs}: {@code probe depth|sky|sun|bed} sets out (or takes down) a probe
 * rig in front of the sender ({@link ProbeCommands}), {@code swatch [clear]} the swatch wall ({@link SwatchCommand}),
 * {@code cam <A-E>} puts the sender at a camera point of their team's nexus ({@link CamCommand}).</li>
 * </ul>
 * The arguments are read by {@link EchoCommand}; every reply, the usage and the errors are lang keys, so each player
 * reads them in their own language.
 */
public class CommandFluxEcho extends CommandBase {

    private static final String P = "fluxecho.cmd.";

    @Override
    public String getCommandName() {
        return "fluxecho";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return P + "usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (ProbeArgs.claims(args)) {
            probe(sender, args);
            return;
        }
        EchoCommand.Parsed use = EchoCommand.parse(args);
        if (use == null) throw new WrongUsageException(P + "usage");
        switch (use.action) {
            case RESEARCH_ALL, RESEARCH_RESET -> research(sender, use);
            default -> build(sender, use);
        }
    }

    // ---- probes, swatch and camera points (0.17.0)

    /** {@code probe depth|sky|sun|bed}, {@code swatch [clear]}, {@code cam <A-E>}: an operator's check-ups. */
    private static void probe(ICommandSender sender, String[] args) {
        ProbeArgs.Parsed use = ProbeArgs.parse(args);
        if (use == null) throw new WrongUsageException(P + "probe.usage");
        if (!(sender instanceof EntityPlayer p)) throw new CommandException(P + "not_player");
        switch (use.action) {
            case PROBE_DEPTH -> ProbeCommands.depth(p);
            case PROBE_SKY -> ProbeCommands.sky(p);
            case PROBE_SUN -> ProbeCommands.sun(p);
            case PROBE_BED -> ProbeCommands.bed(p);
            case SWATCH -> SwatchCommand.build(p);
            case SWATCH_CLEAR -> SwatchCommand.clear(p);
            default -> CamCommand.go(p, use.point);
        }
    }

    // ---- research

    private static void research(ICommandSender sender, EchoCommand.Parsed use) {
        EntityPlayer p = target(sender, use.player);
        UUID team = Owners.team(p.getUniqueID());
        boolean all = use.action == EchoCommand.Action.RESEARCH_ALL;
        int n = all ? Research.grantAll(team) : Research.reset(team);
        sender.addChatMessage(
            new ChatComponentTranslation(P + (all ? "research_all" : "research_reset"), p.getCommandSenderName(), n));
    }

    /** The named online player, or the sender when none is named (which must then be a player). */
    private static EntityPlayer target(ICommandSender sender, String name) {
        if (name == null) {
            if (sender instanceof EntityPlayer p) return p;
            throw new CommandException(P + "not_player");
        }
        try {
            return getPlayer(sender, name);
        } catch (PlayerNotFoundException e) {
            throw new CommandException(P + "unknown_player", name);
        }
    }

    // ---- build

    private static void build(ICommandSender sender, EchoCommand.Parsed use) {
        if (!(sender instanceof EntityPlayer p)) throw new CommandException(P + "not_player");
        TileNexus n = nearest(p.worldObj, p.posX, p.posY, p.posZ);
        if (n == null) throw new CommandException(P + "no_nexus", (int) EchoCommand.RANGE);
        Campus c = n.campus();
        String at = n.xCoord + ", " + n.yCoord + ", " + n.zCoord;
        switch (use.action) {
            case BUILD_FREE -> {
                boolean on = use.free != null ? use.free : !c.freeBuild();
                sender.addChatMessage(withNexus(new ChatComponentTranslation(c.setFreeBuild(on)), at));
            }
            case BUILD_FINISH -> sender.addChatMessage(withNexus(new ChatComponentTranslation(c.finishNow(p)), at));
            default -> status(sender, n, c, at);
        }
    }

    /** The nearest loaded nexus within range of the point, in its world; null when there is none. */
    private static TileNexus nearest(World w, double x, double y, double z) {
        List<TileNexus> found = new ArrayList<>();
        List<double[]> centres = new ArrayList<>();
        for (TileNexus n : NexusRegistry.nexuses(w.provider.dimensionId)) {
            if (n.isInvalid() || n.getWorldObj() != w) continue;
            int[] c = n.centre();
            found.add(n);
            centres.add(new double[] { c[0] + 0.5, c[1] + 0.5, c[2] + 0.5 });
        }
        int i = EchoCommand.nearest(centres, x, y, z);
        return i < 0 ? null : found.get(i);
    }

    /** A reply with the nexus it was about after it. */
    private static IChatComponent withNexus(IChatComponent reply, String at) {
        return reply.appendSibling(new ChatComponentTranslation(P + "at_nexus", at));
    }

    /** {@code build status}: the nexus, its job (state, stage, counts) or that it has none, the queue and the store. */
    private static void status(ICommandSender s, TileNexus n, Campus c, String at) {
        IChatComponent head = new ChatComponentTranslation(P + "status.head", at);
        head.getChatStyle()
            .setColor(EnumChatFormatting.AQUA);
        s.addChatMessage(head);
        if (!c.active()) {
            s.addChatMessage(new ChatComponentTranslation(P + "status.legacy"));
            return;
        }
        BuildJob j = c.job();
        if (j == null) s.addChatMessage(new ChatComponentTranslation("fluxecho.build.no_job"));
        else {
            s.addChatMessage(new ChatComponentTranslation(P + "status.job", j.name(), phase(j)));
            s.addChatMessage(
                new ChatComponentTranslation(
                    P + "status.progress",
                    c.placed(),
                    c.total(),
                    c.cleared(),
                    c.blocked(),
                    c.skipped(),
                    c.unloaded()));
            s.addChatMessage(
                new ChatComponentTranslation(P + "status.ground", j.natural(), j.toFill(), j.filled(), j.machines()));
            long eta = BuildState.ended(j.state()) ? 0 : c.etaTicks();
            s.addChatMessage(
                new ChatComponentTranslation(
                    P + "status.cost",
                    yesNo(c.free(j)),
                    yesNo(c.freeBuild()),
                    eta < 0 ? "?" : String.valueOf((eta + 19) / 20)));
        }
        List<IChatComponent> names = new ArrayList<>();
        for (String k : c.queue()) names.add(BuildJob.name(k, k));
        IChatComponent queue = names.isEmpty() ? new ChatComponentTranslation("fluxecho.build.gui.none") : join(names);
        s.addChatMessage(new ChatComponentTranslation(P + "status.queue", queue));
        RecycleStore r = c.recycle();
        s.addChatMessage(new ChatComponentTranslation("fluxecho.build.gui.recycle", r.kinds(), Compact.si(r.total())));
    }

    /** Where the job is: its stage while building, why it waits while paused, else its state (translated). */
    private static IChatComponent phase(BuildJob j) {
        BuildState.State st = j.state();
        String key;
        if (st == BuildState.State.BUILDING) key = NexusGui.stageKey(j.planKey(), j.stage(), j.deepens(), j.floats());
        else if (st == BuildState.State.PAUSED && j.pause() != BuildState.Pause.NONE)
            key = NexusGui.pauseKey(j.pause());
        else key = NexusGui.stateKey(st);
        return new ChatComponentTranslation("fluxecho." + key);
    }

    private static IChatComponent yesNo(boolean b) {
        return new ChatComponentTranslation(P + (b ? "yes" : "no"));
    }

    private static IChatComponent join(List<IChatComponent> parts) {
        IChatComponent out = new ChatComponentText("");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.appendText(" · ");
            out.appendSibling(parts.get(i));
        }
        return out;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length > 1 && ProbeArgs.claims(args)) return ProbeArgs.complete(args);
        List<String> out = new ArrayList<>(
            EchoCommand.complete(
                args,
                Arrays.asList(
                    MinecraftServer.getServer()
                        .getAllUsernames())));
        if (args.length == 1) for (String w : ProbeArgs.complete(args)) if (!out.contains(w)) out.add(w);
        return out;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return index == 2 && args.length > 0 && "research".equalsIgnoreCase(args[0]);
    }
}
