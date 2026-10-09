package com.fluxecho.research.client;

import static com.fluxecho.client.FluxDraw.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.fluxecho.client.FluxDraw;
import com.fluxecho.client.Motifs;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.nexus.Costs;
import com.fluxecho.nexus.NexusNet;

/**
 * The research star map of a Flux Nexus (blueprint 3.5): the six branches as constellations round the anchor, each
 * research a star, lit once done; the running one pulses with its progress and a stream of data runs out to it from
 * the anchor. Hover a star for what it costs, opens and needs; click an open one to start it. Opened from the nexus's
 * GUI or from a flux terminal bound to the nexus.
 */
public class StarMapScreen extends GuiScreen implements NexusNet.ClientSink {

    private static final RenderItem ITEMS = new RenderItem();
    private static final int STARS = 220;

    private final int dim, x, y, z;
    private NBTTagCompound data;
    private int ask;
    private String selected, toast = "";
    private long toastAt;
    private boolean confirmCancel;

    // the map's place on the screen, set each frame
    private double ox, oy, scale;
    /** Click areas of this frame: x0, y0, x1, y1 -> action. */
    private final List<Object[]> clicks = new ArrayList<>();

    public StarMapScreen(int dim, int x, int y, int z) {
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void initGui() {
        NexusNet.setSink(this);
        request();
    }

    @Override
    public void onGuiClosed() {
        NexusNet.setSink(null);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void request() {
        NexusNet.ask(NexusNet.ASK_MAP, NexusNet.at(dim, x, y, z));
    }

    @Override
    public void updateScreen() {
        if (++ask >= 20) {
            ask = 0;
            request();
        }
    }

    @Override
    public void receive(int kind, NBTTagCompound t) {
        if (kind == NexusNet.MAP) {
            if (t.getInteger("Dim") == dim && t.getInteger("X") == x
                && t.getInteger("Y") == y
                && t.getInteger("Z") == z) data = t;
        } else if (kind == NexusNet.RESULT) {
            toast = EchoText.t("research." + t.getString("Key"));
            toastAt = Minecraft.getSystemTime();
            confirmCancel = false;
        }
    }

    // ---- what the server said

    private Set<String> set(String key) {
        Set<String> s = new HashSet<>();
        if (data == null) return s;
        NBTTagList l = data.getTagList(key, 8);
        for (int i = 0; i < l.tagCount(); i++) s.add(l.getStringTagAt(i));
        return s;
    }

    private boolean found() {
        return data != null && data.getBoolean("Found");
    }

    private String running() {
        return data == null ? "" : data.getString("Research");
    }

    private long cost(ResearchTree.Node n) {
        if (data != null && data.getCompoundTag("Cost")
            .hasKey(n.id))
            return data.getCompoundTag("Cost")
                .getLong(n.id);
        return n.compute;
    }

    private ResearchTree.Block block(ResearchTree.Node n, Set<String> open) {
        return ResearchTree
            .check(n, open, data == null ? 1 : data.getInteger("Phase"), data == null ? 0 : data.getInteger("Records"));
    }

    // ---- drawing

    @Override
    public void drawScreen(int mx, int my, float partial) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glDisable(GL11.GL_CULL_FACE);
        try {
            draw(mx, my);
        } finally {
            GL11.glPopAttrib();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
        super.drawScreen(mx, my, partial);
    }

    private void draw(int mx, int my) {
        clicks.clear();
        float t = Minecraft.getSystemTime() / 50f;
        double margin = 24;
        scale = Math.min((width - 2 * margin) / ResearchTree.WIDTH, (height - 70) / (double) ResearchTree.HEIGHT);
        ox = (width - ResearchTree.WIDTH * scale) / 2;
        oy = 30 + (height - 70 - ResearchTree.HEIGHT * scale) / 2;

        FluxDraw.begin();
        sky(t);
        Set<String> open = set("Open");
        String run = running();
        for (ResearchTree.Branch b : ResearchTree.Branch.values()) nebula(b, t);
        for (ResearchTree.Node n : ResearchTree.all())
            for (String r : n.requires) link(ResearchTree.get(r), n, open, t);
        if (!run.isEmpty()) stream(ResearchTree.get(ResearchTree.ANCHOR), ResearchTree.get(run), t);
        ResearchTree.Node hover = null;
        for (ResearchTree.Node n : ResearchTree.all()) {
            star(n, open, run, t);
            double sx = sx(n.x), sy = sy(n.y);
            if (Math.hypot(mx - sx, my - sy) <= Math.max(8, 7 * scale)) hover = n;
        }
        FluxDraw.end();

        for (ResearchTree.Branch b : ResearchTree.Branch.values()) {
            String name = EchoText.t("research.branch." + b.key);
            smallCentered(
                name,
                sx(b.labelX),
                sy(b.labelY) - 4,
                (float) Math.max(0.75, Math.min(1.2, scale * 0.6)),
                b.color,
                0.75f);
        }
        for (ResearchTree.Node n : ResearchTree.all()) {
            if (block(n, open) == ResearchTree.Block.LATER_PHASE && !open.contains(n.id)) continue;
            smallCentered(
                EchoText.t("research.node." + n.id),
                sx(n.x),
                sy(n.y) + 7 * scale + 2,
                0.75f,
                open.contains(n.id) ? WHITE : DIM,
                0.9f);
        }

        header();
        footer(open, mx, my);
        if (hover != null) tooltip(hover, open, mx, my);
        if (!toast.isEmpty() && Minecraft.getSystemTime() - toastAt < 3500) {
            float a = Math.min(1f, (3500 - (Minecraft.getSystemTime() - toastAt)) / 500f);
            FluxDraw.begin();
            int tw = font().getStringWidth(toast) + 16;
            pane(width / 2.0 - tw / 2.0, height - 60, width / 2.0 + tw / 2.0, height - 44, a);
            FluxDraw.end();
            centered(toast, width / 2.0, height - 56, WHITE, a);
        }
    }

    private double sx(double mapX) {
        return ox + mapX * scale;
    }

    private double sy(double mapY) {
        return oy + mapY * scale;
    }

    /** Deep space, stars twinkling. */
    private void sky(float t) {
        gradient(0, 0, width, height, 0x050A12, 0.97f, 0x0A1622, 0.97f);
        for (int i = 0; i < STARS; i++) {
            double px = Motifs.hash(i * 3 + 1) * width, py = Motifs.hash(i * 3 + 2) * height;
            double tw = 0.35 + 0.35 * Math.sin(t * (0.02 + Motifs.hash(i) * 0.05) + i);
            double s = Motifs.hash(i * 7) < 0.1 ? 1.5 : 1;
            Motifs.dot(px, py, s, Motifs.hash(i * 5) < 0.2 ? 0xC8D8FF : WHITE, (float) tw * 0.6f);
        }
    }

    /** A soft glow in the branch's colour where its constellation is. */
    private void nebula(ResearchTree.Branch b, float t) {
        double cx = 0, cy = 0;
        int n = 0;
        for (ResearchTree.Node node : ResearchTree.all()) if (node.branch == b) {
            cx += node.x;
            cy += node.y;
            n++;
        }
        if (n == 0) return;
        cx = cx / n * 0.6 + b.labelX * 0.4;
        cy = cy / n * 0.6 + b.labelY * 0.4;
        disc(sx(cx), sy(cy), 46 * scale, b.color, 0.07f + 0.02f * (float) Math.sin(t * 0.03 + b.ordinal()), 0f);
    }

    /** A filled circle shading from {@code aIn} in the middle to {@code aOut} at the rim. */
    static void disc(double cx, double cy, double r, int rgb, float aIn, float aOut) {
        Tessellator tz = FluxDraw.start(GL11.GL_QUADS);
        int seg = 28;
        for (int i = 0; i < seg; i++) {
            double p = -i * Math.PI * 2 / seg, q = -(i + 1) * Math.PI * 2 / seg;
            tz.setColorRGBA_I(rgb, (int) (aIn * 255));
            tz.addVertex(cx, cy, 0);
            tz.addVertex(cx, cy, 0);
            tz.setColorRGBA_I(rgb, (int) (aOut * 255));
            tz.addVertex(cx + Math.cos(q) * r, cy + Math.sin(q) * r, 0);
            tz.addVertex(cx + Math.cos(p) * r, cy + Math.sin(p) * r, 0);
        }
        tz.draw();
    }

    /** A straight band between two points. */
    static void band(double x0, double y0, double x1, double y1, double w, int rgb, float a) {
        double dx = x1 - x0, dy = y1 - y0, len = Math.hypot(dx, dy);
        if (len < 0.01) return;
        double nx = -dy / len * w / 2, ny = dx / len * w / 2;
        Tessellator tz = FluxDraw.start(GL11.GL_QUADS);
        tz.setColorRGBA_I(rgb, (int) (Math.max(0, Math.min(1, a)) * 255));
        tz.addVertex(x0 - nx, y0 - ny, 0);
        tz.addVertex(x0 + nx, y0 + ny, 0);
        tz.addVertex(x1 + nx, y1 + ny, 0);
        tz.addVertex(x1 - nx, y1 - ny, 0);
        tz.draw();
    }

    private void link(ResearchTree.Node from, ResearchTree.Node to, Set<String> open, float t) {
        if (from == null) return;
        double x0 = sx(from.x), y0 = sy(from.y), x1 = sx(to.x), y1 = sy(to.y);
        boolean lit = open.contains(from.id) && open.contains(to.id), reach = open.contains(from.id);
        int c = to.branch.color;
        if (lit) {
            band(x0, y0, x1, y1, 1.6, c, 0.55f);
            for (int i = 0; i < 3; i++) {
                double f = (t * 0.012 + i / 3.0) % 1;
                Motifs.dot(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, 2, WHITE, 0.8f);
            }
        } else if (reach) {
            int dashes = (int) Math.max(3, Math.hypot(x1 - x0, y1 - y0) / 6);
            for (int i = 0; i < dashes; i += 2) {
                double f0 = i / (double) dashes, f1 = (i + 1) / (double) dashes;
                band(x0 + (x1 - x0) * f0, y0 + (y1 - y0) * f0, x0 + (x1 - x0) * f1, y0 + (y1 - y0) * f1, 1, c, 0.4f);
            }
        } else band(x0, y0, x1, y1, 0.8, SEAM, 0.5f);
    }

    /** Data running from the anchor out to the running research. */
    private void stream(ResearchTree.Node from, ResearchTree.Node to, float t) {
        if (from == null || to == null || from == to) return;
        double x0 = sx(from.x), y0 = sy(from.y), x1 = sx(to.x), y1 = sy(to.y);
        for (int i = 0; i < 8; i++) {
            double f = (t * 0.03 + i / 8.0) % 1;
            double wob = Math.sin(f * Math.PI) * 6 * Math.sin(i * 2.1);
            double nx = -(y1 - y0), ny = x1 - x0, len = Math.hypot(nx, ny);
            Motifs.dot(
                x0 + (x1 - x0) * f + nx / len * wob,
                y0 + (y1 - y0) * f + ny / len * wob,
                1.8,
                to.branch.color,
                (float) Math.sin(f * Math.PI));
        }
    }

    private void star(ResearchTree.Node n, Set<String> open, String run, float t) {
        double cx = sx(n.x), cy = sy(n.y), r = 7 * scale;
        int c = n.branch.color;
        ResearchTree.Block b = block(n, open);
        boolean done = open.contains(n.id), running = n.id.equals(run), later = b == ResearchTree.Block.LATER_PHASE;
        boolean can = b == ResearchTree.Block.NONE;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 0.12 + n.x);
        if (later) {
            Motifs.ring(cx, cy, r * 0.8, 12, 1, SEAM, 0.7f);
            return;
        }
        if (done) disc(cx, cy, r * 2.2, c, 0.35f, 0f);
        else if (can || running) disc(cx, cy, r * (1.6 + 0.4 * pulse), c, 0.25f, 0f);
        double arm = done ? r * 1.4 : r * 0.9;
        float a = done ? 1f : can || running ? 0.6f + 0.4f * pulse : 0.35f;
        int col = done || can || running ? c : DIM;
        band(cx - arm, cy, cx + arm, cy, done ? 1.4 : 1, col, a);
        band(cx, cy - arm, cx, cy + arm, done ? 1.4 : 1, col, a);
        Motifs.dot(cx, cy, done ? r * 0.7 : r * 0.45, done ? WHITE : col, a);
        if (selected != null && selected.equals(n.id)) Motifs.ring(cx, cy, r * 1.6, 16, 1.2, WHITE, 0.9f);
        if (running) {
            float p = data == null ? 0 : data.getFloat("Done");
            int seg = 28;
            for (int i = 0; i < seg; i++) {
                double ang = -Math.PI / 2 + i * Math.PI * 2 / seg;
                boolean lit = i < seg * p;
                Motifs.dot(
                    cx + Math.cos(ang) * r * 2,
                    cy + Math.sin(ang) * r * 2,
                    lit ? 1.8 : 1,
                    lit ? c : SEAM,
                    lit ? 1f : 0.6f);
            }
        }
    }

    private void header() {
        FluxDraw.begin();
        gradient(0, 0, width, 24, DEEP, 0.95f, DEEP, 0.6f);
        rect(0, 23, width, 24, SEAM, 1f);
        FluxDraw.end();
        text(EchoText.t("research.title"), 10, 8, CYAN, 1f);
        String right;
        if (data == null) right = EchoText.t("research.loading");
        else if (!found()) right = EchoText.t("research.not_loaded");
        else right = EchoText.t(
            "research.header",
            data.getString("Owner"),
            EchoText.t("nexus.phase." + data.getInteger("Phase")),
            String.format("%.1f", data.getDouble("Compute")),
            data.getInteger("Records"));
        right(right, width - 10, 8, found() && data.getBoolean("Powered") ? WHITE : AMBER, 1f);
    }

    /** The bar at the bottom: what runs, or the chosen star with its start button. */
    private void footer(Set<String> open, int mx, int my) {
        int h = 30, y0 = height - h;
        FluxDraw.begin();
        gradient(0, y0, width, height, DEEP, 0.6f, DEEP, 0.95f);
        rect(0, y0, width, y0 + 1, SEAM, 1f);
        FluxDraw.end();
        String run = running();
        boolean member = data != null && data.getBoolean("Member");
        if (!run.isEmpty()) {
            ResearchTree.Node n = ResearchTree.get(run);
            float p = data.getFloat("Done");
            String line = EchoText
                .t("research.running", EchoText.t("research.node." + run), Math.round(p * 100), eta(n, p));
            text(line, 10, y0 + 11, n == null ? VIOLET : n.branch.color, 1f);
            if (member) button(
                EchoText.t(confirmCancel ? "research.cancel_confirm" : "research.cancel"),
                width - 10,
                y0 + 7,
                mx,
                my,
                () -> {
                    if (!confirmCancel) confirmCancel = true;
                    else {
                        NexusNet.ask(NexusNet.CANCEL, NexusNet.at(dim, x, y, z));
                        confirmCancel = false;
                    }
                });
            return;
        }
        ResearchTree.Node sel = ResearchTree.get(selected);
        if (sel == null) {
            text(EchoText.t(found() && !member ? "research.not_member" : "research.hint"), 10, y0 + 11, DIM, 1f);
            return;
        }
        ResearchTree.Block b = block(sel, open);
        String name = EchoText.t("research.node." + sel.id);
        text(name, 10, y0 + 11, sel.branch.color, 1f);
        if (b == ResearchTree.Block.NONE && member) {
            boolean pay = canPay(sel);
            button(EchoText.t(pay ? "research.start" : "research.start_missing"), width - 10, y0 + 7, mx, my, () -> {
                NBTTagCompound t = NexusNet.at(dim, x, y, z);
                t.setString("Id", sel.id);
                NexusNet.ask(NexusNet.START, t);
            });
        } else text(state(sel, b), 20 + font().getStringWidth(name), y0 + 11, AMBER, 1f);
    }

    private void button(String label, int right, int top, int mx, int my, Runnable action) {
        int w = font().getStringWidth(label) + 16, x0 = right - w;
        boolean hover = mx >= x0 && mx < right && my >= top && my < top + 16;
        FluxDraw.begin();
        gradient(x0, top, right, top + 16, hover ? 0x16384A : PANE, 1f, DEEP, 1f);
        frame(x0, top, right, top + 16, hover ? CYAN : SEAM, 1f);
        corners(x0, top, right, top + 16, 3, CYAN, 1f);
        FluxDraw.end();
        centered(label, x0 + w / 2.0, top + 4, hover ? WHITE : CYAN, 1f);
        clicks.add(new Object[] { x0, top, right, top + 16, action });
    }

    private String eta(ResearchTree.Node n, float p) {
        double rate = data == null ? 0 : data.getDouble("Compute");
        if (n == null || rate <= 0) return EchoText.t("research.eta_stopped");
        return minutes(cost(n) * (1 - p) / rate);
    }

    private static String minutes(double seconds) {
        if (seconds < 60) return EchoText.t("research.seconds", (int) Math.ceil(seconds));
        return EchoText.t("research.minutes", String.format("%.1f", seconds / 60));
    }

    private String state(ResearchTree.Node n, ResearchTree.Block b) {
        switch (b) {
            case DONE:
                return EchoText.t("research.state.done");
            case LATER_PHASE:
                return EchoText.t("research.state.later_phase", EchoText.t("nexus.phase." + n.phase));
            case RECORDS:
                return EchoText
                    .t("research.state.records", n.minRecords, data == null ? 0 : data.getInteger("Records"));
            case REQUIRES: {
                List<String> need = new ArrayList<>();
                Set<String> open = set("Open");
                for (String r : n.requires) if (!open.contains(r)) need.add(EchoText.t("research.node." + r));
                return EchoText.t("research.state.requires", String.join("、", need));
            }
            default:
                return n.id.equals(running()) ? EchoText.t("research.state.running")
                    : EchoText.t("research.state.open");
        }
    }

    private boolean canPay(ResearchTree.Node n) {
        if (mc.thePlayer.capabilities.isCreativeMode) return true;
        return Costs.plan(n.costs, mc.thePlayer.inventory.mainInventory) != null;
    }

    private void tooltip(ResearchTree.Node n, Set<String> open, int mx, int my) {
        List<String> lore = font().listFormattedStringToWidth(EchoText.t("research.node." + n.id + ".desc"), 200);
        int w = 216, lines = lore.size();
        int h = 34 + lines * 10 + (n.costs.isEmpty() ? 0 : 24) + (n.compute > 0 ? 10 : 0);
        int x0 = Math.min(mx + 12, width - w - 4), y0 = Math.min(my + 8, height - h - 4);
        FluxDraw.begin();
        pane(x0, y0, x0 + w, y0 + h, 0.97f);
        rect(x0 + 1, y0 + 1, x0 + 3, y0 + h - 1, n.branch.color, 0.9f);
        FluxDraw.end();
        int ty = y0 + 6;
        text(EchoText.t("research.node." + n.id), x0 + 8, ty, n.branch.color, 1f);
        smallRight(EchoText.t("research.branch." + n.branch.key), x0 + w - 6, ty + 1, 0.75f, DIM, 1f);
        ty += 12;
        ResearchTree.Block b = block(n, open);
        text(
            state(n, b),
            x0 + 8,
            ty,
            b == ResearchTree.Block.DONE ? GREEN : b == ResearchTree.Block.NONE ? CYAN : AMBER,
            1f);
        ty += 12;
        for (String l : lore) {
            text(l, x0 + 8, ty, 0xB8D0DC, 1f);
            ty += 10;
        }
        if (n.compute > 0) {
            double rate = data == null ? 0 : data.getDouble("Compute");
            String time = rate > 0 ? minutes(cost(n) / rate) : EchoText.t("research.eta_stopped");
            text(EchoText.t("research.compute", cost(n), time), x0 + 8, ty, CYAN, 1f);
            ty += 10;
        }
        if (!n.costs.isEmpty()) {
            int ix = x0 + 8;
            ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
            for (ResearchTree.Cost c : n.costs) {
                List<ItemStack> opts = Costs.options(c);
                if (opts.isEmpty()) continue;
                ItemStack s = opts.get((int) (Minecraft.getSystemTime() / 1000 % opts.size()));
                drawItem(s, ix, ty + 2);
                boolean have = mc.thePlayer.capabilities.isCreativeMode
                    || Costs.plan(Collections.singletonList(c), inv) != null;
                small("×" + c.count, ix + 17, ty + 10, 0.75f, have ? GREEN : RED, 1f);
                ix += 34;
            }
        }
    }

    private void drawItem(ItemStack s, int px, int py) {
        GL11.glPushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        ITEMS.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), s, px, py);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        super.mouseClicked(mx, my, button);
        if (button != 0) return;
        for (Object[] c : new ArrayList<>(clicks)) {
            if (mx >= (Integer) c[0] && mx < (Integer) c[2] && my >= (Integer) c[1] && my < (Integer) c[3]) {
                ((Runnable) c[4]).run();
                return;
            }
        }
        for (ResearchTree.Node n : ResearchTree.all()) {
            if (Math.hypot(mx - sx(n.x), my - sy(n.y)) <= Math.max(8, 7 * scale)) {
                selected = n.id;
                confirmCancel = false;
                return;
            }
        }
        selected = null;
    }
}
