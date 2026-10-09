package com.fluxecho.nei;

import static com.fluxecho.client.FluxDraw.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.client.Motifs;
import com.fluxecho.codex.ClientLedger;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.Compact;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.nexus.Costs;
import com.fluxecho.nexus.Manifests;
import com.fluxecho.nexus.NexusModule;
import com.fluxecho.nexus.NexusNei;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.API;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * The Flux Nexus's manifestation table in NEI, in the flux look: the inputs, the making, the result, its time and
 * power, and the research it opens with (a lock until the team has done it). Look up a result, an input, or the
 * nexus's core for every page.
 */
public class ManifestHandler extends TemplateRecipeHandler {

    public static final int WIDTH = 166, HEIGHT = 92;
    private static final int TOP = 19, AREA = 50;

    public class CachedManifest extends CachedRecipe {

        final Manifests.Recipe recipe;
        final List<PositionedStack> inputs = new ArrayList<>();
        final PositionedStack output;

        CachedManifest(Manifests.Recipe r) {
            recipe = r;
            for (int i = 0; i < r.inputs.size(); i++) {
                List<ItemStack> opts = Costs.options(r.inputs.get(i));
                if (opts.isEmpty()) continue;
                inputs.add(new PositionedStack(opts, 6 + i % 3 * 18, TOP + 4 + i / 3 * 18));
            }
            output = new PositionedStack(r.output(), 136, TOP + 16);
        }

        @Override
        public PositionedStack getResult() {
            return output;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return getCycledIngredients(cycleticks / 20, inputs);
        }
    }

    public ManifestHandler() {
        transferRects.clear();
        transferRects.add(new RecipeTransferRect(new java.awt.Rectangle(64, TOP, 60, AREA), NexusNei.ID));
    }

    @Override
    public TemplateRecipeHandler newInstance() {
        return new ManifestHandler();
    }

    public static void register() {
        ManifestHandler h = new ManifestHandler();
        API.registerRecipeHandler(h);
        API.registerUsageHandler(h);
        ItemStack core = new ItemStack(NexusModule.core);
        HandlerInfo built = new HandlerInfo.Builder(NexusNei.ID, FluxEcho.NAME, FluxEcho.MODID).setHeight(HEIGHT)
            .setWidth(WIDTH)
            .setMaxRecipesPerPage(2)
            .setDisplayStack(core)
            .build();
        API.addRecipeCatalyst(core, NexusNei.ID);
        NeiCheck.track(h, core);
        GuiRecipeTab.handlerMap.put(NexusNei.ID, built);
        GuiRecipeTab.handlerAdderFromIMC.put(NexusNei.ID, built);
    }

    @Override
    public String getRecipeName() {
        return EchoText.t("nei.manifest");
    }

    @Override
    public String getRecipeTabName() {
        return getRecipeName();
    }

    @Override
    public String getHandlerId() {
        return NexusNei.ID;
    }

    @Override
    public String getOverlayIdentifier() {
        return NexusNei.ID;
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
    public void loadTransferRects() {}

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (NexusNei.ID.equals(outputId)) {
            for (Manifests.Recipe r : Manifests.all()) arecipes.add(new CachedManifest(r));
        } else super.loadCraftingRecipes(outputId, results);
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        for (Manifests.Recipe r : Manifests.all()) {
            ItemStack o = r.output();
            if (o != null && NEIServerUtils.areStacksSameTypeCrafting(o, result)) arecipes.add(new CachedManifest(r));
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (NEIServerUtils.areStacksSameTypeCrafting(new ItemStack(NexusModule.core), ingredient)) {
            loadCraftingRecipes(NexusNei.ID);
            return;
        }
        for (Manifests.Recipe r : Manifests.all()) {
            boolean uses = false;
            for (ResearchTree.Cost c : r.inputs) uses |= Costs.matches(c, ingredient);
            if (uses) arecipes.add(new CachedManifest(r));
        }
    }

    @Override
    public void drawBackground(int recipe) {
        CachedManifest c = (CachedManifest) arecipes.get(recipe);
        float t = Minecraft.getSystemTime() / 50f;
        boolean open = ClientLedger.research()
            .contains(c.recipe.research);
        begin();
        pane(0, 0, WIDTH, HEIGHT - 4, 1f);
        hgradient(1, 1, WIDTH * 0.6, 14, VIOLET, 0.25f, VIOLET, 0f);
        rect(1, 14, WIDTH - 1, 15, VIOLET, 0.6f);
        for (int i = 0; i < 6; i++) slot(5 + i % 3 * 18, TOP + 3 + i / 3 * 18, CYAN);
        slot(135, TOP + 15, GREEN);
        gradient(64, TOP, 124, TOP + AREA - 8, DEEP, 1f, 0x120A30, 1f);
        frame(64, TOP, 124, TOP + AREA - 8, SEAM, 1f);
        double cx = 94, cy = TOP + (AREA - 8) / 2.0;
        for (int i = 0; i < 6; i++) {
            double ang = t * 0.03 + i * Math.PI / 3;
            Motifs.dot(cx + Math.cos(ang) * 12, cy + Math.sin(ang) * 7, 1.5, VIOLET, 0.9f);
        }
        for (int i = 0; i < 4; i++) {
            double f = (t * 0.05 + i / 4.0) % 1;
            Motifs.dot(64 + f * 30, cy, 1.4, CYAN, (float) f);
        }
        Motifs.hex(cx, cy, 5, open ? VIOLET : SEAM, 0.7f);
        corners(64, TOP, 124, TOP + AREA - 8, 4, VIOLET, 1f);
        rect(4, TOP + AREA + 2, WIDTH - 4, TOP + AREA + 3, SEAM, 1f);
        end();
    }

    private static void slot(int x, int y, int lip) {
        rect(x, y, x + 18, y + 18, DEEP, 1f);
        frame(x, y, x + 18, y + 18, SEAM, 1f);
        rect(x + 1, y + 17, x + 17, y + 18, lip, 0.35f);
    }

    @Override
    public void drawExtras(int recipe) {
        CachedManifest c = (CachedManifest) arecipes.get(recipe);
        text(fit(getRecipeName(), WIDTH - 50), 5, 4, CYAN, 1f);
        right(EchoText.t("nei.manifest.where"), WIDTH - 5, 4, VIOLET, 1f);
        boolean open = ClientLedger.research()
            .contains(c.recipe.research);
        int y = TOP + AREA + 5, width = (int) ((WIDTH - 22) / 0.6f);
        long eut = Config.manifestEut, ticks = c.recipe.ticks;
        small(
            fit(
                EchoText.t("nei.stats", Compact.si(eut), EchoText.seconds((int) ticks), Compact.si(eut * ticks)),
                width),
            5,
            y,
            0.6f,
            WHITE,
            1f);
        y += 7;
        String lock = EchoText
            .t(open ? "nei.manifest.open" : "nei.manifest.locked", EchoText.t("research.node." + c.recipe.research));
        small(fit(lock, width), 5, y, 0.6f, open ? GREEN : RED, 1f);
        if (c.recipe.record) {
            y += 7;
            small(fit(EchoText.t("nei.manifest.record", Config.recordCooldown / 1200), width), 5, y, 0.6f, DIM, 1f);
        }
        if (!open) {
            GL11.glPushMatrix();
            begin();
            rect(134, TOP + 14, 154, TOP + 34, DEEP, 0.55f);
            end();
            GL11.glPopMatrix();
        }
    }
}
