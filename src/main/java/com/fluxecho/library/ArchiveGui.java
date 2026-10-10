package com.fluxecho.library;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import com.fluxecho.core.EchoText;
import com.fluxecho.core.FluxMachineGui;
import com.fluxecho.library.client.ArchiveScreen;
import com.fluxecho.library.client.LibraryScreen;
import com.fluxecho.logic.LibraryUnits;
import com.fluxecho.nexus.client.NexusScreen;
import com.gtnewhorizons.modularui.api.forge.IItemHandlerModifiable;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.math.Size;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;

/**
 * The Echo Archive's GUI, for a library standing as the Archive ({@link TileLibrary#isArchive()}; a 0.9.2 hall keeps
 * the window of {@link LibraryGui}). Two pages, chosen by the tabs at the top right. 书架 shows one bookcase: its call
 * number, how many of its nine places hold a book and where it stands in the room; its books as a 3 x 3 grid (row 0 on
 * top, column 0 on the left of someone facing the books, as {@link LibraryUnits#slot} numbers them); arrows to the
 * neighbouring bookcase and to the one five along; a return slot that files a sample into the bookcase's first free
 * place; and the floor plans of both storeys ({@link ArchiveMap}), every bookcase a tile coloured by how full it is,
 * the one shown outlined, a click picking another. 目录 lists every book with its call number and place: a click on a
 * line goes to its bookcase, a click on the card mark at its end points the card desk at it. Below both pages: the
 * card desk, the book count, the lending line and the team vault's line with its 取回 button; then the player's
 * inventory.
 * <p>
 * The bookcase shown is the window's own selection, kept on the server ({@link Sel}). It starts at
 * {@link TileLibrary#selectedUnit} (which the bookcase clicked to open the window has just set) and every change is
 * written back there, so the next opening returns to it, while two players browsing the same Archive do not move each
 * other's grid. The grid is a nine-slot view of the samples ({@link Shelf}) that on the server looks the selection up
 * on every access, so a change of bookcase never leaves a stale slot mapping behind: the container simply sees nine
 * slots whose contents changed and sends them. The client asks for a bookcase through {@link Link} (the plans and the
 * catalogue) or with the arrow buttons; the server checks it (0..77) and the answer comes back as the synced
 * selection, {@link TileLibrary#guiUnit}. The plans, the catalogue and the counts read the client's copy of the books,
 * {@link TileLibrary#clientBooks}.
 * <p>
 * Shift-clicking a sample in the inventory goes to the return slot ({@link Drop}), which files one book per click into
 * the shown bookcase; the grid's own slots take no shift-clicks, so a stack is never spread over the bookcase as
 * copies of one book. Blank cards go to the desk first. After a click that touched the grid or the return slot the
 * server sends the whole window once more ({@link Link}), since vanilla trusts the client to have foreseen a click's
 * effects and a filed book lands in a place the client did not touch.
 */
public final class ArchiveGui {

    /** The window. */
    public static final int W = 260, H = 256, INV_Y = 174;
    /** The pages: one bookcase, and the catalogue. */
    public static final int SHELF = 0, CATALOGUE = 1;
    // the top row: the header and the tabs
    public static final int TAB_Y = 4, TAB_W = 42, TAB_H = 15, TAB_SHELF_X = 168, TAB_LIST_X = 212, HEADER_W = 160;
    // the page area: on 书架 the bookcase panel on the left and the plans on the right
    public static final int PAGE_TOP = 22, PAGE_BOTTOM = 140, SHELF_RIGHT = 86, MAP_LEFT = 89, PANEL_RIGHT = 254;
    public static final int HEAD_X = 9, HEAD_Y = 25, HEAD_W = 75, HEAD_H = 16, GRID_X = 19, GRID_Y = 44;
    public static final int NAV_Y = 103, NAV_W = 17, NAV_H = 14, DROP_X = 9, DROP_Y = 120, DROP_LABEL_X = 29,
        DROP_LABEL_W = 55;
    public static final int MAP_Y = 25;
    /** Where the arrows (-5, -1, +1, +5) and the two plans (ground, gallery) stand. */
    private static final int[] NAV_X = { 9, 28, 47, 66 }, NAV_STEP = { -5, -1, 1, 5 }, MAP_X = { 92, 174 };
    private static final String[] NAV_TIP = { "library.gui.prev5", "library.gui.prev", "library.gui.next",
        "library.gui.next5" };
    // the catalogue
    public static final int LIST_X = 9, LIST_HEAD_Y = 25, LIST_Y = 36, LIST_W = 234, ROW_H = 11, ROWS = 9, CARD_W = 12,
        SCROLL_X = 245, SCROLL_W = 6;
    /** Lines a turn of the mouse wheel scrolls the catalogue. */
    public static final int SCROLL_STEP = 3;
    // the strip below both pages: the card desk, the counts and the vault
    public static final int STRIP_Y = 142, DESK_X = 8, DESK_Y = 146, INFO_X = 132, INFO_Y = 144, INFO_W = 122,
        INFO_H = 26;
    public static final int REFILL_X = 216, REFILL_Y = 157, REFILL_W = 38, REFILL_H = 12;

