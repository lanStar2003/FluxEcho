package com.fluxecho.nexus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.campus.BuildJob;
import com.fluxecho.campus.Campus;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.core.Directory;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.FluxMachineGui;
import com.fluxecho.logic.BuildLedger;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.PartRecipes;
import com.fluxecho.nexus.client.NexusScreen;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;

/**
 * The Flux Nexus's GUI, in the flux look, on two pages chosen by the tabs at the top right. 中枢: the manifestation
 * table on the left (six inputs, the making drawn in the middle, two outputs, what it can make below), the nexus's
 * state on the right (power, compute, research, ring, bound teams), a button to the research star map and the
 * hologram switch. 建造: what its campus builds (job and site, state and pause, stage, progress, EU/t and time left, the
 * counts), the buttons that drive it, the bill of what it still needs with the intake slot, the balance and the
 * spoils. The player's inventory is below both.
 * <p>
 * The page is the client's own: switching it turns the nexus's slots and the intake slot on and off (the slots tell
 * the server themselves) and every other widget follows the page through a dynamic enabled test. The build page's
 * numbers come as one compound refreshed every {@link #REFRESH} ticks. Its buttons act on the server only, for a
 * member standing within 8 blocks, through the {@link Campus} entry points; what they did comes back as a chat line.
 * The window opens on 建造 when the nexus is not formed and its campus has a job (the establish job raising it).
 */
public final class NexusGui {

    public static final int W = 248, H = 248, INV_Y = 166;
    /** Input slots: 3 x 2 from here. */
    static final int IN_X = 10, IN_Y = 36, OUT_X = 114;

    /** The window's pages: the nexus's own, and its campus's construction. */
    public static final int PAGE_NEXUS = 0, PAGE_BUILD = 1;
    /** Bill lines on the page at once, and the most the server sends (two pages). */
    public static final int BILL_ROWS = 8, BILL_MAX = 16;
    /** Ticks between two refreshes of the build page's numbers. */
    public static final int REFRESH = 10;
    /** How long the first click on 取消 waits for the second, in milliseconds. */
    public static final long CANCEL_MS = 3000;
    /** How near a member must stand to use the build page's buttons (8 blocks, squared). */
    static final double REACH_SQ = 64;

    // the layout of the tabs and the build page
    public static final int TAB_Y = 4, TAB_W = 38, TAB_H = 15, TAB_NEXUS_X = 164, TAB_BUILD_X = 204, HEADER_W = 156;
    public static final int JOB_X = 8, JOB_Y = 24, JOB_W = 126, JOB_H = 84;
    public static final int BTN_H = 14, ROW1_Y = 110, ROW2_Y = 127, ROW3_Y = 144;
    public static final int BILL_X = 142, BILL_Y = 24, BILL_W = 98, BILL_HEAD_W = 80, ROW_H = 12, ROWS_Y = 35;
    public static final int PAGER_X = 224, PAGER_W = 16, PAGER_H = 10;
    public static final int INTAKE_X = 144, INTAKE_Y = 137, FOOT_X = 164, FOOT_Y = 136, FOOT_W = 76, FOOT_H = 22;

    private static final String P = "fluxecho.build.";

    private NexusGui() {}

    /** One open window, on its own side: the page shown and the build page's numbers as last synced. */
    public static final class View {

        /** {@link #PAGE_NEXUS} or {@link #PAGE_BUILD}; the client's own. */
        public int page = PAGE_NEXUS;
        /** The bill page shown (0 or 1). */
        public int billPage;
        /** When 取消 was clicked once ({@code System.currentTimeMillis}); 0 when it was not. */
        public long cancelAt;
        /** The build page's numbers. */
        public final Build build = new Build();
        boolean opened;

        /** Whether the next click on 取消 confirms it. */
        public boolean cancelArmed() {
            return cancelAt != 0 && System.currentTimeMillis() - cancelAt <= CANCEL_MS;
        }
    }

    /** One line of the bill: what it is, how much the ledger holds of it and how much the job needs, in items. */
    public static final class Row {

        /** The item it stands for (one), or null when nothing in the pack does. */
        public final ItemStack item;
        /** The ledger key ({@code ore:...}, {@code item:...}, {@code part:<code>}). */
        public final String key;
        public final long have, need;

        Row(ItemStack item, String key, long have, long need) {
            this.item = item;
            this.key = key;
            this.have = have;
            this.need = need;
        }
    }

