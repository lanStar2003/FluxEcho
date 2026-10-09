package com.fluxecho.nei;

import static com.fluxecho.client.FluxDraw.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.fluxecho.FluxEcho;
import com.fluxecho.client.Motifs;
import com.fluxecho.core.EchoRecipeMaps;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.FluxMachineGui;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Machines;
import com.fluxecho.logic.Compact;
import com.fluxecho.logic.SlotLayout;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.API;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;

/**
 * An echo machine's NEI page, in its own tab and the flux world's look rather than GT's: the sample in its golden
 * slot, the inputs, the machine's motif at work, the outputs, and its power and time. The examples come from the
 * machine's GT recipe map (whose own GT page is switched off). Look up an output, an input or the sample, or the
 * machine itself for every page.
 */
public class FluxRecipeHandler extends TemplateRecipeHandler {

    public static final int WIDTH = 166, HEIGHT = 100;
    private static final int AREA_TOP = 19, AREA_H = 58;

    private final MachineId kind;
    private final RecipeMap<?> map;
    private final String id;

    public class CachedFlux extends CachedRecipe {

        final GTRecipe recipe;
        final PositionedStack sample;
        final List<PositionedStack> inputs = new ArrayList<>(), outputs = new ArrayList<>();
        final int panelLeft, panelRight;

        CachedFlux(GTRecipe r) {
            recipe = r;
            ItemStack special = r.mSpecialItems instanceof ItemStack s ? s : null;
            sample = kind.sample && special != null ? new PositionedStack(special.copy(), 6, 39) : null;
            ItemStack[] ins = present(r.mInputs), outs = present(r.mOutputs);
            int[][] in = SlotLayout.grid(Math.max(1, ins.length), 28, AREA_TOP, AREA_H, false),
                out = SlotLayout.grid(Math.max(1, outs.length), 160, AREA_TOP, AREA_H, true);
            for (int i = 0; i < ins.length; i++)
                inputs.add(new PositionedStack(ins[i].copy(), in[i][0] + 1, in[i][1] + 1));
            for (int i = 0; i < outs.length; i++)
                outputs.add(new PositionedStack(outs[i].copy(), out[i][0] + 1, out[i][1] + 1));
            panelLeft = SlotLayout.right(in) + 4;
            panelRight = SlotLayout.left(out) - 4;
        }

        @Override
        public PositionedStack getResult() {
            return outputs.isEmpty() ? null : outputs.get(0);
        }

        @Override
        public List<PositionedStack> getIngredients() {
            List<PositionedStack> l = new ArrayList<>(inputs);
            if (sample != null) l.add(sample);
            return l;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return outputs.size() <= 1 ? new ArrayList<>() : new ArrayList<>(outputs.subList(1, outputs.size()));
        }
    }

    public FluxRecipeHandler(MachineId kind, RecipeMap<?> map) {
        this.kind = kind;
        this.map = map;
        this.id = FluxMachineGui.neiId(kind);
    }

    /**
     * NEI answers every lookup with a fresh copy of the handler, made by default through a constructor without
     * arguments, which this one (one per machine) does not have: hand it a copy for the same machine.
     */
    @Override
    public TemplateRecipeHandler newInstance() {
        return new FluxRecipeHandler(kind, map);
    }

    /** Registers the page of every echo machine whose map GT made; called by FluxEcho's NEI plugin. */
    public static void registerAll(Iterable<MachineId> kinds,
        java.util.function.Function<MachineId, RecipeMap<?>> maps) {
        for (MachineId k : kinds) {
            RecipeMap<?> map = maps.apply(k);
            ItemStack machine = Machines.get(k);
            if (map == null || machine == null) continue;
            FluxRecipeHandler h = new FluxRecipeHandler(k, map);
            API.registerRecipeHandler(h);
            API.registerUsageHandler(h);
            HandlerInfo built = new HandlerInfo.Builder(h.id, FluxEcho.NAME, FluxEcho.MODID).setHeight(HEIGHT)
                .setWidth(WIDTH)
                .setMaxRecipesPerPage(2)
                .setDisplayStack(machine)
                .build();
            API.addRecipeCatalyst(machine, h.id);
            // NEI rebuilds its tab table when it loads handler info; whichever comes first, the tab keeps its look
            GuiRecipeTab.handlerMap.put(h.id, built);
            GuiRecipeTab.handlerAdderFromIMC.put(h.id, built);
        }
    }

    private static ItemStack[] present(ItemStack[] stacks) {
        List<ItemStack> l = new ArrayList<>();
        if (stacks != null) for (ItemStack s : stacks) if (s != null && s.getItem() != null) l.add(s);
        return l.toArray(new ItemStack[0]);
    }

    private List<GTRecipe> recipes() {
        return EchoRecipeMaps.examples(kind);
    }

    @Override
    public String getRecipeName() {
        return StatCollector.translateToLocal("gt.blockmachines.fluxecho." + kind.key + ".name");
    }

    @Override
    public String getRecipeTabName() {
        return getRecipeName();
    }

    @Override
    public String getHandlerId() {
        return id;
    }

    @Override
    public String getOverlayIdentifier() {
        return id;
    }

    @Override
    public String getGuiTexture() {
        return "fluxecho:textures/gui/flux/slot.png";
    }