    /** Bookcases in the Archive. */
    private static final int UNITS = LibraryUnits.UNITS.size();

    private ArchiveGui() {}

    /**
     * One open window's client side: the page shown, the catalogue's scroll, and the catalogue itself (the slots that
     * hold a book, in slot order, rebuilt from {@link TileLibrary#clientBooks} at most every {@link #REBUILD_MS}).
     */
    public static final class View {

        static final long REBUILD_MS = 150;

        /** {@link #SHELF} or {@link #CATALOGUE}; the client's own. */
        public int page = SHELF;
        /** The catalogue's first line shown. */
        public int scroll;
        private int[] books = new int[0];
        private long builtAt;

        /** The slots holding a book, in slot order (and so bookcase by bookcase). */
        public int[] books(TileLibrary l) {
            long now = System.currentTimeMillis();
            if (now - builtAt >= REBUILD_MS || now < builtAt) {
                builtAt = now;
                int cap = Math.min(l.capacity(), l.clientBooks.length), n = 0;
                for (int i = 0; i < cap; i++) if (l.clientBooks[i] != null) n++;
                int[] out = new int[n];
                n = 0;
                for (int i = 0; i < cap; i++) if (l.clientBooks[i] != null) out[n++] = i;
                books = out;
                scroll = clampScroll(scroll, out.length);
            }
            return books;
        }

        /** The slot of the book on a line of the catalogue, or -1 for an empty line. */
        public int slotAt(TileLibrary l, int line) {
            int[] b = books(l);
            int i = scroll + line;
            return line < 0 || i >= b.length ? -1 : b[i];
        }

        void scrollBy(TileLibrary l, int lines) {
            scroll = clampScroll(scroll + lines, books(l).length);
        }

        /** Scrolls so that the bookcase's first book (or the next bookcase's) is the first line. */
        void scrollTo(TileLibrary l, int unit) {
            int[] b = books(l);
            int i = 0;
            while (i < b.length && b[i] < unit * 9) i++;
            scroll = clampScroll(i, b.length);
        }

        /** Scrolls to a point of the scroll bar, 0 at its top and 1 at its bottom. */
        void scrollToFraction(TileLibrary l, double f) {
            int n = books(l).length;
            scroll = clampScroll((int) Math.round(Math.max(0, Math.min(1, f)) * (n - ROWS)), n);
        }

        private static int clampScroll(int s, int n) {
            return Math.max(0, Math.min(s, n - ROWS));
        }
    }

    /**
     * The window's selection on the server: the bookcase its grid shows. Every change is written to
     * {@link TileLibrary#selectedUnit} too, the bookcase the next window opens at.
     */
    static final class Sel {

        private final TileLibrary l;
        int unit;

        Sel(TileLibrary l) {
            this.l = l;
            unit = unitOf(l.selectedUnit);
        }

        /** Shows the bookcase; anything outside 0..77 is ignored. */
        void set(int u) {
            if (u < 0 || u >= UNITS) return;
            unit = u;
            l.selectUnit(u);
        }

        /** Moves along the bookcases, round from the last to the first. */
        void step(int by) {
            set(Math.floorMod(unit + by, UNITS));
        }
    }

