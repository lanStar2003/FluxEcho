package com.fluxecho.campus;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ChatComponentTranslation;

import com.fluxecho.FluxEcho;
import com.fluxecho.campus.client.RecycleScreen;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.FluxMachineGui;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.nexus.client.NexusScreen;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;

/**
 * The recycle bin's screen (0.10.1), in the flux look: the campus's recycle store as a grid nine kinds wide, the
 * largest first, each kind with its icon and how many the store holds, scrolled by the mouse wheel or the bar on its
 * right; below it {@code N 种 / M 个} and the 全部取出 button; then the player's inventory.
 * <p>
 * A click on a kind takes a stack of it to the cursor (onto a stack of the same kind the cursor holds, while it has
 * room), a right click half a stack, a shift-click a stack straight into the inventory (as much as fits); 全部取出
 * fills the inventory from the largest kinds down ({@link Campus#takeRecycled(EntityPlayer)}). Nothing is predicted on
 * the client: a click names the kind it drew (its store index and the hash of the whole kind, {@link RecyclePage}) and
 * the server acts for a member of the bin's team standing within 8 blocks of it while its nexus is loaded; a refusal
 * comes back as a chat line.
 * <p>
 * The store can hold thousands of kinds with heavy NBT, so the server sends only the rows shown ({@link #ROWS} rows of
 * {@link #COLS}) as one {@link RecyclePage}, made again when the store changes or the client scrolls ({@link Link}),
 * and sent only when it changed.
 */
public final class RecycleGui {

    /** The window, and where the player's inventory starts. */
    public static final int W = 190, H = 240, INV_Y = 158;
    /** The grid: its corner, kinds per row, rows shown, cell size; the scroll bar on its right. */
    public static final int GRID_X = 10, GRID_Y = 24, COLS = 9, ROWS = 6, CELL = 18, SCROLL_X = 176, SCROLL_W = 6;
    /** The line under the grid and the 全部取出 button. */
    public static final int FOOT_X = 10, FOOT_Y = 138, FOOT_W = 110, ALL_X = 124, ALL_Y = 136, ALL_W = 58, ALL_H = 14;
    /** Kinds on one page. */
    public static final int PAGE = COLS * ROWS;
    /** How a click takes: a stack to the cursor, half a stack to the cursor, a stack into the inventory. */
    public static final int STACK = 0, HALF = 1, INVENTORY = 2;
    /** What the screen knows of the link: linked and loaded, linked but its nexus not loaded, not linked. */
    public static final int LINKED = 0, UNLOADED = 1, UNLINKED = 2;
    /** How near a member must stand to take from it (8 blocks, squared). */
    static final double REACH_SQ = 64;

    private static final String P = "fluxecho.recycle_bin.";

    private RecycleGui() {}

    /** One open window's client side: the first row shown and the last page the server sent. */
    public static final class View {

        /** The first row of the grid shown. */
        public int row;
        public RecyclePage.Page page = RecyclePage.Page.EMPTY;
        /** One item of each kind on the page (null where the item is not in the game), in page order. */
        public ItemStack[] stacks = new ItemStack[0];
        /** {@link #LINKED}, {@link #UNLOADED} or {@link #UNLINKED}; where the nexus is ("" when unknown). */
        public int link = LINKED;
        public String nexus = "";
        /** Whether the store is full (the builder waits until some is taken out). */
        public boolean full;
        public boolean received;

        void read(NBTTagCompound t) {
            if (t == null) t = new NBTTagCompound();
            received = true;
            link = t.getByte("L");
            nexus = t.getString("Nx");
            full = t.getBoolean("Fu");
            page = RecyclePage.read(t.getCompoundTag("P"));
            ItemStack[] s = new ItemStack[page.entries.size()];
            for (int i = 0; i < s.length; i++) s[i] = RecycleStore.stackOf(page.entries.get(i).kind, 1);
            stacks = s;
            row = clampRow(row, page.kinds);
        }

        /** The store index of a cell of the grid. */
        public int index(int cell) {
            return row * COLS + cell;
        }

        /** The page entry drawn in a cell, or null (an empty cell, or one whose row has not come yet). */
        public RecyclePage.Entry entry(int cell) {
            return page.at(index(cell));
        }

