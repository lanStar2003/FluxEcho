package com.fluxdepths.nei;

import static com.fluxdepths.client.FluxDraw.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.fluxdepths.Config;
import com.fluxdepths.item.ItemImprint;
import com.fluxdepths.shard.Collectors;
import com.fluxdepths.shard.ShardText;
import com.fluxdepths.shard.ShardTier;
import com.fluxdepths.shard.Veins;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * NEI's page for the Flux Shard Collector, in its own tab and in the flux world's look: one vein per page, the
 * imprint that tunes to it, its ores with their chances, where it generates, and how many ores a minute each core
 * circuit gives. Search an ore to see the veins that have it, an imprint for its vein, the collector for all.
 */
public class VeinHandler extends TemplateRecipeHandler {

    public static final String ID = "fluxdepths.veins";
    public static final int WIDTH = 166, HEIGHT = 120;

    /** Where the ores sit: two columns of two, each with its chance beside it. */
    private static final int[][] ORE_POS = { { 78, 22 }, { 122, 22 }, { 78, 44 }, { 122, 44 } };

    public class CachedVein extends CachedRecipe {

        final Veins.Vein vein;
        final PositionedStack imprint;
        final List<PositionedStack> ores = new ArrayList<>();
        final List<Double> shares = new ArrayList<>();

        CachedVein(Veins.Vein vein) {
            this.vein = vein;
            imprint = new PositionedStack(ItemImprint.forNei(vein.name), 9, 33);
            int n = Math.min(ORE_POS.length, vein.mix.size());
            for (int i = 0; i < n; i++) {
                ItemStack ore = vein.mix.ores()
                    .get(i)
                    .copy();
                ore.stackSize = 1;
                ores.add(new PositionedStack(ore, ORE_POS[i][0], ORE_POS[i][1]));
                shares.add(vein.mix.share(i));
            }
        }

        @Override
        public PositionedStack getResult() {
            return ores.isEmpty() ? null : ores.get(0);
        }

        @Override
        public List<PositionedStack> getIngredients() {
            List<PositionedStack> l = new ArrayList<>();
            l.add(imprint);
            return l;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return ores.size() <= 1 ? new ArrayList<>() : new ArrayList<>(ores.subList(1, ores.size()));
        }
    }

    @Override
    public String getRecipeName() {
        return StatCollector.translateToLocal("fluxdepths.nei.title");
    }

    @Override
    public String getRecipeTabName() {
        return getRecipeName();
    }

    @Override
    public String getHandlerId() {
        return ID;
    }

    @Override
    public String getOverlayIdentifier() {
        return ID;
    }

    @Override
    public String getGuiTexture() {
        return "fluxdepths:textures/gui/slot.png";
    }

    @Override
    public int recipiesPerPage() {
        return 2;
    }

    @Override
    public void loadTransferRects() {
        transferRects.add(new RecipeTransferRect(new java.awt.Rectangle(36, 18, 30, 50), ID));
    }

    private static List<Veins.Vein> veins() {
        List<Veins.Vein> l = new ArrayList<>();
        if (!Config.shardsEnabled) return l;
        for (Veins.Vein v : Veins.all()
            .values()) if (v.enabled && !v.mix.isEmpty()) l.add(v);
        return l;
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (ID.equals(outputId)) {
            for (Veins.Vein v : veins()) arecipes.add(new CachedVein(v));
        } else super.loadCraftingRecipes(outputId, results);
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        for (Veins.Vein v : veins())
            for (ItemStack ore : v.mix.ores()) if (NEIServerUtils.areStacksSameTypeCrafting(ore, result)) {
                arecipes.add(new CachedVein(v));
                break;
            }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        String vein = ItemImprint.vein(ingredient);
        if (vein != null) {
            Veins.Vein v = Veins.get(vein);
            if (v != null && v.enabled) arecipes.add(new CachedVein(v));
            return;
        }
        ItemStack collector = Collectors.main();
        if (collector != null && NEIServerUtils.areStacksSameTypeCrafting(collector, ingredient))
            loadCraftingRecipes(ID);
    }

    // ---- drawing

    @Override
    public void drawBackground(int recipe) {
        float t = Minecraft.getSystemTime() / 50f;
        begin();
        pane(0, 0, WIDTH, HEIGHT - 4, 1f);
        rect(1, 1, WIDTH - 1, 14, CYAN, 0.12f);
        rect(1, 14, WIDTH - 1, 15, CYAN, 0.55f);
        // the imprint's slot and the depth shaft
        slot(8, 32);
        gradient(40, 18, 64, 68, DEEP, 1f, 0x150A2A, 1f);
        frame(40, 18, 64, 68, SEAM, 1f);
        gradient(47, 20, 57, 66, VIOLET, 0.12f, VIOLET, 0.4f);
        for (int i = 0; i < 6; i++) {
            double at = (t * 0.6 + i * 8) % 46;
            float fade = (float) (at / 46);
            rect(52 - 8 + fade * 3, 20 + at, 52 + 8 - fade * 3, 21 + at, CYAN, 0.6f * (1 - fade));
        }
        corners(40, 18, 64, 68, 4, CYAN, 1f);
        // the ores
        CachedVein v = (CachedVein) arecipes.get(recipe);
        for (int i = 0; i < v.ores.size(); i++) slot(ORE_POS[i][0] - 1, ORE_POS[i][1] - 1);
        rect(4, 72, WIDTH - 4, 73, SEAM, 1f);
        end();
    }

    private static void slot(int x, int y) {
        rect(x, y, x + 18, y + 18, DEEP, 1f);
        frame(x, y, x + 18, y + 18, SEAM, 1f);
        rect(x + 1, y + 17, x + 17, y + 18, CYAN, 0.35f);
    }

    @Override
    public void drawExtras(int recipe) {
        CachedVein v = (CachedVein) arecipes.get(recipe);
        String name = font().trimStringToWidth(v.vein.displayName(), WIDTH - 50);
        text(name, 5, 4, CYAN, 1f);
        right(StatCollector.translateToLocal("fluxdepths.nei.tag"), WIDTH - 5, 4, DIM, 1f);
        smallCentered(StatCollector.translateToLocal("fluxdepths.nei.imprint"), 17, 54, 0.6f, DIM, 1f);
        for (int i = 0; i < v.ores.size(); i++) {
            String pct = String.format(Locale.ROOT, "%.1f%%", v.shares.get(i) * 100);
            small(pct, ORE_POS[i][0] + 19, ORE_POS[i][1] + 5, 0.75f, i < 2 ? WHITE : DIM, 1f);
        }
        // where it generates
        String dims = String.join(", ", v.vein.dims);
        small(
            font().trimStringToWidth(StatCollector.translateToLocalFormatted("fluxdepths.nei.dims", dims), 280),
            5,
            76,
            0.55f,
            DIM,
            1f);
        // ores a minute per core
        ShardTier[] all = ShardTier.values();
        for (int i = 0; i < all.length; i++) {
            ShardTier tier = all[i];
            int col = i % 4, row = i / 4;
            double x = 5 + col * 40, y = 86 + row * 13;
            begin();
            rect(x, y, x + 38, y + 11, tier.color(), 0.14f);
            rect(x, y, x + 2, y + 11, tier.color(), 0.9f);
            end();
            small(tier.label(), x + 4, y + 2, 0.6f, tier.color(), 1f);
            small(ShardText.t("nei.per_minute", Math.round(tier.perMinute())), x + 4, y + 7, 0.45f, WHITE, 1f);
        }
    }
}