    /** A bookcase index brought into 0..77. */
    static int unitOf(int u) {
        return Math.max(0, Math.min(UNITS - 1, u));
    }

    /**
     * The grid: the nine places of the bookcase shown as one nine-slot handler, place {@code k} being sample slot
     * {@code unit * 9 + k}. On the server every access looks the window's selection up anew and goes to the library's
     * samples, so the slot mapping is never stale; whether a book may go in is the samples' own rule
     * ({@code isItemValid}: a place of the shape, not a written card, not a book the library already keeps). On the
     * client it is a plain copy that the container fills, and its checks only predict the server's from the client's
     * books.
     */
    static final class Shelf implements IItemHandlerModifiable {

        private final TileLibrary l;
        private final IntSupplier unit;
        private final Link link;
        private final boolean client;
        private final ItemStack[] shown = new ItemStack[9];

        Shelf(TileLibrary l, IntSupplier unit, Link link, boolean client) {
            this.l = l;
            this.unit = unit;
            this.link = link;
            this.client = client;
        }

        /** The sample slot of place {@code k} of the bookcase shown. */
        int slot(int k) {
            return unitOf(unit.getAsInt()) * 9 + k;
        }

        @Override
        public int getSlots() {
            return 9;
        }

        @Override
        public ItemStack getStackInSlot(int k) {
            if (k < 0 || k >= 9) return null;
            return client ? shown[k] : l.samples.getStackInSlot(slot(k));
        }

        @Override
        public void setStackInSlot(int k, ItemStack s) {
            if (k < 0 || k >= 9) return;
            if (client) shown[k] = s;
            else {
                link.touch();
                l.samples.setStackInSlot(slot(k), s);
            }
        }

        @Override
        public ItemStack insertItem(int k, ItemStack s, boolean simulate) {
            if (k < 0 || k >= 9 || s == null) return s;
            if (!isItemValid(k, s)) return s;
            if (!client) return l.samples.insertItem(slot(k), s, simulate);
            if (shown[k] != null) return s;
            if (!simulate) shown[k] = one(s);
            return rest(s);
        }

        @Override
        public ItemStack extractItem(int k, int amount, boolean simulate) {
            if (k < 0 || k >= 9 || amount <= 0) return null;
            if (!client) {
                link.touch();
                return l.samples.extractItem(slot(k), amount, simulate);
            }
            ItemStack s = shown[k];
            if (s == null) return null;
            int n = Math.min(amount, s.stackSize);
            ItemStack out = s.copy();
            out.stackSize = n;
            if (!simulate) {
                if (n >= s.stackSize) shown[k] = null;
                else {
                    ItemStack left = s.copy();
                    left.stackSize -= n;
                    shown[k] = left;
                }
            }
            return out;
        }

        @Override
        public int getSlotLimit(int k) {
            return 1;
        }

        @Override
        public boolean isItemValid(int k, ItemStack s) {
            if (k < 0 || k >= 9) return false;
            if (client) return mayShelve(k, s);
            link.touch();
            return l.samples.isItemValid(slot(k), s);
        }

        /**
         * Client: whether the server will take the book at place {@code k}, judged from what the client knows: the
         * grid's own copy for this bookcase, the client's books for the rest.
         */
        boolean mayShelve(int k, ItemStack s) {
            if (s == null || ItemLibraryCard.written(s)) return false;
            int base = slot(0), cap = Math.min(l.capacity(), l.clientBooks.length);
            if (base + k >= cap) return false;
            for (int j = 0; j < 9; j++) if (j != k && same(shown[j], s)) return false;
            for (int i = 0; i < cap; i++) if ((i < base || i >= base + 9) && same(l.clientBooks[i], s)) return false;
            return true;
        }

        /** Client: the first empty place of the grid, or -1. */
        int firstShown() {
            for (int k = 0; k < 9; k++) if (shown[k] == null) return k;
            return -1;
        }
    }