        /** One item of the kind drawn in a cell, or null. */
        public ItemStack stack(int cell) {
            int i = index(cell) - page.first;
            return i < 0 || i >= stacks.length ? null : stacks[i];
        }

        /** How many rows the store fills. */
        public int rows() {
            return (page.kinds + COLS - 1) / COLS;
        }
    }

    /** The first row brought into range for a store of {@code kinds} kinds. */
    static int clampRow(int row, int kinds) {
        int rows = (kinds + COLS - 1) / COLS;
        return Math.max(0, Math.min(row, rows - ROWS));
    }

    /**
     * The server's side of a window: the row the client shows, and the compound it was last sent, made again when the
     * row or the link changed, after the player took something, and when the store changed, at most every
     * {@link #STORE_TICKS} ticks for that (while the builder clears, the store changes every tick).
     */
    static final class Feed implements Supplier<NBTTagCompound> {

        static final int STORE_TICKS = 5;
        private final TileRecycleBin bin;
        int row;
        /** Set by the player's own actions: the next page goes out at once. */
        boolean stale;
        private NBTTagCompound last;
        private RecycleStore lastStore;
        private int lastVersion = Integer.MIN_VALUE, lastRow = -1, lastLink = -1;
        private long builtAt = Long.MIN_VALUE;

        Feed(TileRecycleBin bin) {
            this.bin = bin;
        }

        @Override
        public NBTTagCompound get() {
            TileNexus n = bin.nexus();
            RecycleStore store = n == null ? null
                : n.campus()
                    .recycle();
            int link = n != null ? LINKED : bin.linked() ? UNLOADED : UNLINKED;
            int version = store == null ? 0 : store.version();
            long now = bin.getWorldObj() == null ? 0
                : bin.getWorldObj()
                    .getTotalWorldTime();
            boolean same = last != null && store == lastStore && row == lastRow && link == lastLink;
            if (same && (version == lastVersion || !stale && now >= builtAt && now - builtAt < STORE_TICKS))
                return last;
            stale = false;
            builtAt = now;
            lastStore = store;
            lastVersion = version;
            lastRow = row;
            lastLink = link;
            NBTTagCompound t = new NBTTagCompound();
            t.setByte("L", (byte) link);
            int[] at = bin.linkPos();
            if (at != null) t.setString("Nx", at[0] + ", " + at[1] + ", " + at[2]);
            if (store != null) {
                List<RecycleStore.Entry> list = store.list();
                row = clampRow(row, list.size());
                lastRow = row;
                t.setBoolean("Fu", store.full());
                t.setTag("P", RecyclePage.write(list, row * COLS, PAGE, store.total()));
            }
            last = t;
            return t;
        }
    }

    /**
     * The window's link to the server: the page shown (server to client), and the client's asks (another row, a kind
     * taken). The server checks every ask.
     */
    static final class Link extends FakeSyncWidget<NBTTagCompound> {

        static final int ASK_ROW = 1, ASK_TAKE = 2;
        private final TileRecycleBin bin;
        private final Feed feed;

        Link(TileRecycleBin bin, Feed feed, View view) {
            super(feed, view::read, RecycleGui::write, RecycleGui::read);
            this.bin = bin;
            this.feed = feed;
        }

        /** Client: asks for the page from a row. */
        void ask(int row) {
            syncToServer(ASK_ROW, buf -> buf.writeVarIntToBuffer(Math.max(0, row)));
        }

        /** Client: asks to take the kind drawn at a store index, the way the click said. */
        void take(int index, int hash, int mode) {
            syncToServer(ASK_TAKE, buf -> {
                buf.writeVarIntToBuffer(Math.max(0, index));
                buf.writeInt(hash);
                buf.writeByte(mode);
            });
        }

        @Override
        public void readOnServer(int id, PacketBuffer buf) throws IOException {
            if (id == ASK_ROW) {
                feed.row = Math.max(0, buf.readVarIntFromBuffer());
                return;
            }
            if (id != ASK_TAKE) {
                super.readOnServer(id, buf);
                return;
            }
            int index = buf.readVarIntFromBuffer(), hash = buf.readInt(), mode = buf.readByte();
            EntityPlayer p = getContext() == null ? null : getContext().getPlayer();
            if (p == null) return;
            try {
                RecycleGui.take(bin, p, index, hash, mode);
                feed.stale = true;
            } catch (RuntimeException e) {
                FluxEcho.LOG
                    .warn("Taking from the recycle bin at {},{},{} failed", bin.xCoord, bin.yCoord, bin.zCoord, e);
            }
        }
    }