    /** The build page's numbers as the client got them ({@link #tag} on the server). */
    public static final class Build {

        /** Whether the first compound has come. */
        public boolean received;
        /** Whether the campus may build at all (config), and whether this one is active (not a 0.9.2 nexus). */
        public boolean enabled = true, active;
        public boolean skipBlocked, canRepair, started, canMove;
        /** The job key and its plan key; both empty without a job. */
        public String job = "", planKey = "";
        public int site = -1, state, pause, stage, stages, placed, total, cleared, blocked, skipped, unloaded;
        /** EU per tick the builder drew lately, and the estimated ticks left (-1: cannot progress). */
        public long eu, eta;
        /** Whole items in the balance that can be taken out, finished parts held, items among the spoils. */
        public int balanceItems, balanceParts, spoils;
        /** The bill (at most {@link #BILL_MAX} lines, the module's core first) and the queued jobs {key, plan key}. */
        public List<Row> bill = Collections.emptyList();
        public List<String[]> queue = Collections.emptyList();

        public void read(NBTTagCompound t) {
            if (t == null) t = new NBTTagCompound();
            received = true;
            enabled = t.getBoolean("En");
            active = t.getBoolean("Ac");
            skipBlocked = t.getBoolean("Sk");
            canRepair = t.getBoolean("Rp");
            started = t.getBoolean("Go");
            canMove = t.getBoolean("Mv");
            job = t.getString("K");
            planKey = t.hasKey("Pk") ? t.getString("Pk") : job;
            site = t.hasKey("S") ? t.getInteger("S") : -1;
            state = t.getByte("St");
            pause = t.getByte("Ps");
            stage = t.getByte("Sg");
            stages = t.getByte("Sn");
            placed = t.getInteger("Pl");
            total = t.getInteger("To");
            cleared = t.getInteger("Cl");
            blocked = t.getInteger("Bk");
            skipped = t.getInteger("Sp");
            unloaded = t.getInteger("Un");
            eu = t.getLong("Eu");
            eta = t.getLong("Et");
            balanceItems = t.getInteger("Bi");
            balanceParts = t.getInteger("Bp");
            spoils = t.getInteger("Sv");
            List<Row> rows = new ArrayList<>();
            NBTTagList b = t.getTagList("B", 10);
            for (int i = 0; i < b.tagCount(); i++) {
                NBTTagCompound r = b.getCompoundTagAt(i);
                ItemStack s = r.hasKey("I") ? ItemStack.loadItemStackFromNBT(r.getCompoundTag("I")) : null;
                rows.add(new Row(s, r.getString("K"), r.getLong("H"), r.getLong("N")));
            }
            bill = rows;
            List<String[]> q = new ArrayList<>();
            NBTTagList ql = t.getTagList("Q", 10);
            for (int i = 0; i < ql.tagCount(); i++) {
                NBTTagCompound e = ql.getCompoundTagAt(i);
                q.add(new String[] { e.getString("K"), e.getString("Pk") });
            }
            queue = q;
        }

        public boolean hasJob() {
            return !job.isEmpty();
        }

        /** Whether there is a job that is not over. */
        public boolean live() {
            return hasJob() && !BuildState.ended(stateEnum());
        }

        public BuildState.State stateEnum() {
            return stateOf(state);
        }

        public BuildState.Pause pauseEnum() {
            return pauseOf(pause);
        }

        /** Whether the 开始/暂停 button reads 开始. */
        public boolean startMode() {
            return live() && NexusGui.startMode(stateEnum(), pauseEnum(), started);
        }
    }