    /**
     * The return slot: a one-slot handler that always looks empty and files whatever is put in (one book at a time)
     * into the first free place of the bookcase shown. It refuses what the bookcase could not take there (a full
     * bookcase, a book the library already keeps, a written card) and every card, so blank cards find the desk. On
     * the client it only predicts: it takes nothing, and remembers the last book it was given for a moment, so a
     * shift-click predicts one book filed, as the server does, and not the whole stack.
     */
    static final class Drop implements IItemHandlerModifiable {

        /** How long the client takes the last book it was given as already filed, in milliseconds. */
        private static final long PENDING_MS = 1500;

        private final TileLibrary l;
        private final IntSupplier unit;
        private final Shelf shelf;
        private final Link link;
        private final EntityPlayer player;
        private final boolean client;
        private ItemStack pending;
        private long pendingAt;

        Drop(TileLibrary l, IntSupplier unit, Shelf shelf, Link link, EntityPlayer player, boolean client) {
            this.l = l;
            this.unit = unit;
            this.shelf = shelf;
            this.link = link;
            this.player = player;
            this.client = client;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int k) {
            return null;
        }

        @Override
        public void setStackInSlot(int k, ItemStack s) {
            if (k != 0 || s == null) return;
            if (client) {
                pending = s.copy();
                pendingAt = System.currentTimeMillis();
            } else {
                link.touch();
                file(s);
            }
        }

        @Override
        public ItemStack insertItem(int k, ItemStack s, boolean simulate) {
            if (s == null) return null;
            if (!isItemValid(k, s)) return s;
            if (!simulate) setStackInSlot(k, one(s));
            return rest(s);
        }

        @Override
        public ItemStack extractItem(int k, int amount, boolean simulate) {
            return null;
        }

        @Override
        public int getSlotLimit(int k) {
            return 1;
        }

        @Override
        public boolean isItemValid(int k, ItemStack s) {
            if (k != 0 || s == null || s.getItem() instanceof ItemLibraryCard) return false;
            if (client) {
                int free = shelf.firstShown();
                return free >= 0 && !pending(s) && shelf.mayShelve(free, s);
            }
            link.touch();
            int free = l.firstFree(unitOf(unit.getAsInt()));
            return free >= 0 && l.samples.isItemValid(free, s);
        }

        /** Client: whether the book was just given and is still on its way to the server. */
        private boolean pending(ItemStack s) {
            return pending != null && System.currentTimeMillis() - pendingAt < PENDING_MS && same(pending, s);
        }

        /**
         * Server: files one of the stack into the bookcase's first free place. Whatever it cannot file (nothing, as the
         * slot checks first and takes one at a time, but never lost) goes back to the player at their feet; so does
         * all of it when the library was broken while the window was open.
         */
        private void file(ItemStack s) {
            int u = unitOf(unit.getAsInt()), free = l.isInvalid() ? -1 : l.firstFree(u), left = s.stackSize;
            ItemStack one = one(s);
            if (free >= 0 && l.samples.isItemValid(free, one)) {
                l.samples.setStackInSlot(free, one);
                left--;
                int[] at = l.unitCell(u, free - u * 9);
                if (at != null && l.getWorldObj() != null) Shelves.sound(l.getWorldObj(), at[0], at[1], at[2], 1.1f);
            }
            if (left > 0 && player != null) {
                ItemStack back = s.copy();
                back.stackSize = left;
                player.dropPlayerItemWithRandomChoice(back, false);
            }
        }
    }

    /**
     * The window's selection, synced: the server sends the bookcase shown ({@link TileLibrary#guiUnit} on the client),
     * and the client asks through it for another bookcase ({@link #ask}) or for the book the card desk writes for
     * ({@link #point}). The server checks both.
     * <p>
     * It also keeps the client's slots honest. Right after a click the server sends no slot changes, taking the client
     * to have foreseen them ({@code EntityPlayerMP.isChangingQuantityOnly}); but a book filed through the return slot
     * lands in a place of the grid the client did not touch, and the client can misjudge a place (it does not know the
     * NBT of every book). So after a click that asked the grid or the return slot anything, the whole window is sent
     * again on the next tick, as vanilla does after a click it refused.
     */
    static final class Link extends FakeSyncWidget<Integer> {

