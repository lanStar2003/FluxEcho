package com.fluxecho.mana;

import static com.fluxecho.client.FluxDraw.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.core.CoreCircuits;
import com.fluxecho.core.MachineId;
import com.fluxecho.core.Machines;
import com.fluxecho.logic.Compact;
import com.fluxecho.logic.ManaSpring;
import com.fluxecho.nei.NeiCheck;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.API;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.TemplateRecipeHandler;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * NEI's page for the Mana Echo Spring, in its own tab and the flux world's look: one core circuit per page, with what
 * it makes of the spring (voltage, mana and EU a tick, a petal's worth, reach), the petals it takes and where the
 * mana goes. Look up a mystical petal's uses or the spring itself to see it.
 */
public class SpringNei extends TemplateRecipeHandler {

    public static final String ID = "fluxecho.spring";
    public static final int WIDTH = 166, HEIGHT = 80;
    /** NEI keeps the right edge of each recipe's lower part for its bookmark and overlay buttons. */
    private static final int BUTTONS = 17;

    public class CachedTier extends CachedRecipe {

        final int tier;
        final PositionedStack core, petals, pool, tablet;

        CachedTier(int tier) {
            this.tier = tier;
            core = stack(CoreCircuits.example(tier), 6, 20);
            petals = stack(petals(), 6, 42);
            pool = stack(block("pool"), 76, 20);
            tablet = stack(item("manaTablet"), 76, 42);
        }

        @Override
        public PositionedStack getResult() {
            return pool;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            List<PositionedStack> l = new ArrayList<>();
            if (core != null) l.add(core);
            if (petals != null) l.add(getCycledIngredients(cycleticks / 20, Collections.singletonList(petals)).get(0));
            return l;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            List<PositionedStack> l = new ArrayList<>();
            if (tablet != null) l.add(tablet);
            return l;
        }
    }

    /** Registers the page; called by FluxEcho's NEI plugin when Botania is loaded. */
    public static void register() {
        SpringNei h = new SpringNei();
        API.registerRecipeHandler(h);
        API.registerUsageHandler(h);
        HandlerInfo.Builder info = new HandlerInfo.Builder(ID, FluxEcho.NAME, FluxEcho.MODID).setHeight(HEIGHT)
            .setWidth(WIDTH)
            .setMaxRecipesPerPage(2);
        ItemStack spring = Machines.get(MachineId.MANA_ECHO);
        if (spring != null) {
            info.setDisplayStack(spring);
            API.addRecipeCatalyst(spring, ID);
        }
        NeiCheck.track(h, spring);
        HandlerInfo built = info.build();
        GuiRecipeTab.handlerMap.put(ID, built);
        GuiRecipeTab.handlerAdderFromIMC.put(ID, built);
    }

    private static PositionedStack stack(Object o, int x, int y) {
        if (o == null || o instanceof List<?>l && l.isEmpty()) return null;
        return new PositionedStack(o, x, y, o instanceof List);
    }

    /** All sixteen mystical petals, one after another. */
    private static List<ItemStack> petals() {
        List<ItemStack> l = new ArrayList<>();
        Item petal = GameRegistry.findItem("Botania", "petal");
        if (petal != null) for (int i = 0; i < 16; i++) l.add(new ItemStack(petal, 1, i));
        return l;
    }

    private static ItemStack item(String name) {
        Item i = GameRegistry.findItem("Botania", name);
        return i == null ? null : new ItemStack(i);
    }

    private static ItemStack block(String name) {
        Block b = GameRegistry.findBlock("Botania", name);
        return b == null ? null : new ItemStack(b);
    }

    @Override
    public String getRecipeName() {
        return ManaText.t("nei.title");
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
        return "fluxecho:textures/gui/spring/slot.png";
    }

    @Override
    public int recipiesPerPage() {
        return 2;
    }

    @Override
    public void loadTransferRects() {
        transferRects.add(new RecipeTransferRect(new java.awt.Rectangle(28, 18, 44, 44), ID));
    }

    private void addAll() {
        if (!Config.manaEnabled) return;
        for (int t = ManaSpring.MIN_TIER; t <= ManaSpring.MAX_TIER; t++) arecipes.add(new CachedTier(t));
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (ID.equals(outputId)) addAll();
        else super.loadCraftingRecipes(outputId, results);
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (MTEManaSpring.isPetal(ingredient)) {
            addAll();
            return;
        }
        ItemStack spring = Machines.get(MachineId.MANA_ECHO);
        if (spring != null && NEIServerUtils.areStacksSameTypeCrafting(spring, ingredient)) {
            addAll();
            return;
        }
        // a core circuit: the page of the tier it gives the spring
        int tier = CoreCircuits.tier(ingredient);
        if (Config.manaEnabled && tier >= ManaSpring.MIN_TIER && tier <= ManaSpring.MAX_TIER)
            arecipes.add(new CachedTier(tier));
    }

    // ---- drawing

    @Override
    public void drawBackground(int recipe) {
        CachedTier c = (CachedTier) arecipes.get(recipe);
        int color = CoreCircuits.color(c.tier);
        float t = Minecraft.getSystemTime() / 50f;
        begin();
        pane(0, 0, WIDTH, HEIGHT - 4, 1f);
        hgradient(1, 1, WIDTH * 0.6, 14, color, 0.25f, color, 0f);
        rect(1, 14, WIDTH - 1, 15, color, 0.6f);
        slot(5, 19);
        slot(5, 41);
        slot(75, 19);
        slot(75, 41);
        // a small spring: flux layer, the echo rising, the basin
        double cx = 50;
        gradient(28, 18, 72, 62, DEEP, 1f, 0x0A1430, 1f);
        frame(28, 18, 72, 62, SEAM, 1f);
        gradient(29, 52, 71, 61, VIOLET, 0.1f, VIOLET, 0.4f);
        gradient(cx - 3, 36, cx + 3, 52, MANA, 0.2f, VIOLET, 0.35f);
        for (int i = 0; i < 4; i++) {
            double at = (t * 0.6 + i * 4) % 16;
            float fade = (float) (at / 16);
            rect(cx - 3 + fade, 52 - at, cx + 3 - fade, 53 - at, MANA_PINK, 0.6f * (1 - fade));
        }
        trapezoid(cx, 23, 18, 36, 7, SEAM, 1f, SEAM, 1f);
        trapezoid(cx, 24, 17, 35, 6, 0x7FDDFF, 0.9f, 0x1E6FA8, 0.95f);
        for (double wx = cx - 16; wx < cx + 15; wx += 2) rect(
            wx,
            24 + Math.sin(wx * 0.4 + t * 0.2) * 0.8,
            wx + 2,
            25 + Math.sin(wx * 0.4 + t * 0.2) * 0.8,
            WHITE,
            0.5f);
        corners(28, 18, 72, 62, 4, MANA, 1f);
        // the arrows: in from the left, out to the right
        rect(24, 29, 28, 30, MANA, 0.4f);
        rect(24, 51, 28, 52, MANA, 0.4f);
        rect(72, 29, 75, 30, MANA, 0.4f);
        rect(72, 51, 75, 52, MANA, 0.4f);
        rect(98, 18, 99, 62, SEAM, 1f);
        end();
    }

    private static void slot(int x, int y) {
        rect(x, y, x + 18, y + 18, DEEP, 1f);
        frame(x, y, x + 18, y + 18, SEAM, 1f);
        rect(x + 1, y + 17, x + 17, y + 18, MANA, 0.35f);
    }

    @Override
    public void drawExtras(int recipe) {
        CachedTier c = (CachedTier) arecipes.get(recipe);
        int tier = c.tier, color = CoreCircuits.color(tier);
        text(ManaText.coreLabel(tier), 5, 4, color, 1f);
        right(ManaText.t("nei.tag"), WIDTH - 5, 4, DIM, 1f);

        int mana = ManaSpring.manaPerTick(Config.manaPerTick, tier);
        long eu = ManaSpring.euPerTick(Config.manaPerTick, Config.euPerMana, tier);
        long petal = ManaSpring.manaPerPetal(Config.manaPerPetal, tier);
        double x = 103;
        stat(ManaText.t("nei.mana"), Compact.si(mana), x, 19, MANA);
        stat(ManaText.t("nei.eu"), Compact.si(eu), x, 28, WHITE);
        stat(ManaText.t("nei.petal"), Compact.si(petal), x, 37, MANA_PINK);
        stat(ManaText.t("nei.petals"), ManaText.perMinute(), x, 46, DIM);
        stat(
            ManaText.t("nei.reach"),
            ManaSpring.range(Config.manaRange, tier) + " / ±" + ManaSpring.height(Config.manaHeight, tier),
            x,
            55,
            CYAN);
        smallCentered(ManaText.t("nei.in"), 14, 63, 0.5f, DIM, 1f);
        smallCentered(ManaText.t("nei.out"), 84, 63, 0.5f, DIM, 1f);
        small(ManaText.t("nei.note"), 5, 70, 0.5f, DIM, 1f);
    }

    /** A label on the left of the stats column, its value on the right. */
    private static void stat(String label, String value, double x, double y, int color) {
        small(label, x, y, 0.6f, DIM, 1f);
        smallRight(value, WIDTH - BUTTONS, y, 0.6f, color, 1f);
    }
}