    static ModularWindow window(TileNexus n, UIBuildContext ctx) {
        View view = new View();
        ModularWindow.Builder b = ModularWindow.builder(W, H);
        b.setBackground((x, y, w, h, partial) -> NexusScreen.background(n, view.page, x, y, w, h));
        sync(n, b);
        b.bindPlayerInventory(ctx.getPlayer(), new Pos2d((W - 162) / 2, INV_Y), FluxMachineGui.SLOT);

        List<SlotWidget> nexusSlots = new ArrayList<>();
        for (int i = 0; i < TileNexus.INPUTS; i++) {
            SlotWidget s = new SlotWidget(n.inv, i).setAccess(true, true);
            s.setBackground(FluxMachineGui.SLOT);
            s.setPos(IN_X + i % 3 * 18, IN_Y + i / 3 * 18);
            nexusSlots.add(s);
            b.widget(s);
        }
        for (int i = 0; i < TileNexus.OUTPUTS; i++) {
            SlotWidget s = new SlotWidget(n.inv, TileNexus.INPUTS + i).setAccess(true, false);
            s.setBackground(FluxMachineGui.SLOT_OUT);
            s.setPos(OUT_X, IN_Y + i * 18);
            nexusSlots.add(s);
            b.widget(s);
        }
        // the build page's intake: hidden until the page is shown (the slot tells the server itself)
        SlotWidget intake = new SlotWidget(
            n.campus()
                .intake(),
            0).setAccess(true, true);
        intake.setBackground(FluxMachineGui.SLOT);
        intake.setPos(INTAKE_X, INTAKE_Y);
        intake.addTooltip(EchoText.t("build.gui.intake.tip"));
        intake.setEnabled(false);
        b.widget(intake);
        Runnable toNexus = () -> show(view, PAGE_NEXUS, nexusSlots, intake);
        Runnable toBuild = () -> show(view, PAGE_BUILD, nexusSlots, intake);

        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> NexusScreen.header(n, x, y, w, h))
                .setTicker(widget -> {
                    // the first screen tick: open on the build page while the campus raises the nexus
                    if (view.opened) return;
                    view.opened = true;
                    if (!n.formed() && n.clientCampus.hasJob()) toBuild.run();
                })
                .setPos(6, 5)
                .setSize(HEADER_W, 14));
        b.widget(
            new ButtonWidget().setOnClick((click, widget) -> { if (widget.isClient()) toNexus.run(); })
                .setBackground(
                    (x, y, w, h, partial) -> NexusScreen
                        .tab(EchoText.t("build.gui.tab_nexus"), x, y, w, h, view.page == PAGE_NEXUS))
                .setPos(TAB_NEXUS_X, TAB_Y)
                .setSize(TAB_W, TAB_H));
        b.widget(
            new ButtonWidget().setOnClick((click, widget) -> { if (widget.isClient()) toBuild.run(); })
                .setBackground(
                    (x, y, w, h, partial) -> NexusScreen
                        .tab(EchoText.t("build.gui.tab_build"), x, y, w, h, view.page == PAGE_BUILD))
                .setPos(TAB_BUILD_X, TAB_Y)
                .setSize(TAB_W, TAB_H));

        nexusPage(n, ctx, b, view);
        buildPage(n, b, view);
        return b.build();
    }

    /** Shows a page: the nexus's slots only on its page, the intake only on the build page. */
    private static void show(View view, int page, List<SlotWidget> nexusSlots, SlotWidget intake) {
        view.page = page;
        view.cancelAt = 0;
        for (SlotWidget s : nexusSlots) s.setEnabled(page == PAGE_NEXUS);
        intake.setEnabled(page == PAGE_BUILD);
    }

    /** The nexus page: the making, the table, the state, the star map, the hologram and the binding switch. */
    private static void nexusPage(TileNexus n, UIBuildContext ctx, ModularWindow.Builder b, View view) {
        Function<Widget, Boolean> on = widget -> view.page == PAGE_NEXUS;
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> NexusScreen.making(n, x, y, w, h))
                .setNEITransferRect(NexusNei.ID)
                .setPos(68, 34)
                .setSize(42, 40)
                .setEnabled(on));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> NexusScreen.table(n, x, y, w, h))
                .setPos(6, 78)
                .setSize(130, 80)
                .setEnabled(on));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> NexusScreen.state(n, x, y, w, h))
                .setPos(140, 22)
                .setSize(102, 98)
                .setEnabled(on));

        b.widget(
            new ButtonWidget().setOnClick((click, widget) -> { if (widget.isClient()) NexusScreen.openStarMap(n); })
                .setBackground(
                    (x, y, w, h, partial) -> NexusScreen.button(EchoText.t("nexus.gui.star_map"), x, y, w, h, false))
                .addTooltip(EchoText.t("nexus.gui.star_map.tip"))
                .setPos(140, 124)
                .setSize(58, 18)
                .setEnabled(on));
        b.widget(
            new ButtonWidget().setOnClick((click, widget) -> { if (!widget.isClient()) n.setHologram(!n.hologram()); })
                .setBackground(
                    (x, y, w, h, partial) -> NexusScreen
                        .button(EchoText.t("nexus.gui.holo"), x, y, w, h, n.clientHologram))
                .addTooltip(EchoText.t("nexus.gui.holo.tip"))
                .setPos(200, 124)
                .setSize(42, 18)
                .setEnabled(on));
        b.widget(new FakeSyncWidget.BooleanSyncer(n::openBinding, open -> n.guiOpenBinding = open));
        boolean owner = n.team() != null && n.team()
            .equals(
                com.fluxecho.core.Owners.team(
                    ctx.getPlayer()
                        .getUniqueID()));
        b.widget(
            new ButtonWidget()
                .setOnClick((click, widget) -> { if (!widget.isClient() && owner) n.setOpenBinding(!n.openBinding()); })
                .setBackground(
                    (x, y, w, h, partial) -> NexusScreen.button(
                        EchoText.t(n.guiOpenBinding ? "nexus.gui.binding_open" : "nexus.gui.binding_closed"),
                        x,
                        y,
                        w,
                        h,
                        n.guiOpenBinding))
                .addTooltip(EchoText.t(owner ? "nexus.gui.binding.tip" : "nexus.gui.binding.owner_only"))
                .setPos(140, 144)
                .setSize(102, 16)
                .setEnabled(on));
    }

    /**
     * The build page: the job panel, the buttons below it (开始/暂停, 补给, 取消; ◀ site ▶, 跳过受阻; 取出, 铺设广场 or
     * 修复), the bill with its pager, the intake slot and the balance on the right.
     */
    private static void buildPage(TileNexus n, ModularWindow.Builder b, View view) {
        Function<Widget, Boolean> on = widget -> view.page == PAGE_BUILD;
        Build bd = view.build;
        Feed feed = new Feed(n);
        b.widget(new FakeSyncWidget<>(feed, bd::read, NexusGui::write, NexusGui::read));

        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> NexusScreen.buildJob(view, x, y, w, h))
                .setPos(JOB_X, JOB_Y)
                .setSize(JOB_W, JOB_H)
                .setEnabled(on));

        // row 1: 开始/暂停, 补给, 取消 (two clicks: the first arms, the second, on its own button, confirms)
        b.widget(
            button(
                () -> EchoText.t(!bd.live() || bd.startMode() ? "build.gui.start" : "build.gui.pause"),
                () -> bd.startMode(),
                () -> !bd.live(),
                () -> EchoText.lines(!bd.live() || bd.startMode() ? "build.gui.start.tip" : "build.gui.pause.tip"),
                (widget) -> act(n, widget, feed, p -> startOrPause(n.campus(), p))).setPos(8, ROW1_Y)
                    .setSize(40, BTN_H)
                    .setEnabled(on));
        b.widget(
            button(
                () -> EchoText.t("build.gui.supply"),
                () -> false,
                () -> !bd.live(),
                () -> EchoText.lines("build.gui.supply.tip"),
                (widget) -> act(
                    n,
                    widget,
                    feed,
                    p -> n.campus()
                        .supply(p))).setPos(50, ROW1_Y)
                            .setSize(40, BTN_H)
                            .setEnabled(on));
        b.widget(
            button(
                () -> EchoText.t("build.gui.cancel"),
                () -> false,
                () -> !bd.live(),
                () -> EchoText.lines("build.gui.cancel.tip"),
                (widget) -> { if (widget.isClient()) view.cancelAt = System.currentTimeMillis(); }).setPos(92, ROW1_Y)
                    .setSize(42, BTN_H)
                    .setEnabled(widget -> view.page == PAGE_BUILD && !view.cancelArmed()));
        b.widget(new ButtonWidget().setOnClick((click, widget) -> {
            if (widget.isClient()) view.cancelAt = 0;
            else act(
                n,
                widget,
                feed,
                p -> n.campus()
                    .cancel(p));
        })
            .setBackground(
                (x, y, w, h, partial) -> NexusScreen
                    .button(EchoText.t("build.gui.cancel_confirm"), x, y, w, h, true, false, NexusScreen.DANGER))
            .dynamicTooltip(() -> EchoText.lines("build.gui.cancel.tip"))
            .setPos(92, ROW1_Y)
            .setSize(42, BTN_H)
            .setEnabled(widget -> view.page == PAGE_BUILD && view.cancelArmed()));

        // row 2: ◀ site ▶, 跳过受阻
        b.widget(
            button(
                () -> "◀",
                () -> false,
                () -> !bd.canMove,
                () -> EchoText.lines("build.gui.site_prev.tip"),
                (widget) -> act(
                    n,
                    widget,
                    feed,
                    p -> n.campus()
                        .moveSite(p, -1))).setPos(8, ROW2_Y)
                            .setSize(14, BTN_H)
                            .setEnabled(on));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> NexusScreen.site(view, x, y, w, h))
                .setPos(22, ROW2_Y)
                .setSize(36, BTN_H)
                .setEnabled(on));
        b.widget(
            button(
                () -> "▶",
                () -> false,
                () -> !bd.canMove,
                () -> EchoText.lines("build.gui.site_next.tip"),
                (widget) -> act(
                    n,
                    widget,
                    feed,
                    p -> n.campus()
                        .moveSite(p, 1))).setPos(58, ROW2_Y)
                            .setSize(14, BTN_H)
                            .setEnabled(on));
        b.widget(
            button(
                () -> EchoText.t("build.gui.skip_blocked"),
                () -> bd.skipBlocked,
                () -> !bd.active,
                () -> EchoText.lines("build.gui.skip_blocked.tip"),
                (widget) -> act(n, widget, feed, p -> toggleSkip(n.campus()))).setPos(74, ROW2_Y)
                    .setSize(60, BTN_H)
                    .setEnabled(on));

        // row 3: 取出, 铺设广场 (a 0.9.2 nexus) or 修复
        b.widget(
            button(
                () -> EchoText.t("build.gui.withdraw"),
                () -> false,
                () -> bd.balanceItems <= 0 && bd.spoils <= 0,
                () -> EchoText.lines("build.gui.withdraw.tip"),
                (widget) -> act(
                    n,
                    widget,
                    feed,
                    p -> n.campus()
                        .withdraw(p))).setPos(8, ROW3_Y)
                            .setSize(40, BTN_H)
                            .setEnabled(on));
        b.widget(
            button(
                () -> EchoText.t(bd.active ? "build.gui.repair" : "build.gui.lay_forum"),
                () -> !bd.active && bd.enabled,
                () -> bd.active ? !bd.canRepair : !bd.enabled,
                () -> EchoText.lines(bd.active ? "build.gui.repair.tip" : "build.gui.lay_forum.tip"),
                (widget) -> act(n, widget, feed, p -> forumOrRepair(n.campus(), p))).setPos(50, ROW3_Y)
                    .setSize(84, BTN_H)
                    .setEnabled(on));

        // the bill: its title, the pager, eight lines
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> NexusScreen.billHead(view, x, y, w, h))
                .setPos(BILL_X, BILL_Y)
                .setSize(BILL_HEAD_W, 10)
                .setEnabled(on));
        b.widget(new ButtonWidget().setOnClick((click, widget) -> {
            if (!widget.isClient()) return;
            int pages = Math.max(1, (bd.bill.size() + BILL_ROWS - 1) / BILL_ROWS);
            view.billPage = (view.billPage + 1) % pages;
        })
            .setBackground((x, y, w, h, partial) -> NexusScreen.pager(view, x, y, w, h))
            .dynamicTooltip(() -> EchoText.lines("build.gui.bill_page.tip"))
            .setPos(PAGER_X, BILL_Y)
            .setSize(PAGER_W, PAGER_H)
            .setEnabled(widget -> view.page == PAGE_BUILD && bd.bill.size() > BILL_ROWS));
        for (int i = 0; i < BILL_ROWS; i++) {
            int line = i;
            b.widget(
                new DrawableWidget().setDrawable((x, y, w, h, partial) -> NexusScreen.billRow(view, line, x, y, w, h))
                    .dynamicTooltip(() -> rowTooltip(view, line))
                    .setPos(BILL_X, ROWS_Y + i * ROW_H)
                    .setSize(BILL_W, ROW_H)
                    .setEnabled(on));
        }
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> NexusScreen.buildFoot(view, x, y, w, h))
                .setPos(FOOT_X, FOOT_Y)
                .setSize(FOOT_W, FOOT_H)
                .setEnabled(on));
    }

    /** A flux button with a changing label and state; {@code click} runs on both sides. */
    private static Widget button(Supplier<String> label, Supplier<Boolean> lit, Supplier<Boolean> dim,
        Supplier<List<String>> tip, java.util.function.Consumer<Widget> click) {
        return new ButtonWidget().setOnClick((c, widget) -> click.accept(widget))
            .setBackground(
                (x, y, w, h, partial) -> NexusScreen
                    .button(label.get(), x, y, w, h, lit.get(), dim.get(), NexusScreen.ACCENT))
            .dynamicTooltip(tip);
    }

    /** The bill line's tooltip: the item's name and what is held against what is needed. */
    private static List<String> rowTooltip(View view, int line) {
        int i = view.billPage * BILL_ROWS + line;
        List<Row> bill = view.build.bill;
        if (i >= bill.size()) return Collections.emptyList();
        Row r = bill.get(i);
        List<String> out = new ArrayList<>();
        out.add(r.item != null ? r.item.getDisplayName() : r.key);
        out.add(EchoText.t("build.gui.have_need", r.have, r.need));
        if (r.have < r.need) out.add(EchoText.t("build.gui.missing", r.need - r.have));
        return out;
    }

    // ---- server side

    /**
     * Runs a button's action for the clicking player on the server: a member of the nexus within reach, while the
     * campus may build; the outcome (a lang key) goes to the player as a chat line.
     */
    private static void act(TileNexus n, Widget widget, Feed feed, Function<EntityPlayer, String> action) {
        if (widget.isClient()) return;
        EntityPlayer p = widget.getContext()
            .getPlayer();
        if (p == null) return;
        String r = refuse(n, p);
        if (r == null) {
            try {
                r = action.apply(p);
            } catch (RuntimeException e) {
                FluxEcho.LOG
                    .warn("A build button of the Flux Nexus at {},{},{} failed", n.xCoord, n.yCoord, n.zCoord, e);
                r = null;
            }
            feed.stale();
        }
        if (r != null && !r.isEmpty()) p.addChatMessage(new ChatComponentTranslation(r));
    }

    /** Why the player may not use the build page's buttons (a lang key), or null when they may. */
    static String refuse(TileNexus n, EntityPlayer p) {
        if (!Config.nexusEnabled || !Config.campusEnabled) return P + "disabled";
        if (n.isInvalid() || n.getWorldObj() == null) return "";
        if (!n.member(p)) return P + "not_member";
        if (p.getDistanceSq(n.xCoord + 0.5, n.yCoord + 0.5, n.zCoord + 0.5) > REACH_SQ) return P + "too_far";
        return null;
    }

    /** 开始 when the job can start (or record consent during its survey), else 暂停. */
    private static String startOrPause(Campus c, EntityPlayer p) {
        BuildJob j = c.job();
        if (j != null && startMode(j.state(), j.pause(), j.started())) return c.start(p);
        return c.pause(p);
    }

    /** 跳过受阻 on or off. */
    private static String toggleSkip(Campus c) {
        if (!c.active()) return P + "no_job";
        c.setSkipBlocked(!c.skipBlocked());
        return P + (c.skipBlocked() ? "skip_on" : "skip_off");
    }

    /** 铺设广场 on a 0.9.2 nexus, 修复 on an active campus. */
    private static String forumOrRepair(Campus c, EntityPlayer p) {
        return c.active() ? c.repair(p) : c.layForum(p);
    }

    /**
     * Whether the 开始/暂停 button starts: the job can start ({@link BuildState#canStart}), or it is still surveyed
     * and no member has said 开始 yet (or a member paused it during the survey). The client labels the button and the
     * server acts by the same rule.
     */
    public static boolean startMode(BuildState.State s, BuildState.Pause p, boolean started) {
        return BuildState.canStart(s, p) || s == BuildState.State.SURVEY && (!started || p == BuildState.Pause.PLAYER);
    }

    /**
     * The build page's numbers, rebuilt at most every {@link #REFRESH} ticks (at once after a button) and sent only
     * when they changed. The EU/t is the average over the ticks since the last rebuild, so the line does not flicker
     * between the builder's busy and idle ticks. Its getter runs on the server every tick the window is open.
     */
    static final class Feed implements Supplier<NBTTagCompound> {

        private final TileNexus n;
        private NBTTagCompound last;
        private long at;
        private boolean stale = true;
        private long euSum;
        private int euTicks;

        Feed(TileNexus n) {
            this.n = n;
        }

        @Override
        public NBTTagCompound get() {
            World w = n.getWorldObj();
            long now = w == null ? 0 : w.getTotalWorldTime();
            euSum += n.campus()
                .euPerTick();
            euTicks++;
            if (!stale && last != null && now >= at && now - at < REFRESH) return last;
            stale = false;
            at = now;
            long eu = euSum / Math.max(1, euTicks);
            euSum = 0;
            euTicks = 0;
            last = tag(n, eu);
            return last;
        }

        void stale() {
            stale = true;
        }
    }

    /** The build page's compound (read by {@link Build#read}). */
    static NBTTagCompound tag(TileNexus n, long eu) {
        Campus c = n.campus();
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("En", Config.nexusEnabled && Config.campusEnabled);
        t.setBoolean("Ac", c.active());
        t.setBoolean("Sk", c.skipBlocked());
        t.setBoolean("Rp", c.active() && c.canRepair());
        BuildLedger ledger = c.ledger();
        int items = 0, parts = 0, spoils = 0;
        for (int v : ledger.withdrawable()
            .values()) items += v;
        for (int v : ledger.partView()
            .values()) parts += v;
        for (int i = 0; i < c.spoils()
            .getSlots(); i++) {
            ItemStack s = c.spoils()
                .getStackInSlot(i);
            if (s != null) spoils += s.stackSize;
        }
        t.setInteger("Bi", items);
        t.setInteger("Bp", parts);
        t.setInteger("Sv", spoils);
        NBTTagList q = new NBTTagList();
        for (String k : c.queue()) {
            NBTTagCompound e = new NBTTagCompound();
            e.setString("K", k);
            e.setString("Pk", queuedPlanKey(n, k));
            q.appendTag(e);
        }
        t.setTag("Q", q);
        BuildJob j = c.job();
        if (j == null) return t;
        boolean ended = BuildState.ended(j.state());
        t.setString("K", j.key());
        t.setString("Pk", j.planKey());
        t.setInteger("S", j.site());
        t.setByte(
            "St",
            (byte) j.state()
                .ordinal());
        t.setByte(
            "Ps",
            (byte) j.pause()
                .ordinal());
        t.setByte("Sg", (byte) j.stage());
        BuildPlan.Plan pl = j.plan(c);
        t.setByte("Sn", (byte) (pl == null ? 0 : pl.stages));
        t.setInteger("Pl", c.placed());
        t.setInteger("To", c.total());
        t.setInteger("Cl", c.cleared());
        t.setInteger("Bk", c.blocked());
        t.setInteger("Sp", c.skipped());
        t.setInteger("Un", c.unloaded());
        t.setLong("Eu", eu);
        t.setLong("Et", ended ? 0 : c.etaTicks());
        t.setBoolean("Go", j.started());
        t.setBoolean(
            "Mv",
            !ended && j.module() != null
                && !j.repair()
                && !j.touched()
                && j.flights()
                    .isEmpty());
        NBTTagList bill = new NBTTagList();
        for (Map.Entry<String, Long> e : c.bill()
            .entrySet()) {
            if (bill.tagCount() >= BILL_MAX) break;
            NBTTagCompound r = new NBTTagCompound();
            r.setString("K", e.getKey());
            ItemStack s = Campus.stackOf(e.getKey(), 1);
            if (s != null) r.setTag("I", s.writeToNBT(new NBTTagCompound()));
            long have = have(ledger, e.getKey());
            long missing = (e.getValue() + PartRecipes.UNIT - 1) / PartRecipes.UNIT;
            r.setLong("H", have);
            r.setLong("N", have + missing);
            bill.appendTag(r);
        }
        t.setTag("B", bill);
        return t;
    }

    /** Whole items (or finished parts) the ledger holds of a bill key. */
    static long have(BuildLedger ledger, String key) {
        int code = PartRecipes.partCode(key);
        return code >= 0 ? ledger.part(code) : ledger.raw(key) / PartRecipes.UNIT;
    }

    /**
     * The plan key of a queued job, for its name: a module repair ({@code repair:<site>}) builds the job of the module
     * standing on its site; the others build their own key (a nexus repair builds the establish plan).
     */
    static String queuedPlanKey(TileNexus n, String key) {
        if (key == null || !key.startsWith(BuildJob.REPAIR)) return key;
        String rest = key.substring(BuildJob.REPAIR.length());
        if (BuildPlan.ESTABLISH.equals(rest)) return rest;
        try {
            int site = Integer.parseInt(rest);
            int[] at = n.campus()
                .siteController(site);
            World w = n.getWorldObj();
            if (at != null && w != null && w.blockExists(at[0], at[1], at[2])) {
                TileEntity te = w.getTileEntity(at[0], at[1], at[2]);
                ModuleSpec spec = te instanceof TileModule m ? ModuleSpecs.get(m.moduleKey()) : null;
                if (spec != null) return spec.jobKey(site);
            }
        } catch (NumberFormatException ignored) {}
        return key;
    }

    private static void write(PacketBuffer buf, NBTTagCompound t) {
        try {
            buf.writeNBTTagCompoundToBuffer(t);
        } catch (IOException e) {
            throw new IllegalStateException("could not write the build page", e);
        }
    }

    private static NBTTagCompound read(PacketBuffer buf) {
        try {
            return buf.readNBTTagCompoundFromBuffer();
        } catch (IOException e) {
            FluxEcho.LOG.warn("Could not read the build page", e);
            return null;
        }
    }

    // ---- texts shared by the GUI, Waila and the terminal

    public static BuildState.State stateOf(int ordinal) {
        BuildState.State[] v = BuildState.State.values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : BuildState.State.SURVEY;
    }

    public static BuildState.Pause pauseOf(int ordinal) {
        BuildState.Pause[] v = BuildState.Pause.values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : BuildState.Pause.NONE;
    }

    /**
     * The lang key (under {@code fluxecho.}) of a stage: {@code build.stage.e<n>} or {@code build.stage.m<n>}. A job
     * past its last stage (a module waiting for its structure to form, a finished job) shows its last stage.
     */
    public static String stageKey(String planKey, int stage) {
        boolean campus = BuildPlan.ESTABLISH.equals(planKey) || BuildPlan.FORUM.equals(planKey);
        int last = campus ? BuildPlan.E_FIXTURE : BuildPlan.M_COMMISSION;
        return "build.stage." + (campus ? "e" : "m") + Math.max(0, Math.min(stage, last));
    }

    /** The lang key (under {@code fluxecho.}) of a state. */
    public static String stateKey(BuildState.State s) {
        return "build.state." + s.name()
            .toLowerCase(Locale.ROOT);
    }

    /** The lang key (under {@code fluxecho.}) of a pause reason. */
    public static String pauseKey(BuildState.Pause p) {
        return "build.pause." + p.name()
            .toLowerCase(Locale.ROOT);
    }

    /**
     * Where a job is, in a few words (translated): its stage while it builds, why it waits while it is paused, else
     * its state.
     */
    public static String phaseText(String planKey, int state, int pause, int stage) {
        BuildState.State s = stateOf(state);
        if (s == BuildState.State.BUILDING) return EchoText.t(stageKey(planKey, stage));
        BuildState.Pause p = pauseOf(pause);
        if (s == BuildState.State.PAUSED && p != BuildState.Pause.NONE) return EchoText.t(pauseKey(p));
        return EchoText.t(stateKey(s));
    }

    // ---- the nexus page's numbers

    /** What the screen shows that only the server knows. */
    private static void sync(TileNexus n, ModularWindow.Builder b) {
        b.widget(new FakeSyncWidget.StringSyncer(() -> Directory.nexusStatus(n), v -> n.guiStatus = v));
        b.widget(new FakeSyncWidget.StringSyncer(n::research, v -> n.guiResearch = v));
        b.widget(new FakeSyncWidget.StringSyncer(n::manifest, v -> n.guiManifest = v));
        b.widget(new FakeSyncWidget.StringSyncer(n::balance, v -> n.guiBalance = v));
        b.widget(new FakeSyncWidget.StringSyncer(n::unlockedList, v -> n.guiUnlocked = v));
        b.widget(new FakeSyncWidget.LongSyncer(n::upkeep, v -> n.guiUpkeep = v));
        b.widget(
            new FakeSyncWidget.IntegerSyncer(() -> (int) Math.round(n.computeRate() * 10), v -> n.guiCompute10 = v));
        b.widget(
            new FakeSyncWidget.IntegerSyncer(() -> Math.round(n.researchFraction() * 1000), v -> n.guiResearchPm = v));
        b.widget(
            new FakeSyncWidget.IntegerSyncer(() -> Math.round(n.manifestFraction() * 1000), v -> n.guiManifestPm = v));
        b.widget(
            new FakeSyncWidget.IntegerSyncer(
                () -> n.docked()
                    .size(),
                v -> n.guiDocked = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(n::openSlots, v -> n.guiOpen = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(n::boundCount, v -> n.guiBound = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(n::recordCount, v -> n.guiRecords = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(n::restingCount, v -> n.guiResting = v));
    }

    /** The cooldown of a codex entry in minutes, for the GUI. */
    public static int cooldownMinutes() {
        return Math.max(1, Config.recordCooldown / 1200);
    }
}