        static final int ASK_UNIT = 1, ASK_DESK = 2;
        private final TileLibrary l;
        private final Sel sel;
        /** Whether the grid or the return slot was asked anything since the last pass; whether a resend is owed. */
        private boolean touched, owed;

        Link(TileLibrary l, Sel sel) {
            super(
                () -> sel.unit,
                v -> l.guiUnit = v,
                (buf, v) -> buf.writeVarIntToBuffer(v),
                PacketBuffer::readVarIntFromBuffer);
            this.l = l;
            this.sel = sel;
        }

        /** Client: asks the server to show a bookcase. */
        void ask(int unit) {
            if (unit >= 0 && unit < UNITS) syncToServer(ASK_UNIT, buf -> buf.writeVarIntToBuffer(unit));
        }

        /** Client: asks the server to point the card desk at a book (a slot). */
        void point(int slot) {
            if (slot >= 0) syncToServer(ASK_DESK, buf -> buf.writeVarIntToBuffer(slot));
        }

        @Override
        public void readOnServer(int id, PacketBuffer buf) throws IOException {
            int v = buf.readVarIntFromBuffer();
            if (id == ASK_UNIT) sel.set(v);
            else if (id == ASK_DESK) l.selectSample(v);
        }

        /** Server: the grid or the return slot was asked something (a click is being handled). */
        void touch() {
            touched = true;
        }

        @Override
        public void detectAndSendChanges(boolean init) {
            resend();
            super.detectAndSendChanges(init);
        }

        /** Server: after a click's quiet pass that touched the grid or the return slot, sends the window again. */
        private void resend() {
            EntityPlayer p = getContext() == null ? null : getContext().getPlayer();
            if (!(p instanceof EntityPlayerMP)) return;
            EntityPlayerMP mp = (EntityPlayerMP) p;
            if (mp.isChangingQuantityOnly) {
                if (touched) owed = true;
                touched = false;
                return;
            }
            touched = false;
            if (!owed) return;
            owed = false;
            Container c = getContext().getContainer();
            if (c != null && mp.openContainer == c) mp.sendContainerAndContentsToPlayer(c, c.getInventory());
        }
    }

    /** A catalogue button that scrolls the catalogue with the mouse wheel (client). */
    private static final class ScrollButton extends ButtonWidget {

        private final TileLibrary l;
        private final View view;

        ScrollButton(TileLibrary l, View view) {
            this.l = l;
            this.view = view;
        }

        @Override
        public boolean onMouseScroll(int direction) {
            if (direction == 0) return false;
            view.scrollBy(l, direction > 0 ? -SCROLL_STEP : SCROLL_STEP);
            return true;
        }
    }

    static ModularWindow window(TileLibrary l, UIBuildContext ctx) {
        EntityPlayer player = ctx.getPlayer();
        boolean client = player.worldObj.isRemote;
        View view = new View();
        Sel sel = new Sel(l);
        // nothing is shown until the server says which bookcase
        if (client) l.guiUnit = -1;
        IntSupplier unit = client ? () -> l.guiUnit : () -> sel.unit;
        Link link = new Link(l, sel);
        Shelf shelf = new Shelf(l, unit, link, client);
        Drop drop = new Drop(l, unit, shelf, link, player, client);

        ModularWindow.Builder b = ModularWindow.builder(W, H);
        b.setBackground((x, y, w, h, partial) -> ArchiveScreen.background(view, x, y, w, h));
        b.bindPlayerInventory(player, new Pos2d((W - 162) / 2, INV_Y), FluxMachineGui.SLOT);
        b.widget(link);
        b.widget(new FakeSyncWidget.StringSyncer(l::dockStatus, v -> l.guiDock = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(l::lendsShown, v -> l.guiLends = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(() -> l.selected, v -> l.guiSelected = v));
        b.widget(new FakeSyncWidget.BooleanSyncer(l::powered, v -> l.guiPowered = v));
        b.widget(new FakeSyncWidget.IntegerSyncer(l::vaultCount, v -> l.guiVault = v));

        // the slots of the bookcase page, switched on and off with it (each tells the server itself)
        List<SlotWidget> pageSlots = new ArrayList<>();
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> ArchiveScreen.header(l, x, y, w, h))
                .setPos(6, 5)
                .setSize(HEADER_W, 14));
        b.widget(
            new ButtonWidget()
                .setOnClick((click, widget) -> { if (widget.isClient()) show(l, view, SHELF, pageSlots); })
                .setBackground(
                    (x, y, w, h, partial) -> NexusScreen
                        .tab(EchoText.t("library.gui.tab_shelf"), x, y, w, h, view.page == SHELF))
                .setPos(TAB_SHELF_X, TAB_Y)
                .setSize(TAB_W, TAB_H));
        b.widget(
            new ButtonWidget()
                .setOnClick((click, widget) -> { if (widget.isClient()) show(l, view, CATALOGUE, pageSlots); })
                .setBackground(
                    (x, y, w, h, partial) -> NexusScreen
                        .tab(EchoText.t("library.gui.tab_catalogue"), x, y, w, h, view.page == CATALOGUE))
                .setPos(TAB_LIST_X, TAB_Y)
                .setSize(TAB_W, TAB_H));

        shelfPage(l, b, view, sel, shelf, drop, link, player, pageSlots);
        cataloguePage(l, b, view, link, pageSlots);
        strip(l, b);
        return b.build();
    }