    /** A button of the grid that scrolls it with the mouse wheel (client). */
    private static final class ScrollButton extends ButtonWidget {

        private final View view;
        private final Link link;

        ScrollButton(View view, Link link) {
            this.view = view;
            this.link = link;
        }

        @Override
        public boolean onMouseScroll(int direction) {
            if (direction == 0) return false;
            scrollTo(view, link, view.row + (direction > 0 ? -1 : 1));
            return true;
        }
    }

    /** Client: shows the grid from a row (brought into range) and asks the server for it. */
    static void scrollTo(View view, Link link, int row) {
        int r = clampRow(row, view.page.kinds);
        if (r == view.row) return;
        view.row = r;
        link.ask(r);
    }

    static ModularWindow window(TileRecycleBin bin, UIBuildContext ctx) {
        // the window closes once the bin is broken (or its chunk goes)
        ctx.setValidator(bin::standing);
        EntityPlayer player = ctx.getPlayer();
        View view = new View();
        Feed feed = new Feed(bin);
        Link link = new Link(bin, feed, view);

        ModularWindow.Builder b = ModularWindow.builder(W, H);
        b.setBackground((x, y, w, h, partial) -> RecycleScreen.background(x, y, w, h));
        b.bindPlayerInventory(player, new Pos2d((W - 162) / 2, INV_Y), FluxMachineGui.SLOT);
        b.widget(link);
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> RecycleScreen.header(view, x, y, w, h))
                .setPos(6, 5)
                .setSize(W - 12, 14));

        for (int i = 0; i < PAGE; i++) {
            int cell = i;
            ScrollButton c = new ScrollButton(view, link);
            c.setOnClick((click, widget) -> {
                if (!widget.isClient()) return;
                RecyclePage.Entry e = view.entry(cell);
                if (e == null) return;
                int mode = click.shift ? INVENTORY : click.mouseButton == 1 ? HALF : STACK;
                link.take(view.index(cell), e.hash, mode);
            });
            c.setBackground((x, y, w, h, partial) -> RecycleScreen.cell(view, cell, c.isHovering(), x, y, w, h));
            c.dynamicTooltip(() -> RecycleScreen.cellTip(view, cell));
            c.setUpdateTooltipEveryTick(true);
            c.setPos(GRID_X + i % COLS * CELL, GRID_Y + i / COLS * CELL);
            c.setSize(CELL, CELL);
            b.widget(c);
        }
        ScrollButton bar = new ScrollButton(view, link);
        bar.setOnClick((click, widget) -> {
            if (!widget.isClient() || widget.getContext() == null) return;
            Pos2d m = widget.getContext()
                .getMousePos(), at = widget.getAbsolutePos();
            if (m == null || at == null) return;
            double f = (m.y - at.y + 0.5) / (ROWS * CELL);
            int max = Math.max(0, view.rows() - ROWS);
            scrollTo(view, link, (int) Math.round(Math.max(0, Math.min(1, f)) * max));
        });
        bar.setBackground((x, y, w, h, partial) -> RecycleScreen.scrollbar(view, x, y, w, h));
        bar.setPos(SCROLL_X, GRID_Y);
        bar.setSize(SCROLL_W, ROWS * CELL);
        b.widget(bar);

        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> RecycleScreen.foot(view, x, y, w, h))
                .setPos(FOOT_X, FOOT_Y)
                .setSize(FOOT_W, 10));
        b.widget(new ButtonWidget().setOnClick((click, widget) -> {
            if (widget.isClient()) return;
            EntityPlayer p = widget.getContext()
                .getPlayer();
            if (p == null) return;
            takeAll(bin, p);
            feed.stale = true;
        })
            .setBackground(
                (x, y, w, h, partial) -> NexusScreen.button(
                    EchoText.t("recycle_bin.gui.take_all"),
                    x,
                    y,
                    w,
                    h,
                    false,
                    view.page.kinds == 0,
                    NexusScreen.ACCENT))
            .dynamicTooltip(() -> EchoText.lines("recycle_bin.gui.take_all.tip"))
            .setPos(ALL_X, ALL_Y)
            .setSize(ALL_W, ALL_H));
        return b.build();
    }

    // ---- server side

    /** Why the player may not take from the bin (a lang key; "" to say nothing), or null when they may. */
    static String refuse(TileRecycleBin bin, EntityPlayer p) {
        if (!bin.standing()) return "";
        if (!bin.mayUse(p)) return P + "not_yours";
        // the same block in another dimension is not within reach
        if (p.worldObj != bin.getWorldObj()
            || p.getDistanceSq(bin.xCoord + 0.5, bin.yCoord + 0.5, bin.zCoord + 0.5) > REACH_SQ) return P + "too_far";
        if (bin.nexus() == null) return bin.linked() ? P + "unloaded" : P + "unlinked";
        return null;
    }

    private static void tell(EntityPlayer p, String key, Object... args) {
        if (key != null && !key.isEmpty()) p.addChatMessage(new ChatComponentTranslation(key, args));
    }

    /**
     * A click on a kind: a stack (or half of one) to the cursor while it is empty or holds the same kind with room,
     * else (and for a shift-click) a stack into the inventory, as much as fits. What is not taken stays in the store.
     */
    static void take(TileRecycleBin bin, EntityPlayer p, int index, int hash, int mode) {
        String why = refuse(bin, p);
        if (why != null) {
            tell(p, why);
            return;
        }
        Campus c = bin.nexus()
            .campus();
        RecycleStore store = c.recycle();
        List<RecycleStore.Entry> list = store.list();
        int at = RecyclePage.find(list, index, hash);
        if (at < 0) return;
        RecycleStore.Entry e = list.get(at);
        ItemStack one = RecycleStore.stackOf(e.kind, 1);
        if (one == null) {
            tell(p, P + "unknown_item");
            return;
        }
        int max = Math.max(1, Math.min(one.getMaxStackSize(), p.inventory.getInventoryStackLimit()));
        int stack = (int) Math.min(e.count, max);
        ItemStack cursor = p.inventory.getItemStack();
        boolean toCursor = mode != INVENTORY && (cursor == null || cursor.stackSize < max && sameKind(cursor, one));
        if (toCursor) {
            int room = cursor == null ? max : max - cursor.stackSize;
            int want = mode == HALF ? Math.max(1, (stack + 1) / 2) : stack;
            ItemStack got = c.takeRecycled(e.kind, Math.min(want, room));
            if (got == null) return;
            if (cursor == null) p.inventory.setItemStack(got);
            else cursor.stackSize += got.stackSize;
            if (p instanceof EntityPlayerMP mp) mp.updateHeldItem();
            return;
        }
        ItemStack s = RecycleStore.stackOf(e.kind, stack);
        int took = s == null ? 0 : Campus.insert(p, s);
        if (took <= 0) {
            tell(p, P + "inventory_full");
            return;
        }
        store.extract(e.kind, took);
        p.inventory.markDirty();
    }

    /** 全部取出: as much of the store as the inventory takes, the largest kinds first. */
    static void takeAll(TileRecycleBin bin, EntityPlayer p) {
        String why = refuse(bin, p);
        if (why != null) {
            tell(p, why);
            return;
        }
        Campus c = bin.nexus()
            .campus();
        if (c.recycle()
            .isEmpty()) {
            tell(p, P + "empty");
            return;
        }
        int moved = c.takeRecycled(p);
        if (moved > 0) tell(p, P + "took_all", moved);
        else tell(p, P + "inventory_full");
    }

    private static boolean sameKind(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage()
            && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static void write(PacketBuffer buf, NBTTagCompound t) {
        try {
            buf.writeNBTTagCompoundToBuffer(t);
        } catch (IOException e) {
            throw new IllegalStateException("could not write the recycle bin's page", e);
        }
    }

    private static NBTTagCompound read(PacketBuffer buf) {
        try {
            return buf.readNBTTagCompoundFromBuffer();
        } catch (IOException e) {
            FluxEcho.LOG.warn("Could not read the recycle bin's page", e);
            return null;
        }
    }
}