    @Override
    public int recipiesPerPage() {
        return 2;
    }

    @Override
    public void loadTransferRects() {
        transferRects.add(new RecipeTransferRect(new java.awt.Rectangle(60, AREA_TOP, 46, AREA_H), id));
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (id.equals(outputId)) {
            for (GTRecipe r : recipes()) arecipes.add(new CachedFlux(r));
        } else super.loadCraftingRecipes(outputId, results);
    }

    /**
     * Whether a page's stack is the one looked up: the same item, and for an item with NBT (an imprint, a sample)
     * the same NBT unless the one looked up has none (NEI's item list), so an imprint shows its own pages.
     */
    private static boolean same(ItemStack page, ItemStack wanted) {
        return NEIServerUtils.areStacksSameTypeCrafting(page, wanted)
            && (!wanted.hasTagCompound() || !page.hasTagCompound() || ItemStack.areItemStackTagsEqual(page, wanted));
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        for (GTRecipe r : recipes()) for (ItemStack o : present(r.mOutputs)) if (same(o, result)) {
            arecipes.add(new CachedFlux(r));
            break;
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        ItemStack machine = Machines.get(kind);
        if (machine != null && NEIServerUtils.areStacksSameTypeCrafting(machine, ingredient)) {
            loadCraftingRecipes(id);
            return;
        }
        for (GTRecipe r : recipes()) {
            boolean uses = r.mSpecialItems instanceof ItemStack s && same(s, ingredient);
            for (ItemStack i : present(r.mInputs)) uses |= same(i, ingredient);
            if (uses) arecipes.add(new CachedFlux(r));
        }
    }

    // ---- drawing

    @Override
    public void drawBackground(int recipe) {
        CachedFlux c = (CachedFlux) arecipes.get(recipe);
        float t = Minecraft.getSystemTime() / 50f;
        boolean working = true;
        float progress = (t % 60f) / 60f;
        begin();
        pane(0, 0, WIDTH, HEIGHT - 4, 1f);
        hgradient(1, 1, WIDTH * 0.6, 14, kind.accent, 0.25f, kind.accent, 0f);
        rect(1, 14, WIDTH - 1, 15, kind.accent, 0.6f);
        if (c.sample != null) {
            rect(5, 38, 23, 56, 0x0E1410, 1f);
            frame(5, 38, 23, 56, 0x5A4A22, 1f);
            corners(5, 38, 23, 56, 4, 0xFFD27A, 1f);
        }
        for (PositionedStack s : c.inputs) slot(s.relx - 1, s.rely - 1, CYAN);
        for (PositionedStack s : c.outputs) slot(s.relx - 1, s.rely - 1, GREEN);
        double w = c.panelRight - c.panelLeft;
        gradient(c.panelLeft, AREA_TOP, c.panelRight, AREA_TOP + AREA_H, DEEP, 1f, 0x0A1430, 1f);
        frame(c.panelLeft, AREA_TOP, c.panelRight, AREA_TOP + AREA_H, SEAM, 1f);
        GL11.glPushMatrix();
        GL11.glTranslated(c.panelLeft + 2, AREA_TOP + 2, 0);
        Motifs.draw(kind.motif, w - 4, AREA_H - 4, t, progress, working, kind.accent, 1f);
        GL11.glPopMatrix();
        corners(c.panelLeft, AREA_TOP, c.panelRight, AREA_TOP + AREA_H, 4, kind.accent, 1f);
        rect(4, AREA_TOP + AREA_H + 3, WIDTH - 4, AREA_TOP + AREA_H + 4, SEAM, 1f);
        end();
    }

    private static void slot(int x, int y, int lip) {
        rect(x, y, x + 18, y + 18, DEEP, 1f);
        frame(x, y, x + 18, y + 18, SEAM, 1f);
        rect(x + 1, y + 17, x + 17, y + 18, lip, 0.35f);
    }

    @Override
    public void drawExtras(int recipe) {
        CachedFlux c = (CachedFlux) arecipes.get(recipe);
        text(font().trimStringToWidth(getRecipeName(), WIDTH - 50), 5, 4, CYAN, 1f);
        right(EchoText.tier(kind.tier) + " · " + EchoText.t(kind.key + ".type"), WIDTH - 5, 4, kind.accent, 1f);
        if (c.sample != null) smallCentered(EchoText.t("nei.sample"), 14, 58, 0.5f, 0xFFD27A, 1f);
        GTRecipe r = c.recipe;
        long eut = Math.max(0, r.mEUt), ticks = Math.max(0, r.mDuration);
        int y = AREA_TOP + AREA_H + 6, width = (int) ((WIDTH - 10) / 0.6f);
        if (eut > 0) {
            String stats = EchoText
                .t("nei.stats", Compact.si(eut), EchoText.seconds((int) ticks), Compact.si(eut * ticks));
            small(font().trimStringToWidth(stats, width), 5, y, 0.6f, WHITE, 1f);
            y += 7;
        }
        String note = "fluxecho.nei.note." + kind.key;
        if (StatCollector.canTranslate(note))
            small(font().trimStringToWidth(StatCollector.translateToLocal(note), width), 5, y, 0.6f, DIM, 1f);
    }
}