    /**
     * Shows a page: the grid and the return slot only on the bookcase page (the other widgets follow the page through
     * their enabled test). Opening the catalogue scrolls it to the bookcase shown.
     */
    private static void show(TileLibrary l, View view, int page, List<SlotWidget> pageSlots) {
        if (page == CATALOGUE && view.page != CATALOGUE) view.scrollTo(l, l.guiUnit);
        view.page = page;
        for (SlotWidget s : pageSlots) s.setEnabled(page == SHELF);
    }

    /** The bookcase page: its header, the grid, the arrows, the return slot and the two plans. */
    private static void shelfPage(TileLibrary l, ModularWindow.Builder b, View view, Sel sel, Shelf shelf, Drop drop,
        Link link, EntityPlayer player, List<SlotWidget> pageSlots) {
        Function<Widget, Boolean> on = widget -> view.page == SHELF;
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> ArchiveScreen.unitHead(l, x, y, w, h))
                .dynamicTooltip(() -> ArchiveScreen.unitTip(l))
                .setUpdateTooltipEveryTick(true)
                .setPos(HEAD_X, HEAD_Y)
                .setSize(HEAD_W, HEAD_H)
                .setEnabled(on));
        for (int k = 0; k < 9; k++) {
            int place = k;
            // one item a place, even from a hotbar key over an empty place
            SlotWidget s = new SlotWidget(new BookSlot(shelf, k, player)).setAccess(true, true)
                .disableShiftInsert();
            s.setBackground(
                FluxMachineGui.SLOT_SAMPLE,
                (x, y, w, h, partial) -> ArchiveScreen.place(l, place, x, y, w, h));
            s.setPos(GRID_X + k % 3 * 18, GRID_Y + k / 3 * 18);
            pageSlots.add(s);
            b.widget(s);
        }
        for (int i = 0; i < NAV_X.length; i++) {
            int by = NAV_STEP[i];
            String tip = NAV_TIP[i];
            b.widget(
                new ButtonWidget().setOnClick((click, widget) -> { if (!widget.isClient()) sel.step(by); })
                    .setBackground((x, y, w, h, partial) -> ArchiveScreen.arrows(x, y, w, h, by > 0, Math.abs(by) > 1))
                    .dynamicTooltip(() -> EchoText.lines(tip))
                    .setPos(NAV_X[i], NAV_Y)
                    .setSize(NAV_W, NAV_H)
                    .setEnabled(on));
        }
        SlotWidget dropSlot = new SlotWidget(drop, 0).setAccess(false, true);
        dropSlot.setBackground(FluxMachineGui.SLOT, (x, y, w, h, partial) -> ArchiveScreen.dropMark(x, y, w, h));
        dropSlot.dynamicTooltip(() -> EchoText.lines("library.gui.drop.tip"));
        dropSlot.setPos(DROP_X, DROP_Y);
        pageSlots.add(dropSlot);
        b.widget(dropSlot);
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> ArchiveScreen.drop(l, x, y, w, h))
                .setPos(DROP_LABEL_X, DROP_Y)
                .setSize(DROP_LABEL_W, 18)
                .setEnabled(on));
        for (int i = 0; i < MAP_X.length; i++) {
            int storey = i;
            ButtonWidget map = new ButtonWidget();
            map.setOnClick((click, widget) -> {
                if (!widget.isClient()) return;
                int u = mapUnit(widget, storey);
                if (u >= 0) link.ask(u);
            });
            map.setBackground((x, y, w, h, partial) -> ArchiveScreen.map(l, storey, mapUnit(map, storey), x, y, w, h));
            map.dynamicTooltip(() -> ArchiveScreen.mapTip(l, mapUnit(map, storey)));
            map.setUpdateTooltipEveryTick(true);
            map.setPos(MAP_X[i], MAP_Y);
            map.setSize(ArchiveMap.W, ArchiveMap.H);
            map.setEnabled(on);
            b.widget(map);
        }
    }

    /** The catalogue page: its title, the lines and the scroll bar. */
    private static void cataloguePage(TileLibrary l, ModularWindow.Builder b, View view, Link link,
        List<SlotWidget> pageSlots) {
        Function<Widget, Boolean> on = widget -> view.page == CATALOGUE;
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> ArchiveScreen.listHead(l, view, x, y, w, h))
                .setPos(LIST_X, LIST_HEAD_Y)
                .setSize(LIST_W, 10)
                .setEnabled(on));
        for (int i = 0; i < ROWS; i++) {
            int line = i;
            ButtonWidget row = new ScrollButton(l, view);
            row.setOnClick((click, widget) -> {
                if (!widget.isClient()) return;
                int slot = view.slotAt(l, line);
                if (slot < 0) return;
                if (onCard(widget)) link.point(slot);
                else {
                    link.ask(slot / 9);
                    show(l, view, SHELF, pageSlots);
                }
            });
            row.setBackground((x, y, w, h, partial) -> ArchiveScreen.row(l, view, line, onCard(row), x, y, w, h));
            row.dynamicTooltip(() -> ArchiveScreen.rowTip(l, view, line, onCard(row)));
            row.setUpdateTooltipEveryTick(true);
            row.setPos(LIST_X, LIST_Y + i * ROW_H);
            row.setSize(LIST_W, ROW_H);
            row.setEnabled(on);
            b.widget(row);
        }
        ButtonWidget bar = new ScrollButton(l, view);
        bar.setOnClick((click, widget) -> {
            if (!widget.isClient()) return;
            double[] m = mouse(widget);
            if (m != null) view.scrollToFraction(l, m[1] / (ROWS * ROW_H));
        });
        bar.setBackground((x, y, w, h, partial) -> ArchiveScreen.scrollbar(l, view, x, y, w, h));
        bar.setPos(SCROLL_X, LIST_Y);
        bar.setSize(SCROLL_W, ROWS * ROW_H);
        bar.setEnabled(on);
        b.widget(bar);
    }

    /**
     * The strip below both pages: the card desk (blank cards in, the chosen book between the arrows, written cards
     * out), the counts, and the vault's line with its 取回 button while the team's vault holds books.
     */
    private static void strip(TileLibrary l, ModularWindow.Builder b) {
        b.widget(
            new SlotWidget(l.desk, 0).setAccess(true, true)
                .setShiftClickPriority(-1)
                .setBackground(FluxMachineGui.SLOT)
                .dynamicTooltip(() -> EchoText.lines("library.gui.blank"))
                .setPos(DESK_X, DESK_Y));
        b.widget(
            new ButtonWidget().setOnClick((click, widget) -> { if (!widget.isClient()) l.select(-1); })
                .setBackground((x, y, w, h, partial) -> LibraryScreen.arrow(x, y, w, h, false))
                .dynamicTooltip(() -> EchoText.lines("library.gui.desk_prev"))
                .setPos(DESK_X + 20, DESK_Y)
                .setSize(12, 18));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> LibraryScreen.chosen(l, x, y, w, h))
                .dynamicTooltip(() -> ArchiveScreen.chosenTip(l))
                .setUpdateTooltipEveryTick(true)
                .setPos(DESK_X + 34, DESK_Y)
                .setSize(18, 18));
        b.widget(
            new ButtonWidget().setOnClick((click, widget) -> { if (!widget.isClient()) l.select(1); })
                .setBackground((x, y, w, h, partial) -> LibraryScreen.arrow(x, y, w, h, true))
                .dynamicTooltip(() -> EchoText.lines("library.gui.desk_next"))
                .setPos(DESK_X + 54, DESK_Y)
                .setSize(12, 18));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> LibraryScreen.press(l, x, y, w, h))
                .setPos(DESK_X + 68, DESK_Y)
                .setSize(30, 18));
        b.widget(
            new SlotWidget(l.desk, 1).setAccess(true, false)
                .setBackground(FluxMachineGui.SLOT_OUT)
                .setPos(DESK_X + 100, DESK_Y));
        b.widget(
            new DrawableWidget().setDrawable((x, y, w, h, partial) -> ArchiveScreen.info(l, x, y, w, h))
                .setPos(INFO_X, INFO_Y)
                .setSize(INFO_W, INFO_H));
        b.widget(new ButtonWidget().setOnClick((click, widget) -> {
            // the library checks the player and tells them what came back
            if (widget.isClient()) return;
            EntityPlayer p = widget.getContext()
                .getPlayer();
            if (p != null) l.refill(p);
        })
            .setBackground(
                (x, y, w, h, partial) -> NexusScreen
                    .button(EchoText.t("library.gui.refill"), x, y, w, h, false, false, TileLibrary.COLOR))
            .dynamicTooltip(() -> EchoText.lines("library.gui.refill.tip"))
            .setPos(REFILL_X, REFILL_Y)
            .setSize(REFILL_W, REFILL_H)
            .setEnabled(widget -> l.guiVault > 0));
    }

    // ---- client helpers

    /** Where the mouse is over the widget, {x, y} in the widget's own pixels; null when it is elsewhere (client). */
    static double[] mouse(Widget widget) {
        if (widget == null || widget.getContext() == null || !widget.isClient()) return null;
        Pos2d m = widget.getContext()
            .getMousePos(), at = widget.getAbsolutePos();
        Size size = widget.getSize();
        if (m == null || at == null || size == null) return null;
        double mx = m.x - at.x + 0.5, my = m.y - at.y + 0.5;
        return mx < 0 || my < 0 || mx > size.width || my > size.height ? null : new double[] { mx, my };
    }

    /** The bookcase under the mouse on a storey's plan, or -1 (client). */
    static int mapUnit(Widget widget, int storey) {
        double[] m = mouse(widget);
        return m == null ? -1 : ArchiveMap.nearest(storey, m[0], m[1]);
    }

    /** Whether the mouse is on the card mark at the end of a catalogue line (client). */
    static boolean onCard(Widget widget) {
        double[] m = mouse(widget);
        return m != null && m[0] >= LIST_W - CARD_W;
    }

    /**
     * The book the card desk writes for, as the client knows it: the chosen slot, or the next one holding a book
     * (as {@link TileLibrary#selectedSample(int)} finds it); -1 when the library keeps none.
     */
    public static int chosenSlot(TileLibrary l) {
        int cap = Math.min(l.capacity(), l.clientBooks.length);
        for (int k = 0; k < cap; k++) {
            int i = Math.floorMod(l.guiSelected + k, cap);
            if (l.book(i) != null) return i;
        }
        return -1;
    }

    /** Whether two stacks are the same book: item, damage and NBT (as the library tells its books apart). */
    static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null
            && a.getItem() == b.getItem()
            && a.getItemDamage() == b.getItemDamage()
            && ItemStack.areItemStackTagsEqual(a, b);
    }

    /** One of the stack. */
    private static ItemStack one(ItemStack s) {
        ItemStack one = s.copy();
        one.stackSize = 1;
        return one;
    }

    /** The stack less one, or null when that leaves nothing. */
    private static ItemStack rest(ItemStack s) {
        if (s.stackSize <= 1) return null;
        ItemStack rest = s.copy();
        rest.stackSize--;
        return rest;
    }
}
